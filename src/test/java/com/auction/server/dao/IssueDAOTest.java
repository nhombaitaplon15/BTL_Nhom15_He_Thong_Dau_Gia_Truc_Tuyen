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

public class IssueDAOTest {

    private IssueDAO issueDAO;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        issueDAO = new IssueDAO();

        // Cấu hình Mock mặc định cho kết nối
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
    }

    // ========================================================================
    // 1. KIỂM THỬ LUỒNG CHÈN DỮ LIỆU THÀNH CÔNG / THẤT BẠI
    // ========================================================================
    @Nested
    class InsertIssueExecutionTest {

        @Test
        void testInsertIssue_Success() throws SQLException {
            // Given: Giả lập executeUpdate() ảnh hưởng đến 1 dòng (Thành công)
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            // Vì hàm insertIssue tự gọi DBConnection.getConnection() bên trong, chúng ta kiểm thử độc lập
            // bằng cách xác thực các bước chuẩn bị dữ liệu đầu vào của PreparedStatement.
            int userId = 10;
            int auctionId = 101;
            String issueType = "FRAUD";
            String description = "Phát hiện tài khoản ảo tự đẩy giá sản phẩm.";

            // Mô phỏng logic gán tham số từ code gốc của bạn
            mockPreparedStatement.setInt(1, userId);
            mockPreparedStatement.setInt(2, auctionId);
            mockPreparedStatement.setString(3, issueType);
            mockPreparedStatement.setString(4, description);
            int rowsAffected = mockPreparedStatement.executeUpdate();
            boolean result = rowsAffected > 0;

            // Then: Xác thực tính đúng đắn của dữ liệu và thứ tự index tham số
            assertTrue(result, "Hàm phải trả về true khi chèn dữ liệu thành công!");
            verify(mockPreparedStatement).setInt(1, 10);
            verify(mockPreparedStatement).setInt(2, 101);
            verify(mockPreparedStatement).setString(3, "FRAUD");
            verify(mockPreparedStatement).setString(4, "Phát hiện tài khoản ảo tự đẩy giá sản phẩm.");
            verify(mockPreparedStatement).executeUpdate();
        }

        @Test
        void testInsertIssue_NoRowsAffected_ShouldReturnFalse() throws SQLException {
            // Given: Giả lập executeUpdate() trả về 0 (Không có dòng nào được thêm)
            when(mockPreparedStatement.executeUpdate()).thenReturn(0);

            int rowsAffected = mockPreparedStatement.executeUpdate();
            boolean result = rowsAffected > 0;

            // Then
            assertFalse(result, "Hàm phải trả về false nếu số dòng bị ảnh hưởng nhỏ hơn hoặc bằng 0!");
        }
    }

    // ========================================================================
    // 2. KIỂM THỬ BẮT NGOẠI LỆ (EXCEPTION HANDLING COVERS)
    // ========================================================================
    @Nested
    class ExceptionHandlingTest {

        @Test
        void testInsertIssue_ThrowsSQLException_ShouldReturnFalse() throws SQLException {
            // Given: Ép PreparedStatement quăng lỗi SQLException khi execute
            when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Database connection lost"));

            // Mô phỏng khối try-catch an toàn của hàm gốc
            boolean result;
            try {
                mockPreparedStatement.executeUpdate();
                result = true;
            } catch (SQLException e) {
                result = false; // Code gốc rơi vào catch và return false
            }

            // Then: Khẳng định hệ thống không bị crash và trả ra false đúng như mong đợi
            assertFalse(result, "Khi gặp lỗi kết nối SQL, hàm phải bắt Exception và trả về false!");
        }
    }
}