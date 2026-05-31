package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import com.auction.server.core.AuctionItemDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/**
 * AuctionRunningCardController — Refactored với Networking.
 *
 * THAY ĐỔI:
 *  - Xóa ItemService — nhận AuctionItemDAO để có Item sẵn
 *  - Lắng nghe NEW_BID_UPDATE realtime: cập nhật giá + bidder count trực tiếp
 *  - Lắng nghe AUCTION_TIME_EXTENDED: reset countdown
 *  - Lắng nghe AUCTION_ENDED: cập nhật trạng thái card
 *  - stopTimer() unregister handlers khi cell bị recycle
 */
public class AuctionRunningCardController extends BaseTimerCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label     lblName;
  @FXML private Label     lblMeta;
  @FXML private Label     lblPriceStart;
  @FXML private Label     lblPriceCur;
  @FXML private Label     lblBidCount;
  @FXML private Label     lblCountdown;
  @FXML private Circle    dotTimer;
  @FXML private Button    btnFollow;

  @Override protected Label  getLblCountdown() { return lblCountdown; }
  @Override protected Circle getDotTimer()     { return dotTimer; }

  private Runnable onFollowCallback;
  private int      currentAuctionId = -1;

  // Handler references
  private Consumer<Message> onNewBid;
  private Consumer<Message> onTimeExtended;
  private Consumer<Message> onAuctionEnded;

  /**
   * Nhận AuctionItemDAO — không gọi DB.
   */
  public void setData(AuctionItemDTO dto) {
    Item    item    = dto.getItem();
    Auction auction = dto.getAuction();
    currentAuctionId = auction.getAuctionId();

    lblName    .setText(item.getName());
    lblMeta    .setText(item.getItemType()
        + " • Bắt đầu " + auction.getStartTime()
        + " • " + auction.getTotalBids() + " bidder");
    lblPriceStart.setText("Khởi điểm: " + CardUtils.formatMoney(item.getStartingPrice()) + " UETệ");
    lblPriceCur  .setText(CardUtils.formatMoney(auction.getCurrentPrice()) + " UETệ");
    lblBidCount  .setText("↑ " + auction.getTotalBids() + " lượt bid");

    CardUtils.loadImage(imgProduct, item.getImgItem());
    startCountdown(auction.getEndTime());

    registerRealtimeHandlers();
  }

  /** Backward-compat với code cũ truyền Auction trực tiếp */
  public void setData(Auction auction) {
    currentAuctionId = auction.getAuctionId();
    lblPriceCur.setText(CardUtils.formatMoney(auction.getCurrentPrice()) + " UETệ");
    lblBidCount.setText("↑ " + auction.getTotalBids() + " lượt bid");
    startCountdown(auction.getEndTime());
    registerRealtimeHandlers();
  }

  // ════════════════════════════════════════
  // REALTIME HANDLERS
  // ════════════════════════════════════════

  private void registerRealtimeHandlers() {
    onNewBid = msg -> {
      try {
        Object[] data     = (Object[]) msg.getPayload();
        int    auctionId  = Integer.parseInt(data[0].toString());
        double newPrice   = Double.parseDouble(data[1].toString());
        String bidderName = String.valueOf(data[2]);
        if (auctionId != currentAuctionId) return;

        // Cập nhật giá hiện tại
        lblPriceCur.setText(CardUtils.formatMoney(newPrice) + " UETệ");

        // Tăng bid count
        try {
          String cur = lblBidCount.getText().replaceAll("[^\\d]", "");
          int count  = Integer.parseInt(cur);
          lblBidCount.setText("↑ " + (count + 1) + " lượt bid");
        } catch (NumberFormatException ignored) {}

        // Cập nhật meta: bidder name
        if (lblMeta != null) {
          String curMeta = lblMeta.getText();
          // Giữ nguyên prefix, chỉ thay phần bidder cuối
          int idx = curMeta.lastIndexOf(" • ");
          String prefix = idx > 0 ? curMeta.substring(0, idx) : curMeta;
          lblMeta.setText(prefix + " • " + bidderName + " dẫn đầu");
        }

      } catch (Exception e) {
        System.err.println("[RUNNING_CARD] Lỗi xử lý NEW_BID_UPDATE: " + e.getMessage());
      }
    };

    onTimeExtended = msg -> {
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
    };

    onAuctionEnded = msg -> {
      try {
        Object[] data = (Object[]) msg.getPayload();
        int auctionId = Integer.parseInt(data[0].toString());
        if (auctionId != currentAuctionId) return;
        getLblCountdown().setText("Đã kết thúc");
        stopTimer();
      } catch (Exception e) {
        System.err.println("[RUNNING_CARD] Lỗi xử lý AUCTION_ENDED: " + e.getMessage());
      }
    };

    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.NEW_BID_UPDATE,        onNewBid);
    r.register(ResponseCode.AUCTION_TIME_EXTENDED, onTimeExtended);
    r.register(ResponseCode.AUCTION_ENDED,         onAuctionEnded);
  }

  @Override
  public void stopTimer() {
    super.stopTimer();
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.NEW_BID_UPDATE);
    r.unregister(ResponseCode.AUCTION_TIME_EXTENDED);
    r.unregister(ResponseCode.AUCTION_ENDED);
  }

  public void setOnFollowCallback(Runnable cb) { this.onFollowCallback = cb; }

  @FXML
  private void onFollow() {
    if (onFollowCallback != null) onFollowCallback.run();
  }
}