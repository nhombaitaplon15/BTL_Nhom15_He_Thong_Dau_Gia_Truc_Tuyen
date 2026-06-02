package com.auction.client.controller.auth;

import com.auction.client.controller.bidder.The_Home_Page_Bidder_View_Controller;
import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.User;
import com.auction.common.network.LoginDTO;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import java.text.Normalizer;
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

import java.util.function.Consumer;

public class LoginController {
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    // Các hàm lắng nghe phản hồi từ Server thông qua Socket
    private final Consumer<Message> onLoginSuccess = this::handleLoginSuccess;
    private final Consumer<Message> onLoginFailed = this::handleLoginFailed;

    @FXML
    public void initialize() {
        // Đăng ký nhận tín hiệu phản hồi đăng nhập khi khởi tạo màn hình
        MessageRouter.getInstance().register(ResponseCode.LOGIN_SUCCESS, onLoginSuccess);
        MessageRouter.getInstance().register(ResponseCode.LOGIN_FAILED, onLoginFailed);
    }

    private void cleanupHandlers() {
        // Dọn dẹp bộ nhớ: Hủy đăng ký khi rời khỏi màn hình Đăng Nhập
        MessageRouter.getInstance().unregister(ResponseCode.LOGIN_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.LOGIN_FAILED);
    }

    @FXML
    void handleForgotPassword(ActionEvent event) {
        cleanupHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/auth/ForgotPasswordView.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Khôi Phục Mật Khẩu");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể mở giao diện quên mật khẩu: " + e.getMessage());
        }
    }

    @FXML
    void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        username = Normalizer.normalize(username, Normalizer.Form.NFC);
        String password = txtPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        System.out.println("🔄 Đang gửi yêu cầu đăng nhập qua Socket cho User: " + username);
        System.out.println(">>> [DEBUG CLIENT] Chuẩn bị gửi lên Server | Tài khoản: [" + username + "] - Mật khẩu: [" + password + "]");
        // Gói dữ liệu và gửi thẳng qua Socket (Nhờ Server kiểm tra thay vì tự kiểm tra)
        LoginDTO loginData = new LoginDTO(username, password);
        SocketClient.getInstance().sendRequest(RequestCode.LOGIN, loginData);

        // (Không thực hiện chuyển trang ở đây, mà chờ tín hiệu từ Server ở hàm handleLoginSuccess bên dưới)
    }

    // Server phản hồi Đăng Nhập Thành Công
    private void handleLoginSuccess(Message msg) {
        Platform.runLater(() -> {
            User user = (User) msg.getPayload();
            System.out.println("🎉 Đăng nhập thành công! Quyền: " + user.getRole());

            // Lưu thông tin người dùng vào Session
            ClientSession.getInstance().setCurrentUser(user);

            // Tiến hành chuyển trang
            chuyenTrangChu(user.getRole(), user);
        });
    }

    // Server phản hồi Đăng Nhập Thất Bại (Sai mật khẩu, bị khoá...)
    private void handleLoginFailed(Message msg) {
        Platform.runLater(() -> {
            String errorMsg = msg.getMessage() != null ? msg.getMessage() : "Tài khoản hoặc mật khẩu không chính xác!";
            showAlert(Alert.AlertType.ERROR, "Đăng nhập thất bại", errorMsg);
        });
    }

    @FXML
    public void handleGoToRegister(ActionEvent event) {
        cleanupHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/auth/RegisterView.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Đăng Ký Thành Viên");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi đồ họa", "Không thể nạp giao diện đăng ký: " + e.getMessage());
        }
    }

    private void chuyenTrangChu(String role, User user) {
        cleanupHandlers();
        String fxmlFile;
        try {
            if ("ADMIN".equalsIgnoreCase(role)) {
                fxmlFile = "/view/view/admin/The_Home_Page_Admin_View.fxml";
            } else {
                fxmlFile = "/view/view/bidder/The_Home_Page_Bidder_View.fxml";
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();

            if (!"ADMIN".equalsIgnoreCase(role)) {
                The_Home_Page_Bidder_View_Controller homeController = loader.getController();
                homeController.setUserData(user);
            }

            // Lấy giao diện hiển thị hiện tại qua trường txtUsername
            Stage stage = (Stage) txtUsername.getScene().getWindow();
            Scene scene = new Scene(root, 1280, 720);

            stage.setScene(scene);
            stage.setTitle("Elite Auction - Trang chủ hệ thống");
            stage.setMaximized(true);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể tải giao diện trang chủ! Chi tiết: " + e.getMessage());
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