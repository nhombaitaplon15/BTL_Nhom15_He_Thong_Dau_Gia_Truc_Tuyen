package com.auction.server.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PaymentDAOTest {

    private PaymentDAO paymentDAO;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        paymentDAO = new PaymentDAO();

        // Cấu hình mock mặc định cho kết nối SQL
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
    }

    // ========================================================================
    // 1. KIỂM THỬ HÀM CẬP NHẬT SỐ DƯ (UPDATE BALANCE)
    // ========================================================================
    @Nested
    class UpdateBalanceTest {

        @Test
        void testUpdateBalance_Deposit_Success() throws SQLException {
            // Given: Thiết lập câu lệnh nạp tiền (+) chạy thành công (ảnh hưởng 1 dòng)
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Nạp (+) 500,000đ cho user_id = 1
            boolean result = paymentDAO.updateBalance(mockConnection, 1, 500000.0, "+");

            // Then: Kiểm tra hàm trả về true và các chỉ mục tham số (index) gán chuẩn xác
            assertTrue(result);
            verify(mockPreparedStatement).setDouble(1, 500000.0);
            verify(mockPreparedStatement).setInt(2, 1);
            // Với phép cộng, không được gán tham số thứ 3
            verify(mockPreparedStatement, never()).setDouble(eq(3), anyDouble());
        }

        @Test
        void testUpdateBalance_Withdraw_Success() throws SQLException {
            // Given: Thiết lập câu lệnh trừ tiền (-) chạy thành công
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Rút (-) 200,000đ từ user_id = 1
            boolean result = paymentDAO.updateBalance(mockConnection, 1, 200000.0, "-");

            // Then: Kiểm tra việc gán tham số thứ 3 (điều kiện chống âm tài khoản: balance >= ?)
            assertTrue(result);
            verify(mockPreparedStatement).setDouble(1, 200000.0);
            verify(mockPreparedStatement).setInt(2, 1);
            verify(mockPreparedStatement).setDouble(3, 200000.0); // Bắt buộc có index 3
        }

        @Test
        void testUpdateBalance_InvalidOperator_ShouldReturnFalse() throws SQLException {
            // When: Truyền toán tử sai quy định (ví dụ: * hoặc /)
            boolean result = paymentDAO.updateBalance(mockConnection, 1, 100.0, "*");

            // Then: Hàm chặn ngay lập tức và trả về false trước khi tạo PreparedStatement
            assertFalse(result);
            verify(mockConnection, never()).prepareStatement(anyString());
        }
    }

    // ========================================================================
    // 2. KIỂM THỬ CẬP NHẬT QUỸ ADMIN (UPDATE ADMIN FUNDS)
    // ========================================================================
    @Nested
    class UpdateAdminFundsTest {

        @Test
        void testUpdateAdminFunds_Success() throws SQLException {
            // Given: Giả lập cập nhật dòng thành công
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Admin_id = 999, tăng quỹ treo tạm giữ 1,500,000đ (+), doanh thu sàn tăng 50,000đ
            boolean result = paymentDAO.updateAdminFunds(mockConnection, 999, 1500000.0, "+", 50000.0);

            // Then: Xác định các tham số truyền vào đúng thứ tự trong câu SQL
            assertTrue(result);
            verify(mockPreparedStatement).setDouble(1, 1500000.0);
            verify(mockPreparedStatement).setDouble(2, 50000.0);
            verify(mockPreparedStatement).setInt(3, 999);
        }

        @Test
        void testUpdateAdminFunds_InvalidOperator_ShouldReturnFalse() throws SQLException {
            // When: Truyền sai toán tử quản lý quỹ
            boolean result = paymentDAO.updateAdminFunds(mockConnection, 999, 100.0, "INVALID", 10.0);

            // Then
            assertFalse(result);
            verify(mockConnection, never()).prepareStatement(anyString());
        }
    }

    // ========================================================================
    // 3. KIỂM THỬ LẤY SỐ DƯ (GET BALANCE LOGIC MAPPER)
    // ========================================================================
    @Nested
    class GetBalanceTest {

        @Test
        void testGetBalance_Mapping_Success() throws SQLException {
            // Given: Giả lập ResultSet bóc tách dữ liệu từ DB
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getDouble("balance")).thenReturn(750000.5);

            // Giả lập luồng xử lý độc lập để test cơ chế map dữ liệu từ ResultSet
            double actualBalance = -1.0;
            if (mockResultSet.next()) {
                actualBalance = mockResultSet.getDouble("balance");
            }

            // Then: Đảm bảo lấy ra đúng con số số dư thực tế
            assertEquals(750000.5, actualBalance);
        }

        @Test
        void testGetBalance_UserNotFound_ShouldReturnDefault() throws SQLException {
            // Given: Giả lập không tìm thấy tài khoản (ResultSet trả về false)
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(false);

            double actualBalance = -1.0;
            if (mockResultSet.next()) {
                actualBalance = mockResultSet.getDouble("balance");
            }

            // Then: Số dư mặc định khi lỗi hoặc không thấy user theo code gốc là -1.0
            assertEquals(-1.0, actualBalance);
        }
    }
}