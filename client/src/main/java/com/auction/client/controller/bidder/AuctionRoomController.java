package com.auction.client.controller.bidder;
import com.auction.common.model.*;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.service.BiddingService ;
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
import java.util.List;

public class AuctionRoomController {

    // 🎯 Đồng bộ 4 ID Admin để xác định Admin quản lý phòng
    private static final int[] ESCROW_ADMIN_IDS = {1, 2, 3, 4};

    @FXML private Label lblProductName;
    @FXML private ImageView imgProduct;
    @FXML private Label lblDescription;
    @FXML private Label lblCountdown;
    @FXML private Label lblUserBalance;
    @FXML private Label lblUserEscrow;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;

    @FXML private VBox listHistoryContainer;
    @FXML private VBox vboxProperties;
    @FXML private VBox boxBidHistory;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final BiddingService biddingService = new BiddingService(null);

    private int activeAuctionId;
    private User currentOnlineUser;
    private double currentAuctionPrice;
    private Timeline roomCountdownTimeline;

    public void loadAuctionDetail(int auctionId, String itemName, User user) {
        this.activeAuctionId = auctionId;
        this.currentOnlineUser = user;

        if (lblProductName != null) lblProductName.setText(itemName);
        refreshRoomData();
    }
    private void refreshRoomData() {
        new Thread(() -> {
            try {
                Auction auction = auctionDAO.getAuctionById(activeAuctionId);

                double realBalance = paymentDAO.getBalance(currentOnlineUser.getId());
                currentOnlineUser.setBalance(realBalance);

                List<BiddingHistory> historyList = auctionDAO.getBiddingHistoryByAuctionId(activeAuctionId);

                if (auction != null) {
                    this.currentAuctionPrice = auction.getCurrentPrice();
                    Item item = itemDAO.getItemById(auction.getItemId());

                    // 🎯 ĐÃ SỬA LOGIC: Tính số tiền cọc của RIÊNG Bidder này đang bị Admin giữ
                    double bidderEscrowInThisRoom = 0;

                    // Nếu người đang dẫn đầu phiên đấu giá chính là User đang đăng nhập này
                    if (auction.getCurrentWinnerId() == currentOnlineUser.getId()) {
                        bidderEscrowInThisRoom = auction.getCurrentPrice();
                        // Tiền cọc đang bị Admin đóng băng chính là mức giá cao nhất mà họ đã trả
                    } else {
                        bidderEscrowInThisRoom = 0;
                        // Nếu bị người khác đè giá, tiền đã hoàn về ví chính nên ví tạm của họ bằng 0
                    }

                    final double finalEscrow = bidderEscrowInThisRoom;

                    Platform.runLater(() -> {
                        if (lblCurrentPrice != null) lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
                        if (lblStartPrice != null) lblStartPrice.setText(String.format("%,.0f UETệ", auction.getStartingPrice()));

                        if (lblUserBalance != null) lblUserBalance.setText(String.format("%,.0f UETệ", realBalance));

                        // 🎯 HIỂN THỊ CHUẨN: Chỉ hiện số tiền cọc cá nhân của Bidder này đang nằm trong ví Admin
                        if (lblUserEscrow != null) {
                            lblUserEscrow.setText(String.format("Tiền cọc của bạn: %,.0f UETệ", finalEscrow));
                        }

                        if (item != null) {
                            if (lblProductName != null) lblProductName.setText(item.getName());
                            if (lblDescription != null) lblDescription.setText(item.getDescription());

                            loadProductProperties(item);

                            if (imgProduct != null && item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                                try {
                                    String path = item.getImgItem().trim();
                                    if (!path.startsWith("/")) path = "/" + path;
                                    InputStream is = getClass().getResourceAsStream(path);
                                    if (is != null) imgProduct.setImage(new Image(is));
                                } catch (Exception e) { e.printStackTrace(); }
                            }
                        }

                        populateBidHistory(historyList);
                        startRoomCountdown(auction.getEndTime());
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void populateBidHistory(List<BiddingHistory> historyList) {
        if (listHistoryContainer == null) return;

        listHistoryContainer.getChildren().clear();

        if (historyList == null || historyList.isEmpty()) {
            Label lblEmpty = new Label("Chưa có lượt đặt giá nào trong phòng này.");
            lblEmpty.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic; -fx-font-size: 13px;");
            listHistoryContainer.getChildren().add(lblEmpty);
            return;
        }

        historyList.sort((b1, b2) -> Double.compare(b2.getBidAmount(), b1.getBidAmount()));

        for (BiddingHistory bid : historyList) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; " +
                    "-fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");

            int bId = bid.getBidderId();
            String bidderName = "Người dùng #" + bId;

            if (bId == currentOnlineUser.getId()) {
                bidderName = "Bạn (Tôi)";
            } else {
                String sqlGetUsername = "SELECT username FROM public.users WHERE user_id = ?";
                try (java.sql.Connection conn = com.auction.server.dao.DBConnection.getConnection();
                     java.sql.PreparedStatement ps = conn.prepareStatement(sqlGetUsername)) {
                    ps.setInt(1, bId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            bidderName = rs.getString("username");
                        }
                    }
                } catch (Exception e) {
                    // Giữ nguyên fallback name nếu lỗi
                }
            }

            Label lblUser = new Label(bidderName);

            if (bId == currentOnlineUser.getId()) {
                lblUser.setStyle("-fx-text-fill: #0F172A; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
                row.setStyle("-fx-padding: 8 12; -fx-background-color: #EFF6FF; -fx-background-radius: 8; " +
                        "-fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-alignment: center-left;");
            } else {
                lblUser.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
            }

            Label lblPrice = new Label(String.format("%,.0f UETệ", bid.getBidAmount()));
            if (bid.getBidAmount() >= currentAuctionPrice) {
                lblPrice.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 13px;");
            } else {
                lblPrice.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
            }

            HBox rightContainer = new HBox();
            javafx.scene.layout.HBox.setHgrow(rightContainer, javafx.scene.layout.Priority.ALWAYS);
            rightContainer.setStyle("-fx-alignment: center-right;");
            rightContainer.getChildren().add(lblPrice);

            row.getChildren().addAll(lblUser, rightContainer);
            listHistoryContainer.getChildren().add(row);
        }
    }

    public void addSingleBidToHistory(int bidderId, String username, double bidAmount) {
        Platform.runLater(() -> {
            if (bidAmount > this.currentAuctionPrice) {
                this.currentAuctionPrice = bidAmount;
                if (lblCurrentPrice != null) {
                    lblCurrentPrice.setText(String.format("%,.0f UETệ", bidAmount));
                }
            }

            if (!listHistoryContainer.getChildren().isEmpty() && listHistoryContainer.getChildren().get(0) instanceof Label) {
                listHistoryContainer.getChildren().clear();
            }

            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8 12; -fx-background-radius: 8; -fx-border-radius: 8; -fx-alignment: center-left;");

            String displayName = (bidderId == currentOnlineUser.getId()) ? "Bạn (Tôi)" : username;
            Label lblUser = new Label(displayName);

            if (bidderId == currentOnlineUser.getId()) {
                lblUser.setStyle("-fx-text-fill: #0F172A; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
                row.setStyle("-fx-padding: 8 12; -fx-background-color: #EFF6FF; -fx-background-radius: 8; -fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-alignment: center-left;");
            } else {
                lblUser.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
                row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");
            }

            Label lblPrice = new Label(String.format("%,.0f UETệ", bidAmount));
            lblPrice.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 13px;");

            HBox rightContainer = new HBox();
            javafx.scene.layout.HBox.setHgrow(rightContainer, javafx.scene.layout.Priority.ALWAYS);
            rightContainer.setStyle("-fx-alignment: center-right;");
            rightContainer.getChildren().add(lblPrice);

            row.getChildren().addAll(lblUser, rightContainer);
            listHistoryContainer.getChildren().add(0, row);
        });
    }

    private void loadProductProperties(Item item) {
        if (vboxProperties == null || item == null) return;
        vboxProperties.getChildren().clear();

        if (item instanceof Vehicle) {
            Vehicle v = (Vehicle) item;
            addPropertyRow("Hãng sản xuất:", v.getMake());
            addPropertyRow("Dòng xe (Model):", v.getModelVehicle());
            addPropertyRow("Năm sản xuất:", v.getManufactureYear() > 0 ? String.valueOf(v.getManufactureYear()) : null);
            addPropertyRow("Số KM đã đi (ODO):", v.getMileage() > 0 ? String.format("%,d km", v.getMileage()) : "0 km");
            addPropertyRow("Loại nhiên liệu:", v.getFuelType());
            addPropertyRow("Biển số xe:", v.getLicensePlate());
        } else if (item instanceof Art) {
            Art a = (Art) item;
            addPropertyRow("Họa sĩ / Tác giả:", a.getArtist());
            addPropertyRow("Năm sáng tác:", a.getYearCreated() > 0 ? String.valueOf(a.getYearCreated()) : null);
            addPropertyRow("Chất liệu tác phẩm:", a.getMedium());
            addPropertyRow("Chứng nhận bản quyền:", a.isHasCertificate() ? "Đã cấp chứng chỉ (Xác thực)" : "Chưa xác minh");
        } else if (item instanceof Electronics) {
            Electronics e = (Electronics) item;
            addPropertyRow("Thương hiệu:", e.getBrand());
            addPropertyRow("Mã Model máy:", e.getModel());
            addPropertyRow("Thời hạn bảo hành:", e.getWarrantyMonths() > 0 ? e.getWarrantyMonths() + " tháng" : "Hết bảo hành");
            addPropertyRow("Tình trạng thiết bị:", e.getItemCondition());
        }
    }

    private void addPropertyRow(String attrName, String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("[NULL]")) return;
        HBox row = new HBox(15);
        Label lblTitle = new Label(attrName);
        lblTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 14px; -fx-min-width: 150px;");
        Label lblValue = new Label(value);
        lblValue.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 14px; -fx-font-weight: bold;");
        row.getChildren().addAll(lblTitle, lblValue);
        vboxProperties.getChildren().add(row);
    }

    private void startRoomCountdown(LocalDateTime endTime) {
        if (roomCountdownTimeline != null) roomCountdownTimeline.stop();
        if (lblCountdown == null) return;
        if (endTime == null) { lblCountdown.setText("Không giới hạn"); return; }

        roomCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(endTime)) {
                lblCountdown.setText("ĐÃ KẾT THÚC!");
                lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
                if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
                roomCountdownTimeline.stop();
                return;
            }
            long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            lblCountdown.setText(String.format("%02dh : %02dm : %02ds", hours, minutes, seconds));
        }));
        roomCountdownTimeline.setCycleCount(Animation.INDEFINITE);
        roomCountdownTimeline.play();
    }

    @FXML
    void handleBackToMarket(ActionEvent event) {
        if (roomCountdownTimeline != null) roomCountdownTimeline.stop();
        Stage stage = (Stage) lblProductName.getScene().getWindow();
        stage.close();
    }

    @FXML
    void onSubmitBid(ActionEvent event) {
        String inputStr = txtBidAmount.getText().trim();
        if (inputStr.isEmpty()) {
            showAlert("Thông báo", "Vui lòng nhập số tiền bạn muốn trả giá!", Alert.AlertType.WARNING);
            return;
        }

        try {
            double bidAmount = Double.parseDouble(inputStr);

            if (bidAmount <= currentAuctionPrice) {
                showAlert("Lỗi đặt giá", "Giá đặt phải lớn hơn giá hiện tại!", Alert.AlertType.ERROR);
                return;
            }

            Auction auction = auctionDAO.getAuctionById(activeAuctionId);
            if (auction == null) {
                showAlert("Lỗi", "Không tìm thấy thông tin phiên đấu giá này!", Alert.AlertType.ERROR);
                return;
            }

            if (!"RUNNING".equalsIgnoreCase(auction.getAuctionStatus())) {
                showAlert("Lỗi", "Phiên đấu giá này đã kết thúc hoặc chưa bắt đầu!", Alert.AlertType.ERROR);
                return;
            }

            try {
                biddingService.placeBidDirectFromDB(currentOnlineUser, auction.getAuctionId(), bidAmount);
                showAlert("Thành công", "Đặt giá thành công! Số tiền cũ của người trước đã được hoàn trả, tài khoản hệ thống Admin đã đóng băng giữ cọc phiên này.", Alert.AlertType.INFORMATION);
                txtBidAmount.clear();

            } catch (com.auction.common.exception.AuctionException ae) {
                showAlert("Thất bại", "Lỗi hệ thống: " + ae.getMessage(), Alert.AlertType.ERROR);
            } catch (Exception ex) {
                showAlert("Thất bại", "Giao dịch không thành công. Vui lòng kiểm tra lại số dư ví chính!", Alert.AlertType.ERROR);
                ex.printStackTrace();
            }

            refreshRoomData();

        } catch (NumberFormatException e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số tiền hợp lệ!", Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}