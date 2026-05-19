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

    // ĐÃ BỎ: Các thuộc tính sellerRadio và bidderRadio phiền phức

    private static UserService userService = new UserService();

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        // Kiểm tra nhanh ở Client xem các ô nhập liệu có bị trống không
        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đầy đủ tất cả thông tin!");
            return;
        }

        try {
            // Gọi thẳng Service truyền các chuỗi String trực tiếp, không lo lớp trừu tượng nữa!
            userService.handleRegister(username, password, email, phone);

            showAlert(Alert.AlertType.INFORMATION, "Xác nhận",
                    "Đăng ký thành công!\nChào mừng thành viên mới: " + username);

            // Mặc định chuyển thẳng sang Trang chủ của Bidder (Người mua)
            chuyenTrangChu(event);

        } catch (AuctionException e) {
            // Bắt các lỗi cụ thể từ Service ném ra (Trùng tên đăng nhập, số điện thoại sai định dạng,...)
            showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Đã xảy ra lỗi: " + e.getMessage());
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
            // Mặc định điều hướng vào trang chủ Bidder sau khi đăng ký thành công
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.centerOnScreen(); // Đưa giao diện ra giữa màn hình
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tải giao diện trang chủ!");
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