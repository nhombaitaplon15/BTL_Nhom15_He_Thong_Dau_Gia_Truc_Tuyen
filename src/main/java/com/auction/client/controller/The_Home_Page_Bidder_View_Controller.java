package com.auction.client.controller;

import com.auction.common.model.User;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;
import com.auction.common.model.Art;
import com.auction.common.model.Electronics;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class The_Home_Page_Bidder_View_Controller {

    @FXML private Label lblBidderName;
    @FXML private Label lblBalance;
    @FXML private TextField txtSearch;
    @FXML private Label lblRoomTitle;
    @FXML private GridPane gridAuctions;

    // 🌟 THÊM MỚI: Khai báo đối tượng Sidebar container từ FXML để xử lý ẩn/hiện
    @FXML private VBox sidebarContainer;

    private User currentUser;

    // 🌟 THÊM MỚI: Biến kiểm soát trạng thái ẩn/hiện của thanh bên trái
    private boolean isSidebarVisible = true;

    @FXML
    public void initialize() {
        System.out.println("🎉 Khởi tạo trang chủ Bidder thành công.");
        loadAuctionsToGrid("ALL");
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblBidderName != null) {
            lblBidderName.setText(user.getUsername());
        }

        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f đ", user.getBalance()));
        }
    }

    // =========================================================================
    // 🌟 THÊM MỚI: HÀM ĐIỀU KHIỂN ẨN / HIỆN THANH MENU BÊN TRÁI (TOGGLE)
    // =========================================================================
    @FXML
    void handleToggleSidebar(ActionEvent event) {
        if (sidebarContainer == null) return;

        if (isSidebarVisible) {
            // Ẩn hoàn toàn thanh bên trái và giải phóng khoảng trống cho nội dung chính
            sidebarContainer.setManaged(false);
            sidebarContainer.setVisible(false);
            isSidebarVisible = false;
        } else {
            // Hiện lại thanh bên trái và đẩy không gian như cũ
            sidebarContainer.setManaged(true);
            sidebarContainer.setVisible(true);
            isSidebarVisible = true;
        }
    }

    // =========================================================================
    // 🚗 PHÒNG ĐẤU GIÁ (MOUSE EVENT)
    // =========================================================================
    @FXML
    void handleRoomVehicle(MouseEvent event) {
        if (lblRoomTitle != null) {
            lblRoomTitle.setText("🚗 Phòng Đấu Giá: PHƯƠNG TIỆN (Vehicle)");
        }
        loadAuctionsToGrid("VEHICLE");
    }

    @FXML
    void handleRoomArt(MouseEvent event) {
        if (lblRoomTitle != null) {
            lblRoomTitle.setText("🎨 Phòng Đấu Giá: NGHỆ THUẬT (Art)");
        }
        loadAuctionsToGrid("ART");
    }

    @FXML
    void handleRoomElectronics(MouseEvent event) {
        if (lblRoomTitle != null) {
            lblRoomTitle.setText("⚡ Phòng Đấu Giá: ĐIỆN TỬ (Electronics)");
        }
        loadAuctionsToGrid("ELECTRONICS");
    }

    // =========================================================================
    // 🏛 HÀM XỬ LÝ SỰ KIỆN TỪ FILE FXML
    // =========================================================================
    @FXML void handleNavDashboard(ActionEvent event) { System.out.println("Sàn đấu giá"); }
    @FXML void handleNavBidHistory(ActionEvent event) { System.out.println("Lịch sử đặt giá"); }
    @FXML void handleSearch(ActionEvent event) { System.out.println("Tìm kiếm"); }
    @FXML void handleSwitchToSeller(ActionEvent event) { System.out.println("Chuyển người bán"); }

    @FXML
    void handleNavTransactionHistory(ActionEvent event) {
        switchSceneWithUser(event, "/view/TransactionHistoryView.fxml", "Elite Auction - Lịch Sử Giao Dịch", 1);
    }

    @FXML
    void handleNavDepositWithdraw(ActionEvent event) {
        switchSceneWithUser(event, "/view/DepositWithdrawView.fxml", "Elite Auction - Nạp / Rút Tiền", 2);
    }

    @FXML
    void handleNavProfile(ActionEvent event) {
        switchSceneWithUser(event, "/view/ProfileView.fxml", "Elite Auction - Hồ Sơ Cá Nhân", 3);
    }

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

    // =========================================================================
    // 📦 THUẬT TOÁN ĐỔ DỮ LIỆU VÀO GRID SẢN PHẨM MẪU (ĐÃ SỬA CO GIÃN)
    // =========================================================================
    private void loadAuctionsToGrid(String category) {
        if (gridAuctions == null) return;
        gridAuctions.getChildren().clear();

        List<Item> allItems = getFakeDataFromDatabase();
        List<Item> filteredItems = new ArrayList<>();

        for (Item item : allItems) {
            String itemCategory = item.getItemType();
            if (category.equals("ALL") || (itemCategory != null && itemCategory.equalsIgnoreCase(category))) {
                filteredItems.add(item);
            }
        }

        int columnsCount = 3;
        int currentColumn = 0;
        int currentRow = 0;

        try {
            for (Item item : filteredItems) {
                FXMLLoader loader = new FXMLLoader();
                loader.setLocation(getClass().getResource("/view/ItemCard.fxml"));
                VBox cardBox = loader.load();

                // Chú ý: Cần chắc chắn ItemCardController tồn tại trong project của bạn
                // ItemCardController cardController = loader.getController();
                // if (cardController != null) {
                //     cardController.setData(item);
                // }

                // 🌟 Ép Card sản phẩm phình to lấp đầy ô 1/3 của lưới, triệt tiêu viền trắng lỗi!
                GridPane.setHgrow(cardBox, Priority.ALWAYS);
                GridPane.setVgrow(cardBox, Priority.ALWAYS);
                cardBox.setMaxWidth(Double.MAX_VALUE);

                if (currentColumn == columnsCount) {
                    currentColumn = 0;
                    currentRow++;
                }

                gridAuctions.add(cardBox, currentColumn++, currentRow);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Item> getFakeDataFromDatabase() {
        List<Item> list = new ArrayList<>();

        list.add(new Item(1, "Bugatti La Voiture Noire", "Siêu xe đẳng cấp", "VEHICLE",
                2000000.0, "NEW", 101, "view/images/Bugatti_La_Voiture_N.png", LocalDateTime.now()) {
            @Override public String getDetailedSpecs() { return ""; }
        });

        list.add(new Item(2, "Tranh Van Gogh Cổ Ngạn", "Kiệt tác nghệ thuật", "ART",
                850000.0, "GOOD", 102, "view/images/Tranh_Van_Gogh.png", LocalDateTime.now()) {
            @Override public String getDetailedSpecs() { return ""; }
        });

        list.add(new Item(3, "Đồng hồ Romain Jerome", "Thiết bị cao cấp", "ELECTRONICS",
                150000.0, "LIKE_NEW", 103, "view/images/Đồng_hồ_Romain_Jei.png", LocalDateTime.now()) {
            @Override public String getDetailedSpecs() { return ""; }
        });

        return list;
    }

    // =========================================================================
    // 🔄 HÀM CHUYỂN TRANG THÔNG MINH (DUY TRÌ MAXIMIZED)
    // =========================================================================
    private void switchSceneWithUser(ActionEvent event, String fxmlPath, String title, int type) {
        try {
            URL fxmlLocation = getClass().getResource(fxmlPath);
            if (fxmlLocation == null) {
                System.err.println("❌ Không tìm thấy file FXML: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();
            Object controller = loader.getController();

            if (controller != null) {
                try {
                    java.lang.reflect.Method method = controller.getClass().getMethod("setUserData", User.class);
                    method.invoke(controller, this.currentUser);
                } catch (NoSuchMethodException e) {
                    System.out.println("ℹ️ Controller đích không yêu cầu nhận dữ liệu User.");
                }
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.setMaximized(true); // Đảm bảo trang sau duy trì phóng to
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Lỗi điều hướng: " + e.getMessage());
            alert.showAndWait();
        }
    }
}