package com.auction.client.controller;

import com.auction.common.model.Art;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;
import com.auction.server.dao.AuctionItemDAO; // DTO chứa Item và Auction
import com.auction.server.dao.ItemDAO;         // Class kết nối Database của bạn
import com.auction.service.ItemService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;

public class MyProductsController  {

  @FXML private ScrollPane paneAuctioning;
  @FXML private ScrollPane panePending;
  @FXML private ScrollPane paneSold;

  // --- CÁC LISTVIEW ---
  @FXML private ListView<AuctionItemDAO> listAuctioning;
  @FXML private ListView<AuctionItemDAO> listPending;
  @FXML private ListView<AuctionItemDAO> listSold;

  // --- CÁC LABEL ĐẾM SỐ LƯỢNG (Từ file FXML của bạn) ---
  @FXML private Label lblCountAuc;
  @FXML private Label badgeAuctioning;
  @FXML private Label lblCountPend;
  @FXML private Label badgePending;
  @FXML private Label lblCountSold;

  // Khai báo các nút bấm ở Sidebar
  @FXML private HBox btnAuctioning;
  @FXML private HBox btnPending;
  @FXML private HBox btnSold;

  // --- CÁC Ô TÌM KIẾM ---
  @FXML private TextField searchAuctioning;
  @FXML private TextField searchPending;
  @FXML private TextField searchSold;

  @FXML private StackPane mainContentArea;

  private final int currentSellerId = 11;

  @FXML
  public void initialize() {

    setupCellFactories();

    // 2. LẤY DỮ LIỆU TỪ DATABASE VÀ ĐỔ LÊN GIAO DIỆN
    loadAllProducts();

    btnAuctioning.setOnMouseClicked(event -> switchTab(btnAuctioning, paneAuctioning));
    btnPending.setOnMouseClicked(event -> switchTab(btnPending, panePending));
    btnSold.setOnMouseClicked(event -> switchTab(btnSold, paneSold));

    switchTab(btnAuctioning, paneAuctioning);

    searchAuctioning.textProperty().addListener((observable, oldValue, newValue) -> {
      filterAndDisplay("RUNNING", newValue, listAuctioning, lblCountAuc, badgeAuctioning);
    });

    searchPending.textProperty().addListener((observable, oldValue, newValue) -> {
      filterAndDisplay("WAITING_FOR_ADMIN", newValue, listPending, lblCountPend, badgePending);
    });

    searchSold.textProperty().addListener((observable, oldValue, newValue) -> {
      filterAndDisplay("SOLD", newValue, listSold, lblCountSold, null);
    });

  }

  /**
   * Hàm này chịu trách nhiệm gọi database để lấy dữ liệu đổ vào 3 tab
   */
  private void loadAllProducts() {
    ItemDAO dao = new ItemDAO();// Gọi class chứa hàm query DB của bạn

    filterAndDisplay("RUNNING", "", listAuctioning, lblCountAuc, badgeAuctioning);
    filterAndDisplay("WAITING_FOR_ADMIN", "", listPending, lblCountPend, badgePending);
    filterAndDisplay("SOLD", "", listSold, lblCountSold, null); // Tab đã bán không có badge
    // 1. Tab Đang đấu giá
    List<AuctionItemDAO> auctioningList = dao.getSellerProductsByStatusAndKeyword(currentSellerId, "RUNNING", "");
    ObservableList<AuctionItemDAO> obsAuctioning = FXCollections.observableArrayList(auctioningList);
    listAuctioning.setItems(obsAuctioning);

    // Cập nhật số lượng
    if (lblCountAuc != null) lblCountAuc.setText("· " + auctioningList.size() + " sản phẩm");
    if (badgeAuctioning != null) badgeAuctioning.setText(String.valueOf(auctioningList.size()));

    // 2. Tab Chờ duyệt
    List<AuctionItemDAO> pendingList = dao.getSellerProductsByStatusAndKeyword(currentSellerId, "WAITING_FOR_ADMIN", "");
    ObservableList<AuctionItemDAO> obsPending = FXCollections.observableArrayList(pendingList);
    listPending.setItems(obsPending);

    if (lblCountPend != null) lblCountPend.setText("· " + pendingList.size() + " sản phẩm");
    if (badgePending != null) badgePending.setText(String.valueOf(pendingList.size()));

    // 3. Tab Đã bán
    List<AuctionItemDAO> soldList = dao.getSellerProductsByStatusAndKeyword(currentSellerId, "SOLD", "");
    ObservableList<AuctionItemDAO> obsSold = FXCollections.observableArrayList(soldList);
    listSold.setItems(obsSold);

    if (lblCountSold != null) lblCountSold.setText("· " + soldList.size() + " sản phẩm");
  }

  private void setupCellFactories() {
    // --- 1. listAuctioning ---
    listAuctioning.setCellFactory(listView -> new ListCell<AuctionItemDAO>() {
      private Node graphicContent;
      private AuctioningCardController cardController;

      {
        try {
          FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctioningCard.fxml"));
          graphicContent = loader.load();
          cardController = loader.getController();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      @Override
      protected void updateItem(AuctionItemDAO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
        } else {
          cardController.setData(item);
          setGraphic(graphicContent);
        }
      }
    });

    // --- 2. listPending ---
    listPending.setCellFactory(listView -> new ListCell<AuctionItemDAO>() {
      private Node graphicContent;
      private PendingCardController cardController;

      {
        try {
          FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/PendingCard.fxml"));
          graphicContent = loader.load();
          cardController = loader.getController();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      @Override
      protected void updateItem(AuctionItemDAO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
        } else {
          cardController.setData(item);
          setGraphic(graphicContent);
        }
      }
    });

    // --- 3. listSold ---
    listSold.setCellFactory(listView -> new ListCell<AuctionItemDAO>() {
      private Node graphicContent;
      private SoldCardController cardController;

      {
        try {
          FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/SoldCard.fxml"));
          graphicContent = loader.load();
          cardController = loader.getController();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      @Override
      protected void updateItem(AuctionItemDAO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
        } else {
          cardController.setData(item);
          setGraphic(graphicContent);
        }
      }
    });
  }
  private void switchTab(HBox activeBtn, ScrollPane activePane) {
    // --- BƯỚC 1: Ẩn tất cả các màn hình ---
    paneAuctioning.setVisible(false); paneAuctioning.setManaged(false);
    panePending.setVisible(false); panePending.setManaged(false);
    paneSold.setVisible(false); paneSold.setManaged(false);

    // paneNew.setVisible(false); paneNew.setManaged(false); // Bỏ comment nếu có

    // --- BƯỚC 2: Chỉ hiển thị màn hình được chọn ---
    activePane.setVisible(true);
    activePane.setManaged(true);

    // --- BƯỚC 3: Reset giao diện của tất cả các nút về mặc định (chưa chọn) ---
    String defaultStyle = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 0 0 0 3; -fx-padding: 11 18 11 18; -fx-cursor: hand;";
    btnAuctioning.setStyle(defaultStyle);
    btnPending.setStyle(defaultStyle);
    btnSold.setStyle(defaultStyle);

    // --- BƯỚC 4: Làm nổi bật nút vừa được bấm (thêm nền mờ và viền màu vàng bên trái) ---
    // Style này mình lấy đúng chuẩn thiết kế FXML ban đầu của bạn
    String activeStyle = "-fx-background-color: rgba(215,168,89,0.14); -fx-border-color: transparent transparent transparent #D7A859; -fx-border-width: 0 0 0 3; -fx-padding: 11 18 11 18; -fx-cursor: hand;";
    activeBtn.setStyle(activeStyle);
  }

  private void filterAndDisplay(String status, String keyword, ListView<AuctionItemDAO> listView, Label lblCount, Label badge) {
    ItemDAO dao = new ItemDAO();
    // Gọi database với keyword (nếu keyword rỗng "", hàm bên DAO sẽ tự động lấy tất cả)
    List<AuctionItemDAO> list = dao.getSellerProductsByStatusAndKeyword(currentSellerId, status, keyword);

    // Đẩy lên ListView
    listView.setItems(FXCollections.observableArrayList(list));

    // Cập nhật số lượng
    if (lblCount != null) {
      lblCount.setText("· " + list.size() + " sản phẩm");
    }
    if (badge != null) {
      badge.setText(String.valueOf(list.size()));
    }
  }
  @FXML
  void showInsertItemView(ActionEvent event) {
    try {
      // Load file InsertItemView.fxml
      // Lưu ý: Sửa lại đường dẫn string bên dưới cho khớp với cấu trúc thư mục project của bạn
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/InsertItemView.fxml"));
      Parent insertView = loader.load();
      System.out.println("...") ;
      // Xóa nội dung hiện tại và set nội dung mới vào mainContentArea
      mainContentArea.getChildren().setAll(insertView);

    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Lỗi không thể tải InsertItemView.fxml");
    }
  }
}