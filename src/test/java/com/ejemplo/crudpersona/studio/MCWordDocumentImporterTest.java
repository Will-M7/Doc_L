package com.ejemplo.crudpersona.studio;

import org.apache.poi.openxml4j.opc.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;

class MCWordDocumentImporterTest {
    private static final String MCDOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    @TempDir Path MCdirectory;

    @Test void MCstandardDocxIsValidatedWithoutChangingItsBytes() throws Exception {
        byte[] MCoriginal = MCstandardDocument();
        for (String MCextension : List.of("docx", "DOCX", "DoCx")) {
            Path MCfile = Files.write(MCdirectory.resolve("contrato." + MCextension), MCoriginal);
            byte[] MCimported = MCWordDocumentImporter.MCimportToDocx(MCfile);
            assertThat(MCimported).isEqualTo(MCoriginal);
            MCassertEditableAndGeneratable(MCimported);
        }
    }

    @Test void MCtemplatesAndMacroDocumentsBecomeStandardEditableDocxWithoutVba() throws Exception {
        Map<String, String> MCtypes = Map.of(
                "dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
                "docm", "application/vnd.ms-word.document.macroEnabled.main+xml",
                "dotm", "application/vnd.ms-word.template.macroEnabledTemplate.main+xml");
        for (var MCentry : MCtypes.entrySet()) {
            byte[] MCsource = MCooxmlVariant(MCentry.getValue(), !MCentry.getKey().equals("dotx"));
            for (String MCextension : List.of(MCentry.getKey(), MCentry.getKey().toUpperCase(Locale.ROOT))) {
                Path MCfile = Files.write(MCdirectory.resolve("modelo." + MCextension), MCsource);
                byte[] MCimported = MCWordDocumentImporter.MCimportToDocx(MCfile);
                assertThat(Files.readAllBytes(MCfile)).isEqualTo(MCsource);
                MCassertEditableAndGeneratable(MCimported);
                try (ZipInputStream MCzip = new ZipInputStream(new ByteArrayInputStream(MCimported))) {
                    java.util.zip.ZipEntry MCpart;
                    while ((MCpart = MCzip.getNextEntry()) != null)
                        assertThat(MCpart.getName()).doesNotContain("vbaProject", "vbaData", "_xmlsignatures");
                }
                try (XWPFDocument MCdocument = MCDocumentEngine.MCopen(MCimported)) {
                    assertThat(MCdocument.getPackagePart().getContentType()).isEqualTo(MCDOCX_TYPE);
                    assertThat(MCdocument.getHeaderList().get(0).getText()).contains("Encabezado conservado");
                    assertThat(MCdocument.getPackage().getParts()).noneMatch(MCpart ->
                            MCpart.getContentType().toLowerCase(Locale.ROOT).contains("vba")
                                    || MCpart.getContentType().contains("digital-signature"));
                    for (PackageRelationship MCrelationship : MCdocument.getPackagePart().getRelationships())
                        assertThat(MCrelationship.getRelationshipType()).doesNotContain("vba");
                }
            }
        }
    }

    @Test void MCbinaryDocAndDotImportThroughTheAutomaticConverter() throws Exception {
        for (String MCextension : List.of("doc", "DOC", "dot", "DOT")) {
            Path MCfile = MClegacyFile(MCextension);
            byte[] MCoriginal = Files.readAllBytes(MCfile);
            MCassertLegacyContent(MCWordDocumentImporter.MCimportToDocx(MCfile));
            assertThat(Files.readAllBytes(MCfile)).isEqualTo(MCoriginal);
        }
    }

    @Test void MChwpfFallbackPreservesTextTablesVariablesAlignmentAndFontStyles() throws Exception {
        byte[] MCconverted = MCWordDocumentImporter.MCimportToDocx(MClegacyFile("doc"), MCfile -> null);
        MCassertLegacyContent(MCconverted);
        try (XWPFDocument MCdocument = MCDocumentEngine.MCopen(MCconverted)) {
            assertThat(MCdocument.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly(
                    "Contrato {{nombre}}", "  Texto con espacios  ", "", "Firma final", "Texto justificado");
            XWPFParagraph MCtitle = MCdocument.getParagraphs().get(0);
            assertThat(MCtitle.getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            XWPFRun MCtitleRun = MCtitle.getRuns().get(0);
            assertThat(MCtitleRun.isBold()).isTrue();
            assertThat(MCtitleRun.getFontFamily()).isEqualTo("Times New Roman");
            assertThat(MCtitleRun.getFontSizeAsDouble()).isEqualTo(14.0);
            XWPFRun MCemphasis = MCdocument.getParagraphs().get(1).getRuns().get(0);
            assertThat(MCemphasis.isItalic()).isTrue();
            assertThat(MCemphasis.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);
            assertThat(MCemphasis.getFontSizeAsDouble()).isEqualTo(12.5);
            assertThat(MCdocument.getParagraphs().get(3).getAlignment()).isEqualTo(ParagraphAlignment.RIGHT);
            assertThat(MCdocument.getParagraphs().get(4).getAlignment()).isEqualTo(ParagraphAlignment.BOTH);
            assertThat(MCdocument.getBodyElements()).extracting(IBodyElement::getElementType).containsExactly(
                    BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH,
                    BodyElementType.TABLE, BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH);
            assertThat(MCdocument.getTables().get(0).getRow(0).getCell(0).getParagraphs()).hasSize(1);
        }
    }

    @Test void MCaFailedExternalConversionAlsoFallsBackToOfflineHwpf() throws Exception {
        AtomicBoolean MCcalled = new AtomicBoolean();
        byte[] MCimported = MCWordDocumentImporter.MCimportToDocx(MClegacyFile("DOT"), MCfile -> {
            MCcalled.set(true);
            throw new IOException("Conversor no disponible");
        });
        assertThat(MCcalled).isTrue();
        MCassertLegacyContent(MCimported);
    }

    @Test void MCsuccessfulOfficeConversionIsPreferredOverHwpf() throws Exception {
        byte[] MCofficeOutput = MCstandardDocument();
        byte[] MCimported = MCWordDocumentImporter.MCimportToDocx(MClegacyFile("doc"), MCfile -> MCofficeOutput);
        assertThat(MCimported).isEqualTo(MCofficeOutput);
        MCassertEditableAndGeneratable(MCimported);
    }

    @Test void MCunsupportedExtensionsAreRejectedEvenIfTheContentIsWord() throws Exception {
        Path MCnoExtension = Files.write(MCdirectory.resolve("docx"), MCstandardDocument());
        assertThatThrownBy(() -> MCWordDocumentImporter.MCimportToDocx(MCnoExtension))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Solo se admiten documentos de Word");
        for (String MCextension : List.of("txt", "pdf", "rtf", "odt", "zip")) {
            Path MCfile = Files.write(MCdirectory.resolve("archivo." + MCextension), MCstandardDocument());
            assertThatThrownBy(() -> MCWordDocumentImporter.MCimportToDocx(MCfile))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Solo se admiten documentos de Word");
        }
    }

    @Test void MCemptyCorruptAndDisguisedFilesAreRejectedClearlyWithoutRunningOffice() throws Exception {
        for (String MCextension : List.of("doc", "docx", "docm", "dot", "dotx", "dotm")) {
            Path MCfile = Files.write(MCdirectory.resolve("vacio." + MCextension), new byte[0]);
            assertThatThrownBy(() -> MCWordDocumentImporter.MCimportToDocx(MCfile))
                    .isInstanceOf(IOException.class).hasMessageContaining("vacío").hasMessageContaining("Word");
            Files.writeString(MCfile, "%PDF-1.4 archivo falso");
            assertThatThrownBy(() -> MCWordDocumentImporter.MCimportToDocx(MCfile, MCignored -> {
                fail("Un archivo inválido no debe enviarse al conversor externo");
                return null;
            })).isInstanceOf(IOException.class).hasMessageContaining("dañado").hasMessageContaining("Word");
        }
    }

    private Path MClegacyFile(String MCextension) throws IOException {
        try (InputStream MCinput = getClass().getResourceAsStream("legacy-word.doc.bin")) {
            assertThat(MCinput).isNotNull();
            return Files.write(MCdirectory.resolve("binario." + MCextension), MCinput.readAllBytes());
        }
    }

    private static void MCassertLegacyContent(byte[] MCbytes) throws Exception {
        try (XWPFDocument MCdocument = MCDocumentEngine.MCopen(MCbytes)) {
            assertThat(MCdocument.getPackagePart().getContentType()).isEqualTo(MCDOCX_TYPE);
            assertThat(MCdocument.getParagraphs()).extracting(XWPFParagraph::getText)
                    .contains("Contrato {{nombre}}", "Firma final", "Texto justificado");
            assertThat(MCdocument.getTables()).hasSize(1);
            XWPFTable MCtable = MCdocument.getTables().get(0);
            assertThat(MCtable.getRows()).hasSize(2);
            assertThat(MCtable.getRow(0).getTableCells()).hasSize(2);
            assertThat(MCtable.getRow(0).getCell(0).getText()).isEqualTo("Producto");
            assertThat(MCtable.getRow(0).getCell(1).getText()).isEqualTo("{{Persona.precio}}");
            assertThat(MCtable.getRow(1).getCell(0).getText()).isEqualTo("Silla");
            assertThat(MCtable.getRow(1).getCell(1).getText()).isEqualTo("120.00");
            assertThat(MCDocumentEngine.MCmarkers(MCdocument)).containsExactlyInAnyOrder("nombre", "Persona.precio");
        }
        try (XWPFDocument MCgenerated = MCDocumentEngine.MCopen(MCDocumentEngine.MCgenerate(MCbytes,
                Map.of("nombre", "Ana", "Persona.precio", "150.00")))) {
            assertThat(MCDocumentEngine.MCmarkers(MCgenerated)).isEmpty();
            assertThat(MCgenerated.getParagraphs().get(0).getText()).isEqualTo("Contrato Ana");
            assertThat(MCgenerated.getTables().get(0).getRow(0).getCell(1).getText()).isEqualTo("150.00");
        }
    }

    private static byte[] MCstandardDocument() throws IOException {
        try (XWPFDocument MCdocument = new XWPFDocument()) {
            MCdocument.createParagraph().createRun().setText("Hola {{nombre}}");
            MCdocument.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Encabezado conservado");
            MCdocument.createTable(1, 1).getRow(0).getCell(0).setText("{{Persona.precio}}");
            return MCDocumentEngine.MCbytes(MCdocument);
        }
    }

    private static byte[] MCooxmlVariant(String MCcontentType, boolean MCmacros) throws Exception {
        try (XWPFDocument MCdocument = MCDocumentEngine.MCopen(MCstandardDocument());
             ByteArrayOutputStream MCoutput = new ByteArrayOutputStream()) {
            MCdocument.getPackagePart().setContentType(MCcontentType);
            if (MCmacros) {
                PackagePart MCvba = MCdocument.getPackage().createPart(PackagingURIHelper.createPartName("/word/vbaProject.bin"),
                        "application/vnd.ms-office.vbaProject");
                try (OutputStream MCstream = MCvba.getOutputStream()) { MCstream.write(new byte[]{1, 2, 3}); }
                MCdocument.getPackagePart().addRelationship(MCvba.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.microsoft.com/office/2006/relationships/vbaProject");
                PackagePart MCdata = MCdocument.getPackage().createPart(PackagingURIHelper.createPartName("/word/vbaData.xml"),
                        "application/vnd.ms-word.vbaData+xml");
                try (OutputStream MCstream = MCdata.getOutputStream()) { MCstream.write("<vba/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                MCvba.addRelationship(MCdata.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.microsoft.com/office/2006/relationships/wordVbaData");
                PackagePart MCsignature = MCdocument.getPackage().createPart(
                        PackagingURIHelper.createPartName("/_xmlsignatures/origin.sigs"),
                        "application/vnd.openxmlformats-package.digital-signature-origin");
                MCdocument.getPackage().addRelationship(MCsignature.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.openxmlformats.org/package/2006/relationships/digital-signature/origin");
            }
            MCdocument.getPackage().save(MCoutput);
            return MCoutput.toByteArray();
        }
    }

    private static void MCassertEditableAndGeneratable(byte[] MCbytes) throws Exception {
        try (XWPFDocument MCdocument = MCDocumentEngine.MCopen(MCbytes)) {
            MCdocument.getParagraphs().get(0).createRun().setText(" editable");
            byte[] MCgenerated = MCDocumentEngine.MCgenerate(MCDocumentEngine.MCbytes(MCdocument),
                    Map.of("nombre", "Luis", "Persona.precio", "20"));
            try (XWPFDocument MCresult = MCDocumentEngine.MCopen(MCgenerated)) {
                assertThat(MCresult.getParagraphs().get(0).getText()).isEqualTo("Hola Luis editable");
                assertThat(MCresult.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo("20");
            }
        }
    }
}
