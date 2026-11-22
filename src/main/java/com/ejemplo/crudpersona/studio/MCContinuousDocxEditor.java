package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.*;
import java.util.*;
import java.util.regex.*;

final class MCContinuousDocxEditor {
    private static final Pattern MCTOKEN=Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");
    private final XWPFDocument MCdocument;
    private final Map<String,XWPFParagraph> MCparagraphs=new LinkedHashMap<>();
    private final Map<String,XWPFTableCell> MCcells=new LinkedHashMap<>();
    private final Map<String,XWPFTable> MCtables=new LinkedHashMap<>();
    private final Map<String,IBody> MCparts=new LinkedHashMap<>();
    private int MCnextId;
    private boolean MCreadOnly;
    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr MCpreviewDefaults;
    private final Map<String,java.math.BigInteger> MClistNumbers=new HashMap<>();

    private final Map<String,String> MCpersonaNames;
    MCContinuousDocxEditor(XWPFDocument MCdocument){this(MCdocument,Map.of());}
    MCContinuousDocxEditor(XWPFDocument MCdocument,Map<String,String> MCpersonaNames){this.MCdocument=MCdocument;this.MCpersonaNames=Map.copyOf(MCpersonaNames);}

    String MCgenerationHtml()throws IOException{
        try(InputStream MCinput=Objects.requireNonNull(getClass().getResourceAsStream("/editor/document-editor.html"))){
            return new String(MCinput.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
                    .replace("__DOCUMENT_CONTENT__",MChtml());
        }
    }

    String MChtml(){
        MCparagraphs.clear();MCcells.clear();MCtables.clear();MCparts.clear();MCnextId=0;
        StringBuilder MCout=new StringBuilder();
        for(XWPFHeader MCh:MCdocument.getHeaderList())for(XWPFParagraph MCp:MCh.getParagraphs())MCout.append(MCparagraph(MCp));
        for(IBodyElement MCelement:MCdocument.getBodyElements()){
            if(MCelement instanceof XWPFParagraph MCp)MCout.append(MCparagraph(MCp));
            else if(MCelement instanceof XWPFTable MCt)MCout.append(MCtable(MCt));
        }
        for(XWPFFooter MCf:MCdocument.getFooterList())for(XWPFParagraph MCp:MCf.getParagraphs())MCout.append(MCparagraph(MCp));
        if(MCout.length()==0)MCout.append("<p><br></p>");
        return MCout.toString();
    }

    String MCpreviewHtml(){
        MCreadOnly=true;
        if(MCdocument.getStyles()!=null&&MCdocument.getStyles().getPackagePart()!=null)try(InputStream MCin=MCdocument.getStyles().getPackagePart().getInputStream()){
            var MCstyles=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles.Factory.parse(MCin);if(MCstyles.getDocDefaults()!=null&&MCstyles.getDocDefaults().getRPrDefault()!=null)MCpreviewDefaults=MCstyles.getDocDefaults().getRPrDefault().getRPr();
        }catch(Exception MCignored){}
        try{
            var MCsection=MCdocument.getDocument().getBody().getSectPr();double MCwidth=794,MCheight=1123,MCleft=96,MCright=96,MCtop=96,MCbottom=96,MCheader=48,MCfooter=48;
            if(MCsection!=null){
                if(MCsection.isSetPgSz()){MCwidth=((Number)MCsection.getPgSz().getW()).doubleValue()/15; MCheight=((Number)MCsection.getPgSz().getH()).doubleValue()/15;}
                if(MCsection.isSetPgMar()){var MCm=MCsection.getPgMar();MCleft=MCtwips(MCm.getLeft(),MCleft);MCright=MCtwips(MCm.getRight(),MCright);MCtop=MCtwips(MCm.getTop(),MCtop);MCbottom=MCtwips(MCm.getBottom(),MCbottom);MCheader=MCtwips(MCm.getHeader(),MCheader);MCfooter=MCtwips(MCm.getFooter(),MCfooter);}
            }
            StringBuilder MCbody=new StringBuilder(),MChead=new StringBuilder(),MCfoot=new StringBuilder();
            MCrenderBody(MCdocument.getBodyElements(),MCbody);
            if(!MCdocument.getHeaderList().isEmpty())MCrenderBody(MCdocument.getHeaderList().get(0).getBodyElements(),MChead);
            if(!MCdocument.getFooterList().isEmpty())MCrenderBody(MCdocument.getFooterList().get(0).getBodyElements(),MCfoot);
            String MCdefaults="font-family:"+MCesc(MCdefaultFont())+";font-size:"+MCdefaultSize()+"pt;";
            String MCcss="html,body{margin:0;padding:0;background:#f6f8fa;color:#000}*{box-sizing:border-box}#pages{position:relative;width:100%;overflow:hidden}.page-slot{position:relative;margin:0 auto 12px}.doc-page{position:absolute;top:0;left:0;background:#fff;transform-origin:top left;width:"+MCwidth+"px;height:"+MCheight+"px;"+MCdefaults+"}.page-body{position:absolute;left:"+MCleft+"px;top:"+MCtop+"px;width:"+(MCwidth-MCleft-MCright)+"px;height:"+(MCheight-MCtop-MCbottom)+"px;overflow:hidden}.doc-flow{width:"+(MCwidth-MCleft-MCright)+"px;height:"+(MCheight-MCtop-MCbottom)+"px;column-width:"+(MCwidth-MCleft-MCright)+"px;column-gap:0;column-fill:auto;overflow:visible;"+MCdefaults+"}.doc-header,.doc-footer{position:absolute;left:"+MCleft+"px;right:"+MCright+"px}.doc-header{top:"+MCheader+"px}.doc-footer{bottom:"+MCfooter+"px}p{white-space:pre-wrap;overflow-wrap:break-word;margin:0;min-height:1em;line-height:normal}table{border-collapse:collapse;max-width:100%}td{vertical-align:top;padding:0 5px}tr{break-inside:avoid}img{max-width:100%}.page-break{break-before:column;display:block}p[data-page-break]{break-before:column}p[data-list]{display:list-item;margin-left:24px}p[data-list=bullet]{list-style-type:disc}p[data-list=decimal]{list-style-type:decimal}#measure{position:absolute;left:-100000px;top:0;visibility:hidden}";
            return "<!doctype html><html><head><meta charset=\"utf-8\"><style>"+MCcss+"</style></head><body contenteditable=\"false\"><div id=\"measure\"><div class=\"doc-flow\">"+MCbody+"</div></div><div id=\"pages\"></div><template id=\"header\">"+MChead+"</template><template id=\"footer\">"+MCfoot+"</template><script>"+
                "(function(){var MCflow=document.querySelector('#measure .doc-flow'),MCpages=document.getElementById('pages'),MCw="+MCwidth+",MCh="+MCheight+",MCcw="+(MCwidth-MCleft-MCright)+";"+
                "function MCfit(){var MCscale=Math.min(1,document.documentElement.clientWidth/MCw);Array.from(MCpages.children).forEach(function(MCslot){MCslot.style.width=MCw*MCscale+'px';MCslot.style.height=MCh*MCscale+'px';MCslot.firstChild.style.transform='scale('+MCscale+')'})}"+
                "function MCrender(){MCpages.innerHTML='';var MCcount=Math.max(1,Math.ceil(MCflow.scrollWidth/MCcw));for(var MCi=0;MCi<MCcount;MCi++){var MCslot=document.createElement('div');MCslot.className='page-slot';var MCpage=document.createElement('article');MCpage.className='doc-page';var MCviewport=document.createElement('div');MCviewport.className='page-body';var MCcopy=MCflow.cloneNode(true);MCcopy.style.position='relative';MCcopy.style.left=(-MCi*MCcw)+'px';MCviewport.appendChild(MCcopy);MCpage.appendChild(MCviewport);['header','footer'].forEach(function(MCpart){var MCel=document.createElement('div');MCel.className='doc-'+MCpart;MCel.innerHTML=document.getElementById(MCpart).innerHTML;MCpage.appendChild(MCel)});MCslot.appendChild(MCpage);MCpages.appendChild(MCslot)}MCfit();window.scrollTo(0,0);document.documentElement.scrollTop=0;document.body.scrollTop=0;window.previewReady=true}"+
                "window.addEventListener('resize',MCfit);window.addEventListener('load',MCrender);})();</script></body></html>";
        }finally{MCreadOnly=false;}
    }
    private void MCrenderBody(List<IBodyElement> MCelements,StringBuilder MCout){for(IBodyElement MCelement:MCelements){if(MCelement instanceof XWPFParagraph MCp)MCout.append(MCparagraph(MCp));else if(MCelement instanceof XWPFTable MCt)MCout.append(MCtable(MCt));}}
    private static double MCtwips(Object MCvalue,double MCfallback){return MCvalue instanceof Number MCn?MCn.doubleValue()/15:MCfallback;}
    private String MCdefaultFont(){String MCfont=MCrunAttribute(MCpreviewDefaults,"rFonts","ascii");return MCfont==null?"Calibri":MCfont;}
    private static final String MCWORD_NS="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static String MCrunAttribute(org.apache.xmlbeans.XmlObject MCprops,String MCname,String MCattribute){
        if(MCprops==null)return null;for(Node MCnode=MCprops.getDomNode().getFirstChild();MCnode!=null;MCnode=MCnode.getNextSibling())if(MCnode instanceof Element MCe&&MCname.equals(MCe.getLocalName()))return MCe.hasAttributeNS(MCWORD_NS,MCattribute)?MCe.getAttributeNS(MCWORD_NS,MCattribute):"true";return null;
    }
    private List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> MCrunProperties(XWPFParagraph MCparagraph,XWPFRun MCrun){
        List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> MCout=new ArrayList<>();if(MCrun.getCTR().isSetRPr())MCout.add(MCrun.getCTR().getRPr());
        var MCstyles=MCdocument.getStyles();if(MCstyles!=null)for(String MCstart:Arrays.asList(MCrun.getStyle(),MCparagraph.getStyleID(),"Normal")){String MCkey=MCstart;Set<String> MCvisited=new HashSet<>();while(MCkey!=null&&MCvisited.add(MCkey)){XWPFStyle MCstyle=MCstyles.getStyle(MCkey);if(MCstyle==null)break;if(MCstyle.getCTStyle().isSetRPr())MCout.add(MCstyle.getCTStyle().getRPr());MCkey=MCstyle.getBasisStyleID();}}
        if(MCpreviewDefaults!=null)MCout.add(MCpreviewDefaults);return MCout;
    }
    private static String MCrunAttribute(List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr> MCprops,String MCname,String MCattr){for(var MCprop:MCprops){String MCvalue=MCrunAttribute(MCprop,MCname,MCattr);if(MCvalue!=null)return MCvalue;}return null;}
    private static boolean MCenabled(String MCvalue){return MCvalue!=null&&!Set.of("false","0","off","none").contains(MCvalue);}
    private String MCpreviewRunStyle(XWPFParagraph MCparagraph,XWPFRun MCrun){
        var MCprops=MCrunProperties(MCparagraph,MCrun);String MCfont=MCrunAttribute(MCprops,"rFonts","ascii"),MCsize=MCrunAttribute(MCprops,"sz","val"),MCcolor=MCrunAttribute(MCprops,"color","val");double MCpoints=MCdefaultSize();if(MCsize!=null)try{MCpoints=Double.parseDouble(MCsize)/2;}catch(NumberFormatException MCignored){}
        return "font-family:"+MCesc(MCfont==null?MCdefaultFont():MCfont)+";font-size:"+MCpoints+"pt;"+(MCenabled(MCrunAttribute(MCprops,"b","val"))?"font-weight:bold;":"")+(MCenabled(MCrunAttribute(MCprops,"i","val"))?"font-style:italic;":"")+(MCenabled(MCrunAttribute(MCprops,"u","val"))?"text-decoration:underline;":"")+(MCcolor!=null&&MCcolor.matches("[a-fA-F0-9]{6}")?"color:#"+MCcolor+";":"");
    }
    private double MCdefaultSize(){var MCstyles=MCdocument.getStyles();var MCdefaults=MCstyles==null?null:MCstyles.getDefaultRunStyle();return MCdefaults!=null&&MCdefaults.getFontSize()>0?MCdefaults.getFontSize():11;}
    private String MCtable(XWPFTable MCtable){
        String MCtableId="t"+(++MCnextId);MCtables.put(MCtableId,MCtable);StringBuilder MCb=new StringBuilder("<table data-table=\"").append(MCtableId).append("\"").append(MCreadOnly?" style=\""+MCtableStyle(MCtable)+"\"":"").append(">");
        for(XWPFTableRow MCrow:MCtable.getRows()){
            MCb.append("<tr>");for(XWPFTableCell MCcell:MCrow.getTableCells()){
                String MCid="c"+(++MCnextId);MCcells.put(MCid,MCcell);MCb.append("<td data-cell=\"").append(MCid).append("\"").append(MCreadOnly?" style=\""+MCcellStyle(MCcell)+"\"":"").append(">");for(IBodyElement MCelement:MCcell.getBodyElements()){if(MCelement instanceof XWPFParagraph MCp)MCb.append(MCparagraph(MCp));else if(MCelement instanceof XWPFTable MCnested)MCb.append(MCtable(MCnested));}MCb.append("</td>");
            }MCb.append("</tr>");
        }return MCb.append("</table>").toString();
    }
    private String MCparagraph(XWPFParagraph MCp){
        String MCid="p"+(++MCnextId);MCparagraphs.put(MCid,MCp);
        StringBuilder MCb=new StringBuilder("<p data-id=\"").append(MCid).append("\" data-block-index=\"").append(MCid).append("\"");
        if(!MCreadOnly&&MCp.getText().contains("\u200B"))MCb.append(" data-changed=\"true\"");
        if(MCp.getBody() instanceof XWPFHeaderFooter){String MCpart="part"+MCparts.size();for(var MCentry:MCparts.entrySet())if(MCentry.getValue()==MCp.getBody()){MCpart=MCentry.getKey();break;}MCparts.put(MCpart,MCp.getBody());MCb.append(" data-part=\"").append(MCpart).append("\"");}
        if(MCp.getNumID()!=null)MCb.append(" data-list=\"").append("bullet".equals(MCp.getNumFmt())?"bullet":"decimal").append("\"");
        boolean MCrule=MCp.getCTP().isSetPPr()&&MCp.getCTP().getPPr().isSetPBdr()&&MCp.getCTP().getPPr().getPBdr().isSetBottom();
        if(MCrule)MCb.append(" data-rule=\"true\"");
        MCb.append(" style=\"").append(MCreadOnly?MCparagraphStyle(MCp):"").append("text-align:").append(switch(MCp.getAlignment()){case CENTER->"center";case RIGHT->"right";case BOTH,DISTRIBUTE->"justify";default->"left";}).append(MCrule?";border-bottom:1px solid #8c959f;\">":";\">");
        if(MCp.isPageBreak())MCb.append("<span class=\"page-break\" contenteditable=\"false\"></span>");
        StringBuilder MCjoined=new StringBuilder();for(XWPFRun MCrun:MCp.getRuns())MCjoined.append(MCrun.text()==null?"":MCrun.text());
        List<MCInlineToken> MCtokens=new ArrayList<>();Matcher MCmatches=MCTOKEN.matcher(MCjoined);while(!MCreadOnly&&MCmatches.find())MCtokens.add(new MCInlineToken(MCmatches.start(),MCmatches.end(),MCmatches.group(1)));
        int MCoffset=0;
        for(XWPFRun MCr:MCp.getRuns()){
            String MCcontent=MCr.text()==null?"":MCr.text();String MCfragment=MCinlineText(MCcontent,MCoffset,MCtokens,MChighlightStyle(MCp,MCr));MCoffset+=MCcontent.length();
            if(!MCfragment.isEmpty()){
                if(MCreadOnly)MCb.append("<span style=\"").append(MCpreviewRunStyle(MCp,MCr)).append("\">");else{
                MCb.append("<span style=\"font-family:").append(MCesc(MCr.getFontFamily()==null?(MCreadOnly?MCdefaultFont():"Arial"):MCr.getFontFamily())).append(";font-size:").append(MCreadOnly?(MCr.getFontSizeAsDouble()!=null?MCr.getFontSizeAsDouble():MCdefaultSize()):MCfontPoints(MCr.getFontSizeAsDouble()!=null?MCr.getFontSizeAsDouble():12)).append("pt;");
                if(MCr.isBold())MCb.append("font-weight:bold;");if(MCr.isItalic())MCb.append("font-style:italic;");
                if(MCr.getCTR().isSetRPr()&&MCr.getUnderline()!=UnderlinePatterns.NONE)MCb.append("text-decoration:underline;");
                if(MCr.getColor()!=null)MCb.append("color:#").append(MCr.getColor()).append(';');MCb.append(MChighlightStyle(MCp,MCr)).append("\">");}
                MCb.append(MCfragment).append("</span>");
            }
            for(XWPFPicture MCpicture:MCr.getEmbeddedPictures()){
                String MCmime=MCpicture.getPictureData().getPackagePart().getContentType();
                MCb.append("<img src=\"data:").append(MCmime).append(";base64,").append(Base64.getEncoder().encodeToString(MCpicture.getPictureData().getData())).append("\" width=\"").append(Math.max(1,Math.round(MCpicture.getWidth()*96/72))).append("\" height=\"").append(Math.max(1,Math.round(MCpicture.getDepth()*96/72))).append("\" alt=\"Imagen\">");
            }
        }
        if(MCjoined.length()==0&&MCp.getRuns().stream().allMatch(MCr->MCr.getEmbeddedPictures().isEmpty()))MCb.append("<br>");return MCb.append("</p>").toString();
    }
    private static String MCparagraphStyle(XWPFParagraph MCp){
        StringBuilder MCstyle=new StringBuilder();MCstyle.append("margin-top:").append(Math.max(0,MCp.getSpacingBefore())/15.0).append("px;margin-bottom:").append(Math.max(0,MCp.getSpacingAfter())/15.0).append("px;");
        if(MCp.getIndentationLeft()>0)MCstyle.append("margin-left:").append(MCp.getIndentationLeft()/15.0).append("px;");if(MCp.getIndentationRight()>0)MCstyle.append("margin-right:").append(MCp.getIndentationRight()/15.0).append("px;");
        if(MCp.getIndentationFirstLine()>0)MCstyle.append("text-indent:").append(MCp.getIndentationFirstLine()/15.0).append("px;");else if(MCp.getIndentationHanging()>0)MCstyle.append("text-indent:-").append(MCp.getIndentationHanging()/15.0).append("px;");
        double MCspacing=MCp.getSpacingBetween();if(MCspacing>0)MCstyle.append("line-height:").append(MCspacing).append(MCp.getSpacingLineRule()==LineSpacingRule.AUTO?"":"pt").append(';');return MCstyle.toString();
    }
    private static String MCtableStyle(XWPFTable MCtable){String MCwidth=MCtable.getWidthType()==TableWidthType.PCT?MCtable.getWidth()/50.0+"%":MCtable.getWidth()>0?MCtable.getWidth()/15.0+"px":"100%";return "width:"+MCwidth+";";}
    private static String MCcellStyle(XWPFTableCell MCcell){
        StringBuilder MCstyle=new StringBuilder();if(MCcell.getCTTc().getTcPr()!=null&&MCcell.getCTTc().getTcPr().getTcW()!=null&&MCcell.getCTTc().getTcPr().getTcW().getW()!=null&&MCcell.getWidth()>0)MCstyle.append("width:").append(MCcell.getWidthType()==TableWidthType.PCT?MCcell.getWidth()/50.0+"%":MCcell.getWidth()/15.0+"px").append(';');
        var MCprops=MCcell.getCTTc().getTcPr();if(MCprops!=null&&MCprops.isSetTcBorders()){var MCborders=MCprops.getTcBorders();if(MCborders.isSetTop())MCstyle.append(MCborderStyle("top",MCborders.getTop()));if(MCborders.isSetBottom())MCstyle.append(MCborderStyle("bottom",MCborders.getBottom()));if(MCborders.isSetLeft())MCstyle.append(MCborderStyle("left",MCborders.getLeft()));if(MCborders.isSetRight())MCstyle.append(MCborderStyle("right",MCborders.getRight()));}
        else{XWPFTable MCtable=MCcell.getTableRow().getTable();String MCcolor=MCtable.getInsideHBorderColor();if(MCtable.getInsideHBorderType()!=null&&MCtable.getInsideHBorderType()!=XWPFTable.XWPFBorderType.NONE)MCstyle.append("border:1px solid #").append(MCcolor==null||!MCcolor.matches("[A-Fa-f0-9]{6}")?"000000":MCcolor).append(';');}
        String MCcolor=MCcell.getColor();if(MCcolor!=null&&MCcolor.matches("[A-Fa-f0-9]{6}"))MCstyle.append("background:#").append(MCcolor).append(';');return MCstyle.toString();
    }
    private static String MCborderStyle(String MCside,org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder MCborder){String MCval=String.valueOf(MCborder.getVal());if("nil".equals(MCval)||"none".equals(MCval))return "border-"+MCside+":none;";Object MCcolor=MCborder.getColor();String MChex=MCcolor instanceof byte[] MCbytes?java.util.HexFormat.of().formatHex(MCbytes):String.valueOf(MCcolor);if(!MChex.matches("[A-Fa-f0-9]{6}"))MChex="000000";return "border-"+MCside+":"+(MCborder.getSz()==null?1:MCborder.getSz().doubleValue()/6)+"px "+("double".equals(MCval)?"double":"dashed".equals(MCval)?"dashed":"solid")+" #"+MChex+";";}
    private record MCInlineToken(@com.fasterxml.jackson.annotation.JsonProperty("start") int MCstart,@com.fasterxml.jackson.annotation.JsonProperty("end") int MCend,@com.fasterxml.jackson.annotation.JsonProperty("key") String MCkey){}
    private String MCinlineText(String MCtext,int MCstart,List<MCInlineToken> MCtokens,String MChighlight){
        StringBuilder MChtml=new StringBuilder();int MCat=0,MCend=MCstart+MCtext.length();
        for(MCInlineToken MCtoken:MCtokens){
            if(MCtoken.MCend()<=MCstart||MCtoken.MCstart()>=MCend)continue;
            if(MCtoken.MCstart()>=MCstart){
                MChtml.append(MCinlineWhitespace(MCtext.substring(MCat,MCtoken.MCstart()-MCstart)));String MCkey=MCesc(MCtoken.MCkey()),MCperson=MCDocumentEngine.MCpersonaKey(MCtoken.MCkey()),MClabel=MCesc(MCperson.isEmpty()?MCtoken.MCkey():MCpersonaNames.getOrDefault(MCperson,MCperson.replace('_',' '))+" · "+MCDocumentEngine.MCbaseKey(MCtoken.MCkey()));
                String MCchipStyle=(MCperson.isEmpty()?"":"--persona-hue:"+Math.floorMod(MCperson.hashCode()*137,360)+";")+MChighlight;
                MChtml.append("<span class=\"variable\" data-var=\"true\" data-key=\"").append(MCkey).append("\"");
                if(!MCperson.isEmpty())MChtml.append(" data-persona=\"").append(MCesc(MCperson)).append("\"");
                if(!MCchipStyle.isEmpty())MChtml.append(" style=\"").append(MCchipStyle).append("\"");
                MChtml.append(" contenteditable=\"false\" draggable=\"true\" title=\"{{").append(MCkey).append("}}\">⟦").append(MClabel).append("⟧</span>");
            }
            MCat=Math.min(MCtext.length(),MCtoken.MCend()-MCstart);
        }
        return MChtml.append(MCinlineWhitespace(MCtext.substring(MCat))).toString();
    }
    private static String MCinlineWhitespace(String MCtext){return MCesc(MCtext).replace("\t","<span class=\"docx-tab\" data-tab=\"true\">\t</span>").replace("\n","<br/>");}
    private static String MCfontPoints(double MCpoints){return MCpoints==Math.rint(MCpoints)?Long.toString(Math.round(MCpoints)):Double.toString(MCpoints);}
    private String MChighlightStyle(XWPFParagraph MCparagraph,XWPFRun MCrun){
        var MCprops=MCrunProperties(MCparagraph,MCrun);String MCfill=MCrunAttribute(MCprops,"shd","fill");
        if(MCfill!=null&&MCfill.matches("[a-fA-F0-9]{6}"))return "background-color:#"+MCfill+";";
        String MChighlight=MCrunAttribute(MCprops,"highlight","val");
        String MCcolor=MCWORD_HIGHLIGHTS.get(MChighlight==null?"":MChighlight);
        return MCcolor==null?"":"background-color:#"+MCcolor+";";
    }
    private static final Map<String,String> MCWORD_HIGHLIGHTS=Map.ofEntries(
        Map.entry("yellow","FFFF00"),Map.entry("green","00FF00"),Map.entry("cyan","00FFFF"),Map.entry("magenta","FF00FF"),
        Map.entry("blue","0000FF"),Map.entry("red","FF0000"),Map.entry("darkBlue","000080"),Map.entry("darkCyan","008080"),
        Map.entry("darkGreen","008000"),Map.entry("darkMagenta","800080"),Map.entry("darkRed","800000"),Map.entry("darkYellow","808000"),
        Map.entry("darkGray","808080"),Map.entry("lightGray","C0C0C0"),Map.entry("black","000000"),Map.entry("white","FFFFFF"));
    private static String MCesc(String MCs){return MCs.replace("\u200B","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}

    String MCapply(String MCxml) throws Exception {
        DocumentBuilderFactory MCfactory=DocumentBuilderFactory.newInstance();MCfactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);MCfactory.setFeature("http://xml.org/sax/features/external-general-entities",false);MCfactory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        org.w3c.dom.Document MCdom=MCfactory.newDocumentBuilder().parse(new InputSource(new StringReader(MCxml)));
        Element MCeditor=MCdom.getDocumentElement();
        List<Element> MCblocks=new ArrayList<>();MCcollectBlocks(MCeditor,MCblocks);
        Map<String,Element> MCseen=new HashMap<>();
        for(Element MCblock:MCblocks){if("table".equalsIgnoreCase(MCblock.getTagName()))continue;String MCid=MCblockId(MCblock);
            if("true".equals(MCblock.getAttribute("data-new-block"))){MCblock.removeAttribute("data-id");MCblock.removeAttribute("data-block-index");MCblock.setAttribute("data-changed","true");continue;}
            if(!MCid.isBlank()&&MCseen.containsKey(MCid)){MCseen.get(MCid).setAttribute("data-changed","true");MCblock.removeAttribute("data-id");MCblock.removeAttribute("data-block-index");MCblock.setAttribute("data-new-block","true");MCblock.setAttribute("data-changed","true");}
            else if(!MCid.isBlank())MCseen.put(MCid,MCblock);
        }
        for(int MCi=0;MCi<MCblocks.size();MCi++){
            Element MCblock=MCblocks.get(MCi);if(!"table".equalsIgnoreCase(MCblock.getTagName()))continue;
            XWPFTable MCtable=MCtables.get(MCblock.getAttribute("data-table"));boolean MCcreated=MCtable==null;
            if(MCcreated){
                XWPFParagraph MCnextParagraph=null;XWPFTable MCnextTable=null;
                for(int MCj=MCi+1;MCj<MCblocks.size();MCj++){Element MCfuture=MCblocks.get(MCj);MCnextParagraph=MCparagraphs.get(MCblockId(MCfuture));if(MCnextParagraph!=null&&MCnextParagraph.getBody()==MCdocument)break;MCnextParagraph=null;MCnextTable=MCtables.get(MCfuture.getAttribute("data-table"));if(MCnextTable!=null)break;}
                if(MCnextParagraph!=null)MCtable=MCdocument.insertNewTbl(MCnextParagraph.getCTP().newCursor());else if(MCnextTable!=null)MCtable=MCdocument.insertNewTbl(MCnextTable.getCTTbl().newCursor());else MCtable=MCdocument.createTable();
                String MCid="newTable"+(++MCnextId);MCtables.put(MCid,MCtable);MCblock.setAttribute("data-table",MCid);
            }
            if(MCcreated||"true".equals(MCblock.getAttribute("data-changed")))MCreconcileTable(MCtable,MCblock);
        }
        Map<IBody,IBodyElement> MCprevious=new IdentityHashMap<>();Map<IBody,XWPFParagraph> MCbaseStyle=new IdentityHashMap<>();Map<IBody,MCStyle> MCrunStyles=new IdentityHashMap<>();
        for(int MCi=0;MCi<MCblocks.size();MCi++){
            Element MCblock=MCblocks.get(MCi);
            if("table".equalsIgnoreCase(MCblock.getTagName())){XWPFTable MCtable=MCtables.get(MCblock.getAttribute("data-table"));if(MCtable!=null)MCprevious.put(MCtable.getBody(),MCtable);continue;}
            String MCidentity=MCblockId(MCblock);XWPFParagraph MCp="true".equals(MCblock.getAttribute("data-new-block"))?null:MCparagraphs.get(MCidentity);
            if(MCp==null){
                Element MCcellElement=MCancestor(MCblock,"td");IBody MCbody=MCcellElement==null?MCparts.getOrDefault(MCblock.getAttribute("data-part"),MCdocument):MCcells.get(MCcellElement.getAttribute("data-cell"));if(MCbody==null)MCbody=MCdocument;
                IBodyElement MCpreceding=MCprevious.get(MCbody);XWPFParagraph MCbase=MCbaseStyle.get(MCbody);
                if(MCpreceding!=null){try(var MCcursor=(MCpreceding instanceof XWPFParagraph MCprior?MCprior.getCTP():((XWPFTable)MCpreceding).getCTTbl()).newCursor()){MCcursor.toEndToken();MCcursor.toNextToken();MCp=MCbody.insertNewParagraph(MCcursor);}}
                else{
                    for(int MCj=MCi+1;MCj<MCblocks.size()&&MCp==null;MCj++){Element MCnext=MCblocks.get(MCj);XWPFParagraph MCfollowing=MCparagraphs.get(MCblockId(MCnext));XWPFTable MCtable=MCtables.get(MCnext.getAttribute("data-table"));
                        if(MCfollowing!=null&&MCfollowing.getBody()==MCbody){try(var MCcursor=MCfollowing.getCTP().newCursor()){MCp=MCbody.insertNewParagraph(MCcursor);}if(MCbase==null)MCbase=MCfollowing;}
                        else if(MCtable!=null&&MCtable.getBody()==MCbody)try(var MCcursor=MCtable.getCTTbl().newCursor()){MCp=MCbody.insertNewParagraph(MCcursor);}
                    }
                }
                if(MCp==null){if(MCbody instanceof XWPFTableCell MCcell)MCp=MCcell.addParagraph();else if(MCbody instanceof XWPFHeaderFooter MCpart)MCp=MCpart.createParagraph();else MCp=MCdocument.createParagraph();}
                if(MCbase!=null&&MCbase.getCTP().isSetPPr())MCp.getCTP().setPPr((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr)MCbase.getCTP().getPPr().copy());
                String MCid="new"+(++MCnextId);MCparagraphs.put(MCid,MCp);MCblock.setAttribute("data-id",MCid);MCblock.setAttribute("data-block-index",MCid);MCblock.removeAttribute("data-new-block");MCwriteParagraph(MCp,MCblock,MCrunStyles.getOrDefault(MCbody,MCbaseStyleOf(MCbase)));
            }else if("true".equals(MCblock.getAttribute("data-changed")))MCwriteParagraph(MCp,MCblock);
            MCprevious.put(MCp.getBody(),MCp);MCbaseStyle.put(MCp.getBody(),MCp);if(!MCp.getRuns().isEmpty())MCrunStyles.put(MCp.getBody(),MCbaseStyleOf(MCp));
        }
        Set<String> MCpresent=new HashSet<>();for(Element MCe:MCblocks)MCpresent.add(MCblockId(MCe));
        for(var MCentry:new ArrayList<>(MCparagraphs.entrySet()))if(!MCpresent.contains(MCentry.getKey())){XWPFParagraph MCp=MCentry.getValue();if(MCp.getBody()==MCdocument){int MCindex=MCdocument.getPosOfParagraph(MCp);if(MCindex>=0)MCdocument.removeBodyElement(MCindex);}else if(MCp.getBody() instanceof XWPFTableCell MCcell){int MCindex=MCcell.getParagraphs().indexOf(MCp);if(MCindex>=0)MCcell.removeParagraph(MCindex);}}
        Set<String> MCpresentTables=new HashSet<>();for(Element MCe:MCblocks)MCpresentTables.add(MCe.getAttribute("data-table"));for(var MCentry:MCtables.entrySet())if(!MCpresentTables.contains(MCentry.getKey())){int MCindex=MCdocument.getBodyElements().indexOf(MCentry.getValue());if(MCindex>=0)MCdocument.removeBodyElement(MCindex);}
        var MCtransformer=javax.xml.transform.TransformerFactory.newInstance().newTransformer();MCtransformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION,"yes");StringWriter MCnormalized=new StringWriter();MCtransformer.transform(new javax.xml.transform.dom.DOMSource(MCdom),new javax.xml.transform.stream.StreamResult(MCnormalized));return MCnormalized.toString();
    }
    private void MCreconcileTable(XWPFTable MCtable,Element MChtml){
        NodeList MCrows=MChtml.getElementsByTagName("tr");int MCwanted=Math.max(1,MCrows.getLength());while(MCtable.getNumberOfRows()<MCwanted)MCtable.createRow();while(MCtable.getNumberOfRows()>MCwanted)MCtable.removeRow(MCtable.getNumberOfRows()-1);
        for(int MCri=0;MCri<MCrows.getLength();MCri++){
            Element MCrow=(Element)MCrows.item(MCri);List<Element> MChtmlCells=MCdirectChildren(MCrow,"td");XWPFTableRow MCtarget=MCtable.getRow(MCri);int MCcolumns=Math.max(1,MChtmlCells.size());while(MCtarget.getTableCells().size()<MCcolumns)MCtarget.addNewTableCell();while(MCtarget.getTableCells().size()>MCcolumns)MCtarget.removeCell(MCtarget.getTableCells().size()-1);
            for(int MCci=0;MCci<MChtmlCells.size();MCci++){
                Element MChtmlCell=MChtmlCells.get(MCci);XWPFTableCell MCcell=MCtarget.getCell(MCci);String MCid=MChtmlCell.getAttribute("data-cell");if(!MCcells.containsKey(MCid)){MCid="newCell"+(++MCnextId);MChtmlCell.setAttribute("data-cell",MCid);MCcells.put(MCid,MCcell);}
                List<Element> MCparagraphsInCell=MCdirectChildren(MChtmlCell,"p");if(!MCparagraphsInCell.isEmpty()&&MCparagraphsInCell.get(0).getAttribute("data-id").isBlank()&&!MCcell.getParagraphs().isEmpty()){Element MCfirst=MCparagraphsInCell.get(0);String MCparagraphId="new"+(++MCnextId);MCfirst.setAttribute("data-id",MCparagraphId);MCfirst.setAttribute("data-changed","true");MCparagraphs.put(MCparagraphId,MCcell.getParagraphs().get(0));}
            }
        }
        if(MChtml.hasAttribute("data-borders")){String MCmode=MChtml.getAttribute("data-borders"),MCcolor=MChtml.getAttribute("data-border-color").replace("#","");if(!MCcolor.matches("[0-9a-fA-F]{6}"))MCcolor="8C959F";int MCsize=(int)Math.max(2,Math.min(96,MCmeasure(MChtml.getAttribute("data-border-width"),1)*8));XWPFTable.XWPFBorderType MCline=switch(MChtml.getAttribute("data-border-style")){case "double"->XWPFTable.XWPFBorderType.DOUBLE;case "dashed"->XWPFTable.XWPFBorderType.DASHED;default->XWPFTable.XWPFBorderType.SINGLE;},MCnone=XWPFTable.XWPFBorderType.NONE;boolean MCouter=MCmode.equals("all")||MCmode.equals("outer"),MCinner=MCmode.equals("all")||MCmode.equals("inner");MCtable.setTopBorder(MCouter?MCline:MCnone,MCsize,0,MCcolor);MCtable.setBottomBorder(MCouter?MCline:MCnone,MCsize,0,MCcolor);MCtable.setLeftBorder(MCouter?MCline:MCnone,MCsize,0,MCcolor);MCtable.setRightBorder(MCouter?MCline:MCnone,MCsize,0,MCcolor);MCtable.setInsideHBorder(MCinner?MCline:MCnone,MCsize,0,MCcolor);MCtable.setInsideVBorder(MCinner?MCline:MCnone,MCsize,0,MCcolor);}
        MCtable.setWidth("100%");
    }
    private static List<Element> MCdirectChildren(Element MCparent,String MCtag){List<Element> MCout=new ArrayList<>();NodeList MCnodes=MCparent.getChildNodes();for(int MCi=0;MCi<MCnodes.getLength();MCi++)if(MCnodes.item(MCi) instanceof Element MCe&&MCe.getTagName().equalsIgnoreCase(MCtag))MCout.add(MCe);return MCout;}
    private static Element MCancestor(Element MCfrom,String MCtag){Node MCnode=MCfrom.getParentNode();while(MCnode instanceof Element MCe){if(MCe.getTagName().equalsIgnoreCase(MCtag))return MCe;MCnode=MCe.getParentNode();}return null;}
    private static String MCblockId(Element MCblock){return MCblock.hasAttribute("data-block-index")?MCblock.getAttribute("data-block-index"):MCblock.getAttribute("data-id");}
    private static void MCcollectBlocks(Element MCparent,List<Element> MCout){
        NodeList MCchildren=MCparent.getChildNodes();for(int MCi=0;MCi<MCchildren.getLength();MCi++)if(MCchildren.item(MCi) instanceof Element MCe){String MCtag=MCe.getTagName().toLowerCase();if(MCtag.equals("p")||MCtag.equals("div")||MCtag.equals("li")){if(MCdirectChildren(MCe,"p").isEmpty()&&MCdirectChildren(MCe,"div").isEmpty()&&MCdirectChildren(MCe,"li").isEmpty())MCout.add(MCe);else MCcollectBlocks(MCe,MCout);}else if(MCtag.equals("table")){MCout.add(MCe);MCcollectBlocks(MCe,MCout);}else MCcollectBlocks(MCe,MCout);}
    }
    private static MCStyle MCbaseStyleOf(XWPFParagraph MCparagraph){
        if(MCparagraph==null||MCparagraph.getRuns().isEmpty())return new MCStyle();XWPFRun MCrun=MCparagraph.getRuns().get(MCparagraph.getRuns().size()-1);
        return new MCStyle(MCrun.getFontFamily()==null?"Arial":MCrun.getFontFamily(),MCrun.getFontSize()>0?MCrun.getFontSize():12,MCrun.isBold(),MCrun.isItalic(),MCrun.getCTR().isSetRPr()&&MCrun.getUnderline()!=UnderlinePatterns.NONE,MCrun.getColor(),null);
    }
    private void MCwriteParagraph(XWPFParagraph MCp,Element MCe)throws Exception{MCwriteParagraph(MCp,MCe,new MCStyle());}
    private void MCwriteParagraph(XWPFParagraph MCp,Element MCe,MCStyle MCinherited) throws Exception {
        String MCalign=MCe.getAttribute("style").toLowerCase();if(MCalign.contains("text-align:center"))MCp.setAlignment(ParagraphAlignment.CENTER);else if(MCalign.contains("text-align:right"))MCp.setAlignment(ParagraphAlignment.RIGHT);else if(MCalign.contains("text-align:justify"))MCp.setAlignment(ParagraphAlignment.BOTH);else if(MCalign.contains("text-align:left"))MCp.setAlignment(ParagraphAlignment.LEFT);
        String MClist=MCe.getAttribute("data-list");
        if("bullet".equals(MClist)||"decimal".equals(MClist)){
            if(MCp.getNumID()==null||!MClist.equals("bullet".equals(MCp.getNumFmt())?"bullet":"decimal")){MCp.setNumID(MClistNumber(MClist));MCp.setNumILvl(java.math.BigInteger.ZERO);}
        }else if(MCp.getCTP().isSetPPr()&&MCp.getCTP().getPPr().isSetNumPr())MCp.getCTP().getPPr().unsetNumPr();
        if("true".equals(MCe.getAttribute("data-rule")))MCp.setBorderBottom(Borders.SINGLE);
        while(!MCp.getRuns().isEmpty())MCp.removeRun(0);
        MCp.setPageBreak(MChasPageBreak(MCe));
        boolean MCplaceholder=MCe.getTextContent().isEmpty()&&MCe.getElementsByTagName("br").getLength()==1&&MCe.getElementsByTagName("img").getLength()==0;
        if(!MCplaceholder)MCappendNodes(MCp,MCe,MCderive(MCinherited,MCe));
    }
    private java.math.BigInteger MClistNumber(String MCkind){
        return MClistNumbers.computeIfAbsent(MCkind,MCkey->{
            XWPFNumbering MCnumbering=MCdocument.getNumbering()==null?MCdocument.createNumbering():MCdocument.getNumbering();
            java.math.BigInteger MCid=java.math.BigInteger.ZERO;while(MCnumbering.getAbstractNum(MCid)!=null)MCid=MCid.add(java.math.BigInteger.ONE);
            var MCdefinition=org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum.Factory.newInstance();MCdefinition.setAbstractNumId(MCid);
            var MClevel=MCdefinition.addNewLvl();MClevel.setIlvl(java.math.BigInteger.ZERO);MClevel.addNewStart().setVal(java.math.BigInteger.ONE);
            MClevel.addNewNumFmt().setVal("bullet".equals(MCkey)?org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.BULLET:org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.DECIMAL);
            MClevel.addNewLvlText().setVal("bullet".equals(MCkey)?"•":"%1.");var MCindent=MClevel.addNewPPr().addNewInd();MCindent.setLeft(java.math.BigInteger.valueOf(720));MCindent.setHanging(java.math.BigInteger.valueOf(360));
            return MCnumbering.addNum(MCnumbering.addAbstractNum(new XWPFAbstractNum(MCdefinition)));
        });
    }
    private static boolean MChasPageBreak(Element MCe){NodeList MCspans=MCe.getElementsByTagName("span");for(int MCi=0;MCi<MCspans.getLength();MCi++)if(MCspans.item(MCi) instanceof Element MCs&&MCs.getAttribute("class").contains("page-break"))return true;return false;}
    private record MCStyle(@com.fasterxml.jackson.annotation.JsonProperty("font") String MCfont,@com.fasterxml.jackson.annotation.JsonProperty("size") double MCsize,@com.fasterxml.jackson.annotation.JsonProperty("bold") boolean MCbold,@com.fasterxml.jackson.annotation.JsonProperty("italic") boolean MCitalic,@com.fasterxml.jackson.annotation.JsonProperty("underline") boolean MCunderline,@com.fasterxml.jackson.annotation.JsonProperty("color") String MCcolor,@com.fasterxml.jackson.annotation.JsonProperty("highlight") String MChighlight){MCStyle(){this("Arial",12,false,false,false,null,null);}}
    private static void MCappendNodes(XWPFParagraph MCp,Node MCparent,MCStyle MCinherited) throws Exception {
        NodeList MCnodes=MCparent.getChildNodes();for(int MCi=0;MCi<MCnodes.getLength();MCi++){
            Node MCn=MCnodes.item(MCi);if(MCn.getNodeType()==Node.TEXT_NODE){String MCtext=MCn.getTextContent().replace("\u200B","");if(!MCtext.isEmpty()){XWPFRun MCr=MCp.createRun();MCstyle(MCr,MCinherited);MCappendText(MCr,MCtext);}}
            else if(MCn instanceof Element MCe){String MCtag=MCe.getTagName().toLowerCase();if(Arrays.asList(MCe.getAttribute("class").split("\\s+")).contains("variable")){XWPFRun MCr=MCp.createRun();MCstyle(MCr,MCderive(MCinherited,MCe));MCr.setText("{{"+MCe.getAttribute("data-key")+"}}");}
                else if("true".equals(MCe.getAttribute("data-tab"))){XWPFRun MCr=MCp.createRun();MCstyle(MCr,MCderive(MCinherited,MCe));MCr.addTab();}
                else if(MCtag.equals("br")){if(MCe!=MCtrailingBreak(MCparagraphElement(MCe)))MCp.createRun().addBreak();}
                else if(MCtag.equals("img")){String MCsrc=MCe.getAttribute("src");int MCcomma=MCsrc.indexOf(",");if(MCsrc.startsWith("data:")&&MCcomma>0){String MCmime=MCsrc.substring(5,MCcomma).toLowerCase();int MCtype=MCmime.contains("png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;byte[] MCbytes=Base64.getDecoder().decode(MCsrc.substring(MCcomma+1));double MCwidth=MCmeasure(MCe.getAttribute("width"),240),MCheight=MCmeasure(MCe.getAttribute("height"),160);try(InputStream MCin=new ByteArrayInputStream(MCbytes)){MCp.createRun().addPicture(MCin,MCtype,"editor-image",Units.toEMU(MCwidth*72/96),Units.toEMU(MCheight*72/96));}}}
                else if(!"page-break".equals(MCe.getAttribute("class")))MCappendNodes(MCp,MCe,MCderive(MCinherited,MCe));
            }
        }
    }
    private static Node MCparagraphElement(Node MCnode){Node MCcurrent=MCnode;while(MCcurrent.getParentNode() instanceof Element MCe&&!Set.of("p","div","li").contains(((Element)MCcurrent).getTagName().toLowerCase()))MCcurrent=MCe;return MCcurrent;}
    private static Node MCtrailingBreak(Node MCnode){Node MClast=MCnode.getLastChild();while(MClast!=null&&MClast.getNodeType()==Node.TEXT_NODE&&MClast.getTextContent().isEmpty())MClast=MClast.getPreviousSibling();if(MClast instanceof Element MCe){if("br".equalsIgnoreCase(MCe.getTagName()))return MCe;return MCtrailingBreak(MCe);}return null;}
    private static double MCmeasure(String MCtext,double MCfallback){try{return Math.max(1,Double.parseDouble(MCtext.replaceAll("[^0-9.]","")));}catch(Exception MCignored){return MCfallback;}}
    private static void MCappendText(XWPFRun MCrun,String MCtext){
        int MCstart=0;for(int MCi=0;MCi<MCtext.length();MCi++)if(MCtext.charAt(MCi)=='\t'||MCtext.charAt(MCi)=='\n'){
            if(MCi>MCstart)MCrun.setText(MCtext.substring(MCstart,MCi));if(MCtext.charAt(MCi)=='\t')MCrun.addTab();else MCrun.addBreak();MCstart=MCi+1;
        }if(MCstart<MCtext.length())MCrun.setText(MCtext.substring(MCstart));
    }
    private static MCStyle MCderive(MCStyle MCbase,Element MCe){
        Map<String,String> MCcss=new HashMap<>();for(String MCdeclaration:MCe.getAttribute("style").split(";")){int MCcolon=MCdeclaration.indexOf(':');if(MCcolon>0)MCcss.put(MCdeclaration.substring(0,MCcolon).trim().toLowerCase(Locale.ROOT),MCdeclaration.substring(MCcolon+1).trim());}
        String MCtag=MCe.getTagName().toLowerCase(Locale.ROOT),MCfont=MCbase.MCfont(),MCcolor=MCbase.MCcolor(),MChighlight=MCbase.MChighlight();double MCsize=MCbase.MCsize();
        boolean MCbold=MCbase.MCbold()||Set.of("b","strong").contains(MCtag),MCitalic=MCbase.MCitalic()||Set.of("i","em").contains(MCtag),MCunderline=MCbase.MCunderline()||MCtag.equals("u");
        String MCvalue=MCcss.get("font-family");if(MCvalue!=null&&!MCvalue.equalsIgnoreCase("inherit"))MCfont=MCvalue.split(",")[0].replace("'","").replace("\"","").trim();
        MCvalue=MCcss.get("font-size");if(MCvalue!=null){Matcher MCm=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(pt|px)?").matcher(MCvalue);if(MCm.matches())MCsize=Double.parseDouble(MCm.group(1))*("px".equals(MCm.group(2))?.75:1);}
        MCvalue=MCcss.get("font-weight");if(MCvalue!=null&&!MCvalue.equalsIgnoreCase("inherit"))MCbold=MCvalue.equalsIgnoreCase("bold")||MCvalue.equalsIgnoreCase("bolder")||MCvalue.matches("[6-9]00");
        MCvalue=MCcss.get("font-style");if(MCvalue!=null&&!MCvalue.equalsIgnoreCase("inherit"))MCitalic=MCvalue.equalsIgnoreCase("italic")||MCvalue.equalsIgnoreCase("oblique");
        MCvalue=MCcss.getOrDefault("text-decoration-line",MCcss.get("text-decoration"));if(MCvalue!=null&&!MCvalue.equalsIgnoreCase("inherit"))MCunderline=MCvalue.contains("underline");
        if(MCe.hasAttribute("color"))MCcolor=MCcssColor(MCe.getAttribute("color"),MCcolor);if(MCcss.containsKey("color"))MCcolor=MCcssColor(MCcss.get("color"),MCcolor);
        if(MCcss.containsKey("background-color"))MChighlight=MCcssColor(MCcss.get("background-color"),MChighlight);
        if(MCe.hasAttribute("face"))MCfont=MCe.getAttribute("face");
        return new MCStyle(MCfont,MCsize,MCbold,MCitalic,MCunderline,MCcolor,MChighlight);
    }
    private static String MCcssColor(String MCvalue,String MCfallback){
        MCvalue=MCvalue.trim().toLowerCase(Locale.ROOT);if(MCvalue.matches("#[0-9a-f]{6}"))return MCvalue.substring(1).toUpperCase(Locale.ROOT);
        if(MCvalue.matches("#[0-9a-f]{3}"))return (""+MCvalue.charAt(1)+MCvalue.charAt(1)+MCvalue.charAt(2)+MCvalue.charAt(2)+MCvalue.charAt(3)+MCvalue.charAt(3)).toUpperCase(Locale.ROOT);
        Matcher MCrgb=Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([0-9.]+))?\\s*\\)").matcher(MCvalue);
        if(MCrgb.matches()){if(MCrgb.group(4)!=null&&Double.parseDouble(MCrgb.group(4))==0)return null;return String.format("%02X%02X%02X",Math.min(255,Integer.parseInt(MCrgb.group(1))),Math.min(255,Integer.parseInt(MCrgb.group(2))),Math.min(255,Integer.parseInt(MCrgb.group(3))));}
        if(MCvalue.equals("transparent")||MCvalue.equals("none"))return null;
        return switch(MCvalue){case "black"->"000000";case "white"->"FFFFFF";case "red"->"FF0000";case "yellow"->"FFFF00";case "blue"->"0000FF";case "green"->"008000";default->MCfallback;};
    }
    private static void MCstyle(XWPFRun MCr,MCStyle MCs){MCr.setFontFamily(MCs.MCfont());MCr.setFontSize(MCs.MCsize());MCr.setBold(MCs.MCbold());MCr.setItalic(MCs.MCitalic());if(MCs.MCunderline())MCr.setUnderline(UnderlinePatterns.SINGLE);if(MCs.MCcolor()!=null)MCr.setColor(MCs.MCcolor());if(MCs.MChighlight()!=null){var MCprops=MCr.getCTR().isSetRPr()?MCr.getCTR().getRPr():MCr.getCTR().addNewRPr();MCprops.addNewShd().setFill(MCs.MChighlight());}}
}
