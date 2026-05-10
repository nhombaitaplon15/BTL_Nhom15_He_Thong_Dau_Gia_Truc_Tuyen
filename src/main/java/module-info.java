module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires java.desktop;
    requires com.auction;
    //requires com.auction;

    // Cho phép JavaFX đọc các file Controller của em
    opens com.auction.client.controller to javafx.fxml;
    // Cho phép JavaFX khởi chạy từ package chính
    opens com.auction to javafx.fxml;

    exports com.auction;
//    exports com.auction.test;
//    opens com.auction.test to javafx.fxml;
}