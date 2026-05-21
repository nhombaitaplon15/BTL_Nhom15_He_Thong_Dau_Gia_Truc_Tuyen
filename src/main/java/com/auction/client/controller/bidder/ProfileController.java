package com.auction.client.controller.bidder;

import com.auction.common.model.User;
import com.auction.client.controller.The_Home_Page_Bidder_View_Controller;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ProfileController {

    private User currentUser;

    @FXML private TextField txtUsername;
    @FXML private TextField txtFullName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;

    @FXML
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        // Đổ thông tin thật từ DB lên form giao diện
        if (txtUsername != null) {
            txtUsername.setText(user.getUsername());
            txtUsername.setEditable(false); // Khóa không cho sửa
        }
        if (txtFullName != null) {
            txtFullName.setText(user.getUsername());
            txtFullName.setEditable(false); // Khóa không cho sửa
        }
        if (txtEmail != null) {
            txtEmail.setText(user.getEmail());
            txtEmail.setEditable(false); // Khóa không cho sửa
        }
        if (txtPhone != null) {
            txtPhone.setText(user.getPhone());
            txtPhone.setEditable(false); // Khóa không cho sửa
        }
    }

    @FXML
    void handleUpdateProfile(ActionEvent event) {
        System.out.println(">>> Hệ thống cấu hình không cho phép chỉnh sửa thông tin cá nhân.");
    }

    @FXML
    void handleChangePassword(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bidder/ChangePasswordView.fxml"));
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
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
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
}