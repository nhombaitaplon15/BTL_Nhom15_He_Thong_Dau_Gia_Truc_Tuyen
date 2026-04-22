module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql; // Để server dùng JDBC

    // Cho phép JavaFX load file FXML và Controller
    opens com.auction.client.controller to javafx.fxml;
    exports com.auction.client;

    // Cho phép JavaFX đọc dữ liệu từ Model để hiển thị lên TableView
    opens com.auction.common.model to javafx.base;
}