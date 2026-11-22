package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** @author MC */
final class ContinuousDocxEditor {
    private static final Pattern TOKEN=Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");
    private final XWPFDocument document;
    private final Map<String,XWPFParagraph> paragraphs=new LinkedHashMap<>();
    private final Map<String,XWPFTableCell> cells=new LinkedHashMap<>();
    private final Map<String,XWPFTable> tables=new LinkedHashMap<>();
    private final Map<String,IBody> parts=new LinkedHashMap<>();
    private int nextId;
    private boolean readOnly;
    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr previewDefaults;
    private final Map<String,java.math.BigInteger> listNumbers=new HashMap<>();

    private final Map<String,String> personaNames;
    ContinuousDocxEditor(XWPFDocument document){this(document,Map.of());}
    ContinuousDocxEditor(XWPFDocument document,Map<String,String> personaNames){this.document=document;this.personaNames=Map.copyOf(personaNames);}

    String generationHtml()throws IOException{
        try(InputStream input=Objects.requireNonNull(getClass().getResourceAsStream("/editor/document-editor.html"))){
            return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
                    .replace("__DOCUMENT_CONTENT__",html());
        }
    }

    String html(){
        paragraphs.clear();cells.clear();tables.clear();parts.clear();nextId=0;
        StringBuilder out=new StringBuilder();
        for(XWPFHeader h:document.getHeaderList())for(XWPFParagraph p:h.getParagraphs())out.append(paragraph(p));
        for(IBodyElement element:document.getBodyElements()){
            if(element instanceof XWPFParagraph p)out.append(paragraph(p));
            else if(element instanceof XWPFTable t)out.append(table(t));
        }
        for(XWPFFooter f:document.getFooterList())for(XWPFParagraph p:f.getParagraphs())out.append(paragraph(p));
        if(out.length()==0)out.append("<p><br></p>");
        return out.toString();
    }

    String previewHtml(){
        readOnly=true;
        if(document.getStyles()!=null&&document.getStyles().getPackagePart()!=null)try(InputStream in=document.getStyles().getPackagePart().getInputStream()){
            var styles=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles.Factory.parse(in);if(styles.getDocDefaults()!=null&&styles.getDocDefaults().getRPrDefault()!=null)previewDefaults=styles.getDocDefaults().getRPrDefault().getRPr();
        }catch(Exception ignored){}
        try{
            var section=document.getDocument().getBody().getSectPr();double width=794,height=1123,left=96,right=96,top=96,bottom=96,header=48,footer=48;
            if(section!=null){
                if(section.isSetPgSz()){width=((Number)section.getPgSz().getW()).doubleValue()/15; height=((Number)section.getPgSz().getH()).doubleValue()/15;}
                if(section.isSetPgMar()){var m=section.getPgMar();left=twips(m.getLeft(),left);right=twips(m.getRight(),right);top=twips(m.getTop(),top);bottom=twips(m.getBottom(),bottom);header=twips(m.getHeader(),header);footer=twips(m.getFooter(),footer);}
            }
            StringBuilder body=new StringBuilder(),head=new StringBuilder(),foot=new StringBuilder();
            renderBody(document.getBodyElements(),body);
            if(!document.getHeaderList().isEmpty())renderBody(document.getHeaderList().get(0).getBodyElements(),head);
            if(!document.getFooterList().isEmpty())renderBody(document.getFooterList().get(0).getBodyElements(),foot);
            String defaults="font-family:"+esc(defaultFont())+";font-size:"+defaultSize()+"pt;";
            String css="html,body{margin:0;padding:0;background:#f6f8fa;color:#000}*{box-sizing:border-box}#pages{position:relative;width:100%;overflow:hidden}.page-slot{position:relative;margin:0 auto 12px}.doc-page{position:absolute;top:0;left:0;background:#fff;transform-origin:top left;width:"+width+"px;height:"+height+"px;"+defaults+"}.page-body{position:absolute;left:"+left+"px;top:"+top+"px;width:"+(width-left-right)+"px;height:"+(height-top-bottom)+"px;overflow:hidden}.doc-flow{width:"+(width-left-right)+"px;height:"+(height-top-bottom)+"px;column-width:"+(width-left-right)+"px;column-gap:0;column-fill:auto;overflow:visible;"+defaults+"}.doc-header,.doc-footer{position:absolute;left:"+left+"px;right:"+right+"px}.doc-header{top:"+header+"px}.doc-footer{bottom:"+footer+"px}p{white-space:pre-wrap;overflow-wrap:break-word;margin:0;min-height:1em;line-height:normal}table{border-collapse:collapse;max-width:100%}td{vertical-align:top;padding:0 5px}tr{break-inside:avoid}img{max-width:100%}.page-break{break-before:column;display:block}p[data-page-break]{break-before:column}p[data-list]{display:list-item;margin-left:24px}p[data-list=bullet]{list-style-type:disc}p[data-list=decimal]{list-style-type:decimal}#measure{position:absolute;left:-100000px;top:0;visibility:hidden}";
            return "<!doctype html><html><head><meta charset=\"utf-8\"><style>"+css+"</style></head><body contenteditable=\"false\"><div id=\"measure\"><div class=\"doc-flow\">"+body+"</div></div><div id=\"pages\"></div><template id=\"header\">"+head+"</template><template id=\"footer\">"+foot+"</template><script>"+
                "(function(){var flow=document.querySelector('#measure .doc-flow'),pages=document.getElementById('pages'),w="+width+",h="+height+",cw="+(width-left-right)+";"+
                "function fit(){var scale=Math.min(1,document.documentElement.clientWidth/w);Array.from(pages.children).forEach(function(slot){slot.style.width=w*scale+'px';slot.style.height=h*scale+'px';slot.firstChild.style.transform='scale('+scale+')'})}"+
                "function render(){pages.innerHTML='';var count=Math.max(1,Math.ceil(flow.scrollWidth/cw));for(var i=0;i<count;i++){var slot=document.createElement('div');slot.className='page-slot';var page=document.createElement('article');page.className='doc-page';var viewport=document.createElement('div');viewport.className='page-body';var copy=flow.cloneNode(true);copy.style.position='relative';copy.style.left=(-i*cw)+'px';viewport.appendChild(copy);page.appendChild(viewport);['header','footer'].forEach(function(part){var el=document.createElement('div');el.className='doc-'+part;el.innerHTML=document.getElementById(part).innerHTML;page.appendChild(el)});slot.appendChild(page);pages.appendChild(slot)}fit();window.scrollTo(0,0);document.documentElement.scrollTop=0;document.body.scrollTop=0;window.previewReady=true}"+
                "window.addEventListener('resize',fit);window.addEventListener('load',render);})();</script></body></html>";
        }finally{readOnly=false;}
    }
    private void renderBody(List<IBodyElement> elements,StringBuilder out){for(IBodyElement element:elements){if(element instanceof XWPFParagraph p)out.append(paragraph(p));else if(element instanceof XWPFTable t)out.append(table(t));}}
    private static double twips(Object value,double fallback){return value instanceof Number n?n.doubleValue()/15:fallback;}
    private String defaultFont(){String font=runAttribute(previewDefaults,"rFonts","ascii");return font==null?"Calibri":font;}
    private static final String WORD_NS="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static String runAttribute(org.apache.xmlbeans.XmlObject props,String name,String attribute){
        if(props==null)return null;for(Node node=props.getDomNode().getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element e&&name.equals(e.getLocalName()))return e.hasAttributeNS(WORD_NS,attribute)?e.getAttributeNS(WORD_NS,attribute):"true";return null;
    }
    private List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> runProperties(XWPFParagraph paragraph,XWPFRun run){
        List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> out=new ArrayList<>();if(run.getCTR().isSetRPr())out.add(run.getCTR().getRPr());
        var styles=document.getStyles();if(styles!=null)for(String start:Arrays.asList(run.getStyle(),paragraph.getStyleID(),"Normal")){String key=start;Set<String> visited=new HashSet<>();while(key!=null&&visited.add(key)){XWPFStyle style=styles.getStyle(key);if(style==null)break;if(style.getCTStyle().isSetRPr())out.add(style.getCTStyle().getRPr());key=style.getBasisStyleID();}}
        if(previewDefaults!=null)out.add(previewDefaults);return out;
    }
    private static String runAttribute(List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> props,String name,String attr){for(var prop:props){String value=runAttribute(prop,name,attr);if(value!=null)return value;}return null;}
    private static boolean enabled(String value){return value!=null&&!Set.of("false","0","off","none").contains(value);}
    private String previewRunStyle(XWPFParagraph paragraph,XWPFRun run){
        var props=runProperties(paragraph,run);String font=runAttribute(props,"rFonts","ascii"),size=runAttribute(props,"sz","val"),color=runAttribute(props,"color","val");double points=defaultSize();if(size!=null)try{points=Double.parseDouble(size)/2;}catch(NumberFormatException ignored){}
        return "font-family:"+esc(font==null?defaultFont():font)+";font-size:"+points+"pt;"+(enabled(runAttribute(props,"b","val"))?"font-weight:bold;":"")+(enabled(runAttribute(props,"i","val"))?"font-style:italic;":"")+(enabled(runAttribute(props,"u","val"))?"text-decoration:underline;":"")+(color!=null&&color.matches("[a-fA-F0-9]{6}")?"color:#"+color+";":"");
    }
    private double defaultSize(){var styles=document.getStyles();var defaults=styles==null?null:styles.getDefaultRunStyle();return defaults!=null&&defaults.getFontSize()>0?defaults.getFontSize():11;}
    private String table(XWPFTable table){
        String tableId="t"+(++nextId);tables.put(tableId,table);StringBuilder b=new StringBuilder("<table data-table=\"").append(tableId).append("\"").append(readOnly?" style=\""+tableStyle(table)+"\"":"").append(">");
        for(XWPFTableRow row:table.getRows()){
            b.append("<tr>");for(XWPFTableCell cell:row.getTableCells()){
                String id="c"+(++nextId);cells.put(id,cell);b.append("<td data-cell=\"").append(id).append("\"").append(readOnly?" style=\""+cellStyle(cell)+"\"":"").append(">");for(IBodyElement element:cell.getBodyElements()){if(element instanceof XWPFParagraph p)b.append(paragraph(p));else if(element instanceof XWPFTable nested)b.append(table(nested));}b.append("</td>");
            }b.append("</tr>");
        }return b.append("</table>").toString();
    }
    private String paragraph(XWPFParagraph p){
        String id="p"+(++nextId);paragraphs.put(id,p);
        StringBuilder b=new StringBuilder("<p data-id=\"").append(id).append("\" data-block-index=\"").append(id).append("\"");
        if(!readOnly&&p.getText().contains("\u200B"))b.append(" data-changed=\"true\"");
        if(p.getBody() instanceof XWPFHeaderFooter){String part="part"+parts.size();for(var entry:parts.entrySet())if(entry.getValue()==p.getBody()){part=entry.getKey();break;}parts.put(part,p.getBody());b.append(" data-part=\"").append(part).append("\"");}
        if(p.getNumID()!=null)b.append(" data-list=\"").append("bullet".equals(p.getNumFmt())?"bullet":"decimal").append("\"");
        boolean rule=p.getCTP().isSetPPr()&&p.getCTP().getPPr().isSetPBdr()&&p.getCTP().getPPr().getPBdr().isSetBottom();
        if(rule)b.append(" data-rule=\"true\"");
        b.append(" style=\"").append(readOnly?paragraphStyle(p):"").append("text-align:").append(switch(p.getAlignment()){case CENTER->"center";case RIGHT->"right";case BOTH,DISTRIBUTE->"justify";default->"left";}).append(rule?";border-bottom:1px solid #8c959f;\">":";\">");
        if(p.isPageBreak())b.append("<span class=\"page-break\" contenteditable=\"false\"></span>");
        StringBuilder joined=new StringBuilder();for(XWPFRun run:p.getRuns())joined.append(run.text()==null?"":run.text());
        List<InlineToken> tokens=new ArrayList<>();Matcher matches=TOKEN.matcher(joined);while(!readOnly&&matches.find())tokens.add(new InlineToken(matches.start(),matches.end(),matches.group(1)));
        int offset=0;
        for(XWPFRun r:p.getRuns()){
            String content=r.text()==null?"":r.text();String fragment=inlineText(content,offset,tokens,highlightStyle(p,r));offset+=content.length();
            if(!fragment.isEmpty()){
                if(readOnly)b.append("<span style=\"").append(previewRunStyle(p,r)).append("\">");else{
                b.append("<span style=\"font-family:").append(esc(r.getFontFamily()==null?(readOnly?defaultFont():"Arial"):r.getFontFamily())).append(";font-size:").append(readOnly?(r.getFontSizeAsDouble()!=null?r.getFontSizeAsDouble():defaultSize()):fontPoints(r.getFontSizeAsDouble()!=null?r.getFontSizeAsDouble():12)).append("pt;");
                if(r.isBold())b.append("font-weight:bold;");if(r.isItalic())b.append("font-style:italic;");
                if(r.getCTR().isSetRPr()&&r.getUnderline()!=UnderlinePatterns.NONE)b.append("text-decoration:underline;");
                if(r.getColor()!=null)b.append("color:#").append(r.getColor()).append(';');b.append(highlightStyle(p,r)).append("\">");}
                b.append(fragment).append("</span>");
            }
            for(XWPFPicture picture:r.getEmbeddedPictures()){
                String mime=picture.getPictureData().getPackagePart().getContentType();
                b.append("<img src=\"data:").append(mime).append(";base64,").append(Base64.getEncoder().encodeToString(picture.getPictureData().getData())).append("\" width=\"").append(Math.max(1,Math.round(picture.getWidth()*96/72))).append("\" height=\"").append(Math.max(1,Math.round(picture.getDepth()*96/72))).append("\" alt=\"Imagen\">");
            }
        }
        if(joined.length()==0&&p.getRuns().stream().allMatch(r->r.getEmbeddedPictures().isEmpty()))b.append("<br>");return b.append("</p>").toString();
    }
    private static String paragraphStyle(XWPFParagraph p){
        StringBuilder style=new StringBuilder();style.append("margin-top:").append(Math.max(0,p.getSpacingBefore())/15.0).append("px;margin-bottom:").append(Math.max(0,p.getSpacingAfter())/15.0).append("px;");
        if(p.getIndentationLeft()>0)style.append("margin-left:").append(p.getIndentationLeft()/15.0).append("px;");if(p.getIndentationRight()>0)style.append("margin-right:").append(p.getIndentationRight()/15.0).append("px;");
        if(p.getIndentationFirstLine()>0)style.append("text-indent:").append(p.getIndentationFirstLine()/15.0).append("px;");else if(p.getIndentationHanging()>0)style.append("text-indent:-").append(p.getIndentationHanging()/15.0).append("px;");
        double spacing=p.getSpacingBetween();if(spacing>0)style.append("line-height:").append(spacing).append(p.getSpacingLineRule()==LineSpacingRule.AUTO?"":"pt").append(';');return style.toString();
    }
    private static String tableStyle(XWPFTable table){String width=table.getWidthType()==TableWidthType.PCT?table.getWidth()/50.0+"%":table.getWidth()>0?table.getWidth()/15.0+"px":"100%";return "width:"+width+";";}
    private static String cellStyle(XWPFTableCell cell){
        StringBuilder style=new StringBuilder();if(cell.getCTTc().getTcPr()!=null&&cell.getCTTc().getTcPr().getTcW()!=null&&cell.getCTTc().getTcPr().getTcW().getW()!=null&&cell.getWidth()>0)style.append("width:").append(cell.getWidthType()==TableWidthType.PCT?cell.getWidth()/50.0+"%":cell.getWidth()/15.0+"px").append(';');
        var props=cell.getCTTc().getTcPr();if(props!=null&&props.isSetTcBorders()){var borders=props.getTcBorders();if(borders.isSetTop())style.append(borderStyle("top",borders.getTop()));if(borders.isSetBottom())style.append(borderStyle("bottom",borders.getBottom()));if(borders.isSetLeft())style.append(borderStyle("left",borders.getLeft()));if(borders.isSetRight())style.append(borderStyle("right",borders.getRight()));}
        else{XWPFTable table=cell.getTableRow().getTable();String color=table.getInsideHBorderColor();if(table.getInsideHBorderType()!=null&&table.getInsideHBorderType()!=XWPFTable.XWPFBorderType.NONE)style.append("border:1px solid #").append(color==null||!color.matches("[A-Fa-f0-9]{6}")?"000000":color).append(';');}
        String color=cell.getColor();if(color!=null&&color.matches("[A-Fa-f0-9]{6}"))style.append("background:#").append(color).append(';');return style.toString();
    }
    private static String borderStyle(String side,org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder border){String val=String.valueOf(border.getVal());if("nil".equals(val)||"none".equals(val))return "border-"+side+":none;";Object color=border.getColor();String hex=color instanceof byte[] bytes?java.util.HexFormat.of().formatHex(bytes):String.valueOf(color);if(!hex.matches("[A-Fa-f0-9]{6}"))hex="000000";return "border-"+side+":"+(border.getSz()==null?1:border.getSz().doubleValue()/6)+"px "+("double".equals(val)?"double":"dashed".equals(val)?"dashed":"solid")+" #"+hex+";";}
    /** @author MC */
    private record InlineToken(int start,int end,String key){}
    private String inlineText(String text,int start,List<InlineToken> tokens,String highlight){
        StringBuilder html=new StringBuilder();int at=0,end=start+text.length();
        for(InlineToken token:tokens){
            if(token.end()<=start||token.start()>=end)continue;
            if(token.start()>=start){
                html.append(inlineWhitespace(text.substring(at,token.start()-start)));String key=esc(token.key()),person=DocumentEngine.personaKey(token.key()),label=esc(person.isEmpty()?token.key():personaNames.getOrDefault(person,person.replace('_',' '))+" · "+DocumentEngine.baseKey(token.key()));
                String chipStyle=(person.isEmpty()?"":"--persona-hue:"+Math.floorMod(person.hashCode()*137,360)+";")+highlight;
                html.append("<span class=\"variable\" data-var=\"true\" data-key=\"").append(key).append("\"");
                if(!person.isEmpty())html.append(" data-persona=\"").append(esc(person)).append("\"");
                if(!chipStyle.isEmpty())html.append(" style=\"").append(chipStyle).append("\"");
                html.append(" contenteditable=\"false\" draggable=\"true\" title=\"{{").append(key).append("}}\">⟦").append(label).append("⟧</span>");
            }
            at=Math.min(text.length(),token.end()-start);
        }
        return html.append(inlineWhitespace(text.substring(at))).toString();
    }
    private static String inlineWhitespace(String text){return esc(text).replace("\t","<span class=\"docx-tab\" data-tab=\"true\">\t</span>").replace("\n","<br/>");}
    private static String fontPoints(double points){return points==Math.rint(points)?Long.toString(Math.round(points)):Double.toString(points);}
    private String highlightStyle(XWPFParagraph paragraph,XWPFRun run){
        var props=runProperties(paragraph,run);String fill=runAttribute(props,"shd","fill");
        if(fill!=null&&fill.matches("[a-fA-F0-9]{6}"))return "background-color:#"+fill+";";
        String highlight=runAttribute(props,"highlight","val");
        String color=WORD_HIGHLIGHTS.get(highlight==null?"":highlight);
        return color==null?"":"background-color:#"+color+";";
    }
    private static final Map<String,String> WORD_HIGHLIGHTS=Map.ofEntries(
        Map.entry("yellow","FFFF00"),Map.entry("green","00FF00"),Map.entry("cyan","00FFFF"),Map.entry("magenta","FF00FF"),
        Map.entry("blue","0000FF"),Map.entry("red","FF0000"),Map.entry("darkBlue","000080"),Map.entry("darkCyan","008080"),
        Map.entry("darkGreen","008000"),Map.entry("darkMagenta","800080"),Map.entry("darkRed","800000"),Map.entry("darkYellow","808000"),
        Map.entry("darkGray","808080"),Map.entry("lightGray","C0C0C0"),Map.entry("black","000000"),Map.entry("white","FFFFFF"));
    private static String esc(String s){return s.replace("\u200B","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}

    String apply(String xml) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        org.w3c.dom.Document dom=factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element editor=dom.getDocumentElement();
        List<Element> blocks=new ArrayList<>();collectBlocks(editor,blocks);
        Map<String,Element> seen=new HashMap<>();
        for(Element block:blocks){if("table".equalsIgnoreCase(block.getTagName()))continue;String id=blockId(block);
            if("true".equals(block.getAttribute("data-new-block"))){block.removeAttribute("data-id");block.removeAttribute("data-block-index");block.setAttribute("data-changed","true");continue;}
            if(!id.isBlank()&&seen.containsKey(id)){seen.get(id).setAttribute("data-changed","true");block.removeAttribute("data-id");block.removeAttribute("data-block-index");block.setAttribute("data-new-block","true");block.setAttribute("data-changed","true");}
            else if(!id.isBlank())seen.put(id,block);
        }
        for(int i=0;i<blocks.size();i++){
            Element block=blocks.get(i);if(!"table".equalsIgnoreCase(block.getTagName()))continue;
            XWPFTable table=tables.get(block.getAttribute("data-table"));boolean created=table==null;
            if(created){
                XWPFParagraph nextParagraph=null;XWPFTable nextTable=null;
                for(int j=i+1;j<blocks.size();j++){Element future=blocks.get(j);nextParagraph=paragraphs.get(blockId(future));if(nextParagraph!=null&&nextParagraph.getBody()==document)break;nextParagraph=null;nextTable=tables.get(future.getAttribute("data-table"));if(nextTable!=null)break;}
                if(nextParagraph!=null)table=document.insertNewTbl(nextParagraph.getCTP().newCursor());else if(nextTable!=null)table=document.insertNewTbl(nextTable.getCTTbl().newCursor());else table=document.createTable();
                String id="newTable"+(++nextId);tables.put(id,table);block.setAttribute("data-table",id);
            }
            if(created||"true".equals(block.getAttribute("data-changed")))reconcileTable(table,block);
        }
        Map<IBody,IBodyElement> previous=new IdentityHashMap<>();Map<IBody,XWPFParagraph> baseStyle=new IdentityHashMap<>();Map<IBody,Style> runStyles=new IdentityHashMap<>();
        for(int i=0;i<blocks.size();i++){
            Element block=blocks.get(i);
            if("table".equalsIgnoreCase(block.getTagName())){XWPFTable table=tables.get(block.getAttribute("data-table"));if(table!=null)previous.put(table.getBody(),table);continue;}
            String identity=blockId(block);XWPFParagraph p="true".equals(block.getAttribute("data-new-block"))?null:paragraphs.get(identity);
            if(p==null){
                Element cellElement=ancestor(block,"td");IBody body=cellElement==null?parts.getOrDefault(block.getAttribute("data-part"),document):cells.get(cellElement.getAttribute("data-cell"));if(body==null)body=document;
                IBodyElement preceding=previous.get(body);XWPFParagraph base=baseStyle.get(body);
                if(preceding!=null){try(var cursor=(preceding instanceof XWPFParagraph prior?prior.getCTP():((XWPFTable)preceding).getCTTbl()).newCursor()){cursor.toEndToken();cursor.toNextToken();p=body.insertNewParagraph(cursor);}}
                else{
                    for(int j=i+1;j<blocks.size()&&p==null;j++){Element next=blocks.get(j);XWPFParagraph following=paragraphs.get(blockId(next));XWPFTable table=tables.get(next.getAttribute("data-table"));
                        if(following!=null&&following.getBody()==body){try(var cursor=following.getCTP().newCursor()){p=body.insertNewParagraph(cursor);}if(base==null)base=following;}
                        else if(table!=null&&table.getBody()==body)try(var cursor=table.getCTTbl().newCursor()){p=body.insertNewParagraph(cursor);}
                    }
                }
                if(p==null){if(body instanceof XWPFTableCell cell)p=cell.addParagraph();else if(body instanceof XWPFHeaderFooter part)p=part.createParagraph();else p=document.createParagraph();}
                if(base!=null&&base.getCTP().isSetPPr())p.getCTP().setPPr((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr)base.getCTP().getPPr().copy());
                String id="new"+(++nextId);paragraphs.put(id,p);block.setAttribute("data-id",id);block.setAttribute("data-block-index",id);block.removeAttribute("data-new-block");writeParagraph(p,block,runStyles.getOrDefault(body,baseStyleOf(base)));
            }else if("true".equals(block.getAttribute("data-changed")))writeParagraph(p,block);
            previous.put(p.getBody(),p);baseStyle.put(p.getBody(),p);if(!p.getRuns().isEmpty())runStyles.put(p.getBody(),baseStyleOf(p));
        }
        Set<String> present=new HashSet<>();for(Element e:blocks)present.add(blockId(e));
        for(var entry:new ArrayList<>(paragraphs.entrySet()))if(!present.contains(entry.getKey())){XWPFParagraph p=entry.getValue();if(p.getBody()==document){int index=document.getPosOfParagraph(p);if(index>=0)document.removeBodyElement(index);}else if(p.getBody() instanceof XWPFTableCell cell){int index=cell.getParagraphs().indexOf(p);if(index>=0)cell.removeParagraph(index);}}
        Set<String> presentTables=new HashSet<>();for(Element e:blocks)presentTables.add(e.getAttribute("data-table"));for(var entry:tables.entrySet())if(!presentTables.contains(entry.getKey())){int index=document.getBodyElements().indexOf(entry.getValue());if(index>=0)document.removeBodyElement(index);}
        var transformer=javax.xml.transform.TransformerFactory.newInstance().newTransformer();transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION,"yes");StringWriter normalized=new StringWriter();transformer.transform(new javax.xml.transform.dom.DOMSource(dom),new javax.xml.transform.stream.StreamResult(normalized));return normalized.toString();
    }
    private void reconcileTable(XWPFTable table,Element html){
        NodeList rows=html.getElementsByTagName("tr");int wanted=Math.max(1,rows.getLength());while(table.getNumberOfRows()<wanted)table.createRow();while(table.getNumberOfRows()>wanted)table.removeRow(table.getNumberOfRows()-1);
        for(int ri=0;ri<rows.getLength();ri++){
            Element row=(Element)rows.item(ri);List<Element> htmlCells=directChildren(row,"td");XWPFTableRow target=table.getRow(ri);int columns=Math.max(1,htmlCells.size());while(target.getTableCells().size()<columns)target.addNewTableCell();while(target.getTableCells().size()>columns)target.removeCell(target.getTableCells().size()-1);
            for(int ci=0;ci<htmlCells.size();ci++){
                Element htmlCell=htmlCells.get(ci);XWPFTableCell cell=target.getCell(ci);String id=htmlCell.getAttribute("data-cell");if(!cells.containsKey(id)){id="newCell"+(++nextId);htmlCell.setAttribute("data-cell",id);cells.put(id,cell);}
                List<Element> paragraphsInCell=directChildren(htmlCell,"p");if(!paragraphsInCell.isEmpty()&&paragraphsInCell.get(0).getAttribute("data-id").isBlank()&&!cell.getParagraphs().isEmpty()){Element first=paragraphsInCell.get(0);String paragraphId="new"+(++nextId);first.setAttribute("data-id",paragraphId);first.setAttribute("data-changed","true");paragraphs.put(paragraphId,cell.getParagraphs().get(0));}
            }
        }
        if(html.hasAttribute("data-borders")){String mode=html.getAttribute("data-borders"),color=html.getAttribute("data-border-color").replace("#","");if(!color.matches("[0-9a-fA-F]{6}"))color="8C959F";int size=(int)Math.max(2,Math.min(96,measure(html.getAttribute("data-border-width"),1)*8));XWPFTable.XWPFBorderType line=switch(html.getAttribute("data-border-style")){case "double"->XWPFTable.XWPFBorderType.DOUBLE;case "dashed"->XWPFTable.XWPFBorderType.DASHED;default->XWPFTable.XWPFBorderType.SINGLE;},none=XWPFTable.XWPFBorderType.NONE;boolean outer=mode.equals("all")||mode.equals("outer"),inner=mode.equals("all")||mode.equals("inner");table.setTopBorder(outer?line:none,size,0,color);table.setBottomBorder(outer?line:none,size,0,color);table.setLeftBorder(outer?line:none,size,0,color);table.setRightBorder(outer?line:none,size,0,color);table.setInsideHBorder(inner?line:none,size,0,color);table.setInsideVBorder(inner?line:none,size,0,color);}
        table.setWidth("100%");
    }
    private static List<Element> directChildren(Element parent,String tag){List<Element> out=new ArrayList<>();NodeList nodes=parent.getChildNodes();for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element e&&e.getTagName().equalsIgnoreCase(tag))out.add(e);return out;}
    private static Element ancestor(Element from,String tag){Node node=from.getParentNode();while(node instanceof Element e){if(e.getTagName().equalsIgnoreCase(tag))return e;node=e.getParentNode();}return null;}
    private static String blockId(Element block){return block.hasAttribute("data-block-index")?block.getAttribute("data-block-index"):block.getAttribute("data-id");}
    private static void collectBlocks(Element parent,List<Element> out){
        NodeList children=parent.getChildNodes();for(int i=0;i<children.getLength();i++)if(children.item(i) instanceof Element e){String tag=e.getTagName().toLowerCase();if(tag.equals("p")||tag.equals("div")||tag.equals("li")){if(directChildren(e,"p").isEmpty()&&directChildren(e,"div").isEmpty()&&directChildren(e,"li").isEmpty())out.add(e);else collectBlocks(e,out);}else if(tag.equals("table")){out.add(e);collectBlocks(e,out);}else collectBlocks(e,out);}
    }
    private static Style baseStyleOf(XWPFParagraph paragraph){
        if(paragraph==null||paragraph.getRuns().isEmpty())return new Style();XWPFRun run=paragraph.getRuns().get(paragraph.getRuns().size()-1);
        return new Style(run.getFontFamily()==null?"Arial":run.getFontFamily(),run.getFontSize()>0?run.getFontSize():12,run.isBold(),run.isItalic(),run.getCTR().isSetRPr()&&run.getUnderline()!=UnderlinePatterns.NONE,run.getColor(),null);
    }
    private void writeParagraph(XWPFParagraph p,Element e)throws Exception{writeParagraph(p,e,new Style());}
    private void writeParagraph(XWPFParagraph p,Element e,Style inherited) throws Exception {
        String align=e.getAttribute("style").toLowerCase();if(align.contains("text-align:center"))p.setAlignment(ParagraphAlignment.CENTER);else if(align.contains("text-align:right"))p.setAlignment(ParagraphAlignment.RIGHT);else if(align.contains("text-align:justify"))p.setAlignment(ParagraphAlignment.BOTH);else if(align.contains("text-align:left"))p.setAlignment(ParagraphAlignment.LEFT);
        String list=e.getAttribute("data-list");
        if("bullet".equals(list)||"decimal".equals(list)){
            if(p.getNumID()==null||!list.equals("bullet".equals(p.getNumFmt())?"bullet":"decimal")){p.setNumID(listNumber(list));p.setNumILvl(java.math.BigInteger.ZERO);}
        }else if(p.getCTP().isSetPPr()&&p.getCTP().getPPr().isSetNumPr())p.getCTP().getPPr().unsetNumPr();
        if("true".equals(e.getAttribute("data-rule")))p.setBorderBottom(Borders.SINGLE);
        while(!p.getRuns().isEmpty())p.removeRun(0);
        p.setPageBreak(hasPageBreak(e));
        boolean placeholder=e.getTextContent().isEmpty()&&e.getElementsByTagName("br").getLength()==1&&e.getElementsByTagName("img").getLength()==0;
        if(!placeholder)appendNodes(p,e,derive(inherited,e));
    }
    private java.math.BigInteger listNumber(String kind){
        return listNumbers.computeIfAbsent(kind,key->{
            XWPFNumbering numbering=document.getNumbering()==null?document.createNumbering():document.getNumbering();
            java.math.BigInteger id=java.math.BigInteger.ZERO;while(numbering.getAbstractNum(id)!=null)id=id.add(java.math.BigInteger.ONE);
            var definition=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum.Factory.newInstance();definition.setAbstractNumId(id);
            var level=definition.addNewLvl();level.setIlvl(java.math.BigInteger.ZERO);level.addNewStart().setVal(java.math.BigInteger.ONE);
            level.addNewNumFmt().setVal("bullet".equals(key)?org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.BULLET:org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.DECIMAL);
            level.addNewLvlText().setVal("bullet".equals(key)?"•":"%1.");var indent=level.addNewPPr().addNewInd();indent.setLeft(java.math.BigInteger.valueOf(720));indent.setHanging(java.math.BigInteger.valueOf(360));
            return numbering.addNum(numbering.addAbstractNum(new XWPFAbstractNum(definition)));
        });
    }
    private static boolean hasPageBreak(Element e){NodeList spans=e.getElementsByTagName("span");for(int i=0;i<spans.getLength();i++)if(spans.item(i) instanceof Element s&&s.getAttribute("class").contains("page-break"))return true;return false;}
    /** @author MC */
    private record Style(String font,double size,boolean bold,boolean italic,boolean underline,String color,String highlight){Style(){this("Arial",12,false,false,false,null,null);}}
    private static void appendNodes(XWPFParagraph p,Node parent,Style inherited) throws Exception {
        NodeList nodes=parent.getChildNodes();for(int i=0;i<nodes.getLength();i++){
            Node n=nodes.item(i);if(n.getNodeType()==Node.TEXT_NODE){String text=n.getTextContent().replace("\u200B","");if(!text.isEmpty()){XWPFRun r=p.createRun();style(r,inherited);appendText(r,text);}}
            else if(n instanceof Element e){String tag=e.getTagName().toLowerCase();if(Arrays.asList(e.getAttribute("class").split("\\s+")).contains("variable")){XWPFRun r=p.createRun();style(r,derive(inherited,e));r.setText("{{"+e.getAttribute("data-key")+"}}");}
                else if("true".equals(e.getAttribute("data-tab"))){XWPFRun r=p.createRun();style(r,derive(inherited,e));r.addTab();}
                else if(tag.equals("br")){if(e!=trailingBreak(paragraphElement(e)))p.createRun().addBreak();}
                else if(tag.equals("img")){String src=e.getAttribute("src");int comma=src.indexOf(",");if(src.startsWith("data:")&&comma>0){String mime=src.substring(5,comma).toLowerCase();int type=mime.contains("png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;byte[] bytes=Base64.getDecoder().decode(src.substring(comma+1));double width=measure(e.getAttribute("width"),240),height=measure(e.getAttribute("height"),160);try(InputStream in=new ByteArrayInputStream(bytes)){p.createRun().addPicture(in,type,"editor-image",Units.toEMU(width*72/96),Units.toEMU(height*72/96));}}}
                else if(!"page-break".equals(e.getAttribute("class")))appendNodes(p,e,derive(inherited,e));
            }
        }
    }
    private static Node paragraphElement(Node node){Node current=node;while(current.getParentNode() instanceof Element e&&!Set.of("p","div","li").contains(((Element)current).getTagName().toLowerCase()))current=e;return current;}
    private static Node trailingBreak(Node node){Node last=node.getLastChild();while(last!=null&&last.getNodeType()==Node.TEXT_NODE&&last.getTextContent().isEmpty())last=last.getPreviousSibling();if(last instanceof Element e){if("br".equalsIgnoreCase(e.getTagName()))return e;return trailingBreak(e);}return null;}
    private static double measure(String text,double fallback){try{return Math.max(1,Double.parseDouble(text.replaceAll("[^0-9.]","")));}catch(Exception ignored){return fallback;}}
    private static void appendText(XWPFRun run,String text){
        int start=0;for(int i=0;i<text.length();i++)if(text.charAt(i)=='\t'||text.charAt(i)=='\n'){
            if(i>start)run.setText(text.substring(start,i));if(text.charAt(i)=='\t')run.addTab();else run.addBreak();start=i+1;
        }if(start<text.length())run.setText(text.substring(start));
    }
    private static Style derive(Style base,Element e){
        Map<String,String> css=new HashMap<>();for(String declaration:e.getAttribute("style").split(";")){int colon=declaration.indexOf(':');if(colon>0)css.put(declaration.substring(0,colon).trim().toLowerCase(Locale.ROOT),declaration.substring(colon+1).trim());}
        String tag=e.getTagName().toLowerCase(Locale.ROOT),font=base.font(),color=base.color(),highlight=base.highlight();double size=base.size();
        boolean bold=base.bold()||Set.of("b","strong").contains(tag),italic=base.italic()||Set.of("i","em").contains(tag),underline=base.underline()||tag.equals("u");
        String value=css.get("font-family");if(value!=null&&!value.equalsIgnoreCase("inherit"))font=value.split(",")[0].replace("'","").replace("\"","").trim();
        value=css.get("font-size");if(value!=null){Matcher m=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(pt|px)?").matcher(value);if(m.matches())size=Double.parseDouble(m.group(1))*("px".equals(m.group(2))?.75:1);}
        value=css.get("font-weight");if(value!=null&&!value.equalsIgnoreCase("inherit"))bold=value.equalsIgnoreCase("bold")||value.equalsIgnoreCase("bolder")||value.matches("[6-9]00");
        value=css.get("font-style");if(value!=null&&!value.equalsIgnoreCase("inherit"))italic=value.equalsIgnoreCase("italic")||value.equalsIgnoreCase("oblique");
        value=css.getOrDefault("text-decoration-line",css.get("text-decoration"));if(value!=null&&!value.equalsIgnoreCase("inherit"))underline=value.contains("underline");
        if(e.hasAttribute("color"))color=cssColor(e.getAttribute("color"),color);if(css.containsKey("color"))color=cssColor(css.get("color"),color);
        if(css.containsKey("background-color"))highlight=cssColor(css.get("background-color"),highlight);
        if(e.hasAttribute("face"))font=e.getAttribute("face");
        return new Style(font,size,bold,italic,underline,color,highlight);
    }
    private static String cssColor(String value,String fallback){
        value=value.trim().toLowerCase(Locale.ROOT);if(value.matches("#[0-9a-f]{6}"))return value.substring(1).toUpperCase(Locale.ROOT);
        if(value.matches("#[0-9a-f]{3}"))return (""+value.charAt(1)+value.charAt(1)+value.charAt(2)+value.charAt(2)+value.charAt(3)+value.charAt(3)).toUpperCase(Locale.ROOT);
        Matcher rgb=Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([0-9.]+))?\\s*\\)").matcher(value);
        if(rgb.matches()){if(rgb.group(4)!=null&&Double.parseDouble(rgb.group(4))==0)return null;return String.format("%02X%02X%02X",Math.min(255,Integer.parseInt(rgb.group(1))),Math.min(255,Integer.parseInt(rgb.group(2))),Math.min(255,Integer.parseInt(rgb.group(3))));}
        if(value.equals("transparent")||value.equals("none"))return null;
        return switch(value){case "black"->"000000";case "white"->"FFFFFF";case "red"->"FF0000";case "yellow"->"FFFF00";case "blue"->"0000FF";case "green"->"008000";default->fallback;};
    }
    private static void style(XWPFRun r,Style s){r.setFontFamily(s.font());r.setFontSize(s.size());r.setBold(s.bold());r.setItalic(s.italic());if(s.underline())r.setUnderline(UnderlinePatterns.SINGLE);if(s.color()!=null)r.setColor(s.color());if(s.highlight()!=null){var props=r.getCTR().isSetRPr()?r.getCTR().getRPr():r.getCTR().addNewRPr();props.addNewShd().setFill(s.highlight());}}
}
