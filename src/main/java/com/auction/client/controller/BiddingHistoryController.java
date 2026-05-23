package com.auction.client.controller;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User; // Đảm bảo đúng class User hệ thống của bạn
import com.auction.server.dao.BiddingHistoryDAO;
import com.auction.server.dao.UserDAO;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class BiddingHistoryController implements Initializable {

    @FXML private VBox sideMenu; // Khai báo để xử lý ẩn hiện sidebar menu
    @FXML private Label lblAccountName;
    @FXML private Label lblBalance;
    @FXML private TextField txtSearch;

    @FXML private TableView<BidHistoryRow> historyTable;
    @FXML private TableColumn<BidHistoryRow, Integer> colId;
    @FXML private TableColumn<BidHistoryRow, Integer> colAuctionId;
    @FXML private TableColumn<BidHistoryRow, String> colItemName;
    @FXML private TableColumn<BidHistoryRow, Double> colBidAmount;
    @FXML private TableColumn<BidHistoryRow, String> colBidTime;
    @FXML private TableColumn<BidHistoryRow, String> colStatus;

    private ObservableList<BidHistoryRow> historyList = FXCollections.observableArrayList();
    private BiddingHistoryDAO historyDAO = new BiddingHistoryDAO();
    private UserDAO userDAO = new UserDAO();

    // Dữ liệu User kết nối thực tế
    private User currentUser;
    private MainContainerController mainContainer;

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSearch();
    }

    /**
     * HÀM NHẬN DỮ LIỆU USER ĐỘNG ĐÃ ĐƯỢC ĐƯA RA NGOÀI ĐỘC LẬP - HẾT LỖI LỒNG HÀM
     */
    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        // Cập nhật thông tin hiển thị và định dạng VND đẹp mắt
        if (lblAccountName != null) {
            lblAccountName.setText(user.getUsername() != null ? user.getUsername() : "Khách");
        }
        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f UETệ", user.getBalance()));
        }

        // Gọi nạp dữ liệu động từ DB theo ID tài khoản thực tế
        loadHistory();
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colBidTime.setCellValueFactory(new PropertyValueFactory<>("bidTime"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    /**
     * Tải dữ liệu bằng luồng Thread ngầm dựa trên ID User thực tế
     */
    private void loadHistory() {
        if (currentUser == null) return;

        new Thread(() -> {
            try {
                // Lấy ID trực tiếp từ currentUser đang đăng nhập hệ thống
                List<BidHistoryRow> list = historyDAO.getHistoryByUser(currentUser.getId());

                Platform.runLater(() -> {
                    historyList.setAll(list);
                    historyTable.setItems(historyList);
                });
            } catch (Exception e) {
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
                    .filter(item -> item.getItemName() != null &&
                            item.getItemName().toLowerCase().contains(newValue.toLowerCase()))
                    .collect(Collectors.toList());

            historyTable.setItems(FXCollections.observableArrayList(filtered));
        });
    }

    @FXML
    private void handleRefresh() {
        if (currentUser != null) {
            double currentBalance = userDAO.getBalance(currentUser.getId());
            currentUser.setBalance(currentBalance);
            lblBalance.setText(String.format("%,.0f đ", currentBalance));
        }
        loadHistory();
    }

    // ==========================================
    // CÁC HÀM SỰ KIỆN SIDEBAR MENU (CHUYỂN TRANG CHUẨN)
    // ==========================================

    @FXML
    private void onLiveMenuClick() {
        try {
            System.out.println("🏛 Đang quay lại Sàn Đấu Giá Live...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
            Parent root = loader.load();

            // Truyền trả dữ liệu user hiện tại về lại trang chủ
            The_Home_Page_Bidder_View_Controller homeController = loader.getController();
            if (homeController != null) {
                homeController.setUserData(this.currentUser);
            }

            Stage stage = (Stage) txtSearch.getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.setTitle("Elite Auction - Sàn Đấu Giá");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML private void onHistoryMenuClick() { System.out.println("Bạn đang ở trang Lịch sử đặt giá rồi."); }
    @FXML private void onTransactionsMenuClick() { System.out.println("Transactions"); }
    @FXML private void onWalletMenuClick() { System.out.println("Wallet"); }
    @FXML private void onProfileMenuClick() { System.out.println("Profile"); }
    @FXML private void onSwitchRoleClick() { System.out.println("Switch role"); }

    @FXML
    private void onLogoutClick() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/LoginView.fxml"));
            Stage stage = (Stage) txtSearch.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Trang đấu giá trực tuyến - Nhóm 15");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleToggleMenu() {
        if (sideMenu != null) {
            boolean isVisible = !sideMenu.isVisible();
            sideMenu.setVisible(isVisible);
            sideMenu.setManaged(isVisible);
        }
    }
    @FXML
    private void handleReportIssue() {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText("Vui lòng chọn phiên đấu giá gặp sự cố trong danh sách để báo cáo!");
            alert.showAndWait();
            return;
        }

        try {
            // Đường dẫn đến file FXML Báo cáo lỗi của bạn
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ReportIssueView.fxml"));
            Parent root = loader.load();

            // Gọi Controller báo cáo lỗi và truyền cả dòng chọn + User đang đăng nhập sang
            ReportIssueController dialogController = loader.getController();
            if (dialogController != null) {
                dialogController.setIssueData(selected, this.currentUser); // Nhớ dùng hàm nhận 2 tham số đã sửa ở bước trước
            }

            // Tạo Stage dạng Pop-up
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Báo Cáo Sự Cố - Phiên #" + selected.getAuctionId());
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(historyTable.getScene().getWindow());
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            dialogStage.showAndWait();

        } catch (Exception e) {
            System.err.println("❌ Lỗi không thể mở cửa sổ báo cáo sự cố!");
            e.printStackTrace();
        }
    }
    @FXML
    private void handleViewDetail() {
        System.out.println("Không");
    }
}