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
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;

public class ProfileController {

    private User currentUser;

    @FXML private TextField txtUsername;
    @FXML private TextField txtFullName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;

    @FXML
    public void initialize() {
        MessageRouter.getInstance().register(ResponseCode.PROFILE_RESULT, this::handleProfileResult);
        MessageRouter.getInstance().register(ResponseCode.PROFILE_UPDATED, this::handleProfileUpdated);
    }

    private void handleProfileResult(Message msg) {
        Platform.runLater(() -> {
            User freshUser = (User) msg.getPayload();
            if (freshUser != null) {
                this.currentUser = freshUser;
                if (txtUsername != null) {
                    txtUsername.setText(freshUser.getUsername());
                    txtUsername.setEditable(false);
                }
                if (txtFullName != null) {
                    txtFullName.setText(freshUser.getUsername());
                    txtFullName.setEditable(false);
                }
                if (txtEmail != null) {
                    txtEmail.setText(freshUser.getEmail());
                    txtEmail.setEditable(true);
                }
                if (txtPhone != null) {
                    txtPhone.setText(freshUser.getPhone());
                    txtPhone.setEditable(true);
                }
            }
        });
    }

    private void handleProfileUpdated(Message msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Cập nhật thông tin cá nhân thành công!");
            alert.showAndWait();
        });
    }

    @FXML
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        // ĐẨY VÀO LUỒNG NỀN
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
        });
    }

    @FXML
    void handleUpdateProfile(ActionEvent event) {
        if (currentUser == null) return;

        if (txtEmail != null) currentUser.setEmail(txtEmail.getText());
        if (txtPhone != null) currentUser.setPhone(txtPhone.getText());

        // ĐẨY VÀO LUỒNG NỀN ĐỂ KHÔNG ĐƠ NÚT LƯU
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.UPDATE_PROFILE, currentUser);
        });
    }

    @FXML
    void handleChangePassword(ActionEvent event) {
        cleanupHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/ChangePasswordView.fxml"));
            Parent root = loader.load();

            ChangePasswordController controller = loader.getController();
            if (controller != null) {
                controller.setUserData(this.currentUser);
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleBackToHome(ActionEvent event) {
        cleanupHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/The_Home_Page_Bidder_View.fxml"));
            Parent root = loader.load();

            The_Home_Page_Bidder_View_Controller home = loader.getController();
            if (home != null) {
                home.setUserData(this.currentUser);
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.PROFILE_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.PROFILE_UPDATED);
    }
}