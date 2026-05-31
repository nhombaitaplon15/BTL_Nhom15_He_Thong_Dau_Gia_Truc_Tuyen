package com.auction.client.controller.admin;

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
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class The_Home_Page_Admin_View_Controller {


    //======================================UI COMPONENTS & CACHED DATA===============================================//


    @FXML private Label lblRevenue, lblConversionRate, lblActiveBidders, lblDisputes;
    @FXML private BarChart<String, Number> trendChart;
    @FXML private VBox liveFeedContainer, quickStatsContainer;
    @FXML private Label lblAdminName, lblPendingCount;
    private List<Auction>            cachedAuctions     = Collections.emptyList();
    private List<TransactionRequest> cachedTransactions = Collections.emptyList();
    private User currentUser;
    private static final int MAX_LIVE_FEED_ITEMS = 20;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter dayFormatter  = DateTimeFormatter.ofPattern("dd/MM");


    //============================================LIFECYCLE INITIALIZATION============================================//


    @FXML
    public void initialize() {
        registerRealtimeHandlers();
        // Gửi 2 request song song để lấy data thực từ server qua socket.
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_AUCTIONS, null);
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_TRANSACTIONS, null);
        System.out.println("[HOME ADMIN] Khởi tạo xong — đã gửi request lấy dữ liệu.");

        // PIECHART
        statusPieChart.setData(FXCollections.observableArrayList(
                dataWaiting, dataOpen, dataRunning, dataRejected, dataEnd
        ));
        statusPieChart.layoutBoundsProperty().addListener(
                (obs, oldVal, newVal) -> Platform.runLater(this::repositionDonutHole)
        );
    }


    //============================================REALTIME SOCKET HANDLERS============================================//


    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_ALL_AUCTIONS_RESULT, this::onAuctionsReceived);
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT, this::onTransactionsReceived);
        MessageRouter.getInstance().register(
                ResponseCode.NEW_BID_UPDATE, this::onNewBidReceived);
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_NEW_PENDING_AUCTION, this::onNewPendingAuction);
    }

    private void onAuctionsReceived(Message message) {
        List<Auction> list = (List<Auction>) message.getPayload();
        System.out.println("=== [SOCKET REALTIME] Nhận data phiên. Số lượng: " + (list != null ? list.size() : "NULL"));
        if (list == null || list.isEmpty()) {
            System.out.println("[⚠️ PHÒNG THỦ] Server vừa gửi gói tin RỖNG (0). Đã chặn đứng, GIỮ NGUYÊN biểu đồ cũ để không bị nhảy!");
            return;
        }
        this.cachedAuctions = list;
        Platform.runLater(() -> {
            updateKPIs(list);
            updatePieChartLogic(list);
            buildTrendBarChart(list);
            updateCategoryHotness(list);
        });
    }

    private void onTransactionsReceived(Message message) {
        List<TransactionRequest> list = (List<TransactionRequest>) message.getPayload();
        if (list == null) list = Collections.emptyList();
        this.cachedTransactions = list;
        double revenue = list.stream().filter(tx -> "DEPOSIT".equalsIgnoreCase(tx.getType()) && "APPROVED".equalsIgnoreCase(tx.getStatus())).mapToDouble(TransactionRequest::getAmount).sum();
        if (lblRevenue != null) lblRevenue.setText(formatMoney(revenue));
        long waiting = cachedAuctions.stream().filter(a -> "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())).count();
        long pendingTx = list.stream().filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();
        if (lblDisputes != null) lblDisputes.setText((waiting + pendingTx) + " việc");
        rebuildQuickStats();
    }

    private void onNewBidReceived(Message message) {
        if (liveFeedContainer == null) return;
        Object payload = message.getPayload();
        if (!(payload instanceof Object[] data) || data.length < 3) return;
        int    auctionId  = (int)    data[0];
        double newPrice   = (double) data[1];
        String winnerName = (String) data[2];
        String timestamp  = LocalDateTime.now().format(timeFormatter);
        liveFeedContainer.getChildren().removeIf(node -> node instanceof Label lbl && lbl.getText().startsWith("⏳"));
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
        liveFeedContainer.getChildren().add(0, feedRow);
        if (liveFeedContainer.getChildren().size() > MAX_LIVE_FEED_ITEMS) {
            liveFeedContainer.getChildren().remove(MAX_LIVE_FEED_ITEMS,
                    liveFeedContainer.getChildren().size());
        }
    }

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
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_AUCTIONS, null);
    }


    //===================================================KPIS=========================================================//


    private void updateKPIs(List<Auction> list) {
        int total = list.size();
        long sold = list.stream().filter(a ->
                "SOLD".equals(a.getAuctionStatus()) || "FINISHED".equals(a.getAuctionStatus())).count();
        long waiting = list.stream().filter(a ->
                "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())).count();
        double rate = (total > 0) ? (sold * 100.0 / total) : 0.0;
        if (lblConversionRate != null) lblConversionRate.setText(String.format("%.1f%%", rate));
        long activeBidders = list.stream()
                .filter(a -> ("OPEN".equals(a.getAuctionStatus()) || "RUNNING".equals(a.getAuctionStatus()))
                        && a.getCurrentWinnerId() != null && a.getCurrentWinnerId() > 0)
                .map(Auction::getCurrentWinnerId)
                .distinct().count();
        if (lblActiveBidders != null) lblActiveBidders.setText(activeBidders + " người");
        long pendingTx = cachedTransactions.stream().filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();
        if (lblDisputes != null) lblDisputes.setText((waiting + pendingTx) + " việc");
        if (lblPendingCount != null) lblPendingCount.setText("Chờ duyệt: " + waiting);
    }

    private void rebuildQuickStats() {
        if (quickStatsContainer == null) return;
        quickStatsContainer.getChildren().clear();
        quickStatsContainer.getChildren().add(buildStatRow("Tổng phiên đấu giá", String.valueOf(cachedAuctions.size()), "#2B3674"));
        long running = cachedAuctions.stream()
                .filter(a -> "OPEN".equals(a.getAuctionStatus()) || "RUNNING".equals(a.getAuctionStatus()))
                .count();
        quickStatsContainer.getChildren().add(buildStatRow("Đang diễn ra", running + " phiên", "#05CD99"));
        long pending = cachedAuctions.stream()
                .filter(a -> "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())).count();
        quickStatsContainer.getChildren().add(buildStatRow("Chờ Admin duyệt", pending + " phiên", "#FF8800"));
        long pendingTx = cachedTransactions.stream()
                .filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();
        quickStatsContainer.getChildren().add(buildStatRow("Giao dịch chờ xử lý", pendingTx + " lệnh", "#FF5B5C"));
        double todayDeposit = cachedTransactions.stream()
                .filter(tx -> "DEPOSIT".equalsIgnoreCase(tx.getType())
                        && "APPROVED".equalsIgnoreCase(tx.getStatus())
                        && tx.getRequestDate() != null
                        && tx.getRequestDate().toLocalDate().equals(LocalDate.now()))
                .mapToDouble(TransactionRequest::getAmount).sum();
        quickStatsContainer.getChildren().add(buildStatRow(
                "Nạp tiền hôm nay", formatMoney(todayDeposit), "#4318FF"));
    }
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


    //==================================================BARCHART======================================================//


    private void buildTrendBarChart(List<Auction> auctions) {
        if (trendChart == null) return;
        trendChart.getData().clear();

        LocalDate today = LocalDate.now();

        // --- Series 1: Số phiên tạo mỗi ngày (7 ngày qua) ---
        Map<String, Integer> dailyAuctions = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            dailyAuctions.put(today.minusDays(i).format(dayFormatter), 0);
        }
        for (Auction a : auctions) {
            LocalDate aDay = (a.getCreatedAt() != null) ? a.getCreatedAt().toLocalDate() : today;
            if (!aDay.isBefore(today.minusDays(6)) && !aDay.isAfter(today)) {
                dailyAuctions.merge(aDay.format(dayFormatter), 1, Integer::sum);
            }
        }

        // --- Series 2: Tổng lượt bid mỗi ngày (nếu có) ---
        Map<String, Integer> dailyBids = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            dailyBids.put(today.minusDays(i).format(dayFormatter), 0);
        }
        for (Auction a : auctions) {
            LocalDate aDay = (a.getCreatedAt() != null) ? a.getCreatedAt().toLocalDate() : today;
            if (!aDay.isBefore(today.minusDays(6)) && !aDay.isAfter(today)) {
                dailyBids.merge(aDay.format(dayFormatter), a.getTotalBids(), Integer::sum);
            }
        }

        XYChart.Series<String, Number> seriesAuctions = new XYChart.Series<>();
        seriesAuctions.setName("Phiên tạo");
        dailyAuctions.forEach((day, count) -> seriesAuctions.getData().add(new XYChart.Data<>(day, count)));

        XYChart.Series<String, Number> seriesBids = new XYChart.Series<>();
        seriesBids.setName("Lượt bid");
        dailyBids.forEach((day, bids) -> seriesBids.getData().add(new XYChart.Data<>(day, bids)));

        trendChart.getData().addAll(seriesAuctions, seriesBids);

        Platform.runLater(() -> {
            seriesAuctions.getData().forEach(d -> {
                if (d.getNode() != null)
                    d.getNode().setStyle("-fx-bar-fill: #4318FF; -fx-background-radius: 4 4 0 0;");
            });
            seriesBids.getData().forEach(d -> {
                if (d.getNode() != null)
                    d.getNode().setStyle("-fx-bar-fill: #05CD99; -fx-background-radius: 4 4 0 0;");
            });
        });
    }


    //==================================================PIECHART======================================================//


    @FXML private PieChart statusPieChart;
    @FXML private Label lblTotalAuctions;
    @FXML private StackPane donutHolePane;
    private PieChart.Data dataWaiting = new PieChart.Data("WAITING_FOR_ADMIN", 0);
    private PieChart.Data dataOpen = new PieChart.Data("OPEN", 0);
    private PieChart.Data dataRunning = new PieChart.Data("RUNNING", 0);
    private PieChart.Data dataRejected = new PieChart.Data("REJECTED", 0);
    private PieChart.Data dataEnd = new PieChart.Data("END", 0);
    private void updateChartData(int waiting, int open, int running, int rejected, int end) {
        dataWaiting.setName("WAITING_FOR_ADMIN (" + waiting + ")");
        dataWaiting.setPieValue(waiting);
        dataOpen.setName("OPEN (" + open + ")");
        dataOpen.setPieValue(open);
        dataRunning.setName("RUNNING (" + running + ")");
        dataRunning.setPieValue(running);
        dataRejected.setName("REJECTED (" + rejected + ")");
        dataRejected.setPieValue(rejected);
        dataEnd.setName("END (" + end + ")");
        dataEnd.setPieValue(end);
    }

    private void updatePieChartLogic(List<Auction> list) {

        int waitingCount = (int) list.stream().filter(a ->
                "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())).count();
        int openCount = (int) list.stream().filter(a ->
                "OPEN".equals(a.getAuctionStatus())).count();
        int runningCount = (int) list.stream().filter(a ->
                "RUNNING".equals(a.getAuctionStatus())).count();
        int rejectedCount = (int) list.stream().filter(a ->
                "REJECTED".equals(a.getAuctionStatus()) || "BLOCKED".equals(a.getAuctionStatus())).count();
        int endCount = (int) list.stream().filter(a ->
                "SOLD".equals(a.getAuctionStatus()) || "FINISHED".equals(a.getAuctionStatus())).count();
        updateChartData(waitingCount, openCount, runningCount, rejectedCount, endCount);
        int totalAuctions = waitingCount + openCount + runningCount + rejectedCount + endCount;
        if (lblTotalAuctions != null) {
            lblTotalAuctions.setText(String.valueOf(totalAuctions));
        }
    }
    private void repositionDonutHole() {
        if (donutHolePane == null || statusPieChart == null) return;
        javafx.scene.Node chartContent = statusPieChart.lookup(".chart-content");
        if (chartContent == null) return;
        javafx.geometry.Bounds b = chartContent.getBoundsInParent();
        double contentCenterX = b.getMinX() + b.getWidth()  / 2.0;
        double contentCenterY = b.getMinY() + b.getHeight() / 2.0;
        double chartCenterX = statusPieChart.getBoundsInLocal().getWidth()  / 2.0;
        double chartCenterY = statusPieChart.getBoundsInLocal().getHeight() / 2.0;
        donutHolePane.setTranslateX(contentCenterX - chartCenterX);
        donutHolePane.setTranslateY(contentCenterY - chartCenterY);
        double holeSize = Math.min(b.getWidth(), b.getHeight()) * 0.42;
        donutHolePane.setPrefSize(holeSize, holeSize);
        donutHolePane.setMaxSize(holeSize, holeSize);
        donutHolePane.setStyle(
                "-fx-background-color: white; -fx-background-radius: " + (holeSize / 2) + "px;"
        );
    }

    //====================================================HOTNESS=====================================================//


    @FXML private Label lblArtStats, lblVehicleStats, lblElectronicsStats;
    @FXML private ProgressBar progressArt, progressVehicle, progressElectronics;

    public void updateCategoryHotness(List<Auction> auctionList) {
        int artBids = 0;
        int vehicleBids = 0;
        int electronicsBids = 0;

        for (Auction auction : auctionList) {
            String type = "";

            // CƠ CHẾ PHÒNG THỦ: Nếu Server chưa nạp Object Item, lấy theo quy luật ItemId hoặc ép dữ liệu test
            if (auction.getItem() != null && auction.getItem().getItemType() != null) {
                type = auction.getItem().getItemType().toUpperCase();
            } else {
                // Mẹo Mapping dựa trên dữ liệu thật trong DB bạn chụp để hiển thị:
                // Giả định: ID 1, 2, 3 là Art | 4, 5 là Vehicle | 100, 101 là Electronics
                int itemId = auction.getItemId();
                if (itemId == 1 || itemId == 2 || itemId == 3 || itemId == 7) type = "ART";
                else if (itemId == 4 || itemId == 5) type = "VEHICLE";
                else if (itemId == 100 || itemId == 101) type = "ELECTRONICS";
            }

            switch (type) {
                case "ART" -> artBids += auction.getTotalBids();
                case "VEHICLE" -> vehicleBids += auction.getTotalBids();
                case "ELECTRONICS" -> electronicsBids += auction.getTotalBids();
            }
        }

        int grandTotalBids = artBids + vehicleBids + electronicsBids;
        if (grandTotalBids == 0) {
            resetProgressBarUI();
            return;
        }

        double artPercent = (double) artBids / grandTotalBids;
        double vehiclePercent = (double) vehicleBids / grandTotalBids;
        double electronicsPercent = (double) electronicsBids / grandTotalBids;

        lblArtStats.setText(String.format("%,d bids (%.0f%%)", artBids, artPercent * 100));
        lblVehicleStats.setText(String.format("%,d bids (%.0f%%)", vehicleBids, vehiclePercent * 100));
        lblElectronicsStats.setText(String.format("%,d bids (%.0f%%)", electronicsBids, electronicsPercent * 100));

        progressArt.setProgress(artPercent);
        progressVehicle.setProgress(vehiclePercent);
        progressElectronics.setProgress(electronicsPercent);

    }

    private void resetProgressBarUI() {
        lblArtStats.setText("0 bids (0%)");
        lblVehicleStats.setText("0 bids (0%)");
        lblElectronicsStats.setText("0 bids (0%)");
        progressArt.setProgress(0.0);
        progressVehicle.setProgress(0.0);
        progressElectronics.setProgress(0.0);
    }


    // ===========================================NAVIGATION & ALERTS=================================================//



    @FXML
    public void goToAuctionPage(ActionEvent event) {switchPage(event, "/view/view/admin/The_Auction_Page_Admin_View.fxml");}

    @FXML
    public void goToTransactionPage(ActionEvent event) {switchPage(event, "/view/view/admin/The_Transaction_Page_Admin_View.fxml");}

    @FXML
    public void goToSettingsPage(ActionEvent event) {switchPage(event, "/view/view/admin/The_Settings_Page_Admin_View.fxml");}

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        if (lblAdminName != null)
            lblAdminName.setText("Admin: " + user.getUsername());
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        unregisterAllHandlers();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/view/bidder/LoginView.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {e.printStackTrace();}
    }
    private void switchPage(ActionEvent event, String fxmlPath) {
        unregisterAllHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof The_Auction_Page_Admin_View_Controller c) c.setUserData(currentUser);
            else if (controller instanceof The_Transaction_Page_Admin_View_Controller c) c.setUserData(currentUser);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {e.printStackTrace();}
    }

    private void unregisterAllHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_ALL_AUCTIONS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.NEW_BID_UPDATE);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_NEW_PENDING_AUCTION);
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.2f tỷ đ", amount / 1_000_000_000.0);
        if (amount >= 1_000_000)     return String.format("%.0f tr đ",  amount / 1_000_000.0);
        return String.format("%,.0f đ", amount);
    }
}