package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.service.ItemService;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class AuctionOpenCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label lblName;
  @FXML private Label lblMeta;
  @FXML private Label lblPriceStart;
  @FXML private Label lblPriceCur;
  @FXML private Label lblBidCount;
  @FXML private Label lblCountdown;
  @FXML private Circle dotTimer;
  @FXML private Button btnDetail;

  private ItemService itemService = new ItemService();
  private Auction currentAuction;
  private Timeline countdownTimer;
  private Runnable onDetailCallback;

  public void setData( Auction auction) {
    this.currentAuction = auction;

    // Gọi service để lấy đối tượng Item thông qua itemId
    Item item = itemService.getItemById(auction.getItemId());

    lblName.setText(item.getName());

    // Lưu ý: Nếu bạn có hàm format thời gian riêng thì đổi lại getStartTime() nhé
    lblMeta.setText(item.getItemType() + " • Mở lúc " + auction.getStartTime());

    lblPriceStart.setText("Khởi điểm: " + formatMoney(item.getStartingPrice()) + "UETệ");
    lblPriceCur.setText(formatMoney(auction.getCurrentPrice()) + "UETệ");
    lblBidCount.setText(auction.getTotalBids() + " lượt bid");

    startCountdown(auction.getEndTime());

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

  private void startCountdown(LocalDateTime endTime) {
    if (countdownTimer != null) countdownTimer.stop();
    countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
      if (secs <= 0) {
        lblCountdown.setText("Đã kết thúc");
        countdownTimer.stop();
        return;
      }
      long h = secs / 3600;
      long m = (secs % 3600) / 60;
      long s = secs % 60;
      lblCountdown.setText(String.format("Còn %02d:%02d:%02d", h, m, s));
      // Đổi màu dot: cam nếu còn < 1 giờ
      dotTimer.setFill(secs < 3600
          ? Color.web("#FF9800")
          : Color.web("#43A047"));
    }));
    countdownTimer.setCycleCount(Animation.INDEFINITE);
    countdownTimer.play();
  }

  // Dừng timer khi cell bị recycle
  public void stopTimer() {
    if (countdownTimer != null) countdownTimer.stop();
  }

  public void setOnDetailCallback(Runnable cb) { this.onDetailCallback = cb; }

  @FXML private void onDetail() { if (onDetailCallback != null) onDetailCallback.run(); }

  private String formatMoney(double  amount) {
    return NumberFormat.getNumberInstance(new Locale("vi","VN")).format(amount);
  }
}
