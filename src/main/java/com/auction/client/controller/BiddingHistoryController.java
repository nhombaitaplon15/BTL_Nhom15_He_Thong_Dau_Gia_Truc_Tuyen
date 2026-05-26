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
    @FXML private Button btnViewDetail;

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

        // 1. Định dạng hiển thị tiền tệ UETệ (Bình thường màu xanh dương, ấn chọn tự động chuyển sang chữ trắng)
        colBidAmount.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%,.0f UETệ", amount));

                    // Kiểm tra trạng thái được chọn của dòng
                    TableRow<?> row = getTableRow();
                    if (row != null && row.isSelected()) {
                        setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // 2. 🎯 TỰ ĐỊNH NGHĨA CÁC KIỂU TRẠNG THÁI TIẾNG VIỆT & SỬA LỖI MÀU CHỮ KHI ẤN CHỌN
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(empty || item == null ? null : item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String s = item.toUpperCase();

                    // Dịch các từ khóa trạng thái sang chữ Tiếng Việt có icon sinh động
                    if (s.contains("SUCCESS") || s.contains("THẮNG")) setText("THẮNG CUỘC 🏆");
                    else if (s.contains("DẪN ĐẦU")) setText("ĐANG DẪN ĐẦU");
                    else if (s.contains("ĐÈ GIÁ")) setText("BỊ ĐÈ GIÁ ⚠️");
                    else if (s.contains("FAILED") || s.contains("THẤT BẠI")) setText("THẤT BẠI");
                    else setText(item);

                    // Quản lý màu sắc chữ dựa theo trạng thái dòng (Bình thường vs Khi được ấn chọn)
                    TableRow<?> row = getTableRow();
                    if (row != null && row.isSelected()) {
                        // Khi dòng đang ĐƯỢC ẤN CHỌN: Ép chữ biến thành màu trắng hoàn toàn để nổi bật trên nền xanh đậm
                        setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                    } else {
                        // Khi dòng Ở TRẠNG THÁI BÌNH THƯỜNG: Đổ màu chữ phân loại rực rỡ trên màu nền bảng gốc
                        if (s.contains("SUCCESS") || s.contains("THẮNG")) setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;"); // Chữ Xanh lá
                        else if (s.contains("DẪN ĐẦU")) setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;"); // Chữ Xanh ngọc
                        else if (s.contains("ĐÈ GIÁ")) setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");  // Chữ Màu cam
                        else if (s.contains("FAILED") || s.contains("THẤT BẠI")) setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;"); // Chữ Màu đỏ
                        else setStyle("");
                    }
                }
            }
        });

        // 3. Giữ nguyên màu nền bảng mặc định, bắt các ô tự động vẽ lại màu chữ khi click chọn
        historyTable.setRowFactory(tv -> {
            TableRow<BidHistoryRow> row = new TableRow<>();
            row.selectedProperty().addListener((obs, oldVal, newVal) -> {
                // Khi người dùng bấm click chọn dòng, ép các cell chạy lại để ăn màu chữ trắng ngay lập tức
                row.requestLayout();
            });
            return row;
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
     * 🎯 XEM CHI TIẾT TĨNH: Khi click vào dòng lịch sử đấu giá thực tế -> Hiện Pop-up tĩnh chuẩn chỉnh
     */
    @FXML
    private void handleViewDetail(javafx.event.ActionEvent event) {
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

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo hệ thống");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}