package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** @author MC */
class MCContinuousDocxEditorTest {
    @Test void mcTabsRoundTripAsOoxmlElementsWithoutChangingSpacesTextOrOrder()throws Exception{
        try(XWPFDocument mcDoc=new XWPFDocument()){
            var mcP=mcDoc.createParagraph();var mcRun=mcP.createRun();mcRun.setText("  Antes ");mcRun.addTab();mcRun.setText(" entre  ");mcRun.addTab();mcRun.addTab();mcRun.setText(" {{nombre}} después ");
            var mcCell=mcDoc.createTable(1,1).getRow(0).getCell(0).getParagraphs().get(0);mcCell.createRun().setText(" Celda\t final ");
            MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDoc);String mcTab="<span class=\"docx-tab\" data-tab=\"true\">\t</span>";
            String mcHtml=mcEditor.mcHtml();assertThat(mcHtml).contains(mcTab);assertThat(mcHtml.split("data-tab=",-1)).hasSize(5);
            mcEditor.mcApply("<div>"+mcHtml.replace("data-id=\"","data-changed=\"true\" data-id=\"").replace(" después "," después \t extra ")+"</div>");
            for(int mcI=0;mcI<3;mcI++){
                try(XWPFDocument mcSaved=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcDoc))){
                    assertThat(mcSaved.getParagraphs().get(0).getText()).isEqualTo("  Antes \t entre  \t\t {{nombre}} después \t extra ");
                    assertThat(mcSaved.getParagraphs().get(0).getRuns().stream().mapToInt(mcR->mcR.getCTR().sizeOfTabArray()).sum()).isEqualTo(4);
                    assertThat(mcSaved.getParagraphs().get(0).getRuns().stream().flatMap(mcR->mcR.getCTR().getTList().stream()).map(mcT->mcT.getStringValue())).allMatch(mcT->!mcT.contains("\t"));
                    assertThat(mcSaved.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo(" Celda\t final ");
                }
                mcHtml=mcEditor.mcHtml();mcEditor.mcApply("<div>"+mcHtml.replace("data-id=\"","data-changed=\"true\" data-id=\"")+"</div>");
            }
            try(XWPFDocument mcGenerated=MCDocumentEngine.mcOpen(MCDocumentEngine.mcGenerate(MCDocumentEngine.mcBytes(mcDoc),java.util.Map.of("nombre","Ana")))){
                assertThat(mcGenerated.getParagraphs().get(0).getText()).isEqualTo("  Antes \t entre  \t\t Ana después \t extra ");
                assertThat(mcGenerated.getParagraphs().get(0).getRuns().stream().mapToInt(mcR->mcR.getCTR().sizeOfTabArray()).sum()).isEqualTo(4);
            }
        }
    }

    @Test void mcChipAndContainerFormattingSurviveSavingReloadingAndFinalGeneration()throws Exception{
        try(XWPFDocument mcDoc=new XWPFDocument()){
            mcDoc.createParagraph().createRun().setText("Antes");MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDoc);mcEditor.mcHtml();
            mcEditor.mcApply("""
                <div><p data-id="p1" data-changed="true">Antes <strong><em><u><span style="font-size: 18pt; font-family: Times New Roman; color: rgb(18, 52, 86); background-color: rgb(255, 238, 128)"><span class="variable" data-key="Persona_1.nombre">⟦nombre⟧</span></span></u></em></strong> <span class="variable" data-key="cargo" style="font-weight: 700; font-style: italic; text-decoration: underline; font-size: 24.5pt; font-family: Calibri; color: #aabbcc; background-color: #abcdef">⟦cargo⟧</span> después</p></div>
                """);
            for(int mcI=0;mcI<2;mcI++){
                try(XWPFDocument mcSaved=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcDoc))){
                    var mcRuns=mcSaved.getParagraphs().get(0).getRuns();var mcPerson=mcRuns.stream().filter(mcR->mcR.text().equals("{{Persona_1.nombre}}")).findFirst().orElseThrow();var mcCargo=mcRuns.stream().filter(mcR->mcR.text().equals("{{cargo}}")).findFirst().orElseThrow();
                    for(var mcR:java.util.List.of(mcPerson,mcCargo)){assertThat(mcR.isBold()).isTrue();assertThat(mcR.isItalic()).isTrue();assertThat(mcR.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);}
                    assertThat(mcPerson.getFontFamily()).isEqualTo("Times New Roman");assertThat(mcPerson.getFontSizeAsDouble()).isEqualTo(18);assertThat(mcPerson.getColor()).isEqualTo("123456");
                    assertThat(mcCargo.getFontFamily()).isEqualTo("Calibri");assertThat(mcCargo.getFontSizeAsDouble()).isEqualTo(24.5);assertThat(mcCargo.getColor()).isEqualTo("AABBCC");
                    String mcHtml=new MCContinuousDocxEditor(mcSaved).mcHtml();assertThat(mcHtml).contains("font-family:Times New Roman;font-size:18pt;font-weight:bold;font-style:italic;text-decoration:underline;color:#123456;background-color:#FFEE80;","font-family:Calibri;font-size:24.5pt;font-weight:bold;font-style:italic;text-decoration:underline;color:#AABBCC;background-color:#ABCDEF;","data-key=\"Persona_1.nombre\"","data-key=\"cargo\"");
                }
                String mcHtml=mcEditor.mcHtml();mcEditor.mcApply("<div>"+mcHtml.replace("data-id=\"","data-changed=\"true\" data-id=\"")+"</div>");
            }
            try(XWPFDocument mcGenerated=MCDocumentEngine.mcOpen(MCDocumentEngine.mcGenerate(MCDocumentEngine.mcBytes(mcDoc),java.util.Map.of("Persona_1.nombre","Ana","cargo","Directora")))){
                var mcAna=mcGenerated.getParagraphs().get(0).getRuns().stream().filter(mcR->mcR.text().equals("Ana")).findFirst().orElseThrow();assertThat(mcAna.isBold()).isTrue();assertThat(mcAna.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);assertThat(mcAna.getFontSizeAsDouble()).isEqualTo(18);assertThat(mcAna.getColor()).isEqualTo("123456");
            }
        }
    }

    @Test void mcDirectChipFormattingCanClearInheritedEmphasisAndReadWordHighlight()throws Exception{
        try(XWPFDocument mcDoc=new XWPFDocument()){
            var mcR=mcDoc.createParagraph().createRun();mcR.setText("{{nombre}}");mcR.setTextHighlightColor("yellow");MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDoc);assertThat(mcEditor.mcHtml()).contains("background-color:#FFFF00;");
            mcEditor.mcApply("<div><p data-id=\"p1\" data-changed=\"true\"><b><i><u><span class=\"variable\" data-key=\"nombre\" style=\"font-weight:normal;font-style:normal;text-decoration:none\">nombre</span></u></i></b></p></div>");
            var mcResult=mcDoc.getParagraphs().get(0).getRuns().get(0);assertThat(mcResult.isBold()).isFalse();assertThat(mcResult.isItalic()).isFalse();assertThat(mcResult.getUnderline()).isEqualTo(UnderlinePatterns.NONE);
        }
    }

    @Test void mcRendersSplitRunTokensAsOneChipAndRewritesOnlyMovedParagraphs()throws Exception{
        try(XWPFDocument mcDocument=new XWPFDocument()){
            XWPFParagraph mcSource=mcDocument.createParagraph();mcSource.createRun().setText("{{nom");mcSource.createRun().setText("bre}}");
            XWPFParagraph mcTarget=mcDocument.createParagraph();mcTarget.createRun().setText("Destino ");
            XWPFParagraph mcUntouched=mcDocument.createParagraph();mcUntouched.setStyle("Heading1");mcUntouched.createRun().setText("Intacto");String mcOriginal=mcUntouched.getCTP().xmlText();
            MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDocument);String mcHtml=mcEditor.mcHtml();
            var mcMatch=java.util.regex.Pattern.compile("<span class=\"variable\"[^>]*>.*?</span>").matcher(mcHtml);assertThat(mcMatch.find()).isTrue();String mcChip=mcMatch.group();assertThat(mcMatch.find()).isFalse();assertThat(mcChip).contains("contenteditable=\"false\"","draggable=\"true\"");
            String mcMoved=mcHtml.replace(mcChip,"").replaceFirst("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"").replaceFirst("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\"").replace("Destino ","Destino "+mcChip);
            mcEditor.mcApply("<div>"+mcMoved+"</div>");assertThat(mcSource.getText()).isEmpty();assertThat(mcTarget.getText()).isEqualTo("Destino {{nombre}}");assertThat(mcUntouched.getCTP().xmlText()).isEqualTo(mcOriginal);
        }
    }

    @Test void mcRepeatedSyncKeepsRealSpacesAndBlankParagraphsWithoutChipSentinelsOrFillerBreaks()throws Exception{
        try(XWPFDocument mcDoc=new XWPFDocument()){
            mcDoc.createParagraph().createRun().setText("Antes  {{Persona_1.nombre}} después ");mcDoc.createParagraph().createRun().setText("");mcDoc.createParagraph().createRun().setText("Final");
            MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDoc);String mcHtml=mcEditor.mcHtml().replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"").replace("⟧</span>","⟧</span>\u200B").replace("después </span>","después <br/></span>").replace("<br>","<br/>");
            String mcNormalized=mcEditor.mcApply("<div>"+mcHtml+"</div>");mcEditor.mcApply(mcNormalized);String mcAgain=mcEditor.mcHtml().replace("<br>","<br/>");mcEditor.mcApply("<div>"+mcAgain+"</div>");
            assertThat(mcDoc.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  {{Persona_1.nombre}} después ","","Final");
            byte[] mcFilled=MCDocumentEngine.mcGenerate(MCDocumentEngine.mcBytes(mcDoc),java.util.Map.of("Persona_1.nombre","Ana"));try(XWPFDocument mcSaved=MCDocumentEngine.mcOpen(mcFilled)){assertThat(mcSaved.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  Ana después ","","Final");}
        }
    }
    @Test void mcReplacementKeepsEveryTextNodeSpaceTabAndBreakInTheOriginalRuns()throws Exception{
        try(XWPFDocument mcDoc=new XWPFDocument()){
            XWPFParagraph mcP=mcDoc.createParagraph();var mcR=mcP.createRun();mcR.setText("  A{{");mcR.setText("nombre}}  B ");mcR.addTab();mcR.setText(" {{Persona_2.");mcR.setText("cargo}} ");mcR.addBreak();mcR.setText("C{{vacio}}  D  ");mcDoc.createParagraph();
            byte[] mcSource=MCDocumentEngine.mcBytes(mcDoc);for(int mcI=0;mcI<3;mcI++){mcSource=MCDocumentEngine.mcGenerate(mcSource,java.util.Map.of("nombre","Ana","Persona_2.cargo","Presidenta"));try(XWPFDocument mcSaved=MCDocumentEngine.mcOpen(mcSource)){assertThat(mcSaved.getParagraphs()).hasSize(2);assertThat(mcSaved.getParagraphs().get(0).getText()).isEqualTo("  AAna  B \t Presidenta \nC  D  ");assertThat(mcSaved.getParagraphs().get(1).getText()).isEmpty();}}
        }
    }
    @Test void mcReadOnlyPreviewUsesInheritedRunFormattingWithoutTurningTokensIntoChips()throws Exception{
        try(XWPFDocument mcDoc=new XWPFDocument()){
            var mcStyles=mcDoc.createStyles();var mcDefinition=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory.newInstance();mcDefinition.setStyleId("DocumentBody");var mcProps=mcDefinition.addNewRPr();mcProps.addNewRFonts().setAscii("Times New Roman");mcProps.addNewSz().setVal(java.math.BigInteger.valueOf(28));mcProps.addNewB();mcProps.addNewI();mcStyles.addStyle(new XWPFStyle(mcDefinition));var mcParagraph=mcDoc.createParagraph();mcParagraph.setStyle("DocumentBody");mcParagraph.createRun().setText("Contenido {{nombre}}");String mcPreview=new MCContinuousDocxEditor(mcDoc).mcPreviewHtml();assertThat(mcPreview).contains("contenteditable=\"false\"","font-family:Times New Roman;font-size:14.0pt;font-weight:bold;font-style:italic;","Contenido {{nombre}}").doesNotContain("class=\"variable\"","id=\"ruler-x\"","contenteditable=\"true\"");
        }
    }
    @Test void mcRibbonFontSizesListsAndHorizontalRuleSurviveDocxRoundTrip()throws Exception{
        try(XWPFDocument mcDocument=new XWPFDocument()){
            for(String mcText:new String[]{"Viñeta","Numerado","Línea"})mcDocument.createParagraph().createRun().setText(mcText);
            MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDocument);String mcHtml=mcEditor.mcHtml().replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\" data-list=\"bullet\"").replace("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\" data-list=\"decimal\"").replace("data-id=\"p3\"","data-id=\"p3\" data-changed=\"true\" data-rule=\"true\"").replace("font-size:12pt","font-size:24pt");
            mcEditor.mcApply("<div>"+mcHtml+"</div>");
            try(XWPFDocument mcReopened=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcDocument))){
                assertThat(mcReopened.getParagraphs().get(0).getNumFmt()).isEqualTo("bullet");assertThat(mcReopened.getParagraphs().get(1).getNumFmt()).isEqualTo("decimal");assertThat(mcReopened.getParagraphs().get(0).getRuns().get(0).getFontSize()).isEqualTo(24);assertThat(mcReopened.getParagraphs().get(2).getBorderBottom()).isEqualTo(Borders.SINGLE);
                MCContinuousDocxEditor mcAgain=new MCContinuousDocxEditor(mcReopened);String mcRendered=mcAgain.mcHtml();assertThat(mcRendered).contains("data-list=\"bullet\"","data-list=\"decimal\"","data-rule=\"true\"","font-size:24pt");
                String mcNumbered=mcReopened.getParagraphs().get(1).getCTP().xmlText();mcAgain.mcApply("<div>"+mcRendered.replace("data-list=\"bullet\"","data-changed=\"true\"")+"</div>");assertThat(mcReopened.getParagraphs().get(0).getNumID()).isNull();assertThat(mcReopened.getParagraphs().get(1).getCTP().xmlText()).isEqualTo(mcNumbered);
            }
        }
    }

    @Test void mcEnterClonedBlockIndicesKeepUpperTextAndEveryEmptyParagraphInDomOrder()throws Exception{
        try(XWPFDocument mcDocument=new XWPFDocument()){
            XWPFParagraph mcUpper=mcDocument.createParagraph();mcUpper.setStyle("Heading1");mcUpper.createRun().setText("Línea superior");XWPFParagraph mcSplit=mcDocument.createParagraph();mcSplit.setStyle("Heading2");mcSplit.setAlignment(ParagraphAlignment.CENTER);XWPFRun mcBase=mcSplit.createRun();mcBase.setText("Arriba abajo");mcBase.setFontSize(18);mcBase.setBold(true);mcDocument.createParagraph().createRun().setText("Última");String mcUpperXml=mcUpper.getCTP().xmlText();
            MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDocument);String mcHtml=mcEditor.mcHtml();String mcOriginal=mcHtml.substring(mcHtml.indexOf("<p data-id=\"p2\""),mcHtml.indexOf("<p data-id=\"p3\""));
            String mcFirst=mcOriginal.replace("Arriba abajo","Arriba").replace("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\"");String mcBlank="<p data-block-index=\"p2\" data-id=\"p2\"><br/></p>";String mcTail="<div data-block-index=\"p2\" data-id=\"p2\">abajo</div>";
            mcEditor.mcApply("<div>"+mcHtml.replace(mcOriginal,mcFirst+mcBlank+mcBlank+mcTail)+"</div>");
            try(XWPFDocument mcSaved=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcDocument))){assertThat(mcSaved.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Línea superior","Arriba","","","abajo","Última");assertThat(mcUpper.getCTP().xmlText()).isEqualTo(mcUpperXml);assertThat(mcSaved.getParagraphs().get(4).getRuns().get(0).getFontSize()).isEqualTo(18);assertThat(mcSaved.getParagraphs().get(4).getRuns().get(0).isBold()).isTrue();for(int mcI=2;mcI<=4;mcI++){assertThat(mcSaved.getParagraphs().get(mcI).getStyle()).isEqualTo("Heading2");assertThat(mcSaved.getParagraphs().get(mcI).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);}}
        }
    }

    @Test void mcNewEmptyAndListBlocksAfterATableKeepTheirExactPositionAndRepeatedSaves()throws Exception{
        try(XWPFDocument mcDocument=new XWPFDocument()){
            mcDocument.createParagraph().createRun().setText("Antes");mcDocument.createTable(1,1).getRow(0).getCell(0).setText("Tabla");mcDocument.createParagraph().createRun().setText("Después");MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDocument);String mcHtml=mcEditor.mcHtml();String mcInserted=mcHtml.replace("</table>","</table><p data-new-block=\"true\"><br/></p><li data-new-block=\"true\">Nuevo</li><div data-new-block=\"true\"><br/></div>");
            mcEditor.mcApply("<div>"+mcInserted+"</div>");String mcOnce=mcEditor.mcHtml().replace("<br>","<br/>");mcEditor.mcApply("<div>"+mcOnce+"</div>");
            assertThat(mcDocument.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes","","Nuevo","","Después");assertThat(mcDocument.getBodyElements().get(1)).isInstanceOf(XWPFTable.class);try(XWPFDocument mcSaved=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcDocument))){assertThat(mcSaved.getTables().get(0).getText()).contains("Tabla");}
        }
    }

    @Test void mcQualifiedChipsKeepTheirPersonAndRenderItsRoleAcrossSplitRuns()throws Exception{
        try(XWPFDocument mcDoc=new XWPFDocument()){var mcP=mcDoc.createParagraph();mcP.createRun().setText("{{Persona_1.");mcP.createRun().setText("DNI}} {{Persona_2.Cargo}}");MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDoc,java.util.Map.of("Persona_1","Solicitante","Persona_2","Dirigido"));String mcHtml=mcEditor.mcHtml();assertThat(mcHtml).contains("⟦Solicitante · DNI⟧","⟦Dirigido · Cargo⟧","data-persona=\"Persona_1\"","data-key=\"Persona_2.Cargo\"");mcEditor.mcApply("<div>"+mcHtml.replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"")+"</div>");assertThat(mcP.getText()).isEqualTo("{{Persona_1.DNI}} {{Persona_2.Cargo}}");}
    }
    @Test void mcCreatesRowsAndDeletesWholeTableWithoutLeavingEmptyContent() throws Exception {
        try(XWPFDocument mcDoc=new XWPFDocument()){
            mcDoc.createParagraph().createRun().setText("Antes");MCContinuousDocxEditor mcFirst=new MCContinuousDocxEditor(mcDoc);
            String mcHtml=mcFirst.mcHtml()+"<table data-borders=\"all\" data-border-width=\"1\" data-border-color=\"#8c959f\"><tr><td><p>Uno</p></td><td><p>Dos</p></td></tr></table>";
            mcFirst.mcApply("<div>"+mcHtml+"</div>");
            try(XWPFDocument mcWithFirstTable=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcDoc))){
                assertThat(mcWithFirstTable.getTables()).hasSize(1);assertThat(mcWithFirstTable.getTables().get(0).getText()).contains("Uno","Dos");
                MCContinuousDocxEditor mcSecond=new MCContinuousDocxEditor(mcWithFirstTable);String mcWithTable=mcSecond.mcHtml();
                String mcAdded=mcWithTable.replace("</table>","<tr><td><p>Tres</p></td><td><p>Cuatro</p></td></tr></table>").replaceFirst("<table ","<table data-changed=\"true\" ");
                mcSecond.mcApply("<div>"+mcAdded+"</div>");
                try(XWPFDocument mcWithSecondRow=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcWithFirstTable))){
                    assertThat(mcWithSecondRow.getTables().get(0).getNumberOfRows()).isEqualTo(2);assertThat(mcWithSecondRow.getTables().get(0).getRow(1).getCell(1).getText()).contains("Cuatro");
                    MCContinuousDocxEditor mcThird=new MCContinuousDocxEditor(mcWithSecondRow);String mcRemoved=mcThird.mcHtml().replaceAll("(?s)<table[^>]*>.*?</table>","");mcThird.mcApply("<div>"+mcRemoved+"</div>");
                    try(XWPFDocument mcReopened=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcWithSecondRow))){assertThat(mcReopened.getTables()).isEmpty();assertThat(mcReopened.getParagraphs()).hasSize(1);assertThat(mcReopened.getParagraphs().get(0).getText()).isEqualTo("Antes");}
                }
            }
        }
    }
    @Test void mcKeepsUnchangedStructureAndSavesContinuousParagraphsAndVariables() throws Exception {
        try(XWPFDocument mcDoc=new XWPFDocument()){
            XWPFHeader mcHeader=mcDoc.createHeader(HeaderFooterType.DEFAULT);mcHeader.createParagraph().createRun().setText("Membrete");
            XWPFParagraph mcFirst=mcDoc.createParagraph();mcFirst.createRun().setText("Hola ");mcFirst.createRun().setText("mundo");
            XWPFTable mcTable=mcDoc.createTable(1,1);mcTable.getRow(0).getCell(0).getParagraphs().get(0).createRun().setText("Celda");
            MCContinuousDocxEditor mcEditor=new MCContinuousDocxEditor(mcDoc);
            String mcHtml=mcEditor.mcHtml();
            mcHtml=mcHtml.replaceFirst("data-id=\"p1\"", "data-id=\"p1\" data-changed=\"true\"")
                .replace("Hola ","Hola <span class=\"variable\" data-key=\"persona_nombre\">⟦persona_nombre⟧</span> ")
                .replace(">mundo</span>", "><b><u>mundo</u></b></span>")
                .replace("<table data-table", "<p>Segundo párrafo</p><table data-table")
                .replace("Celda", "Celda <span class=\"variable\" data-key=\"otra_persona\">⟦otra_persona⟧</span>")
                .replaceFirst("(<td[^>]*><p data-id=\"[^\"]+\")", "$1 data-changed=\"true\"");
            mcEditor.mcApply("<div>"+mcHtml+"</div>");
            byte[] mcBytes=MCDocumentEngine.mcBytes(mcDoc);
            try(XWPFDocument mcReopened=MCDocumentEngine.mcOpen(mcBytes)){
                assertThat(mcReopened.getHeaderList().get(0).getText()).contains("Membrete");
                assertThat(mcReopened.getParagraphs().stream().map(XWPFParagraph::getText)).contains("Hola {{persona_nombre}} mundo","Segundo párrafo");
                assertThat(mcReopened.getParagraphs().get(0).getRuns().stream().anyMatch(mcR->mcR.isBold()&&mcR.getUnderline()==UnderlinePatterns.SINGLE&&mcR.text().contains("mundo"))).isTrue();
                assertThat(mcReopened.getBodyElements().get(1)).isInstanceOf(XWPFParagraph.class);
                assertThat(mcReopened.getTables().get(0).getText()).contains("Celda {{otra_persona}}");
            }
        }
    }
}
