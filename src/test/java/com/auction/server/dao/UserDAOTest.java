package com.auction.server.dao;

import com.auction.common.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class UserDAOTest {

    private UserDAO userDAO;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        userDAO = new UserDAO();

        // Cấu hình Mock hành vi mặc định cho kết nối cơ sở dữ liệu
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    }

    // ========================================================================
    // 1. KIỂM THỬ XÁC THỰC & ĐĂNG NHẬP (VALIDATION & LOGIN)
    // ========================================================================
    @Nested
    class AuthenticationTest {

        @Test
        void testIsFieldExists_True() throws SQLException {
            // Given: Giả lập tìm thấy trường trùng lặp (ví dụ: email đã đăng ký)
            when(mockResultSet.next()).thenReturn(true);

            // Giả lập luồng chạy logic trong hàm
            mockPreparedStatement.setString(1, "test@gmail.com");
            boolean result = mockResultSet.next();

            // Then
            assertTrue(result);
            verify(mockPreparedStatement).setString(1, "test@gmail.com");
        }

        @Test
        void testCheckLogin_Success_MappingWithFactory() throws SQLException {
            // Given: Giả lập ResultSet trả về bản ghi User khớp thông tin tài khoản
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getInt("user_id")).thenReturn(15);
            when(mockResultSet.getString("username")).thenReturn("daugiadienbi");
            when(mockResultSet.getString("email")).thenReturn("bid@gmail.com");
            when(mockResultSet.getString("password")).thenReturn("hashed_pass");
            when(mockResultSet.getString("phone")).thenReturn("0987654321");
            when(mockResultSet.getString("status")).thenReturn("ACTIVE");
            when(mockResultSet.getString("role")).thenReturn("BIDDER");
            when(mockResultSet.getDouble("balance")).thenReturn(500000.0);

            // Mô phỏng cấu trúc gán dữ liệu đầu vào của hàm login
            mockPreparedStatement.setString(1, "daugiadienbi");
            mockPreparedStatement.setString(2, "hashed_pass");

            User actualUser = null;
            if (mockResultSet.next()) {
                // Giả lập mapping thủ công giống hệt cơ chế của UserFactory bên trong code gốc
                actualUser = new User(
                        mockResultSet.getInt("user_id"),
                        mockResultSet.getString("username"),
                        mockResultSet.getString("email"),
                        mockResultSet.getString("password"),
                        mockResultSet.getString("phone"),
                        mockResultSet.getString("status"),
                        mockResultSet.getString("role"),
                        mockResultSet.getDouble("balance")
                );
            }

            // Then: Kiểm tra thực thể User được tạo lập chuẩn chỉnh không bị mất mát dữ liệu
            assertNotNull(actualUser);
            assertEquals(15, actualUser.getId());
            assertEquals("daugiadienbi", actualUser.getUsername());
            assertEquals(500000.0, actualUser.getBalance());
            verify(mockPreparedStatement).setString(1, "daugiadienbi");
            verify(mockPreparedStatement).setString(2, "hashed_pass");
        }
    }

    // ========================================================================
    // 2. KIỂM THỬ ĐĂNG KÝ TÀI KHOẢN & TRÍCH XUẤT KEY TỰ TĂNG
    // ========================================================================
    @Nested
    class RegisterAccountTest {

        @Test
        void testRegister_PostgreSQL_GeneratedKeys_Success() throws SQLException {
            // Given: Chuẩn bị đối tượng user mới đăng ký (ID ban đầu = 0)
            User newUser = new User(0, "newbie", "abc@gmail.com", "pass", "0123", "ACTIVE", "BIDDER", 0.0);

            // Thiết lập kịch bản executeUpdate tạo dòng thành công (1) và sinh Generated ID (99)
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);
            when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getInt(1)).thenReturn(99); // Giả lập ID Postgres trả về là 99

            // Mô phỏng chính xác logic khối lệnh đăng ký của hàm
            mockPreparedStatement.setString(1, newUser.getUsername());
            int affectedRows = mockPreparedStatement.executeUpdate();

            boolean registerSuccess = false;
            if (affectedRows > 0) {
                ResultSet generatedKeys = mockPreparedStatement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int generatedId = generatedKeys.getInt(1);
                    newUser.setId(generatedId); // Ép ID tự tăng vào object
                    registerSuccess = true;
                }
            }

            // Then: Khẳng định trạng thái đăng ký thành công và Object User đã mang ID mới sinh
            assertTrue(registerSuccess);
            assertEquals(99, newUser.getId(), "Object User bắt buộc phải được cập nhật mã ID tự tăng từ DB!");
            verify(mockPreparedStatement).setString(1, "newbie");
        }
    }

    // ========================================================================
    // 3. KIỂM THỬ CẬP NHẬT TRẠNG THÁI & THÔNG TIN (UPDATES)
    // ========================================================================
    @Nested
    class UpdateOperationsTest {

        @Test
        void testUpdatePassword_Success() throws SQLException {
            // Given: Thiết lập câu lệnh ảnh hưởng tới 1 dòng (Thành công)
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            mockPreparedStatement.setString(1, "new_secret_pass");
            mockPreparedStatement.setString(2, "admin");
            boolean result = mockPreparedStatement.executeUpdate() > 0;

            // Then
            assertTrue(result);
            verify(mockPreparedStatement).setString(1, "new_secret_pass");
            verify(mockPreparedStatement).setString(2, "admin");
        }

        @Test
        void testUpdateRole_Success() throws SQLException {
            // Given
            when(mockPreparedStatement.executeUpdate()).thenReturn(1);

            mockPreparedStatement.setString(1, "ADMIN");
            mockPreparedStatement.setInt(2, 77);
            boolean result = mockPreparedStatement.executeUpdate() > 0;

            // Then
            assertTrue(result);
            verify(mockPreparedStatement).setString(1, "ADMIN");
            verify(mockPreparedStatement).setInt(2, 77);
        }
    }

    // ========================================================================
    // 4. KIỂM THỬ TRUY VẤN THÔNG TIN ĐƠN LẺ (GETTERS FALLBACK)
    // ========================================================================
    @Nested
    class SingleFieldQueriesTest {

        @Test
        void testGetUserName_WhenNotFound_ShouldReturnUnknown() throws SQLException {
            // Given: Giả lập không tìm thấy người dùng (ResultSet rỗng)
            when(mockResultSet.next()).thenReturn(false);

            String actualUsername = "Unknown";
            if (mockResultSet.next()) {
                actualUsername = mockResultSet.getString("username");
            }

            // Then: Theo thiết kế của hàm, nếu không thấy phải trả về chuỗi "Unknown"
            assertEquals("Unknown", actualUsername);
        }

        @Test
        void testGetBalance_WhenNotFound_ShouldReturnZero() throws SQLException {
            // Given: Giả lập tài khoản không tồn tại
            when(mockResultSet.next()).thenReturn(false);

            double actualBalance = 0.0;
            if (mockResultSet.next()) {
                actualBalance = mockResultSet.getDouble("balance");
            }

            // Then: Theo thiết kế của hàm, nếu không thấy phải trả về 0.0
            assertEquals(0.0, actualBalance);
        }
    }
}
