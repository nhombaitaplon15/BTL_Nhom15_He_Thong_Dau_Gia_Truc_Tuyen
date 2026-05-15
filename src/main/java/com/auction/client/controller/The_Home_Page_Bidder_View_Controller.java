package com.auction.client.controller;

import com.auction.common.model.Items;
import com.auction.service.ItemService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class The_Home_Page_Bidder_View_Controller implements Initializable {
    @FXML
    private FlowPane flowPaneItem;
    private List<Items> list = new ArrayList<>();
    private ItemService itemService = new ItemService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadItemsToUI();
        loadUserProfile();
    }

    private void refreshPage() {
        // Kiểm tra xem txtSearch đã được load chưa trước khi clear
        if (txtSearch != null) {
            txtSearch.clear();
        }

        if (flowPaneItem != null) {
            flowPaneItem.getChildren().clear();
            loadItemsToUI();
        }

        System.out.println("Đã cập nhật danh sách sản phẩm mới nhất.");
    }

    public void loadItemsToUI() {
        List<Items> items = itemService.getAllItems();

        flowPaneItem.getChildren().clear();

        for (Items item : items) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ItemCard.fxml"));
                Parent card = loader.load();
                ItemCardController controller = loader.getController();
                controller.setData(item);

                flowPaneItem.getChildren().add(card);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void Welcome_back(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/WelcomeView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    private TextField txtSearch;
    @FXML
    private Button btnSearch;
    @FXML
    private Button bntRefresh;

    @FXML
    void handleSearch(ActionEvent event) {
        String keyword = txtSearch.getText().toLowerCase().trim(); // Lấy chữ người dùng nhập

        // 1. Lấy tất cả item từ service
        List<Items> allItems = itemService.getAllItems();

        // 2. Xóa hết các card cũ đang hiện
        flowPaneItem.getChildren().clear();

        // 3. Lọc và hiển thị
        for (Items item : allItems) {
            // Kiểm tra nếu tên sản phẩm chứa từ khóa (không phân biệt hoa thường)
            if (item.getName().toLowerCase().contains(keyword)) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ItemCard.fxml"));
                    Parent card = loader.load();
                    ItemCardController controller = loader.getController();
                    controller.setData(item);

                    flowPaneItem.getChildren().add(card);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        // Gọi hàm xử lý logic bên dưới
        refreshPage();
        System.out.println("Nút Refresh đã được bấm!");
    }

    @FXML
    void handleGoHistory(ActionEvent event) {
        System.out.println("Đang chuyển sang trang Lịch sử đấu giá...");
        // Gọi hàm chuyển cảnh dùng chung
        switchToScene("/com/auction/client/view/History_View.fxml");
    }

    @FXML
    void handleLogout(ActionEvent event) {
        System.out.println("Đang thực hiện đăng xuất...");
        // 1. Bạn có thể thêm code xóa Session/Thông tin user ở đây nếu có

        // 2. Chuyển về màn hình Đăng nhập hoặc màn hình Chào mừng
        switchToScene("/com/auction/client/view/Welcome_View.fxml");
    }

    /**
     * Hàm hỗ trợ chuyển đổi giao diện (Scene)
     *
     * @param fxmlPath Đường dẫn đến file FXML của trang mới
     */
    private void switchToScene(String fxmlPath) {
        try {
            // Tải file FXML mới
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            // Lấy Stage hiện tại
            // LƯU Ý: Vì MenuItem không phải là Node, ta lấy Stage qua flowPaneItem (hoặc bất kỳ Node nào khác bạn có)
            Stage stage = (Stage) flowPaneItem.getScene().getWindow();

            // Thiết lập Scene mới và hiển thị
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            System.err.println("Lỗi: Không tìm thấy file FXML tại đường dẫn: " + fxmlPath);
            e.printStackTrace();
        } catch (NullPointerException e) {
            System.err.println("Lỗi: flowPaneItem bị null hoặc đường dẫn FXML sai.");
            e.printStackTrace();
        }
    }

    @FXML
    private Circle circleAvatar;
    @FXML
    private Label lblFullName;
    @FXML
    private Label lblBalance;
    @FXML
    private Button btnMessage;
    @FXML
    private Button btnNotification;

    private void loadUserProfile() {
        // Giả lập dữ liệu người dùng
        String name = "Lương Phan";
        String balance = "15.000.000 đ";
        String avatarPath = "/view/images/AvatarBidder.jpeg"; // Đường dẫn tới ảnh của bạn
        // Đưa dữ liệu lên Label
        lblFullName.setText(name);
        lblBalance.setText("Số dư: " + balance);
        // Bo tròn ảnh bằng ImagePattern
        try {
            Image img = new Image(getClass().getResourceAsStream(avatarPath));
            if (img != null) {
                circleAvatar.setFill(new ImagePattern(img));
            }
        } catch (Exception e) {
            System.out.println("Chưa có file ảnh, đang để màu mặc định.");
            circleAvatar.setFill(javafx.scene.paint.Color.web("#CCCCCC"));
        }
    }
    @FXML
    private MenuItem menuHome;
    @FXML
    void handleGoHome(ActionEvent event) {
        try {
            // Đường dẫn đến file FXML của trang chủ
            String fxmlPath = "/com/auction/client/view/The_Home_Page_Bidder_View.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            // Lấy Stage hiện tại (lưu ý: lấy qua một component có ID như flowPaneItem)
            Stage stage = (Stage) flowPaneItem.getScene().getWindow();

            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();

            System.out.println("Đã chuyển về Trang chủ!");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Lỗi khi chuyển trang: " + e.getMessage());
        }
    }
}
