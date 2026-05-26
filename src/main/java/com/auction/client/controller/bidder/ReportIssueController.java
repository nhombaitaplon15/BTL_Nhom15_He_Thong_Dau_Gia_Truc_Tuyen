package com.auction.client.controller.bidder;

import com.auction.common.model.BidHistoryRow;
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
    }

    public void setIssueData(BidHistoryRow data) {
        if (data != null) {
            txtSessionId.setText(String.valueOf(data.getAuctionId()));
            txtSessionId.setEditable(false);
        }
    }

    @FXML
    private void handleCancel() {
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

        System.out.println(">>> Đã gửi báo cáo lỗi phiên " + txtSessionId.getText() + " sang DB Admin.");

        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
        successAlert.setTitle("Thành công");
        successAlert.setHeaderText(null);
        successAlert.setContentText("Báo cáo sự cố đã được gửi thành công!");
        successAlert.showAndWait();

        handleCancel();
    }
}