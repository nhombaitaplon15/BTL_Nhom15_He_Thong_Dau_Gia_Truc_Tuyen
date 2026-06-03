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
        Field field = UserService.class.getDeclaredField("userDAO");
        field.setAccessible(true);
        field.set(userService, userDAO);
    }

    private User makeUser(int id, String username, String password, String email, String phone, String status, String role, double balance) {
        return new User(id, username, email, password, phone, status, role, balance) {};
    }

    @Nested @DisplayName("1. handleRegister")
    class RegisterTests {
        @Test @DisplayName("Đăng ký thành công")
        void register_success() {
            when(userDAO.isFieldExists(anyString(), anyString())).thenReturn(false);
            when(userDAO.register(any())).thenReturn(true);
            assertTrue(userService.handleRegister("newuser", "pass12345678", "test@gmail.com", "0901234567"));
        }
    }

    @Nested @DisplayName("2. handleLogin")
    class LoginTests {
        @Test @DisplayName("Đăng nhập thành công và tự reset SELLER về BIDDER")
        void login_autoResetSellerToBidder() {
            User seller = makeUser(1, "seller1", "pass1234", "s@g.com", "0900000000", "ACTIVE", "SELLER", 0);
            when(userDAO.checkLogin("seller1", "pass1234")).thenReturn(seller);
            when(userDAO.updateRole(1, "BIDDER")).thenReturn(true);

            User result = userService.handleLogin("seller1", "pass1234");

            assertEquals("BIDDER", result.getRole());
            verify(userDAO).updateRole(1, "BIDDER");
        }
    }

    @Nested @DisplayName("3. handleChangePassword")
    class ChangePasswordTests {
        @Test @DisplayName("Đổi mật khẩu thành công")
        void changePassword_success() {
            User user = makeUser(1, "user1", "oldpass123", "a@a.com", "0901234567", "ACTIVE", "BIDDER", 0);
            when(userDAO.updatePassword("user1", "newpass123")).thenReturn(true);

            userService.handleChangePassword(user, "oldpass123", "newpass123", "newpass123");
            assertEquals("newpass123", user.getPassword());
        }
    }

    @Nested @DisplayName("4. handleSwitchRole")
    class SwitchRoleTests {
        @Test @DisplayName("Chuyển sang SELLER thành công")
        void switchRole_success() {
            User bidder = makeUser(1, "user1", "pass1234", "a@a.com", "0901234567", "ACTIVE", "BIDDER", 0);
            when(userDAO.updateRole(1, "SELLER")).thenReturn(true);

            userService.handleSwitchRole(bidder, "SELLER");
            assertEquals("SELLER", bidder.getRole());
        }
    }

    @Nested @DisplayName("5. Admin & Profile")
    class AdminAndProfileTests {
        @Test @DisplayName("banUser thành công")
        void banUser_success() {
            when(userDAO.updateStatus(1, "LOCKED")).thenReturn(true);
            assertDoesNotThrow(() -> userService.banUser(1));
        }

        @Test @DisplayName("getAllUsers trả về list")
        void getAllUsers_success() {
            when(userDAO.getAllUsers()).thenReturn(Arrays.asList(new User(1, "u", "e", "p", "ph", "A", "B", 0){}));
            assertEquals(1, userService.getAllUsers().size());
        }
    }
}
