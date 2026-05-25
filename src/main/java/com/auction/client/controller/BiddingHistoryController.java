package com.auction.client.controller;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.server.dao.BiddingHistoryDAO;
import com.auction.server.dao.UserDAO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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

    private ObservableList<BidHistoryRow> historyList = FXCollections.observableArrayList();
    private BiddingHistoryDAO historyDAO = new BiddingHistoryDAO();
    private UserDAO userDAO = new UserDAO();
    private User currentUser;

    // 🔥 Biến lưu trữ Scene trang chủ cũ để quay lại không bị load lại DB
    private Scene homeScene;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSearch();
    }

    // Hàm nhận Scene cũ từ trang chủ dội sang
    public void setMainHomeController(Scene homeScene) {
        this.homeScene = homeScene;
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
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));

        // 🌟 THÊM ĐOẠN NÀY: Biến số 1500000.0 thô thành chuỗi "1.500.000 UETệ" hiển thị cực đẹp mắt
        colBidAmount.setCellFactory(column -> new javafx.scene.control.TableCell<BidHistoryRow, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f UETệ", amount));
                }
            }
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
            } catch (Exception e) { e.printStackTrace(); }
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

    @FXML
    private void handleRefresh() {
        if (currentUser != null) {
            currentUser.setBalance(userDAO.getBalance(currentUser.getId()));
        }
        loadHistory();
    }

    /**
     * 🔥 HÀM QUAY LẠI THÔNG MINH GIỮ NGUYÊN PHÒNG
     */
    @FXML
    private void onLiveMenuClick() {
        try {
            if (homeScene != null) {
                System.out.println("🏛 Phục hồi nguyên vẹn Sàn Đấu Giá Live cũ...");
                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(homeScene); // Đặt lại Scene cũ là xong, giữ nguyên phòng đang chọn!
                stage.setTitle("Elite Auction - Sàn Đấu Giá");
                stage.show();
            } else {
                // Phương án dự phòng nếu không tìm thấy scene cũ
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
                Parent root = loader.load();
                The_Home_Page_Bidder_View_Controller homeController = loader.getController();
                if (homeController != null) homeController.setUserData(this.currentUser);

                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(new Scene(root, 1280, 720));
                stage.show();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleReportIssue() {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ReportIssueView.fxml"));
            Parent root = loader.load();
            ReportIssueController dialogController = loader.getController();
            if (dialogController != null) dialogController.setIssueData(selected, this.currentUser);

            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleViewDetail() {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionDetailView.fxml"));
            Parent root = loader.load();
            AuctionDetailController detailController = loader.getController();
            if (detailController != null) detailController.loadAuctionDetail(selected.getAuctionId(), selected.getItemName(), this.currentUser);

            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root));
            dialogStage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
}