package com.auction.client.controller.bidder;

import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.*;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * AuctionRoomController — Phòng đấu giá realtime.
 *
 * ĐÃ FIX TOÀN DIỆN DÒNG TIỀN TẠM GIỮ (ESCROW):
 * 1. Tính toán động số tiền tạm giữ dựa trên việc User có đang dẫn đầu phiên (Winner) hay không.
 * 2. Tự động trả ví tạm giữ về 0 và lấy lại số dư ví chính mới nhất từ Server khi bị đè giá.
 * 3. Giữ nguyên toàn bộ logic hiển thị thuộc tính động, lịch sử bid và anti-sniping của em.
 */
public class AuctionRoomController {

    @FXML private Button btnBack;
    @FXML private Label lblProductName;
    @FXML private ImageView imgProduct;
    @FXML private VBox vboxProperties;
    @FXML private Label lblDescription;
    @FXML private Label lblCountdown;
    @FXML private Label lblUserBalance;
    @FXML private Label lblUserEscrow;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private VBox listHistoryContainer;

    private Auction currentAuction;
    private User currentUser;
    private Timeline countdownTimeline;

    @FXML
    public void initialize() {
        System.out.println("⚡ Đã khởi tạo Phòng đấu giá Real-time.");
        MessageRouter router = MessageRouter.getInstance();
        router.register(ResponseCode.ROOM_JOIN_SUCCESS, this::handleRoomJoinSuccess);
        router.register(ResponseCode.NEW_BID_UPDATE, this::handleNewBidUpdate);
        router.register(ResponseCode.BID_SUCCESS, this::handleBidSuccess);
        router.register(ResponseCode.AUCTION_TIME_EXTENDED, this::handleTimeExtended);
        router.register(ResponseCode.AUCTION_ENDED, this::handleAuctionEnded);
        router.register(ResponseCode.PROFILE_RESULT, this::handleProfileUpdate);
        router.register(ResponseCode.ROOM_JOIN_FAILED, this::handleRoomJoinFailed);
        router.register(ResponseCode.BID_FAILED, this::handleBidFailed);
    }

    /**
     * Được gọi từ ItemCardController.handleBidAction().
     */
    public void loadAuctionDetail(Auction auction, User user) {
        this.currentAuction = auction;
        User sessionUser = ClientSession.getInstance().getCurrentUser();
        this.currentUser = (sessionUser != null) ? sessionUser : user;

        if (this.currentUser == null) {
            showAlert("Lỗi phiên", "Bạn chưa đăng nhập! Vui lòng đăng nhập lại.", Alert.AlertType.ERROR);
            return;
        }

        SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, auction.getAuctionId());
    }

    private void handleRoomJoinSuccess(Message message) {
        Platform.runLater(() -> {
            System.out.println("🟢 Server báo tham gia phòng thành công!");
            if (message.getPayload() instanceof Auction) {
                this.currentAuction = (Auction) message.getPayload();
            }
            // Đồng bộ user balance mới nhất từ server
            SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
            updateRoomUI();
            if (currentAuction.getEndTime() != null) {
                startCountdownTimer(currentAuction.getEndTime());
            }
        });
    }

    private void handleNewBidUpdate(Message message) {
        Platform.runLater(() -> {
            try {
                Object[] payload = (Object[]) message.getPayload();
                int auctionId = (Integer) payload[0];
                double newPrice = (Double) payload[1];
                String bidderName = (String) payload[2];

                if (currentAuction == null || currentAuction.getAuctionId() != auctionId) return;

                System.out.println("🔥 [Live] Giá mới: " + newPrice + " bởi " + bidderName);
                currentAuction.setCurrentPrice(newPrice);
                lblCurrentPrice.setText(String.format("%,.0f UETệ", newPrice));

                // Hiển thị tên "Bạn (Tôi)" nếu là chính mình
                boolean isMe = currentUser != null && bidderName.equals(currentUser.getUsername());
                String displayName = isMe ? "Bạn (Tôi)" : bidderName;
                addBidLogToUI(displayName, newPrice, true);

                // --- SỬA LOGIC: Cập nhật Winner mới lên RAM Client để tính toán ví tạm giữ ---
                if (isMe) {
                    currentAuction.setCurrentWinnerId(currentUser.getId());
                } else {
                    // Nếu người khác đè giá, kiểm tra xem mình có vừa bị mất ngôi dẫn đầu không
                    if (currentAuction.getCurrentWinnerId() != null && currentAuction.getCurrentWinnerId().equals(currentUser.getId())) {
                        currentAuction.setCurrentWinnerId(-1); // Đánh dấu không còn dẫn đầu
                    }
                }

                // Cập nhật ngay lập tức UI số tiền tạm giữ
                if (lblUserEscrow != null) {
                    double escrow = isMe ? newPrice : 0;
                    lblUserEscrow.setText(String.format("%,.0f UETệ", escrow));
                }

                // Nếu bị người khác đè giá -> Chủ động xin Server Profile mới để cộng lại tiền vào ví chính
                if (!isMe) {
                    SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
                }
                // ----------------------------------------------------------------------------

            } catch (Exception e) {
                System.err.println("Lỗi gói tin NEW_BID_UPDATE: " + e.getMessage());
            }
        });
    }

    private void handleBidSuccess(Message message) {
        Platform.runLater(() -> {
            Double myBidAmount = (Double) message.getPayload();
            showAlert("Thành công",
                    "Bạn đã đặt giá thành công: " + String.format("%,.0f UETệ", myBidAmount),
                    Alert.AlertType.INFORMATION);
            txtBidAmount.clear();

            if (myBidAmount != null) {
                lblUserEscrow.setText(String.format("%,.0f UETệ", myBidAmount));
                if (currentAuction != null && currentUser != null) {
                    currentAuction.setCurrentWinnerId(currentUser.getId());
                    currentAuction.setCurrentPrice(myBidAmount);
                    lblCurrentPrice.setText(String.format("%,.0f UETệ", myBidAmount));
                }
            }
            // Cập nhật lại số dư ví chính realtime từ server
            SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
        });
    }

    private void handleBidFailed(Message message) {
        Platform.runLater(() -> {
            String reason = (message.getMessage() != null && !message.getMessage().isBlank())
                    ? message.getMessage()
                    : (message.getPayload() instanceof String ? (String) message.getPayload() : "Lỗi không xác định.");
            showAlert("Đặt giá thất bại", reason, Alert.AlertType.ERROR);
        });
    }

    private void handleTimeExtended(Message message) {
        Platform.runLater(() -> {
            try {
                LocalDateTime newEndTime = (LocalDateTime) message.getPayload();
                currentAuction.setEndTime(newEndTime);
                startCountdownTimer(newEndTime);
                showAlert("Gia hạn", "Có người đặt giá phút chót! Thời gian đã được cộng thêm.", Alert.AlertType.WARNING);
            } catch (Exception e) {
                System.err.println("[ROOM] Lỗi handleTimeExtended: " + e.getMessage());
            }
        });
    }

    private void handleAuctionEnded(Message message) {
        Platform.runLater(() -> {
            try {
                Object[] payload = (Object[]) message.getPayload();
                int auctionId;
                if (payload[0] instanceof Integer) {
                    auctionId = (Integer) payload[0];
                } else {
                    return;
                }
                if (currentAuction == null || currentAuction.getAuctionId() != auctionId) return;

                if (countdownTimeline != null) countdownTimeline.stop();
                lblCountdown.setText("ĐÃ KẾT THÚC!");
                lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
                btnPlaceBid.setDisable(true);
                txtBidAmount.setDisable(true);

                String winnerUsername = payload.length > 1 && payload[1] != null ? (String) payload[1] : null;
                double finalPrice = payload.length > 2 ? ((Number) payload[2]).doubleValue() : 0;

                String msg;
                if (winnerUsername != null && currentUser != null && winnerUsername.equals(currentUser.getUsername())) {
                    msg = "🎉 CHÚC MỪNG! Bạn đã THẮNG phiên đấu giá với mức " + String.format("%,.0f UETệ", finalPrice);
                } else if (winnerUsername != null) {
                    msg = "Phiên đã kết thúc! Người thắng: " + winnerUsername + " với giá " + String.format("%,.0f UETệ", finalPrice);
                } else {
                    msg = "Phiên đấu giá đã kết thúc mà không có ai đặt giá.";
                }
                showAlert("Kết thúc", msg, Alert.AlertType.INFORMATION);

            } catch (Exception e) {
                System.err.println("[ROOM] Lỗi handleAuctionEnded: " + e.getMessage());
            }
        });
    }

    private void handleProfileUpdate(Message message) {
        Platform.runLater(() -> {
            if (message.getPayload() instanceof User) {
                User updatedUser = (User) message.getPayload();
                this.currentUser = updatedUser;
                ClientSession.getInstance().setCurrentUser(updatedUser);

                if (lblUserBalance != null) {
                    lblUserBalance.setText(String.format("%,.0f UETệ", updatedUser.getBalance()));
                }

                // --- SỬA LOGIC: Khi Profile cập nhật, tự tính toán hiển thị lại ví tạm giữ ---
                if (lblUserEscrow != null && currentAuction != null) {
                    double escrow = 0;
                    if (currentAuction.getCurrentWinnerId() != null && currentAuction.getCurrentWinnerId().equals(updatedUser.getId())) {
                        escrow = currentAuction.getCurrentPrice();
                    }
                    lblUserEscrow.setText(String.format("%,.0f UETệ", escrow));
                }
                // ----------------------------------------------------------------------------
            }
        });
    }

    @FXML
    void onSubmitBid(ActionEvent event) {
        if (currentAuction == null) {
            showAlert("Lỗi", "Dữ liệu phòng đấu giá chưa sẵn sàng!", Alert.AlertType.ERROR);
            return;
        }

        if (!ClientSession.getInstance().isLoggedIn()) {
            showAlert("Chưa đăng nhập", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!", Alert.AlertType.ERROR);
            return;
        }

        String amountText = txtBidAmount.getText().trim();
        if (amountText.isEmpty()) {
            showAlert("Thông báo", "Vui lòng nhập số tiền!", Alert.AlertType.WARNING);
            return;
        }

        try {
            double bidAmount = Double.parseDouble(amountText);
            if (bidAmount <= currentAuction.getCurrentPrice()) {
                showAlert("Lỗi đặt giá",
                        "Giá mới phải cao hơn giá hiện tại (" + String.format("%,.0f UETệ", currentAuction.getCurrentPrice()) + ")!",
                        Alert.AlertType.ERROR);
                return;
            }

            System.out.println("💰 Gửi đặt giá: " + bidAmount + " cho phiên #" + currentAuction.getAuctionId());
            SocketClient.getInstance().sendRequest(
                    RequestCode.PLACE_BID,
                    new com.auction.common.network.BidPlaceDTO(currentAuction.getAuctionId(), bidAmount)
            );

        } catch (NumberFormatException e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số tiền hợp lệ!", Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleBackToMarket(ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        if (currentAuction != null) {
            SocketClient.getInstance().sendRequest(RequestCode.LEAVE_ROOM, currentAuction.getAuctionId());
        }
        MessageRouter router = MessageRouter.getInstance();
        router.unregister(ResponseCode.ROOM_JOIN_SUCCESS);
        router.unregister(ResponseCode.NEW_BID_UPDATE);
        router.unregister(ResponseCode.BID_SUCCESS);
        router.unregister(ResponseCode.AUCTION_TIME_EXTENDED);
        router.unregister(ResponseCode.AUCTION_ENDED);
        router.unregister(ResponseCode.PROFILE_RESULT);
        router.unregister(ResponseCode.ROOM_JOIN_FAILED);
        router.unregister(ResponseCode.BID_FAILED);

        Stage stage = (Stage) lblProductName.getScene().getWindow();
        stage.close();
    }

    private void updateRoomUI() {
        if (currentAuction == null) return;

        if (currentAuction.getItem() != null) {
            lblProductName.setText(currentAuction.getItem().getName());
            if (lblDescription != null) lblDescription.setText(currentAuction.getItem().getDescription());
            loadProductProperties(currentAuction.getItem());

            if (currentAuction.getItem().getImgItem() != null) {
                try {
                    String path = currentAuction.getItem().getImgItem().trim();
                    if (!path.startsWith("/")) path = "/" + path;
                    InputStream is = getClass().getResourceAsStream(path);
                    if (is != null && imgProduct != null) imgProduct.setImage(new Image(is));
                } catch (Exception e) {
                    System.err.println("[ROOM] Không load được ảnh: " + e.getMessage());
                }
            }
        }

        if (lblStartPrice != null)
            lblStartPrice.setText(String.format("%,.0f UETệ", currentAuction.getStartingPrice()));
        if (lblCurrentPrice != null)
            lblCurrentPrice.setText(String.format("%,.0f UETệ", currentAuction.getCurrentPrice()));
        if (lblUserBalance != null && currentUser != null)
            lblUserBalance.setText(String.format("%,.0f UETệ", currentUser.getBalance()));

        // --- SỬA LOGIC: Thay thế hardcode "0 UETệ", tự tính toán khi render UI phòng phòng ---
        if (lblUserEscrow != null) {
            double escrow = 0;
            if (currentUser != null && currentAuction.getCurrentWinnerId() != null
                    && currentAuction.getCurrentWinnerId().equals(currentUser.getId())) {
                escrow = currentAuction.getCurrentPrice();
            }
            lblUserEscrow.setText(String.format("%,.0f UETệ", escrow));
        }
        // ----------------------------------------------------------------------------------

        if (listHistoryContainer != null) listHistoryContainer.getChildren().clear();

        if (currentAuction.getBids() != null && !currentAuction.getBids().isEmpty()) {
            currentAuction.getBids().sort((b1, b2) -> Double.compare(b1.getBidAmount(), b2.getBidAmount()));
            for (BiddingHistory bid : currentAuction.getBids()) {
                boolean isMe = (currentUser != null && bid.getBidderId() == currentUser.getId());
                String displayName = isMe ? "Bạn (Tôi)" : "Người dùng #" + bid.getBidderId();
                addBidLogToUI(displayName, bid.getBidAmount(), false);
            }
        }

        if (btnPlaceBid != null) {
            boolean isRunning = "RUNNING".equalsIgnoreCase(currentAuction.getAuctionStatus())
                    || currentAuction.getAuctionStatus() == null;
            btnPlaceBid.setDisable(!isRunning);
        }
        if (txtBidAmount != null) {
            txtBidAmount.setDisable(false);
        }
    }

    private void addBidLogToUI(String bidderName, double amount, boolean isNewest) {
        if (listHistoryContainer == null) return;

        HBox row = new HBox(10);
        boolean isMe = bidderName.equals("Bạn (Tôi)")
                || (currentUser != null && bidderName.equals(currentUser.getUsername()));

        if (isMe) {
            row.setStyle("-fx-padding: 8 12; -fx-background-color: #EFF6FF; -fx-background-radius: 8; " +
                    "-fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-alignment: center-left;");
        } else {
            row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; " +
                    "-fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");
        }

        Label lblUser = new Label(bidderName);
        lblUser.setStyle("-fx-text-fill: " + (isMe ? "#0F172A" : "#1E293B") +
                "; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");

        Label lblPrice = new Label(String.format("%,.0f UETệ", amount));
        lblPrice.setStyle("-fx-text-fill: " + (isNewest ? "#DC2626" : "#475569") +
                "; -fx-font-weight: bold; -fx-font-size: 13px;");

        HBox rightContainer = new HBox();
        javafx.scene.layout.HBox.setHgrow(rightContainer, javafx.scene.layout.Priority.ALWAYS);
        rightContainer.setStyle("-fx-alignment: center-right;");
        rightContainer.getChildren().add(lblPrice);

        row.getChildren().addAll(lblUser, rightContainer);
        listHistoryContainer.getChildren().add(0, row);
    }

    private void startCountdownTimer(LocalDateTime endTime) {
        if (countdownTimeline != null) countdownTimeline.stop();
        if (lblCountdown == null || endTime == null) return;

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(endTime)) {
                lblCountdown.setText("ĐÃ KẾT THÚC!");
                lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
                if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
                countdownTimeline.stop();
                return;
            }
            long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();
            lblCountdown.setText(String.format("%02dh : %02dm : %02ds",
                    totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60));
        }));
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    private void loadProductProperties(Item item) {
        if (vboxProperties == null || item == null) return;
        vboxProperties.getChildren().clear();

        if (item instanceof Vehicle) {
            Vehicle v = (Vehicle) item;
            addPropertyRow("Hãng sản xuất:", v.getMake());
            addPropertyRow("Dòng xe:", v.getModelVehicle());
            addPropertyRow("Năm sản xuất:", v.getManufactureYear() > 0 ? String.valueOf(v.getManufactureYear()) : null);
            addPropertyRow("ODO:", v.getMileage() > 0 ? String.format("%,d km", v.getMileage()) : "0 km");
            addPropertyRow("Nhiên liệu:", v.getFuelType());
        } else if (item instanceof Art) {
            Art a = (Art) item;
            addPropertyRow("Họa sĩ:", a.getArtist());
            addPropertyRow("Năm sáng tác:", a.getYearCreated() > 0 ? String.valueOf(a.getYearCreated()) : null);
            addPropertyRow("Chất liệu:", a.getMedium());
        } else if (item instanceof Electronics) {
            Electronics e = (Electronics) item;
            addPropertyRow("Thương hiệu:", e.getBrand());
            addPropertyRow("Model:", e.getModel());
            addPropertyRow("Tình trạng:", e.getItemCondition());
        }
    }

    private void addPropertyRow(String attrName, String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("[NULL]")) return;
        HBox row = new HBox(15);
        Label lblTitle = new Label(attrName);
        lblTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 14px; -fx-min-width: 100px;");
        Label lblValue = new Label(value);
        lblValue.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 14px; -fx-font-weight: bold;");
        row.getChildren().addAll(lblTitle, lblValue);
        vboxProperties.getChildren().add(row);
    }

    private void handleRoomJoinFailed(Message message) {
        Platform.runLater(() -> {
            System.err.println("❌ Vào phòng thất bại: " + message.getMessage());
            String reason = (message.getMessage() != null) ? message.getMessage() : "Lỗi không xác định từ Server.";
            showAlert("Vào phòng thất bại", "Server từ chối: " + reason, Alert.AlertType.ERROR);

            if (lblProductName != null && lblProductName.getScene() != null) {
                Stage stage = (Stage) lblProductName.getScene().getWindow();
                stage.close();
            }
        });
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}