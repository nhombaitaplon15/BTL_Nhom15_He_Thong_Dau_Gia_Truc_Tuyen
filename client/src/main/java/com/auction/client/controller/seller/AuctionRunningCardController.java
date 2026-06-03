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
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

public class AuctionRunningCardController extends BaseTimerCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label     lblName;
  @FXML private Label     lblMeta;
  @FXML private Label     lblPriceStart;
  @FXML private Label     lblPriceCur;
  @FXML private Label     lblBidCount;
  @FXML private Label     lblCountdown;
  @FXML private Button    btnFollow;
  @FXML private Circle    dotTimer;

  private Runnable onFollowCallback;
  private int currentAuctionId = -1;

  private Consumer<Message> onNewBid;
  private Consumer<Message> onTimeExtended;
  private Consumer<Message> onAuctionEnded;

  @Override protected Label  getLblCountdown() { return lblCountdown; }
  @Override protected Circle getDotTimer()     { return dotTimer; }

  public void setData(AuctionItemDTO dto) {
    Item item = dto.getItem();
    Auction auction = dto.getAuction();
    currentAuctionId = auction.getAuctionId();

    if (lblName != null) lblName.setText(item.getName());
    if (lblMeta != null) lblMeta.setText(item.getItemType() + " · Đang diễn ra");
    if (lblPriceStart != null) lblPriceStart.setText("Khởi điểm: " + CardUtils.formatMoney(item.getStartingPrice()) + " UETệ");
    if (lblPriceCur != null) lblPriceCur.setText("Hiện tại: " + CardUtils.formatMoney(auction.getCurrentPrice()) + " UETệ");
    if (lblBidCount != null) lblBidCount.setText(auction.getTotalBids() + " lượt bid");

    CardUtils.loadImage(imgProduct, item.getImgItem());

    //setLblCountdown(lblCountdown);
    startCountdown(auction.getEndTime());

    registerRealtimeHandlers();
  }

  private void registerRealtimeHandlers() {
    onNewBid = msg -> {
      Platform.runLater(() -> {
        try {
          Object[] data = (Object[]) msg.getPayload();
          int auctionId = Integer.parseInt(data[0].toString());
          if (auctionId != currentAuctionId) return;

          double newPrice = Double.parseDouble(data[1].toString());
          if (lblPriceCur != null) lblPriceCur.setText("Hiện tại: " + CardUtils.formatMoney(newPrice) + " UETệ");

          try {
            String currentBidsStr = lblBidCount.getText().replaceAll("[^0-9]", "");
            int curBids = currentBidsStr.isEmpty() ? 0 : Integer.parseInt(currentBidsStr);
            if (lblBidCount != null) lblBidCount.setText((curBids + 1) + " lượt bid");
          } catch (Exception ignored) {}

        } catch (Exception e) {
          System.err.println("[RUNNING_CARD] Lỗi xử lý NEW_BID_UPDATE: " + e.getMessage());
        }
      });
    };

    onTimeExtended = msg -> {
      Platform.runLater(() -> {
        try {
          Object[] data = (Object[]) msg.getPayload();
          int auctionId = Integer.parseInt(data[0].toString());
          if (auctionId != currentAuctionId) return;

          if (data[1] instanceof java.time.LocalDateTime newEnd) {
            startCountdown(newEnd);
          }
        } catch (Exception e) {
          System.err.println("[RUNNING_CARD] Lỗi xử lý TIME_EXTENDED: " + e.getMessage());
        }
      });
    };

    onAuctionEnded = msg -> {
      Platform.runLater(() -> {
        try {
          Object[] data = (Object[]) msg.getPayload();
          int auctionId = Integer.parseInt(data[0].toString());
          if (auctionId != currentAuctionId) return;

          if (getLblCountdown() != null) getLblCountdown().setText("Đã kết thúc");
          stopTimer();
        } catch (Exception e) {
          System.err.println("[RUNNING_CARD] Lỗi xử lý AUCTION_ENDED: " + e.getMessage());
        }
      });
    };

    MessageRouter.getInstance().register(ResponseCode.NEW_BID_UPDATE, onNewBid);
    MessageRouter.getInstance().register(ResponseCode.AUCTION_TIME_EXTENDED, onTimeExtended);
    MessageRouter.getInstance().register(ResponseCode.AUCTION_ENDED, onAuctionEnded);
  }

  @Override
  public void stopTimer() {
    super.stopTimer();
    unregisterHandlers();
  }

  private void unregisterHandlers() {
    MessageRouter.getInstance().unregister(ResponseCode.NEW_BID_UPDATE);
    MessageRouter.getInstance().unregister(ResponseCode.AUCTION_TIME_EXTENDED);
    MessageRouter.getInstance().unregister(ResponseCode.AUCTION_ENDED);
  }

  public void setOnFollowCallback(Runnable cb) { this.onFollowCallback = cb; }

  @FXML private void onFollow() { if (onFollowCallback != null) onFollowCallback.run(); }
}