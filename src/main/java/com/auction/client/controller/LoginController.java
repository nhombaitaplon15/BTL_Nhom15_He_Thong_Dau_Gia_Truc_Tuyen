package com.auction.client.controller;


import com.auction.common.model.User;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.UserDAO;
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


public class LoginController {
  @FXML
  private TextField txtUsername;
  @FXML
  private PasswordField txtPassword;

  @FXML
  public void handleLogin(ActionEvent event) {
    String inputUser = txtUsername.getText().trim();
    String inputPass = txtPassword.getText().trim();

    if (inputUser.isEmpty() || inputPass.isEmpty()) {
      showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
      return;
    }

    UserDAO  userDAO = new UserDAO();

    try {
      User user = userDAO.checkLogin(inputUser, inputPass);

      showAlert(Alert.AlertType.INFORMATION, "Xác nhận",
          "Đăng nhập thành công!\nChào: " + user.getUsername() + "\nVai trò: " + user.getRole());

      chuyenTrangChu(event, user.getRole());

    } catch (Exception e) {
      showAlert(Alert.AlertType.ERROR, "Đăng nhập thất bại", e.getMessage());
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
    String fxmlFile;
    try {
      if ("ADMIN".equalsIgnoreCase(role)) {
        fxmlFile = "/view/The_Home_Page_Admin_View.fxml";
      } else if ("SELLER".equalsIgnoreCase(role)) {
        fxmlFile = "/view/The_Home_Page_Seller_View.fxml";
      } else {
        fxmlFile = "/view/The_Home_Page_Bidder_View.fxml";
      }
      javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(fxmlFile));
      javafx.scene.Parent root = loader.load();

      javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
      stage.setScene(new javafx.scene.Scene(root));
      stage.show();
    } catch (Exception e) {
      e.printStackTrace();
      showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể tải giao diện trang chủ!");
    }
  }

  @FXML
  public void Welcome_back(ActionEvent event) {
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