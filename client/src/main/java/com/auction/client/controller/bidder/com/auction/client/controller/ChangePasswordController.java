package com.auction.client.controller;

import com.auction.common.model.User;
import com.auction.server.service.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class ChangePasswordController {

    private final UserService userService = new UserService();
    private User currentUser;

    @FXML private PasswordField txtOldPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;

    @FXML
    public void setUserData(User user) {
        this.currentUser = user;
    }

    @FXML
    void handleUpdatePassword(ActionEvent event) {
        String oldPass = txtOldPassword != null ? txtOldPassword.getText() : "";
        String newPass = txtNewPassword != null ? txtNewPassword.getText() : "";
        String confirmPass = txtConfirmPassword.getText();

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi nhập liệu", "Vui lòng nhập đầy đủ các trường mật khẩu!");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            showAlert(Alert.AlertType.WARNING, "Mật khẩu không khớp", "Mật khẩu mới và mật khẩu nhập lại không trùng nhau!");
            return;
        }

        try {
            // GỌI CHÍNH XÁC HÀM TRONG USERSERVICE CỦA EM
            userService.handleChangePassword(currentUser, oldPass, newPass, confirmPass);

            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Thay đổi mật khẩu tài khoản thành công!");

            // Xóa sạch chữ trên form sau khi đổi thành công
            if (txtOldPassword != null) txtOldPassword.clear();
            if (txtNewPassword != null) txtNewPassword.clear();
            txtConfirmPassword.clear();

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Thất bại", e.getMessage());
        }
    }

    @FXML
    void handleBackToProfile(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bidder/ProfileView.fxml"));
            Parent root = loader.load();

            ProfileController profile = loader.getController();
            if (profile != null) {
                profile.setUserData(this.currentUser);
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}