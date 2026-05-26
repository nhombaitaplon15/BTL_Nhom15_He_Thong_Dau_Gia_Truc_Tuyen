package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.service.ItemService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;

import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;

public class AuctionFinishedCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label lblName;
  @FXML private Label lblMeta;
  @FXML private Label lblPriceStart;
  @FXML private Label lblPriceFinal;
  @FXML private Label lblSubStatus;
  @FXML private Label lblStatusPill;
  @FXML private Button btnRemind;
  @FXML private Button btnView;

  private ItemService itemService = new ItemService();
  private Auction currentAuction;
  private Runnable onRemindCallback;
  private Runnable onViewCallback;

  public void setData(Auction auction) {
    this.currentAuction = auction;
    lblName.setText(itemService.getItemById(auction.getItemId()).getName());
    lblPriceStart.setText("Khởi điểm: "
        + formatMoney(itemService.getItemById(auction.getItemId()).getStartingPrice()) + "UETệ");

    switch (auction.getAuctionStatus()) {

      case "SOLD":
        // Meta
        lblMeta.setText("Kết thúc " + auction.getEndTime()
            + " · Người thắng: " + auction.getCurrentWinnerId());
        // Giá
        lblPriceFinal.setText(formatMoney(auction.getCurrentPrice()) + "UETệ");
        lblPriceFinal.setStyle("-fx-font-size:15;-fx-font-weight:bold;-fx-text-fill:#43A047;");
        lblSubStatus.setText("✓ Đã thanh toán");
        lblSubStatus.setStyle("-fx-font-size:10.5;-fx-text-fill:#2E7D32;");
        // Pill
        lblStatusPill.setText("PAID");
        lblStatusPill.setStyle(
            "-fx-background-color:#E8F5E9;-fx-text-fill:#2E7D32;"
                + "-fx-font-size:10;-fx-font-weight:bold;"
                + "-fx-padding:3 10 3 10;-fx-background-radius:99;"
                + "-fx-min-width:80;-fx-alignment:CENTER;");
        // Nút
        btnRemind.setVisible(false);
        btnRemind.setManaged(false);
        break;

      case "FINISHED":
        lblMeta.setText("Kết thúc " + auction.getEndTime()
            + " · Người thắng: " + auction.getCurrentWinnerId());
        lblPriceFinal.setText(formatMoney(auction.getCurrentPrice()) + "UETệ");
        lblPriceFinal.setStyle("-fx-font-size:15;-fx-font-weight:bold;-fx-text-fill:#D7A859;");
        lblSubStatus.setText("⏳ Chờ thanh toán");
        lblSubStatus.setStyle("-fx-font-size:10.5;-fx-text-fill:#E65100;");
        lblStatusPill.setText("FINISHED");
        lblStatusPill.setStyle(
            "-fx-background-color:#FFF3E0;-fx-text-fill:#E65100;"
                + "-fx-font-size:10;-fx-font-weight:bold;"
                + "-fx-padding:3 10 3 10;-fx-background-radius:99;"
                + "-fx-min-width:80;-fx-alignment:CENTER;");
        btnRemind.setVisible(true);
        btnRemind.setManaged(true);
        break;

      case "CANCELED":
        lblMeta.setText("Kết thúc " + auction.getEndTime()
            + " · Không có người thắng");
        lblPriceFinal.setText("Không có bid");
        lblPriceFinal.setStyle("-fx-font-size:13;-fx-font-weight:normal;-fx-text-fill:#A08C6E;");
        lblSubStatus.setText("");
        lblStatusPill.setText("CANCELED");
        lblStatusPill.setStyle(
            "-fx-background-color:#FFEBEE;-fx-text-fill:#B71C1C;"
                + "-fx-font-size:10;-fx-font-weight:bold;"
                + "-fx-padding:3 10 3 10;-fx-background-radius:99;"
                + "-fx-min-width:80;-fx-alignment:CENTER;");
        btnRemind.setVisible(false);
        btnRemind.setManaged(false);
        break;

      default:
        break;
    }

    String imagePathFromDB = itemService.getItemById(auction.getItemId()).getImgItem(); // Lấy đường dẫn bạn đã lưu trong CSDL

    if (imagePathFromDB != null && !imagePathFromDB.trim().isEmpty()) {
      File imageFile = new File(imagePathFromDB);

      // Kiểm tra xem file có thực sự tồn tại trong ổ cứng không
      if (imageFile.exists()) {
        // Lệnh toURI().toString() sẽ tự động thêm "file:/" và chuyển đổi các ký tự khoảng trắng cho chuẩn
        Image image = new Image(imageFile.toURI().toString());

        // imageViewProduct là cái biến ImageView trên giao diện của bạn
        imgProduct.setImage(image);
      } else {
        System.out.println("Lỗi: Không tìm thấy file ảnh tại đường dẫn: " + imagePathFromDB);
        // Ở đây bạn có thể set một ảnh mặc định (default image) nếu không tìm thấy ảnh
      }
    }
  }

  public void setOnRemindCallback(Runnable cb) { this.onRemindCallback = cb; }
  public void setOnViewCallback(Runnable cb)   { this.onViewCallback = cb; }

  @FXML private void onRemind() { if (onRemindCallback != null) onRemindCallback.run(); }
  @FXML private void onView()   { if (onViewCallback != null)   onViewCallback.run(); }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi","VN")).format(amount);
  }
}
