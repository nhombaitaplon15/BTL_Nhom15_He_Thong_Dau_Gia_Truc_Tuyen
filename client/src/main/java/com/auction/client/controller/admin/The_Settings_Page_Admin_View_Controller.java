package com.auction.client.controller.admin; // [SỬA] client.controller.admin -> com.auction.client.controller.admin

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller Trang Cài Đặt / Quản Lý Người Dùng (Admin).
 *
 * CÁC LỖI ĐÃ SỬA SO VỚI FILE GỐC:
 * 1. [SỬA] Package: client.controller.admin -> com.auction.client.controller.admin
 * 2. [THÊM] Quản lý người dùng: tải danh sách user, ban/unban qua socket
 * 3. [THÊM] setUserData() - nhận User từ màn hình trước
 * 4. [SỬA] Đường dẫn FXML: /view/... -> /view/view/...
 *
 * ĐẶT TẠI: client/src/main/java/com/auction/client/controller/admin/The_Settings_Page_Admin_View_Controller.java
 */
public class The_Settings_Page_Admin_View_Controller implements Initializable {

    @FXML private Label lblAdminName;
    @FXML private Label lblStatusBar;

    // Bảng quản lý users
    @FXML private TableView<User> tblUsers;
    @FXML private TableColumn<User, Integer> colUserId;
    @FXML private TableColumn<User, String>  colUsername;
    @FXML private TableColumn<User, String>  colUserRole;
    @FXML private TableColumn<User, Double>  colUserBalance;
    @FXML private TableColumn<User, String>  colUserStatus;
    @FXML private TableColumn<User, Void>    colUserAction;

    private User currentUser;
    private final ObservableList<User> userList = FXCollections.observableArrayList();

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUserTable();
        registerRealtimeHandlers();
        loadAllUsers();
    }

    public void setUserData(User user) {
        this.currentUser = user;
        if (lblAdminName != null && user != null)
            lblAdminName.setText("Admin: " + user.getUsername());
    }

    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(ResponseCode.ADMIN_USERS_RESULT, this::onUsersReceived);
        MessageRouter.getInstance().register(ResponseCode.ADMIN_BAN_SUCCESS, msg -> {
            setStatus("✅ Đã ban User#" + msg.getPayload());
            loadAllUsers();
        });
        MessageRouter.getInstance().register(ResponseCode.ADMIN_UNBAN_SUCCESS, msg -> {
            setStatus("✅ Đã unban User#" + msg.getPayload());
            loadAllUsers();
        });
    }

    // =========================================================
    // DATA
    // =========================================================

    private void loadAllUsers() {
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_USERS, null);
        setStatus("⏳ Đang tải danh sách người dùng...");
    }

    @SuppressWarnings("unchecked")
    private void onUsersReceived(Message message) {
        List<User> list = (List<User>) message.getPayload();
        userList.clear();
        if (list != null) userList.addAll(list);
        setStatus("✅ Đã tải " + userList.size() + " tài khoản.");
    }

    // =========================================================
    // TABLE SETUP
    // =========================================================

    private void setupUserTable() {
        if (tblUsers == null) return;

        colUserId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUserRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colUserBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));
        colUserStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Format balance
        colUserBalance.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double balance, boolean empty) {
                super.updateItem(balance, empty);
                if (empty || balance == null) { setText(null); return; }
                setText(String.format("%,.0f đ", balance));
            }
        });

        // Color-code status
        colUserStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle("BANNED".equalsIgnoreCase(status)
                        ? "-fx-text-fill: #FF5B5C; -fx-font-weight: bold;"
                        : "-fx-text-fill: #05CD99; -fx-font-weight: bold;");
            }
        });

        // Nút Ban / Unban
        colUserAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnBan   = new Button("Ban");
            private final Button btnUnban = new Button("Unban");
            private final HBox   box      = new HBox(6);

            {
                btnBan.setStyle("-fx-background-color: #FF5B5C; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnUnban.setStyle("-fx-background-color: #05CD99; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

                btnBan.setOnAction(e -> {
                    User user = (User) getTableRow().getItem();
                    if (user == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Ban tài khoản: " + user.getUsername()
                                    + "?\nUser sẽ bị kick ra nếu đang online.",
                            ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_BAN_USER, user.getId());
                        }
                    });
                });

                btnUnban.setOnAction(e -> {
                    User user = (User) getTableRow().getItem();
                    if (user == null) return;
                    SocketClient.getInstance().sendRequest(
                            RequestCode.ADMIN_UNBAN_USER, user.getId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                User user = (User) getTableRow().getItem();
                // Không cho Admin ban chính mình
                if (currentUser != null && user.getId() == currentUser.getId()) {
                    setGraphic(null); return;
                }
                box.getChildren().clear();
                boolean isBanned = "BANNED".equalsIgnoreCase(user.getStatus());
                box.getChildren().add(isBanned ? btnUnban : btnBan);
                setGraphic(box);
            }
        });

        tblUsers.setItems(userList);
    }

    // =========================================================
    // FXML HANDLERS
    // =========================================================

    @FXML
    void handleRefreshUsers(ActionEvent event) {
        loadAllUsers();
    }

    @FXML public void goToHomePage(ActionEvent event) {
        switchPage(event, "/view/view/The_Home_Page_Admin_View.fxml");
    }
    @FXML public void goToAuctionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Auction_Page_Admin_View.fxml");
    }
    @FXML public void goToTransactionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Transaction_Page_Admin_View.fxml");
    }

    private void switchPage(ActionEvent event, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof The_Auction_Page_Admin_View_Controller)
                ((The_Auction_Page_Admin_View_Controller) ctrl).setUserData(currentUser);
            else if (ctrl instanceof The_Transaction_Page_Admin_View_Controller)
                ((The_Transaction_Page_Admin_View_Controller) ctrl).setUserData(currentUser);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setStatus(String msg) {
        if (lblStatusBar != null) lblStatusBar.setText(msg);
    }
}