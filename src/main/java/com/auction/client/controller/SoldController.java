package com.auction.client.controller;

import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.AuctionItemDAO;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class SoldController implements Initializable {

  @FXML private VBox productListVBox;
  @FXML private TextField searchField;
  @FXML private Button filterButton;

  private final AuctionDAO auctionDAO = new AuctionDAO();

  // Trạng thái phiên đấu giá đã kết thúc
  private final String CURRENT_STATUS = "FINISHED";
  private int loggedInSellerId = 11;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    loadDataToUI("");

    filterButton.setOnAction(event -> {
      String keyword = searchField.getText().trim();
      loadDataToUI(keyword);
    });

    searchField.setOnAction(event -> {
      String keyword = searchField.getText().trim();
      loadDataToUI(keyword);
    });
  }

  private void loadDataToUI(String keyword) {
    productListVBox.getChildren().clear();
    productListVBox.getChildren().addAll(new ProgressIndicator(), new Label("Đang tải dữ liệu..."));

    Task<List<AuctionItemDAO>> loadDataTask = new Task<List<AuctionItemDAO>>() {
      @Override
      protected List<AuctionItemDAO> call() throws Exception {
        // GỌI HÀM MỚI VÀ TRUYỀN THÊM loggedInSellerId VÀO ĐÂY
        return auctionDAO.getAuctionsBySellerStatusAndKeyword(loggedInSellerId, CURRENT_STATUS, keyword);
      }
    };

    loadDataTask.setOnSucceeded(event -> {
      List<AuctionItemDAO> dtoList = loadDataTask.getValue();
      productListVBox.getChildren().clear();

      if (dtoList == null || dtoList.isEmpty()) {
        productListVBox.getChildren().add(new Label("Chưa có phiên đấu giá nào hoàn tất."));
        return;
      }

      for (AuctionItemDAO dto : dtoList) {
        try {
          // Trỏ đúng vào file FXML của thẻ Sold
          FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/SoldCard.fxml"));
          HBox itemCard = loader.load();

          // Gọi đúng Controller của thẻ Sold
          SoldCardController cardController = loader.getController();
          cardController.setData(dto);

          productListVBox.getChildren().add(itemCard);
        } catch (IOException e) {
          System.err.println("Lỗi khi load SoldCard.fxml: " + e.getMessage());
          e.printStackTrace();
        }
      }
    });

    loadDataTask.setOnFailed(event -> {
      productListVBox.getChildren().clear();
      Throwable exception = loadDataTask.getException();
      productListVBox.getChildren().add(new Label("Lỗi tải dữ liệu: " + exception.getMessage()));
      exception.printStackTrace();
    });

    Thread backgroundThread = new Thread(loadDataTask);
    backgroundThread.setDaemon(true);
    backgroundThread.start();
  }
}