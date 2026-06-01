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
import java.time.temporal.ChronoUnit;
import java.util.List;

public class AuctionRoomController {

    // 🎯 Đồng bộ mảng 4 ID Admin hệ thống quản lý ví tạm giữ
    private static final int[] ESCROW_ADMIN_IDS = {1, 2, 3, 4};

    @FXML private Button btnBack;
    @FXML private Label lblProductName;
    @FXML private ImageView imgProduct;
    @FXML private VBox vboxProperties; // Vùng hiển thị thông số chi tiết (Xe, Nghệ thuật, Điện tử)
    @FXML private Label lblDescription;
    @FXML private Label lblCountdown;
    @FXML private Label lblUserBalance;
    @FXML private Label lblUserEscrow;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private VBox listHistoryContainer; // Vùng chứa danh sách lịch sử đấu giá công khai

    private Auction currentAuction;
    private User currentUser;
    private Timeline countdownTimeline; // Luồng đếm ngược Real-time

    @FXML
    public void initialize() {
        System.out.println("⚡ Đã kết nối vào phòng đấu giá trực tuyến (Real-time Socket Mode).");

        // Đăng ký nhận phản hồi xử lý mạng từ Event Bus
        MessageRouter.getInstance().register(ResponseCode.ROOM_JOIN_SUCCESS, this::handleRoomJoinSuccess);
        MessageRouter.getInstance().register(ResponseCode.BID_SUCCESS, this::handleBidSuccess);
    }

    /**
     * Đồng bộ dữ liệu phòng đấu giá Real-time khi nhận gói tin từ Server
     */
    private void handleRoomJoinSuccess(Message message) {
        if (!(message.getPayload() instanceof Auction)) return;
        this.currentAuction = (Auction) message.getPayload();
        Item item = currentAuction.getItem();

        // ⏱️ Khởi chạy đồng hồ đếm ngược dựa trên thời gian kết thúc thực tế
        startCountdownTimer(currentAuction.getEndTime());

        // 💸 LOGIC DÒNG TIỀN: Tính toán số tiền cọc của RIÊNG Bidder này đang bị đóng băng
        double bidderEscrowInThisRoom = 0;
        if (currentUser != null && currentAuction.getCurrentWinnerId() != null
                && currentAuction.getCurrentWinnerId().intValue() == currentUser.getId()) {
            // Nếu tôi đang dẫn đầu, số tiền tôi trả giá chính là số tiền đang nằm trong ví tạm hệ thống
            bidderEscrowInThisRoom = currentAuction.getCurrentPrice();
        }
        final double finalEscrow = bidderEscrowInThisRoom;

        Platform.runLater(() -> {
            if (lblCurrentPrice != null) lblCurrentPrice.setText(String.format("%,.0f UETệ", currentAuction.getCurrentPrice()));
            if (lblStartPrice != null) lblStartPrice.setText(String.format("%,.0f UETệ", currentAuction.getStartingPrice()));
            if (lblUserBalance != null && currentUser != null) lblUserBalance.setText(String.format("%,.0f UETệ", currentUser.getBalance()));

            // Hiển thị chuẩn hóa số tiền cọc cá nhân
            if (lblUserEscrow != null) {
                lblUserEscrow.setText(String.format("Tiền cọc của bạn: %,.0f UETệ", finalEscrow));
            }

            if (item != null) {
                if (lblProductName != null) lblProductName.setText(item.getName());
                if (lblDescription != null) lblDescription.setText(item.getDescription());

                // 🚗🎨⚡ Đồng bộ tính năng phân loại hiển thị thông số chi tiết từ file 1
                loadProductProperties(item);

                // Nạp ảnh vật phẩm an toàn
                if (imgProduct != null && item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                    try {
                        String path = item.getImgItem().trim();
                        if (!path.startsWith("/")) path = "/" + path;
                        InputStream is = getClass().getResourceAsStream(path);
                        if (is != null) imgProduct.setImage(new Image(is));
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }

            // 📜 Vẽ danh sách lịch sử đấu giá Real-time lên giao diện
            populateBidHistory();
        });
    }

    /**
     * ĐỒNG BỘ LOGIC FILE 1: Phân tách và hiển thị thông số động theo kiểu thực thể
     */
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

    /**
     * ĐỒNG BỘ LOGIC FILE 1: Kết xuất và hiển thị danh sách lịch sử đấu giá động
     */
    private void populateBidHistory() {
        if (listHistoryContainer == null || currentAuction == null) return;
        listHistoryContainer.getChildren().clear();

        // 1. SỬA ĐỒNG BỘ: Ép kiểu danh sách lịch sử về đúng class BiddingHistory của bạn
        // (Giả định Server gửi danh sách qua một phương thức trong currentAuction, bạn hãy kiểm tra xem
        // currentAuction.getBids() trả về List<BiddingHistory> chưa, nếu chưa hãy đổi tên hàm getter cho đúng)
        List<BiddingHistory> historyList = currentAuction.getBids();

        if (historyList == null || historyList.isEmpty()) {
            Label lblEmpty = new Label("Chưa có lượt đặt giá nào trong phòng này.");
            lblEmpty.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic; -fx-font-size: 13px;");
            listHistoryContainer.getChildren().add(lblEmpty);
            return;
        }

        // 2. Sắp xếp lượt đặt giá cao nhất lên đầu danh sách hiển thị
        historyList.sort((b1, b2) -> Double.compare(b2.getBidAmount(), b1.getBidAmount()));

        for (BiddingHistory bid : historyList) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; " +
                    "-fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");

            int bId = bid.getBidderId();

            // Vì BiddingHistory chỉ trả về ID, ta sẽ dùng chuỗi hiển thị mặc định là "Người dùng #ID"
            String bidderName = "Người dùng #" + bId;

            // Nếu ID này trùng với người dùng đang đăng nhập hiện tại
            if (currentUser != null && bId == currentUser.getId()) {
                bidderName = "Bạn (Tôi)";
                row.setStyle("-fx-padding: 8 12; -fx-background-color: #EFF6FF; -fx-background-radius: 8; " +
                        "-fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-alignment: center-left;");
            }

            Label lblUser = new Label(bidderName);
            if (currentUser != null && bId == currentUser.getId()) {
                lblUser.setStyle("-fx-text-fill: #0F172A; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
            } else {
                lblUser.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
            }

            // 3. Đổi hàm gọi từ bid.getBidAmount() -> Chuẩn theo model của bạn
            Label lblPrice = new Label(String.format("%,.0f UETệ", bid.getBidAmount()));
            if (bid.getBidAmount() >= currentAuction.getCurrentPrice()) {
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
    /**
     * bộ đếm ngược thời gian kết thúc Real-time qua Timeline JavaFX
     */
    private void startCountdownTimer(LocalDateTime endTime) {
        if (countdownTimeline != null) countdownTimeline.stop();
        if (lblCountdown == null) return;
        if (endTime == null) { lblCountdown.setText("Không giới hạn"); return; }

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
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            lblCountdown.setText(String.format("%02dh : %02dm : %02ds", hours, minutes, seconds));
        }));
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    /**
     * XỬ LÝ SỰ KIỆN: Gửi lệnh đặt giá cọc lên Server qua TCP Socket
     */
    @FXML
    void onSubmitBid(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        String amountText = txtBidAmount.getText().trim();
        if (amountText.isEmpty()) {
            showAlert("Thông báo", "Vui lòng nhập số tiền bạn muốn trả giá!", Alert.AlertType.WARNING);
            return;
        }

        try {
            double bidAmount = Double.parseDouble(amountText);

            if (bidAmount <= currentAuction.getCurrentPrice()) {
                showAlert("Lỗi đặt giá", "Giá đặt mới phải cao hơn Giá hiện tại của phòng!", Alert.AlertType.ERROR);
                return;
            }

            if (bidAmount > currentUser.getBalance()) {
                showAlert("Lỗi số dư", "Ví chính của bạn không đủ tiền để thực hiện đặt giá cọc này!", Alert.AlertType.ERROR);
                return;
            }

            // Gửi yêu cầu đặt giá an toàn qua mạng
            int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
            int targetAdminId = ESCROW_ADMIN_IDS[idx];
            System.out.println("💸 [Socket] Gửi lệnh đặt giá cọc. Hệ thống sẽ tạm đóng băng tài khoản chuyển vào ví Admin ID: " + targetAdminId);

            Object[] bidPayload = new Object[] { currentAuction.getAuctionId(), bidAmount };
            SocketClient.getInstance().sendRequest(RequestCode.PLACE_BID, bidPayload);

        } catch (NumberFormatException e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số tiền hợp lệ!", Alert.AlertType.ERROR);
        }
    }

    /**
     * Nhận phản hồi đặt giá thành công từ Server, tự động kích hoạt đồng bộ dữ liệu ví
     */
    private void handleBidSuccess(Message message) {
        Platform.runLater(() -> {
            showAlert("Thành công", "Đặt giá thành công! Số tiền cũ của người trước đã được hoàn trả, hệ thống Admin đã đóng băng giữ cọc phiên này.", Alert.AlertType.INFORMATION);
            if (txtBidAmount != null) txtBidAmount.clear();

            // Re-join phòng để Server ép đẩy cục Auction mới có danh sách lịch sử đặt giá cập nhật về lại Event Bus
            SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, currentAuction.getAuctionId());
            // Yêu cầu lấy lại Profile mới nhất để đồng bộ tiền khả dụng trong ví chính (lblUserBalance)
            SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
        });
    }

    @FXML
    void handleBackToMarket(ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();

        // Hủy đăng ký lắng nghe phòng từ Event Bus để tránh rò rỉ bộ nhớ mạng
        MessageRouter.getInstance().unregister(ResponseCode.ROOM_JOIN_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.BID_SUCCESS);

        Stage stage = (Stage) lblProductName.getScene().getWindow();
        stage.close();
    }

    public void loadAuctionDetail(int auctionId, String itemName, User user) {
        this.currentUser = user;

        Platform.runLater(() -> {
            if (lblProductName != null) lblProductName.setText(itemName);
            if (lblUserBalance != null) lblUserBalance.setText(String.format("%,.0f UETệ", user.getBalance()));
        });

        // Bắn tín hiệu gia nhập phòng qua Socket mạng
        SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, auctionId);
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