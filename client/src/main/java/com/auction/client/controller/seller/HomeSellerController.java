package com.auction.client.controller.seller;

import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.common.network.AuctionItemDTO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class HomeSellerController {

  @FXML private Label lblActiveAuctions;
  @FXML private Label lblPendingPayments;
  @FXML private Label lblExpectedRevenue;
  @FXML private Label lblTodayBids;
  @FXML private ListView<String> listRealtimeFeed;
  @FXML private TableView<AuctionItemDTO> tblEndingSoon;
  @FXML private TableColumn<AuctionItemDTO, String> colItemName;
  @FXML private TableColumn<AuctionItemDTO, String> colStartPrice;
  @FXML private TableColumn<AuctionItemDTO, String> colCurrentPrice;
  @FXML private TableColumn<AuctionItemDTO, String> colEndTime;
  @FXML private TableColumn<AuctionItemDTO, String> colStatus;

  public static Runnable onRequireSwitchToMyProducts;

  private final Consumer<Message> onAuctionsResult = this::handleAuctionsResult;
  private final Consumer<Message> onNewBid = this::handleNewBidUpdate;
  private final Consumer<Message> onAuctionApproved = this::handleAuctionApproved;
  private final Consumer<Message> onAuctionSold = this::handleAuctionSold;
  private final Consumer<Message> onAuctionEnded = this::handleAuctionEnded;
  private final Consumer<Message> onTimeExtended = this::handleTimeExtended;

  private int myId;

  @FXML
  public void initialize() {
    myId = ClientSession.getInstance().getUserId();
    setupTableCols();
    registerNetworkHandlers();
    loadDashboardData();
  }

  @FXML
  public void handleAddNewProduct(ActionEvent event) {
    if (onRequireSwitchToMyProducts != null) {
      onRequireSwitchToMyProducts.run();
    }
    MyProductsController.openInsertFormDirectly();
  }

  private void registerNetworkHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.SELLER_AUCTIONS_RESULT, onAuctionsResult);
    r.register(ResponseCode.NEW_BID_UPDATE, onNewBid);
    r.register(ResponseCode.SELLER_AUCTION_APPROVED, onAuctionApproved);
    r.register(ResponseCode.SELLER_AUCTION_SOLD, onAuctionSold);
    r.register(ResponseCode.AUCTION_ENDED, onAuctionEnded);
    r.register(ResponseCode.AUCTION_TIME_EXTENDED, onTimeExtended);
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

  private void loadDashboardData() {
    CompletableFuture.runAsync(() -> {
      SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_AUCTIONS, myId);
    });
  }

  @SuppressWarnings("unchecked")
  private void handleAuctionsResult(Message msg) {
    if (!(msg.getPayload() instanceof List<?> list)) return;
    List<AuctionItemDTO> auctions = (List<AuctionItemDTO>) list;

    CompletableFuture.runAsync(() -> {
      for (AuctionItemDTO row : auctions) {
        Auction a = row.getAuction();
        String status = a.getAuctionStatus();
        if ("OPEN".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status)) {
          SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, a.getAuctionId());
        }
      }
    });

    Platform.runLater(() -> {
      long active = 0;
      long pendingPay = 0;
      double revenue = 0;
      int todayBids = 0;

      ObservableList<AuctionItemDTO> activeTableData = FXCollections.observableArrayList();

      for (AuctionItemDTO row : auctions) {
        Auction a = row.getAuction();
        String status = a.getAuctionStatus();

        if ("OPEN".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status)) {
          active++;
          revenue += a.getCurrentPrice();
          todayBids += a.getTotalBids();
          activeTableData.add(row);
        } else if ("SOLD".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) {
          pendingPay++;
          revenue += a.getCurrentPrice();
        } else if ("PAID".equalsIgnoreCase(status)) {
          revenue += a.getCurrentPrice();
        }
      }

      lblActiveAuctions.setText(String.valueOf(active));
      lblPendingPayments.setText(String.valueOf(pendingPay));
      lblExpectedRevenue.setText(formatMoney(revenue) + " UETệ");
      lblTodayBids.setText(String.valueOf(todayBids));

      activeTableData.sort((a, b) -> {
        if (a.getAuction().getEndTime() == null) return 1;
        if (b.getAuction().getEndTime() == null) return -1;
        return a.getAuction().getEndTime().compareTo(b.getAuction().getEndTime());
      });

      if (activeTableData.size() > 5) {
        tblEndingSoon.setItems(FXCollections.observableArrayList(activeTableData.subList(0, 5)));
      } else {
        tblEndingSoon.setItems(activeTableData);
      }
    });
  }

  private void handleNewBidUpdate(Message msg) {
    Platform.runLater(() -> {
      try {
        Object[] data = (Object[]) msg.getPayload();
        int auctionId = Integer.parseInt(data[0].toString());
        double newPrice = Double.parseDouble(data[1].toString());
        String winner = String.valueOf(data[2]);

        addFeedItem(String.format("⚡ %s vừa bid %s UETệ cho phiên #%d", winner, formatMoney(newPrice), auctionId));
        incrementLabel(lblTodayBids, 1);

        double currentRevenue = parseMoneyFromLabel(lblExpectedRevenue.getText());
        double difference = newPrice;

        for (AuctionItemDTO row : tblEndingSoon.getItems()) {
          if (row.getAuction().getAuctionId() == auctionId) {
            difference = newPrice - row.getAuction().getCurrentPrice();
            row.getAuction().setCurrentPrice(newPrice);
            row.getAuction().setTotalBids(row.getAuction().getTotalBids() + 1);
            break;
          }
        }

        lblExpectedRevenue.setText(formatMoney(currentRevenue + difference) + " UETệ");
        tblEndingSoon.refresh();

      } catch (Exception e) {
        System.err.println("[HOME] Error NEW_BID_UPDATE: " + e.getMessage());
      }
    });
  }

  private void handleAuctionApproved(Message message) {
    Platform.runLater(() -> {
      Integer auctionId = (Integer) message.getPayload();
      addFeedItem("🟢 Admin vừa duyệt và mở phiên #" + auctionId);
      incrementLabel(lblActiveAuctions, 1);
      loadDashboardData();
    });
  }

  private void handleAuctionSold(Message message) {
    Platform.runLater(() -> {
      try {
        Object[] payload = (Object[]) message.getPayload();
        int auctionId = Integer.parseInt(payload[0].toString());
        double finalPrice = Double.parseDouble(payload[1].toString());
        String buyerName = String.valueOf(payload[2]);

        addFeedItem(String.format("🔥 Phiên #%d đã chốt %s UETệ — Người mua: %s", auctionId, formatMoney(finalPrice), buyerName));
        incrementLabel(lblActiveAuctions, -1);
        incrementLabel(lblPendingPayments, 1);
        tblEndingSoon.getItems().removeIf(r -> r.getAuction().getAuctionId() == auctionId);
      } catch (Exception e) {
        System.err.println("[HOME] Error SELLER_AUCTION_SOLD: " + e.getMessage());
      }
    });
  }

  private void handleAuctionEnded(Message message) {
    Platform.runLater(() -> {
      try {
        Object[] payload = (Object[]) message.getPayload();
        int auctionId = Integer.parseInt(payload[0].toString());

        addFeedItem("🏁 Phiên #" + auctionId + " đã kết thúc.");
        tblEndingSoon.getItems().removeIf(r -> r.getAuction().getAuctionId() == auctionId);
        incrementLabel(lblActiveAuctions, -1);
      } catch (Exception e) {
        System.err.println("[HOME] Error AUCTION_ENDED: " + e.getMessage());
      }
    });
  }

  private void handleTimeExtended(Message message) {
    Platform.runLater(() -> {
      try {
        Object[] payload = (Object[]) message.getPayload();
        int auctionId = Integer.parseInt(payload[0].toString());
        addFeedItem("⏱ Phiên #" + auctionId + " được gia hạn thêm thời gian!");
      } catch (Exception e) {
        System.err.println("[HOME] Error AUCTION_TIME_EXTENDED: " + e.getMessage());
      }
    });
  }

  private void setupTableCols() {
    colItemName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem().getName()));
    colStartPrice.setCellValueFactory(c -> new SimpleStringProperty(formatMoney(c.getValue().getAuction().getStartingPrice()) + " UETệ"));
    colCurrentPrice.setCellValueFactory(c -> new SimpleStringProperty(formatMoney(c.getValue().getAuction().getCurrentPrice()) + " UETệ"));
    colEndTime.setCellValueFactory(c -> {
      var endTime = c.getValue().getAuction().getEndTime();
      return new SimpleStringProperty(endTime != null ? endTime.toString() : "—");
    });
    colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAuction().getAuctionStatus()));
  }

  private void addFeedItem(String text) {
    listRealtimeFeed.getItems().add(0, text);
    if (listRealtimeFeed.getItems().size() > 50) {
      listRealtimeFeed.getItems().remove(50, listRealtimeFeed.getItems().size());
    }
  }

  private void incrementLabel(Label label, int delta) {
    try {
      int val = Integer.parseInt(label.getText().replaceAll("[^\\d\\-]", ""));
      int next = Math.max(0, val + delta);
      label.setText(String.valueOf(next));
    } catch (NumberFormatException ignored) {}
  }

  private double parseMoneyFromLabel(String text) {
    try {
      String cleanStr = text.replaceAll("[^\\d]", "");
      if (cleanStr.isEmpty()) return 0;
      return Double.parseDouble(cleanStr);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount);
  }
}