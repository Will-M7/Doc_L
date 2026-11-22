package com.ejemplo.crudpersona.studio;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.xwpf.usermodel.*;

import java.io.*;

/** @author MC */
public final class MCDocxPdfExporter {
    private MCDocxPdfExporter(){}
    public static byte[] mcRender(byte[] mcDocx)throws Exception{
        try(XWPFDocument mcSource=MCDocumentEngine.mcOpen(mcDocx);ByteArrayOutputStream mcOutput=new ByteArrayOutputStream()){
            Rectangle mcPage=PageSize.A4;float mcLeft=54,mcRight=54,mcTop=54,mcBottom=54;
            var mcSection=mcSource.getDocument().getBody().getSectPr();
            if(mcSection!=null){
                if(mcSection.getPgSz()!=null){float mcWidth=mcTwips(mcSection.getPgSz().getW()),mcHeight=mcTwips(mcSection.getPgSz().getH());if(mcWidth>100&&mcHeight>100)mcPage=new Rectangle(mcWidth,mcHeight);}
                if(mcSection.getPgMar()!=null){var mcM=mcSection.getPgMar();if(mcM.getLeft()!=null)mcLeft=mcTwips(mcM.getLeft());if(mcM.getRight()!=null)mcRight=mcTwips(mcM.getRight());if(mcM.getTop()!=null)mcTop=mcTwips(mcM.getTop());if(mcM.getBottom()!=null)mcBottom=mcTwips(mcM.getBottom());}
            }
            com.lowagie.text.Document mcPdf=new com.lowagie.text.Document(mcPage,mcLeft,mcRight,mcTop,mcBottom);PdfWriter.getInstance(mcPdf,mcOutput);mcPdf.open();
            for(XWPFHeader mcHeader:mcSource.getHeaderList())for(XWPFParagraph mcP:mcHeader.getParagraphs())mcPdf.add(mcParagraph(mcP));
            for(IBodyElement mcElement:mcSource.getBodyElements()){
                if(mcElement instanceof XWPFParagraph mcP){if(mcP.isPageBreak())mcPdf.newPage();mcPdf.add(mcParagraph(mcP));for(XWPFRun mcRun:mcP.getRuns())for(XWPFPicture mcPicture:mcRun.getEmbeddedPictures())mcAddImage(mcPdf,mcPicture);}
                else if(mcElement instanceof XWPFTable mcTable)mcPdf.add(mcTable(mcTable));
            }
            for(XWPFFooter mcFooter:mcSource.getFooterList())for(XWPFParagraph mcP:mcFooter.getParagraphs())mcPdf.add(mcParagraph(mcP));
            mcPdf.close();
            byte[] mcResult=mcOutput.toByteArray();
            if(mcResult.length<100||!new String(mcResult,0,Math.min(5,mcResult.length),java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"))throw new IOException("No se generó un PDF válido.");
            return mcResult;
        }
    }
    private static float mcTwips(Object mcValue){return Float.parseFloat(mcValue.toString())/20f;}
    private static Paragraph mcParagraph(XWPFParagraph mcP){Paragraph mcOut=new Paragraph();mcOut.setAlignment(switch(mcP.getAlignment()){case CENTER->Element.ALIGN_CENTER;case RIGHT->Element.ALIGN_RIGHT;case BOTH,DISTRIBUTE->Element.ALIGN_JUSTIFIED;default->Element.ALIGN_LEFT;});mcOut.setSpacingBefore(Math.max(0,mcP.getSpacingBefore()/20f));mcOut.setSpacingAfter(Math.max(0,mcP.getSpacingAfter()/20f));mcOut.setIndentationLeft(Math.max(0,mcP.getIndentationLeft()/20f));mcOut.setIndentationRight(Math.max(0,mcP.getIndentationRight()/20f));mcOut.setFirstLineIndent(mcP.getIndentationFirstLine()/20f);
        for(XWPFRun mcRun:mcP.getRuns()){String mcText=mcRun.text();if(mcText==null||mcText.isEmpty())continue;int mcStyle=(mcRun.isBold()?Font.BOLD:0)|(mcRun.isItalic()?Font.ITALIC:0);float mcSize=mcRun.getFontSize()>0?mcRun.getFontSize():11;Font mcFont=FontFactory.getFont(FontFactory.HELVETICA,"Cp1252",false,mcSize,mcStyle);mcOut.add(new Chunk(mcText,mcFont));}return mcOut;}
    private static PdfPTable mcTable(XWPFTable mcSource)throws DocumentException{
        int mcColumns=mcSource.getRows().stream().mapToInt(mcRow->mcRow.getTableCells().size()).max().orElse(1);PdfPTable mcOut=new PdfPTable(mcColumns);mcOut.setWidthPercentage(100);mcOut.setSpacingBefore(5);mcOut.setSpacingAfter(5);
        for(XWPFTableRow mcRow:mcSource.getRows()){int mcCount=0;for(XWPFTableCell mcCell:mcRow.getTableCells()){PdfPCell mcTarget=new PdfPCell();mcTarget.setPadding(5);for(XWPFParagraph mcP:mcCell.getParagraphs())mcTarget.addElement(mcParagraph(mcP));for(XWPFTable mcNested:mcCell.getTables())mcTarget.addElement(mcTable(mcNested));mcOut.addCell(mcTarget);mcCount++;}while(mcCount++<mcColumns)mcOut.addCell(new PdfPCell());}return mcOut;
    }
    private static void mcAddImage(com.lowagie.text.Document mcPdf,XWPFPicture mcPicture)throws Exception{Image mcImage=Image.getInstance(mcPicture.getPictureData().getData());mcImage.scaleToFit(mcPdf.right()-mcPdf.left(),mcPdf.top()-mcPdf.bottom());mcPdf.add(mcImage);}
}
