package com.auction.client.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class SearchItemsController implements Initializable {

  @FXML private TextField txtKeyword;
  @FXML private ListView<String> listSearchHistory;

  // Tạo một danh sách để lưu các từ khóa đã tìm
  private ObservableList<String> historyList = FXCollections.observableArrayList();

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    // Gắn danh sách lịch sử vào ListView
    listSearchHistory.setItems(historyList);
    listSearchHistory.setVisible(false); // Đảm bảo lúc mới mở lên thì nó ẩn đi

    // 1. Khi người dùng click chuột vào ô tìm kiếm -> Hiện lịch sử lên
    txtKeyword.setOnMouseClicked(event -> {
      if (!historyList.isEmpty()) {
        listSearchHistory.setVisible(true);
        listSearchHistory.toFront(); // Đẩy lên lớp trên cùng để không bị che
      }
    });

    // 2. Khi người dùng bấm chọn một dòng trong bảng lịch sử
    listSearchHistory.setOnMouseClicked(event -> {
      String selectedKeyword = listSearchHistory.getSelectionModel().getSelectedItem();
      if (selectedKeyword != null) {
        txtKeyword.setText(selectedKeyword); // Điền chữ vào ô tìm kiếm
        listSearchHistory.setVisible(false); // Ẩn cái bảng lịch sử đi

        // (Tùy chọn) Gọi luôn hàm tìm kiếm ở đây nếu muốn bấm cái là tìm luôn
        // handleSearch(new ActionEvent());
      }
    });
  }

  @FXML
  public void handleSearch(ActionEvent event) {
    String keyword = txtKeyword.getText().trim();

    if (!keyword.isEmpty()) {
      // Kiểm tra nếu từ khóa chưa có trong lịch sử thì mới thêm vào
      if (!historyList.contains(keyword)) {
        historyList.add(0, keyword); // add(0, ...) để đẩy từ khóa mới nhất lên đầu danh sách
      }

      // Ẩn bảng lịch sử đi sau khi bấm tìm kiếm
      listSearchHistory.setVisible(false);

      // ... Gọi AuctionDAO để query PostgreSQL tìm sản phẩm và đổ ra bảng kết quả ...
      System.out.println("Đang tìm kiếm: " + keyword);
    }
  }
}
