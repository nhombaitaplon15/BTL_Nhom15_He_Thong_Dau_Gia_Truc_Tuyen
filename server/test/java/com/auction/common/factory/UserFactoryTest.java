package com.auction.common.factory;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserFactoryTest {

  // Tạo sẵn một bộ dữ liệu mẫu (dummy data) để tái sử dụng cho các hàm test
  private final int testId = 1;
  private final String testName = "Test User";
  private final String testEmail = "test@example.com";
  private final String testPassword = "password123";
  private final String testPhone = "0123456789";
  private final String testStatus = "ACTIVE";
  private final double testBalance = 500.0;

  @Test
  void testCreateAdmin() {
    // Thực thi
    User user = UserFactory.createUser(testId, testName, testEmail, testPassword, testPhone, testStatus, "ADMIN", testBalance);

    // Kiểm tra kết quả
    assertNotNull(user, "User không được null");
    assertTrue(user instanceof Admin, "Phải tạo ra và trả về đúng đối tượng Admin");
  }

  @Test
  void testCreateSeller() {
    User user = UserFactory.createUser(testId, testName, testEmail, testPassword, testPhone, testStatus, "SELLER", testBalance);

    assertNotNull(user, "User không được null");
    assertTrue(user instanceof Seller, "Phải tạo ra và trả về đúng đối tượng Seller");
  }

  @Test
  void testCreateBidder() {
    User user = UserFactory.createUser(testId, testName, testEmail, testPassword, testPhone, testStatus, "BIDDER", testBalance);

    assertNotNull(user, "User không được null");
    assertTrue(user instanceof Bidder, "Phải tạo ra và trả về đúng đối tượng Bidder");
  }

  @Test
  void testCreateUserWithNullRole_ShouldReturnNull() {
    // Test trường hợp role bị truyền vào là null
    User user = UserFactory.createUser(testId, testName, testEmail, testPassword, testPhone, testStatus, null, testBalance);

    assertNull(user, "Khi truyền role là null, hàm phải trả về null theo đúng logic");
  }

  @Test
  void testCreateUserWithUnknownRole_ShouldFallbackToBidder() {
    // Test trường hợp role lạ không có trong hệ thống
    User user = UserFactory.createUser(testId, testName, testEmail, testPassword, testPhone, testStatus, "HACKER_ROLE", testBalance);

    assertNotNull(user, "User không được null");
    assertTrue(user instanceof Bidder, "Với các role lạ, hệ thống phải tự động quy về Bidder để đảm bảo an toàn");
  }

  @Test
  void testCreateUserWithLowerCaseRole() {
    // Do trong code bạn có dùng role.toUpperCase() nên mình test thử chữ thường
    User user = UserFactory.createUser(testId, testName, testEmail, testPassword, testPhone, testStatus, "admin", testBalance);

    assertNotNull(user, "User không được null");
    assertTrue(user instanceof Admin, "Hàm phải xử lý được chữ thường mà không bị lỗi (case-insensitive)");
  }
}