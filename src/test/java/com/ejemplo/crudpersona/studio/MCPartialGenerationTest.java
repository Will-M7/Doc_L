package com.ejemplo.crudpersona.studio;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MCPartialGenerationTest {
    @Test void MCmissingBlankAndNullValuesBecomeEmptyInBodyTablesHeadersAndFooters()throws Exception{
        byte[] MCtemplate;
        try(XWPFDocument MCdocument=new XWPFDocument()){
            MCdocument.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Cabecera {{cabecera}}");
            XWPFParagraph MCparagraph=MCdocument.createParagraph();MCparagraph.createRun().setText("Nombre {{nom");MCparagraph.createRun().setText("bre}}; DNI {{dni}}; Total {{total}}");
            MCdocument.createTable(1,1).getRow(0).getCell(0).setText("{{direccion}}");MCdocument.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Pie {{pie}}");MCtemplate=MCDocumentEngine.MCbytes(MCdocument);
        }
        Map<String,String> MCpartial=new LinkedHashMap<>();MCpartial.put("nombre","Ana");MCpartial.put("dni","");MCpartial.put("total",null);
        for(Map<String,String> MCvalues:List.of(Map.<String,String>of(),MCpartial))try(XWPFDocument MCgenerated=MCDocumentEngine.MCopen(MCDocumentEngine.MCgenerate(MCtemplate,MCvalues))){
            assertThat(MCDocumentEngine.MCmarkers(MCgenerated)).isEmpty();assertThat(MCgenerated.getParagraphs().get(0).getText()).isEqualTo(MCvalues.isEmpty()?"Nombre ; DNI ; Total ":"Nombre Ana; DNI ; Total ");
            assertThat(MCgenerated.getTables().get(0).getText()).doesNotContain("{{","null");assertThat(MCgenerated.getHeaderList().get(0).getText()).doesNotContain("{{");assertThat(MCgenerated.getFooterList().get(0).getText()).doesNotContain("{{");assertThat(MCDocxPdfExporter.MCrender(MCDocumentEngine.MCbytes(MCgenerated))).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    @Test void MCeveryInputTypeAcceptsEmptyAndWhitespaceValues(){
        List<String> MCtypes=new ArrayList<>(MCVariableInput.MCTYPES);MCtypes.addAll(List.of("DNI","RUC","Monto","Texto largo","Domicilio"));
        for(String MCtype:MCtypes){assertThat(MCVariableInput.MCvalidate(MCtype,"  ",Locale.forLanguageTag("es-PE"))).isEmpty();assertThat(MCVariableInput.MCvalidate(MCtype,null,Locale.forLanguageTag("es-PE"))).isEmpty();}
    }
}
