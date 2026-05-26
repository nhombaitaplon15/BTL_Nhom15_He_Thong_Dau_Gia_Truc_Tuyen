package com.auction.client.controller;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.server.dao.BiddingHistoryDAO;
import com.auction.server.dao.PaymentDAO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class BiddingHistoryController implements Initializable {

    @FXML private TextField txtSearch;
    @FXML private TableView<BidHistoryRow> historyTable;
    @FXML private TableColumn<BidHistoryRow, Integer> colId;
    @FXML private TableColumn<BidHistoryRow, Integer> colAuctionId;
    @FXML private TableColumn<BidHistoryRow, String> colItemName;
    @FXML private TableColumn<BidHistoryRow, Double> colBidAmount;
    @FXML private TableColumn<BidHistoryRow, String> colBidTime;
    @FXML private TableColumn<BidHistoryRow, String> colStatus;
    @FXML private Button btnViewDetail;

    private final ObservableList<BidHistoryRow> historyList = FXCollections.observableArrayList();
    private final BiddingHistoryDAO historyDAO = new BiddingHistoryDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private User currentUser;

    // 🎯 SỬA LỖI: Lưu trực tiếp instance Controller thay vì gọi qua Scene để tránh lỗi Compile
    private The_Home_Page_Bidder_View_Controller homeControllerInstance;
    private Scene homeScene;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSearch();
    }

    /**
     * 🎯 SỬA LỖI: Nhận cả Scene chuyển cảnh và Instance Controller của màn hình chính
     */
    public void setMainHomeController(Scene homeScene, The_Home_Page_Bidder_View_Controller homeController) {
        this.homeScene = homeScene;
        this.homeControllerInstance = homeController;
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        loadHistory();
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colBidTime.setCellValueFactory(new PropertyValueFactory<>("bidTime"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colBidAmount.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%,.0f UETệ", amount));
                    TableRow<?> row = getTableRow();
                    if (row != null && row.isSelected()) {
                        setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                    }
                }
            }
        });

        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(empty || item == null ? null : item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String s = item.toUpperCase();
                    if (s.contains("SUCCESS") || s.contains("THẮNG")) setText("THẮNG CUỘC 🏆");
                    else if (s.contains("DẪN ĐẦU")) setText("ĐANG DẪN ĐẦU");
                    else if (s.contains("ĐÈ GIÁ")) setText("BỊ ĐÈ GIÁ ⚠️");
                    else if (s.contains("FAILED") || s.contains("THẤT BẠI")) setText("THẤT BẠI");
                    else setText(item);

                    TableRow<?> row = getTableRow();
                    if (row != null && row.isSelected()) {
                        setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                    } else {
                        if (s.contains("SUCCESS") || s.contains("THẮNG")) setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                        else if (s.contains("DẪN ĐẦU")) setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                        else if (s.contains("ĐÈ GIÁ")) setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
                        else if (s.contains("FAILED") || s.contains("THẤT BẠI")) setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                        else setStyle("");
                    }
                }
            }
        });

        historyTable.setRowFactory(tv -> {
            TableRow<BidHistoryRow> row = new TableRow<>();
            row.selectedProperty().addListener((obs, oldVal, newVal) -> row.requestLayout());
            return row;
        });
    }

    private void loadHistory() {
        if (currentUser == null) return;
        new Thread(() -> {
            try {
                List<BidHistoryRow> list = historyDAO.getHistoryByUser(currentUser.getId());
                Platform.runLater(() -> {
                    historyList.setAll(list);
                    historyTable.setItems(historyList);
                });
            } catch (Exception e) {
                System.err.println("❌ Lỗi kết nối Database khi tải bảng lịch sử đặt giá!");
                e.printStackTrace();
            }
        }).start();
    }

    private void setupSearch() {
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                historyTable.setItems(historyList);
                return;
            }
            List<BidHistoryRow> filtered = historyList.stream()
                    .filter(item -> item.getItemName() != null && item.getItemName().toLowerCase().contains(newValue.toLowerCase()))
                    .collect(Collectors.toList());
            historyTable.setItems(FXCollections.observableArrayList(filtered));
        });
    }

    /**
     * ⚡ LÀM MỚI DATABASE: Đồng bộ số dư và kích hoạt vẽ lại giao diện ví
     */
    @FXML
    private void handleRefresh() {
        if (currentUser != null) {
            new Thread(() -> {
                try {
                    double actualBalance = paymentDAO.getBalance(currentUser.getId());
                    Platform.runLater(() -> currentUser.setBalance(actualBalance));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
        loadHistory();

        // 🎯 SỬA LỖI: Gọi trực tiếp instance Controller không qua Scene.getController() nữa
        if (homeControllerInstance != null) {
            homeControllerInstance.updateWalletUI();
        }
    }

    @FXML
    private void onLiveMenuClick() {
        try {
            if (homeScene != null) {
                // 🎯 SỬA LỖI: Đồng bộ số tiền ví chính và ví tạm trước khi quay về
                if (homeControllerInstance != null) {
                    homeControllerInstance.updateWalletUI();
                }
                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(homeScene);
                stage.setTitle("Elite Auction - Sàn Đấu Giá");
                stage.show();
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
                Parent root = loader.load();
                The_Home_Page_Bidder_View_Controller homeController = loader.getController();

                if (homeController != null) {
                    homeController.setUserData(this.currentUser);
                    homeController.updateWalletUI();
                }
                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(new Scene(root, 1280, 720));
                stage.show();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleReportIssue() {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một lượt đặt giá từ bảng để báo cáo sự cố!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ReportIssueView.fxml"));
            Parent root = loader.load();
            ReportIssueController dialogController = loader.getController();
            if (dialogController != null) dialogController.setIssueData(selected, this.currentUser);

            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Báo Cáo Sự Cố Phiên Đấu Giá");
            dialogStage.showAndWait();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleViewDetail(javafx.event.ActionEvent event) {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một dòng phiên đấu giá trong bảng lịch sử để xem!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionDetailView.fxml"));
            Parent root = loader.load();
            AuctionDetailController detailController = loader.getController();
            if (detailController != null) {
                detailController.loadAuctionDetail(selected.getAuctionId(), selected.getItemName(), this.currentUser);
            }
            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root, 700, 500));
            dialogStage.setTitle("Thông Tin Chi Tiết Phiên Đấu Giá - #" + selected.getAuctionId());
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            Stage ownerStage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            dialogStage.initOwner(ownerStage);
            dialogStage.setResizable(false);
            dialogStage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo hệ thống");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}