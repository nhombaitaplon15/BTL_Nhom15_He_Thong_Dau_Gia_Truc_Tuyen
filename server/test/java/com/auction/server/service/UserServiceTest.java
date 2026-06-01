package com.auction.server.service;

import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.UserDAO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserDAO userDAO;

    private UserService userService;

    @BeforeEach
    void setUp() throws Exception {
        userService = new UserService();
        // Dùng Reflection để nhúng mock UserDAO vào UserService
        Field field = UserService.class.getDeclaredField("userDAO");
        field.setAccessible(true);
        field.set(userService, userDAO);
    }

    // Helper: Tạo nhanh đối tượng bằng Lớp nặc danh dựa trên cấu trúc của User.java
    private User makeUser(int id, String username, String password, String email, String phone, String status, String role, double balance) {
        return new User(id, username, email, password, phone, status, role, balance) {};
    }

    @Nested @DisplayName("1. handleRegister")
    class RegisterTests {

        @Test @DisplayName("Đăng ký thành công với dữ liệu hợp lệ")
        void register_success() {
            when(userDAO.isFieldExists("username", "newuser")).thenReturn(false);
            when(userDAO.isFieldExists("email","123@gmail.com")).thenReturn(false);
            when(userDAO.isFieldExists("phone", "0901234567")).thenReturn(false);
            when(userDAO.register(any())).thenReturn(true);

            assertTrue(userService.handleRegister("newuser", "pass1234", "123@gmail.com", "0901234567"));
        }

        @Test @DisplayName("Ném lỗi khi SĐT không đủ 10 số — không gọi DAO")
        void register_invalidPhone() {
            assertThrows(AuctionException.class, () ->
                userService.handleRegister("user1", "pass1234", "123@gmail.com","090123"));
            verifyNoInteractions(userDAO);
        }

        @Test @DisplayName("Ném lỗi khi mật khẩu dưới 8 ký tự — không gọi DAO")
        void register_shortPassword() {
            assertThrows(AuctionException.class, () ->
                userService.handleRegister("user1", "abc", "123@gmail.com","0901234567"));
            verifyNoInteractions(userDAO);
        }

        @Test @DisplayName("Ném lỗi khi sai định dạng Email — không gọi DAO")
        void register_invalidEmailFormat() {
            assertThrows(AuctionException.class, () ->
                userService.handleRegister("user1", "pass1234", "sai_email.com","0901234567"));
            verifyNoInteractions(userDAO);
        }

        @Test @DisplayName("Ném lỗi khi username đã tồn tại")
        void register_duplicateUsername() {
            when(userDAO.isFieldExists("username", "existed")).thenReturn(true);
            assertThrows(AuctionException.class, () ->
                userService.handleRegister("existed", "pass1234", "123@gmail.com", "0901234567"));
        }

        @Test @DisplayName("Ném lỗi khi email đã tồn tại")
        void register_duplicateEmail() {
            when(userDAO.isFieldExists("username", "newuser")).thenReturn(false);
            when(userDAO.isFieldExists("email", "dup@gmail.com")).thenReturn(true);

            AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("newuser", "pass1234", "dup@gmail.com", "0901234567"));
            assertTrue(ex.getMessage().contains("Email này đã được sử dụng"));
        }

        @Test @DisplayName("Ném lỗi khi SĐT đã đăng ký")
        void register_duplicatePhone() {
            when(userDAO.isFieldExists("username", "newuser")).thenReturn(false);
            when(userDAO.isFieldExists("email", "new@gmail.com")).thenReturn(false);
            when(userDAO.isFieldExists("phone", "0901234567")).thenReturn(true);

            AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("newuser", "pass1234", "new@gmail.com", "0901234567"));
            assertTrue(ex.getMessage().contains("Số điện thoại này đã được đăng ký"));
        }

        @Test @DisplayName("Ném lỗi khi DAO register thất bại")
        void register_daoFails() {
            when(userDAO.isFieldExists(anyString(), anyString())).thenReturn(false);
            when(userDAO.register(any())).thenReturn(false);
            assertThrows(AuctionException.class, () ->
                userService.handleRegister("newuser", "pass1234", "12@gmail.com", "0901234567"));
        }
    }

    @Nested @DisplayName("2. handleLogin")
    class LoginTests {

        @Test @DisplayName("Đăng nhập thành công")
        void login_success() {
            User stored = makeUser(1, "user1", "pass1234", "123@gmail.com", "0901234567", "ACTIVE", "BIDDER", 500_000);
            when(userDAO.checkLogin("user1", "pass1234")).thenReturn(stored);

            User result = userService.handleLogin("user1", "pass1234");
            assertEquals("user1", result.getUsername());
        }

        @Test @DisplayName("Ném lỗi khi sai username hoặc password")
        void login_wrongCredentials() {
            when(userDAO.checkLogin("user1", "wrongpass")).thenReturn(null);
            assertThrows(AuctionException.class, () -> userService.handleLogin("user1", "wrongpass"));
        }

        @Test @DisplayName("Ném lỗi khi tài khoản bị LOCKED")
        void login_lockedAccount() {
            User locked = makeUser(1, "user1", "pass1234", "123@gmail.com", "0901234567", "LOCKED", "BIDDER", 0);
            when(userDAO.checkLogin("user1", "pass1234")).thenReturn(locked);
            assertThrows(AuctionException.class, () -> userService.handleLogin("user1", "pass1234"));
        }
    }

    @Nested @DisplayName("3. handleChangePassword")
    class ChangePasswordTests {
        private User user;

        @BeforeEach
        void setUpUser() {
            user = makeUser(1, "user1", "oldpass1", "123@gmail.com", "0901234567", "ACTIVE", "BIDDER", 0);
        }

        @Test @DisplayName("Đổi mật khẩu thành công — RAM được cập nhật")
        void changePassword_success() {
            when(userDAO.updatePassword("user1", "newpass1")).thenReturn(true);
            userService.handleChangePassword(user, "oldpass1", "newpass1", "newpass1");
            assertEquals("newpass1", user.getPassword());
        }

        @Test @DisplayName("Ném lỗi khi mật khẩu cũ sai")
        void changePassword_wrongOld() {
            assertThrows(AuctionException.class, () -> userService.handleChangePassword(user, "wrongold", "newpass1", "newpass1"));
            verifyNoInteractions(userDAO);
        }
    }

    @Nested @DisplayName("4. handleSwitchRole")
    class SwitchRoleTests {

        @Test @DisplayName("Bidder → Seller thành công, RAM cập nhật")
        void switchRole_bidderToSeller() {
            User bidder = makeUser(1, "user1", "pass1234", "1@gmail.com", "0901234567", "ACTIVE", "BIDDER", 0);
            when(userDAO.updateRole(1, "SELLER")).thenReturn(true);
            userService.handleSwitchRole(bidder);
            assertEquals("SELLER", bidder.getRole());
        }

        @Test @DisplayName("Admin không được đổi vai trò")
        void switchRole_adminForbidden() {
            User admin = makeUser(1, "admin", "pass1234", "1@gmail.com", "0901234567", "ACTIVE", "ADMIN", 0);
            assertThrows(AuctionException.class, () -> userService.handleSwitchRole(admin));
            verifyNoInteractions(userDAO);
        }
    }

    @Nested @DisplayName("5. Tính năng Admin & Profile (Được bổ sung)")
    class AdminAndProfileTests {

        @Test @DisplayName("updateProfile - Thành công")
        void updateProfile_success() {
            User current = makeUser(1, "user1", "pass", "old@gmail.com", "0901111111", "ACTIVE", "BIDDER", 0);
            User updated = makeUser(1, "user1", "pass", "new@gmail.com", "0902222222", "ACTIVE", "BIDDER", 0);

            when(userDAO.getUserById(1)).thenReturn(current);
            when(userDAO.updateProfile(current)).thenReturn(true);

            assertDoesNotThrow(() -> userService.updateProfile(updated));
            assertEquals("new@gmail.com", current.getEmail());
            assertEquals("0902222222", current.getPhone());
        }

        @Test @DisplayName("updateProfile - Lỗi không tìm thấy User")
        void updateProfile_userNotFound() {
            User updated = makeUser(99, "user99", "pass", "new@gmail.com", "0902222222", "ACTIVE", "BIDDER", 0);
            when(userDAO.getUserById(99)).thenReturn(null);

            assertThrows(AuctionException.class, () -> userService.updateProfile(updated));
        }

        @Test @DisplayName("getAllUsers - Trả về danh sách")
        void getAllUsers_success() {
            List<User> mockList = Arrays.asList(
                makeUser(1, "user1", "", "", "", "", "", 0),
                makeUser(2, "user2", "", "", "", "", "", 0)
            );
            when(userDAO.getAllUsers()).thenReturn(mockList);

            List<User> result = userService.getAllUsers();
            assertEquals(2, result.size());
        }

        @Test @DisplayName("banUser - Khóa tài khoản thành công")
        void banUser_success() {
            when(userDAO.updateStatus(1, "LOCKED")).thenReturn(true);
            assertDoesNotThrow(() -> userService.banUser(1));
        }

        @Test @DisplayName("unbanUser - Mở khóa tài khoản thành công")
        void unbanUser_success() {
            when(userDAO.updateStatus(1, "ACTIVE")).thenReturn(true);
            assertDoesNotThrow(() -> userService.unbanUser(1));
        }
    }
}