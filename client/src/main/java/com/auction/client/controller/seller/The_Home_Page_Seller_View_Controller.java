package com.auction.client.controller.seller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class The_Home_Page_Seller_View_Controller {

    @FXML
    private StackPane mainContent;

    @FXML
    public void initialize() {
        // Bàn giao mainContent cho ViewSwitcher quản lý
        ViewSwitcher.setMainContentArea(mainContent);

        // THÊM MỚI: Nối dây để nhận tín hiệu chuyển tab từ màn hình Trang chủ
        HomeSellerController.onRequireSwitchToMyProducts = () -> {
            clickSanPhamCuaBan(); // Khi nhận tín hiệu, tự động gọi hàm chuyển trang bên dưới
        };

        // Load trang tổng quan bán hàng làm mặc định
        ViewSwitcher.switchTo("HomeSellerView");
    }

    // Các hàm chuyển trang từ Menu
    @FXML
    private void clickSanPhamCuaBan() {
        ViewSwitcher.switchTo("MyProductsView");
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
}