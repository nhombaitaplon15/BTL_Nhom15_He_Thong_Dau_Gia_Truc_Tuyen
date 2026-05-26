package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.server.dao.AuctionItemDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AuctioningCardController {

    @FXML private Label itemNameLabel;
    @FXML private Label categoryDateLabel;
    @FXML private Label startPriceLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label bidCountLabel;
    @FXML private Button detailButton;

    public void setData(AuctionItemDAO dto) {
        try {
            Item item = dto.getItem();
            Auction auction = dto.getAuction();
            NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));

            itemNameLabel.setText(item.getName());

            String createdDate = item.getCreatedAt() != null ?
                item.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
            categoryDateLabel.setText(item.getItemType() + " · Đăng " + createdDate);

            startPriceLabel.setText("Khởi điểm: " + formatter.format(item.getStartingPrice()) + "đ");

            // Lấy giá trị thực tế từ class Auction!
            currentPriceLabel.setText(formatter.format(auction.getCurrentPrice()) + "đ");
            bidCountLabel.setText("↑ " + auction.getTotalBids() + " lượt bid");

            detailButton.setOnAction(event -> {
                System.out.println("Mở chi tiết phiên đấu giá ID: " + auction.getAuctionId());

            });
        } catch (Exception e ) {
                System.err.println("Báo động đỏ: Hàm setData sập ở thẻ có SP: " + dto.getItem().getName());
                e.printStackTrace(); // Dòng này sẽ chỉ đích danh lỗi nằm ở dòng số mấy
        }
    }
}