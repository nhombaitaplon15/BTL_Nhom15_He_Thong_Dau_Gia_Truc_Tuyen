package com.auction.client.controller;

import com.auction.common.model.Item;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;

public class AuctionCreateCardController {
  @FXML
  private ImageView imgProduct;
  @FXML
  private Label lblName;
  @FXML
  private Label lblMeta;
  @FXML
  private Label lblPrice;
  @FXML
  private Button btnCreate;

  // Reference về item để dùng trong button handler
  private Item currentItem;
  // Callback về AuctionManagementController
  private Runnable onCreateCallback;

  public void setData(Item item) {
    this.currentItem = item;
    lblName.setText(item.getName());
    lblMeta.setText(item.getItemType() + " · Đã được admin duyệt ");
    lblPrice.setText(formatMoney(item.getStartingPrice()) + "UETệ");

    String imagePathFromDB = item.getImgItem(); // Lấy đường dẫn bạn đã lưu trong CSDL

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
    public void setOnCreateCallback (Runnable callback){
      this.onCreateCallback = callback;
    }

    @FXML
    private void onCreateAuction () {
      if (onCreateCallback != null) onCreateCallback.run();
      // Hoặc mở dialog trực tiếp:
      // openCreateAuctionDialog(currentItem);
    }

    private String formatMoney ( double amount){
      return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount);
    }

}
