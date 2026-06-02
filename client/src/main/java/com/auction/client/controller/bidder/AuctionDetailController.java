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
 * AuctionDetailController — ĐÃ HOÀN THIỆN TOÀN DIỆN:
 *
 * 1. Lắng nghe WINNER_NOTIFICATION realtime:
 *    Nếu cửa sổ đang mở và server push WINNER_NOTIFICATION cho phiên này
 *    → tự động hiện 2 nút Chấp nhận / Hủy mà không cần đóng mở lại.
 *
 * 2. Lắng nghe AUCTION_ENDED realtime:
 *    Nếu user đang mở chi tiết phiên đang chạy và phiên kết thúc → cập nhật UI ngay.
 *
 * 3. Trạng thái "ĐANG CHỜ XÁC NHẬN MUA":
 *    Sau khi phiên kết thúc và user là winner → lblStatus hiển thị
 *    "🏆 WINNER — Chờ bạn xác nhận mua hàng" với màu vàng.
 *
 * 4. Khi nhấn "Chấp nhận mua":
 *    - 15% phí sàn → admin, 85% → seller.
 *    - Trạng thái → "ĐÃ XÁC NHẬN MUA — Chờ giao hàng".
 *    - Ẩn 2 nút.
 *
 * 5. Khi nhấn "Hủy không mua":
 *    - 7% vào ví admin (phạt cọc), 93% hoàn lại người mua.
 *    - Trạng thái → "ĐÃ HỦY (phạt cọc 7%)".
 *    - Ẩn 2 nút.
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
     * Gọi từ BiddingHistoryController.handleViewDetail().
     * Gửi GET_AUCTION_DETAIL lên server để lấy dữ liệu đầy đủ.
     */
    public void loadAuctionDetail(int auctionId, String fallbackName, User user) {
        this.currentUser = user;

        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName  != null) lblItemName.setText(fallbackName != null ? fallbackName : "Đang tải...");
        if (lblStatus    != null) lblStatus.setText("Đang tải dữ liệu...");

        setWinnerActionsVisible(false);

        cleanupListeners();

        MessageRouter router = MessageRouter.getInstance();
        router.register(ResponseCode.AUCTION_DETAIL_RESULT,  this::handleAuctionDetailResult);
        router.register(ResponseCode.AUCTION_DETAIL_FAILED,  this::handleAuctionDetailFailed);
        router.register(ResponseCode.ADMIN_TRANSACTION_CREATED, this::handlePaySuccess);
        router.register(ResponseCode.REJECT_WIN_SUCCESS,     this::handleRejectWinSuccess);
        router.register(ResponseCode.REJECT_WIN_FAILED,      this::handleRejectWinFailed);
        // ✅ MỚI: Lắng nghe push cá nhân winner & broadcast kết thúc phiên
        router.register(ResponseCode.WINNER_NOTIFICATION,    this::handleWinnerPushNotification);
        router.register(ResponseCode.AUCTION_ENDED,          this::handleAuctionEndedRealtime);

        SocketClient.getInstance().sendRequest(RequestCode.GET_AUCTION_DETAIL, auctionId);
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────────

    private void handleAuctionDetailResult(Message message) {
        if (!(message.getPayload() instanceof Auction)) {
            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.setText("Không thể tải dữ liệu phiên.");
            });
            return;
        }

        this.currentAuction = (Auction) message.getPayload();
        Item item = currentAuction.getItem();

        Platform.runLater(() -> {
            if (lblAuctionId    != null) lblAuctionId.setText("#" + currentAuction.getAuctionId());
            if (lblCurrentPrice != null)
                lblCurrentPrice.setText(String.format("%,.0f UETệ", currentAuction.getCurrentPrice()));
            if (lblStartTime != null && currentAuction.getStartTime() != null)
                lblStartTime.setText(currentAuction.getStartTime().toString().replace("T", " "));
            if (lblEndTime != null && currentAuction.getEndTime() != null)
                lblEndTime.setText(currentAuction.getEndTime().toString().replace("T", " "));

            if (item != null) {
                if (lblItemName   != null) lblItemName.setText(item.getName());
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

            checkAndToggleWinnerActions(currentAuction, currentUser);

            // Theo dõi realtime nếu phiên đang chạy
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
            showNotification("Thành công",
                    "Xác nhận mua hàng thành công!\n" +
                            "15% phí sàn đã chuyển admin, 85% chuyển cho người bán.");
            if (currentAuction != null) {
                currentAuction.setAuctionStatus("PAID");
                if (lblStatus != null) {
                    lblStatus.setText("✅ ĐÃ XÁC NHẬN MUA — Chờ giao hàng");
                    lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; " +
                            "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                }
                setWinnerActionsVisible(false);
            }
        });
    }

    private void handleRejectWinSuccess(Message message) {
        Platform.runLater(() -> {
            showNotification("Hủy thành công",
                    "Bạn đã hủy không mua.\n" +
                            "7% phí phạt chuyển admin, 93% đã hoàn vào ví của bạn.");
            if (currentAuction != null) {
                currentAuction.setAuctionStatus("REJECTED");
                if (lblStatus != null) {
                    lblStatus.setText("❌ ĐÃ HỦY KHÔNG MUA (phạt cọc 7%)");
                    lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; " +
                            "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                }
                setWinnerActionsVisible(false);
            }
        });
    }

    private void handleRejectWinFailed(Message message) {
        Platform.runLater(() -> {
            String reason = message.getMessage() != null ? message.getMessage() : "Lỗi không xác định.";
            showNotification("Thất bại", "Không thể hủy: " + reason);
        });
    }

    /**
     * ✅ MỚI: Nhận push WINNER_NOTIFICATION từ server khi phiên kết thúc.
     * Payload: Object[] {auctionId, finalPrice, itemName}
     * Nếu auctionId trùng → cập nhật UI hiện 2 nút và trạng thái "chờ xác nhận".
     */
    private void handleWinnerPushNotification(Message message) {
        Platform.runLater(() -> {
            try {
                Object[] payload = (Object[]) message.getPayload();
                int    auctionId  = (Integer) payload[0];
                double finalPrice = (Double)  payload[1];

                if (currentAuction == null || currentAuction.getAuctionId() != auctionId) return;

                // Cập nhật trạng thái phiên trong RAM
                currentAuction.setAuctionStatus("FINISHED");
                currentAuction.setCurrentPrice(finalPrice);
                if (currentUser != null) {
                    currentAuction.setCurrentWinnerId(currentUser.getId());
                }

                // Hiển thị trạng thái và 2 nút
                showWinnerPendingStatus(finalPrice, currentAuction.getAuctionId());
                setWinnerActionsVisible(true);

            } catch (Exception e) {
                System.err.println("[DETAIL] Lỗi handleWinnerPushNotification: " + e.getMessage());
            }
        });
    }

    /**
     * ✅ MỚI: Lắng nghe broadcast AUCTION_ENDED khi cửa sổ chi tiết đang mở.
     * Payload: Object[] {auctionId, winnerUsername, finalPrice}
     */
    private void handleAuctionEndedRealtime(Message message) {
        Platform.runLater(() -> {
            try {
                Object payload = message.getPayload();
                if (!(payload instanceof Object[])) return;
                Object[] arr = (Object[]) payload;

                if (!(arr[0] instanceof Integer)) return;
                int    auctionId      = (Integer) arr[0];
                String winnerUsername = arr.length > 1 && arr[1] instanceof String ? (String) arr[1] : null;
                double finalPrice     = arr.length > 2 && arr[2] instanceof Number ? ((Number) arr[2]).doubleValue() : 0;

                if (currentAuction == null || currentAuction.getAuctionId() != auctionId) return;

                currentAuction.setAuctionStatus("FINISHED");
                currentAuction.setCurrentPrice(finalPrice);

                boolean isWinner = winnerUsername != null && currentUser != null
                        && winnerUsername.equals(currentUser.getUsername());

                if (isWinner) {
                    if (currentUser != null) currentAuction.setCurrentWinnerId(currentUser.getId());
                    showWinnerPendingStatus(finalPrice, auctionId);
                    setWinnerActionsVisible(true);
                } else {
                    if (lblStatus != null) {
                        lblStatus.setText("ĐÃ KẾT THÚC — Bạn không thắng phiên này");
                        lblStatus.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563; " +
                                "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                    }
                    setWinnerActionsVisible(false);
                }

                if (liveStatusTimeline != null) liveStatusTimeline.stop();

            } catch (Exception e) {
                System.err.println("[DETAIL] Lỗi handleAuctionEndedRealtime: " + e.getMessage());
            }
        });
    }

    // ─── WINNER ACTION LOGIC ─────────────────────────────────────────────────

    private void checkAndToggleWinnerActions(Auction auction, User user) {
        String status = auction.getAuctionStatus();

        if ("PAID".equalsIgnoreCase(status) || "SOLD".equalsIgnoreCase(status)) {
            if (lblStatus != null) {
                lblStatus.setText("✅ ĐÃ XÁC NHẬN MUA — Chờ giao hàng");
                lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; " +
                        "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
            }
            setWinnerActionsVisible(false);
            return;
        }

        if ("REJECTED".equalsIgnoreCase(status)) {
            if (lblStatus != null) {
                lblStatus.setText("❌ ĐÃ HỦY KHÔNG MUA (phạt cọc 7%)");
                lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; " +
                        "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
            }
            setWinnerActionsVisible(false);
            return;
        }

        if ("FINISHED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            boolean isWinner = auction.getCurrentWinnerId() != null
                    && user != null
                    && auction.getCurrentWinnerId().intValue() == user.getId();

            if (isWinner) {
                java.time.Duration passed = java.time.Duration.between(
                        auction.getEndTime() != null ? auction.getEndTime() : LocalDateTime.now(),
                        LocalDateTime.now());
                long hoursPassed = passed.toHours();

                if (hoursPassed < 24) {
                    showWinnerPendingStatus(auction.getCurrentPrice(), auction.getAuctionId());
                    setWinnerActionsVisible(true);
                } else {
                    if (lblStatus != null) {
                        lblStatus.setText("❌ QUÁ HẠN 24H — Hệ thống tự động phong tỏa ví cọc");
                        lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; " +
                                "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                    }
                    setWinnerActionsVisible(false);
                }
            } else {
                if (lblStatus != null) {
                    lblStatus.setText("ĐÃ KẾT THÚC — Bạn không thắng phiên này");
                    lblStatus.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563; " +
                            "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                }
                setWinnerActionsVisible(false);
            }
        } else {
            // Phiên đang chạy hoặc các trạng thái khác
            updateStatusStyle(status);
            setWinnerActionsVisible(false);
        }
    }

    /**
     * Hiển thị trạng thái "WINNER — Chờ xác nhận mua hàng" với đếm ngược 24h.
     */
    private void showWinnerPendingStatus(double finalPrice, int auctionId) {
        if (lblStatus == null) return;

        int assignedAdminId = ESCROW_ADMIN_IDS[auctionId % ESCROW_ADMIN_IDS.length];
        LocalDateTime endTime = (currentAuction != null && currentAuction.getEndTime() != null)
                ? currentAuction.getEndTime() : LocalDateTime.now();
        java.time.Duration passed = java.time.Duration.between(endTime, LocalDateTime.now());
        long totalMinLeft = (24 * 60) - passed.toMinutes();
        if (totalMinLeft < 0) totalMinLeft = 0;
        long hoursLeft = totalMinLeft / 60;
        long minsLeft  = totalMinLeft % 60;

        lblStatus.setText(String.format(
                "🏆 WINNER — Chờ xác nhận mua hàng\n" +
                        "Tiền cọc tạm giữ tại Admin #%d | Còn %dh %dm để quyết định",
                assignedAdminId, hoursLeft, minsLeft));
        lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706; " +
                "-fx-background-radius: 5; -fx-padding: 6 12; -fx-font-weight: bold; " +
                "-fx-border-color: #f59e0b; -fx-border-width: 1; -fx-border-radius: 5;");
    }

    private void setWinnerActionsVisible(boolean visible) {
        if (hboxWinnerActions != null) {
            hboxWinnerActions.setVisible(visible);
            hboxWinnerActions.setManaged(visible);
        }
    }

    private void updateStatusStyle(String status) {
        if (lblStatus == null) return;
        if ("PENDING".equalsIgnoreCase(status) || "WAITING_FOR_ADMIN".equalsIgnoreCase(status)) {
            lblStatus.setText("CHỜ KÍCH HOẠT");
            lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; " +
                    "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else if ("RUNNING".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐANG DIỄN RA 🔴");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; " +
                    "-fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else {
            lblStatus.setText(status != null ? status : "");
            lblStatus.setStyle("");
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
     * Chấp nhận mua: 15% phí sàn → admin, 85% → seller.
     */
    @FXML
    void handlePayAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double price         = currentAuction.getCurrentPrice();
        double adminFee      = price * 0.15;
        double sellerReceived = price * 0.85;
        int assignedAdminId  = ESCROW_ADMIN_IDS[currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length];

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Xác nhận MUA HÀNG?\n\n" +
                                "💰 Giá thắng: %,.0f UETệ\n" +
                                "📊 Phí sàn 15%%: %,.0f UETệ → Admin #%d\n" +
                                "💵 Người bán nhận 85%%: %,.0f UETệ\n\n" +
                                "Bạn chắc chắn muốn xác nhận mua?",
                        price, adminFee, assignedAdminId, sellerReceived),
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận mua hàng");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
                Object[] payload = {
                        currentAuction.getAuctionId(),
                        currentUser.getId(),
                        currentAuction.getCurrentPrice()
                };
                SocketClient.getInstance().sendRequest(RequestCode.ADMIN_CREATE_TRANSACTION, payload);
            }
        });
    }

    /**
     * Hủy không mua: phạt cọc 7% → admin, hoàn 93% → ví người mua.
     */
    @FXML
    void handleCancelAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double price   = currentAuction.getCurrentPrice();
        double penalty = price * 0.07;
        double refund  = price * 0.93;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Xác nhận HỦY KHÔNG MUA?\n\n" +
                                "💰 Giá thắng: %,.0f UETệ\n" +
                                "❌ Phạt cọc 7%%: %,.0f UETệ → Admin\n" +
                                "↩ Hoàn lại ví bạn 93%%: %,.0f UETệ\n\n" +
                                "Bạn chắc chắn muốn hủy?",
                        price, penalty, refund),
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận hủy không mua");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
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
        MessageRouter router = MessageRouter.getInstance();
        router.unregister(ResponseCode.AUCTION_DETAIL_RESULT);
        router.unregister(ResponseCode.AUCTION_DETAIL_FAILED);
        router.unregister(ResponseCode.ADMIN_TRANSACTION_CREATED);
        router.unregister(ResponseCode.REJECT_WIN_SUCCESS);
        router.unregister(ResponseCode.REJECT_WIN_FAILED);
        router.unregister(ResponseCode.WINNER_NOTIFICATION);
        router.unregister(ResponseCode.AUCTION_ENDED);
    }

    private void showNotification(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}