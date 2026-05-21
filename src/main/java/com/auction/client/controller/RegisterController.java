package com.auction.client.controller;

import com.auction.exception.AuctionException;
import com.auction.service.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;

    private static UserService userService = new UserService();

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đầy đủ tất cả thông tin!");
            return;
        }

        try {
            // 1. Đăng ký tài khoản xuống DB
            userService.handleRegister(username, password, email, phone);

            // 2. Hiện thông báo thành công trước
            showAlert(Alert.AlertType.INFORMATION, "Xác nhận",
                    "Đăng ký thành công!\nChào mừng thành viên mới: " + username);

            // 3. Tiến hành chuyển trang (Lỗi thường phát sinh ở đây do sai đường dẫn FXML)
            chuyenTrangChu(event);

        } catch (AuctionException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", e.getMessage());
        } catch (Exception e) {
            // In chi tiết lỗi ra console để em kiểm tra lỗi gì nếu chuyển trang thất bại
            System.err.println("=== LỖI PHÁT SINH KHI ĐĂNG KÝ/CHUYỂN TRANG ===");
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Đăng ký xong nhưng lỗi điều hướng: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void chuyenTrangChu(ActionEvent event) {
        try {
            // ⚠️ EM KIỂM TRA KỸ ĐƯỜNG DẪN NÀY:
            // Nếu file nằm trong thư mục bidder thì phải là: "/view/bidder/The_Home_Page_Bidder_View.fxml"
            String fxmlPath = "/view/The_Home_Page_Bidder_View.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            System.err.println("=== LỖI KHÔNG TÌM THẤY FILE FXML TRANG CHỦ ===");
            e.printStackTrace(); // In lỗi đỏ lòm ở terminal để check chuẩn tên file
            showAlert(Alert.AlertType.ERROR, "Lỗi điều hướng", "Không thể tải giao diện trang chủ! Kiểm tra lại tên file FXML.");
        }
    }

    @FXML
    public void Welcome_back(ActionEvent event){
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}