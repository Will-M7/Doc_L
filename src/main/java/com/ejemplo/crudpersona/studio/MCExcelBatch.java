package com.ejemplo.crudpersona.studio;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Formatted, evaluated Excel values, detached from the source workbook. */
final class MCExcelBatch {
    enum MCOrientation { VERTICAL, HORIZONTAL }
    record MCSheet(String MCname, List<List<String>> MCrows, int MCcolumns) {
        String MCvalue(int MCrow,int MCcolumn) {
            return MCrow<0||MCrow>=MCrows.size()||MCcolumn<0||MCcolumn>=MCrows.get(MCrow).size()?"":MCrows.get(MCrow).get(MCcolumn);
        }
    }
    record MCRange(String MCfrom,String MCto,MCOrientation MCorientation,boolean MCreverse) {
        List<String> MCcells() {
            CellReference MCa=MCreference(MCfrom),MCb=MCreference(MCto);
            boolean MCvertical=MCorientation==MCOrientation.VERTICAL;
            if(MCvertical?MCa.getCol()!=MCb.getCol():MCa.getRow()!=MCb.getRow())
                throw new IllegalArgumentException("El rango debe ocupar una sola "+(MCvertical?"columna.":"fila."));
            int MCstart=MCvertical?MCa.getRow():MCa.getCol(),MCend=MCvertical?MCb.getRow():MCb.getCol();
            List<String> MCout=new ArrayList<>();
            for(int MCi=Math.min(MCstart,MCend);MCi<=Math.max(MCstart,MCend);MCi++)
                MCout.add(new CellReference(MCvertical?MCi:MCa.getRow(),MCvertical?MCa.getCol():MCi).formatAsString());
            if(MCreverse)Collections.reverse(MCout);return MCout;
        }
        List<String> MCvalues(MCSheet MCsheet) {
            return MCcells().stream().map(MCExcelBatch::MCreference).map(MCc->MCsheet.MCvalue(MCc.getRow(),MCc.getCol())).toList();
        }
    }
    static CellReference MCreference(String MCtext) {
        if(MCtext==null||!MCtext.trim().matches("(?i)[A-Z]{1,3}[1-9][0-9]{0,6}"))throw new IllegalArgumentException("Referencia inválida: "+MCtext+". Use A2, C15…");
        CellReference MCref=new CellReference(MCtext.trim().toUpperCase(Locale.ROOT));
        if(MCref.getCol()>16383||MCref.getRow()>1048575)throw new IllegalArgumentException("Referencia fuera del Excel: "+MCtext);
        return MCref;
    }
    static List<MCSheet> MCread(Path MCpath)throws IOException {
        try(InputStream MCin=Files.newInputStream(MCpath);Workbook MCbook=WorkbookFactory.create(MCin)) {
            DataFormatter MCformatter=new DataFormatter();FormulaEvaluator MCevaluator=MCbook.getCreationHelper().createFormulaEvaluator();
            List<MCSheet> MCout=new ArrayList<>();
            for(Sheet MCsheet:MCbook) {
                int MCcolumns=0;for(Row MCrow:MCsheet)MCcolumns=Math.max(MCcolumns,MCrow.getLastCellNum());
                List<List<String>> MCrows=new ArrayList<>();
                int MCcount=MCsheet.getPhysicalNumberOfRows()==0?0:MCsheet.getLastRowNum()+1;
                for(int MCr=0;MCr<MCcount;MCr++) {
                    Row MCrow=MCsheet.getRow(MCr);List<String> MCvalues=new ArrayList<>();
                    for(int MCc=0;MCc<MCcolumns;MCc++)MCvalues.add(MCformatter.formatCellValue(MCrow==null?null:MCrow.getCell(MCc),MCevaluator));
                    MCrows.add(List.copyOf(MCvalues));
                }
                MCout.add(new MCSheet(MCsheet.getSheetName(),List.copyOf(MCrows),MCcolumns));
            }
            return List.copyOf(MCout);
        }
    }
    static MCRange MCdetect(MCSheet MCsheet,String MCstart,MCOrientation MCorientation,boolean MCreverse) {
        CellReference MCref=MCreference(MCstart);int MCr=MCref.getRow(),MCc=MCref.getCol(),MCendR=MCr,MCendC=MCc;
        int MCstep=MCreverse?-1:1;
        for(int MCr2=MCr,MCc2=MCc;MCr2>=0&&MCc2>=0&&MCr2<MCsheet.MCrows().size()&&MCc2<MCsheet.MCcolumns();) {
            if(!MCsheet.MCvalue(MCr2,MCc2).isBlank()){MCendR=MCr2;MCendC=MCc2;}
            if(MCorientation==MCOrientation.VERTICAL)MCr2+=MCstep;else MCc2+=MCstep;
        }
        return new MCRange(MCstart,new CellReference(MCendR,MCendC).formatAsString(),MCorientation,MCreverse);
    }
    static List<String> MCmissing(Collection<String> MCrequired,Map<String,MCRange> MCranges) {
        return MCrequired.stream().filter(MCkey->!MCranges.containsKey(MCkey)).toList();
    }
    static List<Map<String,String>> MCextract(MCSheet MCsheet,Collection<String> MCrequired,Map<String,MCRange> MCranges) {
        List<String> MCmissing=MCmissing(MCrequired,MCranges);
        if(!MCmissing.isEmpty())throw new IllegalArgumentException("Falta colocar: "+String.join(", ",MCmissing));
        Map<String,List<String>> MCcolumns=new LinkedHashMap<>();int MCcount=1;
        for(String MCkey:MCrequired){List<String> MCvalues=MCranges.get(MCkey).MCvalues(MCsheet);MCcolumns.put(MCkey,MCvalues);MCcount=Math.max(MCcount,MCvalues.size());}
        for(var MCentry:MCcolumns.entrySet())if(MCentry.getValue().size()!=1&&MCentry.getValue().size()!=MCcount)
            throw new IllegalArgumentException("El rango de "+MCentry.getKey()+" contiene "+MCentry.getValue().size()+" celdas; debe contener 1 o "+MCcount+" para emparejar todos los documentos.");
        List<Map<String,String>> MCout=new ArrayList<>();
        for(int MCi=0;MCi<MCcount;MCi++){Map<String,String> MCvalues=new LinkedHashMap<>();for(var MCentry:MCcolumns.entrySet())MCvalues.put(MCentry.getKey(),MCentry.getValue().get(MCentry.getValue().size()==1?0:MCi));MCout.add(MCvalues);}
        return MCout;
    }
}
