package com.auction.client.controller.admin; // [SỬA] client.controller.admin -> com.auction.client.controller.admin

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class The_Auction_Page_Admin_View_Controller implements Initializable {

    @FXML private TableView<Auction> auctionTable;
    @FXML private TableColumn<Auction, Void> colItemAndId;
    @FXML private TableColumn<Auction, Void> colParticipants;
    @FXML private TableColumn<Auction, Void> colFinancials;
    @FXML private TableColumn<Auction, Void> colStatusAndTime;
    @FXML private TableColumn<Auction, Void> colAction;
    @FXML private Label lblStatusBar;   // Thanh trạng thái phía dưới bảng

    private User currentUser;
    private final ObservableList<Auction> auctionList = FXCollections.observableArrayList();

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupPremiumTable();
        registerRealtimeHandlers();
        loadAuctions();
    }

    public void setUserData(User user) {
        this.currentUser = user;
    }

    /**
     * Đăng ký các handler nhận response từ server.
     */
    private void registerRealtimeHandlers() {
        // Nhận danh sách tất cả phiên
        MessageRouter.getInstance().register(ResponseCode.ADMIN_ALL_AUCTIONS_RESULT, this::onAuctionsReceived);

        // Nhận kết quả duyệt/từ chối/block
        MessageRouter.getInstance().register(ResponseCode.ADMIN_APPROVE_SUCCESS, msg -> onActionSuccess(msg, "duyệt"));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_APPROVE_FAILED,  msg -> onActionFailed(msg));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_REJECT_SUCCESS,  msg -> onActionSuccess(msg, "từ chối"));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_REJECT_FAILED,   msg -> onActionFailed(msg));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_BLOCK_SUCCESS,   msg -> onActionSuccess(msg, "phong tỏa"));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_BLOCK_FAILED,    msg -> onActionFailed(msg));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_CREATED, msg -> onTransactionCreated());
        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_FAILED,  msg -> onActionFailed(msg));

        // [REALTIME] Tự động load lại khi có Seller gửi phiên mới
        MessageRouter.getInstance().register(ResponseCode.ADMIN_NEW_PENDING_AUCTION, msg -> {
            setStatus("🔔 Có phiên mới cần duyệt! Đang tải lại...");
            loadAuctions();
        });
    }

    // =========================================================
    // DATA
    // =========================================================

    /**
     * [ĐÃ SỬA] Gửi request qua socket thay vì gọi managerService.getAllAuctions() trực tiếp.
     */
    private void loadAuctions() {
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_AUCTIONS, null);
        setStatus("⏳ Đang tải danh sách phiên...");
    }

    // =========================================================
    // REALTIME HANDLERS
    // =========================================================

    @SuppressWarnings("unchecked")
    private void onAuctionsReceived(Message message) {
        List<Auction> list = (List<Auction>) message.getPayload();
        auctionList.clear();
        if (list != null) auctionList.addAll(list);
        auctionTable.setItems(null);
        auctionTable.setItems(auctionList);
        auctionTable.refresh();
        setStatus("✅ Đã tải " + auctionList.size() + " phiên đấu giá.");
    }

    private void onActionSuccess(Message msg, String action) {
        setStatus("✅ " + action.substring(0, 1).toUpperCase() + action.substring(1)
                + " phiên thành công: #" + msg.getPayload());
        loadAuctions();
    }

    private void onActionFailed(Message msg) {
        setStatus("❌ Thao tác thất bại: " + msg.getMessage());
        showAlert(Alert.AlertType.ERROR, "Lỗi Thực Thi", msg.getMessage());
    }

    private void onTransactionCreated() {
        setStatus("✅ Đã tạo giao dịch thành công!");
        showAlert(Alert.AlertType.INFORMATION, "Thành Công",
                "Giao dịch đã được tạo. Sang tab Quản Lý Giao Dịch để kiểm tra.");
        loadAuctions();
    }

    // =========================================================
    // TABLE SETUP (giao diện giống file gốc nhưng action gọi socket)
    // =========================================================

    private void setupPremiumTable() {
        // --- CỘT 1: SẢN PHẨM & MÃ PHIÊN ---
        colItemAndId.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                Label lblTitle = new Label("Sản phẩm #" + ac.getItemId());
                lblTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1B2559; -fx-font-size: 14px;");
                Label lblId = new Label("MÃ PHIÊN: #" + ac.getAuctionId());
                lblId.setStyle("-fx-text-fill: #A3AED0; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
                setGraphic(new VBox(4, lblTitle, lblId));
            }
        });

        // --- CỘT 2: ĐỐI TƯỢNG THAM GIA ---
        colParticipants.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                Label lblSeller = new Label("Người bán ID: " + ac.getSellerId() + " ★");
                lblSeller.setStyle("-fx-text-fill: #2B3674; -fx-font-size: 13px; -fx-font-weight: bold;");
                String winnerText = (ac.getCurrentWinnerId() != null && ac.getCurrentWinnerId() > 0)
                        ? "Đang dẫn đầu: ID " + ac.getCurrentWinnerId() : "Chưa có lượt đặt";
                Label lblWinner = new Label(winnerText);
                lblWinner.setStyle("-fx-text-fill: #707EAE; -fx-font-size: 12px; -fx-font-style: italic;");
                setGraphic(new VBox(4, lblSeller, lblWinner));
            }
        });

        // --- CỘT 3: TÀI CHÍNH ---
        colFinancials.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                Label lblPrice = new Label(String.format("%,.0f đ", ac.getCurrentPrice()));
                lblPrice.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #05CD99;");
                String bidText = ac.getTotalBids() > 10 ? "🔥 " + ac.getTotalBids() + " lượt bids" : ac.getTotalBids() + " lượt bids";
                Label lblBids = new Label(bidText);
                lblBids.setStyle("-fx-text-fill: #A3AED0; -fx-font-size: 12px;");
                setGraphic(new VBox(4, lblPrice, lblBids));
            }
        });

        // --- CỘT 4: TRẠNG THÁI & THỜI GIAN ---
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        colStatusAndTime.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                String status = ac.getAuctionStatus();
                Label lblStatus = new Label(status);
                switch (status != null ? status : "") {
                    case "WAITING_FOR_ADMIN" -> lblStatus.setStyle("-fx-text-fill: #FF8800; -fx-font-weight: bold;");
                    case "OPEN", "RUNNING"   -> lblStatus.setStyle("-fx-text-fill: #05CD99; -fx-font-weight: bold;");
                    case "CLOSED","FINISHED" -> lblStatus.setStyle("-fx-text-fill: #4318FF; -fx-font-weight: bold;");
                    case "REJECTED"          -> lblStatus.setStyle("-fx-text-fill: #FF5B5C; -fx-font-weight: bold;");
                    default                  -> lblStatus.setStyle("-fx-text-fill: #707EAE;");
                }
                Label lblTime = new Label(ac.getEndTime() != null
                        ? "Kết thúc: " + ac.getEndTime().format(formatter) : "Chưa có thời gian");
                lblTime.setStyle("-fx-text-fill: #707EAE; -fx-font-size: 12px;");
                VBox box = new VBox(6, lblStatus, lblTime);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        // --- CỘT 5: HÀNH ĐỘNG (gọi socket - không gọi service trực tiếp) ---
        colAction.setCellFactory(param -> new TableCell<Auction, Void>() {
            private final Button btnInfo        = new Button("Xem");
            private final Button btnApprove     = new Button("Duyệt");
            private final Button btnReject      = new Button("Từ chối");
            private final Button btnBlock       = new Button("Chặn");
            private final Button btnTransaction = new Button("Giao dịch");
            private final HBox   container      = new HBox(6);

            {
                container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                btnInfo.setStyle("-fx-background-color: #4318FF; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnApprove.setStyle("-fx-background-color: #05CD99; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnReject.setStyle("-fx-background-color: #FF5B5C; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnBlock.setStyle("-fx-background-color: #FFBB00; -fx-text-fill: #1B2559; -fx-font-weight: bold; -fx-cursor: hand;");
                btnTransaction.setStyle("-fx-background-color: #E0E7FF; -fx-text-fill: #4338CA; -fx-border-color: #C7D2FE; -fx-font-weight: bold; -fx-cursor: hand;");

                // XEM
                btnInfo.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    showAlert(Alert.AlertType.INFORMATION,
                            "Chi Tiết Phiên #" + ac.getAuctionId(),
                            "Mã sản phẩm: " + ac.getItemId()
                                    + "\nMã người bán: " + ac.getSellerId()
                                    + "\nGiá khởi điểm: " + String.format("%,.0f đ", ac.getStartingPrice())
                                    + "\nGiá hiện tại: " + String.format("%,.0f đ", ac.getCurrentPrice())
                                    + "\nTổng bids: " + ac.getTotalBids()
                                    + "\nTrạng thái: " + ac.getAuctionStatus());
                });

                // [SỬA] DUYỆT - gọi socket thay vì adminService.approveAuction()
                btnApprove.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Duyệt và mở phòng đấu giá #" + ac.getAuctionId() + "?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_APPROVE_AUCTION, ac.getAuctionId());
                        }
                    });
                });

                // [SỬA] TỪ CHỐI - gọi socket
                btnReject.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    TextInputDialog dialog = new TextInputDialog("Vi phạm điều khoản");
                    dialog.setTitle("Từ Chối Phiên #" + ac.getAuctionId());
                    dialog.setContentText("Nhập lý do từ chối:");
                    dialog.showAndWait().ifPresent(reason -> {
                        if (!reason.trim().isEmpty()) {
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_REJECT_AUCTION,
                                    new Object[]{ac.getAuctionId(), reason.trim()});
                        }
                    });
                });

                // [SỬA] CHẶN KHẨN CẤP - gọi socket
                btnBlock.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "DỪNG KHẨN CẤP phiên #" + ac.getAuctionId()
                                    + "?\nHành động này sẽ đóng phiên và broadcast tới tất cả bidder!",
                            ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_BLOCK_AUCTION, ac.getAuctionId());
                        }
                    });
                });

                // [SỬA] TẠO GIAO DỊCH - gọi socket
                btnTransaction.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    if (ac.getCurrentWinnerId() == null || ac.getCurrentWinnerId() == 0) {
                        showAlert(Alert.AlertType.WARNING, "Không Thể Tạo Giao Dịch",
                                "Phiên kết thúc nhưng không có người thắng.");
                        return;
                    }
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Tạo giao dịch cho phiên #" + ac.getAuctionId()
                                    + "?\nGiá: " + String.format("%,.0f đ", ac.getCurrentPrice())
                                    + " | Người thắng: ID#" + ac.getCurrentWinnerId(),
                            ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_CREATE_TRANSACTION,
                                    new Object[]{ac.getAuctionId(), ac.getCurrentWinnerId(), ac.getCurrentPrice()});
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                Auction ac = (Auction) getTableRow().getItem();
                String status = ac.getAuctionStatus();
                container.getChildren().clear();
                container.getChildren().add(btnInfo);
                if ("WAITING_FOR_ADMIN".equals(status)) {
                    container.getChildren().addAll(btnApprove, btnReject);
                } else if ("OPEN".equals(status) || "RUNNING".equals(status)) {
                    container.getChildren().add(btnBlock);
                } else if ("CLOSED".equals(status) || "FINISHED".equals(status) || "SOLD".equals(status)) {
                    container.getChildren().add(btnTransaction);
                }
                setGraphic(container);
            }
        });
    }


    @FXML public void goToHomePage(ActionEvent event) {
        switchPage(event, "/view/view/The_Home_Page_Admin_View.fxml");
    }
    @FXML public void goToTransactionPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Transaction_Page_Admin_View.fxml");
    }
    @FXML public void goToSettingsPage(ActionEvent event) {
        switchPage(event, "/view/view/The_Settings_Page_Admin_View.fxml");
    }

    private void switchPage(ActionEvent event, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof The_Transaction_Page_Admin_View_Controller)
                ((The_Transaction_Page_Admin_View_Controller) ctrl).setUserData(currentUser);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setStatus(String msg) {
        if (lblStatusBar != null) lblStatusBar.setText(msg);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void Welcome_back(ActionEvent actionEvent) {

    }
}