package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.List;

public class TransactionHistoryController {

    @FXML private TableView<TransactionModel> tableHistory;
    @FXML private TableColumn<TransactionModel, String> colId;
    @FXML private TableColumn<TransactionModel, String> colType;
    @FXML private TableColumn<TransactionModel, String> colAmount;
    @FXML private TableColumn<TransactionModel, String> colTime;
    @FXML private TableColumn<TransactionModel, String> colStatus;

    private User currentUser;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        colType.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        colAmount.setCellValueFactory(cellData -> cellData.getValue().amountProperty());
        colTime.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
        colStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());

        // Đăng ký lắng nghe kết quả từ Server qua Socket
        MessageRouter.getInstance().register(ResponseCode.TRANSACTION_HISTORY_RESULT, this::handleTransactionResult);
    }

    @FXML
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        // Gửi request lấy lịch sử giao dịch qua Socket
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_TRANSACTION_HISTORY, null);
    }

    private void handleTransactionResult(Message message) {
        if (message == null || !(message.getPayload() instanceof List)) return;

        @SuppressWarnings("unchecked")
        List<TransactionRequest> txList = (List<TransactionRequest>) message.getPayload();

        Platform.runLater(() -> {
            ObservableList<TransactionModel> dataList = FXCollections.observableArrayList();
            for (TransactionRequest tx : txList) {
                String typeText = switch (tx.getType().toUpperCase()) {
                    case "DEPOSIT", "DEPOSIT_REQUEST" -> "Nạp Tiền";
                    case "WITHDRAW", "WITHDRAW_REQUEST" -> "Rút Tiền";
                    default -> tx.getType().startsWith("BID_PLACED") ? "Đặt Giá" : tx.getType();
                };
                String statusText = switch (tx.getStatus().toUpperCase()) {
                    case "APPROVED", "SUCCESS" -> "✅ Đã duyệt";
                    case "REJECTED" -> "❌ Từ chối";
                    case "PENDING" -> "⏳ Đang xử lý";
                    default -> tx.getStatus();
                };
                dataList.add(new TransactionModel(
                        String.valueOf(tx.getRequestId()),
                        typeText,
                        String.format("%,.0f đ", tx.getAmount()),
                        tx.getRequestDate() != null ? tx.getRequestDate().toString().replace("T", " ").substring(0, Math.min(19, tx.getRequestDate().toString().length())) : "Vừa xong",
                        statusText
                ));
            }
            tableHistory.setItems(dataList);
        });
    }

    @FXML
    void handleBackToHome(ActionEvent event) {
        MessageRouter.getInstance().unregister(ResponseCode.TRANSACTION_HISTORY_RESULT);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/The_Home_Page_Bidder_View.fxml"));
            Parent root = loader.load();
            The_Home_Page_Bidder_View_Controller home = loader.getController();
            if (home != null) home.setUserData(this.currentUser);
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class TransactionModel {
        private final SimpleStringProperty id, type, amount, time, status;

        public TransactionModel(String id, String type, String amount, String time, String status) {
            this.id = new SimpleStringProperty(id);
            this.type = new SimpleStringProperty(type);
            this.amount = new SimpleStringProperty(amount);
            this.time = new SimpleStringProperty(time);
            this.status = new SimpleStringProperty(status);
        }
        public SimpleStringProperty idProperty() { return id; }
        public SimpleStringProperty typeProperty() { return type; }
        public SimpleStringProperty amountProperty() { return amount; }
        public SimpleStringProperty timeProperty() { return time; }
        public SimpleStringProperty statusProperty() { return status; }
    }
}
