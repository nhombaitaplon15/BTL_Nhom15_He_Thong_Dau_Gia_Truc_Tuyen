package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
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

    private User currentUser;
    private The_Home_Page_Bidder_View_Controller homeControllerInstance;
    private Scene homeScene;

    // Các Consumer lắng nghe dữ liệu trả về từ Server Realtime
    private final Consumer<Message> onHistoryResult = this::handleHistoryResult;
    private final Consumer<Message> onProfileResult = this::handleProfileResult;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSearch();

        // Đăng ký nhận kết quả từ Server qua Socket
        MessageRouter.getInstance().register(ResponseCode.BID_HISTORY_RESULT, onHistoryResult);
        MessageRouter.getInstance().register(ResponseCode.PROFILE_RESULT, onProfileResult);
    }

    private void cleanupHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.BID_HISTORY_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.PROFILE_RESULT);
    }

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

                // GỌI CSS: Xóa class cũ trước khi gán mới (JavaFX hay dùng lại Cell cũ khi cuộn)
                getStyleClass().removeAll("amount-text");

                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f UETệ", amount));
                    getStyleClass().add("amount-text");
                }
            }
        });

        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(empty || item == null ? null : item, empty);

                // GỌI CSS: Dọn dẹp sạch sẽ các màu cũ
                getStyleClass().removeAll("status-win", "status-lead", "status-fail", "status-default");

                if (empty || item == null) {
                    setText(null);
                    return;
                }

                String displayStatus = item.toUpperCase();

                if (displayStatus.contains("SUCCESS") || displayStatus.contains("WIN") || displayStatus.contains("THẮNG")) {
                    displayStatus = "THẮNG CUỘC 🏆";
                    getStyleClass().add("status-win");
                } else if (displayStatus.contains("RUNNING") || displayStatus.contains("LEAD") || displayStatus.contains("DẪN ĐẦU")) {
                    displayStatus = "ĐANG DẪN ĐẦU";
                    getStyleClass().add("status-lead");
                } else if (displayStatus.contains("FAIL") || displayStatus.contains("LOSE") || displayStatus.contains("OVERBID") || displayStatus.contains("THẤT BẠI")) {
                    displayStatus = "THẤT BẠI";
                    getStyleClass().add("status-fail");
                } else {
                    getStyleClass().add("status-default");
                }
                setText(displayStatus);
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
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, currentUser.getId());
    }

    private void handleHistoryResult(Message msg) {
        Object payload = msg.getPayload();

        // 🛡️ LỚP KHIÊN BẢO VỆ CHỐNG CRASH CLASSCASTEXCEPTION
        if (payload instanceof List<?> rawList) {
            List<BidHistoryRow> safeList = new ArrayList<>();
            for (Object obj : rawList) {
                if (obj instanceof BidHistoryRow) {
                    safeList.add((BidHistoryRow) obj);
                } else {
                    System.err.println("❌ LỖI NGHIÊM TRỌNG: Server trả về sai kiểu dữ liệu (" + obj.getClass().getSimpleName() + " thay vì BidHistoryRow). Vui lòng báo Backend sửa DAO!");
                }
            }

            Platform.runLater(() -> {
                historyList.setAll(safeList);
                historyTable.setItems(historyList);
            });
        }
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
            SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, currentUser.getId());
        }
        loadHistory();
    }

    private void handleProfileResult(Message msg) {
        Object payload = msg.getPayload();
        if (payload instanceof User) {
            User updatedUser = (User) payload;
            Platform.runLater(() -> {
                this.currentUser.setBalance(updatedUser.getBalance());
                if (homeControllerInstance != null) {
                    homeControllerInstance.updateWalletUI();
                }
            });
        }
    }

    @FXML
    private void onLiveMenuClick() {
        cleanupHandlers();
        try {
            if (homeScene != null) {
                if (homeControllerInstance != null) {
                    homeControllerInstance.updateWalletUI();
                }
                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(homeScene);
                stage.setTitle("Elite Auction - Sàn Đấu Giá");
                stage.show();
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/The_Home_Page_Bidder_View.fxml"));
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/ReportIssueView.fxml"));
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/AuctionDetailView.fxml"));
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