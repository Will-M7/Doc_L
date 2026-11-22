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

/** @author MC */
class MCWordDocumentImporterTest {
    private static final String mcDOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    @TempDir Path mcDirectory;

    @Test void mcStandardDocxIsValidatedWithoutChangingItsBytes() throws Exception {
        byte[] mcOriginal = mcStandardDocument();
        for (String mcExtension : List.of("docx", "DOCX", "DoCx")) {
            Path mcFile = Files.write(mcDirectory.resolve("contrato." + mcExtension), mcOriginal);
            byte[] mcImported = MCWordDocumentImporter.mcImportToDocx(mcFile);
            assertThat(mcImported).isEqualTo(mcOriginal);
            mcAssertEditableAndGeneratable(mcImported);
        }
    }

    @Test void mcTemplatesAndMacroDocumentsBecomeStandardEditableDocxWithoutVba() throws Exception {
        Map<String, String> mcTypes = Map.of(
                "dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
                "docm", "application/vnd.ms-word.document.macroEnabled.main+xml",
                "dotm", "application/vnd.ms-word.template.macroEnabledTemplate.main+xml");
        for (var mcEntry : mcTypes.entrySet()) {
            byte[] mcSource = mcOoxmlVariant(mcEntry.getValue(), !mcEntry.getKey().equals("dotx"));
            for (String mcExtension : List.of(mcEntry.getKey(), mcEntry.getKey().toUpperCase(Locale.ROOT))) {
                Path mcFile = Files.write(mcDirectory.resolve("modelo." + mcExtension), mcSource);
                byte[] mcImported = MCWordDocumentImporter.mcImportToDocx(mcFile);
                assertThat(Files.readAllBytes(mcFile)).isEqualTo(mcSource);
                mcAssertEditableAndGeneratable(mcImported);
                try (ZipInputStream mcZip = new ZipInputStream(new ByteArrayInputStream(mcImported))) {
                    java.util.zip.ZipEntry mcPart;
                    while ((mcPart = mcZip.getNextEntry()) != null)
                        assertThat(mcPart.getName()).doesNotContain("vbaProject", "vbaData", "_xmlsignatures");
                }
                try (XWPFDocument mcDocument = MCDocumentEngine.mcOpen(mcImported)) {
                    assertThat(mcDocument.getPackagePart().getContentType()).isEqualTo(mcDOCX_TYPE);
                    assertThat(mcDocument.getHeaderList().get(0).getText()).contains("Encabezado conservado");
                    assertThat(mcDocument.getPackage().getParts()).noneMatch(mcPart ->
                            mcPart.getContentType().toLowerCase(Locale.ROOT).contains("vba")
                                    || mcPart.getContentType().contains("digital-signature"));
                    for (PackageRelationship mcRelationship : mcDocument.getPackagePart().getRelationships())
                        assertThat(mcRelationship.getRelationshipType()).doesNotContain("vba");
                }
            }
        }
    }

    @Test void mcBinaryDocAndDotImportThroughTheAutomaticConverter() throws Exception {
        for (String mcExtension : List.of("doc", "DOC", "dot", "DOT")) {
            Path mcFile = mcLegacyFile(mcExtension);
            byte[] mcOriginal = Files.readAllBytes(mcFile);
            mcAssertLegacyContent(MCWordDocumentImporter.mcImportToDocx(mcFile));
            assertThat(Files.readAllBytes(mcFile)).isEqualTo(mcOriginal);
        }
    }

    @Test void mcHwpfFallbackPreservesTextTablesVariablesAlignmentAndFontStyles() throws Exception {
        byte[] mcConverted = MCWordDocumentImporter.mcImportToDocx(mcLegacyFile("doc"), mcFile -> null);
        mcAssertLegacyContent(mcConverted);
        try (XWPFDocument mcDocument = MCDocumentEngine.mcOpen(mcConverted)) {
            assertThat(mcDocument.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly(
                    "Contrato {{nombre}}", "  Texto con espacios  ", "", "Firma final", "Texto justificado");
            XWPFParagraph mcTitle = mcDocument.getParagraphs().get(0);
            assertThat(mcTitle.getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            XWPFRun mcTitleRun = mcTitle.getRuns().get(0);
            assertThat(mcTitleRun.isBold()).isTrue();
            assertThat(mcTitleRun.getFontFamily()).isEqualTo("Times New Roman");
            assertThat(mcTitleRun.getFontSizeAsDouble()).isEqualTo(14.0);
            XWPFRun mcEmphasis = mcDocument.getParagraphs().get(1).getRuns().get(0);
            assertThat(mcEmphasis.isItalic()).isTrue();
            assertThat(mcEmphasis.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);
            assertThat(mcEmphasis.getFontSizeAsDouble()).isEqualTo(12.5);
            assertThat(mcDocument.getParagraphs().get(3).getAlignment()).isEqualTo(ParagraphAlignment.RIGHT);
            assertThat(mcDocument.getParagraphs().get(4).getAlignment()).isEqualTo(ParagraphAlignment.BOTH);
            assertThat(mcDocument.getBodyElements()).extracting(IBodyElement::getElementType).containsExactly(
                    BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH,
                    BodyElementType.TABLE, BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH);
            assertThat(mcDocument.getTables().get(0).getRow(0).getCell(0).getParagraphs()).hasSize(1);
        }
    }

    @Test void mcAFailedExternalConversionAlsoFallsBackToOfflineHwpf() throws Exception {
        AtomicBoolean mcCalled = new AtomicBoolean();
        byte[] mcImported = MCWordDocumentImporter.mcImportToDocx(mcLegacyFile("DOT"), mcFile -> {
            mcCalled.set(true);
            throw new IOException("Conversor no disponible");
        });
        assertThat(mcCalled).isTrue();
        mcAssertLegacyContent(mcImported);
    }

    @Test void mcSuccessfulOfficeConversionIsPreferredOverHwpf() throws Exception {
        byte[] mcOfficeOutput = mcStandardDocument();
        byte[] mcImported = MCWordDocumentImporter.mcImportToDocx(mcLegacyFile("doc"), mcFile -> mcOfficeOutput);
        assertThat(mcImported).isEqualTo(mcOfficeOutput);
        mcAssertEditableAndGeneratable(mcImported);
    }

    @Test void mcUnsupportedExtensionsAreRejectedEvenIfTheContentIsWord() throws Exception {
        Path mcNoExtension = Files.write(mcDirectory.resolve("docx"), mcStandardDocument());
        assertThatThrownBy(() -> MCWordDocumentImporter.mcImportToDocx(mcNoExtension))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Solo se admiten documentos de Word");
        for (String mcExtension : List.of("txt", "pdf", "rtf", "odt", "zip")) {
            Path mcFile = Files.write(mcDirectory.resolve("archivo." + mcExtension), mcStandardDocument());
            assertThatThrownBy(() -> MCWordDocumentImporter.mcImportToDocx(mcFile))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Solo se admiten documentos de Word");
        }
    }

    @Test void mcEmptyCorruptAndDisguisedFilesAreRejectedClearlyWithoutRunningOffice() throws Exception {
        for (String mcExtension : List.of("doc", "docx", "docm", "dot", "dotx", "dotm")) {
            Path mcFile = Files.write(mcDirectory.resolve("vacio." + mcExtension), new byte[0]);
            assertThatThrownBy(() -> MCWordDocumentImporter.mcImportToDocx(mcFile))
                    .isInstanceOf(IOException.class).hasMessageContaining("vacío").hasMessageContaining("Word");
            Files.writeString(mcFile, "%PDF-1.4 archivo falso");
            assertThatThrownBy(() -> MCWordDocumentImporter.mcImportToDocx(mcFile, mcIgnored -> {
                fail("Un archivo inválido no debe enviarse al conversor externo");
                return null;
            })).isInstanceOf(IOException.class).hasMessageContaining("dañado").hasMessageContaining("Word");
        }
    }

    private Path mcLegacyFile(String mcExtension) throws IOException {
        try (InputStream mcInput = getClass().getResourceAsStream("legacy-word.doc.bin")) {
            assertThat(mcInput).isNotNull();
            return Files.write(mcDirectory.resolve("binario." + mcExtension), mcInput.readAllBytes());
        }
    }

    private static void mcAssertLegacyContent(byte[] mcBytes) throws Exception {
        try (XWPFDocument mcDocument = MCDocumentEngine.mcOpen(mcBytes)) {
            assertThat(mcDocument.getPackagePart().getContentType()).isEqualTo(mcDOCX_TYPE);
            assertThat(mcDocument.getParagraphs()).extracting(XWPFParagraph::getText)
                    .contains("Contrato {{nombre}}", "Firma final", "Texto justificado");
            assertThat(mcDocument.getTables()).hasSize(1);
            XWPFTable mcTable = mcDocument.getTables().get(0);
            assertThat(mcTable.getRows()).hasSize(2);
            assertThat(mcTable.getRow(0).getTableCells()).hasSize(2);
            assertThat(mcTable.getRow(0).getCell(0).getText()).isEqualTo("Producto");
            assertThat(mcTable.getRow(0).getCell(1).getText()).isEqualTo("{{Persona.precio}}");
            assertThat(mcTable.getRow(1).getCell(0).getText()).isEqualTo("Silla");
            assertThat(mcTable.getRow(1).getCell(1).getText()).isEqualTo("120.00");
            assertThat(MCDocumentEngine.mcMarkers(mcDocument)).containsExactlyInAnyOrder("nombre", "Persona.precio");
        }
        try (XWPFDocument mcGenerated = MCDocumentEngine.mcOpen(MCDocumentEngine.mcGenerate(mcBytes,
                Map.of("nombre", "Ana", "Persona.precio", "150.00")))) {
            assertThat(MCDocumentEngine.mcMarkers(mcGenerated)).isEmpty();
            assertThat(mcGenerated.getParagraphs().get(0).getText()).isEqualTo("Contrato Ana");
            assertThat(mcGenerated.getTables().get(0).getRow(0).getCell(1).getText()).isEqualTo("150.00");
        }
    }

    private static byte[] mcStandardDocument() throws IOException {
        try (XWPFDocument mcDocument = new XWPFDocument()) {
            mcDocument.createParagraph().createRun().setText("Hola {{nombre}}");
            mcDocument.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Encabezado conservado");
            mcDocument.createTable(1, 1).getRow(0).getCell(0).setText("{{Persona.precio}}");
            return MCDocumentEngine.mcBytes(mcDocument);
        }
    }

    private static byte[] mcOoxmlVariant(String mcContentType, boolean mcMacros) throws Exception {
        try (XWPFDocument mcDocument = MCDocumentEngine.mcOpen(mcStandardDocument());
             ByteArrayOutputStream mcOutput = new ByteArrayOutputStream()) {
            mcDocument.getPackagePart().setContentType(mcContentType);
            if (mcMacros) {
                PackagePart mcVba = mcDocument.getPackage().createPart(PackagingURIHelper.createPartName("/word/vbaProject.bin"),
                        "application/vnd.ms-office.vbaProject");
                try (OutputStream mcStream = mcVba.getOutputStream()) { mcStream.write(new byte[]{1, 2, 3}); }
                mcDocument.getPackagePart().addRelationship(mcVba.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.microsoft.com/office/2006/relationships/vbaProject");
                PackagePart mcData = mcDocument.getPackage().createPart(PackagingURIHelper.createPartName("/word/vbaData.xml"),
                        "application/vnd.ms-word.vbaData+xml");
                try (OutputStream mcStream = mcData.getOutputStream()) { mcStream.write("<vba/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                mcVba.addRelationship(mcData.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.microsoft.com/office/2006/relationships/wordVbaData");
                PackagePart mcSignature = mcDocument.getPackage().createPart(
                        PackagingURIHelper.createPartName("/_xmlsignatures/origin.sigs"),
                        "application/vnd.openxmlformats-package.digital-signature-origin");
                mcDocument.getPackage().addRelationship(mcSignature.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.openxmlformats.org/package/2006/relationships/digital-signature/origin");
            }
            mcDocument.getPackage().save(mcOutput);
            return mcOutput.toByteArray();
        }
    }

    private static void mcAssertEditableAndGeneratable(byte[] mcBytes) throws Exception {
        try (XWPFDocument mcDocument = MCDocumentEngine.mcOpen(mcBytes)) {
            mcDocument.getParagraphs().get(0).createRun().setText(" editable");
            byte[] mcGenerated = MCDocumentEngine.mcGenerate(MCDocumentEngine.mcBytes(mcDocument),
                    Map.of("nombre", "Luis", "Persona.precio", "20"));
            try (XWPFDocument mcResult = MCDocumentEngine.mcOpen(mcGenerated)) {
                assertThat(mcResult.getParagraphs().get(0).getText()).isEqualTo("Hola Luis editable");
                assertThat(mcResult.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo("20");
            }
        }
    }
}
