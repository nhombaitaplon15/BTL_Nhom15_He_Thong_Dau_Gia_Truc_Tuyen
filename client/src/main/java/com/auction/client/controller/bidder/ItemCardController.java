package client.controller.bidder;

import com.auction.common.model.Item;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.util.Optional;

public class ItemCardController {
    @FXML private ImageView imgItem;
    @FXML private Label name;
    @FXML private Label startPrice;

    private Item currentItem; // Lưu lại item hiện tại của thẻ này

    public void setData(Item item) {
        if (item == null) return;
        this.currentItem = item;

        name.setText("Tên sản phẩm: " + item.getName());
        // Định dạng lại tiền tệ nhìn cho chuyên nghiệp hơn
        startPrice.setText(String.format("Giá khởi điểm: %,.0f UETệ", item.getStartingPrice()));

        try {
            if (item.getImgItem() != null && !item.getImgItem().isEmpty()) {
                Image image = new Image(getClass().getResourceAsStream("/" + item.getImgItem()));
                imgItem.setImage(image);
            }
        } catch (Exception e) {
            System.out.println("Không tìm thấy ảnh cho: " + item.getName());
        }
    }

    //  HÀM : Xử lý khi bấm nút "Đặt Giá Ngay"
    @FXML
    void handleBidAction(ActionEvent event) {
        if (currentItem == null) return;

        // 1. Tạo một Pop-up nhanh bắt người dùng nhập số tiền
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(" Đấu giá trực tuyến nhóm 15 - Đặt giá");
        dialog.setHeaderText("Sản phẩm: " + currentItem.getName());
        dialog.setContentText("Nhập số tiền bạn muốn trả (UETệ):");

        Optional<String> result = dialog.showAndWait();

        // 2. Xử lý khi người dùng nhấn OK
        result.ifPresent(amountStr -> {
            try {
                double bidAmount = Double.parseDouble(amountStr.trim());

                // Kiểm tra xem tiền đặt có lớn hơn giá khởi điểm không
                if (bidAmount <= currentItem.getStartingPrice()) {
                    showAlert(Alert.AlertType.WARNING, "Lỗi đặt giá",
                            "Giá bạn đặt phải lớn hơn giá khởi điểm (" + currentItem.getStartingPrice() + " UETệ)!");
                    return;
                }

                // 3. TODO: Gọi hàm PLACE_BID của bạn gửi lên cơ sở dữ liệu/Server ở đây
                // Ví dụ: biddingService.placeBid(currentUser, currentItem.getItemId(), bidAmount);

                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Bạn đã đặt giá thành công " + String.format("%,.0f", bidAmount) + " UETệ cho sản phẩm này!");

            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Vui lòng chỉ nhập số tiền hợp lệ!");
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}