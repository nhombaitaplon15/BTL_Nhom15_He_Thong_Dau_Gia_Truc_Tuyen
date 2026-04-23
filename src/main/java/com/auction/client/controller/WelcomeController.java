package com.auction.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class WelcomeController {
    @FXML
    public void Open_login_screen(ActionEvent event){
        System.out.println("Loading...");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/client/view/LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    @FXML
    public void Open_the_registration_screen(ActionEvent event){
        System.out.println("Loading...");
        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo2/view/RegisterView.fxml"));
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
