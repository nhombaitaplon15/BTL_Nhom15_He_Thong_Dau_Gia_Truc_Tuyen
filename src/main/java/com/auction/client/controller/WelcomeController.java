package com.auction.client.controller;

import com.auction.common.model.Bidder;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.factory.UserFactory;
import com.auction.server.dao.UserDAO;
import com.auction.service.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;

public class WelcomeController {
    @FXML private AnchorPane loginPane;
    @FXML private AnchorPane forgotPasswordStep1Pane;
    @FXML private AnchorPane forgotPasswordFailedPane;
    @FXML private AnchorPane forgotPasswordStep2Pane;
    @FXML private AnchorPane loginFailedPane;
    @FXML private AnchorPane registerPane;
    @FXML private AnchorPane registeredSuccessfullyPane;

    private UserService userService = new UserService();
    private String verifiedUsername = "";

    @FXML private TextField txtUsernameLogin;
    @FXML private PasswordField txtPasswordLogin;

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;

    @FXML private TextField txtUsernameForgot;
    @FXML private TextField txtEmailForgot;
    @FXML private TextField txtPhoneForgot;

    @FXML private PasswordField txtPasswordForgot1;
    @FXML private PasswordField txtPasswordForgot2;


    @FXML
    public void handleLogin(ActionEvent event) {
        String inputUser = txtUsernameLogin.getText().trim();
        String inputPass = txtPasswordLogin.getText().trim();

        if (inputUser.isEmpty() || inputPass.isEmpty()) {
            loginFailedPane.setVisible(true);
            return;
        }
        try {
            User user = new Bidder ();
            user.setUsername(inputUser);
            user.setPassword(inputPass);

            // Gọi qua Service, hàm handleLogin trong Service của bạn sẽ check khóa tài khoản, sai pass, v.v.
            User loggedInUser = userService.handleLogin(user);
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();
        } catch (Exception e) {
            loginFailedPane.setVisible(true);
            e.getMessage();
        }
    }

    public void handleRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        try {
            // Khởi tạo đối tượng request
            User userRequest = UserFactory.createUser(0, username, email, password, phone, "ACTIVE", "BIDDER", 0.0);
            if (userService.handleRegister(userRequest)) {
                registeredSuccessfullyPane.setVisible(true);
            }
        } catch (AuctionException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi đăng ký", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Đã xảy ra lỗi: " + e.getMessage());
        }
    }
    public void handleForgotPassword(ActionEvent event){
        String username = txtUsernameForgot .getText().trim();
        String email = txtEmailForgot.getText().trim();
        String phone = txtPhoneForgot.getText().trim();

        try {
            userService.verifyIdentityForReset(username, phone, email);
            this.verifiedUsername = username;
            forgotPasswordStep2Pane.setVisible(true);
            forgotPasswordStep1Pane.setVisible(false);
        }catch (AuctionException e) {
            // Thông tin sai, báo lỗi
            showAlert(Alert.AlertType.ERROR, "Lỗi xác minh", e.getMessage());
        }

    }
    public void executeResetPassword(ActionEvent event){
        String password1 = txtPasswordForgot1.getText().trim();
        String password2 = txtPasswordForgot2.getText().trim();
        try {

            if (password1.isEmpty() || password2.isEmpty()) {
                throw new AuctionException("INVALID_INPUT", "Vui lòng nhập đầy đủ mật khẩu mới!");
            }
            if (password1.length() < 8) {
                throw new AuctionException("INVALID_INPUT", "Mật khẩu phải có ít nhất 8 ký tự!");
            }
            if (!password1.equals(password2)) {
                throw new AuctionException("INVALID_INPUT", "Mật khẩu nhập lại không khớp!");
            }
            if (userService.executeResetPassword(this.verifiedUsername, password1)) {
                 showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đổi mật khẩu thành công! Vui lòng đăng nhập lại.");

                this.verifiedUsername = "";
                forgotPasswordStep2Pane.setVisible(false);
                loginPane.setVisible(true);
            }

        } catch (AuctionException e) {
            // In ra lỗi (Ví dụ: "Mật khẩu nhập lại không khớp!")
            System.err.println("Lỗi: " + e.getMessage());
             showAlert(Alert.AlertType.ERROR, "Lỗi đổi mật khẩu", e.getMessage());
        }
    }


    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    @FXML public void buttonOK(ActionEvent event) {
        loginFailedPane.setVisible(false);
        registeredSuccessfullyPane.setVisible(false);
        registerPane.setVisible(false) ;
        forgotPasswordFailedPane.setVisible(false);

    }
    @FXML public void buttonTTK(ActionEvent event) {
        registerPane.setVisible(true);
        loginPane.setVisible(false);
    }
    @FXML public void buttonExit(ActionEvent event) {
        registerPane.setVisible(false);
        forgotPasswordStep1Pane.setVisible(false);
        loginPane.setVisible(true);

    }
    @FXML public void buttonExit1(ActionEvent event) {
        forgotPasswordStep2Pane.setVisible(false);
        forgotPasswordStep1Pane.setVisible(true);

    }
    @FXML public void buttonforgotPassword(ActionEvent event) {
        forgotPasswordStep1Pane.setVisible(true);
        loginPane.setVisible(false);
    }
    @FXML public void buttonContinue(ActionEvent event) {
        forgotPasswordFailedPane.setVisible(true);
    }






}
