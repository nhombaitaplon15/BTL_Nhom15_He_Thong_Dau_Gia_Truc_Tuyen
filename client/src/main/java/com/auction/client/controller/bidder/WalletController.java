package com.auction.client.controller.bidder;
import com.auction.common.model.User;
import com.auction.server.service.TransactionService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.text.DecimalFormat;

public class WalletController {

    private final TransactionService transactionService = new TransactionService(null);
    private User currentUser;

    // 🌟 ĐÃ ĐỒNG BỘ CHÍNH XÁC ID THEO FILE FXML CỦA EM
    @FXML private Button btnTabDeposit;   // Khớp hoàn toàn với fx:id="btnTabDeposit"
    @FXML private Button btnTabWithdraw;  // Khớp hoàn toàn với fx:id="btnTabWithdraw"
    @FXML private Button btnSubmit;       // Khớp hoàn toàn với fx:id="btnSubmit"
    @FXML private Label lblLargeBalance;  // Khớp hoàn toàn với fx:id="lblLargeBalance"

    @FXML private TextField txtAmount;
    @FXML private TextField txtNote;

    // Mã màu CSS chuẩn UI của em: Xanh đậm (#2563EB) và Xám nhạt (#F1F5F9)
    private final String BLUE_STYLE = "-fx-background-color: #2563EB; -fx-text-fill: WHITE; -fx-background-radius: 8 0 0 8; -fx-cursor: hand; -fx-font-weight: bold;";
    private final String GRAY_STYLE = "-fx-background-color: #F1F5F9; -fx-text-fill: #64748B; -fx-background-radius: 0 8 8 0; -fx-cursor: hand; -fx-font-weight: bold;";

    @FXML
    public void initialize() {
        // Vừa mở màn hình lên, ép giao diện sáng nút Nạp Tiền trước
        highlightDepositTab();
        System.out.println(">>> [OK] Màn hình Giao dịch tài chính đã khởi tạo thành công!");
    }

    // ĐỒNG BỘ SỐ DƯ TỪ HỆ THỐNG KHI CHUYỂN MÀN HÌNH
    public void setUserData(User user) {
        if (user == null) {
            System.out.println(">>> [LỖI] Dữ liệu User truyền sang bị NULL!");
            return;
        }
        this.currentUser = user;

        // Đổ số dư thực tế vào nhãn lớn (Ví dụ: 50,000,000 đ)
        if (lblLargeBalance != null) {
            DecimalFormat formatter = new DecimalFormat("#,###");
            lblLargeBalance.setText(formatter.format(currentUser.getBalance()) + " đ");
            System.out.println(">>> Đã nạp số dư hệ thống: " + currentUser.getBalance());
        }
    }

    // KHI BẤM NÚT NẠP TIỀN
    @FXML
    void handleSwitchToDeposit(ActionEvent event) {
        System.out.println(">>> Clicked: Tab Nạp Tiền");
        highlightDepositTab();
    }

    private void highlightDepositTab() {
        if (btnTabDeposit != null && btnTabWithdraw != null && btnSubmit != null) {
            // Sửa lại bán kính bo góc (radius) cho đúng thiết kế ban đầu của em
            btnTabDeposit.setStyle("-fx-background-color: #2563EB; -fx-text-fill: WHITE; -fx-background-radius: 8 0 0 8;");
            btnTabWithdraw.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #64748B; -fx-background-radius: 0 8 8 0;");
            btnSubmit.setText("Xác Nhận Nạp Tiền");
        }
    }

    // KHI BẤM NÚT RÚT TIỀN
    @FXML
    void handleSwitchToWithdraw(ActionEvent event) {
        System.out.println(">>> Clicked: Tab Rút Tiền");
        if (btnTabDeposit != null && btnTabWithdraw != null && btnSubmit != null) {
            // Đảo ngược màu sắc: Rút biến thành xanh, Nạp thành xám
            btnTabDeposit.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #64748B; -fx-background-radius: 8 0 0 8;");
            btnTabWithdraw.setStyle("-fx-background-color: #2563EB; -fx-text-fill: WHITE; -fx-background-radius: 0 8 8 0;");
            btnSubmit.setText("Xác Nhận Rút Tiền");
        }
    }

    // XỬ LÝ KHI NHẤN NÚT XÁC NHẬN TO Ở DƯỚI CÙNG
    @FXML
    void handleSubmitTransaction(ActionEvent event) {
        String amountStr = txtAmount.getText().trim();

        if (amountStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi nhập liệu", "Vui lòng nhập số tiền!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);

            if (currentUser == null) {
                showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không tìm thấy phiên đăng nhập!");
                return;
            }

            String actionTypeText = btnSubmit.getText();

            if (actionTypeText.contains("Nạp")) {
                transactionService.handleDepositRequest(currentUser, amount);
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Yêu cầu NẠP TIỀN đã được gửi, chờ Admin duyệt!");
                txtAmount.clear();
                if (txtNote != null) txtNote.clear();
            }
            else if (actionTypeText.contains("Rút")) {
                // Kiểm tra số dư
                if (currentUser.getBalance() < amount) {

                    showAlert(
                            Alert.AlertType.ERROR,
                            "Giao dịch thất bại",
                            "Số dư tài khoản không đủ để thực hiện yêu cầu rút tiền này!"
                    );

                    return;
                }
                // Lấy thông tin ngân hàng từ ô ghi chú
                String bankInfo = txtNote.getText().trim();
                if (bankInfo.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập thông tin ngân hàng để rút tiền!");
                    return;
                }
                transactionService.handleWithdrawRequest(currentUser, amount, bankInfo);
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Yêu cầu RÚT TIỀN đã được gửi, chờ Admin duyệt!");
                txtAmount.clear();
                if (txtNote != null) {txtNote.clear();}
            }

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Số tiền nhập vào phải là số hợp lệ!");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Thất bại", e.getMessage());
        }
    }

    // XỬ LÝ QUAY LẠI TRANG CHỦ
    @FXML
    void handleBackToHome(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/The_Home_Page_Bidder_View.fxml"));
            Parent root = loader.load();

            Object controller = loader.getController();
            if (controller != null) {
                try {
                    controller.getClass().getMethod("setUserData", User.class).invoke(controller, this.currentUser);
                } catch (Exception ignored) {}
            }

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 720));
            stage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi chuyển trang", "Không thể quay lại trang chủ: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}