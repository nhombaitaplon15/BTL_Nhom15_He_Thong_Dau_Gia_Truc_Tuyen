package com.auction.server.dao;

import com.auction.common.factory.UserFactory;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TransactionDAOTest {

  private TransactionDAO transactionDAO;
  private Connection mockConnection;
  private PreparedStatement mockPreparedStatement;
  private ResultSet mockResultSet;

  private MockedStatic<DBConnection> mockedDBConnection;
  private MockedStatic<UserFactory> mockedUserFactory;

  @BeforeEach
  void setUp() throws SQLException {
    transactionDAO = new TransactionDAO();
    mockConnection = mock(Connection.class);
    mockPreparedStatement = mock(PreparedStatement.class);
    mockResultSet = mock(ResultSet.class);

    // Giả lập kết nối DB
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);

    // Mặc định trả về mockPreparedStatement cho mọi câu lệnh SQL
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

    // Giả lập UserFactory để tránh lỗi khi getAllTransactions gọi tạo User
    mockedUserFactory = Mockito.mockStatic(UserFactory.class);
    User mockUser = mock(User.class);
    mockedUserFactory.when(() -> UserFactory.createUser(anyInt(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
        .thenReturn(mockUser);
  }

  @AfterEach
  void tearDown() {
    mockedDBConnection.close();
    mockedUserFactory.close();
  }

  // --- 1. TEST CÁC HÀM CẬP NHẬT TRẠNG THÁI & TẠO GIAO DỊCH CƠ BẢN ---

  @Test
  @DisplayName("updateTransactionStatus - Thành công")
  void testUpdateTransactionStatus_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = transactionDAO.updateTransactionStatus(mockConnection, 10, "APPROVED");
    assertTrue(result);
    verify(mockPreparedStatement).setString(1, "APPROVED");
    verify(mockPreparedStatement).setInt(2, 10);
  }

  @Test
  @DisplayName("updateTransactionStatus - Thất bại quăng lỗi (ném ra ngoài cho Service xử lý)")
  void testUpdateTransactionStatus_ThrowsException() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("DB Error"));
    assertThrows(SQLException.class, () -> transactionDAO.updateTransactionStatus(mockConnection, 10, "APPROVED"));
  }

  @Test
  @DisplayName("createTransaction (Có truyền Connection) - Thành công")
  void testCreateTransaction_WithConn_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = transactionDAO.createTransaction(mockConnection, 1, 5000.0, "DEPOSIT", "PENDING");
    assertTrue(result);
    verify(mockPreparedStatement).setString(3, "DEPOSIT");
  }

  @Test
  @DisplayName("createTransaction (Không truyền Connection, tự mở) - Thành công")
  void testCreateTransaction_NoConn_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = transactionDAO.createTransaction(1, 5000.0, "DEPOSIT", "PENDING");
    assertTrue(result);
  }

  @Test
  @DisplayName("rejectTransaction - Thành công")
  void testRejectTransaction_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = transactionDAO.rejectTransaction(99);
    assertTrue(result);
  }

  // --- 2. TEST GIAO DỊCH (TRANSACTION) NGUYÊN TỬ CHO HÀM DUYỆT ---

  @Test
  @DisplayName("processApproval - DEPOSIT (Nạp tiền) thành công trơn tru -> Commit")
  void testProcessApproval_Deposit_Success() throws SQLException {
    // Lệnh chạy 2 lần executeUpdate (1 cho bảng transactions, 1 cho bảng users).
    // Trả về 1 cho cả hai lần gọi để giả lập thành công.
    when(mockPreparedStatement.executeUpdate()).thenReturn(1, 1);

    boolean result = transactionDAO.processApproval(100, 1, 5000.0, "DEPOSIT");

    assertTrue(result);
    verify(mockConnection).setAutoCommit(false); // Đảm bảo bật chế độ Transaction
    verify(mockConnection).commit();             // Đảm bảo chốt giao dịch
    verify(mockConnection, never()).rollback();  // Không có lỗi nên không rollback
  }

  @Test
  @DisplayName("processApproval - WITHDRAW (Rút tiền) thành công -> Commit")
  void testProcessApproval_Withdraw_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1, 1);

    boolean result = transactionDAO.processApproval(101, 2, 2000.0, "WITHDRAW");

    assertTrue(result);
    verify(mockConnection).commit();

    // Xác minh xem lệnh prepareStatement thứ 2 có gán điều kiện chặn âm balance không (ps2.setDouble(3, amount))
    verify(mockPreparedStatement).setDouble(3, 2000.0);
  }

  @Test
  @DisplayName("processApproval - Lỗi khi duyệt phiếu (Phiếu không tồn tại) -> Rollback")
  void testProcessApproval_FailOnTransactionUpdate_Rollback() throws SQLException {
    // executeUpdate trả về 0 ở ngay câu lệnh ĐẦU TIÊN (cập nhật bảng transactions)
    when(mockPreparedStatement.executeUpdate()).thenReturn(0);

    boolean result = transactionDAO.processApproval(102, 3, 1000.0, "DEPOSIT");

    assertFalse(result);
    verify(mockConnection).rollback(); // Đảm bảo gọi Rollback khi có lỗi
    verify(mockConnection, never()).commit();
  }

  @Test
  @DisplayName("processApproval - Lỗi số dư không đủ khi rút tiền -> Rollback")
  void testProcessApproval_FailOnUserBalanceUpdate_Rollback() throws SQLException {
    // Lần 1: Cập nhật phiếu APPROVED thành công (trả về 1)
    // Lần 2: Trừ tiền thất bại do balance < amount (trả về 0)
    when(mockPreparedStatement.executeUpdate()).thenReturn(1, 0);

    boolean result = transactionDAO.processApproval(103, 4, 99999.0, "WITHDRAW");

    assertFalse(result);
    verify(mockConnection).rollback(); // Phiếu đã duyệt phải bị thu hồi lại trạng thái
    verify(mockConnection, never()).commit();
  }

  // --- 3. TEST LẤY DANH SÁCH LỊCH SỬ GIAO DỊCH ---

  @Test
  @DisplayName("getAllTransactions - Trả về danh sách chính xác và parse đúng ngày tháng")
  void testGetAllTransactions_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false); // Trả về 1 dòng rồi dừng

    // Giả lập dữ liệu ResultSet
    when(mockResultSet.getInt("user_id")).thenReturn(5);
    when(mockResultSet.getString("transaction_type")).thenReturn("DEPOSIT");
    when(mockResultSet.getDouble("amount")).thenReturn(15000.0);
    when(mockResultSet.getString("status")).thenReturn("APPROVED");
    when(mockResultSet.getInt("transaction_id")).thenReturn(999);
    when(mockResultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 10, 0)));

    List<TransactionRequest> list = transactionDAO.getAllTransactions();

    assertEquals(1, list.size());
    assertEquals("DEPOSIT", list.get(0).getType());
    assertEquals(15000.0, list.get(0).getAmount());
    assertEquals(999, list.get(0).getRequestId());
    assertEquals("APPROVED", list.get(0).getStatus());
  }
}