package com.ejemplo.crudpersona.studio;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static com.ejemplo.crudpersona.studio.MCExcelBatch.MCOrientation.*;

class MCBatchGenerationTest {
    @TempDir Path MCtemp;
    @Test void MCgodModeDefaultsToFalseAndPersistsAcrossReopensWithoutLosingCatalogs()throws Exception {
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp,false);
        var MCdefinition=MCstore.MCsaveDefinition(new MCDocumentStore.MCDefinition(0,"nombre","Nombre","Texto corto",""));
        assertThat(MCstore.MCgodModeEnabled()).isFalse();MCstore.MCsetGodModeEnabled(true);
        MCstore=new MCDocumentStore(MCtemp,false);assertThat(MCstore.MCgodModeEnabled()).isTrue();
        MCstore.MCsetGodModeEnabled(false);assertThat(new MCDocumentStore(MCtemp,false).MCgodModeEnabled()).isFalse();
        assertThat(MCstore.MCdefinitions()).containsExactly(MCdefinition);
    }
    private MCExcelBatch.MCSheet MCsheet(){return new MCExcelBatch.MCSheet("Datos",List.of(List.of("Nombre","Número","Global"),List.of("Ana","1","Constante"),List.of("Luis","2",""),List.of("Eva","3","")),3);}
    @Test void MCextractsVerticalHorizontalAndBothReverseDirectionsAndDetectsLastNonemptyCell(){
        var MCsheet=MCsheet();
        assertThat(new MCExcelBatch.MCRange("A2","A4",VERTICAL,false).MCvalues(MCsheet)).containsExactly("Ana","Luis","Eva");
        assertThat(new MCExcelBatch.MCRange("A2","A4",VERTICAL,true).MCvalues(MCsheet)).containsExactly("Eva","Luis","Ana");
        assertThat(new MCExcelBatch.MCRange("A2","C2",HORIZONTAL,false).MCvalues(MCsheet)).containsExactly("Ana","1","Constante");
        assertThat(new MCExcelBatch.MCRange("C2","A2",HORIZONTAL,true).MCvalues(MCsheet)).containsExactly("Constante","1","Ana");
        assertThat(MCExcelBatch.MCdetect(MCsheet,"A2",VERTICAL,false).MCto()).isEqualTo("A4");
        assertThat(MCExcelBatch.MCdetect(MCsheet,"C2",VERTICAL,false).MCto()).isEqualTo("C2");
        assertThat(MCExcelBatch.MCdetect(MCsheet,"A2",HORIZONTAL,false).MCto()).isEqualTo("C2");
        assertThat(MCExcelBatch.MCdetect(MCsheet,"A4",VERTICAL,true).MCto()).isEqualTo("A1");
    }
    @Test void MCvalidatesEveryRequiredVariableAndBroadcastsSingleCellsAndPairsByIndex(){
        var MCranges=new LinkedHashMap<String,MCExcelBatch.MCRange>();
        MCranges.put("nombre",new MCExcelBatch.MCRange("A2","A4",VERTICAL,false));
        assertThat(MCExcelBatch.MCmissing(List.of("nombre","global"),MCranges)).containsExactly("global");
        assertThatThrownBy(()->MCExcelBatch.MCextract(MCsheet(),List.of("nombre","global"),MCranges)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("global");
        MCranges.put("global",new MCExcelBatch.MCRange("C2","C2",VERTICAL,false));
        var MCrows=MCExcelBatch.MCextract(MCsheet(),List.of("nombre","global"),MCranges);
        assertThat(MCrows).containsExactly(Map.of("nombre","Ana","global","Constante"),Map.of("nombre","Luis","global","Constante"),Map.of("nombre","Eva","global","Constante"));
        MCranges.put("global",new MCExcelBatch.MCRange("B2","B3",VERTICAL,false));
        assertThatThrownBy(()->MCExcelBatch.MCextract(MCsheet(),List.of("nombre","global"),MCranges)).hasMessageContaining("1 o 3");
        assertThatThrownBy(()->new MCExcelBatch.MCRange("A2","B3",VERTICAL,false).MCcells()).hasMessageContaining("columna");
        assertThatThrownBy(()->MCExcelBatch.MCreference("XFE1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MCExcelBatch.MCreference("A0")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void MCreadsBothExcelFormatsWithSheetsFormulasAndRealNumberFormatting()throws Exception {
        for(boolean MClegacy:List.of(false,true))try(Workbook MCbook=MClegacy?new HSSFWorkbook():new XSSFWorkbook()){
            Sheet MCsheet=MCbook.createSheet("Datos");Row MCrow=MCsheet.createRow(0);MCrow.createCell(0).setCellValue(42);
            CellStyle MCstyle=MCbook.createCellStyle();MCstyle.setDataFormat(MCbook.createDataFormat().getFormat("00000000"));MCrow.getCell(0).setCellStyle(MCstyle);
            MCrow.createCell(1).setCellFormula("2+3");MCrow.createCell(2).setCellValue("Ana");MCbook.createSheet("Otra");
            Path MCfile=MCtemp.resolve(MClegacy?"datos.xls":"datos.xlsx");try(var MCout=Files.newOutputStream(MCfile)){MCbook.write(MCout);}
            var MCsheets=MCExcelBatch.MCread(MCfile);assertThat(MCsheets).extracting(MCExcelBatch.MCSheet::MCname).containsExactly("Datos","Otra");
            assertThat(MCsheets.get(0).MCrows().get(0)).containsExactly("00000042","5","Ana");
        }
    }
    @Test void MCzipExportsDocxPdfOrBothWithUniqueNumberedNamesAndActualGeneratedContents()throws Exception {
        byte[] MCtemplate;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("Hola {{nombre}}");MCtemplate=MCDocumentEngine.MCbytes(MCdoc);}
        var MCdocuments=new ArrayList<MCBatchZip.MCDocument>();
        for(String MCname:List.of("Ana","Luis"))MCdocuments.add(new MCBatchZip.MCDocument("Cliente",MCDocumentEngine.MCgenerate(MCtemplate,Map.of("nombre",MCname))));
        for(var MCformat:MCBatchZip.MCFormat.values()){
            Path MCzip=MCtemp.resolve(MCformat+".zip");MCBatchZip.MCwrite(MCzip,MCdocuments,MCformat);
            try(ZipFile MCarchive=new ZipFile(MCzip.toFile())){
                List<String> MCnames=MCarchive.stream().map(ZipEntry::getName).toList();assertThat(MCnames).doesNotHaveDuplicates();
                assertThat(MCnames).hasSize(MCformat==MCBatchZip.MCFormat.BOTH?4:2);
                if(MCformat!=MCBatchZip.MCFormat.PDF){assertThat(MCnames).contains("01_Cliente.docx","02_Cliente.docx");try(XWPFDocument MCdoc=new XWPFDocument(MCarchive.getInputStream(MCarchive.getEntry("02_Cliente.docx")))){assertThat(MCdoc.getParagraphs().get(0).getText()).isEqualTo("Hola Luis");}}
                if(MCformat!=MCBatchZip.MCFormat.DOCX){assertThat(MCnames).contains("01_Cliente.pdf","02_Cliente.pdf");assertThat(new String(MCarchive.getInputStream(MCarchive.getEntry("01_Cliente.pdf")).readNBytes(4),java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");}
            }
        }
    }
}
