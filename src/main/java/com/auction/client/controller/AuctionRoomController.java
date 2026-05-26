package com.auction.client.controller;

import com.auction.common.model.*;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.PaymentDAO; // 🎯 THAY THẾ: Sử dụng PaymentDAO thay thế cho UserDAO cũ để kiểm soát ví tạm giữ
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
    @FXML private Label lblUserBalance; // Label hiển thị ví chính màu xanh lá
    @FXML private Label lblUserEscrow;  // 🎯 BỔ SUNG: Ánh xạ nhãn hiển thị tiền Đang đóng băng (Nhóm bạn nhớ điền fx:id="lblUserEscrow" vào FXML nhé)
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;

    // Khung chứa danh sách lịch sử đặt giá
    @FXML private VBox listHistoryContainer;
    @FXML private VBox vboxProperties;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO(); // 🎯 THAY THẾ: Khởi tạo PaymentDAO xử lý luồng tiền bình thông nhau

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

                // 🎯 ĐỒNG BỘ VÍ TIỀN: Kéo cả hai thông số tiền khả dụng và tiền đóng băng cọc từ Postgres
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

    /**
     * Sinh giao diện danh sách lịch sử đặt giá động
     */
    private void populateBidHistory(List<BiddingHistory> historyList) {
        if (listHistoryContainer == null) return;

        listHistoryContainer.getChildren().clear(); // Xóa sạch lịch sử cũ trước khi nạp mới

        if (historyList == null || historyList.isEmpty()) {
            Label lblEmpty = new Label("Chưa có lượt đặt giá nào trong phòng này.");
            lblEmpty.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic; -fx-font-size: 13px;");
            listHistoryContainer.getChildren().add(lblEmpty);
            return;
        }

        // Duyệt qua danh sách lịch sử (hiển thị tối đa các lượt đặt gần nhất)
        for (BiddingHistory bid : historyList) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; " +
                    "-fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");

            String bidderName = "Người dùng #" + bid.getId();
            if (bid.getId() == currentOnlineUser.getId()) {
                bidderName = "Bạn (Tôi)";
            }

            Label lblUser = new Label(bidderName);
            lblUser.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");

            // Mức giá đặt
            Label lblPrice = new Label(String.format("%,.0f UETệ", bid.getBidAmount()));

            // Nếu là lượt đặt cao nhất hiện tại (lượt đầu tiên trong danh sách xếp giảm dần), cho chữ màu đỏ tươi
            if (bid.getBidAmount() >= currentAuctionPrice) {
                lblPrice.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 13px;");
            } else {
                lblPrice.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
            }

            // Đẩy giá sang bên phải dòng
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

            // 🎯 ĐỒNG BỘ LOGIC: Kiểm tra số dư ví khả dụng chuẩn xác thông qua Database trước khi gửi lệnh đi
            double actualBalanceOnDB = paymentDAO.getBalance(currentOnlineUser.getId());
            if (actualBalanceOnDB < bidAmount) {
                showAlert("Lỗi tài khoản", "Số dư ví không đủ để tham gia đấu giá!", Alert.AlertType.ERROR);
                return;
            }

            // Chạy hàm Transaction đóng băng tiền túi, ném vào quỹ ký quỹ escrow_balance
            boolean success = auctionDAO.executePlaceBidTransaction(activeAuctionId, currentOnlineUser.getId(), bidAmount);

            if (success) {
                showAlert("Thành công", "Đặt giá thành công! Số tiền đặt đã được chuyển sang trạng thái tạm đóng băng bảo lãnh.", Alert.AlertType.INFORMATION);
                txtBidAmount.clear();

                // 🎯 ÉP TẢI LẠI: Đồng bộ lập tức tiền mặt sụt giảm, tiền ví tạm nhảy lên giao diện
                refreshRoomData();
            } else {
                showAlert("Thất bại", "Đặt giá thất bại! Đã có người nhanh tay hơn trả giá cao hơn bạn.", Alert.AlertType.ERROR);
                refreshRoomData();
            }
        } catch (NumberFormatException e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số hợp lệ!", Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleBackToMarket(ActionEvent event) {
        if (roomCountdownTimeline != null) roomCountdownTimeline.stop();
        Stage stage = (Stage) lblProductName.getScene().getWindow();
        stage.close();
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