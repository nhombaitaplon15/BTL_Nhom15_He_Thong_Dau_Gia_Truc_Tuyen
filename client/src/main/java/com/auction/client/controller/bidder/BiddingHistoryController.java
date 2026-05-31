package com.auction.client.controller.bidder;

import com.auction.client.controller.bidder.MainContainerController;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;

public class BiddingHistoryController {

    // ─── MAP CHUẨN FX:ID THEO ĐÚNG FILE FXML CỦA BẠN ───
    @FXML private VBox sideMenu;
    @FXML private Label lblAccountName;
    @FXML private Label lblBalance;
    @FXML private Button btnMenuLive;
    @FXML private Button btnMenuHistory;
    @FXML private Button btnMenuTransactions;
    @FXML private Button btnMenuWallet;
    @FXML private Button btnMenuProfile;
    @FXML private Button btnSwitchRole;
    @FXML private Button btnLogout;
    @FXML private AnchorPane contentArea;
    @FXML private Button btnToggleMenu;
    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh;

    // Khớp bảng và cột của FXML
    @FXML private TableView<BidHistoryRow> historyTable;
    @FXML private TableColumn<BidHistoryRow, Integer> colId;
    @FXML private TableColumn<BidHistoryRow, Integer> colAuctionId;
    @FXML private TableColumn<BidHistoryRow, String> colItemName;
    @FXML private TableColumn<BidHistoryRow, Double> colBidAmount; // Thay thế colMyBid cũ
    @FXML private TableColumn<BidHistoryRow, String> colBidTime;
    @FXML private TableColumn<BidHistoryRow, String> colStatus;

    private User currentUser;
    private MainContainerController mainContainer;
    private final ObservableList<BidHistoryRow> masterDataList = FXCollections.observableArrayList();

    public void setMainContainer(MainContainerController mainContainer) {
        this.mainContainer = mainContainer;
    }

    @FXML
    public void initialize() {
        System.out.println("⏱ Khởi tạo màn hình Lịch sử đặt giá.");

        // 1. Cấu hình ánh xạ các cột TableView khớp với thuộc tính của entity BidHistoryRow
        // colId.setCellValueFactory(new PropertyValueFactory<>("id")); // Nếu model của bạn có trường ID tự tăng (STT)
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("myBidAmount")); // Ánh xạ vào trường dữ liệu số tiền đã đặt của bạn
        // colBidTime.setCellValueFactory(new PropertyValueFactory<>("bidTime")); // Mở ra nếu model có thuộc tính thời gian đặt
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        if (historyTable != null) {
            historyTable.setItems(masterDataList);
        }

        // 2. Đăng ký lắng nghe gói tin lịch sử đấu giá từ Server đẩy về
        MessageRouter.getInstance().register(ResponseCode.BID_HISTORY_RESULT, this::handleBidHistoryResult);
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;

        // Cập nhật thông tin lên Sidebar bên trái
        if (lblAccountName != null) lblAccountName.setText(user.getUsername());
        if (lblBalance != null) lblBalance.setText(String.format("%,.0f UETệ", user.getBalance()));

        // Gửi lệnh lên Server lấy lịch sử đặt giá dựa theo ID người dùng
        SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, user.getId());
    }

    private void handleBidHistoryResult(Message message) {
        if (!(message.getPayload() instanceof List)) return;
        @SuppressWarnings("unchecked")
        List<BidHistoryRow> historyRows = (List<BidHistoryRow>) message.getPayload();

        Platform.runLater(() -> {
            masterDataList.clear();
            if (historyRows != null) {
                masterDataList.addAll(historyRows);
            }
        });
    }

    /**
     * NÚT LÀM MỚI BẢNG
     */
    @FXML
    void handleRefresh(ActionEvent event) {
        if (currentUser != null) {
            SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, currentUser.getId());
        }
    }

    /**
     * NÚT XEM CHI TIẾT PHIÊN ĐẤU GIÁ CHỌN TỪ BẢNG
     */
    @FXML
    void handleViewDetail(ActionEvent event) {
        if (historyTable == null) return;
        BidHistoryRow selectedRow = historyTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            System.out.println("⚠️ Vui lòng chọn một phiên đấu giá trong bảng trước!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bidder/AuctionDetailView.fxml"));
            Parent root = loader.load();

            // Tìm đến màn hình chi tiết để nạp dữ liệu vào phòng
            com.auction.client.controller.bidder.AuctionDetailController detailController = loader.getController();
            if (detailController != null) {
                detailController.loadAuctionDetail(selectedRow.getAuctionId(), selectedRow.getItemName(), currentUser);
            }

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Chi Tiết Phiên Đấu Giá #" + selectedRow.getAuctionId());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * NÚT BÁO CÁO SỰ CỐ (Thay thế handleOpenReportModal cũ)
     */
    @FXML
    void handleReportIssue(ActionEvent event) {
        if (historyTable == null) return;
        BidHistoryRow selectedRow = historyTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            System.out.println("⚠️ Vui lòng chọn một phiên đấu giá bị lỗi trong bảng trước!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ReportIssueView.fxml"));
            Parent root = loader.load();

            // Giả định bạn có ReportIssueController để cấu hình thông tin lỗi
            // ReportIssueController reportController = loader.getController();
            // if (reportController != null) { reportController.setIssueData(selectedRow, currentUser); }

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Báo Cáo Sự Cố - Phiên #" + selectedRow.getAuctionId());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * NÚT ☰ ĐÓNG MỞ SIDEBAR TRÁI
     */
    @FXML
    void handleToggleMenu(ActionEvent event) {
        if (this.mainContainer != null) {
            this.mainContainer.toggleSidebar();
        }
    }

    // ─── ĐIỀU HƯỚNG CÁC NÚT BẤM MENU SIDEBAR (TRÙNG KHỚP FXML 100%) ───
    @FXML void onLiveMenuClick(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) this.mainContainer.setPage("/view/The_Home_Page_Bidder_View.fxml");
    }

    @FXML void onHistoryMenuClick(ActionEvent event) {
        if (currentUser != null) {
            SocketClient.getInstance().sendRequest(RequestCode.FETCH_BID_HISTORY, currentUser.getId());
        }
    }

    @FXML void onTransactionsMenuClick(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) this.mainContainer.setPage("/view/bidder/TransactionHistoryView.fxml");
    }

    @FXML void onWalletMenuClick(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) this.mainContainer.setPage("/view/bidder/DepositWithdrawView.fxml");
    }

    @FXML void onProfileMenuClick(ActionEvent event) {
        cleanupListeners();
        if (this.mainContainer != null) this.mainContainer.setPage("/view/bidder/ProfileView.fxml");
    }

    @FXML void onSwitchRoleClick(ActionEvent event) { System.out.println("🔄 Chuyển sang giao diện người bán"); }

    @FXML
    void onLogoutClick(ActionEvent event) {
        cleanupListeners();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/view/LoginView.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.setTitle("Elite Auction - Đăng Nhập");
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void cleanupListeners() {
        MessageRouter.getInstance().unregister(ResponseCode.BID_HISTORY_RESULT);
    }
}