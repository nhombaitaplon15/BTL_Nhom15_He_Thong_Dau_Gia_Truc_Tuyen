package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.service.ItemService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;

public class AuctionPendingCardController {
  @FXML private ImageView imgProduct;
  @FXML private Label lblName;
  @FXML private Label lblCreatedAt;
  @FXML private Label lblPrice;
  @FXML
  private Label lblStartTime;
  @FXML private Button btnEdit;
  @FXML private Button btnCancel;

  private ItemService itemService = new ItemService();
  private Auction currentAuction;
  private Runnable onEditCallback;
  private Runnable onCancelCallback;

  public void setData( Auction auction) {
    this.currentAuction = auction;

    Item item = itemService.getItemById(auction.getItemId());

    lblName.setText(item.getName());
    lblCreatedAt.setText("Tạo phiên lúc " + auction.getCreatedAt());
    lblPrice.setText(formatMoney(item.getStartingPrice()) + "UETệ");
    lblStartTime.setText("Bắt đầu: " + auction.getStartTime());

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

  public void setOnEditCallback(Runnable cb)   { this.onEditCallback = cb; }
  public void setOnCancelCallback(Runnable cb) { this.onCancelCallback = cb; }

  @FXML private void onEdit()   { if (onEditCallback != null)   onEditCallback.run(); }
  @FXML private void onCancel() { if (onCancelCallback != null) onCancelCallback.run(); }

  private String formatMoney(double  amount) {
    return NumberFormat.getNumberInstance(new Locale("vi","VN")).format(amount);
  }
}
