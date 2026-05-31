package com.auction.client.controller.bidder;



import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.*;
import com.auction.common.network.BidPlaceDTO;
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
import java.util.List;

public class AuctionRoomController {

    // 🎯 Mảng 4 ID Admin quản lý hệ thống ví tạm để Client map thông tin hiển thị (nếu cần thông báo)
    private static final int[] ESCROW_ADMIN_IDS = {1, 2, 3, 4};

    @FXML private Button btnBack;
    @FXML private Label lblProductName;
    @FXML private ImageView imgProduct;
    @FXML private VBox vboxProperties;
    @FXML private Label lblDescription;
    @FXML private Label lblCountdown;
    @FXML private Label lblUserBalance;
    @FXML private Label lblUserEscrow;   // Nhãn hiển thị số tiền đang bị đóng băng ở ví Admin
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private VBox listHistoryContainer;

    private Auction currentAuction;
    private User currentUser;

    @FXML
    public void initialize() {
        System.out.println("⚡ Đã kết nối vào phòng đấu giá trực tuyến.");

        // Đăng ký nhận phản hồi đồng bộ phòng từ Event Bus
        MessageRouter.getInstance().register(ResponseCode.ROOM_JOIN_SUCCESS, this::handleRoomJoinSuccess);
        MessageRouter.getInstance().register(ResponseCode.BID_SUCCESS, this::handleBidSuccess);
    }

    /**
     * Đồng bộ dữ liệu phòng khi bước chân vào phòng đấu giá
     */
    private void handleRoomJoinSuccess(Message message) {
        if (!(message.getPayload() instanceof Auction)) return;
        this.currentAuction = (Auction) message.getPayload();
        Item item = currentAuction.getItem();

        // LOGIC DÒNG TIỀN: Tính toán số tiền cọc đang bị giam giữ tại ví tạm Admin của phiên này
        double bidderEscrowInThisRoom = 0;
        if (currentUser != null && currentAuction.getCurrentWinnerId() != null
                && currentAuction.getCurrentWinnerId().intValue() == currentUser.getId()) {
            // Nếu Bidder này đang dẫn đầu, số tiền trúng hiện tại chính là số tiền đang nằm trong ví tạm Admin
            bidderEscrowInThisRoom = currentAuction.getCurrentPrice();
        }
        final double finalEscrow = bidderEscrowInThisRoom;

        Platform.runLater(() -> {
            if (lblCurrentPrice != null) lblCurrentPrice.setText(String.format("%,.0f UETệ", currentAuction.getCurrentPrice()));
            if (lblStartPrice != null) lblStartPrice.setText(String.format("%,.0f UETệ", currentAuction.getStartingPrice()));
            if (lblUserBalance != null && currentUser != null) lblUserBalance.setText(String.format("%,.0f UETệ", currentUser.getBalance()));

            // Đổ số tiền đang bị giam giữ tại ví Admin lên nhãn giao diện hiển thị cho người dùng
            if (lblUserEscrow != null) {
                lblUserEscrow.setText(String.format("%,.0f UETệ", finalEscrow));
            }

            if (item != null) {
                if (lblProductName != null) lblProductName.setText(item.getName());
                if (lblDescription != null) lblDescription.setText(item.getDescription());

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
        });
    }

    /**
     * XỬ LÝ SỰ KIỆN: BẤM NÚT XÁC NHẬN ĐẶT GIÁ
     */
    @FXML
    void onSubmitBid(ActionEvent event) {
        if (currentAuction == null || currentUser == null) return;

        String amountText = txtBidAmount.getText().trim();
        if (amountText.isEmpty()) {
            showAlert("Lỗi", "Vui lòng nhập số tiền muốn đặt!", Alert.AlertType.ERROR);
            return;
        }

        try {
            double bidAmount = Double.parseDouble(amountText);

            // Kiểm tra luật đặt giá cơ bản dưới Client trước khi đẩy lệnh lên mạng
            if (bidAmount <= currentAuction.getCurrentPrice()) {
                showAlert("Lỗi đặt giá", "Giá đặt mới phải cao hơn Giá hiện tại của phòng!", Alert.AlertType.ERROR);
                return;
            }

            if (bidAmount > currentUser.getBalance()) {
                showAlert("Lỗi số dư", "Ví chính của bạn không đủ tiền để thực hiện đặt giá cọc này!", Alert.AlertType.ERROR);
                return;
            }

            // Tính toán trước xem nếu lệnh này thành công thì tiền sẽ chạy vào tài khoản Admin ID nào
            int idx = currentAuction.getAuctionId() % ESCROW_ADMIN_IDS.length;
            int targetAdminId = ESCROW_ADMIN_IDS[idx];

            System.out.println("💸 Gửi lệnh đặt giá. Nếu thành công tiền cọc sẽ chạy vào ví tạm của Admin ID: " + targetAdminId);

            // Đóng gói dữ liệu {auctionId, bidAmount} đẩy lên Server xử lý Database
            Object[] bidPayload = new Object[] { currentAuction.getAuctionId(), bidAmount };
            SocketClient.getInstance().sendRequest(RequestCode.PLACE_BID, bidPayload);

        } catch (NumberFormatException e) {
            showAlert("Lỗi", "Mức giá nhập vào phải là một số hợp lệ!", Alert.AlertType.ERROR);
        }
    }

    private void handleBidSuccess(Message message) {
        Platform.runLater(() -> {
            showAlert("Thành công", "Bạn đã đặt giá thành công! Tiền cọc đã được chuyển sang ví đóng băng hệ thống.", Alert.AlertType.INFORMATION);
            if (txtBidAmount != null) txtBidAmount.clear();
            // Lệnh lấy lại profile mới để cập nhật đồng bộ lại lblUserBalance khả dụng sau khi bị trừ cọc
            SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
        });
    }

    @FXML
    void handleBackToMarket(ActionEvent event) {
        // Hủy đăng ký Event Bus để giải phóng bộ nhớ trước khi thoát phòng
        MessageRouter.getInstance().unregister(ResponseCode.ROOM_JOIN_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.BID_SUCCESS);

        // Logic điều hướng quay lại màn hình chính của bạn...
    }
    public void loadAuctionDetail(int auctionId, String itemName, User user) {
        // Việc 1: Ghi nhận "Tôi là ai" (Lưu thông tin người dùng đang xem phòng để tí nữa còn biết ai bấm đặt giá)
        this.currentUser = user;

        // Việc 2: Đổ dữ liệu tĩnh lên màn hình ngay lập tức cho người dùng nhìn thấy đỡ sốt ruột
        Platform.runLater(() -> {
            lblProductName.setText(itemName); // Hiện tên vật phẩm lên góc trên
            lblUserBalance.setText(String.format("%,.0f UETệ", user.getBalance())); // Hiện số tiền trong ví của bạn
        });

        // Việc 3: Bắn một tín hiệu qua Socket lên Server: "Ê Server, user này vừa vào phòng số X này, gửi dữ liệu lịch sử đặt giá và thời gian đếm ngược thật về đây đi!"
        SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, auctionId);
    }
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

}
