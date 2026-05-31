package com.auction.client.controller.bidder;  // [SỬA DÒNG 1] client.controller.bidder -> com.auction.client.controller.bidder

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
// [XÓA] import com.auction.server.service.ItemService; // VI PHẠM KIẾN TRÚC: Client KHÔNG import Server!

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;

/**
 * Controller trang chủ Bidder.
 *
 * ĐÃ SỬA CÁC LỖI KIẾN TRÚC:
 * 1. Package: client.controller.bidder -> com.auction.client.controller.bidder
 * 2. XÓA import com.auction.server.service.ItemService - CLIENT KHÔNG ĐƯỢC IMPORT SERVER!
 * 3. XÓA private final ItemService itemService = new ItemService() - gọi DB trực tiếp từ client là sai.
 * 4. loadAuctionsFromDatabase() cũ: gọi itemService.getItemsByType() TRỰC TIẾP tới DB
 *    => Nay: gửi FETCH_ITEMS request qua SocketClient và nhận response qua MessageRouter.
 * 5. Đăng ký handlers realtime: NEW_BID_UPDATE, FETCH_ITEMS_RESULT, AUCTION_ENDED
 *    => UI tự động cập nhật khi server broadcast.
 *
 * Đặt tại: client/src/main/java/com/auction/client/controller/bidder/The_Home_Page_Bidder_View_Controller.java
 */
public class The_Home_Page_Bidder_View_Controller {

    @FXML private Label lblBidderName;
    @FXML private Label lblBalance;
    @FXML private TextField txtSearch;
    @FXML private GridPane gridAuctions;
    @FXML private VBox sidebarContainer;
    @FXML private Label lblRoomTitle;

    private User currentUser;
    private MainContainerController mainContainer;

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        System.out.println("Khởi tạo trang chủ Bidder thành công.");

        // [ĐÃ SỬA] Đăng ký handler realtime tại đây thay vì trong loadAuctionsFromDatabase
        registerRealtimeHandlers();
    }

    /**
     * [ĐÃ THÊM] Đăng ký các handler nhận thông báo realtime từ server.
     * Khi màn hình này active, mọi broadcast NEW_BID_UPDATE và AUCTION_ENDED
     * sẽ tự động cập nhật UI mà không cần polling.
     */
    private void registerRealtimeHandlers() {
        // Handler: nhận danh sách Items từ server sau khi gửi FETCH_ITEMS
        MessageRouter.getInstance().register(ResponseCode.FETCH_ITEMS_RESULT, this::onItemsReceived);

        // Handler: cập nhật giá realtime khi có người bid trong phòng
        MessageRouter.getInstance().register(ResponseCode.NEW_BID_UPDATE, this::onNewBidUpdate);

        // Handler: thông báo phiên đấu giá kết thúc
        MessageRouter.getInstance().register(ResponseCode.AUCTION_ENDED, this::onAuctionEnded);
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        if (lblBidderName != null) lblBidderName.setText(user.getUsername());
        if (lblBalance != null)
            lblBalance.setText(String.format("%,.0f UETệ", user.getBalance()));
    }

    // =========================================================
    // NAVIGATION HANDLERS
    // =========================================================

    @FXML void handleNavTransactionHistory(ActionEvent event) {
        switchSceneWithUser(event, "/view/view/bidder/TransactionHistoryView.fxml", "Elite Auction - Lịch Sử Giao Dịch", 1);
    }

    @FXML
    void handleNavDepositWithdraw(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/DepositWithdrawView.fxml"));
            Parent root = loader.load();
            WalletController walletController = loader.getController();
            if (walletController != null) walletController.setUserData(this.currentUser);
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML void handleNavProfile(ActionEvent event) {
        switchSceneWithUser(event, "/view/view/bidder/ProfileView.fxml", "Elite Auction - Hồ Sơ Cá Nhân", 3);
    }

    @FXML void handleNavDashboard(ActionEvent event) { System.out.println("Sàn đấu giá"); }
    @FXML void handleNavBidHistory(ActionEvent event) {
        switchSceneWithUser(event, "/view/view/BiddingHistoryView.fxml", "Elite Auction - Lịch Sử Đặt Giá", 5);
    }
    @FXML void handleSearch(ActionEvent event) { System.out.println("Tìm kiếm: " + txtSearch.getText()); }
    @FXML void handleSwitchToSeller(ActionEvent event) {
        switchSceneWithUser(event, "/view/view/seller/The_Home_Page_Seller_View.fxml", "", 5);
        System.out.println("Chuyển người bán"); }

    @FXML
    void handleLogout(ActionEvent event) {
        // Hủy đăng ký handlers realtime trước khi rời màn hình
        MessageRouter.getInstance().unregister(ResponseCode.FETCH_ITEMS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.NEW_BID_UPDATE);
        MessageRouter.getInstance().unregister(ResponseCode.AUCTION_ENDED);

        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/view/LoginView.fxml"));
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================
    // PHÒNG ĐẤU GIÁ (VehicleRoom / ArtRoom / Electronics)
    // =========================================================

    @FXML
    void handleRoomVehicle(MouseEvent event) {
        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        requestItemsFromServer("VEHICLE"); // [SỬA] Gửi qua socket thay vì gọi DB trực tiếp
    }

    @FXML
    void handleRoomArt(MouseEvent event) {
        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: NGHỆ THUẬT (Live)");
        requestItemsFromServer("ART");
    }

    @FXML
    void handleRoomElectronics(MouseEvent event) {
        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: ĐIỆN TỬ (Live)");
        requestItemsFromServer("ELECTRONICS");
    }

    /**
     * [ĐÃ SỬA] Gửi FETCH_ITEMS request qua SocketClient thay vì gọi ItemService trực tiếp.
     *
     * Luồng cũ (SAI): Client -> ItemDAO -> DB (Client biết cấu trúc DB, vi phạm kiến trúc)
     * Luồng mới (ĐÚNG): Client -> Socket -> Server -> ItemService -> DB -> Socket -> Client
     */
    private void requestItemsFromServer(String category) {
        // Chỉ cần 1 dòng - SocketClient lo phần còn lại
        // Kết quả trả về sẽ trigger onItemsReceived() qua MessageRouter
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_ITEMS, category);
    }

    // =========================================================
    // REALTIME HANDLERS (được gọi từ MessageRouter sau Platform.runLater)
    // =========================================================

    /**
     * [ĐÃ THÊM] Gọi khi server trả về danh sách Items (FETCH_ITEMS_RESULT).
     * Chạy trên JavaFX Application Thread (đã được Platform.runLater bọc ở SocketClient).
     */
    @SuppressWarnings("unchecked")
    private void onItemsReceived(Message message) {
        if (gridAuctions == null) return;
        List<Item> itemList = (List<Item>) message.getPayload();
        gridAuctions.getChildren().clear();

        if (itemList == null || itemList.isEmpty()) {
            System.out.println("Không có sản phẩm nào trong danh mục này.");
            return;
        }

        int column = 0, row = 0;
        for (Item item : itemList) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/ItemCard.fxml"));
                Parent itemCard = loader.load();
                ItemCardController cardController = loader.getController();
                cardController.setData(item);

                if (column == 3) { column = 0; row++; }
                gridAuctions.add(itemCard, column++, row);
                GridPane.setMargin(itemCard, new javafx.geometry.Insets(15));
            } catch (Exception e) {
                System.err.println("Lỗi khi render thẻ sản phẩm: " + e.getMessage());
            }
        }
        System.out.println("Đã vẽ " + itemList.size() + " sản phẩm lên màn hình!");
    }

    /**
     * [ĐÃ THÊM] Gọi khi server broadcast có giá bid mới (NEW_BID_UPDATE).
     * Cập nhật giá trên ItemCard tương ứng - đây là tính năng REALTIME cốt lõi.
     */
    private void onNewBidUpdate(Message message) {
        // Payload: Object[] {auctionId, newPrice, winnerId}
        Object[] payload = (Object[]) message.getPayload();
        if (payload == null || payload.length < 2) return;

        int auctionId = (int) payload[0];
        double newPrice = (double) payload[1];

        // Cập nhật label giá trong tất cả ItemCard đang hiển thị auctionId này
        // ItemCardController cần expose hàm updatePrice(auctionId, newPrice)
        gridAuctions.getChildren().forEach(node -> {
            if (node.getUserData() instanceof ItemCardController) {
                ItemCardController card = (ItemCardController) node.getUserData();
                card.updatePriceIfMatch(auctionId, newPrice);
            }
        });

        System.out.println("[REALTIME] Giá mới: " + auctionId + " = " + newPrice);
    }

    /**
     * [ĐÃ THÊM] Gọi khi server broadcast phiên kết thúc (AUCTION_ENDED).
     */
    private void onAuctionEnded(Message message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Phiên Đấu Giá Kết Thúc");
        alert.setHeaderText(null);
        alert.setContentText(message.getMessage());
        alert.show();
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    @FXML
    void handleToggleSidebar(ActionEvent event) {
        if (sidebarContainer != null) {
            boolean isVisible = !sidebarContainer.isVisible();
            sidebarContainer.setVisible(isVisible);
            sidebarContainer.setManaged(isVisible);
        }
    }

    private void switchSceneWithUser(ActionEvent event, String fxmlPath, String title, int type) {
        try {
            URL fxmlLocation = getClass().getResource(fxmlPath);
            if (fxmlLocation == null) {
                System.err.println("Không tìm thấy FXML: " + fxmlPath);
                return;
            }
            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller != null) {
                if (type == 1 && controller instanceof TransactionHistoryController)
                    ((TransactionHistoryController) controller).setUserData(this.currentUser);
                else if (type == 2 && controller instanceof WalletController)
                    ((WalletController) controller).setUserData(this.currentUser);
                else if (type == 3 && controller instanceof ProfileController)
                    ((ProfileController) controller).setUserData(this.currentUser);
                else if (type == 5 && controller instanceof BiddingHistoryController)
                    ((BiddingHistoryController) controller).setUserData(this.currentUser);
            }
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.setTitle(title);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Lỗi điều hướng: " + e.getMessage()).showAndWait();
        }
    }
}
