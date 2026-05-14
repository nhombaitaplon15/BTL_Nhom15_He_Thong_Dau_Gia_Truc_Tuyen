package com.auction.client.controller;

import com.auction.common.model.Items;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Map;

public class ItemCardController {
    @FXML private ImageView imgItem;
    @FXML private Label name;
    @FXML private Label startPrice;
    private static Map<Integer, Items> itemsList = new HashMap<>();
    public void setData(Items item) {
        name.setText("Tên sản phẩm: " + item.getName());
        startPrice.setText("Giá khởi điểm: " + item.getStartPrice() + " UETệ");
        try {
            Image image = new Image(getClass().getResourceAsStream("/"+  item.getImgItem()));
            imgItem.setImage(image);
        } catch (Exception e) {
            System.out.println("Không tìm thấy ảnh cho: " + item.getName());
        }
    }
}
