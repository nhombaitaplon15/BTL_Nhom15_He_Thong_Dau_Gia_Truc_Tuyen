package com.auction.client.controller;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.server.dao.BiddingHistoryDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.AuctionDAO;
import com.auction.common.model.Auction;
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
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private User currentUser;

    private The_Home_Page_Bidder_View_Controller homeControllerInstance;
    private Scene homeScene;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSearch();
    }

    public void setMainHomeController(Scene homeScene, The_Home_Page_Bidder_View_Controller homeController) {
        this.homeScene = homeScene;
        this.homeControllerInstance = homeController;
    }

    public void setUserData(User user) {
        if (user == null) return;
        this.currentUser = user;
        loadHistory();
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("bidAmount"));
        colBidTime.setCellValueFactory(new PropertyValueFactory<>("bidTime"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colBidAmount.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%,.0f UETệ", amount));
                    TableRow<?> row = getTableRow();
                    if (row != null && row.isSelected()) {
                        setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // 🎯 ĐÃ TINH GỌN: Bỏ qua trạng thái "Bị đè giá" phức tạp
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(empty || item == null ? null : item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                BidHistoryRow rowData = getTableRow() != null ? getTableRow().getItem() : null;

                if (rowData != null) {
                    try {
                        Auction auction = auctionDAO.getAuctionById(rowData.getAuctionId());

                        if (auction != null) {
                            String auctionStatus = auction.getAuctionStatus() != null ? auction.getAuctionStatus().toUpperCase() : "RUNNING";

                            // LÚC ĐANG CHẠY: Mặc định hiển thị là đang dẫn đầu phiên
                            if ("RUNNING".equals(auctionStatus)) {
                                setText("ĐANG DẪN ĐẦU");
                            }
                            // KHI KẾT THÚC: Phân định thắng / thua rõ ràng
                            else {
                                if (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId() == currentUser.getId()
                                        && rowData.getBidAmount() >= auction.getCurrentPrice()) {
                                    setText("THẮNG CUỘC 🏆");
                                } else {
                                    setText("THẤT BẠI");
                                }
                            }
                        } else {
                            setText(item);
                        }
                    } catch (Exception e) {
                        setText(item);
                    }
                } else {
                    setText(item);
                }

                // Đổ màu sắc trạng thái ngắn gọn
                TableRow<?> row = getTableRow();
                if (row != null && row.isSelected()) {
                    setStyle("-fx-text-fill: white !important; -fx-font-weight: bold;");
                } else {
                    String currentText = getText();
                    if ("THẮNG CUỘC 🏆".equals(currentText)) {
                        setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;"); // Màu xanh lá
                    } else if ("ĐANG DẪN ĐẦU".equals(currentText)) {
                        setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;"); // Màu xanh ngọc
                    } else if ("THẤT BẠI".equals(currentText)) {
                        setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;"); // Màu đỏ
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        historyTable.setRowFactory(tv -> {
            TableRow<BidHistoryRow> row = new TableRow<>();
            row.selectedProperty().addListener((obs, oldVal, newVal) -> row.requestLayout());
            return row;
        });
    }

    private void loadHistory() {
        if (currentUser == null) return;
        new Thread(() -> {
            try {
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

    @FXML
    private void handleRefresh() {
        if (currentUser != null) {
            new Thread(() -> {
                try {
                    double actualBalance = paymentDAO.getBalance(currentUser.getId());
                    Platform.runLater(() -> currentUser.setBalance(actualBalance));
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        }
        loadHistory();

        if (homeControllerInstance != null) {
            homeControllerInstance.updateWalletUI();
        }
    }

    @FXML
    private void onLiveMenuClick() {
        try {
            if (homeScene != null) {
                if (homeControllerInstance != null) {
                    homeControllerInstance.updateWalletUI();
                }
                Stage stage = (Stage) txtSearch.getScene().getWindow();
                stage.setScene(homeScene);
                stage.setTitle("Elite Auction - Sàn Đấu Giá");
                stage.show();
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
                Parent root = loader.load();
                The_Home_Page_Bidder_View_Controller homeController = loader.getController();

                if (homeController != null) {
                    homeController.setUserData(this.currentUser);
                    homeController.updateWalletUI();
                }
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

    @FXML
    private void handleViewDetail(javafx.event.ActionEvent event) {
        BidHistoryRow selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Vui lòng chọn một dòng phiên đấu giá trong bảng lịch sử để xem!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/AuctionDetailView.fxml"));
            Parent root = loader.load();
            AuctionDetailController detailController = loader.getController();
            if (detailController != null) {
                detailController.loadAuctionDetail(selected.getAuctionId(), selected.getItemName(), this.currentUser);
            }
            Stage dialogStage = new Stage();
            dialogStage.setScene(new Scene(root, 700, 500));
            dialogStage.setTitle("Thông Tin Chi Tiết Phiên Đấu Giá - #" + selected.getAuctionId());
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            Stage ownerStage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            dialogStage.initOwner(ownerStage);
            dialogStage.setResizable(false);
            dialogStage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo hệ thống");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}