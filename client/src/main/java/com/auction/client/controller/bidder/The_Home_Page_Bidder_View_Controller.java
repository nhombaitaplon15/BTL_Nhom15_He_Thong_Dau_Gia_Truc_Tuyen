package com.auction.client.controller.bidder;

import com.auction.client.controller.bidder.ItemCardController;
import com.auction.client.controller.bidder.ProfileController;
import com.auction.client.controller.bidder.TransactionHistoryController;
import com.auction.client.controller.bidder.WalletController;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class The_Home_Page_Bidder_View_Controller {

    @FXML private Label lblBidderName;
    @FXML private Label lblBalance;
    @FXML private Label lblEscrowBalance;
    @FXML private TextField txtSearch;
    @FXML private GridPane gridAuctions;
    @FXML private VBox sidebarContainer;
    @FXML private Label lblRoomTitle;

    private User currentUser;
    private MainContainerController mainContainer;

    // ─── REALTIME UPDATE: Loại bỏ hoàn toàn các DAO kết nối Database trực tiếp ───
    private final List<ItemCardController> activeCardControllers = new ArrayList<>();

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        System.out.println("🎉 Khởi tạo trang chủ Bidder phẳng (Đồng bộ 100% mạng Socket).");

        // Đăng ký nhận sự kiện Realtime từ Server qua Event Bus
        MessageRouter.getInstance().register(ResponseCode.FETCH_ITEMS_RESULT, this::handleFetchItemsResult);
        MessageRouter.getInstance().register(ResponseCode.WALLET_UPDATE_RESULT, this::handleWalletUpdateResult);
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblBidderName != null) {
            lblBidderName.setText(user.getUsername());
        }

        updateWalletUI();

        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE"); // Giữ nguyên tên hàm gọi ban đầu của bạn
    }

    /**
     * 🎯 REALTIME: Phát lệnh yêu cầu Server cập nhật số ví và tiền tạm giữ qua mạng Socket
     */
    public void updateWalletUI() {
        if (currentUser == null) return;

        new Thread(() -> {
            try {
                // Gửi ID của user lên server yêu cầu lấy thông tin ví mới nhất
                SocketClient.getInstance().sendRequest(RequestCode.GET_WALLET_INFO, currentUser.getId());
            } catch (Exception e) {
                System.err.println("❌ Lỗi gửi request cập nhật ví: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 🎯 REALTIME HANDLER: Hứng gói tin thông tin ví Realtime từ Server trả về
     */
    private void handleWalletUpdateResult(Message message) {
        if (message == null || message.getPayload() == null) return;

        Platform.runLater(() -> {
            try {
                // Giả định Server trả về một Map chứa cả số dư thực tế và tiền tạm giữ hoặc một đối tượng tùy chỉnh
                if (message.getPayload() instanceof Map) {
                    Map<?, ?> walletData = (Map<?, ?>) message.getPayload();
                    double actualBalance = ((Number) walletData.get("balance")).doubleValue();
                    double totalUserEscrow = ((Number) walletData.get("escrow")).doubleValue();

                    currentUser.setBalance(actualBalance);

                    if (lblBalance != null) {
                        lblBalance.setText(String.format("%,.0f UETệ", actualBalance));
                    }
                    if (lblEscrowBalance != null) {
                        lblEscrowBalance.setText(String.format("Tạm giữ: %,.0f UETệ", totalUserEscrow));
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi cập nhật giao diện ví: " + e.getMessage());
            }
        });
    }

    private void clearActiveTimers() {
        for (ItemCardController controller : activeCardControllers) {
            if (controller != null) {
                controller.stopTimer();
            }
        }
        activeCardControllers.clear();
    }

    @FXML
    void handleNavTransactionHistory(ActionEvent event) {
        clearActiveTimers();
        switchSceneWithUser(event, "/view/view/bidder/TransactionHistoryView.fxml", "Elite Auction - Lịch Sử Giao Dịch", 1);
    }

    @FXML
    void handleNavDepositWithdraw(ActionEvent event) {
        clearActiveTimers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/DepositWithdrawView.fxml"));
            Parent root = loader.load();
            WalletController walletController = loader.getController();
            if (walletController != null) {
                walletController.setUserData(this.currentUser);
            }
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    void handleNavProfile(ActionEvent event) {
        clearActiveTimers();
        switchSceneWithUser(event, "/view/view/bidder/ProfileView.fxml", "Elite Auction - Hồ Sơ Cá Nhân", 3);
    }

    @FXML
    void handleNavDashboard(ActionEvent event) {
        updateWalletUI();
        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE");
    }

    @FXML
    void handleNavBidHistory(ActionEvent event) {
        clearActiveTimers();
        switchSceneWithUser(event, "/view/view/bidder/BiddingHistoryView.fxml", "Elite Auction - Lịch Sử Đặt Giá", 5);
    }

    @FXML void handleSearch(ActionEvent event) { System.out.println("Tìm kiếm"); }
    @FXML void handleSwitchToSeller(ActionEvent event) { System.out.println("Chuyển người bán"); }

    @FXML
    void handleLogout(ActionEvent event) {
        cleanupListeners(); // Đảm bảo hủy lắng nghe Socket khi đăng xuất
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/view/bidder/LoginView.fxml"));
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void switchSceneWithUser(ActionEvent event, String fxmlPath, String title, int type) {
        try {
            URL fxmlLocation = getClass().getResource(fxmlPath);
            if (fxmlLocation == null) return;

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();
            Object controller = loader.getController();

            if (controller != null) {
                if (type == 1 && controller instanceof TransactionHistoryController) {
                    ((TransactionHistoryController) controller).setUserData(this.currentUser);
                } else if (type == 2 && controller instanceof WalletController) {
                    ((WalletController) controller).setUserData(this.currentUser);
                } else if (type == 3 && controller instanceof ProfileController) {
                    ((ProfileController) controller).setUserData(this.currentUser);
                } else if (type == 5 && controller instanceof BiddingHistoryController) {
                    ((BiddingHistoryController) controller).setUserData(this.currentUser);
                }
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.setTitle(title);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    void handleRoomVehicle(MouseEvent event) {
        updateWalletUI();
        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE");
    }

    @FXML
    void handleRoomArt(MouseEvent event) {
        updateWalletUI();
        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: NGHỆ THUẬT (Live)");
        loadAuctionsFromDatabase("ART");
    }

    @FXML
    void handleRoomElectronics(MouseEvent event) {
        updateWalletUI();
        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: ĐIỆN TỬ (Live)");
        loadAuctionsFromDatabase("ELECTRONICS");
    }

    @FXML
    void handleToggleSidebar(ActionEvent event) {
        if (sidebarContainer != null) {
            boolean isVisible = !sidebarContainer.isVisible();
            sidebarContainer.setVisible(isVisible);
            sidebarContainer.setManaged(isVisible);
        }
    }

    /**
     * 🎯 REALTIME: Giữ nguyên tên hàm cũ của bạn nhưng chuyển sang đẩy Request qua Socket thay vì kết nối DB trực tiếp
     */
    private void loadAuctionsFromDatabase(String category) {
        if (gridAuctions == null) return;

        clearActiveTimers();
        gridAuctions.getChildren().clear();

        new Thread(() -> {
            try {
                // Gửi tín hiệu lấy danh sách phòng qua mạng Socket
                SocketClient.getInstance().sendRequest(RequestCode.FETCH_ITEMS, category);
            } catch (Exception e) {
                System.err.println("❌ Lỗi gửi request Socket tải danh mục: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 🎯 REALTIME HANDLER: Tiếp nhận gói tin chứa danh sách phòng do mạng Socket trả về từ Event Bus
     */
    private void handleFetchItemsResult(Message message) {
        if (gridAuctions == null || message == null || message.getPayload() == null) return;
        if (!(message.getPayload() instanceof List)) return;

        List<?> rawList = (List<?>) message.getPayload();

        Platform.runLater(() -> {
            try {
                clearActiveTimers();
                gridAuctions.getChildren().clear();

                int column = 0;
                int row = 0;
                boolean hasItems = false;
                LocalDateTime now = LocalDateTime.now();

                for (Object obj : rawList) {
                    Auction auction = null;
                    Item item = null;

                    // Khắc phục lỗi ClassCastException: linh hoạt kiểm tra kiểu Server trả về
                    if (obj instanceof Auction) {
                        auction = (Auction) obj;
                        item = auction.getItem();
                    } else if (obj instanceof Item) {
                        item = (Item) obj;
                        // Tạo đối tượng bọc tạm thời nếu server chỉ trả về danh sách Item/Vehicle thô
                        auction = new Auction();
                        auction.setItem(item);
                        auction.setAuctionId(item.getId());
                        auction.setCurrentPrice(item.getStartingPrice());
                        auction.setEndTime(LocalDateTime.now().plusHours(2));
                    }

                    if (item != null && auction != null) {
                        // Bỏ qua các phòng đấu giá đã quá hạn thời gian kết thúc
                        if (auction.getEndTime() != null && now.isAfter(auction.getEndTime())) {
                            continue;
                        }

                        hasItems = true;

                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/bidder/ItemCard.fxml"));
                        Parent itemCard = loader.load();

                        ItemCardController cardController = loader.getController();
                        if (cardController != null) {
                            cardController.setData(item, auction.getAuctionId(), currentUser, auction.getCurrentPrice(), auction.getEndTime());
                            activeCardControllers.add(cardController);
                        }

                        if (column == 3) {
                            column = 0;
                            row++;
                        }
                        gridAuctions.add(itemCard, column++, row);
                        GridPane.setMargin(itemCard, new javafx.geometry.Insets(15));
                    }
                }

                if (!hasItems) {
                    Label lblEmpty = new Label("Hiện tại phòng này chưa có sản phẩm nào được đấu giá.");
                    lblEmpty.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic; -fx-font-size: 16px;");
                    gridAuctions.add(lblEmpty, 0, 0);
                }

            } catch (Exception e) {
                System.err.println("❌ Lỗi hiển thị lưới sản phẩm Realtime: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * 🎯 CLEANUP LISTENERS: Hủy đăng ký lắng nghe trên Router để tránh lỗi tràn/rò rỉ bộ nhớ
     */
    public void cleanupListeners() {
        clearActiveTimers();
        MessageRouter.getInstance().unregister(ResponseCode.FETCH_ITEMS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.WALLET_UPDATE_RESULT);
    }
}
