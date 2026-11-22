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

/** @author MC */
public final class MCStudioWindow {
    private static final Pattern mcTOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)?)}}");
    private static final List<String> mcTYPES = MCVariableInput.mcTYPES;
    private final Stage mcStage;
    private final MCDocumentStore mcStore;
    private final MCStudioData mcData;
    private final MCApiCredentialStore mcCredentials;
    private final MCLookupRouter mcLookupRouter;
    private final MCStudioTransfer mcTransfer;
    private final MCDocumentActions mcDocumentActions;
    private final ObjectMapper mcJson=new ObjectMapper();
    private final BorderPane mcRoot = new BorderPane();
    private final StackPane mcWorkspace = new StackPane();
    private final Label mcStatus = new Label("Datos locales · Sin conexión requerida");
    private final ToggleGroup mcMenu = new ToggleGroup();
    private final List<ToggleButton> mcNavigation = new ArrayList<>();
    private final List<Node> mcMenuTexts = new ArrayList<>();
    private VBox mcSidebar;
    private VBox mcBrandTexts;
    private Region mcIndicator;
    private Button mcThemeButton, mcCollapseButton;
    private ToggleButton mcGenerateNavigation;
    private boolean mcDarkTheme, mcCollapsed;
    private final ComboBox<MCTemplate> mcTemplateChoice = new ComboBox<>();
    private BorderPane mcLibraryPage;
    private final ListView<MCTemplate> mcLibraryList=new ListView<>();
    private final ListView<MCTemplateGroup> mcGroupList=new ListView<>();
    private final TextField mcLibrarySearch=new TextField();
    private final ComboBox<String> mcLibrarySort=new ComboBox<>();
    private final Label mcLibraryGroups=new Label("Sin grupos");
    private Button mcAddToGroup,mcRemoveFromGroup;
    private Map<Long,List<Long>> mcLibraryMemberships=Map.of();
    private Map<Long,String> mcLibraryGroupNames=Map.of(),mcLibraryModifiedDates=Map.of();
    private boolean mcRefreshingLibrary;
    private final ListView<MCDefinition> mcVariableList = new ListView<>();
    private final VBox mcPaper = new VBox(10);
    private final ScrollPane mcDocumentScroll = new ScrollPane();
    private final WebView mcRichEditor = new WebView();
    private MCContinuousDocxEditor mcRichModel;
    private boolean mcEditorReady;
    private final MCEditorBridge mcEditorBridge=new MCEditorBridge();
    private boolean mcUpdatingToolbarFromSelection;
    private final ComboBox<Integer> mcFontSizeChoice=new ComboBox<>();
    private final ColorPicker mcEditorTextColor=new ColorPicker(Color.BLACK),mcEditorHighlightColor=new ColorPicker(Color.YELLOW);
    private final Map<String,Button> mcSelectionFormatButtons=new LinkedHashMap<>();
    private double mcEditorLeft=72,mcEditorRight=72,mcEditorTop=72,mcEditorBottom=72;
    private final Label mcPageIndicator = new Label("Página 1");
    private final ComboBox<String> mcZoomChoice = new ComboBox<>();
    private final TextField mcPaletteSearch = new TextField();
    private VBox mcPalettePanel;
    private Button mcPaletteToggle;
    private TabPane mcEditorTabs;
    private HBox mcTableTools;
    private HBox mcImageTools;
    private final Scale mcPaperScale = new Scale(1,1);
    private double mcPageWidth = 794, mcPageHeight = 1123;
    private String mcZoomMode = "Ancho";
    private double mcEditorZoom=1;
    private final Label mcZoomPercent=new Label("100%"),mcEditorSaveStatus=new Label("Sin plantilla");
    private ToggleButton mcGuidesView,mcRulersView,mcVariablesView;
    private boolean mcReadingPageSize;
    private final TextField mcTemplateName = new TextField();
    private BorderPane mcDefinitionsPage;
    private final ListView<MCDefinition> mcDefinitionList=new ListView<>();
    private final TextField mcDefinitionSearch=new TextField(),mcDefinitionKey=new TextField(),mcDefinitionLabel=new TextField(),mcDefinitionDefault=new TextField();
    private final ComboBox<String> mcDefinitionType=new ComboBox<>();
    private final CheckBox mcDefinitionApiEnabled=new CheckBox("Vincular con API");
    private final ComboBox<String> mcDefinitionApiService=new ComboBox<>(),mcDefinitionApiField=new ComboBox<>();
    private final Label mcDefinitionApiPath=new Label("Consulta automática cuando haya conexión · Ingreso manual disponible");
    private VBox mcDefinitionApiOptions;
    private MCDefinition mcSelectedDefinition;
    private final ComboBox<MCDocumentStore.MCPersona> mcEditorPersona=new ComboBox<>();
    private final Map<String,String> mcEditorPersonaNames=new LinkedHashMap<>();
    private long mcPersonaTemplateId;
    private final ToggleGroup mcVariableScope=new ToggleGroup();
    private final RadioButton mcClientScope=new RadioButton("Amarrada a cliente / persona"),mcGlobalScope=new RadioButton("Variable independiente (global / sin cliente)");
    private final CheckBox mcAutoCurrentDate=new CheckBox("Amarrar esta variable a la fecha actual del sistema");
    private final ComboBox<String> mcCurrentDateFormat=new ComboBox<>();
    private final Label mcCurrentDatePreview=new Label();
    private final Map<String,MCDefinition> mcFormDefinitions=new HashMap<>();
    private final ComboBox<MCDefinition> mcComplementChoice=new ComboBox<>(),mcContactExtraChoice=new ComboBox<>();
    private final CheckBox mcComplementBoth=new CheckBox("Autocompletar en ambos sentidos (bidireccional)");
    private final VBox mcComplementLinks=new VBox(6);
    private final TableView<MCContactExtra> mcContactVariableTable=new TableView<>();
    private final TextField mcContactExtraValue=new TextField();
    private final Map<String,Set<String>> mcComplementTargets=new LinkedHashMap<>();
    private final Map<String,ContextMenu> mcComplementMenus=new HashMap<>();
    private final Map<String,PauseTransition> mcComplementDelays=new HashMap<>();
    private final List<MCDefinition> mcActiveComplementDefinitions=new ArrayList<>();
    private final List<MCDocumentStore.MCComplement> mcActiveComplementLinks=new ArrayList<>();
    private boolean mcApplyingComplements,mcCompletingMissingComplements;
    private final ComboBox<String> mcFontChoice = new ComboBox<>();
    private final ComboBox<String> mcPaperChoice = new ComboBox<>();
    private MCTemplate mcCurrentTemplate;
    private boolean mcUpdatingTemplates;
    private XWPFDocument mcDocument;
    private boolean mcDirty;
    private XWPFRun mcSelectedRun;
    private XWPFParagraph mcSelectedParagraph;
    private TextField mcSelectedText;
    private XWPFRun mcDragSourceRun;
    private String mcDragSourceToken;
    private int mcDragSourceOffset;
    private XWPFRun mcDragTargetRun;
    private int mcDragTargetOffset;

    private final ListView<MCKind> mcKindList = new ListView<>();
    private final TextField mcKindSearch = new TextField(), mcKindTitle = new TextField(), mcKindCategory = new TextField();
    private final TextArea mcKindDescription = new TextArea();
    private final ComboBox<MCTemplate> mcKindTemplate = new ComboBox<>();
    private final CheckBox mcKindActive = new CheckBox("Activo");
    private final ListView<MCVariable> mcFieldList = new ListView<>();
    private final ComboBox<MCVariable> mcAddFieldChoice = new ComboBox<>();
    private final CheckBox mcFieldRequired=new CheckBox("Obligatorio en este documento");
    private final Map<String,Boolean> mcKindRequiredChoices=new HashMap<>();
    private final TextField mcFieldDefault=new TextField();
    private final Map<String,String> mcKindDefaultChoices=new HashMap<>();
    private boolean mcLoadingFieldRequired;
    private final TextField mcBindingGroup=new TextField();
    private final ComboBox<String> mcBindingKind=new ComboBox<>(),mcBindingField=new ComboBox<>();
    private final ComboBox<MCVariable> mcBindingVariable=new ComboBox<>();
    private final ListView<MCContactBinding> mcBindingList=new ListView<>();
    private MCKind mcCurrentKind;

    private final ComboBox<MCTemplate> mcGenerateTemplate = new ComboBox<>();
    private boolean mcRefreshingGenerator;
    private final Map<String,PauseTransition> mcLookupDelays=new HashMap<>();
    private final ComboBox<MCTemplateGroup> mcGenerateGroup=new ComboBox<>();
    private final TextField mcGenerateSearch=new TextField();
    private final VBox mcGeneratedForm = new VBox(15);
    private final Map<String, Control> mcInputs = new LinkedHashMap<>();
    private final Map<String,MCVariable> mcFormVariables=new LinkedHashMap<>();
    private final Set<String> mcManuallyEdited=new HashSet<>();
    private boolean mcApplyingApi;
    private final Map<String,String> mcLocalValueOverrides=new HashMap<>();
    private final WebView mcPreview = new WebView();
    private XWPFDocument mcGenerationDocument;
    private MCContinuousDocxEditor mcGenerationModel;
    private boolean mcGenerationReady;
    private Map<String,String> mcGenerationDisplayValues=Map.of();
    private final Map<String,String> mcGenerationValueAliases=new LinkedHashMap<>();
    private final MCGenerationBridge mcGenerationBridge=new MCGenerationBridge();
    private MCClientDocument mcUpdatingClientDocument;
    private MCContact mcUpdatingClient;
    private final Label mcUpdateDocumentBanner=new Label();
    private final Button mcUpdateDocumentButton=new Button("Actualizar documento");
    private final ListView<MCClientDocument> mcClientDocumentList=new ListView<>();
    private final BorderPane mcClientDocumentsPane=new BorderPane();
    private BorderPane mcClientDetailsPane;
    private final Button mcClientDetailsToggle=new Button("Ver detalles del cliente");
    private final Label mcClientPanelTitle=new Label("Documentos del cliente");
    private boolean mcShowingClientDetails;
    private boolean mcGeneratorVariablesCollapsed;
    private VBox mcGeneratorVariablesPanel;
    private GridPane mcGeneratorColumns;
    private ColumnConstraints mcGeneratorFormColumn,mcGeneratorDocumentColumn;
    private final Button mcGeneratorVariablesToggle=new Button();
    private byte[] mcGeneratedBytes;
    private byte[] mcGeneratedPdf;
    private Map<String,String> mcGeneratedValues=Map.of();
    private long mcGeneratedHistoryId;
    private byte[] mcHistoricalTemplate;
    private String mcContactSaveSummary="";
    private BorderPane mcContactsPage,mcHistoryPage,mcApiPage,mcSettingsPage;
    private final ListView<MCContact> mcContactList=new ListView<>();
    private final TextField mcContactSearch=new TextField(),mcContactGiven=new TextField(),mcContactFamily=new TextField(),mcContactDocType=new TextField(),mcContactNumber=new TextField(),mcContactAddress=new TextField();
    private final TextArea mcContactRepresentatives=new TextArea(),mcContactExtras=new TextArea();
    private final ComboBox<String> mcContactKind=new ComboBox<>();
    private MCContact mcSelectedContact;
    private final PasswordField mcApiToken=new PasswordField();
    private final ListView<MCApiCredentialStore.MCCredential> mcCredentialList=new ListView<>();
    private final TextField mcCredentialName=new TextField();
    private final ComboBox<String> mcCredentialProvider=new ComboBox<>();
    private final CheckBox mcCredentialEnabled=new CheckBox("Habilitada");
    private final Label mcCredentialStats=new Label("Sin credencial seleccionada");
    private final ComboBox<String> mcApiType=new ComboBox<>();
    private final TextField mcApiNumber=new TextField();
    private final Label mcApiState=new Label("Sin consulta");
    private boolean mcApiQueryRunning;
    private final VBox mcApiResultBox=new VBox(8);
    private final ComboBox<MCTemplate> mcMapTemplate=new ComboBox<>();
    private final ComboBox<MCVariable> mcMapSource=new ComboBox<>(),mcMapTarget=new ComboBox<>();
    private final ComboBox<String> mcMapApiField=new ComboBox<>();
    private final ListView<MCMapping> mcMappingList=new ListView<>();
    private final ListView<MCHistory> mcHistoryList=new ListView<>();
    private final TextField mcHistorySearch=new TextField();
    private final DatePicker mcHistoryDate=new DatePicker();
    private final ComboBox<String> mcImportConflict=new ComboBox<>(),mcExportScope=new ComboBox<>();
    private final ComboBox<MCTemplate> mcExportTemplate=new ComboBox<>();
    private final ComboBox<MCTemplateGroup> mcExportGroup=new ComboBox<>();
    private final Label mcApiSaved=new Label();

    public MCStudioWindow(Stage mcStage, MCDocumentStore mcStore) throws Exception {
        this(mcStage,mcStore,null);
    }
    MCStudioWindow(Stage mcStage, MCDocumentStore mcStore,MCLookupRouter mcRouter) throws Exception {
        this(mcStage,mcStore,mcRouter,new MCSystemDocumentActions());
    }
    MCStudioWindow(Stage mcStage,MCDocumentStore mcStore,MCLookupRouter mcRouter,MCDocumentActions mcDocumentActions)throws Exception{
        this.mcStage=mcStage; this.mcStore=mcStore;
        this.mcDocumentActions=mcDocumentActions;
        mcData=new MCStudioData(mcStore.mcDirectory());mcCredentials=new MCApiCredentialStore(mcStore.mcDirectory());mcLookupRouter=mcRouter==null?new MCLookupRouter(mcCredentials,List.of(new MCFactilizaProvider(),new MCApisPeruProvider())):mcRouter;mcTransfer=new MCStudioTransfer(mcStore,mcData);
        mcBuildShell();
        mcBuildDefinitions();
        mcBuildEditor();
        mcBuildLibrary();
        mcBuildGenerator();
        mcBuildContacts();mcBuildHistory();mcBuildApi();mcBuildSettings();
        mcMenu.selectToggle(mcGenerateNavigation);
        Platform.runLater(() -> mcMoveIndicator(mcGenerateNavigation));
    }
    public Parent mcRoot() { return mcRoot; }

    /** @author MC */
    interface MCDocumentActions {
        void mcOpen(Path mcDocument)throws Exception;
        void mcPrint(Path mcDocument)throws Exception;
    }
    /** @author MC */
    private static final class MCSystemDocumentActions implements MCDocumentActions {
        @Override public void mcOpen(Path mcDocument)throws Exception{
            try{if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.OPEN)){Desktop.getDesktop().open(mcDocument.toFile());return;}}catch(IOException|UnsupportedOperationException mcIgnored){}
            String mcOs=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
            ProcessBuilder mcCommand;
            if(mcOs.contains("win"))mcCommand=new ProcessBuilder("cmd","/c","start","",mcDocument.toAbsolutePath().toString());
            else if(mcOs.contains("linux"))mcCommand=new ProcessBuilder("xdg-open",mcDocument.toAbsolutePath().toString());
            else throw new IOException("No hay un procesador de texto disponible para abrir el DOCX.");
            Process mcProcess=mcCommand.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(mcProcess.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)&&mcProcess.exitValue()!=0)throw new IOException("No se pudo abrir el DOCX en el procesador de texto del sistema.");
        }
        @Override public void mcPrint(Path mcDocument)throws Exception{
            try{if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.PRINT)){Desktop.getDesktop().print(mcDocument.toFile());return;}}catch(IOException|UnsupportedOperationException mcIgnored){}
            String mcOs=System.getProperty("os.name","").toLowerCase(Locale.ROOT);ProcessBuilder mcCommand;
            if(mcOs.contains("linux"))mcCommand=new ProcessBuilder("lp",mcDocument.toAbsolutePath().toString());
            else if(mcOs.contains("win")){
                mcCommand=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command","Start-Process -FilePath $env:STUDIO_PRINT_FILE -Verb Print -ErrorAction Stop");
                mcCommand.environment().put("STUDIO_PRINT_FILE",mcDocument.toAbsolutePath().toString());
            }else throw new IOException("La impresión directa no está disponible en este sistema.");
            Process mcProcess=mcCommand.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!mcProcess.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){mcProcess.destroy();throw new IOException("El sistema de impresión no respondió.");}
            if(mcProcess.exitValue()!=0)throw new IOException("No se pudo enviar el PDF a la impresora predeterminada. Revise la impresora del sistema.");
        }
    }

    private void mcBuildShell() {
        mcRoot.getStyleClass().add("app");
        mcSidebar=new VBox(6);mcSidebar.getStyleClass().add("sidebar");mcSidebar.setPrefWidth(242);mcSidebar.setMinWidth(242);
        ImageView mcLogoImage=new ImageView(new Image(MCStudioWindow.class.getResourceAsStream("/img/icon-128.png")));mcLogoImage.setFitWidth(34);mcLogoImage.setFitHeight(34);mcLogoImage.setPreserveRatio(true);mcLogoImage.setSmooth(true);
        StackPane mcLogo=new StackPane(mcLogoImage);mcLogo.getStyleClass().add("marca-logo");
        mcBrandTexts=new VBox(1,new Label("Gestion"),new Label("Panel administrativo"));
        mcBrandTexts.getChildren().get(0).getStyleClass().add("marca-titulo");mcBrandTexts.getChildren().get(1).getStyleClass().add("marca-subtitulo");
        HBox mcBrand=new HBox(12,mcLogo,mcBrandTexts);mcBrand.getStyleClass().add("marca");mcSidebar.getChildren().add(mcBrand);
        mcIndicator=new Region();mcIndicator.getStyleClass().add("nav-indicador");mcIndicator.setPrefHeight(42);mcIndicator.setMaxHeight(42);
        VBox mcItems=new VBox(3);
        mcAddSection(mcItems,"GENERAL");mcGenerateNavigation=mcAddNavigation(mcItems,"Generar documentos","fth-file-text",this::mcShowGenerator);
        mcAddSection(mcItems,"DOCUMENTOS");mcAddNavigation(mcItems,"Variables","fth-tag",this::mcShowDefinitions);mcAddNavigation(mcItems,"Plantillas","fth-folder",this::mcShowLibrary);mcAddNavigation(mcItems,"Editor de plantillas","fth-edit-3",this::mcShowEditor);
        mcAddSection(mcItems,"REGISTROS");mcAddNavigation(mcItems,"Clientes","fth-users",this::mcShowContacts);mcAddNavigation(mcItems,"Historial","fth-clock",this::mcShowHistory);
        mcAddSection(mcItems,"SISTEMA");mcAddNavigation(mcItems,"Conexiones API","fth-link",this::mcShowApi);mcAddNavigation(mcItems,"Configuración y respaldos","fth-settings",this::mcShowSettings);
        StackPane mcMenuPane=new StackPane(mcIndicator,mcItems);mcMenuPane.setAlignment(Pos.TOP_LEFT);mcSidebar.getChildren().add(mcMenuPane);
        Region mcSpacer=new Region();VBox.setVgrow(mcSpacer,Priority.ALWAYS);mcSidebar.getChildren().add(mcSpacer);
        Separator mcSeparator=new Separator();mcSeparator.getStyleClass().add("nav-separador");mcSidebar.getChildren().add(mcSeparator);
        mcThemeButton=mcSidebarAction("Modo oscuro","fth-moon",this::mcChangeTheme);
        mcCollapseButton=mcSidebarAction("Contraer menú","fth-chevrons-left",this::mcCollapseMenu);
        mcSidebar.getChildren().addAll(mcThemeButton,mcCollapseButton);
        mcMenu.selectedToggleProperty().addListener((mcO,mcPrevious,mcCurrent)->{
            if(mcCurrent==null){mcMenu.selectToggle(mcPrevious);return;}
            Runnable mcAction=(Runnable)((ToggleButton)mcCurrent).getUserData();
            mcAction.run();mcMoveIndicator(mcCurrent);
        });
        mcWorkspace.getStyleClass().add("contenido");mcRoot.setLeft(mcSidebar);mcRoot.setCenter(mcWorkspace);
    }
    private void mcAddSection(VBox mcItems,String mcTitle){Label mcSection=new Label(mcTitle);mcSection.getStyleClass().add("nav-seccion");mcItems.getChildren().add(mcSection);mcMenuTexts.add(mcSection);}
    private ToggleButton mcAddNavigation(VBox mcItems,String mcTitle,String mcIcon,Runnable mcAction){ToggleButton mcButton=new ToggleButton(mcTitle,new FontIcon(mcIcon));mcButton.setToggleGroup(mcMenu);mcButton.setMaxWidth(Double.MAX_VALUE);mcButton.getStyleClass().add("nav-item");mcButton.setUserData(mcAction);Tooltip mcTip=new Tooltip(mcTitle);mcTip.setShowDelay(Duration.millis(300));mcButton.setTooltip(mcTip);mcNavigation.add(mcButton);mcItems.getChildren().add(mcButton);return mcButton;}
    private Button mcSidebarAction(String mcTitle,String mcIcon,Runnable mcAction){Button mcButton=new Button(mcTitle,new FontIcon(mcIcon));mcButton.getStyleClass().add("nav-item");mcButton.setMaxWidth(Double.MAX_VALUE);mcButton.setOnAction(mcE->mcSafe(mcAction));return mcButton;}
    private void mcMoveIndicator(javafx.scene.control.Toggle mcToggle){if(mcToggle==null)return;Node mcButton=(Node)mcToggle;double mcY=mcButton.getBoundsInParent().getMinY();mcIndicator.setPrefHeight(mcButton.getBoundsInParent().getHeight());new Timeline(new KeyFrame(Duration.millis(280),new KeyValue(mcIndicator.translateYProperty(),mcY,Interpolator.SPLINE(0.4,0,0.2,1)))).play();}
    private void mcChangeTheme(){mcDarkTheme=!mcDarkTheme;Application.setUserAgentStylesheet(mcDarkTheme?new PrimerDark().getUserAgentStylesheet():new PrimerLight().getUserAgentStylesheet());if(mcDarkTheme)mcRoot.getStyleClass().add("dark");else mcRoot.getStyleClass().remove("dark");((FontIcon)mcThemeButton.getGraphic()).setIconCode(mcDarkTheme?Feather.SUN:Feather.MOON);mcThemeButton.setText(mcDarkTheme?"Modo claro":"Modo oscuro");Animations.fadeIn(mcWorkspace,Duration.millis(300)).play();}
    private void mcCollapseMenu(){mcCollapsed=!mcCollapsed;double mcWidth=mcCollapsed?76:242;Timeline mcTransition=new Timeline(new KeyFrame(Duration.millis(260),new KeyValue(mcSidebar.prefWidthProperty(),mcWidth,Interpolator.SPLINE(0.4,0,0.2,1)),new KeyValue(mcSidebar.minWidthProperty(),mcWidth,Interpolator.SPLINE(0.4,0,0.2,1))));List<Node> mcTexts=new ArrayList<>(mcMenuTexts);mcTexts.add(mcBrandTexts);List<ButtonBase> mcButtons=new ArrayList<>(mcNavigation);mcButtons.add(mcThemeButton);mcButtons.add(mcCollapseButton);if(mcCollapsed){mcTexts.forEach(mcN->{mcN.setVisible(false);mcN.setManaged(false);});mcButtons.forEach(mcB->mcB.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));mcTransition.play();}else{mcTransition.setOnFinished(mcE->{mcTexts.forEach(mcN->{mcN.setVisible(true);mcN.setManaged(true);});mcButtons.forEach(mcB->mcB.setContentDisplay(ContentDisplay.LEFT));ParallelTransition mcFade=new ParallelTransition();mcTexts.forEach(mcN->mcFade.getChildren().add(Animations.fadeIn(mcN,Duration.millis(200))));mcFade.play();Platform.runLater(()->mcMoveIndicator(mcMenu.getSelectedToggle()));});mcTransition.play();}((FontIcon)mcCollapseButton.getGraphic()).setIconCode(mcCollapsed?Feather.CHEVRONS_RIGHT:Feather.CHEVRONS_LEFT);Platform.runLater(()->mcMoveIndicator(mcMenu.getSelectedToggle()));}
    private void mcPage(String mcTitle,Node mcContent){
        Label mcHeading=new Label(mcTitle);mcHeading.getStyleClass().add("pagina-titulo");
        if(!mcStatus.getStyleClass().contains("pagina-subtitulo"))mcStatus.getStyleClass().add("pagina-subtitulo");
        mcStatus.setText(switch(mcTitle){
            case "Generar documentos"->"Complete los datos y prepare su documento con una plantilla.";
            case "Variables"->"Defina campos reutilizables y vincule los datos que necesita.";
            case "Plantillas"->"Organice sus documentos en grupos y acceda al editor.";
            case "Editor de plantillas"->"Edite el documento e inserte sus variables.";
            case "Clientes"->"Consulte y actualice personas y empresas guardadas en este equipo.";
            case "Historial"->"Recupere documentos generados y reutilice sus datos.";
            case "Conexiones API"->"Administre credenciales y consulte DNI o RUC con guardado automático.";
            default->"Importe archivos y exporte sus plantillas o clientes.";
        });
        mcStatus.setWrapText(true);VBox mcHeaderText=new VBox(4,mcHeading,mcStatus);HBox mcHeader=new HBox(mcHeaderText);mcHeader.getStyleClass().add("pagina-cabecera");
        VBox mcPage="Editor de plantillas".equals(mcTitle)?new VBox(mcContent):new VBox(mcHeader,mcContent);mcPage.getStyleClass().add("pagina");VBox.setVgrow(mcContent,Priority.ALWAYS);mcWorkspace.getChildren().setAll(mcPage);Animations.fadeInUp(mcPage,Duration.millis(380)).play();
    }
    private Button mcButton(String mcLabel,Runnable mcAction){
        Button mcB=new Button(mcLabel.startsWith("+ ")?mcLabel.substring(2):mcLabel);mcB.getStyleClass().add("secondary-btn");
        if(mcLabel.startsWith("Guardar")||mcLabel.startsWith("Importar archivo"))mcB.getStyleClass().add("primary-btn");
        else if(mcLabel.startsWith("Eliminar"))mcB.getStyleClass().add("danger-btn");
        String mcIcon=null;
        if(mcLabel.startsWith("Guardar"))mcIcon="fth-save";
        else if(mcLabel.startsWith("Importar"))mcIcon="fth-upload";
        else if(mcLabel.startsWith("Exportar")||mcLabel.startsWith("Descargar"))mcIcon="fth-download";
        else if(mcLabel.startsWith("Eliminar"))mcIcon="fth-trash-2";
        else if(mcLabel.startsWith("Duplicar"))mcIcon="fth-copy";
        else if(mcLabel.startsWith("Consultar")||mcLabel.startsWith("Buscar"))mcIcon="fth-search";
        else if(mcLabel.startsWith("Abrir"))mcIcon="fth-external-link";
        else if(mcLabel.startsWith("Editar"))mcIcon="fth-edit-2";
        else if(mcLabel.startsWith("Nueva")||mcLabel.startsWith("+ "))mcIcon="fth-plus";
        if(mcIcon!=null)mcB.setGraphic(new FontIcon(mcIcon));
        mcB.setOnAction(mcE->mcSafe(mcAction));return mcB;
    }
    /** @author MC */
    @FunctionalInterface private interface MCCheckedAction {void mcRun()throws Exception;}
    private void mcClientAction(MCCheckedAction mcAction){try{mcAction.mcRun();}catch(Exception mcE){mcError(mcE);}}
    private void mcSafe(Runnable mcAction){try{mcAction.run();}catch(Exception mcE){mcError(mcE);}}
    private void mcError(Exception mcE){mcStatus.setText("Error: "+mcE.getMessage());new Alert(Alert.AlertType.ERROR,"No se completó la operación:\n"+mcE.getMessage(),ButtonType.OK).showAndWait();}
    private void mcSaved(String mcMessage){mcStatus.setText(mcMessage);mcUpdateEditorSaveStatus();}
    private static VBox mcLabelled(String mcName,Node mcNode){Label mcL=new Label(mcName);mcL.getStyleClass().add("field-label");VBox mcBox=new VBox(5,mcL,mcNode);mcBox.setMinWidth(0);mcBox.setMaxWidth(Double.MAX_VALUE);mcL.setWrapText(true);if(mcNode instanceof Region mcR){mcR.setMinWidth(0);mcR.setMaxWidth(Double.MAX_VALUE);}return mcBox;}

    private static GridPane mcTwoColumns(){
        GridPane mcGrid=new GridPane();mcGrid.setHgap(14);mcGrid.setVgap(14);mcGrid.setMinWidth(0);
        for(int mcI=0;mcI<2;mcI++){ColumnConstraints mcCol=new ColumnConstraints();mcCol.setPercentWidth(50);mcCol.setFillWidth(true);mcGrid.getColumnConstraints().add(mcCol);}return mcGrid;
    }
    private static ScrollPane mcScrolling(Node mcContent){ScrollPane mcScroll=new ScrollPane(mcContent);mcScroll.setFitToWidth(true);mcScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);mcScroll.setMinWidth(0);mcScroll.getStyleClass().add("content-scroll");return mcScroll;}
    private static void mcUniformButtons(Pane mcPane){for(Node mcN:mcPane.getChildren())if(mcN instanceof Button mcB)mcB.setMaxWidth(Double.MAX_VALUE);}
    private static void mcFixedWidth(Region mcNode,double mcWidth){mcNode.setMinWidth(mcWidth);mcNode.setPrefWidth(mcWidth);mcNode.setMaxWidth(mcWidth);}
    private static Label mcDescription(String mcText){Label mcLabel=new Label(mcText);mcLabel.setWrapText(true);mcLabel.setMinWidth(0);mcLabel.getStyleClass().add("field-label");return mcLabel;}
    private static <T> void mcTwoLineCells(ListView<T> mcList,java.util.function.Function<T,String> mcTitle,java.util.function.Function<T,String> mcDetail){
        mcList.setCellFactory(mcView->new ListCell<>(){
            private final Label mcPrimary=new Label(),mcSecondary=new Label();private final VBox mcBox=new VBox(4,mcPrimary,mcSecondary);
            {mcPrimary.setWrapText(true);mcSecondary.setWrapText(true);mcSecondary.getStyleClass().add("field-label");mcBox.setMinWidth(0);mcBox.prefWidthProperty().bind(widthProperty().subtract(28));mcBox.setMaxWidth(Double.MAX_VALUE);}
            @Override protected void updateItem(T mcValue,boolean mcEmpty){super.updateItem(mcValue,mcEmpty);setText(null);setGraphic(mcEmpty||mcValue==null?null:mcBox);if(!mcEmpty&&mcValue!=null){mcPrimary.setText(mcTitle.apply(mcValue));mcSecondary.setText(mcDetail.apply(mcValue));}}
        });
    }

    private void mcBuildDefinitions(){
        mcDefinitionsPage=new BorderPane();mcDefinitionsPage.getStyleClass().add("page");
        mcDefinitionSearch.setPromptText("Buscar identificador, etiqueta o tipo");mcDefinitionSearch.textProperty().addListener((mcO,mcA,mcB)->mcRefreshDefinitions());
        mcDefinitionList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->mcLoadDefinition(mcB));
        mcTwoLineCells(mcDefinitionList,MCDefinition::mcLabel,mcD->mcD.mcKey()+" · "+mcD.mcType());
        VBox mcLeft=new VBox(8,mcDefinitionSearch,mcDefinitionList,mcButton("+ Nueva variable",this::mcNewDefinition),mcButton("Duplicar variable",this::mcDuplicateDefinition),mcButton("Eliminar variable",this::mcDeleteDefinition));
        mcLeft.getStyleClass().add("side-panel");mcFixedWidth(mcLeft,340);mcUniformButtons(mcLeft);VBox.setVgrow(mcDefinitionList,Priority.ALWAYS);mcDefinitionsPage.setLeft(mcLeft);BorderPane.setMargin(mcLeft,new Insets(0,14,0,0));
        mcDefinitionType.getItems().setAll(mcTYPES);mcDefinitionApiService.getItems().setAll("DNI","RUC");mcDefinitionApiService.setValue("DNI");mcDefinitionApiService.setOnAction(mcE->mcRefreshDefinitionApiFields());
        GridPane mcApiGrid=mcTwoColumns();mcApiGrid.add(mcLabelled("Servicio",mcDefinitionApiService),0,0);mcApiGrid.add(mcLabelled("Dato solicitado",mcDefinitionApiField),1,0);
        mcDefinitionApiPath.setWrapText(true);mcDefinitionApiOptions=new VBox(12,mcApiGrid,mcDefinitionApiPath);
        mcDefinitionApiEnabled.selectedProperty().addListener((mcO,mcA,mcB)->{mcDefinitionApiOptions.setVisible(mcB);mcDefinitionApiOptions.setManaged(mcB);});mcDefinitionApiOptions.setVisible(false);mcDefinitionApiOptions.setManaged(false);mcRefreshDefinitionApiFields();
        GridPane mcFields=mcTwoColumns();mcFields.add(mcLabelled("Identificador estable",mcDefinitionKey),0,0);mcFields.add(mcLabelled("Etiqueta visible",mcDefinitionLabel),1,0);mcFields.add(mcLabelled("Tipo de entrada",mcDefinitionType),0,1);mcFields.add(mcLabelled("Valor predeterminado opcional",mcDefinitionDefault),1,1);
        mcDefinitionChoice(mcComplementChoice);mcComplementBoth.setSelected(true);mcComplementBoth.setWrapText(true);mcComplementChoice.setPromptText("Variable complemento");mcComplementChoice.setMinWidth(0);mcComplementChoice.setMaxWidth(Double.MAX_VALUE);
        VBox mcApiCard=new VBox(10,new Label("Conexión API"),mcDefinitionApiEnabled,mcDefinitionApiOptions),mcComplementCard=new VBox(10,new Label("Complemento de variable"),mcLabelled("Variable complemento",mcComplementChoice),mcComplementBoth,mcButton("Vincular complemento",this::mcLinkComplement),mcComplementLinks);
        for(VBox mcCard:List.of(mcApiCard,mcComplementCard)){mcCard.getStyleClass().add("binding-card");mcCard.setMinWidth(0);mcCard.setPrefWidth(1);mcCard.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(mcCard,Priority.ALWAYS);}HBox mcConnections=new HBox(12,mcApiCard,mcComplementCard);
        mcCurrentDateFormat.getItems().setAll(MCDocumentStore.mcDATE_FORMATS);mcCurrentDateFormat.setValue("dd/MM/yyyy");mcCurrentDateFormat.setMaxWidth(Double.MAX_VALUE);mcCurrentDateFormat.disableProperty().bind(mcAutoCurrentDate.selectedProperty().not());mcCurrentDateFormat.valueProperty().addListener((mcO,mcA,mcB)->mcUpdateAutomaticDatePreview());mcAutoCurrentDate.setWrapText(true);mcCurrentDatePreview.setWrapText(true);mcUpdateAutomaticDatePreview();
        mcCurrentDateFormat.setCellFactory(mcList->new ListCell<>(){@Override protected void updateItem(String mcFormat,boolean mcEmpty){super.updateItem(mcFormat,mcEmpty);setText(mcEmpty||mcFormat==null?null:mcFormat+" — "+LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(mcFormat,Locale.forLanguageTag("es-PE"))));}});
        VBox mcDateCard=new VBox(10,new Label("Fecha actual automática"),mcAutoCurrentDate,mcCurrentDateFormat,mcCurrentDatePreview);mcDateCard.getStyleClass().add("binding-card");
        mcClientScope.setToggleGroup(mcVariableScope);mcGlobalScope.setToggleGroup(mcVariableScope);mcClientScope.setSelected(true);mcClientScope.setWrapText(true);mcGlobalScope.setWrapText(true);
        VBox mcScopeCard=new VBox(10,new Label("Alcance de la variable"),mcClientScope,mcGlobalScope);mcScopeCard.getStyleClass().add("binding-card");
        VBox mcForm=new VBox(16,new Label("DEFINICIÓN DE VARIABLE"),mcFields,mcConnections,mcDateCard,mcScopeCard,mcButton("Guardar variable",this::mcSaveDefinition));mcForm.getStyleClass().add("catalog-form");mcDefinitionsPage.setCenter(mcScrolling(mcForm));mcNewDefinition();
    }
    private void mcShowDefinitions(){mcRefreshDefinitions();mcPage("Variables",mcDefinitionsPage);}
    private void mcRefreshDefinitions(){try{long mcSelected=mcSelectedDefinition==null?0:mcSelectedDefinition.mcId();String mcQ=mcDefinitionSearch.getText().trim().toLowerCase();mcDefinitionList.getItems().setAll(mcStore.mcDefinitions().stream().filter(mcD->(mcD.mcKey()+" "+mcD.mcLabel()+" "+mcD.mcType()).toLowerCase().contains(mcQ)).toList());if(mcSelected!=0)mcDefinitionList.getSelectionModel().select(mcDefinitionList.getItems().stream().filter(mcD->mcD.mcId()==mcSelected).findFirst().orElse(null));}catch(Exception mcE){mcError(mcE);}}
    private void mcLoadDefinition(MCDefinition mcD){if(mcD==null)return;mcSelectedDefinition=mcD;mcDefinitionKey.setText(mcD.mcKey());mcDefinitionKey.setDisable(true);mcDefinitionLabel.setText(mcD.mcLabel());mcDefinitionType.setValue(MCVariableInput.mcVisualType(mcD.mcType()));mcDefinitionDefault.setText(mcD.mcDefaultValue()==null?"":mcD.mcDefaultValue());mcClientScope.setSelected(mcD.mcBindToClient());mcGlobalScope.setSelected(!mcD.mcBindToClient());mcAutoCurrentDate.setSelected(mcD.mcAutoCurrentDate());mcCurrentDateFormat.setValue(mcD.mcDateFormat());mcUpdateAutomaticDatePreview();mcRefreshComplements();try{MCApiBinding mcBinding=mcData.mcApiBinding(mcD.mcId());mcDefinitionApiEnabled.setSelected(mcBinding!=null);mcDefinitionApiService.setValue(mcBinding==null?"DNI":mcBinding.mcService());mcRefreshDefinitionApiFields();if(mcBinding!=null){String mcKey=MCApiFieldCatalog.mcCanonical(mcBinding.mcService(),mcBinding.mcPath());mcDefinitionApiField.setValue(mcKey.startsWith("legacy:")?"Campo anterior sin equivalencia":MCApiFieldCatalog.mcLabel(mcBinding.mcService(),mcKey));}}catch(Exception mcE){mcError(mcE);}}
    private void mcNewDefinition(){mcSelectedDefinition=null;mcDefinitionList.getSelectionModel().clearSelection();mcDefinitionKey.setDisable(false);mcDefinitionKey.clear();mcDefinitionLabel.clear();mcDefinitionType.setValue(mcTYPES.get(0));mcDefinitionDefault.clear();mcDefinitionApiEnabled.setSelected(false);mcDefinitionApiService.setValue("DNI");mcDefinitionApiField.setValue(null);mcComplementBoth.setSelected(true);mcClientScope.setSelected(true);mcAutoCurrentDate.setSelected(false);mcCurrentDateFormat.setValue("dd/MM/yyyy");mcUpdateAutomaticDatePreview();mcRefreshComplements();}
    private void mcRefreshDefinitionApiFields(){String mcCurrent=mcDefinitionApiField.getValue();mcDefinitionApiField.getItems().setAll(MCApiFieldCatalog.mcFields(mcDefinitionApiService.getValue()).stream().map(MCApiFieldCatalog.MCField::mcLabel).toList());if(mcCurrent!=null&&mcDefinitionApiField.getItems().contains(mcCurrent))mcDefinitionApiField.setValue(mcCurrent);else mcDefinitionApiField.getSelectionModel().selectFirst();}
    private void mcSaveDefinition(){String mcKey=mcDefinitionKey.getText().trim(),mcLabel=mcDefinitionLabel.getText().trim();if(!mcKey.matches("[a-zA-Z][a-zA-Z0-9_]*"))throw new IllegalArgumentException("El identificador debe empezar con letra y contener letras, números o guion bajo.");if(mcLabel.isBlank())throw new IllegalArgumentException("Escriba una etiqueta.");try{String mcType=mcDefinitionType.getValue();if(!mcDefinitionDefault.getText().isBlank()&&!"Fecha".equals(mcType))MCVariableInput.mcValidate(mcType,mcDefinitionDefault.getText(),Locale.getDefault());String mcStored=null;if(mcDefinitionApiEnabled.isSelected()){String mcRequested=mcDefinitionApiField.getValue();MCApiFieldCatalog.MCField mcField=MCApiFieldCatalog.mcFields(mcDefinitionApiService.getValue()).stream().filter(mcF->mcF.mcLabel().equals(mcRequested)).findFirst().orElse(null);if(mcField!=null)mcStored="field:"+mcField.mcKey();else if(mcSelectedDefinition!=null&&"Campo anterior sin equivalencia".equals(mcRequested)){MCApiBinding mcPrior=mcData.mcApiBinding(mcSelectedDefinition.mcId());if(mcPrior!=null)mcStored=mcPrior.mcPath();}if(mcStored==null)throw new IllegalArgumentException("Seleccione un dato solicitado.");}MCDefinition mcSaved=mcStore.mcSaveDefinition(new MCDefinition(mcSelectedDefinition==null?0:mcSelectedDefinition.mcId(),mcKey,mcLabel,mcType,mcDefinitionDefault.getText(),mcAutoCurrentDate.isSelected(),mcCurrentDateFormat.getValue(),!mcGlobalScope.isSelected()));if(mcDefinitionApiEnabled.isSelected())mcData.mcSaveApiBinding(new MCApiBinding(mcSaved.mcId(),mcDefinitionApiService.getValue(),mcStored));else mcData.mcDeleteApiBinding(mcSaved.mcId());mcSelectedDefinition=mcSaved;mcRefreshDefinitions();mcRefreshVariables();mcRefreshComplements();mcSaved("Variable guardada en el catálogo");}catch(Exception mcE){mcError(mcE);}}
    private void mcDuplicateDefinition(){MCDefinition mcSelected=mcDefinitionList.getSelectionModel().getSelectedItem();if(mcSelected==null)return;try{Set<String> mcKeys=new HashSet<>();for(MCDefinition mcD:mcStore.mcDefinitions())mcKeys.add(mcD.mcKey());String mcBase=mcSelected.mcKey()+"_copia",mcKey=mcBase;int mcSuffix=2;while(mcKeys.contains(mcKey))mcKey=mcBase+mcSuffix++;MCDefinition mcCopy=mcStore.mcSaveDefinition(new MCDefinition(0,mcKey,mcSelected.mcLabel()+" (copia)",MCVariableInput.mcVisualType(mcSelected.mcType()),"",mcSelected.mcAutoCurrentDate(),mcSelected.mcDateFormat(),mcSelected.mcBindToClient()));mcSelectedDefinition=mcCopy;mcRefreshDefinitions();mcDefinitionList.getSelectionModel().select(mcCopy);mcSaved("Variable duplicada sin datos ni asociaciones");}catch(Exception mcE){mcError(mcE);}}
    private void mcDeleteDefinition(){
        MCDefinition mcSelected=mcDefinitionList.getSelectionModel().getSelectedItem();if(mcSelected==null)return;
        try{
            List<MCTemplate> mcAffected=mcStore.mcDefinitionTemplates(mcSelected.mcId());
            if(!mcAffected.isEmpty()){
                ButtonType mcOpen=new ButtonType("Abrir editor");Alert mcWarning=new Alert(Alert.AlertType.WARNING,"La variable se utiliza en estas plantillas:\n"+String.join("\n",mcAffected.stream().map(MCTemplate::mcName).toList())+"\n\nQuite o reemplace cada aparición y desvincule la variable antes de eliminarla.",mcOpen,ButtonType.CANCEL);
                if(mcWarning.showAndWait().orElse(ButtonType.CANCEL)==mcOpen)mcOpenAffectedTemplate(mcAffected.get(0));return;
            }
            Alert mcConfirm=new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la variable "+mcSelected.mcKey()+"?",ButtonType.YES,ButtonType.CANCEL);if(mcConfirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;
            mcData.mcDeleteApiBinding(mcSelected.mcId());mcStore.mcDeleteDefinition(mcSelected.mcId());mcNewDefinition();mcRefreshDefinitions();mcRefreshVariables();mcSaved("Variable eliminada");
        }catch(Exception mcE){mcError(mcE);}
    }
    private void mcOpenAffectedTemplate(MCTemplate mcTemplate){
        ToggleButton mcEditor=mcNavigation.stream().filter(mcButton->"Editor de plantillas".equals(mcButton.getText())).findFirst().orElseThrow();
        if(mcMenu.getSelectedToggle()==mcEditor)mcShowEditor();else mcMenu.selectToggle(mcEditor);
        mcOpenTemplate(mcTemplate);
    }
    private void mcEditDefinitionFromEditor(){MCDefinition mcD=mcVariableList.getSelectionModel().getSelectedItem();mcMenu.getToggles().stream().filter(mcT->mcT instanceof ToggleButton mcB&&"Variables".equals(mcB.getText())).findFirst().ifPresent(mcMenu::selectToggle);if(mcD!=null){mcSelectedDefinition=mcD;mcDefinitionList.getSelectionModel().select(mcDefinitionList.getItems().stream().filter(mcX->mcX.mcId()==mcD.mcId()).findFirst().orElse(null));}}

    private BorderPane mcEditorPage;
    private void mcBuildEditor(){
        mcEditorPage=new BorderPane();mcEditorPage.getStyleClass().add("page");
        BorderPane mcDocumentPage=new BorderPane();mcDocumentPage.getStyleClass().add("page");
        FlowPane mcToolbar=new FlowPane(6,4);mcToolbar.setAlignment(Pos.CENTER_LEFT);
        mcTemplateChoice.setPrefWidth(190);mcRibbonControl(mcTemplateChoice,"Plantilla — Selecciona la plantilla que desea editar");mcRibbonChoice(mcTemplateChoice,Feather.FILE_TEXT);mcTemplateChoice.setPromptText("Seleccione plantilla");
        mcTemplateChoice.setOnAction(mcE->{if(!mcUpdatingTemplates)mcSafe(()->mcOpenTemplate(mcTemplateChoice.getValue()));});
        mcTemplateName.setPromptText("Nombre de la plantilla");mcTemplateName.setPrefWidth(150);mcRibbonControl(mcTemplateName,"Nombre de plantilla — Cambia el nombre de la plantilla actual");mcTemplateName.textProperty().addListener((mcO,mcA,mcB)->{if(mcCurrentTemplate!=null&&!Objects.equals(mcB,mcCurrentTemplate.mcName())){mcDirty=true;mcUpdateEditorSaveStatus();}});
        Button mcSave=mcRibbonButton("Guardar plantilla",Feather.SAVE,"Guardar plantilla — Conserva los cambios del documento actual",this::mcRequestSaveTemplate);mcSave.getStyleClass().add("primary-btn");mcEditorSaveStatus.getStyleClass().add("editor-save-status");
        mcToolbar.getChildren().addAll(mcRibbonButton("",Feather.FOLDER,"Abrir Plantillas — Abre la biblioteca de plantillas",this::mcShowLibrary),mcTemplateChoice,mcTemplateName,mcEditorSaveStatus,mcSave,mcRibbonButton("Nueva",Feather.FILE_PLUS,"Nueva plantilla — Crea una plantilla en blanco",this::mcNewTemplate),mcRibbonButton("Importar",Feather.UPLOAD,"Importar Word — Abre un documento Word como plantilla",this::mcImportDocx));
        mcToolbar.getStyleClass().add("toolbar");
        mcToolbar.getStyleClass().add("editor-toolbar");VBox mcTop=new VBox(4,mcToolbar,mcBuildEditorTools());mcDocumentPage.setTop(mcTop);
        mcPaletteSearch.setPromptText("Buscar variable");mcPaletteSearch.textProperty().addListener((mcO,mcA,mcB)->mcRefreshVariables());
        Label mcPaletteTitle=new Label("Variables");Region mcPaletteSpacer=new Region();HBox.setHgrow(mcPaletteSpacer,Priority.ALWAYS);
        mcEditorPersona.setPromptText("General (sin persona)");mcEditorPersona.setMaxWidth(Double.MAX_VALUE);mcEditorPersona.setMinWidth(0);VBox mcPeople=new VBox(5,new Label("Personas en este documento"),mcEditorPersona,mcButton("+ Agregar persona",this::mcAddEditorPersona),new HBox(4,mcButton("Renombrar",this::mcRenameEditorPersona),mcButton("Quitar",this::mcRemoveEditorPersona)));mcPeople.getChildren().get(0).setStyle("-fx-font-size: 11px;");
        VBox mcPaletteBody=new VBox(7,mcPeople,mcPaletteSearch,mcVariableList,mcButton("Insertar en cursor",this::mcInsertSelected),mcButton("Abrir Variables",this::mcEditDefinitionFromEditor));VBox.setVgrow(mcVariableList,Priority.ALWAYS);
        mcPaletteToggle=mcButton("",()->{
            boolean mcVisible=!mcPaletteBody.isManaged();mcVariablesView.setSelected(mcVisible);mcPaletteBody.setVisible(mcVisible);mcPaletteBody.setManaged(mcVisible);mcPaletteTitle.setVisible(mcVisible);mcPaletteTitle.setManaged(mcVisible);mcPaletteSpacer.setVisible(mcVisible);mcPaletteSpacer.setManaged(mcVisible);
            mcPalettePanel.setPrefWidth(mcVisible?250:68);mcPalettePanel.setMinWidth(mcVisible?250:68);
            mcPaletteToggle.setGraphic(new FontIcon(mcVisible?"fth-chevron-left":"fth-chevron-right"));String mcAction=mcVisible?"Contraer variables":"Expandir variables";mcPaletteToggle.setAccessibleText(mcAction);mcPaletteToggle.setTooltip(new Tooltip(mcAction));Platform.runLater(this::mcUpdateZoom);
        });
        mcPaletteToggle.setGraphic(new FontIcon("fth-chevron-left"));mcPaletteToggle.setAccessibleText("Contraer variables");mcPaletteToggle.setTooltip(new Tooltip("Contraer variables"));mcPaletteToggle.setStyle("-fx-padding: 8;");mcPaletteToggle.setMinSize(36,36);mcPaletteToggle.setPrefSize(36,36);mcPaletteToggle.setMaxSize(36,36);
        HBox mcPaletteHeader=new HBox(4,mcPaletteTitle,mcPaletteSpacer,mcPaletteToggle);mcPaletteHeader.setAlignment(Pos.CENTER_LEFT);
        mcPalettePanel=new VBox(7,mcPaletteHeader,mcPaletteBody);VBox.setVgrow(mcPaletteBody,Priority.ALWAYS);
        mcPalettePanel.getStyleClass().add("side-panel");mcPalettePanel.setPrefWidth(250);mcPalettePanel.setMinWidth(250);VBox.setVgrow(mcVariableList,Priority.ALWAYS);
        mcVariableList.setCellFactory(mcList->new ListCell<>(){
            @Override protected void updateItem(MCDefinition mcV,boolean mcEmpty){super.updateItem(mcV,mcEmpty);setText(mcEmpty||mcV==null?null:mcV.mcLabel());setTooltip(mcEmpty||mcV==null?null:new Tooltip(mcV.mcLabel()+"\n"+mcV.mcKey()+" · "+mcV.mcType()+(mcV.mcDefaultValue()==null||mcV.mcDefaultValue().isBlank()?"":"\nPredeterminado: "+mcV.mcDefaultValue())));}
        });
        mcVariableList.setOnDragDetected(mcE->{MCDefinition mcV=mcVariableList.getSelectionModel().getSelectedItem();if(mcV!=null){Dragboard mcDb=mcVariableList.startDragAndDrop(TransferMode.COPY);ClipboardContent mcCc=new ClipboardContent();mcCc.putString("definition:"+mcV.mcId()+":"+mcEditorTokenKey(mcV));mcDb.setContent(mcCc);mcE.consume();}});
        mcDocumentPage.setLeft(mcPalettePanel);
        mcRichEditor.setContextMenuEnabled(true);
        mcRichEditor.getEngine().getLoadWorker().stateProperty().addListener((mcO,mcA,mcB)->{if(mcB==Worker.State.SUCCEEDED){if(mcDocument==null||mcRichModel==null||!Boolean.TRUE.equals(mcRichEditor.getEngine().executeScript("typeof window.mcStudio !== 'undefined'"))){mcEditorReady=false;return;}mcEditorReady=true;((JSObject)mcRichEditor.getEngine().executeScript("window")).setMember("mcJavaBridge",mcEditorBridge);mcRichEditor.getEngine().executeScript("window.mcStudio.mcPaper("+mcPageWidth+","+mcPageHeight+","+mcEditorLeft+","+mcEditorTop+","+mcEditorRight+","+mcEditorBottom+")");mcEditorScript("mcPersonas("+mcJsonString(mcEditorPersonaNames)+")");mcEditorScript("mcNotifySelectionFormat()");mcUpdateEditorView();mcUpdateZoom();}});
        mcRichEditor.getEngine().locationProperty().addListener((mcO,mcA,mcB)->{if(mcB!=null&&(mcB.startsWith("http:")||mcB.startsWith("https:")))mcRichEditor.getEngine().loadContent("");});
        mcRichEditor.widthProperty().addListener((mcO,mcA,mcB)->mcUpdateZoom());mcRichEditor.heightProperty().addListener((mcO,mcA,mcB)->mcUpdateZoom());
        mcDocumentPage.setCenter(mcRichEditor);
        mcEditorTabs=new TabPane(new Tab("Documento",mcDocumentPage));mcEditorTabs.getTabs().get(0).setClosable(false);mcEditorPage.setCenter(mcEditorTabs);
    }
    private void mcAttachGenerationConfiguration(){Tab mcConfig=new Tab("Configurar generación",mcCatalogPage);mcConfig.setClosable(false);mcEditorTabs.getTabs().add(mcConfig);mcEditorTabs.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->{if(mcB==mcConfig){if(mcDirty)mcSafe(this::mcSaveTemplate);mcRefreshTemplates();mcRefreshKinds();if(mcCurrentTemplate!=null&&mcKindTemplate.getValue()==null){mcKindTemplate.setValue(mcCurrentTemplate);}}});}
    private void mcShowEditor(){mcRefreshTemplates();mcRefreshVariables();mcEditorTabs.getSelectionModel().select(0);mcPage("Editor de plantillas",mcEditorPage);if(mcDocument==null)mcRichEditor.getEngine().loadContent("<html><body style='font:14px Arial;padding:30px'>Importe un DOCX o cree una plantilla nueva.</body></html>");}
    private void mcRefreshTemplates(){try{mcUpdatingTemplates=true;MCTemplate mcSelected=mcCurrentTemplate;mcTemplateChoice.getItems().setAll(mcStore.mcTemplates());mcKindTemplate.getItems().setAll(mcTemplateChoice.getItems());if(mcSelected!=null)mcTemplateChoice.getSelectionModel().select(mcTemplateChoice.getItems().stream().filter(mcT->mcT.mcId()==mcSelected.mcId()).findFirst().orElse(null));}catch(Exception mcE){mcError(mcE);}finally{mcUpdatingTemplates=false;}}
    private void mcNewTemplate(){TextInputDialog mcName=new TextInputDialog();mcName.setTitle("Nueva plantilla");mcName.setHeaderText("Nombre de la plantilla");String mcSelected=mcName.showAndWait().orElse(null);if(mcSelected==null)return;if(mcSelected.isBlank())throw new IllegalArgumentException("Escriba un nombre.");if(!mcConfirmPendingSwitch())return;try{XWPFDocument mcD=new XWPFDocument();mcD.createParagraph().createRun().setText("Escriba aquí el contenido del documento.");byte[] mcContent=MCDocumentEngine.mcBytes(mcD);mcD.close();MCTemplate mcT=mcStore.mcSaveTemplate(new MCTemplate(0,mcSelected.trim(),mcContent,mcContent));mcData.mcRecordRevision(mcT.mcId(),mcContent);MCTemplateGroup mcSelectedGroup=mcGroupList.getSelectionModel().getSelectedItem();if(mcSelectedGroup!=null)mcStore.mcSetTemplateGroups(mcT.mcId(),List.of(mcSelectedGroup.mcId()));mcRefreshTemplates();mcRefreshLibrary();mcRefreshKinds();mcOpenTemplate(mcT);mcSaved("Plantilla creada");}catch(Exception mcE){mcError(mcE);}}
    private void mcImportDocx(){FileChooser mcFc=new FileChooser();mcFc.setTitle("Importar documento Word");mcFc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos de Word (*.doc, *.docx, *.docm, *.dot, *.dotx, *.dotm)","*.doc","*.DOC","*.docx","*.DOCX","*.docm","*.DOCM","*.dot","*.DOT","*.dotx","*.DOTX","*.dotm","*.DOTM"));File mcFile=mcFc.showOpenDialog(mcStage);if(mcFile==null||!mcConfirmPendingSwitch())return;try{byte[] mcContent=MCWordDocumentImporter.mcImportToDocx(mcFile.toPath());MCTemplate mcT=mcStore.mcSaveTemplate(new MCTemplate(0,mcFile.getName().replaceFirst("(?i)\\.(docx?|docm|dotx?|dotm)$",""),mcContent,mcContent));mcData.mcRecordRevision(mcT.mcId(),mcContent);MCTemplateGroup mcSelectedGroup=mcGroupList.getSelectionModel().getSelectedItem();if(mcSelectedGroup!=null)mcStore.mcSetTemplateGroups(mcT.mcId(),List.of(mcSelectedGroup.mcId()));mcRefreshTemplates();mcRefreshLibrary();mcRefreshKinds();mcOpenTemplate(mcT);mcSaved("Documento Word importado y guardado localmente");}catch(Exception mcE){mcError(mcE);}}
    private boolean mcConfirmPendingSwitch(){if(mcDocument==null||!mcDirty)return true;Alert mcChoice=new Alert(Alert.AlertType.CONFIRMATION,"Hay cambios pendientes. ¿Guardar antes de abrir otra plantilla?",ButtonType.YES,ButtonType.NO,ButtonType.CANCEL);ButtonType mcResult=mcChoice.showAndWait().orElse(ButtonType.CANCEL);if(mcResult==ButtonType.CANCEL)return false;if(mcResult==ButtonType.YES)mcSaveTemplate();else mcDirty=false;return true;}
    private void mcOpenTemplate(MCTemplate mcT){if(mcT==null)return;try{if(mcCurrentTemplate!=null&&mcCurrentTemplate.mcId()!=mcT.mcId()&&!mcConfirmPendingSwitch())return;if(mcDocument!=null)mcDocument.close();mcCurrentTemplate=mcT;mcDocument=MCDocumentEngine.mcOpen(mcT.mcEdited());mcSelectedRun=null;mcSelectedParagraph=null;mcSelectedText=null;mcTemplateName.setText(mcT.mcName());mcDirty=false;mcUpdateEditorSaveStatus();mcRefreshVariables();mcRefreshEditorPersonas();mcRenderDocument();mcUpdatingTemplates=true;mcTemplateChoice.getSelectionModel().select(mcTemplateChoice.getItems().stream().filter(mcX->mcX.mcId()==mcT.mcId()).findFirst().orElse(mcT));mcUpdatingTemplates=false;}catch(Exception mcE){mcUpdatingTemplates=false;mcError(mcE);}}
    private void mcRequestSaveTemplate(){if(mcDocument==null)return;if(!mcDirty&&Objects.equals(mcTemplateName.getText().trim(),mcCurrentTemplate.mcName())){mcSaved("La plantilla ya está guardada");return;}if(new Alert(Alert.AlertType.CONFIRMATION,"¿Guardar los cambios de esta plantilla?",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.YES)mcSaveTemplate();}
    private void mcSaveTemplate(){if(mcDocument==null)return;if(mcTemplateName.getText().isBlank())throw new IllegalArgumentException("Escriba un nombre de plantilla.");try{mcSyncEditor();byte[] mcContent=MCDocumentEngine.mcBytes(mcDocument);boolean mcContentChanged=!Arrays.equals(mcContent,mcCurrentTemplate.mcEdited());boolean mcNameChanged=!mcTemplateName.getText().trim().equals(mcCurrentTemplate.mcName());if(!mcContentChanged&&!mcNameChanged){mcDirty=false;mcSaved("La plantilla ya está guardada");return;}mcCurrentTemplate=mcStore.mcSaveTemplate(new MCTemplate(mcCurrentTemplate.mcId(),mcTemplateName.getText().trim(),mcCurrentTemplate.mcOriginal(),mcContent));if(mcContentChanged)mcData.mcRecordRevision(mcCurrentTemplate.mcId(),mcContent);mcDirty=false;mcRefreshTemplates();mcRefreshLibrary();mcRefreshKinds();mcSaved("Plantilla guardada");mcRenderDocument();}catch(Exception mcE){throw new RuntimeException("No se pudo guardar la plantilla",mcE);}}
    private void mcRefreshVariables(){try{long mcSelected=mcVariableList.getSelectionModel().getSelectedItem()==null?0:mcVariableList.getSelectionModel().getSelectedItem().mcId();String mcQ=mcPaletteSearch.getText().trim().toLowerCase();mcVariableList.getItems().setAll(mcStore.mcDefinitions().stream().filter(mcD->(mcD.mcLabel()+" "+mcD.mcKey()).toLowerCase().contains(mcQ)).toList());if(mcSelected!=0)mcVariableList.getSelectionModel().select(mcVariableList.getItems().stream().filter(mcD->mcD.mcId()==mcSelected).findFirst().orElse(null));}catch(Exception mcE){mcError(mcE);}}
    private void mcBuildLibrary(){
        mcLibraryPage=new BorderPane();mcLibraryPage.getStyleClass().add("page");mcLibrarySearch.setPromptText("Buscar plantilla");mcLibrarySearch.getStyleClass().add("buscador");mcLibrarySearch.textProperty().addListener((mcO,mcA,mcB)->mcRefreshLibrary());
        mcGroupList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->{if(!mcRefreshingLibrary)mcRefreshLibrary();});
        VBox mcGroups=new VBox(8,new Label("GRUPOS"),mcGroupList,mcButton("+ Nuevo grupo",this::mcCreateGroup),mcButton("Renombrar grupo",this::mcRenameGroup),mcButton("Eliminar grupo",this::mcRemoveGroup));mcGroups.getStyleClass().add("side-panel");mcFixedWidth(mcGroups,240);mcUniformButtons(mcGroups);BorderPane.setMargin(mcGroups,new Insets(0,12,0,0));VBox.setVgrow(mcGroupList,Priority.ALWAYS);mcLibraryPage.setLeft(mcGroups);
        mcLibrarySort.getItems().setAll("Nombre A-Z","Nombre Z-A","Más recientes","Más antiguas");mcLibrarySort.setValue("Nombre A-Z");mcLibrarySort.setOnAction(mcE->mcRefreshLibrary());
        HBox mcSearchRow=new HBox(8,mcLibrarySearch,mcLibrarySort);HBox.setHgrow(mcLibrarySearch,Priority.ALWAYS);mcLibrarySearch.setMaxWidth(Double.MAX_VALUE);mcLibrarySort.setPrefWidth(135);
        mcGroupList.setPlaceholder(mcDescription("Cree su primer grupo para organizar las plantillas."));
        VBox mcTemplates=new VBox(9,mcSearchRow,mcLibraryList);mcTemplates.setMinWidth(0);mcTemplates.getStyleClass().add("catalog-form");mcTemplates.setMaxWidth(Double.MAX_VALUE);VBox.setVgrow(mcLibraryList,Priority.ALWAYS);mcLibraryPage.setCenter(mcTemplates);
        mcLibraryList.setCellFactory(mcView->new ListCell<>(){
            private final Label mcTitle=new Label(),mcDetail=new Label();private final VBox mcLines=new VBox(4,mcTitle,mcDetail);
            {mcTitle.setWrapText(true);mcDetail.setWrapText(true);mcDetail.getStyleClass().add("field-label");mcLines.setMinWidth(0);mcLines.prefWidthProperty().bind(widthProperty().subtract(28));}
            @Override protected void updateItem(MCTemplate mcTemplate,boolean mcEmpty){
                super.updateItem(mcTemplate,mcEmpty);getStyleClass().remove("library-associated");setText(null);setGraphic(null);
                if(mcEmpty||mcTemplate==null)return;
                List<Long> mcMemberships=mcLibraryMemberships.getOrDefault(mcTemplate.mcId(),List.of());MCTemplateGroup mcGroup=mcGroupList.getSelectionModel().getSelectedItem();
                if(mcGroup!=null&&mcLibraryHighlighted(mcMemberships,mcGroup.mcId()))getStyleClass().add("library-associated");
                mcTitle.setText(mcTemplate.mcName());String mcNames=mcMemberships.stream().map(mcLibraryGroupNames::get).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));
                String mcDate=mcLibraryModifiedDates.get(mcTemplate.mcId());mcDetail.setText(mcNames+(mcDate==null?"":" · "+mcDate));setGraphic(mcLines);
            }
        });
        mcLibraryGroups.setWrapText(true);mcAddToGroup=mcButton("Añadir plantilla al grupo",this::mcAddLibraryGroup);mcRemoveFromGroup=mcButton("Quitar del grupo",this::mcRemoveLibraryGroup);
        VBox mcActions=new VBox(9,new Label("PLANTILLA SELECCIONADA"),mcButton("Abrir en editor",this::mcOpenLibraryTemplate),mcButton("Renombrar plantilla",this::mcRenameLibraryTemplate),mcButton("Duplicar plantilla",this::mcDuplicateLibraryTemplate),mcButton("Eliminar plantilla",this::mcRemoveLibraryTemplate),new Separator(),new Label("GRUPOS ASIGNADOS"),mcLibraryGroups,mcAddToGroup,mcRemoveFromGroup);mcActions.getStyleClass().add("side-panel");mcFixedWidth(mcActions,270);mcUniformButtons(mcActions);mcLibraryPage.setRight(mcScrolling(mcActions));BorderPane.setMargin(mcLibraryPage.getRight(),new Insets(0,0,0,12));
        mcLibraryList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->mcUpdateLibraryActions());mcUpdateLibraryActions();
    }
    private void mcShowLibrary(){mcRefreshLibrary();mcPage("Plantillas",mcLibraryPage);}
    private void mcRefreshLibrary(){if(mcRefreshingLibrary)return;mcRefreshingLibrary=true;try{
        MCTemplate mcSelected=mcLibraryList.getSelectionModel().getSelectedItem();MCTemplateGroup mcSelectedGroup=mcGroupList.getSelectionModel().getSelectedItem();String mcQ=mcLibrarySearch.getText().trim().toLowerCase();List<MCTemplate> mcAll=mcStore.mcTemplates();List<MCTemplateGroup> mcGroups=mcStore.mcGroups();Map<Long,String> mcDates=mcData.mcRevisionDates();Map<Long,List<Long>> mcMemberships=new HashMap<>();for(MCTemplate mcT:mcAll)mcMemberships.put(mcT.mcId(),mcStore.mcGroupIds(mcT.mcId()));mcLibraryMemberships=mcMemberships;mcLibraryModifiedDates=mcDates;Map<Long,String> mcNames=new HashMap<>();for(MCTemplateGroup mcG:mcGroups)mcNames.put(mcG.mcId(),mcG.mcName());mcLibraryGroupNames=mcNames;
        mcGroupList.getItems().setAll(mcGroups);
        MCTemplateGroup mcActive=mcGroups.stream().filter(mcG->mcSelectedGroup!=null&&mcG.mcId()==mcSelectedGroup.mcId()).findFirst().orElse(mcGroups.isEmpty()?null:mcGroups.get(0));
        mcGroupList.getSelectionModel().select(mcActive);
        mcLibraryList.setPlaceholder(mcDescription(mcAll.isEmpty()?"Todavía no hay plantillas guardadas. Créelas o impórtelas desde el Editor de plantillas.":"No hay plantillas que coincidan con la búsqueda."));
        List<MCTemplate> mcFound=new ArrayList<>();for(MCTemplate mcT:mcAll)if(mcLibraryMatches(mcT.mcName(),mcQ))mcFound.add(mcT);
        Comparator<MCTemplate> mcName=Comparator.comparing(MCTemplate::mcName,String.CASE_INSENSITIVE_ORDER).thenComparingLong(MCTemplate::mcId);String mcSort=mcLibrarySort.getValue();Comparator<MCTemplate> mcOrder=switch(mcSort==null?"Nombre A-Z":mcSort){case "Nombre Z-A"->mcName.reversed();case "Más recientes"->Comparator.comparing((MCTemplate mcT)->mcDates.get(mcT.mcId()),Comparator.nullsFirst(String::compareTo)).reversed().thenComparing(mcName);case "Más antiguas"->Comparator.comparing((MCTemplate mcT)->mcDates.get(mcT.mcId()),Comparator.nullsLast(String::compareTo)).thenComparing(mcName);default->mcName;};mcFound.sort(mcOrder);
        mcLibraryList.getItems().setAll(mcFound);if(mcSelected!=null)mcLibraryList.getSelectionModel().select(mcFound.stream().filter(mcT->mcT.mcId()==mcSelected.mcId()).findFirst().orElse(null));mcLibraryList.refresh();mcUpdateLibraryActions();
    }catch(Exception mcEx){mcError(mcEx);}finally{mcRefreshingLibrary=false;}}
    static boolean mcLibraryMatches(String mcName,String mcSearch){return mcName.toLowerCase(Locale.ROOT).contains(mcSearch.toLowerCase(Locale.ROOT));}
    static boolean mcLibraryHighlighted(List<Long> mcMemberships,long mcSelectedGroup){return mcSelectedGroup>0&&mcMemberships.contains(mcSelectedGroup);}
    private void mcUpdateLibraryActions(){try{MCTemplate mcT=mcLibraryList.getSelectionModel().getSelectedItem();MCTemplateGroup mcGroup=mcGroupList.getSelectionModel().getSelectedItem();List<Long> mcIds=mcT==null?List.of():mcStore.mcGroupIds(mcT.mcId());String mcNames=mcStore.mcGroups().stream().filter(mcG->mcIds.contains(mcG.mcId())).map(MCTemplateGroup::mcName).collect(java.util.stream.Collectors.joining(", "));mcLibraryGroups.setText(mcT==null?"Seleccione una plantilla":mcNames.isBlank()?"Sin grupo":mcNames);boolean mcReady=mcT!=null&&mcGroup!=null&&mcGroup.mcId()>0;boolean mcMember=mcReady&&mcIds.contains(mcGroup.mcId());if(mcAddToGroup!=null)mcAddToGroup.setDisable(mcGroup==null);if(mcRemoveFromGroup!=null)mcRemoveFromGroup.setDisable(!mcReady||!mcMember);}catch(Exception mcEx){mcError(mcEx);}}
    private void mcAddLibraryGroup(){try{
        MCTemplateGroup mcGroup=mcSelectedGroup();List<MCTemplate> mcAvailable=mcStore.mcTemplates().stream().filter(mcT->!mcLibraryMemberships.getOrDefault(mcT.mcId(),List.of()).contains(mcGroup.mcId())).toList();
        if(mcAvailable.isEmpty()){mcSaved("Todas las plantillas guardadas ya pertenecen a este grupo. Cree o importe otra plantilla.");return;}
        ChoiceDialog<MCTemplate> mcChoice=new ChoiceDialog<>(mcAvailable.get(0),mcAvailable);mcChoice.setTitle("Añadir plantilla al grupo");mcChoice.setHeaderText("Seleccione una plantilla para "+mcGroup.mcName());
        mcChoice.showAndWait().ifPresent(mcT->{try{Set<Long> mcIds=new LinkedHashSet<>(mcStore.mcGroupIds(mcT.mcId()));mcIds.add(mcGroup.mcId());mcStore.mcSetTemplateGroups(mcT.mcId(),mcIds);mcRefreshLibrary();mcLibraryList.getSelectionModel().select(mcT);mcSaved("Plantilla añadida al grupo");}catch(Exception mcEx){mcError(mcEx);}});
    }catch(Exception mcEx){mcError(mcEx);}}
    private void mcRemoveLibraryGroup(){mcChangeLibraryGroup(false);}
    private void mcChangeLibraryGroup(boolean mcAdd){try{MCTemplate mcT=mcSelectedLibraryTemplate();MCTemplateGroup mcG=mcSelectedGroup();Set<Long> mcIds=new LinkedHashSet<>(mcStore.mcGroupIds(mcT.mcId()));if(mcAdd)mcIds.add(mcG.mcId());else mcIds.remove(mcG.mcId());mcStore.mcSetTemplateGroups(mcT.mcId(),mcIds);mcRefreshLibrary();mcSaved(mcAdd?"Plantilla añadida al grupo":"Plantilla quitada del grupo");}catch(Exception mcEx){mcError(mcEx);}}
    private MCTemplate mcSelectedLibraryTemplate(){MCTemplate mcT=mcLibraryList.getSelectionModel().getSelectedItem();if(mcT==null)throw new IllegalArgumentException("Seleccione una plantilla.");return mcT;}
    private MCTemplateGroup mcSelectedGroup(){MCTemplateGroup mcG=mcGroupList.getSelectionModel().getSelectedItem();if(mcG==null||mcG.mcId()<=0)throw new IllegalArgumentException("Seleccione un grupo de Mis grupos.");return mcG;}
    private void mcCreateGroup(){TextInputDialog mcInput=new TextInputDialog();mcInput.setTitle("Nuevo grupo");mcInput.setHeaderText("Nombre del grupo de plantillas");mcInput.showAndWait().ifPresent(mcName->{try{MCTemplateGroup mcG=mcStore.mcSaveGroup(0,mcName);mcRefreshLibrary();mcGroupList.getSelectionModel().select(mcG);mcSaved("Grupo creado");}catch(Exception mcEx){mcError(mcEx);}});}
    private void mcRenameGroup(){MCTemplateGroup mcG=mcSelectedGroup();TextInputDialog mcInput=new TextInputDialog(mcG.mcName());mcInput.setTitle("Renombrar grupo");mcInput.setHeaderText("Nombre del grupo");mcInput.showAndWait().ifPresent(mcName->{try{mcStore.mcSaveGroup(mcG.mcId(),mcName);mcRefreshLibrary();mcSaved("Grupo renombrado");}catch(Exception mcEx){mcError(mcEx);}});}
    private void mcRemoveGroup(){try{MCTemplateGroup mcG=mcSelectedGroup();if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar el grupo '"+mcG.mcName()+"'? Sus "+mcG.mcCount()+" plantillas permanecerán guardadas.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;mcStore.mcDeleteGroup(mcG.mcId(),null);mcGroupList.getSelectionModel().clearSelection();mcRefreshLibrary();mcSaved("Grupo eliminado; las plantillas permanecen guardadas");}catch(Exception mcEx){mcError(mcEx);}}
    private void mcOpenLibraryTemplate(){MCTemplate mcT=mcSelectedLibraryTemplate();mcShowEditor();mcOpenTemplate(mcT);}
    private void mcRenameLibraryTemplate(){MCTemplate mcT=mcSelectedLibraryTemplate();TextInputDialog mcInput=new TextInputDialog(mcT.mcName());mcInput.setTitle("Renombrar plantilla");mcInput.setHeaderText("Nombre de la plantilla");mcInput.showAndWait().ifPresent(mcName->{try{if(mcName.isBlank())throw new IllegalArgumentException("Escriba un nombre.");mcStore.mcSaveTemplate(new MCTemplate(mcT.mcId(),mcName.trim(),mcT.mcOriginal(),mcT.mcEdited()));if(mcCurrentTemplate!=null&&mcCurrentTemplate.mcId()==mcT.mcId()){mcCurrentTemplate=new MCTemplate(mcT.mcId(),mcName.trim(),mcT.mcOriginal(),mcT.mcEdited());mcTemplateName.setText(mcName.trim());}mcRefreshLibrary();mcRefreshTemplates();mcSaved("Plantilla renombrada");}catch(Exception mcEx){mcError(mcEx);}});}
    private void mcDuplicateLibraryTemplate(){MCTemplate mcT=mcSelectedLibraryTemplate();try{MCTemplate mcCopy=mcStore.mcSaveTemplate(new MCTemplate(0,mcT.mcName()+" (copia)",mcT.mcOriginal(),mcT.mcEdited()));for(var mcPerson:mcStore.mcPersonas(mcT.mcId()))mcStore.mcAddPersona(mcCopy.mcId(),mcPerson.mcPersonaKey(),mcPerson.mcDisplayName(),mcPerson.mcSortOrder());for(MCVariable mcV:mcStore.mcVariables(mcT.mcId())){MCVariable mcCreated=mcStore.mcSaveVariable(new MCVariable(0,mcCopy.mcId(),mcV.mcKey(),mcV.mcLabel(),mcV.mcType(),mcV.mcDefaultValue(),mcV.mcRequired(),mcV.mcPosition()));long mcDef=mcStore.mcDefinitionId(mcV.mcId());if(mcDef>0)mcStore.mcLinkDefinition(mcCreated.mcId(),mcDef);}for(MCApiContext mcC:mcData.mcApiContexts(mcT.mcId()))mcData.mcSaveApiContext(new MCApiContext(mcCopy.mcId(),mcC.mcVariableKey(),mcC.mcGroupKey(),mcC.mcService(),mcC.mcSource()));for(MCContactBinding mcB:mcData.mcContactBindings(mcT.mcId()))mcData.mcSaveContactBinding(new MCContactBinding(0,mcCopy.mcId(),mcB.mcGroupKey(),mcB.mcKind(),mcB.mcField(),mcB.mcVariableKey()));mcData.mcRecordRevision(mcCopy.mcId(),mcCopy.mcEdited());mcRefreshLibrary();mcRefreshTemplates();mcLibraryList.getSelectionModel().select(mcCopy);mcSaved("Plantilla duplicada sin grupos");}catch(Exception mcEx){mcError(mcEx);}}
    private void mcRemoveLibraryTemplate(){MCTemplate mcT=mcSelectedLibraryTemplate();try{List<String> mcLegacy=mcStore.mcTemplateDependencies(mcT.mcId());String mcDetail=mcLegacy.isEmpty()?"":" También se quitarán "+mcLegacy.size()+" configuraciones antiguas vinculadas a esta plantilla.";if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la plantilla '"+mcT.mcName()+"'? Los documentos ya generados se conservarán en Historial."+mcDetail,ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;mcStore.mcDeleteTemplateWithLegacyKinds(mcT.mcId());mcData.mcClearMappings(mcT.mcId());mcData.mcClearContactBindings(mcT.mcId());mcData.mcClearApiContexts(mcT.mcId());if(mcCurrentTemplate!=null&&mcCurrentTemplate.mcId()==mcT.mcId()){if(mcDocument!=null)mcDocument.close();mcDocument=null;mcCurrentTemplate=null;mcRichModel=null;mcEditorReady=false;}mcRefreshLibrary();mcRefreshTemplates();mcRefreshKinds();mcRefreshHistory();mcSaved("Plantilla eliminada; documentos conservados en Historial");}catch(Exception mcEx){mcError(mcEx);}}
    private void mcInsertSelected(){MCDefinition mcD=mcVariableList.getSelectionModel().getSelectedItem();if(mcD==null||mcDocument==null||!mcEditorReady){mcSaved("Seleccione una variable y coloque el cursor en el documento");return;}if(mcBindVariable(mcD))mcRichEditor.getEngine().executeScript("window.mcStudio.mcInsertToken("+mcJsonString(mcEditorTokenKey(mcD))+")");}
    private boolean mcBindVariable(MCDefinition mcD){return mcBindVariable(mcD,mcEditorTokenKey(mcD));}
    private boolean mcBindVariable(MCDefinition mcD,String mcKey){try{if(mcCurrentTemplate==null)return false;MCVariable mcBound=mcStore.mcVariables(mcCurrentTemplate.mcId()).stream().filter(mcV->mcV.mcKey().equals(mcKey)).findFirst().orElse(null);if(mcBound==null)mcStore.mcBindDefinition(mcCurrentTemplate.mcId(),mcD.mcId(),mcKey,false,mcStore.mcVariables(mcCurrentTemplate.mcId()).size()+1);else if(mcStore.mcDefinitionId(mcBound.mcId())!=mcD.mcId())throw new IllegalArgumentException("Esta plantilla ya utiliza '"+mcD.mcKey()+"' con otra definición.");return true;}catch(Exception mcEx){mcError(mcEx);return false;}}
    private String mcJsonString(Object mcValue){try{return mcJson.writeValueAsString(mcValue);}catch(Exception mcEx){throw new RuntimeException(mcEx);}}
    private void mcEditorCommand(String mcName){mcEditorCommand(mcName,null);}
    private void mcEditorCommand(String mcName,String mcValue){if(mcEditorReady&&!mcUpdatingToolbarFromSelection)mcRichEditor.getEngine().executeScript("window.mcStudio.mcCommand("+mcJsonString(mcName)+","+(mcValue==null?"null":mcJsonString(mcValue))+")");}
    private void mcSyncEditor() throws Exception {if(mcEditorReady&&mcRichModel!=null&&mcDocument!=null){String mcXml=String.valueOf(mcRichEditor.getEngine().executeScript("window.mcStudio.mcHtml()"));mcRichModel.mcApply(mcXml);XWPFDocument mcNormalized=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcDocument));mcDocument.close();mcDocument=mcNormalized;mcRichModel=new MCContinuousDocxEditor(mcDocument,mcEditorPersonaNames);mcRichEditor.getEngine().executeScript("window.mcStudio.mcAccept("+mcJsonString("<div>"+mcRichModel.mcHtml()+"</div>")+")");}}
    private void mcUpdateToolbarFromSelection(String mcJsonState){
        if(!mcEditorReady)return;
        try{
            var mcState=mcJson.readTree(mcJsonState);if(mcState==null||!mcState.isObject())return;
            mcUpdatingToolbarFromSelection=true;
            String mcFont=mcState.path("fontFamily").asText("Arial");
            if(!mcFontChoice.getItems().contains(mcFont))mcFontChoice.getItems().add(mcFont);
            mcFontChoice.setValue(mcFont);mcFontSizeChoice.setValue(mcState.path("fontSizePt").asInt(12));
            String mcAlignment=mcState.path("alignment").asText("left");
            mcSelectionFormatButtons.forEach((mcKey,mcButton)->{
                boolean mcActive=Set.of("left","center","right","justify").contains(mcKey)?mcKey.equals(mcAlignment):mcState.path(mcKey).asBoolean(false);
                if(mcActive){if(!mcButton.getStyleClass().contains("ribbon-btn-active"))mcButton.getStyleClass().add("ribbon-btn-active");}
                else mcButton.getStyleClass().remove("ribbon-btn-active");
            });
            mcEditorTextColor.setValue(Color.web(mcState.path("textColor").asText("#000000")));
            mcEditorHighlightColor.setValue(Color.web(mcState.path("highlightColor").asText("#ffffff")));
        }catch(IOException|IllegalArgumentException mcIgnored){}
        finally{mcUpdatingToolbarFromSelection=false;}
    }
    /** @author MC */
    public final class MCEditorBridge {
        public void mcSaveCurrentTemplate(){Platform.runLater(()->mcSafe(MCStudioWindow.this::mcSaveTemplate));}
        public void mcOnSelectionFormatChanged(String mcJsonState){Platform.runLater(()->mcUpdateToolbarFromSelection(mcJsonState));}
        public boolean mcBindDroppedDefinition(String mcId,String mcKey){
            try{MCDefinition mcDefinition=mcStore.mcDefinitions().stream().filter(mcD->Long.toString(mcD.mcId()).equals(mcId)&&mcD.mcKey().equals(MCDocumentEngine.mcBaseKey(mcKey))).findFirst().orElse(null);return mcDefinition!=null&&(mcDefinition.mcBindToClient()||MCDocumentEngine.mcPersonaKey(mcKey).isEmpty())&&MCDocumentEngine.mcValidKey(mcKey)&&(MCDocumentEngine.mcPersonaKey(mcKey).isEmpty()||mcEditorPersonaNames.containsKey(MCDocumentEngine.mcPersonaKey(mcKey)))&&mcEditorReady&&mcBindVariable(mcDefinition,mcKey);}catch(Exception mcE){mcError(mcE);return false;}
        }
public void mcMarkDirty(){Platform.runLater(()->{mcDirty=true;mcUpdateEditorSaveStatus();mcStatus.setText("Cambios sin guardar");});}public void mcPageMoved(){Platform.runLater(MCStudioWindow.this::mcUpdatePageIndicator);}public void mcTableSelected(boolean mcSelected){Platform.runLater(()->{if(mcTableTools!=null){mcTableTools.setVisible(mcSelected);mcTableTools.setManaged(mcSelected);}});}public void mcImageSelected(boolean mcSelected){Platform.runLater(()->{if(mcImageTools!=null){mcImageTools.setVisible(mcSelected);mcImageTools.setManaged(mcSelected);}});}}
    private void mcTableAction(String mcAction){mcTableAction(mcAction,null);}
    private void mcTableAction(String mcAction,String mcValue){if(mcEditorReady)mcRichEditor.getEngine().executeScript("window.mcStudio.mcTableAction("+mcJsonString(mcAction)+","+(mcValue==null?"null":mcJsonString(mcValue))+")");}
    private void mcImageAction(String mcAction){if(mcEditorReady)mcRichEditor.getEngine().executeScript("window.mcStudio.mcImageAction("+mcJsonString(mcAction)+")");}
    private <T extends Control> T mcRibbonControl(T mcControl,String mcDescription){
        mcControl.setTooltip(new Tooltip(mcDescription));mcControl.setAccessibleText(mcDescription.split(" — ")[0]);mcControl.getStyleClass().add("ribbon-btn");mcControl.setFocusTraversable(false);return mcControl;
    }
    private <T> void mcRibbonChoice(ComboBox<T> mcChoice,Feather mcIcon){
        mcChoice.setButtonCell(new ListCell<>(){@Override protected void updateItem(T mcValue,boolean mcEmpty){super.updateItem(mcValue,mcEmpty);setText(mcValue==null?mcChoice.getPromptText():mcValue.toString());setGraphic(new FontIcon(mcIcon));}});
    }
    private Button mcRibbonButton(String mcText,Feather mcIcon,String mcDescription,Runnable mcAction){
        Button mcControl=mcRibbonControl(new Button(mcText,new FontIcon(mcIcon)),mcDescription);mcControl.setOnAction(mcE->mcSafe(mcAction));return mcControl;
    }
    private Button mcSelectionFormatButton(String mcState,Feather mcIcon,String mcDescription,Runnable mcAction){
        Button mcButton=mcRibbonButton("",mcIcon,mcDescription,()->{if(!mcUpdatingToolbarFromSelection)mcAction.run();});
        mcSelectionFormatButtons.put(mcState,mcButton);return mcButton;
    }
    private ToggleButton mcRibbonToggle(String mcText,Feather mcIcon,String mcDescription,boolean mcSelected,Runnable mcAction){
        ToggleButton mcControl=mcRibbonControl(new ToggleButton(mcText,new FontIcon(mcIcon)),mcDescription);mcControl.setSelected(mcSelected);mcControl.setOnAction(mcE->mcSafe(mcAction));return mcControl;
    }
    private MenuButton mcRibbonMenu(String mcText,Feather mcIcon,String mcDescription){return mcRibbonControl(new MenuButton(mcText,new FontIcon(mcIcon)),mcDescription);}
    private MenuItem mcRibbonItem(String mcText,Feather mcIcon,String mcDescription,Runnable mcAction){
        Label mcLabel=new Label(mcText,new FontIcon(mcIcon));mcLabel.setMaxWidth(Double.MAX_VALUE);Tooltip mcTooltip=new Tooltip(mcDescription);Tooltip.install(mcLabel,mcTooltip);
        CustomMenuItem mcItem=new CustomMenuItem(mcLabel);mcItem.setText(mcText);mcItem.getProperties().put("ribbon-tooltip",mcTooltip);mcItem.setOnAction(mcE->mcSafe(mcAction));return mcItem;
    }
    private VBox mcRibbonGroup(String mcTitle,Node... mcControls){
        HBox mcRow=new HBox(3,mcControls);mcRow.setAlignment(Pos.CENTER_LEFT);Label mcCaption=new Label(mcTitle);mcCaption.getStyleClass().add("ribbon-caption");
        VBox mcGroup=new VBox(3,mcRow,mcCaption);mcGroup.setAlignment(Pos.CENTER);mcGroup.getStyleClass().add("ribbon-group");return mcGroup;
    }
    private HBox mcRibbonRow(Node... mcGroups){
        HBox mcRow=new HBox(3);mcRow.setAlignment(Pos.CENTER_LEFT);for(Node mcGroup:mcGroups){if(!mcRow.getChildren().isEmpty()){Separator mcSeparator=new Separator(Orientation.VERTICAL);mcSeparator.getStyleClass().add("ribbon-sep");mcRow.getChildren().add(mcSeparator);}mcRow.getChildren().add(mcGroup);}return mcRow;
    }
    private MenuButton mcTableGrid(){
        MenuButton mcPicker=mcRibbonMenu("Tabla",Feather.GRID,"Insertar tabla — Agrega una tabla editable de filas y columnas en la posición del cursor");
        GridPane mcGrid=new GridPane();mcGrid.setHgap(2);mcGrid.setVgap(2);Label mcDimensions=new Label("Seleccione filas y columnas");VBox mcContent=new VBox(5,mcDimensions,mcGrid);
        for(int mcRow=1;mcRow<=8;mcRow++)for(int mcCol=1;mcCol<=8;mcCol++){
            final int mcRows=mcRow,mcCols=mcCol;Button mcSquare=mcRibbonControl(new Button(),"Insertar tabla — "+mcCols+" columnas × "+mcRows+" filas");mcSquare.getStyleClass().add("ribbon-grid-cell");mcSquare.setMinSize(21,21);mcSquare.setPrefSize(21,21);mcSquare.setMaxSize(21,21);
            mcSquare.setOnMouseEntered(mcE->{mcDimensions.setText(mcCols+" columnas × "+mcRows+" filas");for(Node mcN:mcGrid.getChildren()){int mcC=GridPane.getColumnIndex(mcN)==null?0:GridPane.getColumnIndex(mcN),mcR=GridPane.getRowIndex(mcN)==null?0:GridPane.getRowIndex(mcN);if(mcC<mcCols&&mcR<mcRows){if(!mcN.getStyleClass().contains("accent"))mcN.getStyleClass().add("accent");}else mcN.getStyleClass().remove("accent");}});
            mcSquare.setOnAction(mcE->{mcPicker.hide();if(mcEditorReady)mcRichEditor.getEngine().executeScript("window.mcStudio.mcInsertTable("+mcCols+","+mcRows+")");});mcGrid.add(mcSquare,mcCol-1,mcRow-1);
        }
        mcPicker.getItems().add(new CustomMenuItem(mcContent,false));return mcPicker;
    }
    private Node mcBuildEditorTools(){
        mcFontChoice.setEditable(false);mcFontChoice.setPrefWidth(130);mcFontChoice.getItems().setAll(Font.getFamilies().stream().filter(mcF->!mcF.toLowerCase().contains("symbol")).toList());mcFontChoice.setPromptText("Fuente");mcFontChoice.setValue("Arial");mcRibbonControl(mcFontChoice,"Fuente — Cambia la tipografía del texto seleccionado");mcRibbonChoice(mcFontChoice,Feather.TYPE);mcFontChoice.setOnAction(mcE->{if(!mcUpdatingToolbarFromSelection&&mcFontChoice.getValue()!=null)mcEditorCommand("fontName",mcFontChoice.getValue());});
        ComboBox<Integer> mcFontSize=mcRibbonControl(mcFontSizeChoice,"Tamaño de fuente — Cambia el tamaño del texto seleccionado en puntos");mcFontSize.getItems().setAll(8,9,10,11,12,14,16,18,20,24,28,36,48,72);mcRibbonChoice(mcFontSize,Feather.TYPE);mcFontSize.setValue(12);mcFontSize.setPrefWidth(80);mcFontSize.setOnAction(mcE->{if(!mcUpdatingToolbarFromSelection&&mcEditorReady&&mcFontSize.getValue()!=null)mcRichEditor.getEngine().executeScript("window.mcStudio.mcFontSize("+mcFontSize.getValue()+")");});
        ColorPicker mcColor=mcRibbonControl(mcEditorTextColor,"Color de texto — Aplica un color al texto seleccionado");mcColor.setPrefWidth(36);mcColor.setOnAction(mcE->mcEditorCommand("foreColor","#"+mcColor.getValue().toString().substring(2,8)));
        ColorPicker mcHighlight=mcRibbonControl(mcEditorHighlightColor,"Resaltado — Destaca el texto seleccionado con un color de fondo");mcHighlight.setPrefWidth(36);mcHighlight.setOnAction(mcE->mcEditorCommand("hiliteColor","#"+mcHighlight.getValue().toString().substring(2,8)));
        HBox mcTextColor=new HBox(2,new Label("",new FontIcon(Feather.TYPE)),mcColor),mcTextHighlight=new HBox(2,new Label("",new FontIcon(Feather.EDIT_3)),mcHighlight);mcTextColor.setAlignment(Pos.CENTER_LEFT);mcTextHighlight.setAlignment(Pos.CENTER_LEFT);
        HBox mcHome=mcRibbonRow(
            mcRibbonGroup("Historial",mcRibbonButton("",Feather.ROTATE_CCW,"Deshacer — Revierte la última edición del documento",()->mcEditorCommand("undo")),mcRibbonButton("",Feather.ROTATE_CW,"Rehacer — Recupera la última edición deshecha",()->mcEditorCommand("redo"))),
            mcRibbonGroup("Fuente",mcFontChoice,mcFontSize,mcSelectionFormatButton("bold",Feather.BOLD,"Negrita — Resalta el texto seleccionado con trazo grueso",()->mcEditorCommand("bold")),mcSelectionFormatButton("italic",Feather.ITALIC,"Cursiva — Inclina el texto seleccionado",()->mcEditorCommand("italic")),mcSelectionFormatButton("underline",Feather.UNDERLINE,"Subrayado — Traza una línea bajo el texto seleccionado",()->mcEditorCommand("underline")),mcTextColor,mcTextHighlight),
            mcRibbonGroup("Párrafo",mcSelectionFormatButton("left",Feather.ALIGN_LEFT,"Alinear izquierda — Alinea el párrafo al margen izquierdo",()->mcEditorCommand("justifyLeft")),mcSelectionFormatButton("center",Feather.ALIGN_CENTER,"Centrar — Centra el párrafo entre los márgenes",()->mcEditorCommand("justifyCenter")),mcSelectionFormatButton("right",Feather.ALIGN_RIGHT,"Alinear derecha — Alinea el párrafo al margen derecho",()->mcEditorCommand("justifyRight")),mcSelectionFormatButton("justify",Feather.ALIGN_JUSTIFY,"Justificar — Distribuye el párrafo entre ambos márgenes",()->mcEditorCommand("justifyFull")),mcSelectionFormatButton("bulletList",Feather.LIST,"Viñetas — Activa o quita las viñetas de los párrafos seleccionados",()->mcEditorScript("mcList('bullet')")),mcSelectionFormatButton("numberedList",Feather.HASH,"Numeración — Activa o quita la numeración de los párrafos seleccionados",()->mcEditorScript("mcList('decimal')"))));
        MenuButton mcImage=mcRibbonMenu("Imagen",Feather.IMAGE,"Insertar imagen — Agrega una imagen en la posición del cursor");mcImage.getItems().add(mcRibbonItem("Desde este dispositivo...",Feather.UPLOAD,"Desde este dispositivo — Selecciona una imagen PNG o JPG del equipo",()->mcInsertImage(false)));
        MenuButton mcHeader=mcRibbonMenu("Encabezado",Feather.ARROW_UP_CIRCLE,"Encabezado — Agrega texto o una imagen al encabezado del documento");mcHeader.getItems().addAll(mcRibbonItem("Agregar encabezado",Feather.TYPE,"Agregar encabezado — Inserta texto en la parte superior del documento",()->mcAddHeaderFooter(true)),mcRibbonItem("Imagen en encabezado",Feather.IMAGE,"Imagen en encabezado — Agrega una imagen a la cabecera del documento",()->mcInsertImage(true)));
        MenuButton mcFooter=mcRibbonMenu("Pie de página",Feather.ARROW_DOWN_CIRCLE,"Pie de página — Agrega texto, imágenes o numeración al pie del documento");mcFooter.getItems().addAll(mcRibbonItem("Agregar pie",Feather.TYPE,"Agregar pie — Inserta texto al pie del documento",()->mcAddHeaderFooter(false)),mcRibbonItem("Imagen en pie",Feather.IMAGE,"Imagen en pie — Agrega una imagen al pie del documento",this::mcInsertImageInFooter),mcRibbonItem("Número de página",Feather.HASH,"Número de página — Inserta el campo de numeración de páginas en el pie",this::mcInsertPageNumber));
        HBox mcInsert=mcRibbonRow(mcRibbonGroup("Elementos",mcTableGrid(),mcImage,mcRibbonButton("Salto",Feather.FILE_PLUS,"Salto de página — Comienza una página nueva en el documento",this::mcInsertPageBreak),mcRibbonButton("Línea",Feather.MINUS,"Línea separadora — Inserta una línea horizontal en la posición del cursor",()->mcEditorScript("mcInsertRule()")),mcRibbonButton("Variable",Feather.CODE,"Insertar variable — Inserta la variable seleccionada en la posición del cursor",this::mcInsertSelected)),mcRibbonGroup("Documento",mcHeader,mcFooter));
        mcPaperChoice.getItems().setAll("A4","Carta");mcPaperChoice.setValue("A4");mcPaperChoice.setPrefWidth(85);mcRibbonChoice(mcPaperChoice,Feather.FILE);mcRibbonControl(mcPaperChoice,"Papel — Selecciona el tamaño A4 o Carta del documento");mcPaperChoice.setOnAction(mcE->{if(!mcReadingPageSize)mcSetPaperSize();});
        mcZoomChoice.getItems().setAll("Ancho","Página","75%","100%","125%","150%","200%");mcZoomChoice.setValue("Ancho");mcZoomChoice.setPrefWidth(110);mcRibbonChoice(mcZoomChoice,Feather.ZOOM_IN);mcRibbonControl(mcZoomChoice,"Zoom — Ajusta el documento al ancho, a la página o a un porcentaje");mcZoomChoice.setOnAction(mcE->{mcZoomMode=mcZoomChoice.getValue();mcUpdateZoom();});
        mcGuidesView=mcRibbonToggle("Márgenes",Feather.LAYOUT,"Guías de página — Muestra u oculta los límites de los márgenes",false,()->mcUpdateEditorView());
        mcRulersView=mcRibbonToggle("Regla",Feather.EYE,"Regla — Muestra u oculta las reglas del documento",true,()->mcUpdateEditorView());
        mcVariablesView=mcRibbonToggle("Variables",Feather.SLIDERS,"Panel de Variables — Muestra u oculta el panel lateral de variables",true,()->mcPaletteToggle.fire());
        HBox mcView=mcRibbonRow(mcRibbonGroup("Zoom",mcRibbonButton("",Feather.ZOOM_OUT,"Alejar — Reduce el zoom del documento",()->mcAdjustEditorZoom(-.1)),mcZoomPercent,mcRibbonButton("",Feather.ZOOM_IN,"Acercar — Aumenta el zoom del documento",()->mcAdjustEditorZoom(.1)),mcRibbonButton("100%",Feather.MAXIMIZE_2,"Restablecer zoom — Muestra el documento al 100%",()->mcSetEditorZoom(1)),mcZoomChoice),mcRibbonGroup("Visualización",mcGuidesView,mcRulersView,mcVariablesView),mcRibbonGroup("Papel",mcPaperChoice,mcPageIndicator));
        TabPane mcTools=new TabPane();mcTools.getStyleClass().add("editor-ribbon");Map<String,Node> mcSections=new LinkedHashMap<>();mcSections.put("Inicio",mcHome);mcSections.put("Insertar",mcInsert);mcSections.put("Vista",mcView);
        for(var mcEntry:mcSections.entrySet()){ScrollPane mcStrip=new ScrollPane(mcEntry.getValue());mcStrip.setFitToHeight(true);mcStrip.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);mcStrip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);mcStrip.getStyleClass().add("ribbon-strip");Tab mcTab=new Tab(mcEntry.getKey(),mcStrip);mcTab.getStyleClass().add("ribbon-tab");mcTab.setTooltip(new Tooltip(mcEntry.getKey()+" — Herramientas de "+mcEntry.getKey().toLowerCase()));mcTab.setClosable(false);mcTools.getTabs().add(mcTab);}
        mcTools.setMinHeight(94);mcTools.setPrefHeight(94);mcTools.setMaxHeight(94);
        mcTableTools=new HBox(4);mcTableTools.getStyleClass().add("ribbon-context");mcTableTools.setVisible(false);mcTableTools.setManaged(false);
        MenuButton mcBorders=mcRibbonMenu("Bordes",Feather.GRID,"Bordes — Selecciona los bordes visibles de la tabla");for(String[] mcB:new String[][]{{"Todos los bordes","all"},{"Sin bordes","none"},{"Bordes exteriores","outer"},{"Bordes interiores","inner"}})mcBorders.getItems().add(mcRibbonItem(mcB[0],Feather.GRID,mcB[0]+" — Aplica esta disposición de bordes a la tabla",()->mcTableAction("borders",mcB[1])));
        MenuButton mcBorderStyle=mcRibbonMenu("Estilo",Feather.EDIT_3,"Estilo de bordes — Selecciona líneas sencillas, dobles o discontinuas");for(String[] mcStyle:new String[][]{{"Sencillo","solid"},{"Doble","double"},{"Discontinuo","dashed"}})mcBorderStyle.getItems().add(mcRibbonItem(mcStyle[0],Feather.MINUS,mcStyle[0]+" — Cambia el trazo de los bordes de la tabla",()->mcTableAction("borderStyle",mcStyle[1])));
        ComboBox<String> mcThickness=mcRibbonControl(new ComboBox<>(),"Grosor de bordes — Cambia el grosor de las líneas de la tabla");mcThickness.getItems().setAll("1","2","3");mcThickness.setValue("1");mcThickness.setPrefWidth(65);mcThickness.setOnAction(mcE->mcTableAction("borderWidth",mcThickness.getValue()));
        ColorPicker mcBorderColor=mcRibbonControl(new ColorPicker(Color.GRAY),"Color de bordes — Cambia el color de las líneas de la tabla");mcBorderColor.setPrefWidth(36);mcBorderColor.setOnAction(mcE->mcTableAction("borderColor","#"+mcBorderColor.getValue().toString().substring(2,8)));
        MenuButton mcRemove=mcRibbonMenu("Eliminar",Feather.TRASH_2,"Eliminar de tabla — Quita la fila, columna o tabla seleccionada");for(String[] mcA:new String[][]{{"Eliminar fila","deleteRow"},{"Eliminar columna","deleteColumn"},{"Eliminar tabla completa","deleteTable"}})mcRemove.getItems().add(mcRibbonItem(mcA[0],Feather.TRASH_2,mcA[0]+" — Elimina este elemento de la tabla",()->mcTableAction(mcA[1])));
        mcTableTools.getChildren().addAll(new Label("TABLA"),mcBorders,mcBorderStyle,mcThickness,mcBorderColor,mcRemove);
        mcImageTools=new HBox(4,new Label("IMAGEN"),mcRibbonButton("Reducir",Feather.MINUS,"Reducir imagen — Disminuye el tamaño de la imagen seleccionada",()->mcImageAction("smaller")),mcRibbonButton("Ampliar",Feather.PLUS,"Ampliar imagen — Aumenta el tamaño de la imagen seleccionada",()->mcImageAction("larger")),mcRibbonButton("",Feather.ALIGN_LEFT,"Imagen a la izquierda — Alinea la imagen al margen izquierdo",()->mcImageAction("left")),mcRibbonButton("",Feather.ALIGN_CENTER,"Centrar imagen — Centra la imagen entre los márgenes",()->mcImageAction("center")),mcRibbonButton("",Feather.ALIGN_RIGHT,"Imagen a la derecha — Alinea la imagen al margen derecho",()->mcImageAction("right")),mcRibbonButton("Eliminar",Feather.TRASH_2,"Eliminar imagen — Quita la imagen seleccionada del documento",()->mcImageAction("delete")));mcImageTools.getStyleClass().add("ribbon-context");mcImageTools.setVisible(false);mcImageTools.setManaged(false);
        return new VBox(2,mcTools,mcTableTools,mcImageTools);
    }
    private void mcEditorScript(String mcCommand){if(mcEditorReady)mcRichEditor.getEngine().executeScript("window.mcStudio."+mcCommand);}
    private void mcUpdateEditorView(){if(mcEditorReady){mcEditorScript("mcView('guides',"+mcGuidesView.isSelected()+")");mcEditorScript("mcView('rulers',"+mcRulersView.isSelected()+")");}}
    private void mcAdjustEditorZoom(double mcChange){mcSetEditorZoom(mcEditorZoom+mcChange);}
    private void mcSetEditorZoom(double mcScale){mcZoomChoice.setValue(Math.round(Math.max(.25,Math.min(3,mcScale))*100)+"%");}
    private void mcUpdateEditorSaveStatus(){mcEditorSaveStatus.setText(mcCurrentTemplate==null?"Sin plantilla":mcDirty?"Cambios sin guardar":"Guardado");}
    private void mcSetLineSpacing(double mcSpacing){if(mcSelectedParagraph==null)return;mcSelectedParagraph.setSpacingBetween(mcSpacing);mcChanged();}
    private void mcAdjustParagraphSpacing(boolean mcBefore,int mcChange){if(mcSelectedParagraph==null)return;if(mcBefore)mcSelectedParagraph.setSpacingBefore(Math.max(0,mcSelectedParagraph.getSpacingBefore()+mcChange));else mcSelectedParagraph.setSpacingAfter(Math.max(0,mcSelectedParagraph.getSpacingAfter()+mcChange));mcChanged();}
    private void mcAdjustIndent(int mcChange){if(mcSelectedParagraph==null)return;mcSelectedParagraph.setIndentationLeft(Math.max(0,mcSelectedParagraph.getIndentationLeft()+mcChange));mcChanged();}
    private void mcSetHeading(String mcStyle){if(mcSelectedParagraph==null)return;mcSelectedParagraph.setStyle(mcStyle);mcChanged();}
    private void mcClearFormat(){if(mcSelectedRun==null)return;mcSelectedRun.setBold(false);mcSelectedRun.setItalic(false);mcSelectedRun.setUnderline(UnderlinePatterns.NONE);mcSelectedRun.setStrikeThrough(false);mcSelectedRun.setColor("000000");mcSelectedRun.setTextHighlightColor("none");if(mcSelectedRun.getCTR().isSetRPr()){var mcProps=mcSelectedRun.getCTR().getRPr();while(mcProps.sizeOfShdArray()>0)mcProps.removeShd(0);}mcChanged();}
    private void mcAdjustTable(boolean mcRow,boolean mcAdd){if(mcSelectedParagraph==null||!(mcSelectedParagraph.getBody() instanceof XWPFTableCell mcCell))return;XWPFTable mcTable=mcCell.getTableRow().getTable();if(mcRow){if(mcAdd)mcTable.createRow();else if(mcTable.getNumberOfRows()>1)mcTable.removeRow(mcTable.getRows().indexOf(mcCell.getTableRow()));}else{int mcColumn=mcCell.getTableRow().getTableCells().indexOf(mcCell);for(XWPFTableRow mcR:mcTable.getRows()){if(mcAdd)mcR.addNewTableCell();else if(mcR.getTableCells().size()>1)mcR.removeCell(Math.min(mcColumn,mcR.getTableCells().size()-1));}}mcChanged();}
    private void mcChanged(){try{mcSyncEditor();}catch(Exception mcEx){mcError(mcEx);return;}mcDirty=true;mcUpdateEditorSaveStatus();mcStatus.setText("Cambios sin guardar");mcRenderDocument();}
    private void mcUpdateZoom(){double mcViewport=mcRichEditor.getWidth();if(mcViewport<=0||!mcEditorReady)return;double mcScale=switch(mcZoomMode){case "Página"->Math.min((mcViewport-56)/mcPageWidth,(mcRichEditor.getHeight()-80)/mcPageHeight);case "Ancho"->(mcViewport-56)/mcPageWidth;default->Double.parseDouble(mcZoomMode.replace("%",""))/100.0;};mcScale=Math.max(.25,Math.min(3,mcScale));mcEditorZoom=mcScale;mcZoomPercent.setText(Math.round(mcScale*100)+"%");mcRichEditor.getEngine().executeScript("window.mcStudio.mcZoom("+mcScale+")");mcUpdatePageIndicator();}
    private void mcUpdatePageIndicator(){if(!mcEditorReady)return;try{int mcCurrent=((Number)mcRichEditor.getEngine().executeScript("window.mcStudio.mcCurrentPage()")).intValue(),mcCount=((Number)mcRichEditor.getEngine().executeScript("window.mcStudio.mcPageCount()")).intValue();mcPageIndicator.setText("Página "+Math.min(mcCurrent,mcCount)+" de "+mcCount);}catch(Exception mcIgnored){}}
    private void mcSetPaperSize(){if(mcDocument==null)return;mcPageWidth="Carta".equals(mcPaperChoice.getValue())?816:794;mcPageHeight="Carta".equals(mcPaperChoice.getValue())?1056:1123;var mcSect=mcDocument.getDocument().getBody().isSetSectPr()?mcDocument.getDocument().getBody().getSectPr():mcDocument.getDocument().getBody().addNewSectPr();var mcSize=mcSect.isSetPgSz()?mcSect.getPgSz():mcSect.addNewPgSz();mcSize.setW(java.math.BigInteger.valueOf(Math.round(mcPageWidth*15)));mcSize.setH(java.math.BigInteger.valueOf(Math.round(mcPageHeight*15)));mcChanged();}
    private void mcToggleOrientation(){if(mcDocument==null)return;double mcW=mcPageWidth;mcPageWidth=mcPageHeight;mcPageHeight=mcW;var mcSect=mcDocument.getDocument().getBody().isSetSectPr()?mcDocument.getDocument().getBody().getSectPr():mcDocument.getDocument().getBody().addNewSectPr();var mcSize=mcSect.isSetPgSz()?mcSect.getPgSz():mcSect.addNewPgSz();mcSize.setW(java.math.BigInteger.valueOf(Math.round(mcPageWidth*15)));mcSize.setH(java.math.BigInteger.valueOf(Math.round(mcPageHeight*15)));mcChanged();}
    private void mcSetMargins(int mcTwips){if(mcDocument==null)return;var mcSect=mcDocument.getDocument().getBody().isSetSectPr()?mcDocument.getDocument().getBody().getSectPr():mcDocument.getDocument().getBody().addNewSectPr();var mcMargin=mcSect.isSetPgMar()?mcSect.getPgMar():mcSect.addNewPgMar();long mcN=mcTwips;mcMargin.setLeft(java.math.BigInteger.valueOf(mcN));mcMargin.setRight(java.math.BigInteger.valueOf(mcN));mcMargin.setTop(java.math.BigInteger.valueOf(mcN));mcMargin.setBottom(java.math.BigInteger.valueOf(mcN));mcChanged();}
    private void mcInsertTable(){if(mcDocument==null)return;mcDocument.createTable(2,2);mcChanged();}
    private void mcInsertPageBreak(){if(mcDocument==null)return;XWPFParagraph mcP=mcDocument.createParagraph();mcP.setPageBreak(true);mcChanged();}
    private void mcAddHeaderFooter(boolean mcHeader){if(mcDocument==null)return;XWPFHeaderFooter mcPart=mcHeader?(mcDocument.getHeaderList().isEmpty()?mcDocument.createHeader(HeaderFooterType.DEFAULT):mcDocument.getHeaderList().get(0)):(mcDocument.getFooterList().isEmpty()?mcDocument.createFooter(HeaderFooterType.DEFAULT):mcDocument.getFooterList().get(0));mcPart.createParagraph().createRun().setText(mcHeader?"Encabezado":"Pie de página");mcChanged();}
    private void mcInsertPageNumber(){if(mcDocument==null)return;XWPFFooter mcFooter=mcDocument.getFooterList().isEmpty()?mcDocument.createFooter(HeaderFooterType.DEFAULT):mcDocument.getFooterList().get(0);XWPFRun mcRun=mcFooter.createParagraph().createRun();mcRun.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);XWPFRun mcInstruction=mcFooter.getParagraphs().get(mcFooter.getParagraphs().size()-1).createRun();mcInstruction.getCTR().addNewInstrText().setStringValue(" PAGE ");XWPFRun mcEnd=mcFooter.getParagraphs().get(mcFooter.getParagraphs().size()-1).createRun();mcEnd.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);mcChanged();}
    private void mcInsertImage(boolean mcHeader){if(mcDocument==null)return;FileChooser mcChooser=new FileChooser();mcChooser.setTitle("Insertar imagen");mcChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG/JPG","*.png","*.jpg","*.jpeg"));File mcFile=mcChooser.showOpenDialog(mcStage);if(mcFile==null)return;try(InputStream mcIn=Files.newInputStream(mcFile.toPath())){Image mcSource=new Image(mcFile.toURI().toString());if(mcSource.isError()||mcSource.getWidth()<=0)throw new IOException("No se pudo leer la imagen.");double mcWidth=Math.min(300,mcSource.getWidth()*72/96),mcHeight=mcWidth*mcSource.getHeight()/mcSource.getWidth();if(mcHeight>600){mcWidth*=600/mcHeight;mcHeight=600;}if(!mcHeader&&mcEditorReady){String mcMime=mcFile.getName().toLowerCase().endsWith(".png")?"image/png":"image/jpeg";String mcUri="data:"+mcMime+";base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(mcFile.toPath()));if(Boolean.TRUE.equals(mcRichEditor.getEngine().executeScript("window.mcStudio.mcInsertImage("+mcJsonString(mcUri)+","+Math.round(mcWidth*96/72)+","+Math.round(mcHeight*96/72)+")")))return;throw new IllegalArgumentException("Coloque el cursor en el documento antes de insertar la imagen.");}XWPFHeader mcH=mcDocument.getHeaderList().isEmpty()?mcDocument.createHeader(HeaderFooterType.DEFAULT):mcDocument.getHeaderList().get(0);XWPFParagraph mcP=mcH.createParagraph();int mcType=mcFile.getName().toLowerCase().endsWith(".png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;mcP.createRun().addPicture(mcIn,mcType,mcFile.getName(),Units.toEMU(mcWidth),Units.toEMU(mcHeight));mcChanged();}catch(Exception mcE){mcError(mcE);}}
    private void mcInsertImageInFooter(){if(mcDocument==null)return;FileChooser mcChooser=new FileChooser();mcChooser.setTitle("Imagen en pie de página");mcChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG/JPG","*.png","*.jpg","*.jpeg"));File mcFile=mcChooser.showOpenDialog(mcStage);if(mcFile==null)return;try(InputStream mcIn=Files.newInputStream(mcFile.toPath())){Image mcSource=new Image(mcFile.toURI().toString());if(mcSource.isError()||mcSource.getWidth()<=0)throw new IOException("No se pudo leer la imagen.");double mcWidth=Math.min(300,mcSource.getWidth()*72/96),mcHeight=mcWidth*mcSource.getHeight()/mcSource.getWidth();XWPFFooter mcFooter=mcDocument.getFooterList().isEmpty()?mcDocument.createFooter(HeaderFooterType.DEFAULT):mcDocument.getFooterList().get(0);int mcType=mcFile.getName().toLowerCase().endsWith(".png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;mcFooter.createParagraph().createRun().addPicture(mcIn,mcType,mcFile.getName(),Units.toEMU(mcWidth),Units.toEMU(mcHeight));mcChanged();}catch(Exception mcEx){mcError(mcEx);}}
    private void mcResizePicture(XWPFRun mcRun,XWPFPicture mcPicture,double mcFactor){var mcInline=mcRun.getCTR().getDrawingArray(0).getInlineArray(0);if(mcInline.getExtent()==null)return;long mcWidth=Math.max(Units.toEMU(20),Math.round(mcInline.getExtent().getCx()*mcFactor));long mcHeight=Math.max(Units.toEMU(20),Math.round(mcInline.getExtent().getCy()*mcFactor));mcInline.getExtent().setCx(mcWidth);mcInline.getExtent().setCy(mcHeight);var mcShape=mcPicture.getCTPicture().getSpPr();if(mcShape!=null&&mcShape.isSetXfrm()&&mcShape.getXfrm().isSetExt()){mcShape.getXfrm().getExt().setCx(mcWidth);mcShape.getXfrm().getExt().setCy(mcHeight);}mcChanged();}
    private boolean mcInsertToken(TextField mcField,String mcReference){
        if(mcCurrentTemplate==null){mcSaved("Primero cree o importe una plantilla");return false;}
        try{
            String mcKey=mcReference;
            if(mcReference.startsWith("definition:")){
                long mcId=Long.parseLong(mcReference.substring("definition:".length()));
                MCDefinition mcD=mcStore.mcDefinitions().stream().filter(mcX->mcX.mcId()==mcId).findFirst().orElseThrow(()->new IllegalArgumentException("Variable inexistente."));mcKey=mcD.mcKey();
                MCVariable mcBound=mcStore.mcVariables(mcCurrentTemplate.mcId()).stream().filter(mcV->mcV.mcKey().equals(mcD.mcKey())).findFirst().orElse(null);
                if(mcBound==null)mcStore.mcBindDefinition(mcCurrentTemplate.mcId(),mcId,false,mcStore.mcVariables(mcCurrentTemplate.mcId()).size()+1);
                else if(mcStore.mcDefinitionId(mcBound.mcId())!=mcId)throw new IllegalArgumentException("Esta plantilla ya utiliza '"+mcKey+"' con otra definición. Use otro identificador.");
            }
            mcField.insertText(mcField.getCaretPosition(),"{{"+mcKey+"}}");mcRenderDocument();mcDirty=true;mcSaved("Variable insertada · Guarde la plantilla");return true;
        }catch(Exception mcE){mcError(mcE);return false;}
    }

    private void mcRenderDocument(){mcSelectedText=null;if(mcDocument==null)return;
        mcPageWidth=794;mcPageHeight=1123;var mcSect=mcDocument.getDocument().getBody().getSectPr();if(mcSect!=null&&mcSect.isSetPgSz()){mcPageWidth=((Number)mcSect.getPgSz().getW()).doubleValue()/15.0;mcPageHeight=((Number)mcSect.getPgSz().getH()).doubleValue()/15.0;}mcReadingPageSize=true;mcPaperChoice.setValue(Math.abs(mcPageWidth-816)<15?"Carta":"A4");mcReadingPageSize=false;
        double mcLeft=mcSect!=null&&mcSect.isSetPgMar()&&mcSect.getPgMar().getLeft()!=null?((Number)mcSect.getPgMar().getLeft()).doubleValue()/15.0:72;
        double mcRight=mcSect!=null&&mcSect.isSetPgMar()&&mcSect.getPgMar().getRight()!=null?((Number)mcSect.getPgMar().getRight()).doubleValue()/15.0:mcLeft;
        double mcTop=mcSect!=null&&mcSect.isSetPgMar()&&mcSect.getPgMar().getTop()!=null?((Number)mcSect.getPgMar().getTop()).doubleValue()/15.0:72;
        double mcBottom=mcSect!=null&&mcSect.isSetPgMar()&&mcSect.getPgMar().getBottom()!=null?((Number)mcSect.getPgMar().getBottom()).doubleValue()/15.0:mcTop;
        mcRichModel=new MCContinuousDocxEditor(mcDocument,mcEditorPersonaNames);
        try(InputStream mcIn=Objects.requireNonNull(getClass().getResourceAsStream("/editor/document-editor.html"))){String mcShell=new String(mcIn.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);String mcHtml=mcShell.replace("__DOCUMENT_CONTENT__",mcRichModel.mcHtml());mcEditorLeft=mcLeft;mcEditorRight=mcRight;mcEditorTop=mcTop;mcEditorBottom=mcBottom;mcEditorReady=false;mcRichEditor.getEngine().loadContent(mcHtml);}catch(Exception mcEx){mcError(mcEx);}
    }
    private VBox mcNewPage(double mcMargin,double mcTop){VBox mcPage=new VBox(5);mcPage.getStyleClass().add("paper");mcPage.setPrefSize(mcPageWidth,mcPageHeight);mcPage.setMinSize(mcPageWidth,mcPageHeight);mcPage.setMaxWidth(mcPageWidth);mcPage.setPadding(new Insets(mcTop,mcMargin,Math.min(mcTop,72),mcMargin));VBox mcBody=new VBox(5);for(XWPFHeader mcHeader:mcDocument.getHeaderList())for(XWPFParagraph mcP:mcHeader.getParagraphs())mcPage.getChildren().add(mcRenderParagraph(mcP,true));mcPage.getChildren().add(mcBody);Region mcSpacer=new Region();VBox.setVgrow(mcSpacer,Priority.ALWAYS);mcPage.getChildren().add(mcSpacer);for(XWPFFooter mcFooter:mcDocument.getFooterList())for(XWPFParagraph mcP:mcFooter.getParagraphs())mcPage.getChildren().add(mcRenderParagraph(mcP,true));mcPaper.getChildren().add(mcPage);return mcBody;}
    private double mcEstimateHeight(Node mcNode){if(mcNode instanceof FlowPane mcLine){double mcWidth=Math.max(100,mcPageWidth-150);double mcLength=mcLine.getChildren().stream().mapToDouble(mcN->mcN instanceof TextField mcF?mcF.getPrefWidth():mcN instanceof ImageView mcI?mcI.getFitWidth():70).sum();return Math.max(30,Math.ceil(mcLength/mcWidth)*28);}return 40;}
    private Node mcRenderParagraph(XWPFParagraph mcP,boolean mcEditable){FlowPane mcLine=new FlowPane(2,3);mcLine.getStyleClass().add("doc-paragraph");mcLine.setAlignment(switch(mcP.getAlignment()){case CENTER->Pos.CENTER;case RIGHT->Pos.CENTER_RIGHT;default->Pos.CENTER_LEFT;});
        if(mcP.getRuns().isEmpty()&&mcEditable)mcP.createRun().setText("");
        for(XWPFRun mcRun:mcP.getRuns()){
            String mcRaw=mcRun.getText(0);if(mcRaw!=null){Matcher mcM=mcTOKEN.matcher(mcRaw);int mcAt=0;while(mcM.find()){if(mcM.start()>mcAt)mcAddTextSegment(mcLine,mcRun,mcP,mcRaw.substring(mcAt,mcM.start()),mcEditable);mcAddToken(mcLine,mcRun,mcM.group(1),mcEditable,mcM.start());mcAt=mcM.end();}if(mcAt<mcRaw.length()||mcRaw.isEmpty())mcAddTextSegment(mcLine,mcRun,mcP,mcRaw.substring(mcAt),mcEditable);}
            for(XWPFPicture mcPicture:mcRun.getEmbeddedPictures()){try{ImageView mcImage=new ImageView(new Image(new ByteArrayInputStream(mcPicture.getPictureData().getData())));mcImage.setPreserveRatio(true);mcImage.setFitWidth(Math.min(mcPageWidth-160,Math.max(80,mcPicture.getWidth()*96/72)));if(mcEditable&&mcRun.getEmbeddedPictures().size()==1&&mcRun.getCTR().sizeOfDrawingArray()==1&&mcRun.getCTR().getDrawingArray(0).sizeOfInlineArray()==1){MenuItem mcSmaller=new MenuItem("Reducir a 75 %"),mcLarger=new MenuItem("Ampliar a 125 %"),mcRemove=new MenuItem("Eliminar imagen");mcSmaller.setOnAction(mcE->mcResizePicture(mcRun,mcPicture,.75));mcLarger.setOnAction(mcE->mcResizePicture(mcRun,mcPicture,1.25));mcRemove.setOnAction(mcE->{mcRun.getCTR().removeDrawing(0);mcChanged();});mcImage.setOnContextMenuRequested(mcE->new ContextMenu(mcSmaller,mcLarger,mcRemove).show(mcImage,mcE.getScreenX(),mcE.getScreenY()));}mcLine.getChildren().add(mcImage);}catch(Exception mcIgnored){mcLine.getChildren().add(new Label("[Imagen no previsualizable]"));}}
        }
        if(mcLine.getChildren().isEmpty())mcLine.getChildren().add(new Label(" "));return mcLine;}
    private void mcAddTextSegment(FlowPane mcLine,XWPFRun mcRun,XWPFParagraph mcP,String mcText,boolean mcEditable){
        if(!mcEditable){Text mcT=new Text(mcText);mcT.setFont(Font.font(mcRun.getFontFamily()==null?"Serif":mcRun.getFontFamily(),mcRun.isBold()?FontWeight.BOLD:FontWeight.NORMAL,mcRun.getFontSize()>0?mcRun.getFontSize():13));mcLine.getChildren().add(mcT);return;}
        TextField mcField=new TextField(mcText);mcField.getStyleClass().add("run-field");mcField.setPrefWidth(Math.max(28,Math.min(700,mcText.length()*8+25)));mcField.setStyle("-fx-font-family:'"+(mcRun.getFontFamily()==null?"Serif":mcRun.getFontFamily())+"';-fx-font-size:"+(mcRun.getFontSize()>0?mcRun.getFontSize():13)+"px;"+(mcRun.isBold()?"-fx-font-weight:bold;":"")+(mcRun.isItalic()?"-fx-font-style:italic;":"")+(mcRun.getColor()!=null?"-fx-text-fill:#"+mcRun.getColor()+";":""));
        mcField.focusedProperty().addListener((mcO,mcA,mcB)->{if(mcB){mcSelectedRun=mcRun;mcSelectedParagraph=mcP;mcSelectedText=mcField;}});
        mcField.textProperty().addListener((mcO,mcA,mcB)->{mcField.setPrefWidth(Math.max(28,Math.min(700,mcB.length()*8+25)));StringBuilder mcAll=new StringBuilder();for(Node mcN:mcLine.getChildren()){if(mcN.getUserData()==mcRun){if(mcN instanceof TextField mcF)mcAll.append(mcF.getText());else if(mcN instanceof Label mcL)mcAll.append("{{").append(mcN.getProperties().get("key")).append("}}");}}mcRun.setText(mcAll.toString(),0);mcDirty=true;mcUpdateEditorSaveStatus();mcStatus.setText("Cambios sin guardar");});
        mcField.setUserData(mcRun);mcField.setOnDragOver(mcE->{if(mcE.getDragboard().hasString())mcE.acceptTransferModes(TransferMode.COPY,TransferMode.MOVE);mcE.consume();});mcField.setOnDragDropped(mcE->{String mcKey=mcE.getDragboard().getString();if(mcKey!=null){if(mcField.getSkin() instanceof TextFieldSkin mcSkin)mcField.positionCaret(mcSkin.getIndex(mcE.getX(),mcE.getY()).getInsertionIndex());mcDragTargetRun=mcRun;mcDragTargetOffset=mcRunOffset(mcLine,mcField)+mcField.getCaretPosition();mcE.setDropCompleted(mcInsertToken(mcField,mcKey));}mcE.consume();});
        mcLine.getChildren().add(mcField);
    }
    private int mcRunOffset(FlowPane mcLine,Node mcTarget){int mcOffset=0;for(Node mcN:mcLine.getChildren()){if(mcN==mcTarget)break;if(mcN.getUserData()==mcTarget.getUserData()){if(mcN instanceof TextField mcF)mcOffset+=mcF.getText().length();else if(mcN instanceof Label mcL)mcOffset+=String.valueOf(mcL.getProperties().get("key")).length()+4;}}return mcOffset;}
    private void mcAddToken(FlowPane mcLine,XWPFRun mcRun,String mcKey,boolean mcEditable,int mcOffset){Label mcChip=new Label("⟦"+mcKey+"⟧");mcChip.getStyleClass().add("token");mcChip.setUserData(mcRun);mcChip.getProperties().put("key",mcKey);mcChip.setTooltip(new Tooltip("{{"+mcKey+"}} · Arrastre, copie o elimine esta aparición"));if(mcEditable){mcChip.setOnDragDetected(mcE->{mcDragSourceRun=mcRun;mcDragSourceToken="{{"+mcKey+"}}";mcDragSourceOffset=mcRunOffset(mcLine,mcChip);mcDragTargetRun=null;Dragboard mcDb=mcChip.startDragAndDrop(TransferMode.MOVE);ClipboardContent mcCc=new ClipboardContent();mcCc.putString(mcKey);mcDb.setContent(mcCc);mcE.consume();});mcChip.setOnDragDone(mcE->{if(mcE.getTransferMode()==TransferMode.MOVE&&mcDragSourceRun!=null){String mcTxt=mcDragSourceRun.getText(0);int mcPos=mcDragSourceOffset+(mcDragSourceRun==mcDragTargetRun&&mcDragTargetOffset<=mcDragSourceOffset?mcDragSourceToken.length():0);if(mcTxt!=null&&mcPos>=0&&mcPos+mcDragSourceToken.length()<=mcTxt.length()&&mcTxt.startsWith(mcDragSourceToken,mcPos))mcDragSourceRun.setText(mcTxt.substring(0,mcPos)+mcTxt.substring(mcPos+mcDragSourceToken.length()),0);mcDirty=true;mcRenderDocument();}mcDragSourceRun=null;mcDragTargetRun=null;mcE.consume();});MenuItem mcCopy=new MenuItem("Copiar variable"),mcCut=new MenuItem("Cortar variable"),mcDelete=new MenuItem("Eliminar aparición");mcCopy.setOnAction(mcE->mcCopyToken(mcKey));mcCut.setOnAction(mcE->{mcCopyToken(mcKey);mcRemoveToken(mcLine,mcChip,mcRun,mcKey);});mcDelete.setOnAction(mcE->mcRemoveToken(mcLine,mcChip,mcRun,mcKey));mcChip.setContextMenu(new ContextMenu(mcCopy,mcCut,mcDelete));mcChip.setOnMouseClicked(mcE->{if(mcE.getClickCount()==2)mcRemoveToken(mcLine,mcChip,mcRun,mcKey);});}mcLine.getChildren().add(mcChip);}
    private void mcCopyToken(String mcKey){ClipboardContent mcContent=new ClipboardContent();mcContent.putString("{{"+mcKey+"}}");Clipboard.getSystemClipboard().setContent(mcContent);}
    private void mcRemoveToken(FlowPane mcLine,Node mcChip,XWPFRun mcRun,String mcKey){String mcTxt=mcRun.getText(0);int mcAt=mcRunOffset(mcLine,mcChip);if(mcTxt!=null&&mcTxt.startsWith("{{"+mcKey+"}}",mcAt)){mcRun.setText(mcTxt.substring(0,mcAt)+mcTxt.substring(mcAt+mcKey.length()+4),0);mcChanged();}}
    private void mcStyleRun(String mcAction){if(mcSelectedRun==null)return;switch(mcAction){case "bold"->mcSelectedRun.setBold(!mcSelectedRun.isBold());case "italic"->mcSelectedRun.setItalic(!mcSelectedRun.isItalic());case "underline"->mcSelectedRun.setUnderline(mcSelectedRun.getUnderline()==UnderlinePatterns.NONE?UnderlinePatterns.SINGLE:UnderlinePatterns.NONE);case "strike"->mcSelectedRun.setStrikeThrough(!mcSelectedRun.isStrikeThrough());case "smaller"->mcSelectedRun.setFontSize(Math.max(8,mcSelectedRun.getFontSize()-1));case "larger"->mcSelectedRun.setFontSize((mcSelectedRun.getFontSize()>0?mcSelectedRun.getFontSize():12)+1);}mcChanged();}
    private void mcAlign(ParagraphAlignment mcA){if(mcSelectedParagraph==null)return;mcSelectedParagraph.setAlignment(mcA);mcDirty=true;mcRenderDocument();}
    private void mcAddParagraph(){if(mcDocument==null)return;mcDocument.createParagraph().createRun().setText("");mcDirty=true;mcRenderDocument();}

    private BorderPane mcCatalogPage;
    private void mcBuildCatalog(){mcCatalogPage=new BorderPane();mcCatalogPage.getStyleClass().add("page");VBox mcLeft=new VBox(8,mcKindSearch,mcKindList,mcButton("+ Nuevo tipo",this::mcNewKind),mcButton("Duplicar",this::mcDuplicateKind),mcButton("Eliminar",this::mcDeleteKind));mcLeft.getStyleClass().add("side-panel");mcLeft.setPrefWidth(250);VBox.setVgrow(mcKindList,Priority.ALWAYS);mcKindSearch.setPromptText("Buscar título o categoría");mcKindSearch.textProperty().addListener((mcO,mcA,mcB)->mcRefreshKinds());mcKindList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->mcLoadKind(mcB));mcCatalogPage.setLeft(mcLeft);
        mcKindDescription.setPrefRowCount(3);mcFieldList.setPrefHeight(230);mcKindActive.setSelected(true);
        HBox mcOrder=new HBox(8,mcButton("↑",()->mcMoveField(-1)),mcButton("↓",()->mcMoveField(1)),mcButton("Quitar campo",()->{MCVariable mcV=mcFieldList.getSelectionModel().getSelectedItem();if(mcV!=null)mcFieldList.getItems().remove(mcV);}));
        HBox mcAddField=new HBox(8,mcAddFieldChoice,mcButton("Añadir campo",()->{MCVariable mcV=mcAddFieldChoice.getValue();if(mcV!=null&&mcFieldList.getItems().stream().noneMatch(mcX->mcX.mcKey().equals(mcV.mcKey())))mcFieldList.getItems().add(mcV);}));
        mcFieldList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->{mcLoadingFieldRequired=true;mcFieldRequired.setDisable(mcB==null);mcFieldDefault.setDisable(mcB==null);mcFieldRequired.setSelected(mcB!=null&&mcKindRequiredChoices.getOrDefault(mcB.mcKey(),mcB.mcRequired()));mcFieldDefault.setText(mcB==null?"":mcKindDefaultChoices.getOrDefault(mcB.mcKey(),""));mcLoadingFieldRequired=false;});
        mcFieldRequired.setDisable(true);mcFieldRequired.setOnAction(mcE->{MCVariable mcV=mcFieldList.getSelectionModel().getSelectedItem();if(mcV!=null&&!mcLoadingFieldRequired)mcKindRequiredChoices.put(mcV.mcKey(),mcFieldRequired.isSelected());});
        mcFieldDefault.setDisable(true);mcFieldDefault.setPromptText("Valor predeterminado solo para este documento");mcFieldDefault.textProperty().addListener((mcO,mcA,mcB)->{MCVariable mcV=mcFieldList.getSelectionModel().getSelectedItem();if(mcV!=null&&!mcLoadingFieldRequired)mcKindDefaultChoices.put(mcV.mcKey(),mcB);});
        mcBindingGroup.setPromptText("Ej.: persona_1");mcBindingKind.getItems().setAll("Persona","Empresa");mcBindingKind.setValue("Persona");mcBindingKind.setOnAction(mcE->mcRefreshBindingFields());mcRefreshBindingFields();mcBindingList.setPrefHeight(150);
        HBox mcBindingRow=new HBox(8,mcBindingGroup,mcBindingKind,mcBindingField,mcBindingVariable);mcBindingGroup.setPrefWidth(130);mcBindingField.setPrefWidth(165);mcBindingVariable.setPrefWidth(190);
        VBox mcForm=new VBox(12,new Label("CONFIGURAR GENERACIÓN"),mcLabelled("Título",mcKindTitle),mcLabelled("Categoría opcional",mcKindCategory),mcLabelled("Descripción opcional",mcKindDescription),mcLabelled("Plantilla",mcKindTemplate),mcKindActive,new Label("CAMPOS DEL FORMULARIO · Ordene con ↑ ↓"),mcFieldList,mcFieldRequired,mcLabelled("Valor predeterminado del campo seleccionado",mcFieldDefault),mcOrder,mcAddField,mcButton("Guardar documento",this::mcSaveKind),new Separator(),new Label("VINCULACIÓN LOCAL DE PERSONAS Y EMPRESAS"),new Label("Cree un grupo por cada registro independiente. La generación guardará datos asociados con documento válido."),mcBindingRow,mcButton("Añadir asociación",this::mcSaveContactBinding),mcBindingList,mcButton("Eliminar asociación",this::mcDeleteContactBinding));mcForm.getStyleClass().add("catalog-form");ScrollPane mcScroll=new ScrollPane(mcForm);mcScroll.setFitToWidth(true);mcCatalogPage.setCenter(mcScroll);
        mcKindTemplate.setOnAction(mcE->mcLoadFieldsForTemplate());
    }
    private void mcShowCatalog(){mcShowEditor();mcRefreshKinds();mcEditorTabs.getSelectionModel().select(1);}
    private void mcRefreshKinds(){
        if(mcRefreshingGenerator)return;mcRefreshingGenerator=true;
        try{
            String mcQ=mcKindSearch.getText().toLowerCase(Locale.ROOT);mcKindList.getItems().setAll(mcStore.mcKinds().stream().filter(mcK->mcK.mcTitle().toLowerCase(Locale.ROOT).contains(mcQ)||(mcK.mcCategory()!=null&&mcK.mcCategory().toLowerCase(Locale.ROOT).contains(mcQ))).toList());
            MCTemplate mcSelected=mcGenerateTemplate.getValue();MCTemplateGroup mcGroup=mcGenerateGroup.getValue();String mcSearch=mcGenerateSearch.getText().trim();List<MCTemplate> mcChoices=new ArrayList<>();
            if(mcGroup!=null)for(MCTemplate mcTemplate:mcStore.mcTemplates())if(mcStore.mcInGroup(mcTemplate.mcId(),mcGroup.mcId())&&mcLibraryMatches(mcTemplate.mcName(),mcSearch))mcChoices.add(mcTemplate);
            mcGenerateTemplate.getItems().setAll(mcChoices);
            MCTemplate mcRestored=mcSelected==null?null:mcChoices.stream().filter(mcT->mcT.mcId()==mcSelected.mcId()).findFirst().orElse(null);mcGenerateTemplate.getSelectionModel().select(mcRestored);
            if(mcRestored==null){mcHistoricalTemplate=null;mcBuildDynamicForm();}
        }catch(Exception mcE){mcError(mcE);}finally{mcRefreshingGenerator=false;}
    }



    private MCDefinition mcDefinitionFor(MCVariable mcVariable,List<MCDefinition> mcDefinitions){long mcId=mcSafeDefinitionId(mcVariable);return mcDefinitions.stream().filter(mcD->mcD.mcId()==mcId).findFirst().orElse(null);}
    private List<MCVariable> mcVariablesForTemplate(MCTemplate mcTemplate)throws Exception{
        List<MCVariable> mcBound=mcStore.mcVariables(mcTemplate.mcId());Map<String,MCVariable> mcResult=new LinkedHashMap<>();List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();
        try(XWPFDocument mcDoc=MCDocumentEngine.mcOpen(mcTemplate.mcEdited())){for(String mcMarker:MCDocumentEngine.mcMarkers(mcDoc)){
            MCVariable mcExisting=mcBound.stream().filter(mcV->mcV.mcKey().equals(mcMarker)).findFirst().orElse(null);MCDefinition mcDefinition=mcExisting==null?mcDefinitions.stream().filter(mcD->mcD.mcKey().equals(MCDocumentEngine.mcBaseKey(mcMarker))).findFirst().orElse(null):mcDefinitionFor(mcExisting,mcDefinitions);
            if(mcDefinition!=null&&!mcDefinition.mcBindToClient()){String mcKey=mcDefinition.mcKey();mcResult.putIfAbsent(mcKey,new MCVariable(-mcDefinition.mcId(),mcTemplate.mcId(),mcKey,mcDefinition.mcLabel(),mcDefinition.mcType(),mcDefinition.mcDefaultValue(),false,mcResult.size()+1));}
            else if(mcExisting!=null)mcResult.putIfAbsent(mcMarker,mcExisting);
            else if(mcDefinition!=null)mcResult.putIfAbsent(mcMarker,new MCVariable(-mcDefinition.mcId(),mcTemplate.mcId(),mcMarker,mcDefinition.mcLabel(),mcDefinition.mcType(),mcDefinition.mcDefaultValue(),false,mcResult.size()+1));
            else mcResult.putIfAbsent(mcMarker,new MCVariable(0,mcTemplate.mcId(),mcMarker,MCDocumentEngine.mcBaseKey(mcMarker),"Texto corto","",false,mcResult.size()+1));
        }}return new ArrayList<>(mcResult.values());
    }
    private Map<String,String> mcDocumentValues(byte[] mcSource,Map<String,String> mcValues)throws Exception{
        Map<String,String> mcResult=new LinkedHashMap<>(mcValues);List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();MCTemplate mcTemplate=mcGenerateTemplate.getValue();List<MCVariable> mcBound=mcTemplate==null?List.of():mcStore.mcVariables(mcTemplate.mcId());
        try(XWPFDocument mcDoc=MCDocumentEngine.mcOpen(mcSource)){for(String mcMarker:MCDocumentEngine.mcMarkers(mcDoc))if(!MCDocumentEngine.mcPersonaKey(mcMarker).isEmpty()){
            MCVariable mcVariable=mcBound.stream().filter(mcV->mcV.mcKey().equals(mcMarker)).findFirst().orElse(null);MCDefinition mcDefinition=mcVariable==null?mcDefinitions.stream().filter(mcD->mcD.mcKey().equals(MCDocumentEngine.mcBaseKey(mcMarker))).findFirst().orElse(null):mcDefinitionFor(mcVariable,mcDefinitions);
            if(mcDefinition!=null&&!mcDefinition.mcBindToClient())mcResult.put(mcMarker,mcValues.getOrDefault(mcDefinition.mcKey(),mcDefinition.mcInitialValue()));
        }}return mcResult;
    }
    private void mcNewKind(){mcCurrentKind=null;mcKindRequiredChoices.clear();mcKindDefaultChoices.clear();mcKindList.getSelectionModel().clearSelection();mcKindTitle.clear();mcKindCategory.clear();mcKindDescription.clear();mcKindActive.setSelected(true);mcKindTemplate.setValue(mcCurrentTemplate);mcFieldList.getItems().clear();mcBindingList.getItems().clear();}
    private void mcLoadKind(MCKind mcK){if(mcK==null)return;mcCurrentKind=mcK;mcKindTitle.setText(mcK.mcTitle());mcKindCategory.setText(mcK.mcCategory());mcKindDescription.setText(mcK.mcDescription());mcKindActive.setSelected(mcK.mcActive());mcKindTemplate.getSelectionModel().select(mcKindTemplate.getItems().stream().filter(mcT->mcT.mcId()==mcK.mcTemplateId()).findFirst().orElse(null));mcLoadFieldsForTemplate();mcRefreshContactBindings();try{mcKindRequiredChoices.putAll(mcStore.mcKindRequirements(mcK.mcId()));mcKindDefaultChoices.clear();mcKindDefaultChoices.putAll(mcStore.mcKindDefaults(mcK.mcId()));}catch(Exception mcE){mcError(mcE);}Map<String,MCVariable> mcVars=new HashMap<>();for(MCVariable mcV:mcAddFieldChoice.getItems())mcVars.put(mcV.mcKey(),mcV);List<MCVariable> mcOrdered=new ArrayList<>();for(String mcKey:mcK.mcFields().split(",")){MCVariable mcV=mcVars.get(mcKey);if(mcV!=null)mcOrdered.add(mcV);}mcFieldList.getItems().setAll(mcOrdered);}
    private void mcLoadFieldsForTemplate(){MCTemplate mcT=mcKindTemplate.getValue();try{mcKindRequiredChoices.clear();mcKindDefaultChoices.clear();List<MCVariable> mcVars;if(mcT==null)mcVars=List.of();else{Set<String> mcUsed;try(XWPFDocument mcDoc=MCDocumentEngine.mcOpen(mcT.mcEdited())){mcUsed=MCDocumentEngine.mcMarkers(mcDoc);}mcVars=mcStore.mcVariables(mcT.mcId()).stream().filter(mcV->mcUsed.contains(mcV.mcKey())).toList();}mcFieldList.getItems().setAll(mcVars);mcAddFieldChoice.getItems().setAll(mcVars);List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();mcBindingVariable.getItems().setAll(mcVars.stream().filter(mcV->MCContactReuse.mcClientKey(mcV.mcKey(),mcDefinitions)).toList());mcRefreshContactBindings();}catch(Exception mcE){mcError(mcE);}}
    private void mcRefreshBindingFields(){mcBindingField.getItems().setAll("Empresa".equals(mcBindingKind.getValue())?List.of("Razón social","RUC","Domicilio fiscal","Representantes"):List.of("Nombres","Apellidos","Tipo de documento","Número de documento","Domicilio"));mcBindingField.getSelectionModel().selectFirst();}
    private void mcRefreshContactBindings(){MCTemplate mcT=mcKindTemplate.getValue();try{mcBindingList.getItems().setAll(mcT==null?List.of():mcData.mcContactBindings(mcT.mcId()));}catch(Exception mcE){mcError(mcE);}}
    private void mcSaveContactBinding(){MCTemplate mcT=mcKindTemplate.getValue();MCVariable mcV=mcBindingVariable.getValue();if(mcT==null||mcV==null||mcBindingGroup.getText().isBlank())throw new IllegalArgumentException("Seleccione plantilla, grupo, dato y variable.");try{String mcField=switch(mcBindingField.getValue()){case "Nombres","Razón social"->"given";case "Apellidos"->"family";case "Tipo de documento"->"document_type";case "Número de documento","RUC"->"number";case "Domicilio","Domicilio fiscal"->"address";case "Representantes"->"representatives";default->throw new IllegalArgumentException("Dato no admitido.");};mcData.mcSaveContactBinding(new MCContactBinding(0,mcT.mcId(),mcBindingGroup.getText().trim(),mcBindingKind.getValue(),mcField,mcV.mcKey()));mcRefreshContactBindings();mcSaved("Asociación local guardada");}catch(Exception mcE){mcError(mcE);}}
    private void mcDeleteContactBinding(){MCContactBinding mcB=mcBindingList.getSelectionModel().getSelectedItem();if(mcB==null)return;try{mcData.mcDeleteContactBinding(mcB.mcId());mcRefreshContactBindings();mcSaved("Asociación eliminada");}catch(Exception mcE){mcError(mcE);}}
    private void mcMoveField(int mcDelta){int mcI=mcFieldList.getSelectionModel().getSelectedIndex();int mcJ=mcI+mcDelta;if(mcI<0||mcJ<0||mcJ>=mcFieldList.getItems().size())return;Collections.swap(mcFieldList.getItems(),mcI,mcJ);mcFieldList.getSelectionModel().select(mcJ);}
    private void mcSaveKind(){MCTemplate mcT=mcKindTemplate.getValue();if(mcT==null||mcKindTitle.getText().isBlank())throw new IllegalArgumentException("Título y plantilla son obligatorios.");try{Set<String> mcMarkers;try(XWPFDocument mcDoc=MCDocumentEngine.mcOpen(mcT.mcEdited())){mcMarkers=MCDocumentEngine.mcMarkers(mcDoc);}Map<String,MCVariable> mcBound=new HashMap<>();for(MCVariable mcV:mcStore.mcVariables(mcT.mcId()))mcBound.put(mcV.mcKey(),mcV);Set<String> mcShown=new HashSet<>();for(MCVariable mcV:mcFieldList.getItems())mcShown.add(mcV.mcKey());for(String mcMarker:mcMarkers){MCVariable mcV=mcBound.get(mcMarker);if(mcV==null)throw new IllegalArgumentException("Variable sin vincular en la plantilla: "+mcMarker);if(!mcShown.contains(mcMarker)&&(mcKindDefaultChoices.getOrDefault(mcMarker,mcV.mcDefaultValue())==null||mcKindDefaultChoices.getOrDefault(mcMarker,mcV.mcDefaultValue()).isBlank()))throw new IllegalArgumentException("La variable '"+mcMarker+"' no está en el formulario ni tiene valor predeterminado.");}String mcFields=String.join(",",mcFieldList.getItems().stream().map(MCVariable::mcKey).toList());MCKind mcK=mcStore.mcSaveKind(new MCKind(mcCurrentKind==null?0:mcCurrentKind.mcId(),mcKindTitle.getText().trim(),mcKindCategory.getText().trim(),mcKindDescription.getText().trim(),mcT.mcId(),mcFields,mcKindActive.isSelected()));Map<String,Boolean> mcRequirements=new HashMap<>();for(MCVariable mcV:mcFieldList.getItems())mcRequirements.put(mcV.mcKey(),mcKindRequiredChoices.getOrDefault(mcV.mcKey(),mcV.mcRequired()));mcStore.mcSaveKindFieldRules(mcK.mcId(),mcRequirements,mcKindDefaultChoices);mcCurrentKind=mcK;mcRefreshKinds();mcKindList.getSelectionModel().select(mcK);mcSaved("Tipo documental guardado");}catch(Exception mcE){mcError(mcE);}}
    private void mcDuplicateKind(){MCKind mcK=mcKindList.getSelectionModel().getSelectedItem();if(mcK==null)return;try{MCKind mcCopy=mcStore.mcSaveKind(new MCKind(0,mcK.mcTitle()+" (copia)",mcK.mcCategory(),mcK.mcDescription(),mcK.mcTemplateId(),mcK.mcFields(),mcK.mcActive()));mcStore.mcSaveKindFieldRules(mcCopy.mcId(),mcStore.mcKindRequirements(mcK.mcId()),mcStore.mcKindDefaults(mcK.mcId()));mcRefreshKinds();mcSaved("Tipo duplicado");}catch(Exception mcE){mcError(mcE);}}
    private void mcDeleteKind(){MCKind mcK=mcKindList.getSelectionModel().getSelectedItem();if(mcK==null)return;try{mcStore.mcDeleteKind(mcK.mcId());mcNewKind();mcRefreshKinds();mcSaved("Tipo eliminado");}catch(Exception mcE){mcError(mcE);}}

    private BorderPane mcGeneratorPage;
    private void mcBuildGenerator(){
        mcGeneratorPage=new BorderPane();mcGeneratorPage.getStyleClass().add("page");
        GridPane mcTop=new GridPane();mcTop.setHgap(10);for(double mcWidth:new double[]{24,46,30}){ColumnConstraints mcCol=new ColumnConstraints();mcCol.setPercentWidth(mcWidth);mcTop.getColumnConstraints().add(mcCol);}
        mcGenerateGroup.setOnAction(mcE->{if(!mcRefreshingGenerator){mcUpdatingClientDocument=null;mcUpdatingClient=null;mcRefreshKinds();}});mcGenerateTemplate.setOnAction(mcE->{if(!mcRefreshingGenerator)mcClientAction(()->{mcUpdatingClientDocument=null;mcUpdatingClient=null;mcHistoricalTemplate=null;mcBuildDynamicForm();});});mcGenerateSearch.setPromptText("Buscar entre plantillas");mcGenerateSearch.textProperty().addListener((mcO,mcA,mcB)->{mcUpdatingClientDocument=null;mcUpdatingClient=null;mcRefreshKinds();});
        mcTop.add(mcLabelled("Grupo",mcGenerateGroup),0,0);mcTop.add(mcLabelled("Documento o plantilla",mcGenerateTemplate),1,0);mcTop.add(mcLabelled("Buscar",mcGenerateSearch),2,0);mcTop.getStyleClass().add("toolbar");mcGeneratorPage.setTop(mcTop);BorderPane.setMargin(mcTop,new Insets(0,0,12,0));
        mcGeneratedForm.getStyleClass().add("generator-form");ScrollPane mcFormScroll=mcScrolling(mcGeneratedForm);
        mcPreview.setMinSize(0,0);mcPreview.setContextMenuEnabled(false);
        mcPreview.getEngine().getLoadWorker().stateProperty().addListener((mcO,mcA,mcB)->{if(mcB==Worker.State.SUCCEEDED&&mcGenerationModel!=null&&Boolean.TRUE.equals(mcPreview.getEngine().executeScript("typeof window.mcStudio !== 'undefined'"))){mcGenerationReady=true;((JSObject)mcPreview.getEngine().executeScript("window")).setMember("mcJavaBridge",mcGenerationBridge);mcClientAction(()->{var mcSect=mcGenerationDocument.getDocument().getBody().getSectPr();double mcW=794,mcH=1123,mcL=72,mcR=72,mcT=72,mcD=72;if(mcSect!=null){if(mcSect.isSetPgSz()){mcW=((Number)mcSect.getPgSz().getW()).doubleValue()/15;mcH=((Number)mcSect.getPgSz().getH()).doubleValue()/15;}if(mcSect.isSetPgMar()){var mcM=mcSect.getPgMar();if(mcM.getLeft()!=null)mcL=((Number)mcM.getLeft()).doubleValue()/15;if(mcM.getRight()!=null)mcR=((Number)mcM.getRight()).doubleValue()/15;if(mcM.getTop()!=null)mcT=((Number)mcM.getTop()).doubleValue()/15;if(mcM.getBottom()!=null)mcD=((Number)mcM.getBottom()).doubleValue()/15;}}mcPreview.getEngine().executeScript("window.mcStudio.mcPaper("+mcW+","+mcH+","+mcL+","+mcT+","+mcR+","+mcD+")");mcPreview.getEngine().executeScript("window.mcStudio.mcGeneration("+mcJsonString(mcGenerationDisplayValues)+")");});}});
        GridPane mcActions=new GridPane();mcActions.setHgap(8);mcActions.setVgap(8);mcActions.getStyleClass().addAll("action-bar","generator-actions");
        for(int mcI=0;mcI<2;mcI++){ColumnConstraints mcCol=new ColumnConstraints();mcCol.setPercentWidth(50);mcActions.getColumnConstraints().add(mcCol);}
        int mcActionIndex=0;for(Button mcAction:List.of(mcButton("Previsualizar",this::mcMakePreview),mcButton("Guardar DOCX",this::mcSaveGenerated),mcButton("Guardar PDF",this::mcSavePdf),mcButton("Abrir en Word",this::mcOpenGeneratedInWord),mcButton("Imprimir",this::mcPrintGenerated))){mcAction.setGraphic(null);mcAction.setMinWidth(0);mcAction.setMaxWidth(Double.MAX_VALUE);mcAction.setWrapText(true);mcActions.add(mcAction,mcActionIndex%2,mcActionIndex/2);mcActionIndex++;}
        mcUpdateDocumentButton.setOnAction(mcE->mcClientAction(this::mcUpdateClientDocumentFromGenerator));mcUpdateDocumentButton.setVisible(false);mcUpdateDocumentButton.setManaged(false);
        mcUpdateDocumentBanner.setWrapText(true);mcUpdateDocumentBanner.getStyleClass().add("client-update-banner");mcUpdateDocumentBanner.setVisible(false);mcUpdateDocumentBanner.setManaged(false);
        VBox mcRight=new VBox(10,mcUpdateDocumentBanner,new Label("DOCUMENTO"),mcPreview,mcUpdateDocumentButton,mcActions);mcRight.setMinWidth(0);mcRight.getStyleClass().add("preview-panel");VBox.setVgrow(mcPreview,Priority.ALWAYS);
        mcGeneratorVariablesToggle.setOnAction(mcE->mcToggleGeneratorVariables());Region mcSpace=new Region();HBox.setHgrow(mcSpace,Priority.ALWAYS);Label mcVariablesTitle=new Label("Variables");HBox mcHeading=new HBox(8,mcVariablesTitle,mcSpace,mcGeneratorVariablesToggle);mcHeading.setAlignment(Pos.CENTER_LEFT);
        mcGeneratorVariablesPanel=new VBox(10,mcHeading,mcFormScroll);mcGeneratorVariablesPanel.getStyleClass().add("generator-variables-panel");mcGeneratorVariablesPanel.setMinWidth(0);VBox.setVgrow(mcFormScroll,Priority.ALWAYS);
        mcGeneratorColumns=new GridPane();mcGeneratorColumns.setHgap(14);mcGeneratorFormColumn=new ColumnConstraints();mcGeneratorDocumentColumn=new ColumnConstraints();mcGeneratorColumns.getColumnConstraints().addAll(mcGeneratorFormColumn,mcGeneratorDocumentColumn);RowConstraints mcRow=new RowConstraints();mcRow.setVgrow(Priority.ALWAYS);mcGeneratorColumns.getRowConstraints().add(mcRow);mcGeneratorColumns.add(mcGeneratorVariablesPanel,0,0);mcGeneratorColumns.add(mcRight,1,0);GridPane.setVgrow(mcGeneratorVariablesPanel,Priority.ALWAYS);GridPane.setVgrow(mcRight,Priority.ALWAYS);mcGeneratorPage.setCenter(mcGeneratorColumns);mcUpdateGeneratorColumns();
    }
    private void mcToggleGeneratorVariables(){mcGeneratorVariablesCollapsed=!mcGeneratorVariablesCollapsed;mcUpdateGeneratorColumns();}
    private void mcUpdateGeneratorColumns(){
        boolean mcCollapsed=mcGeneratorVariablesCollapsed;mcGeneratorFormColumn.setPercentWidth(mcCollapsed?-1:46);mcGeneratorFormColumn.setMinWidth(mcCollapsed?48:0);mcGeneratorFormColumn.setPrefWidth(mcCollapsed?48:-1);mcGeneratorFormColumn.setMaxWidth(mcCollapsed?48:Double.MAX_VALUE);mcGeneratorDocumentColumn.setPercentWidth(mcCollapsed?-1:54);mcGeneratorDocumentColumn.setHgrow(Priority.ALWAYS);mcGeneratorDocumentColumn.setMinWidth(0);
        mcGeneratorVariablesToggle.setGraphic(new FontIcon(mcCollapsed?Feather.CHEVRONS_RIGHT:Feather.CHEVRONS_LEFT));mcGeneratorVariablesToggle.setTooltip(new Tooltip(mcCollapsed?"Mostrar panel de variables":"Minimizar panel de variables"));
        HBox mcHeading=(HBox)mcGeneratorVariablesPanel.getChildren().get(0);for(Node mcNode:mcHeading.getChildren())if(mcNode!=mcGeneratorVariablesToggle){mcNode.setVisible(!mcCollapsed);mcNode.setManaged(!mcCollapsed);}Node mcContent=mcGeneratorVariablesPanel.getChildren().get(1);mcContent.setVisible(!mcCollapsed);mcContent.setManaged(!mcCollapsed);mcGeneratorVariablesPanel.getStyleClass().remove("collapsed");if(mcCollapsed)mcGeneratorVariablesPanel.getStyleClass().add("collapsed");
    }

    private void mcShowGenerator(){
        mcRefreshingGenerator=true;
        try{
            MCTemplateGroup mcPrevious=mcGenerateGroup.getValue();List<MCTemplateGroup> mcGroups=mcStore.mcGroups();mcGenerateGroup.getItems().setAll(mcGroups);
            mcGenerateGroup.getSelectionModel().select(mcGroups.stream().filter(mcG->mcPrevious!=null&&mcG.mcId()==mcPrevious.mcId()).findFirst().orElse(mcGroups.isEmpty()?null:mcGroups.get(0)));
        }catch(Exception mcE){mcError(mcE);}finally{mcRefreshingGenerator=false;}
        if(mcUpdatingClientDocument==null)mcRefreshKinds();mcPage("Generar documentos",mcGeneratorPage);
        if(mcGenerateTemplate.getValue()==null)mcGeneratedForm.getChildren().setAll(new Label(mcGenerateGroup.getItems().isEmpty()?"Cree un grupo y asocie sus plantillas desde Plantillas.":"Seleccione una plantilla del grupo."));
    }
    private void mcBuildDynamicForm(){
        mcLookupDelays.values().forEach(PauseTransition::stop);mcLookupDelays.clear();mcComplementDelays.values().forEach(PauseTransition::stop);mcComplementDelays.clear();mcComplementMenus.values().forEach(ContextMenu::hide);mcComplementMenus.clear();mcComplementTargets.clear();mcActiveComplementDefinitions.clear();mcActiveComplementLinks.clear();
        MCTemplate mcTemplate=mcGenerateTemplate.getValue();mcGeneratedBytes=null;mcGeneratedPdf=null;mcGeneratedHistoryId=0;mcGenerationReady=false;mcGenerationModel=null;if(mcGenerationDocument!=null)try{mcGenerationDocument.close();}catch(IOException mcIgnored){}mcGenerationDocument=null;mcPreview.getEngine().loadContent("");
        boolean mcUpdating=mcUpdatingClientDocument!=null;mcUpdateDocumentButton.setVisible(mcUpdating);mcUpdateDocumentButton.setManaged(mcUpdating);mcUpdateDocumentBanner.setVisible(mcUpdating);mcUpdateDocumentBanner.setManaged(mcUpdating);if(mcUpdating)mcUpdateDocumentBanner.setText("Actualizando documento de "+mcClientName(mcUpdatingClient)+": "+mcUpdatingClientDocument.mcTitle());mcInputs.clear();mcFormVariables.clear();mcFormDefinitions.clear();mcManuallyEdited.clear();mcLocalValueOverrides.clear();mcGeneratedForm.getChildren().clear();
        if(mcTemplate==null)return;
        try{
            List<MCVariable> mcVariables=mcVariablesForTemplate(mcTemplate);List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();Map<String,String> mcNames=new LinkedHashMap<>();for(var mcPerson:mcStore.mcPersonas(mcTemplate.mcId()))mcNames.put(mcPerson.mcPersonaKey(),mcPerson.mcDisplayName());
            String mcIndependentScope="!independent";
            Map<String,List<MCVariable>> mcClients=new LinkedHashMap<>();mcClients.put("",new ArrayList<>());for(String mcKey:mcNames.keySet())mcClients.put(mcKey,new ArrayList<>());
            List<MCVariable> mcIndependent=new ArrayList<>();
            for(MCVariable mcVariable:mcVariables){
                MCDefinition mcDefinition=mcDefinitionFor(mcVariable,mcDefinitions);if(mcDefinition!=null)mcFormDefinitions.put(mcVariable.mcKey(),mcDefinition);
                if(mcDefinition!=null&&!mcDefinition.mcBindToClient())mcIndependent.add(mcVariable);
                else mcClients.computeIfAbsent(MCDocumentEngine.mcPersonaKey(mcVariable.mcKey()),mcKey->new ArrayList<>()).add(mcVariable);
            }
            List<String> mcScopes=new ArrayList<>(mcClients.entrySet().stream().filter(mcEntry->!mcEntry.getValue().isEmpty())
                .sorted(Comparator.comparingInt((Map.Entry<String,List<MCVariable>> mcEntry)->mcEntry.getValue().size()).reversed()).map(Map.Entry::getKey).toList());
            if(!mcIndependent.isEmpty())mcScopes.add(mcIndependentScope);
            Map<String,GridPane> mcGrids=new LinkedHashMap<>();Map<String,Integer> mcSlots=new HashMap<>();VBox mcIndependentCard=null;
            for(String mcScope:mcScopes){
                GridPane mcGrid=mcTwoColumns();mcGrids.put(mcScope,mcGrid);boolean mcGlobal=mcScope.equals(mcIndependentScope);
                VBox mcCard=new VBox(10,new Label(mcGlobal?"Variables independientes":mcScope.isEmpty()?"Cliente / Solicitante":mcNames.getOrDefault(mcScope,mcScope.replace('_',' '))),mcGrid);mcCard.getStyleClass().add("binding-card");
                if(mcGlobal)mcIndependentCard=mcCard;else{mcCard.getProperties().put("persona-key",mcScope);mcGeneratedForm.getChildren().add(mcCard);}
            }
            for(MCVariable mcVariable:mcVariables){
                MCDefinition mcDefinition=mcFormDefinitions.get(mcVariable.mcKey());String mcScope=mcDefinition!=null&&!mcDefinition.mcBindToClient()?mcIndependentScope:MCDocumentEngine.mcPersonaKey(mcVariable.mcKey());GridPane mcGrid=mcGrids.get(mcScope);int mcSlot=mcSlots.getOrDefault(mcScope,0);
                String mcInitial=mcDefinition!=null?mcDefinition.mcInitialValue():MCVariableInput.mcInitialValue(mcVariable.mcType(),mcVariable.mcDefaultValue());Control mcInput;
                if(mcDefinition!=null&&(!mcDefinition.mcBindToClient()||mcDefinition.mcAutoCurrentDate()))mcInput=new TextField(mcInitial);
                else if(List.of("Texto largo","Domicilio").contains(mcVariable.mcType())){TextArea mcArea=new TextArea(mcInitial);mcArea.setPrefRowCount(3);mcArea.setWrapText(true);mcInput=mcArea;}
                else if("Fecha".equals(mcVariable.mcType())){DatePicker mcDate=new DatePicker();try{mcDate.setValue(LocalDate.parse(mcInitial));}catch(Exception mcIgnored){mcDate.setValue(LocalDate.now());}mcInput=mcDate;}
                else mcInput=new TextField(mcInitial);
                if(mcInput instanceof TextInputControl mcEditable)mcEditable.textProperty().addListener((mcO,mcA,mcB)->{if(!mcApplyingApi)mcManuallyEdited.add(mcVariable.mcKey());});
                else if(mcInput instanceof DatePicker mcDate)mcDate.valueProperty().addListener((mcO,mcA,mcB)->{if(!mcApplyingApi)mcManuallyEdited.add(mcVariable.mcKey());});
                mcInputs.put(mcVariable.mcKey(),mcInput);mcFormVariables.put(mcVariable.mcKey(),mcVariable);Node mcControl=mcInput;
                String mcService=mcQueryService(mcTemplate.mcId(),mcVariable);
                if(mcService!=null&&mcInput instanceof TextField mcField){
                    PauseTransition mcDelay=new PauseTransition(Duration.millis(350));mcLookupDelays.put(mcVariable.mcKey(),mcDelay);
                    Runnable mcConsult=()->{mcDelay.stop();mcLookupForField(mcTemplate,mcVariable,mcField,mcService);};
                    mcDelay.setOnFinished(mcE->{if(mcInputs.get(mcVariable.mcKey())!=mcField||!mcValidQueryNumber(mcService,mcField.getText()))return;if(mcApiQueryRunning)mcDelay.playFromStart();else mcSafe(mcConsult);});
                    mcField.textProperty().addListener((mcO,mcA,mcB)->{
                        mcDelay.stop();if(mcApplyingApi||!mcValidQueryNumber(mcService,mcB))return;
                        try{mcResetContactEdits(mcTemplate,mcVariable,mcService);if(!mcLoadLocalForField(mcTemplate,mcVariable,mcField,mcService))mcDelay.playFromStart();}catch(Exception mcEx){mcError(mcEx);}
                    });
                    Button mcLookup=mcButton("Consultar API",mcConsult);mcLookup.setTooltip(new Tooltip("Busca primero en Clientes locales; si no existe, consulta la API."));mcField.setMinWidth(0);
                    mcLookup.setGraphic(null);mcLookup.getStyleClass().add("lookup-button");HBox mcQuery=new HBox(6,mcField,mcLookup);HBox.setHgrow(mcField,Priority.ALWAYS);mcControl=mcQuery;
                }
                VBox mcField=mcLabelled(mcVariable.mcLabel(),mcControl);
                if(mcInput instanceof TextArea){if(mcSlot%2!=0)mcSlot++;mcGrid.add(mcField,0,mcSlot/2,2,1);mcSlot+=2;}
                else{mcGrid.add(mcField,mcSlot%2,mcSlot/2);mcSlot++;}mcSlots.put(mcScope,mcSlot);
            }
            mcConfigureComplements(mcVariables);mcAddContactSelectors(mcTemplate);if(mcIndependentCard!=null)mcGeneratedForm.getChildren().add(mcIndependentCard);
            mcRenderPreview(mcHistoricalTemplate==null?mcTemplate.mcEdited():mcHistoricalTemplate);if(mcInputs.isEmpty()){Label mcMessage=new Label("Este documento no contiene variables. Puedes editar su contenido directamente en el panel del documento.");mcMessage.setWrapText(true);mcGeneratedForm.getChildren().add(mcMessage);}
            mcInputs.values().forEach(mcInput->{if(mcInput instanceof TextInputControl mcText)mcText.textProperty().addListener((mcO,mcA,mcB)->mcClientAction(this::mcRefreshGenerationValues));else if(mcInput instanceof DatePicker mcDate)mcDate.valueProperty().addListener((mcO,mcA,mcB)->mcClientAction(this::mcRefreshGenerationValues));});
        }catch(Exception mcE){mcError(mcE);}
    }
    private void mcAddContactSelectors(MCTemplate mcTemplate)throws Exception{
        Map<String,List<MCContactBinding>> mcGroups=new LinkedHashMap<>();for(MCContactBinding mcBinding:mcData.mcContactBindings(mcTemplate.mcId()))if(MCContactReuse.mcClientKey(mcBinding.mcVariableKey(),mcStore.mcDefinitions()))mcGroups.computeIfAbsent(mcBinding.mcGroupKey(),mcIgnored->new ArrayList<>()).add(mcBinding);
        for(var mcEntry:mcGroups.entrySet()){
            List<MCContactBinding> mcGroup=mcEntry.getValue();String mcKind=mcGroup.get(0).mcKind();TextField mcSearch=new TextField();mcSearch.setPromptText("Buscar por nombre o documento");
            ComboBox<MCContact> mcChoice=new ComboBox<>();mcChoice.setPromptText("Seleccionar "+mcKind.toLowerCase());mcChoice.setMaxWidth(Double.MAX_VALUE);mcChoice.setMinWidth(0);
            Runnable mcFilter=()->{try{String mcQ=mcSearch.getText().trim().toLowerCase();mcChoice.getItems().setAll(mcData.mcContacts().stream().filter(mcC->mcKind.equals(mcC.mcKind())).filter(mcC->(mcC.mcGiven()+" "+mcC.mcFamily()+" "+mcC.mcNumber()).toLowerCase().contains(mcQ)).toList());}catch(Exception mcEx){mcError(mcEx);}};
            mcSearch.textProperty().addListener((mcO,mcA,mcB)->mcFilter.run());mcChoice.setOnShowing(mcE->mcFilter.run());mcFilter.run();
            VBox mcRow=new VBox(8,mcSearch,mcChoice,mcButton("Usar registro",()->{MCContact mcSelected=mcChoice.getValue();if(mcSelected==null)return;Map<String,String> mcValues=MCContactReuse.mcValuesFor(mcSelected,mcGroup);for(var mcMapped:mcValues.entrySet()){Control mcTarget=mcInputs.get(mcMapped.getKey());if(mcTarget!=null)mcSetInput(mcTarget,mcMapped.getValue());else mcLocalValueOverrides.put(mcMapped.getKey(),mcMapped.getValue());}mcSaved("Datos locales aplicados a "+mcEntry.getKey()+"; puede editarlos antes de generar");}));
            mcGeneratedForm.getChildren().add(mcLabelled("REGISTRO LOCAL · "+mcEntry.getKey()+" · "+mcKind,mcRow));
        }
    }
    private Map<String,String> mcCurrentFormValues(){Map<String,String> mcValues=new LinkedHashMap<>(mcLocalValueOverrides);mcInputs.forEach((mcKey,mcInput)->mcValues.put(mcKey,mcReadInput(mcInput)));return mcValues;}
    private void mcRenderPreview(byte[] mcBytes)throws Exception{
        mcGenerationReady=false;mcGenerationDocument=MCDocumentEngine.mcOpen(mcBytes);mcGenerationModel=new MCContinuousDocxEditor(mcGenerationDocument);mcGenerationDisplayValues=mcDocumentValues(mcBytes,mcCurrentFormValues());mcGenerationValueAliases.clear();for(String mcKey:mcGenerationDisplayValues.keySet()){MCDefinition mcDefinition=mcFormDefinitions.get(MCDocumentEngine.mcBaseKey(mcKey));mcGenerationValueAliases.put(mcKey,mcDefinition!=null&&!mcDefinition.mcBindToClient()?mcDefinition.mcKey():mcKey);}mcPreview.getEngine().loadContent(mcGenerationModel.mcGenerationHtml());
    }
    private void mcRefreshGenerationValues()throws Exception{if(mcGenerationDocument==null)return;Map<String,String> mcValues=mcCurrentFormValues();mcGenerationDisplayValues=new LinkedHashMap<>(mcValues);mcGenerationValueAliases.forEach((mcKey,mcSource)->mcGenerationDisplayValues.put(mcKey,mcValues.getOrDefault(mcSource,"")));if(mcGenerationReady)mcPreview.getEngine().executeScript("window.mcStudio.mcGeneration("+mcJsonString(mcGenerationDisplayValues)+")");}
    private void mcSyncGenerationEditor()throws Exception{
        if(!mcGenerationReady||mcGenerationModel==null)return;
        mcGenerationModel.mcApply(String.valueOf(mcPreview.getEngine().executeScript("window.mcStudio.mcHtml()")));
        XWPFDocument mcNormalized=MCDocumentEngine.mcOpen(MCDocumentEngine.mcBytes(mcGenerationDocument));mcGenerationDocument.close();mcGenerationDocument=mcNormalized;mcGenerationModel=new MCContinuousDocxEditor(mcNormalized);
        mcPreview.getEngine().executeScript("window.mcStudio.mcAccept("+mcJsonString("<div>"+mcGenerationModel.mcHtml()+"</div>")+")");
    }
    /** @author MC */
    public final class MCGenerationBridge {
        public void mcMarkDirty(){mcGeneratedHistoryId=0;mcGeneratedPdf=null;}
        public void mcTableSelected(boolean mcSelected){} public void mcImageSelected(boolean mcSelected){}
        public void mcOnSelectionFormatChanged(String mcState){}
        public void mcSaveCurrentTemplate(){mcClientAction(()->{if(mcUpdatingClientDocument!=null)mcUpdateClientDocumentFromGenerator();else mcSaveGenerated();});}
    }
    private void mcMakePreview(){
        mcGeneratedBytes=null;mcGeneratedPdf=null;MCTemplate mcSelectedTemplate=mcGenerateTemplate.getValue();if(mcSelectedTemplate==null)throw new IllegalArgumentException("Seleccione una plantilla.");
        try{
            mcCompletingMissingComplements=true;try{for(String mcKey:mcComplementTargets.keySet())mcCompleteExactComplement(mcKey);}finally{mcCompletingMissingComplements=false;}
            Map<String,String> mcValues=new LinkedHashMap<>();Map<String,MCVariable> mcVars=new HashMap<>();
            for(MCVariable mcV:mcVariablesForTemplate(mcSelectedTemplate)){mcVars.put(mcV.mcKey(),mcV);mcValues.put(mcV.mcKey(),MCVariableInput.mcInitialValue(mcV.mcType(),mcV.mcDefaultValue()));}
            mcValues.putAll(mcLocalValueOverrides);
            for(var mcEntry:mcInputs.entrySet()){
                Control mcInput=mcEntry.getValue();MCVariable mcV=mcVars.get(mcEntry.getKey());
                if(mcV==null)throw new IllegalArgumentException("Variable sin definir: "+mcEntry.getKey());
                String mcValue=mcFormDefinitions.containsKey(mcEntry.getKey())&&(!mcFormDefinitions.get(mcEntry.getKey()).mcBindToClient()||mcFormDefinitions.get(mcEntry.getKey()).mcAutoCurrentDate())?mcReadInput(mcInput):MCVariableInput.mcValidate(mcV.mcType(),mcReadInput(mcInput),Locale.getDefault());
                mcValues.put(mcEntry.getKey(),mcValue);
            }
            if(!mcValues.equals(mcGeneratedValues))mcGeneratedHistoryId=0;
            mcSyncGenerationEditor();byte[] mcSource=mcGenerationDocument==null?(mcHistoricalTemplate==null?mcSelectedTemplate.mcEdited():mcHistoricalTemplate):MCDocumentEngine.mcBytes(mcGenerationDocument);
            mcGeneratedBytes=MCDocumentEngine.mcGenerate(mcSource,mcDocumentValues(mcSource,mcValues));mcGeneratedValues=mcValues;mcLearnScopedComplements(mcValues);mcAutoSaveContacts(mcSelectedTemplate);mcRefreshGenerationValues();

            mcSaved("Vista previa generada"+mcContactSaveSummary);
        }catch(Exception mcE){mcError(mcE);}
    }
    private void mcSaveGenerated(){mcMakePreview();if(mcGeneratedBytes==null)return;FileChooser mcFc=new FileChooser();mcFc.setTitle("Guardar documento generado");mcFc.setInitialFileName(mcFileName()+".docx");mcFc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word DOCX","*.docx"));File mcTarget=mcFc.showSaveDialog(mcStage);if(mcTarget==null)return;try{Files.write(mcTarget.toPath(),mcGeneratedBytes);mcRecordGeneration(mcTarget.toString(),null,null);mcSaved("DOCX guardado: "+mcTarget.getName()+mcContactSaveSummary);}catch(Exception mcE){mcError(mcE);}}
    private void mcSavePdf(){mcMakePreview();if(mcGeneratedBytes==null)return;FileChooser mcFc=new FileChooser();mcFc.setTitle("Guardar PDF");mcFc.setInitialFileName(mcFileName()+".pdf");mcFc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));File mcTarget=mcFc.showSaveDialog(mcStage);if(mcTarget==null)return;try{mcGeneratedPdf=MCDocxPdfExporter.mcRender(mcGeneratedBytes);Files.write(mcTarget.toPath(),mcGeneratedPdf);mcRecordGeneration(null,mcTarget.toString(),mcGeneratedPdf);mcSaved("PDF guardado: "+mcTarget.getName()+mcContactSaveSummary);}catch(Exception mcE){mcError(mcE);}}
    private void mcOpenGeneratedInWord(){
        mcMakePreview();if(mcGeneratedBytes==null)return;
        try{Path mcDocument=Files.createTempFile("documento-",".docx");Files.write(mcDocument,mcGeneratedBytes);mcDocument.toFile().deleteOnExit();if(mcUpdatingClientDocument==null)mcRecordGeneration(null,null,null);mcDocumentActions.mcOpen(mcDocument);mcSaved("Documento abierto en el procesador de texto"+mcContactSaveSummary);}catch(Exception mcE){mcError(mcE);}
    }
    private void mcPrintGenerated(){
        mcMakePreview();if(mcGeneratedBytes==null)return;
        try{mcGeneratedPdf=MCDocxPdfExporter.mcRender(mcGeneratedBytes);Path mcDocument=Files.createTempFile("documento-",".pdf");Files.write(mcDocument,mcGeneratedPdf);mcDocument.toFile().deleteOnExit();if(mcUpdatingClientDocument==null)mcRecordGeneration(null,null,mcGeneratedPdf);mcDocumentActions.mcPrint(mcDocument);mcSaved("Documento enviado a impresión"+mcContactSaveSummary);}catch(Exception mcE){mcError(mcE);}
    }
    private String mcFileName(){return mcGenerateTemplate.getValue().mcName().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","");}
    private void mcRecordGeneration(String mcDocxPath,String mcPdfPath,byte[] mcPdf)throws Exception{
        if(mcUpdatingClientDocument!=null){mcReplaceUpdatingClientDocument();return;}
        MCTemplate mcTemplate=mcGenerateTemplate.getValue();int mcVersion=mcData.mcRecordRevision(mcTemplate.mcId(),mcHistoricalTemplate==null?mcTemplate.mcEdited():mcHistoricalTemplate);
        if(mcGeneratedHistoryId==0){MCHistory mcHistory=mcData.mcSaveHistory(new MCHistory(0,mcTemplate.mcName(),mcTemplate.mcId(),mcVersion,null,mcJson.writeValueAsString(mcGeneratedValues),mcDocxPath,mcPdfPath,mcGeneratedBytes,mcPdf));mcGeneratedHistoryId=mcHistory.mcId();}
        else mcData.mcUpdateHistoryFiles(mcGeneratedHistoryId,mcDocxPath,mcPdfPath,mcPdf);
        mcAutoSaveContacts(mcTemplate);mcRefreshClientDocuments();
    }
    private void mcAutoSaveContacts(MCTemplate mcTemplate){mcContactSaveSummary="";try{
        List<MCVariable> mcVariables=mcVariablesForTemplate(mcTemplate);
        Map<String,String> mcTypes=new HashMap<>();for(MCVariable mcV:mcVariables){String mcService=mcQueryService(mcTemplate.mcId(),mcV);mcTypes.put(mcV.mcKey(),mcService==null?mcV.mcType():mcService);}
        MCContactReuse.MCResult mcResult=MCContactReuse.mcSave(mcData,mcContactFormBindings(mcTemplate.mcId(),mcVariables),mcGeneratedValues,mcTypes,mcStore.mcDefinitions());
        mcContactSaveSummary=" · Clientes: "+mcResult.mcCreated()+" nuevos, "+mcResult.mcUpdated()+" actualizados";mcRefreshContacts();
    }catch(Exception mcE){mcContactSaveSummary=" · No se pudo actualizar Clientes: "+mcE.getMessage();}}
    private List<MCContactBinding> mcContactFormBindings(long mcTemplateId,List<MCVariable> mcVariables)throws Exception{
        Map<String,MCApiBinding> mcApi=new LinkedHashMap<>();for(MCVariable mcV:mcVariables){long mcId=mcDefinitionId(mcV);MCApiBinding mcBinding=mcId>0?mcData.mcApiBinding(mcId):null;if(mcBinding!=null)mcApi.put(mcV.mcKey(),mcBinding);}
        return MCContactReuse.mcFormBindings(mcTemplateId,mcVariables,mcData.mcContactBindings(mcTemplateId),mcData.mcApiContexts(mcTemplateId),mcApi,mcStore.mcDefinitions());
    }

    private void mcBuildContacts(){
        mcContactsPage=new BorderPane();mcContactsPage.getStyleClass().add("page");mcContactSearch.setPromptText("Buscar nombre, documento o dirección");mcContactSearch.textProperty().addListener((mcO,mcA,mcB)->mcRefreshContacts());mcContactList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->mcLoadContact(mcB));
        mcTwoLineCells(mcContactList,mcC->("Empresa".equals(mcC.mcKind())?mcC.mcGiven():mcC.mcGiven()+" "+mcC.mcFamily()),mcC->mcC.mcDocumentType()+" · "+mcC.mcNumber());
        VBox mcLeft=new VBox(8,mcContactSearch,mcContactList,mcButton("+ Nueva persona",this::mcNewContact),mcButton("Eliminar cliente",this::mcDeleteContact));mcLeft.getStyleClass().add("side-panel");mcFixedWidth(mcLeft,320);mcUniformButtons(mcLeft);VBox.setVgrow(mcContactList,Priority.ALWAYS);mcContactsPage.setLeft(mcLeft);BorderPane.setMargin(mcLeft,new Insets(0,14,0,0));
        mcContactKind.getItems().setAll("Persona","Empresa");mcContactKind.setValue("Persona");mcContactKind.setOnAction(mcE->{boolean mcCompany="Empresa".equals(mcContactKind.getValue());mcContactFamily.setDisable(mcCompany);mcContactDocType.setDisable(mcCompany);if(mcCompany)mcContactDocType.setText("RUC");else if("RUC".equals(mcContactDocType.getText()))mcContactDocType.setText("DNI");});mcContactRepresentatives.setPrefRowCount(2);mcContactExtras.setPrefRowCount(3);mcContactRepresentatives.setWrapText(true);mcContactExtras.setWrapText(true);
        GridPane mcFields=mcTwoColumns();mcFields.add(mcLabelled("Tipo de cliente",mcContactKind),0,0);mcFields.add(mcLabelled("Nombres / razón social",mcContactGiven),1,0);mcFields.add(mcLabelled("Apellidos",mcContactFamily),0,1);mcFields.add(mcLabelled("Tipo de documento",mcContactDocType),1,1);mcFields.add(mcLabelled("Número de documento / RUC",mcContactNumber),0,2);mcFields.add(mcLabelled("Domicilio / domicilio fiscal",mcContactAddress),1,2);mcFields.add(mcLabelled("Representantes (empresa)",mcContactRepresentatives),0,3,2,1);mcFields.add(mcLabelled("Datos adicionales · JSON",mcContactExtras),0,4,2,1);
        VBox mcForm=new VBox(16,new Label("FICHA LOCAL"),mcFields,mcContactVariables());mcForm.getStyleClass().add("catalog-form");BorderPane mcDetail=new BorderPane(mcScrolling(mcForm));HBox mcActions=new HBox(mcButton("Guardar cliente",this::mcSaveContact));mcActions.getStyleClass().add("action-bar");mcDetail.setBottom(mcActions);mcClientDetailsPane=mcDetail;
        mcTwoLineCells(mcClientDocumentList,MCClientDocument::mcTitle,mcD->mcD.mcExtension().toUpperCase(Locale.ROOT)+" · "+("GENERATED".equals(mcD.mcSourceType())?"Generado":"Subido")+" · "+mcD.mcUpdatedAt());
        Button mcUpload=mcButton("+ Subir documento",this::mcUploadClientDocument),mcOpen=mcButton("Abrir",this::mcOpenClientDocument),mcDownload=mcButton("Descargar",this::mcDownloadClientDocument),mcGenerate=mcButton("Actualizar en Generar documentos",this::mcEditSelectedClientDocument),mcReplace=mcButton("Actualizar subiendo archivo",this::mcReplaceClientDocumentByUpload);
        mcClientDocumentList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->{mcOpen.setDisable(mcB==null);mcDownload.setDisable(mcB==null);mcReplace.setDisable(mcB==null);mcGenerate.setDisable(mcB==null||!mcIsWordExtension(mcB.mcExtension()));});mcOpen.setDisable(true);mcDownload.setDisable(true);mcReplace.setDisable(true);mcGenerate.setDisable(true);
        HBox mcDocumentTop=new HBox(mcUpload);mcDocumentTop.getStyleClass().add("action-bar");FlowPane mcDocumentActions=new FlowPane(8,8,mcOpen,mcDownload,mcGenerate,mcReplace);mcDocumentActions.getStyleClass().add("action-bar");mcClientDocumentsPane.setTop(mcDocumentTop);mcClientDocumentsPane.setCenter(mcClientDocumentList);mcClientDocumentsPane.setBottom(mcDocumentActions);
        mcClientDetailsToggle.setOnAction(mcE->{mcShowingClientDetails=!mcShowingClientDetails;mcShowClientPanel();});Region mcGap=new Region();HBox.setHgrow(mcGap,Priority.ALWAYS);HBox mcHeading=new HBox(10,mcClientPanelTitle,mcGap,mcClientDetailsToggle);mcHeading.setAlignment(Pos.CENTER_LEFT);mcHeading.getStyleClass().add("client-panel-heading");BorderPane mcRight=new BorderPane();mcRight.setTop(mcHeading);mcRight.setCenter(mcClientDocumentsPane);mcContactsPage.setCenter(mcRight);mcNewContact();
    }
    private void mcShowContacts(){try{mcContactExtraChoice.getItems().setAll(mcStore.mcDefinitions().stream().filter(MCDefinition::mcBindToClient).toList());}catch(Exception mcE){mcError(mcE);}mcRefreshContacts();mcPage("Clientes",mcContactsPage);}
    private void mcRefreshContacts(){try{
        long mcSelected=mcSelectedContact==null?0:mcSelectedContact.mcId();String mcQ=mcContactSearch.getText().toLowerCase(Locale.ROOT);
        List<MCContact> mcContacts=mcData.mcFindContacts(mcQ);
        mcContactList.getItems().setAll(mcContacts);mcContactList.getSelectionModel().select(mcContacts.stream().filter(mcC->mcC.mcId()==mcSelected).findFirst().orElse(mcContacts.isEmpty()?null:mcContacts.get(0)));
        if(mcContacts.isEmpty())mcNewContact();
    }catch(Exception mcE){mcError(mcE);}}
    private void mcNewContact(){mcSelectedContact=null;mcContactList.getSelectionModel().clearSelection();mcContactKind.setValue("Persona");mcContactGiven.clear();mcContactFamily.clear();mcContactDocType.setText("DNI");mcContactNumber.clear();mcContactAddress.clear();mcContactRepresentatives.clear();mcContactExtras.setText("{}");mcContactExtraValue.clear();mcContactExtraChoice.getSelectionModel().clearSelection();mcShowingClientDetails=true;mcShowClientPanel();}
    private void mcLoadContact(MCContact mcC){if(mcC==null){mcNewContact();return;}mcSelectedContact=mcC;mcContactKind.setValue(mcC.mcKind());mcContactGiven.setText(mcC.mcGiven());mcContactFamily.setText(mcC.mcFamily());mcContactDocType.setText(mcC.mcDocumentType());mcContactNumber.setText(mcC.mcNumber());mcContactAddress.setText(mcC.mcAddress());mcContactRepresentatives.setText(mcC.mcRepresentatives());mcContactExtras.setText(mcC.mcExtrasJson());mcShowingClientDetails=false;mcShowClientPanel();mcRefreshClientDocuments();}
    private void mcSaveContact(){if(mcContactGiven.getText().isBlank())throw new IllegalArgumentException("Escriba nombre o razón social.");try{if(!mcJson.readTree(mcContactExtras.getText()).isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");MCContact mcC=mcData.mcSaveContact(new MCContact(mcSelectedContact==null?0:mcSelectedContact.mcId(),mcContactKind.getValue(),mcContactGiven.getText().trim(),mcContactFamily.getText().trim(),"Empresa".equals(mcContactKind.getValue())?"RUC":mcContactDocType.getText().trim(),mcContactNumber.getText().trim(),mcContactAddress.getText().trim(),mcContactRepresentatives.getText().trim(),mcContactExtras.getText()));mcRefreshContacts();mcContactList.getSelectionModel().select(mcC);mcSaved("Registro local guardado");}catch(Exception mcE){mcError(mcE);}}
    private void mcDeleteContact(){MCContact mcC=mcContactList.getSelectionModel().getSelectedItem();if(mcC==null)return;try{mcData.mcDeleteContact(mcC.mcId());mcNewContact();mcRefreshContacts();mcSaved("Registro eliminado");}catch(Exception mcE){mcError(mcE);}}

    private static String mcClientName(MCContact mcClient){return mcClient==null?"":(mcClient.mcGiven()+" "+mcClient.mcFamily()).trim();}
    private void mcShowClientPanel(){
        if(mcClientDetailsPane==null)return;mcClientDetailsToggle.setText(mcShowingClientDetails?"Volver a documentos":"Ver detalles del cliente");mcClientPanelTitle.setText(mcShowingClientDetails?"Detalles del cliente":"Documentos del cliente");
        ((BorderPane)mcContactsPage.getCenter()).setCenter(mcShowingClientDetails?mcClientDetailsPane:mcClientDocumentsPane);
    }
    private void mcRefreshClientDocuments(){try{long mcSelected=mcClientDocumentList.getSelectionModel().getSelectedItem()==null?0:mcClientDocumentList.getSelectionModel().getSelectedItem().mcId();mcClientDocumentList.getItems().setAll(mcSelectedContact==null?List.of():mcData.mcClientDocuments(mcSelectedContact.mcId()));mcClientDocumentList.getSelectionModel().select(mcClientDocumentList.getItems().stream().filter(mcD->mcD.mcId()==mcSelected).findFirst().orElse(null));}catch(Exception mcE){mcError(mcE);}}
    private static boolean mcIsWordExtension(String mcExtension){return Set.of("doc","docx","docm","dot","dotx","dotm").contains(mcExtension.toLowerCase(Locale.ROOT));}
    private static String mcFileExtension(String mcName){int mcDot=mcName.lastIndexOf('.');return mcDot<0?"":mcName.substring(mcDot+1).toLowerCase(Locale.ROOT);}
    private File mcChooseClientFile(){FileChooser mcChooser=new FileChooser();mcChooser.setTitle("Seleccionar documento del cliente");mcChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Todos los archivos","*"),new FileChooser.ExtensionFilter("Documentos de Word y PDF","*.doc","*.DOC","*.docx","*.DOCX","*.docm","*.DOCM","*.dot","*.DOT","*.dotx","*.DOTX","*.dotm","*.DOTM","*.pdf","*.PDF"));return mcChooser.showOpenDialog(mcStage);}
    private void mcUploadClientDocument(){if(mcSelectedContact==null)throw new IllegalArgumentException("Seleccione o guarde primero un cliente.");File mcFile=mcChooseClientFile();if(mcFile!=null)mcClientAction(()->{mcData.mcAddClientDocument(mcSelectedContact.mcId(),mcFile.getName(),mcFile.getName(),mcFileExtension(mcFile.getName()),Files.readAllBytes(mcFile.toPath()));mcRefreshClientDocuments();mcSaved("Documento subido al cliente");});}
    private MCClientDocument mcSelectedClientDocument(){MCClientDocument mcSelected=mcClientDocumentList.getSelectionModel().getSelectedItem();if(mcSelected==null)throw new IllegalArgumentException("Seleccione un documento del cliente.");return mcSelected;}
    private void mcReplaceClientDocumentByUpload(){MCClientDocument mcSelected=mcSelectedClientDocument();File mcFile=mcChooseClientFile();if(mcFile!=null)mcClientAction(()->{mcData.mcReplaceClientDocument(mcSelected.mcId(),mcFile.getName(),mcFile.getName(),mcFileExtension(mcFile.getName()),Files.readAllBytes(mcFile.toPath()),"UPLOADED");mcRefreshClientDocuments();mcSaved("Documento del cliente reemplazado");});}
    private void mcOpenClientDocument(){MCClientDocument mcSelected=mcSelectedClientDocument();mcClientAction(()->{Path mcDirectory=Files.createTempDirectory("studio-client-document-");Path mcPath=mcDirectory.resolve(Path.of(mcSelected.mcFileName()).getFileName().toString());Files.write(mcPath,mcSelected.mcContentBytes());mcDirectory.toFile().deleteOnExit();mcPath.toFile().deleteOnExit();mcDocumentActions.mcOpen(mcPath);});}
    private void mcDownloadClientDocument(){MCClientDocument mcSelected=mcSelectedClientDocument();FileChooser mcChooser=new FileChooser();mcChooser.setTitle("Descargar documento del cliente");mcChooser.setInitialFileName(Path.of(mcSelected.mcFileName()).getFileName().toString());File mcTarget=mcChooser.showSaveDialog(mcStage);if(mcTarget!=null)mcClientAction(()->{Files.write(mcTarget.toPath(),mcSelected.mcContentBytes());mcSaved("Documento del cliente descargado");});}
    private void mcEditSelectedClientDocument(){MCClientDocument mcSelected=mcSelectedClientDocument();mcClientAction(()->mcOpenClientDocumentForUpdate(mcSelectedContact,mcSelected));}
    private void mcOpenClientDocumentForUpdate(MCContact mcContact,MCClientDocument mcSelected)throws Exception{
        if(!mcIsWordExtension(mcSelected.mcExtension()))throw new IllegalArgumentException("Seleccione un documento de Word para actualizar en Generar documentos.");
        byte[] mcBytes=MCWordDocumentImporter.mcImportToDocx(mcSelected.mcContentBytes(),"document."+mcSelected.mcExtension());
        mcUpdatingClientDocument=mcSelected;mcUpdatingClient=mcContact;mcHistoricalTemplate=null;MCTemplate mcTemporary=new MCTemplate(0,mcSelected.mcTitle(),mcBytes,mcBytes);
        mcRefreshingGenerator=true;try{mcGenerateTemplate.getItems().setAll(mcTemporary);mcGenerateTemplate.getSelectionModel().select(mcTemporary);}finally{mcRefreshingGenerator=false;}
        mcBuildDynamicForm();mcPage("Generar documentos",mcGeneratorPage);
        for(var mcEntry:mcInputs.entrySet()){String mcValue=mcData.mcContactValue(mcContact,MCDocumentEngine.mcBaseKey(mcEntry.getKey()));if(!mcValue.isBlank())mcSetInput(mcEntry.getValue(),mcValue);}
    }
    private void mcReplaceUpdatingClientDocument()throws Exception{
        if(mcUpdatingClientDocument==null||mcGeneratedBytes==null)return;String mcName=mcUpdatingClientDocument.mcFileName();int mcDot=mcName.lastIndexOf('.');mcName=(mcDot<0?mcName:mcName.substring(0,mcDot))+".docx";
        mcData.mcReplaceClientDocument(mcUpdatingClientDocument.mcId(),mcUpdatingClientDocument.mcTitle(),mcName,"docx",mcGeneratedBytes,"GENERATED");mcRefreshClientDocuments();
    }
    private void mcUpdateClientDocumentFromGenerator()throws Exception{mcMakePreview();if(mcGeneratedBytes==null)return;mcReplaceUpdatingClientDocument();mcSaved("Documento del cliente actualizado");}

    private void mcDefinitionChoice(ComboBox<MCDefinition> mcChoice){
        mcChoice.setCellFactory(mcList->new ListCell<>(){@Override protected void updateItem(MCDefinition mcDefinition,boolean mcEmpty){super.updateItem(mcDefinition,mcEmpty);setText(mcEmpty||mcDefinition==null?null:mcDefinition.mcLabel()+" {{"+mcDefinition.mcKey()+"}}");}});
        mcChoice.setButtonCell(new ListCell<>(){@Override protected void updateItem(MCDefinition mcDefinition,boolean mcEmpty){super.updateItem(mcDefinition,mcEmpty);setText(mcEmpty||mcDefinition==null?null:mcDefinition.mcLabel());}});
    }
    private void mcRefreshComplements(){try{
        List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();mcComplementChoice.getItems().setAll(mcDefinitions.stream().filter(mcD->mcSelectedDefinition==null||mcD.mcId()!=mcSelectedDefinition.mcId()).toList());mcComplementLinks.getChildren().clear();
        if(mcSelectedDefinition==null){mcComplementLinks.getChildren().add(mcDescription("Guarde la variable para vincular complementos."));return;}
        Map<Long,MCDefinition> mcById=new HashMap<>();mcDefinitions.forEach(mcD->mcById.put(mcD.mcId(),mcD));
        for(MCDocumentStore.MCComplement mcLink:mcStore.mcComplements(mcSelectedDefinition.mcId())){MCDefinition mcTarget=mcById.get(mcLink.mcTargetDefId());if(mcTarget==null)continue;Label mcLabel=new Label(mcTarget.mcLabel()+(mcLink.mcBidirectional()?" ↔":" →"));mcLabel.setWrapText(true);mcLabel.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(mcLabel,Priority.ALWAYS);Button mcRemove=mcButton("Quitar",()->{try{mcStore.mcDeleteComplement(mcLink.mcId());mcRefreshComplements();}catch(Exception mcE){mcError(mcE);}});mcRemove.setTooltip(new Tooltip("Quitar el vínculo con "+mcTarget.mcLabel()));HBox mcRow=new HBox(5,mcLabel,mcRemove);mcRow.setAlignment(Pos.CENTER_LEFT);mcComplementLinks.getChildren().add(mcRow);}
    }catch(Exception mcE){mcError(mcE);}}
    private void mcLinkComplement(){if(mcSelectedDefinition==null)throw new IllegalArgumentException("Guarde primero la variable.");MCDefinition mcTarget=mcComplementChoice.getValue();if(mcTarget==null)throw new IllegalArgumentException("Seleccione la variable complemento.");try{
        List<MCDocumentStore.MCComplement> mcExisting=mcStore.mcComplements(mcSelectedDefinition.mcId());MCDocumentStore.MCComplement mcLink=mcExisting.stream().filter(mcX->mcX.mcTargetDefId()==mcTarget.mcId()).findFirst().orElse(null);
        if(mcLink!=null){for(var mcOriginal:mcStore.mcComplements())if(mcOriginal.mcId()==mcLink.mcId()){if(mcOriginal.mcSourceDefId()!=mcSelectedDefinition.mcId()&&!mcComplementBoth.isSelected()){mcStore.mcDeleteComplement(mcOriginal.mcId());mcStore.mcSaveComplement(mcSelectedDefinition.mcId(),mcTarget.mcId(),false);}else mcStore.mcSaveComplement(mcOriginal.mcSourceDefId(),mcOriginal.mcTargetDefId(),mcComplementBoth.isSelected());break;}}
        else mcStore.mcSaveComplement(mcSelectedDefinition.mcId(),mcTarget.mcId(),mcComplementBoth.isSelected());mcRefreshComplements();mcSaved("Complemento vinculado");
    }catch(Exception mcE){mcError(mcE);}}
    /** @author MC */
    private record MCContactExtra(@com.fasterxml.jackson.annotation.JsonProperty("key") String mcKey,@com.fasterxml.jackson.annotation.JsonProperty("value") String mcValue){}
    private Node mcContactVariables(){
        mcDefinitionChoice(mcContactExtraChoice);mcContactVariableTable.setEditable(true);mcContactVariableTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);mcContactVariableTable.setPrefHeight(180);mcContactVariableTable.setMinHeight(110);
        TableColumn<MCContactExtra,String> mcVariable=new TableColumn<>("Variable");mcVariable.setCellValueFactory(mcC->new javafx.beans.property.ReadOnlyStringWrapper(mcExtraLabel(mcC.getValue().mcKey())));
        TableColumn<MCContactExtra,String> mcValue=new TableColumn<>("Valor");mcValue.setCellValueFactory(mcC->new javafx.beans.property.ReadOnlyStringWrapper(mcC.getValue().mcValue()));mcValue.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());mcValue.setOnEditCommit(mcE->mcSafe(()->mcWriteContactExtra(mcE.getRowValue().mcKey(),mcE.getNewValue(),false)));mcContactVariableTable.getColumns().setAll(mcVariable,mcValue);
        mcContactVariableTable.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->{if(mcB==null)return;mcContactExtraChoice.getSelectionModel().select(mcContactExtraChoice.getItems().stream().filter(mcD->MCStudioData.mcVariableKey(mcD.mcKey()).equals(MCStudioData.mcVariableKey(mcB.mcKey()))).findFirst().orElse(null));mcContactExtraValue.setText(mcB.mcValue());});
        mcContactExtraChoice.setPromptText("Variable del sistema");mcContactExtraChoice.setMinWidth(0);mcContactExtraChoice.setMaxWidth(Double.MAX_VALUE);mcContactExtraValue.setPromptText("Valor asociado");HBox mcFields=new HBox(6,mcContactExtraChoice,mcContactExtraValue);HBox.setHgrow(mcContactExtraChoice,Priority.ALWAYS);HBox.setHgrow(mcContactExtraValue,Priority.ALWAYS);mcContactExtraValue.setMinWidth(0);
        Button mcAssign=mcButton("Asignar / Actualizar",()->{MCDefinition mcDefinition=mcContactExtraChoice.getValue();MCContactExtra mcSelected=mcContactVariableTable.getSelectionModel().getSelectedItem();if(mcDefinition==null&&mcSelected==null)throw new IllegalArgumentException("Seleccione una variable.");mcWriteContactExtra(mcDefinition==null?mcSelected.mcKey():mcDefinition.mcKey(),mcContactExtraValue.getText(),false);});
        Button mcRemove=mcButton("Quitar",()->{MCContactExtra mcSelected=mcContactVariableTable.getSelectionModel().getSelectedItem();if(mcSelected!=null)mcWriteContactExtra(mcSelected.mcKey(),"",true);});
        mcContactExtras.textProperty().addListener((mcO,mcA,mcB)->mcRefreshContactExtras());VBox mcBox=new VBox(8,new Label("Variables asociadas a este cliente"),mcContactVariableTable,mcFields,new HBox(6,mcAssign,mcRemove));mcBox.getStyleClass().add("binding-card");return mcBox;
    }
    private String mcExtraLabel(String mcKey){return mcContactExtraChoice.getItems().stream().filter(mcD->MCStudioData.mcVariableKey(mcD.mcKey()).equals(MCStudioData.mcVariableKey(mcKey))).map(MCDefinition::mcLabel).findFirst().orElse(mcKey);}
    private void mcRefreshContactExtras(){try{var mcExtras=mcJson.readTree(mcContactExtras.getText());if(mcExtras==null||!mcExtras.isObject())return;List<MCContactExtra> mcRows=new ArrayList<>();mcExtras.fields().forEachRemaining(mcE->mcRows.add(new MCContactExtra(mcE.getKey(),mcE.getValue().isValueNode()?mcE.getValue().asText(""):mcE.getValue().toString())));mcContactVariableTable.getItems().setAll(mcRows);}catch(Exception mcIgnored){}}
    private void mcWriteContactExtra(String mcKey,String mcValue,boolean mcRemove){try{
        if(!mcRemove&&!MCContactReuse.mcClientKey(mcKey,mcStore.mcDefinitions()))throw new IllegalArgumentException("Las variables independientes se configuran desde Variables.");
        var mcParsed=mcJson.readTree(mcContactExtras.getText());if(mcParsed==null||!mcParsed.isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");var mcExtras=(com.fasterxml.jackson.databind.node.ObjectNode)mcParsed;
        String mcActual=mcKey;var mcNames=mcExtras.fieldNames();while(mcNames.hasNext()){String mcName=mcNames.next();if(MCStudioData.mcVariableKey(mcName).equals(MCStudioData.mcVariableKey(mcKey))){mcActual=mcName;break;}}
        if(mcRemove)mcExtras.remove(mcActual);else mcExtras.put(mcActual,mcValue);mcContactExtras.setText(mcExtras.toString());if(mcSelectedContact!=null)mcSaveContact();
    }catch(Exception mcE){mcError(mcE);}}
    private void mcConfigureComplements(List<MCVariable> mcVariables)throws Exception{
        mcComplementTargets.clear();mcActiveComplementDefinitions.clear();mcActiveComplementLinks.clear();List<MCDefinition> mcDefinitions=mcStore.mcDefinitions();
        Map<String,Map<Long,String>> mcScopes=new LinkedHashMap<>();Set<Long> mcIds=new HashSet<>();for(MCVariable mcVariable:mcVariables){MCDefinition mcDefinition=mcDefinitionFor(mcVariable,mcDefinitions);if(mcDefinition!=null&&!mcDefinition.mcBindToClient())continue;long mcId=mcSafeDefinitionId(mcVariable);mcIds.add(mcId);mcScopes.computeIfAbsent(MCDocumentEngine.mcPersonaKey(mcVariable.mcKey()),mcK->new LinkedHashMap<>()).put(mcId,mcVariable.mcKey());}
        mcDefinitions.stream().filter(mcD->mcIds.contains(mcD.mcId())).forEach(mcActiveComplementDefinitions::add);
        for(var mcLink:mcStore.mcComplements()){
            boolean mcUsed=false;for(var mcKeys:mcScopes.values())if(mcKeys.containsKey(mcLink.mcSourceDefId())&&mcKeys.containsKey(mcLink.mcTargetDefId())){mcUsed=true;mcComplementTargets.computeIfAbsent(mcKeys.get(mcLink.mcSourceDefId()),mcK->new LinkedHashSet<>()).add(mcKeys.get(mcLink.mcTargetDefId()));if(mcLink.mcBidirectional())mcComplementTargets.computeIfAbsent(mcKeys.get(mcLink.mcTargetDefId()),mcK->new LinkedHashSet<>()).add(mcKeys.get(mcLink.mcSourceDefId()));}if(mcUsed)mcActiveComplementLinks.add(mcLink);
        }
        for(var mcEntry:mcComplementTargets.entrySet()){
            String mcKey=mcEntry.getKey();Control mcInput=mcInputs.get(mcKey);if(!(mcInput instanceof TextInputControl mcField))continue;ContextMenu mcPopup=new ContextMenu();mcPopup.setAutoHide(true);mcComplementMenus.put(mcKey,mcPopup);PauseTransition mcDelay=new PauseTransition(Duration.millis(120));mcComplementDelays.put(mcKey,mcDelay);
            mcDelay.setOnFinished(mcE->mcSafe(()->mcShowComplementSuggestions(mcKey,mcField,mcPopup)));
            mcField.textProperty().addListener((mcO,mcA,mcB)->{mcDelay.stop();if(mcApplyingApi||mcApplyingComplements)return;mcPopup.hide();if(!mcB.isBlank())mcDelay.playFromStart();});
            if(mcField instanceof TextField mcText)mcText.addEventHandler(javafx.event.ActionEvent.ACTION,mcE->{mcDelay.stop();mcPopup.hide();mcSafe(()->mcCompleteExactComplement(mcKey));});
            mcField.focusedProperty().addListener((mcO,mcA,mcFocused)->{if(!mcFocused){mcDelay.stop();Platform.runLater(()->{if(mcInputs.get(mcKey)==mcField&&!mcPopup.isShowing())mcSafe(()->mcCompleteExactComplement(mcKey));});}});
        }
    }
    private void mcShowComplementSuggestions(String mcKey,TextInputControl mcField,ContextMenu mcPopup){try{
        if(mcInputs.get(mcKey)!=mcField||!mcField.isFocused()||mcField.getScene()==null||!mcField.getScene().getWindow().isShowing())return;
        mcPopup.getItems().clear();for(MCStudioData.MCSuggestion mcSuggestion:mcData.mcSuggestions(MCDocumentEngine.mcBaseKey(mcKey),mcComplementTargets.getOrDefault(mcKey,Set.of()).stream().map(MCDocumentEngine::mcBaseKey).toList(),mcField.getText(),false)){
            List<String> mcDetails=new ArrayList<>();for(var mcTarget:mcSuggestion.mcComplements().entrySet()){String mcActual=mcActualInputKey(mcKey,mcTarget.getKey());if(mcActual!=null)mcDetails.add(mcFormVariables.get(mcActual).mcLabel()+": "+mcTarget.getValue());}
            MenuItem mcItem=new MenuItem(mcSuggestion.mcValue()+" — "+String.join("; ",mcDetails));mcItem.setOnAction(mcE->{mcPopup.hide();mcApplyComplementSuggestion(mcKey,mcSuggestion,true);});mcPopup.getItems().add(mcItem);
        }
        if(!mcPopup.getItems().isEmpty())mcPopup.show(mcField,Side.BOTTOM,0,0);
    }catch(Exception mcE){mcError(mcE);}}
    private String mcActualInputKey(String mcSource,String mcNormalized){return mcInputs.keySet().stream().filter(mcK->MCDocumentEngine.mcPersonaKey(mcK).equals(MCDocumentEngine.mcPersonaKey(mcSource))&&MCStudioData.mcVariableKey(MCDocumentEngine.mcBaseKey(mcK)).equals(MCStudioData.mcVariableKey(mcNormalized))).findFirst().orElse(null);}
    private void mcCompleteExactComplement(String mcKey){try{
        Control mcInput=mcInputs.get(mcKey);if(mcInput==null||mcApplyingApi||mcApplyingComplements)return;Set<String> mcTargets=mcComplementTargets.get(mcKey);if(mcTargets==null)return;List<MCStudioData.MCSuggestion> mcMatches=mcData.mcSuggestions(MCDocumentEngine.mcBaseKey(mcKey),mcTargets.stream().map(MCDocumentEngine::mcBaseKey).toList(),mcReadInput(mcInput),true);if(!mcMatches.isEmpty())mcApplyComplementSuggestion(mcKey,mcMatches.get(0),false);
    }catch(Exception mcE){mcError(mcE);}}
    private void mcApplyComplementSuggestion(String mcSourceKey,MCStudioData.MCSuggestion mcSuggestion,boolean mcSelected){
        boolean mcPriorApi=mcApplyingApi;mcApplyingApi=true;mcApplyingComplements=true;try{
            if(mcSelected)mcSetInput(mcInputs.get(mcSourceKey),mcSuggestion.mcValue());
            for(var mcEntry:mcSuggestion.mcComplements().entrySet()){
                String mcTarget=mcActualInputKey(mcSourceKey,mcEntry.getKey());if(mcTarget==null||!mcComplementTargets.getOrDefault(mcSourceKey,Set.of()).contains(mcTarget))continue;
                if((mcCompletingMissingComplements||mcManuallyEdited.contains(mcTarget))&&!mcReadInput(mcInputs.get(mcTarget)).isBlank())continue;mcSetInput(mcInputs.get(mcTarget),mcEntry.getValue());
            }
        }finally{mcApplyingComplements=false;mcApplyingApi=mcPriorApi;}
    }
    private String mcEditorTokenKey(MCDefinition mcDefinition){MCDocumentStore.MCPersona mcPerson=mcEditorPersona.getValue();return mcDefinition.mcEditorKey(mcPerson==null?"":mcPerson.mcPersonaKey());}
    private void mcRefreshEditorPersonas()throws Exception{
        String mcSelected=mcEditorPersona.getValue()==null?"":mcEditorPersona.getValue().mcPersonaKey();if(mcCurrentTemplate==null||mcPersonaTemplateId!=mcCurrentTemplate.mcId())mcSelected="";
        List<MCDocumentStore.MCPersona> mcPeople=new ArrayList<>();mcPeople.add(new MCDocumentStore.MCPersona(0,mcCurrentTemplate==null?0:mcCurrentTemplate.mcId(),"","General (sin persona)",-1));mcEditorPersonaNames.clear();
        if(mcCurrentTemplate!=null){mcPeople.addAll(mcStore.mcPersonas(mcCurrentTemplate.mcId()));Set<String> mcKeys=new HashSet<>();mcPeople.forEach(mcP->mcKeys.add(mcP.mcPersonaKey()));if(mcDocument!=null)for(String mcMarker:MCDocumentEngine.mcMarkers(mcDocument)){String mcKey=MCDocumentEngine.mcPersonaKey(mcMarker);if(!mcKey.isEmpty()&&mcKeys.add(mcKey))mcPeople.add(new MCDocumentStore.MCPersona(0,mcCurrentTemplate.mcId(),mcKey,mcKey.replace('_',' '),mcPeople.size()));}}
        mcPeople.forEach(mcP->{if(!mcP.mcPersonaKey().isEmpty())mcEditorPersonaNames.put(mcP.mcPersonaKey(),mcP.mcDisplayName());});mcEditorPersona.getItems().setAll(mcPeople);String mcChoice=mcSelected;mcEditorPersona.getSelectionModel().select(mcPeople.stream().filter(mcP->mcP.mcPersonaKey().equals(mcChoice)).findFirst().orElse(mcPeople.get(0)));mcPersonaTemplateId=mcCurrentTemplate==null?0:mcCurrentTemplate.mcId();
    }
    private void mcAddEditorPersona(){if(mcCurrentTemplate==null)throw new IllegalArgumentException("Abra una plantilla primero.");try{
        int mcN=1;while(mcEditorPersonaNames.containsKey("Persona_"+mcN))mcN++;String mcKey="Persona_"+mcN;TextInputDialog mcDialog=new TextInputDialog("Persona "+mcN);mcDialog.setTitle("Agregar persona");mcDialog.setHeaderText("Nombre o rol de la persona");String mcName=mcDialog.showAndWait().orElse(null);if(mcName==null)return;
        MCDocumentStore.MCPersona mcPerson=mcStore.mcAddPersona(mcCurrentTemplate.mcId(),mcKey,mcName,mcEditorPersonaNames.size());mcRefreshEditorPersonas();mcEditorPersona.getSelectionModel().select(mcPerson);mcEditorScript("mcPersonas("+mcJsonString(mcEditorPersonaNames)+")");
    }catch(Exception mcE){mcError(mcE);}}
    private void mcRenameEditorPersona(){MCDocumentStore.MCPersona mcPerson=mcEditorPersona.getValue();if(mcPerson==null||mcPerson.mcPersonaKey().isEmpty())return;try{
        TextInputDialog mcDialog=new TextInputDialog(mcPerson.mcDisplayName());mcDialog.setTitle("Renombrar persona");mcDialog.setHeaderText("Nombre o rol de la persona");String mcName=mcDialog.showAndWait().orElse(null);if(mcName==null)return;mcSyncEditor();if(mcPerson.mcId()==0)mcStore.mcAddPersona(mcCurrentTemplate.mcId(),mcPerson.mcPersonaKey(),mcName,mcPerson.mcSortOrder());else mcStore.mcRenamePersona(mcPerson.mcId(),mcName);mcRefreshEditorPersonas();mcRenderDocument();
    }catch(Exception mcE){mcError(mcE);}}
    private void mcRemoveEditorPersona(){MCDocumentStore.MCPersona mcPerson=mcEditorPersona.getValue();if(mcPerson==null||mcPerson.mcPersonaKey().isEmpty())return;try{
        if(new Alert(Alert.AlertType.CONFIRMATION,"¿Quitar a '"+mcPerson.mcDisplayName()+"'? Se quitarán sus variables del documento y se guardará la plantilla.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;
        mcEditorScript("mcRemovePersona("+mcJsonString(mcPerson.mcPersonaKey())+")");if(mcPerson.mcId()!=0)mcStore.mcDeletePersona(mcPerson.mcId());mcEditorPersona.getSelectionModel().selectFirst();mcEditorPersonaNames.remove(mcPerson.mcPersonaKey());mcDirty=true;mcSaveTemplate();mcRefreshEditorPersonas();
    }catch(Exception mcE){mcError(mcE);}}
    private void mcUpdateAutomaticDatePreview(){String mcFormat=mcCurrentDateFormat.getValue();if(mcFormat==null)return;mcCurrentDatePreview.setText("Hoy: "+LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(mcFormat,Locale.forLanguageTag("es-PE"))));}
    private Map<String,Map<String,String>> mcScopedValues(Map<String,String> mcValues){Map<String,Map<String,String>> mcScopes=new LinkedHashMap<>();mcValues.forEach((mcKey,mcValue)->mcScopes.computeIfAbsent(MCDocumentEngine.mcPersonaKey(mcKey),mcK->new LinkedHashMap<>()).put(MCDocumentEngine.mcBaseKey(mcKey),mcValue));return mcScopes;}
    private void mcLearnScopedComplements(Map<String,String> mcValues)throws Exception{
        Map<Long,MCDefinition> mcDefinitions=new HashMap<>();mcActiveComplementDefinitions.forEach(mcD->mcDefinitions.put(mcD.mcId(),mcD));
        for(var mcEntry:mcScopedValues(MCContactReuse.mcClientValues(mcValues,mcStore.mcDefinitions())).entrySet()){Map<String,String> mcScope=mcEntry.getValue();if(mcEntry.getKey().isEmpty()){mcData.mcLearnComplements(mcActiveComplementDefinitions,mcActiveComplementLinks,mcScope);continue;}
            for(var mcLink:mcActiveComplementLinks){MCDefinition mcSource=mcDefinitions.get(mcLink.mcSourceDefId()),mcTarget=mcDefinitions.get(mcLink.mcTargetDefId());if(mcSource!=null&&mcTarget!=null)mcData.mcRememberPair(mcSource.mcKey(),mcScope.get(mcSource.mcKey()),mcTarget.mcKey(),mcScope.get(mcTarget.mcKey()),mcLink.mcBidirectional());}
        }
    }
    private void mcBuildApi(){
        mcApiPage=new BorderPane();mcApiPage.getStyleClass().add("page");mcApiToken.setPromptText("Clave nueva o de reemplazo");mcApiType.getItems().setAll("DNI","RUC");mcApiType.setValue("DNI");mcApiNumber.setPromptText("Número a consultar");mcCredentialProvider.getItems().setAll("Factiliza","APIsPERU");mcCredentialProvider.setValue("Factiliza");mcCredentialName.setPromptText("Nombre de la credencial");mcCredentialList.setPrefHeight(150);mcCredentialList.setMinHeight(110);mcTwoLineCells(mcCredentialList,MCApiCredentialStore.MCCredential::mcName,mcC->mcC.mcProvider()+" · "+mcC.mcStatus());mcCredentialList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcB)->mcLoadCredential(mcB));mcCredentialStats.setWrapText(true);
        VBox mcManager=new VBox(10,new Label("CREDENCIALES LOCALES"),mcCredentialList,mcButton("+ Nueva credencial",this::mcNewCredential),mcLabelled("Nombre",mcCredentialName),mcLabelled("Proveedor",mcCredentialProvider),mcLabelled("Clave API",mcApiToken),mcCredentialEnabled,mcButton("Guardar credencial",this::mcSaveCredential),mcButton("Eliminar credencial",this::mcDeleteCredential),mcCredentialStats);mcManager.getStyleClass().add("side-panel");mcUniformButtons(mcManager);ScrollPane mcManagerScroll=mcScrolling(mcManager);mcFixedWidth(mcManagerScroll,380);mcApiPage.setLeft(mcManagerScroll);BorderPane.setMargin(mcManagerScroll,new Insets(0,14,0,0));
        GridPane mcQuery=mcTwoColumns();mcQuery.add(mcLabelled("Documento",mcApiType),0,0);mcQuery.add(mcLabelled("Número",mcApiNumber),1,0);
        FlowPane mcActions=new FlowPane(8,6,mcButton("Consultar",()->mcRunApiQuery(mcApiType.getValue(),mcApiNumber.getText(),null,this::mcShowApiResult)),mcButton("Probar seleccionada",()->{MCApiCredentialStore.MCCredential mcC=mcCredentialList.getSelectionModel().getSelectedItem();if(mcC==null)throw new IllegalArgumentException("Seleccione una credencial.");mcRunApiQuery(mcApiType.getValue(),mcApiNumber.getText(),mcC.mcId(),this::mcShowApiResult);}));
        mcApiState.setWrapText(true);mcApiSaved.setWrapText(true);mcApiSaved.getStyleClass().add("automatic-save");VBox mcContent=new VBox(14,new Label("CONSULTA DNI / RUC"),mcQuery,mcActions,mcApiState,mcApiSaved,mcApiResultBox);mcContent.getStyleClass().add("catalog-form");mcApiPage.setCenter(mcScrolling(mcContent));mcRefreshCredentialList();mcNewCredential();
    }
    private void mcShowApi(){mcRefreshCredentialList();mcPage("Conexiones API",mcApiPage);mcApiState.setText(mcCredentialList.getItems().isEmpty()?"Sin credenciales · El sistema funciona sin conexión":"Consultas opcionales · Selección automática de proveedor");}
    private void mcRefreshCredentialList(){try{long mcId=mcCredentialList.getSelectionModel().getSelectedItem()==null?0:mcCredentialList.getSelectionModel().getSelectedItem().mcId();mcCredentialList.getItems().setAll(mcCredentials.mcCredentials());if(mcId>0)mcCredentialList.getSelectionModel().select(mcCredentialList.getItems().stream().filter(mcC->mcC.mcId()==mcId).findFirst().orElse(null));}catch(Exception mcE){mcError(mcE);}}
    private void mcNewCredential(){mcCredentialList.getSelectionModel().clearSelection();mcCredentialName.clear();mcCredentialProvider.setDisable(false);mcCredentialProvider.setValue("Factiliza");mcApiToken.clear();mcCredentialEnabled.setSelected(true);mcCredentialStats.setText("Sin credencial seleccionada");}
    private void mcLoadCredential(MCApiCredentialStore.MCCredential mcC){if(mcC==null)return;mcCredentialName.setText(mcC.mcName());mcCredentialProvider.setValue(mcC.mcProvider());mcCredentialProvider.setDisable(true);mcApiToken.clear();mcCredentialEnabled.setSelected(mcC.mcEnabled());mcCredentialStats.setText("Registrada: "+mcC.mcCreatedAt()+"\nMes actual: "+mcC.mcRequests()+" solicitudes · "+mcC.mcSuccesses()+" correctas · "+mcC.mcFailures()+" fallidas\nÚltimo uso: "+(mcC.mcLastUsed()==null?"ninguno":mcC.mcLastUsed())+"\nEstado: "+mcC.mcStatus());}
    private void mcSaveCredential(){try{MCApiCredentialStore.MCCredential mcCurrent=mcCredentialList.getSelectionModel().getSelectedItem();if(mcCurrent==null){MCApiCredentialStore.MCCredential mcAdded=mcCredentials.mcAdd(mcCredentialName.getText(),mcCredentialProvider.getValue(),mcApiToken.getText());mcCredentials.mcEnabled(mcAdded.mcId(),mcCredentialEnabled.isSelected());mcRefreshCredentialList();mcCredentialList.getSelectionModel().select(mcCredentialList.getItems().stream().filter(mcC->mcC.mcId()==mcAdded.mcId()).findFirst().orElse(null));}else{mcCredentials.mcRename(mcCurrent.mcId(),mcCredentialName.getText());mcCredentials.mcEnabled(mcCurrent.mcId(),mcCredentialEnabled.isSelected());if(!mcApiToken.getText().isBlank())mcCredentials.mcReplace(mcCurrent.mcId(),mcApiToken.getText());mcRefreshCredentialList();}mcApiToken.clear();mcSaved("Credencial guardada localmente");}catch(Exception mcE){mcError(mcE);}}
    private void mcDeleteCredential(){MCApiCredentialStore.MCCredential mcC=mcCredentialList.getSelectionModel().getSelectedItem();if(mcC==null)return;if(new Alert(Alert.AlertType.CONFIRMATION,"¿Eliminar la credencial '"+mcC.mcName()+"'? Los documentos y datos locales permanecerán intactos.",ButtonType.YES,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.YES)return;try{mcCredentials.mcDelete(mcC.mcId());mcRefreshCredentialList();mcNewCredential();mcSaved("Credencial eliminada; evidencia no sensible conservada");}catch(Exception mcE){mcError(mcE);}}
    private void mcRunApiQuery(String mcType,String mcNumber,Long mcCredentialId,java.util.function.Consumer<MCLookupRouter.MCResult> mcOnSuccess){
        if(mcApiQueryRunning){mcSaved("Ya hay una consulta en curso.");return;}mcApiQueryRunning=true;mcApiSaved.setText("");mcApiState.setText("Consultando…");
        Task<MCLookupRouter.MCResult> mcTask=new Task<>(){@Override protected MCLookupRouter.MCResult call()throws Exception{
            MCLookupRouter.MCResult mcResult=mcCredentialId==null?mcLookupRouter.mcQuery(mcType,mcNumber.trim()):mcLookupRouter.mcQuerySpecific(mcType,mcNumber.trim(),mcCredentialId);
            if(mcResult.mcUsable())mcData.mcUpsertContact(MCContactReuse.mcFromApi(mcResult));return mcResult;
        }};
        mcTask.setOnSucceeded(mcE->{mcApiQueryRunning=false;MCLookupRouter.MCResult mcResult=mcTask.getValue();mcApiState.setText(mcResult.mcUsable()?"Respondió "+mcResult.mcProvider()+" · "+(mcResult.mcStatus()==MCLookupRouter.MCStatus.PARTIAL?"datos parciales":"consulta correcta"):mcResult.mcError());mcRefreshCredentialList();if(mcResult.mcUsable()){mcApiSaved.setText("✓ Guardado automáticamente en Clientes");mcRefreshContacts();mcOnSuccess.accept(mcResult);}});
        mcTask.setOnFailed(mcE->{mcApiQueryRunning=false;mcApiState.setText("Consulta no completada: "+mcTask.getException().getMessage());mcRefreshCredentialList();});Thread mcThread=new Thread(mcTask,"consulta-documental");mcThread.setDaemon(true);mcThread.start();
    }
    private static Map<String,String> mcNormalizedApiValues(MCLookupRouter.MCResult mcResult){Map<String,String> mcValues=new LinkedHashMap<>(mcResult.mcValues());if("DNI".equals(mcResult.mcService()))MCApiFieldCatalog.mcNormalizePersonName(mcValues);return mcValues;}
    private void mcShowApiResult(MCLookupRouter.MCResult mcResult){
        mcApiResultBox.getChildren().clear();GridPane mcGrid=mcTwoColumns();int mcIndex=0;
        for(MCApiFieldCatalog.MCField mcItem:MCApiFieldCatalog.mcFields(mcResult.mcService())){
            String mcValue=mcNormalizedApiValues(mcResult).getOrDefault(mcItem.mcKey(),"");Label mcDataLabel=new Label(mcValue.isBlank()?"No disponible":mcValue);mcDataLabel.setWrapText(true);mcDataLabel.setMinWidth(0);VBox mcCell=mcLabelled(mcItem.mcLabel(),mcDataLabel);mcCell.getStyleClass().add("result-field");mcGrid.add(mcCell,mcIndex%2,mcIndex/2);mcIndex++;
        }
        mcApiResultBox.getChildren().addAll(mcDescription("Proveedor: "+mcResult.mcProvider()),mcGrid);mcApiSaved.setText("✓ Guardado automáticamente en Clientes");mcSaved("Consulta completada · Cliente guardado automáticamente");
    }

    private void mcRefreshMappings(){MCTemplate mcT=mcMapTemplate.getValue();try{if(mcT==null){mcMapSource.getItems().clear();mcMapTarget.getItems().clear();mcMappingList.getItems().clear();return;}List<MCVariable> mcVars=mcStore.mcVariables(mcT.mcId());mcMapSource.getItems().setAll(mcVars.stream().filter(mcV->mcV.mcType().equals("DNI")||mcV.mcType().equals("RUC")).toList());mcMapTarget.getItems().setAll(mcVars);mcMappingList.getItems().setAll(mcData.mcMappings(mcT.mcId()));}catch(Exception mcE){mcError(mcE);}}
    private void mcRefreshApiFieldChoices(){MCVariable mcSource=mcMapSource.getValue();if(mcSource==null)return;if(mcSource.mcType().equals("DNI"))mcMapApiField.getItems().setAll("numero","nombres","apellido_paterno","apellido_materno","nombre_completo","direccion","direccion_completa","departamento","provincia","distrito");else mcMapApiField.getItems().setAll("numero","nombre_o_razon_social","direccion","direccion_completa","tipo_contribuyente","estado","condicion","departamento","provincia","distrito");}
    private void mcSaveMapping(){MCTemplate mcT=mcMapTemplate.getValue();MCVariable mcSource=mcMapSource.getValue(),mcTarget=mcMapTarget.getValue();String mcField=mcMapApiField.getValue();if(mcT==null||mcSource==null||mcTarget==null||mcField==null||mcField.isBlank())throw new IllegalArgumentException("Seleccione plantilla, origen, dato y destino.");try{mcData.mcSaveMapping(new MCMapping(0,mcT.mcId(),mcSource.mcKey(),mcField.trim(),mcTarget.mcKey()));mcRefreshMappings();mcSaved("Correspondencia guardada");}catch(Exception mcE){mcError(mcE);}}
    private void mcDeleteMapping(){MCMapping mcM=mcMappingList.getSelectionModel().getSelectedItem();if(mcM==null)return;try{mcData.mcDeleteMapping(mcM.mcId());mcRefreshMappings();mcSaved("Correspondencia eliminada");}catch(Exception mcE){mcError(mcE);}}
    private long mcSafeDefinitionId(MCVariable mcVariable){try{return mcDefinitionId(mcVariable);}catch(Exception mcE){throw new IllegalStateException(mcE);}}
    private long mcDefinitionId(MCVariable mcVariable)throws Exception{return mcVariable.mcId()<0?-mcVariable.mcId():mcStore.mcDefinitionId(mcVariable.mcId());}
    private String mcQueryService(long mcTemplateId,MCVariable mcVariable)throws Exception{
        MCDefinition mcDefinition=mcDefinitionFor(mcVariable,mcStore.mcDefinitions());if(mcDefinition!=null&&!mcDefinition.mcBindToClient())return null;
        for(MCApiContext mcContext:mcData.mcApiContexts(mcTemplateId))if(mcContext.mcVariableKey().equals(mcVariable.mcKey()))return mcContext.mcSource()?mcContext.mcService():null;
        for(MCContactBinding mcBinding:mcData.mcContactBindings(mcTemplateId))if(mcBinding.mcVariableKey().equals(mcVariable.mcKey()))return "number".equals(mcBinding.mcField())?("Empresa".equals(mcBinding.mcKind())?"RUC":"DNI"):null;
        if(List.of("DNI","RUC").contains(mcVariable.mcType()))return mcVariable.mcType();
        long mcId=mcDefinitionId(mcVariable);MCApiBinding mcBinding=mcId>0?mcData.mcApiBinding(mcId):null;
        if(mcBinding!=null)return "numero".equals(MCApiFieldCatalog.mcCanonical(mcBinding.mcService(),mcBinding.mcPath()))?mcBinding.mcService():null;
        return MCContactReuse.mcIdentifierService(mcVariable);
    }
    private void mcLookupForField(MCTemplate mcTemplate,MCVariable mcVariable,TextField mcField,String mcService){
        PauseTransition mcDelay=mcLookupDelays.get(mcVariable.mcKey());if(mcDelay!=null)mcDelay.stop();
        String mcNumber=mcField.getText().trim();if(!mcValidQueryNumber(mcService,mcNumber))throw new IllegalArgumentException("Número de documento inválido.");
        try{if(mcLoadLocalForField(mcTemplate,mcVariable,mcField,mcService))return;}catch(Exception mcE){mcError(mcE);return;}
        mcRunApiQuery(mcService,mcNumber,null,mcResult->{
            if(mcGenerateTemplate.getValue()==null||mcGenerateTemplate.getValue().mcId()!=mcTemplate.mcId()||mcInputs.get(mcVariable.mcKey())!=mcField||!mcField.getText().trim().equals(mcNumber))return;
            int mcCount=mcApplyApiBindings(mcTemplate.mcId(),mcVariable.mcKey(),mcResult);
            try{mcCount+=mcApplyContactFields(mcTemplate.mcId(),mcVariable.mcKey(),mcData.mcMatchingContact("RUC".equals(mcService)?"Empresa":"Persona",mcService,mcNumber),mcService);}catch(Exception mcE){mcError(mcE);}
            mcSaved("Respondió "+mcResult.mcProvider()+" · "+mcCount+" campos completados · Cliente guardado automáticamente");
        });
    }
    private static boolean mcValidQueryNumber(String mcService,String mcNumber){return mcNumber!=null&&mcNumber.trim().matches("RUC".equals(mcService)?"\\d{11}":"\\d{8}");}
    private boolean mcLoadLocalForField(MCTemplate mcTemplate,MCVariable mcVariable,TextField mcField,String mcService)throws Exception{
        MCContact mcLocal=mcData.mcMatchingContact("RUC".equals(mcService)?"Empresa":"Persona",mcService,mcField.getText());if(mcLocal==null)return false;
        mcResetContactEdits(mcTemplate,mcVariable,mcService);
        Map<String,String> mcValues=MCContactReuse.mcApiValues(mcLocal);MCLookupRouter.MCResult mcResult=new MCLookupRouter.MCResult(mcService,mcField.getText().trim(),"Clientes locales",mcValues,mcValues.keySet(),Set.of(),MCLookupRouter.MCStatus.SUCCESS,"",mcValues);
        mcApplyApiBindings(mcTemplate.mcId(),mcVariable.mcKey(),mcResult);mcApplyContactFields(mcTemplate.mcId(),mcVariable.mcKey(),mcLocal,mcService);mcSaved("Datos cargados desde Clientes locales");return true;
    }
    private void mcResetContactEdits(MCTemplate mcTemplate,MCVariable mcSource,String mcService)throws Exception{
        List<MCContactBinding> mcBindings=mcContactFormBindings(mcTemplate.mcId(),new ArrayList<>(mcFormVariables.values()));String mcGroup=mcContextGroup(mcData.mcApiContexts(mcTemplate.mcId()),mcBindings,mcSource.mcKey(),mcService);String mcKind="RUC".equals(mcService)?"Empresa":"Persona";
        long mcSources=mcBindings.stream().filter(mcB->"number".equals(mcB.mcField())&&mcB.mcKind().equals(mcKind)).count();
        for(MCContactBinding mcBinding:mcBindings)if(!mcBinding.mcVariableKey().equals(mcSource.mcKey())&&mcBinding.mcKind().equals(mcKind)&&mcSameQueryGroup(mcGroup,mcBinding.mcGroupKey(),mcSources,mcSource.mcKey(),mcBinding.mcVariableKey()))mcManuallyEdited.remove(mcBinding.mcVariableKey());
    }
    private int mcApplyContactFields(long mcTemplateId,String mcSourceKey,MCContact mcContact,String mcService)throws Exception{
        if(mcContact==null)return 0;List<MCContactBinding> mcBindings=mcContactFormBindings(mcTemplateId,new ArrayList<>(mcFormVariables.values()));List<MCApiContext> mcContexts=mcData.mcApiContexts(mcTemplateId);
        String mcSourceGroup=mcContextGroup(mcContexts,mcBindings,mcSourceKey,mcService);long mcCount=mcBindings.stream().filter(mcB->"number".equals(mcB.mcField())&&mcB.mcKind().equals(mcContact.mcKind())).count();int mcCompleted=0;
        for(MCContactBinding mcBinding:mcBindings){if(!mcBinding.mcKind().equals(mcContact.mcKind())||!mcSameQueryGroup(mcSourceGroup,mcBinding.mcGroupKey(),mcCount,mcSourceKey,mcBinding.mcVariableKey())||mcManuallyEdited.contains(mcBinding.mcVariableKey()))continue;
            String mcValue=MCContactReuse.mcValuesFor(mcContact,List.of(mcBinding)).get(mcBinding.mcVariableKey());Control mcTarget=mcInputs.get(mcBinding.mcVariableKey());if(mcTarget!=null&&mcValue!=null&&!mcValue.isBlank()){mcApplyingApi=true;try{mcSetInput(mcTarget,mcValue);mcCompleted++;}finally{mcApplyingApi=false;}}
        }return mcCompleted;
    }
    private int mcApplyApiBindings(long mcTemplateId,String mcSourceKey,MCLookupRouter.MCResult mcResult){int mcCompleted=0;try{
        List<MCContactBinding> mcContacts=mcData.mcContactBindings(mcTemplateId);List<MCApiContext> mcContexts=mcData.mcApiContexts(mcTemplateId);String mcSourceGroup=mcContextGroup(mcContexts,mcContacts,mcSourceKey,mcResult.mcService());
        long mcSources=mcFormVariables.values().stream().filter(mcV->{try{return mcResult.mcService().equals(mcQueryService(mcTemplateId,mcV));}catch(Exception mcE){return false;}}).count();
        for(var mcEntry:mcFormVariables.entrySet()){
            MCVariable mcVariable=mcEntry.getValue();long mcId=mcDefinitionId(mcVariable);MCDefinition mcDefinition=mcDefinitionFor(mcVariable,mcStore.mcDefinitions());if(mcDefinition!=null&&!mcDefinition.mcBindToClient())continue;MCApiBinding mcBinding=mcId>0?mcData.mcApiBinding(mcId):null;
            if(mcBinding==null||!mcBinding.mcService().equals(mcResult.mcService()))continue;
            String mcTargetGroup=mcContextGroup(mcContexts,mcContacts,mcVariable.mcKey(),mcResult.mcService());
            if(!mcSameQueryGroup(mcSourceGroup,mcTargetGroup,mcSources,mcSourceKey,mcVariable.mcKey()))continue;
            String mcNormalized=MCApiFieldCatalog.mcCanonical(mcBinding.mcService(),mcBinding.mcPath());String mcValue=mcNormalized.startsWith("legacy:")?("Clientes locales".equals(mcResult.mcProvider())?mcResult.mcRaw().get(mcBinding.mcPath()):MCApiFieldCatalog.mcLegacyValue(mcResult.mcProvider(),mcBinding.mcPath(),mcResult.mcRaw())):mcNormalizedApiValues(mcResult).get(mcNormalized);
            if(mcValue!=null&&!mcValue.isBlank()&&!mcManuallyEdited.contains(mcVariable.mcKey())){Control mcTarget=mcInputs.get(mcVariable.mcKey());if(mcTarget!=null){mcApplyingApi=true;try{mcSetInput(mcTarget,mcValue);mcCompleted++;}finally{mcApplyingApi=false;}}}
        }
        for(MCMapping mcMapping:mcData.mcMappings(mcTemplateId))if(MCContactReuse.mcClientKey(mcMapping.mcTargetKey(),mcStore.mcDefinitions())&&mcMapping.mcSourceKey().equals(mcSourceKey)&&!mcManuallyEdited.contains(mcMapping.mcTargetKey())&&mcSameQueryGroup(mcSourceGroup,mcContextGroup(mcContexts,mcContacts,mcMapping.mcTargetKey(),mcResult.mcService()),mcSources,mcSourceKey,mcMapping.mcTargetKey())){String mcCanonical=MCApiFieldCatalog.mcCanonical(mcResult.mcService(),mcMapping.mcApiField());String mcValue="nombre_completo".equals(mcCanonical)?mcNormalizedApiValues(mcResult).get(mcCanonical):mcResult.mcRaw().getOrDefault(mcMapping.mcApiField(),mcResult.mcRaw().getOrDefault("data."+mcMapping.mcApiField(),mcNormalizedApiValues(mcResult).get(mcCanonical)));Control mcTarget=mcInputs.get(mcMapping.mcTargetKey());if(mcValue!=null&&!mcValue.isBlank()&&mcTarget!=null){mcApplyingApi=true;try{mcSetInput(mcTarget,mcValue);mcCompleted++;}finally{mcApplyingApi=false;}}}
    }catch(Exception mcE){mcError(mcE);}return mcCompleted;}
    static String mcContextGroup(List<MCApiContext> mcContexts,List<MCContactBinding> mcContacts,String mcKey,String mcService){if(!MCDocumentEngine.mcPersonaKey(mcKey).isEmpty())return "persona:"+MCDocumentEngine.mcPersonaKey(mcKey);for(MCApiContext mcC:mcContexts)if(mcC.mcVariableKey().equals(mcKey)&&mcC.mcService().equals(mcService))return mcC.mcGroupKey();for(MCContactBinding mcB:mcContacts)if(mcB.mcVariableKey().equals(mcKey))return mcB.mcGroupKey();return null;}
    static boolean mcSameQueryGroup(String mcSource,String mcTarget,long mcSourceCount,String mcSourceKey,String mcTargetKey){if(!MCDocumentEngine.mcPersonaKey(mcSourceKey).equals(MCDocumentEngine.mcPersonaKey(mcTargetKey)))return false;if(mcSource!=null)return mcSource.equals(mcTarget)||mcSourceKey.equals(mcTargetKey);return mcSourceCount<=1||mcSourceKey.equals(mcTargetKey);}
    private static String mcReadInput(Control mcInput){if(mcInput instanceof DatePicker mcPicker)return mcPicker.getValue()==null?"":mcPicker.getValue().toString();if(mcInput instanceof TextArea mcArea)return mcArea.getText();return ((TextField)mcInput).getText();}
    private static void mcSetInput(Control mcInput,String mcValue){if(mcInput instanceof DatePicker mcPicker){try{mcPicker.setValue(LocalDate.parse(mcValue));}catch(Exception mcFirst){try{mcPicker.setValue(LocalDate.parse(mcValue,java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")));}catch(Exception mcIgnored){}}}else if(mcInput instanceof TextArea mcArea)mcArea.setText(mcValue);else ((TextField)mcInput).setText(mcValue);}

    private void mcBuildHistory(){
        mcHistoryPage=new BorderPane();mcHistoryPage.getStyleClass().add("page");mcHistorySearch.setPromptText("Buscar título o fecha");mcHistorySearch.setMinWidth(0);mcHistorySearch.textProperty().addListener((mcO,mcA,mcB)->mcRefreshHistory());mcHistoryDate.setPromptText("Fecha");mcHistoryDate.setMinWidth(0);mcHistoryDate.setPrefWidth(120);mcHistoryDate.valueProperty().addListener((mcO,mcA,mcB)->mcRefreshHistory());
        Button mcClear=mcButton("×",()->{mcHistorySearch.clear();mcHistoryDate.setValue(null);});mcClear.setTooltip(new Tooltip("Quitar filtros"));HBox mcFilter=new HBox(6,mcHistorySearch,mcHistoryDate,mcClear);HBox.setHgrow(mcHistorySearch,Priority.ALWAYS);
        mcTwoLineCells(mcHistoryList,MCHistory::mcTitle,mcH->mcH.mcGeneratedAt()+" · Versión "+mcH.mcTemplateVersion());VBox mcList=new VBox(10,mcFilter,mcHistoryList);mcList.getStyleClass().add("side-panel");mcFixedWidth(mcList,380);VBox.setVgrow(mcHistoryList,Priority.ALWAYS);mcHistoryPage.setLeft(mcList);BorderPane.setMargin(mcList,new Insets(0,14,0,0));
        Label mcSummary=mcDescription("Seleccione un registro para ver sus detalles y recuperar sus archivos.");mcHistoryList.getSelectionModel().selectedItemProperty().addListener((mcO,mcA,mcH)->mcSummary.setText(mcH==null?"Seleccione un registro.":mcH.mcTitle()+"\nGenerado: "+mcH.mcGeneratedAt()+"\nVersión de plantilla: "+mcH.mcTemplateVersion()+"\nDOCX: "+(mcH.mcDocxPath()==null?"Conservado en el historial":mcH.mcDocxPath())+"\nPDF: "+(mcH.mcPdfPath()==null?"Disponible para exportar":mcH.mcPdfPath())));
        GridPane mcActions=mcTwoColumns();List<Button> mcButtons=List.of(mcButton("Abrir DOCX",this::mcOpenHistory),mcButton("Duplicar registro",this::mcDuplicateHistory),mcButton("Editar y regenerar",this::mcEditHistory),mcButton("Exportar DOCX",()->mcExportHistory(false)),mcButton("Exportar PDF",()->mcExportHistory(true)),mcButton("Eliminar registro",this::mcDeleteHistory));int mcI=0;for(Button mcB:mcButtons){mcB.setWrapText(true);mcB.setMaxWidth(Double.MAX_VALUE);mcActions.add(mcB,mcI%2,mcI/2);mcI++;}
        VBox mcDetail=new VBox(18,new Label("DOCUMENTO SELECCIONADO"),mcSummary,mcActions);mcDetail.getStyleClass().add("catalog-form");mcHistoryPage.setCenter(mcScrolling(mcDetail));
    }
    private void mcShowHistory(){mcRefreshHistory();mcPage("Historial",mcHistoryPage);}
    private void mcRefreshHistory(){try{String mcQ=mcHistorySearch.getText().toLowerCase();String mcDate=mcHistoryDate.getValue()==null?null:mcHistoryDate.getValue().toString();mcHistoryList.getItems().setAll(mcData.mcHistory().stream().filter(mcH->(mcH.mcTitle()+" "+mcH.mcGeneratedAt()).toLowerCase().contains(mcQ)).filter(mcH->mcDate==null||mcH.mcGeneratedAt().startsWith(mcDate)).toList());}catch(Exception mcE){mcError(mcE);}}
    private MCHistory mcSelectedHistory(){MCHistory mcH=mcHistoryList.getSelectionModel().getSelectedItem();if(mcH==null)throw new IllegalArgumentException("Seleccione un documento del historial.");return mcH;}
    private void mcOpenHistory(){try{MCHistory mcH=mcSelectedHistory();Path mcDir=mcStore.mcDirectory().resolve("history-files");Files.createDirectories(mcDir);Path mcPath=mcDir.resolve("documento-"+mcH.mcId()+".docx");Files.write(mcPath,mcH.mcDocx());if(!Desktop.isDesktopSupported())throw new IllegalStateException("No hay aplicación asociada a DOCX en este equipo.");Desktop.getDesktop().open(mcPath.toFile());mcSaved("Abriendo DOCX conservado en el historial");}catch(Exception mcE){mcError(mcE);}}
    private void mcDuplicateHistory(){try{MCHistory mcH=mcSelectedHistory();mcData.mcSaveHistory(new MCHistory(0,mcH.mcTitle()+" (copia)",mcH.mcTemplateId(),mcH.mcTemplateVersion(),null,mcH.mcValuesJson(),null,null,mcH.mcDocx(),mcH.mcPdf()));mcRefreshHistory();mcSaved("Registro duplicado");}catch(Exception mcE){mcError(mcE);}}
    private void mcEditHistory(){try{
        MCHistory mcHistory=mcSelectedHistory();if(mcHistory.mcTemplateId()==null)throw new IllegalStateException("La plantilla original ya no existe. Puede abrir o exportar el documento conservado.");MCTemplate mcTemplate=mcStore.mcTemplates().stream().filter(mcT->mcT.mcId()==mcHistory.mcTemplateId()).findFirst().orElseThrow(()->new IllegalStateException("La plantilla original ya no existe."));
        mcShowGenerator();MCTemplateGroup mcGroup=null;for(MCTemplateGroup mcCandidate:mcGenerateGroup.getItems())if(mcStore.mcInGroup(mcTemplate.mcId(),mcCandidate.mcId())){mcGroup=mcCandidate;break;}
        if(mcGroup==null)throw new IllegalStateException("Asocie la plantilla a un grupo para generar documentos.");
        mcGenerateSearch.clear();mcGenerateGroup.getSelectionModel().select(mcGroup);mcRefreshKinds();mcGenerateTemplate.getSelectionModel().select(mcGenerateTemplate.getItems().stream().filter(mcT->mcT.mcId()==mcTemplate.mcId()).findFirst().orElseThrow());mcBuildDynamicForm();
        mcHistoricalTemplate=mcData.mcRevision(mcHistory.mcTemplateId(),mcHistory.mcTemplateVersion());if(mcHistoricalTemplate==null)throw new IllegalStateException("No se conserva la versión de la plantilla.");
        var mcValues=mcJson.readTree(mcHistory.mcValuesJson());mcApplyingApi=true;try{for(var mcEntry:mcInputs.entrySet())mcSetInput(mcEntry.getValue(),mcValues.path(mcEntry.getKey()).asText(""));}finally{mcApplyingApi=false;}
        mcSaved("Valores recuperados; edite y genere un nuevo documento");
    }catch(Exception mcE){mcError(mcE);}}

    private void mcExportHistory(boolean mcPdf){try{MCHistory mcH=mcSelectedHistory();byte[] mcContent=mcPdf?(mcH.mcPdf()==null?MCDocxPdfExporter.mcRender(mcH.mcDocx()):mcH.mcPdf()):mcH.mcDocx();FileChooser mcFc=new FileChooser();mcFc.setInitialFileName(mcH.mcTitle().replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚ _-]","")+(mcPdf?".pdf":".docx"));mcFc.getExtensionFilters().add(new FileChooser.ExtensionFilter(mcPdf?"PDF":"Word DOCX",mcPdf?"*.pdf":"*.docx"));File mcFile=mcFc.showSaveDialog(mcStage);if(mcFile==null)return;Files.write(mcFile.toPath(),mcContent);mcData.mcUpdateHistoryFiles(mcH.mcId(),mcPdf?null:mcFile.toString(),mcPdf?mcFile.toString():null,mcPdf?mcContent:null);mcRefreshHistory();mcSaved("Archivo exportado nuevamente");}catch(Exception mcE){mcError(mcE);}}
    private void mcDeleteHistory(){try{MCHistory mcH=mcSelectedHistory();mcData.mcDeleteHistory(mcH.mcId());mcRefreshHistory();mcSaved("Registro eliminado; los archivos exportados permanecen en su ubicación");}catch(Exception mcE){mcError(mcE);}}

    private void mcBuildSettings(){
        mcSettingsPage=new BorderPane();mcSettingsPage.getStyleClass().add("page");
        mcImportConflict.getItems().setAll("Conservar existentes","Reemplazar existentes");mcImportConflict.getSelectionModel().selectFirst();
        GridPane mcImportRow=mcTwoColumns();Button mcImportButton=mcButton("Importar archivo (.zip)",this::mcImportConfig);GridPane.setValignment(mcImportButton,VPos.BOTTOM);mcImportRow.add(mcImportButton,0,0);mcImportRow.add(mcLabelled("Resolución de duplicados",mcImportConflict),1,0);
        VBox mcImporting=new VBox(12,new Label("Importar"),mcDescription("El archivo puede contener plantillas, clientes o ambos."),mcImportRow);mcImporting.getStyleClass().add("transfer-card");
        mcExportScope.getItems().setAll("Solo una plantilla","Todas las plantillas de un grupo","Todas las plantillas");mcExportScope.getSelectionModel().selectFirst();
        mcExportTemplate.setPromptText("Seleccione una plantilla");mcExportGroup.setPromptText("Seleccione un grupo");
        mcExportTemplate.disableProperty().bind(mcExportScope.valueProperty().isNotEqualTo("Solo una plantilla"));mcExportGroup.disableProperty().bind(mcExportScope.valueProperty().isNotEqualTo("Todas las plantillas de un grupo"));
        GridPane mcScopeFields=mcTwoColumns();mcScopeFields.add(mcLabelled("Plantilla",mcExportTemplate),0,0);mcScopeFields.add(mcLabelled("Grupo",mcExportGroup),1,0);
        VBox mcTemplates=new VBox(12,new Label("Exportar Plantillas"),mcDescription("Incluye los documentos y sus variables, grupos y asociaciones. Las credenciales permanecen en este equipo."),mcLabelled("Alcance de exportación",mcExportScope),mcScopeFields,mcButton("Exportar plantillas (.zip)",this::mcExportConfig));mcTemplates.getStyleClass().add("transfer-card");
        VBox mcClients=new VBox(12,new Label("Exportar Clientes"),mcDescription("Exporta todas las personas y empresas, incluidos sus datos adicionales."),mcButton("Exportar clientes (.zip)",this::mcExportClients));mcClients.getStyleClass().add("transfer-card");
        VBox mcContent=new VBox(14,mcImporting,mcTemplates,mcClients);mcContent.setFillWidth(true);mcSettingsPage.setCenter(mcScrolling(mcContent));
    }
    private void mcShowSettings(){try{
        MCTemplate mcSelected=mcExportTemplate.getValue();MCTemplateGroup mcGroup=mcExportGroup.getValue();mcExportTemplate.getItems().setAll(mcStore.mcTemplates());mcExportGroup.getItems().setAll(mcStore.mcGroups());
        mcExportTemplate.getSelectionModel().select(mcExportTemplate.getItems().stream().filter(mcT->mcSelected!=null&&mcT.mcId()==mcSelected.mcId()).findFirst().orElse(null));
        mcExportGroup.getSelectionModel().select(mcExportGroup.getItems().stream().filter(mcG->mcGroup!=null&&mcG.mcId()==mcGroup.mcId()).findFirst().orElse(null));
        if(mcExportTemplate.getValue()==null)mcExportTemplate.getSelectionModel().selectFirst();if(mcExportGroup.getValue()==null)mcExportGroup.getSelectionModel().selectFirst();
    }catch(Exception mcE){mcError(mcE);}mcPage("Configuración y respaldos",mcSettingsPage);}
    private void mcExportConfig(){try{
        Collection<Long> mcIds=null;
        if("Solo una plantilla".equals(mcExportScope.getValue())){if(mcExportTemplate.getValue()==null)throw new IllegalArgumentException("Seleccione una plantilla.");mcIds=List.of(mcExportTemplate.getValue().mcId());}
        else if("Todas las plantillas de un grupo".equals(mcExportScope.getValue())){if(mcExportGroup.getValue()==null)throw new IllegalArgumentException("Seleccione un grupo.");List<Long> mcSelected=new ArrayList<>();for(MCTemplate mcT:mcStore.mcTemplates())if(mcStore.mcInGroup(mcT.mcId(),mcExportGroup.getValue().mcId()))mcSelected.add(mcT.mcId());if(mcSelected.isEmpty())throw new IllegalArgumentException("El grupo no contiene plantillas.");mcIds=mcSelected;}
        File mcFile=mcZipChooser("plantillas-documentales.zip").showSaveDialog(mcStage);if(mcFile==null)return;mcTransfer.mcExportConfiguration(mcFile.toPath(),mcIds);mcSaved("Plantillas exportadas con todos sus datos relacionados");
    }catch(Exception mcE){mcError(mcE);}}
    private void mcImportConfig(){File mcFile=mcZipChooser(null).showOpenDialog(mcStage);if(mcFile==null)return;try{
        MCStudioTransfer.MCImportResult mcResult=mcTransfer.mcImportConfiguration(mcFile.toPath(),"Reemplazar existentes".equals(mcImportConflict.getValue())?MCStudioTransfer.MCConflict.REPLACE:MCStudioTransfer.MCConflict.KEEP);
        mcRefreshTemplates();mcRefreshLibrary();mcRefreshKinds();mcRefreshDefinitions();mcRefreshContacts();mcShowSettings();
        mcSaved("Importadas "+mcResult.mcTemplates()+" plantillas, "+mcResult.mcKinds()+" tipos y "+mcResult.mcClients()+" clientes · Coincidencias: "+mcResult.mcConflicts());
    }catch(Exception mcE){mcError(mcE);}}
    private static FileChooser mcZipChooser(String mcName){FileChooser mcChooser=new FileChooser();mcChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo ZIP","*.zip"));if(mcName!=null)mcChooser.setInitialFileName(mcName);return mcChooser;}
    private void mcExportClients(){File mcFile=mcZipChooser("clientes.zip").showSaveDialog(mcStage);if(mcFile==null)return;try{mcTransfer.mcExportClients(mcFile.toPath());mcSaved("Clientes exportados con sus datos adicionales");}catch(Exception mcE){mcError(mcE);}}
}
