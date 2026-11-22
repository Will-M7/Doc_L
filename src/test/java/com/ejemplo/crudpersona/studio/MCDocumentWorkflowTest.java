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

class MCDocumentWorkflowTest {
    @TempDir Path MCtemp;

    @Test void MCpersistsGroupsAndTransfersTemplateAssociations() throws Exception {
        MCDocumentStore MCsource=new MCDocumentStore(MCtemp.resolve("source"));MCStudioData MCsourceData=new MCStudioData(MCsource.MCdirectory());
        byte[] MCbytes;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("Hola {{nombre}}");MCbytes=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCtemplate=MCsource.MCsaveTemplate(new MCTemplate(0,"Carta libre",MCbytes,MCbytes));MCTemplateGroup MCgroup=MCsource.MCsaveGroup(0,"Mis plantillas");MCsource.MCmoveTemplate(MCtemplate.MCid(),MCgroup.MCid());
        Path MCpackageFile=MCtemp.resolve("config.zip");new MCStudioTransfer(MCsource,MCsourceData).MCexportConfiguration(MCpackageFile);
        MCDocumentStore MCtarget=new MCDocumentStore(MCtemp.resolve("target"));MCStudioData MCtargetData=new MCStudioData(MCtarget.MCdirectory());new MCStudioTransfer(MCtarget,MCtargetData).MCimportConfiguration(MCpackageFile,MCStudioTransfer.MCConflict.KEEP);
        assertThat(MCtarget.MCgroups()).extracting(MCTemplateGroup::MCname).contains("Mis plantillas");
        assertThat(MCtarget.MCgroupOf(MCtarget.MCtemplates().get(0).MCid())).isEqualTo(MCtarget.MCgroups().get(0).MCid());
        MCtarget.MCdeleteGroup(MCtarget.MCgroups().get(0).MCid(),null);
        assertThat(MCtarget.MCtemplates()).hasSize(1);assertThat(MCtarget.MCgroupOf(MCtarget.MCtemplates().get(0).MCid())).isNull();
    }

    @Test void MCpreservesPagesHeaderFooterImageAndDocumentDefaultsAfterReopen() throws Exception {
        byte[] MCoriginal;
        try(XWPFDocument MCdoc=new XWPFDocument()){
            XWPFHeader MCheader=MCdoc.createHeader(HeaderFooterType.DEFAULT);
            MCheader.createParagraph().createRun().setText("Membrete institucional");
            ByteArrayOutputStream MCimage=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",MCimage);
            MCheader.createParagraph().createRun().addPicture(new ByteArrayInputStream(MCimage.toByteArray()),XWPFDocument.PICTURE_TYPE_PNG,"logo.png",Units.toEMU(40),Units.toEMU(20));
            MCdoc.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Pie institucional");
            XWPFParagraph MCfirst=MCdoc.createParagraph();MCfirst.setAlignment(ParagraphAlignment.BOTH);MCfirst.createRun().setText("Primera página");
            XWPFParagraph MCnext=MCdoc.createParagraph();MCnext.setPageBreak(true);MCnext.createRun().setText("Segunda página: {{nombre}}");
            MCdoc.createTable(1,1).getRow(0).getCell(0).setText("Tabla conservada");
            MCoriginal=MCDocumentEngine.MCbytes(MCdoc);
        }
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);
        MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Paginado",MCoriginal,MCoriginal));
        MCstore.MCsaveVariable(new MCVariable(0,MCtemplate.MCid(),"nombre","Nombre","Texto corto","",false,1));
        MCKind MCkind=MCstore.MCsaveKind(new MCKind(0,"Paginado","","",MCtemplate.MCid(),"nombre",true));
        MCstore.MCsaveKindFieldRules(MCkind.MCid(),Map.of("nombre",true),Map.of("nombre","Ejemplo"));
        MCDocumentStore MCreopened=new MCDocumentStore(MCtemp);
        assertThat(MCreopened.MCkindDefaults(MCkind.MCid())).containsEntry("nombre","Ejemplo");
        byte[] MCgenerated=MCDocumentEngine.MCgenerate(MCreopened.MCtemplates().get(0).MCedited(),MCreopened.MCkindDefaults(MCkind.MCid()));
        try(XWPFDocument MCdoc=MCDocumentEngine.MCopen(MCgenerated)){
            assertThat(MCdoc.getHeaderList().get(0).getParagraphs().get(0).getText()).contains("Membrete institucional");
            assertThat(MCdoc.getFooterList().get(0).getParagraphs().get(0).getText()).contains("Pie institucional");
            assertThat(MCdoc.getHeaderList().get(0).getParagraphs().get(1).getRuns().get(0).getEmbeddedPictures()).hasSize(1);
            assertThat(MCdoc.getParagraphs().get(1).isPageBreak()).isTrue();
            assertThat(MCdoc.getParagraphs().get(1).getText()).contains("Ejemplo");
            assertThat(MCdoc.getTables().get(0).getText()).contains("Tabla conservada");
        }
    }

    @Test void MCimportEditReopenCatalogGenerateAndPersistFiveIndependentPeople() throws Exception {
        byte[] MCoriginal;
        try (XWPFDocument MCdoc = new XWPFDocument()) {
            XWPFParagraph MCheading = MCdoc.createParagraph();
            MCheading.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun MCtitle = MCheading.createRun(); MCtitle.setText("Documento de ejemplo"); MCtitle.setBold(true);
            XWPFTable MCtable = MCdoc.createTable(1, 1);
            MCtable.getRow(0).getCell(0).setText("Contenido de tabla conservado");
            XWPFParagraph MCsplit = MCdoc.createParagraph();
            MCsplit.createRun().setText("Inicio {{variable_");
            MCsplit.createRun().setText("partida}} fin");
            ByteArrayOutputStream MCimageBytes=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",MCimageBytes);
            MCdoc.createParagraph().createRun().addPicture(new ByteArrayInputStream(MCimageBytes.toByteArray()),XWPFDocument.PICTURE_TYPE_PNG,"sample.png",Units.toEMU(20),Units.toEMU(20));
            MCoriginal = MCDocumentEngine.MCbytes(MCdoc);
        }
        MCDocumentStore MCstore = new MCDocumentStore(MCtemp);
        MCTemplate MCtemplate = MCstore.MCsaveTemplate(new MCTemplate(0,"Ejemplo",MCoriginal,MCoriginal));
        try (XWPFDocument MCedit = MCDocumentEngine.MCopen(MCtemplate.MCedited())) {
            XWPFParagraph MCp = MCedit.createParagraph();
            for (int MCi=1;MCi<=5;MCi++) MCp.createRun().setText("Persona "+MCi+": {{persona"+MCi+"_nombre}}. ");
            MCp.createRun().setText("Repetido: {{persona1_nombre}}.");
            MCtemplate = MCstore.MCsaveTemplate(new MCTemplate(MCtemplate.MCid(),MCtemplate.MCname(),MCtemplate.MCoriginal(),MCDocumentEngine.MCbytes(MCedit)));
        }
        for (int MCi=1;MCi<=5;MCi++) MCstore.MCsaveVariable(new MCVariable(0,MCtemplate.MCid(),"persona"+MCi+"_nombre","Nombre "+MCi,"Texto corto","",true,MCi));
        MCKind MCkind = MCstore.MCsaveKind(new MCKind(0,"Cinco personas","","",MCtemplate.MCid(),"persona1_nombre,persona2_nombre,persona3_nombre,persona4_nombre,persona5_nombre",true));

        MCDocumentStore MCreopened = new MCDocumentStore(MCtemp);
        assertThat(MCreopened.MCtemplates()).hasSize(1);
        assertThat(MCreopened.MCtemplates().get(0).MCoriginal()).isEqualTo(MCoriginal);
        assertThat(MCreopened.MCvariables(MCtemplate.MCid())).hasSize(5);
        assertThat(MCreopened.MCkinds()).hasSize(1);
        Map<String,String> MCvalues = new HashMap<>();
        for (int MCi=1;MCi<=5;MCi++) MCvalues.put("persona"+MCi+"_nombre","Valor "+MCi);
        MCvalues.put("variable_partida","resuelta");
        byte[] MCoutput = MCDocumentEngine.MCgenerate(MCreopened.MCtemplates().get(0).MCedited(),MCvalues);
        try (XWPFDocument MCresult = MCDocumentEngine.MCopen(MCoutput)) {
            assertThat(MCresult.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(MCresult.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(MCresult.getTables().get(0).getText()).contains("Contenido de tabla conservado");
            assertThat(MCresult.getAllPictures()).hasSize(1);
            assertThat(MCresult.getParagraphs().get(1).getText()).contains("Inicio resuelta fin");
            assertThat(MCresult.getParagraphs().get(3).getText()).contains("Valor 1","Valor 2","Valor 3","Valor 4","Valor 5");
            assertThat(MCresult.getParagraphs().get(3).getText()).contains("Repetido: Valor 1");
            assertThat(MCDocumentEngine.MCmarkers(MCresult)).isEmpty();
        }
        byte[] MCpdf=MCDocxPdfExporter.MCrender(MCoutput);
        PdfReader MCreader=new PdfReader(MCpdf);
        assertThat(MCreader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        String MCpdfText=new PdfTextExtractor(MCreader).getTextFromPage(1);
        assertThat(MCpdfText).contains("Documento de ejemplo","Valor 1","Valor 5");
        assertThat(MCpdfText).doesNotContain("{{");
        MCreader.close();
        assertThat(MCkind.MCid()).isPositive();
    }
}
