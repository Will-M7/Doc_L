package com.ejemplo.crudpersona.studio;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.util.Animations;
import com.ejemplo.crudpersona.studio.MCDocumentStore.MCKind;
import com.ejemplo.crudpersona.studio.MCDocumentStore.MCTemplate;
import com.ejemplo.crudpersona.studio.MCDocumentStore.MCVariable;
import com.ejemplo.crudpersona.studio.MCDocumentStore.MCDefinition;
import com.ejemplo.crudpersona.studio.MCDocumentStore.MCTemplateGroup;
import com.ejemplo.crudpersona.studio.MCStudioData.MCContact;
import com.ejemplo.crudpersona.studio.MCStudioData.MCClientDocument;
import com.ejemplo.crudpersona.studio.MCStudioData.MCHistory;
import com.ejemplo.crudpersona.studio.MCStudioData.MCMapping;
import com.ejemplo.crudpersona.studio.MCStudioData.MCApiBinding;
import com.ejemplo.crudpersona.studio.MCStudioData.MCContactBinding;
import com.ejemplo.crudpersona.studio.MCStudioData.MCApiContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;
import javafx.stage.*;
import javafx.util.Duration;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Desktop;
import java.util.*;
import java.util.regex.*;
import java.time.LocalDate;

public final class MCStudioWindow {
    private static final Pattern MCTOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");
    private static final List<String> MCTYPES = MCVariableInput.MCTYPES;
    private final Stage MCstage;
    private final MCDocumentStore MCstore;
    private final MCStudioData MCdata;
    private final MCApiCredentialStore MCcredentials;
    private final MCLookupRouter MClookupRouter;
    private final MCStudioTransfer MCtransfer;
    private final MCDocumentActions MCdocumentActions;
    private final ObjectMapper MCjson=new ObjectMapper();
    private final BorderPane MCroot = new BorderPane();
    private final StackPane MCworkspace = new StackPane();
    private final Label MCstatus = new Label("Datos locales · Sin conexión requerida");
    private final ToggleGroup MCmenu = new ToggleGroup();
    private final List<ToggleButton> MCnavigation = new ArrayList<>();
    private final List<Node> MCmenuTexts = new ArrayList<>();
    private VBox MCsidebar;
    private VBox MCbrandTexts;
    private Region MCindicator;
    private Button MCthemeButton, MCcollapseButton;
    private ToggleButton MCgenerateNavigation;
    private boolean MCdarkTheme, MCcollapsed;
    private final ComboBox<MCTemplate> MCtemplateChoice = new ComboBox<>();
    private BorderPane MClibraryPage;
    private final ListView<MCTemplate> MClibraryList=new ListView<>();
    private final ListView<MCTemplateGroup> MCgroupList=new ListView<>();
    private final TextField MClibrarySearch=new TextField();
    private final ComboBox<String> MClibrarySort=new ComboBox<>();
    private final Label MClibraryGroups=new Label("Sin grupos");
    private Button MCaddToGroup,MCremoveFromGroup;
    private Map<Long,List<Long>> MClibraryMemberships=Map.of();
    private Map<Long,String> MClibraryGroupNames=Map.of(),MClibraryModifiedDates=Map.of();
    private boolean MCrefreshingLibrary;
    private final ListView<MCDefinition> MCvariableList = new ListView<>();
    private final VBox MCpaper = new VBox(10);
    private final ScrollPane MCdocumentScroll = new ScrollPane();
    private final WebView MCrichEditor;
    private MCContinuousDocxEditor MCrichModel;
    private boolean MCeditorReady;
    private final MCEditorBridge MCeditorBridge=new MCEditorBridge();
    private boolean MCupdatingToolbarFromSelection;
    private final ComboBox<Integer> MCfontSizeChoice=new ComboBox<>();
    private final ColorPicker MCeditorTextColor=new ColorPicker(Color.BLACK),MCeditorHighlightColor=new ColorPicker(Color.YELLOW);
    private final Map<String,Button> MCselectionFormatButtons=new LinkedHashMap<>();
    private double MCeditorLeft=72,MCeditorRight=72,MCeditorTop=72,MCeditorBottom=72;
    private final Label MCpageIndicator = new Label("Página 1");
    private final ComboBox<String> MCzoomChoice = new ComboBox<>();
    private final TextField MCpaletteSearch = new TextField();
    private VBox MCpalettePanel;
    private Button MCpaletteToggle;
    private TabPane MCeditorTabs;
    private HBox MCtableTools;
    private HBox MCimageTools;
    private final Scale MCpaperScale = new Scale(1,1);
    private double MCpageWidth = 794, MCpageHeight = 1123;
    private String MCzoomMode = "Ancho";
    private double MCeditorZoom=1;
    private final Label MCzoomPercent=new Label("100%"),MCeditorSaveStatus=new Label("Sin plantilla");
    private ToggleButton MCguidesView,MCrulersView,MCvariablesView;
    private boolean MCreadingPageSize;
    private final TextField MCtemplateName = new TextField();
    private BorderPane MCdefinitionsPage;
    private final ListView<MCDefinition> MCdefinitionList=new ListView<>();
    private final TextField MCdefinitionSearch=new TextField(),MCdefinitionKey=new TextField(),MCdefinitionLabel=new TextField(),MCdefinitionDefault=new TextField();
    private final ComboBox<String> MCdefinitionType=new ComboBox<>();
    private final CheckBox MCdefinitionApiEnabled=new CheckBox("Vincular con API");
    private final ComboBox<String> MCdefinitionApiService=new ComboBox<>(),MCdefinitionApiField=new ComboBox<>();
    private final Label MCdefinitionApiPath=new Label("Consulta automática cuando haya conexión · Ingreso manual disponible");
    private VBox MCdefinitionApiOptions;
    private MCDefinition MCselectedDefinition;
    private final ComboBox<MCDocumentStore.MCPersona> MCeditorPersona=new ComboBox<>();
    private final Map<String,String> MCeditorPersonaNames=new LinkedHashMap<>();
    private long MCpersonaTemplateId;
    private final ToggleGroup MCvariableScope=new ToggleGroup();
    private final RadioButton MCclientScope=new RadioButton("Amarrada a cliente / persona"),MCglobalScope=new RadioButton("Variable independiente (global / sin cliente)");
    private final RadioButton MCcomplementScope=new RadioButton("Complemento de variable"), MCapiScope=new RadioButton("Conexión API"), MCdateScope=new RadioButton("Variable de fecha (Fecha actual automática)");
    private VBox MCdefinitionApiCard,MCdefinitionComplementCard,MCdefinitionDateCard;
    private final CheckBox MCautoCurrentDate=new CheckBox("Amarrar esta variable a la fecha actual del sistema");
    private final ComboBox<String> MCcurrentDateFormat=new ComboBox<>();
    private final Label MCcurrentDatePreview=new Label();
    private final Map<String,MCDefinition> MCformDefinitions=new HashMap<>();
    private final ComboBox<MCDefinition> MCcomplementChoice=new ComboBox<>(),MCcontactExtraChoice=new ComboBox<>();
    private final CheckBox MCcomplementBoth=new CheckBox("Autocompletar en ambos sentidos (bidireccional)");
    private final VBox MCcomplementLinks=new VBox(6);
    private final TableView<MCContactExtra> MCcontactVariableTable=new TableView<>();
    private final TextField MCcontactExtraValue=new TextField();
    private final Map<String,Set<String>> MCcomplementTargets=new LinkedHashMap<>();
    private final Map<String,ContextMenu> MCcomplementMenus=new HashMap<>();
    private final Map<String,PauseTransition> MCcomplementDelays=new HashMap<>();
    private final List<MCDefinition> MCactiveComplementDefinitions=new ArrayList<>();
    private final List<MCDocumentStore.MCComplement> MCactiveComplementLinks=new ArrayList<>();
    private boolean MCapplyingComplements,MCcompletingMissingComplements;
    private final ComboBox<String> MCfontChoice = new ComboBox<>();
    private final ComboBox<String> MCpaperChoice = new ComboBox<>();
    private MCTemplate MCcurrentTemplate;
    private boolean MCupdatingTemplates;
    private XWPFDocument MCdocument;
    private boolean MCdirty;
    private XWPFRun MCselectedRun;
    private XWPFParagraph MCselectedParagraph;
    private TextField MCselectedText;
    private XWPFRun MCdragSourceRun;
    private String MCdragSourceToken;
    private int MCdragSourceOffset;
    private XWPFRun MCdragTargetRun;
    private int MCdragTargetOffset;

    private final ListView<MCKind> MCkindList = new ListView<>();
    private final TextField MCkindSearch = new TextField(), MCkindTitle = new TextField(), MCkindCategory = new TextField();
    private final TextArea MCkindDescription = new TextArea();
    private final ComboBox<MCTemplate> MCkindTemplate = new ComboBox<>();
    private final CheckBox MCkindActive = new CheckBox("Activo");
    private final ListView<MCVariable> MCfieldList = new ListView<>();
    private final ComboBox<MCVariable> MCaddFieldChoice = new ComboBox<>();
    private final CheckBox MCfieldRequired=new CheckBox("Obligatorio en este documento");
    private final Map<String,Boolean> MCkindRequiredChoices=new HashMap<>();
    private final TextField MCfieldDefault=new TextField();
    private final Map<String,String> MCkindDefaultChoices=new HashMap<>();
    private boolean MCloadingFieldRequired;
    private final TextField MCbindingGroup=new TextField();
    private final ComboBox<String> MCbindingKind=new ComboBox<>(),MCbindingField=new ComboBox<>();
    private final ComboBox<MCVariable> MCbindingVariable=new ComboBox<>();
    private final ListView<MCContactBinding> MCbindingList=new ListView<>();
    private MCKind MCcurrentKind;

    private final ComboBox<MCTemplate> MCgenerateTemplate = new ComboBox<>();
    private boolean MCrefreshingGenerator;
    private final Map<String,PauseTransition> MClookupDelays=new HashMap<>();
    private final ComboBox<MCTemplateGroup> MCgenerateGroup=new ComboBox<>();
    private final TextField MCgenerateSearch=new TextField();
    private final VBox MCgeneratedForm = new VBox(15);
    private final Map<String, Control> MCinputs = new LinkedHashMap<>();
    private final Map<String,MCVariable> MCformVariables=new LinkedHashMap<>();
    private final Set<String> MCmanuallyEdited=new HashSet<>();
    private boolean MCapplyingApi;
    private final Map<String,String> MClocalValueOverrides=new HashMap<>();
    private final WebView MCpreview;
    private XWPFDocument MCgenerationDocument;
    private MCContinuousDocxEditor MCgenerationModel;
    private boolean MCgenerationReady;
    private Map<String,String> MCgenerationDisplayValues=Map.of();
    private final Map<String,String> MCgenerationValueAliases=new LinkedHashMap<>();
    private final MCGenerationBridge MCgenerationBridge=new MCGenerationBridge();
    private MCClientDocument MCupdatingClientDocument;
    private MCContact MCupdatingClient;
    private final Label MCupdateDocumentBanner=new Label();
    private final Button MCupdateDocumentButton=new Button("Actualizar documento");
    private final ListView<MCClientDocument> MCclientDocumentList=new ListView<>();
    private final BorderPane MCclientDocumentsPane=new BorderPane();
    private BorderPane MCclientDetailsPane;
    private final Button MCclientDetailsToggle=new Button("Ver detalles del cliente");
    private final Label MCclientPanelTitle=new Label("Documentos del cliente");
    private boolean MCshowingClientDetails;
    private boolean MCgeneratorVariablesCollapsed;
    private VBox MCgeneratorVariablesPanel;
    private GridPane MCgeneratorColumns;
    private ColumnConstraints MCgeneratorFormColumn,MCgeneratorDocumentColumn;
    private final Button MCgeneratorVariablesToggle=new Button();
    private byte[] MCgeneratedBytes;
    private byte[] MCgeneratedPdf;
    private Map<String,String> MCgeneratedValues=Map.of();
    private long MCgeneratedHistoryId;
    private byte[] MChistoricalTemplate;
    private String MCcontactSaveSummary="";
    private BorderPane MCcontactsPage,MChistoryPage,MCapiPage,MCsettingsPage;
    private final ListView<MCContact> MCcontactList=new ListView<>();
    private final TextField MCcontactSearch=new TextField(),MCcontactGiven=new TextField(),MCcontactFamily=new TextField(),MCcontactDocType=new TextField(),MCcontactNumber=new TextField(),MCcontactAddress=new TextField();
    private final TextArea MCcontactRepresentatives=new TextArea(),MCcontactExtras=new TextArea();
    private final ComboBox<String> MCcontactKind=new ComboBox<>();
    private MCContact MCselectedContact;
    private final PasswordField MCapiToken=new PasswordField();
    private final ListView<MCApiCredentialStore.MCCredential> MCcredentialList=new ListView<>();
    private final TextField MCcredentialName=new TextField();
    private final ComboBox<String> MCcredentialProvider=new ComboBox<>();
    private final CheckBox MCcredentialEnabled=new CheckBox("Habilitada");
    private final Label MCcredentialStats=new Label("Sin credencial seleccionada");
    private final ComboBox<String> MCapiType=new ComboBox<>();
    private final TextField MCapiNumber=new TextField();
    private final Label MCapiState=new Label("Sin consulta");
    private boolean MCapiQueryRunning;
    private final VBox MCapiResultBox=new VBox(8);
    private final ComboBox<MCTemplate> MCmapTemplate=new ComboBox<>();
    private final ComboBox<MCVariable> MCmapSource=new ComboBox<>(),MCmapTarget=new ComboBox<>();
    private final ComboBox<String> MCmapApiField=new ComboBox<>();
    private final ListView<MCMapping> MCmappingList=new ListView<>();
    private final ListView<MCHistory> MChistoryList=new ListView<>();
    private final TextField MChistorySearch=new TextField();
    private final DatePicker MChistoryDate=new DatePicker();
    private final ComboBox<String> MCimportConflict=new ComboBox<>(),MCexportScope=new ComboBox<>();
    private final ComboBox<MCTemplate> MCexportTemplate=new ComboBox<>();
    private final ComboBox<MCTemplateGroup> MCexportGroup=new ComboBox<>();
    private final Label MCapiSaved=new Label();

    public MCStudioWindow(Stage MCstage, MCDocumentStore MCstore) throws Exception {
        this(MCstage,MCstore,null);
    }
    MCStudioWindow(Stage MCstage, MCDocumentStore MCstore,MCLookupRouter MCrouter) throws Exception {
        this(MCstage,MCstore,MCrouter,new MCSystemDocumentActions());
    }
    MCStudioWindow(Stage MCstage,MCDocumentStore MCstore,MCLookupRouter MCrouter,MCDocumentActions MCdocumentActions)throws Exception{
        this(MCstage,MCstore,MCrouter,MCdocumentActions,null);
    }
    private MCStudioWindow(Stage MCstage,MCDocumentStore MCstore,MCLookupRouter MCrouter,MCDocumentActions MCdocumentActions,MCStudioWindow MCparent)throws Exception{
        boolean MCbatchChild=MCparent!=null;this.MCbatchChild=MCbatchChild;
        MCrichEditor=MCbatchChild?null:new WebView();MCpreview=MCbatchChild?null:new WebView();MCbatchPreview=MCbatchChild?null:new WebView();
        this.MCstage=MCstage; this.MCstore=MCstore;
        this.MCdocumentActions=MCdocumentActions;
        MCdata=MCbatchChild?MCparent.MCdata:new MCStudioData(MCstore.MCdirectory());MCcredentials=MCbatchChild?MCparent.MCcredentials:new MCApiCredentialStore(MCstore.MCdirectory());MClookupRouter=MCrouter==null?new MCLookupRouter(MCcredentials,List.of(new MCFactilizaProvider(),new MCApisPeruProvider())):MCrouter;MCtransfer=MCbatchChild?MCparent.MCtransfer:new MCStudioTransfer(MCstore,MCdata);
        if(MCbatchChild)return;
        MCbuildShell();
        MCbuildDefinitions();
        MCbuildEditor();
        MCbuildLibrary();
        MCbuildGenerator();
        MCbuildContacts();MCbuildHistory();MCbuildApi();MCbuildSettings();
        MCmenu.selectToggle(MCgenerateNavigation);
        Platform.runLater(() -> MCmoveIndicator(MCgenerateNavigation));
    }
    public Parent MCroot() { return MCroot; }

    interface MCDocumentActions {
        void MCopen(Path MCdocument)throws Exception;
        void MCprint(Path MCdocument)throws Exception;
    }
    private static final class MCSystemDocumentActions implements MCDocumentActions {
        @Override public void MCopen(Path MCdocument)throws Exception{
            try{if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.OPEN)){Desktop.getDesktop().open(MCdocument.toFile());return;}}catch(IOException|UnsupportedOperationException MCignored){}
            String MCos=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
            ProcessBuilder MCcommand;
            if(MCos.contains("win"))MCcommand=new ProcessBuilder("cmd","/c","start","",MCdocument.toAbsolutePath().toString());
            else if(MCos.contains("linux"))MCcommand=new ProcessBuilder("xdg-open",MCdocument.toAbsolutePath().toString());
            else throw new IOException("No hay un procesador de texto disponible para abrir el DOCX.");
            Process MCprocess=MCcommand.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(MCprocess.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)&&MCprocess.exitValue()!=0)throw new IOException("No se pudo abrir el DOCX en el procesador de texto del sistema.");
        }
        @Override public void MCprint(Path MCdocument)throws Exception{
            try{if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.PRINT)){Desktop.getDesktop().print(MCdocument.toFile());return;}}catch(IOException|UnsupportedOperationException MCignored){}
            String MCos=System.getProperty("os.name","").toLowerCase(Locale.ROOT);ProcessBuilder MCcommand;
            if(MCos.contains("linux"))MCcommand=new ProcessBuilder("lp",MCdocument.toAbsolutePath().toString());
            else if(MCos.contains("win")){
                MCcommand=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command","Start-Process -FilePath $env:STUDIO_PRINT_FILE -Verb Print -ErrorAction Stop");
                MCcommand.environment().put("STUDIO_PRINT_FILE",MCdocument.toAbsolutePath().toString());
            }else throw new IOException("La impresión directa no está disponible en este sistema.");
            Process MCprocess=MCcommand.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!MCprocess.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){MCprocess.destroy();throw new IOException("El sistema de impresión no respondió.");}
            if(MCprocess.exitValue()!=0)throw new IOException("No se pudo enviar el PDF a la impresora predeterminada. Revise la impresora del sistema.");
        }
    }

    private void MCbuildShell() {
        MCroot.getStyleClass().add("app");
        MCsidebar=new VBox(6);MCsidebar.getStyleClass().add("sidebar");MCsidebar.setPrefWidth(242);MCsidebar.setMinWidth(242);
        ImageView MClogoImage=new ImageView(new Image(MCStudioWindow.class.getResourceAsStream("/img/icon-128.png")));MClogoImage.setFitWidth(34);MClogoImage.setFitHeight(34);MClogoImage.setPreserveRatio(true);MClogoImage.setSmooth(true);
        StackPane MClogo=new StackPane(MClogoImage);MClogo.getStyleClass().add("marca-logo");
        MCbrandTexts=new VBox(1,new Label("Gestion"),new Label("Panel administrativo"));
        MCbrandTexts.getChildren().get(0).getStyleClass().add("marca-titulo");MCbrandTexts.getChildren().get(1).getStyleClass().add("marca-subtitulo");
        HBox MCbrand=new HBox(12,MClogo,MCbrandTexts);MCbrand.getStyleClass().add("marca");MCsidebar.getChildren().add(MCbrand);
        MCindicator=new Region();MCindicator.getStyleClass().add("nav-indicador");MCindicator.setPrefHeight(42);MCindicator.setMaxHeight(42);
        VBox MCitems=new VBox(3);
        MCaddSection(MCitems,"GENERAL");MCgenerateNavigation=MCaddNavigation(MCitems,"Generar documentos","fth-file-text",this::MCshowGenerator);
        MCaddSection(MCitems,"DOCUMENTOS");MCaddNavigation(MCitems,"Variables","fth-tag",this::MCshowDefinitions);MCaddNavigation(MCitems,"Plantillas","fth-folder",this::MCshowLibrary);MCaddNavigation(MCitems,"Editor de plantillas","fth-edit-3",this::MCshowEditor);
        MCaddSection(MCitems,"REGISTROS");MCaddNavigation(MCitems,"Clientes","fth-users",this::MCshowContacts);MCaddNavigation(MCitems,"Historial","fth-clock",this::MCshowHistory);
        MCaddSection(MCitems,"SISTEMA");MCaddNavigation(MCitems,"Conexiones API","fth-link",this::MCshowApi);MCaddNavigation(MCitems,"Configuración y respaldos","fth-settings",this::MCshowSettings);
        StackPane MCmenuPane=new StackPane(MCindicator,MCitems);MCmenuPane.setAlignment(Pos.TOP_LEFT);MCsidebar.getChildren().add(MCmenuPane);
        Region MCspacer=new Region();VBox.setVgrow(MCspacer,Priority.ALWAYS);MCsidebar.getChildren().add(MCspacer);
        Separator MCseparator=new Separator();MCseparator.getStyleClass().add("nav-separador");MCsidebar.getChildren().add(MCseparator);
        MCthemeButton=MCsidebarAction("Modo oscuro","fth-moon",this::MCchangeTheme);
        MCcollapseButton=MCsidebarAction("Contraer menú","fth-chevrons-left",this::MCcollapseMenu);
        MCsidebar.getChildren().addAll(MCthemeButton,MCcollapseButton);
        MCmenu.selectedToggleProperty().addListener((MCo,MCprevious,MCcurrent)->{
            if(MCcurrent==null){MCmenu.selectToggle(MCprevious);return;}
            Runnable MCaction=(Runnable)((ToggleButton)MCcurrent).getUserData();
            MCaction.run();MCmoveIndicator(MCcurrent);
        });
        MCworkspace.getStyleClass().add("contenido");MCroot.setLeft(MCsidebar);MCroot.setCenter(MCworkspace);
    }
    private void MCaddSection(VBox MCitems,String MCtitle){Label MCsection=new Label(MCtitle);MCsection.getStyleClass().add("nav-seccion");MCitems.getChildren().add(MCsection);MCmenuTexts.add(MCsection);}
    private ToggleButton MCaddNavigation(VBox MCitems,String MCtitle,String MCicon,Runnable MCaction){ToggleButton MCbutton=new ToggleButton(MCtitle,new FontIcon(MCicon));MCbutton.setToggleGroup(MCmenu);MCbutton.setMaxWidth(Double.MAX_VALUE);MCbutton.getStyleClass().add("nav-item");MCbutton.setUserData(MCaction);Tooltip MCtip=new Tooltip(MCtitle);MCtip.setShowDelay(Duration.millis(300));MCbutton.setTooltip(MCtip);MCnavigation.add(MCbutton);MCitems.getChildren().add(MCbutton);return MCbutton;}
    private Button MCsidebarAction(String MCtitle,String MCicon,Runnable MCaction){Button MCbutton=new Button(MCtitle,new FontIcon(MCicon));MCbutton.getStyleClass().add("nav-item");MCbutton.setMaxWidth(Double.MAX_VALUE);MCbutton.setOnAction(MCe->MCsafe(MCaction));return MCbutton;}
    private void MCmoveIndicator(javafx.scene.control.Toggle MCtoggle){if(MCtoggle==null)return;Node MCbutton=(Node)MCtoggle;double MCy=MCbutton.getBoundsInParent().getMinY();MCindicator.setPrefHeight(MCbutton.getBoundsInParent().getHeight());new Timeline(new KeyFrame(Duration.millis(280),new KeyValue(MCindicator.translateYProperty(),MCy,Interpolator.SPLINE(0.4,0,0.2,1)))).play();}
    private void MCchangeTheme(){MCdarkTheme=!MCdarkTheme;Application.setUserAgentStylesheet(MCdarkTheme?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());if(MCdarkTheme)MCroot.getStyleClass().add("dark");else MCroot.getStyleClass().remove("dark");((FontIcon)MCthemeButton.getGraphic()).setIconCode(MCdarkTheme?Feather.SUN:Feather.MOON);MCthemeButton.setText(MCdarkTheme?"Modo claro":"Modo oscuro");Animations.fadeIn(MCworkspace,Duration.millis(300)).play();}
    private void MCcollapseMenu(){MCcollapsed=!MCcollapsed;double MCwidth=MCcollapsed?76:242;Timeline MCtransition=new Timeline(new KeyFrame(Duration.millis(260),new KeyValue(MCsidebar.prefWidthProperty(),MCwidth,Interpolator.SPLINE(0.4,0,0.2,1)),new KeyValue(MCsidebar.minWidthProperty(),MCwidth,Interpolator.SPLINE(0.4,0,0.2,1))));List<Node> MCtexts=new ArrayList<>(MCmenuTexts);MCtexts.add(MCbrandTexts);List<ButtonBase> MCbuttons=new ArrayList<>(MCnavigation);MCbuttons.add(MCthemeButton);MCbuttons.add(MCcollapseButton);if(MCcollapsed){MCtexts.forEach(MCn->{MCn.setVisible(false);MCn.setManaged(false);});MCbuttons.forEach(MCb->MCb.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));MCtransition.play();}else{MCtransition.setOnFinished(MCe->{MCtexts.forEach(MCn->{MCn.setVisible(true);MCn.setManaged(true);});MCbuttons.forEach(MCb->MCb.setContentDisplay(ContentDisplay.LEFT));ParallelTransition MCfade=new ParallelTransition();MCtexts.forEach(MCn->MCfade.getChildren().add(Animations.fadeIn(MCn,Duration.millis(200))));MCfade.play();Platform.runLater(()->MCmoveIndicator(MCmenu.getSelectedToggle()));});MCtransition.play();}((FontIcon)MCcollapseButton.getGraphic()).setIconCode(MCcollapsed?Feather.CHEVRONS_RIGHT:Feather.CHEVRONS_LEFT);Platform.runLater(()->MCmoveIndicator(MCmenu.getSelectedToggle()));}
    private void MCpage(String MCtitle,Node MCcontent){
        Label MCheading=new Label(MCtitle);MCheading.getStyleClass().add("pagina-titulo");
        if(!MCstatus.getStyleClass().contains("pagina-subtitulo"))MCstatus.getStyleClass().add("pagina-subtitulo");
        MCstatus.setText(switch(MCtitle){
            case "Generar documentos"->"";
            case "Variables"->"Defina campos reutilizables y vincule los datos que necesita.";
            case "Plantillas"->"Organice sus documentos en grupos y acceda al editor.";
            case "Editor de plantillas"->"Edite el documento e inserte sus variables.";
            case "Clientes"->"Consulte y actualice personas y empresas guardadas en este equipo.";
            case "Historial"->"Recupere documentos generados y reutilice sus datos.";
            case "Conexiones API"->"Administre credenciales y consulte DNI o RUC con guardado automático.";
            default->"Importe archivos y exporte sus plantillas o clientes.";
        });
        MCstatus.setWrapText(true);VBox MCheaderText="Generar documentos".equals(MCtitle)?new VBox(MCheading):new VBox(4,MCheading,MCstatus);HBox MCheader=new HBox(MCheaderText);MCheader.getStyleClass().add("pagina-cabecera");
        VBox MCpage="Editor de plantillas".equals(MCtitle)?new VBox(MCcontent):new VBox(MCheader,MCcontent);MCpage.getStyleClass().add("pagina");VBox.setVgrow(MCcontent,Priority.ALWAYS);MCworkspace.getChildren().setAll(MCpage);Animations.fadeInUp(MCpage,Duration.millis(380)).play();
    }
    private Button MCbutton(String MClabel,Runnable MCaction){
        Button MCb=new Button(MClabel.startsWith("+ ")?MClabel.substring(2):MClabel);MCb.getStyleClass().add("secondary-btn");
        if(MClabel.startsWith("Guardar")||MClabel.startsWith("Importar archivo"))MCb.getStyleClass().add("primary-btn");
        else if(MClabel.startsWith("Eliminar"))MCb.getStyleClass().add("danger-btn");
        String MCicon=null;
        if(MClabel.startsWith("Guardar"))MCicon="fth-save";
        else if(MClabel.startsWith("Importar"))MCicon="fth-upload";
        else if(MClabel.startsWith("Exportar")||MClabel.startsWith("Descargar"))MCicon="fth-download";
        else if(MClabel.startsWith("Eliminar"))MCicon="fth-trash-2";
        else if(MClabel.startsWith("Duplicar"))MCicon="fth-copy";
        else if(MClabel.startsWith("Consultar")||MClabel.startsWith("Buscar"))MCicon="fth-search";
        else if(MClabel.startsWith("Abrir"))MCicon="fth-external-link";
        else if(MClabel.startsWith("Editar"))MCicon="fth-edit-2";
        else if(MClabel.startsWith("Nueva")||MClabel.startsWith("+ "))MCicon="fth-plus";
        if(MCicon!=null)MCb.setGraphic(new FontIcon(MCicon));
        MCb.setOnAction(MCe->MCsafe(MCaction));return MCb;
    }
    @FunctionalInterface private interface MCCheckedAction {void MCrun()throws Exception;}
    private void MCclientAction(MCCheckedAction MCaction){try{MCaction.MCrun();}catch(Exception MCe){MCerror(MCe);}}
    private void MCsafe(Runnable MCaction){try{MCaction.run();}catch(Exception MCe){MCerror(MCe);}}
    private void MCerror(Exception MCe){MCstatus.setText("Error: "+MCe.getMessage());new Alert(Alert.AlertType.ERROR,"No se completó la operación:\n"+MCe.getMessage(),ButtonType.OK).showAndWait();}
    private void MCsaved(String MCmessage){MCstatus.setText(MCmessage);MCupdateEditorSaveStatus();}
    private static VBox MClabelled(String MCname,Node MCnode){Label MCl=new Label(MCname);MCl.getStyleClass().add("field-label");VBox MCbox=new VBox(5,MCl,MCnode);MCbox.setMinWidth(0);MCbox.setMaxWidth(Double.MAX_VALUE);MCl.setWrapText(true);if(MCnode instanceof Region MCr){MCr.setMinWidth(0);MCr.setMaxWidth(Double.MAX_VALUE);}return MCbox;}

    private static GridPane MCtwoColumns(){
        GridPane MCgrid=new GridPane();MCgrid.setHgap(14);MCgrid.setVgap(14);MCgrid.setMinWidth(0);
        for(int MCi=0;MCi<2;MCi++){ColumnConstraints MCcol=new ColumnConstraints();MCcol.setPercentWidth(50);MCcol.setFillWidth(true);MCgrid.getColumnConstraints().add(MCcol);}return MCgrid;
    }
    private static ScrollPane MCscrolling(Node MCcontent){ScrollPane MCscroll=new ScrollPane(MCcontent);MCscroll.setFitToWidth(true);MCscroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);MCscroll.setMinWidth(0);MCscroll.getStyleClass().add("content-scroll");return MCscroll;}
    private static void MCuniformButtons(Pane MCpane){for(Node MCn:MCpane.getChildren())if(MCn instanceof Button MCb)MCb.setMaxWidth(Double.MAX_VALUE);}
    private static void MCfixedWidth(Region MCnode,double MCwidth){MCnode.setMinWidth(MCwidth);MCnode.setPrefWidth(MCwidth);MCnode.setMaxWidth(MCwidth);}
    private static Label MCdescription(String MCtext){Label MClabel=new Label(MCtext);MClabel.setWrapText(true);MClabel.setMinWidth(0);MClabel.getStyleClass().add("field-label");return MClabel;}
    private static <T> void MCtwoLineCells(ListView<T> MClist,java.util.function.Function<T,String> MCtitle,java.util.function.Function<T,String> MCdetail){
        MClist.setCellFactory(MCview->new ListCell<>(){
            private final Label MCprimary=new Label(),MCsecondary=new Label();private final VBox MCbox=new VBox(4,MCprimary,MCsecondary);
            {MCprimary.setWrapText(true);MCsecondary.setWrapText(true);MCsecondary.getStyleClass().add("field-label");MCbox.setMinWidth(0);MCbox.prefWidthProperty().bind(widthProperty().subtract(28));MCbox.setMaxWidth(Double.MAX_VALUE);}
            @Override protected void updateItem(T MCvalue,boolean MCempty){super.updateItem(MCvalue,MCempty);setText(null);setGraphic(MCempty||MCvalue==null?null:MCbox);if(!MCempty&&MCvalue!=null){MCprimary.setText(MCtitle.apply(MCvalue));MCsecondary.setText(MCdetail.apply(MCvalue));}}
        });
    }

    private void MCbuildDefinitions(){
        MCdefinitionsPage=new BorderPane();MCdefinitionsPage.getStyleClass().add("page");
        MCdefinitionSearch.setPromptText("Buscar identificador, etiqueta o tipo");MCdefinitionSearch.textProperty().addListener((MCo,MCa,MCb)->MCrefreshDefinitions());
        MCdefinitionList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->MCloadDefinition(MCb));
        MCtwoLineCells(MCdefinitionList,MCDefinition::MClabel,MCd->MCd.MCkey()+" · "+MCd.MCtype());
        VBox MCleft=new VBox(8,MCdefinitionSearch,MCdefinitionList,MCbutton("+ Nueva variable",this::MCnewDefinition),MCbutton("Duplicar variable",this::MCduplicateDefinition),MCbutton("Eliminar variable",this::MCdeleteDefinition));
        MCleft.getStyleClass().add("side-panel");MCfixedWidth(MCleft,340);MCuniformButtons(MCleft);VBox.setVgrow(MCdefinitionList,Priority.ALWAYS);MCdefinitionsPage.setLeft(MCleft);BorderPane.setMargin(MCleft,new Insets(0,14,0,0));
        MCdefinitionType.getItems().setAll(MCTYPES);MCdefinitionApiService.getItems().setAll("DNI","RUC");MCdefinitionApiService.setValue("DNI");MCdefinitionApiService.setOnAction(MCe->MCrefreshDefinitionApiFields());
        GridPane MCapiGrid=MCtwoColumns();MCapiGrid.add(MClabelled("Servicio",MCdefinitionApiService),0,0);MCapiGrid.add(MClabelled("Dato solicitado",MCdefinitionApiField),1,0);
        MCdefinitionApiPath.setWrapText(true);MCdefinitionApiOptions=new VBox(12,MCapiGrid,MCdefinitionApiPath);
        MCdefinitionApiEnabled.selectedProperty().addListener((MCo,MCa,MCb)->{MCdefinitionApiOptions.setVisible(MCb);MCdefinitionApiOptions.setManaged(MCb);});MCdefinitionApiOptions.setVisible(false);MCdefinitionApiOptions.setManaged(false);MCrefreshDefinitionApiFields();
        GridPane MCfields=MCtwoColumns();MCfields.add(MClabelled("Identificador estable",MCdefinitionKey),0,0);MCfields.add(MClabelled("Etiqueta visible",MCdefinitionLabel),1,0);MCfields.add(MClabelled("Tipo de entrada",MCdefinitionType),0,1);MCfields.add(MClabelled("Valor predeterminado opcional",MCdefinitionDefault),1,1);
        MCdefinitionChoice(MCcomplementChoice);MCcomplementBoth.setSelected(true);MCcomplementBoth.setWrapText(true);MCcomplementChoice.setPromptText("Variable complemento");MCcomplementChoice.setMinWidth(0);MCcomplementChoice.setMaxWidth(Double.MAX_VALUE);
        VBox MCapiCard=new VBox(10,new Label("Conexión API"),MCdefinitionApiOptions),MCcomplementCard=new VBox(10,new Label("Complemento de variable"),MClabelled("Variable complemento",MCcomplementChoice),MCcomplementBoth,MCbutton("Vincular complemento",this::MClinkComplement),MCcomplementLinks);
        for(VBox MCcard:List.of(MCapiCard,MCcomplementCard)){MCcard.getStyleClass().add("binding-card");MCcard.setMinWidth(0);MCcard.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(MCcard,Priority.ALWAYS);}
        MCcurrentDateFormat.getItems().setAll(MCDocumentStore.MCDATE_FORMATS);MCcurrentDateFormat.setValue("dd/MM/yyyy");MCcurrentDateFormat.setMaxWidth(Double.MAX_VALUE);MCcurrentDateFormat.disableProperty().bind(MCautoCurrentDate.selectedProperty().not());MCcurrentDateFormat.valueProperty().addListener((MCo,MCa,MCb)->MCupdateAutomaticDatePreview());MCautoCurrentDate.setWrapText(true);MCcurrentDatePreview.setWrapText(true);MCupdateAutomaticDatePreview();
        MCcurrentDateFormat.setCellFactory(MClist->new ListCell<>(){@Override protected void updateItem(String MCformat,boolean MCempty){super.updateItem(MCformat,MCempty);setText(MCempty||MCformat==null?null:MCformat+" — "+LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(MCformat,Locale.forLanguageTag("es-PE"))));}});
        VBox MCdateCard=new VBox(10,new Label("Formato de fecha"),MCcurrentDateFormat,MCcurrentDatePreview);MCdateCard.getStyleClass().add("binding-card");
        MCdefinitionApiCard=MCapiCard;MCdefinitionComplementCard=MCcomplementCard;MCdefinitionDateCard=MCdateCard;
        for(RadioButton MCmode:List.of(MCclientScope,MCglobalScope,MCcomplementScope,MCapiScope,MCdateScope)){MCmode.setToggleGroup(MCvariableScope);MCmode.setWrapText(true);}
        MCvariableScope.selectedToggleProperty().addListener((MCo,MCa,MCb)->MCupdateVariableScope());
        MCclientScope.setSelected(true);MCupdateVariableScope();
        VBox MCscopeCard=new VBox(10,new Label("Alcance de la variable"),MCclientScope,MCglobalScope,MCcomplementScope,MCapiScope,MCdateScope);MCscopeCard.getStyleClass().add("binding-card");
        VBox MCform=new VBox(16,new Label("DEFINICIÓN DE VARIABLE"),MCfields,MCscopeCard,MCapiCard,MCcomplementCard,MCdateCard,MCbutton("Guardar variable",this::MCsaveDefinition));MCform.getStyleClass().add("catalog-form");MCdefinitionsPage.setCenter(MCscrolling(MCform));MCnewDefinition();
    }
    private void MCupdateVariableScope(){
        MCdefinitionApiEnabled.setSelected(MCapiScope.isSelected());MCautoCurrentDate.setSelected(MCdateScope.isSelected());
        MCvisible(MCdefinitionApiCard,MCapiScope.isSelected());MCvisible(MCdefinitionComplementCard,MCcomplementScope.isSelected());MCvisible(MCdefinitionDateCard,MCdateScope.isSelected());
    }
    private static void MCvisible(Node MCnode,boolean MCshow){MCnode.setVisible(MCshow);MCnode.setManaged(MCshow);}

    private void MCshowDefinitions(){MCrefreshDefinitions();MCpage("Variables",MCdefinitionsPage);}
    private void MCrefreshDefinitions(){try{long MCselected=MCselectedDefinition==null?0:MCselectedDefinition.MCid();String MCq=MCdefinitionSearch.getText().trim().toLowerCase();MCdefinitionList.getItems().setAll(MCstore.MCdefinitions().stream().filter(MCd->(MCd.MCkey()+" "+MCd.MClabel()+" "+MCd.MCtype()).toLowerCase().contains(MCq)).toList());if(MCselected!=0)MCdefinitionList.getSelectionModel().select(MCdefinitionList.getItems().stream().filter(MCd->MCd.MCid()==MCselected).findFirst().orElse(null));}catch(Exception MCe){MCerror(MCe);}}
    private void MCloadDefinition(MCDefinition MCd){if(MCd==null)return;MCselectedDefinition=MCd;MCdefinitionKey.setText(MCd.MCkey());MCdefinitionKey.setDisable(true);MCdefinitionLabel.setText(MCd.MClabel());MCdefinitionType.setValue(MCVariableInput.MCvisualType(MCd.MCtype()));MCdefinitionDefault.setText(MCd.MCdefaultValue()==null?"":MCd.MCdefaultValue());MCclientScope.setSelected(MCd.MCbindToClient());MCglobalScope.setSelected(!MCd.MCbindToClient());MCautoCurrentDate.setSelected(MCd.MCautoCurrentDate());MCcurrentDateFormat.setValue(MCd.MCdateFormat());MCupdateAutomaticDatePreview();MCrefreshComplements();try{MCApiBinding MCbinding=MCdata.MCapiBinding(MCd.MCid());MCvariableScope.selectToggle(MCbinding!=null?MCapiScope:MCd.MCautoCurrentDate()?MCdateScope:!MCstore.MCcomplements(MCd.MCid()).isEmpty()?MCcomplementScope:!MCd.MCbindToClient()?MCglobalScope:MCclientScope);MCupdateVariableScope();MCdefinitionApiService.setValue(MCbinding==null?"DNI":MCbinding.MCservice());MCrefreshDefinitionApiFields();if(MCbinding!=null){String MCkey=MCApiFieldCatalog.MCcanonical(MCbinding.MCservice(),MCbinding.MCpath());MCdefinitionApiField.setValue(MCkey.startsWith("legacy:")?"Campo anterior sin equivalencia":MCApiFieldCatalog.MClabel(MCbinding.MCservice(),MCkey));}}catch(Exception MCe){MCerror(MCe);}}
    private void MCnewDefinition(){MCselectedDefinition=null;MCdefinitionList.getSelectionModel().clearSelection();MCdefinitionKey.setDisable(false);MCdefinitionKey.clear();MCdefinitionLabel.clear();MCdefinitionType.setValue(MCTYPES.get(0));MCdefinitionDefault.clear();MCdefinitionApiEnabled.setSelected(false);MCdefinitionApiService.setValue("DNI");MCdefinitionApiField.setValue(null);MCcomplementBoth.setSelected(true);MCclientScope.setSelected(true);MCautoCurrentDate.setSelected(false);MCcurrentDateFormat.setValue("dd/MM/yyyy");MCupdateAutomaticDatePreview();MCrefreshComplements();}
    private void MCrefreshDefinitionApiFields(){String MCcurrent=MCdefinitionApiField.getValue();MCdefinitionApiField.getItems().setAll(MCApiFieldCatalog.MCfields(MCdefinitionApiService.getValue()).stream().map(MCApiFieldCatalog.MCField::MClabel).toList());if(MCcurrent!=null&&MCdefinitionApiField.getItems().contains(MCcurrent))MCdefinitionApiField.setValue(MCcurrent);else MCdefinitionApiField.getSelectionModel().selectFirst();}
    private void MCsaveDefinition(){String MCkey=MCdefinitionKey.getText().trim(),MClabel=MCdefinitionLabel.getText().trim();if(!MCkey.matches("[a-zA-Z][a-zA-Z0-9_]*"))throw new IllegalArgumentException("El identificador debe empezar con letra y contener letras, números o guion bajo.");if(MClabel.isBlank())throw new IllegalArgumentException("Escriba una etiqueta.");try{String MCtype=MCdefinitionType.getValue();if(!MCdefinitionDefault.getText().isBlank()&&!"Fecha".equals(MCtype))MCVariableInput.MCvalidate(MCtype,MCdefinitionDefault.getText(),Locale.getDefault());String MCstored=null;if(MCdefinitionApiEnabled.isSelected()){String MCrequested=MCdefinitionApiField.getValue();MCApiFieldCatalog.MCField MCfield=MCApiFieldCatalog.MCfields(MCdefinitionApiService.getValue()).stream().filter(MCf->MCf.MClabel().equals(MCrequested)).findFirst().orElse(null);if(MCfield!=null)MCstored="field:"+MCfield.MCkey();else if(MCselectedDefinition!=null&&"Campo anterior sin equivalencia".equals(MCrequested)){MCApiBinding MCprior=MCdata.MCapiBinding(MCselectedDefinition.MCid());if(MCprior!=null)MCstored=MCprior.MCpath();}if(MCstored==null)throw new IllegalArgumentException("Seleccione un dato solicitado.");}MCDefinition MCsaved=MCstore.MCsaveDefinition(new MCDefinition(MCselectedDefinition==null?0:MCselectedDefinition.MCid(),MCkey,MClabel,MCtype,MCdefinitionDefault.getText(),MCautoCurrentDate.isSelected(),MCcurrentDateFormat.getValue(),!MCglobalScope.isSelected()));if(MCdefinitionApiEnabled.isSelected())MCdata.MCsaveApiBinding(new MCApiBinding(MCsaved.MCid(),MCdefinitionApiService.getValue(),MCstored));else MCdata.MCdeleteApiBinding(MCsaved.MCid());MCselectedDefinition=MCsaved;MCrefreshDefinitions();MCrefreshVariables();MCrefreshComplements();MCsaved("Variable guardada en el catálogo");}catch(Exception MCe){MCerror(MCe);}}
    private void MCduplicateDefinition(){MCDefinition MCselected=MCdefinitionList.getSelectionModel().getSelectedItem();if(MCselected==null)return;try{Set<String> MCkeys=new HashSet<>();for(MCDefinition MCd:MCstore.MCdefinitions())MCkeys.add(MCd.MCkey());String MCbase=MCselected.MCkey()+"_copia",MCkey=MCbase;int MCsuffix=2;while(MCkeys.contains(MCkey))MCkey=MCbase+MCsuffix++;MCDefinition MCcopy=MCstore.MCsaveDefinition(new MCDefinition(0,MCkey,MCselected.MClabel()+" (copia)",MCVariableInput.MCvisualType(MCselected.MCtype()),"",MCselected.MCautoCurrentDate(),MCselected.MCdateFormat(),MCselected.MCbindToClient()));MCselectedDefinition=MCcopy;MCrefreshDefinitions();MCdefinitionList.getSelectionModel().select(MCcopy);MCsaved("Variable duplicada sin datos ni asociaciones");}catch(Exception MCe){MCerror(MCe);}}
    private void MCdeleteDefinition(){
        MCDefinition MCselected=MCdefinitionList.getSelectionModel().getSelectedItem();if(MCselected==null)return;
        try{
            List<MCTemplate> MCaffected=MCstore.MCdefinitionTemplates(MCselected.MCid());
            if(!MCaffected.isEmpty()){
                ButtonType MCopen=new ButtonType("Abrir editor");Alert MCwarning=new Alert(Alert.AlertType.WARNING,"La variable se utiliza en estas plantillas:\n"+String.join("\n",MCaffected.stream().map(MCTemplate::MCname).toList())+"\n\nQuite o reemplace cada aparición y desvincule la variable antes de eliminarla.",MCopen,ButtonType.CANCEL);
                if(MCwarning.showAndWait().orElse(ButtonType.CANCEL)==MCopen)MCopenAffectedTemplate(MCaffected.get(0));return;
            }
            Alert MCconfirm=new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la variable "+MCselected.MCkey()+"?",ButtonType.YES,ButtonType.CANCEL);if(MCconfirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;
            MCdata.MCdeleteApiBinding(MCselected.MCid());MCstore.MCdeleteDefinition(MCselected.MCid());MCnewDefinition();MCrefreshDefinitions();MCrefreshVariables();MCsaved("Variable eliminada");
        }catch(Exception MCe){MCerror(MCe);}
    }
    private void MCopenAffectedTemplate(MCTemplate MCtemplate){
        ToggleButton MCeditor=MCnavigation.stream().filter(MCbutton->"Editor de plantillas".equals(MCbutton.getText())).findFirst().orElseThrow();
        if(MCmenu.getSelectedToggle()==MCeditor)MCshowEditor();else MCmenu.selectToggle(MCeditor);
        MCopenTemplate(MCtemplate);
    }
    private void MCeditDefinitionFromEditor(){MCDefinition MCd=MCvariableList.getSelectionModel().getSelectedItem();MCmenu.getToggles().stream().filter(MCt->MCt instanceof ToggleButton MCb&&"Variables".equals(MCb.getText())).findFirst().ifPresent(MCmenu::selectToggle);if(MCd!=null){MCselectedDefinition=MCd;MCdefinitionList.getSelectionModel().select(MCdefinitionList.getItems().stream().filter(MCx->MCx.MCid()==MCd.MCid()).findFirst().orElse(null));}}

    private BorderPane MCeditorPage;
    private void MCbuildEditor(){
        MCeditorPage=new BorderPane();MCeditorPage.getStyleClass().add("page");
        BorderPane MCdocumentPage=new BorderPane();MCdocumentPage.getStyleClass().add("page");
        FlowPane MCtoolbar=new FlowPane(6,4);MCtoolbar.setAlignment(Pos.CENTER_LEFT);
        MCtemplateChoice.setPrefWidth(190);MCribbonControl(MCtemplateChoice,"Plantilla — Selecciona la plantilla que desea editar");MCribbonChoice(MCtemplateChoice,Feather.FILE_TEXT);MCtemplateChoice.setPromptText("Seleccione plantilla");
        MCtemplateChoice.setOnAction(MCe->{if(!MCupdatingTemplates)MCsafe(()->MCopenTemplate(MCtemplateChoice.getValue()));});
        MCtemplateName.setPromptText("Nombre de la plantilla");MCtemplateName.setPrefWidth(150);MCribbonControl(MCtemplateName,"Nombre de plantilla — Cambia el nombre de la plantilla actual");MCtemplateName.textProperty().addListener((MCo,MCa,MCb)->{if(MCcurrentTemplate!=null&&!Objects.equals(MCb,MCcurrentTemplate.MCname())){MCdirty=true;MCupdateEditorSaveStatus();}});
        Button MCsave=MCribbonButton("Guardar plantilla",Feather.SAVE,"Guardar plantilla — Conserva los cambios del documento actual",this::MCrequestSaveTemplate);MCsave.getStyleClass().add("primary-btn");MCeditorSaveStatus.getStyleClass().add("editor-save-status");
        MCtoolbar.getChildren().addAll(MCribbonButton("",Feather.FOLDER,"Abrir Plantillas — Abre la biblioteca de plantillas",this::MCshowLibrary),MCtemplateChoice,MCtemplateName,MCeditorSaveStatus,MCsave,MCribbonButton("Nueva",Feather.FILE_PLUS,"Nueva plantilla — Crea una plantilla en blanco",this::MCnewTemplate),MCribbonButton("Importar",Feather.UPLOAD,"Importar Word — Abre un documento Word como plantilla",this::MCimportDocx));
        MCtoolbar.getStyleClass().add("toolbar");
        MCtoolbar.getStyleClass().add("editor-toolbar");VBox MCtop=new VBox(4,MCtoolbar,MCbuildEditorTools());MCdocumentPage.setTop(MCtop);
        MCpaletteSearch.setPromptText("Buscar variable");MCpaletteSearch.textProperty().addListener((MCo,MCa,MCb)->MCrefreshVariables());
        Label MCpaletteTitle=new Label("Variables");Region MCpaletteSpacer=new Region();HBox.setHgrow(MCpaletteSpacer,Priority.ALWAYS);
        MCeditorPersona.setPromptText("General (sin persona)");MCeditorPersona.setMaxWidth(Double.MAX_VALUE);MCeditorPersona.setMinWidth(0);VBox MCpeople=new VBox(5,new Label("Personas en este documento"),MCeditorPersona,MCbutton("+ Agregar persona",this::MCaddEditorPersona),new HBox(4,MCbutton("Renombrar",this::MCrenameEditorPersona),MCbutton("Quitar",this::MCremoveEditorPersona)));MCpeople.getChildren().get(0).setStyle("-fx-font-size: 11px;");
        VBox MCpaletteBody=new VBox(7,MCpeople,MCpaletteSearch,MCvariableList,MCbutton("Insertar en cursor",this::MCinsertSelected),MCbutton("Abrir Variables",this::MCeditDefinitionFromEditor));VBox.setVgrow(MCvariableList,Priority.ALWAYS);
        MCpaletteToggle=MCbutton("",()->{
            boolean MCvisible=!MCpaletteBody.isManaged();MCvariablesView.setSelected(MCvisible);MCpaletteBody.setVisible(MCvisible);MCpaletteBody.setManaged(MCvisible);MCpaletteTitle.setVisible(MCvisible);MCpaletteTitle.setManaged(MCvisible);MCpaletteSpacer.setVisible(MCvisible);MCpaletteSpacer.setManaged(MCvisible);
            MCpalettePanel.setPrefWidth(MCvisible?250:68);MCpalettePanel.setMinWidth(MCvisible?250:68);
            MCpaletteToggle.setGraphic(new FontIcon(MCvisible?"fth-chevron-left":"fth-chevron-right"));String MCaction=MCvisible?"Contraer variables":"Expandir variables";MCpaletteToggle.setAccessibleText(MCaction);MCpaletteToggle.setTooltip(new Tooltip(MCaction));Platform.runLater(this::MCupdateZoom);
        });
        MCpaletteToggle.setGraphic(new FontIcon("fth-chevron-left"));MCpaletteToggle.setAccessibleText("Contraer variables");MCpaletteToggle.setTooltip(new Tooltip("Contraer variables"));MCpaletteToggle.setStyle("-fx-padding: 8;");MCpaletteToggle.setMinSize(36,36);MCpaletteToggle.setPrefSize(36,36);MCpaletteToggle.setMaxSize(36,36);
        HBox MCpaletteHeader=new HBox(4,MCpaletteTitle,MCpaletteSpacer,MCpaletteToggle);MCpaletteHeader.setAlignment(Pos.CENTER_LEFT);
        MCpalettePanel=new VBox(7,MCpaletteHeader,MCpaletteBody);VBox.setVgrow(MCpaletteBody,Priority.ALWAYS);
        MCpalettePanel.getStyleClass().add("side-panel");MCpalettePanel.setPrefWidth(250);MCpalettePanel.setMinWidth(250);VBox.setVgrow(MCvariableList,Priority.ALWAYS);
        MCvariableList.setCellFactory(MClist->new ListCell<>(){
            @Override protected void updateItem(MCDefinition MCv,boolean MCempty){super.updateItem(MCv,MCempty);setText(MCempty||MCv==null?null:MCv.MClabel());setTooltip(MCempty||MCv==null?null:new Tooltip(MCv.MClabel()+"\n"+MCv.MCkey()+" · "+MCv.MCtype()+(MCv.MCdefaultValue()==null||MCv.MCdefaultValue().isBlank()?"":"\nPredeterminado: "+MCv.MCdefaultValue())));}
        });
        MCvariableList.setOnDragDetected(MCe->{MCDefinition MCv=MCvariableList.getSelectionModel().getSelectedItem();if(MCv!=null){Dragboard MCdb=MCvariableList.startDragAndDrop(TransferMode.COPY);ClipboardContent MCcc=new ClipboardContent();MCcc.putString("definition:"+MCv.MCid()+":"+MCeditorTokenKey(MCv));MCdb.setContent(MCcc);MCe.consume();}});
        MCdocumentPage.setLeft(MCpalettePanel);
        MCrichEditor.setContextMenuEnabled(true);
        MCrichEditor.getEngine().getLoadWorker().stateProperty().addListener((MCo,MCa,MCb)->{if(MCb==Worker.State.SUCCEEDED){if(MCdocument==null||MCrichModel==null||!Boolean.TRUE.equals(MCrichEditor.getEngine().executeScript("typeof window.MCstudio !== 'undefined'"))){MCeditorReady=false;return;}MCeditorReady=true;((JSObject)MCrichEditor.getEngine().executeScript("window")).setMember("MCjavaBridge",MCeditorBridge);MCrichEditor.getEngine().executeScript("window.MCstudio.MCpaper("+MCpageWidth+","+MCpageHeight+","+MCeditorLeft+","+MCeditorTop+","+MCeditorRight+","+MCeditorBottom+")");MCeditorScript("MCpersonas("+MCjsonString(MCeditorPersonaNames)+")");MCeditorScript("MCnotifySelectionFormat()");MCupdateEditorView();MCupdateZoom();}});
        MCrichEditor.getEngine().locationProperty().addListener((MCo,MCa,MCb)->{if(MCb!=null&&(MCb.startsWith("http:")||MCb.startsWith("https:")))MCrichEditor.getEngine().loadContent("");});
        MCrichEditor.widthProperty().addListener((MCo,MCa,MCb)->MCupdateZoom());MCrichEditor.heightProperty().addListener((MCo,MCa,MCb)->MCupdateZoom());
        MCdocumentPage.setCenter(MCrichEditor);
        MCeditorTabs=new TabPane(new Tab("Documento",MCdocumentPage));MCeditorTabs.getTabs().get(0).setClosable(false);MCeditorPage.setCenter(MCeditorTabs);
    }
    private void MCattachGenerationConfiguration(){Tab MCconfig=new Tab("Configurar generación",MCcatalogPage);MCconfig.setClosable(false);MCeditorTabs.getTabs().add(MCconfig);MCeditorTabs.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->{if(MCb==MCconfig){if(MCdirty)MCsafe(this::MCsaveTemplate);MCrefreshTemplates();MCrefreshKinds();if(MCcurrentTemplate!=null&&MCkindTemplate.getValue()==null){MCkindTemplate.setValue(MCcurrentTemplate);}}});}
    private void MCshowEditor(){MCrefreshTemplates();MCrefreshVariables();MCeditorTabs.getSelectionModel().select(0);MCpage("Editor de plantillas",MCeditorPage);if(MCdocument==null)MCrichEditor.getEngine().loadContent("<html><body style='font:14px Arial;padding:30px'>Importe un DOCX o cree una plantilla nueva.</body></html>");}
    private void MCrefreshTemplates(){try{MCupdatingTemplates=true;MCTemplate MCselected=MCcurrentTemplate;MCtemplateChoice.getItems().setAll(MCstore.MCtemplates());MCkindTemplate.getItems().setAll(MCtemplateChoice.getItems());if(MCselected!=null)MCtemplateChoice.getSelectionModel().select(MCtemplateChoice.getItems().stream().filter(MCt->MCt.MCid()==MCselected.MCid()).findFirst().orElse(null));}catch(Exception MCe){MCerror(MCe);}finally{MCupdatingTemplates=false;}}
    private void MCnewTemplate(){TextInputDialog MCname=new TextInputDialog();MCname.setTitle("Nueva plantilla");MCname.setHeaderText("Nombre de la plantilla");String MCselected=MCname.showAndWait().orElse(null);if(MCselected==null)return;if(MCselected.isBlank())throw new IllegalArgumentException("Escriba un nombre.");if(!MCconfirmPendingSwitch())return;try{XWPFDocument MCd=new XWPFDocument();MCd.createParagraph().createRun().setText("Escriba aquí el contenido del documento.");byte[] MCcontent=MCDocumentEngine.MCbytes(MCd);MCd.close();MCTemplate MCt=MCstore.MCsaveTemplate(new MCTemplate(0,MCselected.trim(),MCcontent,MCcontent));MCdata.MCrecordRevision(MCt.MCid(),MCcontent);MCTemplateGroup MCselectedGroup=MCgroupList.getSelectionModel().getSelectedItem();if(MCselectedGroup!=null)MCstore.MCsetTemplateGroups(MCt.MCid(),List.of(MCselectedGroup.MCid()));MCrefreshTemplates();MCrefreshLibrary();MCrefreshKinds();MCopenTemplate(MCt);MCsaved("Plantilla creada");}catch(Exception MCe){MCerror(MCe);}}
    private void MCimportDocx(){FileChooser MCfc=new FileChooser();MCfc.setTitle("Importar documento Word");MCfc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos de Word (*.doc, *.docx, *.docm, *.dot, *.dotx, *.dotm)","*.doc","*.DOC","*.docx","*.DOCX","*.docm","*.DOCM","*.dot","*.DOT","*.dotx","*.DOTX","*.dotm","*.DOTM"));File MCfile=MCfc.showOpenDialog(MCstage);if(MCfile==null||!MCconfirmPendingSwitch())return;try{byte[] MCcontent=MCWordDocumentImporter.MCimportToDocx(MCfile.toPath());MCTemplate MCt=MCstore.MCsaveTemplate(new MCTemplate(0,MCfile.getName().replaceFirst("(?i)\\.(docx?|docm|dotx?|dotm)$",""),MCcontent,MCcontent));MCdata.MCrecordRevision(MCt.MCid(),MCcontent);MCTemplateGroup MCselectedGroup=MCgroupList.getSelectionModel().getSelectedItem();if(MCselectedGroup!=null)MCstore.MCsetTemplateGroups(MCt.MCid(),List.of(MCselectedGroup.MCid()));MCrefreshTemplates();MCrefreshLibrary();MCrefreshKinds();MCopenTemplate(MCt);MCsaved("Documento Word importado y guardado localmente");}catch(Exception MCe){MCerror(MCe);}}
    private boolean MCconfirmPendingSwitch(){if(MCdocument==null||!MCdirty)return true;Alert MCchoice=new Alert(Alert.AlertType.CONFIRMATION,"Hay cambios pendientes. ¿Guardar antes de abrir otra plantilla?",ButtonType.YES,ButtonType.NO,ButtonType.CANCEL);ButtonType MCresult=MCchoice.showAndWait().orElse(ButtonType.CANCEL);if(MCresult==ButtonType.CANCEL)return false;if(MCresult==ButtonType.YES)MCsaveTemplate();else MCdirty=false;return true;}
    private void MCopenTemplate(MCTemplate MCt){if(MCt==null)return;try{if(MCcurrentTemplate!=null&&MCcurrentTemplate.MCid()!=MCt.MCid()&&!MCconfirmPendingSwitch())return;if(MCdocument!=null)MCdocument.close();MCcurrentTemplate=MCt;MCdocument=MCDocumentEngine.MCopen(MCt.MCedited());MCselectedRun=null;MCselectedParagraph=null;MCselectedText=null;MCtemplateName.setText(MCt.MCname());MCdirty=false;MCupdateEditorSaveStatus();MCrefreshVariables();MCrefreshEditorPersonas();MCrenderDocument();MCupdatingTemplates=true;MCtemplateChoice.getSelectionModel().select(MCtemplateChoice.getItems().stream().filter(MCx->MCx.MCid()==MCt.MCid()).findFirst().orElse(MCt));MCupdatingTemplates=false;}catch(Exception MCe){MCupdatingTemplates=false;MCerror(MCe);}}
    private void MCrequestSaveTemplate(){if(MCdocument==null)return;if(!MCdirty&&Objects.equals(MCtemplateName.getText().trim(),MCcurrentTemplate.MCname())){MCsaved("La plantilla ya está guardada");return;}if(new Alert(Alert.AlertType.CONFIRMATION,"¿Guardar los cambios de esta plantilla?",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.YES)MCsaveTemplate();}
    private void MCsaveTemplate(){if(MCdocument==null)return;if(MCtemplateName.getText().isBlank())throw new IllegalArgumentException("Escriba un nombre de plantilla.");try{MCsyncEditor();byte[] MCcontent=MCDocumentEngine.MCbytes(MCdocument);boolean MCcontentChanged=!Arrays.equals(MCcontent,MCcurrentTemplate.MCedited());boolean MCnameChanged=!MCtemplateName.getText().trim().equals(MCcurrentTemplate.MCname());if(!MCcontentChanged&&!MCnameChanged){MCdirty=false;MCsaved("La plantilla ya está guardada");return;}MCcurrentTemplate=MCstore.MCsaveTemplate(new MCTemplate(MCcurrentTemplate.MCid(),MCtemplateName.getText().trim(),MCcurrentTemplate.MCoriginal(),MCcontent));if(MCcontentChanged)MCdata.MCrecordRevision(MCcurrentTemplate.MCid(),MCcontent);MCdirty=false;MCrefreshTemplates();MCrefreshLibrary();MCrefreshKinds();MCsaved("Plantilla guardada");MCrenderDocument();}catch(Exception MCe){throw new RuntimeException("No se pudo guardar la plantilla",MCe);}}
    private void MCrefreshVariables(){try{long MCselected=MCvariableList.getSelectionModel().getSelectedItem()==null?0:MCvariableList.getSelectionModel().getSelectedItem().MCid();String MCq=MCpaletteSearch.getText().trim().toLowerCase();MCvariableList.getItems().setAll(MCstore.MCdefinitions().stream().filter(MCd->(MCd.MClabel()+" "+MCd.MCkey()).toLowerCase().contains(MCq)).toList());if(MCselected!=0)MCvariableList.getSelectionModel().select(MCvariableList.getItems().stream().filter(MCd->MCd.MCid()==MCselected).findFirst().orElse(null));}catch(Exception MCe){MCerror(MCe);}}
    private void MCbuildLibrary(){
        MClibraryPage=new BorderPane();MClibraryPage.getStyleClass().add("page");MClibrarySearch.setPromptText("Buscar plantilla");MClibrarySearch.getStyleClass().add("buscador");MClibrarySearch.textProperty().addListener((MCo,MCa,MCb)->MCrefreshLibrary());
        MCgroupList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->{if(!MCrefreshingLibrary)MCrefreshLibrary();});
        VBox MCgroups=new VBox(8,new Label("GRUPOS"),MCgroupList,MCbutton("+ Nuevo grupo",this::MCcreateGroup),MCbutton("Renombrar grupo",this::MCrenameGroup),MCbutton("Eliminar grupo",this::MCremoveGroup));MCgroups.getStyleClass().add("side-panel");MCfixedWidth(MCgroups,240);MCuniformButtons(MCgroups);BorderPane.setMargin(MCgroups,new Insets(0,12,0,0));VBox.setVgrow(MCgroupList,Priority.ALWAYS);MClibraryPage.setLeft(MCgroups);
        MClibrarySort.getItems().setAll("Nombre A-Z","Nombre Z-A","Más recientes","Más antiguas");MClibrarySort.setValue("Nombre A-Z");MClibrarySort.valueProperty().addListener((MCo,MCa,MCb)->MCrefreshLibrary());
        HBox MCsearchRow=new HBox(8,MClibrarySearch,MClibrarySort);HBox.setHgrow(MClibrarySearch,Priority.ALWAYS);MClibrarySearch.setMaxWidth(Double.MAX_VALUE);MClibrarySort.setPrefWidth(135);
        MCgroupList.setPlaceholder(MCdescription("Cree su primer grupo para organizar las plantillas."));
        VBox MCtemplates=new VBox(9,MCsearchRow,MClibraryList);MCtemplates.setMinWidth(0);MCtemplates.getStyleClass().add("catalog-form");MCtemplates.setMaxWidth(Double.MAX_VALUE);VBox.setVgrow(MClibraryList,Priority.ALWAYS);MClibraryPage.setCenter(MCtemplates);
        MClibraryList.setCellFactory(MCview->new ListCell<>(){
            private final Label MCtitle=new Label(),MCdetail=new Label();private final VBox MClines=new VBox(4,MCtitle,MCdetail);
            {MCtitle.setWrapText(true);MCdetail.setWrapText(true);MCdetail.getStyleClass().add("field-label");MClines.setMinWidth(0);MClines.prefWidthProperty().bind(widthProperty().subtract(28));}
            @Override protected void updateItem(MCTemplate MCtemplate,boolean MCempty){
                super.updateItem(MCtemplate,MCempty);getStyleClass().remove("library-associated");setText(null);setGraphic(null);
                if(MCempty||MCtemplate==null)return;
                List<Long> MCmemberships=MClibraryMemberships.getOrDefault(MCtemplate.MCid(),List.of());MCTemplateGroup MCgroup=MCgroupList.getSelectionModel().getSelectedItem();
                if(MCgroup!=null&&MClibraryHighlighted(MCmemberships,MCgroup.MCid()))getStyleClass().add("library-associated");
                MCtitle.setText(MCtemplate.MCname());String MCnames=MCmemberships.stream().map(MClibraryGroupNames::get).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));
                String MCdate=MClibraryModifiedDates.get(MCtemplate.MCid());MCdetail.setText(MCnames+(MCdate==null?"":" · "+MCdate));setGraphic(MClines);
            }
        });
        MClibraryGroups.setWrapText(true);MCaddToGroup=MCbutton("Añadir plantilla al grupo",this::MCaddLibraryGroup);MCremoveFromGroup=MCbutton("Quitar del grupo",this::MCremoveLibraryGroup);
        VBox MCactions=new VBox(9,new Label("PLANTILLA SELECCIONADA"),MCbutton("Abrir en editor",this::MCopenLibraryTemplate),MCbutton("Renombrar plantilla",this::MCrenameLibraryTemplate),MCbutton("Duplicar plantilla",this::MCduplicateLibraryTemplate),MCbutton("Eliminar plantilla",this::MCremoveLibraryTemplate),new Separator(),new Label("GRUPOS ASIGNADOS"),MClibraryGroups,MCaddToGroup,MCremoveFromGroup);MCactions.getStyleClass().add("side-panel");MCfixedWidth(MCactions,270);MCuniformButtons(MCactions);MClibraryPage.setRight(MCscrolling(MCactions));BorderPane.setMargin(MClibraryPage.getRight(),new Insets(0,0,0,12));
        MClibraryList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->MCupdateLibraryActions());MCupdateLibraryActions();
    }
    private void MCshowLibrary(){MCrefreshLibrary();MCpage("Plantillas",MClibraryPage);}
    private void MCrefreshLibrary(){if(MCrefreshingLibrary)return;MCrefreshingLibrary=true;try{
        MCTemplate MCselected=MClibraryList.getSelectionModel().getSelectedItem();MCTemplateGroup MCselectedGroup=MCgroupList.getSelectionModel().getSelectedItem();String MCq=MClibrarySearch.getText().trim().toLowerCase();List<MCTemplate> MCall=MCstore.MCtemplates();List<MCTemplateGroup> MCgroups=MCstore.MCgroups();Map<Long,String> MCdates=MCdata.MCrevisionDates();Map<Long,List<Long>> MCmemberships=new HashMap<>();for(MCTemplate MCt:MCall)MCmemberships.put(MCt.MCid(),MCstore.MCgroupIds(MCt.MCid()));MClibraryMemberships=MCmemberships;MClibraryModifiedDates=MCdates;Map<Long,String> MCnames=new HashMap<>();for(MCTemplateGroup MCg:MCgroups)MCnames.put(MCg.MCid(),MCg.MCname());MClibraryGroupNames=MCnames;
        MCgroupList.getItems().setAll(MCgroups);
        MCTemplateGroup MCactive=MCgroups.stream().filter(MCg->MCselectedGroup!=null&&MCg.MCid()==MCselectedGroup.MCid()).findFirst().orElse(MCgroups.isEmpty()?null:MCgroups.get(0));
        MCgroupList.getSelectionModel().select(MCactive);
        MClibraryList.setPlaceholder(MCdescription(MCall.isEmpty()?"Todavía no hay plantillas guardadas. Créelas o impórtelas desde el Editor de plantillas.":"No hay plantillas que coincidan con la búsqueda."));
        List<MCTemplate> MCfound=new ArrayList<>();for(MCTemplate MCt:MCall)if(MClibraryMatches(MCt.MCname(),MCq))MCfound.add(MCt);
        Comparator<MCTemplate> MCname=Comparator.comparing(MCTemplate::MCname,String.CASE_INSENSITIVE_ORDER).thenComparingLong(MCTemplate::MCid);String MCsort=MClibrarySort.getValue();Comparator<MCTemplate> MCorder=switch(MCsort==null?"Nombre A-Z":MCsort){case "Nombre Z-A"->MCname.reversed();case "Más recientes"->Comparator.comparing((MCTemplate MCt)->MCdates.get(MCt.MCid()),Comparator.nullsFirst(String::compareTo)).reversed().thenComparing(MCname);case "Más antiguas"->Comparator.comparing((MCTemplate MCt)->MCdates.get(MCt.MCid()),Comparator.nullsLast(String::compareTo)).thenComparing(MCname);default->MCname;};if(MCactive!=null)MCorder=Comparator.comparing((MCTemplate MCt)->!MCmemberships.getOrDefault(MCt.MCid(),List.of()).contains(MCactive.MCid())).thenComparing(MCorder);MCfound.sort(MCorder);
        MClibraryList.getItems().setAll(MCfound);if(MCselected!=null)MClibraryList.getSelectionModel().select(MCfound.stream().filter(MCt->MCt.MCid()==MCselected.MCid()).findFirst().orElse(null));MClibraryList.refresh();MCupdateLibraryActions();
    }catch(Exception MCex){MCerror(MCex);}finally{MCrefreshingLibrary=false;}}
    static boolean MClibraryMatches(String MCname,String MCsearch){return MCname.toLowerCase(Locale.ROOT).contains(MCsearch.toLowerCase(Locale.ROOT));}
    static boolean MClibraryHighlighted(List<Long> MCmemberships,long MCselectedGroup){return MCselectedGroup>0&&MCmemberships.contains(MCselectedGroup);}
    private void MCupdateLibraryActions(){try{MCTemplate MCt=MClibraryList.getSelectionModel().getSelectedItem();MCTemplateGroup MCgroup=MCgroupList.getSelectionModel().getSelectedItem();List<Long> MCids=MCt==null?List.of():MCstore.MCgroupIds(MCt.MCid());String MCnames=MCstore.MCgroups().stream().filter(MCg->MCids.contains(MCg.MCid())).map(MCTemplateGroup::MCname).collect(java.util.stream.Collectors.joining(", "));MClibraryGroups.setText(MCt==null?"Seleccione una plantilla":MCnames.isBlank()?"Sin grupo":MCnames);boolean MCready=MCt!=null&&MCgroup!=null&&MCgroup.MCid()>0;boolean MCmember=MCready&&MCids.contains(MCgroup.MCid());if(MCaddToGroup!=null)MCaddToGroup.setDisable(MCgroup==null);if(MCremoveFromGroup!=null)MCremoveFromGroup.setDisable(!MCready||!MCmember);}catch(Exception MCex){MCerror(MCex);}}
    private void MCaddLibraryGroup(){try{
        MCTemplateGroup MCgroup=MCselectedGroup();List<MCTemplate> MCavailable=MCstore.MCtemplates().stream().filter(MCt->!MClibraryMemberships.getOrDefault(MCt.MCid(),List.of()).contains(MCgroup.MCid())).toList();
        if(MCavailable.isEmpty()){MCsaved("Todas las plantillas guardadas ya pertenecen a este grupo. Cree o importe otra plantilla.");return;}
        ChoiceDialog<MCTemplate> MCchoice=new ChoiceDialog<>(MCavailable.get(0),MCavailable);MCchoice.setTitle("Añadir plantilla al grupo");MCchoice.setHeaderText("Seleccione una plantilla para "+MCgroup.MCname());
        MCchoice.showAndWait().ifPresent(MCt->{try{Set<Long> MCids=new LinkedHashSet<>(MCstore.MCgroupIds(MCt.MCid()));MCids.add(MCgroup.MCid());MCstore.MCsetTemplateGroups(MCt.MCid(),MCids);MCrefreshLibrary();MClibraryList.getSelectionModel().select(MCt);MCsaved("Plantilla añadida al grupo");}catch(Exception MCex){MCerror(MCex);}});
    }catch(Exception MCex){MCerror(MCex);}}
    private void MCremoveLibraryGroup(){MCchangeLibraryGroup(false);}
    private void MCchangeLibraryGroup(boolean MCadd){try{MCTemplate MCt=MCselectedLibraryTemplate();MCTemplateGroup MCg=MCselectedGroup();Set<Long> MCids=new LinkedHashSet<>(MCstore.MCgroupIds(MCt.MCid()));if(MCadd)MCids.add(MCg.MCid());else MCids.remove(MCg.MCid());MCstore.MCsetTemplateGroups(MCt.MCid(),MCids);MCrefreshLibrary();MCsaved(MCadd?"Plantilla añadida al grupo":"Plantilla quitada del grupo");}catch(Exception MCex){MCerror(MCex);}}
    private MCTemplate MCselectedLibraryTemplate(){MCTemplate MCt=MClibraryList.getSelectionModel().getSelectedItem();if(MCt==null)throw new IllegalArgumentException("Seleccione una plantilla.");return MCt;}
    private MCTemplateGroup MCselectedGroup(){MCTemplateGroup MCg=MCgroupList.getSelectionModel().getSelectedItem();if(MCg==null||MCg.MCid()<=0)throw new IllegalArgumentException("Seleccione un grupo de Mis grupos.");return MCg;}
    private void MCcreateGroup(){TextInputDialog MCinput=new TextInputDialog();MCinput.setTitle("Nuevo grupo");MCinput.setHeaderText("Nombre del grupo de plantillas");MCinput.showAndWait().ifPresent(MCname->{try{MCTemplateGroup MCg=MCstore.MCsaveGroup(0,MCname);MCrefreshLibrary();MCgroupList.getSelectionModel().select(MCg);MCsaved("Grupo creado");}catch(Exception MCex){MCerror(MCex);}});}
    private void MCrenameGroup(){MCTemplateGroup MCg=MCselectedGroup();TextInputDialog MCinput=new TextInputDialog(MCg.MCname());MCinput.setTitle("Renombrar grupo");MCinput.setHeaderText("Nombre del grupo");MCinput.showAndWait().ifPresent(MCname->{try{MCstore.MCsaveGroup(MCg.MCid(),MCname);MCrefreshLibrary();MCsaved("Grupo renombrado");}catch(Exception MCex){MCerror(MCex);}});}
    private void MCremoveGroup(){try{MCTemplateGroup MCg=MCselectedGroup();if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar el grupo '"+MCg.MCname()+"'? Sus "+MCg.MCcount()+" plantillas permanecerán guardadas.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;MCstore.MCdeleteGroup(MCg.MCid(),null);MCgroupList.getSelectionModel().clearSelection();MCrefreshLibrary();MCsaved("Grupo eliminado; las plantillas permanecen guardadas");}catch(Exception MCex){MCerror(MCex);}}
    private void MCopenLibraryTemplate(){MCTemplate MCt=MCselectedLibraryTemplate();MCshowEditor();MCopenTemplate(MCt);}
    private void MCrenameLibraryTemplate(){MCTemplate MCt=MCselectedLibraryTemplate();TextInputDialog MCinput=new TextInputDialog(MCt.MCname());MCinput.setTitle("Renombrar plantilla");MCinput.setHeaderText("Nombre de la plantilla");MCinput.showAndWait().ifPresent(MCname->{try{if(MCname.isBlank())throw new IllegalArgumentException("Escriba un nombre.");MCstore.MCsaveTemplate(new MCTemplate(MCt.MCid(),MCname.trim(),MCt.MCoriginal(),MCt.MCedited()));if(MCcurrentTemplate!=null&&MCcurrentTemplate.MCid()==MCt.MCid()){MCcurrentTemplate=new MCTemplate(MCt.MCid(),MCname.trim(),MCt.MCoriginal(),MCt.MCedited());MCtemplateName.setText(MCname.trim());}MCrefreshLibrary();MCrefreshTemplates();MCsaved("Plantilla renombrada");}catch(Exception MCex){MCerror(MCex);}});}
    private void MCduplicateLibraryTemplate(){MCTemplate MCt=MCselectedLibraryTemplate();try{MCTemplate MCcopy=MCstore.MCsaveTemplate(new MCTemplate(0,MCt.MCname()+" (copia)",MCt.MCoriginal(),MCt.MCedited()));for(var MCperson:MCstore.MCpersonas(MCt.MCid()))MCstore.MCaddPersona(MCcopy.MCid(),MCperson.MCpersonaKey(),MCperson.MCdisplayName(),MCperson.MCsortOrder());for(MCVariable MCv:MCstore.MCvariables(MCt.MCid())){MCVariable MCcreated=MCstore.MCsaveVariable(new MCVariable(0,MCcopy.MCid(),MCv.MCkey(),MCv.MClabel(),MCv.MCtype(),MCv.MCdefaultValue(),MCv.MCrequired(),MCv.MCposition()));long MCdef=MCstore.MCdefinitionId(MCv.MCid());if(MCdef>0)MCstore.MClinkDefinition(MCcreated.MCid(),MCdef);}for(MCApiContext MCc:MCdata.MCapiContexts(MCt.MCid()))MCdata.MCsaveApiContext(new MCApiContext(MCcopy.MCid(),MCc.MCvariableKey(),MCc.MCgroupKey(),MCc.MCservice(),MCc.MCsource()));for(MCContactBinding MCb:MCdata.MCcontactBindings(MCt.MCid()))MCdata.MCsaveContactBinding(new MCContactBinding(0,MCcopy.MCid(),MCb.MCgroupKey(),MCb.MCkind(),MCb.MCfield(),MCb.MCvariableKey()));MCdata.MCrecordRevision(MCcopy.MCid(),MCcopy.MCedited());MCrefreshLibrary();MCrefreshTemplates();MClibraryList.getSelectionModel().select(MCcopy);MCsaved("Plantilla duplicada sin grupos");}catch(Exception MCex){MCerror(MCex);}}
    private void MCremoveLibraryTemplate(){MCTemplate MCt=MCselectedLibraryTemplate();try{List<String> MClegacy=MCstore.MCtemplateDependencies(MCt.MCid());String MCdetail=MClegacy.isEmpty()?"":" También se quitarán "+MClegacy.size()+" configuraciones antiguas vinculadas a esta plantilla.";if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la plantilla '"+MCt.MCname()+"'? Los documentos ya generados se conservarán en Historial."+MCdetail,ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;MCstore.MCdeleteTemplateWithLegacyKinds(MCt.MCid());MCdata.MCclearMappings(MCt.MCid());MCdata.MCclearContactBindings(MCt.MCid());MCdata.MCclearApiContexts(MCt.MCid());if(MCcurrentTemplate!=null&&MCcurrentTemplate.MCid()==MCt.MCid()){if(MCdocument!=null)MCdocument.close();MCdocument=null;MCcurrentTemplate=null;MCrichModel=null;MCeditorReady=false;}MCrefreshLibrary();MCrefreshTemplates();MCrefreshKinds();MCrefreshHistory();MCsaved("Plantilla eliminada; documentos conservados en Historial");}catch(Exception MCex){MCerror(MCex);}}
    private void MCinsertSelected(){MCDefinition MCd=MCvariableList.getSelectionModel().getSelectedItem();if(MCd==null||MCdocument==null||!MCeditorReady){MCsaved("Seleccione una variable y coloque el cursor en el documento");return;}if(MCbindVariable(MCd))MCrichEditor.getEngine().executeScript("window.MCstudio.MCinsertToken("+MCjsonString(MCeditorTokenKey(MCd))+")");}
    private boolean MCbindVariable(MCDefinition MCd){return MCbindVariable(MCd,MCeditorTokenKey(MCd));}
    private boolean MCbindVariable(MCDefinition MCd,String MCkey){try{if(MCcurrentTemplate==null)return false;MCVariable MCbound=MCstore.MCvariables(MCcurrentTemplate.MCid()).stream().filter(MCv->MCv.MCkey().equals(MCkey)).findFirst().orElse(null);if(MCbound==null)MCstore.MCbindDefinition(MCcurrentTemplate.MCid(),MCd.MCid(),MCkey,false,MCstore.MCvariables(MCcurrentTemplate.MCid()).size()+1);else if(MCstore.MCdefinitionId(MCbound.MCid())!=MCd.MCid())throw new IllegalArgumentException("Esta plantilla ya utiliza '"+MCd.MCkey()+"' con otra definición.");return true;}catch(Exception MCex){MCerror(MCex);return false;}}
    private String MCjsonString(Object MCvalue){try{return MCjson.writeValueAsString(MCvalue);}catch(Exception MCex){throw new RuntimeException(MCex);}}
    private void MCeditorCommand(String MCname){MCeditorCommand(MCname,null);}
    private void MCeditorCommand(String MCname,String MCvalue){if(MCeditorReady&&!MCupdatingToolbarFromSelection)MCrichEditor.getEngine().executeScript("window.MCstudio.MCcommand("+MCjsonString(MCname)+","+(MCvalue==null?"null":MCjsonString(MCvalue))+")");}
    private void MCsyncEditor() throws Exception {if(MCeditorReady&&MCrichModel!=null&&MCdocument!=null){String MCxml=String.valueOf(MCrichEditor.getEngine().executeScript("window.MCstudio.MChtml()"));MCrichModel.MCapply(MCxml);XWPFDocument MCnormalized=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCdocument));MCdocument.close();MCdocument=MCnormalized;MCrichModel=new MCContinuousDocxEditor(MCdocument,MCeditorPersonaNames);MCrichEditor.getEngine().executeScript("window.MCstudio.MCaccept("+MCjsonString("<div>"+MCrichModel.MChtml()+"</div>")+")");}}
    private void MCupdateToolbarFromSelection(String MCjsonState){
        if(!MCeditorReady)return;
        try{
            var MCstate=MCjson.readTree(MCjsonState);if(MCstate==null||!MCstate.isObject())return;
            MCupdatingToolbarFromSelection=true;
            String MCfont=MCstate.path("fontFamily").asText("Arial");
            if(!MCfontChoice.getItems().contains(MCfont))MCfontChoice.getItems().add(MCfont);
            MCfontChoice.setValue(MCfont);MCfontSizeChoice.setValue(MCstate.path("fontSizePt").asInt(12));
            String MCalignment=MCstate.path("alignment").asText("left");
            MCselectionFormatButtons.forEach((MCkey,MCbutton)->{
                boolean MCactive=Set.of("left","center","right","justify").contains(MCkey)?MCkey.equals(MCalignment):MCstate.path(MCkey).asBoolean(false);
                if(MCactive){if(!MCbutton.getStyleClass().contains("ribbon-btn-active"))MCbutton.getStyleClass().add("ribbon-btn-active");}
                else MCbutton.getStyleClass().remove("ribbon-btn-active");
            });
            MCeditorTextColor.setValue(Color.web(MCstate.path("textColor").asText("#000000")));
            MCeditorHighlightColor.setValue(Color.web(MCstate.path("highlightColor").asText("#ffffff")));
        }catch(IOException|IllegalArgumentException MCignored){}
        finally{MCupdatingToolbarFromSelection=false;}
    }
    public final class MCEditorBridge {
        public void MCsaveCurrentTemplate(){Platform.runLater(()->MCsafe(MCStudioWindow.this::MCsaveTemplate));}
        public void MConSelectionFormatChanged(String MCjsonState){Platform.runLater(()->MCupdateToolbarFromSelection(MCjsonState));}
        public boolean MCbindDroppedDefinition(String MCid,String MCkey){
            try{MCDefinition MCdefinition=MCstore.MCdefinitions().stream().filter(MCd->Long.toString(MCd.MCid()).equals(MCid)&&MCd.MCkey().equals(MCDocumentEngine.MCbaseKey(MCkey))).findFirst().orElse(null);return MCdefinition!=null&&(MCdefinition.MCbindToClient()||MCDocumentEngine.MCpersonaKey(MCkey).isEmpty())&&MCDocumentEngine.MCvalidKey(MCkey)&&(MCDocumentEngine.MCpersonaKey(MCkey).isEmpty()||MCeditorPersonaNames.containsKey(MCDocumentEngine.MCpersonaKey(MCkey)))&&MCeditorReady&&MCbindVariable(MCdefinition,MCkey);}catch(Exception MCe){MCerror(MCe);return false;}
        }
public void MCmarkDirty(){Platform.runLater(()->{MCdirty=true;MCupdateEditorSaveStatus();MCstatus.setText("Cambios sin guardar");});}public void MCpageMoved(){Platform.runLater(MCStudioWindow.this::MCupdatePageIndicator);}public void MCtableSelected(boolean MCselected){Platform.runLater(()->{if(MCtableTools!=null){MCtableTools.setVisible(MCselected);MCtableTools.setManaged(MCselected);}});}public void MCimageSelected(boolean MCselected){Platform.runLater(()->{if(MCimageTools!=null){MCimageTools.setVisible(MCselected);MCimageTools.setManaged(MCselected);}});}}
    private void MCtableAction(String MCaction){MCtableAction(MCaction,null);}
    private void MCtableAction(String MCaction,String MCvalue){if(MCeditorReady)MCrichEditor.getEngine().executeScript("window.MCstudio.MCtableAction("+MCjsonString(MCaction)+","+(MCvalue==null?"null":MCjsonString(MCvalue))+")");}
    private void MCimageAction(String MCaction){if(MCeditorReady)MCrichEditor.getEngine().executeScript("window.MCstudio.MCimageAction("+MCjsonString(MCaction)+")");}
    private <T extends Control> T MCribbonControl(T MCcontrol,String MCdescription){
        MCcontrol.setTooltip(new Tooltip(MCdescription));MCcontrol.setAccessibleText(MCdescription.split(" — ")[0]);MCcontrol.getStyleClass().add("ribbon-btn");MCcontrol.setFocusTraversable(false);return MCcontrol;
    }
    private <T> void MCribbonChoice(ComboBox<T> MCchoice,Feather MCicon){
        MCchoice.setButtonCell(new ListCell<>(){@Override protected void updateItem(T MCvalue,boolean MCempty){super.updateItem(MCvalue,MCempty);setText(MCvalue==null?MCchoice.getPromptText():MCvalue.toString());setGraphic(new FontIcon(MCicon));}});
    }
    private Button MCribbonButton(String MCtext,Feather MCicon,String MCdescription,Runnable MCaction){
        Button MCcontrol=MCribbonControl(new Button(MCtext,new FontIcon(MCicon)),MCdescription);MCcontrol.setOnAction(MCe->MCsafe(MCaction));return MCcontrol;
    }
    private Button MCselectionFormatButton(String MCstate,Feather MCicon,String MCdescription,Runnable MCaction){
        Button MCbutton=MCribbonButton("",MCicon,MCdescription,()->{if(!MCupdatingToolbarFromSelection)MCaction.run();});
        MCselectionFormatButtons.put(MCstate,MCbutton);return MCbutton;
    }
    private ToggleButton MCribbonToggle(String MCtext,Feather MCicon,String MCdescription,boolean MCselected,Runnable MCaction){
        ToggleButton MCcontrol=MCribbonControl(new ToggleButton(MCtext,new FontIcon(MCicon)),MCdescription);MCcontrol.setSelected(MCselected);MCcontrol.setOnAction(MCe->MCsafe(MCaction));return MCcontrol;
    }
    private MenuButton MCribbonMenu(String MCtext,Feather MCicon,String MCdescription){return MCribbonControl(new MenuButton(MCtext,new FontIcon(MCicon)),MCdescription);}
    private MenuItem MCribbonItem(String MCtext,Feather MCicon,String MCdescription,Runnable MCaction){
        Label MClabel=new Label(MCtext,new FontIcon(MCicon));MClabel.setMaxWidth(Double.MAX_VALUE);Tooltip MCtooltip=new Tooltip(MCdescription);Tooltip.install(MClabel,MCtooltip);
        CustomMenuItem MCitem=new CustomMenuItem(MClabel);MCitem.setText(MCtext);MCitem.getProperties().put("ribbon-tooltip",MCtooltip);MCitem.setOnAction(MCe->MCsafe(MCaction));return MCitem;
    }
    private VBox MCribbonGroup(String MCtitle,Node... MCcontrols){
        HBox MCrow=new HBox(3,MCcontrols);MCrow.setAlignment(Pos.CENTER_LEFT);Label MCcaption=new Label(MCtitle);MCcaption.getStyleClass().add("ribbon-caption");
        VBox MCgroup=new VBox(3,MCrow,MCcaption);MCgroup.setAlignment(Pos.CENTER);MCgroup.getStyleClass().add("ribbon-group");return MCgroup;
    }
    private HBox MCribbonRow(Node... MCgroups){
        HBox MCrow=new HBox(3);MCrow.setAlignment(Pos.CENTER_LEFT);for(Node MCgroup:MCgroups){if(!MCrow.getChildren().isEmpty()){Separator MCseparator=new Separator(Orientation.VERTICAL);MCseparator.getStyleClass().add("ribbon-sep");MCrow.getChildren().add(MCseparator);}MCrow.getChildren().add(MCgroup);}return MCrow;
    }
    private MenuButton MCtableGrid(){
        MenuButton MCpicker=MCribbonMenu("Tabla",Feather.GRID,"Insertar tabla — Agrega una tabla editable de filas y columnas en la posición del cursor");
        GridPane MCgrid=new GridPane();MCgrid.setHgap(2);MCgrid.setVgap(2);Label MCdimensions=new Label("Seleccione filas y columnas");VBox MCcontent=new VBox(5,MCdimensions,MCgrid);
        for(int MCrow=1;MCrow<=8;MCrow++)for(int MCcol=1;MCcol<=8;MCcol++){
            final int MCrows=MCrow,MCcols=MCcol;Button MCsquare=MCribbonControl(new Button(),"Insertar tabla — "+MCcols+" columnas × "+MCrows+" filas");MCsquare.getStyleClass().add("ribbon-grid-cell");MCsquare.setMinSize(21,21);MCsquare.setPrefSize(21,21);MCsquare.setMaxSize(21,21);
            MCsquare.setOnMouseEntered(MCe->{MCdimensions.setText(MCcols+" columnas × "+MCrows+" filas");for(Node MCn:MCgrid.getChildren()){int MCc=GridPane.getColumnIndex(MCn)==null?0:GridPane.getColumnIndex(MCn),MCr=GridPane.getRowIndex(MCn)==null?0:GridPane.getRowIndex(MCn);if(MCc<MCcols&&MCr<MCrows){if(!MCn.getStyleClass().contains("accent"))MCn.getStyleClass().add("accent");}else MCn.getStyleClass().remove("accent");}});
            MCsquare.setOnAction(MCe->{MCpicker.hide();if(MCeditorReady)MCrichEditor.getEngine().executeScript("window.MCstudio.MCinsertTable("+MCcols+","+MCrows+")");});MCgrid.add(MCsquare,MCcol-1,MCrow-1);
        }
        MCpicker.getItems().add(new CustomMenuItem(MCcontent,false));return MCpicker;
    }
    private Node MCbuildEditorTools(){
        MCfontChoice.setEditable(false);MCfontChoice.setPrefWidth(130);MCfontChoice.getItems().setAll(Font.getFamilies().stream().filter(MCf->!MCf.toLowerCase().contains("symbol")).toList());MCfontChoice.setPromptText("Fuente");MCfontChoice.setValue("Arial");MCribbonControl(MCfontChoice,"Fuente — Cambia la tipografía del texto seleccionado");MCribbonChoice(MCfontChoice,Feather.TYPE);MCfontChoice.setOnAction(MCe->{if(!MCupdatingToolbarFromSelection&&MCfontChoice.getValue()!=null)MCeditorCommand("fontName",MCfontChoice.getValue());});
        ComboBox<Integer> MCfontSize=MCribbonControl(MCfontSizeChoice,"Tamaño de fuente — Cambia el tamaño del texto seleccionado en puntos");MCfontSize.getItems().setAll(8,9,10,11,12,14,16,18,20,24,28,36,48,72);MCribbonChoice(MCfontSize,Feather.TYPE);MCfontSize.setValue(12);MCfontSize.setPrefWidth(80);MCfontSize.setOnAction(MCe->{if(!MCupdatingToolbarFromSelection&&MCeditorReady&&MCfontSize.getValue()!=null)MCrichEditor.getEngine().executeScript("window.MCstudio.MCfontSize("+MCfontSize.getValue()+")");});
        ColorPicker MCcolor=MCribbonControl(MCeditorTextColor,"Color de texto — Aplica un color al texto seleccionado");MCcolor.setPrefWidth(36);MCcolor.setOnAction(MCe->MCeditorCommand("foreColor","#"+MCcolor.getValue().toString().substring(2,8)));
        ColorPicker MChighlight=MCribbonControl(MCeditorHighlightColor,"Resaltado — Destaca el texto seleccionado con un color de fondo");MChighlight.setPrefWidth(36);MChighlight.setOnAction(MCe->MCeditorCommand("hiliteColor","#"+MChighlight.getValue().toString().substring(2,8)));
        HBox MCtextColor=new HBox(2,new Label("",new FontIcon(Feather.TYPE)),MCcolor),MCtextHighlight=new HBox(2,new Label("",new FontIcon(Feather.EDIT_3)),MChighlight);MCtextColor.setAlignment(Pos.CENTER_LEFT);MCtextHighlight.setAlignment(Pos.CENTER_LEFT);
        HBox MChome=MCribbonRow(
            MCribbonGroup("Historial",MCribbonButton("",Feather.ROTATE_CCW,"Deshacer — Revierte la última edición del documento",()->MCeditorCommand("undo")),MCribbonButton("",Feather.ROTATE_CW,"Rehacer — Recupera la última edición deshecha",()->MCeditorCommand("redo"))),
            MCribbonGroup("Fuente",MCfontChoice,MCfontSize,MCselectionFormatButton("bold",Feather.BOLD,"Negrita — Resalta el texto seleccionado con trazo grueso",()->MCeditorCommand("bold")),MCselectionFormatButton("italic",Feather.ITALIC,"Cursiva — Inclina el texto seleccionado",()->MCeditorCommand("italic")),MCselectionFormatButton("underline",Feather.UNDERLINE,"Subrayado — Traza una línea bajo el texto seleccionado",()->MCeditorCommand("underline")),MCtextColor,MCtextHighlight),
            MCribbonGroup("Párrafo",MCselectionFormatButton("left",Feather.ALIGN_LEFT,"Alinear izquierda — Alinea el párrafo al margen izquierdo",()->MCeditorCommand("justifyLeft")),MCselectionFormatButton("center",Feather.ALIGN_CENTER,"Centrar — Centra el párrafo entre los márgenes",()->MCeditorCommand("justifyCenter")),MCselectionFormatButton("right",Feather.ALIGN_RIGHT,"Alinear derecha — Alinea el párrafo al margen derecho",()->MCeditorCommand("justifyRight")),MCselectionFormatButton("justify",Feather.ALIGN_JUSTIFY,"Justificar — Distribuye el párrafo entre ambos márgenes",()->MCeditorCommand("justifyFull")),MCselectionFormatButton("bulletList",Feather.LIST,"Viñetas — Activa o quita las viñetas de los párrafos seleccionados",()->MCeditorScript("MClist('bullet')")),MCselectionFormatButton("numberedList",Feather.HASH,"Numeración — Activa o quita la numeración de los párrafos seleccionados",()->MCeditorScript("MClist('decimal')"))));
        MenuButton MCimage=MCribbonMenu("Imagen",Feather.IMAGE,"Insertar imagen — Agrega una imagen en la posición del cursor");MCimage.getItems().add(MCribbonItem("Desde este dispositivo...",Feather.UPLOAD,"Desde este dispositivo — Selecciona una imagen PNG o JPG del equipo",()->MCinsertImage(false)));
        MenuButton MCheader=MCribbonMenu("Encabezado",Feather.ARROW_UP_CIRCLE,"Encabezado — Agrega texto o una imagen al encabezado del documento");MCheader.getItems().addAll(MCribbonItem("Agregar encabezado",Feather.TYPE,"Agregar encabezado — Inserta texto en la parte superior del documento",()->MCaddHeaderFooter(true)),MCribbonItem("Imagen en encabezado",Feather.IMAGE,"Imagen en encabezado — Agrega una imagen a la cabecera del documento",()->MCinsertImage(true)));
        MenuButton MCfooter=MCribbonMenu("Pie de página",Feather.ARROW_DOWN_CIRCLE,"Pie de página — Agrega texto, imágenes o numeración al pie del documento");MCfooter.getItems().addAll(MCribbonItem("Agregar pie",Feather.TYPE,"Agregar pie — Inserta texto al pie del documento",()->MCaddHeaderFooter(false)),MCribbonItem("Imagen en pie",Feather.IMAGE,"Imagen en pie — Agrega una imagen al pie del documento",this::MCinsertImageInFooter),MCribbonItem("Número de página",Feather.HASH,"Número de página — Inserta el campo de numeración de páginas en el pie",this::MCinsertPageNumber));
        HBox MCinsert=MCribbonRow(MCribbonGroup("Elementos",MCtableGrid(),MCimage,MCribbonButton("Salto",Feather.FILE_PLUS,"Salto de página — Comienza una página nueva en el documento",this::MCinsertPageBreak),MCribbonButton("Línea",Feather.MINUS,"Línea separadora — Inserta una línea horizontal en la posición del cursor",()->MCeditorScript("MCinsertRule()")),MCribbonButton("Variable",Feather.CODE,"Insertar variable — Inserta la variable seleccionada en la posición del cursor",this::MCinsertSelected)),MCribbonGroup("Documento",MCheader,MCfooter));
        MCpaperChoice.getItems().setAll("A4","Carta");MCpaperChoice.setValue("A4");MCpaperChoice.setPrefWidth(85);MCribbonChoice(MCpaperChoice,Feather.FILE);MCribbonControl(MCpaperChoice,"Papel — Selecciona el tamaño A4 o Carta del documento");MCpaperChoice.setOnAction(MCe->{if(!MCreadingPageSize)MCsetPaperSize();});
        MCzoomChoice.getItems().setAll("Ancho","Página","75%","100%","125%","150%","200%");MCzoomChoice.setValue("Ancho");MCzoomChoice.setPrefWidth(110);MCribbonChoice(MCzoomChoice,Feather.ZOOM_IN);MCribbonControl(MCzoomChoice,"Zoom — Ajusta el documento al ancho, a la página o a un porcentaje");MCzoomChoice.setOnAction(MCe->{MCzoomMode=MCzoomChoice.getValue();MCupdateZoom();});
        MCguidesView=MCribbonToggle("Márgenes",Feather.LAYOUT,"Guías de página — Muestra u oculta los límites de los márgenes",false,()->MCupdateEditorView());
        MCrulersView=MCribbonToggle("Regla",Feather.EYE,"Regla — Muestra u oculta las reglas del documento",true,()->MCupdateEditorView());
        MCvariablesView=MCribbonToggle("Variables",Feather.SLIDERS,"Panel de Variables — Muestra u oculta el panel lateral de variables",true,()->MCpaletteToggle.fire());
        HBox MCview=MCribbonRow(MCribbonGroup("Zoom",MCribbonButton("",Feather.ZOOM_OUT,"Alejar — Reduce el zoom del documento",()->MCadjustEditorZoom(-.1)),MCzoomPercent,MCribbonButton("",Feather.ZOOM_IN,"Acercar — Aumenta el zoom del documento",()->MCadjustEditorZoom(.1)),MCribbonButton("100%",Feather.MAXIMIZE_2,"Restablecer zoom — Muestra el documento al 100%",()->MCsetEditorZoom(1)),MCzoomChoice),MCribbonGroup("Visualización",MCguidesView,MCrulersView,MCvariablesView),MCribbonGroup("Papel",MCpaperChoice,MCpageIndicator));
        TabPane MCtools=new TabPane();MCtools.getStyleClass().add("editor-ribbon");Map<String,Node> MCsections=new LinkedHashMap<>();MCsections.put("Inicio",MChome);MCsections.put("Insertar",MCinsert);MCsections.put("Vista",MCview);
        for(var MCentry:MCsections.entrySet()){ScrollPane MCstrip=new ScrollPane(MCentry.getValue());MCstrip.setFitToHeight(true);MCstrip.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);MCstrip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);MCstrip.getStyleClass().add("ribbon-strip");Tab MCtab=new Tab(MCentry.getKey(),MCstrip);MCtab.getStyleClass().add("ribbon-tab");MCtab.setTooltip(new Tooltip(MCentry.getKey()+" — Herramientas de "+MCentry.getKey().toLowerCase()));MCtab.setClosable(false);MCtools.getTabs().add(MCtab);}
        MCtools.setMinHeight(94);MCtools.setPrefHeight(94);MCtools.setMaxHeight(94);
        MCtableTools=new HBox(4);MCtableTools.getStyleClass().add("ribbon-context");MCtableTools.setVisible(false);MCtableTools.setManaged(false);
        MenuButton MCborders=MCribbonMenu("Bordes",Feather.GRID,"Bordes — Selecciona los bordes visibles de la tabla");for(String[] MCb:new String[][]{{"Todos los bordes","all"},{"Sin bordes","none"},{"Bordes exteriores","outer"},{"Bordes interiores","inner"}})MCborders.getItems().add(MCribbonItem(MCb[0],Feather.GRID,MCb[0]+" — Aplica esta disposición de bordes a la tabla",()->MCtableAction("borders",MCb[1])));
        MenuButton MCborderStyle=MCribbonMenu("Estilo",Feather.EDIT_3,"Estilo de bordes — Selecciona líneas sencillas, dobles o discontinuas");for(String[] MCstyle:new String[][]{{"Sencillo","solid"},{"Doble","double"},{"Discontinuo","dashed"}})MCborderStyle.getItems().add(MCribbonItem(MCstyle[0],Feather.MINUS,MCstyle[0]+" — Cambia el trazo de los bordes de la tabla",()->MCtableAction("borderStyle",MCstyle[1])));
        ComboBox<String> MCthickness=MCribbonControl(new ComboBox<>(),"Grosor de bordes — Cambia el grosor de las líneas de la tabla");MCthickness.getItems().setAll("1","2","3");MCthickness.setValue("1");MCthickness.setPrefWidth(65);MCthickness.setOnAction(MCe->MCtableAction("borderWidth",MCthickness.getValue()));
        ColorPicker MCborderColor=MCribbonControl(new ColorPicker(Color.GRAY),"Color de bordes — Cambia el color de las líneas de la tabla");MCborderColor.setPrefWidth(36);MCborderColor.setOnAction(MCe->MCtableAction("borderColor","#"+MCborderColor.getValue().toString().substring(2,8)));
        MenuButton MCremove=MCribbonMenu("Eliminar",Feather.TRASH_2,"Eliminar de tabla — Quita la fila, columna o tabla seleccionada");for(String[] MCa:new String[][]{{"Eliminar fila","deleteRow"},{"Eliminar columna","deleteColumn"},{"Eliminar tabla completa","deleteTable"}})MCremove.getItems().add(MCribbonItem(MCa[0],Feather.TRASH_2,MCa[0]+" — Elimina este elemento de la tabla",()->MCtableAction(MCa[1])));
        MCtableTools.getChildren().addAll(new Label("TABLA"),MCborders,MCborderStyle,MCthickness,MCborderColor,MCremove);
        MCimageTools=new HBox(4,new Label("IMAGEN"),MCribbonButton("Reducir",Feather.MINUS,"Reducir imagen — Disminuye el tamaño de la imagen seleccionada",()->MCimageAction("smaller")),MCribbonButton("Ampliar",Feather.PLUS,"Ampliar imagen — Aumenta el tamaño de la imagen seleccionada",()->MCimageAction("larger")),MCribbonButton("",Feather.ALIGN_LEFT,"Imagen a la izquierda — Alinea la imagen al margen izquierdo",()->MCimageAction("left")),MCribbonButton("",Feather.ALIGN_CENTER,"Centrar imagen — Centra la imagen entre los márgenes",()->MCimageAction("center")),MCribbonButton("",Feather.ALIGN_RIGHT,"Imagen a la derecha — Alinea la imagen al margen derecho",()->MCimageAction("right")),MCribbonButton("Eliminar",Feather.TRASH_2,"Eliminar imagen — Quita la imagen seleccionada del documento",()->MCimageAction("delete")));MCimageTools.getStyleClass().add("ribbon-context");MCimageTools.setVisible(false);MCimageTools.setManaged(false);
        return new VBox(2,MCtools,MCtableTools,MCimageTools);
    }
    private void MCeditorScript(String MCcommand){if(MCeditorReady)MCrichEditor.getEngine().executeScript("window.MCstudio."+MCcommand);}
    private void MCupdateEditorView(){if(MCeditorReady){MCeditorScript("MCview('guides',"+MCguidesView.isSelected()+")");MCeditorScript("MCview('rulers',"+MCrulersView.isSelected()+")");}}
    private void MCadjustEditorZoom(double MCchange){MCsetEditorZoom(MCeditorZoom+MCchange);}
    private void MCsetEditorZoom(double MCscale){MCzoomChoice.setValue(Math.round(Math.max(.25,Math.min(3,MCscale))*100)+"%");}
    private void MCupdateEditorSaveStatus(){MCeditorSaveStatus.setText(MCcurrentTemplate==null?"Sin plantilla":MCdirty?"Cambios sin guardar":"Guardado");}
    private void MCsetLineSpacing(double MCspacing){if(MCselectedParagraph==null)return;MCselectedParagraph.setSpacingBetween(MCspacing);MCchanged();}
    private void MCadjustParagraphSpacing(boolean MCbefore,int MCchange){if(MCselectedParagraph==null)return;if(MCbefore)MCselectedParagraph.setSpacingBefore(Math.max(0,MCselectedParagraph.getSpacingBefore()+MCchange));else MCselectedParagraph.setSpacingAfter(Math.max(0,MCselectedParagraph.getSpacingAfter()+MCchange));MCchanged();}
    private void MCadjustIndent(int MCchange){if(MCselectedParagraph==null)return;MCselectedParagraph.setIndentationLeft(Math.max(0,MCselectedParagraph.getIndentationLeft()+MCchange));MCchanged();}
    private void MCsetHeading(String MCstyle){if(MCselectedParagraph==null)return;MCselectedParagraph.setStyle(MCstyle);MCchanged();}
    private void MCclearFormat(){if(MCselectedRun==null)return;MCselectedRun.setBold(false);MCselectedRun.setItalic(false);MCselectedRun.setUnderline(UnderlinePatterns.NONE);MCselectedRun.setStrikeThrough(false);MCselectedRun.setColor("000000");MCselectedRun.setTextHighlightColor("none");if(MCselectedRun.getCTR().isSetRPr()){var MCprops=MCselectedRun.getCTR().getRPr();while(MCprops.sizeOfShdArray()>0)MCprops.removeShd(0);}MCchanged();}
    private void MCadjustTable(boolean MCrow,boolean MCadd){if(MCselectedParagraph==null||!(MCselectedParagraph.getBody() instanceof XWPFTableCell MCcell))return;XWPFTable MCtable=MCcell.getTableRow().getTable();if(MCrow){if(MCadd)MCtable.createRow();else if(MCtable.getNumberOfRows()>1)MCtable.removeRow(MCtable.getRows().indexOf(MCcell.getTableRow()));}else{int MCcolumn=MCcell.getTableRow().getTableCells().indexOf(MCcell);for(XWPFTableRow MCr:MCtable.getRows()){if(MCadd)MCr.addNewTableCell();else if(MCr.getTableCells().size()>1)MCr.removeCell(Math.min(MCcolumn,MCr.getTableCells().size()-1));}}MCchanged();}
    private void MCchanged(){try{MCsyncEditor();}catch(Exception MCex){MCerror(MCex);return;}MCdirty=true;MCupdateEditorSaveStatus();MCstatus.setText("Cambios sin guardar");MCrenderDocument();}
    private void MCupdateZoom(){double MCviewport=MCrichEditor.getWidth();if(MCviewport<=0||!MCeditorReady)return;double MCscale=switch(MCzoomMode){case "Página"->Math.min((MCviewport-56)/MCpageWidth,(MCrichEditor.getHeight()-80)/MCpageHeight);case "Ancho"->(MCviewport-56)/MCpageWidth;default->Double.parseDouble(MCzoomMode.replace("%",""))/100.0;};MCscale=Math.max(.25,Math.min(3,MCscale));MCeditorZoom=MCscale;MCzoomPercent.setText(Math.round(MCscale*100)+"%");MCrichEditor.getEngine().executeScript("window.MCstudio.MCzoom("+MCscale+")");MCupdatePageIndicator();}
    private void MCupdatePageIndicator(){if(!MCeditorReady)return;try{int MCcurrent=((Number)MCrichEditor.getEngine().executeScript("window.MCstudio.MCcurrentPage()")).intValue(),MCcount=((Number)MCrichEditor.getEngine().executeScript("window.MCstudio.MCpageCount()")).intValue();MCpageIndicator.setText("Página "+Math.min(MCcurrent,MCcount)+" de "+MCcount);}catch(Exception MCignored){}}
    private void MCsetPaperSize(){if(MCdocument==null)return;MCpageWidth="Carta".equals(MCpaperChoice.getValue())?816:794;MCpageHeight="Carta".equals(MCpaperChoice.getValue())?1056:1123;var MCsect=MCdocument.getDocument().getBody().isSetSectPr()?MCdocument.getDocument().getBody().getSectPr():MCdocument.getDocument().getBody().addNewSectPr();var MCsize=MCsect.isSetPgSz()?MCsect.getPgSz():MCsect.addNewPgSz();MCsize.setW(java.math.BigInteger.valueOf(Math.round(MCpageWidth*15)));MCsize.setH(java.math.BigInteger.valueOf(Math.round(MCpageHeight*15)));MCchanged();}
    private void MCtoggleOrientation(){if(MCdocument==null)return;double MCw=MCpageWidth;MCpageWidth=MCpageHeight;MCpageHeight=MCw;var MCsect=MCdocument.getDocument().getBody().isSetSectPr()?MCdocument.getDocument().getBody().getSectPr():MCdocument.getDocument().getBody().addNewSectPr();var MCsize=MCsect.isSetPgSz()?MCsect.getPgSz():MCsect.addNewPgSz();MCsize.setW(java.math.BigInteger.valueOf(Math.round(MCpageWidth*15)));MCsize.setH(java.math.BigInteger.valueOf(Math.round(MCpageHeight*15)));MCchanged();}
    private void MCsetMargins(int MCtwips){if(MCdocument==null)return;var MCsect=MCdocument.getDocument().getBody().isSetSectPr()?MCdocument.getDocument().getBody().getSectPr():MCdocument.getDocument().getBody().addNewSectPr();var MCmargin=MCsect.isSetPgMar()?MCsect.getPgMar():MCsect.addNewPgMar();long MCn=MCtwips;MCmargin.setLeft(java.math.BigInteger.valueOf(MCn));MCmargin.setRight(java.math.BigInteger.valueOf(MCn));MCmargin.setTop(java.math.BigInteger.valueOf(MCn));MCmargin.setBottom(java.math.BigInteger.valueOf(MCn));MCchanged();}
    private void MCinsertTable(){if(MCdocument==null)return;MCdocument.createTable(2,2);MCchanged();}
    private void MCinsertPageBreak(){if(MCdocument==null)return;XWPFParagraph MCp=MCdocument.createParagraph();MCp.setPageBreak(true);MCchanged();}
    private void MCaddHeaderFooter(boolean MCheader){if(MCdocument==null)return;XWPFHeaderFooter MCpart=MCheader?(MCdocument.getHeaderList().isEmpty()?MCdocument.createHeader(HeaderFooterType.DEFAULT):MCdocument.getHeaderList().get(0)):(MCdocument.getFooterList().isEmpty()?MCdocument.createFooter(HeaderFooterType.DEFAULT):MCdocument.getFooterList().get(0));MCpart.createParagraph().createRun().setText(MCheader?"Encabezado":"Pie de página");MCchanged();}
    private void MCinsertPageNumber(){if(MCdocument==null)return;XWPFFooter MCfooter=MCdocument.getFooterList().isEmpty()?MCdocument.createFooter(HeaderFooterType.DEFAULT):MCdocument.getFooterList().get(0);XWPFRun MCrun=MCfooter.createParagraph().createRun();MCrun.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);XWPFRun MCinstruction=MCfooter.getParagraphs().get(MCfooter.getParagraphs().size()-1).createRun();MCinstruction.getCTR().addNewInstrText().setStringValue(" PAGE ");XWPFRun MCend=MCfooter.getParagraphs().get(MCfooter.getParagraphs().size()-1).createRun();MCend.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);MCchanged();}
    private void MCinsertImage(boolean MCheader){if(MCdocument==null)return;FileChooser MCchooser=new FileChooser();MCchooser.setTitle("Insertar imagen");MCchooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG/JPG","*.png","*.jpg","*.jpeg"));File MCfile=MCchooser.showOpenDialog(MCstage);if(MCfile==null)return;try(InputStream MCin=Files.newInputStream(MCfile.toPath())){Image MCsource=new Image(MCfile.toURI().toString());if(MCsource.isError()||MCsource.getWidth()<=0)throw new IOException("No se pudo leer la imagen.");double MCwidth=Math.min(300,MCsource.getWidth()*72/96),MCheight=MCwidth*MCsource.getHeight()/MCsource.getWidth();if(MCheight>600){MCwidth*=600/MCheight;MCheight=600;}if(!MCheader&&MCeditorReady){String MCmime=MCfile.getName().toLowerCase().endsWith(".png")?"image/png":"image/jpeg";String MCuri="data:"+MCmime+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(MCfile.toPath()));if(Boolean.TRUE.equals(MCrichEditor.getEngine().executeScript("window.MCstudio.MCinsertImage("+MCjsonString(MCuri)+","+Math.round(MCwidth*96/72)+","+Math.round(MCheight*96/72)+")")))return;throw new IllegalArgumentException("Coloque el cursor en el documento antes de insertar la imagen.");}XWPFHeader MCh=MCdocument.getHeaderList().isEmpty()?MCdocument.createHeader(HeaderFooterType.DEFAULT):MCdocument.getHeaderList().get(0);XWPFParagraph MCp=MCh.createParagraph();int MCtype=MCfile.getName().toLowerCase().endsWith(".png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;MCp.createRun().addPicture(MCin,MCtype,MCfile.getName(),Units.toEMU(MCwidth),Units.toEMU(MCheight));MCchanged();}catch(Exception MCe){MCerror(MCe);}}
    private void MCinsertImageInFooter(){if(MCdocument==null)return;FileChooser MCchooser=new FileChooser();MCchooser.setTitle("Imagen en pie de página");MCchooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG/JPG","*.png","*.jpg","*.jpeg"));File MCfile=MCchooser.showOpenDialog(MCstage);if(MCfile==null)return;try(InputStream MCin=Files.newInputStream(MCfile.toPath())){Image MCsource=new Image(MCfile.toURI().toString());if(MCsource.isError()||MCsource.getWidth()<=0)throw new IOException("No se pudo leer la imagen.");double MCwidth=Math.min(300,MCsource.getWidth()*72/96),MCheight=MCwidth*MCsource.getHeight()/MCsource.getWidth();XWPFFooter MCfooter=MCdocument.getFooterList().isEmpty()?MCdocument.createFooter(HeaderFooterType.DEFAULT):MCdocument.getFooterList().get(0);int MCtype=MCfile.getName().toLowerCase().endsWith(".png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;MCfooter.createParagraph().createRun().addPicture(MCin,MCtype,MCfile.getName(),Units.toEMU(MCwidth),Units.toEMU(MCheight));MCchanged();}catch(Exception MCex){MCerror(MCex);}}
    private void MCresizePicture(XWPFRun MCrun,XWPFPicture MCpicture,double MCfactor){var MCinline=MCrun.getCTR().getDrawingArray(0).getInlineArray(0);if(MCinline.getExtent()==null)return;long MCwidth=Math.max(Units.toEMU(20),Math.round(MCinline.getExtent().getCx()*MCfactor));long MCheight=Math.max(Units.toEMU(20),Math.round(MCinline.getExtent().getCy()*MCfactor));MCinline.getExtent().setCx(MCwidth);MCinline.getExtent().setCy(MCheight);var MCshape=MCpicture.getCTPicture().getSpPr();if(MCshape!=null&&MCshape.isSetXfrm()&&MCshape.getXfrm().isSetExt()){MCshape.getXfrm().getExt().setCx(MCwidth);MCshape.getXfrm().getExt().setCy(MCheight);}MCchanged();}
    private boolean MCinsertToken(TextField MCfield,String MCreference){
        if(MCcurrentTemplate==null){MCsaved("Primero cree o importe una plantilla");return false;}
        try{
            String MCkey=MCreference;
            if(MCreference.startsWith("definition:")){
                long MCid=Long.parseLong(MCreference.substring("definition:".length()));
                MCDefinition MCd=MCstore.MCdefinitions().stream().filter(MCx->MCx.MCid()==MCid).findFirst().orElseThrow(()->new IllegalArgumentException("Variable inexistente."));MCkey=MCd.MCkey();
                MCVariable MCbound=MCstore.MCvariables(MCcurrentTemplate.MCid()).stream().filter(MCv->MCv.MCkey().equals(MCd.MCkey())).findFirst().orElse(null);
                if(MCbound==null)MCstore.MCbindDefinition(MCcurrentTemplate.MCid(),MCid,false,MCstore.MCvariables(MCcurrentTemplate.MCid()).size()+1);
                else if(MCstore.MCdefinitionId(MCbound.MCid())!=MCid)throw new IllegalArgumentException("Esta plantilla ya utiliza '"+MCkey+"' con otra definición. Use otro identificador.");
            }
            MCfield.insertText(MCfield.getCaretPosition(),"{{"+MCkey+"}}");MCrenderDocument();MCdirty=true;MCsaved("Variable insertada · Guarde la plantilla");return true;
        }catch(Exception MCe){MCerror(MCe);return false;}
    }

    private void MCrenderDocument(){MCselectedText=null;if(MCdocument==null)return;
        MCpageWidth=794;MCpageHeight=1123;var MCsect=MCdocument.getDocument().getBody().getSectPr();if(MCsect!=null&&MCsect.isSetPgSz()){MCpageWidth=((Number)MCsect.getPgSz().getW()).doubleValue()/15.0;MCpageHeight=((Number)MCsect.getPgSz().getH()).doubleValue()/15.0;}MCreadingPageSize=true;MCpaperChoice.setValue(Math.abs(MCpageWidth-816)<15?"Carta":"A4");MCreadingPageSize=false;
        double MCleft=MCsect!=null&&MCsect.isSetPgMar()&&MCsect.getPgMar().getLeft()!=null?((Number)MCsect.getPgMar().getLeft()).doubleValue()/15.0:72;
        double MCright=MCsect!=null&&MCsect.isSetPgMar()&&MCsect.getPgMar().getRight()!=null?((Number)MCsect.getPgMar().getRight()).doubleValue()/15.0:MCleft;
        double MCtop=MCsect!=null&&MCsect.isSetPgMar()&&MCsect.getPgMar().getTop()!=null?((Number)MCsect.getPgMar().getTop()).doubleValue()/15.0:72;
        double MCbottom=MCsect!=null&&MCsect.isSetPgMar()&&MCsect.getPgMar().getBottom()!=null?((Number)MCsect.getPgMar().getBottom()).doubleValue()/15.0:MCtop;
        MCrichModel=new MCContinuousDocxEditor(MCdocument,MCeditorPersonaNames);
        try(InputStream MCin=Objects.requireNonNull(getClass().getResourceAsStream("/editor/document-editor.html"))){String MCshell=new String(MCin.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);String MChtml=MCshell.replace("__DOCUMENT_CONTENT__",MCrichModel.MChtml());MCeditorLeft=MCleft;MCeditorRight=MCright;MCeditorTop=MCtop;MCeditorBottom=MCbottom;MCeditorReady=false;MCrichEditor.getEngine().loadContent(MChtml);}catch(Exception MCex){MCerror(MCex);}
    }
    private VBox MCnewPage(double MCmargin,double MCtop){VBox MCpage=new VBox(5);MCpage.getStyleClass().add("paper");MCpage.setPrefSize(MCpageWidth,MCpageHeight);MCpage.setMinSize(MCpageWidth,MCpageHeight);MCpage.setMaxWidth(MCpageWidth);MCpage.setPadding(new Insets(MCtop,MCmargin,Math.min(MCtop,72),MCmargin));VBox MCbody=new VBox(5);for(XWPFHeader MCheader:MCdocument.getHeaderList())for(XWPFParagraph MCp:MCheader.getParagraphs())MCpage.getChildren().add(MCrenderParagraph(MCp,true));MCpage.getChildren().add(MCbody);Region MCspacer=new Region();VBox.setVgrow(MCspacer,Priority.ALWAYS);MCpage.getChildren().add(MCspacer);for(XWPFFooter MCfooter:MCdocument.getFooterList())for(XWPFParagraph MCp:MCfooter.getParagraphs())MCpage.getChildren().add(MCrenderParagraph(MCp,true));MCpaper.getChildren().add(MCpage);return MCbody;}
    private double MCestimateHeight(Node MCnode){if(MCnode instanceof FlowPane MCline){double MCwidth=Math.max(100,MCpageWidth-150);double MClength=MCline.getChildren().stream().mapToDouble(MCn->MCn instanceof TextField MCf?MCf.getPrefWidth():MCn instanceof ImageView MCi?MCi.getFitWidth():70).sum();return Math.max(30,Math.ceil(MClength/MCwidth)*28);}return 40;}
    private Node MCrenderParagraph(XWPFParagraph MCp,boolean MCeditable){FlowPane MCline=new FlowPane(2,3);MCline.getStyleClass().add("doc-paragraph");MCline.setAlignment(switch(MCp.getAlignment()){case CENTER->Pos.CENTER;case RIGHT->Pos.CENTER_RIGHT;default->Pos.CENTER_LEFT;});
        if(MCp.getRuns().isEmpty()&&MCeditable)MCp.createRun().setText("");
        for(XWPFRun MCrun:MCp.getRuns()){
            String MCraw=MCrun.getText(0);if(MCraw!=null){Matcher MCm=MCTOKEN.matcher(MCraw);int MCat=0;while(MCm.find()){if(MCm.start()>MCat)MCaddTextSegment(MCline,MCrun,MCp,MCraw.substring(MCat,MCm.start()),MCeditable);MCaddToken(MCline,MCrun,MCm.group(1),MCeditable,MCm.start());MCat=MCm.end();}if(MCat<MCraw.length()||MCraw.isEmpty())MCaddTextSegment(MCline,MCrun,MCp,MCraw.substring(MCat),MCeditable);}
            for(XWPFPicture MCpicture:MCrun.getEmbeddedPictures()){try{ImageView MCimage=new ImageView(new Image(new ByteArrayInputStream(MCpicture.getPictureData().getData())));MCimage.setPreserveRatio(true);MCimage.setFitWidth(Math.min(MCpageWidth-160,Math.max(80,MCpicture.getWidth()*96/72)));if(MCeditable&&MCrun.getEmbeddedPictures().size()==1&&MCrun.getCTR().sizeOfDrawingArray()==1&&MCrun.getCTR().getDrawingArray(0).sizeOfInlineArray()==1){MenuItem MCsmaller=new MenuItem("Reducir a 75 %"),MClarger=new MenuItem("Ampliar a 125 %"),MCremove=new MenuItem("Eliminar imagen");MCsmaller.setOnAction(MCe->MCresizePicture(MCrun,MCpicture,.75));MClarger.setOnAction(MCe->MCresizePicture(MCrun,MCpicture,1.25));MCremove.setOnAction(MCe->{MCrun.getCTR().removeDrawing(0);MCchanged();});MCimage.setOnContextMenuRequested(MCe->new ContextMenu(MCsmaller,MClarger,MCremove).show(MCimage,MCe.getScreenX(),MCe.getScreenY()));}MCline.getChildren().add(MCimage);}catch(Exception MCignored){MCline.getChildren().add(new Label("[Imagen no previsualizable]"));}}
        }
        if(MCline.getChildren().isEmpty())MCline.getChildren().add(new Label(" "));return MCline;}
    private void MCaddTextSegment(FlowPane MCline,XWPFRun MCrun,XWPFParagraph MCp,String MCtext,boolean MCeditable){
        if(!MCeditable){Text MCt=new Text(MCtext);MCt.setFont(Font.font(MCrun.getFontFamily()==null?"Serif":MCrun.getFontFamily(),MCrun.isBold()?FontWeight.BOLD:FontWeight.NORMAL,MCrun.getFontSize()>0?MCrun.getFontSize():13));MCline.getChildren().add(MCt);return;}
        TextField MCfield=new TextField(MCtext);MCfield.getStyleClass().add("run-field");MCfield.setPrefWidth(Math.max(28,Math.min(700,MCtext.length()*8+25)));MCfield.setStyle("-fx-font-family:'"+(MCrun.getFontFamily()==null?"Serif":MCrun.getFontFamily())+"';-fx-font-size:"+(MCrun.getFontSize()>0?MCrun.getFontSize():13)+"px;"+(MCrun.isBold()?"-fx-font-weight:bold;":"")+(MCrun.isItalic()?"-fx-font-style:italic;":"")+(MCrun.getColor()!=null?"-fx-text-fill:#"+MCrun.getColor()+";":""));
        MCfield.focusedProperty().addListener((MCo,MCa,MCb)->{if(MCb){MCselectedRun=MCrun;MCselectedParagraph=MCp;MCselectedText=MCfield;}});
        MCfield.textProperty().addListener((MCo,MCa,MCb)->{MCfield.setPrefWidth(Math.max(28,Math.min(700,MCb.length()*8+25)));StringBuilder MCall=new StringBuilder();for(Node MCn:MCline.getChildren()){if(MCn.getUserData()==MCrun){if(MCn instanceof TextField MCf)MCall.append(MCf.getText());else if(MCn instanceof Label MCl)MCall.append("{{").append(MCn.getProperties().get("key")).append("}}");}}MCrun.setText(MCall.toString(),0);MCdirty=true;MCupdateEditorSaveStatus();MCstatus.setText("Cambios sin guardar");});
        MCfield.setUserData(MCrun);MCfield.setOnDragOver(MCe->{if(MCe.getDragboard().hasString())MCe.acceptTransferModes(TransferMode.COPY,TransferMode.MOVE);MCe.consume();});MCfield.setOnDragDropped(MCe->{String MCkey=MCe.getDragboard().getString();if(MCkey!=null){if(MCfield.getSkin() instanceof TextFieldSkin MCskin)MCfield.positionCaret(MCskin.getIndex(MCe.getX(),MCe.getY()).getInsertionIndex());MCdragTargetRun=MCrun;MCdragTargetOffset=MCrunOffset(MCline,MCfield)+MCfield.getCaretPosition();MCe.setDropCompleted(MCinsertToken(MCfield,MCkey));}MCe.consume();});
        MCline.getChildren().add(MCfield);
    }
    private int MCrunOffset(FlowPane MCline,Node MCtarget){int MCoffset=0;for(Node MCn:MCline.getChildren()){if(MCn==MCtarget)break;if(MCn.getUserData()==MCtarget.getUserData()){if(MCn instanceof TextField MCf)MCoffset+=MCf.getText().length();else if(MCn instanceof Label MCl)MCoffset+=String.valueOf(MCl.getProperties().get("key")).length()+4;}}return MCoffset;}
    private void MCaddToken(FlowPane MCline,XWPFRun MCrun,String MCkey,boolean MCeditable,int MCoffset){Label MCchip=new Label("⟦"+MCkey+"⟧");MCchip.getStyleClass().add("token");MCchip.setUserData(MCrun);MCchip.getProperties().put("key",MCkey);MCchip.setTooltip(new Tooltip("{{"+MCkey+"}} · Arrastre, copie o elimine esta aparición"));if(MCeditable){MCchip.setOnDragDetected(MCe->{MCdragSourceRun=MCrun;MCdragSourceToken="{{"+MCkey+"}}";MCdragSourceOffset=MCrunOffset(MCline,MCchip);MCdragTargetRun=null;Dragboard MCdb=MCchip.startDragAndDrop(TransferMode.MOVE);ClipboardContent MCcc=new ClipboardContent();MCcc.putString(MCkey);MCdb.setContent(MCcc);MCe.consume();});MCchip.setOnDragDone(MCe->{if(MCe.getTransferMode()==TransferMode.MOVE&&MCdragSourceRun!=null){String MCtxt=MCdragSourceRun.getText(0);int MCpos=MCdragSourceOffset+(MCdragSourceRun==MCdragTargetRun&&MCdragTargetOffset<=MCdragSourceOffset?MCdragSourceToken.length():0);if(MCtxt!=null&&MCpos>=0&&MCpos+MCdragSourceToken.length()<=MCtxt.length()&&MCtxt.startsWith(MCdragSourceToken,MCpos))MCdragSourceRun.setText(MCtxt.substring(0,MCpos)+MCtxt.substring(MCpos+MCdragSourceToken.length()),0);MCdirty=true;MCrenderDocument();}MCdragSourceRun=null;MCdragTargetRun=null;MCe.consume();});MenuItem MCcopy=new MenuItem("Copiar variable"),MCcut=new MenuItem("Cortar variable"),MCdelete=new MenuItem("Eliminar aparición");MCcopy.setOnAction(MCe->MCcopyToken(MCkey));MCcut.setOnAction(MCe->{MCcopyToken(MCkey);MCremoveToken(MCline,MCchip,MCrun,MCkey);});MCdelete.setOnAction(MCe->MCremoveToken(MCline,MCchip,MCrun,MCkey));MCchip.setContextMenu(new ContextMenu(MCcopy,MCcut,MCdelete));MCchip.setOnMouseClicked(MCe->{if(MCe.getClickCount()==2)MCremoveToken(MCline,MCchip,MCrun,MCkey);});}MCline.getChildren().add(MCchip);}
    private void MCcopyToken(String MCkey){ClipboardContent MCcontent=new ClipboardContent();MCcontent.putString("{{"+MCkey+"}}");Clipboard.getSystemClipboard().setContent(MCcontent);}
    private void MCremoveToken(FlowPane MCline,Node MCchip,XWPFRun MCrun,String MCkey){String MCtxt=MCrun.getText(0);int MCat=MCrunOffset(MCline,MCchip);if(MCtxt!=null&&MCtxt.startsWith("{{"+MCkey+"}}",MCat)){MCrun.setText(MCtxt.substring(0,MCat)+MCtxt.substring(MCat+MCkey.length()+4),0);MCchanged();}}
    private void MCstyleRun(String MCaction){if(MCselectedRun==null)return;switch(MCaction){case "bold"->MCselectedRun.setBold(!MCselectedRun.isBold());case "italic"->MCselectedRun.setItalic(!MCselectedRun.isItalic());case "underline"->MCselectedRun.setUnderline(MCselectedRun.getUnderline()==UnderlinePatterns.NONE?UnderlinePatterns.SINGLE:UnderlinePatterns.NONE);case "strike"->MCselectedRun.setStrikeThrough(!MCselectedRun.isStrikeThrough());case "smaller"->MCselectedRun.setFontSize(Math.max(8,MCselectedRun.getFontSize()-1));case "larger"->MCselectedRun.setFontSize((MCselectedRun.getFontSize()>0?MCselectedRun.getFontSize():12)+1);}MCchanged();}
    private void MCalign(ParagraphAlignment MCa){if(MCselectedParagraph==null)return;MCselectedParagraph.setAlignment(MCa);MCdirty=true;MCrenderDocument();}
    private void MCaddParagraph(){if(MCdocument==null)return;MCdocument.createParagraph().createRun().setText("");MCdirty=true;MCrenderDocument();}

    private BorderPane MCcatalogPage;
    private void MCbuildCatalog(){MCcatalogPage=new BorderPane();MCcatalogPage.getStyleClass().add("page");VBox MCleft=new VBox(8,MCkindSearch,MCkindList,MCbutton("+ Nuevo tipo",this::MCnewKind),MCbutton("Duplicar",this::MCduplicateKind),MCbutton("Eliminar",this::MCdeleteKind));MCleft.getStyleClass().add("side-panel");MCleft.setPrefWidth(250);VBox.setVgrow(MCkindList,Priority.ALWAYS);MCkindSearch.setPromptText("Buscar título o categoría");MCkindSearch.textProperty().addListener((MCo,MCa,MCb)->MCrefreshKinds());MCkindList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->MCloadKind(MCb));MCcatalogPage.setLeft(MCleft);
        MCkindDescription.setPrefRowCount(3);MCfieldList.setPrefHeight(230);MCkindActive.setSelected(true);
        HBox MCorder=new HBox(8,MCbutton("↑",()->MCmoveField(-1)),MCbutton("↓",()->MCmoveField(1)),MCbutton("Quitar campo",()->{MCVariable MCv=MCfieldList.getSelectionModel().getSelectedItem();if(MCv!=null)MCfieldList.getItems().remove(MCv);}));
        HBox MCaddField=new HBox(8,MCaddFieldChoice,MCbutton("Añadir campo",()->{MCVariable MCv=MCaddFieldChoice.getValue();if(MCv!=null&&MCfieldList.getItems().stream().noneMatch(MCx->MCx.MCkey().equals(MCv.MCkey())))MCfieldList.getItems().add(MCv);}));
        MCfieldList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->{MCloadingFieldRequired=true;MCfieldRequired.setDisable(MCb==null);MCfieldDefault.setDisable(MCb==null);MCfieldRequired.setSelected(MCb!=null&&MCkindRequiredChoices.getOrDefault(MCb.MCkey(),MCb.MCrequired()));MCfieldDefault.setText(MCb==null?"":MCkindDefaultChoices.getOrDefault(MCb.MCkey(),""));MCloadingFieldRequired=false;});
        MCfieldRequired.setDisable(true);MCfieldRequired.setOnAction(MCe->{MCVariable MCv=MCfieldList.getSelectionModel().getSelectedItem();if(MCv!=null&&!MCloadingFieldRequired)MCkindRequiredChoices.put(MCv.MCkey(),MCfieldRequired.isSelected());});
        MCfieldDefault.setDisable(true);MCfieldDefault.setPromptText("Valor predeterminado solo para este documento");MCfieldDefault.textProperty().addListener((MCo,MCa,MCb)->{MCVariable MCv=MCfieldList.getSelectionModel().getSelectedItem();if(MCv!=null&&!MCloadingFieldRequired)MCkindDefaultChoices.put(MCv.MCkey(),MCb);});
        MCbindingGroup.setPromptText("Ej.: persona_1");MCbindingKind.getItems().setAll("Persona","Empresa");MCbindingKind.setValue("Persona");MCbindingKind.setOnAction(MCe->MCrefreshBindingFields());MCrefreshBindingFields();MCbindingList.setPrefHeight(150);
        HBox MCbindingRow=new HBox(8,MCbindingGroup,MCbindingKind,MCbindingField,MCbindingVariable);MCbindingGroup.setPrefWidth(130);MCbindingField.setPrefWidth(165);MCbindingVariable.setPrefWidth(190);
        VBox MCform=new VBox(12,new Label("CONFIGURAR GENERACIÓN"),MClabelled("Título",MCkindTitle),MClabelled("Categoría opcional",MCkindCategory),MClabelled("Descripción opcional",MCkindDescription),MClabelled("Plantilla",MCkindTemplate),MCkindActive,new Label("CAMPOS DEL FORMULARIO · Ordene con ↑ ↓"),MCfieldList,MCfieldRequired,MClabelled("Valor predeterminado del campo seleccionado",MCfieldDefault),MCorder,MCaddField,MCbutton("Guardar documento",this::MCsaveKind),new Separator(),new Label("VINCULACIÓN LOCAL DE PERSONAS Y EMPRESAS"),new Label("Cree un grupo por cada registro independiente. La generación guardará datos asociados con documento válido."),MCbindingRow,MCbutton("Añadir asociación",this::MCsaveContactBinding),MCbindingList,MCbutton("Eliminar asociación",this::MCdeleteContactBinding));MCform.getStyleClass().add("catalog-form");ScrollPane MCscroll=new ScrollPane(MCform);MCscroll.setFitToWidth(true);MCcatalogPage.setCenter(MCscroll);
        MCkindTemplate.setOnAction(MCe->MCloadFieldsForTemplate());
    }
    private void MCshowCatalog(){MCshowEditor();MCrefreshKinds();MCeditorTabs.getSelectionModel().select(1);}
    private void MCrefreshKinds(){
        if(MCrefreshingGenerator)return;MCrefreshingGenerator=true;
        try{
            String MCq=MCkindSearch.getText().toLowerCase(Locale.ROOT);MCkindList.getItems().setAll(MCstore.MCkinds().stream().filter(MCk->MCk.MCtitle().toLowerCase(Locale.ROOT).contains(MCq)||(MCk.MCcategory()!=null&&MCk.MCcategory().toLowerCase(Locale.ROOT).contains(MCq))).toList());
            MCTemplate MCselected=MCgenerateTemplate.getValue();MCTemplateGroup MCgroup=MCgenerateGroup.getValue();String MCsearch=MCgenerateSearch.getText().trim();List<MCTemplate> MCchoices=new ArrayList<>();
            if(MCgroup!=null)for(MCTemplate MCtemplate:MCstore.MCtemplates())if(MCstore.MCinGroup(MCtemplate.MCid(),MCgroup.MCid())&&MClibraryMatches(MCtemplate.MCname(),MCsearch))MCchoices.add(MCtemplate);
            MCgenerateTemplate.getItems().setAll(MCchoices);
            MCTemplate MCrestored=MCselected==null?null:MCchoices.stream().filter(MCt->MCt.MCid()==MCselected.MCid()).findFirst().orElse(null);MCgenerateTemplate.getSelectionModel().select(MCrestored);
            if(MCrestored==null){MChistoricalTemplate=null;MCbuildDynamicForm();}
        }catch(Exception MCe){MCerror(MCe);}finally{MCrefreshingGenerator=false;}
    }



    private MCDefinition MCdefinitionFor(MCVariable MCvariable,List<MCDefinition> MCdefinitions){long MCid=MCsafeDefinitionId(MCvariable);return MCdefinitions.stream().filter(MCd->MCd.MCid()==MCid).findFirst().orElse(null);}
    private List<MCVariable> MCvariablesForTemplate(MCTemplate MCtemplate)throws Exception{
        List<MCVariable> MCbound=MCstore.MCvariables(MCtemplate.MCid());Map<String,MCVariable> MCresult=new LinkedHashMap<>();List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();
        try(XWPFDocument MCdoc=MCDocumentEngine.MCopen(MCtemplate.MCedited())){for(String MCmarker:MCDocumentEngine.MCmarkers(MCdoc)){
            MCVariable MCexisting=MCbound.stream().filter(MCv->MCv.MCkey().equals(MCmarker)).findFirst().orElse(null);MCDefinition MCdefinition=MCexisting==null?MCdefinitions.stream().filter(MCd->MCd.MCkey().equals(MCDocumentEngine.MCbaseKey(MCmarker))).findFirst().orElse(null):MCdefinitionFor(MCexisting,MCdefinitions);
            if(MCdefinition!=null&&!MCdefinition.MCbindToClient()){String MCkey=MCdefinition.MCkey();MCresult.putIfAbsent(MCkey,new MCVariable(-MCdefinition.MCid(),MCtemplate.MCid(),MCkey,MCdefinition.MClabel(),MCdefinition.MCtype(),MCdefinition.MCdefaultValue(),false,MCresult.size()+1));}
            else if(MCexisting!=null)MCresult.putIfAbsent(MCmarker,MCexisting);
            else if(MCdefinition!=null)MCresult.putIfAbsent(MCmarker,new MCVariable(-MCdefinition.MCid(),MCtemplate.MCid(),MCmarker,MCdefinition.MClabel(),MCdefinition.MCtype(),MCdefinition.MCdefaultValue(),false,MCresult.size()+1));
            else MCresult.putIfAbsent(MCmarker,new MCVariable(0,MCtemplate.MCid(),MCmarker,MCDocumentEngine.MCbaseKey(MCmarker),"Texto corto","",false,MCresult.size()+1));
        }}return new ArrayList<>(MCresult.values());
    }
    private Map<String,String> MCdocumentValues(byte[] MCsource,Map<String,String> MCvalues)throws Exception{
        Map<String,String> MCresult=new LinkedHashMap<>(MCvalues);List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();MCTemplate MCtemplate=MCgenerateTemplate.getValue();List<MCVariable> MCbound=MCtemplate==null?List.of():MCstore.MCvariables(MCtemplate.MCid());
        try(XWPFDocument MCdoc=MCDocumentEngine.MCopen(MCsource)){for(String MCmarker:MCDocumentEngine.MCmarkers(MCdoc))if(!MCDocumentEngine.MCpersonaKey(MCmarker).isEmpty()){
            MCVariable MCvariable=MCbound.stream().filter(MCv->MCv.MCkey().equals(MCmarker)).findFirst().orElse(null);MCDefinition MCdefinition=MCvariable==null?MCdefinitions.stream().filter(MCd->MCd.MCkey().equals(MCDocumentEngine.MCbaseKey(MCmarker))).findFirst().orElse(null):MCdefinitionFor(MCvariable,MCdefinitions);
            if(MCdefinition!=null&&!MCdefinition.MCbindToClient())MCresult.put(MCmarker,MCvalues.getOrDefault(MCdefinition.MCkey(),MCdefinition.MCinitialValue()));
        }}return MCresult;
    }
    private void MCnewKind(){MCcurrentKind=null;MCkindRequiredChoices.clear();MCkindDefaultChoices.clear();MCkindList.getSelectionModel().clearSelection();MCkindTitle.clear();MCkindCategory.clear();MCkindDescription.clear();MCkindActive.setSelected(true);MCkindTemplate.setValue(MCcurrentTemplate);MCfieldList.getItems().clear();MCbindingList.getItems().clear();}
    private void MCloadKind(MCKind MCk){if(MCk==null)return;MCcurrentKind=MCk;MCkindTitle.setText(MCk.MCtitle());MCkindCategory.setText(MCk.MCcategory());MCkindDescription.setText(MCk.MCdescription());MCkindActive.setSelected(MCk.MCactive());MCkindTemplate.getSelectionModel().select(MCkindTemplate.getItems().stream().filter(MCt->MCt.MCid()==MCk.MCtemplateId()).findFirst().orElse(null));MCloadFieldsForTemplate();MCrefreshContactBindings();try{MCkindRequiredChoices.putAll(MCstore.MCkindRequirements(MCk.MCid()));MCkindDefaultChoices.clear();MCkindDefaultChoices.putAll(MCstore.MCkindDefaults(MCk.MCid()));}catch(Exception MCe){MCerror(MCe);}Map<String,MCVariable> MCvars=new HashMap<>();for(MCVariable MCv:MCaddFieldChoice.getItems())MCvars.put(MCv.MCkey(),MCv);List<MCVariable> MCordered=new ArrayList<>();for(String MCkey:MCk.MCfields().split(",")){MCVariable MCv=MCvars.get(MCkey);if(MCv!=null)MCordered.add(MCv);}MCfieldList.getItems().setAll(MCordered);}
    private void MCloadFieldsForTemplate(){MCTemplate MCt=MCkindTemplate.getValue();try{MCkindRequiredChoices.clear();MCkindDefaultChoices.clear();List<MCVariable> MCvars;if(MCt==null)MCvars=List.of();else{Set<String> MCused;try(XWPFDocument MCdoc=MCDocumentEngine.MCopen(MCt.MCedited())){MCused=MCDocumentEngine.MCmarkers(MCdoc);}MCvars=MCstore.MCvariables(MCt.MCid()).stream().filter(MCv->MCused.contains(MCv.MCkey())).toList();}MCfieldList.getItems().setAll(MCvars);MCaddFieldChoice.getItems().setAll(MCvars);List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();MCbindingVariable.getItems().setAll(MCvars.stream().filter(MCv->MCContactReuse.MCclientKey(MCv.MCkey(),MCdefinitions)).toList());MCrefreshContactBindings();}catch(Exception MCe){MCerror(MCe);}}
    private void MCrefreshBindingFields(){MCbindingField.getItems().setAll("Empresa".equals(MCbindingKind.getValue())?List.of("Razón social","RUC","Domicilio fiscal","Representantes"):List.of("Nombres","Apellidos","Tipo de documento","Número de documento","Domicilio"));MCbindingField.getSelectionModel().selectFirst();}
    private void MCrefreshContactBindings(){MCTemplate MCt=MCkindTemplate.getValue();try{MCbindingList.getItems().setAll(MCt==null?List.of():MCdata.MCcontactBindings(MCt.MCid()));}catch(Exception MCe){MCerror(MCe);}}
    private void MCsaveContactBinding(){MCTemplate MCt=MCkindTemplate.getValue();MCVariable MCv=MCbindingVariable.getValue();if(MCt==null||MCv==null||MCbindingGroup.getText().isBlank())throw new IllegalArgumentException("Seleccione plantilla, grupo, dato y variable.");try{String MCfield=switch(MCbindingField.getValue()){case "Nombres","Razón social"->"given";case "Apellidos"->"family";case "Tipo de documento"->"document_type";case "Número de documento","RUC"->"number";case "Domicilio","Domicilio fiscal"->"address";case "Representantes"->"representatives";default->throw new IllegalArgumentException("Dato no admitido.");};MCdata.MCsaveContactBinding(new MCContactBinding(0,MCt.MCid(),MCbindingGroup.getText().trim(),MCbindingKind.getValue(),MCfield,MCv.MCkey()));MCrefreshContactBindings();MCsaved("Asociación local guardada");}catch(Exception MCe){MCerror(MCe);}}
    private void MCdeleteContactBinding(){MCContactBinding MCb=MCbindingList.getSelectionModel().getSelectedItem();if(MCb==null)return;try{MCdata.MCdeleteContactBinding(MCb.MCid());MCrefreshContactBindings();MCsaved("Asociación eliminada");}catch(Exception MCe){MCerror(MCe);}}
    private void MCmoveField(int MCdelta){int MCi=MCfieldList.getSelectionModel().getSelectedIndex();int MCj=MCi+MCdelta;if(MCi<0||MCj<0||MCj>=MCfieldList.getItems().size())return;Collections.swap(MCfieldList.getItems(),MCi,MCj);MCfieldList.getSelectionModel().select(MCj);}
    private void MCsaveKind(){MCTemplate MCt=MCkindTemplate.getValue();if(MCt==null||MCkindTitle.getText().isBlank())throw new IllegalArgumentException("Título y plantilla son obligatorios.");try{Set<String> MCmarkers;try(XWPFDocument MCdoc=MCDocumentEngine.MCopen(MCt.MCedited())){MCmarkers=MCDocumentEngine.MCmarkers(MCdoc);}Map<String,MCVariable> MCbound=new HashMap<>();for(MCVariable MCv:MCstore.MCvariables(MCt.MCid()))MCbound.put(MCv.MCkey(),MCv);Set<String> MCshown=new HashSet<>();for(MCVariable MCv:MCfieldList.getItems())MCshown.add(MCv.MCkey());for(String MCmarker:MCmarkers){MCVariable MCv=MCbound.get(MCmarker);if(MCv==null)throw new IllegalArgumentException("Variable sin vincular en la plantilla: "+MCmarker);if(!MCshown.contains(MCmarker)&&(MCkindDefaultChoices.getOrDefault(MCmarker,MCv.MCdefaultValue())==null||MCkindDefaultChoices.getOrDefault(MCmarker,MCv.MCdefaultValue()).isBlank()))throw new IllegalArgumentException("La variable '"+MCmarker+"' no está en el formulario ni tiene valor predeterminado.");}String MCfields=String.join(",",MCfieldList.getItems().stream().map(MCVariable::MCkey).toList());MCKind MCk=MCstore.MCsaveKind(new MCKind(MCcurrentKind==null?0:MCcurrentKind.MCid(),MCkindTitle.getText().trim(),MCkindCategory.getText().trim(),MCkindDescription.getText().trim(),MCt.MCid(),MCfields,MCkindActive.isSelected()));Map<String,Boolean> MCrequirements=new HashMap<>();for(MCVariable MCv:MCfieldList.getItems())MCrequirements.put(MCv.MCkey(),MCkindRequiredChoices.getOrDefault(MCv.MCkey(),MCv.MCrequired()));MCstore.MCsaveKindFieldRules(MCk.MCid(),MCrequirements,MCkindDefaultChoices);MCcurrentKind=MCk;MCrefreshKinds();MCkindList.getSelectionModel().select(MCk);MCsaved("Tipo documental guardado");}catch(Exception MCe){MCerror(MCe);}}
    private void MCduplicateKind(){MCKind MCk=MCkindList.getSelectionModel().getSelectedItem();if(MCk==null)return;try{MCKind MCcopy=MCstore.MCsaveKind(new MCKind(0,MCk.MCtitle()+" (copia)",MCk.MCcategory(),MCk.MCdescription(),MCk.MCtemplateId(),MCk.MCfields(),MCk.MCactive()));MCstore.MCsaveKindFieldRules(MCcopy.MCid(),MCstore.MCkindRequirements(MCk.MCid()),MCstore.MCkindDefaults(MCk.MCid()));MCrefreshKinds();MCsaved("Tipo duplicado");}catch(Exception MCe){MCerror(MCe);}}
    private void MCdeleteKind(){MCKind MCk=MCkindList.getSelectionModel().getSelectedItem();if(MCk==null)return;try{MCstore.MCdeleteKind(MCk.MCid());MCnewKind();MCrefreshKinds();MCsaved("Tipo eliminado");}catch(Exception MCe){MCerror(MCe);}}

    private final CheckBox MCgodMode=new CheckBox("Activar Modo Dios (Generación de documentos en masa)");
    private final boolean MCbatchChild;
    private final ToggleButton MCindividualMode=new ToggleButton("Generar individual"),MCbatchMode=new ToggleButton("Generar en masa");
    private HBox MCgenerationModes;
    private VBox MCgeneratorHeader;
    private BorderPane MCbatchPage;
    private final VBox MCbatchForms=new VBox(14);
    private final WebView MCbatchPreview;
    private final Label MCbatchPosition=new Label("Documento 0 de 0");
    private Button MCbatchPrevious,MCbatchNext;
    private final List<MCBatchItem> MCbatchItems=new ArrayList<>();
    private int MCbatchIndex=-1;
    private MCTemplate MCbatchTemplate;

    private BorderPane MCgeneratorPage;
    private void MCbuildGenerator(){
        MCgeneratorPage=new BorderPane();MCgeneratorPage.getStyleClass().add("page");
        GridPane MCtop=new GridPane();MCtop.setHgap(10);for(double MCwidth:new double[]{24,46,30}){ColumnConstraints MCcol=new ColumnConstraints();MCcol.setPercentWidth(MCwidth);MCtop.getColumnConstraints().add(MCcol);}
        MCgenerateGroup.setOnAction(MCe->{if(!MCrefreshingGenerator){MCupdatingClientDocument=null;MCupdatingClient=null;MCrefreshKinds();}});MCgenerateTemplate.setOnAction(MCe->{if(!MCrefreshingGenerator)MCclientAction(()->{MCupdatingClientDocument=null;MCupdatingClient=null;MChistoricalTemplate=null;MCbuildDynamicForm();});});MCgenerateSearch.setPromptText("Buscar entre plantillas");MCgenerateSearch.textProperty().addListener((MCo,MCa,MCb)->{MCupdatingClientDocument=null;MCupdatingClient=null;MCrefreshKinds();});
        MCtop.add(MClabelled("Grupo",MCgenerateGroup),0,0);MCtop.add(MClabelled("Documento o plantilla",MCgenerateTemplate),1,0);MCtop.add(MClabelled("Buscar",MCgenerateSearch),2,0);MCtop.getStyleClass().add("toolbar");MCgeneratorHeader=new VBox(8,MCtop);MCgeneratorPage.setTop(MCgeneratorHeader);BorderPane.setMargin(MCgeneratorHeader,new Insets(0,0,12,0));
        MCgeneratedForm.getStyleClass().add("generator-form");ScrollPane MCformScroll=MCscrolling(MCgeneratedForm);
        MCpreview.setMinSize(0,0);MCpreview.setContextMenuEnabled(false);
        MCpreview.getEngine().getLoadWorker().stateProperty().addListener((MCo,MCa,MCb)->{if(MCb==Worker.State.SUCCEEDED&&MCgenerationModel!=null&&Boolean.TRUE.equals(MCpreview.getEngine().executeScript("typeof window.MCstudio !== 'undefined'"))){MCgenerationReady=true;((JSObject)MCpreview.getEngine().executeScript("window")).setMember("MCjavaBridge",MCgenerationBridge);MCclientAction(()->{var MCsect=MCgenerationDocument.getDocument().getBody().getSectPr();double MCw=794,MCh=1123,MCl=72,MCr=72,MCt=72,MCd=72;if(MCsect!=null){if(MCsect.isSetPgSz()){MCw=((Number)MCsect.getPgSz().getW()).doubleValue()/15;MCh=((Number)MCsect.getPgSz().getH()).doubleValue()/15;}if(MCsect.isSetPgMar()){var MCm=MCsect.getPgMar();if(MCm.getLeft()!=null)MCl=((Number)MCm.getLeft()).doubleValue()/15;if(MCm.getRight()!=null)MCr=((Number)MCm.getRight()).doubleValue()/15;if(MCm.getTop()!=null)MCt=((Number)MCm.getTop()).doubleValue()/15;if(MCm.getBottom()!=null)MCd=((Number)MCm.getBottom()).doubleValue()/15;}}MCpreview.getEngine().executeScript("window.MCstudio.MCpaper("+MCw+","+MCh+","+MCl+","+MCt+","+MCr+","+MCd+")");MCpreview.getEngine().executeScript("window.MCstudio.MCgeneration("+MCjsonString(MCgenerationDisplayValues)+")");});}});
        GridPane MCactions=new GridPane();MCactions.setHgap(8);MCactions.setVgap(8);MCactions.getStyleClass().addAll("action-bar","generator-actions");
        for(int MCi=0;MCi<2;MCi++){ColumnConstraints MCcol=new ColumnConstraints();MCcol.setPercentWidth(50);MCactions.getColumnConstraints().add(MCcol);}
        int MCactionIndex=0;for(Button MCaction:List.of(MCbutton("Previsualizar",this::MCmakePreview),MCbutton("Guardar DOCX",this::MCsaveGenerated),MCbutton("Guardar PDF",this::MCsavePdf),MCbutton("Abrir en Word",this::MCopenGeneratedInWord),MCbutton("Imprimir",this::MCprintGenerated))){MCaction.setGraphic(null);MCaction.setMinWidth(0);MCaction.setMaxWidth(Double.MAX_VALUE);MCaction.setWrapText(true);MCactions.add(MCaction,MCactionIndex%2,MCactionIndex/2);MCactionIndex++;}
        MCupdateDocumentButton.setOnAction(MCe->MCclientAction(this::MCupdateClientDocumentFromGenerator));MCupdateDocumentButton.setVisible(false);MCupdateDocumentButton.setManaged(false);
        MCupdateDocumentBanner.setWrapText(true);MCupdateDocumentBanner.getStyleClass().add("client-update-banner");MCupdateDocumentBanner.setVisible(false);MCupdateDocumentBanner.setManaged(false);
        VBox MCright=new VBox(10,MCupdateDocumentBanner,new Label("DOCUMENTO"),MCpreview,MCupdateDocumentButton,MCactions);MCright.setMinWidth(0);MCright.getStyleClass().add("preview-panel");VBox.setVgrow(MCpreview,Priority.ALWAYS);
        MCgeneratorVariablesToggle.setOnAction(MCe->MCtoggleGeneratorVariables());Region MCspace=new Region();HBox.setHgrow(MCspace,Priority.ALWAYS);Label MCvariablesTitle=new Label("Variables");HBox MCheading=new HBox(8,MCvariablesTitle,MCspace,MCgeneratorVariablesToggle);MCheading.setAlignment(Pos.CENTER_LEFT);
        MCgeneratorVariablesPanel=new VBox(10,MCheading,MCformScroll);MCgeneratorVariablesPanel.getStyleClass().add("generator-variables-panel");MCgeneratorVariablesPanel.setMinWidth(0);VBox.setVgrow(MCformScroll,Priority.ALWAYS);
        MCgeneratorColumns=new GridPane();MCgeneratorColumns.setHgap(14);MCgeneratorFormColumn=new ColumnConstraints();MCgeneratorDocumentColumn=new ColumnConstraints();MCgeneratorColumns.getColumnConstraints().addAll(MCgeneratorFormColumn,MCgeneratorDocumentColumn);RowConstraints MCrow=new RowConstraints();MCrow.setVgrow(Priority.ALWAYS);MCgeneratorColumns.getRowConstraints().add(MCrow);MCgeneratorColumns.add(MCgeneratorVariablesPanel,0,0);MCgeneratorColumns.add(MCright,1,0);GridPane.setVgrow(MCgeneratorVariablesPanel,Priority.ALWAYS);GridPane.setVgrow(MCright,Priority.ALWAYS);MCgeneratorPage.setCenter(MCgeneratorColumns);MCupdateGeneratorColumns();MCbuildBatchGenerator();
    }
    /** Each batch item uses the existing individual form logic with its own state. */
    private final class MCBatchItem {
        final MCStudioWindow MCform;
        final VBox MCcard=new VBox(10);
        final Label MCtitle=new Label(),MCidentity=new Label();
        final Button MCremove;
        String MChtml;
        MCBatchItem(Map<String,String> MCvalues)throws Exception {
            MCform=new MCStudioWindow(MCstage,MCstore,MClookupRouter,MCdocumentActions,MCStudioWindow.this);
            MCform.MCgenerateTemplate.setValue(MCgenerateTemplate.getValue());MCform.MCbuildDynamicForm();
            MCform.MCapplyingApi=true;
            try{MCvalues.forEach((MCkey,MCvalue)->{Control MCinput=MCform.MCinputs.get(MCkey);if(MCinput!=null)MCform.MCsetInput(MCinput,MCvalue);});}finally{MCform.MCapplyingApi=false;}
            MCremove=MCbatchButton("Quitar",Feather.X,()->{MCform.MCdisposeBatchForm();MCbatchItems.remove(this);MCrefreshBatchCards();MCselectBatch(Math.min(MCbatchIndex,MCbatchItems.size()-1));});
            Region MCspace=new Region();HBox.setHgrow(MCspace,Priority.ALWAYS);
            Button MCdocx=MCbatchButton("DOCX",Feather.FILE_TEXT,()->MCdownloadBatchItem(this,false));
            Button MCpdf=MCbatchButton("PDF",Feather.DOWNLOAD,()->MCdownloadBatchItem(this,true));
            HBox MCheading=new HBox(6,MCtitle,MCspace,MCdocx,MCpdf,MCremove);MCheading.setAlignment(Pos.CENTER_LEFT);
            MCidentity.setWrapText(true);MCcard.getChildren().addAll(MCheading,MCidentity,MCform.MCgeneratedForm);MCcard.getStyleClass().add("batch-document-card");
            MCcard.setOnMouseClicked(MCe->{if(MCbatchIndex!=MCbatchItems.indexOf(this))MCselectBatch(MCbatchItems.indexOf(this));});
            for(Control MCinput:MCform.MCinputs.values()){
                Runnable MCchanged=()->{MChtml=null;MCform.MCgeneratedBytes=null;MCform.MCgeneratedPdf=null;MCform.MCgeneratedHistoryId=0;MCidentity.setText(MCbatchIdentity(this));MCupdateBatchPosition();};
                if(MCinput instanceof TextInputControl MCtext)MCtext.textProperty().addListener((MCo,MCa,MCb)->MCchanged.run());
                else if(MCinput instanceof DatePicker MCdate)MCdate.valueProperty().addListener((MCo,MCa,MCb)->MCchanged.run());
            }
        }
    }
    private Button MCbatchButton(String MCtext,Feather MCicon,Runnable MCaction){Button MCbutton=MCbutton(MCtext,MCaction);MCbutton.setGraphic(new FontIcon(MCicon));MCbutton.getStyleClass().add("batch-button");return MCbutton;}
    private void MCbuildBatchGenerator(){
        ToggleGroup MCmodes=new ToggleGroup();MCindividualMode.setToggleGroup(MCmodes);MCbatchMode.setToggleGroup(MCmodes);MCindividualMode.setSelected(true);
        MCgenerationModes=new HBox(8,MCindividualMode,MCbatchMode);MCgenerationModes.getStyleClass().add("generation-modes");
        MCmodes.selectedToggleProperty().addListener((MCo,MCa,MCb)->{if(MCb==null){MCmodes.selectToggle(MCa);return;}MCgeneratorPage.setCenter(MCb==MCbatchMode?MCbatchPage:MCgeneratorColumns);});
        MCbatchPage=new BorderPane();MCbatchPage.getStyleClass().add("batch-page");
        Button MCupload=MCbatchButton("Subir Excel",Feather.UPLOAD,this::MCimportBatchExcel);
        Button MCadd=MCbatchButton("+ Agregar otro documento / cliente",Feather.PLUS,()->MCclientAction(()->{MCaddBatchItem(Map.of());MCselectBatch(MCbatchItems.size()-1);}));MCadd.setText("+ Agregar otro documento / cliente");
        VBox MCleft=new VBox(10,MCupload,MCscrolling(MCbatchForms),MCadd);MCleft.setMinWidth(0);VBox.setVgrow(MCleft.getChildren().get(1),Priority.ALWAYS);
        MCbatchPrevious=MCbatchButton("",Feather.CHEVRON_LEFT,()->MCselectBatch(MCbatchIndex-1));MCbatchPrevious.setTooltip(new Tooltip("Documento anterior"));MCbatchPrevious.setAccessibleText("Documento anterior");
        MCbatchNext=MCbatchButton("",Feather.CHEVRON_RIGHT,()->MCselectBatch(MCbatchIndex+1));MCbatchNext.setTooltip(new Tooltip("Siguiente documento"));MCbatchNext.setAccessibleText("Siguiente documento");
        MCbatchPosition.setWrapText(true);HBox.setHgrow(MCbatchPosition,Priority.ALWAYS);MCbatchPosition.setMaxWidth(Double.MAX_VALUE);
        HBox MCpreviewHeading=new HBox(8,MCbatchPrevious,MCbatchPosition,MCbatchNext);MCpreviewHeading.setAlignment(Pos.CENTER_LEFT);
        MCbatchPreview.setMinSize(0,0);MCbatchPreview.setContextMenuEnabled(false);
        MCbatchPreview.getEngine().getLoadWorker().stateProperty().addListener((MCo,MCa,MCb)->{if(MCb==Worker.State.SUCCEEDED)MCbatchPreview.getEngine().executeScript("window.scrollTo(0,0);document.documentElement.scrollTop=0;document.body.scrollTop=0;");});
        HBox MCactions=new HBox(8,MCbatchButton("Previsualizar",Feather.EYE,this::MCpreviewBatch),MCbatchButton("Descargar .ZIP",Feather.DOWNLOAD,this::MCdownloadBatchZip));MCactions.getStyleClass().add("action-bar");
        VBox MCright=new VBox(10,new Label("Vista Previa"),MCpreviewHeading,MCbatchPreview,MCactions);MCright.getStyleClass().add("preview-panel");MCright.setMinWidth(0);VBox.setVgrow(MCbatchPreview,Priority.ALWAYS);
        SplitPane MCcolumns=new SplitPane(MCleft,MCright);MCcolumns.setDividerPositions(.48);MCbatchPage.setCenter(MCcolumns);MCupdateBatchPosition();
    }
    private void MCupdateGenerationModes(){
        boolean MCenabled=MCgodMode.isSelected();
        if(MCenabled){if(MCbatchItems.isEmpty()&&MCgenerateTemplate.getValue()!=null)MCclientAction(()->MCaddBatchItem(Map.of()));if(!MCgeneratorHeader.getChildren().contains(MCgenerationModes))MCgeneratorHeader.getChildren().add(0,MCgenerationModes);}
        else{MCindividualMode.setSelected(true);MCgeneratorHeader.getChildren().remove(MCgenerationModes);MCgeneratorPage.setCenter(MCgeneratorColumns);}
    }
    private void MCdisposeBatchForm(){
        MClookupDelays.values().forEach(PauseTransition::stop);MCcomplementDelays.values().forEach(PauseTransition::stop);MCcomplementMenus.values().forEach(ContextMenu::hide);MCgenerateTemplate.setValue(null);MCinputs.clear();
        if(MCgenerationDocument!=null)try{MCgenerationDocument.close();}catch(IOException MCignored){}
    }
    private void MCresetBatchForTemplate(MCTemplate MCtemplate){
        if(MCbatchPage==null)return;
        if(MCbatchTemplate!=null&&MCtemplate!=null&&MCbatchTemplate.MCid()==MCtemplate.MCid()&&Arrays.equals(MCbatchTemplate.MCedited(),MCtemplate.MCedited()))return;
        MCbatchItems.forEach(MCi->MCi.MCform.MCdisposeBatchForm());MCbatchItems.clear();MCbatchTemplate=MCtemplate;MCbatchIndex=-1;MCbatchPreview.getEngine().loadContent("");
        if(MCtemplate!=null&&MCgodMode.isSelected())MCclientAction(()->MCaddBatchItem(Map.of()));MCrefreshBatchCards();
    }
    private void MCaddBatchItem(Map<String,String> MCvalues)throws Exception{
        if(MCgenerateTemplate.getValue()==null)throw new IllegalArgumentException("Seleccione una plantilla.");
        MCbatchItems.add(new MCBatchItem(MCvalues));if(MCbatchIndex<0)MCbatchIndex=0;MCrefreshBatchCards();
    }
    private void MCrefreshBatchCards(){
        MCbatchForms.getChildren().clear();for(int MCi=0;MCi<MCbatchItems.size();MCi++){
            MCBatchItem MCitem=MCbatchItems.get(MCi);MCitem.MCtitle.setText("Documento #"+(MCi+1));MCvisible(MCitem.MCremove,MCi>0);MCitem.MCidentity.setText(MCbatchIdentity(MCitem));MCbatchForms.getChildren().add(MCitem.MCcard);
        }MCupdateBatchPosition();
    }
    private String MCbatchIdentity(MCBatchItem MCitem){
        Map<String,String> MCvalues=MCitem.MCform.MCcurrentFormValues();List<String> MCidentity=new ArrayList<>();
        for(var MCentry:MCvalues.entrySet())if(MCentry.getKey().toLowerCase(Locale.ROOT).matches(".*(nombre|name|dni|ruc|razon|documento|apellido).*"))if(!MCentry.getValue().isBlank())MCidentity.add(MCentry.getValue());
        if(MCidentity.isEmpty())MCvalues.values().stream().filter(MCv->!MCv.isBlank()).limit(2).forEach(MCidentity::add);
        return MCidentity.isEmpty()?"Cliente pendiente":String.join(" · ",MCidentity);
    }
    private void MCupdateBatchPosition(){
        int MCcount=MCbatchItems.size();MCbatchPosition.setText("Documento "+(MCcount==0?0:MCbatchIndex+1)+" de "+MCcount+(MCbatchIndex>=0&&MCbatchIndex<MCcount?" · "+MCbatchIdentity(MCbatchItems.get(MCbatchIndex)):""));
        if(MCbatchPrevious!=null){MCbatchPrevious.setDisable(MCbatchIndex<=0);MCbatchNext.setDisable(MCbatchIndex<0||MCbatchIndex>=MCcount-1);}
        for(int MCi=0;MCi<MCcount;MCi++){MCbatchItems.get(MCi).MCcard.getStyleClass().remove("batch-document-selected");if(MCi==MCbatchIndex)MCbatchItems.get(MCi).MCcard.getStyleClass().add("batch-document-selected");}
    }
    private void MCselectBatch(int MCindex){
        if(MCindex<0||MCindex>=MCbatchItems.size()){MCbatchIndex=-1;MCbatchPreview.getEngine().loadContent("");MCupdateBatchPosition();return;}
        MCbatchIndex=MCindex;MCupdateBatchPosition();MCclientAction(()->{MCBatchItem MCitem=MCbatchItems.get(MCindex);if(MCitem.MCform.MCgeneratedBytes==null)MCitem.MCform.MCmakePreview();if(MCitem.MCform.MCgeneratedBytes!=null){if(MCitem.MChtml==null)MCitem.MChtml=MCbatchPreviewHtml(MCitem);MCbatchPreview.getEngine().loadContent(MCitem.MChtml);}});
    }
    private String MCbatchPreviewHtml(MCBatchItem MCitem)throws IOException{
        try(XWPFDocument MCdoc=MCDocumentEngine.MCopen(MCitem.MCform.MCgeneratedBytes)){return new MCContinuousDocxEditor(MCdoc).MCpreviewHtml();}
    }
    private boolean MCgenerateBatch(){
        if(MCbatchItems.isEmpty())throw new IllegalArgumentException("Agregue al menos un documento.");
        for(MCBatchItem MCitem:MCbatchItems){MCitem.MCform.MCmakePreview();if(MCitem.MCform.MCgeneratedBytes==null)return false;try{MCitem.MChtml=MCbatchPreviewHtml(MCitem);}catch(IOException MCe){throw new UncheckedIOException(MCe);}}return true;
    }
    private void MCpreviewBatch(){if(MCgenerateBatch())MCselectBatch(Math.max(0,MCbatchIndex));}
    private void MCdownloadBatchItem(MCBatchItem MCitem,boolean MCpdf){
        if(MCpdf)MCitem.MCform.MCsavePdf();else MCitem.MCform.MCsaveGenerated();
    }
    private void MCdownloadBatchZip(){MCclientAction(()->{
        ChoiceDialog<String> MCformat=new ChoiceDialog<>("DOCX y PDF",List.of("DOCX","PDF","DOCX y PDF"));MCformat.initOwner(MCstage);MCformat.setTitle("Formato del lote");MCformat.setHeaderText("Documentos que incluirá el ZIP");String MCchoice=MCformat.showAndWait().orElse(null);if(MCchoice==null)return;
        File MCtarget=MCzipChooser("documentos.zip").showSaveDialog(MCstage);if(MCtarget==null||!MCgenerateBatch())return;
        MCBatchZip.MCFormat MCtype=switch(MCchoice){case "DOCX"->MCBatchZip.MCFormat.DOCX;case "PDF"->MCBatchZip.MCFormat.PDF;default->MCBatchZip.MCFormat.BOTH;};
        List<MCBatchZip.MCDocument> MCdocuments=MCbatchItems.stream().map(MCi->new MCBatchZip.MCDocument(MCbatchIdentity(MCi),MCi.MCform.MCgeneratedBytes)).toList();
        List<byte[]> MCpdfs=MCBatchZip.MCwrite(MCtarget.toPath(),MCdocuments,MCtype);
        for(int MCi=0;MCi<MCbatchItems.size();MCi++)MCbatchItems.get(MCi).MCform.MCrecordGeneration(null,null,MCtype==MCBatchZip.MCFormat.DOCX?null:MCpdfs.get(MCi));
        MCsaved(MCbatchItems.size()+" documentos descargados en "+MCtarget.getName());
    });}
    private void MCimportBatchExcel(){MCclientAction(()->{
        MCTemplate MCtemplate=MCgenerateTemplate.getValue();if(MCtemplate==null)throw new IllegalArgumentException("Seleccione una plantilla.");
        FileChooser MCchooser=new FileChooser();MCchooser.setTitle("Subir Excel");MCchooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx, *.xls)","*.xlsx","*.xls"));File MCfile=MCchooser.showOpenDialog(MCstage);if(MCfile==null)return;
        new MCExcelMappingDialog(MCstage,MCExcelBatch.MCread(MCfile.toPath()),MCvariablesForTemplate(MCtemplate)).MCshow().ifPresent(MCrows->MCclientAction(()->MCpopulateBatch(MCrows)));
    });}
    private void MCpopulateBatch(List<Map<String,String>> MCrows)throws Exception{
        MCbatchItems.forEach(MCi->MCi.MCform.MCdisposeBatchForm());MCbatchItems.clear();MCbatchIndex=-1;
        for(Map<String,String> MCrow:MCrows)MCbatchItems.add(new MCBatchItem(MCrow));MCbatchIndex=MCbatchItems.isEmpty()?-1:0;MCrefreshBatchCards();MCpreviewBatch();
    }

    private void MCtoggleGeneratorVariables(){MCgeneratorVariablesCollapsed=!MCgeneratorVariablesCollapsed;MCupdateGeneratorColumns();}
    private void MCupdateGeneratorColumns(){
        boolean MCcollapsed=MCgeneratorVariablesCollapsed;MCgeneratorFormColumn.setPercentWidth(MCcollapsed?-1:46);MCgeneratorFormColumn.setMinWidth(MCcollapsed?48:0);MCgeneratorFormColumn.setPrefWidth(MCcollapsed?48:-1);MCgeneratorFormColumn.setMaxWidth(MCcollapsed?48:Double.MAX_VALUE);MCgeneratorDocumentColumn.setPercentWidth(MCcollapsed?-1:54);MCgeneratorDocumentColumn.setHgrow(Priority.ALWAYS);MCgeneratorDocumentColumn.setMinWidth(0);
        MCgeneratorVariablesToggle.setGraphic(new FontIcon(MCcollapsed?Feather.CHEVRONS_RIGHT:Feather.CHEVRONS_LEFT));MCgeneratorVariablesToggle.setTooltip(new Tooltip(MCcollapsed?"Mostrar panel de variables":"Minimizar panel de variables"));
        HBox MCheading=(HBox)MCgeneratorVariablesPanel.getChildren().get(0);for(Node MCnode:MCheading.getChildren())if(MCnode!=MCgeneratorVariablesToggle){MCnode.setVisible(!MCcollapsed);MCnode.setManaged(!MCcollapsed);}Node MCcontent=MCgeneratorVariablesPanel.getChildren().get(1);MCcontent.setVisible(!MCcollapsed);MCcontent.setManaged(!MCcollapsed);MCgeneratorVariablesPanel.getStyleClass().remove("collapsed");if(MCcollapsed)MCgeneratorVariablesPanel.getStyleClass().add("collapsed");
    }

    private void MCshowGenerator(){
        MCrefreshingGenerator=true;
        try{
            MCTemplateGroup MCprevious=MCgenerateGroup.getValue();List<MCTemplateGroup> MCgroups=MCstore.MCgroups();MCgenerateGroup.getItems().setAll(MCgroups);
            MCgenerateGroup.getSelectionModel().select(MCgroups.stream().filter(MCg->MCprevious!=null&&MCg.MCid()==MCprevious.MCid()).findFirst().orElse(MCgroups.isEmpty()?null:MCgroups.get(0)));
        }catch(Exception MCe){MCerror(MCe);}finally{MCrefreshingGenerator=false;}
        if(MCupdatingClientDocument==null)MCrefreshKinds();MCpage("Generar documentos",MCgeneratorPage);
        if(MCgenerateTemplate.getValue()==null)MCgeneratedForm.getChildren().setAll(new Label(MCgenerateGroup.getItems().isEmpty()?"Cree un grupo y asocie sus plantillas desde Plantillas.":"Seleccione una plantilla del grupo."));
    }
    private void MCbuildDynamicForm(){
        MClookupDelays.values().forEach(PauseTransition::stop);MClookupDelays.clear();MCcomplementDelays.values().forEach(PauseTransition::stop);MCcomplementDelays.clear();MCcomplementMenus.values().forEach(ContextMenu::hide);MCcomplementMenus.clear();MCcomplementTargets.clear();MCactiveComplementDefinitions.clear();MCactiveComplementLinks.clear();
        MCTemplate MCtemplate=MCgenerateTemplate.getValue();MCgeneratedBytes=null;MCgeneratedPdf=null;MCgeneratedHistoryId=0;MCgenerationReady=false;MCgenerationModel=null;if(MCgenerationDocument!=null)try{MCgenerationDocument.close();}catch(IOException MCignored){}MCgenerationDocument=null;if(!MCbatchChild)MCpreview.getEngine().loadContent("");
        boolean MCupdating=MCupdatingClientDocument!=null;MCupdateDocumentButton.setVisible(MCupdating);MCupdateDocumentButton.setManaged(MCupdating);MCupdateDocumentBanner.setVisible(MCupdating);MCupdateDocumentBanner.setManaged(MCupdating);if(MCupdating)MCupdateDocumentBanner.setText("Actualizando documento de "+MCclientName(MCupdatingClient)+": "+MCupdatingClientDocument.MCtitle());MCinputs.clear();MCformVariables.clear();MCformDefinitions.clear();MCmanuallyEdited.clear();MClocalValueOverrides.clear();MCgeneratedForm.getChildren().clear();
        if(!MCbatchChild)MCresetBatchForTemplate(MCtemplate);
        if(MCtemplate==null)return;
        try{
            List<MCVariable> MCvariables=MCvariablesForTemplate(MCtemplate);List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();Map<String,String> MCnames=new LinkedHashMap<>();for(var MCperson:MCstore.MCpersonas(MCtemplate.MCid()))MCnames.put(MCperson.MCpersonaKey(),MCperson.MCdisplayName());
            String MCindependentScope="!independent";
            Map<String,List<MCVariable>> MCclients=new LinkedHashMap<>();MCclients.put("",new ArrayList<>());for(String MCkey:MCnames.keySet())MCclients.put(MCkey,new ArrayList<>());
            List<MCVariable> MCindependent=new ArrayList<>();
            for(MCVariable MCvariable:MCvariables){
                MCDefinition MCdefinition=MCdefinitionFor(MCvariable,MCdefinitions);if(MCdefinition!=null)MCformDefinitions.put(MCvariable.MCkey(),MCdefinition);
                if(MCdefinition!=null&&!MCdefinition.MCbindToClient())MCindependent.add(MCvariable);
                else MCclients.computeIfAbsent(MCDocumentEngine.MCpersonaKey(MCvariable.MCkey()),MCkey->new ArrayList<>()).add(MCvariable);
            }
            List<String> MCscopes=new ArrayList<>(MCclients.entrySet().stream().filter(MCentry->!MCentry.getValue().isEmpty())
                .sorted(Comparator.comparingInt((Map.Entry<String,List<MCVariable>> MCentry)->MCentry.getValue().size()).reversed()).map(Map.Entry::getKey).toList());
            if(!MCindependent.isEmpty())MCscopes.add(MCindependentScope);
            Map<String,GridPane> MCgrids=new LinkedHashMap<>();Map<String,Integer> MCslots=new HashMap<>();VBox MCindependentCard=null;
            for(String MCscope:MCscopes){
                GridPane MCgrid=MCtwoColumns();MCgrids.put(MCscope,MCgrid);boolean MCglobal=MCscope.equals(MCindependentScope);
                VBox MCcard=new VBox(10,new Label(MCglobal?"Variables independientes":MCscope.isEmpty()?"Cliente / Solicitante":MCnames.getOrDefault(MCscope,MCscope.replace('_',' '))),MCgrid);MCcard.getStyleClass().add("binding-card");
                if(MCglobal)MCindependentCard=MCcard;else{MCcard.getProperties().put("persona-key",MCscope);MCgeneratedForm.getChildren().add(MCcard);}
            }
            for(MCVariable MCvariable:MCvariables){
                MCDefinition MCdefinition=MCformDefinitions.get(MCvariable.MCkey());String MCscope=MCdefinition!=null&&!MCdefinition.MCbindToClient()?MCindependentScope:MCDocumentEngine.MCpersonaKey(MCvariable.MCkey());GridPane MCgrid=MCgrids.get(MCscope);int MCslot=MCslots.getOrDefault(MCscope,0);
                String MCinitial=MCdefinition!=null?MCdefinition.MCinitialValue():MCVariableInput.MCinitialValue(MCvariable.MCtype(),MCvariable.MCdefaultValue());Control MCinput;
                if(MCdefinition!=null&&(!MCdefinition.MCbindToClient()||MCdefinition.MCautoCurrentDate()))MCinput=new TextField(MCinitial);
                else if(List.of("Texto largo","Domicilio").contains(MCvariable.MCtype())){TextArea MCarea=new TextArea(MCinitial);MCarea.setPrefRowCount(3);MCarea.setWrapText(true);MCinput=MCarea;}
                else if("Fecha".equals(MCvariable.MCtype())){DatePicker MCdate=new DatePicker();try{MCdate.setValue(LocalDate.parse(MCinitial));}catch(Exception MCignored){MCdate.setValue(LocalDate.now());}MCinput=MCdate;}
                else MCinput=new TextField(MCinitial);
                if(MCinput instanceof TextInputControl MCeditable)MCeditable.textProperty().addListener((MCo,MCa,MCb)->{if(!MCapplyingApi)MCmanuallyEdited.add(MCvariable.MCkey());});
                else if(MCinput instanceof DatePicker MCdate)MCdate.valueProperty().addListener((MCo,MCa,MCb)->{if(!MCapplyingApi)MCmanuallyEdited.add(MCvariable.MCkey());});
                MCinputs.put(MCvariable.MCkey(),MCinput);MCformVariables.put(MCvariable.MCkey(),MCvariable);Node MCcontrol=MCinput;
                String MCservice=MCqueryService(MCtemplate.MCid(),MCvariable);
                if(MCservice!=null&&MCinput instanceof TextField MCfield){
                    PauseTransition MCdelay=new PauseTransition(Duration.millis(350));MClookupDelays.put(MCvariable.MCkey(),MCdelay);
                    Runnable MCconsult=()->{MCdelay.stop();MClookupForField(MCtemplate,MCvariable,MCfield,MCservice);};
                    MCdelay.setOnFinished(MCe->{if(MCinputs.get(MCvariable.MCkey())!=MCfield||!MCvalidQueryNumber(MCservice,MCfield.getText()))return;if(MCapiQueryRunning)MCdelay.playFromStart();else MCsafe(MCconsult);});
                    MCfield.textProperty().addListener((MCo,MCa,MCb)->{
                        MCdelay.stop();if(MCapplyingApi||!MCvalidQueryNumber(MCservice,MCb))return;
                        try{MCresetContactEdits(MCtemplate,MCvariable,MCservice);if(!MCloadLocalForField(MCtemplate,MCvariable,MCfield,MCservice))MCdelay.playFromStart();}catch(Exception MCex){MCerror(MCex);}
                    });
                    Button MClookup=MCbutton("Consultar API",MCconsult);MClookup.setTooltip(new Tooltip("Busca primero en Clientes locales; si no existe, consulta la API."));MCfield.setMinWidth(0);
                    MClookup.setGraphic(null);MClookup.getStyleClass().add("lookup-button");HBox MCquery=new HBox(6,MCfield,MClookup);HBox.setHgrow(MCfield,Priority.ALWAYS);MCcontrol=MCquery;
                }
                VBox MCfield=MClabelled(MCvariable.MClabel(),MCcontrol);
                if(MCinput instanceof TextArea){if(MCslot%2!=0)MCslot++;MCgrid.add(MCfield,0,MCslot/2,2,1);MCslot+=2;}
                else{MCgrid.add(MCfield,MCslot%2,MCslot/2);MCslot++;}MCslots.put(MCscope,MCslot);
            }
            MCconfigureComplements(MCvariables);MCaddContactSelectors(MCtemplate);if(MCindependentCard!=null)MCgeneratedForm.getChildren().add(MCindependentCard);
            if(!MCbatchChild)MCrenderPreview(MChistoricalTemplate==null?MCtemplate.MCedited():MChistoricalTemplate);if(MCinputs.isEmpty()){Label MCmessage=new Label("Este documento no contiene variables. Puedes editar su contenido directamente en el panel del documento.");MCmessage.setWrapText(true);MCgeneratedForm.getChildren().add(MCmessage);}
            MCinputs.values().forEach(MCinput->{if(MCinput instanceof TextInputControl MCtext)MCtext.textProperty().addListener((MCo,MCa,MCb)->MCclientAction(this::MCrefreshGenerationValues));else if(MCinput instanceof DatePicker MCdate)MCdate.valueProperty().addListener((MCo,MCa,MCb)->MCclientAction(this::MCrefreshGenerationValues));});
        }catch(Exception MCe){MCerror(MCe);}
    }
    private void MCaddContactSelectors(MCTemplate MCtemplate)throws Exception{
        Map<String,List<MCContactBinding>> MCgroups=new LinkedHashMap<>();for(MCContactBinding MCbinding:MCdata.MCcontactBindings(MCtemplate.MCid()))if(MCContactReuse.MCclientKey(MCbinding.MCvariableKey(),MCstore.MCdefinitions()))MCgroups.computeIfAbsent(MCbinding.MCgroupKey(),MCignored->new ArrayList<>()).add(MCbinding);
        for(var MCentry:MCgroups.entrySet()){
            List<MCContactBinding> MCgroup=MCentry.getValue();String MCkind=MCgroup.get(0).MCkind();TextField MCsearch=new TextField();MCsearch.setPromptText("Buscar por nombre o documento");
            ComboBox<MCContact> MCchoice=new ComboBox<>();MCchoice.setPromptText("Seleccionar "+MCkind.toLowerCase());MCchoice.setMaxWidth(Double.MAX_VALUE);MCchoice.setMinWidth(0);
            Runnable MCfilter=()->{try{String MCq=MCsearch.getText().trim().toLowerCase();MCchoice.getItems().setAll(MCdata.MCcontacts().stream().filter(MCc->MCkind.equals(MCc.MCkind())).filter(MCc->(MCc.MCgiven()+" "+MCc.MCfamily()+" "+MCc.MCnumber()).toLowerCase().contains(MCq)).toList());}catch(Exception MCex){MCerror(MCex);}};
            MCsearch.textProperty().addListener((MCo,MCa,MCb)->MCfilter.run());MCchoice.setOnShowing(MCe->MCfilter.run());MCfilter.run();
            VBox MCrow=new VBox(8,MCsearch,MCchoice,MCbutton("Usar registro",()->{MCContact MCselected=MCchoice.getValue();if(MCselected==null)return;Map<String,String> MCvalues=MCContactReuse.MCvaluesFor(MCselected,MCgroup);for(var MCmapped:MCvalues.entrySet()){Control MCtarget=MCinputs.get(MCmapped.getKey());if(MCtarget!=null)MCsetInput(MCtarget,MCmapped.getValue());else MClocalValueOverrides.put(MCmapped.getKey(),MCmapped.getValue());}MCsaved("Datos locales aplicados a "+MCentry.getKey()+"; puede editarlos antes de generar");}));
            MCgeneratedForm.getChildren().add(MClabelled("REGISTRO LOCAL · "+MCentry.getKey()+" · "+MCkind,MCrow));
        }
    }
    private Map<String,String> MCcurrentFormValues(){Map<String,String> MCvalues=new LinkedHashMap<>(MClocalValueOverrides);MCinputs.forEach((MCkey,MCinput)->MCvalues.put(MCkey,MCreadInput(MCinput)));return MCvalues;}
    private void MCrenderPreview(byte[] MCbytes)throws Exception{
        MCgenerationReady=false;MCgenerationDocument=MCDocumentEngine.MCopen(MCbytes);MCgenerationModel=new MCContinuousDocxEditor(MCgenerationDocument);MCgenerationDisplayValues=MCdocumentValues(MCbytes,MCcurrentFormValues());MCgenerationValueAliases.clear();for(String MCkey:MCgenerationDisplayValues.keySet()){MCDefinition MCdefinition=MCformDefinitions.get(MCDocumentEngine.MCbaseKey(MCkey));MCgenerationValueAliases.put(MCkey,MCdefinition!=null&&!MCdefinition.MCbindToClient()?MCdefinition.MCkey():MCkey);}MCpreview.getEngine().loadContent(MCgenerationModel.MCgenerationHtml());
    }
    private void MCrefreshGenerationValues()throws Exception{if(MCgenerationDocument==null)return;Map<String,String> MCvalues=MCcurrentFormValues();MCgenerationDisplayValues=new LinkedHashMap<>(MCvalues);MCgenerationValueAliases.forEach((MCkey,MCsource)->MCgenerationDisplayValues.put(MCkey,MCvalues.getOrDefault(MCsource,"")));if(MCgenerationReady)MCpreview.getEngine().executeScript("window.MCstudio.MCgeneration("+MCjsonString(MCgenerationDisplayValues)+")");}
    private void MCsyncGenerationEditor()throws Exception{
        if(!MCgenerationReady||MCgenerationModel==null)return;
        MCgenerationModel.MCapply(String.valueOf(MCpreview.getEngine().executeScript("window.MCstudio.MChtml()")));
        XWPFDocument MCnormalized=MCDocumentEngine.MCopen(MCDocumentEngine.MCbytes(MCgenerationDocument));MCgenerationDocument.close();MCgenerationDocument=MCnormalized;MCgenerationModel=new MCContinuousDocxEditor(MCnormalized);
        MCpreview.getEngine().executeScript("window.MCstudio.MCaccept("+MCjsonString("<div>"+MCgenerationModel.MChtml()+"</div>")+")");
    }
    public final class MCGenerationBridge {
        public void MCmarkDirty(){MCgeneratedHistoryId=0;MCgeneratedPdf=null;}
        public void MCtableSelected(boolean MCselected){} public void MCimageSelected(boolean MCselected){}
        public void MConSelectionFormatChanged(String MCstate){}
        public void MCsaveCurrentTemplate(){MCclientAction(()->{if(MCupdatingClientDocument!=null)MCupdateClientDocumentFromGenerator();else MCsaveGenerated();});}
    }
    private void MCmakePreview(){
        MCgeneratedBytes=null;MCgeneratedPdf=null;MCTemplate MCselectedTemplate=MCgenerateTemplate.getValue();if(MCselectedTemplate==null)throw new IllegalArgumentException("Seleccione una plantilla.");
        try{
            MCcompletingMissingComplements=true;try{for(String MCkey:MCcomplementTargets.keySet())MCcompleteExactComplement(MCkey);}finally{MCcompletingMissingComplements=false;}
            Map<String,String> MCvalues=new LinkedHashMap<>();Map<String,MCVariable> MCvars=new HashMap<>();
            for(MCVariable MCv:MCvariablesForTemplate(MCselectedTemplate)){MCvars.put(MCv.MCkey(),MCv);MCvalues.put(MCv.MCkey(),MCVariableInput.MCinitialValue(MCv.MCtype(),MCv.MCdefaultValue()));}
            MCvalues.putAll(MClocalValueOverrides);
            for(var MCentry:MCinputs.entrySet()){
                Control MCinput=MCentry.getValue();MCVariable MCv=MCvars.get(MCentry.getKey());
                if(MCv==null)throw new IllegalArgumentException("Variable sin definir: "+MCentry.getKey());
                String MCvalue=MCformDefinitions.containsKey(MCentry.getKey())&&(!MCformDefinitions.get(MCentry.getKey()).MCbindToClient()||MCformDefinitions.get(MCentry.getKey()).MCautoCurrentDate())?MCreadInput(MCinput):MCVariableInput.MCvalidate(MCv.MCtype(),MCreadInput(MCinput),Locale.getDefault());
                MCvalues.put(MCentry.getKey(),MCvalue);
            }
            if(!MCvalues.equals(MCgeneratedValues))MCgeneratedHistoryId=0;
            MCsyncGenerationEditor();byte[] MCsource=MCgenerationDocument==null?(MChistoricalTemplate==null?MCselectedTemplate.MCedited():MChistoricalTemplate):MCDocumentEngine.MCbytes(MCgenerationDocument);
            MCgeneratedBytes=MCDocumentEngine.MCgenerate(MCsource,MCdocumentValues(MCsource,MCvalues));MCgeneratedValues=MCvalues;MClearnScopedComplements(MCvalues);MCautoSaveContacts(MCselectedTemplate);MCrefreshGenerationValues();

            MCsaved("Vista previa generada"+MCcontactSaveSummary);
        }catch(Exception MCe){MCerror(MCe);}
    }
    private void MCsaveGenerated(){MCmakePreview();if(MCgeneratedBytes==null)return;FileChooser MCfc=new FileChooser();MCfc.setTitle("Guardar documento generado");MCfc.setInitialFileName(MCfileName()+".docx");MCfc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word DOCX","*.docx"));File MCtarget=MCfc.showSaveDialog(MCstage);if(MCtarget==null)return;try{Files.write(MCtarget.toPath(),MCgeneratedBytes);MCrecordGeneration(MCtarget.toString(),null,null);MCsaved("DOCX guardado: "+MCtarget.getName()+MCcontactSaveSummary);}catch(Exception MCe){MCerror(MCe);}}
    private void MCsavePdf(){MCmakePreview();if(MCgeneratedBytes==null)return;FileChooser MCfc=new FileChooser();MCfc.setTitle("Guardar PDF");MCfc.setInitialFileName(MCfileName()+".pdf");MCfc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));File MCtarget=MCfc.showSaveDialog(MCstage);if(MCtarget==null)return;try{MCgeneratedPdf=MCDocxPdfExporter.MCrender(MCgeneratedBytes);Files.write(MCtarget.toPath(),MCgeneratedPdf);MCrecordGeneration(null,MCtarget.toString(),MCgeneratedPdf);MCsaved("PDF guardado: "+MCtarget.getName()+MCcontactSaveSummary);}catch(Exception MCe){MCerror(MCe);}}
    private void MCopenGeneratedInWord(){
        MCmakePreview();if(MCgeneratedBytes==null)return;
        try{Path MCdocument=Files.createTempFile("documento-",".docx");Files.write(MCdocument,MCgeneratedBytes);MCdocument.toFile().deleteOnExit();if(MCupdatingClientDocument==null)MCrecordGeneration(null,null,null);MCdocumentActions.MCopen(MCdocument);MCsaved("Documento abierto en el procesador de texto"+MCcontactSaveSummary);}catch(Exception MCe){MCerror(MCe);}
    }
    private void MCprintGenerated(){
        MCmakePreview();if(MCgeneratedBytes==null)return;
        try{MCgeneratedPdf=MCDocxPdfExporter.MCrender(MCgeneratedBytes);Path MCdocument=Files.createTempFile("documento-",".pdf");Files.write(MCdocument,MCgeneratedPdf);MCdocument.toFile().deleteOnExit();if(MCupdatingClientDocument==null)MCrecordGeneration(null,null,MCgeneratedPdf);MCdocumentActions.MCprint(MCdocument);MCsaved("Documento enviado a impresión"+MCcontactSaveSummary);}catch(Exception MCe){MCerror(MCe);}
    }
    private String MCfileName(){return MCgenerateTemplate.getValue().MCname().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","");}
    private void MCrecordGeneration(String MCdocxPath,String MCpdfPath,byte[] MCpdf)throws Exception{
        if(MCupdatingClientDocument!=null){MCreplaceUpdatingClientDocument();return;}
        MCTemplate MCtemplate=MCgenerateTemplate.getValue();int MCversion=MCdata.MCrecordRevision(MCtemplate.MCid(),MChistoricalTemplate==null?MCtemplate.MCedited():MChistoricalTemplate);
        if(MCgeneratedHistoryId==0){MCHistory MChistory=MCdata.MCsaveHistory(new MCHistory(0,MCtemplate.MCname(),MCtemplate.MCid(),MCversion,null,MCjson.writeValueAsString(MCgeneratedValues),MCdocxPath,MCpdfPath,MCgeneratedBytes,MCpdf));MCgeneratedHistoryId=MChistory.MCid();}
        else MCdata.MCupdateHistoryFiles(MCgeneratedHistoryId,MCdocxPath,MCpdfPath,MCpdf);
        MCautoSaveContacts(MCtemplate);MCrefreshClientDocuments();
    }
    private void MCautoSaveContacts(MCTemplate MCtemplate){MCcontactSaveSummary="";try{
        List<MCVariable> MCvariables=MCvariablesForTemplate(MCtemplate);
        Map<String,String> MCtypes=new HashMap<>();for(MCVariable MCv:MCvariables){String MCservice=MCqueryService(MCtemplate.MCid(),MCv);MCtypes.put(MCv.MCkey(),MCservice==null?MCv.MCtype():MCservice);}
        MCContactReuse.MCResult MCresult=MCContactReuse.MCsave(MCdata,MCcontactFormBindings(MCtemplate.MCid(),MCvariables),MCgeneratedValues,MCtypes,MCstore.MCdefinitions());
        MCcontactSaveSummary=" · Clientes: "+MCresult.MCcreated()+" nuevos, "+MCresult.MCupdated()+" actualizados";MCrefreshContacts();
    }catch(Exception MCe){MCcontactSaveSummary=" · No se pudo actualizar Clientes: "+MCe.getMessage();}}
    private List<MCContactBinding> MCcontactFormBindings(long MCtemplateId,List<MCVariable> MCvariables)throws Exception{
        Map<String,MCApiBinding> MCapi=new LinkedHashMap<>();for(MCVariable MCv:MCvariables){long MCid=MCdefinitionId(MCv);MCApiBinding MCbinding=MCid>0?MCdata.MCapiBinding(MCid):null;if(MCbinding!=null)MCapi.put(MCv.MCkey(),MCbinding);}
        return MCContactReuse.MCformBindings(MCtemplateId,MCvariables,MCdata.MCcontactBindings(MCtemplateId),MCdata.MCapiContexts(MCtemplateId),MCapi,MCstore.MCdefinitions());
    }

    private void MCbuildContacts(){
        MCcontactsPage=new BorderPane();MCcontactsPage.getStyleClass().add("page");MCcontactSearch.setPromptText("Buscar nombre, documento o dirección");MCcontactSearch.textProperty().addListener((MCo,MCa,MCb)->MCrefreshContacts());MCcontactList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->MCloadContact(MCb));
        MCtwoLineCells(MCcontactList,MCc->("Empresa".equals(MCc.MCkind())?MCc.MCgiven():MCc.MCgiven()+" "+MCc.MCfamily()),MCc->MCc.MCdocumentType()+" · "+MCc.MCnumber());
        VBox MCleft=new VBox(8,MCcontactSearch,MCcontactList,MCbutton("+ Nueva persona",this::MCnewContact),MCbutton("Eliminar cliente",this::MCdeleteContact));MCleft.getStyleClass().add("side-panel");MCfixedWidth(MCleft,320);MCuniformButtons(MCleft);VBox.setVgrow(MCcontactList,Priority.ALWAYS);MCcontactsPage.setLeft(MCleft);BorderPane.setMargin(MCleft,new Insets(0,14,0,0));
        MCcontactKind.getItems().setAll("Persona","Empresa");MCcontactKind.setValue("Persona");MCcontactKind.setOnAction(MCe->{boolean MCcompany="Empresa".equals(MCcontactKind.getValue());MCcontactFamily.setDisable(MCcompany);MCcontactDocType.setDisable(MCcompany);if(MCcompany)MCcontactDocType.setText("RUC");else if("RUC".equals(MCcontactDocType.getText()))MCcontactDocType.setText("DNI");});MCcontactRepresentatives.setPrefRowCount(2);MCcontactExtras.setPrefRowCount(3);MCcontactRepresentatives.setWrapText(true);MCcontactExtras.setWrapText(true);
        GridPane MCfields=MCtwoColumns();MCfields.add(MClabelled("Tipo de cliente",MCcontactKind),0,0);MCfields.add(MClabelled("Nombres / razón social",MCcontactGiven),1,0);MCfields.add(MClabelled("Apellidos",MCcontactFamily),0,1);MCfields.add(MClabelled("Tipo de documento",MCcontactDocType),1,1);MCfields.add(MClabelled("Número de documento / RUC",MCcontactNumber),0,2);MCfields.add(MClabelled("Domicilio / domicilio fiscal",MCcontactAddress),1,2);MCfields.add(MClabelled("Representantes (empresa)",MCcontactRepresentatives),0,3,2,1);MCfields.add(MClabelled("Datos adicionales · JSON",MCcontactExtras),0,4,2,1);
        VBox MCform=new VBox(16,new Label("FICHA LOCAL"),MCfields,MCcontactVariables());MCform.getStyleClass().add("catalog-form");BorderPane MCdetail=new BorderPane(MCscrolling(MCform));HBox MCactions=new HBox(MCbutton("Guardar cliente",this::MCsaveContact));MCactions.getStyleClass().add("action-bar");MCdetail.setBottom(MCactions);MCclientDetailsPane=MCdetail;
        MCtwoLineCells(MCclientDocumentList,MCClientDocument::MCtitle,MCd->MCd.MCextension().toUpperCase(Locale.ROOT)+" · "+("GENERATED".equals(MCd.MCsourceType())?"Generado":"Subido")+" · "+MCd.MCupdatedAt());
        Button MCupload=MCbutton("+ Subir documento",this::MCuploadClientDocument),MCopen=MCbutton("Abrir",this::MCopenClientDocument),MCdownload=MCbutton("Descargar",this::MCdownloadClientDocument),MCgenerate=MCbutton("Actualizar en Generar documentos",this::MCeditSelectedClientDocument),MCreplace=MCbutton("Actualizar subiendo archivo",this::MCreplaceClientDocumentByUpload);
        MCclientDocumentList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->{MCopen.setDisable(MCb==null);MCdownload.setDisable(MCb==null);MCreplace.setDisable(MCb==null);MCgenerate.setDisable(MCb==null||!MCisWordExtension(MCb.MCextension()));});MCopen.setDisable(true);MCdownload.setDisable(true);MCreplace.setDisable(true);MCgenerate.setDisable(true);
        HBox MCdocumentTop=new HBox(MCupload);MCdocumentTop.getStyleClass().add("action-bar");FlowPane MCdocumentActions=new FlowPane(8,8,MCopen,MCdownload,MCgenerate,MCreplace);MCdocumentActions.getStyleClass().add("action-bar");MCclientDocumentsPane.setTop(MCdocumentTop);MCclientDocumentsPane.setCenter(MCclientDocumentList);MCclientDocumentsPane.setBottom(MCdocumentActions);
        MCclientDetailsToggle.setOnAction(MCe->{MCshowingClientDetails=!MCshowingClientDetails;MCshowClientPanel();});Region MCgap=new Region();HBox.setHgrow(MCgap,Priority.ALWAYS);HBox MCheading=new HBox(10,MCclientPanelTitle,MCgap,MCclientDetailsToggle);MCheading.setAlignment(Pos.CENTER_LEFT);MCheading.getStyleClass().add("client-panel-heading");BorderPane MCright=new BorderPane();MCright.setTop(MCheading);MCright.setCenter(MCclientDocumentsPane);MCcontactsPage.setCenter(MCright);MCnewContact();
    }
    private void MCshowContacts(){try{MCcontactExtraChoice.getItems().setAll(MCstore.MCdefinitions().stream().filter(MCDefinition::MCbindToClient).toList());}catch(Exception MCe){MCerror(MCe);}MCrefreshContacts();MCpage("Clientes",MCcontactsPage);}
    private void MCrefreshContacts(){try{
        long MCselected=MCselectedContact==null?0:MCselectedContact.MCid();String MCq=MCcontactSearch.getText().toLowerCase(Locale.ROOT);
        List<MCContact> MCcontacts=MCdata.MCfindContacts(MCq);
        MCcontactList.getItems().setAll(MCcontacts);MCcontactList.getSelectionModel().select(MCcontacts.stream().filter(MCc->MCc.MCid()==MCselected).findFirst().orElse(MCcontacts.isEmpty()?null:MCcontacts.get(0)));
        if(MCcontacts.isEmpty())MCnewContact();
    }catch(Exception MCe){MCerror(MCe);}}
    private void MCnewContact(){MCselectedContact=null;MCcontactList.getSelectionModel().clearSelection();MCcontactKind.setValue("Persona");MCcontactGiven.clear();MCcontactFamily.clear();MCcontactDocType.setText("DNI");MCcontactNumber.clear();MCcontactAddress.clear();MCcontactRepresentatives.clear();MCcontactExtras.setText("{}");MCcontactExtraValue.clear();MCcontactExtraChoice.getSelectionModel().clearSelection();MCshowingClientDetails=true;MCshowClientPanel();}
    private void MCloadContact(MCContact MCc){if(MCc==null){MCnewContact();return;}MCselectedContact=MCc;MCcontactKind.setValue(MCc.MCkind());MCcontactGiven.setText(MCc.MCgiven());MCcontactFamily.setText(MCc.MCfamily());MCcontactDocType.setText(MCc.MCdocumentType());MCcontactNumber.setText(MCc.MCnumber());MCcontactAddress.setText(MCc.MCaddress());MCcontactRepresentatives.setText(MCc.MCrepresentatives());MCcontactExtras.setText(MCc.MCextrasJson());MCshowingClientDetails=false;MCshowClientPanel();MCrefreshClientDocuments();}
    private void MCsaveContact(){if(MCcontactGiven.getText().isBlank())throw new IllegalArgumentException("Escriba nombre o razón social.");try{if(!MCjson.readTree(MCcontactExtras.getText()).isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");MCContact MCc=MCdata.MCsaveContact(new MCContact(MCselectedContact==null?0:MCselectedContact.MCid(),MCcontactKind.getValue(),MCcontactGiven.getText().trim(),MCcontactFamily.getText().trim(),"Empresa".equals(MCcontactKind.getValue())?"RUC":MCcontactDocType.getText().trim(),MCcontactNumber.getText().trim(),MCcontactAddress.getText().trim(),MCcontactRepresentatives.getText().trim(),MCcontactExtras.getText()));MCrefreshContacts();MCcontactList.getSelectionModel().select(MCc);MCsaved("Registro local guardado");}catch(Exception MCe){MCerror(MCe);}}
    private void MCdeleteContact(){MCContact MCc=MCcontactList.getSelectionModel().getSelectedItem();if(MCc==null)return;try{MCdata.MCdeleteContact(MCc.MCid());MCnewContact();MCrefreshContacts();MCsaved("Registro eliminado");}catch(Exception MCe){MCerror(MCe);}}

    private static String MCclientName(MCContact MCclient){return MCclient==null?"":(MCclient.MCgiven()+" "+MCclient.MCfamily()).trim();}
    private void MCshowClientPanel(){
        if(MCclientDetailsPane==null)return;MCclientDetailsToggle.setText(MCshowingClientDetails?"Volver a documentos":"Ver detalles del cliente");MCclientPanelTitle.setText(MCshowingClientDetails?"Detalles del cliente":"Documentos del cliente");
        ((BorderPane)MCcontactsPage.getCenter()).setCenter(MCshowingClientDetails?MCclientDetailsPane:MCclientDocumentsPane);
    }
    private void MCrefreshClientDocuments(){try{long MCselected=MCclientDocumentList.getSelectionModel().getSelectedItem()==null?0:MCclientDocumentList.getSelectionModel().getSelectedItem().MCid();MCclientDocumentList.getItems().setAll(MCselectedContact==null?List.of():MCdata.MCclientDocuments(MCselectedContact.MCid()));MCclientDocumentList.getSelectionModel().select(MCclientDocumentList.getItems().stream().filter(MCd->MCd.MCid()==MCselected).findFirst().orElse(null));}catch(Exception MCe){MCerror(MCe);}}
    private static boolean MCisWordExtension(String MCextension){return Set.of("doc","docx","docm","dot","dotx","dotm").contains(MCextension.toLowerCase(Locale.ROOT));}
    private static String MCfileExtension(String MCname){int MCdot=MCname.lastIndexOf('.');return MCdot<0?"":MCname.substring(MCdot+1).toLowerCase(Locale.ROOT);}
    private File MCchooseClientFile(){FileChooser MCchooser=new FileChooser();MCchooser.setTitle("Seleccionar documento del cliente");MCchooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Todos los archivos","*"),new FileChooser.ExtensionFilter("Documentos de Word y PDF","*.doc","*.DOC","*.docx","*.DOCX","*.docm","*.DOCM","*.dot","*.DOT","*.dotx","*.DOTX","*.dotm","*.DOTM","*.pdf","*.PDF"));return MCchooser.showOpenDialog(MCstage);}
    private void MCuploadClientDocument(){if(MCselectedContact==null)throw new IllegalArgumentException("Seleccione o guarde primero un cliente.");File MCfile=MCchooseClientFile();if(MCfile!=null)MCclientAction(()->{MCdata.MCaddClientDocument(MCselectedContact.MCid(),MCfile.getName(),MCfile.getName(),MCfileExtension(MCfile.getName()),Files.readAllBytes(MCfile.toPath()));MCrefreshClientDocuments();MCsaved("Documento subido al cliente");});}
    private MCClientDocument MCselectedClientDocument(){MCClientDocument MCselected=MCclientDocumentList.getSelectionModel().getSelectedItem();if(MCselected==null)throw new IllegalArgumentException("Seleccione un documento del cliente.");return MCselected;}
    private void MCreplaceClientDocumentByUpload(){MCClientDocument MCselected=MCselectedClientDocument();File MCfile=MCchooseClientFile();if(MCfile!=null)MCclientAction(()->{MCdata.MCreplaceClientDocument(MCselected.MCid(),MCfile.getName(),MCfile.getName(),MCfileExtension(MCfile.getName()),Files.readAllBytes(MCfile.toPath()),"UPLOADED");MCrefreshClientDocuments();MCsaved("Documento del cliente reemplazado");});}
    private void MCopenClientDocument(){MCClientDocument MCselected=MCselectedClientDocument();MCclientAction(()->{Path MCdirectory=Files.createTempDirectory("studio-client-document-");Path MCpath=MCdirectory.resolve(Path.of(MCselected.MCfileName()).getFileName().toString());Files.write(MCpath,MCselected.MCcontentBytes());MCdirectory.toFile().deleteOnExit();MCpath.toFile().deleteOnExit();MCdocumentActions.MCopen(MCpath);});}
    private void MCdownloadClientDocument(){MCClientDocument MCselected=MCselectedClientDocument();FileChooser MCchooser=new FileChooser();MCchooser.setTitle("Descargar documento del cliente");MCchooser.setInitialFileName(Path.of(MCselected.MCfileName()).getFileName().toString());File MCtarget=MCchooser.showSaveDialog(MCstage);if(MCtarget!=null)MCclientAction(()->{Files.write(MCtarget.toPath(),MCselected.MCcontentBytes());MCsaved("Documento del cliente descargado");});}
    private void MCeditSelectedClientDocument(){MCClientDocument MCselected=MCselectedClientDocument();MCclientAction(()->MCopenClientDocumentForUpdate(MCselectedContact,MCselected));}
    private void MCopenClientDocumentForUpdate(MCContact MCcontact,MCClientDocument MCselected)throws Exception{
        if(!MCisWordExtension(MCselected.MCextension()))throw new IllegalArgumentException("Seleccione un documento de Word para actualizar en Generar documentos.");
        byte[] MCbytes=MCWordDocumentImporter.MCimportToDocx(MCselected.MCcontentBytes(),"document."+MCselected.MCextension());
        MCupdatingClientDocument=MCselected;MCupdatingClient=MCcontact;MChistoricalTemplate=null;MCTemplate MCtemporary=new MCTemplate(0,MCselected.MCtitle(),MCbytes,MCbytes);
        MCrefreshingGenerator=true;try{MCgenerateTemplate.getItems().setAll(MCtemporary);MCgenerateTemplate.getSelectionModel().select(MCtemporary);}finally{MCrefreshingGenerator=false;}
        MCbuildDynamicForm();MCpage("Generar documentos",MCgeneratorPage);
        for(var MCentry:MCinputs.entrySet()){String MCvalue=MCdata.MCcontactValue(MCcontact,MCDocumentEngine.MCbaseKey(MCentry.getKey()));if(!MCvalue.isBlank())MCsetInput(MCentry.getValue(),MCvalue);}
    }
    private void MCreplaceUpdatingClientDocument()throws Exception{
        if(MCupdatingClientDocument==null||MCgeneratedBytes==null)return;String MCname=MCupdatingClientDocument.MCfileName();int MCdot=MCname.lastIndexOf('.');MCname=(MCdot<0?MCname:MCname.substring(0,MCdot))+".docx";
        MCdata.MCreplaceClientDocument(MCupdatingClientDocument.MCid(),MCupdatingClientDocument.MCtitle(),MCname,"docx",MCgeneratedBytes,"GENERATED");MCrefreshClientDocuments();
    }
    private void MCupdateClientDocumentFromGenerator()throws Exception{MCmakePreview();if(MCgeneratedBytes==null)return;MCreplaceUpdatingClientDocument();MCsaved("Documento del cliente actualizado");}

    private void MCdefinitionChoice(ComboBox<MCDefinition> MCchoice){
        MCchoice.setCellFactory(MClist->new ListCell<>(){@Override protected void updateItem(MCDefinition MCdefinition,boolean MCempty){super.updateItem(MCdefinition,MCempty);setText(MCempty||MCdefinition==null?null:MCdefinition.MClabel()+" {{"+MCdefinition.MCkey()+"}}");}});
        MCchoice.setButtonCell(new ListCell<>(){@Override protected void updateItem(MCDefinition MCdefinition,boolean MCempty){super.updateItem(MCdefinition,MCempty);setText(MCempty||MCdefinition==null?null:MCdefinition.MClabel());}});
    }
    private void MCrefreshComplements(){try{
        List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();MCcomplementChoice.getItems().setAll(MCdefinitions.stream().filter(MCd->MCselectedDefinition==null||MCd.MCid()!=MCselectedDefinition.MCid()).toList());MCcomplementLinks.getChildren().clear();
        if(MCselectedDefinition==null){MCcomplementLinks.getChildren().add(MCdescription("Guarde la variable para vincular complementos."));return;}
        Map<Long,MCDefinition> MCbyId=new HashMap<>();MCdefinitions.forEach(MCd->MCbyId.put(MCd.MCid(),MCd));
        for(MCDocumentStore.MCComplement MClink:MCstore.MCcomplements(MCselectedDefinition.MCid())){MCDefinition MCtarget=MCbyId.get(MClink.MCtargetDefId());if(MCtarget==null)continue;Label MClabel=new Label(MCtarget.MClabel()+(MClink.MCbidirectional()?" ↔":" →"));MClabel.setWrapText(true);MClabel.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(MClabel,Priority.ALWAYS);Button MCremove=MCbutton("Quitar",()->{try{MCstore.MCdeleteComplement(MClink.MCid());MCrefreshComplements();}catch(Exception MCe){MCerror(MCe);}});MCremove.setTooltip(new Tooltip("Quitar el vínculo con "+MCtarget.MClabel()));HBox MCrow=new HBox(5,MClabel,MCremove);MCrow.setAlignment(Pos.CENTER_LEFT);MCcomplementLinks.getChildren().add(MCrow);}
    }catch(Exception MCe){MCerror(MCe);}}
    private void MClinkComplement(){if(MCselectedDefinition==null)throw new IllegalArgumentException("Guarde primero la variable.");MCDefinition MCtarget=MCcomplementChoice.getValue();if(MCtarget==null)throw new IllegalArgumentException("Seleccione la variable complemento.");try{
        List<MCDocumentStore.MCComplement> MCexisting=MCstore.MCcomplements(MCselectedDefinition.MCid());MCDocumentStore.MCComplement MClink=MCexisting.stream().filter(MCx->MCx.MCtargetDefId()==MCtarget.MCid()).findFirst().orElse(null);
        if(MClink!=null){for(var MCoriginal:MCstore.MCcomplements())if(MCoriginal.MCid()==MClink.MCid()){if(MCoriginal.MCsourceDefId()!=MCselectedDefinition.MCid()&&!MCcomplementBoth.isSelected()){MCstore.MCdeleteComplement(MCoriginal.MCid());MCstore.MCsaveComplement(MCselectedDefinition.MCid(),MCtarget.MCid(),false);}else MCstore.MCsaveComplement(MCoriginal.MCsourceDefId(),MCoriginal.MCtargetDefId(),MCcomplementBoth.isSelected());break;}}
        else MCstore.MCsaveComplement(MCselectedDefinition.MCid(),MCtarget.MCid(),MCcomplementBoth.isSelected());MCrefreshComplements();MCsaved("Complemento vinculado");
    }catch(Exception MCe){MCerror(MCe);}}
    private record MCContactExtra(@com.fasterxml.jackson.annotation.JsonProperty("key") String MCkey,@com.fasterxml.jackson.annotation.JsonProperty("value") String MCvalue){}
    private Node MCcontactVariables(){
        MCdefinitionChoice(MCcontactExtraChoice);MCcontactVariableTable.setEditable(true);MCcontactVariableTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);MCcontactVariableTable.setPrefHeight(180);MCcontactVariableTable.setMinHeight(110);
        TableColumn<MCContactExtra,String> MCvariable=new TableColumn<>("Variable");MCvariable.setCellValueFactory(MCc->new javafx.beans.property.ReadOnlyStringWrapper(MCextraLabel(MCc.getValue().MCkey())));
        TableColumn<MCContactExtra,String> MCvalue=new TableColumn<>("Valor");MCvalue.setCellValueFactory(MCc->new javafx.beans.property.ReadOnlyStringWrapper(MCc.getValue().MCvalue()));MCvalue.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());MCvalue.setOnEditCommit(MCe->MCsafe(()->MCwriteContactExtra(MCe.getRowValue().MCkey(),MCe.getNewValue(),false)));MCcontactVariableTable.getColumns().setAll(MCvariable,MCvalue);
        MCcontactVariableTable.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->{if(MCb==null)return;MCcontactExtraChoice.getSelectionModel().select(MCcontactExtraChoice.getItems().stream().filter(MCd->MCStudioData.MCvariableKey(MCd.MCkey()).equals(MCStudioData.MCvariableKey(MCb.MCkey()))).findFirst().orElse(null));MCcontactExtraValue.setText(MCb.MCvalue());});
        MCcontactExtraChoice.setPromptText("Variable del sistema");MCcontactExtraChoice.setMinWidth(0);MCcontactExtraChoice.setMaxWidth(Double.MAX_VALUE);MCcontactExtraValue.setPromptText("Valor asociado");HBox MCfields=new HBox(6,MCcontactExtraChoice,MCcontactExtraValue);HBox.setHgrow(MCcontactExtraChoice,Priority.ALWAYS);HBox.setHgrow(MCcontactExtraValue,Priority.ALWAYS);MCcontactExtraValue.setMinWidth(0);
        Button MCassign=MCbutton("Asignar / Actualizar",()->{MCDefinition MCdefinition=MCcontactExtraChoice.getValue();MCContactExtra MCselected=MCcontactVariableTable.getSelectionModel().getSelectedItem();if(MCdefinition==null&&MCselected==null)throw new IllegalArgumentException("Seleccione una variable.");MCwriteContactExtra(MCdefinition==null?MCselected.MCkey():MCdefinition.MCkey(),MCcontactExtraValue.getText(),false);});
        Button MCremove=MCbutton("Quitar",()->{MCContactExtra MCselected=MCcontactVariableTable.getSelectionModel().getSelectedItem();if(MCselected!=null)MCwriteContactExtra(MCselected.MCkey(),"",true);});
        MCcontactExtras.textProperty().addListener((MCo,MCa,MCb)->MCrefreshContactExtras());VBox MCbox=new VBox(8,new Label("Variables asociadas a este cliente"),MCcontactVariableTable,MCfields,new HBox(6,MCassign,MCremove));MCbox.getStyleClass().add("binding-card");return MCbox;
    }
    private String MCextraLabel(String MCkey){return MCcontactExtraChoice.getItems().stream().filter(MCd->MCStudioData.MCvariableKey(MCd.MCkey()).equals(MCStudioData.MCvariableKey(MCkey))).map(MCDefinition::MClabel).findFirst().orElse(MCkey);}
    private void MCrefreshContactExtras(){try{var MCextras=MCjson.readTree(MCcontactExtras.getText());if(MCextras==null||!MCextras.isObject())return;List<MCContactExtra> MCrows=new ArrayList<>();MCextras.fields().forEachRemaining(MCe->MCrows.add(new MCContactExtra(MCe.getKey(),MCe.getValue().isValueNode()?MCe.getValue().asText(""):MCe.getValue().toString())));MCcontactVariableTable.getItems().setAll(MCrows);}catch(Exception MCignored){}}
    private void MCwriteContactExtra(String MCkey,String MCvalue,boolean MCremove){try{
        if(!MCremove&&!MCContactReuse.MCclientKey(MCkey,MCstore.MCdefinitions()))throw new IllegalArgumentException("Las variables independientes se configuran desde Variables.");
        var MCparsed=MCjson.readTree(MCcontactExtras.getText());if(MCparsed==null||!MCparsed.isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");var MCextras=(com.fasterxml.jackson.databind.node.ObjectNode)MCparsed;
        String MCactual=MCkey;var MCnames=MCextras.fieldNames();while(MCnames.hasNext()){String MCname=MCnames.next();if(MCStudioData.MCvariableKey(MCname).equals(MCStudioData.MCvariableKey(MCkey))){MCactual=MCname;break;}}
        if(MCremove)MCextras.remove(MCactual);else MCextras.put(MCactual,MCvalue);MCcontactExtras.setText(MCextras.toString());if(MCselectedContact!=null)MCsaveContact();
    }catch(Exception MCe){MCerror(MCe);}}
    private void MCconfigureComplements(List<MCVariable> MCvariables)throws Exception{
        MCcomplementTargets.clear();MCactiveComplementDefinitions.clear();MCactiveComplementLinks.clear();List<MCDefinition> MCdefinitions=MCstore.MCdefinitions();
        Map<String,Map<Long,String>> MCscopes=new LinkedHashMap<>();Set<Long> MCids=new HashSet<>();for(MCVariable MCvariable:MCvariables){MCDefinition MCdefinition=MCdefinitionFor(MCvariable,MCdefinitions);if(MCdefinition!=null&&!MCdefinition.MCbindToClient())continue;long MCid=MCsafeDefinitionId(MCvariable);MCids.add(MCid);MCscopes.computeIfAbsent(MCDocumentEngine.MCpersonaKey(MCvariable.MCkey()),MCk->new LinkedHashMap<>()).put(MCid,MCvariable.MCkey());}
        MCdefinitions.stream().filter(MCd->MCids.contains(MCd.MCid())).forEach(MCactiveComplementDefinitions::add);
        for(var MClink:MCstore.MCcomplements()){
            boolean MCused=false;for(var MCkeys:MCscopes.values())if(MCkeys.containsKey(MClink.MCsourceDefId())&&MCkeys.containsKey(MClink.MCtargetDefId())){MCused=true;MCcomplementTargets.computeIfAbsent(MCkeys.get(MClink.MCsourceDefId()),MCk->new LinkedHashSet<>()).add(MCkeys.get(MClink.MCtargetDefId()));if(MClink.MCbidirectional())MCcomplementTargets.computeIfAbsent(MCkeys.get(MClink.MCtargetDefId()),MCk->new LinkedHashSet<>()).add(MCkeys.get(MClink.MCsourceDefId()));}if(MCused)MCactiveComplementLinks.add(MClink);
        }
        for(var MCentry:MCcomplementTargets.entrySet()){
            String MCkey=MCentry.getKey();Control MCinput=MCinputs.get(MCkey);if(!(MCinput instanceof TextInputControl MCfield))continue;ContextMenu MCpopup=new ContextMenu();MCpopup.setAutoHide(true);MCcomplementMenus.put(MCkey,MCpopup);PauseTransition MCdelay=new PauseTransition(Duration.millis(120));MCcomplementDelays.put(MCkey,MCdelay);
            MCdelay.setOnFinished(MCe->MCsafe(()->MCshowComplementSuggestions(MCkey,MCfield,MCpopup)));
            MCfield.textProperty().addListener((MCo,MCa,MCb)->{MCdelay.stop();if(MCapplyingApi||MCapplyingComplements)return;MCpopup.hide();if(!MCb.isBlank())MCdelay.playFromStart();});
            if(MCfield instanceof TextField MCtext)MCtext.addEventHandler(javafx.event.ActionEvent.ACTION,MCe->{MCdelay.stop();MCpopup.hide();MCsafe(()->MCcompleteExactComplement(MCkey));});
            MCfield.focusedProperty().addListener((MCo,MCa,MCfocused)->{if(!MCfocused){MCdelay.stop();Platform.runLater(()->{if(MCinputs.get(MCkey)==MCfield&&!MCpopup.isShowing())MCsafe(()->MCcompleteExactComplement(MCkey));});}});
        }
    }
    private void MCshowComplementSuggestions(String MCkey,TextInputControl MCfield,ContextMenu MCpopup){try{
        if(MCinputs.get(MCkey)!=MCfield||!MCfield.isFocused()||MCfield.getScene()==null||!MCfield.getScene().getWindow().isShowing())return;
        MCpopup.getItems().clear();for(MCStudioData.MCSuggestion MCsuggestion:MCdata.MCsuggestions(MCDocumentEngine.MCbaseKey(MCkey),MCcomplementTargets.getOrDefault(MCkey,Set.of()).stream().map(MCDocumentEngine::MCbaseKey).toList(),MCfield.getText(),false)){
            List<String> MCdetails=new ArrayList<>();for(var MCtarget:MCsuggestion.MCcomplements().entrySet()){String MCactual=MCactualInputKey(MCkey,MCtarget.getKey());if(MCactual!=null)MCdetails.add(MCformVariables.get(MCactual).MClabel()+": "+MCtarget.getValue());}
            MenuItem MCitem=new MenuItem(MCsuggestion.MCvalue()+" — "+String.join("; ",MCdetails));MCitem.setOnAction(MCe->{MCpopup.hide();MCapplyComplementSuggestion(MCkey,MCsuggestion,true);});MCpopup.getItems().add(MCitem);
        }
        if(!MCpopup.getItems().isEmpty())MCpopup.show(MCfield,Side.BOTTOM,0,0);
    }catch(Exception MCe){MCerror(MCe);}}
    private String MCactualInputKey(String MCsource,String MCnormalized){return MCinputs.keySet().stream().filter(MCk->MCDocumentEngine.MCpersonaKey(MCk).equals(MCDocumentEngine.MCpersonaKey(MCsource))&&MCStudioData.MCvariableKey(MCDocumentEngine.MCbaseKey(MCk)).equals(MCStudioData.MCvariableKey(MCnormalized))).findFirst().orElse(null);}
    private void MCcompleteExactComplement(String MCkey){try{
        Control MCinput=MCinputs.get(MCkey);if(MCinput==null||MCapplyingApi||MCapplyingComplements)return;Set<String> MCtargets=MCcomplementTargets.get(MCkey);if(MCtargets==null)return;List<MCStudioData.MCSuggestion> MCmatches=MCdata.MCsuggestions(MCDocumentEngine.MCbaseKey(MCkey),MCtargets.stream().map(MCDocumentEngine::MCbaseKey).toList(),MCreadInput(MCinput),true);if(!MCmatches.isEmpty())MCapplyComplementSuggestion(MCkey,MCmatches.get(0),false);
    }catch(Exception MCe){MCerror(MCe);}}
    private void MCapplyComplementSuggestion(String MCsourceKey,MCStudioData.MCSuggestion MCsuggestion,boolean MCselected){
        boolean MCpriorApi=MCapplyingApi;MCapplyingApi=true;MCapplyingComplements=true;try{
            if(MCselected)MCsetInput(MCinputs.get(MCsourceKey),MCsuggestion.MCvalue());
            for(var MCentry:MCsuggestion.MCcomplements().entrySet()){
                String MCtarget=MCactualInputKey(MCsourceKey,MCentry.getKey());if(MCtarget==null||!MCcomplementTargets.getOrDefault(MCsourceKey,Set.of()).contains(MCtarget))continue;
                if((MCcompletingMissingComplements||MCmanuallyEdited.contains(MCtarget))&&!MCreadInput(MCinputs.get(MCtarget)).isBlank())continue;MCsetInput(MCinputs.get(MCtarget),MCentry.getValue());
            }
        }finally{MCapplyingComplements=false;MCapplyingApi=MCpriorApi;}
    }
    private String MCeditorTokenKey(MCDefinition MCdefinition){MCDocumentStore.MCPersona MCperson=MCeditorPersona.getValue();return MCdefinition.MCeditorKey(MCperson==null?"":MCperson.MCpersonaKey());}
    private void MCrefreshEditorPersonas()throws Exception{
        String MCselected=MCeditorPersona.getValue()==null?"":MCeditorPersona.getValue().MCpersonaKey();if(MCcurrentTemplate==null||MCpersonaTemplateId!=MCcurrentTemplate.MCid())MCselected="";
        List<MCDocumentStore.MCPersona> MCpeople=new ArrayList<>();MCpeople.add(new MCDocumentStore.MCPersona(0,MCcurrentTemplate==null?0:MCcurrentTemplate.MCid(),"","General (sin persona)",-1));MCeditorPersonaNames.clear();
        if(MCcurrentTemplate!=null){MCpeople.addAll(MCstore.MCpersonas(MCcurrentTemplate.MCid()));Set<String> MCkeys=new HashSet<>();MCpeople.forEach(MCp->MCkeys.add(MCp.MCpersonaKey()));if(MCdocument!=null)for(String MCmarker:MCDocumentEngine.MCmarkers(MCdocument)){String MCkey=MCDocumentEngine.MCpersonaKey(MCmarker);if(!MCkey.isEmpty()&&MCkeys.add(MCkey))MCpeople.add(new MCDocumentStore.MCPersona(0,MCcurrentTemplate.MCid(),MCkey,MCkey.replace('_',' '),MCpeople.size()));}}
        MCpeople.forEach(MCp->{if(!MCp.MCpersonaKey().isEmpty())MCeditorPersonaNames.put(MCp.MCpersonaKey(),MCp.MCdisplayName());});MCeditorPersona.getItems().setAll(MCpeople);String MCchoice=MCselected;MCeditorPersona.getSelectionModel().select(MCpeople.stream().filter(MCp->MCp.MCpersonaKey().equals(MCchoice)).findFirst().orElse(MCpeople.get(0)));MCpersonaTemplateId=MCcurrentTemplate==null?0:MCcurrentTemplate.MCid();
    }
    private void MCaddEditorPersona(){if(MCcurrentTemplate==null)throw new IllegalArgumentException("Abra una plantilla primero.");try{
        int MCn=1;while(MCeditorPersonaNames.containsKey("Persona_"+MCn))MCn++;String MCkey="Persona_"+MCn;TextInputDialog MCdialog=new TextInputDialog("Persona "+MCn);MCdialog.setTitle("Agregar persona");MCdialog.setHeaderText("Nombre o rol de la persona");String MCname=MCdialog.showAndWait().orElse(null);if(MCname==null)return;
        MCDocumentStore.MCPersona MCperson=MCstore.MCaddPersona(MCcurrentTemplate.MCid(),MCkey,MCname,MCeditorPersonaNames.size());MCrefreshEditorPersonas();MCeditorPersona.getSelectionModel().select(MCperson);MCeditorScript("MCpersonas("+MCjsonString(MCeditorPersonaNames)+")");
    }catch(Exception MCe){MCerror(MCe);}}
    private void MCrenameEditorPersona(){MCDocumentStore.MCPersona MCperson=MCeditorPersona.getValue();if(MCperson==null||MCperson.MCpersonaKey().isEmpty())return;try{
        TextInputDialog MCdialog=new TextInputDialog(MCperson.MCdisplayName());MCdialog.setTitle("Renombrar persona");MCdialog.setHeaderText("Nombre o rol de la persona");String MCname=MCdialog.showAndWait().orElse(null);if(MCname==null)return;MCsyncEditor();if(MCperson.MCid()==0)MCstore.MCaddPersona(MCcurrentTemplate.MCid(),MCperson.MCpersonaKey(),MCname,MCperson.MCsortOrder());else MCstore.MCrenamePersona(MCperson.MCid(),MCname);MCrefreshEditorPersonas();MCrenderDocument();
    }catch(Exception MCe){MCerror(MCe);}}
    private void MCremoveEditorPersona(){MCDocumentStore.MCPersona MCperson=MCeditorPersona.getValue();if(MCperson==null||MCperson.MCpersonaKey().isEmpty())return;try{
        if(new Alert(Alert.AlertType.CONFIRMATION,"¿Quitar a '"+MCperson.MCdisplayName()+"'? Se quitarán sus variables del documento y se guardará la plantilla.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;
        MCeditorScript("MCremovePersona("+MCjsonString(MCperson.MCpersonaKey())+")");if(MCperson.MCid()!=0)MCstore.MCdeletePersona(MCperson.MCid());MCeditorPersona.getSelectionModel().selectFirst();MCeditorPersonaNames.remove(MCperson.MCpersonaKey());MCdirty=true;MCsaveTemplate();MCrefreshEditorPersonas();
    }catch(Exception MCe){MCerror(MCe);}}
    private void MCupdateAutomaticDatePreview(){String MCformat=MCcurrentDateFormat.getValue();if(MCformat==null)return;MCcurrentDatePreview.setText("Hoy: "+LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(MCformat,Locale.forLanguageTag("es-PE"))));}
    private Map<String,Map<String,String>> MCscopedValues(Map<String,String> MCvalues){Map<String,Map<String,String>> MCscopes=new LinkedHashMap<>();MCvalues.forEach((MCkey,MCvalue)->MCscopes.computeIfAbsent(MCDocumentEngine.MCpersonaKey(MCkey),MCk->new LinkedHashMap<>()).put(MCDocumentEngine.MCbaseKey(MCkey),MCvalue));return MCscopes;}
    private void MClearnScopedComplements(Map<String,String> MCvalues)throws Exception{
        Map<Long,MCDefinition> MCdefinitions=new HashMap<>();MCactiveComplementDefinitions.forEach(MCd->MCdefinitions.put(MCd.MCid(),MCd));
        for(var MCentry:MCscopedValues(MCContactReuse.MCclientValues(MCvalues,MCstore.MCdefinitions())).entrySet()){Map<String,String> MCscope=MCentry.getValue();if(MCentry.getKey().isEmpty()){MCdata.MClearnComplements(MCactiveComplementDefinitions,MCactiveComplementLinks,MCscope);continue;}
            for(var MClink:MCactiveComplementLinks){MCDefinition MCsource=MCdefinitions.get(MClink.MCsourceDefId()),MCtarget=MCdefinitions.get(MClink.MCtargetDefId());if(MCsource!=null&&MCtarget!=null)MCdata.MCrememberPair(MCsource.MCkey(),MCscope.get(MCsource.MCkey()),MCtarget.MCkey(),MCscope.get(MCtarget.MCkey()),MClink.MCbidirectional());}
        }
    }
    private void MCbuildApi(){
        MCapiPage=new BorderPane();MCapiPage.getStyleClass().add("page");MCapiToken.setPromptText("Clave nueva o de reemplazo");MCapiType.getItems().setAll("DNI","RUC");MCapiType.setValue("DNI");MCapiNumber.setPromptText("Número a consultar");MCcredentialProvider.getItems().setAll("Factiliza","APIsPERU");MCcredentialProvider.setValue("Factiliza");MCcredentialName.setPromptText("Nombre de la credencial");MCcredentialList.setPrefHeight(150);MCcredentialList.setMinHeight(110);MCtwoLineCells(MCcredentialList,MCApiCredentialStore.MCCredential::MCname,MCc->MCc.MCprovider()+" · "+MCc.MCstatus());MCcredentialList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCb)->MCloadCredential(MCb));MCcredentialStats.setWrapText(true);
        VBox MCmanager=new VBox(10,new Label("CREDENCIALES LOCALES"),MCcredentialList,MCbutton("+ Nueva credencial",this::MCnewCredential),MClabelled("Nombre",MCcredentialName),MClabelled("Proveedor",MCcredentialProvider),MClabelled("Clave API",MCapiToken),MCcredentialEnabled,MCbutton("Guardar credencial",this::MCsaveCredential),MCbutton("Eliminar credencial",this::MCdeleteCredential),MCcredentialStats);MCmanager.getStyleClass().add("side-panel");MCuniformButtons(MCmanager);ScrollPane MCmanagerScroll=MCscrolling(MCmanager);MCfixedWidth(MCmanagerScroll,380);MCapiPage.setLeft(MCmanagerScroll);BorderPane.setMargin(MCmanagerScroll,new Insets(0,14,0,0));
        GridPane MCquery=MCtwoColumns();MCquery.add(MClabelled("Documento",MCapiType),0,0);MCquery.add(MClabelled("Número",MCapiNumber),1,0);
        FlowPane MCactions=new FlowPane(8,6,MCbutton("Consultar",()->MCrunApiQuery(MCapiType.getValue(),MCapiNumber.getText(),null,this::MCshowApiResult)),MCbutton("Probar seleccionada",()->{MCApiCredentialStore.MCCredential MCc=MCcredentialList.getSelectionModel().getSelectedItem();if(MCc==null)throw new IllegalArgumentException("Seleccione una credencial.");MCrunApiQuery(MCapiType.getValue(),MCapiNumber.getText(),MCc.MCid(),this::MCshowApiResult);}));
        MCapiState.setWrapText(true);MCapiSaved.setWrapText(true);MCapiSaved.getStyleClass().add("automatic-save");VBox MCcontent=new VBox(14,new Label("CONSULTA DNI / RUC"),MCquery,MCactions,MCapiState,MCapiSaved,MCapiResultBox);MCcontent.getStyleClass().add("catalog-form");MCapiPage.setCenter(MCscrolling(MCcontent));MCrefreshCredentialList();MCnewCredential();
    }
    private void MCshowApi(){MCrefreshCredentialList();MCpage("Conexiones API",MCapiPage);MCapiState.setText(MCcredentialList.getItems().isEmpty()?"Sin credenciales · El sistema funciona sin conexión":"Consultas opcionales · Selección automática de proveedor");}
    private void MCrefreshCredentialList(){try{long MCid=MCcredentialList.getSelectionModel().getSelectedItem()==null?0:MCcredentialList.getSelectionModel().getSelectedItem().MCid();MCcredentialList.getItems().setAll(MCcredentials.MCcredentials());if(MCid>0)MCcredentialList.getSelectionModel().select(MCcredentialList.getItems().stream().filter(MCc->MCc.MCid()==MCid).findFirst().orElse(null));}catch(Exception MCe){MCerror(MCe);}}
    private void MCnewCredential(){MCcredentialList.getSelectionModel().clearSelection();MCcredentialName.clear();MCcredentialProvider.setDisable(false);MCcredentialProvider.setValue("Factiliza");MCapiToken.clear();MCcredentialEnabled.setSelected(true);MCcredentialStats.setText("Sin credencial seleccionada");}
    private void MCloadCredential(MCApiCredentialStore.MCCredential MCc){if(MCc==null)return;MCcredentialName.setText(MCc.MCname());MCcredentialProvider.setValue(MCc.MCprovider());MCcredentialProvider.setDisable(true);MCapiToken.clear();MCcredentialEnabled.setSelected(MCc.MCenabled());MCcredentialStats.setText("Registrada: "+MCc.MCcreatedAt()+"\nMes actual: "+MCc.MCrequests()+" solicitudes · "+MCc.MCsuccesses()+" correctas · "+MCc.MCfailures()+" fallidas\nÚltimo uso: "+(MCc.MClastUsed()==null?"ninguno":MCc.MClastUsed())+"\nEstado: "+MCc.MCstatus());}
    private void MCsaveCredential(){try{MCApiCredentialStore.MCCredential MCcurrent=MCcredentialList.getSelectionModel().getSelectedItem();if(MCcurrent==null){MCApiCredentialStore.MCCredential MCadded=MCcredentials.MCadd(MCcredentialName.getText(),MCcredentialProvider.getValue(),MCapiToken.getText());MCcredentials.MCenabled(MCadded.MCid(),MCcredentialEnabled.isSelected());MCrefreshCredentialList();MCcredentialList.getSelectionModel().select(MCcredentialList.getItems().stream().filter(MCc->MCc.MCid()==MCadded.MCid()).findFirst().orElse(null));}else{MCcredentials.MCrename(MCcurrent.MCid(),MCcredentialName.getText());MCcredentials.MCenabled(MCcurrent.MCid(),MCcredentialEnabled.isSelected());if(!MCapiToken.getText().isBlank())MCcredentials.MCreplace(MCcurrent.MCid(),MCapiToken.getText());MCrefreshCredentialList();}MCapiToken.clear();MCsaved("Credencial guardada localmente");}catch(Exception MCe){MCerror(MCe);}}
    private void MCdeleteCredential(){MCApiCredentialStore.MCCredential MCc=MCcredentialList.getSelectionModel().getSelectedItem();if(MCc==null)return;if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la credencial '"+MCc.MCname()+"'? Los documentos y datos locales permanecerán intactos.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;try{MCcredentials.MCdelete(MCc.MCid());MCrefreshCredentialList();MCnewCredential();MCsaved("Credencial eliminada; evidencia no sensible conservada");}catch(Exception MCe){MCerror(MCe);}}
    private void MCrunApiQuery(String MCtype,String MCnumber,Long MCcredentialId,java.util.function.Consumer<MCLookupRouter.MCResult> MConSuccess){
        if(MCapiQueryRunning){MCsaved("Ya hay una consulta en curso.");return;}MCapiQueryRunning=true;MCapiSaved.setText("");MCapiState.setText("Consultando…");
        Task<MCLookupRouter.MCResult> MCtask=new Task<>(){@Override protected MCLookupRouter.MCResult call()throws Exception{
            MCLookupRouter.MCResult MCresult=MCcredentialId==null?MClookupRouter.MCquery(MCtype,MCnumber.trim()):MClookupRouter.MCquerySpecific(MCtype,MCnumber.trim(),MCcredentialId);
            if(MCresult.MCusable())MCdata.MCupsertContact(MCContactReuse.MCfromApi(MCresult));return MCresult;
        }};
        MCtask.setOnSucceeded(MCe->{MCapiQueryRunning=false;MCLookupRouter.MCResult MCresult=MCtask.getValue();MCapiState.setText(MCresult.MCusable()?"Respondió "+MCresult.MCprovider()+" · "+(MCresult.MCstatus()==MCLookupRouter.MCStatus.PARTIAL?"datos parciales":"consulta correcta"):MCresult.MCerror());MCrefreshCredentialList();if(MCresult.MCusable()){MCapiSaved.setText("✓ Guardado automáticamente en Clientes");MCrefreshContacts();MConSuccess.accept(MCresult);}});
        MCtask.setOnFailed(MCe->{MCapiQueryRunning=false;MCapiState.setText("Consulta no completada: "+MCtask.getException().getMessage());MCrefreshCredentialList();});Thread MCthread=new Thread(MCtask,"consulta-documental");MCthread.setDaemon(true);MCthread.start();
    }
    private static Map<String,String> MCnormalizedApiValues(MCLookupRouter.MCResult MCresult){Map<String,String> MCvalues=new LinkedHashMap<>(MCresult.MCvalues());if("DNI".equals(MCresult.MCservice()))MCApiFieldCatalog.MCnormalizePersonName(MCvalues);return MCvalues;}
    private void MCshowApiResult(MCLookupRouter.MCResult MCresult){
        MCapiResultBox.getChildren().clear();GridPane MCgrid=MCtwoColumns();int MCindex=0;
        for(MCApiFieldCatalog.MCField MCitem:MCApiFieldCatalog.MCfields(MCresult.MCservice())){
            String MCvalue=MCnormalizedApiValues(MCresult).getOrDefault(MCitem.MCkey(),"");Label MCdataLabel=new Label(MCvalue.isBlank()?"No disponible":MCvalue);MCdataLabel.setWrapText(true);MCdataLabel.setMinWidth(0);VBox MCcell=MClabelled(MCitem.MClabel(),MCdataLabel);MCcell.getStyleClass().add("result-field");MCgrid.add(MCcell,MCindex%2,MCindex/2);MCindex++;
        }
        MCapiResultBox.getChildren().addAll(MCdescription("Proveedor: "+MCresult.MCprovider()),MCgrid);MCapiSaved.setText("✓ Guardado automáticamente en Clientes");MCsaved("Consulta completada · Cliente guardado automáticamente");
    }

    private void MCrefreshMappings(){MCTemplate MCt=MCmapTemplate.getValue();try{if(MCt==null){MCmapSource.getItems().clear();MCmapTarget.getItems().clear();MCmappingList.getItems().clear();return;}List<MCVariable> MCvars=MCstore.MCvariables(MCt.MCid());MCmapSource.getItems().setAll(MCvars.stream().filter(MCv->MCv.MCtype().equals("DNI")||MCv.MCtype().equals("RUC")).toList());MCmapTarget.getItems().setAll(MCvars);MCmappingList.getItems().setAll(MCdata.MCmappings(MCt.MCid()));}catch(Exception MCe){MCerror(MCe);}}
    private void MCrefreshApiFieldChoices(){MCVariable MCsource=MCmapSource.getValue();if(MCsource==null)return;if(MCsource.MCtype().equals("DNI"))MCmapApiField.getItems().setAll("numero","nombres","apellido_paterno","apellido_materno","nombre_completo","direccion","direccion_completa","departamento","provincia","distrito");else MCmapApiField.getItems().setAll("numero","nombre_o_razon_social","direccion","direccion_completa","tipo_contribuyente","estado","condicion","departamento","provincia","distrito");}
    private void MCsaveMapping(){MCTemplate MCt=MCmapTemplate.getValue();MCVariable MCsource=MCmapSource.getValue(),MCtarget=MCmapTarget.getValue();String MCfield=MCmapApiField.getValue();if(MCt==null||MCsource==null||MCtarget==null||MCfield==null||MCfield.isBlank())throw new IllegalArgumentException("Seleccione plantilla, origen, dato y destino.");try{MCdata.MCsaveMapping(new MCMapping(0,MCt.MCid(),MCsource.MCkey(),MCfield.trim(),MCtarget.MCkey()));MCrefreshMappings();MCsaved("Correspondencia guardada");}catch(Exception MCe){MCerror(MCe);}}
    private void MCdeleteMapping(){MCMapping MCm=MCmappingList.getSelectionModel().getSelectedItem();if(MCm==null)return;try{MCdata.MCdeleteMapping(MCm.MCid());MCrefreshMappings();MCsaved("Correspondencia eliminada");}catch(Exception MCe){MCerror(MCe);}}
    private long MCsafeDefinitionId(MCVariable MCvariable){try{return MCdefinitionId(MCvariable);}catch(Exception MCe){throw new IllegalStateException(MCe);}}
    private long MCdefinitionId(MCVariable MCvariable)throws Exception{return MCvariable.MCid()<0?-MCvariable.MCid():MCstore.MCdefinitionId(MCvariable.MCid());}
    private String MCqueryService(long MCtemplateId,MCVariable MCvariable)throws Exception{
        MCDefinition MCdefinition=MCdefinitionFor(MCvariable,MCstore.MCdefinitions());if(MCdefinition!=null&&!MCdefinition.MCbindToClient())return null;
        for(MCApiContext MCcontext:MCdata.MCapiContexts(MCtemplateId))if(MCcontext.MCvariableKey().equals(MCvariable.MCkey()))return MCcontext.MCsource()?MCcontext.MCservice():null;
        for(MCContactBinding MCbinding:MCdata.MCcontactBindings(MCtemplateId))if(MCbinding.MCvariableKey().equals(MCvariable.MCkey()))return "number".equals(MCbinding.MCfield())?("Empresa".equals(MCbinding.MCkind())?"RUC":"DNI"):null;
        if(List.of("DNI","RUC").contains(MCvariable.MCtype()))return MCvariable.MCtype();
        long MCid=MCdefinitionId(MCvariable);MCApiBinding MCbinding=MCid>0?MCdata.MCapiBinding(MCid):null;
        if(MCbinding!=null)return "numero".equals(MCApiFieldCatalog.MCcanonical(MCbinding.MCservice(),MCbinding.MCpath()))?MCbinding.MCservice():null;
        return MCContactReuse.MCidentifierService(MCvariable);
    }
    private void MClookupForField(MCTemplate MCtemplate,MCVariable MCvariable,TextField MCfield,String MCservice){
        PauseTransition MCdelay=MClookupDelays.get(MCvariable.MCkey());if(MCdelay!=null)MCdelay.stop();
        String MCnumber=MCfield.getText().trim();if(!MCvalidQueryNumber(MCservice,MCnumber))throw new IllegalArgumentException("Número de documento inválido.");
        try{if(MCloadLocalForField(MCtemplate,MCvariable,MCfield,MCservice))return;}catch(Exception MCe){MCerror(MCe);return;}
        MCrunApiQuery(MCservice,MCnumber,null,MCresult->{
            if(MCgenerateTemplate.getValue()==null||MCgenerateTemplate.getValue().MCid()!=MCtemplate.MCid()||MCinputs.get(MCvariable.MCkey())!=MCfield||!MCfield.getText().trim().equals(MCnumber))return;
            int MCcount=MCapplyApiBindings(MCtemplate.MCid(),MCvariable.MCkey(),MCresult);
            try{MCcount+=MCapplyContactFields(MCtemplate.MCid(),MCvariable.MCkey(),MCdata.MCmatchingContact("RUC".equals(MCservice)?"Empresa":"Persona",MCservice,MCnumber),MCservice);}catch(Exception MCe){MCerror(MCe);}
            MCsaved("Respondió "+MCresult.MCprovider()+" · "+MCcount+" campos completados · Cliente guardado automáticamente");
        });
    }
    private static boolean MCvalidQueryNumber(String MCservice,String MCnumber){return MCnumber!=null&&MCnumber.trim().matches("RUC".equals(MCservice)?"\\d{11}":"\\d{8}");}
    private boolean MCloadLocalForField(MCTemplate MCtemplate,MCVariable MCvariable,TextField MCfield,String MCservice)throws Exception{
        MCContact MClocal=MCdata.MCmatchingContact("RUC".equals(MCservice)?"Empresa":"Persona",MCservice,MCfield.getText());if(MClocal==null)return false;
        MCresetContactEdits(MCtemplate,MCvariable,MCservice);
        Map<String,String> MCvalues=MCContactReuse.MCapiValues(MClocal);MCLookupRouter.MCResult MCresult=new MCLookupRouter.MCResult(MCservice,MCfield.getText().trim(),"Clientes locales",MCvalues,MCvalues.keySet(),Set.of(),MCLookupRouter.MCStatus.SUCCESS,"",MCvalues);
        MCapplyApiBindings(MCtemplate.MCid(),MCvariable.MCkey(),MCresult);MCapplyContactFields(MCtemplate.MCid(),MCvariable.MCkey(),MClocal,MCservice);MCsaved("Datos cargados desde Clientes locales");return true;
    }
    private void MCresetContactEdits(MCTemplate MCtemplate,MCVariable MCsource,String MCservice)throws Exception{
        List<MCContactBinding> MCbindings=MCcontactFormBindings(MCtemplate.MCid(),new ArrayList<>(MCformVariables.values()));String MCgroup=MCcontextGroup(MCdata.MCapiContexts(MCtemplate.MCid()),MCbindings,MCsource.MCkey(),MCservice);String MCkind="RUC".equals(MCservice)?"Empresa":"Persona";
        long MCsources=MCbindings.stream().filter(MCb->"number".equals(MCb.MCfield())&&MCb.MCkind().equals(MCkind)).count();
        for(MCContactBinding MCbinding:MCbindings)if(!MCbinding.MCvariableKey().equals(MCsource.MCkey())&&MCbinding.MCkind().equals(MCkind)&&MCsameQueryGroup(MCgroup,MCbinding.MCgroupKey(),MCsources,MCsource.MCkey(),MCbinding.MCvariableKey()))MCmanuallyEdited.remove(MCbinding.MCvariableKey());
    }
    private int MCapplyContactFields(long MCtemplateId,String MCsourceKey,MCContact MCcontact,String MCservice)throws Exception{
        if(MCcontact==null)return 0;List<MCContactBinding> MCbindings=MCcontactFormBindings(MCtemplateId,new ArrayList<>(MCformVariables.values()));List<MCApiContext> MCcontexts=MCdata.MCapiContexts(MCtemplateId);
        String MCsourceGroup=MCcontextGroup(MCcontexts,MCbindings,MCsourceKey,MCservice);long MCcount=MCbindings.stream().filter(MCb->"number".equals(MCb.MCfield())&&MCb.MCkind().equals(MCcontact.MCkind())).count();int MCcompleted=0;
        for(MCContactBinding MCbinding:MCbindings){if(!MCbinding.MCkind().equals(MCcontact.MCkind())||!MCsameQueryGroup(MCsourceGroup,MCbinding.MCgroupKey(),MCcount,MCsourceKey,MCbinding.MCvariableKey())||MCmanuallyEdited.contains(MCbinding.MCvariableKey()))continue;
            String MCvalue=MCContactReuse.MCvaluesFor(MCcontact,List.of(MCbinding)).get(MCbinding.MCvariableKey());Control MCtarget=MCinputs.get(MCbinding.MCvariableKey());if(MCtarget!=null&&MCvalue!=null&&!MCvalue.isBlank()){MCapplyingApi=true;try{MCsetInput(MCtarget,MCvalue);MCcompleted++;}finally{MCapplyingApi=false;}}
        }return MCcompleted;
    }
    private int MCapplyApiBindings(long MCtemplateId,String MCsourceKey,MCLookupRouter.MCResult MCresult){int MCcompleted=0;try{
        List<MCContactBinding> MCcontacts=MCdata.MCcontactBindings(MCtemplateId);List<MCApiContext> MCcontexts=MCdata.MCapiContexts(MCtemplateId);String MCsourceGroup=MCcontextGroup(MCcontexts,MCcontacts,MCsourceKey,MCresult.MCservice());
        long MCsources=MCformVariables.values().stream().filter(MCv->{try{return MCresult.MCservice().equals(MCqueryService(MCtemplateId,MCv));}catch(Exception MCe){return false;}}).count();
        for(var MCentry:MCformVariables.entrySet()){
            MCVariable MCvariable=MCentry.getValue();long MCid=MCdefinitionId(MCvariable);MCDefinition MCdefinition=MCdefinitionFor(MCvariable,MCstore.MCdefinitions());if(MCdefinition!=null&&!MCdefinition.MCbindToClient())continue;MCApiBinding MCbinding=MCid>0?MCdata.MCapiBinding(MCid):null;
            if(MCbinding==null||!MCbinding.MCservice().equals(MCresult.MCservice()))continue;
            String MCtargetGroup=MCcontextGroup(MCcontexts,MCcontacts,MCvariable.MCkey(),MCresult.MCservice());
            if(!MCsameQueryGroup(MCsourceGroup,MCtargetGroup,MCsources,MCsourceKey,MCvariable.MCkey()))continue;
            String MCnormalized=MCApiFieldCatalog.MCcanonical(MCbinding.MCservice(),MCbinding.MCpath());String MCvalue=MCnormalized.startsWith("legacy:")?("Clientes locales".equals(MCresult.MCprovider())?MCresult.MCraw().get(MCbinding.MCpath()):MCApiFieldCatalog.MClegacyValue(MCresult.MCprovider(),MCbinding.MCpath(),MCresult.MCraw())):MCnormalizedApiValues(MCresult).get(MCnormalized);
            if(MCvalue!=null&&!MCvalue.isBlank()&&!MCmanuallyEdited.contains(MCvariable.MCkey())){Control MCtarget=MCinputs.get(MCvariable.MCkey());if(MCtarget!=null){MCapplyingApi=true;try{MCsetInput(MCtarget,MCvalue);MCcompleted++;}finally{MCapplyingApi=false;}}}
        }
        for(MCMapping MCmapping:MCdata.MCmappings(MCtemplateId))if(MCContactReuse.MCclientKey(MCmapping.MCtargetKey(),MCstore.MCdefinitions())&&MCmapping.MCsourceKey().equals(MCsourceKey)&&!MCmanuallyEdited.contains(MCmapping.MCtargetKey())&&MCsameQueryGroup(MCsourceGroup,MCcontextGroup(MCcontexts,MCcontacts,MCmapping.MCtargetKey(),MCresult.MCservice()),MCsources,MCsourceKey,MCmapping.MCtargetKey())){String MCcanonical=MCApiFieldCatalog.MCcanonical(MCresult.MCservice(),MCmapping.MCapiField());String MCvalue="nombre_completo".equals(MCcanonical)?MCnormalizedApiValues(MCresult).get(MCcanonical):MCresult.MCraw().getOrDefault(MCmapping.MCapiField(),MCresult.MCraw().getOrDefault("data."+MCmapping.MCapiField(),MCnormalizedApiValues(MCresult).get(MCcanonical)));Control MCtarget=MCinputs.get(MCmapping.MCtargetKey());if(MCvalue!=null&&!MCvalue.isBlank()&&MCtarget!=null){MCapplyingApi=true;try{MCsetInput(MCtarget,MCvalue);MCcompleted++;}finally{MCapplyingApi=false;}}}
    }catch(Exception MCe){MCerror(MCe);}return MCcompleted;}
    static String MCcontextGroup(List<MCApiContext> MCcontexts,List<MCContactBinding> MCcontacts,String MCkey,String MCservice){if(!MCDocumentEngine.MCpersonaKey(MCkey).isEmpty())return "persona:"+MCDocumentEngine.MCpersonaKey(MCkey);for(MCApiContext MCc:MCcontexts)if(MCc.MCvariableKey().equals(MCkey)&&MCc.MCservice().equals(MCservice))return MCc.MCgroupKey();for(MCContactBinding MCb:MCcontacts)if(MCb.MCvariableKey().equals(MCkey))return MCb.MCgroupKey();return null;}
    static boolean MCsameQueryGroup(String MCsource,String MCtarget,long MCsourceCount,String MCsourceKey,String MCtargetKey){if(!MCDocumentEngine.MCpersonaKey(MCsourceKey).equals(MCDocumentEngine.MCpersonaKey(MCtargetKey)))return false;if(MCsource!=null)return MCsource.equals(MCtarget)||MCsourceKey.equals(MCtargetKey);return MCsourceCount<=1||MCsourceKey.equals(MCtargetKey);}
    private static String MCreadInput(Control MCinput){if(MCinput instanceof DatePicker MCpicker)return MCpicker.getValue()==null?"":MCpicker.getValue().toString();if(MCinput instanceof TextArea MCarea)return MCarea.getText();return ((TextField)MCinput).getText();}
    private static void MCsetInput(Control MCinput,String MCvalue){if(MCinput instanceof DatePicker MCpicker){try{MCpicker.setValue(LocalDate.parse(MCvalue));}catch(Exception MCfirst){try{MCpicker.setValue(LocalDate.parse(MCvalue,java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")));}catch(Exception MCignored){}}}else if(MCinput instanceof TextArea MCarea)MCarea.setText(MCvalue);else ((TextField)MCinput).setText(MCvalue);}

    private void MCbuildHistory(){
        MChistoryPage=new BorderPane();MChistoryPage.getStyleClass().add("page");MChistorySearch.setPromptText("Buscar título o fecha");MChistorySearch.setMinWidth(0);MChistorySearch.textProperty().addListener((MCo,MCa,MCb)->MCrefreshHistory());MChistoryDate.setPromptText("Fecha");MChistoryDate.setMinWidth(0);MChistoryDate.setPrefWidth(120);MChistoryDate.valueProperty().addListener((MCo,MCa,MCb)->MCrefreshHistory());
        Button MCclear=MCbutton("×",()->{MChistorySearch.clear();MChistoryDate.setValue(null);});MCclear.setTooltip(new Tooltip("Quitar filtros"));HBox MCfilter=new HBox(6,MChistorySearch,MChistoryDate,MCclear);HBox.setHgrow(MChistorySearch,Priority.ALWAYS);
        MCtwoLineCells(MChistoryList,MCHistory::MCtitle,MCh->MCh.MCgeneratedAt()+" · Versión "+MCh.MCtemplateVersion());VBox MClist=new VBox(10,MCfilter,MChistoryList);MClist.getStyleClass().add("side-panel");MCfixedWidth(MClist,380);VBox.setVgrow(MChistoryList,Priority.ALWAYS);MChistoryPage.setLeft(MClist);BorderPane.setMargin(MClist,new Insets(0,14,0,0));
        Label MCsummary=MCdescription("Seleccione un registro para ver sus detalles y recuperar sus archivos.");MChistoryList.getSelectionModel().selectedItemProperty().addListener((MCo,MCa,MCh)->MCsummary.setText(MCh==null?"Seleccione un registro.":MCh.MCtitle()+"\nGenerado: "+MCh.MCgeneratedAt()+"\nVersión de plantilla: "+MCh.MCtemplateVersion()+"\nDOCX: "+(MCh.MCdocxPath()==null?"Conservado en el historial":MCh.MCdocxPath())+"\nPDF: "+(MCh.MCpdfPath()==null?"Disponible para exportar":MCh.MCpdfPath())));
        GridPane MCactions=MCtwoColumns();List<Button> MCbuttons=List.of(MCbutton("Abrir DOCX",this::MCopenHistory),MCbutton("Duplicar registro",this::MCduplicateHistory),MCbutton("Editar y regenerar",this::MCeditHistory),MCbutton("Exportar DOCX",()->MCexportHistory(false)),MCbutton("Exportar PDF",()->MCexportHistory(true)),MCbutton("Eliminar registro",this::MCdeleteHistory));int MCi=0;for(Button MCb:MCbuttons){MCb.setWrapText(true);MCb.setMaxWidth(Double.MAX_VALUE);MCactions.add(MCb,MCi%2,MCi/2);MCi++;}
        VBox MCdetail=new VBox(18,new Label("DOCUMENTO SELECCIONADO"),MCsummary,MCactions);MCdetail.getStyleClass().add("catalog-form");MChistoryPage.setCenter(MCscrolling(MCdetail));
    }
    private void MCshowHistory(){MCrefreshHistory();MCpage("Historial",MChistoryPage);}
    private void MCrefreshHistory(){try{String MCq=MChistorySearch.getText().toLowerCase();String MCdate=MChistoryDate.getValue()==null?null:MChistoryDate.getValue().toString();MChistoryList.getItems().setAll(MCdata.MChistory().stream().filter(MCh->(MCh.MCtitle()+" "+MCh.MCgeneratedAt()).toLowerCase().contains(MCq)).filter(MCh->MCdate==null||MCh.MCgeneratedAt().startsWith(MCdate)).toList());}catch(Exception MCe){MCerror(MCe);}}
    private MCHistory MCselectedHistory(){MCHistory MCh=MChistoryList.getSelectionModel().getSelectedItem();if(MCh==null)throw new IllegalArgumentException("Seleccione un documento del historial.");return MCh;}
    private void MCopenHistory(){try{MCHistory MCh=MCselectedHistory();Path MCdir=MCstore.MCdirectory().resolve("history-files");Files.createDirectories(MCdir);Path MCpath=MCdir.resolve("documento-"+MCh.MCid()+".docx");Files.write(MCpath,MCh.MCdocx());if(!Desktop.isDesktopSupported())throw new IllegalStateException("No hay aplicación asociada a DOCX en este equipo.");Desktop.getDesktop().open(MCpath.toFile());MCsaved("Abriendo DOCX conservado en el historial");}catch(Exception MCe){MCerror(MCe);}}
    private void MCduplicateHistory(){try{MCHistory MCh=MCselectedHistory();MCdata.MCsaveHistory(new MCHistory(0,MCh.MCtitle()+" (copia)",MCh.MCtemplateId(),MCh.MCtemplateVersion(),null,MCh.MCvaluesJson(),null,null,MCh.MCdocx(),MCh.MCpdf()));MCrefreshHistory();MCsaved("Registro duplicado");}catch(Exception MCe){MCerror(MCe);}}
    private void MCeditHistory(){try{
        MCHistory MChistory=MCselectedHistory();if(MChistory.MCtemplateId()==null)throw new IllegalStateException("La plantilla original ya no existe. Puede abrir o exportar el documento conservado.");MCTemplate MCtemplate=MCstore.MCtemplates().stream().filter(MCt->MCt.MCid()==MChistory.MCtemplateId()).findFirst().orElseThrow(()->new IllegalStateException("La plantilla original ya no existe."));
        MCshowGenerator();MCTemplateGroup MCgroup=null;for(MCTemplateGroup MCcandidate:MCgenerateGroup.getItems())if(MCstore.MCinGroup(MCtemplate.MCid(),MCcandidate.MCid())){MCgroup=MCcandidate;break;}
        if(MCgroup==null)throw new IllegalStateException("Asocie la plantilla a un grupo para generar documentos.");
        MCgenerateSearch.clear();MCgenerateGroup.getSelectionModel().select(MCgroup);MCrefreshKinds();MCgenerateTemplate.getSelectionModel().select(MCgenerateTemplate.getItems().stream().filter(MCt->MCt.MCid()==MCtemplate.MCid()).findFirst().orElseThrow());MCbuildDynamicForm();
        MChistoricalTemplate=MCdata.MCrevision(MChistory.MCtemplateId(),MChistory.MCtemplateVersion());if(MChistoricalTemplate==null)throw new IllegalStateException("No se conserva la versión de la plantilla.");
        var MCvalues=MCjson.readTree(MChistory.MCvaluesJson());MCapplyingApi=true;try{for(var MCentry:MCinputs.entrySet())MCsetInput(MCentry.getValue(),MCvalues.path(MCentry.getKey()).asText(""));}finally{MCapplyingApi=false;}
        MCsaved("Valores recuperados; edite y genere un nuevo documento");
    }catch(Exception MCe){MCerror(MCe);}}

    private void MCexportHistory(boolean MCpdf){try{MCHistory MCh=MCselectedHistory();byte[] MCcontent=MCpdf?(MCh.MCpdf()==null?MCDocxPdfExporter.MCrender(MCh.MCdocx()):MCh.MCpdf()):MCh.MCdocx();FileChooser MCfc=new FileChooser();MCfc.setInitialFileName(MCh.MCtitle().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","")+(MCpdf?".pdf":".docx"));MCfc.getExtensionFilters().add(new FileChooser.ExtensionFilter(MCpdf?"PDF":"Word DOCX",MCpdf?"*.pdf":"*.docx"));File MCfile=MCfc.showSaveDialog(MCstage);if(MCfile==null)return;Files.write(MCfile.toPath(),MCcontent);MCdata.MCupdateHistoryFiles(MCh.MCid(),MCpdf?null:MCfile.toString(),MCpdf?MCfile.toString():null,MCpdf?MCcontent:null);MCrefreshHistory();MCsaved("Archivo exportado nuevamente");}catch(Exception MCe){MCerror(MCe);}}
    private void MCdeleteHistory(){try{MCHistory MCh=MCselectedHistory();MCdata.MCdeleteHistory(MCh.MCid());MCrefreshHistory();MCsaved("Registro eliminado; los archivos exportados permanecen en su ubicación");}catch(Exception MCe){MCerror(MCe);}}

    private void MCbuildSettings(){
        MCsettingsPage=new BorderPane();MCsettingsPage.getStyleClass().add("page");
        MCimportConflict.getItems().setAll("Conservar existentes","Reemplazar existentes");MCimportConflict.getSelectionModel().selectFirst();
        GridPane MCimportRow=MCtwoColumns();Button MCimportButton=MCbutton("Importar archivo (.zip)",this::MCimportConfig);GridPane.setValignment(MCimportButton,VPos.BOTTOM);MCimportRow.add(MCimportButton,0,0);MCimportRow.add(MClabelled("Resolución de duplicados",MCimportConflict),1,0);
        VBox MCimporting=new VBox(12,new Label("Importar"),MCdescription("El archivo puede contener plantillas, clientes o ambos."),MCimportRow);MCimporting.getStyleClass().add("transfer-card");
        MCexportScope.getItems().setAll("Solo una plantilla","Todas las plantillas de un grupo","Todas las plantillas");MCexportScope.getSelectionModel().selectFirst();
        MCexportTemplate.setPromptText("Seleccione una plantilla");MCexportGroup.setPromptText("Seleccione un grupo");
        MCexportTemplate.disableProperty().bind(MCexportScope.valueProperty().isNotEqualTo("Solo una plantilla"));MCexportGroup.disableProperty().bind(MCexportScope.valueProperty().isNotEqualTo("Todas las plantillas de un grupo"));
        GridPane MCscopeFields=MCtwoColumns();MCscopeFields.add(MClabelled("Plantilla",MCexportTemplate),0,0);MCscopeFields.add(MClabelled("Grupo",MCexportGroup),1,0);
        VBox MCtemplates=new VBox(12,new Label("Exportar Plantillas"),MCdescription("Incluye los documentos y sus variables, grupos y asociaciones. Las credenciales permanecen en este equipo."),MClabelled("Alcance de exportación",MCexportScope),MCscopeFields,MCbutton("Exportar plantillas (.zip)",this::MCexportConfig));MCtemplates.getStyleClass().add("transfer-card");
        VBox MCclients=new VBox(12,new Label("Exportar Clientes"),MCdescription("Exporta todas las personas y empresas, incluidos sus datos adicionales."),MCbutton("Exportar clientes (.zip)",this::MCexportClients));MCclients.getStyleClass().add("transfer-card");
        MCgodMode.setWrapText(true);try{MCgodMode.setSelected(MCstore.MCgodModeEnabled());}catch(Exception MCe){MCerror(MCe);}
        MCgodMode.setOnAction(MCe->MCclientAction(()->{MCstore.MCsetGodModeEnabled(MCgodMode.isSelected());MCupdateGenerationModes();}));
        VBox MCgodCard=new VBox(12,new Label("Modo Dios"),MCgodMode,MCdescription("Habilita la pestaña Generar en masa dentro de Generar documentos para preparar y descargar varios documentos a la vez."));MCgodCard.getStyleClass().add("god-mode-card");
        MCupdateGenerationModes();
        VBox MCcontent=new VBox(14,MCgodCard,MCimporting,MCtemplates,MCclients);MCcontent.setFillWidth(true);MCsettingsPage.setCenter(MCscrolling(MCcontent));
    }
    private void MCshowSettings(){try{
        MCTemplate MCselected=MCexportTemplate.getValue();MCTemplateGroup MCgroup=MCexportGroup.getValue();MCexportTemplate.getItems().setAll(MCstore.MCtemplates());MCexportGroup.getItems().setAll(MCstore.MCgroups());
        MCexportTemplate.getSelectionModel().select(MCexportTemplate.getItems().stream().filter(MCt->MCselected!=null&&MCt.MCid()==MCselected.MCid()).findFirst().orElse(null));
        MCexportGroup.getSelectionModel().select(MCexportGroup.getItems().stream().filter(MCg->MCgroup!=null&&MCg.MCid()==MCgroup.MCid()).findFirst().orElse(null));
        if(MCexportTemplate.getValue()==null)MCexportTemplate.getSelectionModel().selectFirst();if(MCexportGroup.getValue()==null)MCexportGroup.getSelectionModel().selectFirst();
    }catch(Exception MCe){MCerror(MCe);}MCpage("Configuración y respaldos",MCsettingsPage);}
    private void MCexportConfig(){try{
        Collection<Long> MCids=null;
        if("Solo una plantilla".equals(MCexportScope.getValue())){if(MCexportTemplate.getValue()==null)throw new IllegalArgumentException("Seleccione una plantilla.");MCids=List.of(MCexportTemplate.getValue().MCid());}
        else if("Todas las plantillas de un grupo".equals(MCexportScope.getValue())){if(MCexportGroup.getValue()==null)throw new IllegalArgumentException("Seleccione un grupo.");List<Long> MCselected=new ArrayList<>();for(MCTemplate MCt:MCstore.MCtemplates())if(MCstore.MCinGroup(MCt.MCid(),MCexportGroup.getValue().MCid()))MCselected.add(MCt.MCid());if(MCselected.isEmpty())throw new IllegalArgumentException("El grupo no contiene plantillas.");MCids=MCselected;}
        File MCfile=MCzipChooser("plantillas-documentales.zip").showSaveDialog(MCstage);if(MCfile==null)return;MCtransfer.MCexportConfiguration(MCfile.toPath(),MCids);MCsaved("Plantillas exportadas con todos sus datos relacionados");
    }catch(Exception MCe){MCerror(MCe);}}
    private void MCimportConfig(){File MCfile=MCzipChooser(null).showOpenDialog(MCstage);if(MCfile==null)return;try{
        MCStudioTransfer.MCImportResult MCresult=MCtransfer.MCimportConfiguration(MCfile.toPath(),"Reemplazar existentes".equals(MCimportConflict.getValue())?MCStudioTransfer.MCConflict.REPLACE:MCStudioTransfer.MCConflict.KEEP);
        MCrefreshTemplates();MCrefreshLibrary();MCrefreshKinds();MCrefreshDefinitions();MCrefreshContacts();MCshowSettings();
        MCsaved("Importadas "+MCresult.MCtemplates()+" plantillas, "+MCresult.MCkinds()+" tipos y "+MCresult.MCclients()+" clientes · Coincidencias: "+MCresult.MCconflicts());
    }catch(Exception MCe){MCerror(MCe);}}
    private static FileChooser MCzipChooser(String MCname){FileChooser MCchooser=new FileChooser();MCchooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo ZIP","*.zip"));if(MCname!=null)MCchooser.setInitialFileName(MCname);return MCchooser;}
    private void MCexportClients(){File MCfile=MCzipChooser("clientes.zip").showSaveDialog(MCstage);if(MCfile==null)return;try{MCtransfer.MCexportClients(MCfile.toPath());MCsaved("Clientes exportados con sus datos adicionales");}catch(Exception MCe){MCerror(MCe);}}
}
