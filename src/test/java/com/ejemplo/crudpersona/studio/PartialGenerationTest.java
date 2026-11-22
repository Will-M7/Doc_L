package com.ejemplo.crudpersona.studio;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** @author MC */
class PartialGenerationTest {
    @Test void missingBlankAndNullValuesBecomeEmptyInBodyTablesHeadersAndFooters()throws Exception{
        byte[] template;
        try(XWPFDocument document=new XWPFDocument()){
            document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Cabecera {{cabecera}}");
            XWPFParagraph paragraph=document.createParagraph();paragraph.createRun().setText("Nombre {{nom");paragraph.createRun().setText("bre}}; DNI {{dni}}; Total {{total}}");
            document.createTable(1,1).getRow(0).getCell(0).setText("{{direccion}}");document.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Pie {{pie}}");template=DocumentEngine.bytes(document);
        }
        Map<String,String> partial=new LinkedHashMap<>();partial.put("nombre","Ana");partial.put("dni","");partial.put("total",null);
        for(Map<String,String> values:List.of(Map.<String,String>of(),partial))try(XWPFDocument generated=DocumentEngine.open(DocumentEngine.generate(template,values))){
            assertThat(DocumentEngine.markers(generated)).isEmpty();assertThat(generated.getParagraphs().get(0).getText()).isEqualTo(values.isEmpty()?"Nombre ; DNI ; Total ":"Nombre Ana; DNI ; Total ");
            assertThat(generated.getTables().get(0).getText()).doesNotContain("{{","null");assertThat(generated.getHeaderList().get(0).getText()).doesNotContain("{{");assertThat(generated.getFooterList().get(0).getText()).doesNotContain("{{");assertThat(DocxPdfExporter.render(DocumentEngine.bytes(generated))).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    @Test void everyInputTypeAcceptsEmptyAndWhitespaceValues(){
        List<String> types=new ArrayList<>(VariableInput.TYPES);types.addAll(List.of("DNI","RUC","Monto","Texto largo","Domicilio"));
        for(String type:types){assertThat(VariableInput.validate(type,"  ",Locale.forLanguageTag("es-PE"))).isEmpty();assertThat(VariableInput.validate(type,null,Locale.forLanguageTag("es-PE"))).isEmpty();}
    }
}
