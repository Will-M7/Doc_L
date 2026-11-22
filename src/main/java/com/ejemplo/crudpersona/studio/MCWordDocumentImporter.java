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

public final class MCWordDocumentImporter {
    private static final Set<String> MCEXTENSIONS = Set.of("doc", "docx", "docm", "dot", "dotx", "dotm");
    private static final String MCDOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final Set<String> MCWORD_TYPES = Set.of(MCDOCX_TYPE,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
            "application/vnd.ms-word.document.macroEnabled.main+xml",
            "application/vnd.ms-word.template.macroEnabledTemplate.main+xml");
    private static final String MCCORE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String MCFORMATS = "Solo se admiten documentos de Word (.doc, .docx, .docm, .dot, .dotx, .dotm).";

    private MCWordDocumentImporter() {}

    public static byte[] MCimportToDocx(Path MCfile) throws IOException {
        return MCimportToDocx(MCfile, MCWordDocumentImporter::MCconvertWithLibreOffice);
    }

    public static byte[] MCimportToDocx(byte[] MCcontent,String MCfileName)throws IOException{
        String MCname=Path.of(MCfileName).getFileName().toString();int MCdot=MCname.lastIndexOf('.');
        if(MCdot<0||!MCEXTENSIONS.contains(MCname.substring(MCdot+1).toLowerCase(Locale.ROOT)))throw new IllegalArgumentException(MCFORMATS);
        Path MCtemporary=Files.createTempFile("studio-client-word-",MCname.substring(MCdot).toLowerCase(Locale.ROOT));
        try{Files.write(MCtemporary,MCcontent);return MCimportToDocx(MCtemporary);}finally{Files.deleteIfExists(MCtemporary);}
    }

    @FunctionalInterface
    interface MCOfficeConverter { byte[] MCconvert(Path MCfile) throws IOException; }

    static byte[] MCimportToDocx(Path MCfile, MCOfficeConverter MCconverter) throws IOException {
        if (MCfile == null || MCfile.getFileName() == null) throw new IllegalArgumentException(MCFORMATS);
        String MCname = MCfile.getFileName().toString();
        int MCdot = MCname.lastIndexOf('.');
        if (MCdot < 0) throw new IllegalArgumentException(MCFORMATS);
        String MCextension = MCname.substring(MCdot + 1).toLowerCase(Locale.ROOT);
        if (!MCEXTENSIONS.contains(MCextension)) throw new IllegalArgumentException(MCFORMATS);
        if (!Files.isRegularFile(MCfile) || Files.size(MCfile) == 0)
            throw new IOException("El documento de Word está vacío o no se puede leer. " + MCFORMATS);
        try {
            byte[] MCsource = Files.readAllBytes(MCfile);
            if (MCextension.equals("docx")) {
                MCvalidateDocx(MCsource);
                return MCsource;
            }
            if (!MCextension.equals("doc") && !MCextension.equals("dot")) return MCnormalizeOoxml(MCsource);
            try (POIFSFileSystem MCole = new POIFSFileSystem(new ByteArrayInputStream(MCsource))) {
                if (!MCole.getRoot().hasEntry("WordDocument")
                        || !(MCole.getRoot().hasEntry("0Table") || MCole.getRoot().hasEntry("1Table")))
                    throw new IOException("El archivo no contiene un documento binario de Word.");
            }
            IOException MCconversionFailure = null;
            try {
                byte[] MCconverted = MCconverter.MCconvert(MCfile);
                if (MCconverted != null) {
                    MCvalidateDocx(MCconverted);
                    return MCconverted;
                }
            } catch (IOException MCe) {
                if (Thread.currentThread().isInterrupted()) throw MCe;
                MCconversionFailure = MCe;
            }
            try {
                return MCconvertWithHwpf(MCsource);
            } catch (IOException | RuntimeException MCe) {
                if (MCconversionFailure != null) MCe.addSuppressed(MCconversionFailure);
                throw MCe;
            }
        } catch (IOException | RuntimeException MCe) {
            throw new IOException("No se pudo importar el documento de Word: está dañado, protegido o no es legible. " + MCFORMATS, MCe);
        }
    }

    private static void MCvalidateDocx(byte[] MCbytes) throws IOException {
        try (XWPFDocument MCdocument = new XWPFDocument(new ByteArrayInputStream(MCbytes))) {
            if (!MCDOCX_TYPE.equals(MCdocument.getPackagePart().getContentType()) || MCdocument.getDocument().getBody() == null)
                throw new IOException("El paquete no contiene un documento DOCX estándar.");
        }
    }

    private static byte[] MCnormalizeOoxml(byte[] MCsource) throws IOException {
        try (OPCPackage MCpkg = OPCPackage.open(new ByteArrayInputStream(MCsource));
             ByteArrayOutputStream MCoutput = new ByteArrayOutputStream()) {
            PackageRelationshipCollection MCroots = MCpkg.getRelationshipsByType(MCCORE_REL);
            if (MCroots.size() != 1) throw new IOException("No se encontró el documento principal de Word.");
            PackagePart MCmain = MCpkg.getPart(MCroots.getRelationship(0));
            if (MCmain == null || !MCWORD_TYPES.contains(MCmain.getContentType()))
                throw new IOException("El paquete no es un documento de Word.");
            MCmain.setContentType(MCDOCX_TYPE);
            List<PackagePart> MCparts = new ArrayList<>(MCpkg.getParts());
            Set<PackagePartName> MCremove = new HashSet<>();
            for (PackagePart MCpart : MCparts) {
                String MCtype = MCpart.getContentType().toLowerCase(Locale.ROOT);
                if (MCtype.contains("vba") || MCtype.contains("digital-signature")) MCremove.add(MCpart.getPartName());
            }
            for (PackagePart MCpart : MCparts) {
                if (MCpart.isRelationshipPart() || MCremove.contains(MCpart.getPartName())) continue;
                List<String> MCrelationships = new ArrayList<>();
                for (PackageRelationship MCrel : MCpart.getRelationships()) {
                    if (MCrel.getTargetMode() == TargetMode.INTERNAL
                            && MCremove.contains(MCpart.getRelatedPart(MCrel).getPartName())) MCrelationships.add(MCrel.getId());
                }
                MCrelationships.forEach(MCpart::removeRelationship);
            }
            List<String> MCrootRelationships = new ArrayList<>();
            for (PackageRelationship MCrel : MCpkg.getRelationships()) {
                if (MCrel.getTargetMode() == TargetMode.INTERNAL && MCremove.contains(MCpkg.getPart(MCrel).getPartName()))
                    MCrootRelationships.add(MCrel.getId());
            }
            MCrootRelationships.forEach(MCpkg::removeRelationship);
            for (PackagePartName MCname : MCremove) {
                PackagePartName MCrelationships = PackagingURIHelper.getRelationshipPartName(MCname);
                if (MCpkg.containPart(MCrelationships)) MCpkg.removePart(MCrelationships);
                MCpkg.removePart(MCname);
            }
            MCpkg.save(MCoutput);
            byte[] MCresult = MCoutput.toByteArray();
            MCvalidateDocx(MCresult);
            return MCresult;
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException MCe) {
            throw new IOException("El paquete de Word no es válido.", MCe);
        }
    }

    private static Path MCfindOffice() {
        String MCpath = System.getenv("PATH");
        if (MCpath != null) for (String MCcommand : List.of("soffice", "libreoffice", "soffice.exe", "libreoffice.exe")) {
            for (String MCdirectory : MCpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path MCexecutable = Path.of(MCdirectory, MCcommand);
                if (Files.isRegularFile(MCexecutable) && Files.isExecutable(MCexecutable)) return MCexecutable;
            }
        }
        for (String MCdirectory : List.of("C:/Program Files/LibreOffice/program", "C:/Program Files (x86)/LibreOffice/program",
                "/Applications/LibreOffice.app/Contents/MacOS")) {
            Path MCexecutable = Path.of(MCdirectory, MCdirectory.startsWith("C:") ? "soffice.exe" : "soffice");
            if (Files.isRegularFile(MCexecutable) && Files.isExecutable(MCexecutable)) return MCexecutable;
        }
        return null;
    }

    private static byte[] MCconvertWithLibreOffice(Path MCfile) throws IOException {
        Path MCexecutable = MCfindOffice();
        if (MCexecutable == null) return null;
        Path MCtemporary = Files.createTempDirectory("studio-word-import-");
        Process MCprocess = null;
        try {
            String MCextension = MCfile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dot") ? ".dot" : ".doc";
            Path MCinput = Files.copy(MCfile, MCtemporary.resolve("document" + MCextension));
            Path MCoutput = Files.createDirectory(MCtemporary.resolve("output"));
            MCprocess = new ProcessBuilder(MCexecutable.toAbsolutePath().toString(),
                    "-env:UserInstallation=" + MCtemporary.resolve("profile").toUri(),
                    "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                    "--convert-to", "docx:Office Open XML Text", "--outdir", MCoutput.toString(), MCinput.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!MCprocess.waitFor(20, TimeUnit.SECONDS)) throw new IOException("LibreOffice excedió el límite de 20 segundos.");
            Path MCconverted = MCoutput.resolve("document.docx");
            if (MCprocess.exitValue() != 0 || !Files.isRegularFile(MCconverted) || Files.size(MCconverted) == 0)
                throw new IOException("LibreOffice no pudo convertir el documento de Word.");
            return Files.readAllBytes(MCconverted);
        } catch (InterruptedException MCe) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la importación de Word.", MCe);
        } finally {
            if (MCprocess != null && MCprocess.isAlive()) {
                MCprocess.descendants().forEach(ProcessHandle::destroyForcibly);
                MCprocess.destroyForcibly();
                try { MCprocess.waitFor(1, TimeUnit.SECONDS); }
                catch (InterruptedException MCe) { Thread.currentThread().interrupt(); }
            }
            try (var MCpaths = Files.walk(MCtemporary)) {
                for (Path MCpath : MCpaths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(MCpath);
            }
        }
    }

    private static byte[] MCconvertWithHwpf(byte[] MCsource) throws IOException {
        try (HWPFDocument MClegacy = new HWPFDocument(new ByteArrayInputStream(MCsource));
             XWPFDocument MCdocument = new XWPFDocument()) {
            Range MCrange = MClegacy.getRange();
            if (MCrange.numSections() > 0) {
                var MCsection = MCrange.getSection(0);
                var MCproperties = MCdocument.getDocument().getBody().addNewSectPr();
                var MCsize = MCproperties.addNewPgSz();
                MCsize.setW(BigInteger.valueOf(MCsection.getPageWidth()));
                MCsize.setH(BigInteger.valueOf(MCsection.getPageHeight()));
                var MCmargins = MCproperties.addNewPgMar();
                MCmargins.setLeft(BigInteger.valueOf(MCsection.getMarginLeft()));
                MCmargins.setRight(BigInteger.valueOf(MCsection.getMarginRight()));
                MCmargins.setTop(BigInteger.valueOf(MCsection.getMarginTop()));
                MCmargins.setBottom(BigInteger.valueOf(MCsection.getMarginBottom()));
            }
            MCappendRange(MCrange, MCdocument, 0);
            return MCDocumentEngine.MCbytes(MCdocument);
        }
    }

    private static void MCappendRange(Range MCrange, IBody MCtarget, int MCparentTableLevel) {
        boolean MCfirstParagraph = true;
        for (int MCindex = 0; MCindex < MCrange.numParagraphs(); MCindex++) {
            var MCparagraph = MCrange.getParagraph(MCindex);
            if (MCparagraph.isInTable() && MCparagraph.getTableLevel() > MCparentTableLevel) {
                var MCsourceTable = MCrange.getTable(MCparagraph);
                XWPFTable MCtable;
                if (MCtarget instanceof XWPFDocument MCdocument) MCtable = MCdocument.createTable();
                else {
                    XWPFTableCell MCcell = (XWPFTableCell) MCtarget;
                    if (MCfirstParagraph) MCcell.removeParagraph(0);
                    try (var MCcursor = MCcell.getCTTc().newCursor()) {
                        MCcursor.toEndToken();
                        MCtable = MCcell.insertNewTbl(MCcursor);
                    }
                }
                MCcopyTable(MCsourceTable, MCtable);
                MCfirstParagraph = false;
                while (MCindex + 1 < MCrange.numParagraphs()
                        && MCrange.getParagraph(MCindex + 1).getStartOffset() < MCsourceTable.getEndOffset()) MCindex++;
                continue;
            }
            if (MCparagraph.isTableRowEnd()) continue;
            XWPFParagraph MCoutput;
            if (MCtarget instanceof XWPFDocument MCdocument) MCoutput = MCdocument.createParagraph();
            else {
                XWPFTableCell MCcell = (XWPFTableCell) MCtarget;
                MCoutput = MCfirstParagraph ? MCcell.getParagraphs().get(0) : MCcell.addParagraph();
            }
            MCcopyParagraph(MCparagraph, MCoutput);
            MCfirstParagraph = false;
        }
    }

    private static void MCcopyTable(org.apache.poi.hwpf.usermodel.Table MCsource, XWPFTable MCtarget) {
        for (int MCrowIndex = 0; MCrowIndex < MCsource.numRows(); MCrowIndex++) {
            var MCsourceRow = MCsource.getRow(MCrowIndex);
            XWPFTableRow MCrow = MCrowIndex == 0 ? MCtarget.getRow(0) : MCtarget.createRow();
            if (MCrow == null) MCrow = MCtarget.createRow();
            while (MCrow.getTableCells().size() < MCsourceRow.numCells()) MCrow.addNewTableCell();
            while (MCrow.getTableCells().size() > MCsourceRow.numCells()) MCrow.removeCell(MCrow.getTableCells().size() - 1);
            for (int MCcellIndex = 0; MCcellIndex < MCsourceRow.numCells(); MCcellIndex++) {
                var MCsourceCell = MCsourceRow.getCell(MCcellIndex);
                XWPFTableCell MCcell = MCrow.getCell(MCcellIndex);
                if (MCsourceCell.getWidth() > 0) MCcell.setWidth(Integer.toString(MCsourceCell.getWidth()));
                MCappendRange(MCsourceCell, MCcell, MCsource.getTableLevel());
            }
        }
    }

    private static void MCcopyParagraph(org.apache.poi.hwpf.usermodel.Paragraph MCsource, XWPFParagraph MCtarget) {
        MCtarget.setAlignment(switch (MCsource.getJustification()) {
            case 1 -> ParagraphAlignment.CENTER;
            case 2 -> ParagraphAlignment.RIGHT;
            case 3 -> ParagraphAlignment.BOTH;
            case 4 -> ParagraphAlignment.DISTRIBUTE;
            default -> ParagraphAlignment.LEFT;
        });
        MCtarget.setIndentationLeft(MCsource.getIndentFromLeft());
        MCtarget.setIndentationRight(MCsource.getIndentFromRight());
        MCtarget.setIndentationFirstLine(MCsource.getFirstLineIndent());
        MCtarget.setSpacingBefore(MCsource.getSpacingBefore());
        MCtarget.setSpacingAfter(MCsource.getSpacingAfter());
        for (int MCindex = 0; MCindex < MCsource.numCharacterRuns(); MCindex++) {
            CharacterRun MCoriginal = MCsource.getCharacterRun(MCindex);
            XWPFRun MCrun = MCtarget.createRun();
            MCrun.setBold(MCoriginal.isBold());
            MCrun.setItalic(MCoriginal.isItalic());
            String MCfont = MCoriginal.getFontName();
            if (MCfont != null && !MCfont.isBlank()) MCrun.setFontFamily(MCfont);
            if (MCoriginal.getFontSize() > 0) MCrun.setFontSize(MCoriginal.getFontSize() / 2.0);
            MCrun.setUnderline(switch (MCoriginal.getUnderlineCode()) {
                case 0 -> UnderlinePatterns.NONE;
                case 2 -> UnderlinePatterns.WORDS;
                case 3 -> UnderlinePatterns.DOUBLE;
                case 4 -> UnderlinePatterns.DOTTED;
                default -> UnderlinePatterns.SINGLE;
            });
            StringBuilder MCtext = new StringBuilder();
            for (char MCcharacter : MCoriginal.text().toCharArray()) {
                if (MCcharacter == '\r' || MCcharacter == '\u0007') continue;
                if (MCcharacter == '\t' || MCcharacter == '\u000b' || MCcharacter == '\f') {
                    if (MCtext.length() > 0) { MCrun.setText(MCtext.toString()); MCtext.setLength(0); }
                    if (MCcharacter == '\t') MCrun.addTab();
                    else MCrun.addBreak(MCcharacter == '\f' ? BreakType.PAGE : BreakType.TEXT_WRAPPING);
                } else if (MCcharacter >= ' ' || MCcharacter == '\n') MCtext.append(MCcharacter);
            }
            if (MCtext.length() > 0) MCrun.setText(MCtext.toString());
        }
    }
}
