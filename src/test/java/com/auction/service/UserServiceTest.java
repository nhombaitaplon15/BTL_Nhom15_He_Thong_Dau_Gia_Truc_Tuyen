package com.auction.service;

import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserService - Quản lý người dùng")
public class UserServiceTest {
    private UserService userService;
    @BeforeEach
    void setUp() {
        userService = new UserService();
        userService.clearData();
    }
    //TEST ĐĂNG KÍ
    @Test
    @DisplayName("register | HỢP LỆ | Đăng ký Bidder đủ thông tin → thành công")
    void register_success_bidder() {
        assertDoesNotThrow(() ->
                userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER"));
    }
    @Test
    @DisplayName("register | HỢP LỆ | Đăng ký Seller đủ thông tin → thành công")
    void register_success_seller() {
        assertDoesNotThrow(() ->
                userService.handleRegister("bob", "password123", "bob@mail.com", "0907654321", "SELLER"));
    }
    @Test
    @DisplayName("register | LỖI INVALID_INPUT | Số điện thoại chỉ 9 chữ số → không hợp lệ")
    void register_invalidPhone_tooShort_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("alice", "password123", "alice@mail.com", "090123456", "BIDDER"));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
    @Test
    @DisplayName("register | LỖI INVALID_INPUT | Số điện thoại chứa chữ cái → không hợp lệ")
    void register_invalidPhone_hasLetters_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("alice", "password123", "alice@mail.com", "090abc4567", "BIDDER"));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
    @Test
    @DisplayName("register | LỖI INVALID_INPUT | Mật khẩu chỉ 6 ký tự, dưới 8 → không hợp lệ")
    void register_shortPassword_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("alice", "abc123", "alice@mail.com", "0901234567", "BIDDER"));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
    @Test
    @DisplayName("register | LỖI UNAUTHORIZED | Username đã tồn tại → không cho đăng ký lại")
    void register_duplicateUsername_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("alice", "password456", "alice2@mail.com", "0902345678", "BIDDER"));
        assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
    }
    @Test
    @DisplayName("register | LỖI UNAUTHORIZED | Email đã được dùng bởi tài khoản khác → không cho đăng ký")
    void register_duplicateEmail_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("alice2", "password456", "alice@mail.com", "0902345678", "BIDDER"));
        assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
    }
    @Test
    @DisplayName("register | LỖI UNAUTHORIZED | Số điện thoại đã được dùng bởi tài khoản khác → không cho đăng ký")
    void register_duplicatePhone_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleRegister("alice2", "password456", "alice2@mail.com", "0901234567", "BIDDER"));
        assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
    }
    @Test
    @DisplayName("register | HỢP LỆ | Đăng ký role SELLER → object trả về là instance của Seller")
    void register_roleIsSeller_returnsSellerInstance() {
        userService.handleRegister("bob", "password123", "bob@mail.com", "0907654321", "SELLER");
        User user = userService.handleLogin("bob", "password123");
        assertInstanceOf(Seller.class, user);
    }
    @Test
    @DisplayName("register | HỢP LỆ | Đăng ký role BIDDER → object trả về là instance của Bidder")
    void register_roleIsBidder_returnsBidderInstance() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        User user = userService.handleLogin("alice", "password123");
        assertInstanceOf(Bidder.class, user);
    }
    //TEST ĐĂNG NHẬP
    @Test
    @DisplayName("login | HỢP LỆ | Đúng username và password → trả về User")
    void login_success() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        User user = userService.handleLogin("alice", "password123");
        assertNotNull(user);
        assertEquals("alice", user.getUsername());
    }
    @Test
    @DisplayName("login | LỖI UNAUTHORIZED | Đúng username nhưng sai password → bị chặn")
    void login_wrongPassword_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleLogin("alice", "wrongpass"));
        assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
    }
    @Test
    @DisplayName("login | LỖI USER_NOT_FOUND | Username chưa đăng ký → không tìm thấy")
    void login_userNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleLogin("nobody", "password123"));
        assertEquals(ErrorCode.USER_NOT_FOUND.name(), ex.getCode());
    }
    //TEST ĐỔI MẬT KHẨU
    @Test
    @DisplayName("changePassword | HỢP LỆ | Đúng pass cũ, pass mới hợp lệ, confirm khớp → đổi thành công")
    void changePassword_success() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        User user = userService.handleLogin("alice", "password123");
        assertDoesNotThrow(() ->
                userService.handleChangePassword(user, "password123", "newpass456", "newpass456"));
        // Login lại bằng pass mới → thành công
        assertDoesNotThrow(() -> userService.handleLogin("alice", "newpass456"));
    }
    @Test
    @DisplayName("changePassword | LỖI UNAUTHORIZED | Nhập sai mật khẩu cũ → bị chặn")
    void changePassword_wrongOldPassword_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        User user = userService.handleLogin("alice", "password123");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleChangePassword(user, "wrongold", "newpass456", "newpass456"));
        assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
    }
    @Test
    @DisplayName("changePassword | LỖI INVALID_INPUT | Mật khẩu mới giống mật khẩu cũ → không được phép")
    void changePassword_sameAsOld_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        User user = userService.handleLogin("alice", "password123");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleChangePassword(user, "password123", "password123", "password123"));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
    @Test
    @DisplayName("changePassword | LỖI INVALID_INPUT | Mật khẩu mới ngắn hơn 8 ký tự → không hợp lệ")
    void changePassword_tooShort_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        User user = userService.handleLogin("alice", "password123");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleChangePassword(user, "password123", "abc12", "abc12"));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
    @Test
    @DisplayName("changePassword | LỖI INVALID_INPUT | Mật khẩu xác nhận không khớp với mật khẩu mới → bị chặn")
    void changePassword_confirmMismatch_shouldThrow() {
        userService.handleRegister("alice", "password123", "alice@mail.com", "0901234567", "BIDDER");
        User user = userService.handleLogin("alice", "password123");
        AuctionException ex = assertThrows(AuctionException.class, () ->
                userService.handleChangePassword(user, "password123", "newpass456", "different789"));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
}

