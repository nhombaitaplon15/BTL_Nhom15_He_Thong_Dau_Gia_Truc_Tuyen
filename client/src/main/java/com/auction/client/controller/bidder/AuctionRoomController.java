package com.auction.client.controller.bidder;

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
    }

    public void loadAuctionDetail(Auction auction, User user) {
        this.currentAuction = auction; // Gắn tạm để lấy ID
        this.currentUser = user;
        SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, auction.getAuctionId());
    }

    private void handleRoomJoinSuccess(Message message) {
        Platform.runLater(() -> {
            System.out.println("🟢 Server báo tham gia phòng thành công!");
            // NẾU SERVER TRẢ VỀ FULL AUCTION (CÓ LỊCH SỬ), TA CẬP NHẬT LẠI currentAuction
            if (message.getPayload() instanceof Auction) {
                this.currentAuction = (Auction) message.getPayload();
            }
            updateRoomUI();
            startCountdownTimer(currentAuction.getEndTime());
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
                addBidLogToUI(bidderName, newPrice, true);
            } catch (Exception e) {
                System.err.println("Lỗi gói tin NEW_BID_UPDATE: " + e.getMessage());
            }
        });
    }

    private void handleBidSuccess(Message message) {
        Platform.runLater(() -> {
            Double myBidAmount = (Double) message.getPayload();
            showAlert("Thành công", "Bạn đã đặt giá thành công: " + String.format("%,.0f UETệ", myBidAmount), Alert.AlertType.INFORMATION);
            txtBidAmount.clear();
            lblUserEscrow.setText(String.format("%,.0f UETệ", myBidAmount));
            // Cập nhật lại ví
            SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
        });
    }

    private void handleTimeExtended(Message message) {
        Platform.runLater(() -> {
            try {
                LocalDateTime newEndTime = (LocalDateTime) message.getPayload();
                currentAuction.setEndTime(newEndTime);
                startCountdownTimer(newEndTime);
                showAlert("Gia hạn", "Có người đặt giá phút chót! Thời gian đã được cộng thêm.", Alert.AlertType.WARNING);
            } catch (Exception e) {}
        });
    }

    private void handleAuctionEnded(Message message) {
        Platform.runLater(() -> {
            try {
                Object[] payload = (Object[]) message.getPayload();
                int auctionId = (Integer) payload[0];
                if (currentAuction == null || currentAuction.getAuctionId() != auctionId) return;

                if (countdownTimeline != null) countdownTimeline.stop();
                lblCountdown.setText("ĐÃ KẾT THÚC!");
                lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
                btnPlaceBid.setDisable(true);
                txtBidAmount.setDisable(true);
                showAlert("Kết thúc", "Phiên đấu giá đã khép lại!", Alert.AlertType.INFORMATION);
            } catch (Exception e) {}
        });
    }

    private void handleProfileUpdate(Message message) {
        Platform.runLater(() -> {
            if (message.getPayload() instanceof User) {
                this.currentUser = (User) message.getPayload();
                lblUserBalance.setText(String.format("%,.0f UETệ", currentUser.getBalance()));
            }
        });
    }

    @FXML
    void onSubmitBid(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;
        String amountText = txtBidAmount.getText().trim();
        if (amountText.isEmpty()) {
            showAlert("Thông báo", "Vui lòng nhập số tiền!", Alert.AlertType.WARNING);
            return;
        }
        try {
            double bidAmount = Double.parseDouble(amountText);
            if (bidAmount <= currentAuction.getCurrentPrice()) {
                showAlert("Lỗi đặt giá", "Giá mới phải cao hơn giá hiện tại!", Alert.AlertType.ERROR);
                return;
            }
            SocketClient.getInstance().sendRequest(RequestCode.PLACE_BID, new com.auction.common.network.BidPlaceDTO(currentAuction.getAuctionId(), bidAmount));
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

        Stage stage = (Stage) lblProductName.getScene().getWindow();
        stage.close();
    }

    private void updateRoomUI() {
        if(currentAuction.getItem() != null) {
            lblProductName.setText(currentAuction.getItem().getName());
            lblDescription.setText(currentAuction.getItem().getDescription());
            loadProductProperties(currentAuction.getItem());

            if (currentAuction.getItem().getImgItem() != null) {
                try {
                    String path = currentAuction.getItem().getImgItem().trim();
                    if (!path.startsWith("/")) path = "/" + path;
                    InputStream is = getClass().getResourceAsStream(path);
                    if (is != null) imgProduct.setImage(new Image(is));
                } catch (Exception e) {}
            }
        }

        lblStartPrice.setText(String.format("%,.0f UETệ", currentAuction.getStartingPrice()));
        lblCurrentPrice.setText(String.format("%,.0f UETệ", currentAuction.getCurrentPrice()));
        lblUserBalance.setText(String.format("%,.0f UETệ", currentUser.getBalance()));

        if (listHistoryContainer != null) listHistoryContainer.getChildren().clear();
        if (currentAuction.getBids() != null) {
            currentAuction.getBids().sort((b1, b2) -> Double.compare(b1.getBidAmount(), b2.getBidAmount())); // Tăng dần để cái mới nhất nhét vào top
            for (BiddingHistory bid : currentAuction.getBids()) {
                String name = (bid.getBidderId() == currentUser.getId()) ? "Bạn (Tôi)" : "Người dùng #" + bid.getBidderId();
                addBidLogToUI(name, bid.getBidAmount(), false);
            }
        }
    }

    private void addBidLogToUI(String bidderName, double amount, boolean isNewest) {
        HBox row = new HBox(10);
        boolean isMe = bidderName.equals("Bạn (Tôi)") || bidderName.equals(currentUser.getUsername());

        if (isMe) {
            bidderName = "Bạn (Tôi)";
            row.setStyle("-fx-padding: 8 12; -fx-background-color: #EFF6FF; -fx-background-radius: 8; -fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-alignment: center-left;");
        } else {
            row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");
        }

        Label lblUser = new Label(bidderName);
        lblUser.setStyle("-fx-text-fill: " + (isMe ? "#0F172A" : "#1E293B") + "; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");

        Label lblPrice = new Label(String.format("%,.0f UETệ", amount));
        lblPrice.setStyle("-fx-text-fill: " + (isNewest ? "#DC2626" : "#475569") + "; -fx-font-weight: bold; -fx-font-size: 13px;");

        HBox rightContainer = new HBox();
        javafx.scene.layout.HBox.setHgrow(rightContainer, javafx.scene.layout.Priority.ALWAYS);
        rightContainer.setStyle("-fx-alignment: center-right;");
        rightContainer.getChildren().add(lblPrice);

        row.getChildren().addAll(lblUser, rightContainer);
        listHistoryContainer.getChildren().add(0, row); // Luôn chèn lên đầu
    }

    private void startCountdownTimer(LocalDateTime endTime) {
        if (countdownTimeline != null) countdownTimeline.stop();
        if (lblCountdown == null || endTime == null) return;

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(endTime)) {
                lblCountdown.setText("ĐÃ KẾT THÚC!");
                lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
                btnPlaceBid.setDisable(true);
                countdownTimeline.stop();
                return;
            }
            long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();
            lblCountdown.setText(String.format("%02dh : %02dm : %02ds", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60));
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

            // Hiện thông báo lỗi từ Server lên màn hình cho User thấy
            String reason = (message.getMessage() != null) ? message.getMessage() : "Lỗi không xác định từ Server.";
            showAlert("Vào phòng thất bại", "Server từ chối: " + reason, Alert.AlertType.ERROR);

            // Tự động đóng cái cửa sổ phòng trống này lại, bắt quay về sàn đấu giá
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