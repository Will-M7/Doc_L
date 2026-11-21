package com.ejemplo.crudpersona.studio;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Normaliza documentos y plantillas Word; nunca modifica el archivo de origen. */
public final class WordDocumentImporter {
    private static final Set<String> EXTENSIONS = Set.of("doc", "docx", "docm", "dot", "dotx", "dotm");
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final Set<String> WORD_TYPES = Set.of(DOCX_TYPE,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
            "application/vnd.ms-word.document.macroEnabled.main+xml",
            "application/vnd.ms-word.template.macroEnabledTemplate.main+xml");
    private static final String CORE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String FORMATS = "Solo se admiten documentos de Word (.doc, .docx, .docm, .dot, .dotx, .dotm).";

    private WordDocumentImporter() {}

    public static byte[] importToDocx(Path file) throws IOException {
        return importToDocx(file, WordDocumentImporter::convertWithLibreOffice);
    }

    /** Stored client files use the same normalization path as files selected from disk. */
    public static byte[] importToDocx(byte[] content,String fileName)throws IOException{
        String name=Path.of(fileName).getFileName().toString();int dot=name.lastIndexOf('.');
        if(dot<0||!EXTENSIONS.contains(name.substring(dot+1).toLowerCase(Locale.ROOT)))throw new IllegalArgumentException(FORMATS);
        Path temporary=Files.createTempFile("studio-client-word-",name.substring(dot).toLowerCase(Locale.ROOT));
        try{Files.write(temporary,content);return importToDocx(temporary);}finally{Files.deleteIfExists(temporary);}
    }

    // Permite comprobar el camino sin LibreOffice sin depender del PATH de la máquina.
    @FunctionalInterface
    interface OfficeConverter { byte[] convert(Path file) throws IOException; }

    static byte[] importToDocx(Path file, OfficeConverter converter) throws IOException {
        if (file == null || file.getFileName() == null) throw new IllegalArgumentException(FORMATS);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) throw new IllegalArgumentException(FORMATS);
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) throw new IllegalArgumentException(FORMATS);
        if (!Files.isRegularFile(file) || Files.size(file) == 0)
            throw new IOException("El documento de Word está vacío o no se puede leer. " + FORMATS);
        try {
            byte[] source = Files.readAllBytes(file);
            if (extension.equals("docx")) {
                validateDocx(source);
                return source;
            }
            if (!extension.equals("doc") && !extension.equals("dot")) return normalizeOoxml(source);
            // No enviar archivos arbitrarios renombrados como .doc a un conversor externo.
            try (POIFSFileSystem ole = new POIFSFileSystem(new ByteArrayInputStream(source))) {
                if (!ole.getRoot().hasEntry("WordDocument")
                        || !(ole.getRoot().hasEntry("0Table") || ole.getRoot().hasEntry("1Table")))
                    throw new IOException("El archivo no contiene un documento binario de Word.");
            }
            IOException conversionFailure = null;
            try {
                byte[] converted = converter.convert(file);
                if (converted != null) {
                    validateDocx(converted);
                    return converted;
                }
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) throw e;
                conversionFailure = e;
            }
            try {
                return convertWithHwpf(source);
            } catch (IOException | RuntimeException e) {
                if (conversionFailure != null) e.addSuppressed(conversionFailure);
                throw e;
            }
        } catch (IOException | RuntimeException e) {
            throw new IOException("No se pudo importar el documento de Word: está dañado, protegido o no es legible. " + FORMATS, e);
        }
    }

    private static void validateDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            if (!DOCX_TYPE.equals(document.getPackagePart().getContentType()) || document.getDocument().getBody() == null)
                throw new IOException("El paquete no contiene un documento DOCX estándar.");
        }
    }

    private static byte[] normalizeOoxml(byte[] source) throws IOException {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PackageRelationshipCollection roots = pkg.getRelationshipsByType(CORE_REL);
            if (roots.size() != 1) throw new IOException("No se encontró el documento principal de Word.");
            PackagePart main = pkg.getPart(roots.getRelationship(0));
            if (main == null || !WORD_TYPES.contains(main.getContentType()))
                throw new IOException("El paquete no es un documento de Word.");
            main.setContentType(DOCX_TYPE);
            List<PackagePart> parts = new ArrayList<>(pkg.getParts());
            Set<PackagePartName> remove = new HashSet<>();
            for (PackagePart part : parts) {
                String type = part.getContentType().toLowerCase(Locale.ROOT);
                // DOCX no admite VBA. Cambiar el paquete también invalida sus firmas.
                if (type.contains("vba") || type.contains("digital-signature")) remove.add(part.getPartName());
            }
            for (PackagePart part : parts) {
                if (part.isRelationshipPart() || remove.contains(part.getPartName())) continue;
                List<String> relationships = new ArrayList<>();
                for (PackageRelationship rel : part.getRelationships()) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL
                            && remove.contains(part.getRelatedPart(rel).getPartName())) relationships.add(rel.getId());
                }
                relationships.forEach(part::removeRelationship);
            }
            List<String> rootRelationships = new ArrayList<>();
            for (PackageRelationship rel : pkg.getRelationships()) {
                if (rel.getTargetMode() == TargetMode.INTERNAL && remove.contains(pkg.getPart(rel).getPartName()))
                    rootRelationships.add(rel.getId());
            }
            rootRelationships.forEach(pkg::removeRelationship);
            for (PackagePartName name : remove) {
                // removePart no elimina automáticamente el .rels del componente retirado.
                PackagePartName relationships = PackagingURIHelper.getRelationshipPartName(name);
                if (pkg.containPart(relationships)) pkg.removePart(relationships);
                pkg.removePart(name);
            }
            pkg.save(output);
            byte[] result = output.toByteArray();
            validateDocx(result);
            return result;
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("El paquete de Word no es válido.", e);
        }
    }

    private static Path findOffice() {
        String path = System.getenv("PATH");
        if (path != null) for (String command : List.of("soffice", "libreoffice", "soffice.exe", "libreoffice.exe")) {
            for (String directory : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path executable = Path.of(directory, command);
                if (Files.isRegularFile(executable) && Files.isExecutable(executable)) return executable;
            }
        }
        for (String directory : List.of("C:/Program Files/LibreOffice/program", "C:/Program Files (x86)/LibreOffice/program",
                "/Applications/LibreOffice.app/Contents/MacOS")) {
            Path executable = Path.of(directory, directory.startsWith("C:") ? "soffice.exe" : "soffice");
            if (Files.isRegularFile(executable) && Files.isExecutable(executable)) return executable;
        }
        return null;
    }

    private static byte[] convertWithLibreOffice(Path file) throws IOException {
        Path executable = findOffice();
        if (executable == null) return null;
        Path temporary = Files.createTempDirectory("studio-word-import-");
        Process process = null;
        try {
            String extension = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dot") ? ".dot" : ".doc";
            Path input = Files.copy(file, temporary.resolve("document" + extension));
            Path output = Files.createDirectory(temporary.resolve("output"));
            process = new ProcessBuilder(executable.toAbsolutePath().toString(),
                    "-env:UserInstallation=" + temporary.resolve("profile").toUri(),
                    "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                    "--convert-to", "docx:Office Open XML Text", "--outdir", output.toString(), input.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) throw new IOException("LibreOffice excedió el límite de 20 segundos.");
            Path converted = output.resolve("document.docx");
            if (process.exitValue() != 0 || !Files.isRegularFile(converted) || Files.size(converted) == 0)
                throw new IOException("LibreOffice no pudo convertir el documento de Word.");
            return Files.readAllBytes(converted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la importación de Word.", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try { process.waitFor(1, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static byte[] convertWithHwpf(byte[] source) throws IOException {
        try (HWPFDocument legacy = new HWPFDocument(new ByteArrayInputStream(source));
             XWPFDocument document = new XWPFDocument()) {
            Range range = legacy.getRange();
            if (range.numSections() > 0) {
                var section = range.getSection(0);
                var properties = document.getDocument().getBody().addNewSectPr();
                var size = properties.addNewPgSz();
                size.setW(BigInteger.valueOf(section.getPageWidth()));
                size.setH(BigInteger.valueOf(section.getPageHeight()));
                var margins = properties.addNewPgMar();
                margins.setLeft(BigInteger.valueOf(section.getMarginLeft()));
                margins.setRight(BigInteger.valueOf(section.getMarginRight()));
                margins.setTop(BigInteger.valueOf(section.getMarginTop()));
                margins.setBottom(BigInteger.valueOf(section.getMarginBottom()));
            }
            appendRange(range, document, 0);
            return DocumentEngine.bytes(document);
        }
    }

    private static void appendRange(Range range, IBody target, int parentTableLevel) {
        boolean firstParagraph = true;
        for (int index = 0; index < range.numParagraphs(); index++) {
            var paragraph = range.getParagraph(index);
            if (paragraph.isInTable() && paragraph.getTableLevel() > parentTableLevel) {
                var sourceTable = range.getTable(paragraph);
                XWPFTable table;
                if (target instanceof XWPFDocument document) table = document.createTable();
                else {
                    XWPFTableCell cell = (XWPFTableCell) target;
                    if (firstParagraph) cell.removeParagraph(0);
                    try (var cursor = cell.getCTTc().newCursor()) {
                        cursor.toEndToken();
                        table = cell.insertNewTbl(cursor);
                    }
                }
                copyTable(sourceTable, table);
                firstParagraph = false;
                while (index + 1 < range.numParagraphs()
                        && range.getParagraph(index + 1).getStartOffset() < sourceTable.getEndOffset()) index++;
                continue;
            }
            if (paragraph.isTableRowEnd()) continue;
            XWPFParagraph output;
            if (target instanceof XWPFDocument document) output = document.createParagraph();
            else {
                XWPFTableCell cell = (XWPFTableCell) target;
                output = firstParagraph ? cell.getParagraphs().get(0) : cell.addParagraph();
            }
            copyParagraph(paragraph, output);
            firstParagraph = false;
        }
    }

    private static void copyTable(org.apache.poi.hwpf.usermodel.Table source, XWPFTable target) {
        // createTable() contiene una fila/celda inicial; reutilizarlas evita blancos añadidos.
        for (int rowIndex = 0; rowIndex < source.numRows(); rowIndex++) {
            var sourceRow = source.getRow(rowIndex);
            XWPFTableRow row = rowIndex == 0 ? target.getRow(0) : target.createRow();
            if (row == null) row = target.createRow();
            while (row.getTableCells().size() < sourceRow.numCells()) row.addNewTableCell();
            while (row.getTableCells().size() > sourceRow.numCells()) row.removeCell(row.getTableCells().size() - 1);
            for (int cellIndex = 0; cellIndex < sourceRow.numCells(); cellIndex++) {
                var sourceCell = sourceRow.getCell(cellIndex);
                XWPFTableCell cell = row.getCell(cellIndex);
                if (sourceCell.getWidth() > 0) cell.setWidth(Integer.toString(sourceCell.getWidth()));
                appendRange(sourceCell, cell, source.getTableLevel());
            }
        }
    }

    private static void copyParagraph(org.apache.poi.hwpf.usermodel.Paragraph source, XWPFParagraph target) {
        target.setAlignment(switch (source.getJustification()) {
            case 1 -> ParagraphAlignment.CENTER;
            case 2 -> ParagraphAlignment.RIGHT;
            case 3 -> ParagraphAlignment.BOTH;
            case 4 -> ParagraphAlignment.DISTRIBUTE;
            default -> ParagraphAlignment.LEFT;
        });
        target.setIndentationLeft(source.getIndentFromLeft());
        target.setIndentationRight(source.getIndentFromRight());
        target.setIndentationFirstLine(source.getFirstLineIndent());
        target.setSpacingBefore(source.getSpacingBefore());
        target.setSpacingAfter(source.getSpacingAfter());
        for (int index = 0; index < source.numCharacterRuns(); index++) {
            CharacterRun original = source.getCharacterRun(index);
            XWPFRun run = target.createRun();
            run.setBold(original.isBold());
            run.setItalic(original.isItalic());
            String font = original.getFontName();
            if (font != null && !font.isBlank()) run.setFontFamily(font);
            if (original.getFontSize() > 0) run.setFontSize(original.getFontSize() / 2.0);
            run.setUnderline(switch (original.getUnderlineCode()) {
                case 0 -> UnderlinePatterns.NONE;
                case 2 -> UnderlinePatterns.WORDS;
                case 3 -> UnderlinePatterns.DOUBLE;
                case 4 -> UnderlinePatterns.DOTTED;
                default -> UnderlinePatterns.SINGLE;
            });
            StringBuilder text = new StringBuilder();
            for (char character : original.text().toCharArray()) {
                if (character == '\r' || character == '\u0007') continue;
                if (character == '\t' || character == '\u000b' || character == '\f') {
                    if (text.length() > 0) { run.setText(text.toString()); text.setLength(0); }
                    if (character == '\t') run.addTab();
                    else run.addBreak(character == '\f' ? BreakType.PAGE : BreakType.TEXT_WRAPPING);
                } else if (character >= ' ' || character == '\n') text.append(character);
            }
            if (text.length() > 0) run.setText(text.toString());
        }
    }
}
