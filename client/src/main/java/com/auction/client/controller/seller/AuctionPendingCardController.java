package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import com.auction.common.network.AuctionItemDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class AuctionPendingCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label     lblName;
  @FXML private Label     lblCreatedAt;
  @FXML private Label     lblPrice;
  @FXML private Label     lblStartTime;
  @FXML private Label     lblStatusBadge;
  @FXML private Button    btnEdit;
  @FXML private Button    btnCancel;

  private Runnable onEditCallback;
  private Runnable onCancelCallback;
  private int currentAuctionId = -1;

  private Consumer<Message> onApproved;
  private Consumer<Message> onRejected;

  public void setData(AuctionItemDTO dto) {
    Item item = dto.getItem();
    Auction auction = dto.getAuction();
    currentAuctionId = auction.getAuctionId();

    if (lblName != null) lblName.setText(item.getName());
    if (lblPrice != null) lblPrice.setText("Khởi điểm: " + CardUtils.formatMoney(item.getStartingPrice()) + " UETệ");

    if (lblCreatedAt != null) {
      String created = item.getCreatedAt() != null ? item.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "N/A";
      lblCreatedAt.setText("Tạo lúc: " + created);
    }

    if (lblStartTime != null) {
      String start = auction.getStartTime() != null ? auction.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "Chưa có";
      lblStartTime.setText("Bắt đầu: " + start);
    }

    CardUtils.loadImage(imgProduct, item.getImgItem());
    registerRealtimeHandlers(auction);
  }

  private void registerRealtimeHandlers(Auction auction) {
    onApproved = msg -> {
      Platform.runLater(() -> {
        try {
          Integer auctionId = (Integer) msg.getPayload();
          if (auctionId != currentAuctionId) return;
          if (lblStatusBadge != null) {
            lblStatusBadge.setText("✓ Đã duyệt");
            lblStatusBadge.setStyle("-fx-background-color:#E8F5E9;-fx-text-fill:#2E7D32;-fx-padding:3 10 3 10;-fx-background-radius:99;-fx-font-size:10;");
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    };

    onRejected = msg -> {
      Platform.runLater(() -> {
        try {
          Object[] data = (Object[]) msg.getPayload();
          int auctionId = Integer.parseInt(data[0].toString());
          String reason = data.length > 1 ? String.valueOf(data[1]) : "Không rõ lý do";
          if (auctionId != currentAuctionId) return;

          if (lblStatusBadge != null) {
            lblStatusBadge.setText("✗ Từ chối");
            lblStatusBadge.setStyle("-fx-background-color:#FFEBEE;-fx-text-fill:#B71C1C;-fx-padding:3 10 3 10;-fx-background-radius:99;-fx-font-size:10;");
          }
          if (lblCreatedAt != null) lblCreatedAt.setText("Lý do từ chối: " + reason);

          CardUtils.setVisible(btnEdit, false);
          CardUtils.setVisible(btnCancel, false);
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    };

    MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTION_APPROVED, onApproved);
    MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTION_REJECTED, onRejected);
  }

  public void cleanupHandlers() {
    MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTION_APPROVED);
    MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTION_REJECTED);
  }

  public void setOnEditCallback(Runnable cb) { this.onEditCallback = cb; }
  public void setOnCancelCallback(Runnable cb) { this.onCancelCallback = cb; }

  @FXML private void onEdit() { if (onEditCallback != null) onEditCallback.run(); }
  @FXML private void onCancel() { if (onCancelCallback != null) onCancelCallback.run(); }
}