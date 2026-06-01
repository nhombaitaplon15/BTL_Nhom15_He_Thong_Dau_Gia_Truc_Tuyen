package com.auction.client.controller.seller;

import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.server.dao.AuctionItemDAO;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ProductsTabController — Refactored với Networking.
 *
 * THAY ĐỔI:
 *  - Xóa AuctionDAO + Task/Thread thủ công
 *  - Gửi SELLER_GET_MY_AUCTIONS qua SocketClient, nhận SELLER_AUCTIONS_RESULT qua MessageRouter
 *  - Filter local trên cache thay vì query DB lại
 *  - Realtime: tự refresh khi nhận SELLER_AUCTION_APPROVED / REJECTED / SOLD
 */
public class ProductsTabController implements Initializable {

  @FXML private VBox      productListVBox;
  @FXML private TextField searchField;
  @FXML private Button    filterButton;

  private ProductsCardController.CardType cardType;
  private String dbStatus;
  private List<AuctionItemDAO> cachedData = List.of();

  // Handler reference để unregister
  private Consumer<Message> onAuctionsResult;
  private final Consumer<Message> onApproved = msg -> requestData();
  private final Consumer<Message> onRejected = msg -> requestData();
  private final Consumer<Message> onSold     = msg -> requestData();

  int myId = ClientSession.getInstance().getUserId();
  @Override
  public void initialize(URL location, ResourceBundle resources) {
    // init() được gọi sau khi load
  }

  /**
   * Phải gọi ngay sau loader.getController() để truyền context.
   */
  public void init(int sellerId, ProductsCardController.CardType type) {
    this.cardType = type;
    this.dbStatus = switch (type) {
      case AUCTIONING -> "RUNNING";
      case PENDING    -> "WAITING_FOR_ADMIN";
      case SOLD       -> "ENDED";
    };

    onAuctionsResult = this::handleAuctionsResult;

    registerNetworkHandlers();

    filterButton.setOnAction(e -> filterAndDisplay(searchField.getText().trim()));
    searchField.setOnAction (e -> filterAndDisplay(searchField.getText().trim()));

    requestData();
  }

  private void registerNetworkHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.SELLER_AUCTIONS_RESULT,  onAuctionsResult);
    r.register(ResponseCode.SELLER_AUCTION_APPROVED, onApproved);
    r.register(ResponseCode.SELLER_AUCTION_REJECTED, onRejected);
    r.register(ResponseCode.SELLER_AUCTION_SOLD,     onSold);
  }

  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.SELLER_AUCTIONS_RESULT);
    r.unregister(ResponseCode.SELLER_AUCTION_APPROVED);
    r.unregister(ResponseCode.SELLER_AUCTION_REJECTED);
    r.unregister(ResponseCode.SELLER_AUCTION_SOLD);
  }

  // ════════════════════════════════════════
  // REQUEST + HANDLE
  // ════════════════════════════════════════

  private void requestData() {
    showLoading();
    SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_AUCTIONS, myId);
  }

  @SuppressWarnings("unchecked")
  private void handleAuctionsResult(Message message) {
    if (!(message.getPayload() instanceof List<?> list)) return;
    cachedData = ((List<AuctionItemDAO>) list).stream()
        .filter(a -> dbStatus.equalsIgnoreCase(a.getAuction().getAuctionStatus()))
        .collect(Collectors.toList());
    filterAndDisplay(searchField.getText().trim());
  }

  // ════════════════════════════════════════
  // FILTER + RENDER
  // ════════════════════════════════════════

  private void filterAndDisplay(String keyword) {
    List<AuctionItemDAO> filtered = (keyword == null || keyword.isBlank())
        ? cachedData
        : cachedData.stream()
        .filter(a -> a.getItem().getName().toLowerCase()
            .contains(keyword.toLowerCase()))
        .collect(Collectors.toList());

    productListVBox.getChildren().clear();

    if (filtered.isEmpty()) {
      productListVBox.getChildren().add(new Label(emptyMessage()));
      return;
    }

    for (AuctionItemDAO dto : filtered) {
      try {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/view/view/seller/ProductCard.fxml"));
        HBox card = loader.load();
        ProductsCardController cc = loader.getController();
        cc.setData(dto, cardType);
        productListVBox.getChildren().add(card);
      } catch (IOException ex) {
        System.err.println("[PRODUCTS_TAB] Lỗi load ProductCard.fxml: " + ex.getMessage());
      }
    }
  }

  private void showLoading() {
    productListVBox.getChildren().setAll(
        new ProgressIndicator(),
        new Label("Đang tải dữ liệu..."));
  }

  private String emptyMessage() {
    return switch (cardType) {
      case AUCTIONING -> "Không tìm thấy phiên đấu giá nào đang chạy.";
      case PENDING    -> "Không có sản phẩm nào đang chờ duyệt.";
      case SOLD       -> "Chưa có phiên đấu giá nào hoàn tất.";
    };
  }
}