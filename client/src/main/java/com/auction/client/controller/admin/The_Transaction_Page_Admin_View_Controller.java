package com.auction.client.controller.admin;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.client.core.ClientSession;
import com.auction.common.model.TransactionRequest;
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
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class The_Transaction_Page_Admin_View_Controller implements Initializable {

    @FXML private TableView<TransactionRequest>             transactionTable;
    @FXML private TableColumn<TransactionRequest, Void>     colTxId;
    @FXML private TableColumn<TransactionRequest, Void>     colUserInfo;
    @FXML private TableColumn<TransactionRequest, Void>     colAmount;
    @FXML private TableColumn<TransactionRequest, Void>     colTxStatus;
    @FXML private TableColumn<TransactionRequest, Void>     colTxAction;

    @FXML private LineChart<String, Number> cashflowChart;
    @FXML private Label lblTotalInflow;
    @FXML private Label lblPendingBadge;
    @FXML private Label lblPendingHeader;
    @FXML private Label lblStatusBar;
    @FXML private Label lblAdminName, lblPendingCount;
    private User currentUser;

    @FXML private TextField tfSearch;
    @FXML private ComboBox<String> cbTxStatus;
    @FXML private DatePicker dpFilter;
    @FXML private VBox pendingRequestsContainer;

    private final ObservableList<TransactionRequest> allTxList      = FXCollections.observableArrayList();
    private final ObservableList<TransactionRequest> filteredTxList = FXCollections.observableArrayList();
    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupSearchFilter();
        registerRealtimeHandlers();
        loadTransactions();
    }

    public void setUserData(User user) { this.currentUser = user; }

    private void setupSearchFilter() {
        if (cbTxStatus != null) {
            cbTxStatus.getItems().addAll("Tất cả", "PENDING", "APPROVED", "REJECTED", "SUCCESS");
            cbTxStatus.getSelectionModel().selectFirst();
            cbTxStatus.setOnAction(e -> applySearchFilter());
        }
        if (tfSearch != null) {
            tfSearch.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter());
        }
        if (dpFilter != null) {
            dpFilter.valueProperty().addListener((obs, oldVal, newVal) -> applySearchFilter());
        }
    }

    private void applySearchFilter() {
        String keyword = (tfSearch != null && tfSearch.getText() != null) ? tfSearch.getText().trim().toLowerCase() : "";
        String statusSel = (cbTxStatus != null && cbTxStatus.getValue() != null) ? cbTxStatus.getValue() : "Tất cả";
        LocalDate dateFilter = (dpFilter != null) ? dpFilter.getValue() : null;

        List<TransactionRequest> result = allTxList.stream()
            .filter(tx -> {
                if (!keyword.isEmpty()) {
                    boolean matchId   = String.valueOf(tx.getRequestId()).contains(keyword);
                    boolean matchUser = tx.getUser() != null && tx.getUser().getUsername() != null && tx.getUser().getUsername().toLowerCase().contains(keyword);
                    boolean matchType = tx.getType() != null && tx.getType().toLowerCase().contains(keyword);
                    if (!matchId && !matchUser && !matchType) return false;
                }
                if (!"Tất cả".equals(statusSel)) {
                    if (!statusSel.equalsIgnoreCase(tx.getStatus())) return false;
                }
                if (dateFilter != null && tx.getRequestDate() != null) {
                    LocalDate txDate = tx.getRequestDate().toLocalDate();
                    if (!txDate.equals(dateFilter)) return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        filteredTxList.setAll(result);
        setStatus("🔍 Hiển thị " + result.size() + " / " + allTxList.size() + " giao dịch.");
    }
    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT, this::onTransactionsReceived);

        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_APPROVED, msg -> {
            Platform.runLater(() -> setStatus("✅ Đã duyệt giao dịch #" + msg.getPayload()));
            updateLocalTransaction(msg.getPayload(), "APPROVED");
        });

        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_REJECTED, msg -> {
            Platform.runLater(() -> setStatus("✅ Đã từ chối giao dịch #" + msg.getPayload()));
            updateLocalTransaction(msg.getPayload(), "REJECTED");
        });

        MessageRouter.getInstance().register(ResponseCode.ERROR_MESSAGE, msg -> {
            Platform.runLater(() -> {
                setStatus("❌ Lỗi: " + msg.getMessage());
                showAlert(Alert.AlertType.ERROR, "Lỗi xử lý", msg.getMessage());
            });
        });

        // [THÊM MỚI]: Bắt sóng khi Bidder vừa tạo lệnh nạp/rút
        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_CREATED, msg -> {
            Platform.runLater(() -> {
                setStatus("🔔 " + msg.getMessage() + "! Đang làm mới dữ liệu...");
                loadTransactions(); // Tự động kéo dữ liệu mới từ Database về
            });
        });
    }

    private void loadTransactions() {
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_TRANSACTIONS, null);
        });
        setStatus("⏳ Đang tải danh sách giao dịch...");
    }

    @SuppressWarnings("unchecked")
    private void onTransactionsReceived(Message message) {
        List<TransactionRequest> list = (List<TransactionRequest>) message.getPayload();
        if (list == null) list = Collections.emptyList();
        final List<TransactionRequest> finalList = list;

        Platform.runLater(() -> {
            allTxList.setAll(finalList);
            applySearchFilter();
            updateSummaryKPIs(finalList);
            buildCashflowChart(finalList);
            buildPendingRequestsSection(finalList);
            setStatus("✅ Đã tải " + finalList.size() + " giao dịch.");
        });
        transactionTable.refresh();
    }

    private void updateSummaryKPIs(List<TransactionRequest> list) {
        LocalDate now = LocalDate.now();
        double totalInflow = list.stream().filter(tx -> "DEPOSIT".equalsIgnoreCase(tx.getType()) && "APPROVED".equalsIgnoreCase(tx.getStatus()) && tx.getRequestDate() != null && tx.getRequestDate().getYear() == now.getYear() && tx.getRequestDate().getMonthValue() == now.getMonthValue()).mapToDouble(TransactionRequest::getAmount).sum();
        long pendingWithdrawCount = list.stream().filter(tx -> "WITHDRAW".equalsIgnoreCase(tx.getType()) && "PENDING".equalsIgnoreCase(tx.getStatus())).count();
        long totalPending = list.stream().filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();

        if (lblTotalInflow  != null) lblTotalInflow.setText("+" + formatMoney(totalInflow));
        if (lblPendingBadge != null) lblPendingBadge.setText(pendingWithdrawCount + " lệnh");
        if (lblPendingHeader != null) lblPendingHeader.setText("Cần duyệt ngay (" + totalPending + ")");
    }

    private void buildCashflowChart(List<TransactionRequest> list) {
        if (cashflowChart == null) return;
        cashflowChart.getData().clear();
        cashflowChart.setTitle("Xu hướng dòng tiền");
        cashflowChart.setStyle("-fx-background-color: transparent;");

        LocalDate today = LocalDate.now();
        Map<String, Double> depositByDay  = new LinkedHashMap<>();
        Map<String, Double> withdrawByDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String key = today.minusDays(i).format(dayFormatter);
            depositByDay.put(key,  0.0);
            withdrawByDay.put(key, 0.0);
        }
        for (TransactionRequest tx : list) {
            if (tx.getRequestDate() == null) continue;
            if (!"APPROVED".equalsIgnoreCase(tx.getStatus())) continue;
            LocalDate txDay = tx.getRequestDate().toLocalDate();
            if (txDay.isBefore(today.minusDays(6)) || txDay.isAfter(today)) continue;
            String key = txDay.format(dayFormatter);
            if ("DEPOSIT".equalsIgnoreCase(tx.getType()))        depositByDay.merge(key, tx.getAmount(), Double::sum);
            else if ("WITHDRAW".equalsIgnoreCase(tx.getType())) withdrawByDay.merge(key, tx.getAmount(), Double::sum);
        }

        XYChart.Series<String, Number> depositSeries = new XYChart.Series<>();
        depositSeries.setName("Nạp tiền");
        depositByDay.forEach((day, amt) -> depositSeries.getData().add(new XYChart.Data<>(day, amt / 1_000_000.0)));

        XYChart.Series<String, Number> withdrawSeries = new XYChart.Series<>();
        withdrawSeries.setName("Rút tiền");
        withdrawByDay.forEach((day, amt) -> withdrawSeries.getData().add(new XYChart.Data<>(day, amt / 1_000_000.0)));

        cashflowChart.getData().addAll(depositSeries, withdrawSeries);

        Platform.runLater(() -> {
            if (depositSeries.getNode() != null) depositSeries.getNode().setStyle("-fx-stroke: #10B981; -fx-stroke-width: 2.5;");
            if (withdrawSeries.getNode() != null) withdrawSeries.getNode().setStyle("-fx-stroke: #EF4444; -fx-stroke-width: 2.5;");
        });
    }

    private void buildPendingRequestsSection(List<TransactionRequest> list) {
        if (pendingRequestsContainer == null) return;
        pendingRequestsContainer.getChildren().clear();
        List<TransactionRequest> pendingList = list.stream().filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).sorted(Comparator.comparing(tx -> tx.getRequestDate() == null ? java.time.LocalDateTime.MIN : tx.getRequestDate(), Comparator.reverseOrder())).collect(Collectors.toList());

        if (pendingList.isEmpty()) {
            Label empty = new Label("✅ Không có giao dịch nào chờ duyệt.");
            empty.setStyle("-fx-text-fill: #10B981; -fx-font-size: 13px; -fx-font-weight: bold;");
            pendingRequestsContainer.getChildren().add(empty);
            return;
        }
        for (TransactionRequest tx : pendingList) pendingRequestsContainer.getChildren().add(buildPendingCard(tx));
    }

    private VBox buildPendingCard(TransactionRequest tx) {
        VBox card = new VBox(8);
        boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.getType());
        String borderColor = isWithdraw ? "#FDE68A" : "#A7F3D0";
        String bgColor     = isWithdraw ? "#FFFBEB" : "#F0FDF4";
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 14; -fx-border-color: " + borderColor + "; -fx-border-width: 1.5; -fx-border-radius: 14; -fx-padding: 12 14 12 14;");

        HBox row1 = new HBox(10);
        row1.setAlignment(Pos.CENTER_LEFT);
        String userName = (tx.getUser() != null && tx.getUser().getUsername() != null) ? "@" + tx.getUser().getUsername() : "User #" + (tx.getUser() != null ? tx.getUser().getId() : "?");
        Label lblUser = new Label(userName);
        lblUser.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 14px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String amountSign  = isWithdraw ? "− " : "+ ";
        String amountColor = isWithdraw ? "#B45309" : "#047857";
        Label lblAmount = new Label(amountSign + formatMoney(tx.getAmount()));
        lblAmount.setStyle("-fx-text-fill: " + amountColor + "; -fx-font-weight: bold; -fx-font-size: 14px;");
        row1.getChildren().addAll(lblUser, spacer, lblAmount);

        String typeText = isWithdraw ? "💸 Rút tiền" : "💰 Nạp tiền";
        StringBuilder infoSb = new StringBuilder(typeText);
        if (tx.getBankInfo() != null && !tx.getBankInfo().isBlank()) infoSb.append(" • ").append(tx.getBankInfo());
        if (tx.getRequestDate() != null) infoSb.append(" • ").append(tx.getRequestDate().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")));
        Label lblInfo = new Label(infoSb.toString());
        lblInfo.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px;");

        HBox btnRow = new HBox(10);
        Button btnApprove = new Button("✓ Duyệt");
        btnApprove.setStyle("-fx-background-color: linear-gradient(to right, #10B981, #34D399); -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(16,185,129,0.3), 6, 0, 0, 2);");
        btnApprove.setFont(Font.font("Times New Roman Bold", 12));
        btnApprove.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Duyệt giao dịch #" + tx.getRequestId() + "?\n" + typeText + ": " + formatMoney(tx.getAmount()), ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Xác nhận duyệt");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_APPROVE_TRANSACTION, tx.getRequestId()));
                    setStatus("⏳ Đang duyệt giao dịch #" + tx.getRequestId() + "...");
                }
            });
        });

        Button btnReject = new Button("✗ Từ chối");
        btnReject.setStyle("-fx-background-color: white; -fx-text-fill: #EF4444; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-weight: bold; -fx-border-color: #FCA5A5; -fx-border-radius: 10; -fx-border-width: 1.5;");
        btnReject.setFont(Font.font("Times New Roman Bold", 12));
        btnReject.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Từ chối giao dịch #" + tx.getRequestId() + "?", ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Xác nhận từ chối");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_REJECT_TRANSACTION, tx.getRequestId()));
                    setStatus("⏳ Đang từ chối giao dịch #" + tx.getRequestId() + "...");
                }
            });
        });

        btnRow.getChildren().addAll(btnApprove, btnReject);
        card.getChildren().addAll(row1, lblInfo, btnRow);
        return card;
    }

    private void setupTable() {
        colTxId.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                Label lbl = new Label("#" + tx.getRequestId());
                lbl.setStyle("-fx-text-fill: #6C63FF; -fx-font-weight: bold; -fx-font-family: 'Courier New'; -fx-font-size: 13px;");
                setGraphic(lbl);
            }
        });

        colUserInfo.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                String userName = (tx.getUser() != null && tx.getUser().getUsername() != null) ? tx.getUser().getUsername() : "ID#" + (tx.getUser() != null ? tx.getUser().getId() : "?");
                Label lblName = new Label("@" + userName);
                lblName.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 13px;");
                boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.getType());
                Label lblType = new Label(isWithdraw ? "💸 Rút tiền" : "💰 Nạp tiền");
                lblType.setStyle("-fx-text-fill: " + (isWithdraw ? "#B45309" : "#047857") + "; -fx-font-size: 11px; -fx-font-weight: bold;");
                setGraphic(new VBox(3, lblName, lblType));
            }
        });

        colAmount.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.getType());
                Label lbl = new Label((isWithdraw ? "− " : "+ ") + formatMoney(tx.getAmount()));
                lbl.setStyle("-fx-text-fill: " + (isWithdraw ? "#B45309" : "#047857") + "; -fx-font-weight: bold; -fx-font-size: 14px;");
                setGraphic(lbl);
            }
        });

        colTxStatus.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                String status = tx.getStatus();
                Label lbl = new Label(status != null ? status : "UNKNOWN");
                String style = switch (status != null ? status : "") {
                    case "PENDING"          -> "-fx-text-fill: #92400E; -fx-font-weight: bold; -fx-background-color: #FEF3C7; -fx-background-radius: 12; -fx-padding: 4 12 4 12;";
                    case "APPROVED"         -> "-fx-text-fill: #047857; -fx-font-weight: bold; -fx-background-color: #D1FAE5; -fx-background-radius: 12; -fx-padding: 4 12 4 12;";
                    case "REJECTED"         -> "-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-background-color: #FEE2E2; -fx-background-radius: 12; -fx-padding: 4 12 4 12;";
                    case "SUCCESS"          -> "-fx-text-fill: #1D4ED8; -fx-font-weight: bold; -fx-background-color: #DBEAFE; -fx-background-radius: 12; -fx-padding: 4 12 4 12;";
                    default                 -> "-fx-text-fill: #64748B; -fx-font-size: 12px;";
                };
                lbl.setStyle(style);
                setGraphic(lbl);
            }
        });

        colTxAction.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            private final Button btnApprove = new Button("✓ Duyệt");
            private final Button btnReject  = new Button("✗ Từ chối");
            private final HBox   box        = new HBox(6, btnApprove, btnReject);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnApprove.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 11px;");
                btnReject.setStyle("-fx-background-color: white; -fx-text-fill: #EF4444; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-border-color: #FCA5A5; -fx-border-radius: 8; -fx-font-size: 11px;");
                btnApprove.setPadding(new Insets(5, 10, 5, 10));
                btnReject.setPadding(new Insets(5, 10, 5, 10));
                btnApprove.setFont(Font.font("Times New Roman Bold", 11));
                btnReject.setFont(Font.font("Times New Roman Bold", 11));

                btnApprove.setOnAction(e -> {
                    TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                    if (tx == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Duyệt giao dịch #" + tx.getRequestId() + "?", ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Xác nhận duyệt");
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES)
                            CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_APPROVE_TRANSACTION, tx.getRequestId()));
                    });
                });

                btnReject.setOnAction(e -> {
                    TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                    if (tx == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Từ chối giao dịch #" + tx.getRequestId() + "?", ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Xác nhận từ chối");
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES)
                            CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_REJECT_TRANSACTION, tx.getRequestId()));
                    });
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                setGraphic("PENDING".equalsIgnoreCase(tx.getStatus()) ? box : null);
            }
        });

        transactionTable.setItems(filteredTxList);
    }

    @FXML void handleRefresh(ActionEvent event) { loadTransactions(); }

    @FXML public void goToHomePage(ActionEvent event) { switchPage(event, "/view/view/admin/The_Home_Page_Admin_View.fxml"); }
    @FXML public void goToAuctionPage(ActionEvent event) { switchPage(event, "/view/view/admin/The_Auction_Page_Admin_View.fxml"); }
    @FXML public void goToSettingsPage(ActionEvent event) { switchPage(event, "/view/view/admin/The_Settings_Page_Admin_View.fxml"); }

    private void switchPage(ActionEvent event, String fxmlPath) {
        unregisterAllHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof The_Auction_Page_Admin_View_Controller c) c.setUserData(currentUser);
            else if (ctrl instanceof The_Home_Page_Admin_View_Controller c) c.setUserData(currentUser);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void unregisterAllHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_APPROVED);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_REJECTED);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_CREATED); // Thêm dòng này
        MessageRouter.getInstance().unregister(ResponseCode.ERROR_MESSAGE);
    }

    private void setStatus(String msg) { Platform.runLater(() -> { if (lblStatusBar != null) lblStatusBar.setText(msg); }); }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.2f tỷ đ",  amount / 1_000_000_000.0);
        if (amount >= 1_000_000)     return String.format("%.0f tr đ",   amount / 1_000_000.0);
        return String.format("%,.0f đ", amount);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content); alert.showAndWait();
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.LOGOUT, null);
            SocketClient.getInstance().disconnect();
        }).thenRun(() -> {
            Platform.runLater(() -> {
                ClientSession.getInstance().clear();
                try {
                    Parent root = FXMLLoader.load(getClass().getResource("/view/view/auth/LoginView.fxml"));
                    Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                    stage.setScene(new Scene(root));
                    stage.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
    }
    private void updateLocalTransaction(Object payloadId, String newStatus) {
        if (payloadId == null) return;
        String idStr = String.valueOf(payloadId);

        // Dòng này cực kỳ quan trọng để bắt bệnh luồng mạng
        System.out.println(">>> ĐÃ NHẬN ĐƯỢC LỆNH CẬP NHẬT TỪ SERVER CHO GIAO DỊCH ID: " + idStr);

        Platform.runLater(() -> {
            boolean isChanged = false;
            for (int i = 0; i < allTxList.size(); i++) {
                TransactionRequest tx = allTxList.get(i);
                if (String.valueOf(tx.getRequestId()).equals(idStr)) {
                    tx.setTransactionStatus(newStatus);

                    // Thủ thuật: Ghi đè lại chính vị trí index đó để ép ObservableList nảy sinh sự kiện "thay đổi"
                    allTxList.set(i, tx);

                    isChanged = true;
                    break;
                }
            }

            if (isChanged) {
                System.out.println(">>> ĐANG VẼ LẠI GIAO DIỆN...");
                applySearchFilter();
                updateSummaryKPIs(allTxList);
                buildCashflowChart(allTxList);
                buildPendingRequestsSection(allTxList);
            } else {
                System.out.println(">>> KHÔNG TÌM THẤY GIAO DỊCH TRONG DANH SÁCH HIỆN TẠI!");
            }
        });
    }
}