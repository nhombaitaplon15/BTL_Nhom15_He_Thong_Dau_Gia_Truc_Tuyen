package com.auction.client.controller;

import com.auction.common.model.*;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.service.BiddingService;
import com.auction.exception.AuctionException;
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

    @FXML private Label lblProductName;
    @FXML private ImageView imgProduct;
    @FXML private Label lblDescription;
    @FXML private Label lblCountdown;
    @FXML private Label lblUserBalance; // Hiển thị ví chính khả dụng
    @FXML private Label lblUserEscrow;  // Hiển thị tiền tạm giữ ký quỹ
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;

    // Khung chứa danh sách lịch sử đặt giá
    @FXML private VBox listHistoryContainer;
    @FXML private VBox vboxProperties;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();

    // Kết nối đến BiddingService, truyền null vì chạy trực tiếp qua Database luồng Client
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

                // Kéo thông số tiền khả dụng và tiền đóng băng cọc từ Postgres lên RAM
                double realBalance = paymentDAO.getBalance(currentOnlineUser.getId());
                double realEscrow = paymentDAO.getEscrowBalance(currentOnlineUser.getId());
                currentOnlineUser.setBalance(realBalance);

                // Lấy danh sách lịch sử đặt giá từ Database lên
                List<BiddingHistory> historyList = auctionDAO.getBiddingHistoryByAuctionId(activeAuctionId);

                if (auction != null) {
                    this.currentAuctionPrice = auction.getCurrentPrice();
                    Item item = itemDAO.getItemById(auction.getItemId());

                    Platform.runLater(() -> {
                        if (lblCurrentPrice != null) lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
                        if (lblStartPrice != null) lblStartPrice.setText(String.format("%,.0f UETệ", auction.getStartingPrice()));

                        // Đổ dữ liệu tiền mặt phân tách dấu phẩy lên giao diện phòng Live
                        if (lblUserBalance != null) lblUserBalance.setText(String.format("%,.0f UETệ", realBalance));
                        if (lblUserEscrow != null) lblUserEscrow.setText(String.format("%,.0f UETệ", realEscrow));

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

                        // Đổ dữ liệu lịch sử đặt giá lên giao diện VBox
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

        // Khởi tạo nhanh một đối tượng UserDAO hoặc tận dụng hàm chạy SQL trực tiếp tại đây để lấy tên
        com.auction.server.dao.AuctionDAO tempDao = new com.auction.server.dao.AuctionDAO();

        for (BiddingHistory bid : historyList) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; " +
                    "-fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");

            // 🎯 CÁCH ĐI TẮT: Sử dụng luôn bid.getBidderId() vốn đã có sẵn trong Model của bạn!
            int bId = bid.getBidderId();
            String bidderName = "Người dùng #" + bId;

            if (bId == currentOnlineUser.getId()) {
                bidderName = "Bạn (Tôi)";
            } else {
                // Thực hiện một câu truy vấn siêu tốc để lấy Username từ user_id trực tiếp, không cần sửa Model!
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
                    // Nếu lỗi thì giữ nguyên "Người dùng #ID", không lo bị crash ứng dụng
                }
            }

            Label lblUser = new Label(bidderName);
            lblUser.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");

            // Highlight nếu là lượt của bạn
            if (bId == currentOnlineUser.getId()) {
                lblUser.setStyle("-fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
                row.setStyle("-fx-padding: 8 12; -fx-background-color: #f8fafc; -fx-background-radius: 8; " +
                        "-fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-alignment: center-left;");
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

    /**
     * 🎯 GIỮ NGUYÊN TÊN HÀM VÀ NÚT BẤM CŨ
     */


    @FXML
    void handleBackToMarket(ActionEvent event) {
        if (roomCountdownTimeline != null) roomCountdownTimeline.stop();
        Stage stage = (Stage) lblProductName.getScene().getWindow();
        stage.close();
    }
    // Khai báo BiddingService ở đầu Class AuctionRoomController nếu chưa có:
    // private final com.auction.service.BiddingService biddingService = new com.auction.service.BiddingService(managerService);
    // Hoặc nếu gọi thẳng qua DB (Direct) thì khởi tạo mặc định:


    @FXML
    void onSubmitBid(ActionEvent event) {
        String inputStr = txtBidAmount.getText().trim();
        if (inputStr.isEmpty()) {
            showAlert("Thông báo", "Vui lòng nhập số tiền bạn muốn trả giá!", Alert.AlertType.WARNING);
            return;
        }

        try {
            double bidAmount = Double.parseDouble(inputStr);

            // 1. Kiểm tra nhanh ở Client để đỡ mất công gọi xuống Service
            if (bidAmount <= currentAuctionPrice) {
                showAlert("Lỗi đặt giá", "Giá đặt phải lớn hơn giá hiện tại!", Alert.AlertType.ERROR);
                return;
            }

            // 2. Lấy thông tin phiên đấu giá hiện tại từ Database
            Auction auction = auctionDAO.getAuctionById(activeAuctionId);
            if (auction == null) {
                showAlert("Lỗi", "Không tìm thấy thông tin phiên đấu giá này!", Alert.AlertType.ERROR);
                return;
            }

            // Kiểm tra trạng thái phiên đấu giá trước khi truyền đi
            if (!"RUNNING".equalsIgnoreCase(auction.getAuctionStatus())) {
                showAlert("Lỗi", "Phiên đấu giá này đã kết thúc hoặc chưa bắt đầu!", Alert.AlertType.ERROR);
                return;
            }

            // 3. ỦY QUYỀN TOÀN BỘ LOGIC CHO VŨ KHÍ BÍ MẬT "BiddingService" XỬ LÝ
            try {
                // Sử dụng hàm đặt giá trực tiếp từ DB kết hợp Transaction nguyên tử của bạn
                biddingService.placeBidDirectFromDB(currentOnlineUser, auction.getAuctionId(), bidAmount);

                // Nếu chạy đến đây không văng Exception -> Đặt giá thành công!
                showAlert("Thành công", "Đặt giá thành công! Số tiền cũ của người trước đã được hoàn trả, tài khoản của bạn đã được đóng băng giữ cọc.", Alert.AlertType.INFORMATION);
                txtBidAmount.clear();

            } catch (com.auction.exception.AuctionException ae) {
                // Bắt các lỗi nghiệp vụ được định nghĩa từ ErrorCode của bạn (Ví dụ: BID_TOO_LOW, INTERNAL_ERROR)
                showAlert("Thất bại", "Lỗi hệ thống: " + ae.getMessage(), Alert.AlertType.ERROR);
            } catch (Exception ex) {
                showAlert("Thất bại", "Giao dịch không thành công. Vui lòng kiểm tra lại số dư ví chính!", Alert.AlertType.ERROR);
                ex.printStackTrace();
            }

            // 4. Đồng bộ làm mới số dư hiển thị và bảng lịch sử phòng đấu giá ngay tức thì
            refreshRoomData();

        } catch (NumberFormatException e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số tiền hợp lệ!", Alert.AlertType.ERROR);
        }
    }
    /**
     * 🎯 SỬA LỖI GẠCH ĐỎ: Thêm cặp ngoặc đóng mở () cho hàm showAndWait() chuẩn JavaFX
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait(); // 🎯 ĐÃ THÊM () Ở ĐÂY ĐỂ SỬA LỖI BIÊN DỊCH
        });
    }
}