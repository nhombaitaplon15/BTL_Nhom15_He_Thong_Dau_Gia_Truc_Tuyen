package com.auction.client.controller;

import com.auction.server.dao.AuctionItemDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PendingCardController {
  @FXML private Label itemNameLabel;
  @FXML private Label timeLabel;
  @FXML private Label startPriceLabel;
  @FXML private Button editButton;
  @FXML private Button deleteButton;

  public void setData(AuctionItemDAO dto) {
    NumberFormat fmt = NumberFormat.getInstance(new Locale("vi", "VN"));
    itemNameLabel.setText(dto.getItem().getName());

    String timeStr = dto.getAuction().getCreatedAt() != null ?
        dto.getAuction().getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "N/A";
    timeLabel.setText(dto.getItem().getItemType() + " · Gửi duyệt " + timeStr);

    startPriceLabel.setText("Khởi điểm: " + fmt.format(dto.getItem().getStartingPrice()) + "đ");

    editButton.setOnAction(e -> System.out.println("Sửa Item ID: " + dto.getItem().getItemId()));
    deleteButton.setOnAction(e -> System.out.println("Xóa Item ID: " + dto.getItem().getItemId()));
  }
}