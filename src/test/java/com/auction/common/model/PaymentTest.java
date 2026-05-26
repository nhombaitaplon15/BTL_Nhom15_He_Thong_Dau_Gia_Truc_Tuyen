package com.auction.common.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentTest {

    private LocalDateTime fixedTimestamp;

    @BeforeEach
    void setUpTime() {
        fixedTimestamp = LocalDateTime.of(2026, 5, 26, 14, 0, 0);
    }

    // ==========================================
    // 1. KIỂM THỬ CONSTRUCTOR ĐẦY ĐỦ (DÙNG CHO DAO)
    // ==========================================
    @Nested
    class FullConstructorTest {
        @Test
        void testFullArgsConstructorAndGetters() {
            Payment payment = new Payment(1001, "RELEASE_FUNDS", 5, 12, 50, 250000.0, 5000.0, fixedTimestamp);

            assertNotNull(payment);
            assertEquals(1001, payment.getLogId());
            assertEquals("RELEASE_FUNDS", payment.getTransactionType());
            assertEquals(5, payment.getFromUserId());
            assertEquals(12, payment.getToUserId());
            assertEquals(50, payment.getAuctionId());
            assertEquals(250000.0, payment.getAmount());
            assertEquals(5000.0, payment.getFee());
            assertEquals(fixedTimestamp, payment.getTimestamp());
        }
    }

    // ==========================================
    // 2. KIỂM THỬ CONSTRUCTOR RÚT GỌN (DÙNG CHO SERVICE)
    // ==========================================
    @Nested
    class ShortConstructorTest {
        @Test
        void testShortConstructor_ShouldAutoGenerateTimestamp() {
            Payment payment = new Payment("HOLD_FUNDS", 8, 0, 42, 120000.0, 0.0);

            assertNotNull(payment);
            assertEquals(0, payment.getLogId());
            assertEquals("HOLD_FUNDS", payment.getTransactionType());
            assertEquals(8, payment.getFromUserId());
            assertEquals(0, payment.getToUserId());
            assertEquals(42, payment.getAuctionId());
            assertEquals(120000.0, payment.getAmount());
            assertEquals(0.0, payment.getFee());
            assertNotNull(payment.getTimestamp());
        }
    }

    // ==========================================
    // 3. KIỂM THỬ ĐỊNH DẠNG HÀM TOSTRING
    // ==========================================
    @Nested
    class ToStringTest {
        @Test
        void testToStringFormat() {
            // Given
            Payment payment = new Payment(2002, "REFUND", 1, 3, 15, 500.55, 10.0, fixedTimestamp);

            // When: Lấy chuỗi format thực tế từ hàm toString() của bạn
            String actualString = payment.toString();

            // Then: Tự động lấy định dạng số theo cấu hình máy hiện tại để so sánh mẫu
            String expectedString = String.format("[%s] REFUND: User 1 -> User 3 (Auction: 15) | Tiền: %.2f | Phí: %.2f",
                    fixedTimestamp, 500.55, 10.0);

            // Đảm bảo chuỗi sinh ra khớp hoàn toàn, bất kể là dấu phẩy (,) hay dấu chấm (.)
            assertEquals(expectedString, actualString);
        }
    }
}