package com.auction.client.controller.seller;

import com.auction.common.network.AuctionItemDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

public class ProductsCardController {

  public enum CardType { AUCTIONING, PENDING, SOLD }

  @FXML private Label itemNameLabel;
  @FXML private Label subTitleLabel;
  @FXML private Label startPriceLabel;
  @FXML private Label mainPriceLabel;
  @FXML private Label priceSubLabel;
  @FXML private Label statusBadgeLabel;
  @FXML private Label winnerLabel;

  @FXML private Button primaryButton;
  @FXML private Button deleteButton;

  @FXML private ImageView imgProduct;

  private Consumer<AuctionItemDTO> onDetailCallback;
  private AuctionItemDTO currentItem;
  private Consumer<AuctionItemDTO> onCancelCallback;

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat VN_FMT = NumberFormat.getInstance(new Locale("vi", "VN"));

  public void setData(AuctionItemDTO dto, CardType type) {
    try {
      this.currentItem = dto;

      var item    = dto.getItem();
      var auction = dto.getAuction();

      itemNameLabel.setText(item.getName());
      startPriceLabel.setText("Khởi điểm: " + VN_FMT.format(item.getStartingPrice()) + "UETệ");

      CardUtils.loadImage(imgProduct, item.getImgItem());

      primaryButton.setOnAction(e -> handleChiTietButtonClick());

      switch (type) {
        case AUCTIONING -> {
          String date = item.getCreatedAt() != null
              ? item.getCreatedAt().format(DATE_FMT) : "N/A";
          subTitleLabel.setText(item.getItemType() + " · Đăng " + date);

          mainPriceLabel.setText(VN_FMT.format(auction.getCurrentPrice()) + "UETệ");
          mainPriceLabel.setStyle("-fx-text-fill: #e1b12c; -fx-font-size: 18; -fx-font-weight: bold;");

          priceSubLabel.setText("↑ " + auction.getTotalBids() + " lượt bid");

          statusBadgeLabel.setText("Đang mở");
          statusBadgeLabel.setStyle("-fx-background-color:#eafaf1;-fx-text-fill:#2ecc71;"
              + "-fx-padding:3 10 3 10;-fx-background-radius:15;-fx-font-size:11;");

          primaryButton.setText("Chi tiết");
          primaryButton.setStyle("-fx-background-color:transparent;-fx-border-color:#dcdde1;-fx-border-radius:5;");

          setVisible(winnerLabel, false);
          setVisible(deleteButton, false);
        }

        case PENDING -> {
          String datetime = auction.getCreatedAt() != null
              ? auction.getCreatedAt().format(DATETIME_FMT) : "N/A";
          subTitleLabel.setText(item.getItemType() + " · Gửi duyệt " + datetime);

          mainPriceLabel.setText("Chưa mở");
          mainPriceLabel.setStyle("-fx-text-fill: #e1b12c; -fx-font-size: 16; -fx-font-weight: bold;");

          priceSubLabel.setText("Chưa có bid");

          statusBadgeLabel.setText("Chờ admin duyệt");
          statusBadgeLabel.setStyle("-fx-background-color:#fef9e7;-fx-text-fill:#f39c12;"
              + "-fx-padding:3 10 3 10;-fx-background-radius:15;-fx-font-size:11;");

          primaryButton.setText("Sửa");
          primaryButton.setStyle("-fx-background-color:#192a56;-fx-text-fill:white;-fx-background-radius:5;");

          setVisible(deleteButton, true);
          deleteButton.setOnAction(e -> handleHuyButtonClick());

          setVisible(winnerLabel, false);
        }

        case SOLD -> {
          String endDate = auction.getEndTime() != null
              ? auction.getEndTime().format(DATE_FMT) : "N/A";
          subTitleLabel.setText(item.getItemType() + " · Kết thúc " + endDate);

          String status = auction.getAuctionStatus();

          if ("REJECTED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
            mainPriceLabel.setText("Không có bid");
            mainPriceLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 16; -fx-font-weight: bold;");

            priceSubLabel.setText("✗ Từ chối / Huỷ");
            priceSubLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");

            statusBadgeLabel.setText(status);
            statusBadgeLabel.setStyle("-fx-background-color:#fadbd8;-fx-text-fill:#c0392b;"
                + "-fx-padding:3 10 3 10;-fx-background-radius:15;-fx-font-size:11;");

            setVisible(winnerLabel, false);
          } else {
            mainPriceLabel.setText(VN_FMT.format(auction.getCurrentPrice()) + "UETệ");
            mainPriceLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 18; -fx-font-weight: bold;");

            priceSubLabel.setText("✓ Đã chốt");
            priceSubLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11;");

            statusBadgeLabel.setText(status);
            statusBadgeLabel.setStyle("-fx-background-color:#eafaf1;-fx-text-fill:#2ecc71;"
                + "-fx-padding:3 10 3 10;-fx-background-radius:15;-fx-font-size:11;");

            String winnerId = auction.getCurrentWinnerId() != null
                ? String.valueOf(auction.getCurrentWinnerId()) : "Không có";
            setVisible(winnerLabel, true);
            winnerLabel.setText("Người thắng ID: " + winnerId);
          }

          primaryButton.setText("Xem lại");
          primaryButton.setStyle("-fx-background-color:transparent;-fx-border-color:#dcdde1;-fx-border-radius:5;");

          setVisible(deleteButton, false);
        }
      }

    } catch (Exception e) {
      System.err.println("ProductsCardController.setData() lỗi ở SP: "
          + dto.getItem().getName());
      e.printStackTrace();
    }
  }

  private void setVisible(javafx.scene.Node node, boolean visible) {
    if (node == null) return;
    node.setVisible(visible);
    node.setManaged(visible);
  }

  public void setOnDetailCallback(Consumer<AuctionItemDTO> callback) {
    this.onDetailCallback = callback;
  }

  @FXML
  private void handleChiTietButtonClick() {
    if (onDetailCallback != null) {
      onDetailCallback.accept(currentItem);
    }
  }

  public void setOnCancelCallback(Consumer<AuctionItemDTO> callback) {
    this.onCancelCallback = callback;
  }

  @FXML
  private void handleHuyButtonClick() {
    if (onCancelCallback != null) {
      onCancelCallback.accept(currentItem);
    }
  }
}