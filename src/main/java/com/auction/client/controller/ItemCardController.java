package com.auction.client.controller;

import com.auction.common.model.Item;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ItemCardController {

    @FXML private ImageView imgItem;
    @FXML private Label name;
    @FXML private Label startPrice;

    private Item itemData;
    private int auctionId;

    @FXML
    public void initialize() {
        // Hàm khởi tạo mặc định của JavaFX khi nạp FXML
    }

    /**
     * Hàm đổ dữ liệu thực tế từ Database vào các thành phần trên giao diện Card
     * @param item Đối tượng Vật phẩm lấy từ bảng items
     * @param currentPrice Giá hiện tại (hoặc giá khởi điểm) lấy từ bảng auctions
     * @param auctionId Mã phiên đấu giá thực tế
     */
    public void setData(Item item, double currentPrice, int auctionId) {
        if (item == null) return;

        this.itemData = item;
        this.auctionId = auctionId;

        // 1. Đổ tên sản phẩm từ DB
        if (name != null) {
            name.setText("Tên sản phẩm: " + (item.getName() != null ? item.getName() : "Chưa cập nhật"));
        }

        // 2. Đổ giá tiền tệ thực tế từ DB (Giữ nguyên đơn vị UETệ của bạn)
        if (startPrice != null) {
            startPrice.setText("Giá hiện tại: " + String.format("%,.0f", currentPrice) + " UETệ");
        }

        // 3. Tải ảnh thực tế dựa trên đường dẫn lưu trong DB (Ví dụ: /images/sh.png)
        if (imgItem != null && item.getImgItem() != null && !item.getImgItem().isEmpty()) {
            try {
                String path = item.getImgItem();
                // Tự động chuẩn hóa dấu gạch chéo đường dẫn tài nguyên hệ thống
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                Image image = new Image(getClass().getResourceAsStream(path));
                imgItem.setImage(image);
            } catch (Exception e) {
                System.out.println("⚠️ Không tìm thấy ảnh tại: " + item.getImgItem() + " cho vật phẩm: " + item.getName());
            }
        }
    }

    public Item getItemData() { return itemData; }
    public int getAuctionId() { return auctionId; }
}