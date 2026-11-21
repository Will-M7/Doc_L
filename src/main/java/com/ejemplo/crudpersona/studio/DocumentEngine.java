package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps the OOXML package intact; only text runs containing markers are changed. */
public final class DocumentEngine {
    public static String personaKey(String key){int dot=key.indexOf('.');return dot<0?"":key.substring(0,dot);}
    public static String baseKey(String key){int dot=key.indexOf('.');return dot<0?key:key.substring(dot+1);}
    public static String qualify(String persona,String key){return persona==null||persona.isBlank()?baseKey(key):persona+"."+baseKey(key);}
    public static boolean validKey(String key){return key!=null&&key.matches("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?");}
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");

    private DocumentEngine() {}

    public static XWPFDocument open(byte[] bytes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    public static byte[] bytes(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
            return out.toByteArray();
        }
    }

    public static Set<String> markers(XWPFDocument document) {
        Set<String> names = new LinkedHashSet<>();
        visit(document, p -> {
            Matcher matcher = TOKEN.matcher(p.getText());
            while (matcher.find()) names.add(matcher.group(1));
        });
        return names;
    }

    public static byte[] generate(byte[] template, Map<String, String> values) throws IOException {
        try (XWPFDocument document = open(template)) {
            visit(document, paragraph -> replace(paragraph, values));
            return bytes(document);
        }
    }

    private static void replace(XWPFParagraph paragraph, Map<String, String> values) {
        record Segment(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText text,int start){}
        List<Segment> segments=new ArrayList<>();StringBuilder joined=new StringBuilder();
        for(XWPFRun run:paragraph.getRuns())try(var cursor=run.getCTR().newCursor()){
            if(cursor.toFirstChild())do{
                if(cursor.getObject() instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText text){segments.add(new Segment(text,joined.length()));joined.append(text.getStringValue());}
                else if(cursor.getName()!=null&&Set.of("tab","br","cr").contains(cursor.getName().getLocalPart()))joined.append('\n');
            }while(cursor.toNextSibling());
        }
        Matcher matcher=TOKEN.matcher(joined);List<int[]> matches=new ArrayList<>();while(matcher.find())matches.add(new int[]{matcher.start(),matcher.end()});
        for(int m=matches.size()-1;m>=0;m--){
            int from=matches.get(m)[0],to=matches.get(m)[1];String key=joined.substring(from+2,to-2),value=values.get(key);if(value==null)value="";
            int first=-1,last=-1;for(int i=0;i<segments.size();i++){Segment segment=segments.get(i);int end=segment.start()+segment.text().getStringValue().length();if(segment.start()<=from&&end>from)first=i;if(segment.start()<to&&end>=to)last=i;}
            if(first<0||last<0)continue;
            Segment a=segments.get(first),b=segments.get(last);String left=a.text().getStringValue(),right=b.text().getStringValue();int localFrom=from-a.start(),localTo=to-b.start();
            if(first==last)setText(a.text(),left.substring(0,localFrom)+value+left.substring(localTo));
            else{setText(a.text(),left.substring(0,localFrom)+value);for(int i=first+1;i<last;i++)setText(segments.get(i).text(),"");setText(b.text(),right.substring(localTo));}
        }
    }
    private static void setText(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText text,String value){
        text.setStringValue(value);text.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
    }

    public static void visit(XWPFDocument document, java.util.function.Consumer<XWPFParagraph> action) {
        visitBody(document.getBodyElements(), action);
        for (XWPFHeader header : document.getHeaderList()) visitBody(header.getBodyElements(), action);
        for (XWPFFooter footer : document.getFooterList()) visitBody(footer.getBodyElements(), action);
    }

    private static void visitBody(List<IBodyElement> elements, java.util.function.Consumer<XWPFParagraph> action) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph p) action.accept(p);
            else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows())
                    for (XWPFTableCell cell : row.getTableCells()) visitBody(cell.getBodyElements(), action);
            }
        }
    }
}
