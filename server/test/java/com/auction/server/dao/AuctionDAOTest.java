package com.auction.server.dao;

import com.auction.common.model.Auction;
import com.auction.common.model.BidHistoryRow;
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

class AuctionDAOTest {

  private AuctionDAO auctionDAO;
  private Connection mockConnection;
  private PreparedStatement mockPreparedStatement;
  private ResultSet mockResultSet;
  private MockedStatic<DBConnection> mockedDBConnection;

  @BeforeEach
  void setUp() throws SQLException {
    auctionDAO = new AuctionDAO();
    mockConnection = mock(Connection.class);
    mockPreparedStatement = mock(PreparedStatement.class);
    mockResultSet = mock(ResultSet.class);

    // Giả lập kết nối DB
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
  }

  @AfterEach
  void tearDown() {
    mockedDBConnection.close();
  }

  // =================================================================================
  // HELPER: Giả lập toàn bộ các cột dữ liệu để hàm map(rs) không bị NullPointerException
  // =================================================================================
  private void mockAuctionResultSet() throws SQLException {
    when(mockResultSet.getInt("auction_id")).thenReturn(1);
    when(mockResultSet.getInt("item_id")).thenReturn(100);
    when(mockResultSet.getInt("seller_id")).thenReturn(200);
    when(mockResultSet.getString("auction_status")).thenReturn("RUNNING");
    when(mockResultSet.getDouble("starting_price")).thenReturn(1000.0);
    when(mockResultSet.getDouble("current_price")).thenReturn(1500.0);
    when(mockResultSet.getInt("total_bids")).thenReturn(5);

    // Mock ResultSet.wasNull() cho current_winner_id
    when(mockResultSet.getInt("current_winner_id")).thenReturn(300);
    when(mockResultSet.wasNull()).thenReturn(false);

    // Mock các cột thời gian
    when(mockResultSet.getTimestamp("start_time"))
        .thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
    when(mockResultSet.getTimestamp("end_time"))
        .thenReturn(Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
    when(mockResultSet.getTimestamp("created_at"))
        .thenReturn(Timestamp.valueOf(LocalDateTime.now().minusDays(2)));
  }

  // --- 1. TEST CÁC HÀM TRUY VẤN (QUERY) ---

  @Test
  @DisplayName("getAll - Lấy danh sách thành công")
  void testGetAll_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false); // Trả về 1 dòng rồi kết thúc
    mockAuctionResultSet();

    List<Auction> list = auctionDAO.getAll();

    assertEquals(1, list.size());
    assertEquals(1, list.get(0).getAuctionId());
    assertEquals("RUNNING", list.get(0).getAuctionStatus());
  }

  @Test
  @DisplayName("getAuctionById - Trả về Auction khi tìm thấy")
  void testGetAuctionById_Exists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false);
    mockAuctionResultSet();

    Auction auction = auctionDAO.getAuctionById(1);

    assertNotNull(auction);
    assertEquals(1, auction.getAuctionId());
    assertEquals(1500.0, auction.getCurrentPrice());
  }

  @Test
  @DisplayName("getAuctionById - Trả về null khi không tìm thấy")
  void testGetAuctionById_NotFound() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(false); // DB trống

    Auction auction = auctionDAO.getAuctionById(99);
    assertNull(auction);
  }

  // --- 2. TEST CÁC HÀM CẬP NHẬT (UPDATE / INSERT) ---

  @Test
  @DisplayName("insertAuction - Thêm phiên đấu giá thành công")
  void testInsertAuction_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    Auction a = new Auction(0, 100, 200, "PENDING", 500.0, 500.0, 0, null,
        LocalDateTime.now(), LocalDateTime.now().plusDays(3), LocalDateTime.now());

    boolean result = auctionDAO.insertAuction(a);
    assertTrue(result);

    // Đảm bảo PreparedStatement set đủ 9 tham số
    verify(mockPreparedStatement, times(9)).setObject(anyInt(), any());
  }

  @Test
  @DisplayName("updateStatus - Thành công")
  void testUpdateStatus_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = auctionDAO.updateStatus(1, "FINISHED");
    assertTrue(result);
  }

  // --- 3. TEST LOGIC GIAO DỊCH (TRANSACTION) NGUYÊN TỬ ---

  @Test
  @DisplayName("executePlaceBidTransaction - Giao dịch đặt giá hoàn hảo")
  void testExecutePlaceBidTransaction_Success() throws SQLException {
    // Giả lập Query lấy itemName và bidderName
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getString("name")).thenReturn("Tranh Picasso");
    when(mockResultSet.getString("username")).thenReturn("diep_nguyen");

    // Giả lập Update ví, Update giá, Insert lịch sử đều thành công
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    boolean result = auctionDAO.executePlaceBidTransaction(1, 10, 5000.0);

    assertTrue(result);
    verify(mockConnection).setAutoCommit(false);
    verify(mockConnection).commit(); // Đảm bảo đã commit transaction
  }

  @Test
  @DisplayName("executePlaceBidTransaction - Rollback khi có người đè giá (Update Auction fail)")
  void testExecutePlaceBidTransaction_RollbackOnAuctionUpdateFail() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(false); // Dữ liệu tên default

    // Giả lập Update Auction thất bại (trả về 0 do điều kiện current_price < amount bị sai)
    when(mockPreparedStatement.executeUpdate()).thenReturn(0);

    boolean result = auctionDAO.executePlaceBidTransaction(1, 10, 2000.0);

    assertFalse(result);
    verify(mockConnection).rollback(); // Đảm bảo lệnh thu hồi dòng tiền được gọi
  }

  @Test
  @DisplayName("closeAuctionAndDetermineWinner - Tìm thấy người thắng và đóng phiên")
  void testCloseAuctionAndDetermineWinner_Success() throws SQLException {
    // Giả lập tìm thấy người thắng
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getInt("bidder_id")).thenReturn(99);

    // Giả lập Update trạng thái
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    assertDoesNotThrow(() -> auctionDAO.closeAuctionAndDetermineWinner(1));

    verify(mockConnection).setAutoCommit(false);
    verify(mockPreparedStatement).setInt(1, 99); // Verify ID người thắng được nạp vào
    verify(mockConnection).commit();
  }

  // --- 4. TEST LỊCH SỬ ĐẶT GIÁ ---

  @Test
  @DisplayName("getBidHistoryByBidder - Xử lý đúng trạng thái hiển thị động")
  void testGetBidHistoryByBidder_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false);

    // Mock dữ liệu trả về cho BidHistoryRow
    when(mockResultSet.getInt("id")).thenReturn(1);
    when(mockResultSet.getInt("auction_id")).thenReturn(10);
    when(mockResultSet.getString("item_name")).thenReturn("Đồng hồ Rolex");
    when(mockResultSet.getDouble("bid_amount")).thenReturn(2000.0);
    when(mockResultSet.getTimestamp("bid_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));

    // Mock dữ liệu logic động (Phiên RUNNING, bidder hiện tại không phải là winner -> BỊ ĐÈ GIÁ)
    when(mockResultSet.getTimestamp("end_time")).thenReturn(Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
    when(mockResultSet.getInt("current_winner_id")).thenReturn(999); // ID người khác
    when(mockResultSet.getString("auction_status")).thenReturn("RUNNING");

    List<BidHistoryRow> list = auctionDAO.getBidHistoryByBidder(100);

    assertEquals(1, list.size());
    assertEquals("Đồng hồ Rolex", list.get(0).getItemName());
    assertEquals("BỊ ĐÈ GIÁ", list.get(0).getStatus()); // Kiểm tra logic tính toán động
  }

  // --- 5. TEST THÔNG TIN VẬT PHẨM ---

  @Test
  @DisplayName("getItemDescription - Lấy mô tả thành công")
  void testGetItemDescription_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getString("description")).thenReturn("Mô tả chi tiết vật phẩm");

    String desc = auctionDAO.getItemDescription(100);
    assertEquals("Mô tả chi tiết vật phẩm", desc);
  }
}