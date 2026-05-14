package com.auction.client.controller;

import com.auction.common.model.Items;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class The_Home_Page_Seller_View_Controller {
    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    private BorderPane mainBorderPane;
    private void loadView(String fxmlFileName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + fxmlFileName + ".fxml"));
            Parent view = loader.load();
            mainBorderPane.setCenter(view);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Không tìm thấy file giao diện: " + fxmlFileName);
        }
    }

    @FXML
    public void showSearchItem(ActionEvent event){
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/SearchItem.fxml"));
            Parent searchView = loader.load();

            // Chỉnh tọa độ (X, Y) để form tìm kiếm nằm ở giữa hoặc vị trí bạn muốn
            searchView.setLayoutX(79);
            searchView.setLayoutY(23);

            // Thêm vào AnchorPane ngoài cùng và đẩy nó lên lớp trên cùng
            mainAnchorPane.getChildren().add(searchView);
            searchView.toFront();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void Welcome_back(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/WelcomeView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
