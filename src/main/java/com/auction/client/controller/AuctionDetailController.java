package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.InputStream;
import java.time.LocalDateTime;

public class AuctionDetailController {

    // 🎯 ĐỒNG BỘ 100% FX:ID VỚI FILE FXML CỦA BẠN
    @FXML private Label lblAuctionId;
    @FXML private ImageView imgProduct;
    @FXML private Label lblItemName;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblStartTime;
    @FXML private Label lblEndTime;
    @FXML private Label lblStatus;
    @FXML private Label lblDescription;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();

    // 🎯 BỔ SUNG: Đối tượng quản lý đồng hồ đếm ngược tự động đổi trạng thái realtime
    private Timeline liveStatusTimeline;

    /**
     * Hàm nhận dữ liệu đồng bộ từ Database dội sang
     */
    public void loadAuctionDetail(int auctionId, String fallbackName, User user) {
        // Khởi tạo thông tin cơ bản tạm thời trước khi luồng DB hoàn thành
        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName != null) lblItemName.setText(fallbackName);

        // Chạy Thread kết nối Database để lấy thông số thực tế tránh đơ UI chính
        new Thread(() -> {
            try {
                Auction auction = auctionDAO.getAuctionById(auctionId);
                if (auction != null) {
                    Item item = itemDAO.getItemById(auction.getItemId());

                    Platform.runLater(() -> {
                        // 1. Đổ dữ liệu thật từ bảng public.auctions
                        if (lblCurrentPrice != null) {
                            lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
                        }

                        // Đồng bộ các nhãn thời gian từ Database lên giao diện mới
                        if (lblStartTime != null && auction.getStartTime() != null) {
                            lblStartTime.setText(auction.getStartTime().toString());
                        }
                        if (lblEndTime != null && auction.getEndTime() != null) {
                            lblEndTime.setText(auction.getEndTime().toString());
                        }

                        // Cập nhật trạng thái ban đầu dựa vào Database
                        if (lblStatus != null) {
                            updateStatusStyle(auction.getAuctionStatus());
                        }

                        // 2. Đổ dữ liệu thật từ bảng public.items
                        if (item != null) {
                            if (lblItemName != null) lblItemName.setText(item.getName());
                            if (lblDescription != null) lblDescription.setText(item.getDescription());

                            // Xử lý nạp ảnh tĩnh từ đường dẫn lưu trong DB PostgreSQL
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

                        // 3. 🎯 KÍCH HOẠT BỘ THEO DÕI THỜI GIAN REALTIME
                        // Nếu phiên đang mở (hoặc chưa bị SOLD), kích hoạt bộ đếm ngược theo end_time
                        if (auction.getEndTime() != null && !"SOLD".equalsIgnoreCase(auction.getAuctionStatus())) {
                            startRealtimeStatusTracker(auction.getEndTime());
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi kết nối dữ liệu chi tiết phiên đấu giá!");
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 🎯 BỔ SUNG: Hàm cập nhật chữ và màu sắc nhãn trạng thái chuyên nghiệp
     */
    private void updateStatusStyle(String status) {
        if (lblStatus == null) return;

        if ("FINISHED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ KẾT THÚC");
            lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        } else if ("PENDING".equalsIgnoreCase(status) || "WAITING_FOR_ADMIN".equalsIgnoreCase(status)) {
            lblStatus.setText("CHỜ KÍCH HOẠT");
            lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        } else if ("SOLD".equalsIgnoreCase(status)) {
            lblStatus.setText("ĐÃ BÁN THÀNH CÔNG");
            lblStatus.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1e40af; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        } else {
            // Mặc định RUNNING
            lblStatus.setText("ĐANG DIỄN RA");
            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12 4 12; -fx-font-weight: bold;");
        }
    }

    /**
     * 🎯 BỔ SUNG: Hàm liên tục kiểm tra thời gian thực để nhảy trạng thái tự động
     */
    private void startRealtimeStatusTracker(LocalDateTime endTime) {
        if (liveStatusTimeline != null) {
            liveStatusTimeline.stop();
        }

        liveStatusTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();

            // Nếu thời gian hiện tại vượt mốc kết thúc, lập tức chốt trạng thái sang Đã kết thúc
            if (now.isAfter(endTime)) {
                updateStatusStyle("FINISHED");
                System.out.println("[MÀN HÌNH CHI TIẾT] Phiên đấu giá #" + lblAuctionId.getText() + " đã hết giờ. Tự động đổi giao diện.");
                liveStatusTimeline.stop(); // Dừng bộ kiểm tra
            }
        }));

        liveStatusTimeline.setCycleCount(Animation.INDEFINITE);
        liveStatusTimeline.play();
    }

    /**
     * 🎯 XỬ LÝ SỰ KIỆN: Đóng popup khi bấm nút ĐÓNG
     */
    @FXML
    void handleClose(ActionEvent event) {
        // Tắt luồng chạy ngầm để giải phóng RAM trước khi đóng cửa sổ
        if (liveStatusTimeline != null) {
            liveStatusTimeline.stop();
        }

        // Lấy Stage của cửa sổ hiện tại từ sự kiện Click và đóng nó lại
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}