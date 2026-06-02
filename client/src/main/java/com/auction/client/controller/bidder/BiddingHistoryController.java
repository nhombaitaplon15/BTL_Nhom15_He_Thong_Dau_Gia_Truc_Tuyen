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

/**
 * BiddingHistoryController — ĐÃ BỔ SUNG:
 *
 * 1. Lắng nghe WINNER_NOTIFICATION (realtime push từ server):
 *    Khi phiên đấu giá kết thúc và user là người thắng, server gửi WINNER_NOTIFICATION
 *    tới cá nhân người thắng. Controller này sẽ:
 *    - Tự động cập nhật trạng thái dòng tương ứng trong bảng lịch sử thành
 *      "WINNER - Chờ xác nhận" (dùng BidHistoryRow.setStatus() mới thêm).
 *    - Refresh bảng để hiển thị ngay.
 *
 * 2. Lắng nghe AUCTION_ENDED (broadcast):
 *    Cập nhật trạng thái dòng nếu đang xem màn hình lịch sử khi phiên kết thúc.
 *
 * 3. Column colStatus — thêm nhận dạng trạng thái "WINNER - Chờ xác nhận"
 *    để hiển thị màu vàng/cam phân biệt.
 */
public class BiddingHistoryController {

    @FXML private Button btnToggleMenu;
    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh;

    @FXML private TableView<BidHistoryRow> historyTable;
    @FXML private TableColumn<BidHistoryRow, Integer> colId;
    @FXML private TableColumn<BidHistoryRow, Integer> colAuctionId;
    @FXML private TableColumn<BidHistoryRow, String>  colItemName;
    @FXML private TableColumn<BidHistoryRow, Double>  colBidAmount;
    @FXML private TableColumn<BidHistoryRow, String>  colBidTime;
    @FXML private TableColumn<BidHistoryRow, String>  colStatus;

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
        registerListeners();
    }

    // ─── ĐĂNG KÝ / HỦY LISTENER ─────────────────────────────────────────────

    private void registerListeners() {
        MessageRouter router = MessageRouter.getInstance();
        router.unregister(ResponseCode.BID_HISTORY_RESULT);
        router.unregister(ResponseCode.WINNER_NOTIFICATION);
        router.unregister(ResponseCode.AUCTION_ENDED);

        router.register(ResponseCode.BID_HISTORY_RESULT,  this::handleBidHistoryResult);
        // ✅ MỚI: lắng nghe push cá nhân khi phiên kết thúc và user là winner
        router.register(ResponseCode.WINNER_NOTIFICATION, this::handleWinnerNotification);
        // ✅ MỚI: lắng nghe broadcast khi phiên kết thúc để cập nhật dòng thắng
        router.register(ResponseCode.AUCTION_ENDED,       this::handleAuctionEndedBroadcast);
    }

    private void cleanupListeners() {
        MessageRouter router = MessageRouter.getInstance();
        router.unregister(ResponseCode.BID_HISTORY_RESULT);
        router.unregister(ResponseCode.WINNER_NOTIFICATION);
        router.unregister(ResponseCode.AUCTION_ENDED);
    }

    // ─── SETUP TABLE & SEARCH ────────────────────────────────────────────────

    private void setupTable() {
        if (colId != null) colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName .setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colBidTime  .setCellValueFactory(new PropertyValueFactory<>("bidTime"));
        colStatus   .setCellValueFactory(new PropertyValueFactory<>("status"));

        colBidAmount.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) { setText(null); setStyle(""); return; }
                setText(String.format("%,.0f UETệ", amount));
                boolean selected = getTableRow() != null && getTableRow().isSelected();
                setStyle(selected
                        ? "-fx-text-fill: white !important; -fx-font-weight: bold;"
                        : "-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
            }
        });

        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(empty || item == null ? null : item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }

                String upper = item.toUpperCase();
                String display;
                String style;

                boolean selected = getTableRow() != null && getTableRow().isSelected();

                // ✅ MỚI: nhận dạng trạng thái WINNER chờ xác nhận
                if (upper.contains("WINNER") || upper.contains("CHỜ XÁC NHẬN")) {
                    display = "🏆 WINNER - Chờ xác nhận";
                    style = selected
                            ? "-fx-text-fill: white !important; -fx-font-weight: bold;"
                            : "-fx-text-fill: #d97706; -fx-font-weight: bold;"; // Màu vàng/cam
                } else if (upper.contains("THẮNG") || upper.contains("WIN")) {
                    display = "THẮNG CUỘC 🏆";
                    style = selected
                            ? "-fx-text-fill: white !important; -fx-font-weight: bold;"
                            : "-fx-text-fill: #16a34a; -fx-font-weight: bold;";
                } else if (upper.contains("DẪN ĐẦU") || upper.contains("LEADING")) {
                    display = "ĐANG DẪN ĐẦU";
                    style = selected
                            ? "-fx-text-fill: white !important; -fx-font-weight: bold;"
                            : "-fx-text-fill: #059669; -fx-font-weight: bold;";
                } else if (upper.contains("THẤT BẠI") || upper.contains("LOSE") || upper.contains("ĐÈ GIÁ")) {
                    display = "THẤT BẠI";
                    style = selected
                            ? "-fx-text-fill: white !important; -fx-font-weight: bold;"
                            : "-fx-text-fill: #dc2626; -fx-font-weight: bold;";
                } else {
                    display = item;
                    style = selected ? "-fx-text-fill: white !important; -fx-font-weight: bold;" : "";
                }

                setText(display);
                setStyle(style);
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

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        registerListeners();
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, null);
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────────

    private void handleBidHistoryResult(Message message) {
        if (!(message.getPayload() instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<BidHistoryRow> rows = (List<BidHistoryRow>) message.getPayload();

        Platform.runLater(() -> {
            historyList.clear();
            if (rows != null && !rows.isEmpty()) historyList.addAll(rows);
            if (historyTable != null) {
                historyTable.setItems(historyList);
                historyTable.refresh();
            }
            System.out.println("✅ Lịch sử đặt giá: nhận " + (rows == null ? 0 : rows.size()) + " dòng.");
        });
    }

    /**
     * ✅ MỚI: Server push WINNER_NOTIFICATION cá nhân tới người thắng.
     * Cập nhật trạng thái dòng thắng trong bảng thành "WINNER - Chờ xác nhận".
     * Payload: Object[] {Integer auctionId, Double finalPrice, String itemName}
     */
    private void handleWinnerNotification(Message message) {
        Platform.runLater(() -> {
            try {
                Object[] payload = (Object[]) message.getPayload();
                int auctionId = (Integer) payload[0];

                // Tìm dòng có auctionId trùng với giá cao nhất (dòng thắng)
                historyList.stream()
                        .filter(r -> r.getAuctionId() == auctionId)
                        .max((a, b) -> Double.compare(a.getBidAmount(), b.getBidAmount()))
                        .ifPresent(row -> row.setStatus("WINNER - Chờ xác nhận"));

                if (historyTable != null) historyTable.refresh();
                System.out.println("🏆 Đã cập nhật trạng thái WINNER cho phiên #" + auctionId);
            } catch (Exception e) {
                System.err.println("[BID_HISTORY] Lỗi handleWinnerNotification: " + e.getMessage());
            }
        });
    }

    /**
     * ✅ MỚI: Lắng nghe broadcast AUCTION_ENDED (người đang ở màn hình lịch sử).
     * Nếu user là winner của phiên kết thúc → cập nhật trạng thái dòng thắng.
     * Payload: Object[] {auctionId, winnerUsername, finalPrice}
     */
    private void handleAuctionEndedBroadcast(Message message) {
        Platform.runLater(() -> {
            try {
                Object payload = message.getPayload();
                if (!(payload instanceof Object[])) return;
                Object[] arr = (Object[]) payload;

                if (arr.length < 2 || !(arr[0] instanceof Integer)) return;
                int    auctionId      = (Integer) arr[0];
                String winnerUsername = arr[1] instanceof String ? (String) arr[1] : null;

                if (winnerUsername == null || currentUser == null) return;
                if (!winnerUsername.equals(currentUser.getUsername())) return;

                // Cập nhật trạng thái dòng thắng
                historyList.stream()
                        .filter(r -> r.getAuctionId() == auctionId)
                        .max((a, b) -> Double.compare(a.getBidAmount(), b.getBidAmount()))
                        .ifPresent(row -> row.setStatus("WINNER - Chờ xác nhận"));

                if (historyTable != null) historyTable.refresh();
            } catch (Exception e) {
                System.err.println("[BID_HISTORY] Lỗi handleAuctionEndedBroadcast: " + e.getMessage());
            }
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

            ReportIssueController reportCtrl = loader.getController();
            if (reportCtrl != null) {
                reportCtrl.setIssueData(selected, currentUser);
            }

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Báo Cáo Sự Cố - Phiên #" + selected.getAuctionId());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner((Stage) ((Node) event.getSource()).getScene().getWindow());
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            showWarning("Không thể mở cửa sổ báo cáo: " + e.getMessage());
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