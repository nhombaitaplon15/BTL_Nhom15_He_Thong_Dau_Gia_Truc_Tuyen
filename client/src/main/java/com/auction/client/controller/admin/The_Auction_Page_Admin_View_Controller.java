package com.auction.client.controller.admin;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.client.core.ClientSession;
import com.auction.common.model.Auction;
import com.auction.common.model.IssueRecord;
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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class The_Auction_Page_Admin_View_Controller implements Initializable {

    @FXML private TableView<Auction>            auctionTable;
    @FXML private TableColumn<Auction, Void>    colItemAndId;
    @FXML private TableColumn<Auction, Void>    colParticipants;
    @FXML private TableColumn<Auction, Void>    colFinancials;
    @FXML private TableColumn<Auction, Void>    colStatusAndTime;
    @FXML private TableColumn<Auction, Void>    colAction;
    @FXML private Label                         lblStatusBar;

    @FXML private TextField tfSearch;
    @FXML private ComboBox<String> cbStatus;
    @FXML private ComboBox<String> cbTime;
    @FXML private Label lblAdminName, lblPendingCount;

    private User currentUser;
    private final ObservableList<Auction> allAuctionList      = FXCollections.observableArrayList();
    private final ObservableList<Auction> filteredAuctionList = FXCollections.observableArrayList();
    private final java.util.Set<Integer> reportedAuctionIds   = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupPremiumTable();
        setupSearchAndFilter();
        registerRealtimeHandlers();
        loadAuctions();

        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_ISSUES, null);
        });
    }

    public void setUserData(User user) {
        this.currentUser = user;
    }

    private void setupSearchAndFilter() {
        if (cbStatus != null) {
            cbStatus.getItems().addAll("Tất cả", "WAITING_FOR_ADMIN", "OPEN", "RUNNING", "REJECTED", "SOLD", "FINISHED", "BLOCKED");
            cbStatus.getSelectionModel().selectFirst();
            cbStatus.setOnAction(e -> applyFilter());
        }
        if (cbTime != null) {
            cbTime.getItems().addAll("Tất cả", "Hôm nay", "7 ngày qua", "30 ngày qua");
            cbTime.getSelectionModel().selectFirst();
            cbTime.setOnAction(e -> applyFilter());
        }
        if (tfSearch != null) {
            tfSearch.textProperty().addListener((obs, old, newVal) -> applyFilter());
        }
    }

    private void applyFilter() {
        String keyword = (tfSearch != null && tfSearch.getText() != null) ? tfSearch.getText().trim().toLowerCase() : "";
        String statusSel = (cbStatus != null && cbStatus.getValue() != null) ? cbStatus.getValue() : "Tất cả";
        String timeSel   = (cbTime   != null && cbTime.getValue()   != null) ? cbTime.getValue()   : "Tất cả";

        java.time.LocalDateTime cutoff = null;
        if ("Hôm nay".equals(timeSel)) {
            cutoff = java.time.LocalDate.now().atStartOfDay();
        } else if ("7 ngày qua".equals(timeSel)) {
            cutoff = java.time.LocalDate.now().minusDays(7).atStartOfDay();
        } else if ("30 ngày qua".equals(timeSel)) {
            cutoff = java.time.LocalDate.now().minusDays(30).atStartOfDay();
        }

        final java.time.LocalDateTime finalCutoff = cutoff;

        List<Auction> result = allAuctionList.stream()
            .filter(ac -> {
                if (!keyword.isEmpty()) {
                    boolean matchId   = String.valueOf(ac.getAuctionId()).contains(keyword);
                    boolean matchItem = String.valueOf(ac.getItemId()).contains(keyword);
                    if (!matchId && !matchItem) return false;
                }
                if (!"Tất cả".equals(statusSel)) {
                    if (!statusSel.equalsIgnoreCase(ac.getAuctionStatus())) return false;
                }
                if (finalCutoff != null && ac.getEndTime() != null) {
                    if (ac.getEndTime().isBefore(finalCutoff)) return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        filteredAuctionList.setAll(result);
        setStatus("🔍 Hiển thị " + result.size() + " / " + allAuctionList.size() + " phiên.");
    }

    private void registerRealtimeHandlers() {
        MessageRouter.getInstance().register(ResponseCode.ADMIN_ALL_AUCTIONS_RESULT, this::onAuctionsReceived);
        MessageRouter.getInstance().register(ResponseCode.ADMIN_APPROVE_SUCCESS, msg -> onActionSuccess(msg, "duyệt"));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_APPROVE_FAILED,  msg -> onActionFailed(msg));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_REJECT_SUCCESS,  msg -> onActionSuccess(msg, "từ chối"));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_REJECT_FAILED,   msg -> onActionFailed(msg));

        MessageRouter.getInstance().register(ResponseCode.ADMIN_BLOCK_SUCCESS, msg -> {
            Platform.runLater(() -> {
                Object payload = msg.getPayload();
                if (payload instanceof Integer blockedId) {
                    allAuctionList.stream()
                        .filter(ac -> ac.getAuctionId() == blockedId)
                        .findFirst()
                        .ifPresent(ac -> ac.setAuctionStatus("BLOCKED"));
                    applyFilter();
                    auctionTable.refresh();
                    setStatus("🚫 Phiên #" + blockedId + " đã bị CHẶN — sẽ tự xóa khỏi bảng sau 5 phút.");
                } else {
                    loadAuctions();
                    setStatus("🚫 Chặn phiên thành công!");
                }
            });
        });

        MessageRouter.getInstance().register(ResponseCode.ADMIN_DELETE_BLOCKED_SUCCESS, msg -> {
            Platform.runLater(() -> {
                Object payload = msg.getPayload();
                if (payload instanceof Integer deletedId) {
                    allAuctionList.removeIf(ac -> ac.getAuctionId() == deletedId);
                    applyFilter();
                    setStatus("🗑 Phiên #" + deletedId + " đã được xóa hoàn toàn khỏi hệ thống.");
                }
            });
        });

        MessageRouter.getInstance().register(ResponseCode.ADMIN_BLOCK_FAILED,    msg -> onActionFailed(msg));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_CREATED, msg -> onTransactionCreated());
        MessageRouter.getInstance().register(ResponseCode.ADMIN_TRANSACTION_FAILED,  msg -> onActionFailed(msg));
        MessageRouter.getInstance().register(ResponseCode.ADMIN_NEW_PENDING_AUCTION, msg -> {
            Platform.runLater(() -> {
                setStatus("🔔 Có phiên mới cần duyệt! Đang tải lại...");
                loadAuctions();
            });
        });

        MessageRouter.getInstance().register(ResponseCode.ADMIN_ISSUES_RESULT, msg -> {
            Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                java.util.List<IssueRecord> issues = (java.util.List<IssueRecord>) msg.getPayload();
                reportedAuctionIds.clear();
                if (issues != null) {
                    issues.forEach(r -> reportedAuctionIds.add(r.getAuctionId()));
                }
                auctionTable.refresh();
                applyFilter();
            });
        });

        MessageRouter.getInstance().register(ResponseCode.ADMIN_NEW_ISSUE, msg -> {
            Platform.runLater(() -> {
                IssueRecord issue = (IssueRecord) msg.getPayload();
                if (issue != null) {
                    reportedAuctionIds.add(issue.getAuctionId());
                    auctionTable.refresh();
                    setStatus("⚠ Báo cáo mới tại phiên #" + issue.getAuctionId() + " | " + issue.getIssueType());
                }
            });
        });
    }

    private void loadAuctions() {
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_AUCTIONS, null);
        });
        setStatus("⏳ Đang tải danh sách phiên...");
    }

    private void onAuctionsReceived(Message message) {
        List<Auction> list = (List<Auction>) message.getPayload();
        Platform.runLater(() -> {
            allAuctionList.clear();
            if (list != null) allAuctionList.addAll(list);
            applyFilter();
            setStatus("✅ Đã tải " + allAuctionList.size() + " phiên đấu giá.");
        });
    }

    private void onActionSuccess(Message msg, String action) {
        Platform.runLater(() -> {
            setStatus("✅ " + action.substring(0, 1).toUpperCase() + action.substring(1) + " phiên thành công: #" + msg.getPayload());
            loadAuctions();
        });
    }

    private void onActionFailed(Message msg) {
        Platform.runLater(() -> {
            setStatus("❌ Thao tác thất bại: " + msg.getMessage());
            showAlert(Alert.AlertType.ERROR, "Lỗi Thực Thi", msg.getMessage());
        });
    }

    private void onTransactionCreated() {
        Platform.runLater(() -> {
            setStatus("✅ Đã tạo giao dịch thành công!");
            showAlert(Alert.AlertType.INFORMATION, "Thành Công", "Giao dịch đã được tạo. Sang tab Quản Lý Giao Dịch để kiểm tra.");
            loadAuctions();
        });
    }

    private void setupPremiumTable() {
        colItemAndId.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                Label lblTitle = new Label("Sản phẩm #" + ac.getItemId());
                lblTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 14px;");
                Label lblId = new Label("MÃ PHIÊN: #" + ac.getAuctionId());
                lblId.setStyle("-fx-text-fill: #94A3B8; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
                setGraphic(new VBox(4, lblTitle, lblId));
            }
        });

        colParticipants.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                Label lblSeller = new Label("Người bán ID: " + ac.getSellerId() + " ★");
                lblSeller.setStyle("-fx-text-fill: #6C63FF; -fx-font-size: 13px; -fx-font-weight: bold;");
                String winnerText = (ac.getCurrentWinnerId() != null && ac.getCurrentWinnerId() > 0) ? "🏆 Đang dẫn: ID " + ac.getCurrentWinnerId() : "Chưa có lượt đặt";
                Label lblWinner = new Label(winnerText);
                lblWinner.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px; -fx-font-style: italic;");
                setGraphic(new VBox(4, lblSeller, lblWinner));
            }
        });

        colFinancials.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                Label lblPrice = new Label(String.format("%,.0f đ", ac.getCurrentPrice()));
                lblPrice.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #10B981;");
                String bidText = ac.getTotalBids() > 10 ? "🔥 " + ac.getTotalBids() + " lượt bids" : ac.getTotalBids() + " lượt bids";
                Label lblBids = new Label(bidText);
                lblBids.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
                setGraphic(new VBox(4, lblPrice, lblBids));
            }
        });

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        colStatusAndTime.setCellFactory(param -> new TableCell<Auction, Void>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Auction ac = (Auction) getTableRow().getItem();
                String status = ac.getAuctionStatus();
                Label lblStatus = new Label(status);
                String badgeStyle = switch (status != null ? status : "") {
                    case "WAITING_FOR_ADMIN" -> "-fx-text-fill: #B45309; -fx-font-weight: bold; -fx-background-color: #FEF3C7; -fx-background-radius: 10; -fx-padding: 3 10 3 10;";
                    case "OPEN"              -> "-fx-text-fill: #047857; -fx-font-weight: bold; -fx-background-color: #D1FAE5; -fx-background-radius: 10; -fx-padding: 3 10 3 10;";
                    case "RUNNING"           -> "-fx-text-fill: #1D4ED8; -fx-font-weight: bold; -fx-background-color: #DBEAFE; -fx-background-radius: 10; -fx-padding: 3 10 3 10;";
                    case "SOLD","FINISHED"   -> "-fx-text-fill: #7C3AED; -fx-font-weight: bold; -fx-background-color: #EDE9FE; -fx-background-radius: 10; -fx-padding: 3 10 3 10;";
                    case "REJECTED"          -> "-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-background-color: #FEE2E2; -fx-background-radius: 10; -fx-padding: 3 10 3 10;";
                    case "BLOCKED"           -> "-fx-text-fill: #374151; -fx-font-weight: bold; -fx-background-color: #F3F4F6; -fx-background-radius: 10; -fx-padding: 3 10 3 10;";
                    default                  -> "-fx-text-fill: #64748B;";
                };
                lblStatus.setStyle(badgeStyle);
                Label lblTime = new Label(ac.getEndTime() != null ? "Kết thúc: " + ac.getEndTime().format(formatter) : "Chưa có thời gian");
                lblTime.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
                VBox box = new VBox(6, lblStatus, lblTime);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        colAction.setCellFactory(param -> new TableCell<Auction, Void>() {
            private final Button btnInfo        = new Button("Xem");
            private final Button btnApprove     = new Button("Duyệt");
            private final Button btnReject      = new Button("Từ chối");
            private final Button btnBlock       = new Button("Chặn");
            private final Button btnTransaction = new Button("Giao dịch");
            private final Button btnReport      = new Button("⚠ Báo cáo");
            private final HBox   container      = new HBox(6);

            {
                container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                btnInfo.setStyle("-fx-background-color: #6C63FF; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 11px;");
                btnApprove.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 11px;");
                btnReject.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 11px;");
                btnBlock.setStyle("-fx-background-color: #F59E0B; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 11px;");
                btnTransaction.setStyle("-fx-background-color: #EDE9FE; -fx-text-fill: #7C3AED; -fx-border-color: #C4B5FD; -fx-border-radius: 8; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 11px;");
                btnReport.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-border-color: #FCA5A5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 11px;");

                btnInfo.setPadding(new javafx.geometry.Insets(5, 10, 5, 10));
                btnApprove.setPadding(new javafx.geometry.Insets(5, 10, 5, 10));
                btnReject.setPadding(new javafx.geometry.Insets(5, 10, 5, 10));
                btnBlock.setPadding(new javafx.geometry.Insets(5, 10, 5, 10));
                btnTransaction.setPadding(new javafx.geometry.Insets(5, 10, 5, 10));
                btnReport.setPadding(new javafx.geometry.Insets(5, 10, 5, 10));

                btnInfo.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    showAlert(Alert.AlertType.INFORMATION,
                        "Chi Tiết Phiên #" + ac.getAuctionId(),
                        "Mã sản phẩm: "    + ac.getItemId()
                            + "\nMã người bán: " + ac.getSellerId()
                            + "\nGiá khởi điểm: " + String.format("%,.0f đ", ac.getStartingPrice())
                            + "\nGiá hiện tại: "  + String.format("%,.0f đ", ac.getCurrentPrice())
                            + "\nTổng bids: "     + ac.getTotalBids()
                            + "\nTrạng thái: "    + ac.getAuctionStatus());
                });

                btnApprove.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Duyệt và mở phòng đấu giá #" + ac.getAuctionId() + "?", ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES)
                            CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_APPROVE_AUCTION, ac.getAuctionId()));
                    });
                });

                btnReject.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    TextInputDialog dialog = new TextInputDialog("Vi phạm điều khoản");
                    dialog.setTitle("Từ Chối Phiên #" + ac.getAuctionId());
                    dialog.setContentText("Nhập lý do từ chối:");
                    dialog.showAndWait().ifPresent(reason -> {
                        if (!reason.trim().isEmpty())
                            CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_REJECT_AUCTION, new Object[]{ac.getAuctionId(), reason.trim()}));
                    });
                });

                btnBlock.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "🚫 CHẶN KHẨN CẤP phiên #" + ac.getAuctionId() + "?", ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Xác nhận Chặn");
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            ac.setAuctionStatus("BLOCKED");
                            auctionTable.refresh();
                            setStatus("⏳ Đang chặn phiên #" + ac.getAuctionId() + "...");
                            CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_BLOCK_AUCTION, ac.getAuctionId()));
                        }
                    });
                });

                btnTransaction.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    String winnerInfo = (ac.getCurrentWinnerId() != null && ac.getCurrentWinnerId() > 0) ? "Người thắng: ID#" + ac.getCurrentWinnerId() : "Không có người thắng";
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Tạo giao dịch cho phiên #" + ac.getAuctionId() + "?\nGiá: " + String.format("%,.0f đ", ac.getCurrentPrice()) + " | " + winnerInfo, ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.YES) {
                            int winnerId = (ac.getCurrentWinnerId() != null) ? ac.getCurrentWinnerId() : 0;
                            CompletableFuture.runAsync(() -> SocketClient.getInstance().sendRequest(RequestCode.ADMIN_CREATE_TRANSACTION, new Object[]{ac.getAuctionId(), winnerId, ac.getCurrentPrice()}));
                        }
                    });
                });

                btnReport.setOnAction(e -> {
                    Auction ac = (Auction) getTableRow().getItem();
                    if (ac == null) return;
                    showReportDialog(ac.getAuctionId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    if (getTableRow() != null) getTableRow().setStyle("");
                    return;
                }
                Auction ac = (Auction) getTableRow().getItem();
                String status = ac.getAuctionStatus();

                boolean hasReport = reportedAuctionIds.contains(ac.getAuctionId());
                getTableRow().setStyle(hasReport ? "-fx-background-color: #FFF0F0;" : "");

                container.getChildren().clear();
                container.getChildren().add(btnInfo);
                switch (status != null ? status : "") {
                    case "WAITING_FOR_ADMIN" -> container.getChildren().addAll(btnApprove, btnReject);
                    case "OPEN"              -> container.getChildren().add(btnBlock);
                    case "RUNNING"           -> container.getChildren().addAll(btnBlock, btnTransaction);
                    case "CLOSED", "FINISHED", "SOLD", "ENDED" -> container.getChildren().add(btnTransaction);
                }
                if (hasReport) container.getChildren().add(btnReport);
                setGraphic(container);
            }
        });
        auctionTable.setItems(filteredAuctionList);
    }

    private void showReportDialog(int auctionId) {
        CompletableFuture.runAsync(() -> {
            SocketClient.getInstance().sendRequest(RequestCode.ADMIN_GET_ALL_ISSUES, null);
        });

        MessageRouter.getInstance().register(ResponseCode.ADMIN_ISSUES_RESULT, msg -> {
            MessageRouter.getInstance().unregister(ResponseCode.ADMIN_ISSUES_RESULT);
            Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                java.util.List<IssueRecord> allIssues = (java.util.List<IssueRecord>) msg.getPayload();
                reportedAuctionIds.clear();
                if (allIssues != null) allIssues.forEach(r -> reportedAuctionIds.add(r.getAuctionId()));
                auctionTable.refresh();

                java.util.List<IssueRecord> filtered = (allIssues == null) ? java.util.Collections.emptyList() : allIssues.stream().filter(r -> r.getAuctionId() == auctionId).collect(java.util.stream.Collectors.toList());
                buildAndShowIssueStage(auctionId, filtered);
            });
        });
    }

    private void buildAndShowIssueStage(int auctionId, java.util.List<IssueRecord> issues) {
        Stage stage = new Stage();
        stage.setTitle("⚠ Báo cáo sự cố — Phiên #" + auctionId);
        VBox root = new VBox(10);
        root.setStyle("-fx-background-color: #FAFAFA; -fx-padding: 20;");
        Label title = new Label("⚠  Báo Cáo Sự Cố — Phiên #" + auctionId);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
        Label subtitle = new Label("Tổng: " + issues.size() + " báo cáo");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        VBox issueList = new VBox(8);
        issueList.setStyle("-fx-padding: 5 0 0 0;");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        if (issues.isEmpty()) {
            Label empty = new Label("Không có báo cáo nào cho phiên này.");
            empty.setStyle("-fx-text-fill: #9CA3AF; -fx-font-style: italic;");
            issueList.getChildren().add(empty);
        } else {
            for (IssueRecord r : issues) {
                VBox card = new VBox(4);
                card.setStyle("-fx-background-color: white; -fx-border-color: #FCA5A5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 14 10 14; -fx-border-width: 1.5;");
                HBox header = new HBox(10);
                header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Label lblBadge = new Label("⚠");
                lblBadge.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 14px;");
                Label lblType = new Label(r.getIssueType() != null ? r.getIssueType() : "Sự cố");
                lblType.setStyle("-fx-font-weight: bold; -fx-text-fill: #DC2626; -fx-font-size: 13px;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                Label lblMeta = new Label("User #" + r.getUserId() + (r.getCreatedAt() != null ? " · " + r.getCreatedAt().format(fmt) : ""));
                lblMeta.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 11px;");
                header.getChildren().addAll(lblBadge, lblType, spacer, lblMeta);
                Label lblDesc = new Label(r.getDescription() != null ? r.getDescription() : "(Không có mô tả)");
                lblDesc.setWrapText(true);
                lblDesc.setStyle("-fx-text-fill: #374151; -fx-font-size: 12px;");
                card.getChildren().addAll(header, lblDesc);
                issueList.getChildren().add(card);
            }
        }
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(issueList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #FAFAFA; -fx-background-color: #FAFAFA;");
        scrollPane.setPrefHeight(400);
        Button btnClose = new Button("Đóng");
        btnClose.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 20 6 20;");
        btnClose.setOnAction(e -> stage.close());
        HBox footer = new HBox();
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.getChildren().add(btnClose);
        root.getChildren().addAll(title, subtitle, new javafx.scene.control.Separator(), scrollPane, footer);
        stage.setScene(new Scene(root, 580, 520));
        stage.setResizable(true);
        stage.show();
    }

    @FXML public void goToHomePage(ActionEvent event) { switchPage(event, "/view/view/admin/The_Home_Page_Admin_View.fxml"); }
    @FXML public void goToTransactionPage(ActionEvent event) { switchPage(event, "/view/view/admin/The_Transaction_Page_Admin_View.fxml"); }
    @FXML public void goToSettingsPage(ActionEvent event) { switchPage(event, "/view/view/admin/The_Settings_Page_Admin_View.fxml"); }

    private void switchPage(ActionEvent event, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof The_Transaction_Page_Admin_View_Controller c) c.setUserData(currentUser);
            else if (ctrl instanceof The_Home_Page_Admin_View_Controller c)  c.setUserData(currentUser);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setStatus(String msg) { Platform.runLater(() -> { if (lblStatusBar != null) lblStatusBar.setText(msg); }); }
    private void showAlert(Alert.AlertType type, String title, String content) { Alert alert = new Alert(type); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content); alert.showAndWait(); }
    public void Welcome_back(ActionEvent actionEvent) { }

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
}