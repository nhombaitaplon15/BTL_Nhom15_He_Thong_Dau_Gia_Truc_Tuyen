package com.auction.client.controller;

import com.auction.common.model.Item;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class The_Home_Page_Seller_View_Controller {
    @FXML private AnchorPane homePane;
    @FXML private VBox insertItemSection;

    @FXML
    private StackPane mainContent;

    @FXML
    public void initialize() {
        // 2. BÀN GIAO "sân khấu" mainContent này cho ViewSwitcher giữ
        ViewSwitcher.setMainContentArea(mainContent);

        // 3. (Tùy chọn) Load luôn trang chủ lên làm mặc định khi vừa mở app
        ViewSwitcher.switchTo("HomeSellerView");
    }

    // Các hàm bắt sự kiện bấm nút ở menu để chuyển trang
    @FXML
    private void clickSanPhamCuaBan() {
        ViewSwitcher.switchTo("MPV");
    }
    @FXML
    private void clickHome() {
        ViewSwitcher.switchTo("HomeSellerView");
    }
    @FXML
    private void clickAuction() {
        ViewSwitcher.switchTo("AuctionManagementView");
    }
    @FXML
    private void clickAccount() {
        ViewSwitcher.switchTo("AccountView");
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
