package com.auction.client.controller.admin;

/*
 * ============================================================
 * FILE: The_Home_Page_Admin_View_Controller.java
 * ĐẶT TẠI: client/src/main/java/com/auction/client/controller/admin/
 *
 * THAY ĐỔI SO VỚI FILE GỐC (rất tối giản - chỉ có navigation):
 *
 * 1. [THÊM] @FXML cho 4 KPI Labels: lblRevenue, lblConversionRate,
 *            lblActiveBidders, lblDisputes
 *            → Khớp với fx:id mới thêm vào FXML
 *
 * 2. [GIỮ]  @FXML trendChart + statusPieChart (đã có fx:id sẵn)
 *
 * 3. [THÊM] @FXML liveFeedContainer (VBox) + quickStatsContainer (VBox)
 *            → Khớp với fx:id mới thêm vào 2 ô panel phía dưới
 *
 * 4. [THÊM] initialize() gửi 2 request: ADMIN_GET_ALL_AUCTIONS +
 *            ADMIN_GET_ALL_TRANSACTIONS để lấy data thực từ DB qua socket
 *
 * 5. [THÊM] onAuctionsReceived():
 *            - Tính Conversion Rate = (SOLD/FINISHED) / total * 100
 *            - Tính Active Bidders = distinct currentWinnerId trong phòng OPEN/RUNNING
 *            - Điền lblDisputes (chờ Admin duyệt phiên + giao dịch pending)
 *            - Vẽ PieChart phân bố trạng thái phiên
 *            - Vẽ BarChart lượt bids theo 7 ngày gần nhất (từ totalBids + createdAt)
 *
 * 6. [THÊM] onTransactionsReceived():
 *            - Tính Revenue = tổng DEPOSIT APPROVED
 *            - Cập nhật lblDisputes (cộng thêm số PENDING transactions)
 *            - Gọi rebuildQuickStats() để cập nhật panel thống kê nhanh
 *
 * 7. [THÊM] onNewBidReceived(): handler real-time NEW_BID_UPDATE từ server
 *            → Khi có bid mới bất kỳ, thêm ngay 1 dòng vào liveFeedContainer
 *            → Giới hạn tối đa 20 dòng, không cần reload trang
 *
 * 8. [THÊM] buildTrendBarChart(): nhóm totalBids theo ngày createdAt 7 ngày
 * 9. [THÊM] rebuildQuickStats(): tổng hợp số liệu quan trọng dạng bảng nhanh
 * 10.[THÊM] unregister() tất cả handler khi chuyển màn hình (tránh memory leak)
 * 11.[GIỮ]  setUserData(), navigation, handleLogout() từ file gốc
 * ============================================================
 */

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class The_Home_Page_Admin_View_Controller {

    // =========================================================
    // FXML FIELDS — tên phải khớp fx:id trong The_Home_Page_Admin_View.fxml
    // =========================================================

    /** [THÊM] 4 KPI Labels — trước đây không có field, text vẫn là "Label" */
    @FXML private Label lblRevenue;
    @FXML private Label lblConversionRate;
    @FXML private Label lblActiveBidders;
    @FXML private Label lblDisputes;

    /** [GIỮ] Charts — fx:id đã có sẵn trong FXML gốc */
    @FXML private BarChart<String, Number> trendChart;
    @FXML private PieChart statusPieChart;

    /** [THÊM] Panels phía dưới — FXML gốc để trống hoàn toàn, nay có fx:id */
    @FXML private VBox liveFeedContainer;
    @FXML private VBox quickStatsContainer;

    /** [GIỮ] Từ file gốc (có thể null nếu FXML không khai báo — null-safe) */
    @FXML private Label lblAdminName;
    @FXML private Label lblPendingCount;

    // =========================================================
    // STATE
    // =========================================================

    private User currentUser;

    /** Số dòng tối đa giữ trong Live Feed để tránh memory leak */
    private static final int MAX_LIVE_FEED_ITEMS = 20;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter dayFormatter  = DateTimeFormatter.ofPattern("dd/MM");

    /**
     * Cache dữ liệu để tính KPI kết hợp auction + transaction.
     * Cần cache vì 2 response đến bất đồng bộ; cái đến sau
     * cần đọc lại kết quả của cái đến trước để tính lblDisputes.
     */
    private List<Auction>            cachedAuctions     = Collections.emptyList();
    private List<TransactionRequest> cachedTransactions = Collections.emptyList();

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @FXML
    public void initialize() {
        registerRealtimeHandlers();
        // Gửi 2 request song song để lấy data thực từ server qua socket.
        // Server xử lý async và push response lại qua MessageRouter.
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_AUCTIONS, null);
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_TRANSACTIONS, null);
        System.out.println("[HOME ADMIN] Khởi tạo xong — đã gửi request lấy dữ liệu.");;
    }

    /** Được gọi từ LoginController hoặc màn hình điều hướng. */
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        if (lblAdminName != null)
            lblAdminName.setText("Admin: " + user.getUsername());
    }

    // =========================================================
    // ĐĂNG KÝ HANDLERS
    // =========================================================

    /**
     * Mỗi màn hình tự quản lý set handler của mình.
     * Lý do: MessageRouter dùng Map<ResponseCode, Consumer> nên
     * mỗi ResponseCode chỉ có 1 handler tại một thời điểm.
     * Nếu không unregister khi chuyển màn, màn cũ có thể
     * xử lý message của màn mới gây lỗi NullPointerException.
     */
    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_ALL_AUCTIONS_RESULT, this::onAuctionsReceived);
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT, this::onTransactionsReceived);
        // Real-time: nhận ngay khi có bid mới từ bất kỳ phòng nào
        MessageRouter.getInstance().register(
                ResponseCode.NEW_BID_UPDATE, this::onNewBidReceived);
        // Push notification khi Seller tạo phiên mới cần duyệt
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_NEW_PENDING_AUCTION, this::onNewPendingAuction);
    }

    // =========================================================
    // HANDLER: NHẬN DANH SÁCH PHIÊN ĐẤU GIÁ
    // =========================================================

    /**
     * [THÊM] Server trả về ADMIN_ALL_AUCTIONS_RESULT → payload: List<Auction>
     *
     * Lý do tính tại client thay vì server:
     * - Giảm tải cho server (server đã bận xử lý nhiều socket)
     * - Data đã có đầy đủ trong payload, không cần thêm request
     * - Conversion Rate, Active Bidders là phép tính đơn giản O(n)
     */
    @SuppressWarnings("unchecked")
    private void onAuctionsReceived(Message message) {
        List<Auction> list = (List<Auction>) message.getPayload();
        if (list == null) list = Collections.emptyList();
        this.cachedAuctions = list;

        int   total    = list.size();
        long  sold     = list.stream().filter(a ->
                "SOLD".equals(a.getAuctionStatus()) || "FINISHED".equals(a.getAuctionStatus())).count();
        long  running  = list.stream().filter(a ->
                "OPEN".equals(a.getAuctionStatus()) || "RUNNING".equals(a.getAuctionStatus())).count();
        long  waiting  = list.stream().filter(a ->
                "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())).count();
        long  rejected = list.stream().filter(a ->
                "REJECTED".equals(a.getAuctionStatus()) || "BLOCKED".equals(a.getAuctionStatus())).count();

        // --- KPI: Conversion Rate = phiên đã có người thắng / tổng phiên ---
        double rate = (total > 0) ? (sold * 100.0 / total) : 0.0;
        if (lblConversionRate != null) lblConversionRate.setText(String.format("%.1f%%", rate));

        // --- KPI: Active Bidders = số unique user đang dẫn đầu trong phiên mở ---
        long activeBidders = list.stream()
                .filter(a -> ("OPEN".equals(a.getAuctionStatus()) || "RUNNING".equals(a.getAuctionStatus()))
                        && a.getCurrentWinnerId() != null && a.getCurrentWinnerId() > 0)
                .map(Auction::getCurrentWinnerId)
                .distinct().count();
        if (lblActiveBidders != null) lblActiveBidders.setText(activeBidders + " người");

        // --- KPI: Disputes = phiên chờ duyệt + giao dịch pending (cập nhật sau khi có tx) ---
        long pendingTx = cachedTransactions.stream()
                .filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();
        if (lblDisputes != null) lblDisputes.setText((waiting + pendingTx) + " việc");

        // Badge số phiên chờ duyệt
        if (lblPendingCount != null) lblPendingCount.setText("Chờ duyệt: " + waiting);

        // --- PIE CHART: Phân bố trạng thái phiên đấu giá ---
        List<PieChart.Data> pieData = new ArrayList<>();
        if (running  > 0) pieData.add(new PieChart.Data("Đang chạy ("  + running  + ")", running));
        if (sold     > 0) pieData.add(new PieChart.Data("Đã bán ("     + sold     + ")", sold));
        if (waiting  > 0) pieData.add(new PieChart.Data("Chờ duyệt ("  + waiting  + ")", waiting));
        if (rejected > 0) pieData.add(new PieChart.Data("Từ chối ("    + rejected + ")", rejected));
        long other = total - running - sold - waiting - rejected;
        if (other    > 0) pieData.add(new PieChart.Data("Khác ("       + other    + ")", other));

        if (statusPieChart != null) {
            statusPieChart.setData(FXCollections.observableArrayList(
                    pieData.isEmpty()
                            ? List.of(new PieChart.Data("Chưa có dữ liệu", 1))
                            : pieData));
        }

        // --- BAR CHART: Lượt bids theo 7 ngày gần nhất ---
        buildTrendBarChart(list);

        // Cập nhật quick stats sau khi có auction data
        rebuildQuickStats();
    }

    // =========================================================
    // HANDLER: NHẬN DANH SÁCH GIAO DỊCH
    // =========================================================

    /**
     * [THÊM] Server trả về ADMIN_ALL_TRANSACTIONS_RESULT → payload: List<TransactionRequest>
     *
     * Revenue = tổng tiền DEPOSIT đã APPROVED (tiền thực đã vào hệ thống).
     * Không tính PENDING vì chưa được xác nhận.
     */
    @SuppressWarnings("unchecked")
    private void onTransactionsReceived(Message message) {
        List<TransactionRequest> list = (List<TransactionRequest>) message.getPayload();
        if (list == null) list = Collections.emptyList();
        this.cachedTransactions = list;

        // Revenue = tổng DEPOSIT APPROVED
        double revenue = list.stream()
                .filter(tx -> "DEPOSIT".equalsIgnoreCase(tx.getType())
                        && "APPROVED".equalsIgnoreCase(tx.getStatus()))
                .mapToDouble(TransactionRequest::getAmount)
                .sum();
        if (lblRevenue != null) lblRevenue.setText(formatMoney(revenue));

        // Cập nhật lblDisputes: cộng thêm số pending transactions
        long waiting = cachedAuctions.stream()
                .filter(a -> "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())).count();
        long pendingTx = list.stream()
                .filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();
        if (lblDisputes != null) lblDisputes.setText((waiting + pendingTx) + " việc");

        rebuildQuickStats();
    }

    // =========================================================
    // HANDLER REAL-TIME: BID MỚI (thêm dòng vào Live Feed)
    // =========================================================

    /**
     * [THÊM] Được gọi ngay khi server broadcast NEW_BID_UPDATE.
     * payload: Object[] {Integer auctionId, Double newPrice, String winnerUsername}
     *
     * Không cần reload trang — chỉ thêm 1 dòng HBox vào liveFeedContainer.
     * Platform.runLater không cần vì MessageRouter chạy trên JavaFX Thread.
     */
    private void onNewBidReceived(Message message) {
        if (liveFeedContainer == null) return;
        Object payload = message.getPayload();
        if (!(payload instanceof Object[] data) || data.length < 3) return;

        int    auctionId  = (int)    data[0];
        double newPrice   = (double) data[1];
        String winnerName = (String) data[2];
        String timestamp  = LocalDateTime.now().format(timeFormatter);

        // Xóa placeholder "Đang chờ..." nếu còn
        liveFeedContainer.getChildren().removeIf(node ->
                node instanceof Label lbl && lbl.getText().startsWith("⏳"));

        // Tạo dòng feed mới
        HBox feedRow = new HBox(10);
        feedRow.setAlignment(Pos.CENTER_LEFT);
        feedRow.setStyle("-fx-background-color: #F8FFF8; -fx-background-radius: 8; -fx-padding: 5 10 5 10;");

        Label lblTime = new Label("[" + timestamp + "]");
        lblTime.setStyle("-fx-text-fill: #A3AED0; -fx-font-size: 11px; -fx-font-family: 'Courier New';");

        Label lblAuction = new Label("Phiên #" + auctionId);
        lblAuction.setStyle("-fx-text-fill: #4318FF; -fx-font-weight: bold; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblInfo = new Label("🏆 " + winnerName + " → " + formatMoney(newPrice));
        lblInfo.setStyle("-fx-text-fill: #05CD99; -fx-font-weight: bold; -fx-font-size: 12px;");

        feedRow.getChildren().addAll(lblTime, lblAuction, spacer, lblInfo);

        // Thêm vào đầu (mới nhất trên cùng)
        liveFeedContainer.getChildren().add(0, feedRow);

        // Giới hạn số dòng để tránh tràn bộ nhớ sau nhiều giờ chạy
        if (liveFeedContainer.getChildren().size() > MAX_LIVE_FEED_ITEMS) {
            liveFeedContainer.getChildren().remove(MAX_LIVE_FEED_ITEMS,
                    liveFeedContainer.getChildren().size());
        }
    }

    // =========================================================
    // HANDLER: PHIÊN MỚI CHỜ DUYỆT (push notification)
    // =========================================================

    private void onNewPendingAuction(Message message) {
        Auction auction = (Auction) message.getPayload();
        if (auction == null) return;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("📋 Phiên Mới Cần Duyệt!");
        alert.setHeaderText("Seller vừa gửi yêu cầu phiên đấu giá mới.");
        alert.setContentText("Mã phiên: #" + auction.getAuctionId()
                + "\nSản phẩm: #" + auction.getItemId()
                + "\nNgười bán: #" + auction.getSellerId()
                + "\n\nHãy vào tab 'Danh sách đấu giá' để duyệt.");
        alert.show();

        // Refresh để cập nhật KPI
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_AUCTIONS, null);
    }

    // =========================================================
    // XÂY DỰNG BAR CHART (Lượt bid theo ngày)
    // =========================================================

    /**
     * Nhóm phiên đấu giá theo ngày createdAt, cộng dồn totalBids.
     *
     * Lý do dùng createdAt + totalBids thay vì log bid riêng:
     * API hiện tại chỉ trả về List<Auction>; không có endpoint
     * riêng cho bid-per-day log. Đây là best-effort với data sẵn có.
     */
    private void buildTrendBarChart(List<Auction> auctions) {
        if (trendChart == null) return;
        trendChart.getData().clear();
        trendChart.setTitle("Tổng lượt bid theo ngày");

        LocalDate today = LocalDate.now();
        // LinkedHashMap giữ thứ tự ngày cũ→mới để chart hiển thị đúng chiều
        Map<String, Integer> dailyBids = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            dailyBids.put(today.minusDays(i).format(dayFormatter), 0);
        }

        for (Auction a : auctions) {
            if (a.getCreatedAt() == null) continue;
            LocalDate aDay = a.getCreatedAt().toLocalDate();
            if (!aDay.isBefore(today.minusDays(6)) && !aDay.isAfter(today)) {
                dailyBids.merge(aDay.format(dayFormatter), a.getTotalBids(), Integer::sum);
            }
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Lượt bid");
        dailyBids.forEach((day, bids) ->
                series.getData().add(new XYChart.Data<>(day, bids)));
        trendChart.getData().add(series);

        // Style màu bar phải set sau khi add vào chart (JavaFX render sau)
        Platform.runLater(() -> series.getData().forEach(d -> {
            if (d.getNode() != null)
                d.getNode().setStyle("-fx-bar-fill: #4318FF;");
        }));
    }

    // =========================================================
    // XÂY DỰNG QUICK STATS
    // =========================================================

    /**
     * Xây dựng bảng thống kê nhanh từ cachedAuctions + cachedTransactions.
     * Được gọi sau khi nhận response từ cả 2 request.
     */
    private void rebuildQuickStats() {
        if (quickStatsContainer == null) return;
        quickStatsContainer.getChildren().clear();

        quickStatsContainer.getChildren().add(buildStatRow(
                "📦 Tổng phiên đấu giá",
                String.valueOf(cachedAuctions.size()), "#2B3674"));

        long running = cachedAuctions.stream()
                .filter(a -> "OPEN".equals(a.getAuctionStatus()) || "RUNNING".equals(a.getAuctionStatus()))
                .count();
        quickStatsContainer.getChildren().add(buildStatRow(
                "🔴 Đang diễn ra", running + " phiên", "#05CD99"));

        long pending = cachedAuctions.stream()
                .filter(a -> "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())).count();
        quickStatsContainer.getChildren().add(buildStatRow(
                "⏳ Chờ Admin duyệt", pending + " phiên", "#FF8800"));

        long pendingTx = cachedTransactions.stream()
                .filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();
        quickStatsContainer.getChildren().add(buildStatRow(
                "💳 Giao dịch chờ xử lý", pendingTx + " lệnh", "#FF5B5C"));

        double todayDeposit = cachedTransactions.stream()
                .filter(tx -> "DEPOSIT".equalsIgnoreCase(tx.getType())
                        && "APPROVED".equalsIgnoreCase(tx.getStatus())
                        && tx.getRequestDate() != null
                        && tx.getRequestDate().toLocalDate().equals(LocalDate.now()))
                .mapToDouble(TransactionRequest::getAmount).sum();
        quickStatsContainer.getChildren().add(buildStatRow(
                "📥 Nạp tiền hôm nay", formatMoney(todayDeposit), "#4318FF"));
    }

    /** Tạo một hàng thống kê: [Label trái] — [Value phải có màu] */
    private HBox buildStatRow(String label, String value, String valueColor) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-border-color: #F4F7FE; -fx-border-width: 0 0 1 0; -fx-padding: 4 0 4 0;");

        Label lblLabel = new Label(label);
        lblLabel.setFont(Font.font("Times New Roman", 12));
        lblLabel.setStyle("-fx-text-fill: #707EAE;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblValue = new Label(value);
        lblValue.setFont(Font.font("Times New Roman Bold", 12));
        lblValue.setStyle("-fx-text-fill: " + valueColor + "; -fx-font-weight: bold;");

        row.getChildren().addAll(lblLabel, spacer, lblValue);
        return row;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.2f tỷ đ", amount / 1_000_000_000.0);
        if (amount >= 1_000_000)     return String.format("%.0f tr đ",  amount / 1_000_000.0);
        return String.format("%,.0f đ", amount);
    }

    // =========================================================
    // NAVIGATION — giữ nguyên từ file gốc, thêm unregister()
    // =========================================================

    @FXML
    public void goToAuctionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Auction_Page_Admin_View.fxml");
    }

    @FXML
    public void goToTransactionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Transaction_Page_Admin_View.fxml");
    }

    @FXML
    public void goToSettingsPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Settings_Page_Admin_View.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        unregisterAllHandlers();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/view/LoginView.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Hủy tất cả handler trước khi chuyển màn.
     * Lý do bắt buộc: MessageRouter.handlers là singleton Map.
     * Nếu không hủy, màn cũ vẫn nhận message gây NullPointerException
     * vì các @FXML field đã bị garbage collect.
     */
    private void unregisterAllHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_ALL_AUCTIONS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.NEW_BID_UPDATE);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_NEW_PENDING_AUCTION);
    }

    private void switchPage(ActionEvent event, String fxmlPath) {
        unregisterAllHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object controller = loader.getController();

            if (controller instanceof The_Auction_Page_Admin_View_Controller c)
                c.setUserData(currentUser);
            else if (controller instanceof The_Transaction_Page_Admin_View_Controller c)
                c.setUserData(currentUser);
            else if (controller instanceof The_Settings_Page_Admin_View_Controller c)
                c.setUserData(currentUser);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}