package com.ejemplo.crudpersona.studio;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** @author MC */
class MCPartialGenerationTest {
    @Test void mcMissingBlankAndNullValuesBecomeEmptyInBodyTablesHeadersAndFooters()throws Exception{
        byte[] mcTemplate;
        try(XWPFDocument mcDocument=new XWPFDocument()){
            mcDocument.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Cabecera {{cabecera}}");
            XWPFParagraph mcParagraph=mcDocument.createParagraph();mcParagraph.createRun().setText("Nombre {{nom");mcParagraph.createRun().setText("bre}}; DNI {{dni}}; Total {{total}}");
            mcDocument.createTable(1,1).getRow(0).getCell(0).setText("{{direccion}}");mcDocument.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Pie {{pie}}");mcTemplate=MCDocumentEngine.mcBytes(mcDocument);
        }
        Map<String,String> mcPartial=new LinkedHashMap<>();mcPartial.put("nombre","Ana");mcPartial.put("dni","");mcPartial.put("total",null);
        for(Map<String,String> mcValues:List.of(Map.<String,String>of(),mcPartial))try(XWPFDocument mcGenerated=MCDocumentEngine.mcOpen(MCDocumentEngine.mcGenerate(mcTemplate,mcValues))){
            assertThat(MCDocumentEngine.mcMarkers(mcGenerated)).isEmpty();assertThat(mcGenerated.getParagraphs().get(0).getText()).isEqualTo(mcValues.isEmpty()?"Nombre ; DNI ; Total ":"Nombre Ana; DNI ; Total ");
            assertThat(mcGenerated.getTables().get(0).getText()).doesNotContain("{{","null");assertThat(mcGenerated.getHeaderList().get(0).getText()).doesNotContain("{{");assertThat(mcGenerated.getFooterList().get(0).getText()).doesNotContain("{{");assertThat(MCDocxPdfExporter.mcRender(MCDocumentEngine.mcBytes(mcGenerated))).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    @Test void mcEveryInputTypeAcceptsEmptyAndWhitespaceValues(){
        List<String> mcTypes=new ArrayList<>(MCVariableInput.mcTYPES);mcTypes.addAll(List.of("DNI","RUC","Monto","Texto largo","Domicilio"));
        for(String mcType:mcTypes){assertThat(MCVariableInput.mcValidate(mcType,"  ",Locale.forLanguageTag("es-PE"))).isEmpty();assertThat(MCVariableInput.mcValidate(mcType,null,Locale.forLanguageTag("es-PE"))).isEmpty();}
    }
}
