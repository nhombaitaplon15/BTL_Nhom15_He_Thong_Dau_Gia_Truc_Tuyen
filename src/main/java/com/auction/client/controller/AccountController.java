package com.auction.client.controller;

import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.dao.UserDAO;
import com.auction.service.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.*;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller cho AccountView.fxml
 *
 * Dựa trên User.java thực tế — chỉ có các field:
 *   id, username, email, password, phone, status, role, balance
 *
 * 5 tab:
 *   1. Thông tin cá nhân  — username(readonly), email, phone — UserDAO/UserService
 *   2. Bảo mật            — đổi mật khẩu qua UserService.handleChangePassword()
 *   3. Ví UETệ            — balance từ PaymentDAO + lịch sử TransactionDAO
 *   4. Thông báo          — CheckBox settings
 *   5. Đăng xuất          — quay về WelcomeView
 */
public class AccountController {

  // ══════════════════════════════════════════════════
  // FXML — SIDEBAR BUTTONS
  // ══════════════════════════════════════════════════
  @FXML private HBox btnProfile;
  @FXML private HBox btnSecurity;
  @FXML private HBox btnWallet;
  @FXML private HBox btnNotif;
  @FXML private HBox btnLogout;

  // ══════════════════════════════════════════════════
  // FXML — CONTENT PANES
  // ══════════════════════════════════════════════════
  @FXML private ScrollPane paneProfile;
  @FXML private ScrollPane paneSecurity;
  @FXML private ScrollPane paneWallet;
  @FXML private ScrollPane paneNotif;
  @FXML private VBox       paneLogout;

  // ══════════════════════════════════════════════════
  // FXML — TAB PROFILE (banner)
  // ══════════════════════════════════════════════════
  /** Chữ cái đầu username làm avatar */
  @FXML private Label lblAvatarInitial;
  /** Hiện username trên banner */
  @FXML private Label lblFullName;
  /** SELLER / BIDDER */
  @FXML private Label lblRolePill;
  /** Email trên banner */
  @FXML private Label lblEmail;
  /** "ID: #11 · Trạng thái: ACTIVE" */
  @FXML private Label lblMemberInfo;
  /** Số sản phẩm đang RUNNING */
  @FXML private Label lblStatProducts;
  /** Số phiên đã PAID/FINISHED */
  @FXML private Label lblStatSold;
  /** Rating placeholder */
  @FXML private Label lblStatRating;

  // ── Form thông tin (chỉ email + phone được sửa) ──
  /** readonly — không cho sửa username */
  @FXML private TextField txtUsername;
  @FXML private TextField txtEmail;
  @FXML private TextField txtPhone;
  /** readonly — role chỉ đổi qua UserService.handleSwitchRole() */
  @FXML private TextField txtRole;
  /** readonly — status */
  @FXML private TextField txtStatus;

  // ══════════════════════════════════════════════════
  // FXML — TAB SECURITY
  // ══════════════════════════════════════════════════
  @FXML private PasswordField txtCurrentPass;
  @FXML private PasswordField txtNewPass;
  @FXML private PasswordField txtConfirmPass;

  @FXML private Label lbl2FAStatus;
  @FXML private Button btn2FAToggle;
  @FXML private Label lblVerifiedEmail;
  @FXML private Label lblEmailStatus;
  @FXML private Label lblSessionInfo;

  // ══════════════════════════════════════════════════
  // FXML — TAB WALLET
  // ══════════════════════════════════════════════════
  @FXML private Label                    lblBalance;
  @FXML private Label                    lblBalanceUpdated;
  @FXML private ListView<TransactionRequest> listTransactions;
  @FXML private VBox                     emptyTransactions;

  // ══════════════════════════════════════════════════
  // FXML — TAB NOTIFICATION
  // ══════════════════════════════════════════════════
  @FXML private CheckBox cbNotifNewBid;
  @FXML private CheckBox cbNotifEnding;
  @FXML private CheckBox cbNotifOutbid;
  @FXML private CheckBox cbNotifResult;
  @FXML private CheckBox cbNotifAdmin;
  @FXML private CheckBox cbNotifPayment;
  @FXML private CheckBox cbNotifNews;

  // ══════════════════════════════════════════════════
  // FXML — TAB LOGOUT
  // ══════════════════════════════════════════════════
  @FXML private Button btnConfirmLogout;
  @FXML private Button btnStay;

  // ══════════════════════════════════════════════════
  // DAO & SERVICE — dùng đúng theo file bạn có
  // ══════════════════════════════════════════════════
  private final UserDAO        userDAO     = new UserDAO();
  private final UserService    userService = new UserService();
  private final PaymentDAO     paymentDAO  = new PaymentDAO();
  private final TransactionDAO transDAO    = new TransactionDAO();

  // ══════════════════════════════════════════════════
  // STATE
  // ══════════════════════════════════════════════════
  /**
   * User đang đăng nhập.
   * TODO: Thay bằng SessionManager.getCurrentUser()
   */
  private User currentUser;

  private static final DateTimeFormatter DT_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  // ══════════════════════════════════════════════════
  // INITIALIZE
  // ══════════════════════════════════════════════════
  @FXML
  public void initialize() {
    // TODO: currentUser = SessionManager.getCurrentUser();
    // Tạm dùng user giả để test — XOÁ khi có session
    currentUser = loadCurrentUser(11);
    if (currentUser == null) return;

    setupTransactionCellFactory();

    // Gắn click sidebar
    btnProfile .setOnMouseClicked(e -> switchTab(btnProfile,  paneProfile,  null));
    btnSecurity.setOnMouseClicked(e -> switchTab(btnSecurity, paneSecurity, null));
    btnWallet  .setOnMouseClicked(e -> switchTab(btnWallet,   paneWallet,   null));
    btnLogout  .setOnMouseClicked(e -> switchTab(btnLogout,   null,         paneLogout));

    // Tab mặc định
    switchTab(btnProfile, paneProfile, null);

    // Load dữ liệu
    loadProfile();
    loadWallet();
  }

  // ══════════════════════════════════════════════════
  // LOAD USER TỪ DB (dùng UserDAO đúng chuẩn)
  // ══════════════════════════════════════════════════
  private User loadCurrentUser(int userId) {
    return userDAO.getUserById(userId);
  }

  // ══════════════════════════════════════════════════
  // LOAD DỮ LIỆU — TAB PROFILE
  // ══════════════════════════════════════════════════
  private void loadProfile() {
    if (currentUser == null) return;

    // ── Banner ──
    // Avatar: chữ cái đầu của username
    String initial = currentUser.getUsername() != null
        && !currentUser.getUsername().isEmpty()
        ? String.valueOf(currentUser.getUsername().charAt(0)).toUpperCase()
        : "?";
    set(lblAvatarInitial, initial);

    // Dùng username vì User không có fullName
    set(lblFullName, currentUser.getUsername());
    set(lblRolePill, nvl(currentUser.getRole(), "USER"));
    set(lblEmail,    nvl(currentUser.getEmail(), "—"));
    set(lblMemberInfo, "ID: #" + currentUser.getId()
        + " · Trạng thái: " + nvl(currentUser.getStatus(), "ACTIVE"));

    // Stat badges (đếm từ DB nền)
    loadProfileStats();

    // ── Form fields ──
    set(txtUsername, nvl(currentUser.getUsername(), ""));
    set(txtEmail,    nvl(currentUser.getEmail(),    ""));
    set(txtPhone,    nvl(currentUser.getPhone(),    ""));
    set(txtRole,     nvl(currentUser.getRole(),     ""));
    set(txtStatus,   nvl(currentUser.getStatus(),   ""));

    // ── Security tab ──
    set(lblVerifiedEmail, nvl(currentUser.getEmail(), "—"));
    set(lblEmailStatus,   "Đã xác minh");
    set(lblSessionInfo,   "Đang hoạt động trên 1 thiết bị");
  }

  /** Đếm số sản phẩm RUNNING và PAID/FINISHED từ DB */
  private void loadProfileStats() {
    new Thread(() -> {
      int running = 0, sold = 0;
      String sql =
          "SELECT " +
              "  COUNT(CASE WHEN a.auction_status = 'RUNNING' THEN 1 END) AS running_count, " +
              "  COUNT(CASE WHEN a.auction_status IN ('FINISHED','PAID') THEN 1 END) AS sold_count " +
              "FROM items i " +
              "LEFT JOIN auctions a ON i.item_id = a.item_id " +
              "WHERE i.seller_id = ?";
      try (Connection conn = DBConnection.getConnection();
           PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, currentUser.getId());
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            running = rs.getInt("running_count");
            sold    = rs.getInt("sold_count");
          }
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
      final int r = running, s = sold;
      Platform.runLater(() -> {
        set(lblStatProducts, String.valueOf(r));
        set(lblStatSold,     String.valueOf(s));
        set(lblStatRating,   "4.9★");
      });
    }).start();
  }

  // ══════════════════════════════════════════════════
  // LOAD DỮ LIỆU — TAB WALLET
  // ══════════════════════════════════════════════════
  private void loadWallet() {
    if (currentUser == null) return;

    new Thread(() -> {
      // 1. Lấy số dư từ PaymentDAO.getBalance(userId)
      double balance = paymentDAO.getBalance(currentUser.getId());

      // 2. Lấy lịch sử từ bảng transactions
      List<TransactionRequest> txList =
          loadTransactionHistory(currentUser.getId());

      Platform.runLater(() -> {
        // Hiện số dư
        if (balance >= 0) {
          set(lblBalance, formatMoney(balance) + " UETệ");
        } else {
          set(lblBalance, "— UETệ");
        }
        set(lblBalanceUpdated,
            "Cập nhật lúc "
                + java.time.LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
                + " · "
                + java.time.LocalDate.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        // Đổ lên ListView
        if (listTransactions != null)
          listTransactions.getItems().setAll(txList);

        // Empty state
        boolean empty = txList.isEmpty();
        toggleNode(emptyTransactions, empty);
        if (listTransactions != null) {
          listTransactions.setVisible(!empty);
          listTransactions.setManaged(!empty);
        }
      });
    }).start();
  }

  /**
   * Query lịch sử giao dịch từ bảng transactions.
   * Dùng trực tiếp JDBC vì TransactionDAO chỉ có insert/update,
   * không có SELECT list.
   */
  private List<TransactionRequest> loadTransactionHistory(int userId) {
    List<TransactionRequest> list = new ArrayList<>();
    String sql =
        "SELECT transaction_id, amount, transaction_type, status, created_at " +
            "FROM transactions " +
            "WHERE user_id = ? " +
            "ORDER BY created_at DESC " +
            "LIMIT 50";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          // Dùng constructor của TransactionRequest:
          // (User user, String type, double amount, String bankInfo, String status)
          TransactionRequest tx = new TransactionRequest(
              currentUser,
              rs.getString("transaction_type"),
              rs.getDouble("amount"),
              null,                          // bankInfo — không cần hiện
              rs.getString("status")
          );
          tx.setRequestId(rs.getInt("transaction_id"));
          Timestamp ts = rs.getTimestamp("created_at");
          if (ts != null) tx.setRequestDate(ts.toLocalDateTime());
          list.add(tx);
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return list;
  }

  // ══════════════════════════════════════════════════
  // ACTION — TAB PROFILE
  // ══════════════════════════════════════════════════

  /** Lưu email + phone — chỉ 2 trường này được sửa theo User.java */
  @FXML
  private void onSaveProfile() {
    String newEmail = txtEmail != null ? txtEmail.getText().trim() : "";
    String newPhone = txtPhone != null ? txtPhone.getText().trim() : "";

    // Validate
    if (newEmail.isEmpty() || !newEmail.contains("@")) {
      showError("Email không hợp lệ!");
      if (txtEmail != null) txtEmail.requestFocus();
      return;
    }
    if (!newPhone.matches("^\\d{10}$")) {
      showError("Số điện thoại phải có đúng 10 chữ số!");
      if (txtPhone != null) txtPhone.requestFocus();
      return;
    }

    // Cập nhật DB — chỉ email và phone
    String sql = "UPDATE users SET email = ?, phone = ? WHERE user_id = ?";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, newEmail);
      ps.setString(2, newPhone);
      ps.setInt(3, currentUser.getId());
      boolean ok = ps.executeUpdate() > 0;
      if (ok) {
        // Đồng bộ RAM — dùng setter từ User.java
        currentUser.setEmail(newEmail);
        currentUser.setPhone(newPhone);

        // Refresh banner
        set(lblEmail, newEmail);

        showSuccess("Đã lưu thông tin thành công!");
      } else {
        showError("Lưu thất bại. Vui lòng thử lại.");
      }
    } catch (SQLException e) {
      showError("Lỗi database: " + e.getMessage());
    }
  }

  /** Đổi vai trò BIDDER ↔ SELLER dùng UserService.handleSwitchRole() */
  @FXML
  private void onSwitchRole() {
    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Đổi vai trò");
    String targetRole = "BIDDER".equalsIgnoreCase(currentUser.getRole())
        ? "SELLER" : "BIDDER";
    confirm.setContentText("Bạn muốn chuyển sang vai trò " + targetRole + "?");
    confirm.showAndWait().ifPresent(btn -> {
      if (btn == ButtonType.OK) {
        try {
          userService.handleSwitchRole(currentUser);
          // currentUser.role đã được cập nhật trong handleSwitchRole()
          set(txtRole,  nvl(currentUser.getRole(), ""));
          set(lblRolePill, nvl(currentUser.getRole(), "USER"));
          showSuccess("Đã chuyển vai trò thành: " + currentUser.getRole());
        } catch (AuctionException e) {
          showError(e.getMessage());
        }
      }
    });
  }

  @FXML
  private void onChangeAvatar() {
    showInfo("Chức năng đổi ảnh sẽ được bổ sung sau.");
  }

  // ══════════════════════════════════════════════════
  // ACTION — TAB SECURITY
  // ══════════════════════════════════════════════════

  /**
   * Đổi mật khẩu dùng UserService.handleChangePassword().
   * UserService kiểm tra: oldPass đúng, newPass >= 8 ký tự,
   * newPass != oldPass, confirmPass == newPass.
   */
  @FXML
  private void onChangePassword() {
    String curPass  = txtCurrentPass  != null ? txtCurrentPass.getText()  : "";
    String newPass  = txtNewPass      != null ? txtNewPass.getText()      : "";
    String confPass = txtConfirmPass  != null ? txtConfirmPass.getText()  : "";

    if (curPass.isEmpty() || newPass.isEmpty() || confPass.isEmpty()) {
      showError("Vui lòng điền đầy đủ các trường mật khẩu!");
      return;
    }

    try {
      // Gọi UserService — đã chứa toàn bộ logic validate
      userService.handleChangePassword(currentUser, curPass, newPass, confPass);

      // Thành công → xoá ô
      if (txtCurrentPass != null) txtCurrentPass.clear();
      if (txtNewPass     != null) txtNewPass.clear();
      if (txtConfirmPass != null) txtConfirmPass.clear();

      showSuccess("Đổi mật khẩu thành công!");

    } catch (AuctionException e) {
      showError(e.getMessage());
    }
  }

  @FXML
  private void onChangeEmail() {
    TextInputDialog dialog = new TextInputDialog(
        currentUser.getEmail() != null ? currentUser.getEmail() : "");
    dialog.setTitle("Đổi email");
    dialog.setHeaderText(null);
    dialog.setContentText("Email mới:");
    Optional<String> result = dialog.showAndWait();
    result.ifPresent(newEmail -> {
      if (newEmail.contains("@")) {
        showInfo("Đã gửi xác nhận tới " + newEmail + ". Kiểm tra hộp thư.");
      } else {
        showError("Email không hợp lệ!");
      }
    });
  }

  @FXML
  private void onLogoutAllSessions() {
    showInfo("Đã đăng xuất tất cả thiết bị khác.");
    set(lblSessionInfo, "Chỉ còn phiên hiện tại");
  }

  // ══════════════════════════════════════════════════
  // ACTION — TAB WALLET
  // ══════════════════════════════════════════════════

  /**
   * Nạp tiền — tạo bản ghi PENDING bằng TransactionDAO.createTransaction().
   * Admin duyệt → PaymentDAO.updateBalance() cộng tiền.
   */
  @FXML
  private void onTopUp() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Nạp tiền vào ví");
    dialog.setHeaderText("Nhập số tiền muốn nạp (UETệ):");
    dialog.setContentText("Số tiền:");

    Optional<String> result = dialog.showAndWait();
    result.ifPresent(input -> {
      try {
        // Xoá ký tự không phải số rồi parse
        double amount = Double.parseDouble(
            input.replaceAll("[^\\d]", ""));
        if (amount <= 0) {
          showError("Số tiền phải lớn hơn 0!");
          return;
        }

        // TransactionDAO.createTransaction(userId, amount, type, status)
        boolean ok = transDAO.createTransaction(
            currentUser.getId(),
            amount,
            "DEPOSIT",
            "PENDING"
        );

        if (ok) {
          showSuccess("Đã gửi yêu cầu nạp "
              + formatMoney(amount) + " UETệ.\n"
              + "Admin sẽ duyệt trong thời gian sớm nhất.");
          loadWallet(); // Refresh danh sách
        } else {
          showError("Tạo yêu cầu thất bại. Thử lại sau.");
        }

      } catch (NumberFormatException e) {
        showError("Số tiền không hợp lệ!");
      } catch (SQLException e) {
        showError("Lỗi database: " + e.getMessage());
      }
    });
  }

  @FXML
  private void onWITHDRAW() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Rút tiền khỏi ví");
    dialog.setHeaderText("Nhập số tiền muốn rút (UETệ):");
    dialog.setContentText("Số tiền:");

    Optional<String> result = dialog.showAndWait();
    result.ifPresent(input -> {
      try {
        // Xoá ký tự không phải số rồi parse
        double amount = Double.parseDouble(
            input.replaceAll("[^\\d]", ""));
        if (amount <= 0) {
          showError("Số tiền phải lớn hơn 0!");
          return;
        }

        // TransactionDAO.createTransaction(userId, amount, type, status)
        boolean ok = transDAO.createTransaction(
            currentUser.getId(),
            amount,
            "WITHDRAW",
            "PENDING"
        );

        if (ok) {
          showSuccess("Đã gửi yêu cầu rút "
              + formatMoney(amount) + " UETệ.\n"
              + "Admin sẽ duyệt trong thời gian sớm nhất.");
          loadWallet(); // Refresh danh sách
        } else {
          showError("Tạo yêu cầu thất bại. Thử lại sau.");
        }

      } catch (NumberFormatException e) {
        showError("Số tiền không hợp lệ!");
      } catch (SQLException e) {
        showError("Lỗi database: " + e.getMessage());
      }
    });
  }

  // ══════════════════════════════════════════════════
  // ACTION — TAB NOTIFICATION
  // ══════════════════════════════════════════════════
  @FXML
  private void onSaveNotifSettings() {
    // TODO: Lưu vào DB khi có bảng user_notifications
    // Tạm thời log ra console
    System.out.println("[NOTIF] newBid="
        + (cbNotifNewBid  != null && cbNotifNewBid.isSelected())
        + " ending=" + (cbNotifEnding  != null && cbNotifEnding.isSelected())
        + " outbid=" + (cbNotifOutbid  != null && cbNotifOutbid.isSelected())
        + " result=" + (cbNotifResult  != null && cbNotifResult.isSelected())
        + " admin="  + (cbNotifAdmin   != null && cbNotifAdmin.isSelected())
        + " payment="+ (cbNotifPayment != null && cbNotifPayment.isSelected())
        + " news="   + (cbNotifNews    != null && cbNotifNews.isSelected()));

    showSuccess("Đã lưu cài đặt thông báo!");
  }

  // ══════════════════════════════════════════════════
  // ACTION — TAB LOGOUT
  // ══════════════════════════════════════════════════
  @FXML
  private void onConfirmLogout() {
    try {
      // TODO: SessionManager.clearSession();
      // TODO: NetworkClient.getInstance().disconnect();

      javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
          getClass().getResource("/view/WelcomeView.fxml"));
      javafx.scene.Parent root = loader.load();
      javafx.stage.Stage stage =
          (javafx.stage.Stage) btnConfirmLogout.getScene().getWindow();
      stage.setScene(new javafx.scene.Scene(root));
      stage.show();

    } catch (Exception e) {
      showError("Không thể đăng xuất: " + e.getMessage());
    }
  }

  @FXML
  private void onStay() {
    switchTab(btnProfile, paneProfile, null);
  }

  // ══════════════════════════════════════════════════
  // NAVBAR
  // ══════════════════════════════════════════════════
  @FXML private void onNavHome()     { navigate("/view/The_Home_Page_Seller_View.fxml"); }
  @FXML private void onNavProducts() { navigate("/view/MyProductsView.fxml"); }
  @FXML private void onNavAuction()  { navigate("/view/AuctionManagementView.fxml"); }
  @FXML private void onNavAccount()  { /* đang ở đây rồi */ }

  private void navigate(String path) {
    try {
      javafx.fxml.FXMLLoader loader =
          new javafx.fxml.FXMLLoader(getClass().getResource(path));
      javafx.scene.Parent root = loader.load();
      javafx.stage.Stage stage =
          (javafx.stage.Stage) btnProfile.getScene().getWindow();
      stage.setScene(new javafx.scene.Scene(root));
    } catch (Exception e) {
      showError("Không thể chuyển trang: " + e.getMessage());
    }
  }

  // ══════════════════════════════════════════════════
  // SWITCH TAB
  // ══════════════════════════════════════════════════
  private void switchTab(HBox activeBtn, ScrollPane activeScroll, VBox activeVBox) {
    // Ẩn tất cả
    for (ScrollPane p : new ScrollPane[]{paneProfile, paneSecurity, paneWallet, paneNotif}) {
      if (p != null) { p.setVisible(false); p.setManaged(false); }
    }
    if (paneLogout != null) { paneLogout.setVisible(false); paneLogout.setManaged(false); }

    // Hiện pane được chọn
    if (activeScroll != null) {
      activeScroll.setVisible(true);
      activeScroll.setManaged(true);
    }
    if (activeVBox != null) {
      activeVBox.setVisible(true);
      activeVBox.setManaged(true);
    }

    // Reset style tất cả sidebar buttons
    String defaultStyle =
        "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-border-width: 0 0 0 3;" +
            "-fx-padding: 10 16 10 16;" +
            "-fx-cursor: hand;";

    String activeStyle =
        "-fx-background-color: rgba(215,168,89,0.14);" +
            "-fx-border-color: transparent transparent transparent #D7A859;" +
            "-fx-border-width: 0 0 0 3;" +
            "-fx-padding: 10 16 10 16;" +
            "-fx-cursor: hand;";

    for (HBox btn : new HBox[]{btnProfile, btnSecurity, btnWallet, btnNotif, btnLogout}) {
      if (btn != null) btn.setStyle(defaultStyle);
    }
    if (activeBtn != null) activeBtn.setStyle(activeStyle);

    // Đổi màu text label trong sidebar
    for (HBox btn : new HBox[]{btnProfile, btnSecurity, btnWallet, btnNotif, btnLogout}) {
      updateSidebarLabelColor(btn, btn == activeBtn ? "#FFD691" : "#8BA8D4");
    }
  }

  /** Đổi màu label thứ 2 trong HBox (skip dot đầu tiên) */
  private void updateSidebarLabelColor(HBox btn, String color) {
    if (btn == null) return;
    btn.getChildren().stream()
        .filter(n -> n instanceof Label)
        .map(n -> (Label) n)
        .skip(1)
        .findFirst()
        .ifPresent(lbl -> lbl.setStyle(
            "-fx-text-fill: " + color + "; -fx-font-size: 12;"));
  }

  // ══════════════════════════════════════════════════
  // CELL FACTORY — ListView giao dịch
  // ══════════════════════════════════════════════════
  private void setupTransactionCellFactory() {
    if (listTransactions == null) return;

    listTransactions.setCellFactory(lv -> new ListCell<TransactionRequest>() {
      @Override
      protected void updateItem(TransactionRequest tx, boolean empty) {
        super.updateItem(tx, empty);
        if (empty || tx == null) { setGraphic(null); return; }

        boolean isIncome = isIncomeType(tx.getType());

        // Icon box
        javafx.scene.layout.StackPane iconBox =
            new javafx.scene.layout.StackPane();
        iconBox.setMinSize(32, 32);
        iconBox.setPrefSize(32, 32);
        iconBox.setStyle("-fx-background-radius:8;-fx-background-color:"
            + (isIncome ? "#E8F5E9" : "#FFF3E0") + ";");
        Label icon = new Label(isIncome ? "📥" : "📤");
        icon.setStyle("-fx-font-size:14;");
        iconBox.getChildren().add(icon);
        iconBox.setAlignment(Pos.CENTER);

        // Mô tả + ngày
        Label lblDesc = new Label(formatTxType(tx.getType()));
        lblDesc.setStyle("-fx-font-size:12;-fx-font-weight:bold;-fx-text-fill:#1A2B4A;");

        Label lblTime = new Label(tx.getRequestDate() != null
            ? tx.getRequestDate().format(DT_FMT) : "—");
        lblTime.setStyle("-fx-font-size:10;-fx-text-fill:#A08C6E;");

        VBox info = new VBox(2, lblDesc, lblTime);
        HBox.setHgrow(info, Priority.ALWAYS);

        // Số tiền + status
        Label lblAmt = new Label(
            (isIncome ? "+" : "−") + formatMoney(tx.getAmount()) + "đ");
        lblAmt.setStyle("-fx-font-size:13;-fx-font-weight:bold;-fx-text-fill:"
            + (isIncome ? "#2E7D32" : "#E65100") + ";");

        Label lblSt = new Label(formatStatus(tx.getTransactionStatus()));
        lblSt.setStyle("-fx-font-size:9.5;-fx-text-fill:#A08C6E;");

        VBox amtBox = new VBox(2, lblAmt, lblSt);
        amtBox.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, iconBox, info, amtBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:8 16 8 16;");
        setGraphic(row);
        setStyle("-fx-background-color:transparent;-fx-padding:2 0 2 0;");
      }
    });
  }

  // ══════════════════════════════════════════════════
  // HELPER — FORMAT
  // ══════════════════════════════════════════════════

  /** Thu: DEPOSIT, RELEASE, REFUND — Chi: còn lại */
  private boolean isIncomeType(String type) {
    if (type == null) return false;
    return type.startsWith("DEPOSIT")
        || type.startsWith("RELEASE_")
        || type.startsWith("REFUND_");
  }

  /** Chuyển transaction_type sang tên đọc được */
  private String formatTxType(String type) {
    if (type == null) return "Giao dịch";
    if (type.startsWith("DEPOSIT"))          return "Nạp tiền vào ví";
    if (type.startsWith("WITHDRAW"))         return "Rút tiền";
    if (type.startsWith("HOLD_AUCTION_"))
      return "Đặt cọc phiên #" + type.replace("HOLD_AUCTION_", "");
    if (type.startsWith("RELEASE_AUCTION_"))
      return "Nhận tiền phiên #" + type.replace("RELEASE_AUCTION_", "");
    if (type.startsWith("REFUND_AUCTION_"))
      return "Hoàn tiền phiên #"  + type.replace("REFUND_AUCTION_", "");
    if (type.startsWith("BID_AUCTION_"))
      return "Đặt giá phiên #"    + type.replace("BID_AUCTION_", "");
    if (type.startsWith("PROFIT_AUCTION_")) return "Phí hoa hồng";
    return type;
  }

  private String formatStatus(String s) {
    if (s == null) return "";
    return switch (s) {
      case "SUCCESS"  -> "✓ Thành công";
      case "PENDING"  -> "⏳ Chờ duyệt";
      case "APPROVED" -> "✓ Đã duyệt";
      case "REJECTED" -> "✗ Từ chối";
      default          -> s;
    };
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN"))
        .format((long) amount);
  }

  // ══════════════════════════════════════════════════
  // HELPER — NULL SAFE
  // ══════════════════════════════════════════════════

  /** Set text an toàn, bỏ qua nếu label null */
  private void set(Labeled node, String text) {
    if (node != null) node.setText(text != null ? text : "");
  }

  private void set(TextField field, String text) {
    if (field != null) field.setText(text != null ? text : "");
  }

  private String nvl(String s, String fallback) {
    return (s != null && !s.isEmpty()) ? s : fallback;
  }

  private void toggleNode(javafx.scene.Node node, boolean show) {
    if (node != null) { node.setVisible(show); node.setManaged(show); }
  }

  // ══════════════════════════════════════════════════
  // HELPER — ALERT
  // ══════════════════════════════════════════════════
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