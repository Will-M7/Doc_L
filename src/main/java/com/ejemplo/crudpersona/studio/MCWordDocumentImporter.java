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

/** @author MC */
public final class MCWordDocumentImporter {
    private static final Set<String> mcEXTENSIONS = Set.of("doc", "docx", "docm", "dot", "dotx", "dotm");
    private static final String mcDOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final Set<String> mcWORD_TYPES = Set.of(mcDOCX_TYPE,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
            "application/vnd.ms-word.document.macroEnabled.main+xml",
            "application/vnd.ms-word.template.macroEnabledTemplate.main+xml");
    private static final String mcCORE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String mcFORMATS = "Solo se admiten documentos de Word (.doc, .docx, .docm, .dot, .dotx, .dotm).";

    private MCWordDocumentImporter() {}

    public static byte[] mcImportToDocx(Path mcFile) throws IOException {
        return mcImportToDocx(mcFile, MCWordDocumentImporter::mcConvertWithLibreOffice);
    }

    public static byte[] mcImportToDocx(byte[] mcContent,String mcFileName)throws IOException{
        String mcName=Path.of(mcFileName).getFileName().toString();int mcDot=mcName.lastIndexOf('.');
        if(mcDot<0||!mcEXTENSIONS.contains(mcName.substring(mcDot+1).toLowerCase(Locale.ROOT)))throw new IllegalArgumentException(mcFORMATS);
        Path mcTemporary=Files.createTempFile("studio-client-word-",mcName.substring(mcDot).toLowerCase(Locale.ROOT));
        try{Files.write(mcTemporary,mcContent);return mcImportToDocx(mcTemporary);}finally{Files.deleteIfExists(mcTemporary);}
    }

    /** @author MC */
    @FunctionalInterface
    interface MCOfficeConverter { byte[] mcConvert(Path mcFile) throws IOException; }

    static byte[] mcImportToDocx(Path mcFile, MCOfficeConverter mcConverter) throws IOException {
        if (mcFile == null || mcFile.getFileName() == null) throw new IllegalArgumentException(mcFORMATS);
        String mcName = mcFile.getFileName().toString();
        int mcDot = mcName.lastIndexOf('.');
        if (mcDot < 0) throw new IllegalArgumentException(mcFORMATS);
        String mcExtension = mcName.substring(mcDot + 1).toLowerCase(Locale.ROOT);
        if (!mcEXTENSIONS.contains(mcExtension)) throw new IllegalArgumentException(mcFORMATS);
        if (!Files.isRegularFile(mcFile) || Files.size(mcFile) == 0)
            throw new IOException("El documento de Word está vacío o no se puede leer. " + mcFORMATS);
        try {
            byte[] mcSource = Files.readAllBytes(mcFile);
            if (mcExtension.equals("docx")) {
                mcValidateDocx(mcSource);
                return mcSource;
            }
            if (!mcExtension.equals("doc") && !mcExtension.equals("dot")) return mcNormalizeOoxml(mcSource);
            try (POIFSFileSystem mcOle = new POIFSFileSystem(new ByteArrayInputStream(mcSource))) {
                if (!mcOle.getRoot().hasEntry("WordDocument")
                        || !(mcOle.getRoot().hasEntry("0Table") || mcOle.getRoot().hasEntry("1Table")))
                    throw new IOException("El archivo no contiene un documento binario de Word.");
            }
            IOException mcConversionFailure = null;
            try {
                byte[] mcConverted = mcConverter.mcConvert(mcFile);
                if (mcConverted != null) {
                    mcValidateDocx(mcConverted);
                    return mcConverted;
                }
            } catch (IOException mcE) {
                if (Thread.currentThread().isInterrupted()) throw mcE;
                mcConversionFailure = mcE;
            }
            try {
                return mcConvertWithHwpf(mcSource);
            } catch (IOException | RuntimeException mcE) {
                if (mcConversionFailure != null) mcE.addSuppressed(mcConversionFailure);
                throw mcE;
            }
        } catch (IOException | RuntimeException mcE) {
            throw new IOException("No se pudo importar el documento de Word: está dañado, protegido o no es legible. " + mcFORMATS, mcE);
        }
    }

    private static void mcValidateDocx(byte[] mcBytes) throws IOException {
        try (XWPFDocument mcDocument = new XWPFDocument(new ByteArrayInputStream(mcBytes))) {
            if (!mcDOCX_TYPE.equals(mcDocument.getPackagePart().getContentType()) || mcDocument.getDocument().getBody() == null)
                throw new IOException("El paquete no contiene un documento DOCX estándar.");
        }
    }

    private static byte[] mcNormalizeOoxml(byte[] mcSource) throws IOException {
        try (OPCPackage mcPkg = OPCPackage.open(new ByteArrayInputStream(mcSource));
             ByteArrayOutputStream mcOutput = new ByteArrayOutputStream()) {
            PackageRelationshipCollection mcRoots = mcPkg.getRelationshipsByType(mcCORE_REL);
            if (mcRoots.size() != 1) throw new IOException("No se encontró el documento principal de Word.");
            PackagePart mcMain = mcPkg.getPart(mcRoots.getRelationship(0));
            if (mcMain == null || !mcWORD_TYPES.contains(mcMain.getContentType()))
                throw new IOException("El paquete no es un documento de Word.");
            mcMain.setContentType(mcDOCX_TYPE);
            List<PackagePart> mcParts = new ArrayList<>(mcPkg.getParts());
            Set<PackagePartName> mcRemove = new HashSet<>();
            for (PackagePart mcPart : mcParts) {
                String mcType = mcPart.getContentType().toLowerCase(Locale.ROOT);
                if (mcType.contains("vba") || mcType.contains("digital-signature")) mcRemove.add(mcPart.getPartName());
            }
            for (PackagePart mcPart : mcParts) {
                if (mcPart.isRelationshipPart() || mcRemove.contains(mcPart.getPartName())) continue;
                List<String> mcRelationships = new ArrayList<>();
                for (PackageRelationship mcRel : mcPart.getRelationships()) {
                    if (mcRel.getTargetMode() == TargetMode.INTERNAL
                            && mcRemove.contains(mcPart.getRelatedPart(mcRel).getPartName())) mcRelationships.add(mcRel.getId());
                }
                mcRelationships.forEach(mcPart::removeRelationship);
            }
            List<String> mcRootRelationships = new ArrayList<>();
            for (PackageRelationship mcRel : mcPkg.getRelationships()) {
                if (mcRel.getTargetMode() == TargetMode.INTERNAL && mcRemove.contains(mcPkg.getPart(mcRel).getPartName()))
                    mcRootRelationships.add(mcRel.getId());
            }
            mcRootRelationships.forEach(mcPkg::removeRelationship);
            for (PackagePartName mcName : mcRemove) {
                PackagePartName mcRelationships = PackagingURIHelper.getRelationshipPartName(mcName);
                if (mcPkg.containPart(mcRelationships)) mcPkg.removePart(mcRelationships);
                mcPkg.removePart(mcName);
            }
            mcPkg.save(mcOutput);
            byte[] mcResult = mcOutput.toByteArray();
            mcValidateDocx(mcResult);
            return mcResult;
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException mcE) {
            throw new IOException("El paquete de Word no es válido.", mcE);
        }
    }

    private static Path mcFindOffice() {
        String mcPath = System.getenv("PATH");
        if (mcPath != null) for (String mcCommand : List.of("soffice", "libreoffice", "soffice.exe", "libreoffice.exe")) {
            for (String mcDirectory : mcPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path mcExecutable = Path.of(mcDirectory, mcCommand);
                if (Files.isRegularFile(mcExecutable) && Files.isExecutable(mcExecutable)) return mcExecutable;
            }
        }
        for (String mcDirectory : List.of("C:/Program Files/LibreOffice/program", "C:/Program Files (x86)/LibreOffice/program",
                "/Applications/LibreOffice.app/Contents/MacOS")) {
            Path mcExecutable = Path.of(mcDirectory, mcDirectory.startsWith("C:") ? "soffice.exe" : "soffice");
            if (Files.isRegularFile(mcExecutable) && Files.isExecutable(mcExecutable)) return mcExecutable;
        }
        return null;
    }

    private static byte[] mcConvertWithLibreOffice(Path mcFile) throws IOException {
        Path mcExecutable = mcFindOffice();
        if (mcExecutable == null) return null;
        Path mcTemporary = Files.createTempDirectory("studio-word-import-");
        Process mcProcess = null;
        try {
            String mcExtension = mcFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dot") ? ".dot" : ".doc";
            Path mcInput = Files.copy(mcFile, mcTemporary.resolve("document" + mcExtension));
            Path mcOutput = Files.createDirectory(mcTemporary.resolve("output"));
            mcProcess = new ProcessBuilder(mcExecutable.toAbsolutePath().toString(),
                    "-env:UserInstallation=" + mcTemporary.resolve("profile").toUri(),
                    "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                    "--convert-to", "docx:Office Open XML Text", "--outdir", mcOutput.toString(), mcInput.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!mcProcess.waitFor(20, TimeUnit.SECONDS)) throw new IOException("LibreOffice excedió el límite de 20 segundos.");
            Path mcConverted = mcOutput.resolve("document.docx");
            if (mcProcess.exitValue() != 0 || !Files.isRegularFile(mcConverted) || Files.size(mcConverted) == 0)
                throw new IOException("LibreOffice no pudo convertir el documento de Word.");
            return Files.readAllBytes(mcConverted);
        } catch (InterruptedException mcE) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la importación de Word.", mcE);
        } finally {
            if (mcProcess != null && mcProcess.isAlive()) {
                mcProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                mcProcess.destroyForcibly();
                try { mcProcess.waitFor(1, TimeUnit.SECONDS); }
                catch (InterruptedException mcE) { Thread.currentThread().interrupt(); }
            }
            try (var mcPaths = Files.walk(mcTemporary)) {
                for (Path mcPath : mcPaths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(mcPath);
            }
        }
    }

    private static byte[] mcConvertWithHwpf(byte[] mcSource) throws IOException {
        try (HWPFDocument mcLegacy = new HWPFDocument(new ByteArrayInputStream(mcSource));
             XWPFDocument mcDocument = new XWPFDocument()) {
            Range mcRange = mcLegacy.getRange();
            if (mcRange.numSections() > 0) {
                var mcSection = mcRange.getSection(0);
                var mcProperties = mcDocument.getDocument().getBody().addNewSectPr();
                var mcSize = mcProperties.addNewPgSz();
                mcSize.setW(BigInteger.valueOf(mcSection.getPageWidth()));
                mcSize.setH(BigInteger.valueOf(mcSection.getPageHeight()));
                var mcMargins = mcProperties.addNewPgMar();
                mcMargins.setLeft(BigInteger.valueOf(mcSection.getMarginLeft()));
                mcMargins.setRight(BigInteger.valueOf(mcSection.getMarginRight()));
                mcMargins.setTop(BigInteger.valueOf(mcSection.getMarginTop()));
                mcMargins.setBottom(BigInteger.valueOf(mcSection.getMarginBottom()));
            }
            mcAppendRange(mcRange, mcDocument, 0);
            return MCDocumentEngine.mcBytes(mcDocument);
        }
    }

    private static void mcAppendRange(Range mcRange, IBody mcTarget, int mcParentTableLevel) {
        boolean mcFirstParagraph = true;
        for (int mcIndex = 0; mcIndex < mcRange.numParagraphs(); mcIndex++) {
            var mcParagraph = mcRange.getParagraph(mcIndex);
            if (mcParagraph.isInTable() && mcParagraph.getTableLevel() > mcParentTableLevel) {
                var mcSourceTable = mcRange.getTable(mcParagraph);
                XWPFTable mcTable;
                if (mcTarget instanceof XWPFDocument mcDocument) mcTable = mcDocument.createTable();
                else {
                    XWPFTableCell mcCell = (XWPFTableCell) mcTarget;
                    if (mcFirstParagraph) mcCell.removeParagraph(0);
                    try (var mcCursor = mcCell.getCTTc().newCursor()) {
                        mcCursor.toEndToken();
                        mcTable = mcCell.insertNewTbl(mcCursor);
                    }
                }
                mcCopyTable(mcSourceTable, mcTable);
                mcFirstParagraph = false;
                while (mcIndex + 1 < mcRange.numParagraphs()
                        && mcRange.getParagraph(mcIndex + 1).getStartOffset() < mcSourceTable.getEndOffset()) mcIndex++;
                continue;
            }
            if (mcParagraph.isTableRowEnd()) continue;
            XWPFParagraph mcOutput;
            if (mcTarget instanceof XWPFDocument mcDocument) mcOutput = mcDocument.createParagraph();
            else {
                XWPFTableCell mcCell = (XWPFTableCell) mcTarget;
                mcOutput = mcFirstParagraph ? mcCell.getParagraphs().get(0) : mcCell.addParagraph();
            }
            mcCopyParagraph(mcParagraph, mcOutput);
            mcFirstParagraph = false;
        }
    }

    private static void mcCopyTable(org.apache.poi.hwpf.usermodel.Table mcSource, XWPFTable mcTarget) {
        for (int mcRowIndex = 0; mcRowIndex < mcSource.numRows(); mcRowIndex++) {
            var mcSourceRow = mcSource.getRow(mcRowIndex);
            XWPFTableRow mcRow = mcRowIndex == 0 ? mcTarget.getRow(0) : mcTarget.createRow();
            if (mcRow == null) mcRow = mcTarget.createRow();
            while (mcRow.getTableCells().size() < mcSourceRow.numCells()) mcRow.addNewTableCell();
            while (mcRow.getTableCells().size() > mcSourceRow.numCells()) mcRow.removeCell(mcRow.getTableCells().size() - 1);
            for (int mcCellIndex = 0; mcCellIndex < mcSourceRow.numCells(); mcCellIndex++) {
                var mcSourceCell = mcSourceRow.getCell(mcCellIndex);
                XWPFTableCell mcCell = mcRow.getCell(mcCellIndex);
                if (mcSourceCell.getWidth() > 0) mcCell.setWidth(Integer.toString(mcSourceCell.getWidth()));
                mcAppendRange(mcSourceCell, mcCell, mcSource.getTableLevel());
            }
        }
    }

    private static void mcCopyParagraph(org.apache.poi.hwpf.usermodel.Paragraph mcSource, XWPFParagraph mcTarget) {
        mcTarget.setAlignment(switch (mcSource.getJustification()) {
            case 1 -> ParagraphAlignment.CENTER;
            case 2 -> ParagraphAlignment.RIGHT;
            case 3 -> ParagraphAlignment.BOTH;
            case 4 -> ParagraphAlignment.DISTRIBUTE;
            default -> ParagraphAlignment.LEFT;
        });
        mcTarget.setIndentationLeft(mcSource.getIndentFromLeft());
        mcTarget.setIndentationRight(mcSource.getIndentFromRight());
        mcTarget.setIndentationFirstLine(mcSource.getFirstLineIndent());
        mcTarget.setSpacingBefore(mcSource.getSpacingBefore());
        mcTarget.setSpacingAfter(mcSource.getSpacingAfter());
        for (int mcIndex = 0; mcIndex < mcSource.numCharacterRuns(); mcIndex++) {
            CharacterRun mcOriginal = mcSource.getCharacterRun(mcIndex);
            XWPFRun mcRun = mcTarget.createRun();
            mcRun.setBold(mcOriginal.isBold());
            mcRun.setItalic(mcOriginal.isItalic());
            String mcFont = mcOriginal.getFontName();
            if (mcFont != null && !mcFont.isBlank()) mcRun.setFontFamily(mcFont);
            if (mcOriginal.getFontSize() > 0) mcRun.setFontSize(mcOriginal.getFontSize() / 2.0);
            mcRun.setUnderline(switch (mcOriginal.getUnderlineCode()) {
                case 0 -> UnderlinePatterns.NONE;
                case 2 -> UnderlinePatterns.WORDS;
                case 3 -> UnderlinePatterns.DOUBLE;
                case 4 -> UnderlinePatterns.DOTTED;
                default -> UnderlinePatterns.SINGLE;
            });
            StringBuilder mcText = new StringBuilder();
            for (char mcCharacter : mcOriginal.text().toCharArray()) {
                if (mcCharacter == '\r' || mcCharacter == '\u0007') continue;
                if (mcCharacter == '\t' || mcCharacter == '\u000b' || mcCharacter == '\f') {
                    if (mcText.length() > 0) { mcRun.setText(mcText.toString()); mcText.setLength(0); }
                    if (mcCharacter == '\t') mcRun.addTab();
                    else mcRun.addBreak(mcCharacter == '\f' ? BreakType.PAGE : BreakType.TEXT_WRAPPING);
                } else if (mcCharacter >= ' ' || mcCharacter == '\n') mcText.append(mcCharacter);
            }
            if (mcText.length() > 0) mcRun.setText(mcText.toString());
        }
    }
}
