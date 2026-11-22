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

class MCLocalModulesTest {
    @TempDir Path MCtemp;

    @Test void MCindependentDefinitionsKeepTheirGlobalDefaultAndScopeAcrossMigrationAndZip()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp.resolve("globals"));MCStudioData MCdata=new MCStudioData(MCstore.MCdirectory());String MCannual="Año de la Esperanza y el Fortalecimiento de la Democracia";
        MCDefinition MCglobal=MCstore.MCsaveDefinition(new MCDefinition(0,"Nombre_del_Anio","Nombre del Año","Texto corto",MCannual,false,"dd/MM/yyyy",false));MCDefinition MCname=MCstore.MCsaveDefinition(new MCDefinition(0,"nombre","Nombre","Texto corto",""));
        assertThat(MCglobal.MCbindToClient()).isFalse();assertThat(MCglobal.MCeditorKey("Persona_1")).isEqualTo("Nombre_del_Anio");assertThat(MCglobal.MCinitialValue()).isEqualTo(MCannual);assertThat(MCname.MCbindToClient()).isTrue();assertThat(MCname.MCeditorKey("Persona_1")).isEqualTo("Persona_1.nombre");
        byte[] MCbytes;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("{{Nombre_del_Anio}} {{Persona_1.nombre}}");MCbytes=MCDocumentEngine.MCbytes(MCdoc);}MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Institucional",MCbytes,MCbytes));
        assertThat(MCstore.MCbindDefinition(MCtemplate.MCid(),MCglobal.MCid(),"Persona_1.Nombre_del_Anio",false,1).MCkey()).isEqualTo("Nombre_del_Anio");MCstore.MCbindDefinition(MCtemplate.MCid(),MCname.MCid(),"Persona_1.nombre",false,2);MCstore.MCaddPersona(MCtemplate.MCid(),"Dirigido");
        assertThat(new MCDocumentStore(MCstore.MCdirectory()).MCdefinitions()).contains(MCglobal,MCname);Path MCzip=MCtemp.resolve("globals.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCzip);
        MCDocumentStore MCtarget=new MCDocumentStore(MCtemp.resolve("import"));MCStudioData MCtargetData=new MCStudioData(MCtarget.MCdirectory());new MCStudioTransfer(MCtarget,MCtargetData).MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);assertThat(MCtarget.MCdefinitions().stream().filter(MCd->MCd.MCkey().equals(MCglobal.MCkey())).findFirst().orElseThrow()).satisfies(MCd->{assertThat(MCd.MCbindToClient()).isFalse();assertThat(MCd.MCinitialValue()).isEqualTo(MCannual);});
        new MCStudioTransfer(MCtarget,MCtargetData).MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.REPLACE);assertThat(MCtarget.MCdefinitions()).hasSize(2);assertThat(MCtarget.MCdefinitions().stream().filter(MCd->MCd.MCkey().equals("nombre")).findFirst().orElseThrow().MCbindToClient()).isTrue();
        var MCjson=new com.fasterxml.jackson.databind.ObjectMapper();com.fasterxml.jackson.databind.node.ObjectNode MCprevious;try(ZipInputStream MCinput=new ZipInputStream(Files.newInputStream(MCzip))){MCinput.getNextEntry();MCprevious=(com.fasterxml.jackson.databind.node.ObjectNode)MCjson.readTree(MCinput.readAllBytes());}assertThat(MCprevious.path("version").asInt()).isEqualTo(9);MCprevious.put("version",8);for(var MCdefinition:MCprevious.path("definitions"))((com.fasterxml.jackson.databind.node.ObjectNode)MCdefinition).remove("bindToClient");Path MClegacy=MCtemp.resolve("previous.zip");try(var MCoutput=new java.util.zip.ZipOutputStream(Files.newOutputStream(MClegacy))){MCoutput.putNextEntry(new java.util.zip.ZipEntry("configuration.json"));MCoutput.write(MCjson.writeValueAsBytes(MCprevious));MCoutput.closeEntry();}MCDocumentStore MColdImport=new MCDocumentStore(MCtemp.resolve("previous"));new MCStudioTransfer(MColdImport,new MCStudioData(MColdImport.MCdirectory())).MCimportConfiguration(MClegacy,MCStudioTransfer.MCConflict.KEEP);assertThat(MColdImport.MCdefinitions()).allMatch(MCDefinition::MCbindToClient);
    }

    @Test void MCindependentVariablesAreNeverClientBindingsOrExtrasEvenWithOldPersonPrefixes()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);MCDefinition MCglobal=MCstore.MCsaveDefinition(new MCDefinition(0,"Nombre_del_Anio","Nombre del Año","Texto corto","Valor institucional",false,"dd/MM/yyyy",false));List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();
        List<MCVariable> MCvars=List.of(new MCVariable(0,1,"dni","DNI","DNI","",false,1),new MCVariable(0,1,"nombre","Nombre","Texto corto","",false,2),new MCVariable(0,1,MCglobal.MCkey(),MCglobal.MClabel(),MCglobal.MCtype(),MCglobal.MCdefaultValue(),false,3),new MCVariable(0,1,"Persona_1.Nombre_del_Anio",MCglobal.MClabel(),MCglobal.MCtype(),MCglobal.MCdefaultValue(),false,4));
        List<MCContactBinding> MCold=List.of(new MCContactBinding(0,1,"document:dni","Persona","extra:Nombre_del_Anio",MCglobal.MCkey()),new MCContactBinding(0,1,"document:dni","Persona","extra:Nombre_del_Anio","Persona_1.Nombre_del_Anio"));List<MCContactBinding> MCbindings=MCContactReuse.MCformBindings(1,MCvars,MCold,List.of(),Map.of(),MCdefinitions);assertThat(MCbindings).extracting(MCContactBinding::MCvariableKey).containsExactlyInAnyOrder("dni","nombre");
        Map<String,String> MCvalues=Map.of("dni","12345678","nombre","Ana",MCglobal.MCkey(),MCglobal.MCinitialValue(),"Persona_1.Nombre_del_Anio",MCglobal.MCinitialValue());List<MCContactBinding> MCwithOld=new ArrayList<>(MCbindings);MCwithOld.addAll(MCold);assertThat(MCContactReuse.MCsave(MCdata,MCwithOld,MCvalues,Map.of(),MCdefinitions).MCcreated()).isEqualTo(1);var MCcontact=MCdata.MCcontacts().get(0);assertThat(MCcontact.MCgiven()).isEqualTo("Ana");assertThat(MCcontact.MCextrasJson()).doesNotContain(MCglobal.MCkey(),MCglobal.MCinitialValue(),"Persona_1");assertThat(MCContactReuse.MCsave(MCdata,MCwithOld,MCvalues,Map.of(),MCdefinitions).MCcreated()).isZero();
    }
    @Test void MCqualifiedTokensAcrossRunsHeadersAndTablesGenerateIndependently()throws Exception{
        byte[] MCbytes;try(XWPFDocument MCdoc=new XWPFDocument()){
            var MCp=MCdoc.createParagraph();MCp.createRun().setText("{{Persona_1.");MCp.createRun().setText("DNI}} / {{Persona_2.DNI}} / {{DNI}}");MCdoc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("{{Persona_1.Cargo}}");MCdoc.createTable(1,1).getRow(0).getCell(0).setText("{{Persona_2.Nombres_Com}}");MCbytes=MCDocumentEngine.MCbytes(MCdoc);
        }
        try(XWPFDocument MCsource=MCDocumentEngine.MCopen(MCbytes)){assertThat(MCDocumentEngine.MCmarkers(MCsource)).containsExactlyInAnyOrder("Persona_1.DNI","Persona_2.DNI","DNI","Persona_1.Cargo","Persona_2.Nombres_Com");}
        try(XWPFDocument MCresult=MCDocumentEngine.MCopen(MCDocumentEngine.MCgenerate(MCbytes,Map.of("Persona_1.DNI","11111111","Persona_2.DNI","22222222","DNI","33333333","Persona_1.Cargo","Presidente","Persona_2.Nombres_Com","María Ruiz")))){assertThat(MCresult.getParagraphs().get(0).getText()).isEqualTo("11111111 / 22222222 / 33333333");assertThat(MCresult.getHeaderList().get(0).getText()).contains("Presidente");assertThat(MCresult.getTables().get(0).getText()).contains("María Ruiz");assertThat(MCDocumentEngine.MCmarkers(MCresult)).isEmpty();}
    }
    @Test void MCpersonasAndAutomaticDateDefinitionsPersistAndTravelWithConfiguration()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp.resolve("source"));MCStudioData MCdata=new MCStudioData(MCstore.MCdirectory());byte[] MCbytes;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("{{Persona_1.DNI}} {{Persona_2.DNI}} {{Fecha}}");MCbytes=MCDocumentEngine.MCbytes(MCdoc);}MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Personas",MCbytes,MCbytes));
        MCDefinition MCdni=MCstore.MCsaveDefinition(new MCDefinition(0,"DNI","DNI","Mixto","")),MCdate=MCstore.MCsaveDefinition(new MCDefinition(0,"Fecha","Fecha","Fecha","",true,"d 'de' MMMM 'de' yyyy"));MCstore.MCbindDefinition(MCtemplate.MCid(),MCdni.MCid(),"Persona_1.DNI",false,1);MCstore.MCbindDefinition(MCtemplate.MCid(),MCdni.MCid(),"Persona_2.DNI",false,2);MCstore.MCbindDefinition(MCtemplate.MCid(),MCdate.MCid(),false,3);
        MCPersona MCfirst=MCstore.MCaddPersona(MCtemplate.MCid(),"Solicitante"),MCsecond=MCstore.MCaddPersona(MCtemplate.MCid(),"Arrendador");assertThat(MCfirst.MCpersonaKey()).isEqualTo("Persona_1");assertThat(MCsecond.MCpersonaKey()).isEqualTo("Persona_2");MCstore.MCrenamePersona(MCsecond.MCid(),"Arrendatario");assertThat(MCstore.MCpersonas(MCtemplate.MCid())).extracting(MCPersona::MCdisplayName).containsExactly("Solicitante","Arrendatario");assertThat(new MCDocumentStore(MCstore.MCdirectory()).MCdefinitions().stream().filter(MCd->MCd.MCkey().equals("Fecha")).findFirst().orElseThrow().MCautoCurrentDate()).isTrue();
        Path MCzip=MCtemp.resolve("personas.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCzip);MCDocumentStore MCimported=new MCDocumentStore(MCtemp.resolve("target"));MCimported.MCsaveDefinition(new MCDefinition(0,"otro","Otro","Texto",""));MCStudioTransfer MCtransfer=new MCStudioTransfer(MCimported,new MCStudioData(MCimported.MCdirectory()));MCtransfer.MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);MCtransfer.MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);
        MCTemplate MCcopy=MCimported.MCtemplates().get(0);assertThat(MCimported.MCpersonas(MCcopy.MCid())).extracting(MCPersona::MCdisplayName).containsExactly("Solicitante","Arrendatario");assertThat(MCimported.MCvariables(MCcopy.MCid())).extracting(MCVariable::MCkey).containsExactly("Persona_1.DNI","Persona_2.DNI","Fecha");assertThat(MCimported.MCdefinitionId(MCimported.MCvariables(MCcopy.MCid()).get(0).MCid())).isEqualTo(MCimported.MCdefinitionId(MCimported.MCvariables(MCcopy.MCid()).get(1).MCid()));MCDefinition MCrestored=MCimported.MCdefinitions().stream().filter(MCd->MCd.MCkey().equals("Fecha")).findFirst().orElseThrow();assertThat(MCrestored.MCautoCurrentDate()).isTrue();assertThat(MCrestored.MCdateFormat()).isEqualTo(MCdate.MCdateFormat());assertThat(MCrestored.MCcurrentDate()).isEqualTo(MCdate.MCcurrentDate());assertThat(MCcopy.MCedited()).containsExactly(MCbytes);
        MCimported.MCdeletePersona(MCimported.MCpersonas(MCcopy.MCid()).get(0).MCid());assertThat(MCimported.MCpersonas(MCcopy.MCid())).hasSize(1);MCtransfer.MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.REPLACE);assertThat(MCimported.MCpersonas(MCcopy.MCid())).hasSize(2);MCimported.MCdeleteTemplate(MCcopy.MCid());assertThat(MCimported.MCpersonas(MCcopy.MCid())).isEmpty();
    }
    @Test void MCparticipantContactsAndTheirExtrasNeverMixAndAllowMissingDocuments()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);List<MCVariable> MCvariables=new ArrayList<>();for(String MCkey:List.of("Persona_1.DNI","Persona_1.Nombres_Com","Persona_1.Cargo","Persona_2.DNI","Persona_2.Nombres_Com","Persona_2.Cargo","Persona_3.Nombres_Com","Persona_3.Cargo"))MCvariables.add(new MCVariable(0,1,MCkey,MCDocumentEngine.MCbaseKey(MCkey),"Texto","",false,MCvariables.size()));
        List<MCContactBinding> MCbindings=MCContactReuse.MCformBindings(1,MCvariables,List.of(),List.of(),Map.of());Map<String,String> MCvalues=new LinkedHashMap<>(Map.of("Persona_1.DNI","11111111","Persona_1.Nombres_Com","Ana Pérez","Persona_1.Cargo","Directora","Persona_2.DNI","22222222","Persona_2.Nombres_Com","Luis Ruiz","Persona_2.Cargo","Secretario","Persona_3.Nombres_Com","Luisito","Persona_3.Cargo","Presidente","Fecha","Privado"));
        assertThat(MCContactReuse.MCsave(MCdata,MCbindings,MCvalues,Map.of()).MCcreated()).isEqualTo(3);MCContact MCana=MCdata.MCmatchingContact("Persona","DNI","11111111"),MCluis=MCdata.MCmatchingContact("Persona","DNI","22222222");assertThat(MCana.MCgiven()).isEqualTo("Ana Pérez");assertThat(MCana.MCextrasJson()).contains("Directora").doesNotContain("Luis Ruiz","Secretario","Persona_2","Privado");assertThat(MCluis.MCextrasJson()).contains("Secretario").doesNotContain("Ana Pérez","Directora");assertThat(MCdata.MCfindContacts("Luisito").get(0).MCnumber()).isEmpty();assertThat(MCContactReuse.MCsave(MCdata,MCbindings,MCvalues,Map.of()).MCcreated()).isZero();MCvalues.put("Persona_1.Cargo","Presidenta");MCContactReuse.MCsave(MCdata,MCbindings,MCvalues,Map.of());assertThat(MCdata.MCmatchingContact("Persona","DNI","22222222")).isEqualTo(MCluis);assertThat(MCdata.MCcontacts()).hasSize(3);
    }
    @Test void MClegacyDefinitionsReceiveSafeAutomaticDateDefaults()throws Exception{
        try(var MCc=java.sql.DriverManager.getConnection("jdbc:sqlite:"+MCtemp.resolve("studio.db"));var MCs=MCc.createStatement()){MCs.execute("PRAGMA foreign_keys=ON");MCs.executeUpdate("CREATE TABLE variable_definition(id INTEGER PRIMARY KEY,key TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,default_value TEXT)");MCs.executeUpdate("INSERT INTO variable_definition VALUES(1,'Fecha','Fecha','Fecha','')");}
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);MCDefinition MCdate=MCstore.MCdefinitions().get(0);assertThat(MCdate.MCbindToClient()).isTrue();assertThat(MCdate.MCautoCurrentDate()).isFalse();assertThat(MCdate.MCdateFormat()).isEqualTo("dd/MM/yyyy");MCstore.MCsaveDefinition(new MCDefinition(MCdate.MCid(),MCdate.MCkey(),MCdate.MClabel(),MCdate.MCtype(),"",true,"EEEE, d 'de' MMMM 'de' yyyy"));assertThat(new MCDocumentStore(MCtemp).MCdefinitions().get(0).MCcurrentDate()).isEqualTo(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",Locale.forLanguageTag("es-PE"))));
    }
    @Test void MCcomplementsLearnObjectsAndUndocumentedPeopleInBothDirectionsAndStayLocal()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);
        MCDefinition MCobject=MCstore.MCsaveDefinition(new MCDefinition(0,"objeto","Objeto","Texto","")),MCprice=MCstore.MCsaveDefinition(new MCDefinition(0,"precio","Precio","Texto","")),MCperson=MCstore.MCsaveDefinition(new MCDefinition(0,"Dirigido","Dirigido","Texto","")),MCrole=MCstore.MCsaveDefinition(new MCDefinition(0,"Cargo","Cargo","Texto",""));
        MCstore.MCsaveComplement(MCobject.MCid(),MCprice.MCid(),true);MCstore.MCsaveComplement(MCperson.MCid(),MCrole.MCid(),true);
        assertThat(MCstore.MCcomplements(MCobject.MCid())).extracting(MCComplement::MCtargetDefId).containsExactly(MCprice.MCid());assertThat(MCstore.MCcomplements(MCprice.MCid())).extracting(MCComplement::MCtargetDefId).containsExactly(MCobject.MCid());assertThatThrownBy(()->MCstore.MCsaveComplement(MCobject.MCid(),MCobject.MCid(),true)).isInstanceOf(IllegalArgumentException.class);
        MCdata.MClearnComplements(MCstore.MCdefinitions(),MCstore.MCcomplements(),Map.of("objeto","Silla","precio","120.00","Dirigido","Luisito","Cargo","Presidente de la Asociación"));
        assertThat(MCdata.MCsuggestions("objeto",List.of("precio"),"si",false).get(0).MCcomplements()).containsEntry("precio","120.00");assertThat(MCdata.MCsuggestions("precio",List.of("objeto"),"120.00",true).get(0).MCcomplements()).containsEntry("objeto","Silla");
        assertThat(MCdata.MCsuggestions("dirigido",List.of("cargo"),"LUISITO",true).get(0).MCcomplements()).containsEntry("cargo","Presidente de la Asociación");assertThat(MCdata.MCsuggestions("Cargo",List.of("Dirigido"),"Presidente",false).get(0).MCcomplements()).containsEntry("dirigido","Luisito");
        MCContact MCluis=MCdata.MCcontacts().get(0);assertThat(MCluis.MCnumber()).isEmpty();assertThat(MCluis.MCgiven()).isEqualTo("Luisito");assertThat(MCluis.MCextrasJson()).contains("cargo","Presidente");
        MCdata.MClearnComplements(MCstore.MCdefinitions(),MCstore.MCcomplements(),Map.of("objeto","Silla","precio","130.00","Dirigido","Luisito","Cargo","Secretario"));assertThat(MCdata.MCsuggestions("precio",List.of("objeto"),"120.00",true)).isEmpty();assertThat(MCdata.MCsuggestions("cargo",List.of("dirigido"),"Presidente de la Asociación",true)).isEmpty();assertThat(MCdata.MCcontacts()).hasSize(1);assertThat(MCdata.MCsuggestions("dirigido",List.of("cargo"),"Luisito",true).get(0).MCcomplements()).containsEntry("cargo","Secretario");assertThat(MCdata.MCsuggestions("objeto",List.of("precio"),"Silla",true).get(0).MCcomplements()).containsEntry("precio","130.00");
        new MCStudioData(MCtemp).MCrememberPair("objeto","100% especial","precio","1",false);assertThat(MCdata.MCsuggestions("objeto",List.of("precio"),"100%",false)).hasSize(1);assertThat(MCdata.MCsuggestions("objeto",List.of("precio"),"S_",false)).isEmpty();
        MCstore.MCsaveComplement(MCobject.MCid(),MCprice.MCid(),false);assertThat(MCstore.MCcomplements(MCprice.MCid())).isEmpty();MCstore.MCdeleteDefinition(MCprice.MCid());assertThat(MCstore.MCcomplements(MCobject.MCid())).isEmpty();
    }

    @Test void MCcontactsWithoutDocumentsDeduplicateByNormalizedNameAndSearchAllExtraValues()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);MCStudioData MCdata=new MCStudioData(MCtemp);
        MCContact MCana=MCdata.MCsaveContact(new MCContact(0,"Persona","Ána","Pérez","DNI","","Lima","","{\"Cargo\":\"Presidenta\",\"Asociacion\":\"Los Pinos\"}"));MCContact MCluis=MCdata.MCsaveContact(new MCContact(0,"Persona","Luisito","","DNI","","","","{\"cargo\":\"Secretario\"}"));assertThat(MCdata.MCcontacts()).hasSize(2);
        MCContact MCupdated=MCdata.MCupsertContact(new MCContact(0,"Persona"," ana "," PÉREZ ","","","","","{\"Cargo\":\"Tesorera\"}"));assertThat(MCupdated.MCid()).isEqualTo(MCana.MCid());assertThat(MCdata.MCcontacts()).hasSize(2);assertThat(MCupdated.MCextrasJson()).contains("Los Pinos","Tesorera");assertThat(MCdata.MCfindContacts("ana perez")).extracting(MCContact::MCid).containsExactly(MCana.MCid());assertThat(MCdata.MCfindContacts("LOS PINOS")).extracting(MCContact::MCid).containsExactly(MCana.MCid());assertThat(MCdata.MCfindContactsByVariable("CARGO","secre",true)).extracting(MCContact::MCid).containsExactly(MCluis.MCid());
        MCContact MCdocumented=MCdata.MCsaveContact(new MCContact(0,"Persona","Juan","Pérez","DNI","12345678","Cusco","","{\"otro\":\"conservado\"}"));MCDefinition MCname=MCstore.MCsaveDefinition(new MCDefinition(0,"dirigido","Dirigido","Texto","")),MCrole=MCstore.MCsaveDefinition(new MCDefinition(0,"cargo","Cargo","Texto",""));MCstore.MCsaveComplement(MCname.MCid(),MCrole.MCid(),true);MCdata.MClearnComplements(MCstore.MCdefinitions(),MCstore.MCcomplements(),Map.of("dirigido","Juan Pérez","cargo","Director"));MCContact MCafter=MCdata.MCmatchingContact("Persona","DNI","12345678");assertThat(MCafter.MCid()).isEqualTo(MCdocumented.MCid());assertThat(MCafter.MCaddress()).isEqualTo("Cusco");assertThat(MCafter.MCextrasJson()).contains("conservado","Director");assertThat(MCdata.MCcontacts()).hasSize(3);
    }

    @Test void MCmigratesLegacyUniqueContactIdentityWithoutLosingExistingData()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp);
        try(var MCc=java.sql.DriverManager.getConnection("jdbc:sqlite:"+MCtemp.resolve("studio.db"));var MCs=MCc.createStatement()){
            MCs.execute("PRAGMA foreign_keys=ON");MCs.executeUpdate("CREATE TABLE contact(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}',UNIQUE(kind,document_type,number))");MCs.executeUpdate("INSERT INTO contact(kind,given,document_type,number,extras_json) VALUES('Persona','Luisito','DNI','','{\"cargo\":\"Presidente\"}')");
        }
        MCStudioData MCdata=new MCStudioData(MCtemp);MCdata.MCsaveContact(new MCContact(0,"Persona","María","","DNI","","","","{}"));assertThat(MCdata.MCcontacts()).hasSize(2);assertThat(MCdata.MCfindContacts("Luisito").get(0).MCextrasJson()).contains("Presidente");assertThat(new MCStudioData(MCtemp).MCcontacts()).hasSize(2);
    }

    @Test void MCcomplementDefinitionsAndLinksTravelWithFilteredTemplateConfiguration()throws Exception{
        MCDocumentStore MCstore=new MCDocumentStore(MCtemp.resolve("export"));MCStudioData MCdata=new MCStudioData(MCstore.MCdirectory());MCDefinition MCsource=MCstore.MCsaveDefinition(new MCDefinition(0,"objeto","Objeto","Texto","")),MCtarget=MCstore.MCsaveDefinition(new MCDefinition(0,"precio","Precio","Texto",""));MCstore.MCsaveComplement(MCsource.MCid(),MCtarget.MCid(),true);
        byte[] MCbytes;try(XWPFDocument MCdocument=new XWPFDocument()){MCdocument.createParagraph().createRun().setText("{{objeto}}");MCbytes=MCDocumentEngine.MCbytes(MCdocument);}MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Complementos",MCbytes,MCbytes));MCstore.MCbindDefinition(MCtemplate.MCid(),MCsource.MCid(),false,1);Path MCzip=MCtemp.resolve("complementos.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCzip,List.of(MCtemplate.MCid()));
        MCDocumentStore MCimported=new MCDocumentStore(MCtemp.resolve("import"));MCStudioData MCimportedData=new MCStudioData(MCimported.MCdirectory());MCStudioTransfer MCtransfer=new MCStudioTransfer(MCimported,MCimportedData);MCtransfer.MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);MCtransfer.MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);assertThat(MCimported.MCdefinitions()).hasSize(2);assertThat(MCimported.MCcomplements()).hasSize(1);MCDefinition MCprice=MCimported.MCdefinitions().stream().filter(MCd->MCd.MCkey().equals("precio")).findFirst().orElseThrow();assertThat(MCimported.MCcomplements(MCprice.MCid())).hasSize(1);assertThat(MCimported.MCtemplates().get(0).MCedited()).containsExactly(MCbytes);
        MCdata.MCsaveContact(new MCContact(0,"Persona","Luisito","","","","","","{\"cargo\":\"Presidente\"}"));MCdata.MCsaveContact(new MCContact(0,"Persona","María","","","","","","{}"));Path MCclients=MCtemp.resolve("personas.zip");new MCStudioTransfer(MCstore,MCdata).MCexportClients(MCclients);MCtransfer.MCimportConfiguration(MCclients,MCStudioTransfer.MCConflict.KEEP);MCtransfer.MCimportConfiguration(MCclients,MCStudioTransfer.MCConflict.KEEP);assertThat(MCimportedData.MCcontacts()).hasSize(2);assertThat(MCimportedData.MCfindContacts("Presidente")).hasSize(1);
    }

    @Test void MCtemplateCatalogGroupsFiltersAndEditorSaveRemainIndependent()throws Exception{
        Path MCdirectory=MCtemp.resolve("catalog");MCDocumentStore MCstore=new MCDocumentStore(MCdirectory);MCStudioData MCdata=new MCStudioData(MCdirectory);
        byte[] MCdocx;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("Plantilla");MCdocx=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCfirst=MCstore.MCsaveTemplate(new MCTemplate(0,"Solicitud",MCdocx,MCdocx));MCdata.MCrecordRevision(MCfirst.MCid(),MCdocx);
        MCTemplateGroup MCsolicitudes=MCstore.MCsaveGroup(0,"Solicitudes");
        assertThat(MCstore.MCtemplates()).hasSize(1);assertThat(MCstore.MCgroupIds(MCfirst.MCid())).isEmpty();assertThat(MCstore.MCgroups().get(0).MCcount()).isZero();
        for(long MCselected:List.of(-1L,0L,MCsolicitudes.MCid()))assertThat(MCstore.MCtemplates().stream().filter(MCt->MCStudioWindow.MClibraryMatches(MCt.MCname(),"")).map(MCTemplate::MCid).toList()).containsExactly(MCfirst.MCid());
        assertThat(MCStudioWindow.MClibraryHighlighted(MCstore.MCgroupIds(MCfirst.MCid()),-1)).isFalse();
        assertThat(MCStudioWindow.MClibraryHighlighted(MCstore.MCgroupIds(MCfirst.MCid()),0)).isFalse();
        assertThat(MCStudioWindow.MClibraryHighlighted(MCstore.MCgroupIds(MCfirst.MCid()),MCsolicitudes.MCid())).isFalse();
        MCstore.MCsetTemplateGroups(MCfirst.MCid(),List.of(MCsolicitudes.MCid()));
        assertThat(MCstore.MCgroups().get(0).MCcount()).isEqualTo(1);assertThat(MCstore.MCtemplates().stream().map(MCTemplate::MCid).toList()).containsExactly(MCfirst.MCid());assertThat(MCStudioWindow.MClibraryHighlighted(MCstore.MCgroupIds(MCfirst.MCid()),MCsolicitudes.MCid())).isTrue();
        MCTemplate MCother=MCstore.MCsaveTemplate(new MCTemplate(0,"Carta",MCdocx,MCdocx));
        assertThat(MCstore.MCtemplates().stream().filter(MCt->MCStudioWindow.MClibraryMatches(MCt.MCname(),"")).map(MCTemplate::MCid).toList()).containsExactlyInAnyOrder(MCfirst.MCid(),MCother.MCid());
        assertThat(MCStudioWindow.MClibraryHighlighted(MCstore.MCgroupIds(MCother.MCid()),MCsolicitudes.MCid())).isFalse();
        assertThat(MCstore.MCtemplates().stream().filter(MCt->MCStudioWindow.MClibraryMatches(MCt.MCname(),"sol")).map(MCTemplate::MCid).toList()).containsExactly(MCfirst.MCid());
        MCTemplateGroup MCfrecuentes=MCstore.MCsaveGroup(0,"Frecuentes");MCstore.MCsetTemplateGroups(MCfirst.MCid(),List.of(MCsolicitudes.MCid(),MCfrecuentes.MCid()));
        assertThat(MCstore.MCtemplates()).hasSize(2);assertThat(MCstore.MCgroupIds(MCfirst.MCid())).containsExactlyInAnyOrder(MCsolicitudes.MCid(),MCfrecuentes.MCid());
        MCstore.MCsaveTemplate(new MCTemplate(MCfirst.MCid(),"Solicitud editada",MCfirst.MCoriginal(),MCfirst.MCedited()));assertThat(MCstore.MCgroupIds(MCfirst.MCid())).hasSize(2);
        MCTemplate MCcopy=MCstore.MCsaveTemplate(new MCTemplate(0,"Solicitud (copia)",MCfirst.MCoriginal(),MCfirst.MCedited()));assertThat(MCstore.MCgroupIds(MCcopy.MCid())).isEmpty();
        MCstore.MCdeleteGroup(MCsolicitudes.MCid(),null);assertThat(MCstore.MCtemplates()).hasSize(3);assertThat(MCstore.MCgroupIds(MCfirst.MCid())).containsExactly(MCfrecuentes.MCid());
        MCDocumentStore MCreopened=new MCDocumentStore(MCdirectory);assertThat(MCreopened.MCgroupIds(MCfirst.MCid())).containsExactly(MCfrecuentes.MCid());assertThat(MCreopened.MCgroupIds(MCcopy.MCid())).isEmpty();assertThat(new MCStudioData(MCdirectory).MCrevisionDates()).containsKey(MCfirst.MCid());
    }

    @Test void MCtransferBackupAndReopen()throws Exception{
        Path MCfirst=MCtemp.resolve("first"),MCsecond=MCtemp.resolve("second");MCDocumentStore MCa=new MCDocumentStore(MCfirst);MCStudioData MCextra=new MCStudioData(MCfirst);
        byte[] MCdocx;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("Hola {{nombre}}");MCdocx=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCtemplate=MCa.MCsaveTemplate(new MCTemplate(0,"Saludo",MCdocx,MCdocx));MCextra.MCrecordRevision(MCtemplate.MCid(),MCdocx);
        MCa.MCsaveVariable(new MCVariable(0,MCtemplate.MCid(),"nombre","Nombre","Texto corto","",true,1));
        MCa.MCsaveKind(new MCKind(0,"Carta","","",MCtemplate.MCid(),"nombre",true));
        MCextra.MCsaveMapping(new MCMapping(0,MCtemplate.MCid(),"nombre","nombres","nombre"));
        MCextra.MCsaveContact(new MCContact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{}"));
        MCextra.MCsaveHistory(new MCHistory(0,"Carta",MCtemplate.MCid(),1,null,"{\"nombre\":\"Ana\"}",null,null,MCDocumentEngine.MCgenerate(MCdocx,Map.of("nombre","Ana")),null));
        MCLocalSecret MCsecret=new MCLocalSecret(MCfirst);MCsecret.MCsave("token-privado");assertThat(MCsecret.MCload()).isEqualTo("token-privado");
        Path MCconfig=MCtemp.resolve("config.zip");MCStudioTransfer MCtransfer=new MCStudioTransfer(MCa,MCextra);MCtransfer.MCexportConfiguration(MCconfig);
        try(ZipInputStream MCzip=new ZipInputStream(Files.newInputStream(MCconfig))){assertThat(MCzip.getNextEntry().getName()).isEqualTo("configuration.json");assertThat(new String(MCzip.readAllBytes())).doesNotContain("token-privado","12345678");}
        MCDocumentStore MCb=new MCDocumentStore(MCsecond);MCStudioData MCbData=new MCStudioData(MCsecond);
        MCStudioTransfer.MCImportResult MCimported=new MCStudioTransfer(MCb,MCbData).MCimportConfiguration(MCconfig,MCStudioTransfer.MCConflict.KEEP);
        assertThat(MCimported.MCtemplates()).isEqualTo(1);assertThat(MCb.MCtemplates()).hasSize(1);assertThat(MCb.MCvariables(MCb.MCtemplates().get(0).MCid())).hasSize(1);assertThat(MCb.MCkinds()).hasSize(1);assertThat(MCbData.MCmappings(MCb.MCtemplates().get(0).MCid())).hasSize(1);assertThat(MCbData.MCcontacts()).isEmpty();assertThat(MCbData.MChistory()).isEmpty();
        assertThat(new MCStudioTransfer(MCb,MCbData).MCimportConfiguration(MCconfig,MCStudioTransfer.MCConflict.KEEP).MCconflicts()).isGreaterThan(0);
        Path MCbackup=MCtemp.resolve("backup.dsbk");MCtransfer.MCbackup(MCbackup,"contrasena-segura".toCharArray());
        assertThat(new String(Files.readAllBytes(MCbackup),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("12345678");
        new MCStudioTransfer(MCb,MCbData).MCrestore(MCbackup,"contrasena-segura".toCharArray());
        MCStudioData MCreopened=new MCStudioData(MCsecond);assertThat(MCreopened.MCcontacts()).hasSize(1);assertThat(MCreopened.MChistory()).hasSize(1);
        assertThat(new MCLocalSecret(MCsecond).MCconfigured()).isFalse();
    }

    @Test void MCfactilizaParsesDocumentedResponsesAndRejectsIncompleteData()throws Exception{
        String MCdni="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"12345678\",\"nombres\":\"ANA\",\"apellido_paterno\":\"PEREZ\",\"direccion_completa\":\"LIMA\"}}";
        String MCruc="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"20123456789\",\"nombre_o_razon_social\":\"EMPRESA\",\"direccion\":\"LIMA\"}}";
        assertThat(MCFactilizaClient.MCparse("DNI","12345678",200,MCdni).MCdata()).containsEntry("nombres","ANA");
        assertThat(MCFactilizaClient.MCparse("RUC","20123456789",200,MCruc).MCdata()).containsEntry("nombre_o_razon_social","EMPRESA");
        assertThatThrownBy(()->MCFactilizaClient.MCparse("DNI","12345678",200,"{\"success\":true,\"data\":{}}" )).hasMessageContaining("incompletos");
        assertThatThrownBy(()->MCFactilizaClient.MCparse("RUC","20123456789",401,"{\"success\":false,\"message\":\"Unauthorized\"}" )).hasMessageContaining("Unauthorized");
        assertThat(MCFactilizaClient.MCparse("DNI","12345678",200,"{\"success\":true,\"data\":{\"numero\":\"12345678\",\"ubigeo\":[\"15\",\"1501\"],\"sexo\":null}}").MCdata()).containsEntry("data.ubigeo[1]","1501").doesNotContainKey("data.sexo");
        assertThat(MCFactilizaClient.MCparse("RUC - Representantes","20123456789",200,"{\"status\":200,\"data\":[{\"nombre\":\"Ejemplo\"}]}").MCdata()).containsEntry("data[0].nombre","Ejemplo");
    }

    @Test void MCvariableApiBindingsRemainIndependentAndTransferWithoutClientData()throws Exception{
        Path MCfirst=MCtemp.resolve("api-source"),MCsecond=MCtemp.resolve("api-target");MCDocumentStore MCstore=new MCDocumentStore(MCfirst);MCStudioData MCdata=new MCStudioData(MCfirst);
        MCDefinition MCoriginal=MCstore.MCsaveDefinition(new MCDefinition(0,"nombre","Nombre","Texto","Antiguo"));
        MCdata.MCsaveApiBinding(new MCApiBinding(MCoriginal.MCid(),"DNI","data.nombres"));
        MCDefinition MCcopy=MCstore.MCsaveDefinition(new MCDefinition(0,"nombre_copia","Nombre (copia)",MCoriginal.MCtype(),""));
        assertThat(MCcopy.MCid()).isNotEqualTo(MCoriginal.MCid());assertThat(MCcopy.MCdefaultValue()).isEmpty();assertThat(MCdata.MCapiBinding(MCcopy.MCid())).isNull();
        assertThat(MCVariableInput.MCinitialValue("Fecha","2020-01-01")).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(MCVariableInput.MCvalidate("Mixto","DNI 001, A.",Locale.getDefault())).isEqualTo("DNI 001, A.");
        assertThat(MCVariableInput.MCvalidate("Número decimal","-1.234,50",Locale.forLanguageTag("es-PE"))).isEqualTo("-1234.50");
        byte[] MCdocx;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("{{nombre}}");MCdocx=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"API",MCdocx,MCdocx));
        assertThat(MCstore.MCdefinitionDependencies(MCoriginal.MCid())).contains("API");
        assertThatThrownBy(()->MCstore.MCdeleteDefinition(MCoriginal.MCid())).hasMessageContaining("API");
        MCdata.MCsaveContact(new MCContact(0,"Persona","Ana","","DNI","12345678","","","{}"));
        Path MCpackageFile=MCtemp.resolve("api.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCpackageFile);
        MCDocumentStore MCtarget=new MCDocumentStore(MCsecond);MCStudioData MCimported=new MCStudioData(MCsecond);new MCStudioTransfer(MCtarget,MCimported).MCimportConfiguration(MCpackageFile,MCStudioTransfer.MCConflict.KEEP);
        MCDefinition MCtransferred=MCtarget.MCdefinitions().stream().filter(MCd->MCd.MCkey().equals("nombre")).findFirst().orElseThrow();
        assertThat(MCimported.MCapiBinding(MCtransferred.MCid()).MCpath()).isEqualTo("data.nombres");assertThat(MCimported.MCcontacts()).isEmpty();
    }

    @Test void MCsharedDefinitionsBindToTemplatesAndSurviveTransfer()throws Exception{
        Path MCsource=MCtemp.resolve("variables-source"),MCdestination=MCtemp.resolve("variables-destination");
        MCDocumentStore MCstore=new MCDocumentStore(MCsource);MCStudioData MCdata=new MCStudioData(MCsource);
        MCDefinition MCdefinition=MCstore.MCsaveDefinition(new MCDefinition(0,"nombre_libre","Nombre","Texto corto",""));
        byte[] MCdocx;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("Hola {{nombre_libre}}");MCdocx=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCfirst=MCstore.MCsaveTemplate(new MCTemplate(0,"Primera",MCdocx,MCdocx));MCTemplate MCsecond=MCstore.MCsaveTemplate(new MCTemplate(0,"Segunda",MCdocx,MCdocx));
        MCstore.MCbindDefinition(MCfirst.MCid(),MCdefinition.MCid(),false,1);MCstore.MCbindDefinition(MCsecond.MCid(),MCdefinition.MCid(),false,1);
        MCKind MCkind=MCstore.MCsaveKind(new MCKind(0,"Saludo","","",MCfirst.MCid(),"nombre_libre",true));
        MCstore.MCsaveKindFieldRules(MCkind.MCid(),Map.of("nombre_libre",true),Map.of("nombre_libre","Valor local"));
        assertThat(MCstore.MCdefinitionDependencies(MCdefinition.MCid())).containsExactly("Primera","Segunda");
        assertThatThrownBy(()->MCstore.MCdeleteDefinition(MCdefinition.MCid())).hasMessageContaining("Primera");
        byte[] MCoutput=MCDocumentEngine.MCgenerate(MCfirst.MCedited(),Map.of("nombre_libre","Ana"));
        try(XWPFDocument MCgenerated=MCDocumentEngine.MCopen(MCoutput)){assertThat(MCgenerated.getParagraphs().get(0).getText()).isEqualTo("Hola Ana");}
        Path MCzip=MCtemp.resolve("variables.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCzip);
        MCDocumentStore MCimported=new MCDocumentStore(MCdestination);MCStudioData MCimportedData=new MCStudioData(MCdestination);
        new MCStudioTransfer(MCimported,MCimportedData).MCimportConfiguration(MCzip,MCStudioTransfer.MCConflict.KEEP);
        assertThat(MCimported.MCdefinitions()).hasSize(1);
        assertThat(MCimported.MCdefinitionId(MCimported.MCvariables(MCimported.MCtemplates().get(0).MCid()).get(0).MCid())).isEqualTo(MCimported.MCdefinitions().get(0).MCid());
        assertThat(MCimported.MCkindRequirements(MCimported.MCkinds().get(0).MCid())).containsEntry("nombre_libre",true);
        assertThat(MCimported.MCkindDefaults(MCimported.MCkinds().get(0).MCid())).containsEntry("nombre_libre","Valor local");
        assertThat(new MCDocumentStore(MCdestination).MCdefinitions()).hasSize(1);
    }

    @Test void MCexplicitGroupsSaveAndReuseIndependentContactsWithoutDuplicates()throws Exception{
        Path MCdirectory=MCtemp.resolve("contact-groups");MCDocumentStore MCstore=new MCDocumentStore(MCdirectory);MCStudioData MCdata=new MCStudioData(MCdirectory);
        byte[] MCdocx;try(XWPFDocument MCdoc=new XWPFDocument()){MCdoc.createParagraph().createRun().setText("{{nombre_a}} {{nombre_b}} {{empresa}} ");MCdocx=MCDocumentEngine.MCbytes(MCdoc);}
        MCTemplate MCtemplate=MCstore.MCsaveTemplate(new MCTemplate(0,"Grupos",MCdocx,MCdocx));
        List<MCStudioData.MCContactBinding> MCbindings=new ArrayList<>();
        String[][] MCrows={{"a","Persona","given","nombre_a"},{"a","Persona","number","dni_a"},{"a","Persona","address","domicilio_a"},
                {"b","Persona","given","nombre_b"},{"b","Persona","number","dni_b"},{"b","Persona","address","domicilio_b"},
                {"empresa","Empresa","given","empresa"},{"empresa","Empresa","number","ruc"}};
        for(String[] MCrow:MCrows){MCstore.MCsaveVariable(new MCVariable(0,MCtemplate.MCid(),MCrow[3],MCrow[3],MCrow[2].equals("number")?(MCrow[1].equals("Persona")?"DNI":"RUC"):"Texto corto","",false,1));MCbindings.add(MCdata.MCsaveContactBinding(new MCStudioData.MCContactBinding(0,MCtemplate.MCid(),MCrow[0],MCrow[1],MCrow[2],MCrow[3])));}
        Map<String,String> MCvalues=Map.of("nombre_a","Juan Pérez","dni_a","12345678","domicilio_a","Lima","nombre_b","María Ruiz","dni_b","87654321","domicilio_b","Cusco","empresa","ABC SAC","ruc","20123456789");
        Map<String,String> MCtypes=Map.of("dni_a","DNI","dni_b","DNI","ruc","RUC");
        MCContactReuse.MCResult MCfirst=MCContactReuse.MCsave(MCdata,MCbindings,MCvalues,MCtypes);
        assertThat(MCfirst.MCcreated()).isEqualTo(3);
        assertThat(MCContactReuse.MCsave(MCdata,MCbindings,MCvalues,MCtypes).MCcreated()).isZero();
        assertThat(MCdata.MCcontacts()).hasSize(3);
        MCStudioData.MCContact MCjuan=MCdata.MCmatchingContact("Persona","DNI","12345678");
        assertThat(MCContactReuse.MCvaluesFor(MCjuan,MCbindings.stream().filter(MCb->MCb.MCgroupKey().equals("a")).toList())).containsEntry("nombre_a","Juan Pérez").containsEntry("domicilio_a","Lima");
        Map<String,String> MCchanged=new HashMap<>(MCvalues);MCchanged.put("domicilio_a","Arequipa");
        MCContactReuse.MCsave(MCdata,MCbindings,MCchanged,MCtypes);
        assertThat(MCdata.MCmatchingContact("Persona","DNI","12345678").MCaddress()).isEqualTo("Arequipa");
        MCContactReuse.MCsave(MCdata,MCbindings,MCchanged,MCtypes);
        assertThat(new MCStudioData(MCdirectory).MCmatchingContact("Persona","DNI","12345678").MCaddress()).isEqualTo("Arequipa");
        Map<String,String> MCwithoutDocument=new HashMap<>(MCvalues);MCwithoutDocument.put("dni_a","");
        assertThat(MCContactReuse.MCsave(MCdata,MCbindings,MCwithoutDocument,MCtypes).MCskipped()).isEqualTo(1);
        assertThat(MCdata.MCcontacts()).hasSize(3);
        Path MCconfig=MCtemp.resolve("groups.zip");new MCStudioTransfer(MCstore,MCdata).MCexportConfiguration(MCconfig);
        Path MCimportedDirectory=MCtemp.resolve("groups-imported");MCDocumentStore MCimported=new MCDocumentStore(MCimportedDirectory);MCStudioData MCimportedData=new MCStudioData(MCimportedDirectory);
        new MCStudioTransfer(MCimported,MCimportedData).MCimportConfiguration(MCconfig,MCStudioTransfer.MCConflict.KEEP);
        assertThat(MCimportedData.MCcontactBindings(MCimported.MCtemplates().get(0).MCid())).hasSize(8);
        assertThat(MCimportedData.MCcontacts()).isEmpty();
    }

    @Test void MCmigratesLegacyVariablesWithoutMergingDifferentDefinitions()throws Exception{
        Path MCdirectory=MCtemp.resolve("legacy");Files.createDirectories(MCdirectory);
        try(java.sql.Connection MCc=java.sql.DriverManager.getConnection("jdbc:sqlite:"+MCdirectory.resolve("studio.db"));java.sql.Statement MCs=MCc.createStatement()){
            MCs.executeUpdate("CREATE TABLE template(id INTEGER PRIMARY KEY,name TEXT NOT NULL,original BLOB NOT NULL,edited BLOB NOT NULL)");
            MCs.executeUpdate("CREATE TABLE variable(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,key TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,default_value TEXT,required INTEGER NOT NULL,position INTEGER NOT NULL,UNIQUE(template_id,key))");
            MCs.executeUpdate("INSERT INTO variable(template_id,key,label,type,default_value,required,position) VALUES(1,'dato','Nombre','Texto corto','',1,1),(2,'dato','Monto','Monto','0',0,1)");
        }
        MCDocumentStore MCmigrated=new MCDocumentStore(MCdirectory);
        assertThat(MCmigrated.MCdefinitions()).hasSize(2);
        assertThat(MCmigrated.MCvariables(1)).hasSize(1);
        assertThat(MCmigrated.MCvariables(2)).hasSize(1);
        assertThat(MCmigrated.MCdefinitionId(MCmigrated.MCvariables(1).get(0).MCid())).isNotEqualTo(MCmigrated.MCdefinitionId(MCmigrated.MCvariables(2).get(0).MCid()));
        assertThat(new MCDocumentStore(MCdirectory).MCdefinitions()).hasSize(2);
    }
}
