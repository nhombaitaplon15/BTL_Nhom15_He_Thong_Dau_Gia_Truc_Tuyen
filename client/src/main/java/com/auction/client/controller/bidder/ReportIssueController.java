package com.auction.client.controller.bidder;

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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * ReportIssueController — ĐÃ SỬA CÁC LỖI:
 *
 * BUG 1 (NGHIÊM TRỌNG): Gửi String thô thay vì ReportIssueDTO
 *   → Server cast sang ReportIssueDTO sẽ throw ClassCastException
 *   → FIX: Tạo ReportIssueDTO đúng format trước khi gửi
 *
 * BUG 2: Lắng nghe REPORT_SENT (không tồn tại) thay vì REPORT_ISSUE_SUCCESS
 *   → ResponseCode.REPORT_SENT không có trong enum → không bao giờ nhận được response
 *   → FIX: Đổi sang ResponseCode.REPORT_ISSUE_SUCCESS / REPORT_ISSUE_FAILED
 *
 * BUG 3: setIssueData() không được gọi từ BiddingHistoryController
 *   → txtSessionId luôn trống → parseInt crash
 *   → FIX: Thêm null-check và fallback an toàn, đồng thời
 *          BiddingHistoryController cần gọi setIssueData() (xem BiddingHistoryController đã sửa)
 */
public class ReportIssueController {

    @FXML private TextField txtSessionId;
    @FXML private ComboBox<String> cbIssueType;
    @FXML private TextArea txtDescription;
    @FXML private Button btnSend;

    private BidHistoryRow currentData;
    private User currentUser;

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

        // FIX BUG 2: Dùng đúng ResponseCode.REPORT_ISSUE_SUCCESS / REPORT_ISSUE_FAILED
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_ISSUE_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_ISSUE_FAILED);
        MessageRouter.getInstance().register(ResponseCode.REPORT_ISSUE_SUCCESS, this::handleReportSuccess);
        MessageRouter.getInstance().register(ResponseCode.REPORT_ISSUE_FAILED,  this::handleReportFailure);
    }

    /**
     * Được BiddingHistoryController gọi để truyền dữ liệu phiên + user vào controller này.
     * Phải gọi TRƯỚC khi stage.show() hoặc stage.showAndWait().
     */
    public void setIssueData(BidHistoryRow data, User user) {
        this.currentData = data;
        this.currentUser = user;

        if (data != null && txtSessionId != null) {
            txtSessionId.setText(String.valueOf(data.getAuctionId()));
            txtSessionId.setEditable(false);
        }
    }

    @FXML
    private void handleCancel() {
        cleanupListeners();
        closeWindow();
    }

    @FXML
    private void handleSendReport() {
        // Validate mô tả
        String description = txtDescription != null ? txtDescription.getText().trim() : "";
        if (description.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập mô tả chi tiết sự cố!");
            return;
        }

        // Lấy auctionId an toàn — ưu tiên từ currentData, fallback sang txtSessionId
        int auctionId;
        try {
            if (currentData != null) {
                auctionId = currentData.getAuctionId();
            } else if (txtSessionId != null && !txtSessionId.getText().trim().isEmpty()) {
                auctionId = Integer.parseInt(txtSessionId.getText().trim());
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không xác định được mã phiên đấu giá!");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Mã phiên không hợp lệ!");
            return;
        }

        String issueType = (cbIssueType != null && cbIssueType.getSelectionModel().getSelectedItem() != null)
                ? cbIssueType.getSelectionModel().getSelectedItem()
                : "Sự cố khác";

        int userId = (currentUser != null) ? currentUser.getId() : 0;

        // FIX BUG 1: Tạo đúng ReportIssueDTO thay vì gửi String thô
        ReportIssueDTO dto = new ReportIssueDTO(userId, auctionId, issueType, description);

        // Vô hiệu nút gửi để tránh gửi trùng
        if (btnSend != null) btnSend.setDisable(true);

        SocketClient.getInstance().sendRequest(RequestCode.REPORT_ISSUE, dto);
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────────

    private void handleReportSuccess(Message message) {
        Platform.runLater(() -> {
            showAlert(Alert.AlertType.INFORMATION, "Thành công",
                    "Báo cáo sự cố đã được hệ thống ghi nhận và chuyển tới ban quản trị!");
            cleanupListeners();
            closeWindow();
        });
    }

    private void handleReportFailure(Message message) {
        Platform.runLater(() -> {
            String reason = "Gửi báo cáo thất bại!";
            if (message != null) {
                if (message.getPayload() instanceof String s) reason = s;
                else if (message.getMessage() != null) reason = message.getMessage();
            }
            showAlert(Alert.AlertType.ERROR, "Thất bại", reason);
            // Mở lại nút gửi để user có thể thử lại
            if (btnSend != null) btnSend.setDisable(false);
        });
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private void cleanupListeners() {
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_ISSUE_SUCCESS);
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_ISSUE_FAILED);
    }

    private void closeWindow() {
        try {
            if (txtSessionId != null && txtSessionId.getScene() != null) {
                Stage stage = (Stage) txtSessionId.getScene().getWindow();
                if (stage != null) stage.close();
            }
        } catch (Exception e) {
            System.err.println("[ReportIssueController] Lỗi đóng cửa sổ: " + e.getMessage());
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
