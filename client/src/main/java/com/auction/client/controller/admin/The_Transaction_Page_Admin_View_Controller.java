package com.auction.client.controller.admin;

/*
 * ============================================================
 * FILE: The_Transaction_Page_Admin_View_Controller.java
 * ĐẶT TẠI: client/src/main/java/com/auction/client/controller/admin/
 *
 * THAY ĐỔI SO VỚI FILE GỐC (The_Transaction_Page_Admin_View_Controller.java
 * trong zip gốc — file đó đã có cơ bản nhưng nhiều lỗi nghiêm trọng):
 *
 * 1. [SỬA] @FXML tblTransactions → transactionTable (khớp fx:id trong FXML).
 *    Gốc: field tên "tblTransactions" nhưng FXML fx:id="transactionTable"
 *    → inject fail → TableView luôn null → NullPointerException.
 *
 * 2. [SỬA] Xóa 6 cột PropertyValueFactory, thay bằng 5 cột custom CellFactory.
 *    Lý do:
 *    - TransactionRequest.getId() trả về Object (không phải Integer)
 *      → PropertyValueFactory<>("id") crash ClassCastException.
 *    - TransactionRequest.getUser() trả về User object (nested)
 *      → PropertyValueFactory<>("userId") fail vì không có getUserId().
 *    - Custom lambda cell factory: an toàn tuyệt đối với nested objects.
 *
 * 3. [THÊM] buildCashflowChart(): vẽ LineChart 7 ngày với data thực.
 *    Gốc: có LineChart fx:id="cashflowChart" trong FXML nhưng controller
 *    KHÔNG BAO GIỜ populate → chart rỗng hoàn toàn.
 *    2 series: DEPOSIT APPROVED (xanh) + WITHDRAW APPROVED (đỏ).
 *
 * 4. [THÊM] buildPendingRequestsSection(): tạo card động từ PENDING transactions.
 *    Gốc: Mock card "@tran_binh / 10,000,000 đ" hardcode trong FXML
 *    → nút Duyệt/Từ chối không gắn action gì cả.
 *    Sau: Lọc PENDING từ data thực, mỗi card có 2 nút gọi socket.
 *
 * 5. [THÊM] updateSummaryKPIs(): cập nhật lblTotalInflow + lblPendingBadge.
 *    Gốc: 2 label này hardcode "542,000,000 đ" và "8 lệnh" trong FXML.
 *
 * 6. [THÊM] setupSearchFilter(): filter real-time theo ID hoặc username.
 *    Dùng ObservableList filteredTxList để không xóa data gốc.
 *
 * 7. [THÊM] unregister() tất cả handler khi chuyển màn (tránh memory leak).
 *    Gốc: không có unregister → MessageRouter giữ reference đến màn cũ.
 *
 * 8. [GIỮ] Socket-based ADMIN_GET_ALL_TRANSACTIONS, ADMIN_APPROVE_TRANSACTION,
 *          ADMIN_REJECT_TRANSACTION (đúng kiến trúc Client-Server).
 * ============================================================
 */

import com.auction.client.core.MessageRouter ;
import com.auction.client.core.SocketClient ;
import com.auction.common.model.TransactionRequest;
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
import java.util.stream.Collectors;
import java.util.ResourceBundle;

public class The_Transaction_Page_Admin_View_Controller implements Initializable {

    // =========================================================
    // FXML FIELDS — tên phải khớp fx:id trong FXML
    // =========================================================

    /** [SỬA] "tblTransactions" → "transactionTable" để khớp fx:id trong FXML */
    @FXML private TableView<TransactionRequest> transactionTable;

    /**
     * [SỬA] 5 cột Void thay vì 6 cột typed.
     * Dùng Void vì tất cả đều sẽ set bằng custom CellFactory.
     * "colUserInfo" = gộp user + type vì TransactionRequest không có getUserId().
     */
    @FXML private TableColumn<TransactionRequest, Void> colTxId;
    @FXML private TableColumn<TransactionRequest, Void> colUserInfo;
    @FXML private TableColumn<TransactionRequest, Void> colAmount;
    @FXML private TableColumn<TransactionRequest, Void> colTxStatus;
    @FXML private TableColumn<TransactionRequest, Void> colTxAction;

    /** [GIỮ] LineChart — fx:id đã có sẵn trong FXML gốc */
    @FXML private LineChart<String, Number> cashflowChart;

    /** [THÊM] Labels KPI — fx:id mới thêm vào FXML */
    @FXML private Label lblTotalInflow;
    @FXML private Label lblPendingBadge;
    @FXML private Label lblPendingHeader;
    @FXML private Label lblStatusBar;

    /** [THÊM] Search field — fx:id mới thêm vào FXML */
    @FXML private TextField tfSearch;

    /** [GIỮ] pendingRequestsContainer — fx:id đã có sẵn trong FXML gốc */
    @FXML private VBox pendingRequestsContainer;

    // =========================================================
    // STATE
    // =========================================================

    private User currentUser;

    /** Toàn bộ dữ liệu gốc — không bao giờ bị xóa bởi filter */
    private final ObservableList<TransactionRequest> allTxList      = FXCollections.observableArrayList();
    /** Dữ liệu hiển thị sau khi filter theo từ khóa */
    private final ObservableList<TransactionRequest> filteredTxList = FXCollections.observableArrayList();

    private final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupSearchFilter();
        registerRealtimeHandlers();
        loadTransactions();
    }

    public void setUserData(User user) {
        this.currentUser = user;
    }

    // =========================================================
    // ĐĂNG KÝ HANDLERS
    // =========================================================

    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT, this::onTransactionsReceived);

        // Sau khi duyệt/từ chối → server trả kết quả → tự động reload
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_TRANSACTION_APPROVED, msg -> {
                    setStatus("✅ Đã duyệt giao dịch #" + msg.getPayload());
                    loadTransactions();
                });
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_TRANSACTION_REJECTED, msg -> {
                    setStatus("✅ Đã từ chối giao dịch #" + msg.getPayload());
                    loadTransactions();
                });
        MessageRouter.getInstance().register(
                ResponseCode.ADMIN_TRANSACTION_FAILED, msg -> {
                    setStatus("❌ Lỗi: " + msg.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Lỗi xử lý", msg.getMessage());
                });
    }

    // =========================================================
    // DATA LOADING
    // =========================================================

    /**
     * Gửi request qua socket → server gọi TransactionDAO/PaymentDAO.
     * Không gọi DAO trực tiếp từ client (vi phạm kiến trúc).
     */
    private void loadTransactions() {
        SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_TRANSACTIONS, null);
        setStatus("⏳ Đang tải danh sách giao dịch...");
    }

    @SuppressWarnings("unchecked")
    private void onTransactionsReceived(Message message) {
        List<TransactionRequest> list = (List<TransactionRequest>) message.getPayload();
        if (list == null) list = Collections.emptyList();

        allTxList.setAll(list);
        applySearchFilter();         // Cập nhật filteredTxList

        updateSummaryKPIs(list);     // Cập nhật 2 KPI header
        buildCashflowChart(list);    // Vẽ line chart 7 ngày
        buildPendingRequestsSection(list); // Tạo card động

        setStatus("✅ Đã tải " + list.size() + " giao dịch.");
    }

    // =========================================================
    // CẬP NHẬT KPI HEADER
    // =========================================================

    /**
     * Tính và hiển thị:
     * - lblTotalInflow: tổng DEPOSIT APPROVED trong tháng hiện tại
     * - lblPendingBadge: số lệnh WITHDRAW đang PENDING
     */
    private void updateSummaryKPIs(List<TransactionRequest> list) {
        LocalDate now = LocalDate.now();

        double totalInflow = list.stream()
                .filter(tx -> "DEPOSIT".equalsIgnoreCase(tx.getType())
                        && "APPROVED".equalsIgnoreCase(tx.getStatus())
                        && tx.getRequestDate() != null
                        && tx.getRequestDate().getYear()       == now.getYear()
                        && tx.getRequestDate().getMonthValue() == now.getMonthValue())
                .mapToDouble(TransactionRequest::getAmount).sum();

        long pendingWithdrawCount = list.stream()
                .filter(tx -> "WITHDRAW".equalsIgnoreCase(tx.getType())
                        && "PENDING".equalsIgnoreCase(tx.getStatus()))
                .count();

        long totalPending = list.stream()
                .filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus())).count();

        if (lblTotalInflow  != null) lblTotalInflow.setText("+" + formatMoney(totalInflow));
        if (lblPendingBadge != null) lblPendingBadge.setText(pendingWithdrawCount + " lệnh");
        if (lblPendingHeader != null)
            lblPendingHeader.setText("Cần duyệt ngay (" + totalPending + ")");
    }

    // =========================================================
    // CASHFLOW LINE CHART (7 ngày)
    // =========================================================

    /**
     * [THÊM] Vẽ xu hướng dòng tiền 7 ngày gần nhất.
     * 2 series:
     *   - "Nạp tiền" (xanh lá): DEPOSIT APPROVED, đơn vị triệu đồng
     *   - "Rút tiền" (đỏ):      WITHDRAW APPROVED, đơn vị triệu đồng
     *
     * Lý do dùng APPROVED: chỉ giao dịch đã xác nhận mới phản ánh dòng tiền thực.
     * Lý do đơn vị triệu: tránh trục Y quá lớn, khó đọc.
     */
    private void buildCashflowChart(List<TransactionRequest> list) {
        if (cashflowChart == null) return;
        cashflowChart.getData().clear();
        cashflowChart.setTitle("Xu hướng dòng tiền");

        LocalDate today = LocalDate.now();
        // LinkedHashMap để giữ thứ tự ngày từ cũ → mới
        Map<String, Double> depositByDay  = new LinkedHashMap<>();
        Map<String, Double> withdrawByDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String key = today.minusDays(i).format(dayFormatter);
            depositByDay.put(key,  0.0);
            withdrawByDay.put(key, 0.0);
        }

        for (TransactionRequest tx : list) {
            if (tx.getRequestDate() == null)                            continue;
            if (!"APPROVED".equalsIgnoreCase(tx.getStatus()))          continue;
            LocalDate txDay = tx.getRequestDate().toLocalDate();
            if (txDay.isBefore(today.minusDays(6)) || txDay.isAfter(today)) continue;

            String key = txDay.format(dayFormatter);
            if ("DEPOSIT".equalsIgnoreCase(tx.getType())) {
                depositByDay.merge(key, tx.getAmount(), Double::sum);
            } else if ("WITHDRAW".equalsIgnoreCase(tx.getType())) {
                withdrawByDay.merge(key, tx.getAmount(), Double::sum);
            }
        }

        XYChart.Series<String, Number> depositSeries = new XYChart.Series<>();
        depositSeries.setName("Nạp tiền");
        depositByDay.forEach((day, amt) ->
                depositSeries.getData().add(new XYChart.Data<>(day, amt / 1_000_000.0)));

        XYChart.Series<String, Number> withdrawSeries = new XYChart.Series<>();
        withdrawSeries.setName("Rút tiền");
        withdrawByDay.forEach((day, amt) ->
                withdrawSeries.getData().add(new XYChart.Data<>(day, amt / 1_000_000.0)));

        cashflowChart.getData().addAll(depositSeries, withdrawSeries);

        // Phải set style sau khi add vào chart (JavaFX render async)
        javafx.application.Platform.runLater(() -> {
            if (depositSeries.getNode() != null)
                depositSeries.getNode().setStyle("-fx-stroke: #05CD99; -fx-stroke-width: 2.5;");
            if (withdrawSeries.getNode() != null)
                withdrawSeries.getNode().setStyle("-fx-stroke: #FF5B5C; -fx-stroke-width: 2.5;");
        });
    }

    // =========================================================
    // PENDING CARDS ĐỘNG
    // =========================================================

    /**
     * [THÊM] Tạo card UI động cho từng giao dịch PENDING.
     * Xóa sạch mock data hardcode trong FXML, thay bằng data thực từ server.
     *
     * Sắp xếp: mới nhất lên trên.
     * Mỗi card: tên user | số tiền | loại | ngân hàng | 2 nút Duyệt/Từ chối.
     */
    private void buildPendingRequestsSection(List<TransactionRequest> list) {
        if (pendingRequestsContainer == null) return;
        pendingRequestsContainer.getChildren().clear();

        List<TransactionRequest> pendingList = list.stream()
                .filter(tx -> "PENDING".equalsIgnoreCase(tx.getStatus()))
                .sorted(Comparator.comparing(
                        tx -> tx.getRequestDate() == null
                                ? java.time.LocalDateTime.MIN : tx.getRequestDate(),
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        if (pendingList.isEmpty()) {
            Label empty = new Label("✅ Không có giao dịch nào chờ duyệt.");
            empty.setStyle("-fx-text-fill: #05CD99; -fx-font-size: 13px;");
            pendingRequestsContainer.getChildren().add(empty);
            return;
        }

        for (TransactionRequest tx : pendingList) {
            pendingRequestsContainer.getChildren().add(buildPendingCard(tx));
        }
    }

    /**
     * Tạo một card VBox cho một lệnh PENDING.
     * Cấu trúc card:
     *   Dòng 1: @username [bên trái]   |   ± số tiền [bên phải]
     *   Dòng 2: Loại GD • bankInfo • thời gian
     *   Dòng 3: [✓ Duyệt]  [✗ Từ chối]
     */
    private VBox buildPendingCard(TransactionRequest tx) {
        VBox card = new VBox(8);
        card.setStyle("-fx-border-color: #E9EDF7; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 12 0;");

        // --- Dòng 1: Tên user + số tiền ---
        HBox row1 = new HBox(10);
        row1.setAlignment(Pos.CENTER_LEFT);

        String userName = (tx.getUser() != null && tx.getUser().getUsername() != null)
                ? "@" + tx.getUser().getUsername()
                : "User #" + (tx.getUser() != null ? tx.getUser().getId() : "?");

        Label lblUser = new Label(userName);
        lblUser.setStyle("-fx-text-fill: #2b3674; -fx-font-weight: bold; -fx-font-size: 14px;");
        lblUser.setFont(Font.font("Times New Roman Bold", 14));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.getType());
        String  amountSign  = isWithdraw ? "- " : "+ ";
        String  amountColor = isWithdraw ? "#a53e3e" : "#05CD99";
        Label lblAmount = new Label(amountSign + formatMoney(tx.getAmount()));
        lblAmount.setStyle("-fx-text-fill: " + amountColor
                + "; -fx-font-weight: bold; -fx-font-size: 14px;");

        row1.getChildren().addAll(lblUser, spacer, lblAmount);

        // --- Dòng 2: Loại + bank + thời gian ---
        String typeText = isWithdraw ? "Rút tiền" : "Nạp tiền";
        StringBuilder infoSb = new StringBuilder(typeText);
        if (tx.getBankInfo() != null && !tx.getBankInfo().isBlank())
            infoSb.append(" • ").append(tx.getBankInfo());
        if (tx.getRequestDate() != null)
            infoSb.append(" • ")
                    .append(tx.getRequestDate().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")));
        Label lblInfo = new Label(infoSb.toString());
        lblInfo.setStyle("-fx-text-fill: #A3AED0; -fx-font-size: 12px;");

        // --- Dòng 3: Nút Duyệt / Từ chối (gọi socket) ---
        HBox btnRow = new HBox(10);

        Button btnApprove = new Button("✓ Duyệt");
        btnApprove.setStyle("-fx-background-color: #79a49e; -fx-text-fill: white; "
                + "-fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold;");
        btnApprove.setFont(Font.font("Times New Roman Bold", 12));
        btnApprove.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Duyệt giao dịch #" + tx.getRequestId()
                            + "\n" + typeText + ": " + formatMoney(tx.getAmount())
                            + "\nUser: " + userName,
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Xác nhận duyệt");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    SocketClient.getInstance().sendRequest(
                            RequestCode.ADMIN_APPROVE_TRANSACTION, tx.getRequestId());
                    setStatus("⏳ Đang duyệt giao dịch #" + tx.getRequestId() + "...");
                }
            });
        });

        Button btnReject = new Button("✗ Từ chối");
        btnReject.setStyle("-fx-background-color: #F4F7FE; -fx-text-fill: #a53e3e; "
                + "-fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold; "
                + "-fx-border-color: #FFCCCC; -fx-border-radius: 8;");
        btnReject.setFont(Font.font("Times New Roman Bold", 12));
        btnReject.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Từ chối giao dịch #" + tx.getRequestId() + "?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Xác nhận từ chối");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    SocketClient.getInstance().sendRequest(
                            RequestCode.ADMIN_REJECT_TRANSACTION, tx.getRequestId());
                    setStatus("⏳ Đang từ chối giao dịch #" + tx.getRequestId() + "...");
                }
            });
        });

        btnRow.getChildren().addAll(btnApprove, btnReject);
        card.getChildren().addAll(row1, lblInfo, btnRow);
        return card;
    }

    // =========================================================
    // TABLE SETUP (custom CellFactory thay PropertyValueFactory)
    // =========================================================

    /**
     * Lý do KHÔNG dùng PropertyValueFactory:
     * - TransactionRequest.getId() trả về Object → ClassCastException
     * - TransactionRequest.getUser() trả về User object (không có getUserId())
     * - Custom lambda hoàn toàn kiểm soát được null, nested object
     */
    private void setupTable() {

        // --- CỘT 1: Mã GD ---
        colTxId.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                Label lbl = new Label("#" + tx.getRequestId());
                lbl.setStyle("-fx-text-fill: #4318FF; -fx-font-weight: bold; "
                        + "-fx-font-family: 'Courier New'; -fx-font-size: 13px;");
                setGraphic(lbl);
            }
        });

        // --- CỘT 2: Người dùng & Loại (gộp user + type thành 1 cell) ---
        colUserInfo.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();

                String userName = (tx.getUser() != null && tx.getUser().getUsername() != null)
                        ? tx.getUser().getUsername()
                        : "ID#" + (tx.getUser() != null ? tx.getUser().getId() : "?");
                Label lblName = new Label("@" + userName);
                lblName.setStyle("-fx-text-fill: #2B3674; -fx-font-weight: bold; -fx-font-size: 13px;");

                boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.getType());
                Label lblType = new Label(isWithdraw ? "💸 Rút tiền" : "💰 Nạp tiền");
                lblType.setStyle("-fx-text-fill: " + (isWithdraw ? "#FF8800" : "#05CD99")
                        + "; -fx-font-size: 11px;");

                setGraphic(new VBox(3, lblName, lblType));
            }
        });

        // --- CỘT 3: Số tiền (có dấu + / - và màu tương ứng) ---
        colAmount.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(tx.getType());
                Label lbl = new Label((isWithdraw ? "- " : "+ ") + formatMoney(tx.getAmount()));
                lbl.setStyle("-fx-text-fill: " + (isWithdraw ? "#a53e3e" : "#05CD99")
                        + "; -fx-font-weight: bold; -fx-font-size: 13px;");
                setGraphic(lbl);
            }
        });

        // --- CỘT 4: Trạng thái (badge với background color) ---
        colTxStatus.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                String status = tx.getStatus();
                Label lbl = new Label(status != null ? status : "UNKNOWN");

                String color, bg;
                switch (status != null ? status : "") {
                    case "PENDING"  -> { color = "#FF8800"; bg = "#FFF4E5"; }
                    case "APPROVED" -> { color = "#05CD99"; bg = "#E6FBF7"; }
                    case "REJECTED" -> { color = "#FF5B5C"; bg = "#FFF0F0"; }
                    default         -> { color = "#707EAE"; bg = "#F4F7FE"; }
                }
                lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; "
                        + "-fx-background-color: " + bg + "; -fx-background-radius: 8; "
                        + "-fx-font-size: 12px;");
                lbl.setPadding(new Insets(3, 8, 3, 8));
                setGraphic(lbl);
            }
        });

        // --- CỘT 5: Thao tác (Duyệt/Từ chối — chỉ hiện khi PENDING) ---
        colTxAction.setCellFactory(col -> new TableCell<TransactionRequest, Void>() {
            private final Button btnApprove = new Button("Duyệt");
            private final Button btnReject  = new Button("Từ chối");
            private final HBox   box        = new HBox(6, btnApprove, btnReject);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnApprove.setStyle("-fx-background-color: #05CD99; -fx-text-fill: white; "
                        + "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");
                btnReject.setStyle("-fx-background-color: #FF5B5C; -fx-text-fill: white; "
                        + "-fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");
                btnApprove.setFont(Font.font("Times New Roman Bold", 11));
                btnReject.setFont(Font.font("Times New Roman Bold", 11));

                btnApprove.setOnAction(e -> {
                    TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                    if (tx == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Duyệt giao dịch #" + tx.getRequestId() + "?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Xác nhận duyệt");
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES)
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_APPROVE_TRANSACTION, tx.getRequestId());
                    });
                });

                btnReject.setOnAction(e -> {
                    TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                    if (tx == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Từ chối giao dịch #" + tx.getRequestId() + "?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Xác nhận từ chối");
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES)
                            SocketClient.getInstance().sendRequest(
                                    RequestCode.ADMIN_REJECT_TRANSACTION, tx.getRequestId());
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                TransactionRequest tx = (TransactionRequest) getTableRow().getItem();
                // Chỉ hiện nút khi PENDING; trạng thái khác = không thể hành động
                setGraphic("PENDING".equalsIgnoreCase(tx.getStatus()) ? box : null);
            }
        });

        transactionTable.setItems(filteredTxList);
    }

    // =========================================================
    // SEARCH FILTER REAL-TIME
    // =========================================================

    /**
     * [THÊM] Gắn listener vào tfSearch.textProperty() để filter real-time.
     * Lọc theo: requestId, username, hoặc loại giao dịch.
     * Dùng filteredTxList để không xóa allTxList gốc.
     */
    private void setupSearchFilter() {
        if (tfSearch == null) return;
        tfSearch.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter());
    }

    private void applySearchFilter() {
        String keyword = (tfSearch != null && tfSearch.getText() != null)
                ? tfSearch.getText().trim().toLowerCase() : "";

        if (keyword.isEmpty()) {
            filteredTxList.setAll(allTxList);
        } else {
            filteredTxList.setAll(allTxList.stream()
                    .filter(tx -> {
                        if (String.valueOf(tx.getRequestId()).contains(keyword)) return true;
                        if (tx.getUser() != null && tx.getUser().getUsername() != null
                                && tx.getUser().getUsername().toLowerCase().contains(keyword)) return true;
                        if (tx.getType() != null
                                && tx.getType().toLowerCase().contains(keyword)) return true;
                        return false;
                    })
                    .collect(Collectors.toList()));
        }
    }

    // =========================================================
    // FXML HANDLERS
    // =========================================================

    @FXML
    void handleRefresh(ActionEvent event) {
        loadTransactions();
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    @FXML public void goToHomePage(ActionEvent event) {
        switchPage(event, "/view/view/admin/The_Home_Page_Admin_View.fxml");
    }
    @FXML public void goToAuctionPage(ActionEvent event) {
        switchPage(event, "/view/view/admin/The_Auction_Page_Admin_View.fxml");
    }
    @FXML public void goToSettingsPage(ActionEvent event) {
        switchPage(event, "/view/view/admin/The_Settings_Page_Admin_View.fxml");
    }

    private void switchPage(ActionEvent event, String fxmlPath) {
        // [THÊM] Hủy handler trước khi rời màn hình
        unregisterAllHandlers();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof The_Auction_Page_Admin_View_Controller c)
                c.setUserData(currentUser);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unregisterAllHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_APPROVED);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_REJECTED);
        MessageRouter.getInstance().unregister(ResponseCode.ADMIN_TRANSACTION_FAILED);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void setStatus(String msg) {
        if (lblStatusBar != null) lblStatusBar.setText(msg);
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.2f tỷ đ",  amount / 1_000_000_000.0);
        if (amount >= 1_000_000)     return String.format("%.0f tr đ",   amount / 1_000_000.0);
        return String.format("%,.0f đ", amount);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}