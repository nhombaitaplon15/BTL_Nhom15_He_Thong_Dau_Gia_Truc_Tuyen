package com.auction.client.controller;

import com.auction.common.model.Item;
import com.auction.common.model.User;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.InputStream;
import java.time.LocalDateTime;

public class ItemCardController {
    @FXML private ImageView imgItem;
    @FXML private Label name;
    @FXML private Label startPrice;      // Nhãn màu xám (Giá khởi điểm:)
    @FXML private Label currentPrice;     // Nhãn màu đỏ (Giá hiện tại:)

    // 🎯 BỔ SUNG: Khai báo fx:id cho nhãn chữ xanh hiển thị thời gian còn lại
    @FXML private Label timeRemaining;   // Hãy đảm bảo fx:id trong file FXML card của bạn trùng tên này

    private Item currentItem;
    private User currentUser;
    private int currentAuctionId;

    // 🎯 BỔ SUNG: Đối tượng Timeline quản lý luồng đếm ngược giảm dần realtime
    private Timeline countdownTimeline;

    /**
     * 🎯 CẬP NHẬT: Nhận thêm biến LocalDateTime endTime từ bảng public.auctions của Database đẩy sang
     */
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

        // Sửa lỗi hiển thị đè chữ và nối tiền tệ
        if (startPrice != null) {
            startPrice.setText(String.format("Giá khởi điểm: %,.0f UETệ", item.getStartingPrice()));
        }
        if (currentPrice != null) {
            currentPrice.setText(String.format("Giá hiện tại: %,.0f UETệ", currentPriceVal));
        }

        // Nạp ảnh sản phẩm an toàn
        try {
            if (item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                String imagePath = item.getImgItem().trim();
                if (!imagePath.startsWith("/")) imagePath = "/" + imagePath;
                InputStream is = getClass().getResourceAsStream(imagePath);
                if (is != null) imgItem.setImage(new Image(is));
            }
        } catch (Exception e) {
            System.out.println("⚠️ Không thể tải ảnh cho sản phẩm: " + item.getName());
        }

        // 🎯 BỔ SUNG: KÍCH HOẠT LUỒNG ĐẾM NGƯỢC GIẢM THỜI GIAN REALTIME TRÊN MÀN HÌNH
        startCountdown(endTime);
    }

    /**
     * 🎯 BỔ SUNG: Hàm tính toán và cập nhật thời gian liên tục từng giây
     */
    private void startCountdown(LocalDateTime endTime) {
        // Hủy bộ đếm cũ nếu có để tránh việc bị lặp luồng, nhảy giây loạn xạ khi làm mới trang
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        if (endTime == null || timeRemaining == null) {
            if (timeRemaining != null) timeRemaining.setText("Thời gian còn lại: --:--:--");
            return;
        }

        // Khởi tạo cơ chế Timeline lặp lại sau mỗi 1 giây (1 Second)
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();

            // Kịch bản 1: Phiên đấu giá đã cạn hết thời gian
            if (now.isAfter(endTime)) {
                timeRemaining.setText("Thời gian còn lại: Đã kết thúc!");
                timeRemaining.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;"); // Biến chữ thành màu đỏ báo dừng phiên
                countdownTimeline.stop(); // Dừng luồng ngầm chạy
                return;
            }

            // Kịch bản 2: Phiên vẫn đang diễn ra -> Tính khoảng cách thời gian còn lại
            long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();

            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            // Định dạng chuỗi hiển thị chuẩn điện tử trực quan: HH:mm:ss
            String timeText = String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds);

            // Ép chữ thay đổi giảm dần trực tiếp lên giao diện màn hình
            timeRemaining.setText(timeText);
        }));

        // Thiết lập chạy lặp đi lặp lại vô hạn cho tới khi chủ động stop hoặc hết giờ
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play(); // Kích hoạt chạy đồng hồ
    }

    /**
     * 🎯 BỔ SUNG: Hàm dọn dẹp để trang chủ gọi giải phóng bộ nhớ RAM khi User chuyển tab hoặc Đăng xuất
     */
    public void stopTimer() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
    }

    /**
     * Sự kiện khi nhấn nút "Đặt Giá Ngay" trên Card
     */
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionRoomView.fxml"));
            Parent root = loader.load();

            AuctionRoomController roomController = loader.getController();
            if (roomController != null) {
                roomController.loadAuctionDetail(currentAuctionId, currentItem.getName(), currentUser);
            }

            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1000, 788));
            stage.setTitle("Sàn Đấu Giá Trực Chiến Live - Phiên #" + currentAuctionId);
            stage.setResizable(false);
            stage.show();

        } catch (Exception e) {
            System.err.println("❌ Lỗi: Không thể khởi chạy giao diện phòng đấu giá!");
            e.printStackTrace();
        }
    }
}