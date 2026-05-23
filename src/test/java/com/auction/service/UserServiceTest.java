package com.auction.service;

import com.auction.common.model.Bidder;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.server.dao.UserDAO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

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

    // Tạo nhanh đối tượng người dùng để test
    private User makeBidder(int id, String username, String password, String phone,
                            String status, double balance) {
        return new Bidder(id, username, username + "@mail.com", password, phone, status, balance);
    }

    @Nested @DisplayName("handleRegister")
    class RegisterTests {



        // Test: Số điện thoại thiếu chữ số phải bị loại ngay từ tầng validate
        @Test @DisplayName("Ném lỗi khi SĐT không đủ 10 số — không gọi DAO")
        void register_invalidPhone() {
            User req = makeBidder(0, "user1", "pass1234", "090123", "ACTIVE", 0);
            assertThrows(AuctionException.class, () -> userService.handleRegister(req));
            verifyNoInteractions(userDAO);
        }

        // Test: Mật khẩu quá ngắn phải bị loại ngay từ tầng validate
        @Test @DisplayName("Ném lỗi khi mật khẩu dưới 8 ký tự — không gọi DAO")
        void register_shortPassword() {
            User req = makeBidder(0, "user1", "abc", "0901234567", "ACTIVE", 0);
            assertThrows(AuctionException.class, () -> userService.handleRegister(req));
            verifyNoInteractions(userDAO);
        }

        // Test: Hệ thống chặn đăng ký khi tên tài khoản đã tồn tại
        @Test @DisplayName("Ném lỗi khi username đã tồn tại")
        void register_duplicateUsername() {
            User req = makeBidder(0, "existed", "pass1234", "0901234567", "ACTIVE", 0);
            when(userDAO.isFieldExists("username", "existed")).thenReturn(true);
            assertThrows(AuctionException.class, () -> userService.handleRegister(req));
        }

        // Test: Hệ thống chặn đăng ký khi email trùng lặp
        @Test @DisplayName("Ném lỗi khi email đã tồn tại")
        void register_duplicateEmail() {
            User req = makeBidder(0, "newuser", "pass1234", "0901234567", "ACTIVE", 0);
            req.setEmail("duplicate@gmail.com");

            when(userDAO.isFieldExists("username", "newuser")).thenReturn(false);
            when(userDAO.isFieldExists("email", "duplicate@gmail.com")).thenReturn(true);

            AuctionException ex = assertThrows(AuctionException.class, () -> userService.handleRegister(req));
            assertTrue(ex.getMessage().contains("Email đã được sử dụng"));
        }

        // Test: Hệ thống chặn đăng ký khi số điện thoại đã được dùng
        @Test @DisplayName("Ném lỗi khi SĐT đã đăng ký")
        void register_duplicatePhone() {
            User req = makeBidder(0, "newuser", "pass1234", "0901234567", "ACTIVE", 0);
            req.setEmail("newuser@gmail.com");

            when(userDAO.isFieldExists("username", "newuser")).thenReturn(false);
            when(userDAO.isFieldExists("email", "newuser@gmail.com")).thenReturn(false);
            when(userDAO.isFieldExists("phone", "0901234567")).thenReturn(true);

            AuctionException ex = assertThrows(AuctionException.class, () -> userService.handleRegister(req));
            assertTrue(ex.getMessage().contains("Số điện thoại đã đăng ký"));
        }

        // Test: Ném lỗi hệ thống nếu quá trình ghi nhận vào DB thất bại
        @Test @DisplayName("Ném lỗi khi DAO register thất bại")
        void register_daoFails() {
            User req = makeBidder(0, "newuser", "pass1234", "0901234567", "ACTIVE", 0);
            when(userDAO.isFieldExists(anyString(), anyString())).thenReturn(false);
            when(userDAO.register(any())).thenReturn(false);
            assertThrows(AuctionException.class, () -> userService.handleRegister(req));
        }

        // Test: Số điện thoại thừa chữ số phải bị chặn từ tầng validate
        @Test @DisplayName("SĐT phải đúng 10 chữ số — 11 số thì fail")
        void register_phoneWith11Digits() {
            User req = makeBidder(0, "user1", "pass1234", "09012345678", "ACTIVE", 0);
            assertThrows(AuctionException.class, () -> userService.handleRegister(req));
            verifyNoInteractions(userDAO);
        }
    }

    @Nested @DisplayName("handleLogin")
    class LoginTests {

        // Test: Đăng nhập thành công khi thông tin tài khoản chính xác
        @Test @DisplayName("Đăng nhập thành công")
        void login_success() {
            User stored = makeBidder(1, "user1", "pass1234", "0901234567", "ACTIVE", 500_000);
            User cred   = makeBidder(0, "user1", "pass1234", "0901234567", "ACTIVE", 0);
            when(userDAO.checkLogin("user1", "pass1234")).thenReturn(stored);

            User result = userService.handleLogin(cred);
            assertEquals("user1", result.getUsername());
        }

        // Test: Từ chối đăng nhập khi sai tài khoản hoặc mật khẩu
        @Test @DisplayName("Ném lỗi khi sai username hoặc password")
        void login_wrongCredentials() {
            User cred = makeBidder(0, "user1", "wrongpass", "0901234567", "ACTIVE", 0);
            when(userDAO.checkLogin("user1", "wrongpass")).thenReturn(null);
            assertThrows(AuctionException.class, () -> userService.handleLogin(cred));
        }

        // Test: Không cho phép đăng nhập nếu tài khoản đang bị khóa
        @Test @DisplayName("Ném lỗi khi tài khoản bị LOCKED")
        void login_lockedAccount() {
            User locked = makeBidder(1, "user1", "pass1234", "0901234567", "LOCKED", 0);
            User cred   = makeBidder(0, "user1", "pass1234", "0901234567", "ACTIVE", 0);
            when(userDAO.checkLogin("user1", "pass1234")).thenReturn(locked);
            assertThrows(AuctionException.class, () -> userService.handleLogin(cred));
        }
    }

    @Nested @DisplayName("handleChangePassword")
    class ChangePasswordTests {

        private User user;

        @BeforeEach
        void setUpUser() {
            user = makeBidder(1, "user1", "oldpass1", "0901234567", "ACTIVE", 0);
        }

        // Test: Đổi mật khẩu thành công và cập nhật lại thông tin trong bộ nhớ
        @Test @DisplayName("Đổi mật khẩu thành công — RAM được cập nhật")
        void changePassword_success() {
            when(userDAO.updatePassword("user1", "newpass1")).thenReturn(true);
            userService.handleChangePassword(user, "oldpass1", "newpass1", "newpass1");
            assertEquals("newpass1", user.getPassword());
        }

        // Test: Chặn đổi mật khẩu nếu nhập sai mật khẩu hiện tại
        @Test @DisplayName("Ném lỗi khi mật khẩu cũ sai — không gọi DAO")
        void changePassword_wrongOld() {
            assertThrows(AuctionException.class,
                    () -> userService.handleChangePassword(user, "wrongold", "newpass1", "newpass1"));
            verifyNoInteractions(userDAO);
        }

        // Test: Chặn đổi mật khẩu nếu mật khẩu mới trùng mật khẩu cũ
        @Test @DisplayName("Ném lỗi khi mật khẩu mới giống cũ — không gọi DAO")
        void changePassword_sameAsOld() {
            assertThrows(AuctionException.class,
                    () -> userService.handleChangePassword(user, "oldpass1", "oldpass1", "oldpass1"));
            verifyNoInteractions(userDAO);
        }

        // Test: Mật khẩu mới không đạt độ dài yêu cầu sẽ bị chặn ngay
        @Test @DisplayName("Ném lỗi khi mật khẩu mới dưới 8 ký tự — không gọi DAO")
        void changePassword_tooShort() {
            assertThrows(AuctionException.class,
                    () -> userService.handleChangePassword(user, "oldpass1", "abc", "abc"));
            verifyNoInteractions(userDAO);
        }

        // Test: Chặn đổi mật khẩu nếu hai lần nhập mật khẩu mới không khớp
        @Test @DisplayName("Ném lỗi khi xác nhận mật khẩu không khớp — không gọi DAO")
        void changePassword_confirmMismatch() {
            assertThrows(AuctionException.class,
                    () -> userService.handleChangePassword(user, "oldpass1", "newpass1", "different1"));
            verifyNoInteractions(userDAO);
        }

        // Test: Ném lỗi nếu DB cập nhật mật khẩu thất bại
        @Test @DisplayName("Ném lỗi khi DAO updatePassword thất bại")
        void changePassword_daoFails() {
            when(userDAO.updatePassword("user1", "newpass1")).thenReturn(false);
            assertThrows(AuctionException.class,
                    () -> userService.handleChangePassword(user, "oldpass1", "newpass1", "newpass1"));
        }
    }

    @Nested @DisplayName("handleForgotPassword")
    class ForgotPasswordTests {

        // Test: Khôi phục mật khẩu thành công khi tài khoản tồn tại
        @Test @DisplayName("Reset mật khẩu thành công")
        void forgotPassword_success() {
            when(userDAO.isFieldExists("username", "user1")).thenReturn(true);
            when(userDAO.updatePassword("user1", "newpass1")).thenReturn(true);
            assertDoesNotThrow(() ->
                    userService.handleForgotPassword("user1", "0901234567", "newpass1"));
        }

        // Test: Chặn khôi phục mật khẩu nếu tài khoản không tồn tại
        @Test @DisplayName("Ném lỗi khi username không tồn tại")
        void forgotPassword_userNotFound() {
            when(userDAO.isFieldExists("username", "ghost")).thenReturn(false);
            assertThrows(AuctionException.class,
                    () -> userService.handleForgotPassword("ghost", "0901234567", "newpass1"));
            verify(userDAO, never()).updatePassword(anyString(), anyString());
        }

        // Test: Ném lỗi nếu DB cập nhật mật khẩu khôi phục thất bại
        @Test @DisplayName("Ném lỗi khi DAO updatePassword thất bại")
        void forgotPassword_daoFails() {
            when(userDAO.isFieldExists("username", "user1")).thenReturn(true);
            when(userDAO.updatePassword("user1", "newpass1")).thenReturn(false);
            assertThrows(AuctionException.class,
                    () -> userService.handleForgotPassword("user1", "0901234567", "newpass1"));
        }
    }

    @Nested @DisplayName("handleSwitchRole")
    class SwitchRoleTests {

        // Test: Chuyển đổi vai trò từ Bidder sang Seller thành công
        @Test @DisplayName("Bidder → Seller thành công, RAM cập nhật")
        void switchRole_bidderToSeller() {
            User bidder = makeBidder(1, "user1", "pass1234", "0901234567", "ACTIVE", 0);
            when(userDAO.updateRole(1, "SELLER")).thenReturn(true);

            userService.handleSwitchRole(bidder);
            assertEquals("SELLER", bidder.getRole());
        }

        // Test: Chuyển đổi vai trò từ Seller về lại Bidder thành công
        @Test @DisplayName("Seller → Bidder thành công, RAM cập nhật")
        void switchRole_sellerToBidder() {
            User seller = makeBidder(1, "user1", "pass1234", "0901234567", "ACTIVE", 0);
            seller.setRole("SELLER");
            when(userDAO.updateRole(1, "BIDDER")).thenReturn(true);

            userService.handleSwitchRole(seller);
            assertEquals("BIDDER", seller.getRole());
        }

        // Test: Tài khoản quản trị viên (Admin) không được phép tự chuyển đổi vai trò
        @Test @DisplayName("Admin không được đổi vai trò — không gọi DAO")
        void switchRole_adminForbidden() {
            User admin = makeBidder(1, "admin", "pass1234", "0901234567", "ACTIVE", 0);
            admin.setRole("ADMIN");

            assertThrows(AuctionException.class, () -> userService.handleSwitchRole(admin));
            verifyNoInteractions(userDAO);
        }

        // Test: Ném lỗi nếu DB cập nhật vai trò mới thất bại
        @Test @DisplayName("Ném lỗi khi DAO updateRole thất bại")
        void switchRole_daoFails() {
            User bidder = makeBidder(1, "user1", "pass1234", "0901234567", "ACTIVE", 0);
            when(userDAO.updateRole(1, "SELLER")).thenReturn(false);
            assertThrows(AuctionException.class, () -> userService.handleSwitchRole(bidder));
        }
    }

    @Nested @DisplayName("getUserById")
    class GetUserByIdTests {

        // Test: Tìm kiếm thông tin người dùng thành công dựa trên ID
        @Test @DisplayName("Tìm thấy user theo ID")
        void getUserById_found() {
            User stored = makeBidder(5, "user5", "pass1234", "0901234567", "ACTIVE", 100_000);
            when(userDAO.getUserById(5)).thenReturn(stored);

            User result = userService.getUserById(5);
            assertNotNull(result);
            assertEquals(5, result.getId());
        }

        // Test: Trả về null khi tìm kiếm với ID không tồn tại
        @Test @DisplayName("Trả về null khi không tìm thấy — không throw exception")
        void getUserById_notFound_returnsNull() {
            when(userDAO.getUserById(99)).thenReturn(null);
            assertNull(userService.getUserById(99));
        }
    }
}