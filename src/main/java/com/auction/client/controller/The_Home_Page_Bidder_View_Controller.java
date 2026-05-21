package com.auction.client.controller;

import com.auction.client.controller.bidder.ProfileController;
import com.auction.client.controller.bidder.TransactionHistoryController;
import com.auction.client.controller.bidder.WalletController;
import com.auction.common.model.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import java.net.URL;

public class The_Home_Page_Bidder_View_Controller {

    @FXML private Label lblBidderName;
    @FXML private Label lblBalance;
    @FXML private TextField txtSearch;
    @FXML private GridPane gridAuctions;

    // Đối tượng User lưu trữ xuyên suốt
    private User currentUser;

    @FXML
    public void initialize() {
        System.out.println("🎉 Khởi tạo trang chủ Bidder thành công.");
    }

    // Nhận dữ liệu thực tế từ DB lúc Đăng nhập thành công dội sang
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblBidderName != null) {
            lblBidderName.setText(user.getUsername() != null ? user.getUsername() : user.getUsername());
        }

        if (lblBalance != null) {
            // ĐỔI %,d THÀNH %,.0f ĐỂ ĐỊNH DẠNG KIỂU DOUBLE MÀ KHÔNG BỊ HIỂN THỊ PHẦN THẬP PHÂN LẺ (.00)
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

            // 🌟 QUAN TRỌNG NHẤT: Lấy Controller của trang Ví và truyền User hiện tại sang
            com.auction.client.controller.bidder.WalletController walletController = loader.getController();
            if (walletController != null) {
                walletController.setUserData(this.currentUser); // Truyền dữ liệu User đăng nhập sang đây!
            }

            // Thực hiện chuyển màn hình
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

    @FXML void handleNavDashboard(ActionEvent event) { System.out.println("Sàn đấu giá"); }
    @FXML void handleNavBidHistory(ActionEvent event) { System.out.println("Lịch sử đặt giá"); }
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

    // HÀM CHUYỂN TRANG THÔNG MINH - ĐÃ FIX 100% LỖI NHẬN DIỆN CONTROLLER
    private void switchSceneWithUser(ActionEvent event, String fxmlPath, String title, int type) {
        try {
            URL fxmlLocation = getClass().getResource(fxmlPath);
            if (fxmlLocation == null) {
                System.err.println("❌ Không tìm thấy file FXML tại: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load(); // Nạp giao diện

            // Bắt đầu bóc tách Controller động dựa trên file FXML đã sửa ở Bước 1
            Object controller = loader.getController();

            if (controller != null) {
                if (type == 1 && controller instanceof TransactionHistoryController) {
                    ((TransactionHistoryController) controller).setUserData(this.currentUser);
                } else if (type == 2 && controller instanceof WalletController) {
                    ((WalletController) controller).setUserData(this.currentUser);
                } else if (type == 3 && controller instanceof ProfileController) {
                    ((ProfileController) controller).setUserData(this.currentUser);
                }
            } else {
                System.err.println("⚠️ Cảnh báo: File FXML chưa khai báo đúng fx:controller!");
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
}