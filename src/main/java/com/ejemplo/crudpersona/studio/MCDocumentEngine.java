package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MCDocumentEngine {
    public static String MCpersonaKey(String MCkey){int MCdot=MCkey.indexOf('.');return MCdot<0?"":MCkey.substring(0,MCdot);}
    public static String MCbaseKey(String MCkey){int MCdot=MCkey.indexOf('.');return MCdot<0?MCkey:MCkey.substring(MCdot+1);}
    public static String MCqualify(String MCpersona,String MCkey){return MCpersona==null||MCpersona.isBlank()?MCbaseKey(MCkey):MCpersona+"."+MCbaseKey(MCkey);}
    public static boolean MCvalidKey(String MCkey){return MCkey!=null&&MCkey.matches("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?");}
    private static final Pattern MCTOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");

    private MCDocumentEngine() {}

    public static XWPFDocument MCopen(byte[] MCbytes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(MCbytes));
    }

    public static byte[] MCbytes(XWPFDocument MCdocument) throws IOException {
        try (ByteArrayOutputStream MCout = new ByteArrayOutputStream()) {
            MCdocument.write(MCout);
            return MCout.toByteArray();
        }
    }

    public static Set<String> MCmarkers(XWPFDocument MCdocument) {
        Set<String> MCnames = new LinkedHashSet<>();
        MCvisit(MCdocument, MCp -> {
            Matcher MCmatcher = MCTOKEN.matcher(MCp.getText());
            while (MCmatcher.find()) MCnames.add(MCmatcher.group(1));
        });
        return MCnames;
    }

    public static byte[] MCgenerate(byte[] MCtemplate, Map<String, String> MCvalues) throws IOException {
        try (XWPFDocument MCdocument = MCopen(MCtemplate)) {
            MCvisit(MCdocument, MCparagraph -> MCreplace(MCparagraph, MCvalues));
            return MCbytes(MCdocument);
        }
    }

    private static void MCreplace(XWPFParagraph MCparagraph, Map<String, String> MCvalues) {
        record MCSegment(@com.fasterxml.jackson.annotation.JsonProperty("text") org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText MCtext,@com.fasterxml.jackson.annotation.JsonProperty("start") int MCstart){}
        List<MCSegment> MCsegments=new ArrayList<>();StringBuilder MCjoined=new StringBuilder();
        for(XWPFRun MCrun:MCparagraph.getRuns())try(var MCcursor=MCrun.getCTR().newCursor()){
            if(MCcursor.toFirstChild())do{
                if(MCcursor.getObject() instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText MCtext){MCsegments.add(new MCSegment(MCtext,MCjoined.length()));MCjoined.append(MCtext.getStringValue());}
                else if(MCcursor.getName()!=null&&Set.of("tab","br","cr").contains(MCcursor.getName().getLocalPart()))MCjoined.append('\n');
            }while(MCcursor.toNextSibling());
        }
        Matcher MCmatcher=MCTOKEN.matcher(MCjoined);List<int[]> MCmatches=new ArrayList<>();while(MCmatcher.find())MCmatches.add(new int[]{MCmatcher.start(),MCmatcher.end()});
        for(int MCm=MCmatches.size()-1;MCm>=0;MCm--){
            int MCfrom=MCmatches.get(MCm)[0],MCto=MCmatches.get(MCm)[1];String MCkey=MCjoined.substring(MCfrom+2,MCto-2),MCvalue=MCvalues.get(MCkey);if(MCvalue==null)MCvalue="";
            int MCfirst=-1,MClast=-1;for(int MCi=0;MCi<MCsegments.size();MCi++){MCSegment MCsegment=MCsegments.get(MCi);int MCend=MCsegment.MCstart()+MCsegment.MCtext().getStringValue().length();if(MCsegment.MCstart()<=MCfrom&&MCend>MCfrom)MCfirst=MCi;if(MCsegment.MCstart()<MCto&&MCend>=MCto)MClast=MCi;}
            if(MCfirst<0||MClast<0)continue;
            MCSegment MCa=MCsegments.get(MCfirst),MCb=MCsegments.get(MClast);String MCleft=MCa.MCtext().getStringValue(),MCright=MCb.MCtext().getStringValue();int MClocalFrom=MCfrom-MCa.MCstart(),MClocalTo=MCto-MCb.MCstart();
            if(MCfirst==MClast)MCsetText(MCa.MCtext(),MCleft.substring(0,MClocalFrom)+MCvalue+MCleft.substring(MClocalTo));
            else{MCsetText(MCa.MCtext(),MCleft.substring(0,MClocalFrom)+MCvalue);for(int MCi=MCfirst+1;MCi<MClast;MCi++)MCsetText(MCsegments.get(MCi).MCtext(),"");MCsetText(MCb.MCtext(),MCright.substring(MClocalTo));}
        }
    }
    private static void MCsetText(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText MCtext,String MCvalue){
        MCtext.setStringValue(MCvalue);MCtext.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
    }

    public static void MCvisit(XWPFDocument MCdocument, java.util.function.Consumer<XWPFParagraph> MCaction) {
        MCvisitBody(MCdocument.getBodyElements(), MCaction);
        for (XWPFHeader MCheader : MCdocument.getHeaderList()) MCvisitBody(MCheader.getBodyElements(), MCaction);
        for (XWPFFooter MCfooter : MCdocument.getFooterList()) MCvisitBody(MCfooter.getBodyElements(), MCaction);
    }

    private static void MCvisitBody(List<IBodyElement> MCelements, java.util.function.Consumer<XWPFParagraph> MCaction) {
        for (IBodyElement MCelement : MCelements) {
            if (MCelement instanceof XWPFParagraph MCp) MCaction.accept(MCp);
            else if (MCelement instanceof XWPFTable MCtable) {
                for (XWPFTableRow MCrow : MCtable.getRows())
                    for (XWPFTableCell MCcell : MCrow.getTableCells()) MCvisitBody(MCcell.getBodyElements(), MCaction);
            }
        }
    }
}
