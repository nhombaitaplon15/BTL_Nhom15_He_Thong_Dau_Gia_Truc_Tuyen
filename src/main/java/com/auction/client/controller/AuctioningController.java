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

public class AuctioningController implements Initializable {

  @FXML private VBox productListVBox;
  @FXML private TextField searchField;
  @FXML private Button filterButton;

  // Khởi tạo AuctionDAO để gọi hàm JOIN dữ liệu
  private final AuctionDAO auctionDAO = new AuctionDAO();

  // Trạng thái của màn hình này (Theo thiết kế DB của bạn: RUNNING hoặc OPEN)
  private final String CURRENT_STATUS = "RUNNING";
  private int loggedInSellerId = 11;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    // 1. Vừa vào màn hình thì load toàn bộ danh sách (từ khóa rỗng)
    loadDataToUI("");

    // 2. Sự kiện khi bấm nút Lọc
    filterButton.setOnAction(event -> {
      String keyword = searchField.getText().trim();
      loadDataToUI(keyword);
    });

    // 3. Sự kiện nhấn Enter ngay trong ô tìm kiếm
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

    // Khi Task lấy dữ liệu thành công
    loadDataTask.setOnSucceeded(event -> {
      List<AuctionItemDAO> dtoList = loadDataTask.getValue();
      productListVBox.getChildren().clear();

      if (dtoList == null || dtoList.isEmpty()) {
        productListVBox.getChildren().add(new Label("Không tìm thấy phiên đấu giá nào đang chạy."));
        return;
      }

      // Duyệt qua danh sách DTO và nạp vào thẻ FXML
      for (AuctionItemDAO dto : dtoList) {
        try {
          FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctioningCard.fxml"));
          HBox itemCard = loader.load();

          // Lấy Controller của thẻ và truyền cả gói DTO vào
          AuctioningCardController cardController = loader.getController();
          cardController.setData(dto);

          productListVBox.getChildren().add(itemCard);
        } catch (IOException e) {
          System.err.println("Lỗi khi load AuctioningCard.fxml: " + e.getMessage());
          e.printStackTrace();
        }
      }
    });

    // Khi Task gặp lỗi (ví dụ: sập Database, sai câu lệnh SQL)
    loadDataTask.setOnFailed(event -> {
      productListVBox.getChildren().clear();
      Throwable exception = loadDataTask.getException();
      productListVBox.getChildren().add(new Label("Lỗi tải dữ liệu: " + exception.getMessage()));
      exception.printStackTrace();
    });

    // Kích hoạt Task chạy trên một luồng riêng biệt để UI mượt mà
    Thread backgroundThread = new Thread(loadDataTask);
    backgroundThread.setDaemon(true);
    backgroundThread.start();
  }
}


