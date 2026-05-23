package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.service.AdminService;
import com.auction.service.ItemService;
import com.auction.service.ManagerService;
import com.auction.service.TransactionService;
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

    // Các Service kết nối dữ liệu
    private ItemService itemService = new ItemService();
    private ManagerService managerService = new ManagerService(itemService);
    private AdminService adminService = new AdminService(managerService);

    // Đã sửa lỗi khởi tạo TransactionService bằng cách truyền managerService vào
    private TransactionService transactionService = new TransactionService(managerService);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupPremiumTable();
        loadAuctions();
    }

    private void loadAuctions() {
        try {
            List<Auction> dbAuctions = managerService.getAllAuctions();
            ObservableList<Auction> auctionList = FXCollections.observableArrayList(dbAuctions);
            auctionTable.setItems(auctionList);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi Tải Dữ Liệu", "Không thể lấy dữ liệu từ cơ sở dữ liệu!", e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupPremiumTable() {
        // --- CỘT 1: SẢN PHẨM & MÃ PHIÊN ---
        colItemAndId.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null || getTableView().getItems().get(getIndex()) == null) { setGraphic(null); }
                else {
                    Auction ac = getTableView().getItems().get(getIndex());
                    Label lblTitle = new Label("Sản phẩm #" + ac.getItemId());
                    lblTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1B2559; -fx-font-size: 14px;");
                    Label lblId = new Label("MÃ PHIÊN: #" + ac.getAuctionId());
                    lblId.setStyle("-fx-text-fill: #A3AED0; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
                    setGraphic(new VBox(4, lblTitle, lblId));
                }
            }
        });

        // --- CỘT 2: ĐỐI TƯỢNG THAM GIA ---
        colParticipants.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null || getTableView().getItems().get(getIndex()) == null) { setGraphic(null); }
                else {
                    Auction ac = getTableView().getItems().get(getIndex());
                    Label lblSeller = new Label("Người bán ID: " + ac.getSellerId() + " ★");
                    lblSeller.setStyle("-fx-text-fill: #2B3674; -fx-font-size: 13px; -fx-font-weight: bold;");
                    String winnerText = (ac.getCurrentWinnerId() != null && ac.getCurrentWinnerId() > 0) ? "Đang dẫn đầu: ID " + ac.getCurrentWinnerId() : "Chưa có lượt đặt";
                    Label lblWinner = new Label(winnerText);
                    lblWinner.setStyle("-fx-text-fill: #707EAE; -fx-font-size: 12px; -fx-font-style: italic;");
                    setGraphic(new VBox(4, lblSeller, lblWinner));
                }
            }
        });

        // --- CỘT 3: TÀI CHÍNH & THỊ TRƯỜNG ---
        colFinancials.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null || getTableView().getItems().get(getIndex()) == null) { setGraphic(null); }
                else {
                    Auction ac = getTableView().getItems().get(getIndex());
                    Label lblPrice = new Label(String.format("%,.0f đ", ac.getCurrentPrice()));
                    lblPrice.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #05CD99;");
                    String bidText = ac.getTotalBids() > 10 ? "🔥 " + ac.getTotalBids() + " lượt bids" : ac.getTotalBids() + " lượt bids";
                    Label lblBids = new Label(bidText);
                    lblBids.setStyle("-fx-text-fill: #A3AED0; -fx-font-size: 12px;");
                    setGraphic(new VBox(4, lblPrice, lblBids));
                }
            }
        });

        // --- CỘT 4: TRẠNG THÁI & THỜI GIAN ---
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        colStatusAndTime.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null || getTableView().getItems().get(getIndex()) == null) { setGraphic(null); }
                else {
                    Auction ac = getTableView().getItems().get(getIndex());
                    String status = ac.getAuctionStatus();
                    Label lblStatus = new Label(status);
                    lblStatus.getStyleClass().add("status-badge");
                    if ("WAITING_FOR_ADMIN".equals(status)) lblStatus.getStyleClass().add("badge-waiting");
                    else if ("OPEN".equals(status) || "RUNNING".equals(status)) lblStatus.getStyleClass().add("badge-open");
                    else if ("CLOSED".equals(status) || "FINISHED".equals(status)) lblStatus.getStyleClass().add("badge-closed");
                    else lblStatus.getStyleClass().add("badge-rejected");
                    Label lblTime = new Label("Kết thúc: " + ac.getEndTime().format(formatter));
                    lblTime.setStyle("-fx-text-fill: #707EAE; -fx-font-size: 12px;");
                    VBox box = new VBox(6, lblStatus, lblTime);
                    box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        });

        // --- CỘT 5: TRUNG TÂM XỬ LÝ (NÚT BẤM REAL BACKEND) ---
        colAction.setCellFactory(param -> new TableCell<Auction, Void>() {
            private final Button btnInfo = new Button("Xem");
            private final Button btnApprove = new Button("Duyệt");
            private final Button btnReject = new Button("Từ chối");
            private final Button btnBlock = new Button("Chặn");
            private final Button btnTransaction = new Button("Giao dịch");
            private final HBox container = new HBox(6);

            {
                container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                btnInfo.setStyle("-fx-background-color: #4318FF; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnApprove.setStyle("-fx-background-color: #05CD99; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnReject.setStyle("-fx-background-color: #FF5B5C; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnBlock.setStyle("-fx-background-color: #FFBB00; -fx-text-fill: #1B2559; -fx-font-weight: bold; -fx-cursor: hand;");
                btnTransaction.setStyle("-fx-background-color: #E0E7FF; -fx-text-fill: #4338CA; -fx-border-color: #C7D2FE; -fx-border-radius: 6px; -fx-font-weight: bold; -fx-cursor: hand;");

                // 1. NÚT XEM
                btnInfo.setOnAction(e -> {
                    Auction ac = getTableView().getItems().get(getIndex());
                    Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
                    infoAlert.setTitle("Chi Tiết Phiên Đấu Giá");
                    infoAlert.setHeaderText("Phiên mã số: #" + ac.getAuctionId());
                    infoAlert.setContentText("Mã sản phẩm: " + ac.getItemId() + "\n" +
                            "Mã người bán: " + ac.getSellerId() + "\n" +
                            "Giá khởi điểm: " + String.format("%,.0f đ", ac.getStartingPrice()) + "\n" +
                            "Giá hiện tại: " + String.format("%,.0f đ", ac.getCurrentPrice()) + "\n" +
                            "Trạng thái: " + ac.getAuctionStatus());
                    infoAlert.showAndWait();
                });

                // 2. NÚT DUYỆT
                btnApprove.setOnAction(e -> {
                    Auction ac = getTableView().getItems().get(getIndex());
                    try {
                        boolean success = adminService.approveAuction(ac.getAuctionId());
                        if (success) {
                            showAlert(Alert.AlertType.INFORMATION, "Thành Công", null, "Đã duyệt và mở kích hoạt phiên #" + ac.getAuctionId());
                            loadAuctions();
                        }
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Lỗi Thực Thi", "Không thể duyệt phiên này!", ex.getMessage());
                    }
                });

                // 3. NÚT TỪ CHỐI
                btnReject.setOnAction(e -> {
                    Auction ac = getTableView().getItems().get(getIndex());
                    TextInputDialog dialog = new TextInputDialog("Vi phạm điều khoản");
                    dialog.setTitle("Từ Chối Phiên Đấu Giá");
                    dialog.setHeaderText("Từ chối phiên của Người bán ID: " + ac.getSellerId());
                    dialog.setContentText("Nhập lý do từ chối cụ thể:");

                    Optional<String> result = dialog.showAndWait();
                    result.ifPresent(reason -> {
                        if (reason.trim().isEmpty()) {
                            showAlert(Alert.AlertType.WARNING, "Cảnh báo", null, "Lý do từ chối không được để trống!");
                            return;
                        }
                        try {
                            adminService.rejectAuction(ac.getAuctionId(), reason);
                            showAlert(Alert.AlertType.INFORMATION, "Thành Công", null, "Đã từ chối phiên đấu giá thành công.");
                            loadAuctions();
                        } catch (Exception ex) {
                            showAlert(Alert.AlertType.ERROR, "Lỗi Thực Thi", "Không thể từ chối phiên!", ex.getMessage());
                        }
                    });
                });

                // 4. NÚT CHẶN KHẨN CẤP
                btnBlock.setOnAction(e -> {
                    Auction ac = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("CẢNH BÁO PHONG TỎA");
                    confirm.setHeaderText("Bạn có chắc chắn muốn DỪNG KHẨN CẤP phiên #" + ac.getAuctionId() + " không?");
                    confirm.setContentText("Hành động này sẽ đóng phiên lập tức và bảo lưu trạng thái.");

                    Optional<ButtonType> click = confirm.showAndWait();
                    if (click.isPresent() && click.get() == ButtonType.OK) {
                        try {
                            boolean success = adminService.blockAuction(ac.getAuctionId());
                            if (success) {
                                showAlert(Alert.AlertType.INFORMATION, "Đã Phong Tỏa", null, "Đã khóa phiên đấu giá này thành công.");
                                loadAuctions();
                            }
                        } catch (Exception ex) {
                            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể phong tỏa phiên!", ex.getMessage());
                        }
                    }
                });

                // 5. NÚT DUYỆT GIAO DỊCH
                btnTransaction.setOnAction(e -> {
                    Auction ac = getTableView().getItems().get(getIndex());
                    if (ac.getCurrentWinnerId() == null || ac.getCurrentWinnerId() == 0) {
                        showAlert(Alert.AlertType.WARNING, "Không Thể Tạo GD", null, "Phiên kết thúc nhưng không có người mua đặt giá.");
                        return;
                    }

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Xác Nhận Tạo Giao Dịch");
                    confirm.setHeaderText("Tiến hành chốt đơn cho phiên #" + ac.getAuctionId());
                    confirm.setContentText("Hệ thống sẽ lập hóa đơn trị giá " + String.format("%,.0f đ", ac.getCurrentPrice()) +
                            "\nNgười thắng mua hàng: ID " + ac.getCurrentWinnerId());

                    Optional<ButtonType> click = confirm.showAndWait();
                    if (click.isPresent() && click.get() == ButtonType.OK) {
                        try {
                            transactionService.createTransactionFromAuction(ac.getAuctionId(), ac.getCurrentWinnerId(), ac.getCurrentPrice());
                            showAlert(Alert.AlertType.INFORMATION, "Thành Công", null, "Đơn giao dịch đã được tạo! Bạn có thể sang tab Quản lý giao dịch để kiểm tra.");
                            loadAuctions();
                        } catch (Exception ex) {
                            showAlert(Alert.AlertType.ERROR, "Lỗi", "Tạo giao dịch thất bại!", ex.getMessage());
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null || getTableView().getItems().get(getIndex()) == null) {
                    setGraphic(null);
                } else {
                    Auction ac = getTableView().getItems().get(getIndex());
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
            }
        });
    }

    // Tiện ích hiển thị thông báo
    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // --- CÁC HÀM CHUYỂN TRANG ---
    @FXML public void Welcome_back(ActionEvent event) { switchPage(event, "/view/WelcomeView.fxml"); }
    @FXML public void goToHomePage(ActionEvent event) { switchPage(event, "/view/The_Home_Page_Admin_View.fxml"); }
    @FXML public void goToTransactionPage(ActionEvent event) { switchPage(event, "/view/The_Transaction_Page_Admin_View.fxml"); }
    @FXML public void goToSettingsPage(ActionEvent event) { switchPage(event, "/view/The_Settings_Page_Admin_View.fxml"); }

    private void switchPage(ActionEvent event, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
