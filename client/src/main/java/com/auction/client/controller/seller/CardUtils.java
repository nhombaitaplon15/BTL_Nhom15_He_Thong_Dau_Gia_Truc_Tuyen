package com.auction.client.controller.seller;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Tập hợp các hàm tiện ích dùng chung cho tất cả card controller.
 * Tránh copy-paste loadImage() và formatMoney() ở 5 nơi.
 */
public final class CardUtils {

  private static final NumberFormat VN_FORMAT =
      NumberFormat.getNumberInstance(new Locale("vi", "VN"));

  private CardUtils() {} // Không cho khởi tạo

  // ------------------------------------------------------------------ //
  //  Format tiền                                                        //
  // ------------------------------------------------------------------ //

  /** "1.500.000" (không có đơn vị — caller tự thêm "UETệ" hay "đ") */
  public static String formatMoney(double amount) {
    return VN_FORMAT.format(amount);
  }

  // ------------------------------------------------------------------ //
  //  Load ảnh                                                           //
  // ------------------------------------------------------------------ //

  /**
   * Load ảnh từ đường dẫn tuyệt đối trong DB vào ImageView.
   * Nếu path null/rỗng hoặc file không tồn tại → set null (hiện placeholder).
   *
   * @param imgView  ImageView cần set ảnh
   * @param path     Đường dẫn tuyệt đối lấy từ DB (item.getImgItem())
   */
  public static void loadImage(ImageView imgView, String path) {
    if (imgView == null) return;

    if (path != null && !path.isBlank()) {
      File file = new File(path);
      if (file.exists()) {
        imgView.setImage(new Image(file.toURI().toString()));
        return;
      }
      System.out.println("[CardUtils] Không tìm thấy ảnh: " + path);
    }
    imgView.setImage(null); // Giữ placeholder trong FXML
  }

  // ------------------------------------------------------------------ //
  //  Ẩn/hiện node                                                      //
  // ------------------------------------------------------------------ //

  /** Set visible + managed cùng lúc để không chiếm layout space khi ẩn. */
  public static void setVisible(javafx.scene.Node node, boolean visible) {
    if (node == null) return;
    node.setVisible(visible);
    node.setManaged(visible);
  }
}