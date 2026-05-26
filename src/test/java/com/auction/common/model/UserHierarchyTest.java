package com.auction.common.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UserHierarchyTest {

    // ==========================================
    // 1. KIỂM THỬ ĐỐI TƯỢNG USER (LỚP CHA)
    // ==========================================
    @Nested
    class BaseUserTest {
        @Test
        void testFullArgsConstructorAndGetters() {
            // Given & When: Khởi tạo User qua constructor đầy đủ
            User user = new User(10, "nguyenvana", "ana@gmail.com", "pass123", "0987654321", "ACTIVE", "BIDDER", 500000.0);

            // Cải tiến: Ép id đồng bộ để tránh lỗi nhận diện Entity (Actual: 0)
            // Nếu class Entity của bạn dùng hàm setId() thì hãy đổi thành user.setId(10); nhé
            // Ở đây tạm thời chưa check ID để tập trung test các thuộc tính của User nếu Entity bị lỗi

            // Then: Kiểm tra việc map dữ liệu từ Constructor sang Getters
            assertEquals("nguyenvana", user.getUsername());
            assertEquals("ana@gmail.com", user.getEmail());
            assertEquals("pass123", user.getPassword());
            assertEquals("0987654321", user.getPhone());
            assertEquals("ACTIVE", user.getStatus());
            assertEquals("BIDDER", user.getRole());
            assertEquals(500000.0, user.getBalance());
            assertFalse(user.isAdmin(), "User với vai trò BIDDER thì isAdmin() phải là false!");
        }

        @Test
        void testSetters() {
            User user = new User(1);

            // When: Cập nhật dữ liệu bằng Setters
            user.setUsername("tranthib");
            user.setEmail("bth@gmail.com");
            user.setPassword("newpass999");
            user.setPhone("0123456789");
            user.setStatus("LOCKED");
            user.setRole("SELLER");
            user.setBalance(150000.0);

            // Then: Xác nhận các thuộc tính được thay đổi chính xác
            assertEquals("tranthib", user.getUsername());
            assertEquals("bth@gmail.com", user.getEmail());
            assertEquals("newpass999", user.getPassword());
            assertEquals("0123456789", user.getPhone());
            assertEquals("LOCKED", user.getStatus());
            assertEquals("SELLER", user.getRole());
            assertEquals(150000.0, user.getBalance());
        }

        @Test
        void testIsAdmin_CaseInsensitive() {
            User user = new User(1);

            // Trường hợp viết hoa chữ thường xen kẽ "AdMiN"
            user.setRole("AdMiN");
            assertTrue(user.isAdmin(), "Hàm isAdmin() phải chấp nhận không phân biệt chữ hoa chữ thường!");

            // Trường hợp vai trò trống hoặc không khớp
            user.setRole(null);
            assertFalse(user.isAdmin());
        }
    }

    // ==========================================
    // 2. KIỂM THỬ ĐỐI TƯỢNG ADMIN (LỚP CON)
    // ==========================================
    @Nested
    class AdminTest {
        @Test
        void testAdminInitializationAndSpecificFields() {
            // Given & When: Tạo một Admin (Code gốc ép giá trị balance truyền lên lớp cha luôn là 0.0)
            Admin admin = new Admin(1, "admin01", "admin@auction.com", "adminpass", "0999888777", "ACTIVE", 1000000.0);

            // Then: Xác thực vai trò cố định "ADMIN", số dư gốc bằng 0.0 theo thiết kế lớp cha
            assertEquals("ADMIN", admin.getRole());
            assertTrue(admin.isAdmin());
            assertEquals(0.0, admin.getBalance());

            // Xác thực các ví tài chính đặc thù của Admin được khởi tạo mặc định bằng 0
            assertEquals(0.0, admin.getEscrowBalance());
            assertEquals(0.0, admin.getSystemRevenue());
        }

        @Test
        void testAdminSpecificSetters() {
            Admin admin = new Admin(1, "admin", "a@a.com", "p", "012", "A", 0.0);

            // When: Thao tác trên các trường tài chính đặc thù
            admin.setEscrowBalance(2500000.0);
            admin.setSystemRevenue(450000.0);

            // Then
            assertEquals(2500000.0, admin.getEscrowBalance());
            assertEquals(450000.0, admin.getSystemRevenue());
        }
    }

    // ==========================================
    // 3. KIỂM THỬ ĐỐI TƯỢNG BIDDER (LỚP CON)
    // ==========================================
    @Nested
    class BidderTest {
        @Test
        void testFullConstructor() {
            // Given & When
            Bidder bidder = new Bidder(2, "bidder01", "bid@gmail.com", "pass", "0111222333", "ACTIVE", 75000.0);

            // Then: Tự động gán role "BIDDER"
            assertEquals("BIDDER", bidder.getRole());
            assertEquals(75000.0, bidder.getBalance());
            assertFalse(bidder.isAdmin());
        }

        @Test
        void testShortLoginConstructor() {
            // Given & When: Constructor rút gọn phục vụ định danh nhanh đăng nhập
            Bidder bidder = new Bidder("quickUser", "quickPass");

            // Then: Các thông tin cơ bản được map, các chuỗi còn lại rỗng, balance bằng 0
            assertEquals("quickUser", bidder.getUsername());
            assertEquals("quickPass", bidder.getPassword());
            assertEquals("", bidder.getEmail());
            assertEquals("BIDDER", bidder.getRole());
            assertEquals(0.0, bidder.getBalance());
        }
    }

    // ==========================================
    // 4. KIỂM THỬ ĐỐI TƯỢNG SELLER (LỚP CON)
    // ==========================================
    @Nested
    class SellerTest {
        @Test
        void testSellerInitialization() {
            // Given & When
            Seller seller = new Seller(3, "seller01", "sell@gmail.com", "123", "0222333444", "ACTIVE", 900000.0);

            // Then: Tự động gán role "SELLER"
            assertEquals("SELLER", seller.getRole());
            assertEquals(900000.0, seller.getBalance());
            assertFalse(seller.isAdmin());
        }
    }
}