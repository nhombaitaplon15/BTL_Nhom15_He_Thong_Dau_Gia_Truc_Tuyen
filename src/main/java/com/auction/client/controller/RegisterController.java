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
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;



public class RegisterController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private RadioButton sellerRadio;
    @FXML private RadioButton bidderRadio;

    private static UserService userService = new UserService();
    private double balance;

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        String role = "";
        if (sellerRadio.isSelected()) role = "SELLER";
        else if (bidderRadio.isSelected()) role = "BIDDER";

        if (role.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng điền đủ thông tin!");
            return;
        }
        try {
            userService.handleRegister(username, password, email, phone, role, balance);
            showAlert(Alert.AlertType.INFORMATION, "Xác nhận",
                "Đăng kí thành công!\nChào: " + username + "\nVai trò: " + role);

            chuyenTrangChu(event, role);;

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

    private void chuyenTrangChu(ActionEvent event, String role) {
        try {
            String fxmlFile = role.equals("SELLER") ? "/view/The_Home_Page_Seller_View.fxml" : "/view/The_Home_Page_Bidder_View.fxml";
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
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
