module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires java.sql;

    opens com.auction.client.controller to javafx.fxml;
    opens com.auction to javafx.fxml;

    exports com.auction;
    exports com.auction.client.controller;
}