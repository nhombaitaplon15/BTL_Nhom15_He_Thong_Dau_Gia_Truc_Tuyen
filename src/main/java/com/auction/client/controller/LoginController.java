package com.auction.client.controller;

import com.auction.common.model.User;
import com.auction.service.UserService;
// Khởi tạo hoặc Injection UserService tùy theo cấu trúc của em (ví dụ qua RMI hoặc Client Service Factory)
// Ở đây anh khai báo sẵn một biến để em sử dụng trực tiếp.

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    // Khai báo UserService kết nối DB của em
    private final UserService userService = new UserService();

    @FXML
    void handleForgotPassword(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ForgotPasswordView.fxml"));
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
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        try {
            System.out.println("🔄 Đang xác thực tài khoản qua UserService: " + username);

            // THAY THẾ GIẢ LẬP BẰNG USER SERVICE THẬT CỦA EM
            User user = userService.handleLogin(username, password);

            if (user != null) {
                System.out.println("🎉 Đăng nhập thành công! Quyền: " + user.getRole());
                // Truyền hẳn thực thể User sang hàm chuyển trang chủ
                chuyenTrangChu(event, user.getRole(), user);
            } else {
                showAlert(Alert.AlertType.ERROR, "Đăng nhập thất bại", "Tài khoản hoặc mật khẩu không chính xác!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối đến máy chủ: " + e.getMessage());
        }
    }

    @FXML
    public void handleGoToRegister(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/RegisterView.fxml"));
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


    private void chuyenTrangChu(ActionEvent event, String role, User user) {
        String fxmlFile;
        try {
            if ("ADMIN".equalsIgnoreCase(role)) {
                fxmlFile = "/view/The_Home_Page_Admin_View.fxml";
            } else {
                fxmlFile = "/view/The_Home_Page_Bidder_View.fxml";
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();

            // NẾU LÀ BIDDER: Lấy đúng Controller và truyền dữ liệu động vào
            if (!"ADMIN".equalsIgnoreCase(role)) {
                The_Home_Page_Bidder_View_Controller homeController = loader.getController();
                homeController.setUserData(user);
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1280, 720);

            stage.setScene(scene);
            stage.setTitle("Elite Auction - Trang chủ hệ thống");
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