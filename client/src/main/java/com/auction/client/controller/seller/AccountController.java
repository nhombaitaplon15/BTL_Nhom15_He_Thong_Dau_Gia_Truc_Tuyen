package com.auction.client.controller.seller;

import com.auction.client.controller.bidder.The_Home_Page_Bidder_View_Controller;
import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class AccountController {

  @FXML private HBox btnProfile;
  @FXML private HBox btnSecurity;
  @FXML private HBox btnWallet;
  @FXML private HBox btnLogout;

  @FXML private ScrollPane paneProfile;
  @FXML private ScrollPane paneSecurity;
  @FXML private ScrollPane paneWallet;
  @FXML private VBox paneLogout;

  @FXML private Label lblAvatarInitial;
  @FXML private Label lblFullName;
  @FXML private Label lblRolePill;
  @FXML private Label lblEmail;
  @FXML private Label lblMemberInfo;
  @FXML private Label lblStatProducts;
  @FXML private Label lblStatSold;
  @FXML private Label lblStatRating;

  @FXML private TextField txtUsername;
  @FXML private TextField txtEmail;
  @FXML private TextField txtPhone;
  @FXML private TextField txtRole;

  @FXML private PasswordField txtCurrentPass;
  @FXML private PasswordField txtNewPass;
  @FXML private PasswordField txtConfirmPass;

  @FXML private Label lblBalance;
  @FXML private Label lblBalanceUpdated;
  @FXML private ListView<TransactionRequest> listTransactions;
  @FXML private VBox emptyTransactions;

  @FXML private Button btnConfirmLogout;
  @FXML private Button btnStay;

  int myId = ClientSession.getInstance().getUserId();

  private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private final Consumer<Message> onProfileResult = this::handleProfileResult;
  private final Consumer<Message> onProfileUpdated = this::handleProfileUpdated;
  private final Consumer<Message> onPasswordChanged = this::handlePasswordChanged;
  private final Consumer<Message> onPasswordFailed = this::handlePasswordFailed;
  private final Consumer<Message> onDepositSuccess = this::handleDepositSuccess;
  private final Consumer<Message> onDepositFailed = msg -> showError("Nạp tiền thất bại: " + msg.getMessage());
  private final Consumer<Message> onWithdrawSuccess = this::handleWithdrawSuccess;
  private final Consumer<Message> onWithdrawFailed = msg -> showError("Rút tiền thất bại: " + msg.getMessage());
  private final Consumer<Message> onTransactionsResult = this::handleTransactionsResult;
  private final Consumer<Message> onSwitchRoleSuccess = this::handleSwitchRoleSuccess;
  private final Consumer<Message> onSwitchRoleFailed = this::handleSwitchRoleFailed;

  @FXML
  public void initialize() {
    setupTransactionCellFactory();
    registerNetworkHandlers();

    btnProfile.setOnMouseClicked(e -> switchTab(btnProfile, paneProfile, null));
    btnSecurity.setOnMouseClicked(e -> switchTab(btnSecurity, paneSecurity, null));
    btnWallet.setOnMouseClicked(e -> switchTab(btnWallet, paneWallet, null));
    btnLogout.setOnMouseClicked(e -> switchTab(btnLogout, null, paneLogout));

    if (btnConfirmLogout != null) btnConfirmLogout.setOnAction(e -> doLogout());
    if (btnStay != null) btnStay.setOnAction(e -> switchTab(btnProfile, paneProfile, null));

    switchTab(btnProfile, paneProfile, null);
    requestProfile();
    requestWalletData();
  }

  private void registerNetworkHandlers() {
    MessageRouter router = MessageRouter.getInstance();
    router.register(ResponseCode.PROFILE_RESULT, onProfileResult);
    router.register(ResponseCode.PROFILE_UPDATED, onProfileUpdated);
    router.register(ResponseCode.PASSWORD_CHANGED, onPasswordChanged);
    router.register(ResponseCode.PASSWORD_CHANGE_FAILED, onPasswordFailed);
    router.register(ResponseCode.DEPOSIT_SUCCESS, onDepositSuccess);
    router.register(ResponseCode.DEPOSIT_FAILED, onDepositFailed);
    router.register(ResponseCode.WITHDRAW_SUCCESS, onWithdrawSuccess);
    router.register(ResponseCode.WITHDRAW_FAILED, onWithdrawFailed);
    router.register(ResponseCode.TRANSACTIONS_RESULT, onTransactionsResult);
    router.register(ResponseCode.SWITCH_ROLE_SUCCESS, onSwitchRoleSuccess);
    router.register(ResponseCode.SWITCH_ROLE_FAILED, onSwitchRoleFailed);
  }

  public void cleanupHandlers() {
    MessageRouter router = MessageRouter.getInstance();
    router.unregister(ResponseCode.PROFILE_RESULT);
    router.unregister(ResponseCode.PROFILE_UPDATED);
    router.unregister(ResponseCode.PASSWORD_CHANGED);
    router.unregister(ResponseCode.PASSWORD_CHANGE_FAILED);
    router.unregister(ResponseCode.DEPOSIT_SUCCESS);
    router.unregister(ResponseCode.DEPOSIT_FAILED);
    router.unregister(ResponseCode.WITHDRAW_SUCCESS);
    router.unregister(ResponseCode.WITHDRAW_FAILED);
    router.unregister(ResponseCode.TRANSACTIONS_RESULT);
    router.unregister(ResponseCode.SWITCH_ROLE_SUCCESS);
    router.unregister(ResponseCode.SWITCH_ROLE_FAILED);
  }

  private void requestProfile() {
    SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, myId);
  }

  private void requestWalletData() {
    SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, myId);
    SocketClient.getInstance().sendRequest(RequestCode.GET_USER_TRANSACTIONS, myId);
  }

  @SuppressWarnings("unchecked")
  private void handleProfileResult(Message msg) {
    Object payload = msg.getPayload();
    if (payload instanceof User user) {
      ClientSession.getInstance().setCurrentUser(user);
      Platform.runLater(() -> {
        loadProfileUI(user);
        loadWalletUI(user);
      });
    }
  }

  @SuppressWarnings("unchecked")
  private void handleTransactionsResult(Message msg) {
    Object payload = msg.getPayload();
    if (payload instanceof List) {
      List<TransactionRequest> transactions = (List<TransactionRequest>) payload;

      Platform.runLater(() -> {
        if (transactions == null || transactions.isEmpty()) {
          if (emptyTransactions != null) {
            emptyTransactions.setVisible(true);
            emptyTransactions.setManaged(true);
          }
          if (listTransactions != null) {
            listTransactions.setVisible(false);
            listTransactions.setManaged(false);
          }
        } else {
          if (emptyTransactions != null) {
            emptyTransactions.setVisible(false);
            emptyTransactions.setManaged(false);
          }
          if (listTransactions != null) {
            listTransactions.setVisible(true);
            listTransactions.setManaged(true);
            listTransactions.getItems().setAll(transactions);
          }
        }
      });
    }
  }

  private void handleProfileUpdated(Message msg) {
    Platform.runLater(() -> {
      showSuccess("Cập nhật thông tin thành công!");
      requestProfile();
    });
  }

  private void handlePasswordChanged(Message msg) {
    Platform.runLater(() -> {
      showSuccess("Đổi mật khẩu thành công!");
      clearPasswordFields();
    });
  }

  private void handlePasswordFailed(Message msg) {
    Platform.runLater(() -> showError("Đổi mật khẩu thất bại: " + msg.getMessage()));
  }

  private void handleDepositSuccess(Message msg) {
    Platform.runLater(() -> {
      showSuccess("Yêu cầu nạp tiền đã được gửi!\nAdmin sẽ xét duyệt sớm.");
      requestProfile();
      requestWalletData();
    });
  }

  private void handleWithdrawSuccess(Message msg) {
    Platform.runLater(() -> {
      showSuccess("Yêu cầu rút tiền đã được gửi!\nAdmin sẽ xét duyệt sớm.");
      requestProfile();
      requestWalletData();
    });
  }

  private void loadProfileUI(User user) {
    String initial = (user.getUsername() != null && !user.getUsername().isEmpty())
        ? String.valueOf(user.getUsername().charAt(0)).toUpperCase() : "?";
    set(lblAvatarInitial, initial);
    set(lblFullName, user.getUsername());
    set(lblRolePill, nvl(user.getRole(), "USER"));
    set(lblEmail, nvl(user.getEmail(), "—"));
    set(lblMemberInfo, "ID: #" + user.getId() + " · Trạng thái: " + nvl(user.getStatus(), "ACTIVE"));

    set(txtUsername, nvl(user.getUsername(), ""));
    set(txtEmail, nvl(user.getEmail(), ""));
    set(txtPhone, nvl(user.getPhone(), ""));
    set(txtRole, nvl(user.getRole(), ""));
  }

  private void loadWalletUI(User user) {
    double balance = user.getBalance();
    set(lblBalance, formatMoney(balance) + " UETệ");
    set(lblBalanceUpdated, "Cập nhật vừa xong");
  }

  @FXML
  private void handleSaveProfile() {
    User user = ClientSession.getInstance().getCurrentUser();
    if (user == null) { showError("Chưa đăng nhập!"); return; }

    if (txtEmail != null) user.setEmail(txtEmail.getText().trim());
    if (txtPhone != null) user.setPhone(txtPhone.getText().trim());

    SocketClient.getInstance().sendRequest(RequestCode.UPDATE_PROFILE, user);
  }

  @FXML
  private void handleChangePassword() {
    String currentPwd = txtCurrentPass != null ? txtCurrentPass.getText().trim() : "";
    String newPwd = txtNewPass != null ? txtNewPass.getText().trim() : "";
    String confirmPwd = txtConfirmPass != null ? txtConfirmPass.getText().trim() : "";

    if (currentPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
      showError("Vui lòng điền đầy đủ các trường mật khẩu!"); return;
    }
    if (!newPwd.equals(confirmPwd)) {
      showError("Mật khẩu mới và xác nhận không khớp!"); return;
    }
    if (newPwd.length() < 6) {
      showError("Mật khẩu mới phải có ít nhất 6 ký tự!"); return;
    }

    String[] passwords = {currentPwd, newPwd, confirmPwd};
    SocketClient.getInstance().sendRequest(RequestCode.CHANGE_PASSWORD, passwords);
  }

  @FXML
  private void handleDeposit() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Nạp tiền UETệ");
    dialog.setHeaderText("Nhập số tiền muốn nạp:");
    dialog.setContentText("Số tiền:");
    dialog.showAndWait().ifPresent(input -> {
      try {
        double amount = Double.parseDouble(input.trim().replace(",", ""));
        if (amount <= 0) { showError("Số tiền phải lớn hơn 0!"); return; }
        SocketClient.getInstance().sendRequest(RequestCode.DEPOSIT_REQUEST, amount);
      } catch (NumberFormatException e) {
        showError("Số tiền không hợp lệ!");
      }
    });
  }

  @FXML
  private void handleWithdraw() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Rút tiền UETệ");
    dialog.setHeaderText("Nhập số tiền muốn rút:");
    dialog.setContentText("Số tiền:");
    dialog.showAndWait().ifPresent(input -> {
      try {
        double amount = Double.parseDouble(input.trim().replace(",", ""));
        if (amount <= 0) { showError("Số tiền phải lớn hơn 0!"); return; }
        SocketClient.getInstance().sendRequest(RequestCode.WITHDRAW_REQUEST, amount);
      } catch (NumberFormatException e) {
        showError("Số tiền không hợp lệ!");
      }
    });
  }

  private void doLogout() {
    cleanupHandlers();
    ClientSession.getInstance().clear();
    SocketClient.getInstance().disconnect();
    try {
      javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
          getClass().getResource("/view/view/auth/LoginView.fxml"));
      javafx.scene.Parent root = loader.load();
      javafx.stage.Stage stage = (javafx.stage.Stage) btnProfile.getScene().getWindow();
      stage.setScene(new javafx.scene.Scene(root));
    } catch (Exception e) {
      showError("Không thể chuyển trang: " + e.getMessage());
    }
  }

  @FXML void handleSwitchToBidder(ActionEvent event) {
    SocketClient.getInstance().sendRequest(RequestCode.SWITCH_ROLE, "BIDDER");
  }

  private void handleSwitchRoleSuccess(Message msg) {
    Platform.runLater(() -> {
      cleanupHandlers();
      try {
        String newRole = (String) msg.getPayload();
        ClientSession.getInstance().getCurrentUser().setRole(newRole);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/The_Home_Page_Bidder_View.fxml"));
        Parent root = loader.load();

        The_Home_Page_Bidder_View_Controller homeController = loader.getController();
        homeController.setUserData(ClientSession.getInstance().getCurrentUser());

        Stage stage = (Stage) txtUsername.getScene().getWindow();
        Scene scene = new Scene(root, 1280, 720);

        stage.setScene(scene);
        stage.setTitle("Elite Auction - Trang chủ hệ thống");
        stage.setMaximized(true);
        stage.centerOnScreen();
        stage.show();
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private void handleSwitchRoleFailed(Message msg) {
    Platform.runLater(() -> showError(msg.getMessage()));
  }

  private void clearPasswordFields() {
    if (txtCurrentPass != null) txtCurrentPass.clear();
    if (txtNewPass != null) txtNewPass.clear();
    if (txtConfirmPass != null) txtConfirmPass.clear();
  }

  private void switchTab(HBox activeBtn, ScrollPane activeScroll, VBox activeVBox) {
    for (ScrollPane p : new ScrollPane[]{paneProfile, paneSecurity, paneWallet}) {
      if (p != null) { p.setVisible(false); p.setManaged(false); }
    }
    if (paneLogout != null) { paneLogout.setVisible(false); paneLogout.setManaged(false); }

    if (activeScroll != null) { activeScroll.setVisible(true); activeScroll.setManaged(true); }
    if (activeVBox != null) { activeVBox.setVisible(true); activeVBox.setManaged(true); }

    for (HBox btn : new HBox[]{btnProfile, btnSecurity, btnWallet, btnLogout}) {
      if (btn != null) {
        btn.getStyleClass().removeAll("sidebar-btn", "sidebar-btn-active");
        if (btn == activeBtn) {
          btn.getStyleClass().add("sidebar-btn-active");
          updateSidebarLabelColor(btn, true);
        } else {
          btn.getStyleClass().add("sidebar-btn");
          updateSidebarLabelColor(btn, false);
        }
      }
    }
  }

  private void updateSidebarLabelColor(HBox btn, boolean isActive) {
    if (btn == null) return;
    btn.getChildren().stream()
        .filter(n -> n instanceof Label)
        .skip(1)
        .map(n -> (Label) n)
        .findFirst()
        .ifPresent(lbl -> {
          lbl.getStyleClass().removeAll("sidebar-text", "sidebar-text-active");
          lbl.getStyleClass().add(isActive ? "sidebar-text-active" : "sidebar-text");
        });
  }

  private void setupTransactionCellFactory() {
    if (listTransactions == null) return;
    listTransactions.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(TransactionRequest tx, boolean empty) {
        super.updateItem(tx, empty);
        if (empty || tx == null) { setGraphic(null); return; }

        boolean isIncome = isIncomeType(tx.getType());

        javafx.scene.layout.StackPane iconBox = new javafx.scene.layout.StackPane();
        iconBox.getStyleClass().addAll("tx-icon-box", isIncome ? "tx-icon-box-income" : "tx-icon-box-outcome");

        Label icon = new Label(isIncome ? "📥" : "📤");
        icon.getStyleClass().add("tx-icon-label");
        iconBox.getChildren().add(icon);
        iconBox.setAlignment(Pos.CENTER);

        Label lblDesc = new Label(formatTxType(tx.getType()));
        lblDesc.getStyleClass().add("tx-desc");

        Label lblTime = new Label(tx.getRequestDate() != null ? tx.getRequestDate().format(DT_FMT) : "—");
        lblTime.getStyleClass().add("tx-time");

        VBox info = new VBox(2, lblDesc, lblTime);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lblAmt = new Label((isIncome ? "+" : "−") + formatMoney(tx.getAmount()) + "đ");
        lblAmt.getStyleClass().add(isIncome ? "tx-amt-income" : "tx-amt-outcome");

        Label lblSt = new Label(formatStatus(tx.getTransactionStatus()));
        lblSt.getStyleClass().add("tx-status");

        VBox amtBox = new VBox(2, lblAmt, lblSt);
        amtBox.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, iconBox, info, amtBox);
        row.getStyleClass().add("tx-row");
        setGraphic(row);

        getStyleClass().add("tx-cell");
      }
    });
  }

  private boolean isIncomeType(String type) {
    if (type == null) return false;
    return type.startsWith("DEPOSIT") || type.startsWith("RELEASE_") || type.startsWith("REFUND_");
  }

  private String formatTxType(String type) {
    if (type == null) return "Giao dịch";
    if (type.startsWith("DEPOSIT")) return "Nạp tiền vào ví";
    if (type.startsWith("WITHDRAW")) return "Rút tiền";
    if (type.startsWith("HOLD_AUCTION_")) return "Đặt cọc phiên #" + type.replace("HOLD_AUCTION_", "");
    if (type.startsWith("RELEASE_AUCTION_")) return "Nhận tiền phiên #" + type.replace("RELEASE_AUCTION_", "");
    if (type.startsWith("REFUND_AUCTION_")) return "Hoàn tiền phiên #" + type.replace("REFUND_AUCTION_", "");
    if (type.startsWith("BID_AUCTION_")) return "Đặt giá phiên #" + type.replace("BID_AUCTION_", "");
    if (type.startsWith("PROFIT_AUCTION_")) return "Phí hoa hồng";
    return type;
  }

  private String formatStatus(String s) {
    if (s == null) return "";
    return switch (s) {
      case "SUCCESS" -> "✓ Thành công";
      case "PENDING" -> "⏳ Chờ duyệt";
      case "APPROVED" -> "✓ Đã duyệt";
      case "REJECTED" -> "✗ Từ chối";
      default -> s;
    };
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format((long) amount);
  }

  private void set(Labeled node, String text) {
    if (node != null) node.setText(text != null ? text : "");
  }

  private void set(TextField field, String text) {
    if (field != null) field.setText(text != null ? text : "");
  }

  private String nvl(String s, String fallback) {
    return (s != null && !s.isEmpty()) ? s : fallback;
  }

  private void showError(String msg) {
    Alert a = new Alert(Alert.AlertType.ERROR);
    a.setTitle("Lỗi"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
  }

  private void showSuccess(String msg) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.setTitle("Thành công"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
  }
}