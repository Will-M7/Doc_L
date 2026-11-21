package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ClientDocumentsTest {
    @TempDir Path directory;
    private Contact client(StudioData data,String given,String family,String number)throws Exception{
        return data.saveContact(new Contact(0,"Persona",given,family,number.isEmpty()?"":"DNI",number,"","","{}"));
    }
    private byte[] word(String text)throws Exception{try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText(text);return DocumentEngine.bytes(doc);}}

    @Test void uploadingAndReplacingUpdatesTheSameRowAndSurvivesReopening()throws Exception{
        new DocumentStore(directory);StudioData data=new StudioData(directory);Contact client=client(data,"Luisito","","");
        ClientDocument original=data.addClientDocument(client.id(),"Antiguo","antiguo.docx","DOCX",word("Antes"));
        byte[] replacement="%PDF-Nuevo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        data.replaceClientDocument(original.id(),"Nuevo","nuevo.pdf","PDF",replacement,"UPLOADED");
        List<ClientDocument> found=new StudioData(directory).clientDocuments(client.id());
        assertThat(found).hasSize(1);ClientDocument result=found.get(0);
        assertThat(result.id()).isEqualTo(original.id());assertThat(result.fileName()).isEqualTo("nuevo.pdf");assertThat(result.title()).isEqualTo("Nuevo");assertThat(result.extension()).isEqualTo("pdf");assertThat(result.contentBytes()).isEqualTo(replacement);assertThat(result.sourceType()).isEqualTo("UPLOADED");assertThat(result.updatedAt()).isNotEqualTo(original.updatedAt());
        data.deleteContact(client.id());assertThat(data.clientDocuments(client.id())).isEmpty();
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+data.database());var s=connection.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM client_document")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isZero();}
    }

    @Test void generatedHistoryIsMatchedAcrossPeopleAndNeverRestoresAReplacedFile()throws Exception{
        DocumentStore store=new DocumentStore(directory);StudioData data=new StudioData(directory);
        Contact ana=client(data,"Ana","Pérez","12345678"),luis=client(data,"Luisito","",""),unrelated=client(data,"Otra persona","","");
        byte[] source=word("Carta original");Template template=store.saveTemplate(new Template(0,"Carta",source,source));
        History history=data.saveHistory(new History(0,"Carta",template.id(),1,null,"{\"Persona_1.DNI\":\"12345678\",\"Persona_1.nombres\":\"Ana\",\"Persona_1.apellidos\":\"Pérez\",\"Persona_2.Dirigido\":\"Luisito\"}",null,null,source,"%PDF-original".getBytes()));
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+data.database());var s=connection.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM client_document")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(4);}
        assertThat(data.clientDocuments(ana.id())).hasSize(2);assertThat(data.clientDocuments(luis.id())).hasSize(2);assertThat(data.clientDocuments(unrelated.id())).isEmpty();
        ClientDocument original=data.clientDocuments(ana.id()).stream().filter(d->d.extension().equals("docx")).findFirst().orElseThrow();
        byte[] newer=word("Carta actualizada");data.replaceClientDocument(original.id(),"Carta actualizada","carta.docx","docx",newer,"GENERATED");
        for(int i=0;i<3;i++){List<ClientDocument> found=data.clientDocuments(ana.id());assertThat(found).hasSize(2);assertThat(found.stream().filter(d->d.id()==original.id()).findFirst().orElseThrow().contentBytes()).isEqualTo(newer);}
        assertThat(data.history().get(0).docx()).isEqualTo(source);
        ClientDocument pdf=data.clientDocuments(ana.id()).stream().filter(d->d.extension().equals("pdf")).findFirst().orElseThrow();
        data.replaceClientDocument(pdf.id(),"Otro archivo","nuevo.txt","txt",new byte[]{1},"UPLOADED");
        assertThat(data.clientDocuments(ana.id())).hasSize(2).extracting(ClientDocument::extension).containsExactlyInAnyOrder("txt","docx");
        data.deleteHistory(history.id());assertThat(data.clientDocuments(ana.id())).hasSize(2);
    }

    @Test void explicitContactIdentityAndIndependentFieldsAreKeptSeparate()throws Exception{
        DocumentStore store=new DocumentStore(directory);StudioData data=new StudioData(directory);Contact client=client(data,"Luisito","","");
        byte[] bytes=word("Sin variables");Template template=store.saveTemplate(new Template(0,"Carta",bytes,bytes));
        Definition independent=store.saveDefinition(new Definition(0,"Dirigido","Global","Texto corto",""));
        store.saveDefinition(new Definition(independent.id(),independent.key(),independent.label(),independent.type(),independent.defaultValue(),false,"dd/MM/yyyy",false));
        data.saveHistory(new History(0,"Global",template.id(),1,null,"{\"Dirigido\":\"Luisito\"}",null,null,bytes,null));
        assertThat(data.clientDocuments(client.id())).isEmpty();
        History direct=data.saveHistory(new History(0,"Asociación directa",template.id(),1,null,"{\"Persona.contact_id\":\""+client.id()+"\"}",null,null,bytes,null));
        assertThat(data.clientDocuments(client.id())).hasSize(1);
        data.linkHistoryToContacts(direct.id(),List.of(client.id(),client.id()));assertThat(data.clientDocuments(client.id())).hasSize(1);
    }
}
