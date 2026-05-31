package com.auction.client.controller.auth;




import com.auction.server.service.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ForgotPasswordController {

    @FXML
    private TextField txtUsername;

    @FXML
    private TextField txtPhone;

    @FXML
    private PasswordField txtNewPassword;

    // XỬ LÝ RESET MẬT KHẨU
    @FXML
    void handleResetPassword(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String phone = txtPhone.getText().trim();
        String newPass = txtNewPassword.getText().trim();

        // Kiểm tra dữ liệu đầu vào rỗng
        if (username.isEmpty() || phone.isEmpty() || newPass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đầy đủ tất cả thông tin!");
            return;
        }

        // Kiểm tra định dạng cơ bản để bảo vệ DB
        if (newPass.length() < 8) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Mật khẩu mới phải từ 8 ký tự trở lên!");
            return;
        }

        UserService userService = new UserService();

        try {
            // Gọi xuống hàm Quên mật khẩu của em ở UserService
            userService.handleForgotPassword(username, phone, newPass);

            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Mật khẩu của bạn đã được cập nhật thành công!");

            // Đổi mật khẩu xong tự động đẩy người dùng về lại trang Đăng nhập
            handleBackToLogin(event);

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Thất bại", e.getMessage());
        }
    }

    // CHUYỂN VỀ TRANG ĐĂNG NHẬP
    @FXML
    void handleBackToLogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Đăng Nhập Hệ Thống - Bidder Elite");
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