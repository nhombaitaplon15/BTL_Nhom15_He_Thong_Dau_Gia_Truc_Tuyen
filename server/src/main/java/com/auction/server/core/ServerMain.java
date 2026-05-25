package com.auction.server.core;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ServerMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // 🎨 Nạp file giao diện tổng MainContainer (Nơi chứa Sidebar xanh của bạn)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainContainer.fxml"));
            HBox root = loader.load();

            // Tạo khung cửa sổ ứng dụng với kích thước tiêu chuẩn HD
            Scene scene = new Scene(root, 1280, 720);

            primaryStage.setTitle("Elite Auction - Hệ Thống Đấu Giá Trực Tuyến");
            primaryStage.setScene(scene);

            // 🌟 Tự động phóng to toàn màn hình khi vừa mở lên cho chuẩn giao diện hiện đại
            primaryStage.setMaximized(true);

            primaryStage.show();
        } catch (Exception e) {
            System.err.println("❌ LỖI KHỞI CHẠY CLIENT: Không thể nạp được file MainContainer.fxml.");
            System.err.println("Hãy chắc chắn rằng file MainContainer.fxml đang nằm đúng trong thư mục src/main/resources/view/");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Kích hoạt môi trường đồ họa JavaFX
        launch(args);
    }
}
