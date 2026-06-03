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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TransactionHistoryController {

    private User currentUser;

    @FXML private TableView<TransactionModel> tableHistory;
    @FXML private TableColumn<TransactionModel, String> colId;
    @FXML private TableColumn<TransactionModel, String> colType;
    @FXML private TableColumn<TransactionModel, String> colAmount;
    @FXML private TableColumn<TransactionModel, String> colTime;
    @FXML private TableColumn<TransactionModel, String> colStatus;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Consumer<Message> onTransactionsResult = this::handleTransactionsResult;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        colType.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        colAmount.setCellValueFactory(cellData -> cellData.getValue().amountProperty());
        colTime.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
        colStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());

        MessageRouter.getInstance().register(ResponseCode.TRANSACTIONS_RESULT, onTransactionsResult);
    }

    @FXML
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        loadRealTransactionData();
    }

    private void loadRealTransactionData() {
        // Đẩy tác vụ lấy lịch sử giao dịch sang luồng nền
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.GET_USER_TRANSACTIONS, currentUser.getId());
        });
    }

    @SuppressWarnings("unchecked")
    private void handleTransactionsResult(Message msg) {
        Object payload = msg.getPayload();
        if (payload instanceof List) {
            List<TransactionRequest> list = (List<TransactionRequest>) payload;

            Platform.runLater(() -> {
                ObservableList<TransactionModel> dataList = FXCollections.observableArrayList();
                for (TransactionRequest t : list) {
                    dataList.add(new TransactionModel(
                        String.valueOf(t.getRequestId()),
                        formatTxType(t.getType()),
                        String.format("%,.0f đ", t.getAmount()),
                        t.getRequestDate() != null ? t.getRequestDate().format(DT_FMT) : "Vừa xong",
                        formatStatus(t.getTransactionStatus())
                    ));
                }
                tableHistory.setItems(dataList);
            });
        }
    }

    private String formatTxType(String type) {
        if (type == null) return "Giao dịch";
        if (type.startsWith("DEPOSIT")) return "Nạp tiền";
        if (type.startsWith("WITHDRAW")) return "Rút tiền";
        if (type.startsWith("HOLD_AUCTION_")) return "Đặt cọc phiên";
        if (type.startsWith("RELEASE_AUCTION_")) return "Nhận tiền phiên";
        if (type.startsWith("REFUND_AUCTION_")) return "Hoàn tiền phiên";
        if (type.startsWith("BID_AUCTION_")) return "Đặt giá phiên";
        if (type.startsWith("PROFIT_AUCTION_")) return "Phí hoa hồng";
        return type;
    }

    private String formatStatus(String s) {
        if (s == null) return "";
        return switch (s) {
            case "SUCCESS" -> "Thành công";
            case "PENDING" -> "Chờ duyệt";
            case "APPROVED" -> "Đã duyệt";
            case "REJECTED" -> "Từ chối";
            default -> s;
        };
    }

    @FXML
    void handleBackToHome(ActionEvent event) {
        try {
            MessageRouter.getInstance().unregister(ResponseCode.TRANSACTIONS_RESULT);

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
        private final SimpleStringProperty id;
        private final SimpleStringProperty type;
        private final SimpleStringProperty amount;
        private final SimpleStringProperty time;
        private final SimpleStringProperty status;

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