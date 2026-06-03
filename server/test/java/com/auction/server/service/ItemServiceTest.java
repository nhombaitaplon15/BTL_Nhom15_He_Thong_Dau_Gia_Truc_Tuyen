package com.auction.server.service;

import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.ItemDAO;
import com.auction.server.service.ItemService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemDAO itemDAO;

    private ItemService itemService;

    @BeforeEach
    void setUp() throws Exception {
        itemService = new ItemService();
        Field field = ItemService.class.getDeclaredField("itemDAO");
        field.setAccessible(true);
        field.set(itemService, itemDAO);
    }

    // Tạo nhanh đối tượng sản phẩm điện tử để test
    private Item makeItem(int id, String name, double price) {
        return new Electronics(id, name, "Mô tả",
                price, "NEW", 1, "img.jpg", LocalDateTime.now(),
                "Samsung", "S24", 12);
    }

    @Nested
    @DisplayName("getAllItems")
    class GetAllTests {

        // Test: Trả về đầy đủ danh sách sản phẩm hiện có trong hệ thống
        @Test
        @DisplayName("Trả về danh sách đầy đủ từ DAO")
        void getAllItems_returnsList() {
            when(itemDAO.getAllItems()).thenReturn(Arrays.asList(
                    makeItem(1, "Laptop", 15_000_000),
                    makeItem(2, "Phone", 8_000_000)
            ));
            assertEquals(2, itemService.getAllItems().size());
        }

        // Test: Trả về danh sách trống khi kho hàng chưa có sản phẩm nào
        @Test
        @DisplayName("Trả về danh sách rỗng khi không có sản phẩm")
        void getAllItems_emptyList() {
            when(itemDAO.getAllItems()).thenReturn(Collections.emptyList());
            assertTrue(itemService.getAllItems().isEmpty());
        }
    }

    @Nested
    @DisplayName("getItemById")
    class GetByIdTests {

        // Test: Lấy thông tin chi tiết sản phẩm thành công dựa trên ID
        @Test
        @DisplayName("Tìm thấy item hợp lệ")
        void getItemById_found() {
            when(itemDAO.getItemById(1)).thenReturn(makeItem(1, "Laptop", 15_000_000));
            assertEquals("Laptop", itemService.getItemById(1).getName());
        }

        // Test: Ném lỗi hệ thống nếu ID sản phẩm tìm kiếm không tồn tại
        @Test
        @DisplayName("Ném lỗi ITEM_NOT_FOUND khi không tìm thấy")
        void getItemById_notFound() {
            when(itemDAO.getItemById(99)).thenReturn(null);
            AuctionException ex = assertThrows(AuctionException.class,
                    () -> itemService.getItemById(99));
            assertTrue(ex.getMessage().contains("ITEM_NOT_FOUND")
                    || ex.getMessage().contains("không tồn tại"));
        }
    }

    @Nested
    @DisplayName("addItem - validate đầu vào")
    class AddItemValidateTests {

        // Test: Ngăn chặn thêm sản phẩm nếu đối tượng truyền vào là null
        @Test
        @DisplayName("Ném lỗi khi item null")
        void addItem_nullItem() {
            assertThrows(AuctionException.class, () -> itemService.addItem(null));
            verifyNoInteractions(itemDAO);
        }

        // Test: Ngăn chặn thêm sản phẩm nếu tên sản phẩm bị bỏ trống
        @Test
        @DisplayName("Ném lỗi khi tên rỗng")
        void addItem_emptyName() {
            assertThrows(AuctionException.class, () -> itemService.addItem(makeItem(0, "", 15_000_000)));
            verifyNoInteractions(itemDAO);
        }

        // Test: Ngăn chặn thêm sản phẩm nếu tên sản phẩm nhận giá trị null
        @Test
        @DisplayName("Ném lỗi khi tên null")
        void addItem_nullName() {
            assertThrows(AuctionException.class, () -> itemService.addItem(makeItem(0, null, 15_000_000)));
            verifyNoInteractions(itemDAO);
        }

        // Test: Ngăn chặn thêm sản phẩm nếu mức giá khởi điểm nhỏ hơn 0
        @Test
        @DisplayName("Ném lỗi khi giá âm")
        void addItem_negativePrice() {
            assertThrows(AuctionException.class, () -> itemService.addItem(makeItem(0, "Laptop", -1)));
            verifyNoInteractions(itemDAO);
        }

        @Nested
        @DisplayName("deleteItem")
        class DeleteItemTests {

            // Test: Xóa sản phẩm thành công khi sản phẩm tồn tại và hợp lệ
            @Test
            @DisplayName("Xóa item thành công")
            void deleteItem_success() {
                when(itemDAO.getItemById(1)).thenReturn(makeItem(1, "Laptop", 15_000_000));
                when(itemDAO.deleteItem(1)).thenReturn(true);
                assertDoesNotThrow(() -> itemService.deleteItem(1));
            }

            // Test: Chặn lệnh xóa ngay từ đầu nếu sản phẩm cần xóa không tồn tại
            @Test
            @DisplayName("Ném lỗi khi item không tồn tại — không gọi deleteItem")
            void deleteItem_notFound() {
                when(itemDAO.getItemById(99)).thenReturn(null);
                assertThrows(AuctionException.class, () -> itemService.deleteItem(99));
                verify(itemDAO, never()).deleteItem(99);
            }

            // Test: Ném lỗi nếu DB từ chối xóa (ví dụ sản phẩm đang trong phiên đấu giá)
            @Test
            @DisplayName("Ném lỗi khi DAO deleteItem trả false (đang đấu giá)")
            void deleteItem_daoReturnsFalse() {
                when(itemDAO.getItemById(1)).thenReturn(makeItem(1, "Laptop", 15_000_000));
                when(itemDAO.deleteItem(1)).thenReturn(false);
                assertThrows(AuctionException.class, () -> itemService.deleteItem(1));
            }
        }
    }
    @Nested @DisplayName("addItem - Giao dịch và SQL")
    class AddItemTransactionTests {

        @Test @DisplayName("addItem: Rollback khi insertItem gặp lỗi SQLException")
        void addItem_rollbackOnException() throws Exception {
            Item item = makeItem(0, "Laptop", 10_000_000);

            // Mock connection và quản lý tĩnh DBConnection
            Connection mockConn = mock(Connection.class);
            try (MockedStatic<DBConnection> dbMock = mockStatic(DBConnection.class)) {
                dbMock.when(DBConnection::getConnection).thenReturn(mockConn);

                // Giả lập insertItem ném ra ngoại lệ
                when(itemDAO.insertItem(eq(mockConn), any())).thenThrow(new SQLException("Lỗi DB"));

                assertThrows(AuctionException.class, () -> itemService.addItem(item));

                // Kiểm tra rollback được gọi
                verify(mockConn).rollback();
                verify(mockConn, never()).commit();
            }
        }
    }
}
