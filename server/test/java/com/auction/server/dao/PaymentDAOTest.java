package com.auction.server.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class PaymentDAOTest {

  private PaymentDAO paymentDAO;
  private Connection mockConnection;
  private MockedStatic<DBConnection> mockedDBConnection;

  @BeforeEach
  void setUp() {
    paymentDAO = new PaymentDAO();
    mockConnection = mock(Connection.class);

    // Giả lập kết nối DB cho các hàm tự mở kết nối (getBalance, getEscrowBalance)
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);
  }

  @AfterEach
  void tearDown() {
    mockedDBConnection.close();
  }

  // =================================================================================
  // 1. TEST HÀM updateBalance (Toán tử trừ tiền "-" & Chuỗi 3 bước bảo mật)
  // =================================================================================

  @Test
  @DisplayName("updateBalance [-] - Thất bại ngay từ Bước 1 vì tài khoản không đủ số dư")
  void testUpdateBalance_Deduct_InsufficientBalance() throws SQLException {
    PreparedStatement psCheck = mock(PreparedStatement.class);
    ResultSet rsCheck = mock(ResultSet.class);

    // Khi gặp câu lệnh SELECT check số dư
    when(mockConnection.prepareStatement(contains("SELECT balance"))).thenReturn(psCheck);
    when(psCheck.executeQuery()).thenReturn(rsCheck);
    when(rsCheck.next()).thenReturn(false); // Giả lập không có dòng nào thỏa mãn (Thiếu tiền)

    boolean result = paymentDAO.updateBalance(mockConnection, 10, 5000.0, "-");

    assertFalse(result, "Hàm phải trả về false ngay khi không đủ số dư ký quỹ");
    // Đảm bảo không chạy xuống Bước 2 và Bước 3
    verify(mockConnection, never()).prepareStatement(contains("UPDATE public.users SET balance"));
  }

  @Test
  @DisplayName("updateBalance [-] - Thất bại ở Bước 2 do lỗi đè giá đồng thời (Concurrent Update)")
  void testUpdateBalance_Deduct_DeductUpdateFails() throws SQLException {
    PreparedStatement psCheck = mock(PreparedStatement.class);
    PreparedStatement psDeduct = mock(PreparedStatement.class);
    ResultSet rsCheck = mock(ResultSet.class);

    when(mockConnection.prepareStatement(contains("SELECT balance"))).thenReturn(psCheck);
    when(psCheck.executeQuery()).thenReturn(rsCheck);
    when(rsCheck.next()).thenReturn(true); // Đủ tiền

    // Khi gặp câu lệnh UPDATE trừ tiền ví chính
    when(mockConnection.prepareStatement(contains("balance = balance -"))).thenReturn(psDeduct);
    when(psDeduct.executeUpdate()).thenReturn(0); // Trả về 0 dòng bị ảnh hưởng (Trừ tiền hụt)

    boolean result = paymentDAO.updateBalance(mockConnection, 10, 5000.0, "-");

    assertFalse(result);
    // Đảm bảo không chạy xuống Bước 3 nạp tiền cho Admin
    verify(mockConnection, never()).prepareStatement(contains("escrow_balance"));
  }

  @Test
  @DisplayName("updateBalance [-] - Thành công toàn bộ 3 bước & Tiền đổ vào đúng danh sách Admin Escrow")
  void testUpdateBalance_Deduct_AllStepsSuccess() throws SQLException {
    PreparedStatement psCheck = mock(PreparedStatement.class);
    PreparedStatement psDeduct = mock(PreparedStatement.class);
    PreparedStatement psAdmin = mock(PreparedStatement.class);
    ResultSet rsCheck = mock(ResultSet.class);

    when(mockConnection.prepareStatement(contains("SELECT balance"))).thenReturn(psCheck);
    when(psCheck.executeQuery()).thenReturn(rsCheck);
    when(rsCheck.next()).thenReturn(true);

    when(mockConnection.prepareStatement(contains("balance = balance -"))).thenReturn(psDeduct);
    when(psDeduct.executeUpdate()).thenReturn(1);

    // Khi gặp câu lệnh nạp tiền vào ví Escrow của Admin
    when(mockConnection.prepareStatement(contains("escrow_balance = escrow_balance +"))).thenReturn(psAdmin);
    when(psAdmin.executeUpdate()).thenReturn(1);

    // Dùng ArgumentCaptor để bắt xem ID Admin được chọn ngẫu nhiên là bao nhiêu
    ArgumentCaptor<Integer> adminIdCaptor = ArgumentCaptor.forClass(Integer.class);

    boolean result = paymentDAO.updateBalance(mockConnection, 10, 5000.0, "-");

    assertTrue(result);
    verify(psAdmin).setInt(eq(2), adminIdCaptor.capture());

    // Kiểm tra xem ID Admin có nằm trong danh sách hằng số {1, 2, 3, 4} không
    int chosenAdminId = adminIdCaptor.getValue();
    assertTrue(Arrays.asList(1, 2, 3, 4).contains(chosenAdminId), "ID Admin phải nằm trong nhóm hệ thống cấp quyền");
  }

  @Test
  @DisplayName("updateBalance [+] - Hoàn tiền thành công cho người bị đè giá")
  void testUpdateBalance_Refund_Success() throws SQLException {
    PreparedStatement psRefund = mock(PreparedStatement.class);
    when(mockConnection.prepareStatement(contains("balance = balance +"))).thenReturn(psRefund);
    when(psRefund.executeUpdate()).thenReturn(1);

    boolean result = paymentDAO.updateBalance(mockConnection, 10, 3000.0, "+");

    assertTrue(result);
    verify(psRefund).setDouble(1, 3000.0);
    verify(psRefund).setInt(2, 10);
  }

  @Test
  @DisplayName("updateBalance - Chặn đứng các toán tử không hợp lệ")
  void testUpdateBalance_InvalidOperator() throws SQLException {
    assertFalse(paymentDAO.updateBalance(mockConnection, 10, 100.0, "*"));
    assertFalse(paymentDAO.updateBalance(mockConnection, 10, 100.0, "abc"));
  }

  // =================================================================================
  // 2. TEST HÀM GIẢI NGÂN & PHẠT BÙNG KÈO (PROCESS ACTIONS)
  // =================================================================================

  @Test
  @DisplayName("processAcceptPayment - Chia chuẩn tỷ lệ 15% Hoa hồng Admin và 85% cho Seller")
  void testProcessAcceptPayment_Success() throws SQLException {
    PreparedStatement psMock = mock(PreparedStatement.class);
    when(mockConnection.prepareStatement(anyString())).thenReturn(psMock);
    when(psMock.executeUpdate()).thenReturn(1);

    // Giả sử món hàng đấu giá thành công với giá 10,000,000 VND
    boolean result = paymentDAO.processAcceptPayment(mockConnection, 88, 1, 10000000.0);

    assertTrue(result);

    // Bước 1: Thu 15% của 10M = 1.5M hoa hồng hệ thống
    verify(psMock).setDouble(1, 10000000.0); // Trừ ví tạm nguyên gốc
    verify(psMock).setDouble(2, 1500000.0);  // Cộng doanh thu hệ thống 15%

    // Bước 2: Chuyển 85% của 10M = 8.5M cho người bán
    verify(psMock).setDouble(1, 8500000.0);  // Chuyển ví chính seller
    verify(psMock).setInt(2, 88);
  }

  @Test
  @DisplayName("processPenalty7Percent - Thu phí phạt bùng kèo 7% và hoàn trả 93% cho Winner")
  void testProcessPenalty7Percent_Success() throws SQLException {
    PreparedStatement psMock = mock(PreparedStatement.class);
    when(mockConnection.prepareStatement(anyString())).thenReturn(psMock);
    when(psMock.executeUpdate()).thenReturn(1);

    boolean result = paymentDAO.processPenalty7Percent(mockConnection, 12, 2, 1000000.0);

    assertTrue(result);
    // Kiểm tra trích phạt 7% của 1M = 70,000 VND
    verify(psMock).setDouble(2, 70000.0);
    // Kiểm tra hoàn trả 93% của 1M = 930,000 VND về ví chính user
    verify(psMock).setDouble(1, 930000.0);
  }

  // =================================================================================
  // 3. TEST CÁC HÀM TRUY VẤN VÀ CẬP NHẬT PHỤ
  // =================================================================================

  @Test
  @DisplayName("updateAdminFunds - Cập nhật thủ công quỹ thành công")
  void testUpdateAdminFunds_Success() throws SQLException {
    PreparedStatement ps = mock(PreparedStatement.class);
    when(mockConnection.prepareStatement(contains("escrow_balance"))).thenReturn(ps);
    when(ps.executeUpdate()).thenReturn(1);

    boolean result = paymentDAO.updateAdminFunds(mockConnection, 1, 500.0, "+", 50.0);
    assertTrue(result);
  }

  @Test
  @DisplayName("getBalance - Lấy số dư ví chính thành công")
  void testGetBalance_Success() throws SQLException {
    PreparedStatement ps = mock(PreparedStatement.class);
    ResultSet rs = mock(ResultSet.class);

    when(mockConnection.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    when(rs.getDouble("balance")).thenReturn(750000.0);

    double balance = paymentDAO.getBalance(10);
    assertEquals(750000.0, balance);
  }

  @Test
  @DisplayName("getEscrowBalance - Lấy số dư ví đóng băng tạm thời của Admin thành công")
  void testGetEscrowBalance_Success() throws SQLException {
    PreparedStatement ps = mock(PreparedStatement.class);
    ResultSet rs = mock(ResultSet.class);

    when(mockConnection.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeQuery()).thenReturn(rs);
    when(rs.next()).thenReturn(true);
    when(rs.getDouble("escrow_balance")).thenReturn(2500000.0);

    double escrowBalance = paymentDAO.getEscrowBalance(2);
    assertEquals(2500000.0, escrowBalance);
  }
}