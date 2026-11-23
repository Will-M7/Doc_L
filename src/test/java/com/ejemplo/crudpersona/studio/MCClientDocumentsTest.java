package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MCClientDocumentsTest {
    @TempDir Path MCdirectory;
    private MCContact MCclient(MCStudioData MCdata,String MCgiven,String MCfamily,String MCnumber)throws Exception{
        return MCdata.MCsaveContact(new MCContact(0,"Persona",MCgiven,MCfamily,MCnumber.isEmpty()?"":"DNI",MCnumber,"","","{}"));
    }
    private byte[] MCword(String MCtext)throws Exception{try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText(MCtext);return MCDocumentEngine.MCbytes(MCdoc);}}

    @Test void MCuploadingAndReplacingUpdatesTheSameRowAndSurvivesReopening()throws Exception{
        new MCDocumentStore(MCdirectory, false);MCStudioData MCdata=new MCStudioData(MCdirectory);MCContact MCclient=MCclient(MCdata,"Luisito","","");
        MCClientDocument MCoriginal=MCdata.MCaddClientDocument(MCclient.MCid(),"Antiguo","antiguo.docx","DOCX",MCword("Antes"));
        byte[] MCreplacement="%PDF-Nuevo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MCdata.MCreplaceClientDocument(MCoriginal.MCid(),"Nuevo","nuevo.pdf","PDF",MCreplacement,"UPLOADED");
        List<MCClientDocument> MCfound=new MCStudioData(MCdirectory).MCclientDocuments(MCclient.MCid());
        assertThat(MCfound).hasSize(1);MCClientDocument MCresult=MCfound.get(0);
        assertThat(MCresult.MCid()).isEqualTo(MCoriginal.MCid());assertThat(MCresult.MCfileName()).isEqualTo("nuevo.pdf");assertThat(MCresult.MCtitle()).isEqualTo("Nuevo");assertThat(MCresult.MCextension()).isEqualTo("pdf");assertThat(MCresult.MCcontentBytes()).isEqualTo(MCreplacement);assertThat(MCresult.MCsourceType()).isEqualTo("UPLOADED");assertThat(MCresult.MCupdatedAt()).isNotEqualTo(MCoriginal.MCupdatedAt());
        MCdata.MCdeleteContact(MCclient.MCid());assertThat(MCdata.MCclientDocuments(MCclient.MCid())).isEmpty();
        try(var MCconnection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+MCdata.MCdatabase());var MCs=MCconnection.createStatement();var MCr=MCs.executeQuery("SELECT COUNT(*) FROM client_document")){assertThat(MCr.next()).isTrue();assertThat(MCr.getInt(1)).isZero();}
    }

    @Test void MCgeneratedHistoryIsMatchedAcrossPeopleAndNeverRestoresAReplacedFile()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCdirectory, false);MCStudioData MCdata=new MCStudioData(MCdirectory);
        MCContact MCana=MCclient(MCdata,"Ana","Pérez","12345678"),MCluis=MCclient(MCdata,"Luisito","",""),MCunrelated=MCclient(MCdata,"Otra persona","","");
        byte[] MCsource=MCword("Carta original");MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Carta",MCsource,MCsource));
        MCHistory MChistory=MCdata.MCsaveHistory(new MCHistory(0,"Carta",MCtemplate.MCid(),1,null,"{\"Persona_1.DNI\":\"12345678\",\"Persona_1.nombres\":\"Ana\",\"Persona_1.apellidos\":\"Pérez\",\"Persona_2.Dirigido\":\"Luisito\"}",null,null,MCsource,"%PDF-original".getBytes()));
        try(var MCconnection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+MCdata.MCdatabase());var MCs=MCconnection.createStatement();var MCr=MCs.executeQuery("SELECT COUNT(*) FROM client_document")){assertThat(MCr.next()).isTrue();assertThat(MCr.getInt(1)).isEqualTo(4);}
        assertThat(MCdata.MCclientDocuments(MCana.MCid())).hasSize(2);assertThat(MCdata.MCclientDocuments(MCluis.MCid())).hasSize(2);assertThat(MCdata.MCclientDocuments(MCunrelated.MCid())).isEmpty();
        MCClientDocument MCoriginal=MCdata.MCclientDocuments(MCana.MCid()).stream().filter(MCd->MCd.MCextension().equals("docx")).findFirst().orElseThrow();
        byte[] MCnewer=MCword("Carta actualizada");MCdata.MCreplaceClientDocument(MCoriginal.MCid(),"Carta actualizada","carta.docx","docx",MCnewer,"GENERATED");
        for(int MCi=0;MCi<3;MCi++){List<MCClientDocument> MCfound=MCdata.MCclientDocuments(MCana.MCid());assertThat(MCfound).hasSize(2);assertThat(MCfound.stream().filter(MCd->MCd.MCid()==MCoriginal.MCid()).findFirst().orElseThrow().MCcontentBytes()).isEqualTo(MCnewer);}
        assertThat(MCdata.MChistory().get(0).MCdocx()).isEqualTo(MCsource);
        MCClientDocument MCpdf=MCdata.MCclientDocuments(MCana.MCid()).stream().filter(MCd->MCd.MCextension().equals("pdf")).findFirst().orElseThrow();
        MCdata.MCreplaceClientDocument(MCpdf.MCid(),"Otro archivo","nuevo.txt","txt",new byte[]{1},"UPLOADED");
        assertThat(MCdata.MCclientDocuments(MCana.MCid())).hasSize(2).extracting(MCClientDocument::MCextension).containsExactlyInAnyOrder("txt","docx");
        MCdata.MCdeleteHistory(MChistory.MCid());assertThat(MCdata.MCclientDocuments(MCana.MCid())).hasSize(2);
    }

    @Test void MCexplicitContactIdentityAndIndependentFieldsAreKeptSeparate()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCdirectory, false);MCStudioData MCdata=new MCStudioData(MCdirectory);MCContact MCclient=MCclient(MCdata,"Luisito","","");
        byte[] MCbytes=MCword("Sin variables");MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Carta",MCbytes,MCbytes));
        MCDefinition MCindependent=MCstore.MCsaveDefinition(new MCDefinition(0,"Dirigido","Global","Texto corto",""));
        MCstore.MCsaveDefinition(new MCDefinition(MCindependent.MCid(),MCindependent.MCkey(),MCindependent.MClabel(),MCindependent.MCtype(),MCindependent.MCdefaultValue(),false,"dd/MM/yyyy",false));
        MCdata.MCsaveHistory(new MCHistory(0,"Global",MCtemplate.MCid(),1,null,"{\"Dirigido\":\"Luisito\"}",null,null,MCbytes,null));
        assertThat(MCdata.MCclientDocuments(MCclient.MCid())).isEmpty();
        MCHistory MCdirect=MCdata.MCsaveHistory(new MCHistory(0,"Asociación directa",MCtemplate.MCid(),1,null,"{\"Persona.contact_id\":\""+MCclient.MCid()+"\"}",null,null,MCbytes,null));
        assertThat(MCdata.MCclientDocuments(MCclient.MCid())).hasSize(1);
        MCdata.MClinkHistoryToContacts(MCdirect.MCid(),List.of(MCclient.MCid(),MCclient.MCid()));assertThat(MCdata.MCclientDocuments(MCclient.MCid())).hasSize(1);
    }
}
