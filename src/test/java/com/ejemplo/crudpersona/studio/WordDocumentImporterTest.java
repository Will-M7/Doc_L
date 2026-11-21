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

class WordDocumentImporterTest {
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    @TempDir Path directory;

    @Test void standardDocxIsValidatedWithoutChangingItsBytes() throws Exception {
        byte[] original = standardDocument();
        for (String extension : List.of("docx", "DOCX", "DoCx")) {
            Path file = Files.write(directory.resolve("contrato." + extension), original);
            byte[] imported = WordDocumentImporter.importToDocx(file);
            assertThat(imported).isEqualTo(original);
            assertEditableAndGeneratable(imported);
        }
    }

    @Test void templatesAndMacroDocumentsBecomeStandardEditableDocxWithoutVba() throws Exception {
        Map<String, String> types = Map.of(
                "dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
                "docm", "application/vnd.ms-word.document.macroEnabled.main+xml",
                "dotm", "application/vnd.ms-word.template.macroEnabledTemplate.main+xml");
        for (var entry : types.entrySet()) {
            byte[] source = ooxmlVariant(entry.getValue(), !entry.getKey().equals("dotx"));
            for (String extension : List.of(entry.getKey(), entry.getKey().toUpperCase(Locale.ROOT))) {
                Path file = Files.write(directory.resolve("modelo." + extension), source);
                byte[] imported = WordDocumentImporter.importToDocx(file);
                assertThat(Files.readAllBytes(file)).isEqualTo(source);
                assertEditableAndGeneratable(imported);
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(imported))) {
                    java.util.zip.ZipEntry part;
                    while ((part = zip.getNextEntry()) != null)
                        assertThat(part.getName()).doesNotContain("vbaProject", "vbaData", "_xmlsignatures");
                }
                try (XWPFDocument document = DocumentEngine.open(imported)) {
                    assertThat(document.getPackagePart().getContentType()).isEqualTo(DOCX_TYPE);
                    assertThat(document.getHeaderList().get(0).getText()).contains("Encabezado conservado");
                    assertThat(document.getPackage().getParts()).noneMatch(part ->
                            part.getContentType().toLowerCase(Locale.ROOT).contains("vba")
                                    || part.getContentType().contains("digital-signature"));
                    for (PackageRelationship relationship : document.getPackagePart().getRelationships())
                        assertThat(relationship.getRelationshipType()).doesNotContain("vba");
                }
            }
        }
    }

    @Test void binaryDocAndDotImportThroughTheAutomaticConverter() throws Exception {
        for (String extension : List.of("doc", "DOC", "dot", "DOT")) {
            Path file = legacyFile(extension);
            byte[] original = Files.readAllBytes(file);
            assertLegacyContent(WordDocumentImporter.importToDocx(file));
            assertThat(Files.readAllBytes(file)).isEqualTo(original);
        }
    }

    @Test void hwpfFallbackPreservesTextTablesVariablesAlignmentAndFontStyles() throws Exception {
        byte[] converted = WordDocumentImporter.importToDocx(legacyFile("doc"), file -> null);
        assertLegacyContent(converted);
        try (XWPFDocument document = DocumentEngine.open(converted)) {
            assertThat(document.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly(
                    "Contrato {{nombre}}", "  Texto con espacios  ", "", "Firma final", "Texto justificado");
            XWPFParagraph title = document.getParagraphs().get(0);
            assertThat(title.getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.getRuns().get(0);
            assertThat(titleRun.isBold()).isTrue();
            assertThat(titleRun.getFontFamily()).isEqualTo("Times New Roman");
            assertThat(titleRun.getFontSizeAsDouble()).isEqualTo(14.0);
            XWPFRun emphasis = document.getParagraphs().get(1).getRuns().get(0);
            assertThat(emphasis.isItalic()).isTrue();
            assertThat(emphasis.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);
            assertThat(emphasis.getFontSizeAsDouble()).isEqualTo(12.5);
            assertThat(document.getParagraphs().get(3).getAlignment()).isEqualTo(ParagraphAlignment.RIGHT);
            assertThat(document.getParagraphs().get(4).getAlignment()).isEqualTo(ParagraphAlignment.BOTH);
            assertThat(document.getBodyElements()).extracting(IBodyElement::getElementType).containsExactly(
                    BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH,
                    BodyElementType.TABLE, BodyElementType.PARAGRAPH, BodyElementType.PARAGRAPH);
            assertThat(document.getTables().get(0).getRow(0).getCell(0).getParagraphs()).hasSize(1);
        }
    }

    @Test void aFailedExternalConversionAlsoFallsBackToOfflineHwpf() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        byte[] imported = WordDocumentImporter.importToDocx(legacyFile("DOT"), file -> {
            called.set(true);
            throw new IOException("Conversor no disponible");
        });
        assertThat(called).isTrue();
        assertLegacyContent(imported);
    }

    @Test void successfulOfficeConversionIsPreferredOverHwpf() throws Exception {
        byte[] officeOutput = standardDocument();
        byte[] imported = WordDocumentImporter.importToDocx(legacyFile("doc"), file -> officeOutput);
        assertThat(imported).isEqualTo(officeOutput);
        assertEditableAndGeneratable(imported);
    }

    @Test void unsupportedExtensionsAreRejectedEvenIfTheContentIsWord() throws Exception {
        Path noExtension = Files.write(directory.resolve("docx"), standardDocument());
        assertThatThrownBy(() -> WordDocumentImporter.importToDocx(noExtension))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Solo se admiten documentos de Word");
        for (String extension : List.of("txt", "pdf", "rtf", "odt", "zip")) {
            Path file = Files.write(directory.resolve("archivo." + extension), standardDocument());
            assertThatThrownBy(() -> WordDocumentImporter.importToDocx(file))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Solo se admiten documentos de Word");
        }
    }

    @Test void emptyCorruptAndDisguisedFilesAreRejectedClearlyWithoutRunningOffice() throws Exception {
        for (String extension : List.of("doc", "docx", "docm", "dot", "dotx", "dotm")) {
            Path file = Files.write(directory.resolve("vacio." + extension), new byte[0]);
            assertThatThrownBy(() -> WordDocumentImporter.importToDocx(file))
                    .isInstanceOf(IOException.class).hasMessageContaining("vacío").hasMessageContaining("Word");
            Files.writeString(file, "%PDF-1.4 archivo falso");
            assertThatThrownBy(() -> WordDocumentImporter.importToDocx(file, ignored -> {
                fail("Un archivo inválido no debe enviarse al conversor externo");
                return null;
            })).isInstanceOf(IOException.class).hasMessageContaining("dañado").hasMessageContaining("Word");
        }
    }

    private Path legacyFile(String extension) throws IOException {
        // Fixture propio generado desde OOXML con LibreOffice en formato Word 97; no requiere LO para los tests offline.
        try (InputStream input = getClass().getResourceAsStream("legacy-word.doc.bin")) {
            assertThat(input).isNotNull();
            return Files.write(directory.resolve("binario." + extension), input.readAllBytes());
        }
    }

    private static void assertLegacyContent(byte[] bytes) throws Exception {
        try (XWPFDocument document = DocumentEngine.open(bytes)) {
            assertThat(document.getPackagePart().getContentType()).isEqualTo(DOCX_TYPE);
            assertThat(document.getParagraphs()).extracting(XWPFParagraph::getText)
                    .contains("Contrato {{nombre}}", "Firma final", "Texto justificado");
            assertThat(document.getTables()).hasSize(1);
            XWPFTable table = document.getTables().get(0);
            assertThat(table.getRows()).hasSize(2);
            assertThat(table.getRow(0).getTableCells()).hasSize(2);
            assertThat(table.getRow(0).getCell(0).getText()).isEqualTo("Producto");
            assertThat(table.getRow(0).getCell(1).getText()).isEqualTo("{{Persona.precio}}");
            assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("Silla");
            assertThat(table.getRow(1).getCell(1).getText()).isEqualTo("120.00");
            assertThat(DocumentEngine.markers(document)).containsExactlyInAnyOrder("nombre", "Persona.precio");
        }
        try (XWPFDocument generated = DocumentEngine.open(DocumentEngine.generate(bytes,
                Map.of("nombre", "Ana", "Persona.precio", "150.00")))) {
            assertThat(DocumentEngine.markers(generated)).isEmpty();
            assertThat(generated.getParagraphs().get(0).getText()).isEqualTo("Contrato Ana");
            assertThat(generated.getTables().get(0).getRow(0).getCell(1).getText()).isEqualTo("150.00");
        }
    }

    private static byte[] standardDocument() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Hola {{nombre}}");
            document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Encabezado conservado");
            document.createTable(1, 1).getRow(0).getCell(0).setText("{{Persona.precio}}");
            return DocumentEngine.bytes(document);
        }
    }

    private static byte[] ooxmlVariant(String contentType, boolean macros) throws Exception {
        try (XWPFDocument document = DocumentEngine.open(standardDocument());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getPackagePart().setContentType(contentType);
            if (macros) {
                PackagePart vba = document.getPackage().createPart(PackagingURIHelper.createPartName("/word/vbaProject.bin"),
                        "application/vnd.ms-office.vbaProject");
                try (OutputStream stream = vba.getOutputStream()) { stream.write(new byte[]{1, 2, 3}); }
                document.getPackagePart().addRelationship(vba.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.microsoft.com/office/2006/relationships/vbaProject");
                PackagePart data = document.getPackage().createPart(PackagingURIHelper.createPartName("/word/vbaData.xml"),
                        "application/vnd.ms-word.vbaData+xml");
                try (OutputStream stream = data.getOutputStream()) { stream.write("<vba/>".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                vba.addRelationship(data.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.microsoft.com/office/2006/relationships/wordVbaData");
                PackagePart signature = document.getPackage().createPart(
                        PackagingURIHelper.createPartName("/_xmlsignatures/origin.sigs"),
                        "application/vnd.openxmlformats-package.digital-signature-origin");
                document.getPackage().addRelationship(signature.getPartName(), TargetMode.INTERNAL,
                        "http://schemas.openxmlformats.org/package/2006/relationships/digital-signature/origin");
            }
            document.getPackage().save(output);
            return output.toByteArray();
        }
    }

    private static void assertEditableAndGeneratable(byte[] bytes) throws Exception {
        try (XWPFDocument document = DocumentEngine.open(bytes)) {
            document.getParagraphs().get(0).createRun().setText(" editable");
            byte[] generated = DocumentEngine.generate(DocumentEngine.bytes(document),
                    Map.of("nombre", "Luis", "Persona.precio", "20"));
            try (XWPFDocument result = DocumentEngine.open(generated)) {
                assertThat(result.getParagraphs().get(0).getText()).isEqualTo("Hola Luis editable");
                assertThat(result.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo("20");
            }
        }
    }
}
