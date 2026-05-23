package com.auction.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class The_Settings_Page_Admin_View_Controller {

    @FXML
    public void goToHomePage(ActionEvent event) {
        switchPage(event, "/view/The_Home_Page_Admin_View.fxml");
    }

    @FXML
    public void goToAuctionPage(ActionEvent event) {
        switchPage(event, "/view/The_Auction_Page_Admin_View.fxml");
    }

    @FXML
    public void goToTransactionPage(ActionEvent event) {
        switchPage(event, "/view/The_Transaction_Page_Admin_View.fxml");
    }

    private void switchPage(ActionEvent event, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            Stage stage = (Stage) ((Node) event.getSource())
                    .getScene()
                    .getWindow();

            stage.setScene(scene);

            stage.setMaximized(true);

            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
