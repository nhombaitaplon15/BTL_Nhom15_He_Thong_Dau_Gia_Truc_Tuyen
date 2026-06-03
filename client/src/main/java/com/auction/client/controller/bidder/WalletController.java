package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.text.DecimalFormat;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class WalletController {

    private User currentUser;

    @FXML private Button btnTabDeposit;
    @FXML private Button btnTabWithdraw;
    @FXML private Button btnSubmit;
    @FXML private Label lblLargeBalance;

    @FXML private TextField txtAmount;
    @FXML private TextField txtNote;

    private final String BLUE_STYLE = "-fx-background-color: #2563EB; -fx-text-fill: WHITE; -fx-background-radius: 8 0 0 8; -fx-cursor: hand; -fx-font-weight: bold;";
    private final String GRAY_STYLE = "-fx-background-color: #F1F5F9; -fx-text-fill: #64748B; -fx-background-radius: 0 8 8 0; -fx-cursor: hand; -fx-font-weight: bold;";

    private final Consumer<Message> onDepositSuccess = this::handleDepositSuccess;
    private final Consumer<Message> onDepositFailed = this::handleDepositFailed;
    private final Consumer<Message> onWithdrawSuccess = this::handleWithdrawSuccess;
    private final Consumer<Message> onWithdrawFailed = this::handleWithdrawFailed;

    @FXML
    public void initialize() {
        highlightDepositTab();
        registerHandlers();
    }

    private void registerHandlers() {
        MessageRouter.getInstance().register(ResponseCode.DEPOSIT_SUCCESS, onDepositSuccess);
        MessageRouter.getInstance().register(ResponseCode.DEPOSIT_FAILED, onDepositFailed);
        MessageRouter.getInstance().register(ResponseCode.WITHDRAW_SUCCESS, onWithdrawSuccess);
        MessageRouter.getInstance().register(ResponseCode.WITHDRAW_FAILED, onWithdrawFailed);
    }

    private void unregisterHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.DEPOSIT_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.DEPOSIT_FAILED);
        MessageRouter.getInstance().unregister(ResponseCode.WITHDRAW_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.WITHDRAW_FAILED);
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblLargeBalance != null) {
            DecimalFormat formatter = new DecimalFormat("#,###");
            lblLargeBalance.setText(formatter.format(currentUser.getBalance()) + " UETệ");
        }
    }

    @FXML
    void handleSwitchToDeposit(ActionEvent event) {
        highlightDepositTab();
    }

    private void highlightDepositTab() {
        if (btnTabDeposit != null && btnTabWithdraw != null && btnSubmit != null) {
            btnTabDeposit.setStyle("-fx-background-color: #2563EB; -fx-text-fill: WHITE; -fx-background-radius: 8 0 0 8;");
            btnTabWithdraw.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #64748B; -fx-background-radius: 0 8 8 0;");
            btnSubmit.setText("Xác Nhận Nạp Tiền");
        }
    }

    @FXML
    void handleSwitchToWithdraw(ActionEvent event) {
        if (btnTabDeposit != null && btnTabWithdraw != null && btnSubmit != null) {
            btnTabDeposit.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #64748B; -fx-background-radius: 8 0 0 8;");
            btnTabWithdraw.setStyle("-fx-background-color: #2563EB; -fx-text-fill: WHITE; -fx-background-radius: 0 8 8 0;");
            btnSubmit.setText("Xác Nhận Rút Tiền");
        }
    }

    @FXML
    void handleSubmitTransaction(ActionEvent event) {
        String amountStr = txtAmount.getText().trim();

        if (amountStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi nhập liệu", "Vui lòng nhập số tiền!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);

            if (currentUser == null) {
                showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không tìm thấy phiên đăng nhập!");
                return;
            }

            String actionTypeText = btnSubmit.getText();

            // Đẩy Socket lên luồng nền
            if (actionTypeText.contains("Nạp")) {
                CompletableFuture.runAsync(() -> {
                    SocketClient.getInstance().sendRequest(RequestCode.DEPOSIT_REQUEST, amount);
                });
            }
            else if (actionTypeText.contains("Rút")) {
                if (currentUser.getBalance() < amount) {
                    showAlert(Alert.AlertType.ERROR, "Giao dịch thất bại", "Số dư tài khoản không đủ để thực hiện yêu cầu rút tiền này!");
                    return;
                }

                String bankInfo = txtNote.getText().trim();
                if (bankInfo.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập thông tin ngân hàng để rút tiền!");
                    return;
                }

                CompletableFuture.runAsync(() -> {
                    SocketClient.getInstance().sendRequest(RequestCode.WITHDRAW_REQUEST, amount);
                });
            }

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Số tiền nhập vào phải là số hợp lệ!");
        }
    }

    private void handleDepositSuccess(Message msg) {
        Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Yêu cầu NẠP TIỀN đã được gửi, chờ Admin duyệt!");
            txtAmount.clear();
            if (txtNote != null) txtNote.clear();
        });
    }

    private void handleDepositFailed(Message msg) {
        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Nạp tiền thất bại", msg.getMessage()));
    }

    private void handleWithdrawSuccess(Message msg) {
        Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Yêu cầu RÚT TIỀN đã được gửi, chờ Admin duyệt!");
            txtAmount.clear();
            if (txtNote != null) txtNote.clear();
        });
    }

    private void handleWithdrawFailed(Message msg) {
        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Rút tiền thất bại", msg.getMessage()));
    }

    @FXML
    void handleBackToHome(ActionEvent event) {
        try {
            unregisterHandlers();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/The_Home_Page_Bidder_View.fxml"));
            Parent root = loader.load();

            Object controller = loader.getController();
            if (controller != null) {
                try {
                    controller.getClass().getMethod("setUserData", User.class).invoke(controller, this.currentUser);
                } catch (Exception ignored) {}
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi chuyển trang", "Không thể quay lại trang chủ: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}