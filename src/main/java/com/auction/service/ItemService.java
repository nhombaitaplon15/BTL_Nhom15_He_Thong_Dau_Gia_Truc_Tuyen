package com.auction.service;

import com.auction.common.model.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemService {
    // Sử dụng HashMap để lưu tạm (Khi nào làm xong SQL sẽ thay thế bằng DAO)
    private Map<Integer, Items> itemsList = new HashMap<>();
     //Thêm sản phẩm mới
     //Trả về boolean để (Networking) báo kết quả về Client
     //Ném Exception để (Unit Test) kiểm tra các trường hợp lỗi
    public boolean addItem(Items item) {
        try {
            if (item == null) {
                throw new Exception("Đối tượng sản phẩm không hợp lệ!");
            }
            if (item.getName() == null || item.getName().trim().isEmpty()) {
                throw new Exception("Tên sản phẩm không được để trống!");
            }
            if (item.getStartPrice() < 0) {
                throw new Exception("Giá khởi điểm không được là số âm!");
            }
            if (itemsList.containsKey(item.getId())) {
                throw new Exception("ID sản phẩm " + item.getId() + " đã tồn tại!");
            }

            itemsList.put(item.getId(), item);
            System.out.println("[SERVER] Thêm thành công: " + item.getName());
            return true;

        } catch (Exception e) {
            // Log lỗi ra server để debug
            System.err.println("[ERROR addItem] " + e.getMessage());
            return false;
        }
    }

    // Cập nhật thông tin sản phẩm
    public boolean updateItem(int id, String producer, String description, String name, String imgItem) {
        try {
            if (!itemsList.containsKey(id)) {
                throw new Exception("Không tìm thấy sản phẩm ID: " + id + " để cập nhật!");
            }

            Items item = itemsList.get(id);
            item.setName(name);
            item.setProducer(producer);
            item.setDescription(description);
            item.setImgItem(imgItem);

            System.out.println("[SERVER] Cập nhật thành công sản phẩm ID: " + id);
            return true;

        } catch (Exception e) {
            System.err.println("[ERROR updateItem] " + e.getMessage());
            return false;
        }
    }

    // xóa sản phẩm
    public boolean deleteItem(int id) {
        try {
            if (!itemsList.containsKey(id)) {
                throw new Exception("Không thể xóa! ID: " + id + " không tồn tại.");
            }
            itemsList.remove(id);
            System.out.println("[SERVER] Đã xóa sản phẩm ID: " + id);
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR deleteItem] " + e.getMessage());
            return false;
        }
    }
    // lấy toàn bộ sản phẩm cho Diệp sử dụng
    public List<Items> getAllItems() {
        return new ArrayList<>(itemsList.values());
    }

    // tìm sản phẩm theo id
    public Items getItemById(int id) {
        return itemsList.get(id);
    }
}
