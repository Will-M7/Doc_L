package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

import static org.assertj.core.api.Assertions.*;

class MCStudioTransferTest {
    @TempDir Path MCtemp;
    private static final ObjectMapper MCJSON=new ObjectMapper();

    @Test void MCselectivePackagesContainOnlyRelatedGraphAndKeepDocxBytesIntact()throws Exception{
        MCDocumentStore MCsource=new MCDocumentStore(MCtemp.resolve("source"), false);MCStudioData MCdata=new MCStudioData(MCsource.MCdirectory());
        MCTemplate MCselected=MCtemplate(MCsource,"Seleccionada","{{dni}} {{nombre}} {{ciudad}}");MCTemplate MCother=MCtemplate(MCsource,"Otra","{{otro}}");
        MCTemplateGroup MCshared=MCsource.MCsaveGroup(0,"Compartido"),MCexclusive=MCsource.MCsaveGroup(0,"Exclusivo");MCsource.MCsetTemplateGroups(MCselected.MCid(),List.of(MCshared.MCid()));MCsource.MCsetTemplateGroups(MCother.MCid(),List.of(MCshared.MCid(),MCexclusive.MCid()));
        MCDefinition MCdni=MCsource.MCsaveDefinition(new MCDefinition(0,"dni","DNI","DNI",""));MCDefinition MCname=MCsource.MCsaveDefinition(new MCDefinition(0,"nombre","Nombre","Texto corto",""));MCDefinition MCcity=MCsource.MCsaveDefinition(new MCDefinition(0,"ciudad","Ciudad","Texto corto",""));MCDefinition MCunrelated=MCsource.MCsaveDefinition(new MCDefinition(0,"otro","Otro","Texto corto",""));
        MCsource.MCbindDefinition(MCselected.MCid(),MCdni.MCid(),true,1);MCsource.MCbindDefinition(MCselected.MCid(),MCname.MCid(),false,2);MCsource.MCbindDefinition(MCother.MCid(),MCunrelated.MCid(),false,1);
        MCdata.MCsaveApiBinding(new MCApiBinding(MCname.MCid(),"DNI","field:nombres"));MCdata.MCsaveApiBinding(new MCApiBinding(MCcity.MCid(),"DNI","field:distrito"));MCdata.MCsaveApiBinding(new MCApiBinding(MCunrelated.MCid(),"RUC","field:razon_social"));
        MCdata.MCsaveApiContext(new MCApiContext(MCselected.MCid(),"dni","cliente","DNI",true));MCdata.MCsaveApiContext(new MCApiContext(MCselected.MCid(),"nombre","cliente","DNI",false));MCdata.MCsaveContactBinding(new MCContactBinding(0,MCselected.MCid(),"cliente","Persona","given","nombre"));
        MCdata.MCsaveMapping(new MCMapping(0,MCselected.MCid(),"dni","nombres","nombre"));
        MCKind MCkind=MCsource.MCsaveKind(new MCKind(0,"Carta","Categoría","Descripción",MCselected.MCid(),"dni,nombre,ciudad",true));MCsource.MCsaveKindFieldRules(MCkind.MCid(),Map.of("dni",true),Map.of("ciudad","Lima"));MCsource.MCsaveKind(new MCKind(0,"No exportar","","",MCother.MCid(),"otro",true));
        new MCApiCredentialStore(MCsource.MCdirectory()).MCadd("Privada","Factiliza","secret-test-token");MCdata.MCsaveContact(new MCContact(0,"Persona","No exportar","","DNI","12345678","","","{}"));
        for(List<Long> MCselection:List.of(List.of(MCselected.MCid()),List.of(MCselected.MCid(),MCother.MCid()))){
            Path MCzip=MCtemp.resolve("export-"+MCselection.size()+".zip");new MCStudioTransfer(MCsource,MCdata).MCexportConfiguration(MCzip,MCselection);Map<String,byte[]> MCentries=MCread(MCzip);assertThat(MCentries.keySet()).containsExactly("configuration.json");String MCpayload=new String(MCentries.get("configuration.json"),java.nio.charset.StandardCharsets.UTF_8);assertThat(MCpayload).doesNotContain("secret-test-token","12345678");
            JsonNode MCroot=MCJSON.readTree(MCpayload);assertThat(MCroot.path("templates")).hasSize(MCselection.size());
            if(MCselection.size()==1){assertThat(MCroot.path("definitions")).hasSize(3);assertThat(MCroot.path("apiBindings")).hasSize(2);assertThat(MCroot.path("groups")).hasSize(1);assertThat(MCroot.path("groups").get(0).path("count").asInt()).isEqualTo(1);assertThat(MCroot.path("kinds")).hasSize(1);assertThat(MCpayload).doesNotContain("No exportar","Exclusivo");}
            MCDocumentStore MCtarget=new MCDocumentStore(MCtemp.resolve("target-"+MCselection.size()), false);MCStudioData MCimported=new MCStudioData(MCtarget.MCdirectory());new MCStudioTransfer(MCtarget,MCimported).MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);
            MCTemplate MCrestored=MCtarget.MCtemplates().stream().filter(MCt->MCt.MCname().equals(MCselected.MCname())).findFirst().orElseThrow();assertThat(MCrestored.MCoriginal()).containsExactly(MCselected.MCoriginal());assertThat(MCrestored.MCedited()).containsExactly(MCselected.MCedited());assertThat(MCtarget.MCvariables(MCrestored.MCid())).extracting(MCVariable::MCkey).containsExactlyInAnyOrder("dni","nombre","ciudad");assertThat(MCimported.MCapiContexts(MCrestored.MCid())).hasSize(2);assertThat(MCimported.MCcontactBindings(MCrestored.MCid())).hasSize(1);assertThat(MCimported.MCmappings(MCrestored.MCid())).hasSize(1);
            MCKind MCrestoredKind=MCtarget.MCkinds().stream().filter(MCk->MCk.MCtitle().equals("Carta")).findFirst().orElseThrow();assertThat(MCtarget.MCkindDefaults(MCrestoredKind.MCid())).containsEntry("ciudad","Lima");
            try(XWPFDocument MCgenerated=MCDocumentEngine.MCopen(MCDocumentEngine.MCgenerate(MCrestored.MCedited(),Map.of("dni","12345678","nombre","Ana","ciudad","Lima")))){assertThat(MCgenerated.getParagraphs().get(0).getText()).isEqualTo("12345678 Ana Lima");}
        }
    }

    @Test void MCclientsAndCombinedPackagesDetectSectionsAndUpsertOnlyExactIdentity()throws Exception{
        MCDocumentStore MCsource=new MCDocumentStore(MCtemp.resolve("source"), false);MCStudioData MCdata=new MCStudioData(MCsource.MCdirectory());MCtemplate(MCsource,"Carta","Contenido");
        MCdata.MCsaveContact(new MCContact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{\"telefono\":\"555\"}"));MCdata.MCsaveContact(new MCContact(0,"Empresa","ACME","","RUC","20123456789","Cusco","Representante","{\"capital\":100}"));
        Path MCconfig=MCtemp.resolve("config.zip"),MCclients=MCtemp.resolve("clients.zip");MCStudioTransfer MCtransfer=new MCStudioTransfer(MCsource,MCdata);MCtransfer.MCexportConfiguration(MCconfig);MCtransfer.MCexportClients(MCclients);JsonNode MCclientRoot=MCJSON.readTree(MCread(MCclients).get("clients.json"));assertThat(MCclientRoot.path("format").asText()).isEqualTo("document-studio-clients");assertThat(MCclientRoot.path("version").asInt()).isEqualTo(1);
        MCDocumentStore MCtarget=new MCDocumentStore(MCtemp.resolve("target"), false);MCStudioData MCimported=new MCStudioData(MCtarget.MCdirectory());MCStudioTransfer MCimporter=new MCStudioTransfer(MCtarget,MCimported);
        MCimported.MCsaveContact(new MCContact(0,"Persona","Otro tipo","","CE","12345678","","","{}"));
        assertThat(MCimporter.MCimportConfiguration(MCclients,MCStudioTransfer.MCConflict.KEEP).MCclients()).isEqualTo(2);assertThat(MCimported.MCcontacts()).hasSize(3);assertThat(MCimporter.MCimportConfiguration(MCclients,MCStudioTransfer.MCConflict.KEEP).MCclients()).isZero();assertThat(MCimported.MCcontacts()).hasSize(3);
        MCContact MCoriginal=MCimported.MCmatchingContact("Persona","DNI","12345678");MCimported.MCsaveContact(new MCContact(MCoriginal.MCid(),MCoriginal.MCkind(),"Local",MCoriginal.MCfamily(),MCoriginal.MCdocumentType(),MCoriginal.MCnumber(),"Arequipa","","{\"local\":true}"));
        MCimporter.MCimportConfiguration(MCclients,MCStudioTransfer.MCConflict.KEEP);assertThat(MCimported.MCmatchingContact("Persona","DNI","12345678").MCgiven()).isEqualTo("Local");
        Map<String,byte[]> MCcombined=new LinkedHashMap<>(MCread(MCconfig));MCcombined.putAll(MCread(MCclients));Path MCzip=MCtemp.resolve("combined.zip");MCwrite(MCzip,MCcombined);MCStudioTransfer.MCImportResult MCresult=MCimporter.MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.REPLACE);assertThat(MCresult.MCtemplates()).isEqualTo(1);assertThat(MCresult.MCclients()).isEqualTo(2);assertThat(MCimported.MCcontacts()).hasSize(3);assertThat(MCimported.MCmatchingContact("Persona","DNI","12345678").MCextrasJson()).contains("555");assertThat(MCimported.MCmatchingContact("Persona","CE","12345678").MCgiven()).isEqualTo("Otro tipo");assertThat(MCimported.MCmatchingContact("Empresa","RUC","20123456789").MCrepresentatives()).isEqualTo("Representante");
    }

    @Test void MCinvalidClientVersionInCombinedPackageIsRejectedBeforeWritingTemplates()throws Exception{
        MCDocumentStore MCsource=new MCDocumentStore(MCtemp.resolve("source"), false);MCStudioData MCdata=new MCStudioData(MCsource.MCdirectory());MCtemplate(MCsource,"Carta","Texto");Path MCconfig=MCtemp.resolve("config.zip");new MCStudioTransfer(MCsource,MCdata).MCexportConfiguration(MCconfig);Map<String,byte[]> MCentries=new LinkedHashMap<>(MCread(MCconfig));MCentries.put("clients.json","{\"format\":\"document-studio-clients\",\"version\":2,\"clients\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));Path MCinvalid=MCtemp.resolve("invalid.zip");MCwrite(MCinvalid,MCentries);
        MCDocumentStore MCtarget=new MCDocumentStore(MCtemp.resolve("target"), false);MCStudioData MCimported=new MCStudioData(MCtarget.MCdirectory());assertThatThrownBy(()->new MCStudioTransfer(MCtarget,MCimported).MCimportConfiguration(MCinvalid,MCStudioTransfer.MCConflict.REPLACE)).hasMessageContaining("clients.json");assertThat(MCtarget.MCtemplates()).isEmpty();assertThat(MCimported.MCcontacts()).isEmpty();
    }

    private static MCTemplate MCtemplate(MCDocumentStore MCstore,String MCname,String MCcontent)throws Exception{try(XWPFDocument MCdocument=new XWPFDocument()){MCdocument.createParagraph().createRun().setText(MCcontent);byte[] MCbytes=MCDocumentEngine.MCbytes(MCdocument);return MCstore.MCsaveTemplate(new MCTemplate(0,MCname,MCbytes,MCbytes));}}
    private static Map<String,byte[]> MCread(Path MCsource)throws Exception{Map<String,byte[]> MCout=new LinkedHashMap<>();try(ZipInputStream MCzip=new ZipInputStream(Files.newInputStream(MCsource))){ZipEntry MCentry;while((MCentry=MCzip.getNextEntry())!=null)MCout.put(MCentry.getName(),MCzip.readAllBytes());}return MCout;}
    private static void MCwrite(Path MCdestination,Map<String,byte[]> MCentries)throws Exception{try(ZipOutputStream MCzip=new ZipOutputStream(Files.newOutputStream(MCdestination))){for(var MCe:MCentries.entrySet()){MCzip.putNextEntry(new ZipEntry(MCe.getKey()));MCzip.write(MCe.getValue());MCzip.closeEntry();}}}
}
