package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.MCDocumentStore.MCVariable;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.stage.Screen;
import org.apache.poi.ss.util.CellReference;
import java.util.*;

/** Explicit, local mapping: variables are dragged onto real spreadsheet cells. */
final class MCExcelMappingDialog {
    private final Dialog<List<Map<String,String>>> MCdialog=new Dialog<>();
    private final TableView<Integer> MCtable=new TableView<>();
    private final VBox MCvariables=new VBox(10);
    private final Label MCvalidation=new Label();
    private final List<MCVariable> MCrequired;
    private final Map<String,MCExcelBatch.MCRange> MCranges=new LinkedHashMap<>();
    private final Map<String,String> MCerrors=new LinkedHashMap<>();
    private final Map<String,List<String>> MCusedCells=new HashMap<>();
    private final Map<String,MCMappingControls> MCcontrols=new LinkedHashMap<>();
    private final ButtonType MCgenerate=new ButtonType("Generar",ButtonBar.ButtonData.OK_DONE);
    private MCExcelBatch.MCSheet MCsheet;

    MCExcelMappingDialog(Window MCowner,List<MCExcelBatch.MCSheet> MCsheets,List<MCVariable> MCvariablesRequired) {
        if(MCsheets.isEmpty())throw new IllegalArgumentException("El libro no contiene hojas.");
        MCrequired=List.copyOf(MCvariablesRequired);MCdialog.initOwner(MCowner);MCdialog.setTitle("Subir Excel · Asignar variables");MCdialog.setResizable(true);
        MCdialog.getDialogPane().getButtonTypes().addAll(MCgenerate,ButtonType.CANCEL);
        var MCscreen=Screen.getPrimary().getVisualBounds();
        double MCwidth=Math.min(1160,MCscreen.getWidth()-40),MCheight=Math.min(800,MCscreen.getHeight()-60);
        MCdialog.getDialogPane().setPrefSize(MCwidth,MCheight);
        MCdialog.setOnShown(MCe->{MCdialog.setWidth(MCwidth);MCdialog.setHeight(MCheight);});
        if(MCowner!=null&&MCowner.getScene()!=null)MCdialog.getDialogPane().getStylesheets().setAll(MCowner.getScene().getStylesheets());
        ComboBox<String> MCsheetChoice=new ComboBox<>();MCsheetChoice.getItems().setAll(MCsheets.stream().map(MCExcelBatch.MCSheet::MCname).toList());
        MCsheetChoice.valueProperty().addListener((MCo,MCa,MCb)->{if(MCb!=null)MCselectSheet(MCsheets.get(MCsheetChoice.getSelectionModel().getSelectedIndex()));});
        Label MChelp=new Label("Arrastre cada variable sobre su celda inicial. Ajuste el rango y el orden de lectura. Una sola celda se repite en todo el lote.");MChelp.setWrapText(true);
        VBox MCtop=new VBox(8,MChelp);if(MCsheets.size()>1)MCtop.getChildren().add(new HBox(8,new Label("Hoja"),MCsheetChoice));
        MCtable.setMinWidth(280);MCtable.setEditable(false);MCtable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);MCtable.getStyleClass().add("excel-grid");
        ScrollPane MCside=new ScrollPane(MCvariables);MCside.setFitToWidth(true);MCside.setMinWidth(320);MCside.setPrefWidth(360);MCvariables.setPadding(new Insets(10));MCvariables.setMinWidth(0);
        SplitPane MCsplit=new SplitPane(MCtable,MCside);MCsplit.setDividerPositions(.66);
        MCvalidation.setWrapText(true);MCvalidation.getStyleClass().add("excel-validation");
        BorderPane MCbody=new BorderPane(MCsplit,MCtop,null,MCvalidation,null);BorderPane.setMargin(MCtop,new Insets(0,0,10,0));BorderPane.setMargin(MCvalidation,new Insets(10,0,0,0));
        MCdialog.getDialogPane().setContent(MCbody);
        MCdialog.setResultConverter(MCanswer->MCanswer==MCgenerate?MCExcelBatch.MCextract(MCsheet,MCkeys(),MCranges):null);
        MCsheetChoice.getSelectionModel().selectFirst();
    }
    Optional<List<Map<String,String>>> MCshow(){return MCdialog.showAndWait();}
    private List<String> MCkeys(){return MCrequired.stream().map(MCVariable::MCkey).toList();}
    private void MCselectSheet(MCExcelBatch.MCSheet MCselected) {
        MCsheet=MCselected;MCranges.clear();MCerrors.clear();MCcontrols.clear();MCvariables.getChildren().setAll(new Label("Variables requeridas por la plantilla"));
        for(MCVariable MCvariable:MCrequired){MCMappingControls MCcontrol=new MCMappingControls(MCvariable);MCcontrols.put(MCvariable.MCkey(),MCcontrol);MCvariables.getChildren().add(MCcontrol.MCcard);}
        MCtable.getColumns().clear();
        TableColumn<Integer,String> MCnumbers=new TableColumn<>("#");MCnumbers.setPrefWidth(48);MCnumbers.setSortable(false);MCnumbers.setCellValueFactory(MCc->new ReadOnlyStringWrapper(Integer.toString(MCc.getValue()+1)));MCtable.getColumns().add(MCnumbers);
        for(int MCc=0;MCc<Math.max(1,MCsheet.MCcolumns());MCc++) {
            final int MCcolumn=MCc;TableColumn<Integer,String> MCcol=new TableColumn<>(CellReference.convertNumToColString(MCc));MCcol.setPrefWidth(135);MCcol.setSortable(false);
            MCcol.setCellValueFactory(MCcell->new ReadOnlyStringWrapper(MCsheet.MCvalue(MCcell.getValue(),MCcolumn)));
            MCcol.setCellFactory(MCignored->new TableCell<>() {
                {setOnDragOver(MCe->{if(MCe.getDragboard().hasString()&&MCcontrols.containsKey(MCe.getDragboard().getString())&&!isEmpty()){MCe.acceptTransferModes(TransferMode.COPY);MCe.consume();}});
                 setOnDragDropped(MCe->{String MCkey=MCe.getDragboard().getString();boolean MCok=!isEmpty()&&MCcontrols.containsKey(MCkey);if(MCok)MCcontrols.get(MCkey).MCplace(new CellReference(getTableRow().getItem(),MCcolumn).formatAsString());MCe.setDropCompleted(MCok);MCe.consume();});}
                @Override protected void updateItem(String MCvalue,boolean MCempty){super.updateItem(MCvalue,MCempty);getStyleClass().remove("excel-cell-used");setText(MCempty?null:MCvalue);setTooltip(null);if(!MCempty&&getTableRow()!=null&&getTableRow().getItem()!=null){String MCref=new CellReference(getTableRow().getItem(),MCcolumn).formatAsString();List<String> MCused=MCusedCells.getOrDefault(MCref,List.of());if(!MCused.isEmpty()){getStyleClass().add("excel-cell-used");setTooltip(new Tooltip(MCref+" · "+String.join(", ",MCused)));}else setTooltip(new Tooltip(MCref+" · "+MCvalue));}}
            });MCtable.getColumns().add(MCcol);
        }
        MCtable.setItems(FXCollections.observableArrayList(java.util.stream.IntStream.range(0,Math.max(1,MCsheet.MCrows().size())).boxed().toList()));MCvalidate();
    }
    private void MCvalidate() {
        MCusedCells.clear();MCranges.forEach((MCkey,MCrange)->MCrange.MCcells().forEach(MCcell->MCusedCells.computeIfAbsent(MCcell,MCignored->new ArrayList<>()).add(MCkey)));
        List<String> MCmissing=MCExcelBatch.MCmissing(MCkeys(),MCranges);String MCmessage;
        if(!MCerrors.isEmpty())MCmessage=String.join(" · ",MCerrors.values());
        else if(!MCmissing.isEmpty())MCmessage="Falta colocar: "+String.join(", ",MCmissing);
        else try{int MCcount=MCExcelBatch.MCextract(MCsheet,MCkeys(),MCranges).size();MCmessage="100% asignadas · "+MCcount+" documentos listos para generar.";}
        catch(IllegalArgumentException MCe){MCmessage=MCe.getMessage();MCdialog.getDialogPane().lookupButton(MCgenerate).setDisable(true);MCvalidation.setText(MCmessage);MCtable.refresh();return;}
        MCdialog.getDialogPane().lookupButton(MCgenerate).setDisable(!MCmissing.isEmpty()||!MCerrors.isEmpty());MCvalidation.setText(MCmessage);MCtable.refresh();
    }
    private final class MCMappingControls {
        final String MCkey;
        final VBox MCcard=new VBox(7);
        final Label MCstate=new Label("Pendiente");
        final TextField MCfrom=new TextField(),MCto=new TextField();
        final ComboBox<String> MCorientation=new ComboBox<>(),MCdirection=new ComboBox<>();
        boolean MCchanging;
        MCMappingControls(MCVariable MCvariable) {
            MCkey=MCvariable.MCkey();Label MCtitle=new Label(MCvariable.MClabel()+" · "+MCkey);MCtitle.setWrapText(true);MCtitle.getStyleClass().add("excel-variable-drag");
            MCtitle.setTooltip(new Tooltip("Arrastre esta variable hacia una celda"));
            MCtitle.setOnDragDetected(MCe->{Dragboard MCdb=MCtitle.startDragAndDrop(TransferMode.COPY);ClipboardContent MCcontent=new ClipboardContent();MCcontent.putString(MCkey);MCdb.setContent(MCcontent);MCe.consume();});
            MCfrom.setPromptText("A2");MCto.setPromptText("A20");MCfrom.setPrefColumnCount(5);MCto.setPrefColumnCount(5);
            HBox MCreferences=new HBox(6,new VBox(4,new Label("Desde"),MCfrom),new VBox(4,new Label("Hasta"),MCto));
            MCorientation.getItems().setAll("Vertical","Horizontal");MCorientation.setValue("Vertical");MCdirections();
            MCorientation.setMaxWidth(Double.MAX_VALUE);MCdirection.setMaxWidth(Double.MAX_VALUE);
            Button MCremove=new Button("Quitar / reasignar");MCremove.setOnAction(MCe->{MCchanging=true;MCfrom.clear();MCto.clear();MCchanging=false;MCcommit();});
            MCcard.getChildren().addAll(MCtitle,MCstate,MCreferences,new Label("Orientación"),MCorientation,new Label("Orden de lectura"),MCdirection,MCremove);MCcard.getStyleClass().add("excel-mapping-card");
            MCfrom.textProperty().addListener((MCo,MCa,MCb)->MCcommit());MCto.textProperty().addListener((MCo,MCa,MCb)->MCcommit());
            MCorientation.setOnAction(MCe->{MCchanging=true;MCdirections();MCchanging=false;if(!MCfrom.getText().isBlank())MCplace(MCfrom.getText());else MCcommit();});
            MCdirection.setOnAction(MCe->MCcommit());
        }
        void MCdirections(){MCdirection.getItems().setAll("Vertical".equals(MCorientation.getValue())?List.of("De arriba hacia abajo","De abajo hacia arriba"):List.of("De izquierda a derecha","De derecha a izquierda"));MCdirection.getSelectionModel().selectFirst();}
        MCExcelBatch.MCOrientation MCorientationValue(){return "Vertical".equals(MCorientation.getValue())?MCExcelBatch.MCOrientation.VERTICAL:MCExcelBatch.MCOrientation.HORIZONTAL;}
        void MCplace(String MCref){try{MCExcelBatch.MCRange MCrange=MCExcelBatch.MCdetect(MCsheet,MCref,MCorientationValue(),false);MCchanging=true;MCfrom.setText(MCrange.MCfrom());MCto.setText(MCrange.MCto());MCchanging=false;MCcommit();}catch(IllegalArgumentException MCe){MCerrors.put(MCkey,MCkey+": "+MCe.getMessage());MCvalidate();}}
        void MCcommit(){if(MCchanging)return;MCranges.remove(MCkey);MCerrors.remove(MCkey);MCstate.setText("Pendiente");if(!MCfrom.getText().isBlank()||!MCto.getText().isBlank())try{MCExcelBatch.MCRange MCrange=new MCExcelBatch.MCRange(MCfrom.getText(),MCto.getText(),MCorientationValue(),MCdirection.getSelectionModel().getSelectedIndex()==1);MCrange.MCcells();MCranges.put(MCkey,MCrange);MCstate.setText("Asignada · "+MCrange.MCcells().size()+" celdas");}catch(IllegalArgumentException MCe){MCerrors.put(MCkey,MCkey+": "+MCe.getMessage());}MCvalidate();}
    }
}
