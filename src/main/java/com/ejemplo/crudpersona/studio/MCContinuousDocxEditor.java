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
final class MCContinuousDocxEditor {
    private static final Pattern mcTOKEN=Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");
    private final XWPFDocument mcDocument;
    private final Map<String,XWPFParagraph> mcParagraphs=new LinkedHashMap<>();
    private final Map<String,XWPFTableCell> mcCells=new LinkedHashMap<>();
    private final Map<String,XWPFTable> mcTables=new LinkedHashMap<>();
    private final Map<String,IBody> mcParts=new LinkedHashMap<>();
    private int mcNextId;
    private boolean mcReadOnly;
    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr mcPreviewDefaults;
    private final Map<String,java.math.BigInteger> mcListNumbers=new HashMap<>();

    private final Map<String,String> mcPersonaNames;
    MCContinuousDocxEditor(XWPFDocument mcDocument){this(mcDocument,Map.of());}
    MCContinuousDocxEditor(XWPFDocument mcDocument,Map<String,String> mcPersonaNames){this.mcDocument=mcDocument;this.mcPersonaNames=Map.copyOf(mcPersonaNames);}

    String mcGenerationHtml()throws IOException{
        try(InputStream mcInput=Objects.requireNonNull(getClass().getResourceAsStream("/editor/document-editor.html"))){
            return new String(mcInput.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
                    .replace("__DOCUMENT_CONTENT__",mcHtml());
        }
    }

    String mcHtml(){
        mcParagraphs.clear();mcCells.clear();mcTables.clear();mcParts.clear();mcNextId=0;
        StringBuilder mcOut=new StringBuilder();
        for(XWPFHeader mcH:mcDocument.getHeaderList())for(XWPFParagraph mcP:mcH.getParagraphs())mcOut.append(mcParagraph(mcP));
        for(IBodyElement mcElement:mcDocument.getBodyElements()){
            if(mcElement instanceof XWPFParagraph mcP)mcOut.append(mcParagraph(mcP));
            else if(mcElement instanceof XWPFTable mcT)mcOut.append(mcTable(mcT));
        }
        for(XWPFFooter mcF:mcDocument.getFooterList())for(XWPFParagraph mcP:mcF.getParagraphs())mcOut.append(mcParagraph(mcP));
        if(mcOut.length()==0)mcOut.append("<p><br></p>");
        return mcOut.toString();
    }

    String mcPreviewHtml(){
        mcReadOnly=true;
        if(mcDocument.getStyles()!=null&&mcDocument.getStyles().getPackagePart()!=null)try(InputStream mcIn=mcDocument.getStyles().getPackagePart().getInputStream()){
            var mcStyles=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles.Factory.parse(mcIn);if(mcStyles.getDocDefaults()!=null&&mcStyles.getDocDefaults().getRPrDefault()!=null)mcPreviewDefaults=mcStyles.getDocDefaults().getRPrDefault().getRPr();
        }catch(Exception mcIgnored){}
        try{
            var mcSection=mcDocument.getDocument().getBody().getSectPr();double mcWidth=794,mcHeight=1123,mcLeft=96,mcRight=96,mcTop=96,mcBottom=96,mcHeader=48,mcFooter=48;
            if(mcSection!=null){
                if(mcSection.isSetPgSz()){mcWidth=((Number)mcSection.getPgSz().getW()).doubleValue()/15; mcHeight=((Number)mcSection.getPgSz().getH()).doubleValue()/15;}
                if(mcSection.isSetPgMar()){var mcM=mcSection.getPgMar();mcLeft=mcTwips(mcM.getLeft(),mcLeft);mcRight=mcTwips(mcM.getRight(),mcRight);mcTop=mcTwips(mcM.getTop(),mcTop);mcBottom=mcTwips(mcM.getBottom(),mcBottom);mcHeader=mcTwips(mcM.getHeader(),mcHeader);mcFooter=mcTwips(mcM.getFooter(),mcFooter);}
            }
            StringBuilder mcBody=new StringBuilder(),mcHead=new StringBuilder(),mcFoot=new StringBuilder();
            mcRenderBody(mcDocument.getBodyElements(),mcBody);
            if(!mcDocument.getHeaderList().isEmpty())mcRenderBody(mcDocument.getHeaderList().get(0).getBodyElements(),mcHead);
            if(!mcDocument.getFooterList().isEmpty())mcRenderBody(mcDocument.getFooterList().get(0).getBodyElements(),mcFoot);
            String mcDefaults="font-family:"+mcEsc(mcDefaultFont())+";font-size:"+mcDefaultSize()+"pt;";
            String mcCss="html,body{margin:0;padding:0;background:#f6f8fa;color:#000}*{box-sizing:border-box}#pages{position:relative;width:100%;overflow:hidden}.page-slot{position:relative;margin:0 auto 12px}.doc-page{position:absolute;top:0;left:0;background:#fff;transform-origin:top left;width:"+mcWidth+"px;height:"+mcHeight+"px;"+mcDefaults+"}.page-body{position:absolute;left:"+mcLeft+"px;top:"+mcTop+"px;width:"+(mcWidth-mcLeft-mcRight)+"px;height:"+(mcHeight-mcTop-mcBottom)+"px;overflow:hidden}.doc-flow{width:"+(mcWidth-mcLeft-mcRight)+"px;height:"+(mcHeight-mcTop-mcBottom)+"px;column-width:"+(mcWidth-mcLeft-mcRight)+"px;column-gap:0;column-fill:auto;overflow:visible;"+mcDefaults+"}.doc-header,.doc-footer{position:absolute;left:"+mcLeft+"px;right:"+mcRight+"px}.doc-header{top:"+mcHeader+"px}.doc-footer{bottom:"+mcFooter+"px}p{white-space:pre-wrap;overflow-wrap:break-word;margin:0;min-height:1em;line-height:normal}table{border-collapse:collapse;max-width:100%}td{vertical-align:top;padding:0 5px}tr{break-inside:avoid}img{max-width:100%}.page-break{break-before:column;display:block}p[data-page-break]{break-before:column}p[data-list]{display:list-item;margin-left:24px}p[data-list=bullet]{list-style-type:disc}p[data-list=decimal]{list-style-type:decimal}#measure{position:absolute;left:-100000px;top:0;visibility:hidden}";
            return "<!doctype html><html><head><meta charset=\"utf-8\"><style>"+mcCss+"</style></head><body contenteditable=\"false\"><div id=\"measure\"><div class=\"doc-flow\">"+mcBody+"</div></div><div id=\"pages\"></div><template id=\"header\">"+mcHead+"</template><template id=\"footer\">"+mcFoot+"</template><script>"+
                "(function(){var mcFlow=document.querySelector('#measure .doc-flow'),mcPages=document.getElementById('pages'),mcW="+mcWidth+",mcH="+mcHeight+",mcCw="+(mcWidth-mcLeft-mcRight)+";"+
                "function mcFit(){var mcScale=Math.min(1,document.documentElement.clientWidth/mcW);Array.from(mcPages.children).forEach(function(mcSlot){mcSlot.style.width=mcW*mcScale+'px';mcSlot.style.height=mcH*mcScale+'px';mcSlot.firstChild.style.transform='scale('+mcScale+')'})}"+
                "function mcRender(){mcPages.innerHTML='';var mcCount=Math.max(1,Math.ceil(mcFlow.scrollWidth/mcCw));for(var mcI=0;mcI<mcCount;mcI++){var mcSlot=document.createElement('div');mcSlot.className='page-slot';var mcPage=document.createElement('article');mcPage.className='doc-page';var mcViewport=document.createElement('div');mcViewport.className='page-body';var mcCopy=mcFlow.cloneNode(true);mcCopy.style.position='relative';mcCopy.style.left=(-mcI*mcCw)+'px';mcViewport.appendChild(mcCopy);mcPage.appendChild(mcViewport);['header','footer'].forEach(function(mcPart){var mcEl=document.createElement('div');mcEl.className='doc-'+mcPart;mcEl.innerHTML=document.getElementById(mcPart).innerHTML;mcPage.appendChild(mcEl)});mcSlot.appendChild(mcPage);mcPages.appendChild(mcSlot)}mcFit();window.scrollTo(0,0);document.documentElement.scrollTop=0;document.body.scrollTop=0;window.previewReady=true}"+
                "window.addEventListener('resize',mcFit);window.addEventListener('load',mcRender);})();</script></body></html>";
        }finally{mcReadOnly=false;}
    }
    private void mcRenderBody(List<IBodyElement> mcElements,StringBuilder mcOut){for(IBodyElement mcElement:mcElements){if(mcElement instanceof XWPFParagraph mcP)mcOut.append(mcParagraph(mcP));else if(mcElement instanceof XWPFTable mcT)mcOut.append(mcTable(mcT));}}
    private static double mcTwips(Object mcValue,double mcFallback){return mcValue instanceof Number mcN?mcN.doubleValue()/15:mcFallback;}
    private String mcDefaultFont(){String mcFont=mcRunAttribute(mcPreviewDefaults,"rFonts","ascii");return mcFont==null?"Calibri":mcFont;}
    private static final String mcWORD_NS="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static String mcRunAttribute(org.apache.xmlbeans.XmlObject mcProps,String mcName,String mcAttribute){
        if(mcProps==null)return null;for(Node mcNode=mcProps.getDomNode().getFirstChild();mcNode!=null;mcNode=mcNode.getNextSibling())if(mcNode instanceof Element mcE&&mcName.equals(mcE.getLocalName()))return mcE.hasAttributeNS(mcWORD_NS,mcAttribute)?mcE.getAttributeNS(mcWORD_NS,mcAttribute):"true";return null;
    }
    private List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> mcRunProperties(XWPFParagraph mcParagraph,XWPFRun mcRun){
        List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> mcOut=new ArrayList<>();if(mcRun.getCTR().isSetRPr())mcOut.add(mcRun.getCTR().getRPr());
        var mcStyles=mcDocument.getStyles();if(mcStyles!=null)for(String mcStart:Arrays.asList(mcRun.getStyle(),mcParagraph.getStyleID(),"Normal")){String mcKey=mcStart;Set<String> mcVisited=new HashSet<>();while(mcKey!=null&&mcVisited.add(mcKey)){XWPFStyle mcStyle=mcStyles.getStyle(mcKey);if(mcStyle==null)break;if(mcStyle.getCTStyle().isSetRPr())mcOut.add(mcStyle.getCTStyle().getRPr());mcKey=mcStyle.getBasisStyleID();}}
        if(mcPreviewDefaults!=null)mcOut.add(mcPreviewDefaults);return mcOut;
    }
    private static String mcRunAttribute(List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> mcProps,String mcName,String mcAttr){for(var mcProp:mcProps){String mcValue=mcRunAttribute(mcProp,mcName,mcAttr);if(mcValue!=null)return mcValue;}return null;}
    private static boolean mcEnabled(String mcValue){return mcValue!=null&&!Set.of("false","0","off","none").contains(mcValue);}
    private String mcPreviewRunStyle(XWPFParagraph mcParagraph,XWPFRun mcRun){
        var mcProps=mcRunProperties(mcParagraph,mcRun);String mcFont=mcRunAttribute(mcProps,"rFonts","ascii"),mcSize=mcRunAttribute(mcProps,"sz","val"),mcColor=mcRunAttribute(mcProps,"color","val");double mcPoints=mcDefaultSize();if(mcSize!=null)try{mcPoints=Double.parseDouble(mcSize)/2;}catch(NumberFormatException mcIgnored){}
        return "font-family:"+mcEsc(mcFont==null?mcDefaultFont():mcFont)+";font-size:"+mcPoints+"pt;"+(mcEnabled(mcRunAttribute(mcProps,"b","val"))?"font-weight:bold;":"")+(mcEnabled(mcRunAttribute(mcProps,"i","val"))?"font-style:italic;":"")+(mcEnabled(mcRunAttribute(mcProps,"u","val"))?"text-decoration:underline;":"")+(mcColor!=null&&mcColor.matches("[a-fA-F0-9]{6}")?"color:#"+mcColor+";":"");
    }
    private double mcDefaultSize(){var mcStyles=mcDocument.getStyles();var mcDefaults=mcStyles==null?null:mcStyles.getDefaultRunStyle();return mcDefaults!=null&&mcDefaults.getFontSize()>0?mcDefaults.getFontSize():11;}
    private String mcTable(XWPFTable mcTable){
        String mcTableId="t"+(++mcNextId);mcTables.put(mcTableId,mcTable);StringBuilder mcB=new StringBuilder("<table data-table=\"").append(mcTableId).append("\"").append(mcReadOnly?" style=\""+mcTableStyle(mcTable)+"\"":"").append(">");
        for(XWPFTableRow mcRow:mcTable.getRows()){
            mcB.append("<tr>");for(XWPFTableCell mcCell:mcRow.getTableCells()){
                String mcId="c"+(++mcNextId);mcCells.put(mcId,mcCell);mcB.append("<td data-cell=\"").append(mcId).append("\"").append(mcReadOnly?" style=\""+mcCellStyle(mcCell)+"\"":"").append(">");for(IBodyElement mcElement:mcCell.getBodyElements()){if(mcElement instanceof XWPFParagraph mcP)mcB.append(mcParagraph(mcP));else if(mcElement instanceof XWPFTable mcNested)mcB.append(mcTable(mcNested));}mcB.append("</td>");
            }mcB.append("</tr>");
        }return mcB.append("</table>").toString();
    }
    private String mcParagraph(XWPFParagraph mcP){
        String mcId="p"+(++mcNextId);mcParagraphs.put(mcId,mcP);
        StringBuilder mcB=new StringBuilder("<p data-id=\"").append(mcId).append("\" data-block-index=\"").append(mcId).append("\"");
        if(!mcReadOnly&&mcP.getText().contains("\u200B"))mcB.append(" data-changed=\"true\"");
        if(mcP.getBody() instanceof XWPFHeaderFooter){String mcPart="part"+mcParts.size();for(var mcEntry:mcParts.entrySet())if(mcEntry.getValue()==mcP.getBody()){mcPart=mcEntry.getKey();break;}mcParts.put(mcPart,mcP.getBody());mcB.append(" data-part=\"").append(mcPart).append("\"");}
        if(mcP.getNumID()!=null)mcB.append(" data-list=\"").append("bullet".equals(mcP.getNumFmt())?"bullet":"decimal").append("\"");
        boolean mcRule=mcP.getCTP().isSetPPr()&&mcP.getCTP().getPPr().isSetPBdr()&&mcP.getCTP().getPPr().getPBdr().isSetBottom();
        if(mcRule)mcB.append(" data-rule=\"true\"");
        mcB.append(" style=\"").append(mcReadOnly?mcParagraphStyle(mcP):"").append("text-align:").append(switch(mcP.getAlignment()){case CENTER->"center";case RIGHT->"right";case BOTH,DISTRIBUTE->"justify";default->"left";}).append(mcRule?";border-bottom:1px solid #8c959f;\">":";\">");
        if(mcP.isPageBreak())mcB.append("<span class=\"page-break\" contenteditable=\"false\"></span>");
        StringBuilder mcJoined=new StringBuilder();for(XWPFRun mcRun:mcP.getRuns())mcJoined.append(mcRun.text()==null?"":mcRun.text());
        List<MCInlineToken> mcTokens=new ArrayList<>();Matcher mcMatches=mcTOKEN.matcher(mcJoined);while(!mcReadOnly&&mcMatches.find())mcTokens.add(new MCInlineToken(mcMatches.start(),mcMatches.end(),mcMatches.group(1)));
        int mcOffset=0;
        for(XWPFRun mcR:mcP.getRuns()){
            String mcContent=mcR.text()==null?"":mcR.text();String mcFragment=mcInlineText(mcContent,mcOffset,mcTokens,mcHighlightStyle(mcP,mcR));mcOffset+=mcContent.length();
            if(!mcFragment.isEmpty()){
                if(mcReadOnly)mcB.append("<span style=\"").append(mcPreviewRunStyle(mcP,mcR)).append("\">");else{
                mcB.append("<span style=\"font-family:").append(mcEsc(mcR.getFontFamily()==null?(mcReadOnly?mcDefaultFont():"Arial"):mcR.getFontFamily())).append(";font-size:").append(mcReadOnly?(mcR.getFontSizeAsDouble()!=null?mcR.getFontSizeAsDouble():mcDefaultSize()):mcFontPoints(mcR.getFontSizeAsDouble()!=null?mcR.getFontSizeAsDouble():12)).append("pt;");
                if(mcR.isBold())mcB.append("font-weight:bold;");if(mcR.isItalic())mcB.append("font-style:italic;");
                if(mcR.getCTR().isSetRPr()&&mcR.getUnderline()!=UnderlinePatterns.NONE)mcB.append("text-decoration:underline;");
                if(mcR.getColor()!=null)mcB.append("color:#").append(mcR.getColor()).append(';');mcB.append(mcHighlightStyle(mcP,mcR)).append("\">");}
                mcB.append(mcFragment).append("</span>");
            }
            for(XWPFPicture mcPicture:mcR.getEmbeddedPictures()){
                String mcMime=mcPicture.getPictureData().getPackagePart().getContentType();
                mcB.append("<img src=\"data:").append(mcMime).append(";base64,").append(Base64.getEncoder().encodeToString(mcPicture.getPictureData().getData())).append("\" width=\"").append(Math.max(1,Math.round(mcPicture.getWidth()*96/72))).append("\" height=\"").append(Math.max(1,Math.round(mcPicture.getDepth()*96/72))).append("\" alt=\"Imagen\">");
            }
        }
        if(mcJoined.length()==0&&mcP.getRuns().stream().allMatch(mcR->mcR.getEmbeddedPictures().isEmpty()))mcB.append("<br>");return mcB.append("</p>").toString();
    }
    private static String mcParagraphStyle(XWPFParagraph mcP){
        StringBuilder mcStyle=new StringBuilder();mcStyle.append("margin-top:").append(Math.max(0,mcP.getSpacingBefore())/15.0).append("px;margin-bottom:").append(Math.max(0,mcP.getSpacingAfter())/15.0).append("px;");
        if(mcP.getIndentationLeft()>0)mcStyle.append("margin-left:").append(mcP.getIndentationLeft()/15.0).append("px;");if(mcP.getIndentationRight()>0)mcStyle.append("margin-right:").append(mcP.getIndentationRight()/15.0).append("px;");
        if(mcP.getIndentationFirstLine()>0)mcStyle.append("text-indent:").append(mcP.getIndentationFirstLine()/15.0).append("px;");else if(mcP.getIndentationHanging()>0)mcStyle.append("text-indent:-").append(mcP.getIndentationHanging()/15.0).append("px;");
        double mcSpacing=mcP.getSpacingBetween();if(mcSpacing>0)mcStyle.append("line-height:").append(mcSpacing).append(mcP.getSpacingLineRule()==LineSpacingRule.AUTO?"":"pt").append(';');return mcStyle.toString();
    }
    private static String mcTableStyle(XWPFTable mcTable){String mcWidth=mcTable.getWidthType()==TableWidthType.PCT?mcTable.getWidth()/50.0+"%":mcTable.getWidth()>0?mcTable.getWidth()/15.0+"px":"100%";return "width:"+mcWidth+";";}
    private static String mcCellStyle(XWPFTableCell mcCell){
        StringBuilder mcStyle=new StringBuilder();if(mcCell.getCTTc().getTcPr()!=null&&mcCell.getCTTc().getTcPr().getTcW()!=null&&mcCell.getCTTc().getTcPr().getTcW().getW()!=null&&mcCell.getWidth()>0)mcStyle.append("width:").append(mcCell.getWidthType()==TableWidthType.PCT?mcCell.getWidth()/50.0+"%":mcCell.getWidth()/15.0+"px").append(';');
        var mcProps=mcCell.getCTTc().getTcPr();if(mcProps!=null&&mcProps.isSetTcBorders()){var mcBorders=mcProps.getTcBorders();if(mcBorders.isSetTop())mcStyle.append(mcBorderStyle("top",mcBorders.getTop()));if(mcBorders.isSetBottom())mcStyle.append(mcBorderStyle("bottom",mcBorders.getBottom()));if(mcBorders.isSetLeft())mcStyle.append(mcBorderStyle("left",mcBorders.getLeft()));if(mcBorders.isSetRight())mcStyle.append(mcBorderStyle("right",mcBorders.getRight()));}
        else{XWPFTable mcTable=mcCell.getTableRow().getTable();String mcColor=mcTable.getInsideHBorderColor();if(mcTable.getInsideHBorderType()!=null&&mcTable.getInsideHBorderType()!=XWPFTable.XWPFBorderType.NONE)mcStyle.append("border:1px solid #").append(mcColor==null||!mcColor.matches("[A-Fa-f0-9]{6}")?"000000":mcColor).append(';');}
        String mcColor=mcCell.getColor();if(mcColor!=null&&mcColor.matches("[A-Fa-f0-9]{6}"))mcStyle.append("background:#").append(mcColor).append(';');return mcStyle.toString();
    }
    private static String mcBorderStyle(String mcSide,org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder mcBorder){String mcVal=String.valueOf(mcBorder.getVal());if("nil".equals(mcVal)||"none".equals(mcVal))return "border-"+mcSide+":none;";Object mcColor=mcBorder.getColor();String mcHex=mcColor instanceof byte[] mcBytes?java.util.HexFormat.of().formatHex(mcBytes):String.valueOf(mcColor);if(!mcHex.matches("[A-Fa-f0-9]{6}"))mcHex="000000";return "border-"+mcSide+":"+(mcBorder.getSz()==null?1:mcBorder.getSz().doubleValue()/6)+"px "+("double".equals(mcVal)?"double":"dashed".equals(mcVal)?"dashed":"solid")+" #"+mcHex+";";}
    /** @author MC */
    private record MCInlineToken(@com.fasterxml.jackson.annotation.JsonProperty("start") int mcStart,@com.fasterxml.jackson.annotation.JsonProperty("end") int mcEnd,@com.fasterxml.jackson.annotation.JsonProperty("key") String mcKey){}
    private String mcInlineText(String mcText,int mcStart,List<MCInlineToken> mcTokens,String mcHighlight){
        StringBuilder mcHtml=new StringBuilder();int mcAt=0,mcEnd=mcStart+mcText.length();
        for(MCInlineToken mcToken:mcTokens){
            if(mcToken.mcEnd()<=mcStart||mcToken.mcStart()>=mcEnd)continue;
            if(mcToken.mcStart()>=mcStart){
                mcHtml.append(mcInlineWhitespace(mcText.substring(mcAt,mcToken.mcStart()-mcStart)));String mcKey=mcEsc(mcToken.mcKey()),mcPerson=MCDocumentEngine.mcPersonaKey(mcToken.mcKey()),mcLabel=mcEsc(mcPerson.isEmpty()?mcToken.mcKey():mcPersonaNames.getOrDefault(mcPerson,mcPerson.replace('_',' '))+" · "+MCDocumentEngine.mcBaseKey(mcToken.mcKey()));
                String mcChipStyle=(mcPerson.isEmpty()?"":"--persona-hue:"+Math.floorMod(mcPerson.hashCode()*137,360)+";")+mcHighlight;
                mcHtml.append("<span class=\"variable\" data-var=\"true\" data-key=\"").append(mcKey).append("\"");
                if(!mcPerson.isEmpty())mcHtml.append(" data-persona=\"").append(mcEsc(mcPerson)).append("\"");
                if(!mcChipStyle.isEmpty())mcHtml.append(" style=\"").append(mcChipStyle).append("\"");
                mcHtml.append(" contenteditable=\"false\" draggable=\"true\" title=\"{{").append(mcKey).append("}}\">⟦").append(mcLabel).append("⟧</span>");
            }
            mcAt=Math.min(mcText.length(),mcToken.mcEnd()-mcStart);
        }
        return mcHtml.append(mcInlineWhitespace(mcText.substring(mcAt))).toString();
    }
    private static String mcInlineWhitespace(String mcText){return mcEsc(mcText).replace("\t","<span class=\"docx-tab\" data-tab=\"true\">\t</span>").replace("\n","<br/>");}
    private static String mcFontPoints(double mcPoints){return mcPoints==Math.rint(mcPoints)?Long.toString(Math.round(mcPoints)):Double.toString(mcPoints);}
    private String mcHighlightStyle(XWPFParagraph mcParagraph,XWPFRun mcRun){
        var mcProps=mcRunProperties(mcParagraph,mcRun);String mcFill=mcRunAttribute(mcProps,"shd","fill");
        if(mcFill!=null&&mcFill.matches("[a-fA-F0-9]{6}"))return "background-color:#"+mcFill+";";
        String mcHighlight=mcRunAttribute(mcProps,"highlight","val");
        String mcColor=mcWORD_HIGHLIGHTS.get(mcHighlight==null?"":mcHighlight);
        return mcColor==null?"":"background-color:#"+mcColor+";";
    }
    private static final Map<String,String> mcWORD_HIGHLIGHTS=Map.ofEntries(
        Map.entry("yellow","FFFF00"),Map.entry("green","00FF00"),Map.entry("cyan","00FFFF"),Map.entry("magenta","FF00FF"),
        Map.entry("blue","0000FF"),Map.entry("red","FF0000"),Map.entry("darkBlue","000080"),Map.entry("darkCyan","008080"),
        Map.entry("darkGreen","008000"),Map.entry("darkMagenta","800080"),Map.entry("darkRed","800000"),Map.entry("darkYellow","808000"),
        Map.entry("darkGray","808080"),Map.entry("lightGray","C0C0C0"),Map.entry("black","000000"),Map.entry("white","FFFFFF"));
    private static String mcEsc(String mcS){return mcS.replace("\u200B","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}

    String mcApply(String mcXml) throws Exception {
        DocumentBuilderFactory mcFactory=DocumentBuilderFactory.newInstance();mcFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);mcFactory.setFeature("http://xml.org/sax/features/external-general-entities",false);mcFactory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        org.w3c.dom.Document mcDom=mcFactory.newDocumentBuilder().parse(new InputSource(new StringReader(mcXml)));
        Element mcEditor=mcDom.getDocumentElement();
        List<Element> mcBlocks=new ArrayList<>();mcCollectBlocks(mcEditor,mcBlocks);
        Map<String,Element> mcSeen=new HashMap<>();
        for(Element mcBlock:mcBlocks){if("table".equalsIgnoreCase(mcBlock.getTagName()))continue;String mcId=mcBlockId(mcBlock);
            if("true".equals(mcBlock.getAttribute("data-new-block"))){mcBlock.removeAttribute("data-id");mcBlock.removeAttribute("data-block-index");mcBlock.setAttribute("data-changed","true");continue;}
            if(!mcId.isBlank()&&mcSeen.containsKey(mcId)){mcSeen.get(mcId).setAttribute("data-changed","true");mcBlock.removeAttribute("data-id");mcBlock.removeAttribute("data-block-index");mcBlock.setAttribute("data-new-block","true");mcBlock.setAttribute("data-changed","true");}
            else if(!mcId.isBlank())mcSeen.put(mcId,mcBlock);
        }
        for(int mcI=0;mcI<mcBlocks.size();mcI++){
            Element mcBlock=mcBlocks.get(mcI);if(!"table".equalsIgnoreCase(mcBlock.getTagName()))continue;
            XWPFTable mcTable=mcTables.get(mcBlock.getAttribute("data-table"));boolean mcCreated=mcTable==null;
            if(mcCreated){
                XWPFParagraph mcNextParagraph=null;XWPFTable mcNextTable=null;
                for(int mcJ=mcI+1;mcJ<mcBlocks.size();mcJ++){Element mcFuture=mcBlocks.get(mcJ);mcNextParagraph=mcParagraphs.get(mcBlockId(mcFuture));if(mcNextParagraph!=null&&mcNextParagraph.getBody()==mcDocument)break;mcNextParagraph=null;mcNextTable=mcTables.get(mcFuture.getAttribute("data-table"));if(mcNextTable!=null)break;}
                if(mcNextParagraph!=null)mcTable=mcDocument.insertNewTbl(mcNextParagraph.getCTP().newCursor());else if(mcNextTable!=null)mcTable=mcDocument.insertNewTbl(mcNextTable.getCTTbl().newCursor());else mcTable=mcDocument.createTable();
                String mcId="newTable"+(++mcNextId);mcTables.put(mcId,mcTable);mcBlock.setAttribute("data-table",mcId);
            }
            if(mcCreated||"true".equals(mcBlock.getAttribute("data-changed")))mcReconcileTable(mcTable,mcBlock);
        }
        Map<IBody,IBodyElement> mcPrevious=new IdentityHashMap<>();Map<IBody,XWPFParagraph> mcBaseStyle=new IdentityHashMap<>();Map<IBody,MCStyle> mcRunStyles=new IdentityHashMap<>();
        for(int mcI=0;mcI<mcBlocks.size();mcI++){
            Element mcBlock=mcBlocks.get(mcI);
            if("table".equalsIgnoreCase(mcBlock.getTagName())){XWPFTable mcTable=mcTables.get(mcBlock.getAttribute("data-table"));if(mcTable!=null)mcPrevious.put(mcTable.getBody(),mcTable);continue;}
            String mcIdentity=mcBlockId(mcBlock);XWPFParagraph mcP="true".equals(mcBlock.getAttribute("data-new-block"))?null:mcParagraphs.get(mcIdentity);
            if(mcP==null){
                Element mcCellElement=mcAncestor(mcBlock,"td");IBody mcBody=mcCellElement==null?mcParts.getOrDefault(mcBlock.getAttribute("data-part"),mcDocument):mcCells.get(mcCellElement.getAttribute("data-cell"));if(mcBody==null)mcBody=mcDocument;
                IBodyElement mcPreceding=mcPrevious.get(mcBody);XWPFParagraph mcBase=mcBaseStyle.get(mcBody);
                if(mcPreceding!=null){try(var mcCursor=(mcPreceding instanceof XWPFParagraph mcPrior?mcPrior.getCTP():((XWPFTable)mcPreceding).getCTTbl()).newCursor()){mcCursor.toEndToken();mcCursor.toNextToken();mcP=mcBody.insertNewParagraph(mcCursor);}}
                else{
                    for(int mcJ=mcI+1;mcJ<mcBlocks.size()&&mcP==null;mcJ++){Element mcNext=mcBlocks.get(mcJ);XWPFParagraph mcFollowing=mcParagraphs.get(mcBlockId(mcNext));XWPFTable mcTable=mcTables.get(mcNext.getAttribute("data-table"));
                        if(mcFollowing!=null&&mcFollowing.getBody()==mcBody){try(var mcCursor=mcFollowing.getCTP().newCursor()){mcP=mcBody.insertNewParagraph(mcCursor);}if(mcBase==null)mcBase=mcFollowing;}
                        else if(mcTable!=null&&mcTable.getBody()==mcBody)try(var mcCursor=mcTable.getCTTbl().newCursor()){mcP=mcBody.insertNewParagraph(mcCursor);}
                    }
                }
                if(mcP==null){if(mcBody instanceof XWPFTableCell mcCell)mcP=mcCell.addParagraph();else if(mcBody instanceof XWPFHeaderFooter mcPart)mcP=mcPart.createParagraph();else mcP=mcDocument.createParagraph();}
                if(mcBase!=null&&mcBase.getCTP().isSetPPr())mcP.getCTP().setPPr((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr)mcBase.getCTP().getPPr().copy());
                String mcId="new"+(++mcNextId);mcParagraphs.put(mcId,mcP);mcBlock.setAttribute("data-id",mcId);mcBlock.setAttribute("data-block-index",mcId);mcBlock.removeAttribute("data-new-block");mcWriteParagraph(mcP,mcBlock,mcRunStyles.getOrDefault(mcBody,mcBaseStyleOf(mcBase)));
            }else if("true".equals(mcBlock.getAttribute("data-changed")))mcWriteParagraph(mcP,mcBlock);
            mcPrevious.put(mcP.getBody(),mcP);mcBaseStyle.put(mcP.getBody(),mcP);if(!mcP.getRuns().isEmpty())mcRunStyles.put(mcP.getBody(),mcBaseStyleOf(mcP));
        }
        Set<String> mcPresent=new HashSet<>();for(Element mcE:mcBlocks)mcPresent.add(mcBlockId(mcE));
        for(var mcEntry:new ArrayList<>(mcParagraphs.entrySet()))if(!mcPresent.contains(mcEntry.getKey())){XWPFParagraph mcP=mcEntry.getValue();if(mcP.getBody()==mcDocument){int mcIndex=mcDocument.getPosOfParagraph(mcP);if(mcIndex>=0)mcDocument.removeBodyElement(mcIndex);}else if(mcP.getBody() instanceof XWPFTableCell mcCell){int mcIndex=mcCell.getParagraphs().indexOf(mcP);if(mcIndex>=0)mcCell.removeParagraph(mcIndex);}}
        Set<String> mcPresentTables=new HashSet<>();for(Element mcE:mcBlocks)mcPresentTables.add(mcE.getAttribute("data-table"));for(var mcEntry:mcTables.entrySet())if(!mcPresentTables.contains(mcEntry.getKey())){int mcIndex=mcDocument.getBodyElements().indexOf(mcEntry.getValue());if(mcIndex>=0)mcDocument.removeBodyElement(mcIndex);}
        var mcTransformer=javax.xml.transform.TransformerFactory.newInstance().newTransformer();mcTransformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION,"yes");StringWriter mcNormalized=new StringWriter();mcTransformer.transform(new javax.xml.transform.dom.DOMSource(mcDom),new javax.xml.transform.stream.StreamResult(mcNormalized));return mcNormalized.toString();
    }
    private void mcReconcileTable(XWPFTable mcTable,Element mcHtml){
        NodeList mcRows=mcHtml.getElementsByTagName("tr");int mcWanted=Math.max(1,mcRows.getLength());while(mcTable.getNumberOfRows()<mcWanted)mcTable.createRow();while(mcTable.getNumberOfRows()>mcWanted)mcTable.removeRow(mcTable.getNumberOfRows()-1);
        for(int mcRi=0;mcRi<mcRows.getLength();mcRi++){
            Element mcRow=(Element)mcRows.item(mcRi);List<Element> mcHtmlCells=mcDirectChildren(mcRow,"td");XWPFTableRow mcTarget=mcTable.getRow(mcRi);int mcColumns=Math.max(1,mcHtmlCells.size());while(mcTarget.getTableCells().size()<mcColumns)mcTarget.addNewTableCell();while(mcTarget.getTableCells().size()>mcColumns)mcTarget.removeCell(mcTarget.getTableCells().size()-1);
            for(int mcCi=0;mcCi<mcHtmlCells.size();mcCi++){
                Element mcHtmlCell=mcHtmlCells.get(mcCi);XWPFTableCell mcCell=mcTarget.getCell(mcCi);String mcId=mcHtmlCell.getAttribute("data-cell");if(!mcCells.containsKey(mcId)){mcId="newCell"+(++mcNextId);mcHtmlCell.setAttribute("data-cell",mcId);mcCells.put(mcId,mcCell);}
                List<Element> mcParagraphsInCell=mcDirectChildren(mcHtmlCell,"p");if(!mcParagraphsInCell.isEmpty()&&mcParagraphsInCell.get(0).getAttribute("data-id").isBlank()&&!mcCell.getParagraphs().isEmpty()){Element mcFirst=mcParagraphsInCell.get(0);String mcParagraphId="new"+(++mcNextId);mcFirst.setAttribute("data-id",mcParagraphId);mcFirst.setAttribute("data-changed","true");mcParagraphs.put(mcParagraphId,mcCell.getParagraphs().get(0));}
            }
        }
        if(mcHtml.hasAttribute("data-borders")){String mcMode=mcHtml.getAttribute("data-borders"),mcColor=mcHtml.getAttribute("data-border-color").replace("#","");if(!mcColor.matches("[0-9a-fA-F]{6}"))mcColor="8C959F";int mcSize=(int)Math.max(2,Math.min(96,mcMeasure(mcHtml.getAttribute("data-border-width"),1)*8));XWPFTable.XWPFBorderType mcLine=switch(mcHtml.getAttribute("data-border-style")){case "double"->XWPFTable.XWPFBorderType.DOUBLE;case "dashed"->XWPFTable.XWPFBorderType.DASHED;default->XWPFTable.XWPFBorderType.SINGLE;},mcNone=XWPFTable.XWPFBorderType.NONE;boolean mcOuter=mcMode.equals("all")||mcMode.equals("outer"),mcInner=mcMode.equals("all")||mcMode.equals("inner");mcTable.setTopBorder(mcOuter?mcLine:mcNone,mcSize,0,mcColor);mcTable.setBottomBorder(mcOuter?mcLine:mcNone,mcSize,0,mcColor);mcTable.setLeftBorder(mcOuter?mcLine:mcNone,mcSize,0,mcColor);mcTable.setRightBorder(mcOuter?mcLine:mcNone,mcSize,0,mcColor);mcTable.setInsideHBorder(mcInner?mcLine:mcNone,mcSize,0,mcColor);mcTable.setInsideVBorder(mcInner?mcLine:mcNone,mcSize,0,mcColor);}
        mcTable.setWidth("100%");
    }
    private static List<Element> mcDirectChildren(Element mcParent,String mcTag){List<Element> mcOut=new ArrayList<>();NodeList mcNodes=mcParent.getChildNodes();for(int mcI=0;mcI<mcNodes.getLength();mcI++)if(mcNodes.item(mcI) instanceof Element mcE&&mcE.getTagName().equalsIgnoreCase(mcTag))mcOut.add(mcE);return mcOut;}
    private static Element mcAncestor(Element mcFrom,String mcTag){Node mcNode=mcFrom.getParentNode();while(mcNode instanceof Element mcE){if(mcE.getTagName().equalsIgnoreCase(mcTag))return mcE;mcNode=mcE.getParentNode();}return null;}
    private static String mcBlockId(Element mcBlock){return mcBlock.hasAttribute("data-block-index")?mcBlock.getAttribute("data-block-index"):mcBlock.getAttribute("data-id");}
    private static void mcCollectBlocks(Element mcParent,List<Element> mcOut){
        NodeList mcChildren=mcParent.getChildNodes();for(int mcI=0;mcI<mcChildren.getLength();mcI++)if(mcChildren.item(mcI) instanceof Element mcE){String mcTag=mcE.getTagName().toLowerCase();if(mcTag.equals("p")||mcTag.equals("div")||mcTag.equals("li")){if(mcDirectChildren(mcE,"p").isEmpty()&&mcDirectChildren(mcE,"div").isEmpty()&&mcDirectChildren(mcE,"li").isEmpty())mcOut.add(mcE);else mcCollectBlocks(mcE,mcOut);}else if(mcTag.equals("table")){mcOut.add(mcE);mcCollectBlocks(mcE,mcOut);}else mcCollectBlocks(mcE,mcOut);}
    }
    private static MCStyle mcBaseStyleOf(XWPFParagraph mcParagraph){
        if(mcParagraph==null||mcParagraph.getRuns().isEmpty())return new MCStyle();XWPFRun mcRun=mcParagraph.getRuns().get(mcParagraph.getRuns().size()-1);
        return new MCStyle(mcRun.getFontFamily()==null?"Arial":mcRun.getFontFamily(),mcRun.getFontSize()>0?mcRun.getFontSize():12,mcRun.isBold(),mcRun.isItalic(),mcRun.getCTR().isSetRPr()&&mcRun.getUnderline()!=UnderlinePatterns.NONE,mcRun.getColor(),null);
    }
    private void mcWriteParagraph(XWPFParagraph mcP,Element mcE)throws Exception{mcWriteParagraph(mcP,mcE,new MCStyle());}
    private void mcWriteParagraph(XWPFParagraph mcP,Element mcE,MCStyle mcInherited) throws Exception {
        String mcAlign=mcE.getAttribute("style").toLowerCase();if(mcAlign.contains("text-align:center"))mcP.setAlignment(ParagraphAlignment.CENTER);else if(mcAlign.contains("text-align:right"))mcP.setAlignment(ParagraphAlignment.RIGHT);else if(mcAlign.contains("text-align:justify"))mcP.setAlignment(ParagraphAlignment.BOTH);else if(mcAlign.contains("text-align:left"))mcP.setAlignment(ParagraphAlignment.LEFT);
        String mcList=mcE.getAttribute("data-list");
        if("bullet".equals(mcList)||"decimal".equals(mcList)){
            if(mcP.getNumID()==null||!mcList.equals("bullet".equals(mcP.getNumFmt())?"bullet":"decimal")){mcP.setNumID(mcListNumber(mcList));mcP.setNumILvl(java.math.BigInteger.ZERO);}
        }else if(mcP.getCTP().isSetPPr()&&mcP.getCTP().getPPr().isSetNumPr())mcP.getCTP().getPPr().unsetNumPr();
        if("true".equals(mcE.getAttribute("data-rule")))mcP.setBorderBottom(Borders.SINGLE);
        while(!mcP.getRuns().isEmpty())mcP.removeRun(0);
        mcP.setPageBreak(mcHasPageBreak(mcE));
        boolean mcPlaceholder=mcE.getTextContent().isEmpty()&&mcE.getElementsByTagName("br").getLength()==1&&mcE.getElementsByTagName("img").getLength()==0;
        if(!mcPlaceholder)mcAppendNodes(mcP,mcE,mcDerive(mcInherited,mcE));
    }
    private java.math.BigInteger mcListNumber(String mcKind){
        return mcListNumbers.computeIfAbsent(mcKind,mcKey->{
            XWPFNumbering mcNumbering=mcDocument.getNumbering()==null?mcDocument.createNumbering():mcDocument.getNumbering();
            java.math.BigInteger mcId=java.math.BigInteger.ZERO;while(mcNumbering.getAbstractNum(mcId)!=null)mcId=mcId.add(java.math.BigInteger.ONE);
            var mcDefinition=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum.Factory.newInstance();mcDefinition.setAbstractNumId(mcId);
            var mcLevel=mcDefinition.addNewLvl();mcLevel.setIlvl(java.math.BigInteger.ZERO);mcLevel.addNewStart().setVal(java.math.BigInteger.ONE);
            mcLevel.addNewNumFmt().setVal("bullet".equals(mcKey)?org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.BULLET:org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.DECIMAL);
            mcLevel.addNewLvlText().setVal("bullet".equals(mcKey)?"•":"%1.");var mcIndent=mcLevel.addNewPPr().addNewInd();mcIndent.setLeft(java.math.BigInteger.valueOf(720));mcIndent.setHanging(java.math.BigInteger.valueOf(360));
            return mcNumbering.addNum(mcNumbering.addAbstractNum(new XWPFAbstractNum(mcDefinition)));
        });
    }
    private static boolean mcHasPageBreak(Element mcE){NodeList mcSpans=mcE.getElementsByTagName("span");for(int mcI=0;mcI<mcSpans.getLength();mcI++)if(mcSpans.item(mcI) instanceof Element mcS&&mcS.getAttribute("class").contains("page-break"))return true;return false;}
    /** @author MC */
    private record MCStyle(@com.fasterxml.jackson.annotation.JsonProperty("font") String mcFont,@com.fasterxml.jackson.annotation.JsonProperty("size") double mcSize,@com.fasterxml.jackson.annotation.JsonProperty("bold") boolean mcBold,@com.fasterxml.jackson.annotation.JsonProperty("italic") boolean mcItalic,@com.fasterxml.jackson.annotation.JsonProperty("underline") boolean mcUnderline,@com.fasterxml.jackson.annotation.JsonProperty("color") String mcColor,@com.fasterxml.jackson.annotation.JsonProperty("highlight") String mcHighlight){MCStyle(){this("Arial",12,false,false,false,null,null);}}
    private static void mcAppendNodes(XWPFParagraph mcP,Node mcParent,MCStyle mcInherited) throws Exception {
        NodeList mcNodes=mcParent.getChildNodes();for(int mcI=0;mcI<mcNodes.getLength();mcI++){
            Node mcN=mcNodes.item(mcI);if(mcN.getNodeType()==Node.TEXT_NODE){String mcText=mcN.getTextContent().replace("\u200B","");if(!mcText.isEmpty()){XWPFRun mcR=mcP.createRun();mcStyle(mcR,mcInherited);mcAppendText(mcR,mcText);}}
            else if(mcN instanceof Element mcE){String mcTag=mcE.getTagName().toLowerCase();if(Arrays.asList(mcE.getAttribute("class").split("\\s+")).contains("variable")){XWPFRun mcR=mcP.createRun();mcStyle(mcR,mcDerive(mcInherited,mcE));mcR.setText("{{"+mcE.getAttribute("data-key")+"}}");}
                else if("true".equals(mcE.getAttribute("data-tab"))){XWPFRun mcR=mcP.createRun();mcStyle(mcR,mcDerive(mcInherited,mcE));mcR.addTab();}
                else if(mcTag.equals("br")){if(mcE!=mcTrailingBreak(mcParagraphElement(mcE)))mcP.createRun().addBreak();}
                else if(mcTag.equals("img")){String mcSrc=mcE.getAttribute("src");int mcComma=mcSrc.indexOf(",");if(mcSrc.startsWith("data:")&&mcComma>0){String mcMime=mcSrc.substring(5,mcComma).toLowerCase();int mcType=mcMime.contains("png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;byte[] mcBytes=Base64.getDecoder().decode(mcSrc.substring(mcComma+1));double mcWidth=mcMeasure(mcE.getAttribute("width"),240),mcHeight=mcMeasure(mcE.getAttribute("height"),160);try(InputStream mcIn=new ByteArrayInputStream(mcBytes)){mcP.createRun().addPicture(mcIn,mcType,"editor-image",Units.toEMU(mcWidth*72/96),Units.toEMU(mcHeight*72/96));}}}
                else if(!"page-break".equals(mcE.getAttribute("class")))mcAppendNodes(mcP,mcE,mcDerive(mcInherited,mcE));
            }
        }
    }
    private static Node mcParagraphElement(Node mcNode){Node mcCurrent=mcNode;while(mcCurrent.getParentNode() instanceof Element mcE&&!Set.of("p","div","li").contains(((Element)mcCurrent).getTagName().toLowerCase()))mcCurrent=mcE;return mcCurrent;}
    private static Node mcTrailingBreak(Node mcNode){Node mcLast=mcNode.getLastChild();while(mcLast!=null&&mcLast.getNodeType()==Node.TEXT_NODE&&mcLast.getTextContent().isEmpty())mcLast=mcLast.getPreviousSibling();if(mcLast instanceof Element mcE){if("br".equalsIgnoreCase(mcE.getTagName()))return mcE;return mcTrailingBreak(mcE);}return null;}
    private static double mcMeasure(String mcText,double mcFallback){try{return Math.max(1,Double.parseDouble(mcText.replaceAll("[^0-9.]","")));}catch(Exception mcIgnored){return mcFallback;}}
    private static void mcAppendText(XWPFRun mcRun,String mcText){
        int mcStart=0;for(int mcI=0;mcI<mcText.length();mcI++)if(mcText.charAt(mcI)=='\t'||mcText.charAt(mcI)=='\n'){
            if(mcI>mcStart)mcRun.setText(mcText.substring(mcStart,mcI));if(mcText.charAt(mcI)=='\t')mcRun.addTab();else mcRun.addBreak();mcStart=mcI+1;
        }if(mcStart<mcText.length())mcRun.setText(mcText.substring(mcStart));
    }
    private static MCStyle mcDerive(MCStyle mcBase,Element mcE){
        Map<String,String> mcCss=new HashMap<>();for(String mcDeclaration:mcE.getAttribute("style").split(";")){int mcColon=mcDeclaration.indexOf(':');if(mcColon>0)mcCss.put(mcDeclaration.substring(0,mcColon).trim().toLowerCase(Locale.ROOT),mcDeclaration.substring(mcColon+1).trim());}
        String mcTag=mcE.getTagName().toLowerCase(Locale.ROOT),mcFont=mcBase.mcFont(),mcColor=mcBase.mcColor(),mcHighlight=mcBase.mcHighlight();double mcSize=mcBase.mcSize();
        boolean mcBold=mcBase.mcBold()||Set.of("b","strong").contains(mcTag),mcItalic=mcBase.mcItalic()||Set.of("i","em").contains(mcTag),mcUnderline=mcBase.mcUnderline()||mcTag.equals("u");
        String mcValue=mcCss.get("font-family");if(mcValue!=null&&!mcValue.equalsIgnoreCase("inherit"))mcFont=mcValue.split(",")[0].replace("'","").replace("\"","").trim();
        mcValue=mcCss.get("font-size");if(mcValue!=null){Matcher mcM=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(pt|px)?").matcher(mcValue);if(mcM.matches())mcSize=Double.parseDouble(mcM.group(1))*("px".equals(mcM.group(2))?.75:1);}
        mcValue=mcCss.get("font-weight");if(mcValue!=null&&!mcValue.equalsIgnoreCase("inherit"))mcBold=mcValue.equalsIgnoreCase("bold")||mcValue.equalsIgnoreCase("bolder")||mcValue.matches("[6-9]00");
        mcValue=mcCss.get("font-style");if(mcValue!=null&&!mcValue.equalsIgnoreCase("inherit"))mcItalic=mcValue.equalsIgnoreCase("italic")||mcValue.equalsIgnoreCase("oblique");
        mcValue=mcCss.getOrDefault("text-decoration-line",mcCss.get("text-decoration"));if(mcValue!=null&&!mcValue.equalsIgnoreCase("inherit"))mcUnderline=mcValue.contains("underline");
        if(mcE.hasAttribute("color"))mcColor=mcCssColor(mcE.getAttribute("color"),mcColor);if(mcCss.containsKey("color"))mcColor=mcCssColor(mcCss.get("color"),mcColor);
        if(mcCss.containsKey("background-color"))mcHighlight=mcCssColor(mcCss.get("background-color"),mcHighlight);
        if(mcE.hasAttribute("face"))mcFont=mcE.getAttribute("face");
        return new MCStyle(mcFont,mcSize,mcBold,mcItalic,mcUnderline,mcColor,mcHighlight);
    }
    private static String mcCssColor(String mcValue,String mcFallback){
        mcValue=mcValue.trim().toLowerCase(Locale.ROOT);if(mcValue.matches("#[0-9a-f]{6}"))return mcValue.substring(1).toUpperCase(Locale.ROOT);
        if(mcValue.matches("#[0-9a-f]{3}"))return (""+mcValue.charAt(1)+mcValue.charAt(1)+mcValue.charAt(2)+mcValue.charAt(2)+mcValue.charAt(3)+mcValue.charAt(3)).toUpperCase(Locale.ROOT);
        Matcher mcRgb=Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([0-9.]+))?\\s*\\)").matcher(mcValue);
        if(mcRgb.matches()){if(mcRgb.group(4)!=null&&Double.parseDouble(mcRgb.group(4))==0)return null;return String.format("%02X%02X%02X",Math.min(255,Integer.parseInt(mcRgb.group(1))),Math.min(255,Integer.parseInt(mcRgb.group(2))),Math.min(255,Integer.parseInt(mcRgb.group(3))));}
        if(mcValue.equals("transparent")||mcValue.equals("none"))return null;
        return switch(mcValue){case "black"->"000000";case "white"->"FFFFFF";case "red"->"FF0000";case "yellow"->"FFFF00";case "blue"->"0000FF";case "green"->"008000";default->mcFallback;};
    }
    private static void mcStyle(XWPFRun mcR,MCStyle mcS){mcR.setFontFamily(mcS.mcFont());mcR.setFontSize(mcS.mcSize());mcR.setBold(mcS.mcBold());mcR.setItalic(mcS.mcItalic());if(mcS.mcUnderline())mcR.setUnderline(UnderlinePatterns.SINGLE);if(mcS.mcColor()!=null)mcR.setColor(mcS.mcColor());if(mcS.mcHighlight()!=null){var mcProps=mcR.getCTR().isSetRPr()?mcR.getCTR().getRPr():mcR.getCTR().addNewRPr();mcProps.addNewShd().setFill(mcS.mcHighlight());}}
}
