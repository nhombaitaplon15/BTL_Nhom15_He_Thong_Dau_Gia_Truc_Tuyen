package com.auction.service;

import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.UserDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserDAO userDAO;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        // Khởi tạo một User mẫu để dùng chung cho các testcase login, change password...
        sampleUser = new User(1);
        sampleUser.setId(1);
        sampleUser.setUsername("testuser");
        sampleUser.setPassword("oldPassword123");
        sampleUser.setEmail("test@gmail.com");
        sampleUser.setPhone("0987654321");
        sampleUser.setStatus("ACTIVE");
        sampleUser.setRole("BIDDER");
    }

    // ==========================================
    // 1. TEST CHỨC NĂNG ĐĂNG KÝ (handleRegister)
    // ==========================================
    @Nested
    class RegisterTest {

        @Test
        void register_Success() {
            // Given
            String u = "newuser";
            String p = "password123";
            String e = "new@gmail.com";
            String ph = "0123456789";

            when(userDAO.isFieldExists("username", u)).thenReturn(false);
            when(userDAO.isFieldExists("email", e)).thenReturn(false);
            when(userDAO.isFieldExists("phone", ph)).thenReturn(false);
            when(userDAO.register(any(User.class))).thenReturn(true);

            // When
            boolean result = userService.handleRegister(u, p, e, ph);

            // Then
            assertTrue(result);
            verify(userDAO, times(1)).register(any(User.class));
        }

        @Test
        void register_Fail_InvalidPhone() {
            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleRegister("user", "pass1234", "a@gmail.com", "12345"); // SĐT ngắn quá
            });
            assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
            assertTrue(ex.getMessage().contains("Số điện thoại phải có đúng 10 chữ số"));
        }

        @Test
        void register_Fail_InvalidEmailExtension() {
            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleRegister("user", "pass1234", "a@gmail.xyz", "0123456789"); // Đuôi .xyz không hợp lệ
            });
            assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
        }

        @Test
        void register_Fail_DuplicateUsername() {
            when(userDAO.isFieldExists("username", "duplicate")).thenReturn(true);

            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleRegister("duplicate", "pass1234", "a@gmail.com", "0123456789");
            });
            assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
            assertEquals("Tên đăng nhập đã tồn tại!", ex.getMessage());
        }

        @Test
        void register_Fail_DatabaseError() {
            when(userDAO.isFieldExists(anyString(), anyString())).thenReturn(false);
            when(userDAO.register(any(User.class))).thenReturn(false); // DB trả về false không lưu được

            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleRegister("user", "pass1234", "a@gmail.com", "0123456789");
            });
            assertEquals(ErrorCode.INTERNAL_ERROR.name(), ex.getCode());
            assertTrue(ex.getMessage().contains("Không thể lưu tài khoản"));
        }
    }

    // ==========================================
    // 2. TEST CHỨC NĂNG ĐĂNG NHẬP (handleLogin)
    // ==========================================
    @Nested
    class LoginTest {

        @Test
        void login_Success() {
            when(userDAO.checkLogin("testuser", "oldPassword123")).thenReturn(sampleUser);

            User result = userService.handleLogin("testuser", "oldPassword123");

            assertNotNull(result);
            assertEquals("testuser", result.getUsername());
        }

        @Test
        void login_Fail_WrongCredentials() {
            when(userDAO.checkLogin("wrong", "pass")).thenReturn(null);

            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleLogin("wrong", "pass");
            });
            assertEquals(ErrorCode.USER_NOT_FOUND.name(), ex.getCode());
        }

        @Test
        void login_Fail_AccountLocked() {
            sampleUser.setStatus("LOCKED");
            when(userDAO.checkLogin("testuser", "oldPassword123")).thenReturn(sampleUser);

            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleLogin("testuser", "oldPassword123");
            });
            assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
            assertTrue(ex.getMessage().contains("bị khóa"));
        }
    }

    // ==========================================
    // 3. TEST ĐỔI MẬT KHẨU (handleChangePassword)
    // ==========================================
    @Nested
    class ChangePasswordTest {

        @Test
        void changePassword_Success() {
            when(userDAO.updatePassword("testuser", "newPassword123")).thenReturn(true);

            userService.handleChangePassword(sampleUser, "oldPassword123", "newPassword123", "newPassword123");

            assertEquals("newPassword123", sampleUser.getPassword()); // Kiểm tra RAM cập nhật chưa
        }

        @Test
        void changePassword_Fail_WrongOldPassword() {
            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleChangePassword(sampleUser, "wrongOld", "newPass123", "newPass123");
            });
            assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
        }

        @Test
        void changePassword_Fail_SameAsOld() {
            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleChangePassword(sampleUser, "oldPassword123", "oldPassword123", "oldPassword123");
            });
            assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
        }

        @Test
        void changePassword_Fail_ConfirmNotMatch() {
            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleChangePassword(sampleUser, "oldPassword123", "newPass123", "differentConfirm");
            });
            assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
        }
    }

    // ==========================================
    // 4. TEST QUÊN MẬT KHẨU (handleForgotPassword)
    // ==========================================
    @Nested
    class ForgotPasswordTest {

        @Test
        void forgotPassword_Success() {
            when(userDAO.isFieldExists("username", "testuser")).thenReturn(true);
            when(userDAO.updatePassword("testuser", "resetPass123")).thenReturn(true);

            assertDoesNotThrow(() -> {
                userService.handleForgotPassword("testuser", "0987654321", "resetPass123");
            });
        }

        @Test
        void forgotPassword_Fail_UserNotFound() {
            when(userDAO.isFieldExists("username", "unknown")).thenReturn(false);

            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleForgotPassword("unknown", "0987654321", "resetPass123");
            });
            assertEquals(ErrorCode.USER_NOT_FOUND.name(), ex.getCode());
        }
    }

    // ==========================================
    // 5. TEST LẤY USER THEO ID (getUserById)
    // ==========================================
    @Nested
    class GetUserByIdTest {

        @Test
        void getUserById_Success() {
            when(userDAO.getUserById(1)).thenReturn(sampleUser);

            User result = userService.getUserById(1);

            assertNotNull(result);
            assertEquals(1, result.getId());
        }

        @Test
        void getUserById_NotFound_ReturnsNull() {
            when(userDAO.getUserById(99)).thenReturn(null);

            User result = userService.getUserById(99);

            assertNull(result); // Trả về null theo thiết kế của code gốc
        }
    }

    // ==========================================
    // 6. TEST CHUYỂN ĐỔI VAI TRÒ (handleSwitchRole)
    // ==========================================
    @Nested
    class SwitchRoleTest {

        @Test
        void switchRole_BidderToSeller_Success() {
            sampleUser.setRole("BIDDER");
            when(userDAO.updateRole(1, "SELLER")).thenReturn(true);

            userService.handleSwitchRole(sampleUser);

            assertEquals("SELLER", sampleUser.getRole());
        }

        @Test
        void switchRole_Fail_IfAdmin() {
            // Giả lập user là admin bằng cách override hoặc mock hàm isAdmin()
            // Ở đây nếu class User có thuộc tính role="ADMIN" và hàm isAdmin() trả về true dựa vào role:
            User adminUser = new User(1);
            adminUser.setRole("ADMIN");
            // Lưu ý: Hãy đảm bảo logic hàm isAdmin() trong class User của bạn trả về true khi role là ADMIN.

            // Một cách an toàn hơn nếu không biết cấu trúc User: mock hành vi (nếu User là interface/class mock được)
            // Tuy nhiên vì đây là Data Model, giả định logic check Admin dựa trên field:
            // Tạm thời tạo spy hoặc gán thẳng dữ liệu để thỏa mãn `currentUser.isAdmin()`

            /* Giả định cấu trúc User của bạn có hàm isAdmin trả về true nếu role là ADMIN: */
            // client code bổ sung nếu cần: adminUser.setAdmin(true);

            // Hãy chắc chắn truyền một user có `isAdmin() == true` vào đây:
            User spyAdmin = spy(new User(1));
            when(spyAdmin.isAdmin()).thenReturn(true);

            AuctionException ex = assertThrows(AuctionException.class, () -> {
                userService.handleSwitchRole(spyAdmin);
            });
            assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
        }
    }
}