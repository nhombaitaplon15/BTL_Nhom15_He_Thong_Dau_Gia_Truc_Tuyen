package com.auction.client.controller.bidder;

import com.auction.client.controller.bidder.MainContainerController;
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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;
import java.util.stream.Collectors;

public class BiddingHistoryController {

    // ─── ĐÃ ĐỒNG BỘ: Chỉ giữ lại các ID thực sự tồn tại trên file FXML mới ───
    @FXML private Button btnToggleMenu; // Đóng vai trò là nút "← Quay lại" trên thanh tiêu đề
    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh;

    @FXML private TableView<BidHistoryRow> historyTable;
    @FXML private TableColumn<BidHistoryRow, Integer> colId;
    @FXML private TableColumn<BidHistoryRow, Integer> colAuctionId;
    @FXML private TableColumn<BidHistoryRow, String> colItemName;
    @FXML private TableColumn<BidHistoryRow, Double> colBidAmount;
    @FXML private TableColumn<BidHistoryRow, String> colBidTime;
    @FXML private TableColumn<BidHistoryRow, String> colStatus;

    private User currentUser;
    private MainContainerController mainContainer;
    private final ObservableList<BidHistoryRow> historyList = FXCollections.observableArrayList();

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        System.out.println("⏱ Khởi tạo màn hình Lịch sử đặt giá phẳng (Khớp 100% với FXML).");
        setupTable();
        setupSearch();
        MessageRouter.getInstance().register(ResponseCode.BID_HISTORY_RESULT, this::handleBidHistoryResult);
    }

    private void setupTable() {
        if (colId != null) colId.setCellValueFactory(new PropertyValueFactory<>("id"));
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
                    return;
                }

                String currentText = item.toUpperCase();
                if (currentText.contains("THẮNG CUỘC") || currentText.contains("WIN")) {
                    setText("THẮNG CUỘC 🏆");
                } else if (currentText.contains("DẪN ĐẦU") || currentText.contains("LEADING")) {
                    setText("ĐANG DẪN ĐẦU");
                } else if (currentText.contains("THẤT BẠI") || currentText.contains("LOSE") || currentText.contains("ĐÈ GIÁ")) {
                    setText("THẤT BẠI");
                } else {
                    setText(item);
                }

                TableRow<?> row = getTableRow();
                if (row != null && row.isSelected()) {
                    setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                } else {
                    String text = getText();
                    if ("THẮNG CUỘC 🏆".equals(text)) {
                        setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                    } else if ("ĐANG DẪN ĐẦU".equals(text)) {
                        setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    } else if ("THẤT BẠI".equals(text)) {
                        setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        if (historyTable != null) {
            historyTable.setItems(historyList);
            historyTable.setRowFactory(tv -> {
                TableRow<BidHistoryRow> row = new TableRow<>();
                row.selectedProperty().addListener((obs, oldVal, newVal) -> row.requestLayout());
                return row;
            });
        }
    }

    private void setupSearch() {
        if (txtSearch == null) return;
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

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, user.getId());
    }

    private void handleBidHistoryResult(Message message) {
        if (!(message.getPayload() instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<BidHistoryRow> historyRows = (List<BidHistoryRow>) message.getPayload();

        Platform.runLater(() -> {
            historyList.clear();
            if (historyRows != null) {
                historyList.addAll(historyRows);
            }
            if (historyTable != null) {
                historyTable.setItems(historyList);
            }
        });
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        if (currentUser != null) {
            SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, currentUser.getId());
        }
    }

    @FXML
    void handleViewDetail(ActionEvent event) {
        if (historyTable == null) return;
        BidHistoryRow selectedRow = historyTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            showWarning("Vui lòng chọn một dòng phiên đấu giá trong bảng lịch sử để xem!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/AuctionDetailView.fxml"));
            Parent root = loader.load();

            com.auction.client.controller.bidder.AuctionDetailController detailController = loader.getController();
            if (detailController != null) {
                detailController.loadAuctionDetail(selectedRow.getAuctionId(), selectedRow.getItemName(), currentUser);
            }

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Chi Tiết Phiên Đấu Giá #" + selectedRow.getAuctionId());
            stage.initModality(Modality.APPLICATION_MODAL);

            Stage ownerStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.initOwner(ownerStage);
            stage.setResizable(false);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleReportIssue(ActionEvent event) {
        if (historyTable == null) return;
        BidHistoryRow selectedRow = historyTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            showWarning("Vui lòng chọn một lượt đặt giá từ bảng để báo cáo sự cố!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/ReportIssueView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Báo Cáo Sự Cố - Phiên #" + selectedRow.getAuctionId());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * NÚT ⟵ QUAY LẠI: Đồng nhất hành động quay lại trang chủ phiên đấu giá trực tuyến
     */
    @FXML
    void onLiveMenuClick(ActionEvent event) {
        cleanupListeners();
        // Nếu không có mainContainer, chuyển scene trực tiếp
        if (this.mainContainer != null) {
            this.mainContainer.setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
        } else {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/view/view/bidder/The_Home_Page_Bidder_View.fxml"));
                Parent root = loader.load();
                The_Home_Page_Bidder_View_Controller homeCtrl = loader.getController();
                if (homeCtrl != null) homeCtrl.setUserData(this.currentUser);
                Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root, 1280, 720));
                stage.show();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }


    private void cleanupListeners() {
        MessageRouter.getInstance().unregister(ResponseCode.BID_HISTORY_RESULT);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo hệ thống");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}