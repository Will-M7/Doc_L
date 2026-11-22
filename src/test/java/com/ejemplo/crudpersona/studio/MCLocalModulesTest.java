package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.*;
import com.ejemplo.crudpersona.studio.MCStudioData.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;

/** @author MC */
class MCLocalModulesTest {
    @TempDir Path mcTemp;

    @Test void mcIndependentDefinitionsKeepTheirGlobalDefaultAndScopeAcrossMigrationAndZip()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp.resolve("globals"));MCStudioData mcData=new MCStudioData(mcStore.mcDirectory());String mcAnnual="Año de la Esperanza y el Fortalecimiento de la Democracia";
        MCDefinition mcGlobal=mcStore.mcSaveDefinition(new MCDefinition(0,"Nombre_del_Anio","Nombre del Año","Texto corto",mcAnnual,false,"dd/MM/yyyy",false));MCDefinition mcName=mcStore.mcSaveDefinition(new MCDefinition(0,"nombre","Nombre","Texto corto",""));
        assertThat(mcGlobal.mcBindToClient()).isFalse();assertThat(mcGlobal.mcEditorKey("Persona_1")).isEqualTo("Nombre_del_Anio");assertThat(mcGlobal.mcInitialValue()).isEqualTo(mcAnnual);assertThat(mcName.mcBindToClient()).isTrue();assertThat(mcName.mcEditorKey("Persona_1")).isEqualTo("Persona_1.nombre");
        byte[] mcBytes;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("{{Nombre_del_Anio}} {{Persona_1.nombre}}");mcBytes=MCDocumentEngine.mcBytes(mcDoc);}MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Institucional",mcBytes,mcBytes));
        assertThat(mcStore.mcBindDefinition(mcTemplate.mcId(),mcGlobal.mcId(),"Persona_1.Nombre_del_Anio",false,1).mcKey()).isEqualTo("Nombre_del_Anio");mcStore.mcBindDefinition(mcTemplate.mcId(),mcName.mcId(),"Persona_1.nombre",false,2);mcStore.mcAddPersona(mcTemplate.mcId(),"Dirigido");
        assertThat(new MCDocumentStore(mcStore.mcDirectory()).mcDefinitions()).contains(mcGlobal,mcName);Path mcZip=mcTemp.resolve("globals.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcZip);
        MCDocumentStore mcTarget=new MCDocumentStore(mcTemp.resolve("import"));MCStudioData mcTargetData=new MCStudioData(mcTarget.mcDirectory());new MCStudioTransfer(mcTarget,mcTargetData).mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);assertThat(mcTarget.mcDefinitions().stream().filter(mcD->mcD.mcKey().equals(mcGlobal.mcKey())).findFirst().orElseThrow()).satisfies(mcD->{assertThat(mcD.mcBindToClient()).isFalse();assertThat(mcD.mcInitialValue()).isEqualTo(mcAnnual);});
        new MCStudioTransfer(mcTarget,mcTargetData).mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.REPLACE);assertThat(mcTarget.mcDefinitions()).hasSize(2);assertThat(mcTarget.mcDefinitions().stream().filter(mcD->mcD.mcKey().equals("nombre")).findFirst().orElseThrow().mcBindToClient()).isTrue();
        var mcJson=new com.fasterxml.jackson.databind.ObjectMapper();com.fasterxml.jackson.databind.node.ObjectNode mcPrevious;try(ZipInputStream mcInput=new ZipInputStream(Files.newInputStream(mcZip))){mcInput.getNextEntry();mcPrevious=(com.fasterxml.jackson.databind.node.ObjectNode)mcJson.readTree(mcInput.readAllBytes());}assertThat(mcPrevious.path("version").asInt()).isEqualTo(9);mcPrevious.put("version",8);for(var mcDefinition:mcPrevious.path("definitions"))((com.fasterxml.jackson.databind.node.ObjectNode)mcDefinition).remove("bindToClient");Path mcLegacy=mcTemp.resolve("previous.zip");try(var mcOutput=new java.util.zip.ZipOutputStream(Files.newOutputStream(mcLegacy))){mcOutput.putNextEntry(new java.util.zip.ZipEntry("configuration.json"));mcOutput.write(mcJson.writeValueAsBytes(mcPrevious));mcOutput.closeEntry();}MCDocumentStore mcOldImport=new MCDocumentStore(mcTemp.resolve("previous"));new MCStudioTransfer(mcOldImport,new MCStudioData(mcOldImport.mcDirectory())).mcImportConfiguration(mcLegacy,MCStudioTransfer.MCConflict.KEEP);assertThat(mcOldImport.mcDefinitions()).allMatch(MCDefinition::mcBindToClient);
    }

    @Test void mcIndependentVariablesAreNeverClientBindingsOrExtrasEvenWithOldPersonPrefixes()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);MCDefinition mcGlobal=mcStore.mcSaveDefinition(new MCDefinition(0,"Nombre_del_Anio","Nombre del Año","Texto corto","Valor institucional",false,"dd/MM/yyyy",false));List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();
        List<MCVariable> mcVars=List.of(new MCVariable(0,1,"dni","DNI","DNI","",false,1),new MCVariable(0,1,"nombre","Nombre","Texto corto","",false,2),new MCVariable(0,1,mcGlobal.mcKey(),mcGlobal.mcLabel(),mcGlobal.mcType(),mcGlobal.mcDefaultValue(),false,3),new MCVariable(0,1,"Persona_1.Nombre_del_Anio",mcGlobal.mcLabel(),mcGlobal.mcType(),mcGlobal.mcDefaultValue(),false,4));
        List<MCContactBinding> mcOld=List.of(new MCContactBinding(0,1,"document:dni","Persona","extra:Nombre_del_Anio",mcGlobal.mcKey()),new MCContactBinding(0,1,"document:dni","Persona","extra:Nombre_del_Anio","Persona_1.Nombre_del_Anio"));List<MCContactBinding> mcBindings=MCContactReuse.mcFormBindings(1,mcVars,mcOld,List.of(),Map.of(),mcDefinitions);assertThat(mcBindings).extracting(MCContactBinding::mcVariableKey).containsExactlyInAnyOrder("dni","nombre");
        Map<String,String> mcValues=Map.of("dni","12345678","nombre","Ana",mcGlobal.mcKey(),mcGlobal.mcInitialValue(),"Persona_1.Nombre_del_Anio",mcGlobal.mcInitialValue());List<MCContactBinding> mcWithOld=new ArrayList<>(mcBindings);mcWithOld.addAll(mcOld);assertThat(MCContactReuse.mcSave(mcData,mcWithOld,mcValues,Map.of(),mcDefinitions).mcCreated()).isEqualTo(1);var mcContact=mcData.mcContacts().get(0);assertThat(mcContact.mcGiven()).isEqualTo("Ana");assertThat(mcContact.mcExtrasJson()).doesNotContain(mcGlobal.mcKey(),mcGlobal.mcInitialValue(),"Persona_1");assertThat(MCContactReuse.mcSave(mcData,mcWithOld,mcValues,Map.of(),mcDefinitions).mcCreated()).isZero();
    }
    @Test void mcQualifiedTokensAcrossRunsHeadersAndTablesGenerateIndependently()throws Exception{
        byte[] mcBytes;try(XWPFDocument mcDoc=new XWPFDocument()){
            var mcP=mcDoc.createParagraph();mcP.createRun().setText("{{Persona_1.");mcP.createRun().setText("DNI}} / {{Persona_2.DNI}} / {{DNI}}");mcDoc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("{{Persona_1.Cargo}}");mcDoc.createTable(1,1).getRow(0).getCell(0).setText("{{Persona_2.Nombres_Com}}");mcBytes=MCDocumentEngine.mcBytes(mcDoc);
        }
        try(XWPFDocument mcSource=MCDocumentEngine.mcOpen(mcBytes)){assertThat(MCDocumentEngine.mcMarkers(mcSource)).containsExactlyInAnyOrder("Persona_1.DNI","Persona_2.DNI","DNI","Persona_1.Cargo","Persona_2.Nombres_Com");}
        try(XWPFDocument mcResult=MCDocumentEngine.mcOpen(MCDocumentEngine.mcGenerate(mcBytes,Map.of("Persona_1.DNI","11111111","Persona_2.DNI","22222222","DNI","33333333","Persona_1.Cargo","Presidente","Persona_2.Nombres_Com","María Ruiz")))){assertThat(mcResult.getParagraphs().get(0).getText()).isEqualTo("11111111 / 22222222 / 33333333");assertThat(mcResult.getHeaderList().get(0).getText()).contains("Presidente");assertThat(mcResult.getTables().get(0).getText()).contains("María Ruiz");assertThat(MCDocumentEngine.mcMarkers(mcResult)).isEmpty();}
    }
    @Test void mcPersonasAndAutomaticDateDefinitionsPersistAndTravelWithConfiguration()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp.resolve("source"));MCStudioData mcData=new MCStudioData(mcStore.mcDirectory());byte[] mcBytes;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("{{Persona_1.DNI}} {{Persona_2.DNI}} {{Fecha}}");mcBytes=MCDocumentEngine.mcBytes(mcDoc);}MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Personas",mcBytes,mcBytes));
        MCDefinition mcDni=mcStore.mcSaveDefinition(new MCDefinition(0,"DNI","DNI","Mixto","")),mcDate=mcStore.mcSaveDefinition(new MCDefinition(0,"Fecha","Fecha","Fecha","",true,"d 'de' MMMM 'de' yyyy"));mcStore.mcBindDefinition(mcTemplate.mcId(),mcDni.mcId(),"Persona_1.DNI",false,1);mcStore.mcBindDefinition(mcTemplate.mcId(),mcDni.mcId(),"Persona_2.DNI",false,2);mcStore.mcBindDefinition(mcTemplate.mcId(),mcDate.mcId(),false,3);
        MCPersona mcFirst=mcStore.mcAddPersona(mcTemplate.mcId(),"Solicitante"),mcSecond=mcStore.mcAddPersona(mcTemplate.mcId(),"Arrendador");assertThat(mcFirst.mcPersonaKey()).isEqualTo("Persona_1");assertThat(mcSecond.mcPersonaKey()).isEqualTo("Persona_2");mcStore.mcRenamePersona(mcSecond.mcId(),"Arrendatario");assertThat(mcStore.mcPersonas(mcTemplate.mcId())).extracting(MCPersona::mcDisplayName).containsExactly("Solicitante","Arrendatario");assertThat(new MCDocumentStore(mcStore.mcDirectory()).mcDefinitions().stream().filter(mcD->mcD.mcKey().equals("Fecha")).findFirst().orElseThrow().mcAutoCurrentDate()).isTrue();
        Path mcZip=mcTemp.resolve("personas.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcZip);MCDocumentStore mcImported=new MCDocumentStore(mcTemp.resolve("target"));mcImported.mcSaveDefinition(new MCDefinition(0,"otro","Otro","Texto",""));MCStudioTransfer mcTransfer=new MCStudioTransfer(mcImported,new MCStudioData(mcImported.mcDirectory()));mcTransfer.mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);mcTransfer.mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);
        MCTemplate mcCopy=mcImported.mcTemplates().get(0);assertThat(mcImported.mcPersonas(mcCopy.mcId())).extracting(MCPersona::mcDisplayName).containsExactly("Solicitante","Arrendatario");assertThat(mcImported.mcVariables(mcCopy.mcId())).extracting(MCVariable::mcKey).containsExactly("Persona_1.DNI","Persona_2.DNI","Fecha");assertThat(mcImported.mcDefinitionId(mcImported.mcVariables(mcCopy.mcId()).get(0).mcId())).isEqualTo(mcImported.mcDefinitionId(mcImported.mcVariables(mcCopy.mcId()).get(1).mcId()));MCDefinition mcRestored=mcImported.mcDefinitions().stream().filter(mcD->mcD.mcKey().equals("Fecha")).findFirst().orElseThrow();assertThat(mcRestored.mcAutoCurrentDate()).isTrue();assertThat(mcRestored.mcDateFormat()).isEqualTo(mcDate.mcDateFormat());assertThat(mcRestored.mcCurrentDate()).isEqualTo(mcDate.mcCurrentDate());assertThat(mcCopy.mcEdited()).containsExactly(mcBytes);
        mcImported.mcDeletePersona(mcImported.mcPersonas(mcCopy.mcId()).get(0).mcId());assertThat(mcImported.mcPersonas(mcCopy.mcId())).hasSize(1);mcTransfer.mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.REPLACE);assertThat(mcImported.mcPersonas(mcCopy.mcId())).hasSize(2);mcImported.mcDeleteTemplate(mcCopy.mcId());assertThat(mcImported.mcPersonas(mcCopy.mcId())).isEmpty();
    }
    @Test void mcParticipantContactsAndTheirExtrasNeverMixAndAllowMissingDocuments()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);List<MCVariable> mcVariables=new ArrayList<>();for(String mcKey:List.of("Persona_1.DNI","Persona_1.Nombres_Com","Persona_1.Cargo","Persona_2.DNI","Persona_2.Nombres_Com","Persona_2.Cargo","Persona_3.Nombres_Com","Persona_3.Cargo"))mcVariables.add(new MCVariable(0,1,mcKey,MCDocumentEngine.mcBaseKey(mcKey),"Texto","",false,mcVariables.size()));
        List<MCContactBinding> mcBindings=MCContactReuse.mcFormBindings(1,mcVariables,List.of(),List.of(),Map.of());Map<String,String> mcValues=new LinkedHashMap<>(Map.of("Persona_1.DNI","11111111","Persona_1.Nombres_Com","Ana Pérez","Persona_1.Cargo","Directora","Persona_2.DNI","22222222","Persona_2.Nombres_Com","Luis Ruiz","Persona_2.Cargo","Secretario","Persona_3.Nombres_Com","Luisito","Persona_3.Cargo","Presidente","Fecha","Privado"));
        assertThat(MCContactReuse.mcSave(mcData,mcBindings,mcValues,Map.of()).mcCreated()).isEqualTo(3);MCContact mcAna=mcData.mcMatchingContact("Persona","DNI","11111111"),mcLuis=mcData.mcMatchingContact("Persona","DNI","22222222");assertThat(mcAna.mcGiven()).isEqualTo("Ana Pérez");assertThat(mcAna.mcExtrasJson()).contains("Directora").doesNotContain("Luis Ruiz","Secretario","Persona_2","Privado");assertThat(mcLuis.mcExtrasJson()).contains("Secretario").doesNotContain("Ana Pérez","Directora");assertThat(mcData.mcFindContacts("Luisito").get(0).mcNumber()).isEmpty();assertThat(MCContactReuse.mcSave(mcData,mcBindings,mcValues,Map.of()).mcCreated()).isZero();mcValues.put("Persona_1.Cargo","Presidenta");MCContactReuse.mcSave(mcData,mcBindings,mcValues,Map.of());assertThat(mcData.mcMatchingContact("Persona","DNI","22222222")).isEqualTo(mcLuis);assertThat(mcData.mcContacts()).hasSize(3);
    }
    @Test void mcLegacyDefinitionsReceiveSafeAutomaticDateDefaults()throws Exception{
        try(var mcC=java.sql.DriverManager.getConnection("jdbc:sqlite:"+mcTemp.resolve("studio.db"));var mcS=mcC.createStatement()){mcS.execute("PRAGMA foreign_keys=ON");mcS.executeUpdate("CREATE TABLE variable_definition(id INTEGER PRIMARY KEY,key TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,default_value TEXT)");mcS.executeUpdate("INSERT INTO variable_definition VALUES(1,'Fecha','Fecha','Fecha','')");}
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);MCDefinition mcDate=mcStore.mcDefinitions().get(0);assertThat(mcDate.mcBindToClient()).isTrue();assertThat(mcDate.mcAutoCurrentDate()).isFalse();assertThat(mcDate.mcDateFormat()).isEqualTo("dd/MM/yyyy");mcStore.mcSaveDefinition(new MCDefinition(mcDate.mcId(),mcDate.mcKey(),mcDate.mcLabel(),mcDate.mcType(),"",true,"EEEE, d 'de' MMMM 'de' yyyy"));assertThat(new MCDocumentStore(mcTemp).mcDefinitions().get(0).mcCurrentDate()).isEqualTo(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",Locale.forLanguageTag("es-PE"))));
    }
    @Test void mcComplementsLearnObjectsAndUndocumentedPeopleInBothDirectionsAndStayLocal()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);
        MCDefinition mcObject=mcStore.mcSaveDefinition(new MCDefinition(0,"objeto","Objeto","Texto","")),mcPrice=mcStore.mcSaveDefinition(new MCDefinition(0,"precio","Precio","Texto","")),mcPerson=mcStore.mcSaveDefinition(new MCDefinition(0,"Dirigido","Dirigido","Texto","")),mcRole=mcStore.mcSaveDefinition(new MCDefinition(0,"Cargo","Cargo","Texto",""));
        mcStore.mcSaveComplement(mcObject.mcId(),mcPrice.mcId(),true);mcStore.mcSaveComplement(mcPerson.mcId(),mcRole.mcId(),true);
        assertThat(mcStore.mcComplements(mcObject.mcId())).extracting(MCComplement::mcTargetDefId).containsExactly(mcPrice.mcId());assertThat(mcStore.mcComplements(mcPrice.mcId())).extracting(MCComplement::mcTargetDefId).containsExactly(mcObject.mcId());assertThatThrownBy(()->mcStore.mcSaveComplement(mcObject.mcId(),mcObject.mcId(),true)).isInstanceOf(IllegalArgumentException.class);
        mcData.mcLearnComplements(mcStore.mcDefinitions(),mcStore.mcComplements(),Map.of("objeto","Silla","precio","120.00","Dirigido","Luisito","Cargo","Presidente de la Asociación"));
        assertThat(mcData.mcSuggestions("objeto",List.of("precio"),"si",false).get(0).mcComplements()).containsEntry("precio","120.00");assertThat(mcData.mcSuggestions("precio",List.of("objeto"),"120.00",true).get(0).mcComplements()).containsEntry("objeto","Silla");
        assertThat(mcData.mcSuggestions("dirigido",List.of("cargo"),"LUISITO",true).get(0).mcComplements()).containsEntry("cargo","Presidente de la Asociación");assertThat(mcData.mcSuggestions("Cargo",List.of("Dirigido"),"Presidente",false).get(0).mcComplements()).containsEntry("dirigido","Luisito");
        MCContact mcLuis=mcData.mcContacts().get(0);assertThat(mcLuis.mcNumber()).isEmpty();assertThat(mcLuis.mcGiven()).isEqualTo("Luisito");assertThat(mcLuis.mcExtrasJson()).contains("cargo","Presidente");
        mcData.mcLearnComplements(mcStore.mcDefinitions(),mcStore.mcComplements(),Map.of("objeto","Silla","precio","130.00","Dirigido","Luisito","Cargo","Secretario"));assertThat(mcData.mcSuggestions("precio",List.of("objeto"),"120.00",true)).isEmpty();assertThat(mcData.mcSuggestions("cargo",List.of("dirigido"),"Presidente de la Asociación",true)).isEmpty();assertThat(mcData.mcContacts()).hasSize(1);assertThat(mcData.mcSuggestions("dirigido",List.of("cargo"),"Luisito",true).get(0).mcComplements()).containsEntry("cargo","Secretario");assertThat(mcData.mcSuggestions("objeto",List.of("precio"),"Silla",true).get(0).mcComplements()).containsEntry("precio","130.00");
        new MCStudioData(mcTemp).mcRememberPair("objeto","100% especial","precio","1",false);assertThat(mcData.mcSuggestions("objeto",List.of("precio"),"100%",false)).hasSize(1);assertThat(mcData.mcSuggestions("objeto",List.of("precio"),"S_",false)).isEmpty();
        mcStore.mcSaveComplement(mcObject.mcId(),mcPrice.mcId(),false);assertThat(mcStore.mcComplements(mcPrice.mcId())).isEmpty();mcStore.mcDeleteDefinition(mcPrice.mcId());assertThat(mcStore.mcComplements(mcObject.mcId())).isEmpty();
    }

    @Test void mcContactsWithoutDocumentsDeduplicateByNormalizedNameAndSearchAllExtraValues()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);MCStudioData mcData=new MCStudioData(mcTemp);
        MCContact mcAna=mcData.mcSaveContact(new MCContact(0,"Persona","Ána","Pérez","DNI","","Lima","","{\"Cargo\":\"Presidenta\",\"Asociacion\":\"Los Pinos\"}"));MCContact mcLuis=mcData.mcSaveContact(new MCContact(0,"Persona","Luisito","","DNI","","","","{\"cargo\":\"Secretario\"}"));assertThat(mcData.mcContacts()).hasSize(2);
        MCContact mcUpdated=mcData.mcUpsertContact(new MCContact(0,"Persona"," ana "," PÉREZ ","","","","","{\"Cargo\":\"Tesorera\"}"));assertThat(mcUpdated.mcId()).isEqualTo(mcAna.mcId());assertThat(mcData.mcContacts()).hasSize(2);assertThat(mcUpdated.mcExtrasJson()).contains("Los Pinos","Tesorera");assertThat(mcData.mcFindContacts("ana perez")).extracting(MCContact::mcId).containsExactly(mcAna.mcId());assertThat(mcData.mcFindContacts("LOS PINOS")).extracting(MCContact::mcId).containsExactly(mcAna.mcId());assertThat(mcData.mcFindContactsByVariable("CARGO","secre",true)).extracting(MCContact::mcId).containsExactly(mcLuis.mcId());
        MCContact mcDocumented=mcData.mcSaveContact(new MCContact(0,"Persona","Juan","Pérez","DNI","12345678","Cusco","","{\"otro\":\"conservado\"}"));MCDefinition mcName=mcStore.mcSaveDefinition(new MCDefinition(0,"dirigido","Dirigido","Texto","")),mcRole=mcStore.mcSaveDefinition(new MCDefinition(0,"cargo","Cargo","Texto",""));mcStore.mcSaveComplement(mcName.mcId(),mcRole.mcId(),true);mcData.mcLearnComplements(mcStore.mcDefinitions(),mcStore.mcComplements(),Map.of("dirigido","Juan Pérez","cargo","Director"));MCContact mcAfter=mcData.mcMatchingContact("Persona","DNI","12345678");assertThat(mcAfter.mcId()).isEqualTo(mcDocumented.mcId());assertThat(mcAfter.mcAddress()).isEqualTo("Cusco");assertThat(mcAfter.mcExtrasJson()).contains("conservado","Director");assertThat(mcData.mcContacts()).hasSize(3);
    }

    @Test void mcMigratesLegacyUniqueContactIdentityWithoutLosingExistingData()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp);
        try(var mcC=java.sql.DriverManager.getConnection("jdbc:sqlite:"+mcTemp.resolve("studio.db"));var mcS=mcC.createStatement()){
            mcS.execute("PRAGMA foreign_keys=ON");mcS.executeUpdate("CREATE TABLE contact(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}',UNIQUE(kind,document_type,number))");mcS.executeUpdate("INSERT INTO contact(kind,given,document_type,number,extras_json) VALUES('Persona','Luisito','DNI','','{\"cargo\":\"Presidente\"}')");
        }
        MCStudioData mcData=new MCStudioData(mcTemp);mcData.mcSaveContact(new MCContact(0,"Persona","María","","DNI","","","","{}"));assertThat(mcData.mcContacts()).hasSize(2);assertThat(mcData.mcFindContacts("Luisito").get(0).mcExtrasJson()).contains("Presidente");assertThat(new MCStudioData(mcTemp).mcContacts()).hasSize(2);
    }

    @Test void mcComplementDefinitionsAndLinksTravelWithFilteredTemplateConfiguration()throws Exception{
        MCDocumentStore mcStore=new MCDocumentStore(mcTemp.resolve("export"));MCStudioData mcData=new MCStudioData(mcStore.mcDirectory());MCDefinition mcSource=mcStore.mcSaveDefinition(new MCDefinition(0,"objeto","Objeto","Texto","")),mcTarget=mcStore.mcSaveDefinition(new MCDefinition(0,"precio","Precio","Texto",""));mcStore.mcSaveComplement(mcSource.mcId(),mcTarget.mcId(),true);
        byte[] mcBytes;try(XWPFDocument mcDocument=new XWPFDocument()){mcDocument.createParagraph().createRun().setText("{{objeto}}");mcBytes=MCDocumentEngine.mcBytes(mcDocument);}MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Complementos",mcBytes,mcBytes));mcStore.mcBindDefinition(mcTemplate.mcId(),mcSource.mcId(),false,1);Path mcZip=mcTemp.resolve("complementos.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcZip,List.of(mcTemplate.mcId()));
        MCDocumentStore mcImported=new MCDocumentStore(mcTemp.resolve("import"));MCStudioData mcImportedData=new MCStudioData(mcImported.mcDirectory());MCStudioTransfer mcTransfer=new MCStudioTransfer(mcImported,mcImportedData);mcTransfer.mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);mcTransfer.mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);assertThat(mcImported.mcDefinitions()).hasSize(2);assertThat(mcImported.mcComplements()).hasSize(1);MCDefinition mcPrice=mcImported.mcDefinitions().stream().filter(mcD->mcD.mcKey().equals("precio")).findFirst().orElseThrow();assertThat(mcImported.mcComplements(mcPrice.mcId())).hasSize(1);assertThat(mcImported.mcTemplates().get(0).mcEdited()).containsExactly(mcBytes);
        mcData.mcSaveContact(new MCContact(0,"Persona","Luisito","","","","","","{\"cargo\":\"Presidente\"}"));mcData.mcSaveContact(new MCContact(0,"Persona","María","","","","","","{}"));Path mcClients=mcTemp.resolve("personas.zip");new MCStudioTransfer(mcStore,mcData).mcExportClients(mcClients);mcTransfer.mcImportConfiguration(mcClients,MCStudioTransfer.MCConflict.KEEP);mcTransfer.mcImportConfiguration(mcClients,MCStudioTransfer.MCConflict.KEEP);assertThat(mcImportedData.mcContacts()).hasSize(2);assertThat(mcImportedData.mcFindContacts("Presidente")).hasSize(1);
    }

    @Test void mcTemplateCatalogGroupsFiltersAndEditorSaveRemainIndependent()throws Exception{
        Path mcDirectory=mcTemp.resolve("catalog");MCDocumentStore mcStore=new MCDocumentStore(mcDirectory);MCStudioData mcData=new MCStudioData(mcDirectory);
        byte[] mcDocx;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("Plantilla");mcDocx=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcFirst=mcStore.mcSaveTemplate(new MCTemplate(0,"Solicitud",mcDocx,mcDocx));mcData.mcRecordRevision(mcFirst.mcId(),mcDocx);
        MCTemplateGroup mcSolicitudes=mcStore.mcSaveGroup(0,"Solicitudes");
        assertThat(mcStore.mcTemplates()).hasSize(1);assertThat(mcStore.mcGroupIds(mcFirst.mcId())).isEmpty();assertThat(mcStore.mcGroups().get(0).mcCount()).isZero();
        for(long mcSelected:List.of(-1L,0L,mcSolicitudes.mcId()))assertThat(mcStore.mcTemplates().stream().filter(mcT->MCStudioWindow.mcLibraryMatches(mcT.mcName(),"")).map(MCTemplate::mcId).toList()).containsExactly(mcFirst.mcId());
        assertThat(MCStudioWindow.mcLibraryHighlighted(mcStore.mcGroupIds(mcFirst.mcId()),-1)).isFalse();
        assertThat(MCStudioWindow.mcLibraryHighlighted(mcStore.mcGroupIds(mcFirst.mcId()),0)).isFalse();
        assertThat(MCStudioWindow.mcLibraryHighlighted(mcStore.mcGroupIds(mcFirst.mcId()),mcSolicitudes.mcId())).isFalse();
        mcStore.mcSetTemplateGroups(mcFirst.mcId(),List.of(mcSolicitudes.mcId()));
        assertThat(mcStore.mcGroups().get(0).mcCount()).isEqualTo(1);assertThat(mcStore.mcTemplates().stream().map(MCTemplate::mcId).toList()).containsExactly(mcFirst.mcId());assertThat(MCStudioWindow.mcLibraryHighlighted(mcStore.mcGroupIds(mcFirst.mcId()),mcSolicitudes.mcId())).isTrue();
        MCTemplate mcOther=mcStore.mcSaveTemplate(new MCTemplate(0,"Carta",mcDocx,mcDocx));
        assertThat(mcStore.mcTemplates().stream().filter(mcT->MCStudioWindow.mcLibraryMatches(mcT.mcName(),"")).map(MCTemplate::mcId).toList()).containsExactlyInAnyOrder(mcFirst.mcId(),mcOther.mcId());
        assertThat(MCStudioWindow.mcLibraryHighlighted(mcStore.mcGroupIds(mcOther.mcId()),mcSolicitudes.mcId())).isFalse();
        assertThat(mcStore.mcTemplates().stream().filter(mcT->MCStudioWindow.mcLibraryMatches(mcT.mcName(),"sol")).map(MCTemplate::mcId).toList()).containsExactly(mcFirst.mcId());
        MCTemplateGroup mcFrecuentes=mcStore.mcSaveGroup(0,"Frecuentes");mcStore.mcSetTemplateGroups(mcFirst.mcId(),List.of(mcSolicitudes.mcId(),mcFrecuentes.mcId()));
        assertThat(mcStore.mcTemplates()).hasSize(2);assertThat(mcStore.mcGroupIds(mcFirst.mcId())).containsExactlyInAnyOrder(mcSolicitudes.mcId(),mcFrecuentes.mcId());
        mcStore.mcSaveTemplate(new MCTemplate(mcFirst.mcId(),"Solicitud editada",mcFirst.mcOriginal(),mcFirst.mcEdited()));assertThat(mcStore.mcGroupIds(mcFirst.mcId())).hasSize(2);
        MCTemplate mcCopy=mcStore.mcSaveTemplate(new MCTemplate(0,"Solicitud (copia)",mcFirst.mcOriginal(),mcFirst.mcEdited()));assertThat(mcStore.mcGroupIds(mcCopy.mcId())).isEmpty();
        mcStore.mcDeleteGroup(mcSolicitudes.mcId(),null);assertThat(mcStore.mcTemplates()).hasSize(3);assertThat(mcStore.mcGroupIds(mcFirst.mcId())).containsExactly(mcFrecuentes.mcId());
        MCDocumentStore mcReopened=new MCDocumentStore(mcDirectory);assertThat(mcReopened.mcGroupIds(mcFirst.mcId())).containsExactly(mcFrecuentes.mcId());assertThat(mcReopened.mcGroupIds(mcCopy.mcId())).isEmpty();assertThat(new MCStudioData(mcDirectory).mcRevisionDates()).containsKey(mcFirst.mcId());
    }

    @Test void mcTransferBackupAndReopen()throws Exception{
        Path mcFirst=mcTemp.resolve("first"),mcSecond=mcTemp.resolve("second");MCDocumentStore mcA=new MCDocumentStore(mcFirst);MCStudioData mcExtra=new MCStudioData(mcFirst);
        byte[] mcDocx;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("Hola {{nombre}}");mcDocx=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcTemplate=mcA.mcSaveTemplate(new MCTemplate(0,"Saludo",mcDocx,mcDocx));mcExtra.mcRecordRevision(mcTemplate.mcId(),mcDocx);
        mcA.mcSaveVariable(new MCVariable(0,mcTemplate.mcId(),"nombre","Nombre","Texto corto","",true,1));
        mcA.mcSaveKind(new MCKind(0,"Carta","","",mcTemplate.mcId(),"nombre",true));
        mcExtra.mcSaveMapping(new MCMapping(0,mcTemplate.mcId(),"nombre","nombres","nombre"));
        mcExtra.mcSaveContact(new MCContact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{}"));
        mcExtra.mcSaveHistory(new MCHistory(0,"Carta",mcTemplate.mcId(),1,null,"{\"nombre\":\"Ana\"}",null,null,MCDocumentEngine.mcGenerate(mcDocx,Map.of("nombre","Ana")),null));
        MCLocalSecret mcSecret=new MCLocalSecret(mcFirst);mcSecret.mcSave("token-privado");assertThat(mcSecret.mcLoad()).isEqualTo("token-privado");
        Path mcConfig=mcTemp.resolve("config.zip");MCStudioTransfer mcTransfer=new MCStudioTransfer(mcA,mcExtra);mcTransfer.mcExportConfiguration(mcConfig);
        try(ZipInputStream mcZip=new ZipInputStream(Files.newInputStream(mcConfig))){assertThat(mcZip.getNextEntry().getName()).isEqualTo("configuration.json");assertThat(new String(mcZip.readAllBytes())).doesNotContain("token-privado","12345678");}
        MCDocumentStore mcB=new MCDocumentStore(mcSecond);MCStudioData mcBData=new MCStudioData(mcSecond);
        MCStudioTransfer.MCImportResult mcImported=new MCStudioTransfer(mcB,mcBData).mcImportConfiguration(mcConfig,MCStudioTransfer.MCConflict.KEEP);
        assertThat(mcImported.mcTemplates()).isEqualTo(1);assertThat(mcB.mcTemplates()).hasSize(1);assertThat(mcB.mcVariables(mcB.mcTemplates().get(0).mcId())).hasSize(1);assertThat(mcB.mcKinds()).hasSize(1);assertThat(mcBData.mcMappings(mcB.mcTemplates().get(0).mcId())).hasSize(1);assertThat(mcBData.mcContacts()).isEmpty();assertThat(mcBData.mcHistory()).isEmpty();
        assertThat(new MCStudioTransfer(mcB,mcBData).mcImportConfiguration(mcConfig,MCStudioTransfer.MCConflict.KEEP).mcConflicts()).isGreaterThan(0);
        Path mcBackup=mcTemp.resolve("backup.dsbk");mcTransfer.mcBackup(mcBackup,"contrasena-segura".toCharArray());
        assertThat(new String(Files.readAllBytes(mcBackup),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("12345678");
        new MCStudioTransfer(mcB,mcBData).mcRestore(mcBackup,"contrasena-segura".toCharArray());
        MCStudioData mcReopened=new MCStudioData(mcSecond);assertThat(mcReopened.mcContacts()).hasSize(1);assertThat(mcReopened.mcHistory()).hasSize(1);
        assertThat(new MCLocalSecret(mcSecond).mcConfigured()).isFalse();
    }

    @Test void mcFactilizaParsesDocumentedResponsesAndRejectsIncompleteData()throws Exception{
        String mcDni="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"12345678\",\"nombres\":\"ANA\",\"apellido_paterno\":\"PEREZ\",\"direccion_completa\":\"LIMA\"}}";
        String mcRuc="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"20123456789\",\"nombre_o_razon_social\":\"EMPRESA\",\"direccion\":\"LIMA\"}}";
        assertThat(MCFactilizaClient.mcParse("DNI","12345678",200,mcDni).mcData()).containsEntry("nombres","ANA");
        assertThat(MCFactilizaClient.mcParse("RUC","20123456789",200,mcRuc).mcData()).containsEntry("nombre_o_razon_social","EMPRESA");
        assertThatThrownBy(()->MCFactilizaClient.mcParse("DNI","12345678",200,"{\"success\":true,\"data\":{}}" )).hasMessageContaining("incompletos");
        assertThatThrownBy(()->MCFactilizaClient.mcParse("RUC","20123456789",401,"{\"success\":false,\"message\":\"Unauthorized\"}" )).hasMessageContaining("Unauthorized");
        assertThat(MCFactilizaClient.mcParse("DNI","12345678",200,"{\"success\":true,\"data\":{\"numero\":\"12345678\",\"ubigeo\":[\"15\",\"1501\"],\"sexo\":null}}").mcData()).containsEntry("data.ubigeo[1]","1501").doesNotContainKey("data.sexo");
        assertThat(MCFactilizaClient.mcParse("RUC - Representantes","20123456789",200,"{\"status\":200,\"data\":[{\"nombre\":\"Ejemplo\"}]}").mcData()).containsEntry("data[0].nombre","Ejemplo");
    }

    @Test void mcVariableApiBindingsRemainIndependentAndTransferWithoutClientData()throws Exception{
        Path mcFirst=mcTemp.resolve("api-source"),mcSecond=mcTemp.resolve("api-target");MCDocumentStore mcStore=new MCDocumentStore(mcFirst);MCStudioData mcData=new MCStudioData(mcFirst);
        MCDefinition mcOriginal=mcStore.mcSaveDefinition(new MCDefinition(0,"nombre","Nombre","Texto","Antiguo"));
        mcData.mcSaveApiBinding(new MCApiBinding(mcOriginal.mcId(),"DNI","data.nombres"));
        MCDefinition mcCopy=mcStore.mcSaveDefinition(new MCDefinition(0,"nombre_copia","Nombre (copia)",mcOriginal.mcType(),""));
        assertThat(mcCopy.mcId()).isNotEqualTo(mcOriginal.mcId());assertThat(mcCopy.mcDefaultValue()).isEmpty();assertThat(mcData.mcApiBinding(mcCopy.mcId())).isNull();
        assertThat(MCVariableInput.mcInitialValue("Fecha","2020-01-01")).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(MCVariableInput.mcValidate("Mixto","DNI 001, A.",Locale.getDefault())).isEqualTo("DNI 001, A.");
        assertThat(MCVariableInput.mcValidate("Número decimal","-1.234,50",Locale.forLanguageTag("es-PE"))).isEqualTo("-1234.50");
        byte[] mcDocx;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("{{nombre}}");mcDocx=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"API",mcDocx,mcDocx));
        assertThat(mcStore.mcDefinitionDependencies(mcOriginal.mcId())).contains("API");
        assertThatThrownBy(()->mcStore.mcDeleteDefinition(mcOriginal.mcId())).hasMessageContaining("API");
        mcData.mcSaveContact(new MCContact(0,"Persona","Ana","","DNI","12345678","","","{}"));
        Path mcPackageFile=mcTemp.resolve("api.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcPackageFile);
        MCDocumentStore mcTarget=new MCDocumentStore(mcSecond);MCStudioData mcImported=new MCStudioData(mcSecond);new MCStudioTransfer(mcTarget,mcImported).mcImportConfiguration(mcPackageFile,MCStudioTransfer.MCConflict.KEEP);
        MCDefinition mcTransferred=mcTarget.mcDefinitions().stream().filter(mcD->mcD.mcKey().equals("nombre")).findFirst().orElseThrow();
        assertThat(mcImported.mcApiBinding(mcTransferred.mcId()).mcPath()).isEqualTo("data.nombres");assertThat(mcImported.mcContacts()).isEmpty();
    }

    @Test void mcSharedDefinitionsBindToTemplatesAndSurviveTransfer()throws Exception{
        Path mcSource=mcTemp.resolve("variables-source"),mcDestination=mcTemp.resolve("variables-destination");
        MCDocumentStore mcStore=new MCDocumentStore(mcSource);MCStudioData mcData=new MCStudioData(mcSource);
        MCDefinition mcDefinition=mcStore.mcSaveDefinition(new MCDefinition(0,"nombre_libre","Nombre","Texto corto",""));
        byte[] mcDocx;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("Hola {{nombre_libre}}");mcDocx=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcFirst=mcStore.mcSaveTemplate(new MCTemplate(0,"Primera",mcDocx,mcDocx));MCTemplate mcSecond=mcStore.mcSaveTemplate(new MCTemplate(0,"Segunda",mcDocx,mcDocx));
        mcStore.mcBindDefinition(mcFirst.mcId(),mcDefinition.mcId(),false,1);mcStore.mcBindDefinition(mcSecond.mcId(),mcDefinition.mcId(),false,1);
        MCKind mcKind=mcStore.mcSaveKind(new MCKind(0,"Saludo","","",mcFirst.mcId(),"nombre_libre",true));
        mcStore.mcSaveKindFieldRules(mcKind.mcId(),Map.of("nombre_libre",true),Map.of("nombre_libre","Valor local"));
        assertThat(mcStore.mcDefinitionDependencies(mcDefinition.mcId())).containsExactly("Primera","Segunda");
        assertThatThrownBy(()->mcStore.mcDeleteDefinition(mcDefinition.mcId())).hasMessageContaining("Primera");
        byte[] mcOutput=MCDocumentEngine.mcGenerate(mcFirst.mcEdited(),Map.of("nombre_libre","Ana"));
        try(XWPFDocument mcGenerated=MCDocumentEngine.mcOpen(mcOutput)){assertThat(mcGenerated.getParagraphs().get(0).getText()).isEqualTo("Hola Ana");}
        Path mcZip=mcTemp.resolve("variables.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcZip);
        MCDocumentStore mcImported=new MCDocumentStore(mcDestination);MCStudioData mcImportedData=new MCStudioData(mcDestination);
        new MCStudioTransfer(mcImported,mcImportedData).mcImportConfiguration(mcZip,MCStudioTransfer.MCConflict.KEEP);
        assertThat(mcImported.mcDefinitions()).hasSize(1);
        assertThat(mcImported.mcDefinitionId(mcImported.mcVariables(mcImported.mcTemplates().get(0).mcId()).get(0).mcId())).isEqualTo(mcImported.mcDefinitions().get(0).mcId());
        assertThat(mcImported.mcKindRequirements(mcImported.mcKinds().get(0).mcId())).containsEntry("nombre_libre",true);
        assertThat(mcImported.mcKindDefaults(mcImported.mcKinds().get(0).mcId())).containsEntry("nombre_libre","Valor local");
        assertThat(new MCDocumentStore(mcDestination).mcDefinitions()).hasSize(1);
    }

    @Test void mcExplicitGroupsSaveAndReuseIndependentContactsWithoutDuplicates()throws Exception{
        Path mcDirectory=mcTemp.resolve("contact-groups");MCDocumentStore mcStore=new MCDocumentStore(mcDirectory);MCStudioData mcData=new MCStudioData(mcDirectory);
        byte[] mcDocx;try(XWPFDocument mcDoc=new XWPFDocument()){mcDoc.createParagraph().createRun().setText("{{nombre_a}} {{nombre_b}} {{empresa}} ");mcDocx=MCDocumentEngine.mcBytes(mcDoc);}
        MCTemplate mcTemplate=mcStore.mcSaveTemplate(new MCTemplate(0,"Grupos",mcDocx,mcDocx));
        List<MCStudioData.MCContactBinding> mcBindings=new ArrayList<>();
        String[][] mcRows={{"a","Persona","given","nombre_a"},{"a","Persona","number","dni_a"},{"a","Persona","address","domicilio_a"},
                {"b","Persona","given","nombre_b"},{"b","Persona","number","dni_b"},{"b","Persona","address","domicilio_b"},
                {"empresa","Empresa","given","empresa"},{"empresa","Empresa","number","ruc"}};
        for(String[] mcRow:mcRows){mcStore.mcSaveVariable(new MCVariable(0,mcTemplate.mcId(),mcRow[3],mcRow[3],mcRow[2].equals("number")?(mcRow[1].equals("Persona")?"DNI":"RUC"):"Texto corto","",false,1));mcBindings.add(mcData.mcSaveContactBinding(new MCStudioData.MCContactBinding(0,mcTemplate.mcId(),mcRow[0],mcRow[1],mcRow[2],mcRow[3])));}
        Map<String,String> mcValues=Map.of("nombre_a","Juan Pérez","dni_a","12345678","domicilio_a","Lima","nombre_b","María Ruiz","dni_b","87654321","domicilio_b","Cusco","empresa","ABC SAC","ruc","20123456789");
        Map<String,String> mcTypes=Map.of("dni_a","DNI","dni_b","DNI","ruc","RUC");
        MCContactReuse.MCResult mcFirst=MCContactReuse.mcSave(mcData,mcBindings,mcValues,mcTypes);
        assertThat(mcFirst.mcCreated()).isEqualTo(3);
        assertThat(MCContactReuse.mcSave(mcData,mcBindings,mcValues,mcTypes).mcCreated()).isZero();
        assertThat(mcData.mcContacts()).hasSize(3);
        MCStudioData.MCContact mcJuan=mcData.mcMatchingContact("Persona","DNI","12345678");
        assertThat(MCContactReuse.mcValuesFor(mcJuan,mcBindings.stream().filter(mcB->mcB.mcGroupKey().equals("a")).toList())).containsEntry("nombre_a","Juan Pérez").containsEntry("domicilio_a","Lima");
        Map<String,String> mcChanged=new HashMap<>(mcValues);mcChanged.put("domicilio_a","Arequipa");
        MCContactReuse.mcSave(mcData,mcBindings,mcChanged,mcTypes);
        assertThat(mcData.mcMatchingContact("Persona","DNI","12345678").mcAddress()).isEqualTo("Arequipa");
        MCContactReuse.mcSave(mcData,mcBindings,mcChanged,mcTypes);
        assertThat(new MCStudioData(mcDirectory).mcMatchingContact("Persona","DNI","12345678").mcAddress()).isEqualTo("Arequipa");
        Map<String,String> mcWithoutDocument=new HashMap<>(mcValues);mcWithoutDocument.put("dni_a","");
        assertThat(MCContactReuse.mcSave(mcData,mcBindings,mcWithoutDocument,mcTypes).mcSkipped()).isEqualTo(1);
        assertThat(mcData.mcContacts()).hasSize(3);
        Path mcConfig=mcTemp.resolve("groups.zip");new MCStudioTransfer(mcStore,mcData).mcExportConfiguration(mcConfig);
        Path mcImportedDirectory=mcTemp.resolve("groups-imported");MCDocumentStore mcImported=new MCDocumentStore(mcImportedDirectory);MCStudioData mcImportedData=new MCStudioData(mcImportedDirectory);
        new MCStudioTransfer(mcImported,mcImportedData).mcImportConfiguration(mcConfig,MCStudioTransfer.MCConflict.KEEP);
        assertThat(mcImportedData.mcContactBindings(mcImported.mcTemplates().get(0).mcId())).hasSize(8);
        assertThat(mcImportedData.mcContacts()).isEmpty();
    }

    @Test void mcMigratesLegacyVariablesWithoutMergingDifferentDefinitions()throws Exception{
        Path mcDirectory=mcTemp.resolve("legacy");Files.createDirectories(mcDirectory);
        try(java.sql.Connection mcC=java.sql.DriverManager.getConnection("jdbc:sqlite:"+mcDirectory.resolve("studio.db"));java.sql.Statement mcS=mcC.createStatement()){
            mcS.executeUpdate("CREATE TABLE template(id INTEGER PRIMARY KEY,name TEXT NOT NULL,original BLOB NOT NULL,edited BLOB NOT NULL)");
            mcS.executeUpdate("CREATE TABLE variable(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,key TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,default_value TEXT,required INTEGER NOT NULL,position INTEGER NOT NULL,UNIQUE(template_id,key))");
            mcS.executeUpdate("INSERT INTO variable(template_id,key,label,type,default_value,required,position) VALUES(1,'dato','Nombre','Texto corto','',1,1),(2,'dato','Monto','Monto','0',0,1)");
        }
        MCDocumentStore mcMigrated=new MCDocumentStore(mcDirectory);
        assertThat(mcMigrated.mcDefinitions()).hasSize(2);
        assertThat(mcMigrated.mcVariables(1)).hasSize(1);
        assertThat(mcMigrated.mcVariables(2)).hasSize(1);
        assertThat(mcMigrated.mcDefinitionId(mcMigrated.mcVariables(1).get(0).mcId())).isNotEqualTo(mcMigrated.mcDefinitionId(mcMigrated.mcVariables(2).get(0).mcId()));
        assertThat(new MCDocumentStore(mcDirectory).mcDefinitions()).hasSize(2);
    }
}
