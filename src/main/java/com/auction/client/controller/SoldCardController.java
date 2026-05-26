package com.auction.client.controller;

import com.auction.server.dao.AuctionItemDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SoldCardController {
  @FXML private Label itemNameLabel;
  @FXML private Label endTimeLabel;
  @FXML private Label startPriceLabel;
  @FXML private Label finalPriceLabel;
  @FXML private Label winnerLabel;
  @FXML private Button viewButton;

  public void setData(AuctionItemDAO dto) {
    NumberFormat fmt = NumberFormat.getInstance(new Locale("vi", "VN"));
    itemNameLabel.setText(dto.getItem().getName());

    String endStr = dto.getAuction().getEndTime() != null ?
        dto.getAuction().getEndTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
    endTimeLabel.setText(dto.getItem().getItemType() + " · Kết thúc " + endStr);

    startPriceLabel.setText("Khởi điểm: " + fmt.format(dto.getItem().getStartingPrice()) + "đ");
    finalPriceLabel.setText(fmt.format(dto.getAuction().getCurrentPrice()) + "đ");

    // Vì currentWinnerId có thể null, ta kiểm tra kỹ
    String winnerId = dto.getAuction().getCurrentWinnerId() != null ?
        String.valueOf(dto.getAuction().getCurrentWinnerId()) : "Không có";
    winnerLabel.setText("Người thắng ID: " + winnerId);

    viewButton.setOnAction(e -> System.out.println("Xem lịch sử phiên: " + dto.getAuction().getAuctionId()));
  }
}
