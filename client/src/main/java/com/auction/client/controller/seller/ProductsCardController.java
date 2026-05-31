package com.auction.client.controller.seller;

import com.auction.server.dao.AuctionItemDAO;
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

/**
 * Controller dùng chung cho 3 loại thẻ: AUCTIONING / PENDING / SOLD.
 * FXML tương ứng: ProductCard.fxml
 *
 * Cách dùng trong cell factory:
 *   cardController.setData(item, ProductsCardController.CardType.AUCTIONING);
 */
public class ProductsCardController {

  public enum CardType { AUCTIONING, PENDING, SOLD }

  // --- Labels chung ---
  @FXML private Label itemNameLabel;
  @FXML private Label subTitleLabel;     // categoryDateLabel / timeLabel / endTimeLabel
  @FXML private Label startPriceLabel;
  @FXML private Label mainPriceLabel;    // currentPrice / "Chưa mở" / finalPrice
  @FXML private Label priceSubLabel;     // bidCount / "Chưa có bid" / "✓ Đã chốt"
  @FXML private Label statusBadgeLabel;  // "Đang mở" / "Chờ admin duyệt" / "Hoàn tất"
  @FXML private Label winnerLabel;       // chỉ dùng cho SOLD

  // --- Buttons ---
  @FXML private Button primaryButton;    // "Chi tiết" / "Sửa" / "Xem lại"
  @FXML private Button deleteButton;     // chỉ hiện ở PENDING

  // --- Image ---
  @FXML private ImageView imgProduct;

  private Consumer<AuctionItemDAO> onDetailCallback;
  private AuctionItemDAO currentItem;
  private Consumer<AuctionItemDAO> onCancelCallback;

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final NumberFormat VN_FMT = NumberFormat.getInstance(new Locale("vi", "VN"));

  public void setData(AuctionItemDAO dto, CardType type) {
    try {
      // SỬA Ở ĐÂY: Gán dữ liệu cho biến global của class
      this.currentItem = dto;

      var item    = dto.getItem();
      var auction = dto.getAuction();

      // --- Tên & giá khởi điểm (giống nhau ở cả 3 loại) ---
      itemNameLabel.setText(item.getName());
      startPriceLabel.setText("Khởi điểm: " + VN_FMT.format(item.getStartingPrice()) + "UETệ");

      // --- Load ảnh (dùng chung) ---
      loadImage(item.getImgItem());

      // SỬA Ở ĐÂY: Gom chung sự kiện click cho primaryButton (Chi tiết / Sửa / Xem lại)
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

          // ĐÃ XÓA dòng primaryButton.setOnAction(e -> System.out.println(...)) ở đây

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

          // ĐÃ XÓA dòng primaryButton.setOnAction(e -> System.out.println(...)) ở đây

          setVisible(deleteButton, true);
          deleteButton.setOnAction(e ->
              System.out.println("Xóa Item ID: " + item.getId())); // Chức năng xóa giữ nguyên tạm thời

          setVisible(winnerLabel, false);
        }

        case SOLD -> {
          String endDate = auction.getEndTime() != null
              ? auction.getEndTime().format(DATE_FMT) : "N/A";
          subTitleLabel.setText(item.getItemType() + " · Kết thúc " + endDate);

          mainPriceLabel.setText(VN_FMT.format(auction.getCurrentPrice()) + "UETệ");
          mainPriceLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 18; -fx-font-weight: bold;");

          priceSubLabel.setText("✓ Đã chốt");
          priceSubLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11;");

          statusBadgeLabel.setText("Hoàn tất");
          statusBadgeLabel.setStyle("-fx-background-color:#eafaf1;-fx-text-fill:#2ecc71;"
              + "-fx-padding:3 10 3 10;-fx-background-radius:15;-fx-font-size:11;");

          String winnerId = auction.getCurrentWinnerId() != null
              ? String.valueOf(auction.getCurrentWinnerId()) : "Không có";
          setVisible(winnerLabel, true);
          winnerLabel.setText("Người thắng ID: " + winnerId);

          primaryButton.setText("Xem lại");
          primaryButton.setStyle("-fx-background-color:transparent;-fx-border-color:#dcdde1;-fx-border-radius:5;");

          // ĐÃ XÓA dòng primaryButton.setOnAction(e -> System.out.println(...)) ở đây

          setVisible(deleteButton, false);
        }
      }

    } catch (Exception e) {
      System.err.println("ProductsCardController.setData() lỗi ở SP: "
          + dto.getItem().getName());
      e.printStackTrace();
    }
  }

  // ------------------------------------------------------------------ //
  //  Helper                                                              //
  // ------------------------------------------------------------------ //

  private void loadImage(String imagePathFromDB) {
    if (imgProduct == null) return;
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
      }  // Ở đây
    }
  }

  /** Ẩn/hiện node và giải phóng layout space luôn */
  private void setVisible(javafx.scene.Node node, boolean visible) {
    if (node == null) return;
    node.setVisible(visible);
    node.setManaged(visible);
  }
  public void setOnDetailCallback(Consumer<AuctionItemDAO> callback) {
    this.onDetailCallback = callback;
  }
  @FXML
  private void handleChiTietButtonClick() {
    if (onDetailCallback != null) {
      onDetailCallback.accept(currentItem);
    }
  }
  public void setOnCancelCallback(Consumer<AuctionItemDAO> callback) {
    this.onCancelCallback = callback;
  }

  @FXML
  private void handleHuyButtonClick() {
    if (onCancelCallback != null) {
      onCancelCallback.accept(currentItem);
    }
  }
}