package com.auction.client.controller.bidder;

import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.model.Auction;
import com.auction.server.dao.AuctionDAO;
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
    private final AuctionDAO auctionDAO = new AuctionDAO();

    public void setData(Item item, int auctionId, User user, double currentPriceVal, LocalDateTime endTime) {
        if (item == null) {
            if (name != null) name.setText("Vật phẩm không tồn tại");
            return;
        }

        this.currentItem = item;
        this.currentAuctionId = auctionId;
        this.currentUser = user;

        if (name != null) {
            name.setText(item.getName());
        }

        if (startPrice != null) {
            startPrice.setText(String.format("Giá khởi điểm: %,.0f UETệ", item.getStartingPrice()));
        }
        if (currentPrice != null) {
            currentPrice.setText(String.format("Giá hiện tại: %,.0f UETệ", currentPriceVal));
        }

        try {
            if (item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                String imagePath = item.getImgItem().trim();
                if (!imagePath.startsWith("/")) imagePath = "/" + imagePath;
                InputStream is = getClass().getResourceAsStream(imagePath);
                if (is != null) imgItem.setImage(new Image(is));
            }
        } catch (Exception e) {
            System.out.println("Lỗi tải ảnh: " + item.getName());
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
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        if (endTime == null || timeRemaining == null) {
            if (timeRemaining != null) timeRemaining.setText("Thời gian còn lại: --:--:--");
            return;
        }

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();

            if (now.isAfter(endTime)) {
                timeRemaining.setText("Thời gian còn lại: Đã kết thúc!");
                timeRemaining.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                countdownTimeline.stop();

                Platform.runLater(() -> {
                    try {
                        auctionDAO.closeAuctionAndDetermineWinner(currentAuctionId);
                        Auction completedAuction = auctionDAO.getAuctionById(currentAuctionId);

                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("KẾT THÚC PHIÊN ĐẤU GIÁ");
                        alert.setHeaderText("Phiên đấu giá cho [" + currentItem.getName() + "] đã khép lại!");

                        if (completedAuction != null && completedAuction.getCurrentWinnerId() != null) {
                            int winnerId = completedAuction.getCurrentWinnerId();
                            double finalPrice = completedAuction.getCurrentPrice();

                            if (currentUser != null && winnerId == currentUser.getId()) {
                                alert.setAlertType(Alert.AlertType.INFORMATION);
                                alert.setContentText("CHÚC MỪNG BẠN! Bạn đã thắng phiên đấu giá với mức giá "
                                    + String.format("%,.0f UETệ", finalPrice) + ".\nVật phẩm đã thuộc sở hữu của bạn!");
                            } else {
                                alert.setAlertType(Alert.AlertType.WARNING);
                                alert.setContentText("Chúc bạn may mắn lần sau!\nVật phẩm đã được bán thành công cho thành viên #"
                                    + winnerId + " với mức giá " + String.format("%,.0f UETệ", finalPrice) + ".");
                            }
                        } else {
                            alert.setAlertType(Alert.AlertType.INFORMATION);
                            alert.setContentText("Phiên đấu giá kết thúc mà không có thành viên nào tham gia trả giá.");
                        }
                        alert.showAndWait();
                        removeCardFromUI();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return;
            }

            long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            String timeText = String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds);
            timeRemaining.setText(timeText);
        }));

        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    private void removeCardFromUI() {
        try {
            Node cardContainer = null;
            Node currentNode = timeRemaining;

            while (currentNode != null) {
                Parent parent = currentNode.getParent();
                if (parent instanceof Pane && !(parent.getClass().getName().contains("Card") || (parent.getId() != null && parent.getId().contains("card")))) {
                    cardContainer = currentNode;
                    break;
                }
                currentNode = parent;
            }

            if (cardContainer == null && timeRemaining.getParent() != null) {
                cardContainer = timeRemaining.getParent().getParent();
            }

            if (cardContainer != null && cardContainer.getParent() instanceof Pane) {
                Pane marketGrid = (Pane) cardContainer.getParent();
                marketGrid.getChildren().remove(cardContainer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopTimer() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
    }

    @FXML
    void handleBidAction(ActionEvent event) {
        if (currentItem == null || currentAuctionId == 0) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText("Phiên đấu giá cho vật phẩm này hiện không khả dụng!");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/AuctionRoomView.fxml"));
            Parent root = loader.load();

            AuctionRoomController roomController = loader.getController();
            if (roomController != null) {
                roomController.loadAuctionDetail(currentAuctionId, currentItem.getName(), currentUser);
            }

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Sàn Đấu Giá Trực Chiến Live - Phiên #" + currentAuctionId);
            stage.setResizable(true);
            stage.setMaximized(true);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getAuctionId() {
        return this.currentAuctionId;
    }

    public void updateLivePrice(double newPrice) {
        Platform.runLater(() -> {
            if (currentPrice != null) {
                currentPrice.setText(String.format("Giá hiện tại: %,.0f UETệ", newPrice));
            }
        });
    }

    public void markAsEnded() {
        Platform.runLater(() -> {
            stopTimer();
            if (timeRemaining != null) {
                timeRemaining.setText("ĐÃ KẾT THÚC");
                timeRemaining.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
        });
    }
}