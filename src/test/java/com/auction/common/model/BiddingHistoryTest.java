package com.auction.common.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class BiddingHistoryTest {

    private LocalDateTime fixedBidTime;

    @BeforeEach
    void setUpTime() {
        // Cố định mốc thời gian để tránh lệch mili-giây khi chạy test
        fixedBidTime = LocalDateTime.of(2026, 5, 26, 13, 0, 0);
    }

    // ==========================================
    // 1. KIỂM THỬ CONSTRUCTOR ĐẦY ĐỦ THAM SỐ
    // ==========================================
    @Nested
    class AllArgsConstructorTest {
        @Test
        void testAllArgsConstructor_Success() {
            // Given & When: Giả lập nạp 1 bản ghi lịch sử đấu giá từ DB lên
            BiddingHistory history = new BiddingHistory(1, 50, 99, 1500000.0, fixedBidTime);

            // Then: Kiểm tra Getters lấy ra đúng giá trị khởi tạo
            assertNotNull(history);
            assertEquals(1, history.getId());
            assertEquals(50, history.getAuctionId());
            assertEquals(99, history.getBidderId());
            assertEquals(1500000.0, history.getBidAmount());
            assertEquals(fixedBidTime, history.getBidTime());
        }
    }

    // ==========================================
    // 2. KIỂM THỬ CONSTRUCTOR MẶC ĐỊNH & SETTERS
    // ==========================================
    @Nested
    class NoArgsConstructorAndSettersTest {
        @Test
        void testNoArgsConstructorAndSetters_Success() {
            // Given: Khởi tạo bằng constructor không tham số (Mặc định các trường số = 0, object = null)
            BiddingHistory history = new BiddingHistory();

            assertEquals(0, history.getId());
            assertNull(history.getBidTime());

            // When: Cập nhật toàn bộ trạng thái dữ liệu bằng Setters
            history.setId(205);
            history.setAuctionId(12);
            history.setBidderId(44);
            history.setBidAmount(3200000.0);
            history.setBidTime(fixedBidTime);

            // Then: Đảm bảo Getters trả về chuẩn xác giá trị mới gán
            assertEquals(205, history.getId());
            assertEquals(12, history.getAuctionId());
            assertEquals(44, history.getBidderId());
            assertEquals(3200000.0, history.getBidAmount());
            assertEquals(fixedBidTime, history.getBidTime());
        }
    }
}