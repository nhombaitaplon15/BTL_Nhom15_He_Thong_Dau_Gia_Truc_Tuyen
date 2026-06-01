
package com.auction.client.controller.bidder;
import com.auction.client.controller.bidder.BiddingHistoryController;
import com.auction.client.controller.bidder.The_Home_Page_Bidder_View_Controller;
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

    @FXML private VBox sideMenu; // Khối menu xanh lề trái chịu trách nhiệm ẩn/hiện
    @FXML private AnchorPane contentArea; // Vùng trống bên phải để nạp giao diện động
    @FXML private Label lblAccountName;
    @FXML private Label lblBalance;
    @FXML private Button btnMenuLive;
    @FXML private Button btnMenuHistory;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cấu hình dữ liệu hiển thị mặc định ban đầu cho Sidebar của bạn
        if (lblAccountName != null) lblAccountName.setText("");
        if (lblBalance != null) lblBalance.setText("0 UETệ");

        // Tự động hiển thị trang Sàn Đấu Giá khi vừa mở ứng dụng lên
        setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
    }

    /**
     * Hàm lõi chịu trách nhiệm xóa trang cũ, nạp trang mới vào vùng contentArea bên phải,
     * đồng thời thực hiện cơ chế "Cầu nối" để truyền chính nó vào các controller con.
     */
    public void setPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent node = loader.load();

            // Lấy controller của trang vừa nạp và truyền thực thể MainContainer vào nó
            Object childController = loader.getController();
            if (childController instanceof BiddingHistoryController) {
                ((BiddingHistoryController) childController).setMainContainer(this);
            } else if (childController instanceof The_Home_Page_Bidder_View_Controller) {
                ((The_Home_Page_Bidder_View_Controller) childController).setMainContainer(this);
            }

            // Xóa sạch giao diện cũ đang hiển thị ở vùng bên phải và nạp trang mới
            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);

            // Căn chỉnh nội dung mới tự động co dãn khít với vùng chứa contentArea của bạn
            AnchorPane.setTopAnchor(node, 0.0);
            AnchorPane.setBottomAnchor(node, 0.0);
            AnchorPane.setLeftAnchor(node, 0.0);
            AnchorPane.setRightAnchor(node, 0.0);

        } catch (IOException e) {
            System.err.println("LỖI ĐƯỜNG DẪN: Không tìm thấy file FXML tại: " + fxmlPath);
            e.printStackTrace();
        }
    }
    //Hàm dùng chung để ẩn/hiện thanh thực đơn (Sidebar) lề trái.
    // Được gọi bởi chính các nút bấm gạch ngang (☰) nằm ở các trang con.

    public void toggleSidebar() {
        if (sideMenu == null) return;

        if (sideMenu.isVisible()) {
            sideMenu.setVisible(false);
            sideMenu.setManaged(false); // Thu hồi không gian chiếm dụng, đẩy nội dung phải rộng ra full màn hình
            System.out.println("MainContainer: Đã đóng thanh Menu lề trái.");
        } else {
            sideMenu.setVisible(true);
            sideMenu.setManaged(true);  // Trả lại không gian hiển thị cho menu lề trái
            System.out.println("MainContainer: Đã mở thanh Menu lề trái.");
        }
    }
    // Khi click vào nút "🏛 Sàn Đấu Giá" trên Sidebar của bạn
    @FXML
    void onLiveMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        btnMenuHistory.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
    }
    // Khi click vào nút "⏱ Lịch Sử Đặt Giá" trên Sidebar của bạn
    @FXML
    void onHistoryMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        btnMenuHistory.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        setPage("/view/view/bidder/BiddingHistoryView.fxml");
    }
}