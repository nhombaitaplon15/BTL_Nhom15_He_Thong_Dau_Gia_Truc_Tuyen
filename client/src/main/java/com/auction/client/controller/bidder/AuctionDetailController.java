package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * AuctionDetailController — Xem chi tiết phiên đấu giá từ màn hình Lịch sử.
 *
 * ĐÃ SỬA TOÀN DIỆN:
 * 1. Dùng RequestCode.GET_AUCTION_DETAIL / ResponseCode.AUCTION_DETAIL_RESULT thay vì
 *    lắng nghe nhầm BID_HISTORY_RESULT (server trả List<BidHistoryRow>, không phải Auction).
 * 2. Nút "Hủy kèo" dùng RequestCode.REJECT_WIN đúng chuẩn (truyền auctionId Integer).
 * 3. Nút "Xác nhận nhận hàng" dùng ADMIN_CREATE_TRANSACTION với payload đúng định dạng.
 * 4. Cleanup listener đầy đủ tránh memory leak.
 */
public class AuctionDetailController {

    private static final int[] ESCROW_ADMIN_IDS = {1, 2, 3, 4};

    @FXML private Label lblAuctionId;
    @FXML private ImageView imgProduct;
    @FXML private Label lblItemName;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblStartTime;
    @FXML private Label lblEndTime;
    @FXML private Label lblStatus;
    @FXML private Label lblDescription;
    @FXML private HBox hboxWinnerActions;

    private Timeline liveStatusTimeline;
    private Auction currentAuction;
    private User currentUser;

    @FXML
    public void initialize() {
        System.out.println("🔌 Khởi tạo AuctionDetailController.");
    }

    /**
     * Được BiddingHistoryController.handleViewDetail() gọi.
     * Gửi yêu cầu GET_AUCTION_DETAIL lên Server để lấy dữ liệu đầy đủ.
     */
    public void loadAuctionDetail(int auctionId, String fallbackName, User user) {
        this.currentUser = user;

        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName != null) lblItemName.setText(fallbackName != null ? fallbackName : "Đang tải...");
        if (lblStatus != null) lblStatus.setText("Đang tải dữ liệu...");

        if (hboxWinnerActions != null) {
            hboxWinnerActions.setVisible(false);
            hboxWinnerActions.setManaged(false);
        }

        // Làm sạch listener cũ, đăng ký mới
        cleanupListeners();
        MessageRouter.getInstance().register(ResponseCode.AUCTION_DETAIL_RESULT, this::handleAuctionDetailResult);
        MessageRouter.getInstance().register(ResponseCode.AUCTION_DETAIL_FAILED, this::handleAuctionDetailFailed);
        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_CREATED, this::handlePaySuccess);
        MessageRouter.getInstance().register(ResponseCode.REJECT_WIN_SUCCESS, this::handleRejectWinSuccess);
        MessageRouter.getInstance().register(ResponseCode.REJECT_WIN_FAILED, this::handleRejectWinFailed);

        // ✅ SỬA: Gửi đúng request code mới thay vì FETCH_BID_HISTORY
        SocketClient.getInstance().sendRequest(RequestCode.GET_AUCTION_DETAIL, auctionId);
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────────

    private void handleAuctionDetailResult(Message message) {
        // ✅ SỬA: Server giờ trả Auction object qua AUCTION_DETAIL_RESULT
        if (!(message.getPayload() instanceof Auction)) {
            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("Không thể tải dữ liệu phiên.");
            });
            return;
        }

        this.currentAuction = (Auction) message.getPayload();
        Item item = currentAuction.getItem();

        Platform.runLater(() -> {
            if (lblAuctionId != null) lblAuctionId.setText("#" + currentAuction.getAuctionId());

            if (lblCurrentPrice != null) {
                lblCurrentPrice.setText(String.format("%,.0f UETệ", currentAuction.getCurrentPrice()));
            }
            if (lblStartTime != null && currentAuction.getStartTime() != null) {
                lblStartTime.setText(currentAuction.getStartTime().toString().replace("T", " "));
            }
            if (lblEndTime != null && currentAuction.getEndTime() != null) {
                lblEndTime.setText(currentAuction.getEndTime().toString().replace("T", " "));
            }

            if (item != null) {
                if (lblItemName != null) lblItemName.setText(item.getName());
                if (lblDescription != null) lblDescription.setText(item.getDescription());

                if (item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                    try {
                        String path = item.getImgItem().trim();
                        if (!path.startsWith("/")) path = "/" + path;
                        InputStream is = getClass().getResourceAsStream(path);
                        if (is != null && imgProduct != null) imgProduct.setImage(new Image(is));
                    } catch (Exception e) {
                        System.err.println("❌ Lỗi load ảnh chi tiết: " + e.getMessage());
                    }
                }
            }

            // Kiểm tra hiển thị nút cho người thắng
            checkAndToggleWinnerActions(currentAuction, currentUser);

            // Nếu phiên đang chạy, theo dõi realtime để ẩn/hiện nút sau khi kết thúc
            if (currentAuction.getEndTime() != null
                    && "RUNNING".equalsIgnoreCase(currentAuction.getAuctionStatus())) {
                startRealtimeStatusTracker(currentAuction.getEndTime(), currentAuction, currentUser);
            }
        });
    }

    private void handleAuctionDetailFailed(Message message) {
        Platform.runLater(() -> {
            String reason = message.getMessage() != null ? message.getMessage() : "Không tìm thấy phiên.";
            if (lblStatus != null) {
                lblStatus.setText("Lỗi: " + reason);
                lblStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            }
        });
    }

    private void handlePaySuccess(Message message) {
        Platform.runLater(() -> {
            showNotification("Thành công", "Xác nhận nhận hàng thành công! Tiền đã được giải ngân.");
            if (currentAuction != null) {
                currentAuction.setAuctionStatus("PAID");
                checkAndToggleWinnerActions(currentAuction, currentUser);
            }
        });
    }

    private void handleRejectWinSuccess(Message message) {
        Platform.runLater(() -> {
            showNotification("Hủy thành công",
                    "Bạn đã hủy nhận hàng. Tiền cọc bị phạt 7%, phần còn lại đã hoàn vào ví.");
            if (currentAuction != null) {
                currentAuction.setAuctionStatus("REJECTED");
                checkAndToggleWinnerActions(currentAuction, currentUser);
            }
        });
    }

    private void handleRejectWinFailed(Message message) {
        Platform.runLater(() -> {
            String reason = message.getMessage() != null ? message.getMessage() : "Lỗi không xác định.";
            showNotification("Thất bại", "Không thể hủy kèo: " + reason);
        });
    }

    // ─── WINNER ACTION LOGIC ─────────────────────────────────────────────────

    private void checkAndToggleWinnerActions(Auction auction, User user) {
        String status = auction.getAuctionStatus();

        // Đã hoàn tất hoặc đã hủy — ẩn nút
        if ("PAID".equalsIgnoreCase(status) || "SOLD".equalsIgnoreCase(status)
                || "REJECTED".equalsIgnoreCase(status)) {
            updateStatusStyle(status);
            setWinnerActionsVisible(false);
            return;
        }

        if ("FINISHED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            boolean isWinner = auction.getCurrentWinnerId() != null
                    && user != null
                    && auction.getCurrentWinnerId().intValue() == user.getId();

            if (isWinner) {
                LocalDateTime endTime = auction.getEndTime() != null ? auction.getEndTime() : LocalDateTime.now();
                java.time.Duration passed = java.time.Duration.between(endTime, LocalDateTime.now());
                long hoursPassed = passed.toHours();

                int assignedAdminId = ESCROW_ADMIN_IDS[auction.getAuctionId() % ESCROW_ADMIN_IDS.length];

                if (hoursPassed < 24) {
                    long totalMinLeft = (24 * 60) - passed.toMinutes();
                    long hoursLeft = totalMinLeft / 60;
                    long minsLeft = totalMinLeft % 60;

                    if (lblStatus != null) {
                        lblStatus.setText(String.format(
                                "🎉 BẠN THẮNG! Tiền cọc tạm giữ tại Admin #%d — Còn %dh %dm để xác nhận",
                                assignedAdminId, hoursLeft, minsLeft));
                        lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706; "
                                + "-fx-background-radius: 5; -fx-padding: 6 12; -fx-font-weight: bold; "
                                + "-fx-border-color: #f59e0b; -fx-border-width: 1; -fx-border-radius: 5;");
                    }
                    setWinnerActionsVisible(true);
                } else {
                    if (lblStatus != null) {
                        lblStatus.setText("❌ QUÁ HẠN 24H — HỆ THỐNG TỰ ĐỘNG PHONG TỎA VÍ CỌC");
                        lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; "
                                + "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                    }
                    setWinnerActionsVisible(false);
                }
            } else {
                if (lblStatus != null) {
                    lblStatus.setText("ĐÃ KẾT THÚC — Bạn không thắng phiên này");
                    lblStatus.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563; "
                            + "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                }
                setWinnerActionsVisible(false);
            }
        } else {
            updateStatusStyle(status);
        }
    }

    private void setWinnerActionsVisible(boolean visible) {
        if (hboxWinnerActions != null) {
            hboxWinnerActions.setVisible(visible);
            hboxWinnerActions.setManaged(visible);
        }
    }

    private void updateStatusStyle(String status) {
        if (lblStatus == null) return;
        if ("FINISHED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ KẾT THÚC");
            lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else if ("PENDING".equalsIgnoreCase(status) || "WAITING_FOR_ADMIN".equalsIgnoreCase(status)) {
            lblStatus.setText("CHỜ KÍCH HOẠT");
            lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else if ("SOLD".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ THANH TOÁN THÀNH CÔNG ✅");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ HỦY NHẬN HÀNG (PHẠT CỌC 7%)");
            lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else {
            lblStatus.setText("ĐANG DIỄN RA 🔴");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        }
    }

    private void startRealtimeStatusTracker(LocalDateTime endTime, Auction auction, User user) {
        if (liveStatusTimeline != null) liveStatusTimeline.stop();
        liveStatusTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (LocalDateTime.now().isAfter(endTime)) {
                auction.setAuctionStatus("FINISHED");
                Platform.runLater(() -> checkAndToggleWinnerActions(auction, user));
                liveStatusTimeline.stop();
            }
        }));
        liveStatusTimeline.setCycleCount(Animation.INDEFINITE);
        liveStatusTimeline.play();
    }

    // ─── FXML ACTIONS ────────────────────────────────────────────────────────

    /**
     * Xác nhận nhận hàng — giải ngân tiền cọc qua Server.
     */
    @FXML
    void handlePayAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double price = currentAuction.getCurrentPrice();
        double adminFee = price * 0.15;
        double sellerReceived = price * 0.85;
        int assignedAdminId = ESCROW_ADMIN_IDS[currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length];

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Xác nhận giải ngân?\n- Phí sàn (15%%): %,.0f UETệ\n- Người bán nhận (85%%): %,.0f UETệ\n(Ví tạm Admin #%d)",
                        adminFee, sellerReceived, assignedAdminId),
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận nhận hàng");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
                // payload: Object[] {auctionId, winnerId, finalPrice}
                Object[] payload = new Object[]{
                        currentAuction.getAuctionId(),
                        currentUser.getId(),
                        currentAuction.getCurrentPrice()
                };
                SocketClient.getInstance().sendRequest(RequestCode.ADMIN_CREATE_TRANSACTION, payload);
            }
        });
    }

    /**
     * Hủy nhận hàng — phạt cọc 7%, hoàn 93% vào ví.
     * ✅ SỬA: Dùng RequestCode.REJECT_WIN thay vì ADMIN_REJECT_TRANSACTION sai format.
     */
    @FXML
    void handleCancelAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double price = currentAuction.getCurrentPrice();
        double penalty = price * 0.07;
        double refund = price * 0.93;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Hủy nhận hàng sẽ:\n- Phạt cọc 7%%: %,.0f UETệ\n- Hoàn lại ví bạn 93%%: %,.0f UETệ\nBạn chắc chắn muốn hủy?",
                        penalty, refund),
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận hủy nhận hàng");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
                // ✅ SỬA: Chỉ gửi auctionId, server tự lấy userId từ session
                SocketClient.getInstance().sendRequest(RequestCode.REJECT_WIN, currentAuction.getAuctionId());
            }
        });
    }

    @FXML
    void handleClose(ActionEvent event) {
        cleanupListeners();
        if (liveStatusTimeline != null) liveStatusTimeline.stop();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private void cleanupListeners() {
        MessageRouter.getInstance().unregister(ResponseCode.AUCTION_DETAIL_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.AUCTION_DETAIL_FAILED);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_CREATED);
        MessageRouter.getInstance().unregister(ResponseCode.REJECT_WIN_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.REJECT_WIN_FAILED);
    }

    private void showNotification(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}