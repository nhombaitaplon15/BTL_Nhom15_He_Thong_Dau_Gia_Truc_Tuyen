package com.auction.service;

import com.auction.common.model.Art;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemService {

    private static Map<Integer, Item> itemsList = new HashMap<>();

    static {
        Item vehicle = new Vehicle(484, "Bugatti", 484000000,
            "Bugatti La Voiture Noire được trang bị động cơ W16 tăng áp kép," +
                " dung tích 8.0 lít, mô-men xoắn cực đại 1.599Nm. " +
                "Siêu xe này mất 2,4 giây để để tăng tốc từ 0-96,6 km/h.", " Bugatti La Voiture Noire", "view/images/Bugatti_La_Voiture_Noire.png") ;
        Item art = new Art(9,"Van Gogh",1000,"Bức tranh khắc họa quang cảnh bên ngoài phòng bệnh của Van Gogh " +
            "ở một bệnh viện tâm thần nằm tại miền Nam nước Pháp.","Bức Đêm đầy sao", "view/images/Tranh_Van_Gogh.png", 1889 , true );

        Item electronics = new Electronics(25,"Đồng hồ Romain Jerome Super Mario Bros","Romain Jerome",18950,"Chiếc đồng hồ có đường kính 46mm và được làm bằng chất liệu titan màu đen." +
            " Bên trong là bộ máy cơ tự động RJ001-A hoạt động ở xung nhịp 4Hz, có thể trữ năng lượng trong 42 giờ. " +
            "Trên bề mặt đồng hồ là một tấm nền 3 lớp mô phỏng các hình ảnh đặc trưng như anh chàng Mario, cây nấm, " +
            "đám mây hay bụi cây được tráng sứ.", "view/images/Đồng_hồ_Romain_Jerome_Super_Mario_Bros.png","Romain Jermo", "12", LocalDate.of(1980, 12, 12), 36 ) ;
        itemsList.put(484,vehicle) ;
        itemsList.put(9,art) ;
        itemsList.put(25,electronics) ;


    }

    // lấy tất cả các mặt hàng
    public List<Item> getAllItems() {
        return new ArrayList<>(itemsList.values());
    }

    // lấy hàng ra theo id
    public Item getItemById(int id) {

        Item item = itemsList.get(id);

        if (item == null) {
            throw new AuctionException(
                    ErrorCode.ITEM_NOT_FOUND.name(),
                    "Sản phẩm không tồn tại"
            );
        }

        return item;
    }

    // tìm hàng
    public Item findItem(int id) {
        return getItemById(id);
    }

    // thêm mặt hàng
    public void addItem(Item item) {

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

    // cập nhật thông tin cho mặt hàng
    public void updateItem(int id, String producer, String description, String name, String imgItem) {

        Item item = itemsList.get(id);

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

    // xóa mặt hàng
    public void deleteItem(int id) {

        Item item = itemsList.get(id);

        if (item == null) {
            throw new AuctionException(
                    ErrorCode.ITEM_NOT_FOUND.name(),
                    "Sản phẩm không tồn tại"
            );
        }

        itemsList.remove(id);

        System.out.println("[ITEM] Đã xóa sản phẩm ID: " + id);
    }
    public void clearData() {
        itemsList.clear();
    }

}