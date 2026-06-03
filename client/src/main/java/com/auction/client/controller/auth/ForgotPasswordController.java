package com.auction.client.controller.auth;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ForgotPasswordController {

    @FXML private TextField txtUsername;
    @FXML private TextField txtPhone;
    @FXML private PasswordField txtNewPassword;

    private final Consumer<Message> onForgotSuccess = this::handleForgotSuccess;
    private final Consumer<Message> onForgotFailed = this::handleForgotFailed;

    @FXML
    public void initialize() {
        MessageRouter.getInstance().register(ResponseCode.FORGOT_PASSWORD_SUCCESS, onForgotSuccess);
        MessageRouter.getInstance().register(ResponseCode.FORGOT_PASSWORD_FAILED, onForgotFailed);
    }

    private void cleanupHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.FORGOT_PASSWORD_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.FORGOT_PASSWORD_FAILED);
    }

    @FXML
    void handleResetPassword(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String phone = txtPhone.getText().trim();
        String newPass = txtNewPassword.getText().trim();

        if (username.isEmpty() || phone.isEmpty() || newPass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đầy đủ tất cả thông tin!");
            return;
        }

        if (newPass.length() < 8) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Mật khẩu mới phải từ 8 ký tự trở lên!");
            return;
        }

        System.out.println("🔄 Đang gửi yêu cầu khôi phục mật khẩu qua Socket...");

        String[] payload = {username, phone, newPass};

        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.FORGOT_PASSWORD, payload);
        });
    }

    private void handleForgotSuccess(Message msg) {
        Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Mật khẩu của bạn đã được cập nhật thành công!");
            chuyenTrangLogin();
        });
    }

    private void handleForgotFailed(Message msg) {
        Platform.runLater(() -> {
            String errorMsg = msg.getMessage() != null ? msg.getMessage() : "Đặt lại mật khẩu thất bại!";
            showAlert(Alert.AlertType.ERROR, "Thất bại", errorMsg);
        });
    }

    @FXML
    void handleBackToLogin(ActionEvent event) {
        chuyenTrangLogin();
    }

    private void chuyenTrangLogin() {
        cleanupHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/auth/LoginView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Đăng Nhập Hệ Thống - Bidder Elite");
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi đồ họa", "Không thể quay lại trang đăng nhập!");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}