package com.auction.server.dao;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory;
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
import static org.mockito.Mockito.*;

class BiddingHistoryDAOTest {

  private BiddingHistoryDAO biddingHistoryDAO;
  private Connection mockConnection;
  private PreparedStatement mockPreparedStatement;
  private ResultSet mockResultSet;
  private MockedStatic<DBConnection> mockedDBConnection;

  @BeforeEach
  void setUp() throws SQLException {
    biddingHistoryDAO = new BiddingHistoryDAO();
    mockConnection = mock(Connection.class);
    mockPreparedStatement = mock(PreparedStatement.class);
    mockResultSet = mock(ResultSet.class);

    // Giả lập DBConnection cho các hàm tự mở kết nối
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);

    // Mặc định trả về mockPreparedStatement khi Connection tạo lệnh SQL
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
  }

  @AfterEach
  void tearDown() {
    // Giải phóng mock static sau mỗi test case
    mockedDBConnection.close();
  }

  // =================================================================================
  // 1. TEST HÀM GHI NHẬN LỊCH SỬ (DÙNG CONNECTION ĐƯỢC TRUYỀN VÀO)
  // =================================================================================

  @Test
  @DisplayName("saveBidRecordWithConnection - Ghi nhật ký đặt giá thành công")
  void testSaveBidRecordWithConnection_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    // Hàm này nhận Connection từ bên ngoài nên ta truyền thẳng mockConnection vào
    assertDoesNotThrow(() -> biddingHistoryDAO.saveBidRecordWithConnection(
        mockConnection, 10, "Tranh Sơn Mài", 5, "diep_nguyen", 150000.0
    ));

    // Xác minh gán đúng thứ tự và giá trị tham số vào câu lệnh INSERT
    verify(mockPreparedStatement).setInt(1, 10);
    verify(mockPreparedStatement).setString(2, "Tranh Sơn Mài");
    verify(mockPreparedStatement).setInt(3, 5);
    verify(mockPreparedStatement).setString(4, "diep_nguyen");
    verify(mockPreparedStatement).setDouble(5, 150000.0);
    verify(mockPreparedStatement).executeUpdate();
  }

  // =================================================================================
  // 2. TEST CÁC HÀM TRUY VẤN LỊCH SỬ (TỰ MỞ KẾT NỐI QUA DBCONNECTION)
  // =================================================================================

  @Test
  @DisplayName("getHistoryByBidderId - Trả về danh sách thực thể gốc BiddingHistory")
  void testGetHistoryByBidderId_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    // Giả lập DB trả về 2 dòng kết quả, dòng thứ 3 là hết (false)
    when(mockResultSet.next()).thenReturn(true, true, false);

    // Mock dữ liệu cho từng cột
    when(mockResultSet.getInt("id")).thenReturn(101, 102);
    when(mockResultSet.getInt("auction_id")).thenReturn(1, 1);
    when(mockResultSet.getInt("bidder_id")).thenReturn(5, 5);
    when(mockResultSet.getDouble("bid_amount")).thenReturn(200000.0, 250000.0);

    Timestamp mockTimestamp = Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 15, 0));
    when(mockResultSet.getTimestamp("bid_time")).thenReturn(mockTimestamp);

    List<BiddingHistory> result = biddingHistoryDAO.getHistoryByBidderId(5);

    assertNotNull(result);
    assertEquals(2, result.size());

    // Kiểm tra đối tượng đầu tiên được map chính xác
    assertEquals(101, result.get(0).getId());
    assertEquals(200000.0, result.get(0).getBidAmount());
    assertEquals(mockTimestamp.toLocalDateTime(), result.get(0).getBidTime());

    verify(mockPreparedStatement).setInt(1, 5);
  }

  @Test
  @DisplayName("getHistoryByUser - Trả về danh sách BidHistoryRow hiển thị giao diện UI")
  void testGetHistoryByUser_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false); // Trả về 1 dòng

    when(mockResultSet.getInt("id")).thenReturn(500);
    when(mockResultSet.getInt("auction_id")).thenReturn(20);
    when(mockResultSet.getString("item_name")).thenReturn("Đồng hồ cổ");
    when(mockResultSet.getDouble("bid_amount")).thenReturn(990000.0);
    when(mockResultSet.getTimestamp("bid_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
    when(mockResultSet.getString("status")).thenReturn("SUCCESS");

    List<BidHistoryRow> result = biddingHistoryDAO.getHistoryByUser(5);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("Đồng hồ cổ", result.get(0).getItemName());
    assertEquals("SUCCESS", result.get(0).getStatus());
    verify(mockPreparedStatement).setInt(1, 5);
  }

  @Test
  @DisplayName("getHistoryByAuction - Truy vấn nhật ký trả giá theo mã phiên Auction ID thành công")
  void testGetHistoryByAuction_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false);

    when(mockResultSet.getInt("id")).thenReturn(700);
    when(mockResultSet.getInt("auction_id")).thenReturn(45);
    when(mockResultSet.getString("item_name")).thenReturn("Bình gốm Chu Đậu");
    when(mockResultSet.getDouble("bid_amount")).thenReturn(550000.0);
    when(mockResultSet.getTimestamp("bid_time")).thenReturn(null); // Giả lập trường hợp thời gian rỗng
    when(mockResultSet.getString("status")).thenReturn("SUCCESS");

    List<BidHistoryRow> result = biddingHistoryDAO.getHistoryByAuction(45);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("Bình gốm Chu Đậu", result.get(0).getItemName());
    assertEquals("", result.get(0).getBidTime(), "Nếu bid_time trong DB rỗng thì chuỗi trả về trên UI phải là rỗng");
    verify(mockPreparedStatement).setInt(1, 45);
  }

  // =================================================================================
  // 3. TEST KỊCH BẢN XẢY RA NGOẠI LỆ (EXCEPTION HANDLING)
  // =================================================================================

  @Test
  @DisplayName("getHistoryByAuction - Tự bắt lỗi catch khi DB sập và trả về danh sách rỗng")
  void testGetHistoryByAuction_CatchException() throws SQLException {
    // Giả lập lệnh query quăng ra lỗi hệ thống DB
    when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Connection lost"));

    List<BidHistoryRow> result = biddingHistoryDAO.getHistoryByAuction(45);

    assertNotNull(result, "Dù lỗi xảy ra, hàm không được crash mà phải trả về một danh sách để tránh sập UI");
    assertTrue(result.isEmpty(), "Danh sách trả về phải trống khi gặp lỗi ngoại lệ");
  }
  @Test
  @DisplayName("getHistoryByAuction - Map chính xác số tiền cực lớn")
  void testGetHistoryByAuction_LargeAmount() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false);
    when(mockResultSet.getDouble("bid_amount")).thenReturn(999999999.99);

    List<BidHistoryRow> result = biddingHistoryDAO.getHistoryByAuction(45);

    assertEquals(999999999.99, result.get(0).getBidAmount());
  }
}