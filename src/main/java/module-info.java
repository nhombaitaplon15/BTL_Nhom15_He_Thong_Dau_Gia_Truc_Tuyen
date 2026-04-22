module com.auction {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.auction.client to javafx.fxml;
    exports com.auction.client;
}