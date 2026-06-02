package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.network.AuctionItemDTO;
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
        // ĐĂNG KÝ LẮNG NGHE KẾT QUẢ TỪ SERVER QUA SOCKET
        MessageRouter.getInstance().register(ResponseCode.AUCTION_DETAIL_RESULT, this::handleDetailResult);
        MessageRouter.getInstance().register(ResponseCode.BIDDER_PAY_SUCCESS, this::handlePaymentSuccess);
        MessageRouter.getInstance().register(ResponseCode.BIDDER_PAY_FAILED, this::handlePaymentFailed);
        MessageRouter.getInstance().register(ResponseCode.BIDDER_CANCEL_SUCCESS, this::handleCancelSuccess);
        MessageRouter.getInstance().register(ResponseCode.BIDDER_CANCEL_FAILED, this::handleCancelFailed);
    }

    public void loadAuctionDetail(int auctionId, String fallbackName, User user) {
        this.currentUser = user;

        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName != null) lblItemName.setText(fallbackName);

        if (hboxWinnerActions != null) {
            hboxWinnerActions.setVisible(false);
            hboxWinnerActions.setManaged(false);
        }

        // 🌟 GỬI REQUEST YÊU CẦU SERVER LẤY CHI TIẾT PHIÊN ĐẤU GIÁ
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_AUCTION_DETAIL, auctionId);
    }

    private void handleDetailResult(Message msg) {
        if (msg.getPayload() instanceof AuctionItemDTO) {
            AuctionItemDTO dto = (AuctionItemDTO) msg.getPayload();
            this.currentAuction = dto.getAuction();
            Item item = dto.getItem();

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

                checkAndToggleWinnerActions(currentAuction, currentUser);

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

                if (currentAuction.getEndTime() != null && "RUNNING".equalsIgnoreCase(currentAuction.getAuctionStatus())) {
                    startRealtimeStatusTracker(currentAuction.getEndTime(), currentAuction, currentUser);
                }
            });
        }
    }

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
            if (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId().intValue() == user.getId()) {
                LocalDateTime endTime = auction.getEndTime();
                LocalDateTime now = LocalDateTime.now();
                if (endTime == null) endTime = now;

                java.time.Duration durationPassed = java.time.Duration.between(endTime, now);
                long hoursPassed = durationPassed.toHours();

                if (hoursPassed < 24) {
                    long totalMinutesLeft = (24 * 60) - durationPassed.toMinutes();
                    long hoursLeft = totalMinutesLeft / 60;
                    long minutesLeft = totalMinutesLeft % 60;

                    int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
                    int assignedAdminId = ESCROW_ADMIN_IDS[idx];

                    if (lblStatus != null) {
                        lblStatus.setText("🎉 BẠN ĐÃ THẮNG PHIÊN (Tiền tạm giữ tại Admin #" + assignedAdminId + " - Còn " + hoursLeft + "g " + minutesLeft + "p để xác nhận)");
                        lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-background-radius: 5; -fx-padding: 6 12 6 12; -fx-font-weight: bold; -fx-border-color: #f59e0b; -fx-border-width: 1; -fx-border-radius: 5;");
                    }
                    if (hboxWinnerActions != null) {
                        hboxWinnerActions.setVisible(true);
                        hboxWinnerActions.setManaged(true);
                    }
                } else {
                    if (lblStatus != null) {
                        lblStatus.setText("❌ QUÁ HẠN 24H (ĐANG XỬ LÝ PHẠT CỌC)");
                        lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
                    }
                    if (hboxWinnerActions != null) {
                        hboxWinnerActions.setVisible(false);
                        hboxWinnerActions.setManaged(false);
                    }
                    autoRejectWinOverdue(); // Nhường việc xử phạt cho Server
                }
            } else {
                if (lblStatus != null) {
                    lblStatus.setText("ĐÃ KẾT THÚC (Bạn không thắng phiên này)");
                    lblStatus.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
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

    private void autoRejectWinOverdue() {
        // Việc phạt cọc do quá hạn 24h bây giờ sẽ được Server tự động quét bằng 1 luồng ngầm (ScheduledTask).
        // Client không can thiệp vào Database nữa.
        System.out.println("Phiên đã quá hạn 24h. Đang đợi Server quét tự động...");
    }

    private void updateStatusStyle(String status) {
        if (lblStatus == null) return;

        if ("FINISHED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ KẾT THÚC");
            lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        } else if ("PENDING".equalsIgnoreCase(status) || "WAITING_FOR_ADMIN".equalsIgnoreCase(status)) {
            lblStatus.setText("CHỜ KÍCH HOẠT");
            lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        } else if ("SOLD".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ THANH TOÁN THÀNH CÔNG");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ HỦY ĐẤU GIÁ (PHẠT CỌC 7%)");
            lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        } else {
            lblStatus.setText("ĐANG DIỄN RA");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        }
    }

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

    // =========================================================================
    // XỬ LÝ NÚT BẤM - ĐẨY GÁNH NẶNG LÊN CHO SERVER
    // =========================================================================

    @FXML
    void handlePayAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double currentBidPrice = currentAuction.getCurrentPrice();
        double adminFee = currentBidPrice * 0.15;
        double sellerReceived = currentBidPrice * 0.85;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            String.format("Xác nhận giải ngân vật phẩm này?\n- Hệ thống thu phí sàn 15%%: %,.0f UETệ\n- Người bán nhận lại 85%%: %,.0f UETệ", adminFee, sellerReceived),
            ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận giải ngân");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // GỬI REQUEST LÊN SERVER ĐỂ XỬ LÝ THANH TOÁN
                SocketClient.getInstance().sendRequest(RequestCode.BIDDER_PAY_AUCTION, currentAuction.getAuctionId());
            }
        });
    }

    private void handlePaymentSuccess(Message msg) {
        Platform.runLater(() -> {
            if (currentAuction != null) currentAuction.setAuctionStatus("PAID");
            checkAndToggleWinnerActions(currentAuction, currentUser);
            showNotification("Thành công", "Giao dịch giải ngân thành công! Vật phẩm đã chính thức thuộc về bạn.");
        });
    }

    private void handlePaymentFailed(Message msg) {
        Platform.runLater(() -> showNotification("Thất bại", msg.getMessage() != null ? msg.getMessage() : "Giao dịch thất bại."));
    }

    @FXML
    void handleCancelAuction(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        double currentBidPrice = currentAuction.getCurrentPrice();
        double penaltyFee = currentBidPrice * 0.07;
        double refundAmt = currentBidPrice * 0.93;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            String.format("Hủy mua bạn sẽ bị phạt 7%% cọc (tương đương %,.0f UETệ).\nSố tiền còn lại 93%% (%,.0f UETệ) sẽ hoàn về ví chính của bạn. Bạn chắc chắn chứ?", penaltyFee, refundAmt),
            ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận hủy nhận hàng");
        alert.setHeaderText(null);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // GỬI REQUEST HỦY PHIÊN LÊN SERVER
                SocketClient.getInstance().sendRequest(RequestCode.BIDDER_CANCEL_AUCTION, currentAuction.getAuctionId());
            }
        });
    }

    private void handleCancelSuccess(Message msg) {
        Platform.runLater(() -> {
            if (currentAuction != null) currentAuction.setAuctionStatus("REJECTED");
            checkAndToggleWinnerActions(currentAuction, currentUser);
            showNotification("Đã hủy phiên", "Hệ thống đã hủy phiên và áp lệnh phạt cọc 7% thành công.");
        });
    }

    private void handleCancelFailed(Message msg) {
        Platform.runLater(() -> showNotification("Thất bại", msg.getMessage() != null ? msg.getMessage() : "Lỗi khi xử lý hủy phạt cọc."));
    }

    @FXML
    void handleClose(ActionEvent event) {
        // DỌN RÁC: Hủy đăng ký lắng nghe để tránh tràn RAM khi mở đi mở lại cửa sổ nhiều lần
        MessageRouter.getInstance().unregister(ResponseCode.AUCTION_DETAIL_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.BIDDER_PAY_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.BIDDER_PAY_FAILED);
        MessageRouter.getInstance().unregister(ResponseCode.BIDDER_CANCEL_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.BIDDER_CANCEL_FAILED);

        if (liveStatusTimeline != null) { liveStatusTimeline.stop(); }
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    private void showNotification(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}