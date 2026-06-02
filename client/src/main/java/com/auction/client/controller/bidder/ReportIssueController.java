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

    // ĐÃ FIX: Sử dụng đúng kiểu BidHistoryRow để đồng bộ với BiddingHistoryController
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

        // Đăng ký nhận kết quả từ Server qua cấu trúc Realtime
        MessageRouter.getInstance().register(ResponseCode.REPORT_SENT, this::handleReportSuccess);
    }

    // ĐÃ FIX: Đổi tham số truyền vào thành BidHistoryRow
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
        MessageRouter.getInstance().unregister(ResponseCode.REPORT_SENT);
        Stage stage = (Stage) txtSessionId.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleSendReport() {
        String issueType = cbIssueType.getSelectionModel().getSelectedItem();
        String description = txtDescription.getText().trim();

        if (description.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Vui lòng nhập mô tả chi tiết sự cố gặp phải!");
            alert.showAndWait();
            return;
        }

        int auctionId = (currentData != null) ? currentData.getAuctionId() : Integer.parseInt(txtSessionId.getText());

        // Đóng gói mảng dữ liệu bắn lên Server
        Object[] reportPayload = new Object[]{auctionId, issueType, description};
        SocketClient.getInstance().sendRequest(RequestCode.REPORT_ISSUE, reportPayload);
    }

    private void handleReportSuccess(Message msg) {
        Platform.runLater(() -> {
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Thành công");
            successAlert.setHeaderText(null);
            successAlert.setContentText("Báo cáo sự cố đã được gửi và lưu trữ thành công!");
            successAlert.showAndWait();
            handleCancel();
        });
    }
}