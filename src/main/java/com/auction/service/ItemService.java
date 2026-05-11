package com.auction.service;

import com.auction.common.model.Item;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.ItemDAO;
import java.util.List;

public class ItemService {
    private final ItemDAO itemDAO = new ItemDAO();

    /**
     * Lấy danh sách hàng hóa - Trả về List các đối tượng con thực tế (Vehicle, Art...)
     */
    public List<Item> getAllItems() {
        return itemDAO.getAllItems();
    }

    /**
     * Tìm hàng theo ID
     */
    public Item getItemById(int id) {
        Item item = itemDAO.getItemById(id);
        if (item == null) {
            throw new AuctionException(ErrorCode.ITEM_NOT_FOUND.name(), "Sản phẩm không tồn tại!");
        }
        return item;
    }

    /**
     * Thêm hàng mới (Dùng cho cả Admin và Seller)
     */
    public void addItem(Item item) {
        // Kiểm tra dữ liệu đầu vào
        validateItem(item);

        // Lưu vào DB
        boolean success = itemDAO.insertItem(item);
        if (!success) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể lưu sản phẩm vào Database!");
        }
        System.out.println("[SERVICE] Đã thêm sản phẩm thành công: " + item.getName());
    }

    /**
     * Xóa hàng
     */
    public void deleteItem(int id) {
        // Check xem có tồn tại không trước khi xóa
        getItemById(id);
        if (!itemDAO.deleteItem(id)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Xóa thất bại (có thể hàng đang được đấu giá)!");
        }
    }

    private void validateItem(Item item) {
        if (item == null) throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Item trống!");
        if (item.getName() == null || item.getName().isEmpty()) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Tên sản phẩm không hợp lệ!");
        }
        if (item.getStartingPrice() < 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Giá khởi điểm không được âm!");
        }
    }
}