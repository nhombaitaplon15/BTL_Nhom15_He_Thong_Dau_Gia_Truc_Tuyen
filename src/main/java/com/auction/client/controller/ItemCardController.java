package com.auction.client.controller;

import com.auction.common.model.Items;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javafx.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

public class ItemCardController {
    @FXML private ImageView imgItem;
    @FXML private Label name;
    @FXML private Label startPrice;
    @FXML private Label timeRemaining;
    @FXML private Button btnBidNow;
    private static Map<Integer, Items> itemsList = new HashMap<>();
    public void setData(Items item) {
        this.currentItem = item; // Quan trọng để nút bấm biết dùng item nào
        name.setText("Tên sản phẩm: " + item.getName());
        startPrice.setText("Giá khởi điểm: " + item.getStartPrice() + " UETệ");
        try {
            Image image = new Image(getClass().getResourceAsStream("/"+  item.getImgItem()));
            imgItem.setImage(image);
        } catch (Exception e) {
            System.out.println("Không tìm thấy ảnh cho: " + item.getName());
        }
    }
    private Items currentItem; // Biến để lưu sản phẩm hiện tại của thẻ này
    @FXML
    void handleBidNow(ActionEvent event) {
        if (currentItem != null) {
            System.out.println("Đang thực hiện đấu giá cho: " + currentItem.getName());
            // Tại đây bạn có thể mở màn hình chi tiết hoặc hiện popup nhập giá tiền
        }
    }

}
