package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.server.core.AuctionItemDTO;
import com.auction.server.dao.BidDAO.BidRow;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;


/**
 * AuctionDetailController — Refactored với Networking.
 *
 * THAY ĐỔI:
 *  - Xóa AuctionDAO, BidDAO, UserDAO (không gọi DB trực tiếp từ client)
 *  - loadBidHistory() → gửi FETCH_BID_HISTORY qua SocketClient
 *  - onCancelAuction() → gửi SELLER_CANCEL_AUCTION thay vì auctionDAO.updateStatus()
 *  - Lắng nghe NEW_BID_UPDATE realtime: cập nhật giá + lịch sử bid khi đang mở view
 *  - Lắng nghe AUCTION_ENDED: đổi UI sang kết quả cuối
 *  - Lắng nghe AUCTION_TIME_EXTENDED: reset countdown
 *  - Lắng nghe SELLER_CANCEL_SUCCESS / FAILED để phản hồi huỷ phiên
 *  - cleanupHandlers() + stopTimer() được gọi khi đóng dialog
 */
public class AuctionDetailController {

  // ── HEADER ──
  @FXML private Label lblAuctionTitle;
  @FXML private Label lblAuctionSubtitle;
  @FXML private Label lblStatusPill;

  // ── WARNING BOX ──
  @FXML private HBox  sectionWarningInfo;
  @FXML private Label lblWarningIcon;
  @FXML private Label lblWarningText;

  // ── PRODUCT CARD ──
  @FXML private ImageView imgProduct;
  @FXML private Label     lblProductName;
  @FXML private Label     lblItemMeta;
  @FXML private Label     lblItemDesc;
  @FXML private Label     lblStartingPrice;
  @FXML private Label     lblCurrentPrice;
  @FXML private Label     lblBidCount;

  // ── INFO GRID ──
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;
  @FXML private Label lblDurationLabel;
  @FXML private Label lblDuration;
  @FXML private Label lblTotalBids;
  @FXML private Label lblBidders;
  @FXML private Label lblIncrement;

  // ── TIMER BAR ──
  @FXML private HBox        sectionTimer;
  @FXML private Label       lblTimerLabel;
  @FXML private Label       lblTimerDisplay;
  @FXML private ProgressBar barProgress;
  @FXML private Label       lblEndTimeShort;

  // ── BID HISTORY ──
  @FXML private VBox             sectionBidHistory;
  @FXML private ListView<BidRow> listBidHistory;

  // ── RESULT CARD ──
  @FXML private VBox  sectionResult;
  @FXML private HBox  bannerWinner;
  @FXML private Label lblResultIcon;
  @FXML private Label lblWinner;
  @FXML private Label lblWinnerTime;
  @FXML private Label lblWinnerPrice;
  @FXML private Label lblPriceIncrease;
  @FXML private HBox  bannerCanceled;
  @FXML private Label lblCancelReason;

  // ── ACTION BUTTONS ──
  @FXML private Button btnEdit;
  @FXML private Button btnCancelAuction;
  @FXML private Button btnViewDetail;
  @FXML private Button btnFollow;
  @FXML private Button btnViewChart;
  @FXML private Button btnExportReport;
  @FXML private Button btnRemindPayment;
  @FXML private Button btnContactWinner;

  // ── STATE ──
  private AuctionItemDTO currentAuctionItem;
  private Auction        currentAuction;
  private Item           currentItem;
  private Timeline       countdownTimer;

  private static final DateTimeFormatter DT_FMT    = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final DateTimeFormatter SHORT_FMT  = DateTimeFormatter.ofPattern("dd/MM · HH:mm");

  // ── Handler references ──
  private Consumer<Message> onNewBid;
  private Consumer<Message> onAuctionEnded;
  private Consumer<Message> onTimeExtended;
  private Consumer<Message> onBidHistory;
  private Consumer<Message> onCancelSuccess;
  private Consumer<Message> onCancelFailed;

  @FXML
  public void initialize() {
    setupBidHistoryCellFactory();
  }

  // ════════════════════════════════════════
  // ENTRY POINT
  // ════════════════════════════════════════

  public void setAuctionItem(AuctionItemDTO auctionItem) {
    this.currentAuctionItem = auctionItem;
    this.currentAuction     = auctionItem.getAuction();
    this.currentItem        = auctionItem.getItem();

    registerNetworkHandlers();

    hideAllSections();
    hideAllButtons();
    fillHeader();
    fillProductCard();
    fillInfoGrid();

    switch (currentAuction.getAuctionStatus()) {
      case "WAITING_FOR_ADMIN" -> setupPendingView();
      case "OPEN"              -> setupOpenView();
      case "RUNNING"           -> setupRunningView();
      case "FINISHED"          -> setupFinishedView(false);
      case "PAID"              -> setupFinishedView(true);
      case "CANCELED"          -> setupCanceledView();
      default                  -> setupPendingView();
    }
  }

  // ════════════════════════════════════════
  // ĐĂNG KÝ / HUỶ HANDLER
  // ════════════════════════════════════════

  private void registerNetworkHandlers() {
    int auctionId = currentAuction.getAuctionId();

    onNewBid = msg -> {
      try {
        Object[] data    = (Object[]) msg.getPayload();
        int id           = Integer.parseInt(data[0].toString());
        if (id != auctionId) return;
        double newPrice  = Double.parseDouble(data[1].toString());
        String winner    = String.valueOf(data[2]);

        // Cập nhật giá hiện tại
        currentAuction.setCurrentPrice(newPrice);
        currentAuction.setTotalBids(currentAuction.getTotalBids() + 1);

        lblCurrentPrice.setText(CardUtils.formatMoney(newPrice) + "đ");
        lblBidCount    .setText(currentAuction.getTotalBids() + " lượt bid");
        lblTotalBids   .setText(String.valueOf(currentAuction.getTotalBids()));

        showWarning("success", "🔔 " + winner + " vừa bid "
            + CardUtils.formatMoney(newPrice) + "đ — Tổng "
            + currentAuction.getTotalBids() + " lượt bid");

        // Reload lịch sử bid
        loadBidHistory();

      } catch (Exception e) {
        System.err.println("[DETAIL] Lỗi NEW_BID_UPDATE: " + e.getMessage());
      }
    };

    onAuctionEnded = msg -> {
      try {
        Object[] data = (Object[]) msg.getPayload();
        int id = Integer.parseInt(data[0].toString());
        if (id != auctionId) return;
        stopTimer();
        currentAuction.setAuctionStatus("FINISHED");
        applyStatusPill("FINISHED");
        showWarning("warning", "⏳ Phiên đã kết thúc. Đang chờ thanh toán.");
        // Reload để có winner info
        setupFinishedView(false);
      } catch (Exception e) {
        System.err.println("[DETAIL] Lỗi AUCTION_ENDED: " + e.getMessage());
      }
    };

    onTimeExtended = msg -> {
      try {
        Object[] data = (Object[]) msg.getPayload();
        int id = Integer.parseInt(data[0].toString());
        if (id != auctionId) return;
        if (data[1] instanceof LocalDateTime newEnd) {
          currentAuction.setEndTime(newEnd);
          lblEndTimeShort.setText(newEnd.format(SHORT_FMT));
          long totalSecs = ChronoUnit.SECONDS.between(
              currentAuction.getStartTime(), newEnd);
          startCountdown(newEnd, totalSecs);
          showWarning("info", "⏱ Phiên được gia hạn! Kết thúc lúc "
              + newEnd.format(DT_FMT));
        }
      } catch (Exception e) {
        System.err.println("[DETAIL] Lỗi TIME_EXTENDED: " + e.getMessage());
      }
    };

    onBidHistory = msg -> {
      if (!(msg.getPayload() instanceof java.util.List<?> list)) return;
      @SuppressWarnings("unchecked")
      var rows = (java.util.List<BidRow>) list;
      listBidHistory.getItems().setAll(rows);
      if (lblBidders != null) lblBidders.setText(rows.size() + " người");
    };

    onCancelSuccess = msg -> {
      AlertUtils.success("Đã huỷ phiên thành công!");
      cleanupHandlers();
      onBack();
    };

    onCancelFailed = msg -> {
      String reason = msg.getMessage() != null ? msg.getMessage() : "Vui lòng thử lại.";
      AlertUtils.error("Huỷ thất bại: " + reason);
      if (btnCancelAuction != null) btnCancelAuction.setDisable(false);
    };

    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.NEW_BID_UPDATE,        onNewBid);
    r.register(ResponseCode.AUCTION_ENDED,         onAuctionEnded);
    r.register(ResponseCode.AUCTION_TIME_EXTENDED, onTimeExtended);
    r.register(ResponseCode.BID_HISTORY_RESULT,    onBidHistory);
    r.register(ResponseCode.SELLER_CANCEL_SUCCESS, onCancelSuccess);
    r.register(ResponseCode.SELLER_CANCEL_FAILED,  onCancelFailed);
  }

  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.NEW_BID_UPDATE);
    r.unregister(ResponseCode.AUCTION_ENDED);
    r.unregister(ResponseCode.AUCTION_TIME_EXTENDED);
    r.unregister(ResponseCode.BID_HISTORY_RESULT);
    r.unregister(ResponseCode.SELLER_CANCEL_SUCCESS);
    r.unregister(ResponseCode.SELLER_CANCEL_FAILED);
  }

  // ════════════════════════════════════════
  // FILL DỮ LIỆU TĨNH
  // ════════════════════════════════════════

  private void fillHeader() {
    lblAuctionTitle   .setText(currentItem.getName());
    lblAuctionSubtitle.setText("Phiên #A" + String.format("%04d", currentAuction.getAuctionId())
        + " · " + formatCreatedAt());
    applyStatusPill(currentAuction.getAuctionStatus());
  }

  private void fillProductCard() {
    lblProductName  .setText(currentItem.getName());
    lblItemMeta     .setText(currentItem.getItemType()
        + (currentItem.getItemCondition() != null
        ? " · " + currentItem.getItemCondition() : ""));
    lblItemDesc     .setText(currentItem.getDescription() != null
        ? currentItem.getDescription() : "");
    lblStartingPrice.setText("Khởi điểm: "
        + CardUtils.formatMoney(currentAuction.getStartingPrice()) + "đ");
    lblCurrentPrice .setText(CardUtils.formatMoney(currentAuction.getCurrentPrice()) + "đ");
    lblBidCount     .setText(currentAuction.getTotalBids() + " lượt bid");
    String priceColor = "PAID".equals(currentAuction.getAuctionStatus()) ? "#43A047" : "#D7A859";
    lblCurrentPrice.setStyle("-fx-font-size:18;-fx-font-weight:bold;-fx-text-fill:" + priceColor + ";");
    CardUtils.loadImage(imgProduct, currentItem.getImgItem());
  }

  private void fillInfoGrid() {
    lblStartTime.setText(currentAuction.getStartTime() != null
        ? currentAuction.getStartTime().format(DT_FMT) : "—");
    lblEndTime  .setText(currentAuction.getEndTime() != null
        ? currentAuction.getEndTime().format(DT_FMT) : "—");
    lblTotalBids.setText(String.valueOf(currentAuction.getTotalBids()));
    lblIncrement.setText("—");
    if (lblBidders != null) lblBidders.setText("...");
  }

  // ════════════════════════════════════════
  // SETUP THEO TRẠNG THÁI
  // ════════════════════════════════════════

  private void setupPendingView() {
    lblDurationLabel.setText("THỜI LƯỢNG DỰ KIẾN");
    lblDuration.setText(calcDuration());
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#1565C0;");
    showWarning("warning", "⚠ Phiên đang chờ admin phê duyệt. Bạn vẫn có thể sửa hoặc huỷ phiên.");
    showButton(btnEdit);
    showButton(btnCancelAuction);
  }

  private void setupOpenView() {
    lblDurationLabel.setText("CÒN LẠI");
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#1565C0;");
    showWarning("info", "ℹ Phiên đã được kích hoạt. Đang chờ bidder tham gia.");
    showSection(sectionTimer);
    lblTimerLabel.setText("THỜI GIAN CÒN LẠI");
    lblEndTimeShort.setText(currentAuction.getEndTime().format(SHORT_FMT));
    startCountdown(currentAuction.getEndTime(),
        ChronoUnit.SECONDS.between(currentAuction.getStartTime(), currentAuction.getEndTime()));
    showButton(btnViewDetail);
    showButton(btnCancelAuction);
  }

  private void setupRunningView() {
    lblDurationLabel.setText("CÒN LẠI");
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#E65100;");
    showWarning("success", "🔔 Phiên đang diễn ra! " + currentAuction.getTotalBids() + " lượt bid.");
    showSection(sectionTimer);
    lblTimerLabel.setText("⏱ THỜI GIAN CÒN LẠI");
    lblEndTimeShort.setText(currentAuction.getEndTime().format(SHORT_FMT));
    startCountdown(currentAuction.getEndTime(),
        ChronoUnit.SECONDS.between(currentAuction.getStartTime(), currentAuction.getEndTime()));
    showSection(sectionBidHistory);
    loadBidHistory();
    showButton(btnFollow);
    showButton(btnViewChart);
  }

  private void setupFinishedView(boolean isPaid) {
    lblDurationLabel.setText("THỜI GIAN CHẠY");
    lblDuration.setText(calcDuration());
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#1A2B4A;");
    showSection(sectionResult);
    showSection(bannerWinner);
    hideSection(bannerCanceled);
    loadWinnerInfo();

    double startP = currentAuction.getStartingPrice(), endP = currentAuction.getCurrentPrice();
    if (startP > 0) {
      double pct = ((endP - startP) / startP) * 100;
      if (lblPriceIncrease != null)
        lblPriceIncrease.setText(String.format("+%.1f%% so với khởi điểm", pct));
    }
    showButton(btnViewChart);
    showButton(btnExportReport);
    if (!isPaid) showButton(btnRemindPayment);
    showButton(btnContactWinner);
    showWarning(isPaid ? "success" : "warning",
        isPaid ? "✓ Phiên đã hoàn tất và được thanh toán."
            : "⏳ Phiên đã kết thúc. Người thắng chưa thanh toán.");
  }

  private void setupCanceledView() {
    lblDurationLabel.setText("TRẠNG THÁI");
    lblDuration.setText("Đã huỷ");
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#B71C1C;");
    showSection(sectionResult);
    hideSection(bannerWinner);
    showSection(bannerCanceled);
    if (lblCancelReason != null)
      lblCancelReason.setText("Phiên không có người tham gia hoặc bị huỷ bởi seller.");
    showWarning("danger", "✗ Phiên đấu giá đã bị huỷ.");
    showButton(btnExportReport);
  }

  // ════════════════════════════════════════
  // LOAD DỮ LIỆU QUA NETWORK (thay vì DAO)
  // ════════════════════════════════════════

  private void loadBidHistory() {
    // Server nhận FETCH_BID_HISTORY (payload: Integer auctionId)
    // trả về BID_HISTORY_RESULT (payload: List<BidRow>)
    SocketClient.getInstance().sendRequest(
        RequestCode.FETCH_BID_HISTORY, currentAuction.getAuctionId());
  }

  private void loadWinnerInfo() {
    Integer winnerId = currentAuction.getCurrentWinnerId();
    if (winnerId == null || winnerId == 0) {
      if (lblWinner      != null) lblWinner.setText("Không có người thắng");
      if (lblWinnerPrice != null) lblWinnerPrice.setText("—");
      if (lblWinnerTime  != null) lblWinnerTime .setText("—");
      return;
    }
    if (lblWinner      != null) lblWinner.setText("User #" + winnerId);
    if (lblWinnerPrice != null) lblWinnerPrice.setText(
        CardUtils.formatMoney(currentAuction.getCurrentPrice()) + "đ");
    if (lblWinnerTime  != null) lblWinnerTime.setText(
        currentAuction.getEndTime() != null
            ? "Kết thúc lúc " + currentAuction.getEndTime().format(DT_FMT) : "—");
    // Nếu muốn tên username: server cần trả về trong SELLER_AUCTIONS_RESULT hoặc
    // thêm 1 request riêng GET_PROFILE với userId
  }

  // ════════════════════════════════════════
  // COUNTDOWN TIMER
  // ════════════════════════════════════════

  private void startCountdown(LocalDateTime endTime, long totalDurationSecs) {
    if (countdownTimer != null) countdownTimer.stop();
    countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      long remaining = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
      if (remaining <= 0) {
        if (lblTimerDisplay != null) lblTimerDisplay.setText("00 : 00 : 00");
        if (barProgress != null) barProgress.setProgress(1.0);
        countdownTimer.stop();
        return;
      }
      String display = String.format("%02d : %02d : %02d",
          remaining / 3600, (remaining % 3600) / 60, remaining % 60);
      if (lblTimerDisplay != null) lblTimerDisplay.setText(display);
      if (lblDuration     != null) lblDuration    .setText(display);
      if (barProgress != null && totalDurationSecs > 0)
        barProgress.setProgress(
            Math.min((double)(totalDurationSecs - remaining) / totalDurationSecs, 1.0));
      if (remaining < 3600 && lblTimerDisplay != null)
        lblTimerDisplay.setStyle("-fx-font-size:22;-fx-font-weight:bold;-fx-text-fill:#E65100;");
    }));
    countdownTimer.setCycleCount(Animation.INDEFINITE);
    countdownTimer.play();
  }

  public void stopTimer() {
    if (countdownTimer != null) countdownTimer.stop();
  }

  // ════════════════════════════════════════
  // ACTION HANDLERS
  // ════════════════════════════════════════

  @FXML private void onBack() {
    stopTimer();
    cleanupHandlers();
    try { ((javafx.stage.Stage) lblAuctionTitle.getScene().getWindow()).close(); }
    catch (Exception ignored) {}
  }

  @FXML private void onEdit() {
    AlertUtils.info("Chức năng sửa phiên đang được phát triển.");
  }

  @FXML
  private void onCancelAuction() {
    boolean confirmed = AlertUtils.confirm(
        "Xác nhận huỷ phiên",
        "Bạn chắc chắn muốn huỷ phiên đấu giá này?",
        "Sản phẩm: " + currentItem.getName());
    if (!confirmed) return;

    // Disable nút tránh double-click trong lúc chờ server
    if (btnCancelAuction != null) btnCancelAuction.setDisable(true);

    // Gửi yêu cầu huỷ qua network — SELLER_CANCEL_SUCCESS/FAILED sẽ xử lý tiếp
    SocketClient.getInstance().sendRequest(
        RequestCode.SELLER_CANCEL_AUCTION, currentAuction.getAuctionId());
  }

  @FXML private void onViewDetail() {
    AlertUtils.info("Sản phẩm: " + currentItem.getName()
        + "\n" + (currentItem.getDescription() != null ? currentItem.getDescription() : ""));
  }

  @FXML private void onFollow() {
    // JOIN_ROOM để nhận realtime bid (nếu chưa join)
    SocketClient.getInstance().sendRequest(
        RequestCode.JOIN_ROOM, currentAuction.getAuctionId());
    AlertUtils.info("Đã bắt đầu theo dõi phiên #" + currentAuction.getAuctionId());
  }

  @FXML private void onViewChart()    { AlertUtils.info("Biểu đồ giá đang phát triển."); }
  @FXML private void onExportReport() { AlertUtils.info("Xuất báo cáo đang phát triển."); }
  @FXML private void onRemindPayment() { AlertUtils.success("Đã gửi nhắc thanh toán tới người thắng!"); }
  @FXML private void onContactWinner() {
    if (currentAuction.getCurrentWinnerId() == null) return;
    AlertUtils.info("Mở kênh liên hệ với người thắng #" + currentAuction.getCurrentWinnerId());
  }

  // ── NAV ──
  @FXML private void onNavHome()     { navigateTo("/view/The_Home_Page_Seller_View.fxml"); }
  @FXML private void onNavProducts() { navigateTo("/view/MyProductsView.fxml"); }
  @FXML private void onNavAuction()  { onBack(); }
  @FXML private void onNavAccount()  { navigateTo("/view/AccountView.fxml"); }

  private void navigateTo(String fxmlPath) {
    stopTimer();
    cleanupHandlers();
    try {
      javafx.fxml.FXMLLoader loader =
          new javafx.fxml.FXMLLoader(getClass().getResource(fxmlPath));
      javafx.scene.Parent root = loader.load();
      ((javafx.stage.Stage) lblAuctionTitle.getScene().getWindow())
          .setScene(new javafx.scene.Scene(root));
    } catch (Exception e) {
      AlertUtils.error("Không thể chuyển trang: " + e.getMessage());
    }
  }

  // ════════════════════════════════════════
  // STATUS PILL
  // ════════════════════════════════════════

  private void applyStatusPill(String status) {
    String text, bg, fg;
    switch (status) {
      case "WAITING_FOR_ADMIN" -> { text = "CHỜ DUYỆT"; bg = "#FFF8E1"; fg = "#F57F17"; }
      case "OPEN"              -> { text = "OPEN";       bg = "#E3F2FD"; fg = "#1565C0"; }
      case "RUNNING"           -> { text = "🟢 RUNNING"; bg = "#E8F5E9"; fg = "#1B5E20"; }
      case "FINISHED"          -> { text = "FINISHED";   bg = "#FFF3E0"; fg = "#E65100"; }
      case "PAID"              -> { text = "✓ PAID";     bg = "#E8F5E9"; fg = "#2E7D32"; }
      case "CANCELED"          -> { text = "CANCELED";   bg = "#FFEBEE"; fg = "#B71C1C"; }
      default                  -> { text = status;       bg = "#F5F0E8"; fg = "#6B5E4A"; }
    }
    lblStatusPill.setText(text);
    lblStatusPill.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";"
        + "-fx-font-size:11;-fx-font-weight:bold;-fx-padding:4 12 4 12;-fx-background-radius:99;");
  }

  // ════════════════════════════════════════
  // WARNING BOX
  // ════════════════════════════════════════

  private void showWarning(String type, String message) {
    if (sectionWarningInfo == null) return;
    String bg, border, fg, icon;
    switch (type) {
      case "warning" -> { bg = "#FFF8E1"; border = "#FFE082"; fg = "#7B5800"; icon = "⚠"; }
      case "info"    -> { bg = "#E8EEF7"; border = "#B8CCE8"; fg = "#1A2B4A"; icon = "ℹ"; }
      case "success" -> { bg = "#E8F5E9"; border = "#A5D6A7"; fg = "#1B5E20"; icon = "✓"; }
      case "danger"  -> { bg = "#FFEBEE"; border = "#FFCDD2"; fg = "#B71C1C"; icon = "✗"; }
      default        -> { bg = "#FFF8E1"; border = "#FFE082"; fg = "#7B5800"; icon = "ℹ"; }
    }
    sectionWarningInfo.setStyle("-fx-background-color:" + bg + ";-fx-border-color:" + border
        + ";-fx-border-width:0.5;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:10 14 10 14;");
    if (lblWarningIcon != null) {
      lblWarningIcon.setText(icon);
      lblWarningIcon.setStyle("-fx-text-fill:" + fg + ";-fx-font-size:14;-fx-font-weight:bold;");
    }
    if (lblWarningText != null) {
      lblWarningText.setText(message);
      lblWarningText.setStyle("-fx-font-size:11;-fx-text-fill:" + fg + ";");
    }
    showSection(sectionWarningInfo);
  }

  // ════════════════════════════════════════
  // BID HISTORY CELL FACTORY
  // ════════════════════════════════════════

  private void setupBidHistoryCellFactory() {
    if (listBidHistory == null) return;
    listBidHistory.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(BidRow row, boolean empty) {
        super.updateItem(row, empty);
        if (empty || row == null) { setGraphic(null); return; }

        Label rankBadge = new Label(String.valueOf(row.rank()));
        rankBadge.setMinSize(22, 22); rankBadge.setPrefSize(22, 22);
        rankBadge.setAlignment(Pos.CENTER);
        rankBadge.setStyle(row.rank() == 1
            ? "-fx-background-color:#D7A859;-fx-text-fill:#3A2408;-fx-font-size:10;-fx-font-weight:bold;-fx-background-radius:99;"
            : "-fx-background-color:#F0EBE0;-fx-text-fill:#8B7355;-fx-font-size:10;-fx-font-weight:bold;-fx-background-radius:99;");

        Label lblUser = new Label(row.username());
        lblUser.setStyle(row.rank() == 1
            ? "-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#1A2B4A;"
            : "-fx-font-size:12;-fx-text-fill:#3A2E22;");
        javafx.scene.layout.HBox.setHgrow(lblUser, javafx.scene.layout.Priority.ALWAYS);

        Label lblTime = new Label(row.bidTime());
        lblTime.setStyle("-fx-font-size:10.5;-fx-text-fill:#A08C6E;");

        Label lblAmt = new Label(CardUtils.formatMoney(row.amount()) + "đ");
        lblAmt.setStyle(row.rank() == 1
            ? "-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#D7A859;"
            : "-fx-font-size:12;-fx-text-fill:#8B7355;");

        HBox cell = new HBox(10, rankBadge, lblUser, lblTime, lblAmt);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setStyle("-fx-padding:6 0 6 0;-fx-border-color:transparent transparent #F5F0E8 transparent;-fx-border-width:0 0 0.5 0;");
        setGraphic(cell);
        setStyle("-fx-background-color:transparent;-fx-padding:0;");
      }
    });
  }

  // ════════════════════════════════════════
  // SHOW / HIDE HELPERS
  // ════════════════════════════════════════

  private void hideAllSections() {
    for (var node : new javafx.scene.Node[]{
        sectionWarningInfo, sectionTimer, sectionBidHistory,
        sectionResult, bannerWinner, bannerCanceled
    }) hideSection(node);
  }

  private void hideAllButtons() {
    for (Button b : new Button[]{btnEdit, btnCancelAuction, btnViewDetail,
        btnFollow, btnViewChart, btnExportReport, btnRemindPayment, btnContactWinner})
      CardUtils.setVisible(b, false);
  }

  private void showSection(Node node) { CardUtils.setVisible(node, true); }
  private void hideSection(Node node) { CardUtils.setVisible(node, false); }
  private void showButton(Button btn)              { CardUtils.setVisible(btn, true); }

  // ════════════════════════════════════════
  // HELPERS
  // ════════════════════════════════════════

  private String calcDuration() {
    if (currentAuction.getStartTime() == null || currentAuction.getEndTime() == null) return "—";
    long days  = ChronoUnit.DAYS .between(currentAuction.getStartTime(), currentAuction.getEndTime());
    long hours = ChronoUnit.HOURS.between(currentAuction.getStartTime(), currentAuction.getEndTime()) % 24;
    return days > 0 ? days + " ngày " + hours + " giờ" : hours + " giờ";
  }

  private String formatCreatedAt() {
    if (currentAuction.getCreatedAt()  != null) return "Tạo lúc "   + currentAuction.getCreatedAt() .format(DT_FMT);
    if (currentAuction.getStartTime()  != null) return "Bắt đầu "   + currentAuction.getStartTime() .format(DT_FMT);
    return "—";
  }
}