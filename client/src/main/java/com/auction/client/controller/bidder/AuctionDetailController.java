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

public class AuctionDetailController {

    // 🎯 ĐỒNG BỘ: Mảng 4 ID Admin hệ thống giải ngân tiền cọc
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
        System.out.println("🔌 Khởi tạo cổng mạng cho màn hình Chi tiết đấu giá.");
    }

    /**
     * Nạp dữ liệu ban đầu và đăng ký các cổng lắng nghe từ Server
     */
    public void loadAuctionDetail(int auctionId, String fallbackName, User user) {
        this.currentUser = user;

        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName != null) lblItemName.setText(fallbackName);

        if (hboxWinnerActions != null) {
            hboxWinnerActions.setVisible(false);
            hboxWinnerActions.setManaged(false);
        }

        // ĐĂNG KÝ AN TOÀN: Làm sạch Listener cũ tránh lặp luồng dữ liệu
        cleanupListeners();
        MessageRouter.getInstance().register(ResponseCode.BID_HISTORY_RESULT, this::handleAuctionDetailResult);
        MessageRouter.getInstance().register(ResponseCode.WITHDRAW_SUCCESS, this::handleTransactionSuccess);
        MessageRouter.getInstance().register(ResponseCode.WITHDRAW_FAILED, this::handleTransactionFailed);

        // Gửi yêu cầu lên Server lấy thông tin chi tiết phiên
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, auctionId);
    }

    /**
     * Xử lý dữ liệu phiên đấu giá từ Server đổ về qua Socket
     */
    private void handleAuctionDetailResult(Message message) {
        Object payload = message.getPayload();
        if (!(payload instanceof Auction)) return;

        this.currentAuction = (Auction) payload;
        Item item = currentAuction.getItem();

        Platform.runLater(() -> {
            if (lblCurrentPrice != null) {
                lblCurrentPrice.setText(String.format("%,.0f UETệ", currentAuction.getCurrentPrice()));
            }

            if (lblStartTime != null && currentAuction.getStartTime() != null) {
                lblStartTime.setText(currentAuction.getStartTime().toString());
            }
            if (lblEndTime != null && currentAuction.getEndTime() != null) {
                lblEndTime.setText(currentAuction.getEndTime().toString());
            }

            // Kiểm tra hiển thị nút bấm cho người thắng cuộc
            checkAndToggleWinnerActions(currentAuction, currentUser);

            // Hiển thị thông tin vật phẩm đính kèm
            if (item != null) {
                if (lblItemName != null) lblItemName.setText(item.getName());
                if (lblDescription != null) lblDescription.setText(item.getDescription());

                if (item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                    try {
                        String path = item.getImgItem().trim();
                        if (!path.startsWith("/")) path = "/" + path;
                        InputStream is = getClass().getResourceAsStream(path);
                        if (is != null) imgProduct.setImage(new Image(is));
                    } catch (Exception e) {
                        System.err.println("❌ Lỗi hiển thị ảnh chi tiết vật phẩm: " + e.getMessage());
                    }
                }
            }

            // Chạy bộ đếm ngược thời gian thực nếu phiên đang diễn ra
            if (currentAuction.getEndTime() != null && "RUNNING".equalsIgnoreCase(currentAuction.getAuctionStatus())) {
                startRealtimeStatusTracker(currentAuction.getEndTime(), currentAuction, currentUser);
            }
        });
    }

    /**
     * Logic kiểm tra xem User hiện tại có phải người thắng phiên để hiện 2 nút Thanh Toán / Hủy Đấu Giá hay không
     */
    private void checkAndToggleWinnerActions(Auction auction, User user) {
        String status = auction.getAuctionStatus();

        if ("PAID".equalsIgnoreCase(status) || "SOLD".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            updateStatusStyle(status);
            if (hboxWinnerActions != null) {
                hboxWinnerActions.setVisible(false);
                hboxWinnerActions.setManaged(false);
            }
            return;
        }

        if ("FINISHED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            if (auction.getCurrentWinnerId() != null && user != null && auction.getCurrentWinnerId().intValue() == user.getId()) {
                LocalDateTime endTime = auction.getEndTime();
                LocalDateTime now = LocalDateTime.now();
                if (endTime == null) endTime = now;

                java.time.Duration durationPassed = java.time.Duration.between(endTime, now);
                long hoursPassed = durationPassed.toHours();

                if (hoursPassed < 24) {
                    long totalMinutesLeft = (24 * 60) - durationPassed.toMinutes();
                    long hoursLeft = totalMinutesLeft / 60;
                    long minutesLeft = totalMinutesLeft % 60;

                    // Xác định Admin đang nắm giữ ví tạm dựa trên thuật toán lấy số dư của id phiên
                    int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
                    int assignedAdminId = ESCROW_ADMIN_IDS[idx];

                    if (lblStatus != null) {
                        lblStatus.setText("🎉 BẠN ĐÃ THẮNG PHIÊN (Tiền cọc đóng băng tại Admin #" + assignedAdminId + " - Còn " + hoursLeft + "g " + minutesLeft + "p để xác nhận)");
                        lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-background-radius: 5; -fx-padding: 6 12; -fx-font-weight: bold; -fx-border-color: #f59e0b; -fx-border-width: 1; -fx-border-radius: 5;");
                    }
                    if (hboxWinnerActions != null) {
                        hboxWinnerActions.setVisible(true);
                        hboxWinnerActions.setManaged(true);
                    }
                } else {
                    if (lblStatus != null) {
                        lblStatus.setText("❌ QUÁ HẠN 24H (HỆ THỐNG TỰ ĐỘNG KHÓA VÀ PHẠT CỌC)");
                        lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                    }
                    if (hboxWinnerActions != null) {
                        hboxWinnerActions.setVisible(false);
                        hboxWinnerActions.setManaged(false);
                    }
                }
            } else {
                if (lblStatus != null) {
                    lblStatus.setText("ĐÃ KẾT THÚC (Bạn không thắng phiên này)");
                    lblStatus.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
                }
                if (hboxWinnerActions != null) {
                    hboxWinnerActions.setVisible(false);
                    hboxWinnerActions.setManaged(false);
                }
            }
        } else {
            updateStatusStyle(status);
        }
    }

    /**
     * Cập nhật màu sắc CSS cho nhãn trạng thái
     */
    private void updateStatusStyle(String status) {
        if (lblStatus == null) return;

        if ("FINISHED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ KẾT THÚC");
            lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else if ("PENDING".equalsIgnoreCase(status) || "WAITING_FOR_ADMIN".equalsIgnoreCase(status)) {
            lblStatus.setText("CHỜ KÍCH HOẠT");
            lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else if ("SOLD".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ THANH TOÁN THÀNH CÔNG");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ HỦY ĐẤU GIÁ (PHẠT CỌC 7%)");
            lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        } else {
            lblStatus.setText("ĐANG DIỄN RA");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12; -fx-font-weight: bold;");
        }
    }

    /**
     * Bộ theo dõi đếm ngược thời gian thực kết thúc phiên
     */
    private void startRealtimeStatusTracker(LocalDateTime endTime, Auction auction, User user) {
        if (liveStatusTimeline != null) { liveStatusTimeline.stop(); }
        liveStatusTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(endTime)) {
                auction.setAuctionStatus("FINISHED");
                checkAndToggleWinnerActions(auction, user);
                liveStatusTimeline.stop();
            }
        }));
        liveStatusTimeline.setCycleCount(Animation.INDEFINITE);
        liveStatusTimeline.play();
    }

    /**
     * 🟢 NÚT XÁC NHẬN NHẬN HÀNG VÀ THANH TOÁN (Gửi lệnh qua Socket)
     */
    @FXML
    void handlePayAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double currentBidPrice = currentAuction.getCurrentPrice();
        double adminFee = currentBidPrice * 0.15;
        double sellerReceived = currentBidPrice * 0.85;

        int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
        int assignedAdminId = ESCROW_ADMIN_IDS[idx];

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Xác nhận giải ngân vật phẩm? (Ví tạm Admin #%d đang giữ tiền)\n- Phí sàn (15%%): %,.0f UETệ\n- Người bán nhận (85%%): %,.0f UETệ", assignedAdminId, adminFee, sellerReceived),
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận giải ngân");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // Đóng gói mảng dữ liệu gửi lên Server xử lý dòng tiền qua Database tập trung
                Object[] txPayload = new Object[] { currentAuction.getAuctionId(), currentUser.getId(), assignedAdminId };
                SocketClient.getInstance().sendRequest(RequestCode.ADMIN_CREATE_TRANSACTION, txPayload);
            }
        });
    }

    /**
     * 🔴 NÚT TỪ CHỐI NHẬN HÀNG - HỦY PHIÊN PHẠT CỌC 7% (Gửi lệnh qua Socket)
     */
    @FXML
    void handleCancelAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double currentBidPrice = currentAuction.getCurrentPrice();
        double penaltyFee = currentBidPrice * 0.07;
        double refundAmt = currentBidPrice * 0.93;

        int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
        int assignedAdminId = ESCROW_ADMIN_IDS[idx];

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Hủy mua sản phẩm sẽ giải phóng ví tạm của Admin #%d:\n- Bạn bị phạt 7%% cọc: %,.0f UETệ\n- Hoàn lại trả ví bạn 93%%: %,.0f UETệ\nBạn chắc chắn hủy chứ?", assignedAdminId, penaltyFee, refundAmt),
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận hủy nhận hàng");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // Gửi thông tin sang server gồm ID phiên và ID Admin giữ tiền để Server thực hiện Trực tiếp trên DB của nó
                Object[] rejectPayload = new Object[] { currentAuction.getAuctionId(), assignedAdminId };
                SocketClient.getInstance().sendRequest(RequestCode.ADMIN_REJECT_TRANSACTION, rejectPayload);
            }
        });
    }

    /**
     * Xử lý khi Server báo Giao dịch / Giải ngân / Phạt cọc THÀNH CÔNG qua Socket
     */
    private void handleTransactionSuccess(Message message) {
        Platform.runLater(() -> {
            showNotification("Thành công", "Hệ thống mạng đã thực thi thay đổi dòng tiền thành công!");
            if (lblStatus != null && currentAuction != null) {
                // Đồng bộ RAM lập tức
                currentAuction.setAuctionStatus("PAID");
                checkAndToggleWinnerActions(currentAuction, currentUser);
            }
        });
    }

    /**
     * Xử lý khi Server báo giao dịch THẤT BẠI
     */
    private void handleTransactionFailed(Message message) {
        Platform.runLater(() -> {
            showNotification("Thất bại", "Giao dịch lỗi hoặc số dư ví tạm Admin không đủ: " + message.getMessage());
        });
    }

    @FXML
    void handleClose(ActionEvent event) {
        cleanupListeners();
        if (liveStatusTimeline != null) { liveStatusTimeline.stop(); }
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    /**
     * Giải phóng cổng lắng nghe khi đóng cửa sổ để tránh rò rỉ bộ nhớ mạng (Memory Leak)
     */
    private void cleanupListeners() {
        MessageRouter.getInstance().unregister(ResponseCode.BID_HISTORY_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.WITHDRAW_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.WITHDRAW_FAILED);
    }

    private void showNotification(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}