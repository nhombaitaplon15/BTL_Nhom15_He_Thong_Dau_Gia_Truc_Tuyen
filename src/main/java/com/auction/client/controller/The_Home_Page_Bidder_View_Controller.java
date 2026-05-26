package com.auction.client.controller;

import com.auction.client.controller.bidder.ProfileController;
import com.auction.client.controller.bidder.TransactionHistoryController;
import com.auction.client.controller.bidder.WalletController;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.PaymentDAO;
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

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        System.out.println("🎉 Khởi tạo trang chủ Bidder thành công.");
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblBidderName != null) {
            lblBidderName.setText(user.getUsername());
        }

        updateWalletUI();

        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE");
    }

    public void updateWalletUI() {
        if (currentUser == null) return;

        new Thread(() -> {
            try {
                double actualBalance = paymentDAO.getBalance(currentUser.getId());
                double actualEscrow = paymentDAO.getEscrowBalance(currentUser.getId());

                Platform.runLater(() -> {
                    currentUser.setBalance(actualBalance);

                    if (lblBalance != null) {
                        lblBalance.setText(String.format("%,.0f UETệ", actualBalance));
                    }
                    if (lblEscrowBalance != null) {
                        lblEscrowBalance.setText(String.format("Tạm giữ: %,.0f UETệ", actualEscrow));
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Lỗi đồng bộ ví tiền trên màn hình trang chủ!");
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
        switchSceneWithUser(event, "/view/bidder/TransactionHistoryView.fxml", "Elite Auction - Lịch Sử Giao Dịch", 1);
    }

    @FXML
    void handleNavDepositWithdraw(ActionEvent event) {
        clearActiveTimers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bidder/DepositWithdrawView.fxml"));
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
        switchSceneWithUser(event, "/view/bidder/ProfileView.fxml", "Elite Auction - Hồ Sơ Cá Nhân", 3);
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
        switchSceneWithUser(event, "/view/BiddingHistoryView.fxml", "Elite Auction - Lịch Sử Đặt Giá", 5);
    }

    @FXML void handleSearch(ActionEvent event) { System.out.println("Tìm kiếm"); }
    @FXML void handleSwitchToSeller(ActionEvent event) { System.out.println("Chuyển người bán"); }

    @FXML
    void handleLogout(ActionEvent event) {
        clearActiveTimers();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/LoginView.fxml"));
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

                    // 🎯 SỬA LỖI: Truyền thêm tham số thứ 2 là "this" (chính là controller Trang Chủ này)
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
                        }

                        if (!hasItems) {
                            Label lblEmpty = new Label("Hiện tại phòng này chưa có sản phẩm nào được đấu giá.");
                            lblEmpty.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic; -fx-font-size: 16px;");
                            gridAuctions.add(lblEmpty, 0, 0);
                        }

                    } catch (Exception e) {
                        System.err.println("❌ Lỗi hiển thị lưới sản phẩm: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Lỗi kết nối Database khi tải danh mục phòng đấu giá!");
                e.printStackTrace();
            }
        }).start();
    }
}