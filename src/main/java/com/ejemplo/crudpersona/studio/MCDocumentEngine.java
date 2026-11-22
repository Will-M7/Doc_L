package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** @author MC */
public final class MCDocumentEngine {
    public static String mcPersonaKey(String mcKey){int mcDot=mcKey.indexOf('.');return mcDot<0?"":mcKey.substring(0,mcDot);}
    public static String mcBaseKey(String mcKey){int mcDot=mcKey.indexOf('.');return mcDot<0?mcKey:mcKey.substring(mcDot+1);}
    public static String mcQualify(String mcPersona,String mcKey){return mcPersona==null||mcPersona.isBlank()?mcBaseKey(mcKey):mcPersona+"."+mcBaseKey(mcKey);}
    public static boolean mcValidKey(String mcKey){return mcKey!=null&&mcKey.matches("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?");}
    private static final Pattern mcTOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");

    private MCDocumentEngine() {}

    public static XWPFDocument mcOpen(byte[] mcBytes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(mcBytes));
    }

    public static byte[] mcBytes(XWPFDocument mcDocument) throws IOException {
        try (ByteArrayOutputStream mcOut = new ByteArrayOutputStream()) {
            mcDocument.write(mcOut);
            return mcOut.toByteArray();
        }
    }

    public static Set<String> mcMarkers(XWPFDocument mcDocument) {
        Set<String> mcNames = new LinkedHashSet<>();
        mcVisit(mcDocument, mcP -> {
            Matcher mcMatcher = mcTOKEN.matcher(mcP.getText());
            while (mcMatcher.find()) mcNames.add(mcMatcher.group(1));
        });
        return mcNames;
    }

    public static byte[] mcGenerate(byte[] mcTemplate, Map<String, String> mcValues) throws IOException {
        try (XWPFDocument mcDocument = mcOpen(mcTemplate)) {
            mcVisit(mcDocument, mcParagraph -> mcReplace(mcParagraph, mcValues));
            return mcBytes(mcDocument);
        }
    }

    private static void mcReplace(XWPFParagraph mcParagraph, Map<String, String> mcValues) {
        /** @author MC */
        record MCSegment(@com.fasterxml.jackson.annotation.JsonProperty("text") org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText mcText,@com.fasterxml.jackson.annotation.JsonProperty("start") int mcStart){}
        List<MCSegment> mcSegments=new ArrayList<>();StringBuilder mcJoined=new StringBuilder();
        for(XWPFRun mcRun:mcParagraph.getRuns())try(var mcCursor=mcRun.getCTR().newCursor()){
            if(mcCursor.toFirstChild())do{
                if(mcCursor.getObject() instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText mcText){mcSegments.add(new MCSegment(mcText,mcJoined.length()));mcJoined.append(mcText.getStringValue());}
                else if(mcCursor.getName()!=null&&Set.of("tab","br","cr").contains(mcCursor.getName().getLocalPart()))mcJoined.append('\n');
            }while(mcCursor.toNextSibling());
        }
        Matcher mcMatcher=mcTOKEN.matcher(mcJoined);List<int[]> mcMatches=new ArrayList<>();while(mcMatcher.find())mcMatches.add(new int[]{mcMatcher.start(),mcMatcher.end()});
        for(int mcM=mcMatches.size()-1;mcM>=0;mcM--){
            int mcFrom=mcMatches.get(mcM)[0],mcTo=mcMatches.get(mcM)[1];String mcKey=mcJoined.substring(mcFrom+2,mcTo-2),mcValue=mcValues.get(mcKey);if(mcValue==null)mcValue="";
            int mcFirst=-1,mcLast=-1;for(int mcI=0;mcI<mcSegments.size();mcI++){MCSegment mcSegment=mcSegments.get(mcI);int mcEnd=mcSegment.mcStart()+mcSegment.mcText().getStringValue().length();if(mcSegment.mcStart()<=mcFrom&&mcEnd>mcFrom)mcFirst=mcI;if(mcSegment.mcStart()<mcTo&&mcEnd>=mcTo)mcLast=mcI;}
            if(mcFirst<0||mcLast<0)continue;
            MCSegment mcA=mcSegments.get(mcFirst),mcB=mcSegments.get(mcLast);String mcLeft=mcA.mcText().getStringValue(),mcRight=mcB.mcText().getStringValue();int mcLocalFrom=mcFrom-mcA.mcStart(),mcLocalTo=mcTo-mcB.mcStart();
            if(mcFirst==mcLast)mcSetText(mcA.mcText(),mcLeft.substring(0,mcLocalFrom)+mcValue+mcLeft.substring(mcLocalTo));
            else{mcSetText(mcA.mcText(),mcLeft.substring(0,mcLocalFrom)+mcValue);for(int mcI=mcFirst+1;mcI<mcLast;mcI++)mcSetText(mcSegments.get(mcI).mcText(),"");mcSetText(mcB.mcText(),mcRight.substring(mcLocalTo));}
        }
    }
    private static void mcSetText(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText mcText,String mcValue){
        mcText.setStringValue(mcValue);mcText.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
    }

    public static void mcVisit(XWPFDocument mcDocument, java.util.function.Consumer<XWPFParagraph> mcAction) {
        mcVisitBody(mcDocument.getBodyElements(), mcAction);
        for (XWPFHeader mcHeader : mcDocument.getHeaderList()) mcVisitBody(mcHeader.getBodyElements(), mcAction);
        for (XWPFFooter mcFooter : mcDocument.getFooterList()) mcVisitBody(mcFooter.getBodyElements(), mcAction);
    }

    private static void mcVisitBody(List<IBodyElement> mcElements, java.util.function.Consumer<XWPFParagraph> mcAction) {
        for (IBodyElement mcElement : mcElements) {
            if (mcElement instanceof XWPFParagraph mcP) mcAction.accept(mcP);
            else if (mcElement instanceof XWPFTable mcTable) {
                for (XWPFTableRow mcRow : mcTable.getRows())
                    for (XWPFTableCell mcCell : mcRow.getTableCells()) mcVisitBody(mcCell.getBodyElements(), mcAction);
            }
        }
    }
}
