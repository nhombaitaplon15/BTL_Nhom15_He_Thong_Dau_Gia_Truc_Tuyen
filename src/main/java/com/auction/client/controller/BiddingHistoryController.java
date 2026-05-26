package com.auction.client.controller;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.server.dao.BiddingHistoryDAO;
import com.auction.server.dao.UserDAO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class BiddingHistoryController implements Initializable {

    @FXML private TextField txtSearch;
    @FXML private TableView<BidHistoryRow> historyTable;
    @FXML private TableColumn<BidHistoryRow, Integer> colId;
    @FXML private TableColumn<BidHistoryRow, Integer> colAuctionId;
    @FXML private TableColumn<BidHistoryRow, String> colItemName;
    @FXML private TableColumn<BidHistoryRow, Double> colBidAmount;
    @FXML private TableColumn<BidHistoryRow, String> colBidTime;
    @FXML private TableColumn<BidHistoryRow, String> colStatus;

    private final ObservableList<BidHistoryRow> historyList = FXCollections.observableArrayList();
    private final BiddingHistoryDAO historyDAO = new BiddingHistoryDAO();
    private final UserDAO userDAO = new UserDAO();
    private User currentUser;
    private Scene homeScene;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSearch();
    }

    public void setMainHomeController(Scene homeScene) {
        this.homeScene = homeScene;
    }

    /**
     * Nhận dữ liệu User online và tự động cào dữ liệu lịch sử từ Database lên bảng
     */
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        // ⚡ KẾT NỐI DATABASE: Tải dữ liệu lịch sử đấu giá thật của User này
        loadHistory();
    }

    private void setupTable() {
        // Ánh xạ các cột trên TableView chuẩn xác theo thuộc tính của Model BidHistoryRow
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colBidTime.setCellValueFactory(new PropertyValueFactory<>("bidTime"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Định dạng hiển thị tiền tệ UETệ động từ DB mượt mà, chuyên nghiệp
        colBidAmount.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f UETệ", amount));
                }
            }
        });
    }

    /**
     * ⚡ TRUY VẤN DATABASE: Gọi hàm lấy lịch sử chuẩn SQL từ BiddingHistoryDAO
     */
    private void loadHistory() {
        if (currentUser == null) return;

        new Thread(() -> {
            try {
                // Đọc trực tiếp từ bảng public.bidding_history theo user_id thật
                List<BidHistoryRow> list = historyDAO.getHistoryByUser(currentUser.getId());

                Platform.runLater(() -> {
                    historyList.setAll(list);
                    historyTable.setItems(historyList);
                });
            } catch (Exception e) {
                System.err.println("❌ Lỗi kết nối Database khi tải bảng lịch sử đặt giá!");
                e.printStackTrace();
            }
        }).start();
    }

    private void setupSearch() {
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                historyTable.setItems(historyList);
                return;
            }
            List<BidHistoryRow> filtered = historyList.stream()
                    .filter(item -> item.getItemName() != null && item.getItemName().toLowerCase().contains(newValue.toLowerCase()))
                    .collect(Collectors.toList());
            historyTable.setItems(FXCollections.observableArrayList(filtered));
        });
    }

    /**
     * ⚡ LÀM MỚI DATABASE: Đồng bộ số dư ví tiền và lịch sử mới nhất từ DB
     */
    @FXML
    private void handleRefresh() {
        if (currentUser != null) {
            new Thread(() -> {
                try {
                    // Lấy số tiền thực tế trong bảng public.users để cập nhật
                    double actualBalance = userDAO.getBalance(currentUser.getId());
                    Platform.runLater(() -> currentUser.setBalance(actualBalance));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
        loadHistory();
    }

    @FXML
    private void onLiveMenuClick() {
        try {
            if (homeScene != null) {
                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(homeScene);
                stage.setTitle("Elite Auction - Sàn Đấu Giá");
                stage.show();
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
                Parent root = loader.load();
                The_Home_Page_Bidder_View_Controller homeController = loader.getController();
                if (homeController != null) homeController.setUserData(this.currentUser);

                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(new Scene(root, 1280, 720));
                stage.show();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleReportIssue() {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một lượt đặt giá từ bảng để báo cáo sự cố!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ReportIssueView.fxml"));
            Parent root = loader.load();
            ReportIssueController dialogController = loader.getController();
            if (dialogController != null) dialogController.setIssueData(selected, this.currentUser);

            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root));
            dialogStage.setTitle("Báo Cáo Sự Cố Phiên Đấu Giá");
            dialogStage.showAndWait();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * 🎯 XEM CHI TIẾT TĨNH: Khi click vào dòng lịch sử đấu giá thực tế $\rightarrow$ Hiện Pop-up tĩnh chuẩn chỉnh
     */
    @FXML
    private void handleViewDetail() {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một dòng phiên đấu giá trong bảng lịch sử để xem!");
            return;
        }

        try {
            // ✅ ĐỒNG BỘ 100%: Gọi chính xác file giao diện Pop-up chi tiết tĩnh (700x500) của bạn
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionDetailView.fxml"));
            Parent root = loader.load();

            // Ánh xạ sang đúng AuctionDetailController tĩnh xử lý dữ liệu
            AuctionDetailController detailController = loader.getController();
            if (detailController != null) {
                // Nạp mã phiên thực tế và tên từ Database sang cho cửa sổ nhỏ hiển thị
                detailController.loadAuctionDetail(selected.getAuctionId(), selected.getItemName(), this.currentUser);
            }

            // Tạo Stage cửa sổ Popup nhỏ nằm đè lên trước
            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root, 700, 500)); // Ép chặt kích thước chuẩn khung AnchorPane của bạn
            dialogStage.setTitle("Thông Tin Chi Tiết Phiên Đấu Giá - #" + selected.getAuctionId());
            dialogStage.setResizable(false); // Cố định khung hình tĩnh sạch sẽ, không méo vỡ giao diện
            dialogStage.show();

        } catch (Exception e) {
            System.err.println("❌ Lỗi: Không thể khởi tạo màn hình xem chi tiết phiên đấu giá tĩnh từ Database!");
            e.printStackTrace();
        }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo hệ thống");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    // 1. Thêm khai báo nút bấm đã bổ sung ID ở Scene Builder vào đầu Controller
    @FXML private Button btnViewDetail;

    /**
     * 🎯 XEM CHI TIẾT TĨNH: Khi click vào dòng lịch sử đấu giá thực tế -> Hiện Pop-up tĩnh chuẩn chỉnh
     */
    @FXML
    private void handleViewDetail(javafx.event.ActionEvent event) { // 🎯 Nên thêm tham số ActionEvent để lấy Stage an toàn
        // 1. Kiểm tra xem người dùng đã chọn dòng nào trên TableView chưa
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một dòng phiên đấu giá trong bảng lịch sử để xem!");
            return;
        }

        try {
            // 2. Gọi chính xác file giao diện Pop-up chi tiết (700x500)
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionDetailView.fxml"));
            Parent root = loader.load();

            // Ánh xạ sang đúng AuctionDetailController xử lý dữ liệu
            AuctionDetailController detailController = loader.getController();
            if (detailController != null) {
                // Nạp mã phiên thực tế và tên từ Database sang cho cửa sổ nhỏ hiển thị
                detailController.loadAuctionDetail(selected.getAuctionId(), selected.getItemName(), this.currentUser);
            }

            // 3. Tạo Stage cửa sổ Popup nhỏ nằm đè lên trước độc lập
            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root, 700, 500)); // Ép chặt kích thước chuẩn khung AnchorPane của bạn
            dialogStage.setTitle("Thông Tin Chi Tiết Phiên Đấu Giá - #" + selected.getAuctionId());

            // Đóng băng màn hình chính phía sau, bắt buộc người dùng tương tác xong popup mới quay lại được
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            // Lấy Stage gốc từ chính nút bấm vừa được click để làm chủ thể sở hữu (Owner)
            Stage ownerStage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            dialogStage.initOwner(ownerStage);

            dialogStage.setResizable(false); // Cố định khung hình tĩnh sạch sẽ, không méo vỡ giao diện
            dialogStage.show();

        } catch (Exception e) {
            System.err.println("❌ Lỗi: Không thể khởi tạo màn hình xem chi tiết phiên đấu giá tĩnh từ Database!");
            e.printStackTrace();
        }
    }
}