package com.auction.client.controller;

import com.auction.common.model.Items;
import com.auction.service.ItemService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class The_Home_Page_Bidder_View_Controller implements Initializable {
    @FXML private FlowPane flowPaneItem;
    private List<Items>  list= new ArrayList<>() ;
    private ItemService itemService = new ItemService();
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadItemsToUI();
    }

    public void loadItemsToUI() {
        List<Items> items = itemService.getAllItems();

        flowPaneItem.getChildren().clear();

        for (Items item : items) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ItemCard.fxml"));
                Parent card = loader.load();
                ItemCardController controller = loader.getController();
                controller.setData(item);

                flowPaneItem.getChildren().add(card);

            } catch (IOException e) {
                e.printStackTrace();
            }
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
