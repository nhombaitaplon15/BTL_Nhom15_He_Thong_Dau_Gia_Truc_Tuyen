package com.auction.client.controller.bidder;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import java.io.IOException;

public class MainController {

    // Inject BorderPane tổng từ FXML vào code
    @FXML
    private BorderPane mainBorderPane;

    private void loadPage(String fxmlFileName) {
        try {
            // Giả sử các file fxml phụ của bạn cũng nằm trong thư mục /view/ giống file chính
            // Ví dụ: "/view/SanDauGia.fxml" hoặc "/view/LichSuDatGia.fxml"
            Parent root = FXMLLoader.load(getClass().getResource("/view/" + fxmlFileName));

            if (root == null) {
                System.out.println("Không tìm thấy file: /view/" + fxmlFileName);
                return;
            }

            // Đưa giao diện mới vào vùng giữa của BorderPane
            mainBorderPane.setCenter(root);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Lỗi khi load trang: " + fxmlFileName);
        }
    }
    // Sự kiện khi bấm nút "Sàn Đấu Giá"
    @FXML
    void handleSanDauGia(ActionEvent event) {
        loadPage("SanDauGia.fxml");
    }

    // Sự kiện khi bấm nút "Lịch Sử Đặt Giá"
    @FXML
    void handleLichSuDatGia(ActionEvent event) {
        loadPage("LichSuDatGia.fxml");
    }
}