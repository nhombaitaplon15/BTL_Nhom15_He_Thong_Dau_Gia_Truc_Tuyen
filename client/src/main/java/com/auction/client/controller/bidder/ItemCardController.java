package com.auction.client.controller.bidder;



import com.auction.client.core.MessageRouter;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.model.Auction;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.InputStream;
import java.time.LocalDateTime;

public class ItemCardController {
    @FXML private ImageView imgItem;
    @FXML private Label name;
    @FXML private Label startPrice;
    @FXML private Label currentPrice;
    @FXML private Label timeRemaining;

    private Item currentItem;
    private User currentUser;
    private int currentAuctionId;

    private Timeline countdownTimeline;

    @FXML
    public void initialize() {
        // Lắng nghe sự kiện kết thúc phiên đấu giá từ Server gửi về cho toàn bộ các Client (Khớp enum của bạn)
        MessageRouter.getInstance().register(ResponseCode.AUCTION_ENDED, this::handleAuctionEndedNotification);
    }

    public void setData(Item item, int auctionId, User user, double currentPriceVal, LocalDateTime endTime) {
        if (item == null) {
            if (name != null) name.setText("Vật phẩm không tồn tại");
            return;
        }

        this.currentItem = item;
        this.currentAuctionId = auctionId;
        this.currentUser = user;

        if (name != null) name.setText(item.getName());
        if (startPrice != null) startPrice.setText(String.format("Giá khởi điểm: %,.0f UETệ", item.getStartingPrice()));
        if (currentPrice != null) currentPrice.setText(String.format("Giá hiện tại: %,.0f UETệ", currentPriceVal));

        try {
            if (item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                String imagePath = item.getImgItem().trim();
                if (!imagePath.startsWith("/")) imagePath = "/" + imagePath;
                InputStream is = getClass().getResourceAsStream(imagePath);
                if (is != null) imgItem.setImage(new Image(is));
            }
        } catch (Exception e) {
            System.out.println("⚠️ Không thể tải ảnh sản phẩm: " + item.getName());
        }

        if (endTime != null && LocalDateTime.now().isAfter(endTime)) {
            if (timeRemaining != null) {
                timeRemaining.setText("Thời gian còn lại: Đã kết thúc!");
                timeRemaining.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            }
            Platform.runLater(this::removeCardFromUI);
            return;
        }

        startCountdown(endTime);
    }

    private void startCountdown(LocalDateTime endTime) {
        if (countdownTimeline != null) countdownTimeline.stop();
        if (endTime == null || timeRemaining == null) return;

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();

            if (now.isAfter(endTime)) {
                timeRemaining.setText("Thời gian còn lại: Đã kết thúc!");
                timeRemaining.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                countdownTimeline.stop();
                // Phía Client chỉ cần đợi Server quét luồng ngầm tự đóng và bắn gói AUCTION_ENDED về
                return;
            }

            long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            timeRemaining.setText(String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds));
        }));

        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    private void handleAuctionEndedNotification(Message message) {
        // Payload của AUCTION_ENDED trả về mảng Object[] {auctionId, winnerUsername, finalPrice}
        if (!(message.getPayload() instanceof Object[])) return;
        Object[] data = (Object[]) message.getPayload();

        int auctionId = (int) data[0];
        if (auctionId != this.currentAuctionId) return; // Không phải thẻ này thì bỏ qua

        String winnerUsername = (String) data[1];
        double finalPrice = (double) data[2];

        Platform.runLater(() -> {
            stopTimer();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("KẾT THÚC PHIÊN ĐẤU GIÁ");
            alert.setHeaderText("Phiên đấu giá cho [" + (currentItem != null ? currentItem.getName() : "#" + currentAuctionId) + "] đã khép lại!");

            if (winnerUsername != null && !winnerUsername.trim().isEmpty()) {
                if (currentUser != null && winnerUsername.equals(currentUser.getUsername())) {
                    alert.setAlertType(Alert.AlertType.INFORMATION);
                    alert.setContentText("🎉 CHÚC MỪNG BẠN!\nBạn đã thắng đấu giá với mức giá " + String.format("%,.0f UETệ", finalPrice));
                } else {
                    alert.setAlertType(Alert.AlertType.WARNING);
                    alert.setContentText("Chúc bạn may mắn lần sau!\nVật phẩm đã thuộc về thành viên [" + winnerUsername + "] với mức giá " + String.format("%,.0f UETệ", finalPrice));
                }
            } else {
                alert.setAlertType(Alert.AlertType.INFORMATION);
                alert.setContentText("Phiên đấu giá kết thúc mà không có ai đặt giá.");
            }
            alert.showAndWait();
            removeCardFromUI();
        });
    }

    private void removeCardFromUI() {
        try {
            Node cardContainer = null;
            Node currentNode = timeRemaining;
            while (currentNode != null) {
                Parent parent = currentNode.getParent();
                if (parent instanceof Pane && ! (parent.getClass().getName().contains("Card") || (parent.getId() != null && parent.getId().contains("card")))) {
                    cardContainer = currentNode;
                    break;
                }
                currentNode = parent;
            }
            if (cardContainer == null && timeRemaining.getParent() != null) {
                cardContainer = timeRemaining.getParent().getParent();
            }
            if (cardContainer != null && cardContainer.getParent() instanceof Pane) {
                ((Pane) cardContainer.getParent()).getChildren().remove(cardContainer);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi hạ thẻ: " + e.getMessage());
        }
    }

    public void stopTimer() {
        if (countdownTimeline != null) countdownTimeline.stop();
        MessageRouter.getInstance().unregister(ResponseCode.AUCTION_ENDED);
    }

    @FXML
    void handleBidAction(ActionEvent event) {
        // Giữ nguyên logic mở phòng đấu giá trực chiến...
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionRoomView.fxml"));
            Parent root = loader.load();
            AuctionRoomController roomController = loader.getController();
            if (roomController != null) {
                roomController.loadAuctionDetail(currentAuctionId, currentItem.getName(), currentUser);
            }
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Sàn Đấu Giá Live - Phiên #" + currentAuctionId);
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
