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

/** @author MC */
class MCStudioTransferTest {
    @TempDir Path mcTemp;
    private static final ObjectMapper mcJSON=new ObjectMapper();

    @Test void mcSelectivePackagesContainOnlyRelatedGraphAndKeepDocxBytesIntact()throws Exception{
        MCDocumentStore mcSource=new MCDocumentStore(mcTemp.resolve("source"));MCStudioData mcData=new MCStudioData(mcSource.mcDirectory());
        MCTemplate mcSelected=mcTemplate(mcSource,"Seleccionada","{{dni}} {{nombre}} {{ciudad}}");MCTemplate mcOther=mcTemplate(mcSource,"Otra","{{otro}}");
        MCTemplateGroup mcShared=mcSource.mcSaveGroup(0,"Compartido"),mcExclusive=mcSource.mcSaveGroup(0,"Exclusivo");mcSource.mcSetTemplateGroups(mcSelected.mcId(),List.of(mcShared.mcId()));mcSource.mcSetTemplateGroups(mcOther.mcId(),List.of(mcShared.mcId(),mcExclusive.mcId()));
        MCDefinition mcDni=mcSource.mcSaveDefinition(new MCDefinition(0,"dni","DNI","DNI",""));MCDefinition mcName=mcSource.mcSaveDefinition(new MCDefinition(0,"nombre","Nombre","Texto corto",""));MCDefinition mcCity=mcSource.mcSaveDefinition(new MCDefinition(0,"ciudad","Ciudad","Texto corto",""));MCDefinition mcUnrelated=mcSource.mcSaveDefinition(new MCDefinition(0,"otro","Otro","Texto corto",""));
        mcSource.mcBindDefinition(mcSelected.mcId(),mcDni.mcId(),true,1);mcSource.mcBindDefinition(mcSelected.mcId(),mcName.mcId(),false,2);mcSource.mcBindDefinition(mcOther.mcId(),mcUnrelated.mcId(),false,1);
        mcData.mcSaveApiBinding(new MCApiBinding(mcName.mcId(),"DNI","field:nombres"));mcData.mcSaveApiBinding(new MCApiBinding(mcCity.mcId(),"DNI","field:distrito"));mcData.mcSaveApiBinding(new MCApiBinding(mcUnrelated.mcId(),"RUC","field:razon_social"));
        mcData.mcSaveApiContext(new MCApiContext(mcSelected.mcId(),"dni","cliente","DNI",true));mcData.mcSaveApiContext(new MCApiContext(mcSelected.mcId(),"nombre","cliente","DNI",false));mcData.mcSaveContactBinding(new MCContactBinding(0,mcSelected.mcId(),"cliente","Persona","given","nombre"));
        mcData.mcSaveMapping(new MCMapping(0,mcSelected.mcId(),"dni","nombres","nombre"));
        MCKind mcKind=mcSource.mcSaveKind(new MCKind(0,"Carta","Categoría","Descripción",mcSelected.mcId(),"dni,nombre,ciudad",true));mcSource.mcSaveKindFieldRules(mcKind.mcId(),Map.of("dni",true),Map.of("ciudad","Lima"));mcSource.mcSaveKind(new MCKind(0,"No exportar","","",mcOther.mcId(),"otro",true));
        new MCApiCredentialStore(mcSource.mcDirectory()).mcAdd("Privada","Factiliza","secret-test-token");mcData.mcSaveContact(new MCContact(0,"Persona","No exportar","","DNI","12345678","","","{}"));
        for(List<Long> mcSelection:List.of(List.of(mcSelected.mcId()),List.of(mcSelected.mcId(),mcOther.mcId()))){
            Path mcZip=mcTemp.resolve("export-"+mcSelection.size()+".zip");new MCStudioTransfer(mcSource,mcData).mcExportConfiguration(mcZip,mcSelection);Map<String,byte[]> mcEntries=mcRead(mcZip);assertThat(mcEntries.keySet()).containsExactly("configuration.json");String mcPayload=new String(mcEntries.get("configuration.json"),java.nio.charset.StandardCharsets.UTF_8);assertThat(mcPayload).doesNotContain("secret-test-token","12345678");
            JsonNode mcRoot=mcJSON.readTree(mcPayload);assertThat(mcRoot.path("templates")).hasSize(mcSelection.size());
            if(mcSelection.size()==1){assertThat(mcRoot.path("definitions")).hasSize(3);assertThat(mcRoot.path("apiBindings")).hasSize(2);assertThat(mcRoot.path("groups")).hasSize(1);assertThat(mcRoot.path("groups").get(0).path("count").asInt()).isEqualTo(1);assertThat(mcRoot.path("kinds")).hasSize(1);assertThat(mcPayload).doesNotContain("No exportar","Exclusivo");}
            MCDocumentStore mcTarget=new MCDocumentStore(mcTemp.resolve("target-"+mcSelection.size()));MCStudioData mcImported=new MCStudioData(mcTarget.mcDirectory());new MCStudioTransfer(mcTarget,mcImported).mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);
            MCTemplate mcRestored=mcTarget.mcTemplates().stream().filter(mcT->mcT.mcName().equals(mcSelected.mcName())).findFirst().orElseThrow();assertThat(mcRestored.mcOriginal()).containsExactly(mcSelected.mcOriginal());assertThat(mcRestored.mcEdited()).containsExactly(mcSelected.mcEdited());assertThat(mcTarget.mcVariables(mcRestored.mcId())).extracting(MCVariable::mcKey).containsExactlyInAnyOrder("dni","nombre","ciudad");assertThat(mcImported.mcApiContexts(mcRestored.mcId())).hasSize(2);assertThat(mcImported.mcContactBindings(mcRestored.mcId())).hasSize(1);assertThat(mcImported.mcMappings(mcRestored.mcId())).hasSize(1);
            MCKind mcRestoredKind=mcTarget.mcKinds().stream().filter(mcK->mcK.mcTitle().equals("Carta")).findFirst().orElseThrow();assertThat(mcTarget.mcKindDefaults(mcRestoredKind.mcId())).containsEntry("ciudad","Lima");
            try(XWPFDocument mcGenerated=MCDocumentEngine.mcOpen(MCDocumentEngine.mcGenerate(mcRestored.mcEdited(),Map.of("dni","12345678","nombre","Ana","ciudad","Lima")))){assertThat(mcGenerated.getParagraphs().get(0).getText()).isEqualTo("12345678 Ana Lima");}
        }
    }

    @Test void mcClientsAndCombinedPackagesDetectSectionsAndUpsertOnlyExactIdentity()throws Exception{
        MCDocumentStore mcSource=new MCDocumentStore(mcTemp.resolve("source"));MCStudioData mcData=new MCStudioData(mcSource.mcDirectory());mcTemplate(mcSource,"Carta","Contenido");
        mcData.mcSaveContact(new MCContact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{\"telefono\":\"555\"}"));mcData.mcSaveContact(new MCContact(0,"Empresa","ACME","","RUC","20123456789","Cusco","Representante","{\"capital\":100}"));
        Path mcConfig=mcTemp.resolve("config.zip"),mcClients=mcTemp.resolve("clients.zip");MCStudioTransfer mcTransfer=new MCStudioTransfer(mcSource,mcData);mcTransfer.mcExportConfiguration(mcConfig);mcTransfer.mcExportClients(mcClients);JsonNode mcClientRoot=mcJSON.readTree(mcRead(mcClients).get("clients.json"));assertThat(mcClientRoot.path("format").asText()).isEqualTo("document-studio-clients");assertThat(mcClientRoot.path("version").asInt()).isEqualTo(1);
        MCDocumentStore mcTarget=new MCDocumentStore(mcTemp.resolve("target"));MCStudioData mcImported=new MCStudioData(mcTarget.mcDirectory());MCStudioTransfer mcImporter=new MCStudioTransfer(mcTarget,mcImported);
        mcImported.mcSaveContact(new MCContact(0,"Persona","Otro tipo","","CE","12345678","","","{}"));
        assertThat(mcImporter.mcImportConfiguration(mcClients,MCStudioTransfer.MCConflict.KEEP).mcClients()).isEqualTo(2);assertThat(mcImported.mcContacts()).hasSize(3);assertThat(mcImporter.mcImportConfiguration(mcClients,MCStudioTransfer.MCConflict.KEEP).mcClients()).isZero();assertThat(mcImported.mcContacts()).hasSize(3);
        MCContact mcOriginal=mcImported.mcMatchingContact("Persona","DNI","12345678");mcImported.mcSaveContact(new MCContact(mcOriginal.mcId(),mcOriginal.mcKind(),"Local",mcOriginal.mcFamily(),mcOriginal.mcDocumentType(),mcOriginal.mcNumber(),"Arequipa","","{\"local\":true}"));
        mcImporter.mcImportConfiguration(mcClients,MCStudioTransfer.MCConflict.KEEP);assertThat(mcImported.mcMatchingContact("Persona","DNI","12345678").mcGiven()).isEqualTo("Local");
        Map<String,byte[]> mcCombined=new LinkedHashMap<>(mcRead(mcConfig));mcCombined.putAll(mcRead(mcClients));Path mcZip=mcTemp.resolve("combined.zip");mcWrite(mcZip,mcCombined);MCStudioTransfer.MCImportResult mcResult=mcImporter.mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.REPLACE);assertThat(mcResult.mcTemplates()).isEqualTo(1);assertThat(mcResult.mcClients()).isEqualTo(2);assertThat(mcImported.mcContacts()).hasSize(3);assertThat(mcImported.mcMatchingContact("Persona","DNI","12345678").mcExtrasJson()).contains("555");assertThat(mcImported.mcMatchingContact("Persona","CE","12345678").mcGiven()).isEqualTo("Otro tipo");assertThat(mcImported.mcMatchingContact("Empresa","RUC","20123456789").mcRepresentatives()).isEqualTo("Representante");
    }

    @Test void mcInvalidClientVersionInCombinedPackageIsRejectedBeforeWritingTemplates()throws Exception{
        MCDocumentStore mcSource=new MCDocumentStore(mcTemp.resolve("source"));MCStudioData mcData=new MCStudioData(mcSource.mcDirectory());mcTemplate(mcSource,"Carta","Texto");Path mcConfig=mcTemp.resolve("config.zip");new MCStudioTransfer(mcSource,mcData).mcExportConfiguration(mcConfig);Map<String,byte[]> mcEntries=new LinkedHashMap<>(mcRead(mcConfig));mcEntries.put("clients.json","{\"format\":\"document-studio-clients\",\"version\":2,\"clients\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));Path mcInvalid=mcTemp.resolve("invalid.zip");mcWrite(mcInvalid,mcEntries);
        MCDocumentStore mcTarget=new MCDocumentStore(mcTemp.resolve("target"));MCStudioData mcImported=new MCStudioData(mcTarget.mcDirectory());assertThatThrownBy(()->new MCStudioTransfer(mcTarget,mcImported).mcImportConfiguration(mcInvalid,MCStudioTransfer.MCConflict.REPLACE)).hasMessageContaining("clients.json");assertThat(mcTarget.mcTemplates()).isEmpty();assertThat(mcImported.mcContacts()).isEmpty();
    }

    private static MCTemplate mcTemplate(MCDocumentStore mcStore,String mcName,String mcContent)throws Exception{try(XWPFDocument mcDocument=new XWPFDocument()){mcDocument.createParagraph().createRun().setText(mcContent);byte[] mcBytes=MCDocumentEngine.mcBytes(mcDocument);return mcStore.mcSaveTemplate(new MCTemplate(0,mcName,mcBytes,mcBytes));}}
    private static Map<String,byte[]> mcRead(Path mcSource)throws Exception{Map<String,byte[]> mcOut=new LinkedHashMap<>();try(ZipInputStream mcZip=new ZipInputStream(Files.newInputStream(mcSource))){ZipEntry mcEntry;while((mcEntry=mcZip.getNextEntry())!=null)mcOut.put(mcEntry.getName(),mcZip.readAllBytes());}return mcOut;}
    private static void mcWrite(Path mcDestination,Map<String,byte[]> mcEntries)throws Exception{try(ZipOutputStream mcZip=new ZipOutputStream(Files.newOutputStream(mcDestination))){for(var mcE:mcEntries.entrySet()){mcZip.putNextEntry(new ZipEntry(mcE.getKey()));mcZip.write(mcE.getValue());mcZip.closeEntry();}}}
}
