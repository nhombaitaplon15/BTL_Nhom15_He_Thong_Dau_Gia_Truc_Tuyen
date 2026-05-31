package com.auction.client.core;


import com.auction.common.network.RequestCode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * ClientMain - Điểm khởi đầu của ứng dụng Client.
 *
 * ĐÃ SỬA LỖI CHÍNH: File này trước đây RỖNG hoàn toàn, chương trình không thể chạy.
 * MainApp.java (đặt SAI ở server package) mới là file thực sự chạy JavaFX,
 * điều này vi phạm nguyên tắc phân tách Client-Server.
 *
 * Đặt tại: client/src/main/java/com/auction/client/core/ClientMain.java
 */
public class ClientMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Kết nối tới Server ngay khi khởi động
            SocketClient.getInstance().connect();

            // 2. Nạp màn hình đăng nhập đầu tiên
            Parent root = FXMLLoader.load(getClass().getResource("/view/view/bidder/LoginView.fxml"));

            Scene scene = new Scene(root, 1280, 720);
            primaryStage.setTitle("Elite Auction - Hệ Thống Đấu Giá Trực Tuyến");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);

            // 3. Ngắt kết nối Socket khi đóng app (Graceful shutdown)
            primaryStage.setOnCloseRequest(e -> {
                SocketClient.getInstance().disconnect();
                Platform.exit();
            });

            primaryStage.show();

        } catch (Exception e) {
            System.err.println("[CLIENT MAIN] Lỗi khởi chạy ứng dụng: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

