package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/**
 * AuctionOpenCardController — Refactored với Networking.
 *
 * THAY ĐỔI:
 *  - Xóa ItemService (setData nhận AuctionItemDAO thay vì chỉ Auction để có Item)
 *  - Lắng nghe NEW_BID_UPDATE realtime để cập nhật giá + bid count trực tiếp trên card
 *  - Lắng nghe AUCTION_TIME_EXTENDED để reset countdown khi phiên được gia hạn
 *  - stopTimer() gọi cả unregister để dọn handler khi cell bị recycle
 */
public class AuctionOpenCardController extends BaseTimerCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label     lblName;
  @FXML private Label     lblMeta;
  @FXML private Label     lblPriceStart;
  @FXML private Label     lblPriceCur;
  @FXML private Label     lblBidCount;
  @FXML private Label     lblCountdown;
  @FXML private Circle    dotTimer;
  @FXML private Button    btnDetail;

  @Override protected Label  getLblCountdown() { return lblCountdown; }
  @Override protected Circle getDotTimer()     { return dotTimer; }

  private Runnable onDetailCallback;
  private int      currentAuctionId = -1;

  // Handler references
  private Consumer<Message> onNewBid;
  private Consumer<Message> onTimeExtended;

  /**
   * Nhận AuctionItemDAO để có đủ Item (tên, ảnh, giá) + Auction (trạng thái, bid).
   * Không gọi DB / ItemService nữa.
   */
  public void setData(com.auction.server.dao.AuctionItemDAO dto) {
    Item    item    = dto.getItem();
    Auction auction = dto.getAuction();
    currentAuctionId = auction.getAuctionId();

    lblName    .setText(item.getName());
    lblMeta    .setText(item.getItemType() + " • Mở lúc " + auction.getStartTime());
    lblPriceStart.setText("Khởi điểm: " + CardUtils.formatMoney(item.getStartingPrice()) + " UETệ");
    lblPriceCur  .setText(CardUtils.formatMoney(auction.getCurrentPrice()) + " UETệ");
    lblBidCount  .setText(auction.getTotalBids() + " lượt bid");

    CardUtils.loadImage(imgProduct, item.getImgItem());
    startCountdown(auction.getEndTime());

    registerRealtimeHandlers();
  }

  /** Backward-compat: vẫn hoạt động nếu code cũ truyền Auction + tên item */
  public void setData(Auction auction) {
    currentAuctionId = auction.getAuctionId();
    lblPriceCur  .setText(CardUtils.formatMoney(auction.getCurrentPrice()) + " UETệ");
    lblBidCount  .setText(auction.getTotalBids() + " lượt bid");
    startCountdown(auction.getEndTime());
    registerRealtimeHandlers();
  }

  // ════════════════════════════════════════
  // REALTIME HANDLERS
  // ════════════════════════════════════════

  private void registerRealtimeHandlers() {
    onNewBid = msg -> {
      try {
        Object[] data = (Object[]) msg.getPayload();
        int    auctionId = Integer.parseInt(data[0].toString());
        double newPrice  = Double.parseDouble(data[1].toString());
        if (auctionId != currentAuctionId) return;

        lblPriceCur.setText(CardUtils.formatMoney(newPrice) + " UETệ");
        // Tăng bid count
        try {
          int cur = Integer.parseInt(lblBidCount.getText().replaceAll("[^\\d]", ""));
          lblBidCount.setText((cur + 1) + " lượt bid");
        } catch (NumberFormatException ignored) {}

      } catch (Exception e) {
        System.err.println("[OPEN_CARD] Lỗi xử lý NEW_BID_UPDATE: " + e.getMessage());
      }
    };

    onTimeExtended = msg -> {
      try {
        Object[] data = (Object[]) msg.getPayload();
        int auctionId = Integer.parseInt(data[0].toString());
        if (auctionId != currentAuctionId) return;
        // data[1] là LocalDateTime newEndTime
        if (data[1] instanceof java.time.LocalDateTime newEnd) {
          startCountdown(newEnd); // reset countdown với thời gian mới
        }
      } catch (Exception e) {
        System.err.println("[OPEN_CARD] Lỗi xử lý TIME_EXTENDED: " + e.getMessage());
      }
    };

    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.NEW_BID_UPDATE,        onNewBid);
    r.register(ResponseCode.AUCTION_TIME_EXTENDED, onTimeExtended);
  }

  /** Override stopTimer để unregister handler khi cell bị recycle */
  @Override
  public void stopTimer() {
    super.stopTimer();
    unregisterHandlers();
  }

  private void unregisterHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.NEW_BID_UPDATE);
    r.unregister(ResponseCode.AUCTION_TIME_EXTENDED);
  }

  public void setOnDetailCallback(Runnable cb) { this.onDetailCallback = cb; }

  @FXML private void onDetail() { if (onDetailCallback != null) onDetailCallback.run(); }
}