package com.auction.client.controller.seller;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;

public final class CardUtils {

  private static final NumberFormat VN_FORMAT =
      NumberFormat.getNumberInstance(new Locale("vi", "VN"));

  private CardUtils() {}

  public static String formatMoney(double amount) {
    return VN_FORMAT.format(amount);
  }

  public static void loadImage(ImageView imgView, String path) {
    if (imgView == null) return;

    if (path != null && !path.isBlank()) {
      try {
        // 1. Dạy hệ thống đọc link Internet (từ ImgBB)
        if (path.startsWith("http://") || path.startsWith("https://")) {
          imgView.setImage(new Image(path, true)); // true: tải ngầm để không đơ màn hình
          return;
        }

        // 2. Vẫn giữ lại cơ chế đọc file nội bộ cũ (dành cho các ảnh cũ lưu trước đó)
        File file = new File(path);
        if (file.exists()) {
          imgView.setImage(new Image(file.toURI().toString(), true));
          return;
        }

        System.out.println("[CardUtils] Không tìm thấy ảnh: " + path);
      } catch (Exception e) {
        System.err.println("[CardUtils] Lỗi tải ảnh: " + e.getMessage());
      }
    }

    // Nếu không có link hoặc tải lỗi, set ảnh về null (khung trắng)
    imgView.setImage(null);
  }

  public static void setVisible(javafx.scene.Node node, boolean visible) {
    if (node == null) return;
    node.setVisible(visible);
    node.setManaged(visible);
  }
}