package com.auction.client.controller.bidder;


import com.auction.client.controller.bidder.MainContainerController;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Admin;
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
import javafx.stage.Stage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class The_Home_Page_Bidder_View_Controller {

    @FXML private Label lblBidderName;
    @FXML private Label lblBalance;
    @FXML private Label lblEscrowBalance;
    @FXML private TextField txtSearch;
    @FXML private GridPane gridAuctions;
    @FXML private Label lblRoomTitle;

    private User currentUser;
    private MainContainerController mainContainer;
    private final List<ItemCardController> activeCardControllers = new ArrayList<>();

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        System.out.println("🎉 Khởi tạo trang chủ Bidder thành công.");

        // Đăng ký nhận dữ liệu từ Event Bus
        MessageRouter.getInstance().register(ResponseCode.PROFILE_RESULT, this::handleProfileResult);
        MessageRouter.getInstance().register(ResponseCode.ROOM_LIST_RESULT, this::handleRoomListResult);
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblBidderName != null) lblBidderName.setText(user.getUsername());

        updateWalletUI();

        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromSocket("VEHICLE");
    }

    public void updateWalletUI() {
        // Gửi yêu cầu lấy Profile để cập nhật lại tiền tài khoản
        SocketClient.getInstance().sendRequest(RequestCode.GET_PROFILE, null);
    }

    private void handleProfileResult(Message message) {
        if (!(message.getPayload() instanceof User)) return;
        Admin updatedUser = (Admin) message.getPayload();

        Platform.runLater(() -> {
            if (this.currentUser != null) {
                this.currentUser.setBalance(updatedUser.getBalance());
            }

            if (lblBalance != null) {
                lblBalance.setText(String.format("%,.0f UETệ", updatedUser.getBalance()));
            }

            // GIỮ NGUYÊN GỐC: Đồng bộ số tiền cọc đang nằm ở ví tạm Admin hiển thị lên UI người dùng
            if (lblEscrowBalance != null) {
                lblEscrowBalance.setText(String.format("Tạm giữ: %,.0f UETệ", updatedUser.getEscrowBalance()));
            }

            // Đồng bộ hiển thị số dư lên cả thanh Sidebar của MainContainer
            if (this.mainContainer != null) {
                this.mainContainer.updateBalanceDisplay(updatedUser.getBalance());
            }
        });
    }

    private void loadAuctionsFromSocket(String category) {
        if (gridAuctions == null) return;
        clearActiveTimers();
        gridAuctions.getChildren().clear();

        // Gửi mã yêu cầu lấy danh sách phiên đấu giá theo danh mục phòng
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_ITEMS, category);
    }

    private void handleRoomListResult(Message message) {
        if (gridAuctions == null || !(message.getPayload() instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<Auction> activeAuctions = (List<Auction>) message.getPayload();

        Platform.runLater(() -> {
            try {
                int column = 0;
                int row = 0;
                boolean hasItems = false;
                LocalDateTime now = LocalDateTime.now();

                for (Auction auction : activeAuctions) {
                    if (auction.getEndTime() != null && now.isAfter(auction.getEndTime())) {
                        continue;
                    }

                    Item item = auction.getItem();
                    if (item != null) {
                        hasItems = true;

                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ItemCard.fxml"));
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
                e.printStackTrace();
            }
        });
    }

    private void clearActiveTimers() {
        for (ItemCardController controller : activeCardControllers) {
            if (controller != null) controller.stopTimer();
        }
        activeCardControllers.clear();
    }

    // ─── ĐIỀU HƯỚNG QUA MAIN CONTAINER (GIỮ NGUYÊN SIDEBAR) ───
    @FXML
    void handleNavTransactionHistory(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) {
            this.mainContainer.setPage("/view/bidder/TransactionHistoryView.fxml");
        }
    }

    @FXML
    void handleNavProfile(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) {
            this.mainContainer.setPage("/view/bidder/ProfileView.fxml");
        }
    }

    @FXML
    void handleNavBidHistory(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) {
            this.mainContainer.setPage("/view/BiddingHistoryView.fxml");
        }
    }

    @FXML
    void handleNavDepositWithdraw(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) {
            this.mainContainer.setPage("/view/bidder/DepositWithdrawView.fxml");
        }
    }

    @FXML
    void handleNavDashboard(ActionEvent event) {
        updateWalletUI();
        loadAuctionsFromSocket("VEHICLE");
    }

    @FXML
    void handleToggleSidebar(ActionEvent event) {
        if (this.mainContainer != null) {
            this.mainContainer.toggleSidebar();
        }
    }

    @FXML
    void handleLogout(ActionEvent event) {
        cleanupListeners();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/LoginView.fxml"));
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.setTitle("Elite Auction - Đăng Nhập");
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML void handleSearch(ActionEvent event) { System.out.println("Tìm kiếm"); }
    @FXML void handleSwitchToSeller(ActionEvent event) { System.out.println("Chuyển người bán"); }

    @FXML void handleRoomVehicle(MouseEvent event) { updateWalletUI(); if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: PHƯƠNG TIỆN (Live)"); loadAuctionsFromSocket("VEHICLE"); }
    @FXML void handleRoomArt(MouseEvent event) { updateWalletUI(); if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: NGHỆ THUẬT (Live)"); loadAuctionsFromSocket("ART"); }
    @FXML void handleRoomElectronics(MouseEvent event) { updateWalletUI(); if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: ĐIỆN TỬ (Live)"); loadAuctionsFromSocket("ELECTRONICS"); }

    private void cleanupListeners() {
        clearActiveTimers();
        MessageRouter.getInstance().unregister(ResponseCode.PROFILE_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.ROOM_LIST_RESULT);
    }
}
