package com.auction.client.controller.seller; // [SỬA] client.controller.seller -> com.auction.client.controller.seller

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.network.CreateAuctionDTO;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller trang chủ Seller - PHIÊN BẢN ĐẦY ĐỦ CÓ NETWORKING.
 *
 * CÁC LỖI ĐÃ SỬA SO VỚI FILE GỐC:
 * 1. [SỬA] Package: client.controller.seller -> com.auction.client.controller.seller
 * 2. [XÓA] Welcome_back() chuyển về WelcomeView không tồn tại -> thay bằng logout thật
 * 3. [THÊM] setUserData() - nhận User từ LoginController qua SocketClient
 * 4. [THÊM] Tải danh sách phiên đấu giá của Seller qua socket (SELLER_GET_MY_AUCTIONS)
 * 5. [THÊM] Form tạo phiên đấu giá gửi qua socket (SELLER_CREATE_AUCTION)
 * 6. [THÊM] Realtime: nhận push SELLER_AUCTION_APPROVED / REJECTED / SOLD từ Admin
 * 7. [THÊM] Hủy phiên qua socket (SELLER_CANCEL_AUCTION)
 * 8. [THÊM] Xác nhận bán qua socket (SELLER_CONFIRM_SALE)
 *
 * ĐẶT TẠI: client/src/main/java/com/auction/client/controller/seller/The_Home_Page_Seller_View_Controller.java
 */
public class The_Home_Page_Seller_View_Controller {

    // ===== FXML BINDINGS =====
    @FXML private Label lblSellerName;
    @FXML private Label lblSellerBalance;
    @FXML private Label lblStatusMessage; // Label hiển thị thông báo realtime

    // --- Bảng danh sách phiên đấu giá của seller ---
    @FXML private TableView<Auction> tblMyAuctions;
    @FXML private TableColumn<Auction, Integer> colAuctionId;
    @FXML private TableColumn<Auction, Integer> colItemId;
    @FXML private TableColumn<Auction, Double>  colCurrentPrice;
    @FXML private TableColumn<Auction, String>  colStatus;
    @FXML private TableColumn<Auction, String>  colEndTime;

    // --- Form tạo phiên mới ---
    @FXML private ComboBox<Item>   cmbMyItems;      // Chọn item để đấu giá
    @FXML private TextField        txtStartPrice;   // Giá khởi điểm
    @FXML private DatePicker       dpStartDate;     // Ngày bắt đầu
    @FXML private DatePicker       dpEndDate;       // Ngày kết thúc
    @FXML private TextField        txtStartHour;    // Giờ bắt đầu (HH:mm)
    @FXML private TextField        txtEndHour;      // Giờ kết thúc (HH:mm)

    // ===== STATE =====
    private User currentUser;
    private final ObservableList<Auction> myAuctions = FXCollections.observableArrayList();

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @FXML
    public void initialize() {
        setupAuctionTable();
        registerRealtimeHandlers();
        System.out.println("[SELLER] Trang chủ Seller đã khởi tạo.");
    }

    /**
     * Được gọi bởi LoginController sau khi đăng nhập thành công.
     */
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        Platform.runLater(() -> {
            if (lblSellerName != null)
                lblSellerName.setText("Xin chào, " + user.getUsername());
            if (lblSellerBalance != null)
                lblSellerBalance.setText(String.format("Số dư: %,.0f UETệ", user.getBalance()));
        });

        // Tải dữ liệu ngay sau khi gán user
        loadMyAuctions();
        loadMyItems();
    }

    // =========================================================
    // SETUP
    // =========================================================

    private void setupAuctionTable() {
        if (tblMyAuctions == null) return;

        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemId.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("auctionStatus"));

        // Format endTime thành chuỗi dd/MM HH:mm
        colEndTime.setCellFactory(col -> new TableCell<>() {
            private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    Auction a = (Auction) getTableRow().getItem();
                    setText(a.getEndTime() != null ? a.getEndTime().format(fmt) : "-");
                }
            }
        });

        // Color-code theo trạng thái
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                switch (status) {
                    case "WAITING_FOR_ADMIN" -> setStyle("-fx-text-fill: #FF8800; -fx-font-weight: bold;");
                    case "OPEN", "RUNNING"   -> setStyle("-fx-text-fill: #05CD99; -fx-font-weight: bold;");
                    case "CLOSED", "FINISHED"-> setStyle("-fx-text-fill: #4318FF; -fx-font-weight: bold;");
                    case "SOLD"              -> setStyle("-fx-text-fill: #05CD99;");
                    case "REJECTED"          -> setStyle("-fx-text-fill: #FF5B5C;");
                    default                  -> setStyle("");
                }
            }
        });

        tblMyAuctions.setItems(myAuctions);
    }

    /**
     * Đăng ký các handler realtime từ server (push về Seller).
     */
    private void registerRealtimeHandlers() {
        // Nhận danh sách phiên của seller
        MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTIONS_RESULT, this::onAuctionsReceived);

        // Nhận danh sách item của seller (để điền vào ComboBox tạo phiên)
        MessageRouter.getInstance().register(ResponseCode.SELLER_ITEMS_RESULT, this::onItemsReceived);

        // Push realtime: phiên được duyệt
        MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTION_APPROVED, this::onAuctionApproved);

        // Push realtime: phiên bị từ chối
        MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTION_REJECTED, this::onAuctionRejected);

        // Push realtime: phiên kết thúc và có người thắng
        MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTION_SOLD, this::onAuctionSold);

        // Tạo phiên thành công
        MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTION_CREATED, this::onAuctionCreated);

        // Tạo phiên thất bại
        MessageRouter.getInstance().register(ResponseCode.SELLER_AUCTION_CREATE_FAILED, this::onAuctionCreateFailed);

        // Hủy phiên
        MessageRouter.getInstance().register(ResponseCode.SELLER_CANCEL_SUCCESS, msg ->
                showStatus("✅ Yêu cầu hủy phiên đã được gửi lên Admin.", false));

        // Xác nhận bán
        MessageRouter.getInstance().register(ResponseCode.SELLER_CONFIRM_SALE_SUCCESS, msg ->
                showStatus("✅ Xác nhận bán thành công!", false));
    }

    // =========================================================
    // DATA LOADING (qua Socket - không gọi DB trực tiếp)
    // =========================================================

    private void loadMyAuctions() {
        SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_AUCTIONS, null);
    }

    private void loadMyItems() {
        SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_ITEMS, null);
    }

    // =========================================================
    // ACTION HANDLERS (FXML)
    // =========================================================

    /**
     * Nút "Tạo Phiên Đấu Giá" - gửi CreateAuctionDTO qua socket.
     * Server sẽ tạo phiên trạng thái WAITING_FOR_ADMIN và push tới Admin.
     */
    @FXML
    void handleCreateAuction(ActionEvent event) {
        try {
            Item selectedItem = cmbMyItems.getValue();
            if (selectedItem == null) {
                showStatus("❌ Vui lòng chọn sản phẩm!", true);
                return;
            }

            String startPriceTxt = txtStartPrice.getText().trim();
            if (startPriceTxt.isEmpty()) {
                showStatus("❌ Vui lòng nhập giá khởi điểm!", true);
                return;
            }
            double startingPrice = Double.parseDouble(startPriceTxt);

            if (dpStartDate.getValue() == null || dpEndDate.getValue() == null) {
                showStatus("❌ Vui lòng chọn ngày bắt đầu và kết thúc!", true);
                return;
            }

            String startHourTxt = txtStartHour.getText().trim().isEmpty() ? "00:00" : txtStartHour.getText().trim();
            String endHourTxt   = txtEndHour.getText().trim().isEmpty()   ? "23:59" : txtEndHour.getText().trim();

            LocalDateTime startTime = LocalDateTime.parse(
                    dpStartDate.getValue() + "T" + startHourTxt + ":00");
            LocalDateTime endTime = LocalDateTime.parse(
                    dpEndDate.getValue() + "T" + endHourTxt + ":00");

            if (!endTime.isAfter(startTime)) {
                showStatus("❌ Thời gian kết thúc phải sau thời gian bắt đầu!", true);
                return;
            }

            CreateAuctionDTO dto = new CreateAuctionDTO(
                    selectedItem.getId(), startingPrice, startTime, endTime);

            // [ĐÚNG] Gửi qua socket -> Server xử lý -> Response về qua MessageRouter
            SocketClient.getInstance().sendRequest(RequestCode.SELLER_CREATE_AUCTION, dto);
            showStatus("⏳ Đang gửi yêu cầu tạo phiên...", false);

        } catch (NumberFormatException e) {
            showStatus("❌ Giá khởi điểm không hợp lệ!", true);
        } catch (Exception e) {
            showStatus("❌ Lỗi: " + e.getMessage(), true);
        }
    }

    /**
     * Nút "Làm Mới" - tải lại danh sách phiên.
     */
    @FXML
    void handleRefresh(ActionEvent event) {
        loadMyAuctions();
        showStatus("🔄 Đang tải dữ liệu...", false);
    }

    /**
     * Nút "Hủy Phiên" - hủy phiên đang chọn trong bảng.
     */
    @FXML
    void handleCancelAuction(ActionEvent event) {
        Auction selected = tblMyAuctions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("❌ Vui lòng chọn phiên cần hủy!", true);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc chắn muốn hủy phiên #" + selected.getAuctionId() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác Nhận Hủy Phiên");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                SocketClient.getInstance().sendRequest(
                        RequestCode.SELLER_CANCEL_AUCTION, selected.getAuctionId());
            }
        });
    }

    /**
     * Nút "Xác Nhận Đã Bán" - xác nhận hoàn tất giao dịch sau khi phiên kết thúc.
     */
    @FXML
    void handleConfirmSale(ActionEvent event) {
        Auction selected = tblMyAuctions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("❌ Vui lòng chọn phiên cần xác nhận!", true);
            return;
        }
        if (!"CLOSED".equals(selected.getAuctionStatus()) && !"FINISHED".equals(selected.getAuctionStatus())) {
            showStatus("❌ Chỉ xác nhận được phiên đã kết thúc!", true);
            return;
        }
        SocketClient.getInstance().sendRequest(
                RequestCode.SELLER_CONFIRM_SALE, selected.getAuctionId());
    }

    /**
     * Nút Đăng Xuất.
     */
    @FXML
    void handleLogout(ActionEvent event) {
        // Hủy tất cả handlers tránh memory leak
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTIONS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_ITEMS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTION_APPROVED);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTION_REJECTED);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTION_SOLD);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTION_CREATED);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_AUCTION_CREATE_FAILED);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_CANCEL_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.SELLER_CONFIRM_SALE_SUCCESS);

        switchScene(event, "/view/view/LoginView.fxml", "Elite Auction - Đăng Nhập");
    }

    // =========================================================
    // REALTIME HANDLERS (MessageRouter -> đây)
    // Tất cả đều được gọi trên JavaFX Thread (Platform.runLater bao trong SocketClient)
    // =========================================================

    @SuppressWarnings("unchecked")
    private void onAuctionsReceived(Message message) {
        List<Auction> list = (List<Auction>) message.getPayload();
        myAuctions.clear();
        if (list != null) myAuctions.addAll(list);
        showStatus("✅ Đã tải " + myAuctions.size() + " phiên đấu giá.", false);
    }

    @SuppressWarnings("unchecked")
    private void onItemsReceived(Message message) {
        List<Item> items = (List<Item>) message.getPayload();
        if (cmbMyItems != null && items != null) {
            cmbMyItems.setItems(FXCollections.observableArrayList(items));
        }
    }

    private void onAuctionCreated(Message message) {
        showStatus("✅ Phiên #" + message.getPayload() + " đã được tạo! Đang chờ Admin duyệt.", false);
        loadMyAuctions(); // Reload bảng
    }

    private void onAuctionCreateFailed(Message message) {
        showStatus("❌ Tạo phiên thất bại: " + message.getMessage(), true);
    }

    /**
     * [REALTIME PUSH] Được server push khi Admin duyệt phiên của seller này.
     */
    private void onAuctionApproved(Message message) {
        Integer auctionId = (Integer) message.getPayload();
        showStatus("🎉 Phiên #" + auctionId + " của bạn đã được Admin DUYỆT! Phòng đấu giá đã mở.", false);
        showAlert(Alert.AlertType.INFORMATION,
                "Phiên Được Duyệt!",
                "Phiên #" + auctionId + " của bạn đã được Admin phê duyệt.\nPhòng đấu giá đã mở và nhận bid từ bây giờ!");
        loadMyAuctions();
    }

    /**
     * [REALTIME PUSH] Được server push khi Admin từ chối phiên của seller này.
     */
    private void onAuctionRejected(Message message) {
        Object[] payload = (Object[]) message.getPayload();
        int auctionId = (int) payload[0];
        String reason = (String) payload[1];
        showStatus("❌ Phiên #" + auctionId + " bị TỪ CHỐI. Lý do: " + reason, true);
        showAlert(Alert.AlertType.WARNING,
                "Phiên Bị Từ Chối",
                "Phiên #" + auctionId + " đã bị Admin từ chối.\nLý do: " + reason);
        loadMyAuctions();
    }

    /**
     * [REALTIME PUSH] Được server push khi phiên kết thúc và có người thắng.
     */
    private void onAuctionSold(Message message) {
        Object[] payload = (Object[]) message.getPayload();
        int auctionId   = (int)    payload[0];
        double price    = (double) payload[1];
        String buyer    = (String) payload[2];
        showStatus("💰 Phiên #" + auctionId + " đã kết thúc! Giá cuối: "
                + String.format("%,.0f đ", price) + " - Người mua: " + buyer, false);
        showAlert(Alert.AlertType.INFORMATION,
                "Phiên Đấu Giá Kết Thúc!",
                "Phiên #" + auctionId + " đã kết thúc thành công!\n"
                        + "Giá bán cuối: " + String.format("%,.0f đ", price) + "\n"
                        + "Người mua: " + buyer + "\n\n"
                        + "Hãy bấm 'Xác Nhận Đã Bán' để hoàn tất giao dịch.");
        loadMyAuctions();
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private void showStatus(String msg, boolean isError) {
        if (lblStatusMessage != null) {
            lblStatusMessage.setText(msg);
            lblStatusMessage.setStyle(isError
                    ? "-fx-text-fill: #FF5B5C; -fx-font-weight: bold;"
                    : "-fx-text-fill: #05CD99; -fx-font-weight: bold;");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    private void switchScene(ActionEvent event, String fxmlPath, String title) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}