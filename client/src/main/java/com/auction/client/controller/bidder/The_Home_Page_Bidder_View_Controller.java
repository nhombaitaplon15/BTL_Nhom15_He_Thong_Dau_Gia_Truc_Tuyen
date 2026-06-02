package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.PaymentDAO;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();

    private final List<ItemCardController> activeCardControllers = new ArrayList<>();

    private final Consumer<Message> onSwitchRoleSuccess = this::handleSwitchRoleSuccess;
    private final Consumer<Message> onSwitchRoleFailed = this::handleSwitchRoleFailed;
    private final Consumer<Message> onNewBidUpdate = this::handleNewBidUpdate;
    private final Consumer<Message> onAuctionEnded = this::handleAuctionEnded;

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        MessageRouter.getInstance().register(ResponseCode.SWITCH_ROLE_SUCCESS, onSwitchRoleSuccess);
        MessageRouter.getInstance().register(ResponseCode.SWITCH_ROLE_FAILED, onSwitchRoleFailed);
        MessageRouter.getInstance().register(ResponseCode.NEW_BID_UPDATE, onNewBidUpdate);
        MessageRouter.getInstance().register(ResponseCode.AUCTION_ENDED, onAuctionEnded);
    }

    private void cleanupHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.SWITCH_ROLE_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.SWITCH_ROLE_FAILED);
        MessageRouter.getInstance().unregister(ResponseCode.NEW_BID_UPDATE);
        MessageRouter.getInstance().unregister(ResponseCode.AUCTION_ENDED);
    }

    private void handleNewBidUpdate(Message msg) {
        Platform.runLater(() -> {
            try {
                Object[] payload = (Object[]) msg.getPayload();
                int auctionId = (int) payload[0];
                double newPrice = (double) payload[1];

                for (ItemCardController controller : activeCardControllers) {
                    if (controller.getAuctionId() == auctionId) {
                        controller.updateLivePrice(newPrice);
                        updateWalletUI();
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleAuctionEnded(Message msg) {
        Platform.runLater(() -> {
            try {
                Object[] payload = (Object[]) msg.getPayload();
                int auctionId = (int) payload[0];

                for (ItemCardController controller : activeCardControllers) {
                    if (controller.getAuctionId() == auctionId) {
                        controller.markAsEnded();
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblBidderName != null) {
            lblBidderName.setText(user.getUsername());
        }

        updateWalletUI();

        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE");
    }

    public void updateWalletUI() {
        if (currentUser == null) return;

        new Thread(() -> {
            try {
                double actualBalance = paymentDAO.getBalance(currentUser.getId());
                double totalUserEscrow = 0;
                String sqlEscrow = "SELECT SUM(current_price) FROM auctions WHERE current_winner_id = ? AND auction_status = 'RUNNING'";

                try (java.sql.Connection conn = com.auction.server.dao.DBConnection.getConnection();
                     java.sql.PreparedStatement ps = conn.prepareStatement(sqlEscrow)) {
                    ps.setInt(1, currentUser.getId());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            totalUserEscrow = rs.getDouble(1);
                        }
                    }
                }

                final double finalEscrow = totalUserEscrow;

                Platform.runLater(() -> {
                    currentUser.setBalance(actualBalance);

                    if (lblBalance != null) {
                        lblBalance.setText(String.format("%,.0f UETệ", actualBalance));
                    }

                    if (lblEscrowBalance != null) {
                        lblEscrowBalance.setText(String.format("Tạm giữ: %,.0f UETệ", finalEscrow));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
        cleanupHandlers();
        switchSceneWithUser(event, "/view/view/bidder/TransactionHistoryView.fxml", "Elite Auction - Lịch Sử Giao Dịch", 1);
    }

    @FXML
    void handleNavDepositWithdraw(ActionEvent event) {
        clearActiveTimers();
        cleanupHandlers();
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
        cleanupHandlers();
        switchSceneWithUser(event, "/view/view/bidder/ProfileView.fxml", "Elite Auction - Hồ Sơ Cá Nhân", 3);
    }

    @FXML
    void handleNavDashboard(ActionEvent event) {
        updateWalletUI();
        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE");
    }

    @FXML
    void handleNavBidHistory(ActionEvent event) {
        clearActiveTimers();
        cleanupHandlers();
        switchSceneWithUser(event, "/view/view/bidder/BiddingHistoryView.fxml", "Elite Auction - Lịch Sử Đặt Giá", 5);
    }

    @FXML void handleSearch(ActionEvent event) { }

    @FXML void handleSwitchToSeller(ActionEvent event) {
        SocketClient.getInstance().sendRequest(RequestCode.SWITCH_ROLE, "SELLER");
    }

    private void handleSwitchRoleSuccess(Message msg) {
        Platform.runLater(() -> {
            cleanupHandlers();
            try {
                String newRole = (String) msg.getPayload();
                currentUser.setRole(newRole);

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/view/seller/The_Home_Page_Seller_View.fxml"));
                Parent root = loader.load();

                Stage stage = (Stage) txtSearch.getScene().getWindow();
                Scene scene = new Scene(root, 1280, 720);

                stage.setScene(scene);
                stage.setTitle("Elite Auction - Trang chủ hệ thống");
                stage.setMaximized(true);
                stage.centerOnScreen();
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleSwitchRoleFailed(Message msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(msg.getMessage());
            alert.showAndWait();
        });
    }

    @FXML
    void handleLogout(ActionEvent event) {
        clearActiveTimers();
        cleanupHandlers();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/view/auth/LoginView.fxml"));
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
                    ((BiddingHistoryController) controller).setMainHomeController(
                        ((javafx.scene.Node) event.getSource()).getScene(),
                        this
                    );
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
        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE");
    }

    @FXML
    void handleRoomArt(MouseEvent event) {
        updateWalletUI();
        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: NGHỆ THUẬT (Live)");
        loadAuctionsFromDatabase("ART");
    }

    @FXML
    void handleRoomElectronics(MouseEvent event) {
        updateWalletUI();
        if (lblRoomTitle != null) lblRoomTitle.setText("Phòng Đấu Giá: ĐIỆN TỬ (Live)");
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

    private void loadAuctionsFromDatabase(String category) {
        if (gridAuctions == null) return;

        clearActiveTimers();
        gridAuctions.getChildren().clear();

        new Thread(() -> {
            try {
                List<Auction> activeAuctions = auctionDAO.getLiveAuctionsByCategory(category);

                Platform.runLater(() -> {
                    try {
                        int column = 0;
                        int row = 0;
                        boolean hasItems = false;

                        if (activeAuctions != null) {
                            LocalDateTime now = LocalDateTime.now();

                            for (Auction auction : activeAuctions) {
                                if (auction.getEndTime() != null && now.isAfter(auction.getEndTime())) {
                                    continue;
                                }

                                Item item = itemDAO.getItemById(auction.getItemId());
                                if (item != null) {
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}