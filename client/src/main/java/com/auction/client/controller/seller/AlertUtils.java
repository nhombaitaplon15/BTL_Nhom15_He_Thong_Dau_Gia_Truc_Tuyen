package com.auction.client.controller.seller;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Tiện ích hiển thị Alert — thay thế 3 hàm showError/showSuccess/showInfo
 * bị lặp ở AuctionDetailController (và các controller khác sau này).
 */
public final class AlertUtils {

  private AlertUtils() {}

  public static void error(String message) {
    show(Alert.AlertType.ERROR, "Lỗi", message);
  }

  public static void success(String message) {
    show(Alert.AlertType.INFORMATION, "Thành công", message);
  }

  public static void info(String message) {
    show(Alert.AlertType.INFORMATION, "Thông báo", message);
  }

  /**
   * Hiện dialog xác nhận, trả về true nếu user bấm OK.
   * Dùng cho onCancelAuction() và các thao tác destructive khác.
   */
  public static boolean confirm(String title, String header, String content) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(title);
    alert.setHeaderText(header);
    alert.setContentText(content);
    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
  }

  // ------------------------------------------------------------------ //

  private static void show(Alert.AlertType type, String title, String message) {
    Alert alert = new Alert(type);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }
}