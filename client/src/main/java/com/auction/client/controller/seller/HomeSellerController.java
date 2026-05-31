package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.server.dao.AuctionItemDAO;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class HomeSellerController {

  @FXML private Label lblActiveAuctions;
  @FXML private Label lblPendingPayments;
  @FXML private Label lblExpectedRevenue;
  @FXML private Label lblTodayBids;
  @FXML private ListView<String>        listRealtimeFeed;
  @FXML private TableView<AuctionItemDAO> tblEndingSoon;
  @FXML private TableColumn<AuctionItemDAO, String> colItemName;
  @FXML private TableColumn<AuctionItemDAO, String> colStartPrice;
  @FXML private TableColumn<AuctionItemDAO, String> colCurrentPrice;
  @FXML private TableColumn<AuctionItemDAO, String> colEndTime;
  @FXML private TableColumn<AuctionItemDAO, String> colStatus;

  // ── Khai báo đường dây liên lạc (Callback) để nhờ Controller chính đổi tab ──
  public static Runnable onRequireSwitchToMyProducts;

  // ── Handler references để unregister ──
  private final java.util.function.Consumer<Message> onAuctionsResult  = this::handleInitialAuctionsData;
  private final java.util.function.Consumer<Message> onNewBid          = this::handleNewBid;
  private final java.util.function.Consumer<Message> onAuctionApproved = this::handleAuctionApproved;
  private final java.util.function.Consumer<Message> onAuctionSold     = this::handleAuctionSold;
  private final java.util.function.Consumer<Message> onAuctionEnded    = this::handleAuctionEnded;
  private final java.util.function.Consumer<Message> onTimeExtended    = this::handleTimeExtended;

  @FXML
  public void initialize() {
    setupTableColumns();
    registerNetworkHandlers();

    // Lấy dữ liệu ban đầu
    SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_AUCTIONS, null);
  }

  // ════════════════════════════════════════
  // XỬ LÝ SỰ KIỆN NÚT "THÊM SẢN PHẨM MỚI"
  // ════════════════════════════════════════
  @FXML
  public void handleAddNewProduct(ActionEvent event) {
    // 1. Nhờ MainController (nơi chứa menu ngang) đổi tab sang "Sản phẩm của bạn"
    if (onRequireSwitchToMyProducts != null) {
      onRequireSwitchToMyProducts.run();
    }

    // 2. Ép MyProductsController mở form thêm sản phẩm ngay lập tức
    MyProductsController.openInsertFormDirectly();
  }

  // ════════════════════════════════════════
  // ĐĂNG KÝ / HUỶ HANDLER
  // ════════════════════════════════════════

  private void registerNetworkHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.SELLER_AUCTIONS_RESULT,  onAuctionsResult);
    r.register(ResponseCode.NEW_BID_UPDATE,          onNewBid);
    r.register(ResponseCode.SELLER_AUCTION_APPROVED, onAuctionApproved);
    r.register(ResponseCode.SELLER_AUCTION_SOLD,     onAuctionSold);
    r.register(ResponseCode.AUCTION_ENDED,           onAuctionEnded);
    r.register(ResponseCode.AUCTION_TIME_EXTENDED,   onTimeExtended);
  }

  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.SELLER_AUCTIONS_RESULT);
    r.unregister(ResponseCode.NEW_BID_UPDATE);
    r.unregister(ResponseCode.SELLER_AUCTION_APPROVED);
    r.unregister(ResponseCode.SELLER_AUCTION_SOLD);
    r.unregister(ResponseCode.AUCTION_ENDED);
    r.unregister(ResponseCode.AUCTION_TIME_EXTENDED);
  }

  // ════════════════════════════════════════
  // XỬ LÝ RESPONSE TỪ SERVER
  // ════════════════════════════════════════

  @SuppressWarnings("unchecked")
  private void handleInitialAuctionsData(Message message) {
    try {
      List<AuctionItemDAO> list = (List<AuctionItemDAO>) message.getPayload();
      if (list == null) return;

      int activeCount = 0, pendingCount = 0;
      double expectedRevenue = 0;
      ObservableList<AuctionItemDAO> tableData = FXCollections.observableArrayList();

      for (AuctionItemDAO row : list) {
        Auction a = row.getAuction();
        String status = a.getAuctionStatus();

        if ("OPEN".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status)) {
          activeCount++;
          expectedRevenue += a.getCurrentPrice();
          tableData.add(row);

          SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, a.getAuctionId());

        } else if ("SOLD".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) {
          pendingCount++;
          expectedRevenue += a.getCurrentPrice();
        }
      }

      lblActiveAuctions .setText(String.valueOf(activeCount));
      lblPendingPayments.setText(String.valueOf(pendingCount));
      lblExpectedRevenue.setText(formatMoney(expectedRevenue) + " UETệ");
      lblTodayBids      .setText("0");
      tblEndingSoon     .setItems(tableData);

    } catch (Exception e) {
      System.err.println("[HOME] Lỗi load dữ liệu ban đầu: " + e.getMessage());
    }
  }

  private void handleNewBid(Message message) {
    try {
      Object payload = message.getPayload();
      if (!(payload instanceof Object[] data)) return;

      int    auctionId  = Integer.parseInt(data[0].toString());
      double newPrice   = Double.parseDouble(data[1].toString());
      String bidderName = String.valueOf(data[2]);

      addFeedItem(String.format("⚡ %s vừa bid %s UETệ cho phiên #%d",
          bidderName, formatMoney(newPrice), auctionId));

      lblExpectedRevenue.setText(formatMoney(newPrice) + " UETệ");
      incrementLabel(lblTodayBids, 1);

      for (AuctionItemDAO row : tblEndingSoon.getItems()) {
        if (row.getAuction().getAuctionId() == auctionId) {
          row.getAuction().setCurrentPrice(newPrice);
          row.getAuction().setTotalBids(row.getAuction().getTotalBids() + 1);
          break;
        }
      }
      tblEndingSoon.refresh();

    } catch (Exception e) {
      System.err.println("[HOME] Lỗi xử lý NEW_BID_UPDATE: " + e.getMessage());
    }
  }

  private void handleAuctionApproved(Message message) {
    Integer auctionId = (Integer) message.getPayload();
    addFeedItem("🟢 Admin vừa duyệt và mở phiên #" + auctionId);
    incrementLabel(lblActiveAuctions, 1);
    SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_AUCTIONS, null);
  }

  private void handleAuctionSold(Message message) {
    try {
      Object[] payload = (Object[]) message.getPayload();
      int    auctionId  = Integer.parseInt(payload[0].toString());
      double finalPrice = Double.parseDouble(payload[1].toString());
      String buyerName  = String.valueOf(payload[2]);

      addFeedItem(String.format("🔥 Phiên #%d đã chốt %s UETệ — Người mua: %s",
          auctionId, formatMoney(finalPrice), buyerName));

      incrementLabel(lblActiveAuctions,  -1);
      incrementLabel(lblPendingPayments,  1);

      tblEndingSoon.getItems().removeIf(r -> r.getAuction().getAuctionId() == auctionId);

    } catch (Exception e) {
      System.err.println("[HOME] Lỗi xử lý SELLER_AUCTION_SOLD: " + e.getMessage());
    }
  }

  private void handleAuctionEnded(Message message) {
    try {
      Object[] payload = (Object[]) message.getPayload();
      int auctionId = Integer.parseInt(payload[0].toString());
      addFeedItem("🏁 Phiên #" + auctionId + " đã kết thúc.");
      tblEndingSoon.getItems().removeIf(r -> r.getAuction().getAuctionId() == auctionId);
      incrementLabel(lblActiveAuctions, -1);
    } catch (Exception e) {
      System.err.println("[HOME] Lỗi xử lý AUCTION_ENDED: " + e.getMessage());
    }
  }

  private void handleTimeExtended(Message message) {
    try {
      Object[] payload = (Object[]) message.getPayload();
      int auctionId = Integer.parseInt(payload[0].toString());
      addFeedItem("⏱ Phiên #" + auctionId + " được gia hạn thêm thời gian!");
    } catch (Exception e) {
      System.err.println("[HOME] Lỗi xử lý AUCTION_TIME_EXTENDED: " + e.getMessage());
    }
  }

  // ════════════════════════════════════════
  // SETUP TABLE COLUMNS
  // ════════════════════════════════════════

  private void setupTableColumns() {
    colItemName   .setCellValueFactory(c ->
        new SimpleStringProperty(c.getValue().getItem().getName()));
    colStartPrice .setCellValueFactory(c ->
        new SimpleStringProperty(formatMoney(c.getValue().getAuction().getStartingPrice()) + " UETệ"));
    colCurrentPrice.setCellValueFactory(c ->
        new SimpleStringProperty(formatMoney(c.getValue().getAuction().getCurrentPrice()) + " UETệ"));
    colEndTime    .setCellValueFactory(c -> {
      var endTime = c.getValue().getAuction().getEndTime();
      return new SimpleStringProperty(endTime != null ? endTime.toString() : "—");
    });
    colStatus     .setCellValueFactory(c ->
        new SimpleStringProperty(c.getValue().getAuction().getAuctionStatus()));
  }

  // ════════════════════════════════════════
  // HELPERS
  // ════════════════════════════════════════

  private void addFeedItem(String text) {
    listRealtimeFeed.getItems().add(0, text);
    if (listRealtimeFeed.getItems().size() > 50)
      listRealtimeFeed.getItems().remove(50, listRealtimeFeed.getItems().size());
  }

  private void incrementLabel(Label label, int delta) {
    try {
      int val = Integer.parseInt(label.getText().replaceAll("[^\\d\\-]", ""));
      int next = Math.max(0, val + delta);
      label.setText(String.valueOf(next));
    } catch (NumberFormatException ignored) {}
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format((long) amount);
  }
}