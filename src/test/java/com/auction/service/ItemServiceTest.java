package com.auction.service;

import com.auction.common.model.Vehicle;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemServiceTest {

    private ItemService itemService;

    private Vehicle makeItem(int id, String name, int price) {
        return new Vehicle(id, "Producer A", price, "Mô tả", name, "img.jpg");
    }

    @BeforeEach
    void setUp() {
        itemService = new ItemService();
        itemService.clearData();
    }

    //thêm item

    // khi thêm item đúng thông tin
    @Test
    void addItem_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        assertEquals(1, itemService.getAllItems().size());
    }
    // khi item null
    @Test
    void addItem_nullItem_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(null));
        assertEquals(ErrorCode.INVALID_ITEM.name(), ex.getCode());
    }
    // khi tên item toàn khoảng trắng
    @Test
    void addItem_emptyName_shouldThrow() {
        Vehicle item = makeItem(1, "   ", 5000000);
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(item));
        assertEquals(ErrorCode.INVALID_ITEM.name(), ex.getCode());
    }
    // khi giá khởi điểm âm
    @Test
    void addItem_negativePrice_shouldThrow() {
        Vehicle item = makeItem(1, "Xe máy", -1);
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(item));
        assertEquals(ErrorCode.INVALID_ITEM.name(), ex.getCode());
    }

    // khi id trùng với item đã có
    @Test
    void addItem_duplicateId_shouldThrow() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(makeItem(1, "Xe máy Yamaha", 6000000)));
        assertEquals(ErrorCode.ITEM_DUPLICATE.name(), ex.getCode());
    }
    // khi giá = 0 không phải âm
    @Test
    void addItem_zeroPriceIsValid() {
        assertDoesNotThrow(() -> itemService.addItem(makeItem(1, "Quà tặng", 0)));
    }


    //phần getId
    // khi id tồn tại
    @Test
    void getItemById_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        assertNotNull(itemService.getItemById(1));
        assertEquals("Xe máy Honda", itemService.getItemById(1).getName());
    }
    // khi id ko tồn tại
    @Test
    void getItemById_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.getItemById(999));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }


    //updateItem
    // khi id tồn tại, thông tin mới hợp lệ
    @Test
    void updateItem_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        itemService.updateItem(1, "Producer B", "Mô tả mới", "Xe máy Yamaha", "new.jpg");

        assertEquals("Xe máy Yamaha", itemService.getItemById(1).getName());
        assertEquals("Producer B", itemService.getItemById(1).getProducer());
    }
    // khi id ko tồn tại
    @Test
    void updateItem_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.updateItem(999, "P", "D", "N", "i.jpg"));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }


    //xóa item
    // khi id tồn tại, danh sách có 1 item
    @Test
    void deleteItem_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        itemService.deleteItem(1);
        assertEquals(0, itemService.getAllItems().size());
    }
    // khi id ko tồn tại
    @Test
    void deleteItem_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.deleteItem(999));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }

    //get all
    // khi chưa có  item
    @Test
    void getAllItems_empty() {
        assertTrue(itemService.getAllItems().isEmpty());
    }
    // thêm 3 item
    @Test
    void getAllItems_multipleItems() {
        itemService.addItem(makeItem(1, "Item A", 1000));
        itemService.addItem(makeItem(2, "Item B", 2000));
        itemService.addItem(makeItem(3, "Item C", 3000));
        assertEquals(3, itemService.getAllItems().size());
    }
}

