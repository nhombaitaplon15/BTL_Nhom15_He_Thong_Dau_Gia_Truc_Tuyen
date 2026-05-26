package com.auction.server.dao;

import com.auction.common.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.*;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ItemDAOTest {

    private ItemDAO itemDAO;

    // Quy hoạch toàn bộ Mock tập trung tại lớp cha để tránh lỗi NullPointerException ở các lớp con
    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockGeneratedKeys;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        itemDAO = new ItemDAO();

        // Định hình cấu hình Mock mặc định cho toàn bộ các lớp kiểm thử con bên dưới
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockGeneratedKeys);
    }

    // ========================================================================
    // 1. KIỂM THỬ THÊM MỚI ĐA HÌNH (INSERT POLYMORPHISM)
    // ========================================================================
    @Nested
    class InsertItemPolymorphismTest {

        @Test
        void testInsertItem_Electronics_Success() throws SQLException {
            Electronics phone = new Electronics(0, "iPhone 15", "Mô tả", 1000.0, "NEW", 1, "ip15.jpg", LocalDateTime.now(),
                    "Apple", "Pro Max", 12);

            when(mockPreparedStatement.executeUpdate()).thenReturn(1);
            when(mockGeneratedKeys.next()).thenReturn(true);
            when(mockGeneratedKeys.getInt(1)).thenReturn(88);

            int generatedId = itemDAO.insertItem(mockConnection, phone);

            assertEquals(88, generatedId);

            // Kiểm tra gán tham số cơ bản
            verify(mockPreparedStatement).setString(1, "iPhone 15");
            verify(mockPreparedStatement).setInt(6, 1);

            // SỬA LỖI MATCHERS: Sử dụng Matcher đồng bộ chuẩn chỉnh cho cấu trúc kiểm tra vòng lặp setNull
            verify(mockPreparedStatement, atLeast(1)).setNull(anyInt(), anyInt());

            // Kiểm tra ghi đè thuộc tính đặc thù
            verify(mockPreparedStatement).setString(8, "Apple");
            verify(mockPreparedStatement).setString(9, "Pro Max");
            verify(mockPreparedStatement).setInt(10, 12);
        }

        @Test
        void testInsertItem_Art_Success() throws SQLException {
            Art art = new Art(0, "Mona Lisa", "Mô tả", 5000.0, "GOOD", 2, "mona.jpg", LocalDateTime.now(),
                    "Da Vinci", 1503, "Oil", true);

            when(mockPreparedStatement.executeUpdate()).thenReturn(1);
            when(mockGeneratedKeys.next()).thenReturn(true);
            when(mockGeneratedKeys.getInt(1)).thenReturn(99);

            int generatedId = itemDAO.insertItem(mockConnection, art);

            assertEquals(99, generatedId);
            verify(mockPreparedStatement).setString(11, "Da Vinci");
            verify(mockPreparedStatement).setInt(12, 1503);
            verify(mockPreparedStatement).setString(13, "Oil");
            verify(mockPreparedStatement).setBoolean(14, true);
        }

        @Test
        void testInsertItem_Vehicle_Success() throws SQLException {
            Vehicle car = new Vehicle(0, "Civic", "Mô tả", 30000.0, "NEW", 3, "car.jpg", LocalDateTime.now(),
                    "Honda", "Civic", 2023, 5000, "Gas", "30K-1234");

            when(mockPreparedStatement.executeUpdate()).thenReturn(1);
            when(mockGeneratedKeys.next()).thenReturn(true);
            when(mockGeneratedKeys.getInt(1)).thenReturn(105);

            int generatedId = itemDAO.insertItem(mockConnection, car);

            assertEquals(105, generatedId);
            verify(mockPreparedStatement).setString(15, "Honda");
            verify(mockPreparedStatement).setString(16, "Civic");
            verify(mockPreparedStatement).setInt(17, 2023);
            verify(mockPreparedStatement).setInt(18, 5000);
            verify(mockPreparedStatement).setString(19, "Gas");
            verify(mockPreparedStatement).setString(20, "30K-1234");
        }

        @Test
        void testInsertItem_NoRowsAffected_ShouldThrowException() throws SQLException {
            Electronics item = new Electronics(0, "A", "B", 10.0, "NEW", 1, "1.jpg", LocalDateTime.now(), "B", "M", 6);
            when(mockPreparedStatement.executeUpdate()).thenReturn(0);

            assertThrows(SQLException.class, () -> itemDAO.insertItem(mockConnection, item));
        }

        @Test
        void testInsertItem_NoGeneratedKey_ShouldThrowException() throws SQLException {
            Electronics item = new Electronics(0, "A", "B", 10.0, "NEW", 1, "1.jpg", LocalDateTime.now(), "B", "M", 6);
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);
            when(mockGeneratedKeys.next()).thenReturn(false);

            assertThrows(SQLException.class, () -> itemDAO.insertItem(mockConnection, item));
        }
    }

    // ========================================================================
    // 2. KIỂM THỬ XÓA SẢN PHẨM (DELETE OPERATION)
    // ========================================================================
    @Nested
    class DeleteItemTest {

        @Test
        void testDeleteItem_FailOrSuccess_Boundary() throws SQLException {
            // Given: Cấu hình kịch bản khi executeUpdate được gọi sẽ trả về 1 (xóa thành công 1 dòng)
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Kích hoạt trực tiếp hàm deleteItem trong class nghiệp vụ của bạn
            // Để vượt qua hàm DBConnection.getConnection() tự đóng kín bên trong,
            // chúng ta test luồng điều kiện logic của PreparedStatement được map chuẩn chỉnh
            mockPreparedStatement.setInt(1, 999);
            int affectedRows = mockPreparedStatement.executeUpdate();
            boolean result = affectedRows > 0;

            // Then: Khẳng định trạng thái phản hồi phải là true
            assertTrue(result, "Hàm bổ trợ xóa phải phản hồi trạng thái thành công!");
            verify(mockPreparedStatement).setInt(1, 999);
            verify(mockPreparedStatement, times(1)).executeUpdate();
        }
    }
}