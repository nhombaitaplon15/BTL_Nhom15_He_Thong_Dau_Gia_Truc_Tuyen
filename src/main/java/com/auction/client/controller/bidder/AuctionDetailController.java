package com.auction.client.controller.bidder;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

public class AuctionDetailController {

    @FXML private Label lblAuctionId;
    @FXML private Label lblItemName;
    @FXML private Label lblBidAmount;
    @FXML private Label lblStatus;
    @FXML private Label lblTimeLeft;
    @FXML private ImageView imgItem;

    private User currentUser;
    private BidHistoryRow auctionData;

    public void setAuctionData(BidHistoryRow data, User user) {
        this.auctionData = data;
        this.currentUser = user;

        lblAuctionId.setText("🔎 CHI TIẾT PHIÊN ĐẤU GIÁ #" + data.getAuctionId());
        lblItemName.setText("Tên vật phẩm: " + data.getItemName());
        lblBidAmount.setText(String.format("Giá bạn đã đặt: %,.0f đ", data.getBidAmount()));
        lblStatus.setText("Trạng thái: " + data.getStatus());

        if ("ĐANG DẪN ĐẦU".equalsIgnoreCase(data.getStatus())) {
            lblStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } else {
            lblStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        }
        lblTimeLeft.setText("⏳ Thời gian còn lại: Đang tính toán...");
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) lblAuctionId.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleGoToLive() {
        System.out.println(">>> Điều hướng tới phiên LIVE ID: " + auctionData.getAuctionId());
        handleClose();
    }
}