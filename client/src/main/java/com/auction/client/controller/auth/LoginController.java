package com.auction.client.controller.auth;

import com.auction.client.controller.bidder.The_Home_Page_Bidder_View_Controller;
import com.auction.common.model.User;
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

public class LoginController {
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;

    // Khai báo UserService kết nối DB
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

            // Gọi kết nối Database thật qua tầng Service
            User user = userService.handleLogin(username, password);

            if (user != null) {
                System.out.println("🎉 Đăng nhập thành công! Quyền: " + user.getRole());
                // Chuyển vào container tổng của ứng dụng
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
            // Gọi thẳng trang giao diện con vì nó đã tích hợp sẵn Sidebar menu của riêng nó
            if ("ADMIN".equalsIgnoreCase(role)) {
                fxmlFile = "/view/The_Home_Page_Admin_View.fxml";
            } else {
                fxmlFile = "/view/The_Home_Page_Bidder_View.fxml";
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();

            if (!"ADMIN".equalsIgnoreCase(role)) {
                // Ép kiểu controller để truyền dữ liệu User vừa lấy từ Database Railway sang trang chủ
                The_Home_Page_Bidder_View_Controller homeController = loader.getController();
                homeController.setUserData(user);
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1280, 720);

            stage.setScene(scene);
            stage.setTitle("Elite Auction - Trang chủ hệ thống");
            stage.setMaximized(true); // Giữ tính năng phóng to toàn màn hình cho đẹp
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