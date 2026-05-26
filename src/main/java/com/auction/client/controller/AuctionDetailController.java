package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.AuctionItemDAO;
import com.auction.server.dao.DBConnection;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller cho AuctionDetailView.fxml
 *
 * Dùng chung cho MỌI trạng thái phiên:
 *   WAITING_FOR_ADMIN → Chờ duyệt
 *   OPEN              → Đang mở, chờ bidder
 *   RUNNING           → Đang diễn ra, có bid
 *   FINISHED          → Kết thúc, chờ thanh toán
 *   PAID              → Đã thanh toán
 *   CANCELED          → Đã huỷ
 *
 * Cách dùng từ AuctionManagementController:
 *   AuctionDetailController ctrl = loader.getController();
 *   ctrl.setAuctionItem(auctionItemDAO);
 */
public class AuctionDetailController {

  // ══════════════════════════════════════════════════
  // FXML — HEADER
  // ══════════════════════════════════════════════════
  @FXML private Label lblAuctionTitle;
  @FXML private Label lblAuctionSubtitle;
  @FXML private Label lblStatusPill;

  // ══════════════════════════════════════════════════
  // FXML — WARNING / INFO BOX
  // ══════════════════════════════════════════════════
  @FXML private HBox  sectionWarningInfo;
  @FXML private Label lblWarningIcon;
  @FXML private Label lblWarningText;

  // ══════════════════════════════════════════════════
  // FXML — PRODUCT CARD
  // ══════════════════════════════════════════════════
  @FXML private ImageView imgProduct;
  @FXML private Label     lblProductName;
  @FXML private Label     lblItemMeta;
  @FXML private Label     lblItemDesc;
  @FXML private Label     lblStartingPrice;
  @FXML private Label     lblCurrentPrice;
  @FXML private Label     lblBidCount;

  // ══════════════════════════════════════════════════
  // FXML — INFO GRID (6 ô)
  // ══════════════════════════════════════════════════
  @FXML private Label lblStartTime;
  @FXML private Label lblEndTime;
  @FXML private Label lblDurationLabel; // "THỜI LƯỢNG" hoặc "CÒN LẠI"
  @FXML private Label lblDuration;
  @FXML private Label lblTotalBids;
  @FXML private Label lblBidders;
  @FXML private Label lblIncrement;

  // ══════════════════════════════════════════════════
  // FXML — TIMER BAR (OPEN + RUNNING)
  // ══════════════════════════════════════════════════
  @FXML private HBox        sectionTimer;
  @FXML private Label       lblTimerLabel;
  @FXML private Label       lblTimerDisplay;
  @FXML private ProgressBar barProgress;
  @FXML private Label       lblEndTimeShort;

  // ══════════════════════════════════════════════════
  // FXML — BID HISTORY (RUNNING)
  // ══════════════════════════════════════════════════
  @FXML private VBox                 sectionBidHistory;
  @FXML private ListView<BidRow>     listBidHistory;

  // ══════════════════════════════════════════════════
  // FXML — RESULT CARD (FINISHED / PAID / CANCELED)
  // ══════════════════════════════════════════════════
  @FXML private VBox  sectionResult;
  @FXML private HBox  bannerWinner;
  @FXML private Label lblResultIcon;
  @FXML private Label lblWinner;
  @FXML private Label lblWinnerTime;
  @FXML private Label lblWinnerPrice;
  @FXML private Label lblPriceIncrease;
  @FXML private HBox  bannerCanceled;
  @FXML private Label lblCancelReason;

  // ══════════════════════════════════════════════════
  // FXML — ACTION BUTTONS
  // ══════════════════════════════════════════════════
  // PENDING
  @FXML private Button btnEdit;
  @FXML private Button btnCancelAuction;
  // OPEN / RUNNING
  @FXML private Button btnViewDetail;
  @FXML private Button btnFollow;
  @FXML private Button btnViewChart;
  // FINISHED / PAID
  @FXML private Button btnExportReport;
  @FXML private Button btnRemindPayment;
  @FXML private Button btnContactWinner;

  // ══════════════════════════════════════════════════
  // DAO & STATE
  // ══════════════════════════════════════════════════
  private final AuctionDAO auctionDAO = new AuctionDAO();

  private AuctionItemDAO currentAuctionItem;
  private Auction        currentAuction;
  private Item           currentItem;

  private Timeline countdownTimer;

  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final DateTimeFormatter SHORT_FMT =
      DateTimeFormatter.ofPattern("dd/MM · HH:mm");

  // ══════════════════════════════════════════════════
  // INNER CLASS — dữ liệu 1 dòng bid history
  // ══════════════════════════════════════════════════
  public static class BidRow {
    public final int    rank;
    public final String username;
    public final double amount;
    public final String bidTime;
    public BidRow(int rank, String username, double amount, String bidTime) {
      this.rank = rank; this.username = username;
      this.amount = amount; this.bidTime = bidTime;
    }
  }

  // ══════════════════════════════════════════════════
  // INITIALIZE
  // ══════════════════════════════════════════════════
  @FXML
  public void initialize() {
    setupBidHistoryCellFactory();
  }

  // ══════════════════════════════════════════════════
  // SETTER — gọi từ AuctionManagementController
  // ══════════════════════════════════════════════════
  public void setAuctionItem(AuctionItemDAO auctionItem) {
    this.currentAuctionItem = auctionItem;
    this.currentAuction     = auctionItem.getAuction();
    this.currentItem        = auctionItem.getItem();

    // Ẩn tất cả section trước, rồi bật theo status
    hideAllSections();
    hideAllButtons();

    // Điền dữ liệu chung
    fillHeader();
    fillProductCard();
    fillInfoGrid();

    // Điền dữ liệu theo từng trạng thái
    String status = currentAuction.getAuctionStatus();
    switch (status) {
      case "WAITING_FOR_ADMIN" -> setupPendingView();
      case "OPEN"              -> setupOpenView();
      case "RUNNING"           -> setupRunningView();
      case "FINISHED"          -> setupFinishedView(false);
      case "PAID"              -> setupFinishedView(true);
      case "CANCELED"          -> setupCanceledView();
      default                  -> setupPendingView();
    }
  }

  // ══════════════════════════════════════════════════
  // FILL CHUNG
  // ══════════════════════════════════════════════════
  private void fillHeader() {
    lblAuctionTitle   .setText(currentItem.getName());
    lblAuctionSubtitle.setText("Phiên #A" + String.format("%04d", currentAuction.getAuctionId())
        + " · " + formatCreatedAt());
    applyStatusPill(currentAuction.getAuctionStatus());
  }

  private void fillProductCard() {
    lblProductName   .setText(currentItem.getName());
    lblItemMeta      .setText(currentItem.getItemType()
        + (currentItem.getItemCondition() != null
        ? " · " + currentItem.getItemCondition() : ""));
    lblItemDesc      .setText(nvl(currentItem.getDescription()));
    lblStartingPrice .setText("Khởi điểm: "
        + formatMoney(currentAuction.getStartingPrice()) + "đ");
    lblCurrentPrice  .setText(formatMoney(currentAuction.getCurrentPrice()) + "đ");
    lblBidCount      .setText(currentAuction.getTotalBids() + " lượt bid");

    // Màu giá: xanh nếu PAID, vàng nếu đang chạy
    String priceColor = "PAID".equals(currentAuction.getAuctionStatus())
        ? "#43A047" : "#D7A859";
    lblCurrentPrice.setStyle("-fx-font-size:18;-fx-font-weight:bold;-fx-text-fill:" + priceColor + ";");

    // Load ảnh
    if (currentItem.getImgItem() != null && !currentItem.getImgItem().isEmpty()) {
      try {
        imgProduct.setImage(new Image(currentItem.getImgItem(), true));
      } catch (Exception ignored) {}
    }
  }

  private void fillInfoGrid() {
    lblStartTime .setText(currentAuction.getStartTime() != null
        ? currentAuction.getStartTime().format(DT_FMT) : "—");
    lblEndTime   .setText(currentAuction.getEndTime() != null
        ? currentAuction.getEndTime().format(DT_FMT) : "—");
    lblTotalBids .setText(String.valueOf(currentAuction.getTotalBids()));
    lblIncrement .setText("—"); // TODO: set từ DB nếu có cột min_increment

    // Số bidder — đếm từ DB
    loadBidderCount();
  }

  private void loadBidderCount() {
    new Thread(() -> {
      int count = countBidders(currentAuction.getAuctionId());
      Platform.runLater(() -> {
        if (lblBidders != null) lblBidders.setText(count + " người");
      });
    }).start();
  }

  // ══════════════════════════════════════════════════
  // SETUP THEO TỪNG TRẠNG THÁI
  // ══════════════════════════════════════════════════

  /** WAITING_FOR_ADMIN — hiện infobox vàng + nút Sửa + Huỷ */
  private void setupPendingView() {
    // Thời lượng
    lblDurationLabel.setText("THỜI LƯỢNG DỰ KIẾN");
    lblDuration.setText(calcDuration());
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#1565C0;");

    // Info box
    showWarning("warning",
        "⚠ Phiên đang chờ admin phê duyệt. Bạn vẫn có thể sửa hoặc huỷ phiên.");

    // Nút
    showButton(btnEdit);
    showButton(btnCancelAuction);
  }

  /** OPEN — hiện timer + nút Chi tiết */
  private void setupOpenView() {
    lblDurationLabel.setText("CÒN LẠI");
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#1565C0;");

    // Info box
    showWarning("info",
        "ℹ Phiên đã được kích hoạt. Đang chờ bidder tham gia. "
            + "Khi có bid đầu tiên, phiên chuyển sang RUNNING.");

    // Timer bar
    showSection(sectionTimer);
    lblTimerLabel.setText("THỜI GIAN CÒN LẠI");
    lblEndTimeShort.setText(currentAuction.getEndTime().format(SHORT_FMT));
    startCountdown(currentAuction.getEndTime(),
        ChronoUnit.SECONDS.between(currentAuction.getStartTime(),
            currentAuction.getEndTime()));

    // Nút
    showButton(btnViewDetail);
    showButton(btnCancelAuction);
  }

  /** RUNNING — timer đỏ cam + bid history + Theo dõi */
  private void setupRunningView() {
    lblDurationLabel.setText("CÒN LẠI");
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#E65100;");

    // Info box
    showWarning("success",
        "🔔 Phiên đang diễn ra trực tiếp! "
            + currentAuction.getTotalBids() + " lượt bid.");

    // Timer bar
    showSection(sectionTimer);
    lblTimerLabel.setText("⏱ THỜI GIAN CÒN LẠI");
    lblEndTimeShort.setText(currentAuction.getEndTime().format(SHORT_FMT));
    long totalSecs = ChronoUnit.SECONDS.between(
        currentAuction.getStartTime(), currentAuction.getEndTime());
    startCountdown(currentAuction.getEndTime(), totalSecs);

    // Bid history
    showSection(sectionBidHistory);
    loadBidHistory();

    // Nút
    showButton(btnFollow);
    showButton(btnViewChart);
  }

  /** FINISHED hoặc PAID — result card */
  private void setupFinishedView(boolean isPaid) {
    lblDurationLabel.setText("THỜI GIAN CHẠY");
    lblDuration.setText(calcActualDuration());
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#1A2B4A;");

    // Result section
    showSection(sectionResult);
    showSection(bannerWinner);
    hideSection(bannerCanceled);

    // Lấy thông tin người thắng từ DB
    loadWinnerInfo(isPaid);

    // Tăng giá %
    double startP = currentAuction.getStartingPrice();
    double endP   = currentAuction.getCurrentPrice();
    if (startP > 0) {
      double pct = ((endP - startP) / startP) * 100;
      lblPriceIncrease.setText(String.format("+%.1f%% so với khởi điểm", pct));
    }

    // Nút
    showButton(btnViewChart);
    showButton(btnExportReport);
    if (!isPaid) showButton(btnRemindPayment);
    showButton(btnContactWinner);

    // Info box
    showWarning(isPaid ? "success" : "warning",
        isPaid ? "✓ Phiên đã hoàn tất và được thanh toán."
            : "⏳ Phiên đã kết thúc. Người thắng chưa thanh toán.");
  }

  /** CANCELED */
  private void setupCanceledView() {
    lblDurationLabel.setText("TRẠNG THÁI");
    lblDuration.setText("Đã huỷ");
    lblDuration.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:#B71C1C;");

    showSection(sectionResult);
    hideSection(bannerWinner);
    showSection(bannerCanceled);
    lblCancelReason.setText("Phiên không có người tham gia hoặc bị huỷ bởi seller.");

    showWarning("danger", "✗ Phiên đấu giá đã bị huỷ.");
    showButton(btnExportReport);
  }

  // ══════════════════════════════════════════════════
  // COUNTDOWN TIMER
  // ══════════════════════════════════════════════════
  private void startCountdown(LocalDateTime endTime, long totalDurationSecs) {
    if (countdownTimer != null) countdownTimer.stop();

    countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      long remaining = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
      if (remaining <= 0) {
        lblTimerDisplay.setText("00 : 00 : 00");
        if (barProgress != null) barProgress.setProgress(1.0);
        countdownTimer.stop();
        return;
      }

      long h = remaining / 3600;
      long m = (remaining % 3600) / 60;
      long s = remaining % 60;
      String display = String.format("%02d : %02d : %02d", h, m, s);
      lblTimerDisplay.setText(display);
      lblDuration.setText(display);

      // Progress bar
      if (barProgress != null && totalDurationSecs > 0) {
        double elapsed = totalDurationSecs - remaining;
        barProgress.setProgress(Math.min(elapsed / totalDurationSecs, 1.0));
      }

      // Đổi màu khi gần hết giờ (< 1 giờ)
      if (remaining < 3600) {
        lblTimerDisplay.setStyle(
            "-fx-font-size:22;-fx-font-weight:bold;-fx-text-fill:#E65100;");
      }
    }));
    countdownTimer.setCycleCount(Animation.INDEFINITE);
    countdownTimer.play();
  }

  /** Dừng timer khi đóng màn hình (tránh memory leak) */
  public void stopTimer() {
    if (countdownTimer != null) countdownTimer.stop();
  }

  // ══════════════════════════════════════════════════
  // LOAD DỮ LIỆU TỪ DB
  // ══════════════════════════════════════════════════

  /** Lấy lịch sử bid từ bảng bids (nếu có) hoặc tính từ transactions */
  private void loadBidHistory() {
    new Thread(() -> {
      List<BidRow> rows = queryBidHistory(currentAuction.getAuctionId());
      Platform.runLater(() -> {
        listBidHistory.getItems().setAll(rows);
      });
    }).start();
  }

  private List<BidRow> queryBidHistory(int auctionId) {
    List<BidRow> list = new ArrayList<>();
    // Thử lấy từ bảng bids trước (nếu bạn có bảng này)
    String sql = "SELECT u.username, b.bid_amount, b.bid_time " +
        "FROM bids b " +
        "JOIN users u ON b.bidder_id = u.user_id " +
        "WHERE b.auction_id = ? " +
        "ORDER BY b.bid_amount DESC " +
        "LIMIT 20";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        int rank = 1;
        while (rs.next()) {
          Timestamp ts = rs.getTimestamp("bid_time");
          String timeStr = ts != null
              ? ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
              : "—";
          list.add(new BidRow(
              rank++,
              rs.getString("username"),
              rs.getDouble("bid_amount"),
              timeStr
          ));
        }
      }
    } catch (SQLException e) {
      // Bảng bids chưa có → thử fallback từ transactions
      list.addAll(queryBidHistoryFromTransactions(auctionId));
    }
    return list;
  }

  /** Fallback: lấy bid history từ bảng transactions */
  private List<BidRow> queryBidHistoryFromTransactions(int auctionId) {
    List<BidRow> list = new ArrayList<>();
    String sql = "SELECT u.username, t.amount, t.created_at " +
        "FROM transactions t " +
        "JOIN users u ON t.user_id = u.user_id " +
        "WHERE t.transaction_type = ? AND t.status = 'SUCCESS' " +
        "ORDER BY t.amount DESC LIMIT 20";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, "BID_AUCTION_" + auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        int rank = 1;
        while (rs.next()) {
          Timestamp ts = rs.getTimestamp("created_at");
          String timeStr = ts != null
              ? ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
              : "—";
          list.add(new BidRow(
              rank++,
              rs.getString("username"),
              rs.getDouble("amount"),
              timeStr
          ));
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return list;
  }

  /** Lấy thông tin người thắng từ DB */
  private void loadWinnerInfo(boolean isPaid) {
    if (currentAuction.getCurrentWinnerId() == null
        || currentAuction.getCurrentWinnerId() == 0) {
      lblWinner     .setText("Không có người thắng");
      lblWinnerPrice.setText("—");
      lblWinnerTime .setText("—");
      return;
    }

    new Thread(() -> {
      String username = queryUsername(currentAuction.getCurrentWinnerId());
      String winTime  = queryWinTime(currentAuction.getAuctionId());
      Platform.runLater(() -> {
        lblWinner     .setText(username != null ? username : "—");
        lblWinnerPrice.setText(formatMoney(currentAuction.getCurrentPrice()) + "đ");
        lblWinnerTime .setText("Bid thắng lúc " + winTime);
      });
    }).start();
  }

  private String queryUsername(int userId) {
    String sql = "SELECT username FROM users WHERE user_id = ?";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getString("username");
      }
    } catch (SQLException e) { e.printStackTrace(); }
    return null;
  }

  private String queryWinTime(int auctionId) {
    // Lấy từ bảng bids nếu có
    String sql = "SELECT bid_time FROM bids " +
        "WHERE auction_id = ? ORDER BY bid_amount DESC LIMIT 1";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          Timestamp ts = rs.getTimestamp("bid_time");
          if (ts != null) return ts.toLocalDateTime().format(
              DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
        }
      }
    } catch (SQLException e) { /* Bảng không có */ }
    // Fallback: dùng end_time
    return currentAuction.getEndTime() != null
        ? currentAuction.getEndTime().format(DT_FMT) : "—";
  }

  private int countBidders(int auctionId) {
    String sql = "SELECT COUNT(DISTINCT bidder_id) AS cnt FROM bids WHERE auction_id = ?";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt("cnt");
      }
    } catch (SQLException e) {
      // fallback từ transactions
      return countBiddersFromTransactions(auctionId);
    }
    return 0;
  }

  private int countBiddersFromTransactions(int auctionId) {
    String sql = "SELECT COUNT(DISTINCT user_id) AS cnt FROM transactions " +
        "WHERE transaction_type = ? AND status = 'SUCCESS'";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, "BID_AUCTION_" + auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt("cnt");
      }
    } catch (SQLException e) { e.printStackTrace(); }
    return 0;
  }

  // ══════════════════════════════════════════════════
  // ACTION HANDLERS
  // ══════════════════════════════════════════════════

  @FXML
  private void onBack() {
    stopTimer();
    try {
      javafx.stage.Stage stage =
          (javafx.stage.Stage) lblAuctionTitle.getScene().getWindow();
      stage.close();
    } catch (Exception ignored) {}
  }

  @FXML
  private void onEdit() {
    showInfo("Chức năng sửa phiên đang được phát triển.");
  }

  @FXML
  private void onCancelAuction() {
    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Xác nhận huỷ phiên");
    confirm.setHeaderText("Bạn chắc chắn muốn huỷ phiên đấu giá này?");
    confirm.setContentText("Sản phẩm: " + currentItem.getName());
    confirm.showAndWait().ifPresent(btn -> {
      if (btn == ButtonType.OK) {
        boolean ok = auctionDAO.updateStatus(
            currentAuction.getAuctionId(), "CANCELED");
        if (ok) {
          currentAuction.setAuctionStatus("CANCELED");
          showSuccess("Đã huỷ phiên thành công!");
          onBack();
        } else {
          showError("Huỷ thất bại. Vui lòng thử lại.");
        }
      }
    });
  }

  @FXML
  private void onViewDetail() {
    showInfo("Xem chi tiết sản phẩm:\n"
        + currentItem.getName() + "\n"
        + currentItem.getDescription());
  }

  @FXML
  private void onFollow() {
    // TODO: Mở màn hình Bidding Live (realtime), đăng ký BidUpdateListener
    showInfo("Chức năng theo dõi live sẽ được bổ sung khi tích hợp Socket.");
  }

  @FXML
  private void onViewChart() {
    // TODO: Mở dialog biểu đồ LineChart giá theo thời gian
    showInfo("Chức năng biểu đồ giá đang được phát triển.");
  }

  @FXML
  private void onExportReport() {
    // TODO: Xuất PDF/Excel báo cáo phiên
    showInfo("Chức năng xuất báo cáo đang được phát triển.");
  }

  @FXML
  private void onRemindPayment() {
    // TODO: Gửi thông báo tới người thắng qua Socket/Observer
    showSuccess("Đã gửi nhắc thanh toán tới người thắng!");
  }

  @FXML
  private void onContactWinner() {
    if (currentAuction.getCurrentWinnerId() == null) return;
    showInfo("Mở kênh liên hệ với người thắng #"
        + currentAuction.getCurrentWinnerId() + ".");
  }

  // NAVBAR
  @FXML private void onNavHome()     { navigateBack("/view/The_Home_Page_Seller_View.fxml"); }
  @FXML private void onNavProducts() { navigateBack("/view/MyProductsView.fxml"); }
  @FXML private void onNavAuction()  { onBack(); }
  @FXML private void onNavAccount()  { navigateBack("/view/AccountView.fxml"); }

  private void navigateBack(String fxmlPath) {
    stopTimer();
    try {
      javafx.fxml.FXMLLoader loader =
          new javafx.fxml.FXMLLoader(getClass().getResource(fxmlPath));
      javafx.scene.Parent root = loader.load();
      javafx.stage.Stage stage =
          (javafx.stage.Stage) lblAuctionTitle.getScene().getWindow();
      stage.setScene(new javafx.scene.Scene(root));
    } catch (Exception e) {
      showError("Không thể chuyển trang: " + e.getMessage());
    }
  }

  // ══════════════════════════════════════════════════
  // STATUS PILL
  // ══════════════════════════════════════════════════
  private void applyStatusPill(String status) {
    String text, style;
    switch (status) {
      case "WAITING_FOR_ADMIN" -> {
        text = "CHỜ DUYỆT";
        style = "-fx-background-color:#FFF8E1;-fx-text-fill:#F57F17;";
      }
      case "OPEN" -> {
        text = "OPEN";
        style = "-fx-background-color:#E3F2FD;-fx-text-fill:#1565C0;";
      }
      case "RUNNING" -> {
        text = "🟢 RUNNING";
        style = "-fx-background-color:#E8F5E9;-fx-text-fill:#1B5E20;";
      }
      case "FINISHED" -> {
        text = "FINISHED";
        style = "-fx-background-color:#FFF3E0;-fx-text-fill:#E65100;";
      }
      case "PAID" -> {
        text = "✓ PAID";
        style = "-fx-background-color:#E8F5E9;-fx-text-fill:#2E7D32;";
      }
      case "CANCELED" -> {
        text = "CANCELED";
        style = "-fx-background-color:#FFEBEE;-fx-text-fill:#B71C1C;";
      }
      default -> {
        text = status;
        style = "-fx-background-color:#F5F0E8;-fx-text-fill:#6B5E4A;";
      }
    }
    lblStatusPill.setText(text);
    lblStatusPill.setStyle(style
        + "-fx-font-size:11;-fx-font-weight:bold;"
        + "-fx-padding:4 12 4 12;-fx-background-radius:99;");
  }

  // ══════════════════════════════════════════════════
  // INFO BOX
  // ══════════════════════════════════════════════════
  private void showWarning(String type, String message) {
    if (sectionWarningInfo == null) return;
    String bg, border, textColor, icon;
    switch (type) {
      case "warning" -> { bg="#FFF8E1"; border="#FFE082"; textColor="#7B5800"; icon="⚠"; }
      case "info"    -> { bg="#E8EEF7"; border="#B8CCE8"; textColor="#1A2B4A"; icon="ℹ"; }
      case "success" -> { bg="#E8F5E9"; border="#A5D6A7"; textColor="#1B5E20"; icon="✓"; }
      case "danger"  -> { bg="#FFEBEE"; border="#FFCDD2"; textColor="#B71C1C"; icon="✗"; }
      default        -> { bg="#FFF8E1"; border="#FFE082"; textColor="#7B5800"; icon="ℹ"; }
    }
    sectionWarningInfo.setStyle(
        "-fx-background-color:" + bg + ";" +
            "-fx-border-color:" + border + ";" +
            "-fx-border-width:0.5;-fx-border-radius:8;" +
            "-fx-background-radius:8;-fx-padding:10 14 10 14;");
    if (lblWarningIcon != null) {
      lblWarningIcon.setText(icon);
      lblWarningIcon.setStyle("-fx-text-fill:" + textColor + ";-fx-font-size:14;-fx-font-weight:bold;");
    }
    if (lblWarningText != null) {
      lblWarningText.setText(message);
      lblWarningText.setStyle("-fx-font-size:11;-fx-text-fill:" + textColor + ";");
    }
    showSection(sectionWarningInfo);
  }

  // ══════════════════════════════════════════════════
  // CELL FACTORY — BID HISTORY
  // ══════════════════════════════════════════════════
  private void setupBidHistoryCellFactory() {
    if (listBidHistory == null) return;
    listBidHistory.setCellFactory(lv -> new ListCell<BidRow>() {
      @Override
      protected void updateItem(BidRow row, boolean empty) {
        super.updateItem(row, empty);
        if (empty || row == null) { setGraphic(null); return; }

        // Rank badge
        Label rankBadge = new Label(String.valueOf(row.rank));
        rankBadge.setMinSize(22, 22);
        rankBadge.setPrefSize(22, 22);
        rankBadge.setAlignment(Pos.CENTER);
        rankBadge.setStyle(row.rank == 1
            ? "-fx-background-color:#D7A859;-fx-text-fill:#3A2408;" +
            "-fx-font-size:10;-fx-font-weight:bold;-fx-background-radius:99;"
            : "-fx-background-color:#F0EBE0;-fx-text-fill:#8B7355;" +
            "-fx-font-size:10;-fx-font-weight:bold;-fx-background-radius:99;");

        // Username
        Label lblUser = new Label(row.username);
        lblUser.setStyle(row.rank == 1
            ? "-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#1A2B4A;"
            : "-fx-font-size:12;-fx-text-fill:#3A2E22;");
        HBox.setHgrow(lblUser, javafx.scene.layout.Priority.ALWAYS);

        // Time
        Label lblTime = new Label(row.bidTime);
        lblTime.setStyle("-fx-font-size:10.5;-fx-text-fill:#A08C6E;");

        // Amount
        Label lblAmt = new Label(formatMoney(row.amount) + "đ");
        lblAmt.setStyle(row.rank == 1
            ? "-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#D7A859;"
            : "-fx-font-size:12;-fx-text-fill:#8B7355;");

        HBox cell = new HBox(10, rankBadge, lblUser, lblTime, lblAmt);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setStyle("-fx-padding: 6 0 6 0;" +
            "-fx-border-color: transparent transparent #F5F0E8 transparent;" +
            "-fx-border-width: 0 0 0.5 0;");
        setGraphic(cell);
        setStyle("-fx-background-color: transparent; -fx-padding: 0;");
      }
    });
  }

  // ══════════════════════════════════════════════════
  // SHOW / HIDE HELPERS
  // ══════════════════════════════════════════════════
  private void hideAllSections() {
    hideSection(sectionWarningInfo);
    hideSection(sectionTimer);
    hideSection(sectionBidHistory);
    hideSection(sectionResult);
    if (bannerWinner   != null) hideSection(bannerWinner);
    if (bannerCanceled != null) hideSection(bannerCanceled);
  }

  private void hideAllButtons() {
    for (Button b : new Button[]{
        btnEdit, btnCancelAuction, btnViewDetail, btnFollow,
        btnViewChart, btnExportReport, btnRemindPayment, btnContactWinner
    }) {
      if (b != null) { b.setVisible(false); b.setManaged(false); }
    }
  }

  private void showSection(javafx.scene.Node node) {
    if (node != null) { node.setVisible(true); node.setManaged(true); }
  }

  private void hideSection(javafx.scene.Node node) {
    if (node != null) { node.setVisible(false); node.setManaged(false); }
  }

  private void showButton(Button btn) {
    if (btn != null) { btn.setVisible(true); btn.setManaged(true); }
  }

  // ══════════════════════════════════════════════════
  // HELPER METHODS
  // ══════════════════════════════════════════════════
  private String calcDuration() {
    if (currentAuction.getStartTime() == null
        || currentAuction.getEndTime() == null) return "—";
    long days  = ChronoUnit.DAYS.between(
        currentAuction.getStartTime(), currentAuction.getEndTime());
    long hours = ChronoUnit.HOURS.between(
        currentAuction.getStartTime(), currentAuction.getEndTime()) % 24;
    return days > 0 ? days + " ngày " + hours + " giờ" : hours + " giờ";
  }

  private String calcActualDuration() {
    if (currentAuction.getStartTime() == null
        || currentAuction.getEndTime() == null) return "—";
    return calcDuration();
  }

  private String formatCreatedAt() {
    if (currentAuction.getCreatedAt() != null)
      return "Tạo lúc " + currentAuction.getCreatedAt().format(DT_FMT);
    if (currentAuction.getStartTime() != null)
      return "Bắt đầu " + currentAuction.getStartTime().format(DT_FMT);
    return "—";
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN"))
        .format((long) amount);
  }

  private String nvl(String s) { return s != null ? s : ""; }

  private void showError(String msg) {
    Alert a = new Alert(Alert.AlertType.ERROR);
    a.setTitle("Lỗi"); a.setHeaderText(null); a.setContentText(msg);
    a.showAndWait();
  }

  private void showSuccess(String msg) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.setTitle("Thành công"); a.setHeaderText(null); a.setContentText(msg);
    a.showAndWait();
  }

  private void showInfo(String msg) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.setTitle("Thông báo"); a.setHeaderText(null); a.setContentText(msg);
    a.showAndWait();
  }
}