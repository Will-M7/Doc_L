package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;

class LocalModulesTest {
    @TempDir Path temp;

    @Test void independentDefinitionsKeepTheirGlobalDefaultAndScopeAcrossMigrationAndZip()throws Exception{
        DocumentStore store=new DocumentStore(temp.resolve("globals"));StudioData data=new StudioData(store.directory());String annual="Año de la Esperanza y el Fortalecimiento de la Democracia";
        Definition global=store.saveDefinition(new Definition(0,"Nombre_del_Anio","Nombre del Año","Texto corto",annual,false,"dd/MM/yyyy",false));Definition name=store.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto",""));
        assertThat(global.bindToClient()).isFalse();assertThat(global.editorKey("Persona_1")).isEqualTo("Nombre_del_Anio");assertThat(global.initialValue()).isEqualTo(annual);assertThat(name.bindToClient()).isTrue();assertThat(name.editorKey("Persona_1")).isEqualTo("Persona_1.nombre");
        byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{Nombre_del_Anio}} {{Persona_1.nombre}}");bytes=DocumentEngine.bytes(doc);}Template template=store.saveTemplate(new Template(0,"Institucional",bytes,bytes));
        assertThat(store.bindDefinition(template.id(),global.id(),"Persona_1.Nombre_del_Anio",false,1).key()).isEqualTo("Nombre_del_Anio");store.bindDefinition(template.id(),name.id(),"Persona_1.nombre",false,2);store.addPersona(template.id(),"Dirigido");
        assertThat(new DocumentStore(store.directory()).definitions()).contains(global,name);Path zip=temp.resolve("globals.zip");new StudioTransfer(store,data).exportConfiguration(zip);
        DocumentStore target=new DocumentStore(temp.resolve("import"));StudioData targetData=new StudioData(target.directory());new StudioTransfer(target,targetData).importConfiguration(zip,StudioTransfer.Conflict.KEEP);assertThat(target.definitions().stream().filter(d->d.key().equals(global.key())).findFirst().orElseThrow()).satisfies(d->{assertThat(d.bindToClient()).isFalse();assertThat(d.initialValue()).isEqualTo(annual);});
        new StudioTransfer(target,targetData).importConfiguration(zip,StudioTransfer.Conflict.REPLACE);assertThat(target.definitions()).hasSize(2);assertThat(target.definitions().stream().filter(d->d.key().equals("nombre")).findFirst().orElseThrow().bindToClient()).isTrue();
        var json=new com.fasterxml.jackson.databind.ObjectMapper();com.fasterxml.jackson.databind.node.ObjectNode previous;try(ZipInputStream input=new ZipInputStream(Files.newInputStream(zip))){input.getNextEntry();previous=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(input.readAllBytes());}assertThat(previous.path("version").asInt()).isEqualTo(9);previous.put("version",8);for(var definition:previous.path("definitions"))((com.fasterxml.jackson.databind.node.ObjectNode)definition).remove("bindToClient");Path legacy=temp.resolve("previous.zip");try(var output=new java.util.zip.ZipOutputStream(Files.newOutputStream(legacy))){output.putNextEntry(new java.util.zip.ZipEntry("configuration.json"));output.write(json.writeValueAsBytes(previous));output.closeEntry();}DocumentStore oldImport=new DocumentStore(temp.resolve("previous"));new StudioTransfer(oldImport,new StudioData(oldImport.directory())).importConfiguration(legacy,StudioTransfer.Conflict.KEEP);assertThat(oldImport.definitions()).allMatch(Definition::bindToClient);
    }

    @Test void independentVariablesAreNeverClientBindingsOrExtrasEvenWithOldPersonPrefixes()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Definition global=store.saveDefinition(new Definition(0,"Nombre_del_Anio","Nombre del Año","Texto corto","Valor institucional",false,"dd/MM/yyyy",false));List<Definition> definitions=store.definitions();
        List<Variable> vars=List.of(new Variable(0,1,"dni","DNI","DNI","",false,1),new Variable(0,1,"nombre","Nombre","Texto corto","",false,2),new Variable(0,1,global.key(),global.label(),global.type(),global.defaultValue(),false,3),new Variable(0,1,"Persona_1.Nombre_del_Anio",global.label(),global.type(),global.defaultValue(),false,4));
        List<ContactBinding> old=List.of(new ContactBinding(0,1,"document:dni","Persona","extra:Nombre_del_Anio",global.key()),new ContactBinding(0,1,"document:dni","Persona","extra:Nombre_del_Anio","Persona_1.Nombre_del_Anio"));List<ContactBinding> bindings=ContactReuse.formBindings(1,vars,old,List.of(),Map.of(),definitions);assertThat(bindings).extracting(ContactBinding::variableKey).containsExactlyInAnyOrder("dni","nombre");
        Map<String,String> values=Map.of("dni","12345678","nombre","Ana",global.key(),global.initialValue(),"Persona_1.Nombre_del_Anio",global.initialValue());List<ContactBinding> withOld=new ArrayList<>(bindings);withOld.addAll(old);assertThat(ContactReuse.save(data,withOld,values,Map.of(),definitions).created()).isEqualTo(1);var contact=data.contacts().get(0);assertThat(contact.given()).isEqualTo("Ana");assertThat(contact.extrasJson()).doesNotContain(global.key(),global.initialValue(),"Persona_1");assertThat(ContactReuse.save(data,withOld,values,Map.of(),definitions).created()).isZero();
    }
    @Test void qualifiedTokensAcrossRunsHeadersAndTablesGenerateIndependently()throws Exception{
        byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){
            var p=doc.createParagraph();p.createRun().setText("{{Persona_1.");p.createRun().setText("DNI}} / {{Persona_2.DNI}} / {{DNI}}");doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("{{Persona_1.Cargo}}");doc.createTable(1,1).getRow(0).getCell(0).setText("{{Persona_2.Nombres_Com}}");bytes=DocumentEngine.bytes(doc);
        }
        try(XWPFDocument source=DocumentEngine.open(bytes)){assertThat(DocumentEngine.markers(source)).containsExactlyInAnyOrder("Persona_1.DNI","Persona_2.DNI","DNI","Persona_1.Cargo","Persona_2.Nombres_Com");}
        try(XWPFDocument result=DocumentEngine.open(DocumentEngine.generate(bytes,Map.of("Persona_1.DNI","11111111","Persona_2.DNI","22222222","DNI","33333333","Persona_1.Cargo","Presidente","Persona_2.Nombres_Com","María Ruiz")))){assertThat(result.getParagraphs().get(0).getText()).isEqualTo("11111111 / 22222222 / 33333333");assertThat(result.getHeaderList().get(0).getText()).contains("Presidente");assertThat(result.getTables().get(0).getText()).contains("María Ruiz");assertThat(DocumentEngine.markers(result)).isEmpty();}
    }
    @Test void personasAndAutomaticDateDefinitionsPersistAndTravelWithConfiguration()throws Exception{
        DocumentStore store=new DocumentStore(temp.resolve("source"));StudioData data=new StudioData(store.directory());byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{Persona_1.DNI}} {{Persona_2.DNI}} {{Fecha}}");bytes=DocumentEngine.bytes(doc);}Template template=store.saveTemplate(new Template(0,"Personas",bytes,bytes));
        Definition dni=store.saveDefinition(new Definition(0,"DNI","DNI","Mixto","")),date=store.saveDefinition(new Definition(0,"Fecha","Fecha","Fecha","",true,"d 'de' MMMM 'de' yyyy"));store.bindDefinition(template.id(),dni.id(),"Persona_1.DNI",false,1);store.bindDefinition(template.id(),dni.id(),"Persona_2.DNI",false,2);store.bindDefinition(template.id(),date.id(),false,3);
        Persona first=store.addPersona(template.id(),"Solicitante"),second=store.addPersona(template.id(),"Arrendador");assertThat(first.personaKey()).isEqualTo("Persona_1");assertThat(second.personaKey()).isEqualTo("Persona_2");store.renamePersona(second.id(),"Arrendatario");assertThat(store.personas(template.id())).extracting(Persona::displayName).containsExactly("Solicitante","Arrendatario");assertThat(new DocumentStore(store.directory()).definitions().stream().filter(d->d.key().equals("Fecha")).findFirst().orElseThrow().autoCurrentDate()).isTrue();
        Path zip=temp.resolve("personas.zip");new StudioTransfer(store,data).exportConfiguration(zip);DocumentStore imported=new DocumentStore(temp.resolve("target"));imported.saveDefinition(new Definition(0,"otro","Otro","Texto",""));StudioTransfer transfer=new StudioTransfer(imported,new StudioData(imported.directory()));transfer.importConfiguration(zip,StudioTransfer.Conflict.KEEP);transfer.importConfiguration(zip,StudioTransfer.Conflict.KEEP);
        Template copy=imported.templates().get(0);assertThat(imported.personas(copy.id())).extracting(Persona::displayName).containsExactly("Solicitante","Arrendatario");assertThat(imported.variables(copy.id())).extracting(Variable::key).containsExactly("Persona_1.DNI","Persona_2.DNI","Fecha");assertThat(imported.definitionId(imported.variables(copy.id()).get(0).id())).isEqualTo(imported.definitionId(imported.variables(copy.id()).get(1).id()));Definition restored=imported.definitions().stream().filter(d->d.key().equals("Fecha")).findFirst().orElseThrow();assertThat(restored.autoCurrentDate()).isTrue();assertThat(restored.dateFormat()).isEqualTo(date.dateFormat());assertThat(restored.currentDate()).isEqualTo(date.currentDate());assertThat(copy.edited()).containsExactly(bytes);
        imported.deletePersona(imported.personas(copy.id()).get(0).id());assertThat(imported.personas(copy.id())).hasSize(1);transfer.importConfiguration(zip,StudioTransfer.Conflict.REPLACE);assertThat(imported.personas(copy.id())).hasSize(2);imported.deleteTemplate(copy.id());assertThat(imported.personas(copy.id())).isEmpty();
    }
    @Test void participantContactsAndTheirExtrasNeverMixAndAllowMissingDocuments()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);List<Variable> variables=new ArrayList<>();for(String key:List.of("Persona_1.DNI","Persona_1.Nombres_Com","Persona_1.Cargo","Persona_2.DNI","Persona_2.Nombres_Com","Persona_2.Cargo","Persona_3.Nombres_Com","Persona_3.Cargo"))variables.add(new Variable(0,1,key,DocumentEngine.baseKey(key),"Texto","",false,variables.size()));
        List<ContactBinding> bindings=ContactReuse.formBindings(1,variables,List.of(),List.of(),Map.of());Map<String,String> values=new LinkedHashMap<>(Map.of("Persona_1.DNI","11111111","Persona_1.Nombres_Com","Ana Pérez","Persona_1.Cargo","Directora","Persona_2.DNI","22222222","Persona_2.Nombres_Com","Luis Ruiz","Persona_2.Cargo","Secretario","Persona_3.Nombres_Com","Luisito","Persona_3.Cargo","Presidente","Fecha","Privado"));
        assertThat(ContactReuse.save(data,bindings,values,Map.of()).created()).isEqualTo(3);Contact ana=data.matchingContact("Persona","DNI","11111111"),luis=data.matchingContact("Persona","DNI","22222222");assertThat(ana.given()).isEqualTo("Ana Pérez");assertThat(ana.extrasJson()).contains("Directora").doesNotContain("Luis Ruiz","Secretario","Persona_2","Privado");assertThat(luis.extrasJson()).contains("Secretario").doesNotContain("Ana Pérez","Directora");assertThat(data.findContacts("Luisito").get(0).number()).isEmpty();assertThat(ContactReuse.save(data,bindings,values,Map.of()).created()).isZero();values.put("Persona_1.Cargo","Presidenta");ContactReuse.save(data,bindings,values,Map.of());assertThat(data.matchingContact("Persona","DNI","22222222")).isEqualTo(luis);assertThat(data.contacts()).hasSize(3);
    }
    @Test void legacyDefinitionsReceiveSafeAutomaticDateDefaults()throws Exception{
        try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+temp.resolve("studio.db"));var s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");s.executeUpdate("CREATE TABLE variable_definition(id INTEGER PRIMARY KEY,key TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,default_value TEXT)");s.executeUpdate("INSERT INTO variable_definition VALUES(1,'Fecha','Fecha','Fecha','')");}
        DocumentStore store=new DocumentStore(temp);Definition date=store.definitions().get(0);assertThat(date.bindToClient()).isTrue();assertThat(date.autoCurrentDate()).isFalse();assertThat(date.dateFormat()).isEqualTo("dd/MM/yyyy");store.saveDefinition(new Definition(date.id(),date.key(),date.label(),date.type(),"",true,"EEEE, d 'de' MMMM 'de' yyyy"));assertThat(new DocumentStore(temp).definitions().get(0).currentDate()).isEqualTo(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",Locale.forLanguageTag("es-PE"))));
    }
    @Test void complementsLearnObjectsAndUndocumentedPeopleInBothDirectionsAndStayLocal()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);
        Definition object=store.saveDefinition(new Definition(0,"objeto","Objeto","Texto","")),price=store.saveDefinition(new Definition(0,"precio","Precio","Texto","")),person=store.saveDefinition(new Definition(0,"Dirigido","Dirigido","Texto","")),role=store.saveDefinition(new Definition(0,"Cargo","Cargo","Texto",""));
        store.saveComplement(object.id(),price.id(),true);store.saveComplement(person.id(),role.id(),true);
        assertThat(store.complements(object.id())).extracting(Complement::targetDefId).containsExactly(price.id());assertThat(store.complements(price.id())).extracting(Complement::targetDefId).containsExactly(object.id());assertThatThrownBy(()->store.saveComplement(object.id(),object.id(),true)).isInstanceOf(IllegalArgumentException.class);
        data.learnComplements(store.definitions(),store.complements(),Map.of("objeto","Silla","precio","120.00","Dirigido","Luisito","Cargo","Presidente de la Asociación"));
        assertThat(data.suggestions("objeto",List.of("precio"),"si",false).get(0).complements()).containsEntry("precio","120.00");assertThat(data.suggestions("precio",List.of("objeto"),"120.00",true).get(0).complements()).containsEntry("objeto","Silla");
        assertThat(data.suggestions("dirigido",List.of("cargo"),"LUISITO",true).get(0).complements()).containsEntry("cargo","Presidente de la Asociación");assertThat(data.suggestions("Cargo",List.of("Dirigido"),"Presidente",false).get(0).complements()).containsEntry("dirigido","Luisito");
        Contact luis=data.contacts().get(0);assertThat(luis.number()).isEmpty();assertThat(luis.given()).isEqualTo("Luisito");assertThat(luis.extrasJson()).contains("cargo","Presidente");
        data.learnComplements(store.definitions(),store.complements(),Map.of("objeto","Silla","precio","130.00","Dirigido","Luisito","Cargo","Secretario"));assertThat(data.suggestions("precio",List.of("objeto"),"120.00",true)).isEmpty();assertThat(data.suggestions("cargo",List.of("dirigido"),"Presidente de la Asociación",true)).isEmpty();assertThat(data.contacts()).hasSize(1);assertThat(data.suggestions("dirigido",List.of("cargo"),"Luisito",true).get(0).complements()).containsEntry("cargo","Secretario");assertThat(data.suggestions("objeto",List.of("precio"),"Silla",true).get(0).complements()).containsEntry("precio","130.00");
        new StudioData(temp).rememberPair("objeto","100% especial","precio","1",false);assertThat(data.suggestions("objeto",List.of("precio"),"100%",false)).hasSize(1);assertThat(data.suggestions("objeto",List.of("precio"),"S_",false)).isEmpty();
        store.saveComplement(object.id(),price.id(),false);assertThat(store.complements(price.id())).isEmpty();store.deleteDefinition(price.id());assertThat(store.complements(object.id())).isEmpty();
    }

    @Test void contactsWithoutDocumentsDeduplicateByNormalizedNameAndSearchAllExtraValues()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);
        Contact ana=data.saveContact(new Contact(0,"Persona","Ána","Pérez","DNI","","Lima","","{\"Cargo\":\"Presidenta\",\"Asociacion\":\"Los Pinos\"}"));Contact luis=data.saveContact(new Contact(0,"Persona","Luisito","","DNI","","","","{\"cargo\":\"Secretario\"}"));assertThat(data.contacts()).hasSize(2);
        Contact updated=data.upsertContact(new Contact(0,"Persona"," ana "," PÉREZ ","","","","","{\"Cargo\":\"Tesorera\"}"));assertThat(updated.id()).isEqualTo(ana.id());assertThat(data.contacts()).hasSize(2);assertThat(updated.extrasJson()).contains("Los Pinos","Tesorera");assertThat(data.findContacts("ana perez")).extracting(Contact::id).containsExactly(ana.id());assertThat(data.findContacts("LOS PINOS")).extracting(Contact::id).containsExactly(ana.id());assertThat(data.findContactsByVariable("CARGO","secre",true)).extracting(Contact::id).containsExactly(luis.id());
        Contact documented=data.saveContact(new Contact(0,"Persona","Juan","Pérez","DNI","12345678","Cusco","","{\"otro\":\"conservado\"}"));Definition name=store.saveDefinition(new Definition(0,"dirigido","Dirigido","Texto","")),role=store.saveDefinition(new Definition(0,"cargo","Cargo","Texto",""));store.saveComplement(name.id(),role.id(),true);data.learnComplements(store.definitions(),store.complements(),Map.of("dirigido","Juan Pérez","cargo","Director"));Contact after=data.matchingContact("Persona","DNI","12345678");assertThat(after.id()).isEqualTo(documented.id());assertThat(after.address()).isEqualTo("Cusco");assertThat(after.extrasJson()).contains("conservado","Director");assertThat(data.contacts()).hasSize(3);
    }

    @Test void migratesLegacyUniqueContactIdentityWithoutLosingExistingData()throws Exception{
        DocumentStore store=new DocumentStore(temp);
        try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+temp.resolve("studio.db"));var s=c.createStatement()){
            s.execute("PRAGMA foreign_keys=ON");s.executeUpdate("CREATE TABLE contact(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}',UNIQUE(kind,document_type,number))");s.executeUpdate("INSERT INTO contact(kind,given,document_type,number,extras_json) VALUES('Persona','Luisito','DNI','','{\"cargo\":\"Presidente\"}')");
        }
        StudioData data=new StudioData(temp);data.saveContact(new Contact(0,"Persona","María","","DNI","","","","{}"));assertThat(data.contacts()).hasSize(2);assertThat(data.findContacts("Luisito").get(0).extrasJson()).contains("Presidente");assertThat(new StudioData(temp).contacts()).hasSize(2);
    }

    @Test void complementDefinitionsAndLinksTravelWithFilteredTemplateConfiguration()throws Exception{
        DocumentStore store=new DocumentStore(temp.resolve("export"));StudioData data=new StudioData(store.directory());Definition source=store.saveDefinition(new Definition(0,"objeto","Objeto","Texto","")),target=store.saveDefinition(new Definition(0,"precio","Precio","Texto",""));store.saveComplement(source.id(),target.id(),true);
        byte[] bytes;try(XWPFDocument document=new XWPFDocument()){document.createParagraph().createRun().setText("{{objeto}}");bytes=DocumentEngine.bytes(document);}Template template=store.saveTemplate(new Template(0,"Complementos",bytes,bytes));store.bindDefinition(template.id(),source.id(),false,1);Path zip=temp.resolve("complementos.zip");new StudioTransfer(store,data).exportConfiguration(zip,List.of(template.id()));
        DocumentStore imported=new DocumentStore(temp.resolve("import"));StudioData importedData=new StudioData(imported.directory());StudioTransfer transfer=new StudioTransfer(imported,importedData);transfer.importConfiguration(zip,StudioTransfer.Conflict.KEEP);transfer.importConfiguration(zip,StudioTransfer.Conflict.KEEP);assertThat(imported.definitions()).hasSize(2);assertThat(imported.complements()).hasSize(1);Definition price=imported.definitions().stream().filter(d->d.key().equals("precio")).findFirst().orElseThrow();assertThat(imported.complements(price.id())).hasSize(1);assertThat(imported.templates().get(0).edited()).containsExactly(bytes);
        data.saveContact(new Contact(0,"Persona","Luisito","","","","","","{\"cargo\":\"Presidente\"}"));data.saveContact(new Contact(0,"Persona","María","","","","","","{}"));Path clients=temp.resolve("personas.zip");new StudioTransfer(store,data).exportClients(clients);transfer.importConfiguration(clients,StudioTransfer.Conflict.KEEP);transfer.importConfiguration(clients,StudioTransfer.Conflict.KEEP);assertThat(importedData.contacts()).hasSize(2);assertThat(importedData.findContacts("Presidente")).hasSize(1);
    }

    @Test void templateCatalogGroupsFiltersAndEditorSaveRemainIndependent()throws Exception{
        Path directory=temp.resolve("catalog");DocumentStore store=new DocumentStore(directory);StudioData data=new StudioData(directory);
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Plantilla");docx=DocumentEngine.bytes(doc);}
        Template first=store.saveTemplate(new Template(0,"Solicitud",docx,docx));data.recordRevision(first.id(),docx);
        TemplateGroup solicitudes=store.saveGroup(0,"Solicitudes");
        assertThat(store.templates()).hasSize(1);assertThat(store.groupIds(first.id())).isEmpty();assertThat(store.groups().get(0).count()).isZero();
        for(long selected:List.of(-1L,0L,solicitudes.id()))assertThat(store.templates().stream().filter(t->StudioWindow.libraryMatches(t.name(),"")).map(Template::id).toList()).containsExactly(first.id());
        assertThat(StudioWindow.libraryHighlighted(store.groupIds(first.id()),-1)).isFalse();
        assertThat(StudioWindow.libraryHighlighted(store.groupIds(first.id()),0)).isFalse();
        assertThat(StudioWindow.libraryHighlighted(store.groupIds(first.id()),solicitudes.id())).isFalse();
        store.setTemplateGroups(first.id(),List.of(solicitudes.id()));
        assertThat(store.groups().get(0).count()).isEqualTo(1);assertThat(store.templates().stream().map(Template::id).toList()).containsExactly(first.id());assertThat(StudioWindow.libraryHighlighted(store.groupIds(first.id()),solicitudes.id())).isTrue();
        Template other=store.saveTemplate(new Template(0,"Carta",docx,docx));
        assertThat(store.templates().stream().filter(t->StudioWindow.libraryMatches(t.name(),"")).map(Template::id).toList()).containsExactlyInAnyOrder(first.id(),other.id());
        assertThat(StudioWindow.libraryHighlighted(store.groupIds(other.id()),solicitudes.id())).isFalse();
        assertThat(store.templates().stream().filter(t->StudioWindow.libraryMatches(t.name(),"sol")).map(Template::id).toList()).containsExactly(first.id());
        TemplateGroup frecuentes=store.saveGroup(0,"Frecuentes");store.setTemplateGroups(first.id(),List.of(solicitudes.id(),frecuentes.id()));
        assertThat(store.templates()).hasSize(2);assertThat(store.groupIds(first.id())).containsExactlyInAnyOrder(solicitudes.id(),frecuentes.id());
        store.saveTemplate(new Template(first.id(),"Solicitud editada",first.original(),first.edited()));assertThat(store.groupIds(first.id())).hasSize(2);
        Template copy=store.saveTemplate(new Template(0,"Solicitud (copia)",first.original(),first.edited()));assertThat(store.groupIds(copy.id())).isEmpty();
        store.deleteGroup(solicitudes.id(),null);assertThat(store.templates()).hasSize(3);assertThat(store.groupIds(first.id())).containsExactly(frecuentes.id());
        DocumentStore reopened=new DocumentStore(directory);assertThat(reopened.groupIds(first.id())).containsExactly(frecuentes.id());assertThat(reopened.groupIds(copy.id())).isEmpty();assertThat(new StudioData(directory).revisionDates()).containsKey(first.id());
    }

    @Test void transferBackupAndReopen()throws Exception{
        Path first=temp.resolve("first"),second=temp.resolve("second");DocumentStore a=new DocumentStore(first);StudioData extra=new StudioData(first);
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Hola {{nombre}}");docx=DocumentEngine.bytes(doc);}
        Template template=a.saveTemplate(new Template(0,"Saludo",docx,docx));extra.recordRevision(template.id(),docx);
        a.saveVariable(new Variable(0,template.id(),"nombre","Nombre","Texto corto","",true,1));
        a.saveKind(new Kind(0,"Carta","","",template.id(),"nombre",true));
        extra.saveMapping(new Mapping(0,template.id(),"nombre","nombres","nombre"));
        extra.saveContact(new Contact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{}"));
        extra.saveHistory(new History(0,"Carta",template.id(),1,null,"{\"nombre\":\"Ana\"}",null,null,DocumentEngine.generate(docx,Map.of("nombre","Ana")),null));
        LocalSecret secret=new LocalSecret(first);secret.save("token-privado");assertThat(secret.load()).isEqualTo("token-privado");
        Path config=temp.resolve("config.zip");StudioTransfer transfer=new StudioTransfer(a,extra);transfer.exportConfiguration(config);
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(config))){assertThat(zip.getNextEntry().getName()).isEqualTo("configuration.json");assertThat(new String(zip.readAllBytes())).doesNotContain("token-privado","12345678");}
        DocumentStore b=new DocumentStore(second);StudioData bData=new StudioData(second);
        StudioTransfer.ImportResult imported=new StudioTransfer(b,bData).importConfiguration(config,StudioTransfer.Conflict.KEEP);
        assertThat(imported.templates()).isEqualTo(1);assertThat(b.templates()).hasSize(1);assertThat(b.variables(b.templates().get(0).id())).hasSize(1);assertThat(b.kinds()).hasSize(1);assertThat(bData.mappings(b.templates().get(0).id())).hasSize(1);assertThat(bData.contacts()).isEmpty();assertThat(bData.history()).isEmpty();
        assertThat(new StudioTransfer(b,bData).importConfiguration(config,StudioTransfer.Conflict.KEEP).conflicts()).isGreaterThan(0);
        Path backup=temp.resolve("backup.dsbk");transfer.backup(backup,"contrasena-segura".toCharArray());
        assertThat(new String(Files.readAllBytes(backup),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("12345678");
        new StudioTransfer(b,bData).restore(backup,"contrasena-segura".toCharArray());
        StudioData reopened=new StudioData(second);assertThat(reopened.contacts()).hasSize(1);assertThat(reopened.history()).hasSize(1);
        assertThat(new LocalSecret(second).configured()).isFalse();
    }

    @Test void factilizaParsesDocumentedResponsesAndRejectsIncompleteData()throws Exception{
        String dni="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"12345678\",\"nombres\":\"ANA\",\"apellido_paterno\":\"PEREZ\",\"direccion_completa\":\"LIMA\"}}";
        String ruc="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"20123456789\",\"nombre_o_razon_social\":\"EMPRESA\",\"direccion\":\"LIMA\"}}";
        assertThat(FactilizaClient.parse("DNI","12345678",200,dni).data()).containsEntry("nombres","ANA");
        assertThat(FactilizaClient.parse("RUC","20123456789",200,ruc).data()).containsEntry("nombre_o_razon_social","EMPRESA");
        assertThatThrownBy(()->FactilizaClient.parse("DNI","12345678",200,"{\"success\":true,\"data\":{}}" )).hasMessageContaining("incompletos");
        assertThatThrownBy(()->FactilizaClient.parse("RUC","20123456789",401,"{\"success\":false,\"message\":\"Unauthorized\"}" )).hasMessageContaining("Unauthorized");
        assertThat(FactilizaClient.parse("DNI","12345678",200,"{\"success\":true,\"data\":{\"numero\":\"12345678\",\"ubigeo\":[\"15\",\"1501\"],\"sexo\":null}}").data()).containsEntry("data.ubigeo[1]","1501").doesNotContainKey("data.sexo");
        assertThat(FactilizaClient.parse("RUC - Representantes","20123456789",200,"{\"status\":200,\"data\":[{\"nombre\":\"Ejemplo\"}]}").data()).containsEntry("data[0].nombre","Ejemplo");
    }

    @Test void variableApiBindingsRemainIndependentAndTransferWithoutClientData()throws Exception{
        Path first=temp.resolve("api-source"),second=temp.resolve("api-target");DocumentStore store=new DocumentStore(first);StudioData data=new StudioData(first);
        Definition original=store.saveDefinition(new Definition(0,"nombre","Nombre","Texto","Antiguo"));
        data.saveApiBinding(new ApiBinding(original.id(),"DNI","data.nombres"));
        Definition copy=store.saveDefinition(new Definition(0,"nombre_copia","Nombre (copia)",original.type(),""));
        assertThat(copy.id()).isNotEqualTo(original.id());assertThat(copy.defaultValue()).isEmpty();assertThat(data.apiBinding(copy.id())).isNull();
        assertThat(VariableInput.initialValue("Fecha","2020-01-01")).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(VariableInput.validate("Mixto","DNI 001, A.",Locale.getDefault())).isEqualTo("DNI 001, A.");
        assertThat(VariableInput.validate("Número decimal","-1.234,50",Locale.forLanguageTag("es-PE"))).isEqualTo("-1234.50");
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{nombre}}");docx=DocumentEngine.bytes(doc);}
        Template template=store.saveTemplate(new Template(0,"API",docx,docx));
        assertThat(store.definitionDependencies(original.id())).contains("API");
        assertThatThrownBy(()->store.deleteDefinition(original.id())).hasMessageContaining("API");
        data.saveContact(new Contact(0,"Persona","Ana","","DNI","12345678","","","{}"));
        Path packageFile=temp.resolve("api.zip");new StudioTransfer(store,data).exportConfiguration(packageFile);
        DocumentStore target=new DocumentStore(second);StudioData imported=new StudioData(second);new StudioTransfer(target,imported).importConfiguration(packageFile,StudioTransfer.Conflict.KEEP);
        Definition transferred=target.definitions().stream().filter(d->d.key().equals("nombre")).findFirst().orElseThrow();
        assertThat(imported.apiBinding(transferred.id()).path()).isEqualTo("data.nombres");assertThat(imported.contacts()).isEmpty();
    }

    @Test void sharedDefinitionsBindToTemplatesAndSurviveTransfer()throws Exception{
        Path source=temp.resolve("variables-source"),destination=temp.resolve("variables-destination");
        DocumentStore store=new DocumentStore(source);StudioData data=new StudioData(source);
        Definition definition=store.saveDefinition(new Definition(0,"nombre_libre","Nombre","Texto corto",""));
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Hola {{nombre_libre}}");docx=DocumentEngine.bytes(doc);}
        Template first=store.saveTemplate(new Template(0,"Primera",docx,docx));Template second=store.saveTemplate(new Template(0,"Segunda",docx,docx));
        store.bindDefinition(first.id(),definition.id(),false,1);store.bindDefinition(second.id(),definition.id(),false,1);
        Kind kind=store.saveKind(new Kind(0,"Saludo","","",first.id(),"nombre_libre",true));
        store.saveKindFieldRules(kind.id(),Map.of("nombre_libre",true),Map.of("nombre_libre","Valor local"));
        assertThat(store.definitionDependencies(definition.id())).containsExactly("Primera","Segunda");
        assertThatThrownBy(()->store.deleteDefinition(definition.id())).hasMessageContaining("Primera");
        byte[] output=DocumentEngine.generate(first.edited(),Map.of("nombre_libre","Ana"));
        try(XWPFDocument generated=DocumentEngine.open(output)){assertThat(generated.getParagraphs().get(0).getText()).isEqualTo("Hola Ana");}
        Path zip=temp.resolve("variables.zip");new StudioTransfer(store,data).exportConfiguration(zip);
        DocumentStore imported=new DocumentStore(destination);StudioData importedData=new StudioData(destination);
        new StudioTransfer(imported,importedData).importConfiguration(zip,StudioTransfer.Conflict.KEEP);
        assertThat(imported.definitions()).hasSize(1);
        assertThat(imported.definitionId(imported.variables(imported.templates().get(0).id()).get(0).id())).isEqualTo(imported.definitions().get(0).id());
        assertThat(imported.kindRequirements(imported.kinds().get(0).id())).containsEntry("nombre_libre",true);
        assertThat(imported.kindDefaults(imported.kinds().get(0).id())).containsEntry("nombre_libre","Valor local");
        assertThat(new DocumentStore(destination).definitions()).hasSize(1);
    }

    @Test void explicitGroupsSaveAndReuseIndependentContactsWithoutDuplicates()throws Exception{
        Path directory=temp.resolve("contact-groups");DocumentStore store=new DocumentStore(directory);StudioData data=new StudioData(directory);
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{nombre_a}} {{nombre_b}} {{empresa}} ");docx=DocumentEngine.bytes(doc);}
        Template template=store.saveTemplate(new Template(0,"Grupos",docx,docx));
        List<StudioData.ContactBinding> bindings=new ArrayList<>();
        String[][] rows={{"a","Persona","given","nombre_a"},{"a","Persona","number","dni_a"},{"a","Persona","address","domicilio_a"},
                {"b","Persona","given","nombre_b"},{"b","Persona","number","dni_b"},{"b","Persona","address","domicilio_b"},
                {"empresa","Empresa","given","empresa"},{"empresa","Empresa","number","ruc"}};
        for(String[] row:rows){store.saveVariable(new Variable(0,template.id(),row[3],row[3],row[2].equals("number")?(row[1].equals("Persona")?"DNI":"RUC"):"Texto corto","",false,1));bindings.add(data.saveContactBinding(new StudioData.ContactBinding(0,template.id(),row[0],row[1],row[2],row[3])));}
        Map<String,String> values=Map.of("nombre_a","Juan Pérez","dni_a","12345678","domicilio_a","Lima","nombre_b","María Ruiz","dni_b","87654321","domicilio_b","Cusco","empresa","ABC SAC","ruc","20123456789");
        Map<String,String> types=Map.of("dni_a","DNI","dni_b","DNI","ruc","RUC");
        ContactReuse.Result first=ContactReuse.save(data,bindings,values,types);
        assertThat(first.created()).isEqualTo(3);
        assertThat(ContactReuse.save(data,bindings,values,types).created()).isZero();
        assertThat(data.contacts()).hasSize(3);
        StudioData.Contact juan=data.matchingContact("Persona","DNI","12345678");
        assertThat(ContactReuse.valuesFor(juan,bindings.stream().filter(b->b.groupKey().equals("a")).toList())).containsEntry("nombre_a","Juan Pérez").containsEntry("domicilio_a","Lima");
        Map<String,String> changed=new HashMap<>(values);changed.put("domicilio_a","Arequipa");
        ContactReuse.save(data,bindings,changed,types);
        assertThat(data.matchingContact("Persona","DNI","12345678").address()).isEqualTo("Arequipa");
        ContactReuse.save(data,bindings,changed,types);
        assertThat(new StudioData(directory).matchingContact("Persona","DNI","12345678").address()).isEqualTo("Arequipa");
        Map<String,String> withoutDocument=new HashMap<>(values);withoutDocument.put("dni_a","");
        assertThat(ContactReuse.save(data,bindings,withoutDocument,types).skipped()).isEqualTo(1);
        assertThat(data.contacts()).hasSize(3);
        Path config=temp.resolve("groups.zip");new StudioTransfer(store,data).exportConfiguration(config);
        Path importedDirectory=temp.resolve("groups-imported");DocumentStore imported=new DocumentStore(importedDirectory);StudioData importedData=new StudioData(importedDirectory);
        new StudioTransfer(imported,importedData).importConfiguration(config,StudioTransfer.Conflict.KEEP);
        assertThat(importedData.contactBindings(imported.templates().get(0).id())).hasSize(8);
        assertThat(importedData.contacts()).isEmpty();
    }

    @Test void migratesLegacyVariablesWithoutMergingDifferentDefinitions()throws Exception{
        Path directory=temp.resolve("legacy");Files.createDirectories(directory);
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("studio.db"));java.sql.Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE template(id INTEGER PRIMARY KEY,name TEXT NOT NULL,original BLOB NOT NULL,edited BLOB NOT NULL)");
            s.executeUpdate("CREATE TABLE variable(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,key TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,default_value TEXT,required INTEGER NOT NULL,position INTEGER NOT NULL,UNIQUE(template_id,key))");
            s.executeUpdate("INSERT INTO variable(template_id,key,label,type,default_value,required,position) VALUES(1,'dato','Nombre','Texto corto','',1,1),(2,'dato','Monto','Monto','0',0,1)");
        }
        DocumentStore migrated=new DocumentStore(directory);
        assertThat(migrated.definitions()).hasSize(2);
        assertThat(migrated.variables(1)).hasSize(1);
        assertThat(migrated.variables(2)).hasSize(1);
        assertThat(migrated.definitionId(migrated.variables(1).get(0).id())).isNotEqualTo(migrated.definitionId(migrated.variables(2).get(0).id()));
        assertThat(new DocumentStore(directory).definitions()).hasSize(2);
    }
}
