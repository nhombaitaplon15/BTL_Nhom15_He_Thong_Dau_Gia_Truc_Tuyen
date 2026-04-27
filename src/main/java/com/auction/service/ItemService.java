package com.auction.service;
import com.auction.common.model.Items;
import com.auction.common.model.Auction;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ItemService {
    private Map<Integer, Items> itemsList = new HashMap<>();                 // list sản phẩm có id và những thứ chứ trong Items
    public void addItem (Items item){                                        //hàm thêm sản phẩm
        if(item == null){                                                    // phải tạo đối tượng cụ thể rồi addItem
            System.out.println("Item không hợp lệ !");
            return;
        }
        itemsList.put(item.getId(), item);                                  // thêm id và sp vào danh sách
        System.out.println("Đã thêm sản phẩm: " + item.getName());
    }
    public void updateItem(int id,String producer, String show,String name, String imgItem) { // hàm update sản phẩm
        Items item = itemsList.get(id);                                                                  // duyệt id để update, sản phẩm mà không tồn tại thì không update được
        if (item == null){
            System.out.println("Item không hợp lệ !");
            return;
        }
        item.setName(name);
        item.setProducer(producer);
        item.setShow(show);
        item.setimgItem(imgItem);
        System.out.println("Đã cập nhật sản phẩm!");
    }
    public void deleteItem(int id){                                          //xóa sản phẩm cũng duyệt id và xóa id
        itemsList.remove(id);
        System.out.println(" Đã xóa ID sản phẩm: "+ id);
    }
    public void setStartingPrice(int id, int price){                        // hàm set giá này thì set giá có ở class Items nên chỉ cần gọi hàm ở class Items
        Items item = itemsList.get(id);
        if (item == null){
            System.out.println("Không tìm thấy sản phẩm !");
            return;
        }
        item.setStartPrice(price);
        item.setCurrentPrice(price);
        System.out.println("Đã thiết lập giá khởi điểm là: "+ price);
    }
    public void setDescription(int id,String show){                       // hàm set mô tả cũng như hàm set giá
        Items item = itemsList.get(id);
        if ( item ==null){
            System.out.println("Không tìm thấy sản phẩm!");
            return;
        }
        item.setShow(show);
        System.out.println("Đã cập nhật mô tả sản phẩm!" + show);
    }
}
