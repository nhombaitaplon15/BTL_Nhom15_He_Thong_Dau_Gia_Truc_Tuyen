package com.auction.client.controller.bidder;

import com.auction.common.model.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainContainerController implements Initializable {

    @FXML private VBox sideMenu;
    @FXML private AnchorPane contentArea;
    @FXML private Label lblAccountName;
    @FXML private Label lblBalance;
    @FXML private Button btnMenuLive;
    @FXML private Button btnMenuHistory;

    // 🔥 THÊM: Biến lưu trữ thông tin User toàn cục cho toàn bộ các màn hình con
    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // XÓA DÒNG setPage Ở ĐÂY.
        // Chúng ta sẽ nạp trang Sàn Đấu Giá SAU KHI nhận được thông tin User từ màn hình Login.
        if (lblAccountName != null) lblAccountName.setText("Đang tải...");
        if (lblBalance != null) lblBalance.setText("0 đ");
    }

    /**
     * 🔥 HÀM MỚI: Bơm dữ liệu User từ màn hình Login sang đây.
     * Cập nhật thông tin lên Sidebar màu xanh, sau đó mới nạp giao diện con.
     */
    public void setUserData(User user) {
        this.currentUser = user;

        // Render thông tin lên Sidebar
        if (lblAccountName != null) {
            String displayName = (user.getUsername() != null && !user.getUsername().trim().isEmpty())
                    ? user.getUsername()
                    : user.getUsername();
            lblAccountName.setText(displayName);
        }

        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f đ", user.getBalance()));
        }

        // BÂY GIỜ mới tự động hiển thị trang Sàn Đấu Giá
        setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
    }

    /**
     * 🔥 HÀM MỚI: Gọi hàm này từ các controller con khi User nạp tiền, rút tiền hoặc bị trừ tiền cọc.
     */
    public void updateBalance(double newBalance) {
        if (this.currentUser != null) {
            this.currentUser.setBalance(newBalance);
        }
        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f đ", newBalance));
        }
    }

    public void setPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent node = loader.load();

            Object childController = loader.getController();

            // Ép kiểu và truyền dữ liệu cho controller con
            if (childController instanceof BiddingHistoryController) {
                ((BiddingHistoryController) childController).setMainContainer(this);
            } else if (childController instanceof The_Home_Page_Bidder_View_Controller) {
                The_Home_Page_Bidder_View_Controller homeCtrl = (The_Home_Page_Bidder_View_Controller) childController;
                homeCtrl.setMainContainer(this);
                // Truyền tiếp User xuống trang con để trang con còn gọi API
                if (this.currentUser != null) {
                    homeCtrl.setUserData(this.currentUser);
                }
            }

            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);

            AnchorPane.setTopAnchor(node, 0.0);
            AnchorPane.setBottomAnchor(node, 0.0);
            AnchorPane.setLeftAnchor(node, 0.0);
            AnchorPane.setRightAnchor(node, 0.0);

        } catch (IOException e) {
            System.err.println("LỖI ĐƯỜNG DẪN: Không tìm thấy file FXML tại: " + fxmlPath);
            e.printStackTrace();
        }
    }

    public void toggleSidebar() {
        if (sideMenu == null) return;
        if (sideMenu.isVisible()) {
            sideMenu.setVisible(false);
            sideMenu.setManaged(false);
        } else {
            sideMenu.setVisible(true);
            sideMenu.setManaged(true);
        }
    }

    @FXML
    void onLiveMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        btnMenuHistory.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
    }

    @FXML
    void onHistoryMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        btnMenuHistory.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        setPage("/view/view/bidder/BiddingHistoryView.fxml");
    }
    // 1. Khai báo thêm 3 nút mới (Nếu em muốn làm hiệu ứng đổi màu khi click)
    @FXML private Button btnMenuTransaction;
    @FXML private Button btnMenuWallet;
    @FXML private Button btnMenuProfile;

    // 2. Thêm 3 hàm xử lý sự kiện chuyển trang
    @FXML
    void onTransactionMenuClick(ActionEvent event) {
        // Nạp trang Lịch Sử Giao Dịch
        setPage("/view/view/bidder/TransactionHistoryView.fxml");
    }

    @FXML
    void onWalletMenuClick(ActionEvent event) {
        // Nạp trang Nạp / Rút Tiền
        setPage("/view/view/bidder/DepositWithdrawView.fxml");
    }

    @FXML
    void onProfileMenuClick(ActionEvent event) {
        // Nạp trang Hồ Sơ Cá Nhân
        setPage("/view/view/bidder/ProfileView.fxml");
    }
}