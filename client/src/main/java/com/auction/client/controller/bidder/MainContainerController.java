package com.auction.client.controller.bidder;


import com.auction.client.controller.bidder.The_Home_Page_Bidder_View_Controller;
import com.auction.client.controller.bidder.BiddingHistoryController;
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

    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (lblAccountName != null) lblAccountName.setText("");
        if (lblBalance != null) lblBalance.setText("0 UETệ");
    }

    /**
     * Nhận dữ liệu User từ LoginController truyền sang khi đăng nhập thành công
     */
    public void initUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        if (lblAccountName != null) lblAccountName.setText(user.getUsername());
        updateBalanceDisplay(user.getBalance());

        // Nạp trang chủ mặc định ngay khi cấu hình xong User
        setPage("/view/The_Home_Page_Bidder_View.fxml");
    }

    /**
     * Cập nhật số dư tiền hiển thị thời gian thực trên thanh lề trái (Sidebar)
     */
    public void updateBalanceDisplay(double balance) {
        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f UETệ", balance));
        }
    }

    /**
     * Hàm điều hướng lõi nạp các phân hệ trang động vào vùng trống bên phải
     */
    public void setPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent node = loader.load();

            // Cầu nối điều hướng truyền dữ liệu tập trung xuống các Controller con
            Object childController = loader.getController();

            if (childController instanceof The_Home_Page_Bidder_View_Controller) {
                The_Home_Page_Bidder_View_Controller home = (The_Home_Page_Bidder_View_Controller) childController;
                home.setMainContainer(this);
                home.setUserData(this.currentUser);
            }
            else if (childController instanceof BiddingHistoryController) {
                BiddingHistoryController history = (BiddingHistoryController) childController;
                history.setMainContainer(this);
                history.setUserData(this.currentUser);
            }

            // Dọn dẹp ruột trang cũ và nhét giao diện mới vào vùng chứa
            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);

            // Neo chặt 4 góc đảm bảo trang con tự động co giãn full màn hình
            AnchorPane.setTopAnchor(node, 0.0);
            AnchorPane.setBottomAnchor(node, 0.0);
            AnchorPane.setLeftAnchor(node, 0.0);
            AnchorPane.setRightAnchor(node, 0.0);

        } catch (IOException e) {
            System.err.println("LỖI ĐƯỜNG DẪN: Không tìm thấy file FXML tại: " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Hàm đóng/mở thanh thực đơn lề trái. Được gọi từ chính nút (☰) ở các trang con.
     */
    public void toggleSidebar() {
        if (sideMenu == null) return;

        boolean isVisible = !sideMenu.isVisible();
        sideMenu.setVisible(isVisible);
        sideMenu.setManaged(isVisible); // Thu hồi diện tích chiếm dụng giúp trang con phóng to ra full cửa sổ
    }

    @FXML
    void onLiveMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        btnMenuHistory.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        setPage("/view/The_Home_Page_Bidder_View.fxml");
    }

    @FXML
    void onHistoryMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        btnMenuHistory.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        setPage("/view/BiddingHistoryView.fxml");
    }
}