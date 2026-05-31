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

import java.util.function.Consumer;

/**
 * AuctionPendingCardController — Refactored với Networking.
 *
 * THAY ĐỔI:
 *  - Xóa ItemService — nhận AuctionItemDAO để có Item sẵn
 *  - Lắng nghe SELLER_AUCTION_APPROVED realtime: đổi màu badge sang "Đã duyệt"
 *  - Lắng nghe SELLER_AUCTION_REJECTED realtime: hiện lý do từ chối
 *  - cleanupHandlers() unregister khi cell bị recycle
 */
public class AuctionPendingCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label     lblName;
  @FXML private Label     lblCreatedAt;
  @FXML private Label     lblPrice;
  @FXML private Label     lblStartTime;
  @FXML private Label     lblStatusBadge; // badge trạng thái (có thể null nếu FXML chưa có)
  @FXML private Button    btnEdit;
  @FXML private Button    btnCancel;

  private Runnable onEditCallback;
  private Runnable onCancelCallback;
  private int      currentAuctionId = -1;

  // Handler references
  private Consumer<Message> onApproved;
  private Consumer<Message> onRejected;

  /**
   * Nhận AuctionItemDAO — không gọi DB.
   */
  public void setData(AuctionItemDTO dto) {
    Item    item    = dto.getItem();
    Auction auction = dto.getAuction();
    currentAuctionId = auction.getAuctionId();

    lblName      .setText(item.getName());
    lblCreatedAt .setText("Tạo phiên lúc " + auction.getCreatedAt());
    lblPrice     .setText(CardUtils.formatMoney(item.getStartingPrice()) + " UETệ");
    lblStartTime .setText("Bắt đầu: " + auction.getStartTime());
    CardUtils.loadImage(imgProduct, item.getImgItem());

    if (lblStatusBadge != null) {
      lblStatusBadge.setText("Chờ duyệt");
      lblStatusBadge.setStyle("-fx-background-color:#FFF8E1;-fx-text-fill:#F57F17;"
          + "-fx-padding:3 10 3 10;-fx-background-radius:99;-fx-font-size:10;");
    }

    registerRealtimeHandlers();
  }

  /** Backward-compat với code cũ truyền Auction trực tiếp */
  public void setData(Auction auction) {
    currentAuctionId = auction.getAuctionId();
    lblCreatedAt.setText("Tạo phiên lúc " + auction.getCreatedAt());
    lblStartTime.setText("Bắt đầu: " + auction.getStartTime());
    registerRealtimeHandlers();
  }

  // ════════════════════════════════════════
  // REALTIME HANDLERS
  // ════════════════════════════════════════

  private void registerRealtimeHandlers() {
    onApproved = msg -> {
      try {
        int auctionId = (Integer) msg.getPayload();
        if (auctionId != currentAuctionId) return;
        if (lblStatusBadge != null) {
          lblStatusBadge.setText("✓ Đã duyệt");
          lblStatusBadge.setStyle("-fx-background-color:#E8F5E9;-fx-text-fill:#2E7D32;"
              + "-fx-padding:3 10 3 10;-fx-background-radius:99;-fx-font-size:10;");
        }
        // Ẩn nút Sửa/Huỷ vì phiên đã được duyệt
        CardUtils.setVisible(btnEdit,   false);
        CardUtils.setVisible(btnCancel, false);
      } catch (Exception e) {
        System.err.println("[PENDING_CARD] Lỗi xử lý APPROVED: " + e.getMessage());
      }
    };

    onRejected = msg -> {
      try {
        Object[] data = (Object[]) msg.getPayload();
        int    auctionId = Integer.parseInt(data[0].toString());
        String reason    = data.length > 1 ? String.valueOf(data[1]) : "Không rõ lý do";
        if (auctionId != currentAuctionId) return;
        if (lblStatusBadge != null) {
          lblStatusBadge.setText("✗ Từ chối");
          lblStatusBadge.setStyle("-fx-background-color:#FFEBEE;-fx-text-fill:#B71C1C;"
              + "-fx-padding:3 10 3 10;-fx-background-radius:99;-fx-font-size:10;");
        }
        if (lblCreatedAt != null)
          lblCreatedAt.setText("Lý do từ chối: " + reason);
        CardUtils.setVisible(btnEdit,   false);
        CardUtils.setVisible(btnCancel, false);
      } catch (Exception e) {
        System.err.println("[PENDING_CARD] Lỗi xử lý REJECTED: " + e.getMessage());
      }
    };

    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.SELLER_AUCTION_APPROVED, onApproved);
    r.register(ResponseCode.SELLER_AUCTION_REJECTED, onRejected);
  }

  /** GỌI KHI CELL BỊ RECYCLE */
  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.SELLER_AUCTION_APPROVED);
    r.unregister(ResponseCode.SELLER_AUCTION_REJECTED);
  }

  public void setOnEditCallback(Runnable cb)   { this.onEditCallback = cb; }
  public void setOnCancelCallback(Runnable cb) { this.onCancelCallback = cb; }

  @FXML private void onEdit()   { if (onEditCallback   != null) onEditCallback.run(); }
  @FXML private void onCancel() { if (onCancelCallback != null) onCancelCallback.run(); }
}