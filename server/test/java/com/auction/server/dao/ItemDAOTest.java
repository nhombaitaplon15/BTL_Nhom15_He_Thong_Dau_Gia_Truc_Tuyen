package com.auction.server.dao;

import com.auction.common.factory.ItemFactory;
import com.auction.common.model.*;
import com.auction.common.network.AuctionItemDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ItemDAOTest {

  private ItemDAO itemDAO;
  private Connection mockConnection;
  private PreparedStatement mockPreparedStatement;
  private ResultSet mockResultSet;

  private MockedStatic<DBConnection> mockedDBConnection;
  private MockedStatic<ItemFactory> mockedItemFactory;

  @BeforeEach
  void setUp() throws SQLException {
    itemDAO = new ItemDAO();
    mockConnection = mock(Connection.class);
    mockPreparedStatement = mock(PreparedStatement.class);
    mockResultSet = mock(ResultSet.class);

    // Giả lập DBConnection
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

    // Giả lập ItemFactory để chống lỗi NullPointerException khi parse ResultSet
    mockedItemFactory = Mockito.mockStatic(ItemFactory.class);
    Item mockItem = mock(Item.class); // Tạo một đối tượng Item ảo để dùng chung
    mockedItemFactory.when(() -> ItemFactory.createFromResultSet(any(ResultSet.class))).thenReturn(mockItem);
  }

  @AfterEach
  void tearDown() {
    mockedDBConnection.close();
    mockedItemFactory.close();
  }

  // --- 1. TEST CÁC HÀM GET (LẤY DANH SÁCH & LẤY THEO ID) ---

  @Test
  @DisplayName("getAllItems - Lấy thành công toàn bộ vật phẩm")
  void testGetAllItems() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, true, false); // Trả về 2 dòng

    List<Item> items = itemDAO.getAllItems();
    assertEquals(2, items.size());
  }

  @Test
  @DisplayName("getItemById - Lấy thành công khi tìm thấy ID")
  void testGetItemById_Exists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);

    Item item = itemDAO.getItemById(1);
    assertNotNull(item);
  }

  @Test
  @DisplayName("getItemsByType - Lọc chuẩn xác theo loại vật phẩm")
  void testGetItemsByType() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false); // Trả về 1 dòng

    List<Item> byType = itemDAO.getItemsByType("ELECTRONICS");

    assertEquals(1, byType.size());
    verify(mockPreparedStatement, times(1)).executeQuery();
  }

  @Test
  @DisplayName("getItemsBySeller - Lọc chuẩn xác theo ID người bán")
  void testGetItemsBySeller() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false); // Trả về 1 dòng độc lập

    List<Item> bySeller = itemDAO.getItemsBySeller(10);

    assertEquals(1, bySeller.size());
    verify(mockPreparedStatement, times(1)).executeQuery();
  }

  // --- 2. TEST HÀM INSERT PHỨC TẠP (XỬ LÝ ĐA HÌNH) ---

  @Test
  @DisplayName("insertItem - Thêm thành công sản phẩm loại Điện tử (Electronics)")
  void testInsertItem_Electronics() throws SQLException {
    // Cấu hình ResultSet trả về Generated ID
    when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getInt(1)).thenReturn(99);

    // Dùng mock để giả lập đối tượng Electronics mà không cần gọi constructor thực tế
    Electronics e = mock(Electronics.class);
    when(e.getName()).thenReturn("Laptop Dell");
    when(e.getItemType()).thenReturn("ELECTRONICS");
    when(e.getBrand()).thenReturn("Dell");
    when(e.getModel()).thenReturn("XPS 15");

    int generatedId = itemDAO.insertItem(mockConnection, e);

    assertEquals(99, generatedId);
    // Verify code có đẩy đúng dữ liệu đặc thù của Electronics vào cột số 7, 8
    verify(mockPreparedStatement).setString(7, "Dell");
    verify(mockPreparedStatement).setString(8, "XPS 15");
  }

  @Test
  @DisplayName("insertItem - Thêm thành công sản phẩm loại Xe cộ (Vehicle)")
  void testInsertItem_Vehicle() throws SQLException {
    when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getInt(1)).thenReturn(100);

    Vehicle v = mock(Vehicle.class);
    when(v.getMake()).thenReturn("Toyota");
    when(v.getLicensePlate()).thenReturn("29A-12345");

    int generatedId = itemDAO.insertItem(mockConnection, v);

    assertEquals(100, generatedId);
    verify(mockPreparedStatement).setString(14, "Toyota"); // Xe cộ set ở cột 14
    verify(mockPreparedStatement).setString(20, "29A-12345"); // Biển số cột 20
  }

  @Test
  @DisplayName("insertItem - Thất bại do executeUpdate trả về 0")
  void testInsertItem_FailNoRowsAffected() throws SQLException {
    when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeUpdate()).thenReturn(0);

    Item dummy = mock(Item.class);
    SQLException exception = assertThrows(SQLException.class, () -> itemDAO.insertItem(mockConnection, dummy));
    assertTrue(exception.getMessage().contains("không có dòng nào được thêm"));
  }

  // --- 3. TEST XÓA SẢN PHẨM ---

  @Test
  @DisplayName("deleteItem - Xóa thành công")
  void testDeleteItem_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = itemDAO.deleteItem(5);
    assertTrue(result);
  }

  // --- 4. TEST CÁC CÂU LỆNH JOIN PHỨC TẠP VỚI BẢNG AUCTION ---

  @Test
  @DisplayName("getSellerProductsByStatusAndKeyword - Khớp nối Item và Auction DTO thành công")
  void testGetSellerProductsByStatusAndKeyword() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false);

    // Giả lập các cột của bảng Auction
    when(mockResultSet.getInt("auction_id")).thenReturn(50);
    when(mockResultSet.getDouble("current_price")).thenReturn(1500.0);
    when(mockResultSet.getInt("total_bids")).thenReturn(12);
    when(mockResultSet.getInt("current_winner_id")).thenReturn(5);

    // Cần giả lập wasNull = false vì hàm test có kiểm tra !rs.wasNull()
    when(mockResultSet.wasNull()).thenReturn(false);

    List<AuctionItemDTO> result = itemDAO.getSellerProductsByStatusAndKeyword(10, "RUNNING", "laptop");

    assertEquals(1, result.size());
    assertEquals(50, result.get(0).getAuction().getAuctionId());
    assertEquals(1500.0, result.get(0).getAuction().getCurrentPrice());

    // Xác minh PreparedStatement nhận đúng keyword
    verify(mockPreparedStatement).setString(3, "%laptop%");
  }

  @Test
  @DisplayName("getApprovedItemsWithoutAuction - Lấy chuẩn sản phẩm chưa có phiên")
  void testGetApprovedItemsWithoutAuction() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, true, false);

    List<Item> result = itemDAO.getApprovedItemsWithoutAuction(10, "Tranh");

    assertEquals(2, result.size());
    verify(mockPreparedStatement).setString(2, "%Tranh%");
  }
}