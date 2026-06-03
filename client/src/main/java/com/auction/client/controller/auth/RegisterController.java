package com.auction.client.controller.auth;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.network.Message;
import com.auction.common.network.RegisterDTO;
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
import java.text.Normalizer;

public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;

    private final Consumer<Message> onRegisterSuccess = this::handleRegisterSuccess;
    private final Consumer<Message> onRegisterFailed = this::handleRegisterFailed;

    @FXML
    public void initialize() {
        MessageRouter.getInstance().register(ResponseCode.REGISTER_SUCCESS, onRegisterSuccess);
        MessageRouter.getInstance().register(ResponseCode.REGISTER_FAILED, onRegisterFailed);
    }

    private void cleanupHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.REGISTER_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.REGISTER_FAILED);
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        username = Normalizer.normalize(username, Normalizer.Form.NFC);
        String password = txtPassword.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đầy đủ tất cả thông tin!");
            return;
        }

        RegisterDTO registerData = new RegisterDTO(username, password, email, phone, "BIDDER");

        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.REGISTER, registerData);
        });
    }

    private void handleRegisterSuccess(Message msg) {
        Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Xác nhận", "Đăng ký thành công!\nVui lòng đăng nhập để tiếp tục.");
            chuyenTrangLogin();
        });
    }

    private void handleRegisterFailed(Message msg) {
        Platform.runLater(() -> {
            String errorMsg = msg.getMessage() != null ? msg.getMessage() : "Đăng ký thất bại do lỗi hệ thống!";
            showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", errorMsg);
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void chuyenTrangLogin() {
        cleanupHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/auth/LoginView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Đăng Nhập Hệ Thống");
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi điều hướng", "Không thể tải giao diện đăng nhập!");
        }
    }

    @FXML
    public void Welcome_back(ActionEvent event){
        cleanupHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/auth/LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            stage.setScene(scene);
            stage.setTitle("Đăng Nhập Hệ Thống");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}