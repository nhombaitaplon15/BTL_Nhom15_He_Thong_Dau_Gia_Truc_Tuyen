package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import src.main.java.com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.DBConnection;
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
import java.sql.Connection;
import java.time.LocalDateTime;

public class AuctionDetailController {

    // 🎯 CỐ ĐỊNH: Đồng bộ mảng 4 ID Admin hệ thống
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

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();

    private Timeline liveStatusTimeline;
    private Auction currentAuction;
    private User currentUser;

    public void loadAuctionDetail(int auctionId, String fallbackName, User user) {
        this.currentUser = user;

        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName != null) lblItemName.setText(fallbackName);

        if (hboxWinnerActions != null) {
            hboxWinnerActions.setVisible(false);
            hboxWinnerActions.setManaged(false);
        }

        new Thread(() -> {
            try {
                Auction auction = auctionDAO.getAuctionById(auctionId);
                if (auction != null) {
                    this.currentAuction = auction;
                    Item item = itemDAO.getItemById(auction.getItemId());

                    Platform.runLater(() -> {
                        if (lblCurrentPrice != null) {
                            lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
                        }

                        if (lblStartTime != null && auction.getStartTime() != null) {
                            lblStartTime.setText(auction.getStartTime().toString());
                        }
                        if (lblEndTime != null && auction.getEndTime() != null) {
                            lblEndTime.setText(auction.getEndTime().toString());
                        }

                        checkAndToggleWinnerActions(auction, user);

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

                        if (auction.getEndTime() != null && "RUNNING".equalsIgnoreCase(auction.getAuctionStatus())) {
                            startRealtimeStatusTracker(auction.getEndTime(), auction, user);
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi kết nối dữ liệu chi tiết phiên đấu giá!");
                e.printStackTrace();
            }
        }).start();
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

                    // Tính toán ID Admin tương ứng hiển thị lên giao diện
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
                        lblStatus.setText("❌ QUÁ HẠN 24H (TỰ ĐỘNG HỦY PHIÊN PHẠT CỌC)");
                        lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
                    }
                    if (hboxWinnerActions != null) {
                        hboxWinnerActions.setVisible(false);
                        hboxWinnerActions.setManaged(false);
                    }
                    autoRejectWinOverdue(user, auction);
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

    private void autoRejectWinOverdue(User user, Auction auction) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            int idx = auction.getAuctionId() % ESCROW_ADMIN_IDS.length;
            int assignedAdminId = ESCROW_ADMIN_IDS[idx];
            if (paymentDAO.processPenalty7Percent(conn, user.getId(), assignedAdminId, auction.getCurrentPrice())) {
                auctionDAO.updateStatus(auction.getAuctionId(), "REJECTED");
                conn.commit();
            } else { conn.rollback(); }
        } catch (Exception e) { e.printStackTrace(); }
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

    /**
     * LUỒNG CHẤP NHẬN: Gọi Database chuyển 15% cho Admin và 85% cho Seller
     */

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
                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);

                    int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
                    int assignedAdminId = ESCROW_ADMIN_IDS[idx];

                    boolean success = paymentDAO.processAcceptPayment(conn, currentAuction.getSellerId(), assignedAdminId, currentBidPrice);

                    if (success) {
                        auctionDAO.updateStatus(currentAuction.getAuctionId(), "PAID");

                        // 🎯 DÒNG THÊM MỚI CHÍ CHÓC: Cập nhật trạng thái ngay trên RAM để đồng bộ UI
                        currentAuction.setAuctionStatus("PAID");

                        conn.commit();
                        showNotification("Thành công", "Giao dịch thành công! Vật phẩm đã chính thức thuộc về bạn.");
                        handleClose(event);
                    } else {
                        conn.rollback();
                        showNotification("Thất bại", "Không thể xử lý giải ngân dòng tiền.");
                    }
                } catch (Exception e) {
                    showNotification("Lỗi hệ thống", "Không thể thực hiện giao dịch: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
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
                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);

                    int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
                    int assignedAdminId = ESCROW_ADMIN_IDS[idx];

                    boolean success = paymentDAO.processPenalty7Percent(conn, currentUser.getId(), assignedAdminId, currentBidPrice);

                    if (success) {
                        auctionDAO.updateStatus(currentAuction.getAuctionId(), "REJECTED");

                        // 🎯 DÒNG THÊM MỚI CHÍ CHÓC: Cập nhật trạng thái ngay trên RAM để đồng bộ UI
                        currentAuction.setAuctionStatus("REJECTED");

                        conn.commit();
                        showNotification("Đã hủy phiên", String.format("Hệ thống đã hủy phiên và áp lệnh phạt cọc 7%% (%,.0f UETệ) thành công.", penaltyFee));
                        handleClose(event);
                    } else {
                        conn.rollback();
                        showNotification("Thất bại", "Không thể xử lý lệnh phạt cọc.");
                    }
                } catch (Exception e) {
                    showNotification("Lỗi hệ thống", "Không thể xử lý hủy phiên: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
    @FXML
    void handleClose(ActionEvent event) {
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