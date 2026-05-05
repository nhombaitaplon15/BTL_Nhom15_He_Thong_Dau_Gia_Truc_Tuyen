package com.auction.service;

import com.auction.common.model.Vehicle;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ItemService - Quản lý sản phẩm")
public class ItemServiceTest {

    private ItemService itemService;

    private Vehicle makeItem(int id, String name, int price) {
        return new Vehicle(id, "Producer A", price, "Mô tả", name, "img.jpg");
    }

    @BeforeEach
    void setUp() {
        itemService = new ItemService();
    }
    //thêm item

    @Test
    @DisplayName("addItem | HỢP LỆ | Thêm item đúng thông tin → lưu thành công")
    void addItem_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        assertEquals(1, itemService.getAllItems().size());
    }

    @Test
    @DisplayName("addItem | LỖI INVALID_ITEM | Item null → không được thêm")
    void addItem_nullItem_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(null));
        assertEquals(ErrorCode.INVALID_ITEM.name(), ex.getCode());
    }

    @Test
    @DisplayName("addItem | LỖI INVALID_ITEM | Tên item toàn khoảng trắng → không hợp lệ")
    void addItem_emptyName_shouldThrow() {
        Vehicle item = makeItem(1, "   ", 5000000);
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(item));
        assertEquals(ErrorCode.INVALID_ITEM.name(), ex.getCode());
    }

    @Test
    @DisplayName("addItem | LỖI INVALID_ITEM | Giá khởi điểm âm → không hợp lệ")
    void addItem_negativePrice_shouldThrow() {
        Vehicle item = makeItem(1, "Xe máy", -1);
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(item));
        assertEquals(ErrorCode.INVALID_ITEM.name(), ex.getCode());
    }

    @Test
    @DisplayName("addItem | LỖI ITEM_DUPLICATE | ID trùng với item đã có → không cho thêm")
    void addItem_duplicateId_shouldThrow() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.addItem(makeItem(1, "Xe máy Yamaha", 6000000)));
        assertEquals(ErrorCode.ITEM_DUPLICATE.name(), ex.getCode());
    }

    @Test
    @DisplayName("addItem | HỢP LỆ | Giá = 0 không phải âm → cho phép thêm")
    void addItem_zeroPriceIsValid() {
        assertDoesNotThrow(() -> itemService.addItem(makeItem(1, "Quà tặng", 0)));
    }

    //phần getId

    @Test
    @DisplayName("getItemById | HỢP LỆ | ID tồn tại → trả về đúng item")
    void getItemById_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        assertNotNull(itemService.getItemById(1));
        assertEquals("Xe máy Honda", itemService.getItemById(1).getName());
    }

    @Test
    @DisplayName("getItemById | LỖI ITEM_NOT_FOUND | ID không tồn tại → báo lỗi")
    void getItemById_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.getItemById(999));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }

    //updateItem

    @Test
    @DisplayName("updateItem | HỢP LỆ | ID tồn tại, thông tin mới hợp lệ → cập nhật thành công")
    void updateItem_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        itemService.updateItem(1, "Producer B", "Mô tả mới", "Xe máy Yamaha", "new.jpg");

        assertEquals("Xe máy Yamaha", itemService.getItemById(1).getName());
        assertEquals("Producer B", itemService.getItemById(1).getProducer());
    }

    @Test
    @DisplayName("updateItem | LỖI ITEM_NOT_FOUND | ID không tồn tại → báo lỗi")
    void updateItem_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.updateItem(999, "P", "D", "N", "i.jpg"));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }
    //xóa item

    @Test
    @DisplayName("deleteItem | HỢP LỆ | ID tồn tại → xóa thành công, danh sách rỗng")
    void deleteItem_success() {
        itemService.addItem(makeItem(1, "Xe máy Honda", 5000000));
        itemService.deleteItem(1);
        assertEquals(0, itemService.getAllItems().size());
    }

    @Test
    @DisplayName("deleteItem | LỖI ITEM_NOT_FOUND | ID không tồn tại → báo lỗi")
    void deleteItem_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class,
                () -> itemService.deleteItem(999));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }

    //get all
    @Test
    @DisplayName("getAllItems | HỢP LỆ | Chưa có item nào → trả về danh sách rỗng")
    void getAllItems_empty() {
        assertTrue(itemService.getAllItems().isEmpty());
    }

    @Test
    @DisplayName("getAllItems | HỢP LỆ | Thêm 3 item → trả về đủ 3 item")
    void getAllItems_multipleItems() {
        itemService.addItem(makeItem(1, "Item A", 1000));
        itemService.addItem(makeItem(2, "Item B", 2000));
        itemService.addItem(makeItem(3, "Item C", 3000));
        assertEquals(3, itemService.getAllItems().size());
    }
}

