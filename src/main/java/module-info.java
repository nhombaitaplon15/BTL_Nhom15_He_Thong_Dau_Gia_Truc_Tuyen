module com.example.demo2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires java.sql;

    opens com.example.demo2.controller to javafx.fxml;
    opens com.example.demo2 to javafx.fxml;
    opens com.auction.server.dao to javafx.fxml;
    exports com.auction.server.dao;

    exports com.example.demo2;
    exports com.example.demo2.controller;
}