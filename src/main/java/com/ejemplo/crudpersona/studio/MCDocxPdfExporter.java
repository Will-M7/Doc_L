package com.ejemplo.crudpersona.studio;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.xwpf.usermodel.*;

import java.io.*;

public final class MCDocxPdfExporter {
    private MCDocxPdfExporter(){}
    public static byte[] MCrender(byte[] MCdocx)throws Exception{
        try(XWPFDocument MCsource=MCDocumentEngine.MCopen(MCdocx);ByteArrayOutputStream MCoutput=new ByteArrayOutputStream()){
            Rectangle MCpage=PageSize.A4;float MCleft=54,MCright=54,MCtop=54,MCbottom=54;
            var MCsection=MCsource.getDocument().getBody().getSectPr();
            if(MCsection!=null){
                if(MCsection.getPgSz()!=null){float MCwidth=MCtwips(MCsection.getPgSz().getW()),MCheight=MCtwips(MCsection.getPgSz().getH());if(MCwidth>100&&MCheight>100)MCpage=new Rectangle(MCwidth,MCheight);}
                if(MCsection.getPgMar()!=null){var MCm=MCsection.getPgMar();if(MCm.getLeft()!=null)MCleft=MCtwips(MCm.getLeft());if(MCm.getRight()!=null)MCright=MCtwips(MCm.getRight());if(MCm.getTop()!=null)MCtop=MCtwips(MCm.getTop());if(MCm.getBottom()!=null)MCbottom=MCtwips(MCm.getBottom());}
            }
            com.lowagie.text.Document MCpdf=new com.lowagie.text.Document(MCpage,MCleft,MCright,MCtop,MCbottom);PdfWriter.getInstance(MCpdf,MCoutput);MCpdf.open();
            for(XWPFHeader MCheader:MCsource.getHeaderList())for(XWPFParagraph MCp:MCheader.getParagraphs())MCpdf.add(MCparagraph(MCp));
            for(IBodyElement MCelement:MCsource.getBodyElements()){
                if(MCelement instanceof XWPFParagraph MCp){if(MCp.isPageBreak())MCpdf.newPage();MCpdf.add(MCparagraph(MCp));for(XWPFRun MCrun:MCp.getRuns())for(XWPFPicture MCpicture:MCrun.getEmbeddedPictures())MCaddImage(MCpdf,MCpicture);}
                else if(MCelement instanceof XWPFTable MCtable)MCpdf.add(MCtable(MCtable));
            }
            for(XWPFFooter MCfooter:MCsource.getFooterList())for(XWPFParagraph MCp:MCfooter.getParagraphs())MCpdf.add(MCparagraph(MCp));
            MCpdf.close();
            byte[] MCresult=MCoutput.toByteArray();
            if(MCresult.length<100||!new String(MCresult,0,Math.min(5,MCresult.length),java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"))throw new IOException("No se generó un PDF válido.");
            return MCresult;
        }
    }
    private static float MCtwips(Object MCvalue){return Float.parseFloat(MCvalue.toString())/20f;}
    private static Paragraph MCparagraph(XWPFParagraph MCp){Paragraph MCout=new Paragraph();MCout.setAlignment(switch(MCp.getAlignment()){case CENTER->Element.ALIGN_CENTER;case RIGHT->Element.ALIGN_RIGHT;case BOTH,DISTRIBUTE->Element.ALIGN_JUSTIFIED;default->Element.ALIGN_LEFT;});MCout.setSpacingBefore(Math.max(0,MCp.getSpacingBefore()/20f));MCout.setSpacingAfter(Math.max(0,MCp.getSpacingAfter()/20f));MCout.setIndentationLeft(Math.max(0,MCp.getIndentationLeft()/20f));MCout.setIndentationRight(Math.max(0,MCp.getIndentationRight()/20f));MCout.setFirstLineIndent(MCp.getIndentationFirstLine()/20f);
        for(XWPFRun MCrun:MCp.getRuns()){String MCtext=MCrun.text();if(MCtext==null||MCtext.isEmpty())continue;int MCstyle=(MCrun.isBold()?Font.BOLD:0)|(MCrun.isItalic()?Font.ITALIC:0);float MCsize=MCrun.getFontSize()>0?MCrun.getFontSize():11;Font MCfont=FontFactory.getFont(FontFactory.HELVETICA,"Cp1252",false,MCsize,MCstyle);MCout.add(new Chunk(MCtext,MCfont));}return MCout;}
    private static PdfPTable MCtable(XWPFTable MCsource)throws DocumentException{
        int MCcolumns=MCsource.getRows().stream().mapToInt(MCrow->MCrow.getTableCells().size()).max().orElse(1);PdfPTable MCout=new PdfPTable(MCcolumns);MCout.setWidthPercentage(100);MCout.setSpacingBefore(5);MCout.setSpacingAfter(5);
        for(XWPFTableRow MCrow:MCsource.getRows()){int MCcount=0;for(XWPFTableCell MCcell:MCrow.getTableCells()){PdfPCell MCtarget=new PdfPCell();MCtarget.setPadding(5);for(XWPFParagraph MCp:MCcell.getParagraphs())MCtarget.addElement(MCparagraph(MCp));for(XWPFTable MCnested:MCcell.getTables())MCtarget.addElement(MCtable(MCnested));MCout.addCell(MCtarget);MCcount++;}while(MCcount++<MCcolumns)MCout.addCell(new PdfPCell());}return MCout;
    }
    private static void MCaddImage(com.lowagie.text.Document MCpdf,XWPFPicture MCpicture)throws Exception{Image MCimage=Image.getInstance(MCpicture.getPictureData().getData());MCimage.scaleToFit(MCpdf.right()-MCpdf.left(),MCpdf.top()-MCpdf.bottom());MCpdf.add(MCimage);}
}
