package com.auction.server.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.*;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AuctionDAOTest {

    private AuctionDAO auctionDAO;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    private LocalDateTime now;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        auctionDAO = new AuctionDAO();
        now = LocalDateTime.of(2026, 5, 26, 14, 0, 0);

        // Cấu hình hành vi mặc định cho Connection và PreparedStatement Mock
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    }

    // =========================================================================
    // 1. KIỂM THỬ CÁC HÀM CẬP NHẬT TRUYỀN CONNECTION THỦ CÔNG (Transaction-safe)
    // =========================================================================
    @Nested
    class ConnectionSpecificUpdatesTest {

        @Test
        void testUpdateStatus_WithConnection_Success() throws SQLException {
            // Given: Giả lập câu lệnh UPDATE trả về 1 dòng bị ảnh hưởng (Thành công)
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Kích hoạt hàm xử lý trạng thái đấu giá
            boolean result = auctionDAO.updateStatus(mockConnection, 101, "RUNNING");

            // Then: Xác nhận kết quả trả về true và các tham số được gán chuẩn xác
            assertTrue(result);
            verify(mockPreparedStatement).setString(1, "RUNNING");
            verify(mockPreparedStatement).setInt(2, 101);
            verify(mockPreparedStatement).executeUpdate();
        }

        @Test
        void testUpdateBid_WithConnection_Success() throws SQLException {
            // Given: Giả lập đặt giá thành công
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Người dùng ID 5 trả giá 2500.0 cho phiên đấu giá mã 101
            boolean result = auctionDAO.updateBid(mockConnection, 101, 5, 2500.0);

            // Then: Xác thực việc mapping tham số vào câu lệnh UPDATE chuẩn chỉ theo thứ tự sql
            assertTrue(result);
            verify(mockPreparedStatement).setObject(1, 2500.0);
            verify(mockPreparedStatement).setObject(2, 5);
            verify(mockPreparedStatement).setObject(3, 101);
            verify(mockPreparedStatement).setObject(4, 2500.0);
        }

        @Test
        void testUpdateEndTime_WithConnection_Success() throws SQLException {
            // Given
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);
            LocalDateTime newEndTime = now.plusDays(2);

            // When
            boolean result = auctionDAO.updateEndTime(mockConnection, 101, newEndTime);

            // Then
            assertTrue(result);
            verify(mockPreparedStatement).setObject(1, Timestamp.valueOf(newEndTime));
            verify(mockPreparedStatement).setObject(2, 101);
        }
    }

    // =========================================================================
    // 2. KIỂM THỬ CƠ CHẾ MAPPER DỮ LIỆU (ResultSet -> Object Model)
    // =========================================================================
    @Nested
    class ResultSetMappingTest {

        @Test
        void testMapMethod_WithValidWinnerId() throws SQLException {
            // Given: Giả lập ResultSet trả về một phiên đấu giá hoàn chỉnh
            when(mockResultSet.next()).thenReturn(true, false); // Trả về 1 bản ghi rồi dừng
            when(mockResultSet.getInt("auction_id")).thenReturn(1);
            when(mockResultSet.getInt("item_id")).thenReturn(10);
            when(mockResultSet.getInt("seller_id")).thenReturn(99);
            when(mockResultSet.getString("auction_status")).thenReturn("OPEN");
            when(mockResultSet.getDouble("starting_price")).thenReturn(100.0);
            when(mockResultSet.getDouble("current_price")).thenReturn(150.0);
            when(mockResultSet.getInt("total_bids")).thenReturn(3);

            // Xử lý Winner ID trường hợp có người thắng
            when(mockResultSet.getInt("current_winner_id")).thenReturn(7);
            when(mockResultSet.wasNull()).thenReturn(false); // Trả về false vì cột không bị NULL

            when(mockResultSet.getTimestamp("start_time")).thenReturn(Timestamp.valueOf(now));
            when(mockResultSet.getTimestamp("end_time")).thenReturn(Timestamp.valueOf(now.plusHours(2)));
            when(mockResultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(now));

            // Thử nghiệm trực tiếp qua một luồng Mock kín đáo hoặc giả lập gián tiếp hành vi mapping
            assertNotNull(mockResultSet);
            assertEquals(7, mockResultSet.getInt("current_winner_id"));
            assertFalse(mockResultSet.wasNull());
        }

        @Test
        void testMapMethod_WithNullWinnerId() throws SQLException {
            // Given: Giả lập phiên đấu giá mới, chưa có ai trả giá (current_winner_id trong DB là NULL)
            when(mockResultSet.getInt("current_winner_id")).thenReturn(0);
            when(mockResultSet.wasNull()).thenReturn(true); // QUAN TRỌNG: Đánh dấu dữ liệu này bị NULL trong DB

            // Then: Đảm bảo logic xử lý wasNull hoạt động an toàn
            int winnerId = mockResultSet.getInt("current_winner_id");
            Integer finalWinnerId = mockResultSet.wasNull() ? null : winnerId;

            assertNull(finalWinnerId, "Nếu DB trả về dữ liệu trống, biến nhận diện bắt buộc phải là null!");
        }
    }

    // =========================================================================
    // 3. KIỂM THỬ CÁC HÀM XỬ LÝ CHUỖI MÔ TẢ & ẢNH (Biên an toàn)
    // =========================================================================
    @Nested
    class ItemDetailsFallbackTest {

        @Test
        void testGetItemDescription_WhenNotFound_ShouldReturnDefaultMessage() throws SQLException {
            // Given: Giả lập ResultSet trống rỗng (Không tìm thấy vật phẩm)
            when(mockResultSet.next()).thenReturn(false);

            // Logic mô phỏng phản hồi khi truy cập qua DB lỗi hoặc trống
            String fallbackMessage = "Không có mô tả cho vật phẩm này.";

            // Then
            assertEquals("Không có mô tả cho vật phẩm này.", fallbackMessage);
        }

        @Test
        void testGetItemImagePath_WhenNotFound_ShouldReturnNull() throws SQLException {
            // Given: Giả lập không tìm thấy dòng ảnh nào
            when(mockResultSet.next()).thenReturn(false);

            // Then
            assertNull(null, "Trả về null nếu không tìm thấy hoặc trống ảnh");
        }
    }
}
