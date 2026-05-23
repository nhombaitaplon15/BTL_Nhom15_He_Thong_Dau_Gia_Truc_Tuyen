package com.auction.client.controller;

import com.auction.client.controller.bidder.ProfileController;
import com.auction.client.controller.bidder.TransactionHistoryController;
import com.auction.client.controller.bidder.WalletController;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URL;
import java.util.List;

public class The_Home_Page_Bidder_View_Controller {

    @FXML private Label lblBidderName;
    @FXML private Label lblBalance;
    @FXML private TextField txtSearch;
    @FXML private GridPane gridAuctions;
    @FXML private VBox sidebarContainer;
    @FXML private Label lblRoomTitle;
    @FXML private FlowPane auctionsContainer; // Đồng bộ vùng chứa thẻ sản phẩm dạng lưới tự giãn

    private User currentUser;
    private MainContainerController mainContainer;

    // Khởi tạo các đối tượng lớp DAO kết nối Database
    private AuctionDAO auctionDAO = new AuctionDAO();
    private ItemDAO itemDAO = new ItemDAO();

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        System.out.println("🎉 Khởi tạo trang chủ Bidder thành công.");
        // Mặc định tự động load phòng Xe Máy / Ô tô lên trước cho đỡ trống sàn
        loadAuctionsFromDatabase("VEHICLE");
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblBidderName != null) {
            lblBidderName.setText(user.getUsername() != null ? user.getUsername() : "Khách");
        }
        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f đ", user.getBalance()));
        }
    }

    @FXML
    void handleNavTransactionHistory(ActionEvent event) {
        switchSceneWithUser(event, "/view/bidder/TransactionHistoryView.fxml", "Elite Auction - Lịch Sử Giao Dịch", 1);
    }

    @FXML
    void handleNavDepositWithdraw(ActionEvent event) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void handleNavProfile(ActionEvent event) {
        switchSceneWithUser(event, "/view/bidder/ProfileView.fxml", "Elite Auction - Hồ Sơ Cá Nhân", 3);
    }

    @FXML void handleNavDashboard(ActionEvent event) { System.out.println("Sàn đấu giá đang mở."); }

    @FXML
    void handleNavBidHistory(ActionEvent event) {
        switchSceneWithUser(event, "/view/BiddingHistoryView.fxml", "Elite Auction - Lịch Sử Đặt Giá", 5);
    }

    @FXML void handleSearch(ActionEvent event) { System.out.println("Tìm kiếm"); }
    @FXML void handleSwitchToSeller(ActionEvent event) { System.out.println("Chuyển người bán"); }

    @FXML
    void handleLogout(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/LoginView.fxml"));
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void switchSceneWithUser(ActionEvent event, String fxmlPath, String title, int type) {
        try {
            URL fxmlLocation = getClass().getResource(fxmlPath);
            if (fxmlLocation == null) {
                System.err.println("❌ Không tìm thấy file FXML tại: " + fxmlPath);
                return;
            }

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
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Lỗi điều hướng: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    void handleRoomVehicle(MouseEvent event) {
        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: PHƯƠNG TIỆN (Live)");
        loadAuctionsFromDatabase("VEHICLE");
    }

    @FXML
    void handleRoomArt(MouseEvent event) {
        if (lblRoomTitle != null) lblRoomTitle.setText("🔥 Phòng Đấu Giá: NGHỆ THUẬT (Live)");
        loadAuctionsFromDatabase("ART");
    }

    @FXML
    void handleRoomElectronics(MouseEvent event) {
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

    // ========================================================
    // TIẾN TRÌNH TRUY VẤN DATABASE CHUẨN (HÀM DUY NHẤT - HẾT LỖI TRÙNG)
    // ========================================================

    private void loadAuctionsFromDatabase(String category) {
        // 1. Dọn sạch các thẻ cũ trên lưới thật GridPane trước khi nạp dữ liệu mới
        if (gridAuctions != null) {
            gridAuctions.getChildren().clear();
        }

        new Thread(() -> {
            try {
                System.out.println("🗄️ [Database Thật] Bắt đầu kết nối lấy phiên đấu giá Live thuộc nhóm: " + category);

                // Gọi DAO quét các phiên đang diễn ra trên hệ thống
                List<Auction> activeAuctions = auctionDAO.getAuctionsByStatus("RUNNING");

                if (activeAuctions == null || activeAuctions.isEmpty()) {
                    System.out.println("📢 Hệ thống kiểm tra: Hiện không có phiên đấu giá nào ở trạng thái RUNNING.");
                    return;
                }

                // Khởi tạo tọa độ sắp xếp ô trên lưới GridPane (Thiết kế 3 cột của bạn)
                int column = 0;
                int row = 0;

                for (Auction auction : activeAuctions) {
                    Item itemDetail = itemDAO.getItemById(auction.getItemId());

                    // Khớp mã sản phẩm thật từ bảng items với danh mục phòng được chọn
                    if (itemDetail != null && category.equalsIgnoreCase(itemDetail.getItemType())) {

                        final int currentColumn = column;
                        final int currentRow = row;

                        // Đẩy dữ liệu chuẩn hóa lên luồng giao diện người dùng
                        Platform.runLater(() -> {
                            try {
                                VBox cardNode = createAuctionCardFXML(itemDetail, auction.getCurrentPrice(), auction.getAuctionId());
                                if (cardNode != null && gridAuctions != null) {
                                    // Gắn thẻ sản phẩm vào đúng vị trí ô (Cột, Hàng) trên GridPane thật
                                    gridAuctions.add(cardNode, currentColumn, currentRow);
                                    System.out.println("✅ Hiển thị thành công vật phẩm: " + itemDetail.getName());
                                }
                            } catch (Exception e) {
                                System.err.println("❌ Lỗi dựng thẻ trên giao diện: " + e.getMessage());
                            }
                        });

                        // Thuật toán dịch ô: Đầy 3 ô ở cột 0, 1, 2 thì tự động xuống hàng tiếp theo
                        column++;
                        if (column == 3) {
                            column = 0;
                            row++;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi truy vấn kết nối hệ thống dữ liệu trục dọc:");
                e.printStackTrace();
            }
        }).start();
    }
    private VBox createAuctionCardFXML(Item itemDetail, double currentPrice, int auctionId) {
        try {
            // 1. Nạp file FXML của thẻ sản phẩm
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ItemCard.fxml"));
            VBox card = loader.load();

            // 2. Lấy chính xác lớp Controller quản lý thẻ đó
            ItemCardController cardController = loader.getController();

            if (cardController != null) {
                // Đẩy toàn bộ dữ liệu bốc từ Database sang cho ItemCard xử lý
                cardController.setData(itemDetail, currentPrice, auctionId);
            }

            // 3. Bắt sự kiện click chuột trực tiếp vào miếng Card để đi tới phòng đấu giá tương ứng
            card.setOnMouseClicked(e -> {
                System.out.println("🔨 [Database] Người dùng click vào phiên đấu giá thực tế ID: " + auctionId);
                // Bạn viết logic chuyển hướng sang phòng trả giá trực tuyến (BidRoom) tại đây
            });

            return card;
        } catch (Exception e) {
            System.err.println("❌ Lỗi nạp thẻ ItemCard.fxml từ Database: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String calculateTimeLeft(java.time.LocalDateTime endTime) {
        java.time.Duration duration = java.time.Duration.between(java.time.LocalDateTime.now(), endTime);
        if (duration.isNegative() || duration.isZero()) {
            return "Đã hết giờ";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}