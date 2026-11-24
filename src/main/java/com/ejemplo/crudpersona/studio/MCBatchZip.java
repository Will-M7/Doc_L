package com.ejemplo.crudpersona.studio;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class MCBatchZip {
    enum MCFormat { DOCX, PDF, BOTH }
    record MCDocument(String MCname,byte[] MCdocx) {}
    static List<byte[]> MCwrite(Path MCtarget,List<MCDocument> MCdocuments,MCFormat MCformat)throws Exception {
        if(MCdocuments.isEmpty())throw new IllegalArgumentException("El lote no contiene documentos.");
        // Build beside the target; failed PDF conversion cannot leave a partial archive.
        Path MCtemporary=Files.createTempFile(MCtarget.toAbsolutePath().getParent(),"lote-",".zip");
        List<byte[]> MCpdfs=new ArrayList<>();
        try {
            try(ZipOutputStream MCzip=new ZipOutputStream(Files.newOutputStream(MCtemporary))) {
                for(int MCi=0;MCi<MCdocuments.size();MCi++) {
                    MCDocument MCdocument=MCdocuments.get(MCi);String MCname=MCdocument.MCname().replaceAll("[^\\p{L}\\p{N} _-]","_").trim();
                    if(MCname.isBlank())MCname="Cliente";
                    String MCbase=String.format(Locale.ROOT,"%02d_%s",MCi+1,MCname);
                    if(MCformat!=MCFormat.PDF)MCentry(MCzip,MCbase+".docx",MCdocument.MCdocx());
                    if(MCformat!=MCFormat.DOCX){byte[] MCpdf=MCDocxPdfExporter.MCrender(MCdocument.MCdocx());MCentry(MCzip,MCbase+".pdf",MCpdf);MCpdfs.add(MCpdf);}
                }
            }
            Files.move(MCtemporary,MCtarget,StandardCopyOption.REPLACE_EXISTING);return MCpdfs;
        } finally {Files.deleteIfExists(MCtemporary);}
    }
    private static void MCentry(ZipOutputStream MCzip,String MCname,byte[] MCbytes)throws IOException {
        MCzip.putNextEntry(new ZipEntry(MCname));MCzip.write(MCbytes);MCzip.closeEntry();
    }
}
