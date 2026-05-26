package com.auction.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BidHistoryRowTest {

    @Test
    void testConstructorAndGetters_Success() {
        // Given: Chuẩn bị dữ liệu mẫu cho một dòng lịch sử đấu giá
        int expectedId = 1;
        int expectedAuctionId = 101;
        String expectedItemName = "iPhone 15 Pro Max";
        double expectedBidAmount = 25000000.0;
        String expectedBidTime = "2026-05-26 14:00:00";
        String expectedStatus = "WINNING";

        // When: Khởi tạo đối tượng qua Constructor đầy đủ tham số
        BidHistoryRow row = new BidHistoryRow(
                expectedId,
                expectedAuctionId,
                expectedItemName,
                expectedBidAmount,
                expectedBidTime,
                expectedStatus
        );

        // Then: Xác thực tất cả các hàm Getter phải trả về chính xác giá trị đã truyền vào
        assertNotNull(row);
        assertEquals(expectedId, row.getId(), "ID bản ghi không khớp!");
        assertEquals(expectedAuctionId, row.getAuctionId(), "Mã phiên đấu giá không khớp!");
        assertEquals(expectedItemName, row.getItemName(), "Tên sản phẩm không khớp!");
        assertEquals(expectedBidAmount, row.getBidAmount(), "Số tiền đặt giá không khớp!");
        assertEquals(expectedBidTime, row.getBidTime(), "Thời gian đặt giá không khớp!");
        assertEquals(expectedStatus, row.getStatus(), "Trạng thái dòng lịch sử không khớp!");
    }

    @Test
    void testConstructor_WithNullValues() {
        // Given, When & Then: Kiểm tra tính an toàn của đối tượng khi truyền giá trị null hoặc trống
        BidHistoryRow row = new BidHistoryRow(0, 0, null, 0.0, "", null);

        assertNotNull(row);
        assertEquals(0, row.getId());
        assertEquals(0, row.getAuctionId());
        assertNull(row.getItemName());
        assertEquals(0.0, row.getBidAmount());
        assertEquals("", row.getBidTime());
        assertNull(row.getStatus());
    }
}