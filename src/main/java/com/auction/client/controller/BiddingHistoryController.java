package com.auction.client.controller;

import com.auction.common.model.BiddingHistory;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.time.LocalDateTime;

public class BiddingHistoryController {

    // ================= KHAI BÁO CÁC THÀNH PHẦN GIAO DIỆN (ĐÃ CẬP NHẬT) =================

    @FXML private VBox sideMenu;                // Toàn bộ khối menu lề trái cần ẩn/hiện
    @FXML private Button btnToggleMenu;         // Nút bấm hình ba dấu gạch ngang ☰
    @FXML private Label lblAccountName;         // Nhãn hiển thị tên tài khoản người dùng
    @FXML private Label lblBalance;             // Nhãn hiển thị số dư tài khoản
    @FXML private Button btnSwitchRole;         // Nút chuyển vai trò sang Người bán

    // Vùng chứa nội dung chính bên phải AnchorPane
    @FXML private AnchorPane contentArea;

    // Định danh các nút trên thanh Menu bên lề trái
    @FXML private Button btnMenuLive;
    @FXML private Button btnMenuHistory;
    @FXML private Button btnMenuTransactions;
    @FXML private Button btnMenuWallet;
    @FXML private Button btnMenuProfile;
    @FXML private Button btnLogout;

    // Các thành phần giao diện bảng dữ liệu bên phải
    @FXML private TableView<BiddingHistory> historyTable;
    @FXML private TableColumn<BiddingHistory, Integer> colId;
    @FXML private TableColumn<BiddingHistory, Integer> colAuctionId;
    @FXML private TableColumn<BiddingHistory, String> colItemName;
    @FXML private TableColumn<BiddingHistory, Double> colBidAmount;
    @FXML private TableColumn<BiddingHistory, LocalDateTime> colBidTime;
    @FXML private TableColumn<BiddingHistory, String> colStatus;
    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh;

    @FXML
    public void initialize() {
        System.out.println("Giao diện Lịch sử đặt giá đã nạp thành công.");

        // Cấu hình dữ liệu hiển thị mặc định ban đầu cho khối tài khoản mới
        if (lblAccountName != null) lblAccountName.setText("Nguyễn Thị Hà");
        if (lblBalance != null) lblBalance.setText("0 đ");

        // Cấu hình hiển thị TableView - Tự động Map các biến từ class BiddingHistory vào từng cột giao diện
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colBidTime.setCellValueFactory(new PropertyValueFactory<>("bidTime"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    // ================= LẬP TRÌNH LOGIC CHO CÁC TÍNH NĂNG MỚI THÊM =================

    /**
     * Hành động: Ấn nút ☰ để bật / tắt thanh menu lề trái.
     * Kết hợp setVisible và setManaged giúp phần bảng bên phải tự co dãn chiếm trọn màn hình khi ẩn sidebar.
     */
    @FXML
    void handleToggleMenu(ActionEvent event) {
        if (sideMenu.isVisible()) {
            sideMenu.setVisible(false);
            sideMenu.setManaged(false); // Thu hồi không gian chiếm dụng của menu lề trái
            System.out.println("Hành động: Đã đóng thanh Menu.");
        } else {
            sideMenu.setVisible(true);
            sideMenu.setManaged(true);  // Trả lại không gian hiển thị cho menu lề trái
            System.out.println("Hành động: Đã mở thanh Menu.");
        }
    }

    /**
     * Hành động: Nhấp chọn chuyển đổi phân hệ quản lý của người bán hàng.
     */
    @FXML
    void onSwitchRoleClick(ActionEvent event) {
        System.out.println("Hành động: Chuyển sang giao diện quản lý của Người Bán...");
        // Tích hợp logic chuyển hướng View của Người bán tại đây, ví dụ:
        // switchPage("/com/auction/view/SellerDashboardView.fxml");
    }

    // ================= 1. XỬ LÝ 3 NÚT CHỨC NĂNG CHÍNH TRÊN TRANG LỊCH SỬ =================

    @FXML
    void handleRefresh(ActionEvent event) {
        System.out.println("Hành động: Đang thực hiện làm mới (Refresh) danh sách lịch sử đặt giá...");
    }

    @FXML
    void handleViewDetail(ActionEvent event) {
        System.out.println("Hành động: Kiểm tra dòng được chọn để xem chi tiết phiên đấu giá...");

        Object selectedItem = historyTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText("Vui lòng chọn một dòng lịch sử trong bảng để xem chi tiết!");
            alert.showAndWait();
        } else {
            System.out.println("Đang mở thông tin chi tiết cho vật phẩm: " + selectedItem.toString());
        }
    }

    @FXML
    void handleReportIssue(ActionEvent event) {
        System.out.println("Hành động: Người dùng bấm báo cáo sự cố cố hệ thống.");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Hỗ Trợ Hệ Thống");
        alert.setHeaderText("Báo Cáo Sự Cố");
        alert.setContentText("Yêu cầu hỗ trợ của bạn đã được ghi nhận. Ban quản trị sẽ liên hệ sớm nhất!");
        alert.showAndWait();
    }

    // ================= 2. XỬ LÝ CÁC HÀNH ĐỘNG ĐIỀU HƯỚNG TRÊN MENU TRÁI =================

    @FXML
    void onLiveMenuClick(ActionEvent event) {
        System.out.println("Chuyển sang giao diện: Sàn Đấu Giá Live");
        switchPage("/com/auction/view/LiveAuctionView.fxml");
    }

    @FXML
    void onHistoryMenuClick(ActionEvent event) {
        System.out.println("Bạn đang đứng sẵn ở trang Lịch Sử Đặt Giá.");
    }

    @FXML
    void onTransactionsMenuClick(ActionEvent event) {
        System.out.println("Chuyển sang giao diện: Lịch Sử Giao Dịch");
        switchPage("/com/auction/view/TransactionHistoryView.fxml");
    }

    @FXML
    void onWalletMenuClick(ActionEvent event) {
        System.out.println("Chuyển sang giao diện: Nạp / Rút Tiền");
        switchPage("/com/auction/view/WalletView.fxml");
    }

    @FXML
    void onProfileMenuClick(ActionEvent event) {
        System.out.println("Chuyển sang giao diện: Hồ Sơ Cá Nhân");
        switchPage("/com/auction/view/ProfileView.fxml");
    }

    @FXML
    void onLogoutClick(ActionEvent event) {
        System.out.println("Thực hiện xoá session và chuyển hướng về màn hình Login ban đầu...");
    }

    // ================= HÀM HỖ TRỢ CHUYỂN TRANG ĐỘNG KHÔNG MẤT SIDEBAR BÊN TRÁI =================
    private void switchPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent newPage = loader.load();

            contentArea.getChildren().clear();

            AnchorPane.setTopAnchor(newPage, 0.0);
            AnchorPane.setBottomAnchor(newPage, 0.0);
            AnchorPane.setLeftAnchor(newPage, 0.0);
            AnchorPane.setRightAnchor(newPage, 0.0);

            contentArea.getChildren().add(newPage);

        } catch (IOException e) {
            System.err.println("Lỗi nghiêm trọng: Không thể tải được file giao diện tại vị trí: " + fxmlPath);
            e.printStackTrace();
        }
    }
}