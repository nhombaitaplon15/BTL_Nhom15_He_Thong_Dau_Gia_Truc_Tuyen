package com.auction.common.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionRequestTest {

    private User sampleUser;

    @BeforeEach
    void setUpSampleUser() {
        // Khởi tạo một đối tượng User làm dữ liệu mẫu để nhúng vào Request
        sampleUser = new User(5, "nguyenvanb", "b@gmail.com", "123", "0912345678", "ACTIVE", "BIDDER", 100000.0);
    }

    // ==========================================
    // 1. KIỂM THỬ CONSTRUCTOR Logic
    // ==========================================
    @Nested
    class ConstructorTest {
        @Test
        void testConstructor_Success() {
            // Given & When: Tạo một yêu cầu nạp tiền mới
            TransactionRequest request = new TransactionRequest(
                    sampleUser, "DEPOSIT", 500000.0, "Techcombank - 1903xxx", "PENDING"
            );

            // Then: Kiểm tra giá trị khởi tạo ban đầu
            assertNotNull(request);
            assertEquals(0, request.getRequestId(), "Mặc định requestId mới tạo phải bằng 0!");
            assertEquals(sampleUser, request.getUser());
            assertEquals("DEPOSIT", request.getType());
            assertEquals(500000.0, request.getAmount());
            assertEquals("Techcombank - 1903xxx", request.getBankInfo());
            assertEquals("PENDING", request.getTransactionStatus());

            // Kiểm tra thời gian khởi tạo tự động (không được null và sát với thời gian hiện tại)
            assertNotNull(request.getRequestDate());
            assertTrue(request.getRequestDate().isBefore(LocalDateTime.now().plusSeconds(1)));
        }
    }

    // ==========================================
    // 2. KIỂM THỬ HỆ THỐNG GETTERS / SETTERS
    // ==========================================
    @Nested
    class GettersSettersTest {
        @Test
        void testSetters_ModifyDataCorrectly() {
            // Given: Khởi tạo một request ban đầu
            TransactionRequest request = new TransactionRequest(sampleUser, "DEPOSIT", 100.0, "", "PENDING");

            // Chuẩn bị dữ liệu mới để set
            User newUser = new User(9, "admin", "admin@gmail.com", "admin", "", "ACTIVE", "ADMIN", 0.0);
            LocalDateTime customDate = LocalDateTime.of(2026, 5, 26, 15, 0, 0);

            // When: Thực hiện thay đổi qua Setters
            request.setRequestId(105);
            request.setUser(newUser);
            request.setType("WITHDRAW");
            request.setAmount(200000.0);
            request.setBankInfo("Vietcombank - 0011xxx");
            request.setTransactionStatus("APPROVED");
            request.setRequestDate(customDate);

            // Then: Xác nhận các Getters lấy ra đúng giá trị mới cập nhật
            assertEquals(105, request.getRequestId());
            assertEquals(newUser, request.getUser());
            assertEquals("WITHDRAW", request.getType());
            assertEquals(200000.0, request.getAmount());
            assertEquals("Vietcombank - 0011xxx", request.getBankInfo());
            assertEquals("APPROVED", request.getTransactionStatus());
            assertEquals(customDate, request.getRequestDate());
        }
    }
}