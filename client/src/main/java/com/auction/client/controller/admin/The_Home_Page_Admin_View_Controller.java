package com.auction.client.controller.admin; // [SỬA] client.controller.admin -> com.auction.client.controller.admin

import MessageRouter;
import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Controller trang chủ Admin.
 *
 * CÁC LỖI ĐÃ SỬA SO VỚI FILE GỐC:
 * 1. [SỬA] Package: client.controller.admin -> com.auction.client.controller.admin
 * 2. [SỬA] Đường dẫn FXML: /view/... -> /view/view/... (đúng với cấu trúc resources)
 * 3. [THÊM] setUserData() - nhận User object sau khi login
 * 4. [THÊM] Đăng ký realtime handler: ADMIN_NEW_PENDING_AUCTION
 *           (khi Seller tạo phiên mới -> server push tới Admin online -> hiện thông báo)
 * 5. [THÊM] Điều hướng truyền User object sang màn hình con
 *
 * ĐẶT TẠI: client/src/main/java/com/auction/client/controller/admin/The_Home_Page_Admin_View_Controller.java
 */
public class The_Home_Page_Admin_View_Controller {

    @FXML private Label lblAdminName;
    @FXML private Label lblPendingCount;    // Label hiển thị số phiên chờ duyệt

    private User currentUser;

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @FXML
    public void initialize() {
        registerRealtimeHandlers();
        System.out.println("[ADMIN] Trang chủ Admin đã khởi tạo.");
    }

    /**
     * Được gọi từ LoginController sau khi đăng nhập.
     */
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        if (lblAdminName != null)
            lblAdminName.setText("Admin: " + user.getUsername());
    }

    /**
     * Đăng ký handler nhận thông báo Seller tạo phiên mới (push realtime).
     */
    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_NEW_PENDING_AUCTION, this::onNewPendingAuction);
    }

    // =========================================================
    // REALTIME HANDLER
    // =========================================================

    /**
     * [REALTIME PUSH] Nhận thông báo khi Seller vừa gửi phiên mới lên chờ duyệt.
     * Server broadcast tới tất cả Admin đang online.
     */
    private void onNewPendingAuction(Message message) {
        Auction auction = (Auction) message.getPayload();
        if (auction == null) return;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("📋 Phiên Mới Cần Duyệt!");
        alert.setHeaderText("Seller vừa gửi yêu cầu phiên đấu giá mới.");
        alert.setContentText("Mã phiên: #" + auction.getAuctionId()
                + "\nSản phẩm: #" + auction.getItemId()
                + "\nNgười bán: #" + auction.getSellerId()
                + "\n\nHãy vào trang Quản Lý Phiên để xem và duyệt.");
        alert.show();

        // Cập nhật badge số phiên chờ
        if (lblPendingCount != null) {
            try {
                int current = Integer.parseInt(lblPendingCount.getText().replaceAll("[^0-9]", ""));
                lblPendingCount.setText("Chờ duyệt: " + (current + 1));
            } catch (NumberFormatException e) {
                lblPendingCount.setText("Chờ duyệt: 1");
            }
        }
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    @FXML
    public void goToAuctionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Auction_Page_Admin_View.fxml",
                The_Auction_Page_Admin_View_Controller.class);
    }

    @FXML
    public void goToTransactionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Transaction_Page_Admin_View.fxml",
                The_Transaction_Page_Admin_View_Controller.class);
    }

    @FXML
    public void goToSettingsPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Settings_Page_Admin_View.fxml",
                The_Settings_Page_Admin_View_Controller.class);
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_NEW_PENDING_AUCTION);
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/view/LoginView.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void switchPage(ActionEvent event, String fxmlPath, Class<?> controllerClass) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            // Truyền User object sang màn hình con
            Object controller = loader.getController();
            if (controller instanceof The_Auction_Page_Admin_View_Controller) {
                ((The_Auction_Page_Admin_View_Controller) controller).setUserData(currentUser);
            } else if (controller instanceof The_Transaction_Page_Admin_View_Controller) {
                ((The_Transaction_Page_Admin_View_Controller) controller).setUserData(currentUser);
            } else if (controller instanceof The_Settings_Page_Admin_View_Controller) {
                ((The_Settings_Page_Admin_View_Controller) controller).setUserData(currentUser);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
}