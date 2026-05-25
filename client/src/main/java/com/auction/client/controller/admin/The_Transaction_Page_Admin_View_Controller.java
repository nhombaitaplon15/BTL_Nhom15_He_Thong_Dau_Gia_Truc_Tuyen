package com.auction.client.controller.admin; // [SỬA] client.controller.admin -> com.auction.client.controller.admin

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.TransactionRequest;
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
 * Controller Quản Lý Giao Dịch (Admin).
 *
 * CÁC LỖI ĐÃ SỬA SO VỚI FILE GỐC:
 * 1. [SỬA] Package: client.controller.admin -> com.auction.client.controller.admin
 * 2. [THÊM] Tải danh sách giao dịch qua socket (ADMIN_GET_ALL_TRANSACTIONS)
 * 3. [THÊM] Duyệt / Từ chối giao dịch (nạp/rút tiền) qua socket
 * 4. [THÊM] setUserData() để nhận User từ màn hình trước
 * 5. [SỬA] Đường dẫn FXML: /view/... -> /view/view/...
 *
 * ĐẶT TẠI: client/src/main/java/com/auction/client/controller/admin/The_Transaction_Page_Admin_View_Controller.java
 */
public class The_Transaction_Page_Admin_View_Controller implements Initializable {

    @FXML private TableView<TransactionRequest> tblTransactions;
    @FXML private TableColumn<TransactionRequest, Integer> colTxId;
    @FXML private TableColumn<TransactionRequest, Integer> colUserId;
    @FXML private TableColumn<TransactionRequest, Double>  colAmount;
    @FXML private TableColumn<TransactionRequest, String>  colType;
    @FXML private TableColumn<TransactionRequest, String>  colTxStatus;
    @FXML private TableColumn<TransactionRequest, Void>    colTxAction;
    @FXML private Label lblStatusBar;

    private User currentUser;
    private final ObservableList<TransactionRequest> txList = FXCollections.observableArrayList();

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        registerRealtimeHandlers();
        loadTransactions();
    }

    public void setUserData(User user) {
        this.currentUser = user;
    }

    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT, this::onTransactionsReceived);
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_TRANSACTION_APPROVED, msg -> {
                    setStatus("✅ Đã duyệt giao dịch #" + msg.getPayload());
                    loadTransactions();
                });
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_TRANSACTION_REJECTED, msg -> {
                    setStatus("✅ Đã từ chối giao dịch #" + msg.getPayload());
                    loadTransactions();
                });
    }

    // =========================================================
    // DATA
    // =========================================================

    private void loadTransactions() {
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_TRANSACTIONS, null);
        setStatus("⏳ Đang tải giao dịch...");
    }

    @SuppressWarnings("unchecked")
    private void onTransactionsReceived(Message message) {
        List<TransactionRequest> list = (List<TransactionRequest>) message.getPayload();
        txList.clear();
        if (list != null) txList.addAll(list);
        setStatus("✅ Đã tải " + txList.size() + " giao dịch.");
    }

    // =========================================================
    // TABLE SETUP
    // =========================================================

    private void setupTable() {
        colTxId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUserId.setCellValueFactory(new PropertyValueFactory<>("userId"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colTxStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Color-code trạng thái
        colTxStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                switch (status) {
                    case "PENDING"  -> setStyle("-fx-text-fill: #FF8800; -fx-font-weight: bold;");
                    case "APPROVED" -> setStyle("-fx-text-fill: #05CD99; -fx-font-weight: bold;");
                    case "REJECTED" -> setStyle("-fx-text-fill: #FF5B5C; -fx-font-weight: bold;");
                    default         -> setStyle("");
                }
            }
        });

        // Format amount
        colAmount.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) { setText(null); return; }
                setText(String.format("%,.0f đ", amount));
                setStyle("-fx-text-fill: #05CD99; -fx-font-weight: bold;");
            }
        });

        // Cột hành động: Duyệt / Từ chối (chỉ hiện khi PENDING)
        colTxAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnApprove = new Button("Duyệt");
            private final Button btnReject  = new Button("Từ chối");
            private final HBox   box        = new HBox(6, btnApprove, btnReject);

            {
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                btnApprove.setStyle("-fx-background-color: #05CD99; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnReject.setStyle("-fx-background-color: #FF5B5C; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

                btnApprove.setOnAction(e -> {
                    TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                    if (tx == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Duyệt giao dịch #" + tx.getId()
                                    + "\nLoại: " + tx.getType()
                                    + "\nSố tiền: " + String.format("%,.0f đ", tx.getAmount()),
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Xác Nhận Duyệt");
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_APPROVE_TRANSACTION, tx.getId());
                        }
                    });
                });

                btnReject.setOnAction(e -> {
                    TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                    if (tx == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Từ chối giao dịch #" + tx.getId() + "?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_REJECT_TRANSACTION, tx.getId());
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                setGraphic("PENDING".equals(tx.getStatus()) ? box : null);
            }
        });

        tblTransactions.setItems(txList);
    }

    // =========================================================
    // FXML HANDLERS
    // =========================================================

    @FXML
    void handleRefresh(ActionEvent event) {
        loadTransactions();
    }

    @FXML public void goToHomePage(ActionEvent event) {
        switchPage(event, "/view/view/The_Home_Page_Admin_View.fxml");
    }
    @FXML public void goToAuctionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Auction_Page_Admin_View.fxml");
    }
    @FXML public void goToSettingsPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Settings_Page_Admin_View.fxml");
    }

    private void switchPage(ActionEvent event, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof The_Auction_Page_Admin_View_Controller)
                ((The_Auction_Page_Admin_View_Controller) ctrl).setUserData(currentUser);
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