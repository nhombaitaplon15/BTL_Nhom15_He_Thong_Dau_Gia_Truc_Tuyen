package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import com.auction.common.network.AuctionItemDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

import java.util.function.Consumer;

/**
 * AuctionFinishedCardController — Refactored với Networking.
 *
 * THAY ĐỔI:
 *  - Xóa ItemService — nhận AuctionItemDAO để có Item sẵn
 *  - Lắng nghe ADMIN_TRANSACTION_APPROVED realtime: đổi badge sang "PAID"
 *    (Admin duyệt thanh toán → Seller thấy ngay trên card)
 */
public class AuctionFinishedCardController {

  @FXML private ImageView imgProduct;
  @FXML private Label     lblName;
  @FXML private Label     lblMeta;
  @FXML private Label     lblPriceStart;
  @FXML private Label     lblPriceFinal;
  @FXML private Label     lblSubStatus;
  @FXML private Label     lblStatusPill;
  @FXML private Button    btnRemind;
  @FXML private Button    btnView;

  private Runnable onRemindCallback;
  private Runnable onViewCallback;
  private int      currentAuctionId = -1;

  // Handler reference
  private Consumer<Message> onTransactionApproved;

  /**
   * Nhận AuctionItemDAO — không gọi DB.
   */
  public void setData(AuctionItemDTO dto) {
    Item    item    = dto.getItem();
    Auction auction = dto.getAuction();
    currentAuctionId = auction.getAuctionId();

    lblName      .setText(item.getName());
    lblPriceStart.setText("Khởi điểm: " + CardUtils.formatMoney(item.getStartingPrice()) + " UETệ");
    CardUtils.loadImage(imgProduct, item.getImgItem());

    applyStatusView(auction);
    registerRealtimeHandlers(auction);
  }

  /** Backward-compat: vẫn nhận Auction trực tiếp (không có Item) */
  public void setData(Auction auction) {
    currentAuctionId = auction.getAuctionId();
    applyStatusView(auction);
    registerRealtimeHandlers(auction);
  }

  // ════════════════════════════════════════
  // RENDER THEO TRẠNG THÁI
  // ════════════════════════════════════════

  private void applyStatusView(Auction auction) {
    switch (auction.getAuctionStatus()) {

      case "SOLD" -> {
        lblMeta.setText("Kết thúc " + auction.getEndTime()
            + " · Người thắng: " + auction.getCurrentWinnerId());
        lblPriceFinal.setText(CardUtils.formatMoney(auction.getCurrentPrice()) + " UETệ");
        lblPriceFinal.setStyle("-fx-font-size:15;-fx-font-weight:bold;-fx-text-fill:#43A047;");
        lblSubStatus .setText("✓ Đã thanh toán");
        lblSubStatus .setStyle("-fx-font-size:10.5;-fx-text-fill:#2E7D32;");
        lblStatusPill.setText("PAID");
        lblStatusPill.setStyle(pillStyle("#E8F5E9", "#2E7D32"));
        CardUtils.setVisible(btnRemind, false);
      }

      case "FINISHED" -> {
        lblMeta.setText("Kết thúc " + auction.getEndTime()
            + " · Người thắng: " + auction.getCurrentWinnerId());
        lblPriceFinal.setText(CardUtils.formatMoney(auction.getCurrentPrice()) + " UETệ");
        lblPriceFinal.setStyle("-fx-font-size:15;-fx-font-weight:bold;-fx-text-fill:#D7A859;");
        lblSubStatus .setText("⏳ Chờ thanh toán");
        lblSubStatus .setStyle("-fx-font-size:10.5;-fx-text-fill:#E65100;");
        lblStatusPill.setText("FINISHED");
        lblStatusPill.setStyle(pillStyle("#FFF3E0", "#E65100"));
        CardUtils.setVisible(btnRemind, true);
      }

      case "CANCELED" -> {
        lblMeta.setText("Kết thúc " + auction.getEndTime() + " · Không có người thắng");
        lblPriceFinal.setText("Không có bid");
        lblPriceFinal.setStyle("-fx-font-size:13;-fx-font-weight:normal;-fx-text-fill:#A08C6E;");
        lblSubStatus .setText("");
        lblStatusPill.setText("CANCELED");
        lblStatusPill.setStyle(pillStyle("#FFEBEE", "#B71C1C"));
        CardUtils.setVisible(btnRemind, false);
      }
    }
  }

  // ════════════════════════════════════════
  // REALTIME: Admin duyệt thanh toán → badge đổi sang PAID
  // ════════════════════════════════════════

  private void registerRealtimeHandlers(Auction auction) {
    onTransactionApproved = msg -> {
      // Payload: Integer transactionId — cần mapping auctionId nếu server hỗ trợ
      // Hiện tại: khi nhận ADMIN_TRANSACTION_APPROVED mà phiên đang FINISHED,
      // cập nhật badge sang PAID (phương án đơn giản nhất)
      if (!"FINISHED".equalsIgnoreCase(auction.getAuctionStatus())) return;
      lblStatusPill.setText("✓ PAID");
      lblStatusPill.setStyle(pillStyle("#E8F5E9", "#2E7D32"));
      lblSubStatus .setText("✓ Đã thanh toán");
      lblSubStatus .setStyle("-fx-font-size:10.5;-fx-text-fill:#2E7D32;");
      CardUtils.setVisible(btnRemind, false);
    };

    MessageRouter.getInstance().register(
        ResponseCode.ADMIN_TRANSACTION_APPROVED, onTransactionApproved);
  }

  /** GỌI KHI CELL BỊ RECYCLE */
  public void cleanupHandlers() {
    MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_APPROVED);
  }

  public void setOnRemindCallback(Runnable cb) { this.onRemindCallback = cb; }
  public void setOnViewCallback(Runnable cb)   { this.onViewCallback   = cb; }

  @FXML private void onRemind() { if (onRemindCallback != null) onRemindCallback.run(); }
  @FXML private void onView()   { if (onViewCallback   != null) onViewCallback.run(); }

  private String pillStyle(String bg, String fg) {
    return "-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";"
        + "-fx-font-size:10;-fx-font-weight:bold;"
        + "-fx-padding:3 10 3 10;-fx-background-radius:99;"
        + "-fx-min-width:80;-fx-alignment:CENTER;";
  }
}
