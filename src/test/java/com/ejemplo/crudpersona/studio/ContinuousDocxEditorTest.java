package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContinuousDocxEditorTest {
    @Test void tabsRoundTripAsOoxmlElementsWithoutChangingSpacesTextOrOrder()throws Exception{
        try(XWPFDocument doc=new XWPFDocument()){
            var p=doc.createParagraph();var run=p.createRun();run.setText("  Antes ");run.addTab();run.setText(" entre  ");run.addTab();run.addTab();run.setText(" {{nombre}} después ");
            var cell=doc.createTable(1,1).getRow(0).getCell(0).getParagraphs().get(0);cell.createRun().setText(" Celda\t final ");
            ContinuousDocxEditor editor=new ContinuousDocxEditor(doc);String tab="<span class=\"docx-tab\" data-tab=\"true\">\t</span>";
            String html=editor.html();assertThat(html).contains(tab);assertThat(html.split("data-tab=",-1)).hasSize(5);
            editor.apply("<div>"+html.replace("data-id=\"","data-changed=\"true\" data-id=\"").replace(" después "," después \t extra ")+"</div>");
            for(int i=0;i<3;i++){
                try(XWPFDocument saved=DocumentEngine.open(DocumentEngine.bytes(doc))){
                    assertThat(saved.getParagraphs().get(0).getText()).isEqualTo("  Antes \t entre  \t\t {{nombre}} después \t extra ");
                    assertThat(saved.getParagraphs().get(0).getRuns().stream().mapToInt(r->r.getCTR().sizeOfTabArray()).sum()).isEqualTo(4);
                    assertThat(saved.getParagraphs().get(0).getRuns().stream().flatMap(r->r.getCTR().getTList().stream()).map(t->t.getStringValue())).allMatch(t->!t.contains("\t"));
                    assertThat(saved.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo(" Celda\t final ");
                }
                html=editor.html();editor.apply("<div>"+html.replace("data-id=\"","data-changed=\"true\" data-id=\"")+"</div>");
            }
            try(XWPFDocument generated=DocumentEngine.open(DocumentEngine.generate(DocumentEngine.bytes(doc),java.util.Map.of("nombre","Ana")))){
                assertThat(generated.getParagraphs().get(0).getText()).isEqualTo("  Antes \t entre  \t\t Ana después \t extra ");
                assertThat(generated.getParagraphs().get(0).getRuns().stream().mapToInt(r->r.getCTR().sizeOfTabArray()).sum()).isEqualTo(4);
            }
        }
    }

    @Test void chipAndContainerFormattingSurviveSavingReloadingAndFinalGeneration()throws Exception{
        try(XWPFDocument doc=new XWPFDocument()){
            doc.createParagraph().createRun().setText("Antes");ContinuousDocxEditor editor=new ContinuousDocxEditor(doc);editor.html();
            editor.apply("""
                <div><p data-id="p1" data-changed="true">Antes <strong><em><u><span style="font-size: 18pt; font-family: Times New Roman; color: rgb(18, 52, 86); background-color: rgb(255, 238, 128)"><span class="variable" data-key="Persona_1.nombre">⟦nombre⟧</span></span></u></em></strong> <span class="variable" data-key="cargo" style="font-weight: 700; font-style: italic; text-decoration: underline; font-size: 24.5pt; font-family: Calibri; color: #aabbcc; background-color: #abcdef">⟦cargo⟧</span> después</p></div>
                """);
            for(int i=0;i<2;i++){
                try(XWPFDocument saved=DocumentEngine.open(DocumentEngine.bytes(doc))){
                    var runs=saved.getParagraphs().get(0).getRuns();var person=runs.stream().filter(r->r.text().equals("{{Persona_1.nombre}}")).findFirst().orElseThrow();var cargo=runs.stream().filter(r->r.text().equals("{{cargo}}")).findFirst().orElseThrow();
                    for(var r:java.util.List.of(person,cargo)){assertThat(r.isBold()).isTrue();assertThat(r.isItalic()).isTrue();assertThat(r.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);}
                    assertThat(person.getFontFamily()).isEqualTo("Times New Roman");assertThat(person.getFontSizeAsDouble()).isEqualTo(18);assertThat(person.getColor()).isEqualTo("123456");
                    assertThat(cargo.getFontFamily()).isEqualTo("Calibri");assertThat(cargo.getFontSizeAsDouble()).isEqualTo(24.5);assertThat(cargo.getColor()).isEqualTo("AABBCC");
                    String html=new ContinuousDocxEditor(saved).html();assertThat(html).contains("font-family:Times New Roman;font-size:18pt;font-weight:bold;font-style:italic;text-decoration:underline;color:#123456;background-color:#FFEE80;","font-family:Calibri;font-size:24.5pt;font-weight:bold;font-style:italic;text-decoration:underline;color:#AABBCC;background-color:#ABCDEF;","data-key=\"Persona_1.nombre\"","data-key=\"cargo\"");
                }
                String html=editor.html();editor.apply("<div>"+html.replace("data-id=\"","data-changed=\"true\" data-id=\"")+"</div>");
            }
            try(XWPFDocument generated=DocumentEngine.open(DocumentEngine.generate(DocumentEngine.bytes(doc),java.util.Map.of("Persona_1.nombre","Ana","cargo","Directora")))){
                var ana=generated.getParagraphs().get(0).getRuns().stream().filter(r->r.text().equals("Ana")).findFirst().orElseThrow();assertThat(ana.isBold()).isTrue();assertThat(ana.getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);assertThat(ana.getFontSizeAsDouble()).isEqualTo(18);assertThat(ana.getColor()).isEqualTo("123456");
            }
        }
    }

    @Test void directChipFormattingCanClearInheritedEmphasisAndReadWordHighlight()throws Exception{
        try(XWPFDocument doc=new XWPFDocument()){
            var r=doc.createParagraph().createRun();r.setText("{{nombre}}");r.setTextHighlightColor("yellow");ContinuousDocxEditor editor=new ContinuousDocxEditor(doc);assertThat(editor.html()).contains("background-color:#FFFF00;");
            editor.apply("<div><p data-id=\"p1\" data-changed=\"true\"><b><i><u><span class=\"variable\" data-key=\"nombre\" style=\"font-weight:normal;font-style:normal;text-decoration:none\">nombre</span></u></i></b></p></div>");
            var result=doc.getParagraphs().get(0).getRuns().get(0);assertThat(result.isBold()).isFalse();assertThat(result.isItalic()).isFalse();assertThat(result.getUnderline()).isEqualTo(UnderlinePatterns.NONE);
        }
    }

    @Test void rendersSplitRunTokensAsOneChipAndRewritesOnlyMovedParagraphs()throws Exception{
        try(XWPFDocument document=new XWPFDocument()){
            XWPFParagraph source=document.createParagraph();source.createRun().setText("{{nom");source.createRun().setText("bre}}");
            XWPFParagraph target=document.createParagraph();target.createRun().setText("Destino ");
            XWPFParagraph untouched=document.createParagraph();untouched.setStyle("Heading1");untouched.createRun().setText("Intacto");String original=untouched.getCTP().xmlText();
            ContinuousDocxEditor editor=new ContinuousDocxEditor(document);String html=editor.html();
            var match=java.util.regex.Pattern.compile("<span class=\"variable\"[^>]*>.*?</span>").matcher(html);assertThat(match.find()).isTrue();String chip=match.group();assertThat(match.find()).isFalse();assertThat(chip).contains("contenteditable=\"false\"","draggable=\"true\"");
            String moved=html.replace(chip,"").replaceFirst("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"").replaceFirst("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\"").replace("Destino ","Destino "+chip);
            editor.apply("<div>"+moved+"</div>");assertThat(source.getText()).isEmpty();assertThat(target.getText()).isEqualTo("Destino {{nombre}}");assertThat(untouched.getCTP().xmlText()).isEqualTo(original);
        }
    }

    @Test void repeatedSyncKeepsRealSpacesAndBlankParagraphsWithoutChipSentinelsOrFillerBreaks()throws Exception{
        try(XWPFDocument doc=new XWPFDocument()){
            doc.createParagraph().createRun().setText("Antes  {{Persona_1.nombre}} después ");doc.createParagraph().createRun().setText("");doc.createParagraph().createRun().setText("Final");
            ContinuousDocxEditor editor=new ContinuousDocxEditor(doc);String html=editor.html().replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"").replace("⟧</span>","⟧</span>\u200B").replace("después </span>","después <br/></span>").replace("<br>","<br/>");
            String normalized=editor.apply("<div>"+html+"</div>");editor.apply(normalized);String again=editor.html().replace("<br>","<br/>");editor.apply("<div>"+again+"</div>");
            assertThat(doc.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  {{Persona_1.nombre}} después ","","Final");
            byte[] filled=DocumentEngine.generate(DocumentEngine.bytes(doc),java.util.Map.of("Persona_1.nombre","Ana"));try(XWPFDocument saved=DocumentEngine.open(filled)){assertThat(saved.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  Ana después ","","Final");}
        }
    }
    @Test void replacementKeepsEveryTextNodeSpaceTabAndBreakInTheOriginalRuns()throws Exception{
        try(XWPFDocument doc=new XWPFDocument()){
            XWPFParagraph p=doc.createParagraph();var r=p.createRun();r.setText("  A{{");r.setText("nombre}}  B ");r.addTab();r.setText(" {{Persona_2.");r.setText("cargo}} ");r.addBreak();r.setText("C{{vacio}}  D  ");doc.createParagraph();
            byte[] source=DocumentEngine.bytes(doc);for(int i=0;i<3;i++){source=DocumentEngine.generate(source,java.util.Map.of("nombre","Ana","Persona_2.cargo","Presidenta"));try(XWPFDocument saved=DocumentEngine.open(source)){assertThat(saved.getParagraphs()).hasSize(2);assertThat(saved.getParagraphs().get(0).getText()).isEqualTo("  AAna  B \t Presidenta \nC  D  ");assertThat(saved.getParagraphs().get(1).getText()).isEmpty();}}
        }
    }
    @Test void readOnlyPreviewUsesInheritedRunFormattingWithoutTurningTokensIntoChips()throws Exception{
        try(XWPFDocument doc=new XWPFDocument()){
            var styles=doc.createStyles();var definition=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory.newInstance();definition.setStyleId("DocumentBody");var props=definition.addNewRPr();props.addNewRFonts().setAscii("Times New Roman");props.addNewSz().setVal(java.math.BigInteger.valueOf(28));props.addNewB();props.addNewI();styles.addStyle(new XWPFStyle(definition));var paragraph=doc.createParagraph();paragraph.setStyle("DocumentBody");paragraph.createRun().setText("Contenido {{nombre}}");String preview=new ContinuousDocxEditor(doc).previewHtml();assertThat(preview).contains("contenteditable=\"false\"","font-family:Times New Roman;font-size:14.0pt;font-weight:bold;font-style:italic;","Contenido {{nombre}}").doesNotContain("class=\"variable\"","id=\"ruler-x\"","contenteditable=\"true\"");
        }
    }
    @Test void ribbonFontSizesListsAndHorizontalRuleSurviveDocxRoundTrip()throws Exception{
        try(XWPFDocument document=new XWPFDocument()){
            for(String text:new String[]{"Viñeta","Numerado","Línea"})document.createParagraph().createRun().setText(text);
            ContinuousDocxEditor editor=new ContinuousDocxEditor(document);String html=editor.html().replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\" data-list=\"bullet\"").replace("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\" data-list=\"decimal\"").replace("data-id=\"p3\"","data-id=\"p3\" data-changed=\"true\" data-rule=\"true\"").replace("font-size:12pt","font-size:24pt");
            editor.apply("<div>"+html+"</div>");
            try(XWPFDocument reopened=DocumentEngine.open(DocumentEngine.bytes(document))){
                assertThat(reopened.getParagraphs().get(0).getNumFmt()).isEqualTo("bullet");assertThat(reopened.getParagraphs().get(1).getNumFmt()).isEqualTo("decimal");assertThat(reopened.getParagraphs().get(0).getRuns().get(0).getFontSize()).isEqualTo(24);assertThat(reopened.getParagraphs().get(2).getBorderBottom()).isEqualTo(Borders.SINGLE);
                ContinuousDocxEditor again=new ContinuousDocxEditor(reopened);String rendered=again.html();assertThat(rendered).contains("data-list=\"bullet\"","data-list=\"decimal\"","data-rule=\"true\"","font-size:24pt");
                String numbered=reopened.getParagraphs().get(1).getCTP().xmlText();again.apply("<div>"+rendered.replace("data-list=\"bullet\"","data-changed=\"true\"")+"</div>");assertThat(reopened.getParagraphs().get(0).getNumID()).isNull();assertThat(reopened.getParagraphs().get(1).getCTP().xmlText()).isEqualTo(numbered);
            }
        }
    }

    @Test void enterClonedBlockIndicesKeepUpperTextAndEveryEmptyParagraphInDomOrder()throws Exception{
        try(XWPFDocument document=new XWPFDocument()){
            XWPFParagraph upper=document.createParagraph();upper.setStyle("Heading1");upper.createRun().setText("Línea superior");XWPFParagraph split=document.createParagraph();split.setStyle("Heading2");split.setAlignment(ParagraphAlignment.CENTER);XWPFRun base=split.createRun();base.setText("Arriba abajo");base.setFontSize(18);base.setBold(true);document.createParagraph().createRun().setText("Última");String upperXml=upper.getCTP().xmlText();
            ContinuousDocxEditor editor=new ContinuousDocxEditor(document);String html=editor.html();String original=html.substring(html.indexOf("<p data-id=\"p2\""),html.indexOf("<p data-id=\"p3\""));
            String first=original.replace("Arriba abajo","Arriba").replace("data-id=\"p2\"","data-id=\"p2\" data-changed=\"true\"");String blank="<p data-block-index=\"p2\" data-id=\"p2\"><br/></p>";String tail="<div data-block-index=\"p2\" data-id=\"p2\">abajo</div>";
            editor.apply("<div>"+html.replace(original,first+blank+blank+tail)+"</div>");
            try(XWPFDocument saved=DocumentEngine.open(DocumentEngine.bytes(document))){assertThat(saved.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Línea superior","Arriba","","","abajo","Última");assertThat(upper.getCTP().xmlText()).isEqualTo(upperXml);assertThat(saved.getParagraphs().get(4).getRuns().get(0).getFontSize()).isEqualTo(18);assertThat(saved.getParagraphs().get(4).getRuns().get(0).isBold()).isTrue();for(int i=2;i<=4;i++){assertThat(saved.getParagraphs().get(i).getStyle()).isEqualTo("Heading2");assertThat(saved.getParagraphs().get(i).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);}}
        }
    }

    @Test void newEmptyAndListBlocksAfterATableKeepTheirExactPositionAndRepeatedSaves()throws Exception{
        try(XWPFDocument document=new XWPFDocument()){
            document.createParagraph().createRun().setText("Antes");document.createTable(1,1).getRow(0).getCell(0).setText("Tabla");document.createParagraph().createRun().setText("Después");ContinuousDocxEditor editor=new ContinuousDocxEditor(document);String html=editor.html();String inserted=html.replace("</table>","</table><p data-new-block=\"true\"><br/></p><li data-new-block=\"true\">Nuevo</li><div data-new-block=\"true\"><br/></div>");
            editor.apply("<div>"+inserted+"</div>");String once=editor.html().replace("<br>","<br/>");editor.apply("<div>"+once+"</div>");
            assertThat(document.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes","","Nuevo","","Después");assertThat(document.getBodyElements().get(1)).isInstanceOf(XWPFTable.class);try(XWPFDocument saved=DocumentEngine.open(DocumentEngine.bytes(document))){assertThat(saved.getTables().get(0).getText()).contains("Tabla");}
        }
    }

    @Test void qualifiedChipsKeepTheirPersonAndRenderItsRoleAcrossSplitRuns()throws Exception{
        try(XWPFDocument doc=new XWPFDocument()){var p=doc.createParagraph();p.createRun().setText("{{Persona_1.");p.createRun().setText("DNI}} {{Persona_2.Cargo}}");ContinuousDocxEditor editor=new ContinuousDocxEditor(doc,java.util.Map.of("Persona_1","Solicitante","Persona_2","Dirigido"));String html=editor.html();assertThat(html).contains("⟦Solicitante · DNI⟧","⟦Dirigido · Cargo⟧","data-persona=\"Persona_1\"","data-key=\"Persona_2.Cargo\"");editor.apply("<div>"+html.replace("data-id=\"p1\"","data-id=\"p1\" data-changed=\"true\"")+"</div>");assertThat(p.getText()).isEqualTo("{{Persona_1.DNI}} {{Persona_2.Cargo}}");}
    }
    @Test void createsRowsAndDeletesWholeTableWithoutLeavingEmptyContent() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            doc.createParagraph().createRun().setText("Antes");ContinuousDocxEditor first=new ContinuousDocxEditor(doc);
            String html=first.html()+"<table data-borders=\"all\" data-border-width=\"1\" data-border-color=\"#8c959f\"><tr><td><p>Uno</p></td><td><p>Dos</p></td></tr></table>";
            first.apply("<div>"+html+"</div>");
            try(XWPFDocument withFirstTable=DocumentEngine.open(DocumentEngine.bytes(doc))){
                assertThat(withFirstTable.getTables()).hasSize(1);assertThat(withFirstTable.getTables().get(0).getText()).contains("Uno","Dos");
                ContinuousDocxEditor second=new ContinuousDocxEditor(withFirstTable);String withTable=second.html();
                String added=withTable.replace("</table>","<tr><td><p>Tres</p></td><td><p>Cuatro</p></td></tr></table>").replaceFirst("<table ","<table data-changed=\"true\" ");
                second.apply("<div>"+added+"</div>");
                try(XWPFDocument withSecondRow=DocumentEngine.open(DocumentEngine.bytes(withFirstTable))){
                    assertThat(withSecondRow.getTables().get(0).getNumberOfRows()).isEqualTo(2);assertThat(withSecondRow.getTables().get(0).getRow(1).getCell(1).getText()).contains("Cuatro");
                    ContinuousDocxEditor third=new ContinuousDocxEditor(withSecondRow);String removed=third.html().replaceAll("(?s)<table[^>]*>.*?</table>","");third.apply("<div>"+removed+"</div>");
                    try(XWPFDocument reopened=DocumentEngine.open(DocumentEngine.bytes(withSecondRow))){assertThat(reopened.getTables()).isEmpty();assertThat(reopened.getParagraphs()).hasSize(1);assertThat(reopened.getParagraphs().get(0).getText()).isEqualTo("Antes");}
                }
            }
        }
    }
    @Test void keepsUnchangedStructureAndSavesContinuousParagraphsAndVariables() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            XWPFHeader header=doc.createHeader(HeaderFooterType.DEFAULT);header.createParagraph().createRun().setText("Membrete");
            XWPFParagraph first=doc.createParagraph();first.createRun().setText("Hola ");first.createRun().setText("mundo");
            XWPFTable table=doc.createTable(1,1);table.getRow(0).getCell(0).getParagraphs().get(0).createRun().setText("Celda");
            ContinuousDocxEditor editor=new ContinuousDocxEditor(doc);
            String html=editor.html();
            html=html.replaceFirst("data-id=\"p1\"", "data-id=\"p1\" data-changed=\"true\"")
                .replace("Hola ","Hola <span class=\"variable\" data-key=\"persona_nombre\">⟦persona_nombre⟧</span> ")
                .replace(">mundo</span>", "><b><u>mundo</u></b></span>")
                .replace("<table data-table", "<p>Segundo párrafo</p><table data-table")
                .replace("Celda", "Celda <span class=\"variable\" data-key=\"otra_persona\">⟦otra_persona⟧</span>")
                .replaceFirst("(<td[^>]*><p data-id=\"[^\"]+\")", "$1 data-changed=\"true\"");
            editor.apply("<div>"+html+"</div>");
            byte[] bytes=DocumentEngine.bytes(doc);
            try(XWPFDocument reopened=DocumentEngine.open(bytes)){
                assertThat(reopened.getHeaderList().get(0).getText()).contains("Membrete");
                assertThat(reopened.getParagraphs().stream().map(XWPFParagraph::getText)).contains("Hola {{persona_nombre}} mundo","Segundo párrafo");
                assertThat(reopened.getParagraphs().get(0).getRuns().stream().anyMatch(r->r.isBold()&&r.getUnderline()==UnderlinePatterns.SINGLE&&r.text().contains("mundo"))).isTrue();
                assertThat(reopened.getBodyElements().get(1)).isInstanceOf(XWPFParagraph.class);
                assertThat(reopened.getTables().get(0).getText()).contains("Celda {{otra_persona}}");
            }
        }
    }
}
