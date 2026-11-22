package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MCContinuousDocxEditorTest {
    @Test void MCtabsRoundTripAsOoxmlElementsWithoutChangingSpacesTextOrOrder()throws Exception{
        try(XWPFDocument MCdoc=new XWPFDocument()){
            var MCp=MCdoc.createParagraph();var MCrun=MCp.createRun();MCrun.setText("  Antes ");MCrun.addTab();MCrun.setText(" entre  ");MCrun.addTab();MCrun.addTab();MCrun.setText(" {{nombre}} después ");
            var MCcell=MCdoc.createTable(1,1).getRow(0).getCell(0).getParagraphs().get(0);MCcell.createRun().setText(" Celda\t final ");
            MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdoc);String MCtab="<span class=\"docx-tab\" data-tab=\"true\">\t</span>";
            String MChtml=MCeditor.MChtml();assertThat(MChtml).contains(MCtab);assertThat(MChtml.split("data-tab=",-1)).hasSize(5);
            MCeditor.MCapply("<div>"+MChtml.replace("data-id=\"","data-changed=\"true\" data-id=\"").replace(" después "," después \t extra ")+"</div>");
            for(int MCi=0;MCi<3;MCi++){
                try(XWPFDocument MCsaved=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCdoc))){
                    assertThat(MCsaved.getParagraphs().get(0).getText()).isEqualTo("  Antes \t entre  \t\t {{nombre}} después \t extra ");
                    assertThat(MCsaved.getParagraphs().get(0).getRuns().stream().mapToInt(MCr->MCr.getCTR().sizeOfTabArray()).sum()).isEqualTo(4);
                    assertThat(MCsaved.getParagraphs().get(0).getRuns().stream().flatMap(MCr->MCr.getCTR().getTList().stream()).map(MCt->MCt.getStringValue())).allMatch(MCt->!MCt.contains("\t"));
                    assertThat(MCsaved.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo(" Celda\t final ");
                }
                MChtml=MCeditor.MChtml();MCeditor.MCapply("<div>"+MChtml.replace("data-id=\"","data-changed=\"true\" data-id=\"")+"</div>");
            }
            try(XWPFDocument MCgenerated=MCDocumentEngine.MCopen(MCDocumentEngine.MCgenerate(MCDocumentEngine.MCbytes(MCdoc),java.util.Map.of("nombre","Ana")))){
                assertThat(MCgenerated.getParagraphs().get(0).getText()).isEqualTo("  Antes \t entre  \t\t Ana después \t extra ");
                assertThat(MCgenerated.getParagraphs().get(0).getRuns().stream().mapToInt(MCr->MCr.getCTR().sizeOfTabArray()).sum()).isEqualTo(4);
            }
        }
    }

    @Test void MCchipAndContainerFormattingSurviveSavingReloadingAndFinalGeneration()throws Exception{
        try(XWPFDocument MCdoc=new XWPFDocument()){
            MCdoc.createParagraph().createRun().setText("Antes");MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdoc);MCeditor.MChtml();
            MCeditor.MCapply("""
                <div><p data-id="p1" data-changed="true">Antes <strong><em><u><span style="font-size: 18pt; font-family: Times New Roman; color: rgb(18, 52, 86); background-color: rgb(255, 238, 128)"><span class="variable" data-key="Persona_1.nombre">⟦nombre⟧</span></span></u></em></strong> <span class="variable" data-key="cargo" style="font-weight: 700; font-style: italic; text-decoration: underline; font-size: 24.5pt; font-family: Calibri; color: #aabbcc; background-color: #abcdef">⟦cargo⟧</span> después</p></div>
                """);
            for(int MCi=0;MCi<2;MCi++){
                try(XWPFDocument MCsaved=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCdoc))){
                    var MCruns=MCsaved.getParagraphs().get(0).getRuns();var MCperson=MCruns.stream().filter(MCr->MCr.text().equals("{{Persona_1.nombre}}")).findFirst().orElseThrow();var MCcargo=MCruns.stream().filter(MCr->MCr.text().equals("{{cargo}}")).findFirst().orElseThrow();
                    for(var MCr:java.util.List.of(MCperson,MCcargo)){assertThat(MCr.isBold()).isTrue();assertThat(MCr.isItalic()).isTrue();assertThat(MCr.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);}
                    assertThat(MCperson.getFontFamily()).isEqualTo("Times New Roman");assertThat(MCperson.getFontSizeAsDouble()).isEqualTo(18);assertThat(MCperson.getColor()).isEqualTo("123456");
                    assertThat(MCcargo.getFontFamily()).isEqualTo("Calibri");assertThat(MCcargo.getFontSizeAsDouble()).isEqualTo(24.5);assertThat(MCcargo.getColor()).isEqualTo("AABBCC");
                    String MChtml=new MCContinuousDocxEditor(MCsaved).MChtml();assertThat(MChtml).contains("font-family:Times New Roman;font-size:18pt;font-weight:bold;font-style:italic;text-decoration:underline;color:#123456;background-color:#FFEE80;","font-family:Calibri;font-size:24.5pt;font-weight:bold;font-style:italic;text-decoration:underline;color:#AABBCC;background-color:#ABCDEF;","data-key=\"Persona_1.nombre\"","data-key=\"cargo\"");
                }
                String MChtml=MCeditor.MChtml();MCeditor.MCapply("<div>"+MChtml.replace("data-id=\"","data-changed=\"true\" data-id=\"")+"</div>");
            }
            try(XWPFDocument MCgenerated=MCDocumentEngine.MCopen(MCDocumentEngine.MCgenerate(MCDocumentEngine.MCbytes(MCdoc),java.util.Map.of("Persona_1.nombre","Ana","cargo","Directora")))){
                var MCana=MCgenerated.getParagraphs().get(0).getRuns().stream().filter(MCr->MCr.text().equals("Ana")).findFirst().orElseThrow();assertThat(MCana.isBold()).isTrue();assertThat(MCana.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);assertThat(MCana.getFontSizeAsDouble()).isEqualTo(18);assertThat(MCana.getColor()).isEqualTo("123456");
            }
        }
    }

    @Test void MCdirectChipFormattingCanClearInheritedEmphasisAndReadWordHighlight()throws Exception{
        try(XWPFDocument MCdoc=new XWPFDocument()){
            var MCr=MCdoc.createParagraph().createRun();MCr.setText("{{nombre}}");MCr.setTextHighlightColor("yellow");MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdoc);assertThat(MCeditor.MChtml()).contains("background-color:#FFFF00;");
            MCeditor.MCapply("<div><p data-id=\"p1\" data-changed=\"true\"><b><i><u><span class=\"variable\" data-key=\"nombre\" style=\"font-weight:normal;font-style:normal;text-decoration:none\">nombre</span></u></i></b></p></div>");
            var MCresult=MCdoc.getParagraphs().get(0).getRuns().get(0);assertThat(MCresult.isBold()).isFalse();assertThat(MCresult.isItalic()).isFalse();assertThat(MCresult.getUnderline()).isEqualTo(UnderlinePatterns.NONE);
        }
    }

    @Test void MCrendersSplitRunTokensAsOneChipAndRewritesOnlyMovedParagraphs()throws Exception{
        try(XWPFDocument MCdocument=new XWPFDocument()){
            XWPFParagraph MCsource=MCdocument.createParagraph();MCsource.createRun().setText("{{nom");MCsource.createRun().setText("bre}}");
            XWPFParagraph MCtarget=MCdocument.createParagraph();MCtarget.createRun().setText("Destino ");
            XWPFParagraph MCuntouched=MCdocument.createParagraph();MCuntouched.setStyle("Heading1");MCuntouched.createRun().setText("Intacto");String MCoriginal=MCuntouched.getCTP().xmlText();
            MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdocument);String MChtml=MCeditor.MChtml();
            var MCmatch=java.util.regex.Pattern.compile("<span class=\"variable\"[^>]*>.*?</span>").matcher(MChtml);assertThat(MCmatch.find()).isTrue();String MCchip=MCmatch.group();assertThat(MCmatch.find()).isFalse();assertThat(MCchip).contains("contenteditable=\"false\"","draggable=\"true\"");
            String MCmoved=MChtml.replace(MCchip,"").replaceFirst("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"").replaceFirst("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\"").replace("Destino ","Destino "+MCchip);
            MCeditor.MCapply("<div>"+MCmoved+"</div>");assertThat(MCsource.getText()).isEmpty();assertThat(MCtarget.getText()).isEqualTo("Destino {{nombre}}");assertThat(MCuntouched.getCTP().xmlText()).isEqualTo(MCoriginal);
        }
    }

    @Test void MCrepeatedSyncKeepsRealSpacesAndBlankParagraphsWithoutChipSentinelsOrFillerBreaks()throws Exception{
        try(XWPFDocument MCdoc=new XWPFDocument()){
            MCdoc.createParagraph().createRun().setText("Antes  {{Persona_1.nombre}} después ");MCdoc.createParagraph().createRun().setText("");MCdoc.createParagraph().createRun().setText("Final");
            MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdoc);String MChtml=MCeditor.MChtml().replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"").replace("⟧</span>","⟧</span>\u200B").replace("después </span>","después <br/></span>").replace("<br>","<br/>");
            String MCnormalized=MCeditor.MCapply("<div>"+MChtml+"</div>");MCeditor.MCapply(MCnormalized);String MCagain=MCeditor.MChtml().replace("<br>","<br/>");MCeditor.MCapply("<div>"+MCagain+"</div>");
            assertThat(MCdoc.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  {{Persona_1.nombre}} después ","","Final");
            byte[] MCfilled=MCDocumentEngine.MCgenerate(MCDocumentEngine.MCbytes(MCdoc),java.util.Map.of("Persona_1.nombre","Ana"));try(XWPFDocument MCsaved=MCDocumentEngine.MCopen(MCfilled)){assertThat(MCsaved.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  Ana después ","","Final");}
        }
    }
    @Test void MCreplacementKeepsEveryTextNodeSpaceTabAndBreakInTheOriginalRuns()throws Exception{
        try(XWPFDocument MCdoc=new XWPFDocument()){
            XWPFParagraph MCp=MCdoc.createParagraph();var MCr=MCp.createRun();MCr.setText("  A{{");MCr.setText("nombre}}  B ");MCr.addTab();MCr.setText(" {{Persona_2.");MCr.setText("cargo}} ");MCr.addBreak();MCr.setText("C{{vacio}}  D  ");MCdoc.createParagraph();
            byte[] MCsource=MCDocumentEngine.MCbytes(MCdoc);for(int MCi=0;MCi<3;MCi++){MCsource=MCDocumentEngine.MCgenerate(MCsource,java.util.Map.of("nombre","Ana","Persona_2.cargo","Presidenta"));try(XWPFDocument MCsaved=MCDocumentEngine.MCopen(MCsource)){assertThat(MCsaved.getParagraphs()).hasSize(2);assertThat(MCsaved.getParagraphs().get(0).getText()).isEqualTo("  AAna  B \t Presidenta \nC  D  ");assertThat(MCsaved.getParagraphs().get(1).getText()).isEmpty();}}
        }
    }
    @Test void MCreadOnlyPreviewUsesInheritedRunFormattingWithoutTurningTokensIntoChips()throws Exception{
        try(XWPFDocument MCdoc=new XWPFDocument()){
            var MCstyles=MCdoc.createStyles();var MCdefinition=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory.newInstance();MCdefinition.setStyleId("DocumentBody");var MCprops=MCdefinition.addNewRPr();MCprops.addNewRFonts().setAscii("Times New Roman");MCprops.addNewSz().setVal(java.math.BigInteger.valueOf(28));MCprops.addNewB();MCprops.addNewI();MCstyles.addStyle(new XWPFStyle(MCdefinition));var MCparagraph=MCdoc.createParagraph();MCparagraph.setStyle("DocumentBody");MCparagraph.createRun().setText("Contenido {{nombre}}");String MCpreview=new MCContinuousDocxEditor(MCdoc).MCpreviewHtml();assertThat(MCpreview).contains("contenteditable=\"false\"","font-family:Times New Roman;font-size:14.0pt;font-weight:bold;font-style:italic;","Contenido {{nombre}}").doesNotContain("class=\"variable\"","id=\"ruler-x\"","contenteditable=\"true\"");
        }
    }
    @Test void MCribbonFontSizesListsAndHorizontalRuleSurviveDocxRoundTrip()throws Exception{
        try(XWPFDocument MCdocument=new XWPFDocument()){
            for(String MCtext:new String[]{"Viñeta","Numerado","Línea"})MCdocument.createParagraph().createRun().setText(MCtext);
            MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdocument);String MChtml=MCeditor.MChtml().replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\" data-list=\"bullet\"").replace("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\" data-list=\"decimal\"").replace("data-id=\"p3\"","data-id=\"p3\" data-changed=\"true\" data-rule=\"true\"").replace("font-size:12pt","font-size:24pt");
            MCeditor.MCapply("<div>"+MChtml+"</div>");
            try(XWPFDocument MCreopened=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCdocument))){
                assertThat(MCreopened.getParagraphs().get(0).getNumFmt()).isEqualTo("bullet");assertThat(MCreopened.getParagraphs().get(1).getNumFmt()).isEqualTo("decimal");assertThat(MCreopened.getParagraphs().get(0).getRuns().get(0).getFontSize()).isEqualTo(24);assertThat(MCreopened.getParagraphs().get(2).getBorderBottom()).isEqualTo(Borders.SINGLE);
                MCContinuousDocxEditor MCagain=new MCContinuousDocxEditor(MCreopened);String MCrendered=MCagain.MChtml();assertThat(MCrendered).contains("data-list=\"bullet\"","data-list=\"decimal\"","data-rule=\"true\"","font-size:24pt");
                String MCnumbered=MCreopened.getParagraphs().get(1).getCTP().xmlText();MCagain.MCapply("<div>"+MCrendered.replace("data-list=\"bullet\"","data-changed=\"true\"")+"</div>");assertThat(MCreopened.getParagraphs().get(0).getNumID()).isNull();assertThat(MCreopened.getParagraphs().get(1).getCTP().xmlText()).isEqualTo(MCnumbered);
            }
        }
    }

    @Test void MCenterClonedBlockIndicesKeepUpperTextAndEveryEmptyParagraphInDomOrder()throws Exception{
        try(XWPFDocument MCdocument=new XWPFDocument()){
            XWPFParagraph MCupper=MCdocument.createParagraph();MCupper.setStyle("Heading1");MCupper.createRun().setText("Línea superior");XWPFParagraph MCsplit=MCdocument.createParagraph();MCsplit.setStyle("Heading2");MCsplit.setAlignment(ParagraphAlignment.CENTER);XWPFRun MCbase=MCsplit.createRun();MCbase.setText("Arriba abajo");MCbase.setFontSize(18);MCbase.setBold(true);MCdocument.createParagraph().createRun().setText("Última");String MCupperXml=MCupper.getCTP().xmlText();
            MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdocument);String MChtml=MCeditor.MChtml();String MCoriginal=MChtml.substring(MChtml.indexOf("<p data-id=\"p2\""),MChtml.indexOf("<p data-id=\"p3\""));
            String MCfirst=MCoriginal.replace("Arriba abajo","Arriba").replace("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\"");String MCblank="<p data-block-index=\"p2\" data-id=\"p2\"><br/></p>";String MCtail="<div data-block-index=\"p2\" data-id=\"p2\">abajo</div>";
            MCeditor.MCapply("<div>"+MChtml.replace(MCoriginal,MCfirst+MCblank+MCblank+MCtail)+"</div>");
            try(XWPFDocument MCsaved=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCdocument))){assertThat(MCsaved.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Línea superior","Arriba","","","abajo","Última");assertThat(MCupper.getCTP().xmlText()).isEqualTo(MCupperXml);assertThat(MCsaved.getParagraphs().get(4).getRuns().get(0).getFontSize()).isEqualTo(18);assertThat(MCsaved.getParagraphs().get(4).getRuns().get(0).isBold()).isTrue();for(int MCi=2;MCi<=4;MCi++){assertThat(MCsaved.getParagraphs().get(MCi).getStyle()).isEqualTo("Heading2");assertThat(MCsaved.getParagraphs().get(MCi).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);}}
        }
    }

    @Test void MCnewEmptyAndListBlocksAfterATableKeepTheirExactPositionAndRepeatedSaves()throws Exception{
        try(XWPFDocument MCdocument=new XWPFDocument()){
            MCdocument.createParagraph().createRun().setText("Antes");MCdocument.createTable(1,1).getRow(0).getCell(0).setText("Tabla");MCdocument.createParagraph().createRun().setText("Después");MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdocument);String MChtml=MCeditor.MChtml();String MCinserted=MChtml.replace("</table>","</table><p data-new-block=\"true\"><br/></p><li data-new-block=\"true\">Nuevo</li><div data-new-block=\"true\"><br/></div>");
            MCeditor.MCapply("<div>"+MCinserted+"</div>");String MConce=MCeditor.MChtml().replace("<br>","<br/>");MCeditor.MCapply("<div>"+MConce+"</div>");
            assertThat(MCdocument.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes","","Nuevo","","Después");assertThat(MCdocument.getBodyElements().get(1)).isInstanceOf(XWPFTable.class);try(XWPFDocument MCsaved=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCdocument))){assertThat(MCsaved.getTables().get(0).getText()).contains("Tabla");}
        }
    }

    @Test void MCqualifiedChipsKeepTheirPersonAndRenderItsRoleAcrossSplitRuns()throws Exception{
        try(XWPFDocument MCdoc=new XWPFDocument()){var MCp=MCdoc.createParagraph();MCp.createRun().setText("{{Persona_1.");MCp.createRun().setText("DNI}} {{Persona_2.Cargo}}");MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdoc,java.util.Map.of("Persona_1","Solicitante","Persona_2","Dirigido"));String MChtml=MCeditor.MChtml();assertThat(MChtml).contains("⟦Solicitante · DNI⟧","⟦Dirigido · Cargo⟧","data-persona=\"Persona_1\"","data-key=\"Persona_2.Cargo\"");MCeditor.MCapply("<div>"+MChtml.replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"")+"</div>");assertThat(MCp.getText()).isEqualTo("{{Persona_1.DNI}} {{Persona_2.Cargo}}");}
    }
    @Test void MCcreatesRowsAndDeletesWholeTableWithoutLeavingEmptyContent() throws Exception {
        try(XWPFDocument MCdoc=new XWPFDocument()){
            MCdoc.createParagraph().createRun().setText("Antes");MCContinuousDocxEditor MCfirst=new MCContinuousDocxEditor(MCdoc);
            String MChtml=MCfirst.MChtml()+"<table data-borders=\"all\" data-border-width=\"1\" data-border-color=\"#8c959f\"><tr><td><p>Uno</p></td><td><p>Dos</p></td></tr></table>";
            MCfirst.MCapply("<div>"+MChtml+"</div>");
            try(XWPFDocument MCwithFirstTable=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCdoc))){
                assertThat(MCwithFirstTable.getTables()).hasSize(1);assertThat(MCwithFirstTable.getTables().get(0).getText()).contains("Uno","Dos");
                MCContinuousDocxEditor MCsecond=new MCContinuousDocxEditor(MCwithFirstTable);String MCwithTable=MCsecond.MChtml();
                String MCadded=MCwithTable.replace("</table>","<tr><td><p>Tres</p></td><td><p>Cuatro</p></td></tr></table>").replaceFirst("<table ","<table data-changed=\"true\" ");
                MCsecond.MCapply("<div>"+MCadded+"</div>");
                try(XWPFDocument MCwithSecondRow=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCwithFirstTable))){
                    assertThat(MCwithSecondRow.getTables().get(0).getNumberOfRows()).isEqualTo(2);assertThat(MCwithSecondRow.getTables().get(0).getRow(1).getCell(1).getText()).contains("Cuatro");
                    MCContinuousDocxEditor MCthird=new MCContinuousDocxEditor(MCwithSecondRow);String MCremoved=MCthird.MChtml().replaceAll("(?s)<table[^>]*>.*?</table>","");MCthird.MCapply("<div>"+MCremoved+"</div>");
                    try(XWPFDocument MCreopened=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCwithSecondRow))){assertThat(MCreopened.getTables()).isEmpty();assertThat(MCreopened.getParagraphs()).hasSize(1);assertThat(MCreopened.getParagraphs().get(0).getText()).isEqualTo("Antes");}
                }
            }
        }
    }
    @Test void MCkeepsUnchangedStructureAndSavesContinuousParagraphsAndVariables() throws Exception {
        try(XWPFDocument MCdoc=new XWPFDocument()){
            XWPFHeader MCheader=MCdoc.createHeader(HeaderFooterType.DEFAULT);MCheader.createParagraph().createRun().setText("Membrete");
            XWPFParagraph MCfirst=MCdoc.createParagraph();MCfirst.createRun().setText("Hola ");MCfirst.createRun().setText("mundo");
            XWPFTable MCtable=MCdoc.createTable(1,1);MCtable.getRow(0).getCell(0).getParagraphs().get(0).createRun().setText("Celda");
            MCContinuousDocxEditor MCeditor=new MCContinuousDocxEditor(MCdoc);
            String MChtml=MCeditor.MChtml();
            MChtml=MChtml.replaceFirst("data-id=\"p1\"", "data-id=\"p1\" data-changed=\"true\"")
                .replace("Hola ","Hola <span class=\"variable\" data-key=\"persona_nombre\">⟦persona_nombre⟧</span> ")
                .replace(">mundo</span>", "><b><u>mundo</u></b></span>")
                .replace("<table data-table", "<p>Segundo párrafo</p><table data-table")
                .replace("Celda", "Celda <span class=\"variable\" data-key=\"otra_persona\">⟦otra_persona⟧</span>")
                .replaceFirst("(<td[^>]*><p data-id=\"[^\"]+\")", "$1 data-changed=\"true\"");
            MCeditor.MCapply("<div>"+MChtml+"</div>");
            byte[] MCbytes=MCDocumentEngine.MCbytes(MCdoc);
            try(XWPFDocument MCreopened=MCDocumentEngine.MCopen(MCbytes)){
                assertThat(MCreopened.getHeaderList().get(0).getText()).contains("Membrete");
                assertThat(MCreopened.getParagraphs().stream().map(XWPFParagraph::getText)).contains("Hola {{persona_nombre}} mundo","Segundo párrafo");
                assertThat(MCreopened.getParagraphs().get(0).getRuns().stream().anyMatch(MCr->MCr.isBold()&&MCr.getUnderline()==UnderlinePatterns.SINGLE&&MCr.text().contains("mundo"))).isTrue();
                assertThat(MCreopened.getBodyElements().get(1)).isInstanceOf(XWPFParagraph.class);
                assertThat(MCreopened.getTables().get(0).getText()).contains("Celda {{otra_persona}}");
            }
        }
    }
}
