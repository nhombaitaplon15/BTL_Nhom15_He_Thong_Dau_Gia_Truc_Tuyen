module com.auction {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires java.desktop;

    // 1. Cho phép JavaFX truy cập vào package controller gốc và package chính
    opens com.auction to javafx.fxml;
    opens com.auction.client.controller to javafx.fxml;

    // ĐÂY RỒI: Cần mở chính xác package con 'bidder' - nơi chứa TransactionHistoryController của em
    opens com.auction.client.controller.bidder to javafx.fxml;

    opens com.auction.common.model to javafx.base, javafx.fxml;
    // Phòng hờ sau này em làm thêm giao diện cho Seller (Người bán) hoặc Admin, hãy mở sẵn luôn ở đây:
    // opens com.auction.client.controller.seller to javafx.fxml;
    // opens com.auction.client.controller.admin to javafx.fxml;

    // 2. Xuất khẩu các package để hệ thống có thể nhận diện và sử dụng chéo
    exports com.auction;
    exports com.auction.client.controller;
    exports com.auction.client.controller.bidder;

    exports com.auction.common.model;
}