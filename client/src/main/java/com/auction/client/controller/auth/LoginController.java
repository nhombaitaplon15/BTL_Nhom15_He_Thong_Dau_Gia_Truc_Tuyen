package com.auction.client.controller.auth;

import com.auction.client.controller.bidder.MainContainerController;
import com.auction.client.controller.bidder.The_Home_Page_Bidder_View_Controller;
import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.User;
import com.auction.common.network.LoginDTO;
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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * LoginController — Đăng nhập qua Socket (KHÔNG gọi DB trực tiếp).
 *
 * FIX CHÍNH: Trước đây LoginController gọi userService.handleLogin() trực tiếp
 * (bypass Socket), khiến Server không bao giờ set loggedInUserId trên ClientHandler.
 * Kết quả: mọi request sau đều bị server trả về "Bạn chưa đăng nhập!".
 *
 * Sau khi sửa: Login gửi qua Socket → Server xử lý → set loggedInUserId → trả
 * LOGIN_SUCCESS kèm User → Client lưu vào ClientSession → điều hướng trang chủ.
 */
public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;

    @FXML
    public void initialize() {
        // Đảm bảo Socket đã kết nối khi vào màn hình Login
        if (!SocketClient.getInstance().isConnected()) {
            SocketClient.getInstance().connect();
        }

        // Đăng ký lắng nghe phản hồi login từ Server
        MessageRouter.getInstance().register(ResponseCode.LOGIN_SUCCESS, this::handleLoginSuccess);
        MessageRouter.getInstance().register(ResponseCode.LOGIN_FAILED, this::handleLoginFailed);
    }

    @FXML
    void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        if (!SocketClient.getInstance().isConnected()) {
            SocketClient.getInstance().connect();
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Đang kết nối lại server, vui lòng thử lại sau!");
            return;
        }

        if (btnLogin != null) btnLogin.setDisable(true);

        // GỬI QUA SOCKET — Server sẽ xác thực và set loggedInUserId trên ClientHandler
        SocketClient.getInstance().sendRequest(RequestCode.LOGIN, new LoginDTO(username, password));
    }

    /**
     * Nhận phản hồi LOGIN_SUCCESS từ Server qua Socket.
     * Server đã set loggedInUserId trên ClientHandler → mọi request sau đều hợp lệ.
     */
    private void handleLoginSuccess(Message message) {
        Platform.runLater(() -> {
            if (btnLogin != null) btnLogin.setDisable(false);

            if (!(message.getPayload() instanceof User)) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Dữ liệu người dùng không hợp lệ từ Server!");
                return;
            }

            User user = (User) message.getPayload();
            System.out.println("✅ Đăng nhập thành công qua Socket! Role: " + user.getRole());

            // Lưu vào ClientSession để các Controller khác dùng
            ClientSession.getInstance().setCurrentUser(user);

            // Hủy đăng ký listener login để tránh lặp
            MessageRouter.getInstance().unregister(ResponseCode.LOGIN_SUCCESS);
            MessageRouter.getInstance().unregister(ResponseCode.LOGIN_FAILED);

            navigateToHome(user);
        });
    }

    private void handleLoginFailed(Message message) {
        Platform.runLater(() -> {
            if (btnLogin != null) btnLogin.setDisable(false);
            String reason = (message != null && message.getMessage() != null && !message.getMessage().isBlank())
                    ? message.getMessage()
                    : "Tài khoản hoặc mật khẩu không chính xác!";
            showAlert(Alert.AlertType.ERROR, "Đăng nhập thất bại", reason);
        });
    }

    private void navigateToHome(User user) {
        try {
            String fxmlFile;
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                fxmlFile = "/view/view/admin/The_Home_Page_Admin_View.fxml";
            } else {
                fxmlFile = "/view/view/bidder/Maincontainer.fxml";
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();

            // SỬA: Lấy đúng kiểu controller tương ứng với từng role
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                // Admin không cần truyền user qua setUserData ở đây
            } else {
                Object controller = loader.getController();
                if (controller instanceof MainContainerController) {
                    ((MainContainerController) controller).setUserData(user);
                } else if (controller instanceof The_Home_Page_Bidder_View_Controller) {
                    ((The_Home_Page_Bidder_View_Controller) controller).setUserData(user);
                }
            }

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            Scene scene = new Scene(root, 1280, 720);
            stage.setScene(scene);
            stage.setTitle("Elite Auction - Hệ thống Sàn Đấu Giá");
            stage.setMaximized(true);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống",
                    "Không thể tải giao diện trang chủ! Chi tiết: " + e.getMessage());
        }
    }
    @FXML
    void handleForgotPassword(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/ForgotPasswordView.fxml"));
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
    public void handleGoToRegister(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/RegisterView.fxml"));
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}