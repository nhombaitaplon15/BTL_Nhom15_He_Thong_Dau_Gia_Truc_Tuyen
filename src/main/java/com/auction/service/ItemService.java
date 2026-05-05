package com.auction.service;

import com.auction.common.model.Items;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemService {

    private final Map<Integer, Items> itemsList = new HashMap<>();

    // =========================
    // ADD ITEM
    // =========================
    public void addItem(Items item) {

        if (item == null) {
            throw new AuctionException(
                    ErrorCode.INVALID_ITEM.name(),
                    "Item không được null"
            );
        }

        if (item.getName() == null || item.getName().trim().isEmpty()) {
            throw new AuctionException(
                    ErrorCode.INVALID_ITEM.name(),
                    "Tên sản phẩm không được để trống"
            );
        }

        if (item.getStartPrice() < 0) {
            throw new AuctionException(
                    ErrorCode.INVALID_ITEM.name(),
                    "Giá khởi điểm không được âm"
            );
        }

        if (itemsList.containsKey(item.getId())) {
            throw new AuctionException(
                    ErrorCode.ITEM_DUPLICATE.name(),
                    "Sản phẩm không được trùng nhau"
            );

        }

        itemsList.put(item.getId(), item);

        System.out.println("[ITEM] Thêm thành công: " + item.getName());
    }

    // =========================
    // UPDATE ITEM
    // =========================
    public void updateItem(int id, String producer, String description, String name, String imgItem) {

        Items item = itemsList.get(id);

        if (item == null) {
            throw new AuctionException(
                    ErrorCode.ITEM_NOT_FOUND.name(),
                    "Sản phẩm không tồn tại"
            );
        }

        item.setName(name);
        item.setProducer(producer);
        item.setDescription(description);
        item.setImgItem(imgItem);

        System.out.println("[ITEM] Cập nhật thành công ID: " + id);
    }

    // =========================
    // DELETE ITEM
    // =========================
    public void deleteItem(int id) {

        Items item = itemsList.get(id);

        if (item == null) {
            throw new AuctionException(
                    ErrorCode.ITEM_NOT_FOUND.name(),
                    "Sản phẩm không tồn tại"
            );
        }

        itemsList.remove(id);

        System.out.println("[ITEM] Đã xóa sản phẩm ID: " + id);
    }

    // =========================
    // GET ALL
    // =========================
    public List<Items> getAllItems() {
        return new ArrayList<>(itemsList.values());
    }

    // =========================
    // GET BY ID
    // =========================
    public Items getItemById(int id) {

        Items item = itemsList.get(id);

        if (item == null) {
            throw new AuctionException(
                    ErrorCode.ITEM_NOT_FOUND.name(),
                    "Sản phẩm không tồn tại"
            );
        }

        return item;
    }
    public Items findItem(int id) {
        return getItemById(id); // Gọi lại hàm đã có sẵn
    }
}