package com.auction.client;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;

    // Logic xử lý khi nhấn nút Đăng nhập
    @FXML
    public void handleLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            messageLabel.setText("Vui lòng nhập đầy đủ thông tin!");
        } else {
            // Tạm thời in ra console để kiểm tra logic
            System.out.println("Đang gửi yêu cầu đăng nhập cho: " + user);

            // Bước tiếp theo sau này: Gửi đối tượng Message qua Socket cho Server
            messageLabel.setText("Đang kết nối tới máy chủ...");
        }
    }
}
