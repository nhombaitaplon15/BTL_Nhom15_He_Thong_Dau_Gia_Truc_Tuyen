package com.auction.client.controller;


import com.auction.common.model.User;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.UserDAO;
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
    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtPassword;
    @FXML
    public void Open_the_home_page(ActionEvent event) {
        String inputUser = txtUsername.getText();
        String inputPass = txtPassword.getText();

        UserDAO userDAO = new UserDAO();
        try {

            User user = userDAO.checkLogin(inputUser, inputPass);

            if (user != null) {
                // In ra console để kiểm tra
                System.out.println("Đăng nhập thành công!");
                System.out.println("Chào: " + user.getUsername());

                // Hiện Alert thông báo
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Xác nhận");
                alert.setHeaderText("Kết nối Database thành công");
                alert.setContentText("Hệ thống đã tìm thấy người dùng: " + user.getUsername()
                        + "\nVai trò: " + user.getRole());
                alert.showAndWait();


            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Thất bại");
                alert.setContentText("Sai tài khoản hoặc mật khẩu rồi bạn ơi!");
                alert.showAndWait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Hàm hiện thông báo (Alert) để người dùng thấy
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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