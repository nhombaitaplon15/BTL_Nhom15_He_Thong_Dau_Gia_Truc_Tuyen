package com.auction.server.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class BidDAOTest {

  private BidDAO bidDAO;
  private Connection mockConnection;
  private PreparedStatement mockPreparedStatement;
  private ResultSet mockResultSet;
  private MockedStatic<DBConnection> mockedDBConnection;

  @BeforeEach
  void setUp() throws SQLException {
    bidDAO = new BidDAO();
    mockConnection = mock(Connection.class);
    mockPreparedStatement = mock(PreparedStatement.class);
    mockResultSet = mock(ResultSet.class);

    // Giả lập kết nối DB
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);

    // Cấu hình mặc định: mọi câu lệnh chuẩn bị đều trả về mockPreparedStatement
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
  }

  @AfterEach
  void tearDown() {
    mockedDBConnection.close();
  }

  // =================================================================================
  // 1. TEST HÀM getBidHistory (Lấy lịch sử đấu giá & Fallback)
  // =================================================================================

  @Test
  @DisplayName("getBidHistory - Trực tiếp từ bidding_history thành công")
  void testGetBidHistory_FromBiddingHistory() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false); // 1 dòng dữ liệu

    // Mock dữ liệu trả về từ bảng bidding_history
    when(mockResultSet.getString("bidder_name")).thenReturn("diep_nguyen");
    when(mockResultSet.getDouble("bid_amount")).thenReturn(5000.0);
    when(mockResultSet.getTimestamp("bid_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 14, 30)));

    List<BidDAO.BidRow> result = bidDAO.getBidHistory(1);

    assertEquals(1, result.size());
    assertEquals(1, result.get(0).rank()); // Kiểm tra logic tự động tăng rank
    assertEquals("diep_nguyen", result.get(0).username());
    assertEquals(5000.0, result.get(0).amount());
    assertEquals("14:30:00", result.get(0).bidTime()); // Kiểm tra formatter
  }

  @Test
  @DisplayName("getBidHistory - Fallback sang transactions khi bidding_history lỗi")
  void testGetBidHistory_FallbackToTransactions() throws SQLException {
    // GIẢ LẬP: Câu SQL chứa chữ "bidding_history" sẽ quăng lỗi
    when(mockConnection.prepareStatement(argThat(sql -> sql != null && sql.contains("bidding_history"))))
        .thenThrow(new SQLException("Table not ready"));

    // Câu SQL chứa chữ "transactions" sẽ chạy bình thường
    PreparedStatement mockFallbackStmt = mock(PreparedStatement.class);
    when(mockConnection.prepareStatement(argThat(sql -> sql != null && sql.contains("transactions"))))
        .thenReturn(mockFallbackStmt);
    when(mockFallbackStmt.executeQuery()).thenReturn(mockResultSet);

    when(mockResultSet.next()).thenReturn(true, false);
    // Mock dữ liệu trả về từ bảng transactions (JOIN users)
    when(mockResultSet.getString("username")).thenReturn("fallback_user");
    when(mockResultSet.getDouble("amount")).thenReturn(3000.0);
    when(mockResultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 9, 15)));

    List<BidDAO.BidRow> result = bidDAO.getBidHistory(1);

    assertEquals(1, result.size());
    assertEquals("fallback_user", result.get(0).username());
    assertEquals("09:15:00", result.get(0).bidTime());

    // Verify code có thực sự truyền đúng tham số loại giao dịch vào fallback không
    verify(mockFallbackStmt).setString(1, "BID_AUCTION_1");
  }

  // =================================================================================
  // 2. TEST HÀM countBidders (Đếm số lượng người tham gia & Fallback)
  // =================================================================================

  @Test
  @DisplayName("countBidders - Từ bidding_history thành công")
  void testCountBidders_FromBiddingHistory() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getInt("cnt")).thenReturn(5);

    int count = bidDAO.countBidders(10);
    assertEquals(5, count);
  }

  @Test
  @DisplayName("countBidders - Fallback sang transactions khi bidding_history trả về 0")
  void testCountBidders_FallbackWhenZero() throws SQLException {
    // Lần 1: bidding_history trả về 0
    ResultSet mockRs1 = mock(ResultSet.class);
    when(mockRs1.next()).thenReturn(true);
    when(mockRs1.getInt("cnt")).thenReturn(0);

    // Lần 2: transactions trả về 3
    ResultSet mockRs2 = mock(ResultSet.class);
    when(mockRs2.next()).thenReturn(true);
    when(mockRs2.getInt("cnt")).thenReturn(3);

    PreparedStatement mockStmt1 = mock(PreparedStatement.class);
    PreparedStatement mockStmt2 = mock(PreparedStatement.class);

    when(mockConnection.prepareStatement(argThat(sql -> sql != null && sql.contains("bidding_history")))).thenReturn(mockStmt1);
    when(mockConnection.prepareStatement(argThat(sql -> sql != null && sql.contains("transactions")))).thenReturn(mockStmt2);

    when(mockStmt1.executeQuery()).thenReturn(mockRs1);
    when(mockStmt2.executeQuery()).thenReturn(mockRs2);

    int count = bidDAO.countBidders(10);
    assertEquals(3, count);
  }

  // =================================================================================
  // 3. TEST HÀM getWinBidTime (Thời điểm đặt giá thắng cuộc)
  // =================================================================================

  @Test
  @DisplayName("getWinBidTime - Lấy thời gian thành công (format chuẩn DATETIME)")
  void testGetWinBidTime_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);

    // Tạo thời gian mẫu: 14:30:00 ngày 01/06/2026
    LocalDateTime testTime = LocalDateTime.of(2026, 6, 1, 14, 30, 0);
    when(mockResultSet.getTimestamp("bid_time")).thenReturn(Timestamp.valueOf(testTime));

    String timeStr = bidDAO.getWinBidTime(1);

    // So sánh với hằng số DATETIME_FMT ("HH:mm:ss dd/MM/yyyy")
    assertEquals("14:30:00 01/06/2026", timeStr);
  }

  @Test
  @DisplayName("getWinBidTime - Trả về null khi bảng chưa có dữ liệu hoặc lỗi")
  void testGetWinBidTime_ReturnsNull() throws SQLException {
    // Giả lập văng lỗi SQL
    when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Table error"));

    String timeStr = bidDAO.getWinBidTime(1);

    assertNull(timeStr, "Hàm phải trả về null để caller tự xử lý fallback bằng endTime");
  }
  @Test
  @DisplayName("getBidHistory - Trả về list rỗng khi cả bảng chính và fallback đều lỗi")
  void testGetBidHistory_AllFail() throws SQLException {
    // Giả lập cả 2 query đều throw exception
    when(mockConnection.prepareStatement(anyString())).thenThrow(new SQLException("DB error"));

    List<BidDAO.BidRow> result = bidDAO.getBidHistory(1);

    assertTrue(result.isEmpty());
  }
  @Test
  @DisplayName("getBidHistory - Xử lý Timestamp bằng null")
  void testGetBidHistory_NullTimestamp() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false);

    // Giả lập Timestamp null
    when(mockResultSet.getTimestamp("bid_time")).thenReturn(null);
    when(mockResultSet.getString("bidder_name")).thenReturn("test_user");

    List<BidDAO.BidRow> result = bidDAO.getBidHistory(1);

    assertEquals("—", result.get(0).bidTime()); // Đảm bảo trả về "—"
  }
}