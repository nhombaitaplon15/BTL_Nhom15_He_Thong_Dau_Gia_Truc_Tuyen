package com.auction.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AuctionExceptionTest {

    @Test
    void testAuctionExceptionInitialization_Success() {
        // Given: Chuẩn bị mã lỗi và thông điệp kiểm thử
        String expectedCode = ErrorCode.USER_NOT_FOUND.name();
        String expectedMessage = "Tài khoản không tồn tại trên hệ thống!";

        // When: Khởi tạo đối tượng ngoại lệ tùy chỉnh
        AuctionException exception = new AuctionException(expectedCode, expectedMessage);

        // Then: Xác thực các giá trị được gán chính xác
        assertNotNull(exception);
        assertEquals(expectedCode, exception.getCode(), "Mã lỗi (code) trả về không khớp với dữ liệu đầu vào!");
        assertEquals(expectedMessage, exception.getMessage(), "Thông điệp (message) trả về không khớp với dữ liệu đầu vào!");
    }

    @Test
    void testAuctionExceptionInheritance() {
        // Given & When
        AuctionException exception = new AuctionException("TEST_CODE", "Test message");

        // Then: Đảm bảo class kế thừa đúng từ RuntimeException để không bắt buộc phải try-catch (Unchecked Exception)
        assertInstanceOf(RuntimeException.class, exception, "AuctionException phải là một thực thể kế thừa từ RuntimeException!");
    }

    @Test
    void testAuctionExceptionWithNullValues() {
        // Given, When & Then: Kiểm tra hành vi biên khi truyền giá trị null
        AuctionException exception = new AuctionException(null, null);

        assertNull(exception.getCode());
        assertNull(exception.getMessage());
    }
}