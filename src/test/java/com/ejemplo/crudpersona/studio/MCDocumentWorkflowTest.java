package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** @author MC */
class MCDocumentWorkflowTest {
    @TempDir Path mcTemp;

    @Test void mcPersistsGroupsAndTransfersTemplateAssociations() throws Exception {
        MCDocumentStore mcSource=new MCDocumentStore(mcTemp.resolve("source"));MCStudioData mcSourceData=new MCStudioData(mcSource.mcDirectory());
        byte[] mcBytes;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("Hola {{nombre}}");mcBytes=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcTemplate=mcSource.mcSaveTemplate(new MCTemplate(0,"Carta libre",mcBytes,mcBytes));MCTemplateGroup mcGroup=mcSource.mcSaveGroup(0,"Mis plantillas");mcSource.mcMoveTemplate(mcTemplate.mcId(),mcGroup.mcId());
        Path mcPackageFile=mcTemp.resolve("config.zip");new MCStudioTransfer(mcSource,mcSourceData).mcExportConfiguration(mcPackageFile);
        MCDocumentStore mcTarget=new MCDocumentStore(mcTemp.resolve("target"));MCStudioData mcTargetData=new MCStudioData(mcTarget.mcDirectory());new MCStudioTransfer(mcTarget,mcTargetData).mcImportConfiguration(mcPackageFile,MCStudioTransfer.MCConflict.KEEP);
        assertThat(mcTarget.mcGroups()).extracting(MCTemplateGroup::mcName).contains("Mis plantillas");
        assertThat(mcTarget.mcGroupOf(mcTarget.mcTemplates().get(0).mcId())).isEqualTo(mcTarget.mcGroups().get(0).mcId());
        mcTarget.mcDeleteGroup(mcTarget.mcGroups().get(0).mcId(),null);
        assertThat(mcTarget.mcTemplates()).hasSize(1);assertThat(mcTarget.mcGroupOf(mcTarget.mcTemplates().get(0).mcId())).isNull();
    }

    @Test void mcPreservesPagesHeaderFooterImageAndDocumentDefaultsAfterReopen() throws Exception {
        byte[] mcOriginal;
        try(XWPFDocument mcDoc=new XWPFDocument()){
            XWPFHeader mcHeader=mcDoc.createHeader(HeaderFooterType.DEFAULT);
            mcHeader.createParagraph().createRun().setText("Membrete institucional");
            ByteArrayOutputStream mcImage=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",mcImage);
            mcHeader.createParagraph().createRun().addPicture(new ByteArrayInputStream(mcImage.toByteArray()),XWPFDocument.PICTURE_TYPE_PNG,"logo.png",Units.toEMU(40),Units.toEMU(20));
            mcDoc.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Pie institucional");
            XWPFParagraph mcFirst=mcDoc.createParagraph();mcFirst.setAlignment(ParagraphAlignment.BOTH);mcFirst.createRun().setText("Primera página");
            XWPFParagraph mcNext=mcDoc.createParagraph();mcNext.setPageBreak(true);mcNext.createRun().setText("Segunda página: {{nombre}}");
            mcDoc.createTable(1,1).getRow(0).getCell(0).setText("Tabla conservada");
            mcOriginal=MCDocumentEngine.mcBytes(mcDoc);
        }
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);
        MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Paginado",mcOriginal,mcOriginal));
        mcStore.mcSaveVariable(new MCVariable(0,mcTemplate.mcId(),"nombre","Nombre","Texto corto","",false,1));
        MCKind mcKind=mcStore.mcSaveKind(new MCKind(0,"Paginado","","",mcTemplate.mcId(),"nombre",true));
        mcStore.mcSaveKindFieldRules(mcKind.mcId(),Map.of("nombre",true),Map.of("nombre","Ejemplo"));
        MCDocumentStore mcReopened=new MCDocumentStore(mcTemp);
        assertThat(mcReopened.mcKindDefaults(mcKind.mcId())).containsEntry("nombre","Ejemplo");
        byte[] mcGenerated=MCDocumentEngine.mcGenerate(mcReopened.mcTemplates().get(0).mcEdited(),mcReopened.mcKindDefaults(mcKind.mcId()));
        try(XWPFDocument mcDoc=MCDocumentEngine.mcOpen(mcGenerated)){
            assertThat(mcDoc.getHeaderList().get(0).getParagraphs().get(0).getText()).contains("Membrete institucional");
            assertThat(mcDoc.getFooterList().get(0).getParagraphs().get(0).getText()).contains("Pie institucional");
            assertThat(mcDoc.getHeaderList().get(0).getParagraphs().get(1).getRuns().get(0).getEmbeddedPictures()).hasSize(1);
            assertThat(mcDoc.getParagraphs().get(1).isPageBreak()).isTrue();
            assertThat(mcDoc.getParagraphs().get(1).getText()).contains("Ejemplo");
            assertThat(mcDoc.getTables().get(0).getText()).contains("Tabla conservada");
        }
    }

    @Test void mcImportEditReopenCatalogGenerateAndPersistFiveIndependentPeople() throws Exception {
        byte[] mcOriginal;
        try (XWPFDocument mcDoc = new XWPFDocument()) {
            XWPFParagraph mcHeading = mcDoc.createParagraph();
            mcHeading.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun mcTitle = mcHeading.createRun(); mcTitle.setText("Documento de ejemplo"); mcTitle.setBold(true);
            XWPFTable mcTable = mcDoc.createTable(1, 1);
            mcTable.getRow(0).getCell(0).setText("Contenido de tabla conservado");
            XWPFParagraph mcSplit = mcDoc.createParagraph();
            mcSplit.createRun().setText("Inicio {{variable_");
            mcSplit.createRun().setText("partida}} fin");
            ByteArrayOutputStream mcImageBytes=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",mcImageBytes);
            mcDoc.createParagraph().createRun().addPicture(new ByteArrayInputStream(mcImageBytes.toByteArray()),XWPFDocument.PICTURE_TYPE_PNG,"sample.png",Units.toEMU(20),Units.toEMU(20));
            mcOriginal = MCDocumentEngine.mcBytes(mcDoc);
        }
        MCDocumentStore mcStore = new MCDocumentStore(mcTemp);
        MCTemplate mcTemplate = mcStore.mcSaveTemplate(new MCTemplate(0,"Ejemplo",mcOriginal,mcOriginal));
        try (XWPFDocument mcEdit = MCDocumentEngine.mcOpen(mcTemplate.mcEdited())) {
            XWPFParagraph mcP = mcEdit.createParagraph();
            for (int mcI=1;mcI<=5;mcI++) mcP.createRun().setText("Persona "+mcI+": {{persona"+mcI+"_nombre}}. ");
            mcP.createRun().setText("Repetido: {{persona1_nombre}}.");
            mcTemplate = mcStore.mcSaveTemplate(new MCTemplate(mcTemplate.mcId(),mcTemplate.mcName(),mcTemplate.mcOriginal(),MCDocumentEngine.mcBytes(mcEdit)));
        }
        for (int mcI=1;mcI<=5;mcI++) mcStore.mcSaveVariable(new MCVariable(0,mcTemplate.mcId(),"persona"+mcI+"_nombre","Nombre "+mcI,"Texto corto","",true,mcI));
        MCKind mcKind = mcStore.mcSaveKind(new MCKind(0,"Cinco personas","","",mcTemplate.mcId(),"persona1_nombre,persona2_nombre,persona3_nombre,persona4_nombre,persona5_nombre",true));

        MCDocumentStore mcReopened = new MCDocumentStore(mcTemp);
        assertThat(mcReopened.mcTemplates()).hasSize(1);
        assertThat(mcReopened.mcTemplates().get(0).mcOriginal()).isEqualTo(mcOriginal);
        assertThat(mcReopened.mcVariables(mcTemplate.mcId())).hasSize(5);
        assertThat(mcReopened.mcKinds()).hasSize(1);
        Map<String,String> mcValues = new HashMap<>();
        for (int mcI=1;mcI<=5;mcI++) mcValues.put("persona"+mcI+"_nombre","Valor "+mcI);
        mcValues.put("variable_partida","resuelta");
        byte[] mcOutput = MCDocumentEngine.mcGenerate(mcReopened.mcTemplates().get(0).mcEdited(),mcValues);
        try (XWPFDocument mcResult = MCDocumentEngine.mcOpen(mcOutput)) {
            assertThat(mcResult.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(mcResult.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(mcResult.getTables().get(0).getText()).contains("Contenido de tabla conservado");
            assertThat(mcResult.getAllPictures()).hasSize(1);
            assertThat(mcResult.getParagraphs().get(1).getText()).contains("Inicio resuelta fin");
            assertThat(mcResult.getParagraphs().get(3).getText()).contains("Valor 1","Valor 2","Valor 3","Valor 4","Valor 5");
            assertThat(mcResult.getParagraphs().get(3).getText()).contains("Repetido: Valor 1");
            assertThat(MCDocumentEngine.mcMarkers(mcResult)).isEmpty();
        }
        byte[] mcPdf=MCDocxPdfExporter.mcRender(mcOutput);
        PdfReader mcReader=new PdfReader(mcPdf);
        assertThat(mcReader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        String mcPdfText=new PdfTextExtractor(mcReader).getTextFromPage(1);
        assertThat(mcPdfText).contains("Documento de ejemplo","Valor 1","Valor 5");
        assertThat(mcPdfText).doesNotContain("{{");
        mcReader.close();
        assertThat(mcKind.mcId()).isPositive();
    }
}
