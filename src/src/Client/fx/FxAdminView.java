package Client.fx;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

/** JavaFX admin shell with TCP account/catalog operations; passwords never enter tables. */
public final class FxAdminView extends BorderPane {
    private final FxScreenNavigator navigator;
    private final TableView<ObservableList<String>> accounts = table("ID", "Username", "Role", "Status");
    private final TableView<ObservableList<String>> fruits = table("ID", "Code", "Tên", "Nhóm", "Mô tả");
    private final Label status = new Label("Đang tải...");
    private final Map<String, String> fruitAssets = new HashMap<>();
    private final Map<String, String> fruitGroups = new HashMap<>();

    public FxAdminView(FxScreenNavigator navigator) {
        this.navigator = navigator; getStyleClass().add("fx-data-view");
        Label title = new Label("QUẢN TRỊ HỆ THỐNG"); title.getStyleClass().add("screen-title"); setTop(title); BorderPane.setAlignment(title, javafx.geometry.Pos.CENTER); BorderPane.setMargin(title, new Insets(20));
        TabPane tabs = new TabPane();
        VBox accountBox = new VBox(10, accounts, accountButtons()); accountBox.setPadding(new Insets(12));
        VBox fruitBox = new VBox(10, fruits, fruitButtons()); fruitBox.setPadding(new Insets(12));
        tabs.getTabs().addAll(new Tab("Tài khoản", accountBox), new Tab("Hoa quả", fruitBox)); tabs.getTabs().forEach(t -> t.setClosable(false)); setCenter(tabs);
        Button logout = new Button("Đăng xuất"); logout.getStyleClass().add("secondary-button"); logout.setOnAction(e -> { try { GameClientSession.instance().logout(); } catch (Exception ignored) {} navigator.showMain(); });
        setBottom(logout); BorderPane.setAlignment(logout, javafx.geometry.Pos.CENTER); BorderPane.setMargin(logout, new Insets(12));
        var css = getClass().getResource("/Client/fx/fx-client.css"); if (css != null) getStylesheets().add(css.toExternalForm());
        loadAccounts(); loadFruits();
    }
    private HBox accountButtons() { Button add=new Button("Thêm"); Button edit=new Button("Sửa"); Button delete=new Button("Xóa"); Button refresh=new Button("Làm mới"); style(add,edit,delete,refresh); add.setOnAction(e->createAccount()); edit.setOnAction(e->editAccount()); delete.setOnAction(e->deleteAccount()); refresh.setOnAction(e->loadAccounts()); return new HBox(10,add,edit,delete,refresh,status); }
    private HBox fruitButtons() { Button add=new Button("Thêm"); Button edit=new Button("Sửa"); Button delete=new Button("Xóa"); Button refresh=new Button("Làm mới"); style(add,edit,delete,refresh); add.setOnAction(e->createFruit()); edit.setOnAction(e->editFruit()); delete.setOnAction(e->deleteFruit()); refresh.setOnAction(e->loadFruits()); return new HBox(10,add,edit,delete,refresh); }
    private TableView<ObservableList<String>> table(String... headers) { TableView<ObservableList<String>> t=new TableView<>(); for(int i=0;i<headers.length;i++){ final int x=i; TableColumn<ObservableList<String>,String> c=new TableColumn<>(headers[i]); c.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(v.getValue().get(x))); c.setPrefWidth(150); t.getColumns().add(c);} return t; }
    private void loadAccounts(){ try{TcpQuery.collect(GameClientSession.instance().client(),ProtocolMessage.of("ADMIN_LIST_ACCOUNTS"),"ADMIN_ACCOUNT","ADMIN_ACCOUNTS_END").thenAccept(list->Platform.runLater(()->{accounts.getItems().clear(); for(var m:list) accounts.getItems().add(FXCollections.observableArrayList(m.fields().getOrDefault("accountId",""),m.fields().getOrDefault("username",""),m.fields().getOrDefault("role",""),m.fields().getOrDefault("status",""))); status.setText("Đã tải "+list.size()+" tài khoản");}));}catch(IOException e){status.setText(e.getMessage());} }
    private void loadFruits(){ try{TcpQuery.collect(GameClientSession.instance().client(),ProtocolMessage.of("GET_FRUIT_CATALOG"),"FRUIT_CATALOG_ITEM","FRUIT_CATALOG_END").thenAccept(list->Platform.runLater(()->{fruits.getItems().clear();fruitAssets.clear();fruitGroups.clear();for(var m:list){String id=m.fields().getOrDefault("fruitId","");fruitAssets.put(id,m.fields().getOrDefault("assetPath",""));fruitGroups.put(id,m.fields().getOrDefault("groupId",""));fruits.getItems().add(FXCollections.observableArrayList(id,m.fields().getOrDefault("fruitCode",""),m.fields().getOrDefault("fruitName",""),m.fields().getOrDefault("groupName",""),m.fields().getOrDefault("description","")));}}));}catch(IOException e){status.setText(e.getMessage());} }
    private void createAccount(){
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Tạo tài khoản"); dialog.setHeaderText("Mật khẩu không được hiển thị lại sau khi tạo");
        FxAssets.styleDialog(dialog.getDialogPane());
        TextField username = new TextField(); PasswordField password = new PasswordField(); ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("PLAYER", "ADMIN")); role.setValue("PLAYER");
        GridPane form = formGrid(); form.addRow(0, new Label("Username"), username); form.addRow(1, new Label("Password"), password); form.addRow(2, new Label("Role"), role); dialog.getDialogPane().setContent(form); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> { if (!username.getText().isBlank() && !password.getText().isBlank()) send(new ProtocolMessage("ADMIN_CREATE_ACCOUNT", Map.of("username", username.getText().trim(), "password", password.getText(), "role", role.getValue())), "ADMIN_ACCOUNT_CREATED", this::loadAccounts); });
    }
    private void createFruit(){ TextInputDialog d=new TextInputDialog(); d.setTitle("Tạo hoa quả"); d.setHeaderText("Nhập groupId|code|name|description|assetPath"); d.showAndWait().ifPresent(v->{String[] p=v.split("\\|",5);if(p.length==5)send(new ProtocolMessage("ADMIN_CREATE_FRUIT",Map.of("groupId",p[0],"fruitCode",p[1],"fruitName",p[2],"description",p[3],"assetPath",p[4])),"ADMIN_FRUIT_CREATED",this::loadFruits);}); }
    private void editAccount(){
        int r=accounts.getSelectionModel().getSelectedIndex(); if(r<0){status.setText("Chọn một tài khoản");return;} var row=accounts.getItems().get(r);
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Sửa tài khoản"); dialog.setHeaderText("Để trống mật khẩu nếu không muốn đổi");
        FxAssets.styleDialog(dialog.getDialogPane());
        TextField username = new TextField(row.get(1)); ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("PLAYER", "ADMIN")); role.setValue(row.get(2)); ComboBox<String> state = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "DISABLED", "LOCKED")); state.setValue(row.get(3)); PasswordField password = new PasswordField();
        GridPane form = formGrid(); form.addRow(0, new Label("Username"), username); form.addRow(1, new Label("Role"), role); form.addRow(2, new Label("Status"), state); form.addRow(3, new Label("Mật khẩu mới"), password); dialog.getDialogPane().setContent(form); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> { java.util.LinkedHashMap<String,String> f=new java.util.LinkedHashMap<>(); f.put("accountId",row.get(0)); f.put("username",username.getText().trim()); f.put("role",role.getValue()); f.put("status",state.getValue()); if(!password.getText().isBlank()) f.put("password",password.getText()); send(new ProtocolMessage("ADMIN_UPDATE_ACCOUNT",f),"ADMIN_ACCOUNT_UPDATED",this::loadAccounts); });
    }
    private void deleteAccount(){ int r=accounts.getSelectionModel().getSelectedIndex(); if(r<0){status.setText("Chọn một tài khoản");return;} send(new ProtocolMessage("ADMIN_DELETE_ACCOUNT",Map.of("accountId",accounts.getItems().get(r).get(0))),"ADMIN_ACCOUNT_DELETED",this::loadAccounts); }
    private void editFruit(){ int r=fruits.getSelectionModel().getSelectedIndex(); if(r<0){status.setText("Chọn một hoa quả");return;} var row=fruits.getItems().get(r); TextInputDialog d=new TextInputDialog(row.get(2)+"|"+row.get(4)); d.setTitle("Sửa hoa quả"); d.setHeaderText("Nhập tên|mô tả"); d.showAndWait().ifPresent(v->{String[] p=v.split("\\|",2);if(p.length==2)send(new ProtocolMessage("ADMIN_UPDATE_FRUIT",Map.of("fruitId",row.get(0),"fruitName",p[0],"description",p[1],"groupId",fruitGroups.getOrDefault(row.get(0),"1"),"fruitCode",row.get(1),"assetPath",fruitAssets.getOrDefault(row.get(0),""))),"ADMIN_FRUIT_UPDATED",this::loadFruits);}); }
    private void deleteFruit(){ int r=fruits.getSelectionModel().getSelectedIndex(); if(r<0){status.setText("Chọn một hoa quả");return;} send(new ProtocolMessage("ADMIN_DELETE_FRUIT",Map.of("fruitId",fruits.getItems().get(r).get(0))),"ADMIN_FRUIT_DELETED",this::loadFruits); }
    private void send(ProtocolMessage req,String response,Runnable refresh){try{TcpQuery.single(GameClientSession.instance().client(),req,response).thenRun(()->Platform.runLater(refresh)).exceptionally(e->{Platform.runLater(()->status.setText(e.getMessage()));return null;});}catch(IOException e){status.setText(e.getMessage());}}
    private GridPane formGrid(){ GridPane grid=new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10)); return grid; }
    private void style(Button... buttons){ for(Button button:buttons) button.getStyleClass().add("secondary-button"); }
}
