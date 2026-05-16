package com.auction.client.controller;

import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.factory.UserFactory;
import com.auction.server.dao.UserDAO;
import com.auction.service.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;



public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;

    private static UserDAO userDAO = new UserDAO();
    private double balance;

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        try {
            User finalUser = UserFactory.createUser(0, username, email, password, phone, "ACTIVE", "BIDDER", 0.0);
            showAlert(Alert.AlertType.INFORMATION, "Xác nhận",
                "Đăng kí thành công!\nChào: " + username );
            userDAO.register(finalUser);

            chuyenTrangChu(event);;

        } catch (AuctionException e) {
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource( "/view/The_Home_Page_Bidder_View.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tải giao diện trang chủ!");
        }
    }

    @FXML
    public void Welcome_back(ActionEvent event){
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/WelcomeView.fxml"));
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
