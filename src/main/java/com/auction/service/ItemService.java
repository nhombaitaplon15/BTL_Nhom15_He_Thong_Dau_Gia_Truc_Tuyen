package com.auction.service;
import com.auction.common.model.Items;
import com.auction.common.model.Auction;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ItemService {
private Map<Integer, Items> itemsList = new HashMap<>();                 // list sản phẩm có id và những thứ chứ trong Items
    public Items findItem(int id) {                                         // hàm tìm kiếm sản phẩm trong list bằng ID sản phẩm
        return itemsList.get(id);
    }
    public boolean addItem(Items item) throws Exception {                   // hàm thêm sản phẩm
        try {
            if (item == null) {                                             // nếu không có sản phẩm
                throw new Exception("Sản phẩm không được để trống !");
            }
            if (item.getName() == null) {                                   // sản phẩm không có tên throw
                throw new Exception("Tên sản phẩm không được để trống!");
            }
            if (itemsList.containsKey(item.getId())) {                      // ID sản phẩm có thì không thể thêm thành sản phẩm mới được
                throw new Exception("ID sản phẩm đã tồn tại !");
            }
            itemsList.put(item.getId(), item);                              // nếu không lỗi thì cho sản phẩm vào trong danh sách
            System.out.println(" Đã thêm sản phẩm: " + item.getName());
            return true;
        } catch(Exception e) {                                              // sai thì bắt ngoại lệ để in lỗi
            System.out.println("[Lỗi] :" + e.getMessage());
            throw e;
        }
    }
    public boolean updateItem(Items item) throws Exception {                 //hàm cập nhật sản phẩm
        try {
            if (item == null) {
                throw new Exception(" Sản phẩm không được để trống!");
            }
            if (!itemsList.containsKey(item.getId())) {                      // ID sản phẩm mà không có trong itemList thì không update được
                throw new Exception("ID sản phẩm không tồn tại!");
            }
            Items oldItem = itemsList.get(item.getId());                     // đây là sản phẩm cần được update
            oldItem.setName(item.getName());                                 // update name
            oldItem.setProducer(item.getProducer());
            oldItem.setStartPrice(item.getStartPrice());
            oldItem.setDescription(item.getDescription());
            oldItem.setImgItem(item.getImgItem());
            System.out.println("Cập nhật sản phẩm thành công");
            return true;
        } catch (Exception e) {
            System.out.println("[Lỗi] :" + e.getMessage());
            throw e;
        }
    }
    public boolean deleteItem(int itemId) throws Exception{                  // hàm xóa sản phẩm
        try {
            if (!itemsList.containsKey(itemId)) {                            // nếu trong ItemList mà không có ID sản phẩm thì không xóa sp
                throw new Exception("ID sản phẩm không tồn tại để xóa!");
            }
            itemsList.remove(itemId);
            System.out.println("Xóa sản phẩm thành công");
            return true;
        } catch (Exception e) {
            System.out.println("[Lỗi] :" + e.getMessage());
            throw e;
        }
    }
}