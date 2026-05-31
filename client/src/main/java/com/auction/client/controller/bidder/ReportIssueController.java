package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ReportIssueController {
    @FXML private TextField txtSessionId;
    @FXML private ComboBox<String> cbIssueType;
    @FXML private TextArea txtDescription;

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

        // Đăng ký nhận kết quả báo cáo sự cố (Khớp chuẩn ResponseCode của bạn)
        MessageRouter.getInstance().register(ResponseCode.REPORT_SENT, this::handleReportSuccess);
        MessageRouter.getInstance().register(ResponseCode.ERROR_MESSAGE, this::handleReportFailure);
    }

    public void setIssueData(BidHistoryRow data, User user) {
        if (data != null) {
            this.currentData = data;
            this.currentUser = user;
            txtSessionId.setText(String.valueOf(data.getAuctionId()));
            txtSessionId.setEditable(false);
        }
    }

    @FXML
    private void handleCancel() {
        cleanupListeners();
        Stage stage = (Stage) txtSessionId.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleSendReport() {
        String issueType = cbIssueType.getSelectionModel().getSelectedItem();
        String description = txtDescription.getText().trim();

        if (description.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Vui lòng nhập mô tả chi tiết sự cố!");
            alert.showAndWait();
            return;
        }

        int auctionId = (currentData != null) ? currentData.getAuctionId() : Integer.parseInt(txtSessionId.getText());

        // Định dạng chuỗi văn bản thuần túy gửi lên Server tương ứng với payload kiểu String của bạn
        String formattedMessage = String.format("[Phiên #%d] Loại lỗi: %s. Chi tiết: %s", auctionId, issueType, description);

        // Gửi thông tin (Khớp chuẩn RequestCode của bạn)
        SocketClient.getInstance().sendRequest(RequestCode.REPORT_ISSUE, formattedMessage);
    }

    private void handleReportSuccess(Message message) {
        Platform.runLater(() -> {
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Thành công");
            successAlert.setContentText("Báo cáo sự cố đã được hệ thống ghi nhận và chuyển tới ban quản trị!");
            successAlert.showAndWait();
            handleCancel();
        });
    }

    private void handleReportFailure(Message message) {
        Platform.runLater(() -> {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Thất bại");
            String reason = (message.getPayload() instanceof String) ? (String) message.getPayload() : "Gửi báo cáo thất bại!";
            errorAlert.setContentText(reason);
            errorAlert.showAndWait();
        });
    }

    private void cleanupListeners() {
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_SENT);
        MessageRouter.getInstance().unregister(ResponseCode.ERROR_MESSAGE);
    }
}