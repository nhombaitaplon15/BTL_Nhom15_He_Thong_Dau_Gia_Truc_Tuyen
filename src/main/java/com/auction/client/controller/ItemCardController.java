package com.auction.client.controller;

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
    @FXML private Label startPrice;      // Nhãn màu xám (Giá khởi điểm:)
    @FXML private Label currentPrice;     // Nhãn màu đỏ (Giá hiện tại:)

    // Nhãn chữ hiển thị thời gian còn lại
    @FXML private Label timeRemaining;

    private Item currentItem;
    private User currentUser;
    private int currentAuctionId;

    // Đối tượng Timeline quản lý luồng đếm ngược giảm dần realtime
    private Timeline countdownTimeline;
    private final AuctionDAO auctionDAO = new AuctionDAO();

    /**
     * Nhận dữ liệu từ bảng public.auctions của Database đẩy sang
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

        // Hiển thị định dạng tiền tệ
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

        // 🎯 CHẶN ĐẦU: Nếu phiên đấu giá thực chất ĐÃ HẾT HẠN từ trước khi mở trang, ẩn thẻ ngay
        if (endTime != null && LocalDateTime.now().isAfter(endTime)) {
            if (timeRemaining != null) {
                timeRemaining.setText("Thời gian còn lại: Đã kết thúc!");
                timeRemaining.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            }
            // Gọi ẩn thẻ ngay mà không thèm chạy đồng hồ Timeline phí tài nguyên
            Platform.runLater(this::removeCardFromUI);
            return;
        }

        // KÍCH HOẠT LUỒNG ĐẾM NGƯỢC GIẢM THỜI GIAN REALTIME TRÊN MÀN HÌNH (Nếu phiên còn hạn)
        startCountdown(endTime);
    }

    /**
     * Hàm tính toán và cập nhật thời gian liên tục từng giây
     */
    private void startCountdown(LocalDateTime endTime) {
        // Hủy bộ đếm cũ nếu có để tránh việc bị lặp luồng khi nạp lại trang
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        if (endTime == null || timeRemaining == null) {
            if (timeRemaining != null) timeRemaining.setText("Thời gian còn lại: --:--:--");
            return;
        }

        // Khởi tạo cơ chế Timeline lặp lại sau mỗi 1 giây
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();

            // 🎯 Kịch bản 1: Phiên đấu giá đang chạy bỗng cạn hết thời gian -> XỬ LÝ CHỐT THẮNG VÀ XÓA THẺ
            if (now.isAfter(endTime)) {
                timeRemaining.setText("Thời gian còn lại: Đã kết thúc!");
                timeRemaining.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                countdownTimeline.stop(); // Dừng luồng chạy ngầm

                Platform.runLater(() -> {
                    try {
                        // 1. Chạy ngầm xử lý chốt người thắng trong Database
                        auctionDAO.closeAuctionAndDetermineWinner(currentAuctionId);

                        // Lấy lại dữ liệu phiên đấu giá vừa chốt để kiểm tra kết quả
                        Auction completedAuction = auctionDAO.getAuctionById(currentAuctionId);

                        // 2. Bắn hộp thoại Alert thông báo kết quả trực quan
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("KẾT THÚC PHIÊN ĐẤU GIÁ");
                        alert.setHeaderText("Phiên đấu giá cho [" + currentItem.getName() + "] đã khép lại!");

                        if (completedAuction != null && completedAuction.getCurrentWinnerId() != null) {
                            int winnerId = completedAuction.getCurrentWinnerId();
                            double finalPrice = completedAuction.getCurrentPrice();

                            if (currentUser != null && winnerId == currentUser.getId()) {
                                alert.setAlertType(Alert.AlertType.INFORMATION);
                                alert.setContentText("🎉 CHÚC MỪNG BẠN! Bạn đã thắng phiên đấu giá với mức giá "
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

                        // 3. Tiến hành trục xuất tấm thẻ này ra khỏi màn hình chính
                        removeCardFromUI();

                    } catch (Exception e) {
                        System.err.println("⚠️ Lỗi khi xử lý kết thúc phiên đấu giá: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                return;
            }

            // Kịch bản 2: Phiên vẫn đang diễn ra -> Tính khoảng cách thời gian còn lại
            long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();

            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            // Định dạng chuỗi hiển thị chuẩn điện tử trực quan: HH:mm:ss
            String timeText = String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds);

            // Cập nhật thời gian trực tiếp lên màn hình
            timeRemaining.setText(timeText);
        }));

        // Thiết lập chạy lặp đi lặp lại vô hạn
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play(); // Kích hoạt chạy đồng hồ
    }

    /**
     * 🎯 TUYỆT CHIÊU ĐỘC LẬP: Hàm bóc tách tìm Node cha ngoài cùng của Card để xóa bỏ khỏi giao diện chính công phá
     */
    private void removeCardFromUI() {
        try {
            Node cardContainer = null;
            Node currentNode = timeRemaining;

            // Vòng lặp tìm ngược lên cho tới khi gặp Node cha bọc ngoài cùng (Thường là AnchorPane/VBox con của FlowPane)
            while (currentNode != null) {
                Parent parent = currentNode.getParent();
                if (parent instanceof Pane && !(parent.getClass().getName().contains("Card") || (parent.getId() != null && parent.getId().contains("card")))) {
                    cardContainer = currentNode;
                    break;
                }
                currentNode = parent;
            }

            // Nếu thuật toán dò tìm không khớp cấu trúc phức tạp, fallback về 2 tầng cơ bản
            if (cardContainer == null && timeRemaining.getParent() != null) {
                cardContainer = timeRemaining.getParent().getParent();
            }

            // Đuổi cổ Node ra khỏi danh sách hiển thị FlowPane/GridPane của Trang chủ chính thức
            if (cardContainer != null && cardContainer.getParent() instanceof Pane) {
                Pane marketGrid = (Pane) cardContainer.getParent();
                marketGrid.getChildren().remove(cardContainer);
                System.out.println("[GIAO DIỆN REALTIME] Đã quét và giải phóng xong 1 thẻ sản phẩm hết hạn.");
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi cấu trúc Layout FXML không thể tự động hạ thẻ: " + e.getMessage());
        }
    }

    /**
     * Hàm giải phóng bộ nhớ RAM khi User chuyển tab hoặc Đăng xuất
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
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Sàn Đấu Giá Trực Chiến Live - Phiên #" + currentAuctionId);
            stage.setResizable(true);
            stage.setMaximized(true); // Ép phòng live tự động PHÓNG TO TOÀN MÀN HÌNH 100%
            stage.show();

        } catch (Exception e) {
            System.err.println("❌ Lỗi: Không thể khởi chạy giao diện phòng đấu giá!");
            e.printStackTrace();
        }
    }
}