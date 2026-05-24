package client.controller.bidder;

import com.auction.common.model.User;
import com.auction.server.dao.TransactionDAO; // Dựa trên import từ TransactionService của em
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

public class TransactionHistoryController {

    // Gọi trực tiếp DAO giao dịch đang dùng trong TransactionService của em
    private final TransactionDAO transDAO = new TransactionDAO();
    private User currentUser;

    @FXML private TableView<TransactionModel> tableHistory;
    @FXML private TableColumn<TransactionModel, String> colId;
    @FXML private TableColumn<TransactionModel, String> colType;
    @FXML private TableColumn<TransactionModel, String> colAmount;
    @FXML private TableColumn<TransactionModel, String> colTime;
    @FXML private TableColumn<TransactionModel, String> colStatus;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        colType.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        colAmount.setCellValueFactory(cellData -> cellData.getValue().amountProperty());
        colTime.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
        colStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
    }

    @FXML
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        loadRealTransactionData();
    }

    private void loadRealTransactionData() {
        try {
            ObservableList<TransactionModel> dataList = FXCollections.observableArrayList();

            // LƯU Ý: Nếu transDAO của em có hàm lấy danh sách (Ví dụ: getTransactionsByUserId), hãy mở comment này ra:
            /*
            var list = transDAO.getTransactionsByUserId(currentUser.getId());
            if (list != null) {
                for (var t : list) {
                    dataList.add(new TransactionModel(
                        String.valueOf(t.getId()),
                        t.getType().equalsIgnoreCase("DEPOSIT") ? "Nạp Tiền" : "Rút Tiền",
                        String.format("%,.0f đ", t.getAmount()),
                        t.getCreatedAt() != null ? t.getCreatedAt().toString() : "Vừa xong",
                        t.getStatus()
                    ));
                }
            }
            */

            tableHistory.setItems(dataList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleBackToHome(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
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