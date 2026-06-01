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

    @FXML private Button btnToggleMenu;
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
        System.out.println("⏱ Khởi tạo màn hình Lịch sử đặt giá.");
        setupTable();
        setupSearch();
        // Đăng ký listener ngay khi initialize — sẽ được re-register khi setUserData() gọi
        registerListeners();
    }

    // ─── ĐĂNG KÝ / HỦY LISTENER ─────────────────────────────────────────────

    private void registerListeners() {
        // Unregister trước để tránh đăng ký trùng khi navigate lại trang này
        MessageRouter.getInstance().unregister(ResponseCode.BID_HISTORY_RESULT);
        MessageRouter.getInstance().register(ResponseCode.BID_HISTORY_RESULT, this::handleBidHistoryResult);
    }

    private void cleanupListeners() {
        MessageRouter.getInstance().unregister(ResponseCode.BID_HISTORY_RESULT);
    }

    // ─── SETUP TABLE & SEARCH ────────────────────────────────────────────────

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
                    setText(null); setStyle("");
                } else {
                    setText(String.format("%,.0f UETệ", amount));
                    boolean selected = getTableRow() != null && getTableRow().isSelected();
                    setStyle(selected ? "-fx-text-fill: white !important; -fx-font-weight: bold;"
                            : "-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                }
            }
        });

        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(empty || item == null ? null : item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }

                String upper = item.toUpperCase();
                String display;
                if (upper.contains("THẮNG") || upper.contains("WIN")) {
                    display = "THẮNG CUỘC 🏆";
                } else if (upper.contains("DẪN ĐẦU") || upper.contains("LEADING")) {
                    display = "ĐANG DẪN ĐẦU";
                } else if (upper.contains("THẤT BẠI") || upper.contains("LOSE") || upper.contains("ĐÈ GIÁ")) {
                    display = "THẤT BẠI";
                } else {
                    display = item;
                }
                setText(display);

                boolean selected = getTableRow() != null && getTableRow().isSelected();
                if (selected) {
                    setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                } else if ("THẮNG CUỘC 🏆".equals(display)) {
                    setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                } else if ("ĐANG DẪN ĐẦU".equals(display)) {
                    setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                } else if ("THẤT BẠI".equals(display)) {
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });

        if (historyTable != null) {
            historyTable.setItems(historyList);
            historyTable.setRowFactory(tv -> {
                TableRow<BidHistoryRow> row = new TableRow<>();
                row.selectedProperty().addListener((obs, o, n) -> row.requestLayout());
                return row;
            });
        }
    }

    private void setupSearch() {
        if (txtSearch == null) return;
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                historyTable.setItems(historyList);
                return;
            }
            List<BidHistoryRow> filtered = historyList.stream()
                    .filter(r -> r.getItemName() != null
                            && r.getItemName().toLowerCase().contains(newVal.toLowerCase()))
                    .collect(Collectors.toList());
            historyTable.setItems(FXCollections.observableArrayList(filtered));
        });
    }

    // ─── DATA LOADING ────────────────────────────────────────────────────────

    /**
     * Được MainContainerController gọi khi điều hướng sang trang Lịch sử.
     * Re-register listener để tránh trường hợp đã bị unregister khi rời trang trước đó.
     */
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        // Đảm bảo listener luôn được đăng ký trước khi gửi request
        registerListeners();

        // Server tự lấy userId từ session — không cần truyền payload
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, null);
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────────

    private void handleBidHistoryResult(Message message) {
        if (!(message.getPayload() instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<BidHistoryRow> rows = (List<BidHistoryRow>) message.getPayload();

        Platform.runLater(() -> {
            historyList.clear();
            if (rows != null && !rows.isEmpty()) {
                historyList.addAll(rows);
            }
            if (historyTable != null) {
                historyTable.setItems(historyList);
                historyTable.refresh();
            }
            System.out.println("✅ Lịch sử đặt giá: nhận " + (rows == null ? 0 : rows.size()) + " dòng.");
        });
    }

    // ─── FXML ACTIONS ────────────────────────────────────────────────────────

    @FXML
    void handleRefresh(ActionEvent event) {
        if (currentUser != null) {
            registerListeners();
            SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, null);
        }
    }

    @FXML
    void handleViewDetail(ActionEvent event) {
        if (historyTable == null) return;
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một dòng phiên đấu giá trong bảng để xem!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/AuctionDetailView.fxml"));
            Parent root = loader.load();

            AuctionDetailController detailCtrl = loader.getController();
            if (detailCtrl != null) {
                detailCtrl.loadAuctionDetail(selected.getAuctionId(), selected.getItemName(), currentUser);
            }

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Chi Tiết Phiên Đấu Giá #" + selected.getAuctionId());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner((Stage) ((Node) event.getSource()).getScene().getWindow());
            stage.setResizable(false);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showWarning("Không thể mở cửa sổ chi tiết: " + e.getMessage());
        }
    }

    @FXML
    void handleReportIssue(ActionEvent event) {
        if (historyTable == null) return;
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một lượt đặt giá từ bảng để báo cáo sự cố!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/ReportIssueView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Báo Cáo Sự Cố - Phiên #" + selected.getAuctionId());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void onLiveMenuClick(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) {
            this.mainContainer.setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
        } else {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/view/view/bidder/The_Home_Page_Bidder_View.fxml"));
                Parent root = loader.load();
                The_Home_Page_Bidder_View_Controller homeCtrl = loader.getController();
                if (homeCtrl != null) homeCtrl.setUserData(this.currentUser);
                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root, 1280, 720));
                stage.show();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo hệ thống");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}