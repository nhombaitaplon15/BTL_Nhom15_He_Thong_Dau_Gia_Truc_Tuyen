package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.User;
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
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;

public class ChangePasswordController {

    private User currentUser;

    @FXML private PasswordField txtOldPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;

    @FXML
    public void initialize() {
        MessageRouter.getInstance().register(ResponseCode.PASSWORD_CHANGED, this::handlePasswordChanged);
        MessageRouter.getInstance().register(ResponseCode.PASSWORD_CHANGE_FAILED, this::handlePasswordChangeFailed);
    }

    public void setUserData(User user) {
        this.currentUser = user;
    }

    private void handlePasswordChanged(Message msg) {
        Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Thay đổi mật khẩu tài khoản thành công!");
            if (txtOldPassword != null) txtOldPassword.clear();
            if (txtNewPassword != null) txtNewPassword.clear();
            if (txtConfirmPassword != null) txtConfirmPassword.clear();
        });
    }

    private void handlePasswordChangeFailed(Message msg) {
        Platform.runLater(() -> {
            showAlert(Alert.AlertType.ERROR, "Thất bại", msg.getMessage() != null ? msg.getMessage() : "Đổi mật khẩu thất bại!");
        });
    }

    @FXML
    void handleUpdatePassword(ActionEvent event) {
        String oldPass = txtOldPassword != null ? txtOldPassword.getText() : "";
        String newPass = txtNewPassword != null ? txtNewPassword.getText() : "";
        String confirmPass = txtConfirmPassword != null ? txtConfirmPassword.getText() : "";

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi nhập liệu", "Vui lòng nhập đầy đủ các trường mật khẩu!");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            showAlert(Alert.AlertType.WARNING, "Mật khẩu không khớp", "Mật khẩu mới và mật khẩu nhập lại không trùng nhau!");
            return;
        }

        String[] payload = new String[]{oldPass, newPass, confirmPass};
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.CHANGE_PASSWORD, payload);
        });
    }

    @FXML
    void handleBackToProfile(ActionEvent event) {
        MessageRouter.getInstance().unregister(ResponseCode.PASSWORD_CHANGED);
        MessageRouter.getInstance().unregister(ResponseCode.PASSWORD_CHANGE_FAILED);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/ProfileView.fxml"));
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