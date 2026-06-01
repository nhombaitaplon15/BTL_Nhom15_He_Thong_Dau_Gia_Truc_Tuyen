package com.auction.client.controller.bidder;

import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.ReportIssueDTO;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * Controller cho form Báo Cáo Sự Cố — phiên bản đã sửa.
 *
 * THAY ĐỔI SO VỚI BẢN CŨ:
 *   - KHÔNG còn gọi IssueDAO trực tiếp từ Client (không thể kết nối DB từ phía client).
 *   - Gửi ReportIssueDTO qua socket (RequestCode.REPORT_ISSUE) đến Server.
 *   - Server xử lý lưu DB và broadcast ADMIN_NEW_ISSUE đến Admin online.
 *   - Lắng nghe REPORT_ISSUE_SUCCESS / REPORT_ISSUE_FAILED để phản hồi UI.
 */
public class ReportIssueController {

    @FXML private TextField       txtSessionId;
    @FXML private ComboBox<String> cbIssueType;
    @FXML private TextArea         txtDescription;

    private BidHistoryRow currentData;
    private User           currentUser;

    // ===================== INITIALIZE =====================

    @FXML
    public void initialize() {
        if (cbIssueType != null) {
            cbIssueType.getItems().addAll(
                    "Lỗi trừ tiền tài khoản",
                    "Lỗi không nhận lệnh đặt giá (Bid)",
                    "Sản phẩm thực tế sai mô tả",
                    "Hệ thống bị giật lag mất kết nối",
                    "Sự cố khác"
            );
            cbIssueType.getSelectionModel().selectFirst();
        }
        // Đăng ký lắng nghe phản hồi từ server
        MessageRouter.getInstance().register(ResponseCode.REPORT_ISSUE_SUCCESS, this::onSuccess);
        MessageRouter.getInstance().register(ResponseCode.REPORT_ISSUE_FAILED,  this::onFailed);
    }

    // ===================== DATA SETTER =====================

    /**
     * Nhận dữ liệu từ màn hình cha (BiddingHistoryController).
     */
    public void setIssueData(BidHistoryRow data, User user) {
        if (data != null) {
            this.currentData = data;
            this.currentUser = user;
            txtSessionId.setText(String.valueOf(data.getAuctionId()));
            txtSessionId.setEditable(false);
        }
    }

    // ===================== ACTIONS =====================

    @FXML
    private void handleCancel() {
        unregisterHandlers();
        Stage stage = (Stage) txtSessionId.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleSendReport() {
        String issueType    = cbIssueType.getSelectionModel().getSelectedItem();
        String description  = txtDescription.getText() != null
                ? txtDescription.getText().trim() : "";

        if (description.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin",
                    "Vui lòng nhập mô tả chi tiết sự cố gặp phải!");
            return;
        }

        // Xác định userId và auctionId
        int userId    = (currentUser != null) ? currentUser.getId()
                : ClientSession.getInstance().getUserId();
        int auctionId;
        try {
            auctionId = (currentData != null)
                    ? currentData.getAuctionId()
                    : Integer.parseInt(txtSessionId.getText().trim());
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.WARNING, "Mã phiên không hợp lệ",
                    "Không xác định được mã phiên đấu giá.");
            return;
        }

        // Vô hiệu hoá nút để tránh gửi 2 lần
        disableSendButton(true);

        // Gửi qua socket — KHÔNG gọi DB trực tiếp
        ReportIssueDTO dto = new ReportIssueDTO(userId, auctionId, issueType, description);
        SocketClient.getInstance().sendRequest(RequestCode.REPORT_ISSUE, dto);
    }

    // ===================== RESPONSE HANDLERS =====================

    private void onSuccess(Message msg) {
        Platform.runLater(() -> {
            unregisterHandlers();
            showAlert(Alert.AlertType.INFORMATION, "Thành công",
                    "Báo cáo sự cố đã được gửi và lưu trữ thành công!");
            Stage stage = (Stage) txtSessionId.getScene().getWindow();
            stage.close();
        });
    }

    private void onFailed(Message msg) {
        Platform.runLater(() -> {
            disableSendButton(false);
            String reason = (msg.getMessage() != null) ? msg.getMessage() : "Lỗi không xác định.";
            showAlert(Alert.AlertType.ERROR, "Thất bại",
                    "Lỗi hệ thống! Không thể lưu báo cáo.\n" + reason);
        });
    }

    // ===================== HELPERS =====================

    private void unregisterHandlers() {
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_ISSUE_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_ISSUE_FAILED);
    }

    private void disableSendButton(boolean disabled) {
        // Tìm nút "Gửi Báo Cáo" qua scene graph (nút có text bắt đầu bằng "Gửi")
        if (txtSessionId.getScene() != null) {
            txtSessionId.getScene().getRoot().lookupAll(".button").forEach(node -> {
                if (node instanceof Button btn && btn.getText() != null
                        && btn.getText().startsWith("Gửi")) {
                    btn.setDisable(disabled);
                    btn.setText(disabled ? "Đang gửi..." : "Gửi Báo Cáo");
                }
            });
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
