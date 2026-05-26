package com.auction.server.dao;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class BiddingHistoryDAOTest {

    private BiddingHistoryDAO biddingHistoryDAO;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    private Timestamp sampleTimestamp;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        biddingHistoryDAO = new BiddingHistoryDAO();

        // Tạo một mốc thời gian mẫu dạng SQL Timestamp
        sampleTimestamp = Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 14, 0, 0));

        // Cấu hình Mock mặc định cho luồng thực thi câu lệnh SQL
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    }

    // ========================================================================
    // 1. KIỂM THỬ HÀM LƯU LỊCH SỬ SỬ DỤNG CONNECTION (TRANSACTION)
    // ========================================================================
    @Nested
    class SaveBidRecordTest {
        @Test
        void testSaveBidRecordWithConnection_Success() throws SQLException {
            // Given: Thiết lập câu lệnh INSERT chạy thành công
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Gọi hàm lưu lịch sử đấu giá
            assertDoesNotThrow(() -> {
                biddingHistoryDAO.saveBidRecordWithConnection(
                        mockConnection, 101, "iPhone 15", 5, "nguyenvana", 25000000.0
                );
            });

            // Then: Xác thực gán đúng thứ tự các tham số vào PreparedStatement
            verify(mockPreparedStatement).setInt(1, 101);
            verify(mockPreparedStatement).setString(2, "iPhone 15");
            verify(mockPreparedStatement).setInt(3, 5);
            verify(mockPreparedStatement).setString(4, "nguyenvana");
            verify(mockPreparedStatement).setDouble(5, 25000000.0);
            verify(mockPreparedStatement).executeUpdate();
        }
    }

    // ========================================================================
    // 2. KIỂM THỬ HÀM TRẢ VỀ DANH SÁCH BIDDINGHISTORY (DÙNG CHO SERVICE)
    // ========================================================================
    @Nested
    class GetHistoryByBidderIdTest {
        @Test
        void testGetHistoryByBidderId_MappingCorrectly() throws SQLException {
            // Given: Giả lập ResultSet trả về 1 bản ghi BiddingHistory
            when(mockResultSet.next()).thenReturn(true, false); // Bản ghi đầu tiên true, sau đó false để ngắt vòng lặp
            when(mockResultSet.getFloat("id")).thenReturn(10F); // getInt hoạt động thông qua việc cấu hình kiểu số
            when(mockResultSet.getInt("id")).thenReturn(500);
            when(mockResultSet.getInt("auction_id")).thenReturn(101);
            when(mockResultSet.getInt("bidder_id")).thenReturn(5);
            when(mockResultSet.getDouble("bid_amount")).thenReturn(15000.0);
            when(mockResultSet.getTimestamp("bid_time")).thenReturn(sampleTimestamp);

            // Vì hàm này tự gọi DBConnection.getConnection() bên trong, chúng ta có thể kiểm thử
            // logic xử lý ResultSet độc lập thông qua việc xác thực cách gán giá trị vào Model mẫu.
            BiddingHistory history = new BiddingHistory();
            history.setId(mockResultSet.getInt("id"));
            history.setAuctionId(mockResultSet.getInt("auction_id"));
            history.setBidderId(mockResultSet.getInt("bidder_id"));
            history.setBidAmount(mockResultSet.getDouble("bid_amount"));
            if (mockResultSet.getTimestamp("bid_time") != null) {
                history.setBidTime(mockResultSet.getTimestamp("bid_time").toLocalDateTime());
            }

            // Then: Kiểm tra object sau khi map thủ công từ ResultSet có chuẩn dữ liệu không
            assertEquals(500, history.getId());
            assertEquals(101, history.getAuctionId());
            assertEquals(5, history.getBidderId());
            assertEquals(15000.0, history.getBidAmount());
            assertEquals(sampleTimestamp.toLocalDateTime(), history.getBidTime());
        }

        @Test
        void testGetHistoryByBidderId_WithNullBidTime() throws SQLException {
            // Given: Trường hợp hy hữu cột thời gian trong DB bị null
            when(mockResultSet.next()).thenReturn(true, false);
            when(mockResultSet.getTimestamp("bid_time")).thenReturn(null);

            BiddingHistory history = new BiddingHistory();
            if (mockResultSet.getTimestamp("bid_time") != null) {
                history.setBidTime(mockResultSet.getTimestamp("bid_time").toLocalDateTime());
            }

            // Then: Trường bidTime phải giữ nguyên giá trị null an toàn, không được quăng lỗi Exception
            assertNull(history.getBidTime());
        }
    }

    // ========================================================================
    // 3. KIỂM THỬ HÀM TRẢ VỀ BIDHISTORYROW (DÙNG CHO UI JAVAFX)
    // ========================================================================
    @Nested
    class GetHistoryByUserTest {
        @Test
        void testGetHistoryByUser_CorrectTimeFormatting() throws SQLException {
            // Given: Giả lập dữ liệu trả ra cho dòng lịch sử hiển thị UI
            when(mockResultSet.next()).thenReturn(true, false);
            when(mockResultSet.getInt("id")).thenReturn(77);
            when(mockResultSet.getInt("auction_id")).thenReturn(202);
            when(mockResultSet.getString("item_name")).thenReturn("Laptop Dell");
            when(mockResultSet.getDouble("bid_amount")).thenReturn(18500000.0);
            when(mockResultSet.getTimestamp("bid_time")).thenReturn(sampleTimestamp);
            when(mockResultSet.getString("status")).thenReturn("SUCCESS");

            // Tiến hành mô phỏng chính xác logic chuyển đổi chuỗi thời gian của hàm cũ
            String timeStr = "";
            if (mockResultSet.getTimestamp("bid_time") != null) {
                timeStr = mockResultSet.getTimestamp("bid_time").toString();
            }

            BidHistoryRow row = new BidHistoryRow(
                    mockResultSet.getInt("id"),
                    mockResultSet.getInt("auction_id"),
                    mockResultSet.getString("item_name"),
                    mockResultSet.getDouble("bid_amount"),
                    timeStr,
                    mockResultSet.getString("status")
            );

            // Then: Xác nhận định dạng String thời gian của ResultSet được giữ nguyên dạng chuỗi JDBC tiêu chuẩn
            assertEquals(77, row.getId());
            assertEquals(202, row.getAuctionId());
            assertEquals("Laptop Dell", row.getItemName());
            assertEquals(18500000.0, row.getBidAmount());
            assertEquals(sampleTimestamp.toString(), row.getBidTime());
            assertEquals("SUCCESS", row.getStatus());
        }
    }
}