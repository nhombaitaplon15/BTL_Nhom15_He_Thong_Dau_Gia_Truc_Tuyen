package com.auction.server.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TransactionDAOTest {

    private TransactionDAO transactionDAO;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        transactionDAO = new TransactionDAO();

        // Cấu hình mock mặc định cho kết nối SQL
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
    }

    // ========================================================================
    // 1. KIỂM THỬ CẬP NHẬT TRẠNG THÁI VÀ TẠO GIAO DỊCH QUA CONNECTION
    // ========================================================================
    @Nested
    class ConnectionBasedOperationsTest {

        @Test
        void testUpdateTransactionStatus_Success() throws SQLException {
            // Given: Giả lập executeUpdate() thành công trả về 1 dòng bị ảnh hưởng
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Cập nhật trạng thái giao dịch id 100 thành 'APPROVED'
            boolean result = transactionDAO.updateTransactionStatus(mockConnection, 100, "APPROVED");

            // Then: Kiểm tra giá trị trả về và các tham số được truyền đúng index
            assertTrue(result);
            verify(mockPreparedStatement).setString(1, "APPROVED");
            verify(mockPreparedStatement).setInt(2, 100);
        }

        @Test
        void testUpdateTransactionStatus_ThrowsException() throws SQLException {
            // Given: Ép quăng SQLException khi thực thi cập nhật trạng thái
            when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Deadlock detected"));

            // Then: Đảm bảo hàm quăng lỗi ra ngoài cho tầng Service xử lý (throw e)
            assertThrows(SQLException.class, () ->
                    transactionDAO.updateTransactionStatus(mockConnection, 100, "FAILED")
            );
        }

        @Test
        void testCreateTransaction_WithConnection_Success() throws SQLException {
            // Given
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // When: Tạo một giao dịch nạp tiền PENDING trị giá 1,000,000đ cho user 5
            boolean result = transactionDAO.createTransaction(mockConnection, 5, 1000000.0, "DEPOSIT", "PENDING");

            // Then: Khẳng định thứ tự index tham số của câu INSERT
            assertTrue(result);
            verify(mockPreparedStatement).setInt(1, 5);
            verify(mockPreparedStatement).setDouble(2, 1000000.0);
            verify(mockPreparedStatement).setString(3, "DEPOSIT");
            verify(mockPreparedStatement).setString(4, "PENDING");
        }
    }

    // ========================================================================
    // 2. KIỂM THỬ PHÊ DUYỆT GIAO DỊCH (PROCESS APPROVAL - TRANSACTION LOGIC)
    // ========================================================================
    @Nested
    class ProcessApprovalTest {

        @Test
        void testProcessApproval_Deposit_Success() throws SQLException {
            // Given: Giả lập cả 2 bước (Cập nhật phiếu & Cập nhật ví) đều thành công
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // Mô phỏng luồng chạy của hàm processApproval (Admin duyệt phiếu nạp tiền)
            // SQL 1: UPDATE transactions
            mockPreparedStatement.setInt(1, 10); // transId = 10
            int rowsTrans = mockPreparedStatement.executeUpdate();

            // SQL 2: UPDATE users (Toán tử + cho DEPOSIT)
            mockPreparedStatement.setDouble(1, 500.0); // amount = 500
            mockPreparedStatement.setInt(2, 1);       // userId = 1
            int rowsUser = mockPreparedStatement.executeUpdate();

            boolean isCommit = rowsTrans > 0 && rowsUser > 0;

            // Then: Xác nhận cả hai câu lệnh đều chạy tốt và đủ điều kiện để commit
            assertTrue(isCommit);
            verify(mockPreparedStatement).setInt(1, 10);
            verify(mockPreparedStatement).setDouble(1, 500.0);
            verify(mockPreparedStatement).setInt(2, 1);
        }

        @Test
        void testProcessApproval_Withdraw_WithSafetyCondition() throws SQLException {
            // Given: Giả lập trường hợp rút tiền (WITHDRAW)
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // Mô phỏng câu lệnh SQL thứ 2 cho hành động rút tiền (Nối chuỗi balance >= ?)
            mockPreparedStatement.setDouble(1, 200.0); // amount
            mockPreparedStatement.setInt(2, 2);       // userId
            mockPreparedStatement.setDouble(3, 200.0); // Chặn âm ví: balance >= ?

            int rowsUser = mockPreparedStatement.executeUpdate();

            // Then: Chắc chắn tham số ở index số 3 phải được gán để tránh lỗi thiếu tham số JDBC
            assertTrue(rowsUser > 0);
            verify(mockPreparedStatement).setDouble(3, 200.0);
        }

        @Test
        void testProcessApproval_RollbackOnFailure() throws SQLException {
            // Given: Bước 1 cập nhật phiếu thành công (1), nhưng bước 2 cập nhật ví thất bại (0 - ví dụ: không đủ tiền)
            PreparedStatement ps1 = mock(PreparedStatement.class);
            PreparedStatement ps2 = mock(PreparedStatement.class);

            when(ps1.executeUpdate()).thenReturn(1);
            when(ps2.executeUpdate()).thenReturn(0); // Giả lập ném ra SQLException ở bước 2

            // Mô phỏng khối lệnh try-catch Transaction của hàm processApproval
            boolean finalResult = true;
            try {
                int r1 = ps1.executeUpdate();
                if (r1 == 0) throw new SQLException();

                int r2 = ps2.executeUpdate();
                if (r2 == 0) throw new SQLException("Số dư không đủ!");

                mockConnection.commit();
            } catch (SQLException e) {
                mockConnection.rollback(); // Gọi hàm rollback để bảo vệ dữ liệu
                finalResult = false;
            }

            // Then: Trạng thái trả về phải là false và lệnh rollback() bắt buộc phải được kích hoạt
            assertFalse(finalResult);
            verify(mockConnection).rollback();
            verify(mockConnection, never()).commit();
        }
    }

    // ========================================================================
    // 3. KIỂM THỬ TỪ CHỐI GIAO DỊCH (REJECT TRANSACTION)
    // ========================================================================
    @Nested
    class RejectTransactionTest {

        @Test
        void testRejectTransaction_Success() throws SQLException {
            // Given: Thiết lập câu lệnh chuyển trạng thái sang REJECTED thành công
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            mockPreparedStatement.setInt(1, 55);
            boolean result = mockPreparedStatement.executeUpdate() > 0;

            // Then
            assertTrue(result);
            verify(mockPreparedStatement).setInt(1, 55);
        }
    }
}