package com.auction.client.controller.seller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class ViewSwitcher {
  // Lưu trữ cái StackPane "sân khấu" ở giữa màn hình
  private static StackPane mainContentArea;

  // Hàm này gọi 1 lần lúc khởi chạy app để set sân khấu
  public static void setMainContentArea(StackPane contentArea) {
    mainContentArea = contentArea;
  }

  // Hàm dùng chung để đổi trang
  public static void switchTo(String fxmlFileName) {
    if (mainContentArea == null) return;

    try {
      // Tải file FXML động (chỉ khi nào gọi mới tải)
      Parent root = FXMLLoader.load(ViewSwitcher.class.getResource("/view/view/seller/" + fxmlFileName + ".fxml"));

      // Xóa màn hình cũ, đắp màn hình mới vào
      mainContentArea.getChildren().clear();
      mainContentArea.getChildren().add(root);

    } catch (IOException e) {
      System.err.println("Không tìm thấy file: " + fxmlFileName);
      e.printStackTrace();
    }
  }
}
