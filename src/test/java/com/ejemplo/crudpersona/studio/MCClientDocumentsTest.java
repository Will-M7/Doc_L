package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** @author MC */
class MCClientDocumentsTest {
    @TempDir Path mcDirectory;
    private MCContact mcClient(MCStudioData mcData,String mcGiven,String mcFamily,String mcNumber)throws Exception{
        return mcData.mcSaveContact(new MCContact(0,"Persona",mcGiven,mcFamily,mcNumber.isEmpty()?"":"DNI",mcNumber,"","","{}"));
    }
    private byte[] mcWord(String mcText)throws Exception{try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText(mcText);return MCDocumentEngine.mcBytes(mcDoc);}}

    @Test void mcUploadingAndReplacingUpdatesTheSameRowAndSurvivesReopening()throws Exception{
        new MCDocumentStore(mcDirectory);MCStudioData mcData=new MCStudioData(mcDirectory);MCContact mcClient=mcClient(mcData,"Luisito","","");
        MCClientDocument mcOriginal=mcData.mcAddClientDocument(mcClient.mcId(),"Antiguo","antiguo.docx","DOCX",mcWord("Antes"));
        byte[] mcReplacement="%PDF-Nuevo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mcData.mcReplaceClientDocument(mcOriginal.mcId(),"Nuevo","nuevo.pdf","PDF",mcReplacement,"UPLOADED");
        List<MCClientDocument> mcFound=new MCStudioData(mcDirectory).mcClientDocuments(mcClient.mcId());
        assertThat(mcFound).hasSize(1);MCClientDocument mcResult=mcFound.get(0);
        assertThat(mcResult.mcId()).isEqualTo(mcOriginal.mcId());assertThat(mcResult.mcFileName()).isEqualTo("nuevo.pdf");assertThat(mcResult.mcTitle()).isEqualTo("Nuevo");assertThat(mcResult.mcExtension()).isEqualTo("pdf");assertThat(mcResult.mcContentBytes()).isEqualTo(mcReplacement);assertThat(mcResult.mcSourceType()).isEqualTo("UPLOADED");assertThat(mcResult.mcUpdatedAt()).isNotEqualTo(mcOriginal.mcUpdatedAt());
        mcData.mcDeleteContact(mcClient.mcId());assertThat(mcData.mcClientDocuments(mcClient.mcId())).isEmpty();
        try(var mcConnection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+mcData.mcDatabase());var mcS=mcConnection.createStatement();var mcR=mcS.executeQuery("SELECT COUNT(*) FROM client_document")){assertThat(mcR.next()).isTrue();assertThat(mcR.getInt(1)).isZero();}
    }

    @Test void mcGeneratedHistoryIsMatchedAcrossPeopleAndNeverRestoresAReplacedFile()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcDirectory);MCStudioData mcData=new MCStudioData(mcDirectory);
        MCContact mcAna=mcClient(mcData,"Ana","Pérez","12345678"),mcLuis=mcClient(mcData,"Luisito","",""),mcUnrelated=mcClient(mcData,"Otra persona","","");
        byte[] mcSource=mcWord("Carta original");MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Carta",mcSource,mcSource));
        MCHistory mcHistory=mcData.mcSaveHistory(new MCHistory(0,"Carta",mcTemplate.mcId(),1,null,"{\"Persona_1.DNI\":\"12345678\",\"Persona_1.nombres\":\"Ana\",\"Persona_1.apellidos\":\"Pérez\",\"Persona_2.Dirigido\":\"Luisito\"}",null,null,mcSource,"%PDF-original".getBytes()));
        try(var mcConnection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+mcData.mcDatabase());var mcS=mcConnection.createStatement();var mcR=mcS.executeQuery("SELECT COUNT(*) FROM client_document")){assertThat(mcR.next()).isTrue();assertThat(mcR.getInt(1)).isEqualTo(4);}
        assertThat(mcData.mcClientDocuments(mcAna.mcId())).hasSize(2);assertThat(mcData.mcClientDocuments(mcLuis.mcId())).hasSize(2);assertThat(mcData.mcClientDocuments(mcUnrelated.mcId())).isEmpty();
        MCClientDocument mcOriginal=mcData.mcClientDocuments(mcAna.mcId()).stream().filter(mcD->mcD.mcExtension().equals("docx")).findFirst().orElseThrow();
        byte[] mcNewer=mcWord("Carta actualizada");mcData.mcReplaceClientDocument(mcOriginal.mcId(),"Carta actualizada","carta.docx","docx",mcNewer,"GENERATED");
        for(int mcI=0;mcI<3;mcI++){List<MCClientDocument> mcFound=mcData.mcClientDocuments(mcAna.mcId());assertThat(mcFound).hasSize(2);assertThat(mcFound.stream().filter(mcD->mcD.mcId()==mcOriginal.mcId()).findFirst().orElseThrow().mcContentBytes()).isEqualTo(mcNewer);}
        assertThat(mcData.mcHistory().get(0).mcDocx()).isEqualTo(mcSource);
        MCClientDocument mcPdf=mcData.mcClientDocuments(mcAna.mcId()).stream().filter(mcD->mcD.mcExtension().equals("pdf")).findFirst().orElseThrow();
        mcData.mcReplaceClientDocument(mcPdf.mcId(),"Otro archivo","nuevo.txt","txt",new byte[]{1},"UPLOADED");
        assertThat(mcData.mcClientDocuments(mcAna.mcId())).hasSize(2).extracting(MCClientDocument::mcExtension).containsExactlyInAnyOrder("txt","docx");
        mcData.mcDeleteHistory(mcHistory.mcId());assertThat(mcData.mcClientDocuments(mcAna.mcId())).hasSize(2);
    }

    @Test void mcExplicitContactIdentityAndIndependentFieldsAreKeptSeparate()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcDirectory);MCStudioData mcData=new MCStudioData(mcDirectory);MCContact mcClient=mcClient(mcData,"Luisito","","");
        byte[] mcBytes=mcWord("Sin variables");MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Carta",mcBytes,mcBytes));
        MCDefinition mcIndependent=mcStore.mcSaveDefinition(new MCDefinition(0,"Dirigido","Global","Texto corto",""));
        mcStore.mcSaveDefinition(new MCDefinition(mcIndependent.mcId(),mcIndependent.mcKey(),mcIndependent.mcLabel(),mcIndependent.mcType(),mcIndependent.mcDefaultValue(),false,"dd/MM/yyyy",false));
        mcData.mcSaveHistory(new MCHistory(0,"Global",mcTemplate.mcId(),1,null,"{\"Dirigido\":\"Luisito\"}",null,null,mcBytes,null));
        assertThat(mcData.mcClientDocuments(mcClient.mcId())).isEmpty();
        MCHistory mcDirect=mcData.mcSaveHistory(new MCHistory(0,"Asociación directa",mcTemplate.mcId(),1,null,"{\"Persona.contact_id\":\""+mcClient.mcId()+"\"}",null,null,mcBytes,null));
        assertThat(mcData.mcClientDocuments(mcClient.mcId())).hasSize(1);
        mcData.mcLinkHistoryToContacts(mcDirect.mcId(),List.of(mcClient.mcId(),mcClient.mcId()));assertThat(mcData.mcClientDocuments(mcClient.mcId())).hasSize(1);
    }
}
