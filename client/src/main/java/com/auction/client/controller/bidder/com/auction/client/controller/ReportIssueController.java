package com.auction.client.controller;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.server.dao.IssueDAO; // Nhớ import đúng vị trí file DAO vừa tạo
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
    private IssueDAO issueDAO = new IssueDAO(); // Khởi tạo đối tượng DAO kết nối DB

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

    /**
     * Nhận đồng thời dữ liệu dòng chọn và thông tin User hiện tại từ màn hình chính chuyển sang
     */
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

        // Tạo luồng phụ xử lý DB nhằm tăng trải nghiệm người dùng
        new Thread(() -> {
            int userId = (currentUser != null) ? currentUser.getId() : 0;
            int auctionId = (currentData != null) ? currentData.getAuctionId() : Integer.parseInt(txtSessionId.getText());

            // Thực hiện ghi nhận xuống DB
            boolean isSuccess = issueDAO.insertIssue(userId, auctionId, issueType, description);

            // Trở lại luồng UI chính JavaFX để tương tác thông báo
            Platform.runLater(() -> {
                if (isSuccess) {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Thành công");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("Báo cáo sự cố đã được gửi và lưu trữ thành công vào Database!");
                    successAlert.showAndWait();
                    handleCancel(); // Tự động đóng cửa sổ sau khi gửi thành công
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Thất bại");
                    errorAlert.setContentText("Lỗi hệ thống! Không thể kết nối Database để lưu báo cáo.");
                    errorAlert.showAndWait();
                }
            });
        }).start();
    }
}