package com.ejemplo.crudpersona.studio;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateCatalogUiTest {
    @TempDir Path temp;

    @BeforeAll static void startToolkit()throws Exception{
        if(System.getProperty("javafx.cachedir")==null)System.setProperty("javafx.cachedir",Path.of(System.getProperty("java.io.tmpdir"),"document-studio-javafx-tests").toString());
        Platform.startup(()->{Platform.setImplicitExit(false);Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());});
    }
    @AfterAll static void stopToolkit(){Platform.exit();}

    @Test void libraryAlwaysShowsAllTemplatesAndHighlightsOnlyMembersOfTheSelectedRealGroup()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template first=template(store,"Solicitud","Plantilla"),second=template(store,"Carta","Plantilla"),unassigned=template(store,"Libre","Plantilla");
        TemplateGroup requests=store.saveGroup(0,"Solicitudes"),letters=store.saveGroup(0,"Cartas");store.setTemplateGroups(first.id(),List.of(requests.id()));store.setTemplateGroups(second.id(),List.of(letters.id()));
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));
            try{
                invoke(window,"showLibrary");ListView<TemplateGroup> groups=value(window,"groupList");ListView<Template> catalog=value(window,"libraryList");
                assertThat(groups.getItems()).containsExactlyElementsOf(store.groups());assertThat(catalog.getItems()).extracting(Template::id).containsExactlyInAnyOrder(first.id(),second.id(),unassigned.id());
                groups.getSelectionModel().select(groups.getItems().stream().filter(g->g.id()==requests.id()).findFirst().orElseThrow());
                assertThat(catalog.getItems()).hasSize(3);ListCell<Template> cell=catalog.getCellFactory().call(catalog);updateCell(cell,first);assertThat(cell.getStyleClass()).contains("library-associated");updateCell(cell,second);assertThat(cell.getStyleClass()).doesNotContain("library-associated");
                groups.getSelectionModel().select(groups.getItems().stream().filter(g->g.id()==letters.id()).findFirst().orElseThrow());
                assertThat(catalog.getItems()).hasSize(3);updateCell(cell,first);assertThat(cell.getStyleClass()).doesNotContain("library-associated");updateCell(cell,second);assertThat(cell.getStyleClass()).contains("library-associated");
                TextField search=value(window,"librarySearch");search.setText("sol");assertThat(catalog.getItems()).extracting(Template::id).containsExactly(first.id());search.clear();assertThat(catalog.getItems()).hasSize(3);
                for(TemplateGroup group:store.groups())store.deleteGroup(group.id(),null);invoke(window,"refreshLibrary");assertThat(groups.getItems()).isEmpty();assertThat(catalog.getItems()).hasSize(3);updateCell(cell,second);assertThat(cell.getStyleClass()).doesNotContain("library-associated");
                window.root().applyCss();window.root().layout();BorderPane page=value(window,"libraryPage");assertThat(buttonLabels(page)).containsExactlyInAnyOrder("Nuevo grupo","Renombrar grupo","Eliminar grupo","Abrir en editor","Renombrar plantilla","Duplicar plantilla","Eliminar plantilla","Añadir plantilla al grupo","Quitar del grupo");
            }finally{stage.close();}return null;
        });
    }

    @Test void generatorContainsOnlyRealGroupsAndTheirRealTemplatesAndBuildsActualMarkers()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template first=template(store,"Carta","{{cliente_dni}} {{nombre}}"),second=template(store,"Contrato","Otro"),unassigned=template(store,"Sin asignar","Otro");
        TemplateGroup a=store.saveGroup(0,"A"),b=store.saveGroup(0,"B");store.setTemplateGroups(first.id(),List.of(a.id()));store.setTemplateGroups(second.id(),List.of(b.id()));
        store.saveVariable(new Variable(0,first.id(),"nombre","Nombre","Texto corto","",false,1));store.saveDefinition(new Definition(0,"cliente_dni","DNI del cliente","Texto corto",""));
        store.saveKind(new Kind(0,"Registro antiguo","","",first.id(),"campo_antiguo",true));
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));
            try{
                invoke(window,"showGenerator");ComboBox<TemplateGroup> groups=value(window,"generateGroup");ComboBox<Template> choices=value(window,"generateTemplate");assertThat(groups.getItems()).containsExactlyElementsOf(store.groups());assertThat(choices.getItems()).extracting(Template::id).containsExactly(first.id());
                choices.getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");assertThat(fields.keySet()).containsExactlyInAnyOrder("nombre","cliente_dni");window.root().applyCss();window.root().layout();assertThat(buttonLabels((BorderPane)value(window,"generatorPage"))).contains("Consultar API","Abrir en Word","Imprimir");
                groups.getSelectionModel().select(groups.getItems().stream().filter(g->g.id()==b.id()).findFirst().orElseThrow());assertThat(choices.getItems()).extracting(Template::id).containsExactly(second.id());
                TextField search=value(window,"generateSearch");search.setText("carta");assertThat(choices.getItems()).isEmpty();search.clear();groups.getSelectionModel().selectFirst();search.setText("CaRtA");assertThat(choices.getItems()).extracting(Template::id).containsExactly(first.id());assertThat(choices.getItems()).extracting(Template::id).doesNotContain(unassigned.id());
            }finally{stage.close();}return null;
        });
    }

    @Test void settingsLayoutKeepsExactlyThreeScopesAndButtonStylesInBothThemes()throws Exception{
        DocumentStore store=new DocumentStore(temp);template(store,"Carta","Contenido");store.saveGroup(0,"Cartas");
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);Scene scene=scene(window);stage.setScene(scene);
            try{
                invoke(window,"showSettings");ComboBox<String> scope=value(window,"exportScope");ComboBox<Template> templates=value(window,"exportTemplate");ComboBox<TemplateGroup> groups=value(window,"exportGroup");
                assertThat(scope.getItems()).containsExactly("Solo una plantilla","Todas las plantillas de un grupo","Todas las plantillas");assertThat(templates.isDisabled()).isFalse();assertThat(groups.isDisabled()).isTrue();
                scope.getSelectionModel().select(1);assertThat(templates.isDisabled()).isTrue();assertThat(groups.isDisabled()).isFalse();scope.getSelectionModel().select(2);assertThat(templates.isDisabled()).isTrue();assertThat(groups.isDisabled()).isTrue();
                ComboBox<String> conflicts=value(window,"importConflict");assertThat(conflicts.getItems()).containsExactly("Conservar existentes","Reemplazar existentes");
                for(boolean dark:List.of(false,true)){
                    Application.setUserAgentStylesheet(dark?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());window.root().applyCss();window.root().layout();
                    assertThat(window.root().lookupAll(".transfer-card")).hasSize(3);
                    Button importing=window.root().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Importar archivo (.zip)")).map(n->(Button)n).findFirst().orElseThrow();
                    assertThat(importing.getStyleClass()).contains("button","primary-btn");assertThat(importing.getMinHeight()).isGreaterThanOrEqualTo(36);assertThat(importing.getBackground()).isNotNull();
                    VBox sidebar=value(window,"sidebar");assertThat(sidebar.getPrefWidth()).isEqualTo(242);
                    for(String view:List.of("showGenerator","showDefinitions","showLibrary","showEditor","showContacts","showHistory","showApi","showSettings")){
                        invoke(window,view);window.root().applyCss();window.root().layout();
                        assertThat(((Label)value(window,"status")).getText()).doesNotContain(store.directory().toString());
                        snapshot(window,view+(dark?"-dark":"-light"));
                    }
                }
            }finally{stage.close();}
            return null;
        });
    }

    @Test void generationUsesLocalExtrasThenSavesManualChangesAndApiResultsWithoutConfirmation()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);ApiCredentialStore vault=new ApiCredentialStore(temp);vault.add("Prueba","Factiliza","fixture-token");
        Template template=template(store,"Carta","{{dni}} {{nombre}} {{direccion}} {{ubigeo}}");
        TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));
        List<Variable> variables=new ArrayList<>();int position=1;
        for(String key:List.of("dni","nombre","direccion","ubigeo"))variables.add(store.saveVariable(new Variable(0,template.id(),key,key,switch(key){case "dni"->"DNI";case "direccion"->"Domicilio";default->"Texto corto";},"",false,position++)));
        for(Variable v:variables)data.saveApiBinding(new ApiBinding(store.definitionId(v.id()),"DNI","field:"+switch(v.key()){case "dni"->"numero";case "nombre"->"nombres";case "direccion"->"direccion";default->"ubigeo";}));
        data.saveContact(new Contact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{\"ubigeo\":\"150101\",\"data.extra\":\"valor\"}"));
        AtomicInteger calls=new AtomicInteger();AtomicReference<Map<String,String>> response=new AtomicReference<>(Map.of("data.numero","87654321","data.nombres","María","data.apellido_paterno","Ruiz","data.direccion","Cusco","data.ubigeo[2]","080101","data.extra","conservado"));
        LookupProvider provider=new LookupProvider(){public String name(){return "Factiliza";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String service,String identifier,String token){calls.incrementAndGet();return response.get();}};
        LookupRouter router=new LookupRouter(vault,List.of(provider));
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store,router);stage.setScene(scene(created));return created;});
        try{
            fx(()->{
                invoke(window,"showGenerator");ComboBox<Template> templates=value(window,"generateTemplate");templates.getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Template selected=templates.getValue();Map<String,Control> fields=value(window,"inputs");
                TextField dni=(TextField)fields.get("dni");dni.setText("12345678");
                invoke(window,"lookupForField",new Class<?>[]{Template.class,Variable.class,TextField.class,String.class},selected,variables.get(0),dni,"DNI");
                assertThat(calls).hasValue(0);assertThat(((TextField)fields.get("nombre")).getText()).isEqualTo("Ana");assertThat(((TextArea)fields.get("direccion")).getText()).isEqualTo("Lima");assertThat(((TextField)fields.get("ubigeo")).getText()).isEqualTo("150101");assertThat(((Label)value(window,"status")).getText()).isEqualTo("Datos cargados desde Clientes locales");
                ((TextField)fields.get("nombre")).setText("Ana actualizada");((TextArea)fields.get("direccion")).setText("Arequipa");invoke(window,"makePreview");assertThat((byte[])value(window,"generatedBytes")).isNotEmpty();
                Contact saved=data.matchingContact("Persona","DNI","12345678");assertThat(saved.given()).isEqualTo("Ana actualizada");assertThat(saved.address()).isEqualTo("Arequipa");assertThat(saved.extrasJson()).contains("150101","data.extra");window.root().applyCss();window.root().layout();snapshot(window,"generator-with-data");return null;
            });
            CompletableFuture<LookupRouter.Result> queried=new CompletableFuture<>();fx(()->{invoke(window,"runApiQuery",new Class<?>[]{String.class,String.class,Long.class,Consumer.class},"DNI","87654321",null,(Consumer<LookupRouter.Result>)r->{try{invoke(window,"showApiResult",new Class<?>[]{LookupRouter.Result.class},r);queried.complete(r);}catch(Exception e){queried.completeExceptionally(e);}});return null;});queried.get(20,TimeUnit.SECONDS);
            Contact saved=data.matchingContact("Persona","DNI","87654321");assertThat(saved.given()).isEqualTo("María");assertThat(saved.family()).isEqualTo("Ruiz");assertThat(saved.extrasJson()).contains("data.extra","conservado","080101");
            fx(()->{assertThat(((Label)value(window,"apiSaved")).getText()).isEqualTo("✓ Guardado automáticamente en Clientes");return null;});assertThat(data.contacts()).hasSize(2);
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void openingAndPrintingSaveNamedIdentifierFormsAndAllExtrasBeforeDispatch()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Template template=template(store,"Documento","{{documento}} {{cliente}} {{apellidos}} {{direccion}} {{referencia}}");TemplateGroup group=store.saveGroup(0,"Documentos");store.setTemplateGroups(template.id(),List.of(group.id()));
        for(String key:List.of("documento","cliente","apellidos","direccion","referencia"))store.saveVariable(new Variable(0,template.id(),key,key.equals("documento")?"DNI del cliente":key,"Texto corto","",false,1));
        List<Path> opened=new ArrayList<>(),printed=new ArrayList<>();StudioWindow.DocumentActions actions=new StudioWindow.DocumentActions(){
            public void open(Path file)throws Exception{assertThat(data.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Ana");opened.add(file);}
            public void print(Path file)throws Exception{assertThat(data.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Ana nueva");printed.add(file);}
        };
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store,null,actions);stage.setScene(scene(window));
            try{
                invoke(window,"showGenerator");ComboBox<Template> choices=value(window,"generateTemplate");choices.getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");
                ((TextField)fields.get("documento")).setText("12345678");((TextField)fields.get("cliente")).setText("Ana");((TextField)fields.get("apellidos")).setText("Pérez");((TextField)fields.get("direccion")).setText("Lima");((TextField)fields.get("referencia")).setText("Puerta azul");
                invoke(window,"openGeneratedInWord");assertThat(opened).hasSize(1);assertThat(opened.get(0).getFileName().toString()).startsWith("documento-").endsWith(".docx");try(XWPFDocument doc=DocumentEngine.open(Files.readAllBytes(opened.get(0)))){assertThat(doc.getParagraphs().get(0).getText()).contains("Ana","Puerta azul");}
                ((TextField)fields.get("cliente")).setText("Ana nueva");((TextField)fields.get("referencia")).setText("Puerta verde");invoke(window,"printGenerated");assertThat(printed).hasSize(1);assertThat(Files.readAllBytes(printed.get(0))).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                assertThat(data.contacts()).hasSize(1);Contact contact=data.matchingContact("Persona","DNI","12345678");assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(contact.extrasJson()).path("referencia").asText()).isEqualTo("Puerta verde");
                ((TextField)fields.get("documento")).clear();((TextField)fields.get("cliente")).clear();((TextField)fields.get("referencia")).clear();((TextField)fields.get("documento")).setText("12345678");assertThat(((TextField)fields.get("cliente")).getText()).isEqualTo("Ana nueva");assertThat(((TextField)fields.get("referencia")).getText()).isEqualTo("Puerta verde");
                invoke(window,"showContacts");window.root().applyCss();window.root().layout();((Button)value(window,"clientDetailsToggle")).fire();window.root().applyCss();window.root().layout();assertThat(buttonLabels((BorderPane)value(window,"contactsPage"))).containsExactlyInAnyOrder("Guardar cliente","Eliminar cliente","Nueva persona","Asignar / Actualizar","Quitar","Volver a documentos");ListView<Contact> clients=value(window,"contactList");assertThat(clients.getItems()).hasSize(1);assertThat(clients.getSelectionModel().getSelectedItem()).isNotNull();
                ((TextField)value(window,"contactGiven")).setText("Editada en Clientes");invoke(window,"saveContact");assertThat(data.contacts()).hasSize(1);assertThat(data.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Editada en Clientes");
                TextField search=value(window,"contactSearch");search.setText("inexistente");assertThat(clients.getItems()).isEmpty();search.clear();assertThat(clients.getItems()).hasSize(1);invoke(window,"deleteContact");assertThat(data.contacts()).isEmpty();
            }finally{stage.close();for(Path file:opened)Files.deleteIfExists(file);for(Path file:printed)Files.deleteIfExists(file);}return null;
        });
    }

    @Test void enteringCompleteRucAutomaticallyQueriesOnlyOnceThenReusesLocalData()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Template template=template(store,"Empresa","{{ruc_empresa}} {{razon_social}} {{direccion}}");TemplateGroup group=store.saveGroup(0,"Empresas");store.setTemplateGroups(template.id(),List.of(group.id()));
        for(String key:List.of("ruc_empresa","razon_social","direccion"))store.saveVariable(new Variable(0,template.id(),key,key,"Texto corto","",false,1));
        ApiCredentialStore vault=new ApiCredentialStore(temp);vault.add("Prueba","Factiliza","fixture-token");AtomicInteger calls=new AtomicInteger();LookupProvider provider=new LookupProvider(){public String name(){return "Factiliza";}public Set<String> services(){return Set.of("RUC");}public Map<String,String> query(String service,String number,String credential){calls.incrementAndGet();return Map.of("data.numero",number,"data.nombre_o_razon_social","ACME","data.direccion","Cusco");}};
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store,new LookupRouter(vault,List.of(provider)));stage.setScene(scene(created));return created;});
        CompletableFuture<Void> completed=new CompletableFuture<>();
        try{
            fx(()->{invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");((TextField)fields.get("razon_social")).textProperty().addListener((o,a,b)->{if(b.equals("ACME"))completed.complete(null);});((TextField)fields.get("razon_social")).setText("Dato anterior");((TextField)fields.get("ruc_empresa")).setText("20123456789");return null;});completed.get(20,TimeUnit.SECONDS);
            fx(()->{assertThat(calls).hasValue(1);assertThat(data.matchingContact("Empresa","RUC","20123456789").given()).isEqualTo("ACME");Map<String,Control> fields=value(window,"inputs");assertThat(((TextField)fields.get("direccion")).getText()).isEqualTo("Cusco");((TextField)fields.get("ruc_empresa")).clear();((TextField)fields.get("razon_social")).clear();((TextField)fields.get("ruc_empresa")).setText("20123456789");assertThat(((TextField)fields.get("razon_social")).getText()).isEqualTo("ACME");assertThat(((Label)value(window,"status")).getText()).isEqualTo("Datos cargados desde Clientes locales");assertThat(calls).hasValue(1);return null;});
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void deletingTemplateRequiresConfirmationAndKeepsGeneratedDocuments()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Template template=template(store,"Carta","Documento");data.saveHistory(new History(0,"Carta generada",template.id(),1,null,"{}",null,null,template.edited(),null));
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));
            try{
                invoke(window,"showLibrary");ListView<Template> catalog=value(window,"libraryList");catalog.getSelectionModel().selectFirst();
                AtomicReference<Throwable> problem=answerDialog(ButtonType.CANCEL,pane->{assertThat(pane.getContentText()).contains("Historial","conservarán");assertThat(store.templates()).hasSize(1);});
                invoke(window,"removeLibraryTemplate");assertThat(problem.get()).isNull();assertThat(store.templates()).hasSize(1);assertThat(data.history().get(0).templateId()).isEqualTo(template.id());
                problem=answerDialog(ButtonType.YES,pane->{assertThat(pane.getContentText()).contains("Carta","Historial");assertThat(store.templates()).hasSize(1);});
                invoke(window,"removeLibraryTemplate");assertThat(problem.get()).isNull();assertThat(store.templates()).isEmpty();assertThat(data.history()).hasSize(1);assertThat(data.history().get(0).templateId()).isNull();assertThat(data.history().get(0).docx()).containsExactly(template.edited());
            }finally{stage.close();}return null;
        });
    }

    @Test void variableWarningLoadsTheFirstActualAffectedTemplateAndPaletteHeaderCanExpandAgain()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template unrelated=template(store,"Afectada","Sin variables"),affected=template(store,"Afectada","Con {{nombre}}"),second=template(store,"Otra","{{nombre}}");
        Definition definition=store.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto",""));store.bindDefinition(affected.id(),definition.id(),false,1);store.bindDefinition(second.id(),definition.id(),false,1);
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},unrelated);return created;});
        try{
            fx(()->{
                invoke(window,"showDefinitions");ListView<Definition> variables=value(window,"definitionList");variables.getSelectionModel().selectFirst();AtomicReference<Throwable> problem=new AtomicReference<>();
                Platform.runLater(()->{DialogPane pane=dialogPane();try{assertThat(pane.getContentText()).contains("Afectada");ButtonType open=pane.getButtonTypes().stream().filter(b->b.getText().equals("Abrir editor")).findFirst().orElseThrow();((Button)pane.lookupButton(open)).fire();}catch(Throwable e){problem.set(e);((Button)pane.lookupButton(ButtonType.CANCEL)).fire();}});
                invoke(window,"deleteDefinition");assertThat(problem.get()).isNull();assertThat(((Template)value(window,"currentTemplate")).id()).isEqualTo(affected.id());assertThat(((ComboBox<Template>)value(window,"templateChoice")).getValue().id()).isEqualTo(affected.id());
                assertThat(((ToggleButton)((ToggleGroup)value(window,"menu")).getSelectedToggle()).getText()).isEqualTo("Editor de plantillas");return null;
            });
            awaitEditor(window);
            fx(()->{
                assertThat(js(window,"document.getElementById('editor').textContent").toString()).contains("nombre");VBox panel=value(window,"palettePanel");Button toggle=value(window,"paletteToggle");assertThat(toggle.getParent().getParent()).isSameAs(panel);
                double expanded=panel.getPrefWidth();toggle.fire();assertThat(panel.isVisible()).isTrue();assertThat(toggle.isVisible()).isTrue();assertThat(panel.getPrefWidth()).isLessThan(expanded);toggle.fire();assertThat(panel.getPrefWidth()).isEqualTo(expanded);return null;
            });
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void previewOpenAndPrintAllowAllRequiredFieldsEmptyOrOnlySomeFilled()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template template=template(store,"Parcial","Nombre {{nombre}}; DNI {{dni}}; Total {{total}}; Fecha {{fecha}}; {{sin_definicion}}");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));
        for(String key:List.of("nombre","dni","total","fecha"))store.saveVariable(new Variable(0,template.id(),key,key,switch(key){case "dni"->"DNI";case "total"->"Número decimal";case "fecha"->"Fecha";default->"Texto corto";},"",true,1));
        List<Path> paths=new ArrayList<>();StudioWindow.DocumentActions actions=new StudioWindow.DocumentActions(){public void open(Path path){paths.add(path);}public void print(Path path){paths.add(path);}};
        fx(()->{
            Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store,null,actions);stage.setScene(scene(window));
            try{
                invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().selectFirst();invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");((DatePicker)fields.get("fecha")).setValue(null);
                invoke(window,"makePreview");byte[] empty=value(window,"generatedBytes");assertThat(empty).isNotEmpty();try(XWPFDocument doc=DocumentEngine.open(empty)){assertThat(doc.getParagraphs().get(0).getText()).isEqualTo("Nombre ; DNI ; Total ; Fecha ; ");assertThat(DocumentEngine.markers(doc)).isEmpty();}
                invoke(window,"openGeneratedInWord");assertThat(paths).hasSize(1);((TextField)fields.get("nombre")).setText("Ana");invoke(window,"makePreview");byte[] partial=value(window,"generatedBytes");try(XWPFDocument doc=DocumentEngine.open(partial)){assertThat(doc.getParagraphs().get(0).getText()).contains("Nombre Ana; DNI ; Total ; Fecha ;");}
                invoke(window,"printGenerated");assertThat(paths).hasSize(2);assertThat(Files.readAllBytes(paths.get(1))).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }finally{stage.close();for(Path path:paths)Files.deleteIfExists(path);}return null;
        });
    }

    @Test void webEditorMovesChipsWithoutDuplicatesCoalescesGuideAndDeletesOnlyRightClickedOccurrence()throws Exception{
        DocumentStore store=new DocumentStore(temp);byte[] bytes;
        try(XWPFDocument document=new XWPFDocument()){
            document.createParagraph().createRun().setText("{{nombre}}");XWPFParagraph untouched=document.createParagraph();untouched.setStyle("Heading1");untouched.createRun().setText("Intacto");document.createParagraph().createRun().setText("Otra {{nombre}}");document.createTable(1,1).getRow(0).getCell(0).setText("Destino");bytes=DocumentEngine.bytes(document);
        }
        Template template=store.saveTemplate(new Template(0,"Arrastre",bytes,bytes));Definition name=store.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto","")),fresh=store.saveDefinition(new Definition(0,"nuevo","Nuevo","Texto corto",""));store.bindDefinition(template.id(),name.id(),false,1);
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{
            awaitEditor(window);
            long layoutDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(fx(()->((StackPane)value(window,"workspace")).getChildren().get(0).getOpacity())<.99&&System.nanoTime()<layoutDeadline)Thread.sleep(25);
            fx(()->{window.root().applyCss();window.root().layout();invoke(window,"setEditorZoom",new Class<?>[]{double.class},1.0);return null;});
            fx(()->{
                js(window,"window.realBridge=window.javaBridge;window.bridgeCalls=0;window.javaBridge={markDirty:function(){bridgeCalls++;realBridge.markDirty()},bindDroppedDefinition:function(id,key){bridgeCalls++;return realBridge.bindDroppedDefinition(id,key)},pageMoved:function(){},tableSelected:function(){},imageSelected:function(){}};window.rafCalls=0;window.realRaf=window.requestAnimationFrame;window.requestAnimationFrame=function(f){rafCalls++;return realRaf.call(window,f)};");
                js(window,"window.transfer={types:['text/plain'],value:'',setData:function(type,value){this.value=value},getData:function(){return this.value}};window.dragEvent=function(type,target){var e=new Event(type,{bubbles:true,cancelable:true});Object.defineProperty(e,'dataTransfer',{value:transfer});Object.defineProperty(e,'clientX',{value:dropX});Object.defineProperty(e,'clientY',{value:dropY});target.dispatchEvent(e)};window.target=document.querySelector('#editor td p');target.scrollIntoView({block:'center'});var text=target.querySelector('span').firstChild;var r=document.createRange();r.setStart(text,text.length);r.collapse(true);var box=r.getBoundingClientRect();window.dropX=box.left+1;window.dropY=box.top+box.height/2;dragEvent('dragstart',document.querySelector('#editor .variable'));for(var i=0;i<50;i++)dragEvent('dragover',target);");
                assertThat(((Number)js(window,"rafCalls")).intValue()).isEqualTo(1);assertThat(((Number)js(window,"bridgeCalls")).intValue()).isZero();return null;
            });
            awaitJs(window,"document.getElementById('drop-caret').style.display==='block'");
            fx(()->{
                assertThat(js(window,"document.getElementById('drop-caret').style.transform").toString()).startsWith("translate3d(");js(window,"dragEvent('drop',target)");
                assertThat(((Number)js(window,"document.querySelectorAll('#editor .variable').length")).intValue()).isEqualTo(2);assertThat(((Number)js(window,"document.querySelector('#editor td p').querySelectorAll('.variable').length")).intValue()).isEqualTo(1);assertThat(((Number)js(window,"document.querySelector('#editor p').querySelectorAll('span').length")).intValue()).isZero();
                js(window,"window.studio.html()");assertThat(((Number)js(window,"document.querySelectorAll('#editor [data-changed]').length")).intValue()).isEqualTo(2);assertThat(js(window,"document.querySelector('#editor table').hasAttribute('data-changed')")).isEqualTo(false);
                js(window,"window.chip=document.querySelector('#editor td .variable');window.rightClick=function(){return chip.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:dropX,clientY:dropY}))};");
                assertThat(js(window,"rightClick()")).isEqualTo(false);assertThat(js(window,"document.querySelector('#variable-menu button').textContent").toString()).isEqualTo("Borrar variable de esta plantilla");
                js(window,"document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))");assertThat(js(window,"document.getElementById('variable-menu').style.display")).isEqualTo("none");
                js(window,"rightClick();document.body.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}))");assertThat(js(window,"document.getElementById('variable-menu').style.display")).isEqualTo("none");
                js(window,"rightClick();window.dispatchEvent(new Event('scroll'))");assertThat(js(window,"document.getElementById('variable-menu').style.display")).isEqualTo("none");
                js(window,"rightClick();document.querySelector('#variable-menu button').click()");assertThat(((Number)js(window,"document.querySelectorAll('#editor .variable').length")).intValue()).isEqualTo(1);assertThat(js(window,"document.querySelector('#editor td').textContent").toString()).isEqualTo("Destino");
                js(window,"transfer.value='definition:"+fresh.id()+":"+fresh.key()+"';dragEvent('drop',target)");assertThat(((Number)js(window,"document.querySelectorAll('#editor .variable[data-key=nuevo]').length")).intValue()).isEqualTo(1);assertThat(store.variables(template.id())).extracting(Variable::key).contains("nuevo");
                js(window,"window.studio.showDrop(dropX,dropY);document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));window.javaBridge=window.realBridge;");assertThat(js(window,"document.getElementById('drop-caret').style.display")).isEqualTo("none");
                XWPFDocument original=value(window,"document");String intact=original.getParagraphs().get(1).getCTP().xmlText();invoke(window,"syncEditor");XWPFDocument result=value(window,"document");assertThat(result.getParagraphs().get(1).getCTP().xmlText()).isEqualTo(intact);assertThat(result.getTables().get(0).getText()).contains("{{nuevo}}").doesNotContain("{{nombre}}");assertThat(result.getParagraphs().get(2).getText()).contains("{{nombre}}");return null;
            });
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void pointerDragMovesTheSameChipCancelsWithEscapeAndKeepsTheGuideCompact()throws Exception{
        DocumentStore store=new DocumentStore(temp);byte[] bytes;
        try(XWPFDocument document=new XWPFDocument()){
            document.createParagraph().createRun().setText("{{nombre}}");document.createParagraph().createRun().setText("Destino");XWPFParagraph untouched=document.createParagraph();untouched.setStyle("Heading1");untouched.createRun().setText("Intacto");bytes=DocumentEngine.bytes(document);
        }
        Template template=store.saveTemplate(new Template(0,"Puntero",bytes,bytes));
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{
            awaitEditor(window);
            fx(()->{
                js(window,"window.studio.zoom(1);window.realBridge=javaBridge;window.bridgeCalls=0;window.javaBridge={markDirty:function(){bridgeCalls++;realBridge.markDirty()},pageMoved:function(){},tableSelected:function(){},imageSelected:function(){}};window.chip=document.querySelector('#editor .variable');window.source=chip.closest('p');window.target=document.querySelectorAll('#editor p')[1];target.scrollIntoView({block:'center'});window.mouse=function(type,node,x,y){return node.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,button:0,buttons:type==='mouseup'?0:1,clientX:x,clientY:y}))};window.start=function(){var box=chip.getBoundingClientRect();window.startX=box.left+box.width/2;window.startY=box.top+box.height/2;return mouse('mousedown',chip,startX,startY)};var text=target.querySelector('span').firstChild,r=document.createRange();r.setStart(text,2);r.collapse(true);var box=r.getBoundingClientRect();window.dropX=box.left;window.dropY=box.top+box.height/2;");
                assertThat(js(window,"start()")).isEqualTo(false);js(window,"mouse('mousemove',document,startX+3,startY)");assertThat(js(window,"document.getElementById('variable-ghost').style.display")).isNotEqualTo("block");
                assertThat(js(window,"chip.dispatchEvent(new Event('dragstart',{bubbles:true,cancelable:true}))")).isEqualTo(false);
                js(window,"window.rafCalls=0;window.realRaf=requestAnimationFrame;window.requestAnimationFrame=function(f){rafCalls++;return realRaf.call(window,f)};for(var i=0;i<50;i++)mouse('mousemove',document,dropX,dropY)");
                assertThat(((Number)js(window,"rafCalls")).intValue()).isEqualTo(1);assertThat(((Number)js(window,"bridgeCalls")).intValue()).isZero();assertThat(js(window,"getComputedStyle(chip).opacity")).isEqualTo("0.35");return null;
            });
            awaitJs(window,"document.getElementById('drop-caret').style.display==='block'");
            fx(()->{
                assertThat(((Number)js(window,"document.getElementById('drop-caret').getBoundingClientRect().width")).doubleValue()).isEqualTo(2);assertThat(((Number)js(window,"document.getElementById('drop-caret').getBoundingClientRect().height")).doubleValue()).isBetween(8.0,35.0);assertThat(js(window,"document.getElementById('variable-ghost').style.transform").toString()).startsWith("translate3d(");
                js(window,"document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));mouse('mouseup',document,dropX,dropY)");
                assertThat(js(window,"chip.closest('p')===source")).isEqualTo(true);assertThat(js(window,"getComputedStyle(chip).opacity")).isEqualTo("1");assertThat(js(window,"document.getElementById('variable-ghost').style.display")).isEqualTo("none");assertThat(((Number)js(window,"bridgeCalls")).intValue()).isZero();assertThat(((Number)js(window,"document.querySelectorAll('[data-changed]').length")).intValue()).isZero();
                js(window,"start();mouse('mousemove',document,dropX,dropY);mouse('mouseup',document,dropX,dropY)");
                assertThat(js(window,"target.querySelector('.variable')===chip")).isEqualTo(true);assertThat(((Number)js(window,"document.querySelectorAll('#editor .variable').length")).intValue()).isEqualTo(1);assertThat(js(window,"source.querySelector('span')===null")).isEqualTo(true);assertThat(((Number)js(window,"bridgeCalls")).intValue()).isEqualTo(1);assertThat(((Number)js(window,"document.querySelectorAll('#editor [data-changed]').length")).intValue()).isEqualTo(2);assertThat(js(window,"document.getElementById('drop-caret').style.display")).isEqualTo("none");
                assertThat(js(window,"chip.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2,clientX:dropX,clientY:dropY}))")).isEqualTo(false);assertThat(js(window,"document.getElementById('variable-menu').style.display")).isEqualTo("block");
                js(window,"document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));window.javaBridge=realBridge");XWPFDocument document=value(window,"document");String intact=document.getParagraphs().get(2).getCTP().xmlText();invoke(window,"syncEditor");XWPFDocument result=value(window,"document");assertThat(result.getParagraphs().get(0).getText()).isBlank();assertThat(result.getParagraphs().get(1).getText()).isEqualTo("De{{nombre}}stino");assertThat(result.getParagraphs().get(2).getCTP().xmlText()).isEqualTo(intact);return null;
            });
            fx(()->{js(window,"window.empty=document.createElement('p');empty.style.height='600px';document.getElementById('editor').appendChild(empty);window.realCaret=document.caretRangeFromPoint;document.caretRangeFromPoint=function(){var r=document.createRange();r.setStart(empty,0);r.collapse(true);return r};window.studio.showDrop(dropX,dropY)");return null;});
            awaitJs(window,"document.getElementById('drop-caret').style.display==='block'");
            fx(()->{assertThat(((Number)js(window,"document.getElementById('drop-caret').getBoundingClientRect().height")).doubleValue()).isBetween(8.0,35.0);js(window,"empty.remove();document.caretRangeFromPoint=realCaret;window.studio.hideDrop()");return null;});
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void ribbonKeepsThreeCompactTabsWithFeatherIconsAndDescriptiveTooltipsInBothThemes()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template template=template(store,"Cinta","Texto de prueba");
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{
            awaitEditor(window);
            long visibleDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(fx(()->((StackPane)value(window,"workspace")).getChildren().get(0).getOpacity())<.99&&System.nanoTime()<visibleDeadline)Thread.sleep(25);
            fx(()->{
                TabPane ribbon=(TabPane)window.root().lookup(".editor-ribbon");assertThat(ribbon.getTabs()).extracting(Tab::getText).containsExactly("Inicio","Insertar","Vista");assertThat(((ComboBox<Template>)value(window,"templateChoice")).getParent()).isNotNull();assertThat(((Label)value(window,"editorSaveStatus")).getText()).isEqualTo("Guardado");
                for(boolean dark:List.of(false,true)){
                    Application.setUserAgentStylesheet(dark?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());
                    for(Tab tab:ribbon.getTabs()){
                        ribbon.getSelectionModel().select(tab);window.root().applyCss();window.root().layout();assertThat(tab.getStyleClass()).contains("ribbon-tab");assertThat(tab.getTooltip().getText()).contains(" — ");assertThat(ribbon.getHeight()).isLessThanOrEqualTo(100);
                        javafx.scene.Parent row=(javafx.scene.Parent)((ScrollPane)tab.getContent()).getContent();assertThat(row.lookupAll(".ribbon-group")).isNotEmpty();assertThat(row.lookupAll(".ribbon-sep")).isNotEmpty();
                        for(Node node:row.lookupAll(".ribbon-btn")){
                            Control control=(Control)node;assertThat(control.getTooltip()).as(control.getAccessibleText()).isNotNull();assertThat(control.getTooltip().getText()).contains(" — ");assertThat(control.getHeight()).isLessThanOrEqualTo(32);
                            if(control instanceof ButtonBase button)assertThat(button.getGraphic()).isInstanceOf(org.kordamp.ikonli.javafx.FontIcon.class);
                            if(control instanceof ComboBox<?> choice)assertThat(choice.getButtonCell().getPadding().getTop()+choice.getButtonCell().getPadding().getBottom()).isLessThanOrEqualTo(4);
                            if(control instanceof MenuButton menu){Label label=(Label)menu.lookup(".label");assertThat(label.getHeight()-label.getPadding().getTop()-label.getPadding().getBottom()).isGreaterThanOrEqualTo(12);}
                            if(control instanceof MenuButton menu)for(MenuItem item:menu.getItems()){
                                if(item instanceof CustomMenuItem custom&&custom.getContent() instanceof Label label){assertThat(label.getGraphic()).isInstanceOf(org.kordamp.ikonli.javafx.FontIcon.class);assertThat(((Tooltip)item.getProperties().get("ribbon-tooltip")).getText()).contains(" — ");}
                            }
                        }
                        snapshot(window,"ribbon-"+tab.getText()+(dark?"-dark":"-light"));
                    }
                }
                javafx.scene.Parent insert=(javafx.scene.Parent)((ScrollPane)ribbon.getTabs().get(1).getContent()).getContent();assertThat(insert.lookupAll(".menu-button").stream().filter(n->n instanceof MenuButton).map(n->((MenuButton)n).getText()).toList()).contains("Tabla","Imagen","Encabezado","Pie de página");
                ToggleButton variables=value(window,"variablesView");Button palette=value(window,"paletteToggle");variables.fire();assertThat(variables.isSelected()).isFalse();assertThat(((VBox)value(window,"palettePanel")).getPrefWidth()).isEqualTo(68);palette.fire();assertThat(variables.isSelected()).isTrue();
                ((ToggleButton)value(window,"guidesView")).fire();assertThat(js(window,"document.getElementById('sheet').classList.contains('page-guides')")).isEqualTo(true);((ToggleButton)value(window,"rulersView")).fire();assertThat(js(window,"document.body.classList.contains('hide-rulers')")).isEqualTo(true);
                ribbon.getSelectionModel().selectFirst();javafx.scene.Parent home=(javafx.scene.Parent)((ScrollPane)ribbon.getTabs().get(0).getContent()).getContent();
                js(window,"var p=document.querySelector('#editor p'),r=document.createRange();r.selectNodeContents(p);var selection=window.getSelection();selection.removeAllRanges();selection.addRange(r);document.dispatchEvent(new Event('selectionchange'))");
                ComboBox<Integer> size=(ComboBox<Integer>)home.lookupAll(".ribbon-btn").stream().filter(n->n instanceof ComboBox<?> c&&c.getTooltip().getText().startsWith("Tamaño de fuente")).findFirst().orElseThrow();size.setValue(24);
                ((Button)home.lookupAll(".ribbon-btn").stream().filter(n->n instanceof Button b&&b.getTooltip().getText().startsWith("Viñetas —")).findFirst().orElseThrow()).fire();assertThat(js(window,"document.querySelector('#editor p').dataset.list")).isEqualTo("bullet");
                js(window,"var r=document.createRange();r.selectNodeContents(document.querySelector('#editor p'));r.collapse(false);var selection=window.getSelection();selection.removeAllRanges();selection.addRange(r);document.dispatchEvent(new Event('selectionchange'))");
                ((Button)insert.lookupAll(".ribbon-btn").stream().filter(n->n instanceof Button b&&b.getTooltip().getText().startsWith("Línea separadora —")).findFirst().orElseThrow()).fire();assertThat(js(window,"document.querySelector('#editor [data-rule]')!==null")).isEqualTo(true);
                invoke(window,"syncEditor");XWPFDocument formatted=value(window,"document");assertThat(formatted.getParagraphs().get(0).getNumFmt()).isEqualTo("bullet");assertThat(formatted.getParagraphs().get(0).getRuns().get(0).getFontSize()).isEqualTo(24);assertThat(formatted.getParagraphs().stream().anyMatch(p->p.getBorderBottom()==org.apache.poi.xwpf.usermodel.Borders.SINGLE)).isTrue();
                invoke(window,"setEditorZoom",new Class<?>[]{double.class},1.0);assertThat(((Label)value(window,"zoomPercent")).getText()).isEqualTo("100%");invoke(window,"adjustEditorZoom",new Class<?>[]{double.class},.1);assertThat(((Label)value(window,"zoomPercent")).getText()).isEqualTo("110%");return null;
            });
        }finally{fx(()->{((Stage)value(window,"stage")).close();Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());return null;});}
    }

    @Test void webkitEnterSplitsKeepEarlierParagraphsAndRepeatedEmptyLinesWhenSaved()throws Exception{
        DocumentStore store=new DocumentStore(temp);byte[] bytes;try(XWPFDocument document=new XWPFDocument()){document.createParagraph().createRun().setText("Línea superior");document.createParagraph().createRun().setText("Arriba abajo");document.createParagraph().createRun().setText("Última");bytes=DocumentEngine.bytes(document);}Template template=store.saveTemplate(new Template(0,"Enter",bytes,bytes));
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{awaitEditor(window);fx(()->{
            js(window,"var editor=document.getElementById('editor');editor.focus();var text=editor.querySelectorAll('p')[1].querySelector('span').firstChild;var r=document.createRange();r.setStart(text,6);r.collapse(true);var s=window.getSelection();s.removeAllRanges();s.addRange(r);editor.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));document.execCommand('insertParagraph');editor.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));document.execCommand('insertParagraph');window.studio.html()");
            assertThat(((Number)js(window,"document.querySelectorAll('#editor [data-new-block]').length")).intValue()).isGreaterThanOrEqualTo(2);assertThat(js(window,"(function(){var seen={};return Array.from(document.querySelectorAll('#editor [data-block-index]')).every(function(p){var id=p.getAttribute('data-block-index');if(seen[id])return false;seen[id]=true;return true})})()")).isEqualTo(true);
            invoke(window,"saveTemplate");try(XWPFDocument result=DocumentEngine.open(store.templates().get(0).edited())){assertThat(result.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Línea superior","Arriba",""," abajo","Última");}return null;
        });awaitEditor(window);fx(()->{invoke(window,"saveTemplate");try(XWPFDocument result=DocumentEngine.open(store.templates().get(0).edited())){assertThat(result.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Línea superior","Arriba",""," abajo","Última");}return null;});}finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void complementCardsClientExtrasAndGeneratorCompleteAndLearnWithoutDocuments()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Definition name=store.saveDefinition(new Definition(0,"Dirigido","Dirigido","Texto","")),role=store.saveDefinition(new Definition(0,"Cargo","Cargo","Texto","")),object=store.saveDefinition(new Definition(0,"objeto","Objeto","Texto","")),price=store.saveDefinition(new Definition(0,"precio","Precio","Texto",""));
        Template template=template(store,"Complementos","{{Dirigido}} {{Cargo}} {{objeto}} {{precio}}");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));Template single=template(store,"Solo objeto","{{objeto}}");store.setTemplateGroups(single.id(),List.of(group.id()));for(Definition d:List.of(name,role,object,price))store.bindDefinition(template.id(),d.id(),false,(int)d.id());
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();return created;});
        try{fx(()->{
            invoke(window,"showDefinitions");((ListView<Definition>)value(window,"definitionList")).getSelectionModel().select(name);((ComboBox<Definition>)value(window,"complementChoice")).getSelectionModel().select(role);assertThat(((CheckBox)value(window,"complementBoth")).isSelected()).isTrue();invoke(window,"linkComplement");store.saveComplement(object.id(),price.id(),true);
            window.root().applyCss();window.root().layout();BorderPane page=value(window,"definitionsPage");assertThat(page.lookupAll(".binding-card")).hasSize(4);Node api=((VBox)value(window,"definitionApiOptions")).getParent();Node complement=((VBox)value(window,"complementLinks")).getParent();assertThat(api.getParent()).isSameAs(complement.getParent());assertThat(api.getParent()).isInstanceOf(HBox.class);snapshotConfigured(window,"complementos-variables");
            invoke(window,"showContacts");invoke(window,"newContact");((TextField)value(window,"contactGiven")).setText("Luisito");((ComboBox<Definition>)value(window,"contactExtraChoice")).getSelectionModel().select(role);((TextField)value(window,"contactExtraValue")).setText("Presidente");invoke(window,"writeContactExtra",new Class<?>[]{String.class,String.class,boolean.class},"Cargo","Presidente",false);invoke(window,"saveContact");assertThat(data.contacts()).hasSize(1);assertThat(data.contacts().get(0).number()).isEmpty();assertThat(((TableView<?>)value(window,"contactVariableTable")).getItems()).hasSize(1);
            invoke(window,"writeContactExtra",new Class<?>[]{String.class,String.class,boolean.class},"Asociacion","Los Pinos",false);assertThat(data.contacts().get(0).extrasJson()).contains("Los Pinos");snapshotConfigured(window,"complementos-clientes");
            invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().select(template);invoke(window,"buildDynamicForm");Map<String,Control> inputs=value(window,"inputs");TextField source=(TextField)inputs.get("Dirigido");source.setText("Luisito");source.fireEvent(new javafx.event.ActionEvent());assertThat(((TextField)inputs.get("Cargo")).getText()).isEqualTo("Presidente");
            ((TextField)inputs.get("Cargo")).setText("Secretario");source.fireEvent(new javafx.event.ActionEvent());assertThat(((TextField)inputs.get("Cargo")).getText()).isEqualTo("Secretario");((TextField)inputs.get("objeto")).setText("Silla");((TextField)inputs.get("precio")).setText("120.00");invoke(window,"makePreview");assertThat(((byte[])value(window,"generatedBytes"))).isNotEmpty();assertThat(data.contacts().get(0).extrasJson()).contains("Secretario","Los Pinos");snapshotConfigured(window,"complementos-generacion");
            invoke(window,"buildDynamicForm");inputs=value(window,"inputs");((TextField)inputs.get("Cargo")).setText("Secretario");((TextField)inputs.get("Cargo")).fireEvent(new javafx.event.ActionEvent());assertThat(((TextField)inputs.get("Dirigido")).getText()).isEqualTo("Luisito");((TextField)inputs.get("precio")).setText("120.00");((TextField)inputs.get("precio")).fireEvent(new javafx.event.ActionEvent());assertThat(((TextField)inputs.get("objeto")).getText()).isEqualTo("Silla");
            ((TextField)inputs.get("objeto")).clear();((TextField)inputs.get("precio")).clear();((TextField)inputs.get("objeto")).setText("Si");((TextField)inputs.get("objeto")).requestFocus();return null;
        });
        fx(()->{Map<String,Control> inputs=value(window,"inputs");TextField field=(TextField)inputs.get("objeto");Map<String,ContextMenu> menus=value(window,"complementMenus");invoke(window,"showComplementSuggestions",new Class<?>[]{String.class,TextInputControl.class,ContextMenu.class},"objeto",field,menus.get("objeto"));assertThat(menus.get("objeto").getItems()).hasSize(1);assertThat(menus.get("objeto").getItems().get(0).getText()).contains("Silla","Precio","120.00");menus.get("objeto").getItems().get(0).fire();assertThat(field.getText()).isEqualTo("Silla");assertThat(((TextField)inputs.get("precio")).getText()).isEqualTo("120.00");
            store.saveComplement(object.id(),price.id(),false);invoke(window,"buildDynamicForm");inputs=value(window,"inputs");((TextField)inputs.get("precio")).setText("120.00");((TextField)inputs.get("precio")).fireEvent(new javafx.event.ActionEvent());assertThat(((TextField)inputs.get("objeto")).getText()).isEmpty();
            ((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().select(single);Map<String,Control> only=value(window,"inputs");assertThat(only.keySet()).containsExactly("objeto");assertThat(((Map<?,?>)value(window,"complementTargets"))).isEmpty();((TextField)only.get("objeto")).setText("Silla");invoke(window,"makePreview");assertThat(((byte[])value(window,"generatedBytes"))).isNotEmpty();return null;});
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void generatorSeparatesIndependentFieldsAndOrdersClientCardsByDescendingFieldCount()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);
        Definition dni=store.saveDefinition(new Definition(0,"DNI","DNI","DNI","")),name=store.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto","")),address=store.saveDefinition(new Definition(0,"Direccion","Dirección","Texto corto",""));
        String annual="Año institucional",phrase="Frase del documento";
        Definition year=store.saveDefinition(new Definition(0,"Nombre_Anio","Nombre del año","Texto corto",annual,false,"dd/MM/yyyy",false));
        Definition global=store.saveDefinition(new Definition(0,"Frase","Frase","Texto corto",phrase,false,"dd/MM/yyyy",false));
        data.saveApiBinding(new ApiBinding(dni.id(),"DNI","field:numero"));data.saveApiBinding(new ApiBinding(name.id(),"DNI","field:nombre_completo"));data.saveApiBinding(new ApiBinding(address.id(),"DNI","field:direccion"));
        // Legacy prefixed global fields still belong exclusively to the independent card.
        Template template=template(store,"Tarjetas","{{Persona_1.Nombre_Anio}} {{Persona_1.DNI}} {{Persona_1.nombre}} {{Frase}} {{Persona_2.DNI}} {{Persona_2.nombre}} {{Persona_2.Direccion}}");
        store.bindDefinition(template.id(),year.id(),"Persona_1.Nombre_Anio",false,1);data.saveApiBinding(new ApiBinding(year.id(),"DNI","field:nombre_completo"));
        store.addPersona(template.id(),"Dirigido");store.addPersona(template.id(),"Solicitante");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));
        data.saveContactBinding(new ContactBinding(0,template.id(),"solicitante","Persona","number","Persona_2.DNI"));
        data.saveContact(new Contact(0,"Persona","Ana","Pérez","DNI","11111111","Lima","","{}"));data.saveContact(new Contact(0,"Persona","Luis","Ruiz","DNI","22222222","Cusco","","{}"));
        fx(()->{Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));stage.show();try{
            invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().select(template);invoke(window,"buildDynamicForm");
            VBox form=value(window,"generatedForm");List<VBox> cards=form.getChildren().stream().filter(n->n.getStyleClass().contains("binding-card")).map(n->(VBox)n).toList();
            assertThat(cards).extracting(card->((Label)card.getChildren().get(0)).getText()).containsExactly("Solicitante","Dirigido","Variables independientes");
            assertThat(cards).extracting(card->((GridPane)card.getChildren().get(1)).getChildren().size()).containsExactly(3,2,2);
            assertThat(cards.get(0).getProperties().get("persona-key")).isEqualTo("Persona_2");assertThat(cards.get(1).getProperties().get("persona-key")).isEqualTo("Persona_1");assertThat(cards.get(2).getProperties()).doesNotContainKey("persona-key");
            assertThat(form.getChildren().get(form.getChildren().size()-1)).isSameAs(cards.get(2));
            Map<String,Control> fields=value(window,"inputs");for(String key:List.of("Persona_2.DNI","Persona_2.nombre","Persona_2.Direccion"))assertThat(formCard(fields.get(key))).isSameAs(cards.get(0));for(String key:List.of("Persona_1.DNI","Persona_1.nombre"))assertThat(formCard(fields.get(key))).isSameAs(cards.get(1));for(String key:List.of("Nombre_Anio","Frase"))assertThat(formCard(fields.get(key))).isSameAs(cards.get(2));
            assertThat(buttonLabels(cards.get(0))).containsExactly("Consultar API");assertThat(buttonLabels(cards.get(1))).containsExactly("Consultar API");assertThat(buttonLabels(cards.get(2))).isEmpty();assertThat(((TextField)fields.get("Nombre_Anio")).getText()).isEqualTo(annual);
            ((TextField)fields.get("Persona_2.DNI")).setText("11111111");assertThat(((TextField)fields.get("Persona_2.nombre")).getText()).isEqualTo("Ana Pérez");assertThat(((TextField)fields.get("Persona_2.Direccion")).getText()).isEqualTo("Lima");assertThat(((TextField)fields.get("Persona_1.nombre")).getText()).isEmpty();
            ((TextField)fields.get("Persona_1.DNI")).setText("22222222");assertThat(((TextField)fields.get("Persona_1.nombre")).getText()).isEqualTo("Luis Ruiz");
            for(VBox card:cards.subList(0,2))((Button)card.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Consultar API")).findFirst().orElseThrow()).fire();
            Map<String,String> values=Map.of("nombre_completo","Ana API","direccion","Tacna");LookupRouter.Result result=new LookupRouter.Result("DNI","11111111","Prueba",values,values.keySet(),Set.of(),LookupRouter.Status.SUCCESS,"",values);invoke(window,"applyApiBindings",new Class<?>[]{long.class,String.class,LookupRouter.Result.class},template.id(),"Persona_2.DNI",result);
            assertThat(((TextField)fields.get("Persona_2.nombre")).getText()).isEqualTo("Ana API");assertThat(((TextField)fields.get("Persona_1.nombre")).getText()).isEqualTo("Luis Ruiz");assertThat(((TextField)fields.get("Nombre_Anio")).getText()).isEqualTo(annual);assertThat(((TextField)fields.get("Frase")).getText()).isEqualTo(phrase);
            ((TextField)fields.get("Persona_1.nombre")).setText("Luis editado");invoke(window,"makePreview");assertThat((byte[])value(window,"generatedBytes")).isNotEmpty();assertThat(data.contacts()).hasSize(2);
            Contact applicant=data.matchingContact("Persona","DNI","11111111"),recipient=data.matchingContact("Persona","DNI","22222222");assertThat(applicant.given()).isEqualTo("Ana API");assertThat(applicant.address()).isEqualTo("Tacna");assertThat(recipient.given()).isEqualTo("Luis editado");for(Contact contact:data.contacts())assertThat(contact.extrasJson()).doesNotContain("Nombre_Anio","Frase",annual,phrase);
        }finally{((Map<String,javafx.animation.PauseTransition>)value(window,"lookupDelays")).values().forEach(javafx.animation.PauseTransition::stop);stage.close();}return null;});
    }

    @Test void participantsIsolateLocalApiComplementsContactsAndFormattedCurrentDates()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Definition dni=store.saveDefinition(new Definition(0,"DNI","DNI","Mixto","")),name=store.saveDefinition(new Definition(0,"Nombres_Com","Nombres completos","Texto","")),address=store.saveDefinition(new Definition(0,"Direccion","Dirección","Texto","")),role=store.saveDefinition(new Definition(0,"Cargo","Cargo","Texto","")),date=store.saveDefinition(new Definition(0,"Fecha","Fecha","Fecha","",true,"EEEE, d 'de' MMMM 'de' yyyy"));
        data.saveApiBinding(new ApiBinding(dni.id(),"DNI","field:numero"));data.saveApiBinding(new ApiBinding(name.id(),"DNI","field:nombre_completo"));data.saveApiBinding(new ApiBinding(address.id(),"DNI","field:direccion"));store.saveComplement(name.id(),role.id(),true);
        Template template=template(store,"Personas","{{DNI}} {{Fecha}} {{Persona_1.DNI}} {{Persona_1.Nombres_Com}} {{Persona_1.Direccion}} {{Persona_1.Cargo}} {{Persona_1.Fecha}} {{Persona_2.DNI}} {{Persona_2.Nombres_Com}} {{Persona_2.Direccion}} {{Persona_2.Cargo}}");store.addPersona(template.id(),"Solicitante");store.addPersona(template.id(),"Dirigido");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));
        data.saveContact(new Contact(0,"Persona","Ana","Pérez","DNI","11111111","Lima","","{\"Cargo\":\"Directora\"}"));data.saveContact(new Contact(0,"Persona","Luis","Ruiz","DNI","22222222","Cusco","","{\"Cargo\":\"Secretario\"}"));data.saveContact(new Contact(0,"Persona","General","","DNI","33333333","Piura","","{}"));
        fx(()->{Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));stage.show();try{
            invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().select(template);invoke(window,"buildDynamicForm");Map<String,Control> fields=value(window,"inputs");assertThat(((TextField)fields.get("Fecha")).getText()).isEqualTo(date.currentDate());assertThat(((TextField)fields.get("Persona_1.Fecha")).getText()).isEqualTo(date.currentDate());
            VBox form=value(window,"generatedForm");assertThat(form.getChildren().stream().filter(n->n.getProperties().containsKey("persona-key")).count()).isEqualTo(3);assertThat(form.getChildren().stream().filter(n->n instanceof VBox).flatMap(n->((VBox)n).getChildren().stream()).filter(n->n instanceof Label).map(n->((Label)n).getText()).toList()).containsExactly("Solicitante","Dirigido","Cliente / Solicitante");
            ((TextField)fields.get("Persona_1.DNI")).setText("11111111");assertThat(((TextField)fields.get("Persona_1.Nombres_Com")).getText()).isEqualTo("Ana Pérez");assertThat(((TextField)fields.get("Persona_1.Direccion")).getText()).isEqualTo("Lima");assertThat(((TextField)fields.get("Persona_2.Nombres_Com")).getText()).isEmpty();
            ((TextField)fields.get("Persona_2.DNI")).setText("22222222");assertThat(((TextField)fields.get("Persona_2.Nombres_Com")).getText()).isEqualTo("Luis Ruiz");((TextField)fields.get("DNI")).setText("33333333");assertThat(((TextField)fields.get("Persona_1.Nombres_Com")).getText()).isEqualTo("Ana Pérez");assertThat(((TextField)fields.get("Persona_2.Nombres_Com")).getText()).isEqualTo("Luis Ruiz");
            ((TextField)fields.get("Persona_1.Cargo")).clear();((TextField)fields.get("Persona_1.Nombres_Com")).fireEvent(new javafx.event.ActionEvent());assertThat(((TextField)fields.get("Persona_1.Cargo")).getText()).isEqualTo("Directora");assertThat(((TextField)fields.get("Persona_2.Cargo")).getText()).isEqualTo("Secretario");
            Map<String,String> resultValues=Map.of("nombre_completo","Luis Nuevo","direccion","Arequipa");LookupRouter.Result result=new LookupRouter.Result("DNI","22222222","Prueba",resultValues,resultValues.keySet(),Set.of(),LookupRouter.Status.SUCCESS,"",resultValues);invoke(window,"applyApiBindings",new Class<?>[]{long.class,String.class,LookupRouter.Result.class},template.id(),"Persona_2.DNI",result);assertThat(((TextField)fields.get("Persona_2.Nombres_Com")).getText()).isEqualTo("Luis Nuevo");assertThat(((TextField)fields.get("Persona_1.Nombres_Com")).getText()).isEqualTo("Ana Pérez");
            ((TextField)fields.get("Persona_1.Cargo")).setText("Presidenta");((TextField)fields.get("Fecha")).setText("Fecha manual");invoke(window,"makePreview");byte[] generated=value(window,"generatedBytes");assertThat(generated).isNotEmpty();try(XWPFDocument output=DocumentEngine.open(generated)){assertThat(output.getParagraphs().get(0).getText()).contains("Fecha manual",date.currentDate(),"11111111","22222222","Ana Pérez","Luis Nuevo");assertThat(DocumentEngine.markers(output)).isEmpty();}
            Contact ana=data.matchingContact("Persona","DNI","11111111"),luis=data.matchingContact("Persona","DNI","22222222");assertThat((ana.given()+" "+ana.family()).trim()).isEqualTo("Ana Pérez");assertThat((luis.given()+" "+luis.family()).trim()).isEqualTo("Luis Nuevo");assertThat(ana.extrasJson()).contains("Presidenta").doesNotContain("Luis Nuevo","Secretario","22222222");assertThat(luis.extrasJson()).contains("Secretario","Arequipa").doesNotContain("Ana Pérez","Presidenta","11111111");assertThat(data.contacts()).hasSize(3);snapshotConfigured(window,"personas-generador");
        }finally{stage.close();}return null;});
    }

    @Test void editorPersonContextQualifiesChipsAndAutomaticDateCardPersistsItsSettings()throws Exception{
        DocumentStore store=new DocumentStore(temp);Definition dni=store.saveDefinition(new Definition(0,"DNI","DNI","Mixto","")),date=store.saveDefinition(new Definition(0,"Fecha","Fecha","Fecha",""));Template template=template(store,"Editor personas","Texto ");store.addPersona(template.id(),"Solicitante");store.addPersona(template.id(),"Dirigido");
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{awaitEditor(window);fx(()->{
            ComboBox<DocumentStore.Persona> people=value(window,"editorPersona");assertThat(people.getItems()).extracting(DocumentStore.Persona::displayName).containsExactly("General (sin persona)","Solicitante","Dirigido");people.getSelectionModel().select(2);((ListView<Definition>)value(window,"variableList")).getSelectionModel().select(dni);
            js(window,"var p=document.querySelector('#editor p'),r=document.createRange();r.selectNodeContents(p);r.collapse(false);var selection=window.getSelection();selection.removeAllRanges();selection.addRange(r);document.dispatchEvent(new Event('selectionchange'))");invoke(window,"insertSelected");assertThat(js(window,"document.querySelector('#editor .variable').dataset.key")).isEqualTo("Persona_2.DNI");assertThat(js(window,"document.querySelector('#editor .variable').textContent")).isEqualTo("⟦Dirigido · DNI⟧");
            people.getSelectionModel().select(1);invoke(window,"insertSelected");assertThat(((Number)js(window,"document.querySelectorAll('#editor .variable').length")).intValue()).isEqualTo(2);assertThat(store.variables(template.id())).extracting(Variable::key).containsExactly("Persona_2.DNI","Persona_1.DNI");invoke(window,"saveTemplate");
            AtomicReference<Throwable> problem=answerDialog(ButtonType.OK,pane->((TextField)pane.lookup(".text-field")).setText("Arrendador"));invoke(window,"addEditorPersona");assertThat(problem.get()).isNull();assertThat(people.getValue().personaKey()).isEqualTo("Persona_3");problem=answerDialog(ButtonType.OK,pane->((TextField)pane.lookup(".text-field")).setText("Arrendatario"));invoke(window,"renameEditorPersona");assertThat(problem.get()).isNull();assertThat(store.personas(template.id()).get(2).displayName()).isEqualTo("Arrendatario");
            invoke(window,"showDefinitions");((ListView<Definition>)value(window,"definitionList")).getSelectionModel().select(date);CheckBox automatic=value(window,"autoCurrentDate");ComboBox<String> format=value(window,"currentDateFormat");assertThat(format.isDisabled()).isTrue();automatic.setSelected(true);format.setValue("d 'de' MMMM 'de' yyyy");assertThat(format.isDisabled()).isFalse();String today=java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(format.getValue(),Locale.forLanguageTag("es-PE")));assertThat(((Label)value(window,"currentDatePreview")).getText()).contains(today);invoke(window,"saveDefinition");Definition saved=store.definitions().stream().filter(d->d.id()==date.id()).findFirst().orElseThrow();assertThat(saved.autoCurrentDate()).isTrue();assertThat(saved.currentDate()).isEqualTo(today);snapshotConfigured(window,"fecha-automatica");return null;
        });awaitEditor(window);fx(()->{invoke(window,"showEditor");snapshotConfigured(window,"personas-editor");ComboBox<DocumentStore.Persona> people=value(window,"editorPersona");people.getSelectionModel().select(people.getItems().stream().filter(p->p.personaKey().equals("Persona_3")).findFirst().orElseThrow());AtomicReference<Throwable> problem=answerDialog(ButtonType.YES,pane->{});invoke(window,"removeEditorPersona");assertThat(problem.get()).isNull();assertThat(store.personas(template.id())).hasSize(2);try(XWPFDocument saved=DocumentEngine.open(store.templates().get(0).edited())){assertThat(DocumentEngine.markers(saved)).containsExactlyInAnyOrder("Persona_1.DNI","Persona_2.DNI");}return null;});}finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }
    @Test void generationShowsCompleteEditableDocumentFromHeaderToFooterAndResetsOnTemplateChange()throws Exception{
        DocumentStore store=new DocumentStore(temp);byte[] bytes;
        try(XWPFDocument doc=new XWPFDocument()){
            var section=doc.getDocument().getBody().addNewSectPr();var size=section.addNewPgSz();size.setW(java.math.BigInteger.valueOf(11910));size.setH(java.math.BigInteger.valueOf(16840));var margins=section.addNewPgMar();margins.setLeft(java.math.BigInteger.valueOf(1440));margins.setRight(java.math.BigInteger.valueOf(1080));margins.setTop(java.math.BigInteger.valueOf(1440));margins.setBottom(java.math.BigInteger.valueOf(1440));margins.setHeader(java.math.BigInteger.valueOf(720));margins.setFooter(java.math.BigInteger.valueOf(720));
            var header=doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph();header.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);header.createRun().setText("MEMBRETE COMPLETO");
            var title=doc.createParagraph();title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);var run=title.createRun();run.setText("Estimado {{nombre}}");run.setBold(true);run.setItalic(true);run.setFontSize(20);
            for(int i=0;i<90;i++){var paragraph=doc.createParagraph();paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH);paragraph.createRun().setText("Párrafo "+i+". "+"Texto continuo para verificar que cada página conserve su contenido. ".repeat(3));}
            var table=doc.createTable(2,2);table.getRow(0).getCell(0).setText("Tabla completa");table.getRow(1).getCell(1).setText("Última celda");doc.createParagraph().createRun().setText("FIN DEL DOCUMENTO");doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("PIE COMPLETO");bytes=DocumentEngine.bytes(doc);
        }
        try(XWPFDocument check=DocumentEngine.open(bytes)){assertThat(new ContinuousDocxEditor(check).previewHtml()).contains("FIN DEL DOCUMENTO");}
        Template first=store.saveTemplate(new Template(0,"A extenso",bytes,bytes)),second=template(store,"B breve","Segunda plantilla");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(first.id(),List.of(group.id()));store.setTemplateGroups(second.id(),List.of(group.id()));store.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto","Ana"));
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showGenerator");((ComboBox<Template>)value(created,"generateTemplate")).getSelectionModel().selectFirst();return created;});
        try{awaitPreview(window);fx(()->{window.root().applyCss();window.root().layout();return null;});Thread.sleep(250);fx(()->{
            assertThat(previewJs(window,"document.querySelector('#editor').contentEditable")).isEqualTo("true");assertThat(previewJs(window,"document.querySelector('#editor p[data-part]').textContent")).isEqualTo("MEMBRETE COMPLETO");assertThat(previewJs(window,"document.querySelector('#editor').textContent").toString()).contains("Estimado Ana","FIN DEL DOCUMENTO","Última celda","PIE COMPLETO");
            assertThat(previewJs(window,"getComputedStyle(document.querySelector('#ruler-x')).display")).isEqualTo("none");assertThat(previewJs(window,"getComputedStyle(document.querySelector('#editor p:not([data-part])')).textAlign")).isEqualTo("center");assertThat(previewJs(window,"getComputedStyle(document.querySelector('#editor p:not([data-part]) span')).fontStyle")).isEqualTo("italic");assertThat(previewJs(window,"getComputedStyle(document.querySelector('#editor p:not([data-part]) span')).fontWeight")).isEqualTo("700");
            assertThat(((Number)previewJs(window,"document.querySelector('#sheet').getBoundingClientRect().width<=document.documentElement.clientWidth?1:0")).intValue()).isEqualTo(1);
            assertThat(previewJs(window,"window.scrollY")).isEqualTo(0);snapshotConfigured(window,"generacion-editable-completa");((TextField)((Map<String,Control>)value(window,"inputs")).get("nombre")).setText("María");invoke(window,"makePreview");return null;
        });awaitPreview(window);fx(()->{assertThat(previewJs(window,"document.querySelector('#editor').textContent").toString()).contains("Estimado María");try(XWPFDocument result=DocumentEngine.open(value(window,"generatedBytes"))){assertThat(result.getParagraphs().get(0).getText()).isEqualTo("Estimado María");}((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().select(second);return null;});awaitPreview(window);fx(()->{assertThat(previewJs(window,"window.scrollY")).isEqualTo(0);assertThat(previewJs(window,"document.querySelector('#editor').textContent")).isEqualTo("Segunda plantilla");return null;});}finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void editorHasNoPageHeaderAndApiFullNameIsNormalizedInFormAndResults()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Definition full=store.saveDefinition(new Definition(0,"nombre_completo","Nombre completo","Texto corto",""));Template template=template(store,"Nombre","{{nombre_completo}}");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));data.saveApiBinding(new ApiBinding(full.id(),"DNI","field:nombre_completo"));
        fx(()->{Stage stage=new Stage();StudioWindow window=new StudioWindow(stage,store);stage.setScene(scene(window));stage.show();try{
            invoke(window,"showEditor");window.root().applyCss();window.root().layout();assertThat(window.root().lookupAll(".pagina-cabecera")).isEmpty();VBox page=(VBox)((StackPane)value(window,"workspace")).getChildren().get(0);assertThat(page.getChildren()).containsExactly((Node)value(window,"editorPage"));
            invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().selectFirst();assertThat(window.root().lookupAll(".pagina-cabecera")).hasSize(1);
            Map<String,String> values=Map.of("nombre_completo","MAYTA AROTAYPE, JHON WILLIAN");LookupRouter.Result result=new LookupRouter.Result("DNI","12345678","Factiliza",values,values.keySet(),Set.of(),LookupRouter.Status.SUCCESS,"",values);invoke(window,"applyApiBindings",new Class<?>[]{long.class,String.class,LookupRouter.Result.class},template.id(),"nombre_completo",result);assertThat(((TextField)((Map<String,Control>)value(window,"inputs")).get("nombre_completo")).getText()).isEqualTo("JHON WILLIAN MAYTA AROTAYPE");invoke(window,"showApi");invoke(window,"showApiResult",new Class<?>[]{LookupRouter.Result.class},result);assertThat(((VBox)value(window,"apiResultBox")).lookupAll(".label").stream().map(n->((Label)n).getText()).toList()).contains("JHON WILLIAN MAYTA AROTAYPE").doesNotContain("MAYTA AROTAYPE, JHON WILLIAN");
        }finally{stage.close();}return null;});
    }
    @Test void clientDocumentsAreDefaultAndGenerationReplacesAnUnmarkedWordWithManualEdits()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);Contact client=data.saveContact(new Contact(0,"Persona","Luisito","","","","","","{}"));
        byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Texto original");bytes=DocumentEngine.bytes(doc);}
        ClientDocument original=data.addClientDocument(client.id(),"Solicitud","solicitud.docx","docx",bytes);
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showContacts");return created;});
        try{
            fx(()->{assertThat((boolean)value(window,"showingClientDetails")).isFalse();assertThat(((ListView<ClientDocument>)value(window,"clientDocumentList")).getItems()).hasSize(1);Button toggle=value(window,"clientDetailsToggle");assertThat(toggle.getText()).isEqualTo("Ver detalles del cliente");toggle.fire();assertThat(toggle.getText()).isEqualTo("Volver a documentos");toggle.fire();invoke(window,"openClientDocumentForUpdate",new Class<?>[]{Contact.class,ClientDocument.class},client,original);return null;});
            awaitPreview(window);
            fx(()->{
                assertThat(((Map<?,?>)value(window,"inputs"))).isEmpty();assertThat(((Label)value(window,"updateDocumentBanner")).getText()).contains("Luisito","Solicitud");
                VBox form=value(window,"generatedForm");assertThat(form.getChildren().stream().filter(n->n instanceof Label).map(n->((Label)n).getText()).toList()).contains("Este documento no contiene variables. Puedes editar su contenido directamente en el panel del documento.");
                previewJs(window,"var p=document.querySelector('#editor p'),r=document.createRange();r.selectNodeContents(p);var sel=window.getSelection();sel.removeAllRanges();sel.addRange(r);document.querySelector('#editor').focus();window.studio.command('bold');document.execCommand('insertText',false,'Texto corregido');document.execCommand('insertParagraph');document.execCommand('insertText',false,'Segunda línea');document.querySelector('#editor').dispatchEvent(new Event('input',{bubbles:true}));");
                invoke(window,"makePreview");assertThat(data.clientDocuments(client.id()).get(0).contentBytes()).isEqualTo(bytes);
                invoke(window,"updateClientDocumentFromGenerator");List<ClientDocument> found=data.clientDocuments(client.id());assertThat(found).hasSize(1);assertThat(found.get(0).id()).isEqualTo(original.id());assertThat(found.get(0).contentBytes()).isNotEqualTo(bytes);
                try(XWPFDocument generated=DocumentEngine.open(found.get(0).contentBytes())){assertThat(generated.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Texto corregido","Segunda línea");assertThat(generated.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();}
                Button collapse=value(window,"generatorVariablesToggle");collapse.fire();assertThat((boolean)value(window,"generatorVariablesCollapsed")).isTrue();assertThat(collapse.getTooltip().getText()).isEqualTo("Mostrar panel de variables");assertThat(((ColumnConstraints)value(window,"generatorFormColumn")).getPrefWidth()).isEqualTo(48);collapse.fire();assertThat((boolean)value(window,"generatorVariablesCollapsed")).isFalse();
                return null;
            });
        }finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void generationKeepsManualChangesWhileFormValuesContinueToReplaceMarkers()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template template=template(store,"Carta editable","Para {{nombre}}.");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));store.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto","Ana"));
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showGenerator");((ComboBox<Template>)value(created,"generateTemplate")).getSelectionModel().selectFirst();return created;});
        try{awaitPreview(window);fx(()->{
            previewJs(window,"var p=document.querySelector('#editor p'),s=document.createElement('span');s.textContent=' Ajuste manual.';s.style.fontStyle='italic';p.appendChild(s);document.querySelector('#editor').dispatchEvent(new Event('input',{bubbles:true}));");
            ((TextField)((Map<String,Control>)value(window,"inputs")).get("nombre")).setText("María");invoke(window,"makePreview");
            try(XWPFDocument generated=DocumentEngine.open(value(window,"generatedBytes"))){assertThat(generated.getParagraphs().get(0).getText()).isEqualTo("Para María. Ajuste manual.");assertThat(generated.getParagraphs().get(0).getRuns().stream().filter(r->r.text().contains("Ajuste manual")).findFirst().orElseThrow().isItalic()).isTrue();}
            ((TextField)((Map<String,Control>)value(window,"inputs")).get("nombre")).setText("Luis");invoke(window,"makePreview");
            try(XWPFDocument generated=DocumentEngine.open(value(window,"generatedBytes"))){assertThat(generated.getParagraphs().get(0).getText()).isEqualTo("Para Luis. Ajuste manual.");}
            return null;
        });}finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    private static Object previewJs(StudioWindow window,String script)throws Exception{return ((javafx.scene.web.WebView)value(window,"preview")).getEngine().executeScript(script);}
    private static void awaitPreview(StudioWindow window)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<deadline){if(fx(()->((javafx.scene.web.WebView)value(window,"preview")).getEngine().getLoadWorker().getState()==javafx.concurrent.Worker.State.SUCCEEDED&&Boolean.TRUE.equals(previewJs(window,"window.previewReady===true"))))return;Thread.sleep(25);}throw new AssertionError("No se cargó la vista previa");}
    @Test void oneEnterAndRepeatedSynchronizationDoNotAddBlankParagraphsOrSpaces()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template template=template(store,"Un Enter","Antes  {{nombre}} después ");StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{awaitEditor(window);fx(()->{js(window,"var editor=document.getElementById('editor'),p=editor.querySelector('p'),r=document.createRange();editor.focus();r.selectNodeContents(p);r.collapse(false);var selection=window.getSelection();selection.removeAllRanges();selection.addRange(r);editor.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));document.execCommand('insertParagraph')");invoke(window,"syncEditor");invoke(window,"syncEditor");XWPFDocument doc=value(window,"document");assertThat(doc.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  {{nombre}} después ","");invoke(window,"saveTemplate");return null;});awaitEditor(window);fx(()->{invoke(window,"saveTemplate");try(XWPFDocument saved=DocumentEngine.open(store.templates().get(0).edited())){assertThat(saved.getParagraphs()).extracting(XWPFParagraph::getText).containsExactly("Antes  {{nombre}} después ","");}return null;});}finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }
    @Test void independentScopeControlsInsertionDragGeneralDefaultsAndExcludesClientReuse()throws Exception{
        DocumentStore store=new DocumentStore(temp);StudioData data=new StudioData(temp);String annual="Año de la Esperanza y el Fortalecimiento de la Democracia";Definition original=store.saveDefinition(new Definition(0,"Nombre_del_Anio","Nombre del Año","Texto corto",annual));Definition dni=store.saveDefinition(new Definition(0,"DNI","DNI","DNI","")),name=store.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto",""));
        Template template=template(store,"Global","{{Persona_1.Nombre_del_Anio}} {{DNI}} {{nombre}} {{Persona_1.DNI}} {{Persona_1.nombre}}");TemplateGroup group=store.saveGroup(0,"Cartas");store.setTemplateGroups(template.id(),List.of(group.id()));store.addPersona(template.id(),"Dirigido");store.bindDefinition(template.id(),original.id(),"Persona_1.Nombre_del_Anio",false,1);data.saveApiBinding(new ApiBinding(original.id(),"DNI","field:nombre_completo"));store.saveComplement(name.id(),original.id(),true);data.saveContactBinding(new ContactBinding(0,template.id(),"viejo","Persona","extra:Nombre_del_Anio","Persona_1.Nombre_del_Anio"));
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();return created;});
        try{fx(()->{
            invoke(window,"showDefinitions");((ListView<Definition>)value(window,"definitionList")).getSelectionModel().select(original);RadioButton client=value(window,"clientScope"),global=value(window,"globalScope");assertThat(client.isSelected()).isTrue();global.fire();assertThat(client.isSelected()).isFalse();invoke(window,"saveDefinition");Definition saved=store.definitions().stream().filter(d->d.id()==original.id()).findFirst().orElseThrow();assertThat(saved.bindToClient()).isFalse();invoke(window,"loadDefinition",new Class<?>[]{Definition.class},saved);assertThat(global.isSelected()).isTrue();window.root().applyCss();window.root().layout();((ScrollPane)((BorderPane)value(window,"definitionsPage")).getCenter()).setVvalue(1);snapshotConfigured(window,"alcance-global");
            invoke(window,"showEditor");invoke(window,"openTemplate",new Class<?>[]{Template.class},store.templates().stream().filter(t->t.id()==template.id()).findFirst().orElseThrow());return null;
        });awaitEditor(window);fx(()->{
            ((ComboBox<DocumentStore.Persona>)value(window,"editorPersona")).getSelectionModel().select(1);Definition saved=store.definitions().stream().filter(d->d.id()==original.id()).findFirst().orElseThrow();((ListView<Definition>)value(window,"variableList")).getSelectionModel().select(saved);assertThat(invoke(window,"editorTokenKey",new Class<?>[]{Definition.class},saved)).isEqualTo("Nombre_del_Anio");
            js(window,"var p=document.querySelector('#editor p'),r=document.createRange();r.selectNodeContents(p);r.collapse(false);var selection=window.getSelection();selection.removeAllRanges();selection.addRange(r);document.dispatchEvent(new Event('selectionchange'))");invoke(window,"insertSelected");assertThat(((Number)js(window,"document.querySelectorAll('#editor .variable[data-key=Nombre_del_Anio]').length")).intValue()).isEqualTo(1);
            assertThat(((StudioWindow.EditorBridge)value(window,"editorBridge")).bindDroppedDefinition(Long.toString(saved.id()),"Nombre_del_Anio")).isTrue();assertThat(((StudioWindow.EditorBridge)value(window,"editorBridge")).bindDroppedDefinition(Long.toString(saved.id()),"Persona_1.Nombre_del_Anio")).isFalse();invoke(window,"saveTemplate");return null;
        });awaitEditor(window);fx(()->{
            invoke(window,"showGenerator");((ComboBox<Template>)value(window,"generateTemplate")).getSelectionModel().selectFirst();Map<String,Control> fields=value(window,"inputs");assertThat(fields).containsKey("Nombre_del_Anio").doesNotContainKey("Persona_1.Nombre_del_Anio");TextField global=(TextField)fields.get("Nombre_del_Anio");assertThat(global.getText()).isEqualTo(annual);VBox parent=formCard(global);assertThat(((Label)parent.getChildren().get(0)).getText()).isEqualTo("Variables independientes");assertThat(parent.getProperties()).doesNotContainKey("persona-key");VBox form=value(window,"generatedForm");assertThat(form.getChildren().stream().filter(n->n.getStyleClass().contains("binding-card")).map(n->((Label)((VBox)n).getChildren().get(0)).getText()).toList()).containsExactly("Cliente / Solicitante","Dirigido","Variables independientes");assertThat(formCard(fields.get("DNI"))).isNotSameAs(parent);assertThat(formCard(fields.get("nombre"))).isSameAs(formCard(fields.get("DNI")));
            ((TextField)fields.get("DNI")).setText("12345678");((TextField)fields.get("nombre")).setText("Ana");((TextField)fields.get("Persona_1.DNI")).setText("87654321");((TextField)fields.get("Persona_1.nombre")).setText("Luis");Map<String,String> values=Map.of("nombre_completo","OTRO NOMBRE");LookupRouter.Result result=new LookupRouter.Result("DNI","12345678","Factiliza",values,values.keySet(),Set.of(),LookupRouter.Status.SUCCESS,"",values);invoke(window,"applyApiBindings",new Class<?>[]{long.class,String.class,LookupRouter.Result.class},template.id(),"DNI",result);assertThat(global.getText()).isEqualTo(annual);assertThat(((Map<String,Set<String>>)value(window,"complementTargets"))).doesNotContainKey("Nombre_del_Anio");invoke(window,"makePreview");
            try(XWPFDocument generated=DocumentEngine.open(value(window,"generatedBytes"))){assertThat(DocumentEngine.markers(generated)).isEmpty();assertThat(generated.getParagraphs().get(0).getText()).contains(annual+" 12345678 Ana 87654321 Luis"+annual);}
            assertThat(data.contacts()).hasSize(2);for(Contact contact:data.contacts())assertThat(contact.extrasJson()).doesNotContain("Nombre_del_Anio",annual);invoke(window,"showContacts");assertThat(((ComboBox<Definition>)value(window,"contactExtraChoice")).getItems()).extracting(Definition::id).doesNotContain(original.id());return null;
        });}finally{fx(()->{((Map<String,javafx.animation.PauseTransition>)value(window,"lookupDelays")).values().forEach(javafx.animation.PauseTransition::stop);((Map<String,javafx.animation.PauseTransition>)value(window,"complementDelays")).values().forEach(javafx.animation.PauseTransition::stop);((Stage)value(window,"stage")).close();return null;});}
    }
    @Test void tabAndShiftTabStayInDocumentNavigateCellsAndPersistRealTabs()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template template;
        try(XWPFDocument doc=new XWPFDocument()){
            doc.createParagraph().createRun().setText("Antes  después ");var table=doc.createTable(1,2);table.getRow(0).getCell(0).setText("Primera");table.getRow(0).getCell(1).setText("Segunda");byte[] bytes=DocumentEngine.bytes(doc);template=store.saveTemplate(new Template(0,"Tabulaciones",bytes,bytes));
        }
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{awaitEditor(window);fx(()->{
            js(window,"window.editor=document.getElementById('editor');window.key=function(key,shift,ctrl,meta){return editor.dispatchEvent(new KeyboardEvent('keydown',{key:key,shiftKey:!!shift,ctrlKey:!!ctrl,metaKey:!!meta,bubbles:true,cancelable:true}))};window.choose=function(node,offset){editor.focus();var r=document.createRange();r.setStart(node,offset);r.collapse(true);var s=window.getSelection();s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'))};choose(editor.querySelector('p span').firstChild,6)");
            assertThat(js(window,"key('Tab')")).isEqualTo(false);assertThat(js(window,"editor.querySelector('p').textContent")).isEqualTo("Antes \t después ");assertThat(js(window,"editor.querySelector('p').dataset.changed")).isEqualTo("true");
            assertThat(js(window,"key('Tab',true)")).isEqualTo(false);assertThat(js(window,"editor.querySelector('p').textContent")).isEqualTo("Antes  después ");
            js(window,"key('Tab');window.first=editor.querySelector('td');choose(first.querySelector('p span').firstChild,0)");
            assertThat(js(window,"key('Tab')")).isEqualTo(false);assertThat(js(window,"window.getSelection().anchorNode.parentElement.closest('td')===editor.querySelectorAll('td')[1]")).isEqualTo(true);
            js(window,"key('Tab',true)");assertThat(js(window,"window.getSelection().anchorNode.parentElement.closest('td')===first")).isEqualTo(true);
            js(window,"key('Tab',true)");assertThat(js(window,"document.activeElement===editor")).isEqualTo(true);
            js(window,"choose(editor.querySelectorAll('td')[1].querySelector('p span').firstChild,0);key('Tab')");assertThat(js(window,"document.activeElement===editor")).isEqualTo(true);
            invoke(window,"saveTemplate");try(XWPFDocument saved=DocumentEngine.open(store.templates().get(0).edited())){assertThat(saved.getParagraphs().get(0).getText()).isEqualTo("Antes \t después ");assertThat(saved.getParagraphs().get(0).getRuns().stream().mapToInt(r->r.getCTR().sizeOfTabArray()).sum()).isEqualTo(1);}return null;
        });}finally{fx(()->{((Stage)value(window,"stage")).close();return null;});}
    }

    @Test void variableFormattingKeyboardUndoAndSelectionSynchronizeHomeWithoutEditingLoops()throws Exception{
        DocumentStore store=new DocumentStore(temp);Template template=template(store,"Formato","Antes {{nombre}} después");
        StudioWindow window=fx(()->{Stage stage=new Stage();StudioWindow created=new StudioWindow(stage,store);stage.setScene(scene(created));stage.show();invoke(created,"showEditor");invoke(created,"openTemplate",new Class<?>[]{Template.class},template);return created;});
        try{awaitEditor(window);fx(()->{
            js(window,"window.editor=document.getElementById('editor');window.key=function(key,shift,ctrl,meta){return editor.dispatchEvent(new KeyboardEvent('keydown',{key:key,shiftKey:!!shift,ctrlKey:ctrl!==false,metaKey:!!meta,bubbles:true,cancelable:true}))};editor.querySelector('.variable').click()");
            assertThat(js(window,"key('b')")).isEqualTo(false);assertThat(js(window,"window.studio.selectionFormat().bold")).isEqualTo(true);
            js(window,"key('z')");assertThat(js(window,"getComputedStyle(editor.querySelector('.variable')).fontWeight").toString()).isIn("400","normal");
            js(window,"key('z',true)");assertThat(js(window,"getComputedStyle(editor.querySelector('.variable')).fontWeight").toString()).isIn("700","bold");
            js(window,"editor.querySelector('.variable').click();key('i',false,false,true);key('u');window.studio.fontSize(18);window.studio.command('fontName','Times New Roman');window.studio.command('foreColor','#123456');window.studio.command('hiliteColor','#ffee80')");
            assertThat(js(window,"window.studio.selectionFormat().italic")).as(js(window,"editor.innerHTML").toString()).isEqualTo(true);assertThat(js(window,"window.studio.selectionFormat().underline")).as(js(window,"editor.innerHTML").toString()).isEqualTo(true);assertThat(js(window,"window.studio.selectionFormat().fontSizePt")).isEqualTo(18);
            js(window,"key(']')");assertThat(js(window,"window.studio.selectionFormat().fontSizePt")).isEqualTo(19);js(window,"key('[')");
            for(String key:List.of("l","e","r","j")){assertThat(js(window,"key('"+key+"')")).isEqualTo(false);assertThat(js(window,"window.studio.selectionFormat().alignment")).isEqualTo(Map.of("l","left","e","center","r","right","j","justify").get(key));}
            js(window,"window.studio.list('bullet');window.formatNotifications=0;window.reportFormat=studio.onSelectionFormatChanged;studio.onSelectionFormatChanged=function(state){formatNotifications++;reportFormat(state)};editor.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}))");return null;
        });
        awaitJs(window,"window.formatNotifications>0");fx(()->{
            ComboBox<String> font=value(window,"fontChoice");ComboBox<Integer> size=value(window,"fontSizeChoice");assertThat(font.getValue()).isEqualTo("Times New Roman");assertThat(size.getValue()).isEqualTo(18);
            Map<String,Button> buttons=value(window,"selectionFormatButtons");for(String state:List.of("bold","italic","underline","justify","bulletList"))assertThat(buttons.get(state).getStyleClass()).contains("ribbon-btn-active");for(String state:List.of("left","center","right","numberedList"))assertThat(buttons.get(state).getStyleClass()).doesNotContain("ribbon-btn-active");
            assertThat(((ColorPicker)value(window,"editorTextColor")).getValue()).isEqualTo(javafx.scene.paint.Color.web("#123456"));assertThat(((ColorPicker)value(window,"editorHighlightColor")).getValue()).isEqualTo(javafx.scene.paint.Color.web("#ffee80"));
            for(boolean dark:List.of(false,true)){Application.setUserAgentStylesheet(dark?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());window.root().applyCss();window.root().layout();assertThat(buttons.get("bold").getBackground()).isNotEqualTo(buttons.get("left").getBackground());assertThat(buttons.get("bold").getBorder()).isNotEqualTo(buttons.get("left").getBorder());}
            js(window,"window.studio.notifySelectionFormat();window.beforeSync=editor.innerHTML");return null;
        });fx(()->{
            assertThat((Boolean)value(window,"updatingToolbarFromSelection")).isEqualTo(false);assertThat(js(window,"editor.innerHTML===window.beforeSync")).isEqualTo(true);
            js(window,"var r=document.createRange();r.setStart(editor.querySelector('p span').firstChild,1);r.collapse(true);var s=window.getSelection();s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'));window.studio.notifySelectionFormat()");return null;
        });fx(()->{
            assertThat(((ComboBox<Integer>)value(window,"fontSizeChoice")).getValue()).isEqualTo(12);assertThat(((ComboBox<String>)value(window,"fontChoice")).getValue()).isEqualTo("Arial");Map<String,Button> buttons=value(window,"selectionFormatButtons");for(String state:List.of("bold","italic","underline"))assertThat(buttons.get(state).getStyleClass()).doesNotContain("ribbon-btn-active");
            js(window,"key('b');window.studio.fontSize(13)");assertThat(js(window,"window.studio.selectionFormat().bold")).isEqualTo(true);assertThat(js(window,"window.studio.selectionFormat().fontSizePt")).isEqualTo(13);
            js(window,"document.execCommand('insertText',false,'X')");assertThat(js(window,"window.studio.selectionFormat().fontSizePt")).isEqualTo(13);assertThat(js(window,"document.getElementById('editor').textContent").toString()).startsWith("AXntes");js(window,"key('z');key('y');key('z')");assertThat(js(window,"editor.textContent")).isEqualTo("Antes ⟦nombre⟧ después");
            // A mixed text-and-variable range receives the same direct formatting.
            js(window,"var r=document.createRange();r.selectNodeContents(editor.querySelector('p'));var s=window.getSelection();s.removeAllRanges();s.addRange(r);document.dispatchEvent(new Event('selectionchange'));window.studio.fontSize(22)");assertThat(js(window,"window.studio.selectionFormat().fontSizePt")).isEqualTo(22);
            js(window,"window.beforeSync=editor.innerHTML;window.studio.notifySelectionFormat()");return null;
        });fx(()->{
            assertThat(js(window,"editor.innerHTML===window.beforeSync")).isEqualTo(true);assertThat(js(window,"key('s',false,false,true)")).isEqualTo(false);return null;
        });awaitEditor(window);fx(()->{
            try(XWPFDocument saved=DocumentEngine.open(store.templates().get(0).edited())){assertThat(saved.getParagraphs().get(0).getText()).isEqualTo("Antes {{nombre}} después");var variable=saved.getParagraphs().get(0).getRuns().stream().filter(r->r.text().equals("{{nombre}}")).findFirst().orElseThrow();assertThat(variable.isBold()).isTrue();assertThat(variable.isItalic()).isTrue();assertThat(variable.getUnderline()).isEqualTo(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);assertThat(variable.getFontSizeAsDouble()).isEqualTo(22);assertThat(variable.getColor()).isEqualTo("123456");}
            js(window,"document.querySelector('#editor .variable').click()");assertThat(js(window,"window.studio.selectionFormat().highlightColor")).isEqualTo("#ffee80");
            js(window,"window.studio.command('underline')");assertThat(js(window,"window.studio.selectionFormat().underline")).isEqualTo(false);assertThat(js(window,"window.studio.selectionFormat().bold")).isEqualTo(true);
            invoke(window,"syncEditor");XWPFDocument cleared=value(window,"document");var variable=cleared.getParagraphs().get(0).getRuns().stream().filter(r->r.text().equals("{{nombre}}")).findFirst().orElseThrow();assertThat(variable.getUnderline()).isEqualTo(org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);assertThat(variable.getFontSizeAsDouble()).isEqualTo(22);return null;
        });}finally{fx(()->{((Stage)value(window,"stage")).close();Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());return null;});}
    }

    @FunctionalInterface private interface DialogCheck {void check(DialogPane pane)throws Exception;}
    private static AtomicReference<Throwable> answerDialog(ButtonType answer,DialogCheck check){AtomicReference<Throwable> problem=new AtomicReference<>();Platform.runLater(()->{DialogPane pane=dialogPane();try{check.check(pane);}catch(Throwable e){problem.set(e);}finally{((Button)pane.lookupButton(answer)).fire();}});return problem;}
    private static DialogPane dialogPane(){return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing).map(w->w.getScene().getRoot()).filter(p->p instanceof DialogPane).map(p->(DialogPane)p).findFirst().orElseThrow();}
    private static Object js(StudioWindow window,String script)throws Exception{return ((javafx.scene.web.WebView)value(window,"richEditor")).getEngine().executeScript(script);}
    private static void awaitEditor(StudioWindow window)throws Exception{awaitJs(window,"typeof window.studio!=='undefined'",true);}
    private static void awaitJs(StudioWindow window,String condition)throws Exception{awaitJs(window,condition,false);}
    private static void awaitJs(StudioWindow window,String condition,boolean requireReady)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<deadline){if(fx(()->(!requireReady||Boolean.TRUE.equals(value(window,"editorReady")))&&Boolean.TRUE.equals(js(window,condition))))return;Thread.sleep(25);}throw new AssertionError("El editor no alcanzó el estado esperado: "+condition);
    }

    private static VBox formCard(Node input){Node node=input;while(node!=null&&!node.getStyleClass().contains("binding-card"))node=node.getParent();assertThat(node).isInstanceOf(VBox.class);return (VBox)node;}
    private static List<String> buttonLabels(javafx.scene.Parent page){return page.lookupAll(".button").stream().filter(n->n instanceof Button).map(n->((Button)n).getText()).toList();}
    private static void updateCell(ListCell<Template> cell,Template value)throws Exception{Method method=cell.getClass().getDeclaredMethod("updateItem",Template.class,boolean.class);method.setAccessible(true);method.invoke(cell,value,false);}

    private static Scene scene(StudioWindow window){Scene scene=new Scene(window.root(),1280,800);for(String file:List.of("/css/app.css","/css/studio.css"))scene.getStylesheets().add(TemplateCatalogUiTest.class.getResource(file).toExternalForm());return scene;}
    private static Template template(DocumentStore store,String name,String text)throws Exception{try(XWPFDocument document=new XWPFDocument()){document.createParagraph().createRun().setText(text);byte[] bytes=DocumentEngine.bytes(document);return store.saveTemplate(new Template(0,name,bytes,bytes));}}
    private static void snapshotConfigured(StudioWindow window,String name)throws Exception{
        if(System.getProperty("studio.ui.snapshots")==null)return;
        Node page=((StackPane)value(window,"workspace")).getChildren().get(0);page.setOpacity(1);page.setTranslateY(0);
        for(boolean dark:List.of(false,true)){Application.setUserAgentStylesheet(dark?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());window.root().applyCss();window.root().layout();snapshot(window,name+(dark?"-dark":"-light"));}Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
    }
    private static void snapshot(StudioWindow window,String name)throws Exception{
        String directory=System.getProperty("studio.ui.snapshots");if(directory==null)return;
        javafx.scene.image.WritableImage pixels=window.root().snapshot(null,null);java.awt.image.BufferedImage image=new java.awt.image.BufferedImage((int)pixels.getWidth(),(int)pixels.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,pixels.getPixelReader().getArgb(x,y));Path path=Path.of(directory);Files.createDirectories(path);javax.imageio.ImageIO.write(image,"png",path.resolve(name+".png").toFile());
    }
    private static <T> T fx(Callable<T> action)throws Exception{CompletableFuture<T> result=new CompletableFuture<>();Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable e){result.completeExceptionally(e);}});return result.get(30,TimeUnit.SECONDS);}
    private static Object invoke(Object target,String name)throws Exception{return invoke(target,name,new Class<?>[0]);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    @SuppressWarnings("unchecked") private static <T> T value(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return (T)field.get(target);}
}
