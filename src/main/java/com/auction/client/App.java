package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Load file giao diện FXML
            // Lưu ý: File Login.fxml nên để trong thư mục src/main/resources/com/auction/client
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/auction/client/Login.fxml"));
            Parent root = loader.load();

            // 2. Tạo khung cảnh (Scene) với kích thước tùy chọn (ví dụ 400x300)
            Scene scene = new Scene(root, 400, 300);

            // 3. Thiết lập Stage (Cửa sổ chính)
            primaryStage.setTitle("Hệ thống Đấu giá Trực tuyến - Đăng nhập");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false); // Không cho co giãn cửa sổ để tránh vỡ giao diện
            primaryStage.show();

        } catch (IOException e) {
            System.err.println("Lỗi: Không tìm thấy file Login.fxml. Hãy kiểm tra lại đường dẫn!");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}