package com.auction.common.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionTest {

    private LocalDateTime now;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUpTimes() {
        now = LocalDateTime.now();
        start = now.plusDays(1);
        end = now.plusDays(3);
    }

    @Nested
    class ConstructorAndGettersTest {

        @Test
        void testAllArgsConstructor_Success() {
            // Given & When: Khởi tạo bằng constructor đầy đủ tham số
            Auction auction = new Auction(
                    1, 101, 55, "RUNNING",
                    100.0, 150.0, 5,
                    99, start, end, now
            );

            // Then: Kiểm tra dữ liệu được map chính xác qua các Getter
            assertNotNull(auction);
            assertEquals(1, auction.getAuctionId());
            assertEquals(101, auction.getItemId());
            assertEquals(55, auction.getSellerId());
            assertEquals("RUNNING", auction.getAuctionStatus());
            assertEquals(100.0, auction.getStartingPrice());
            assertEquals(150.0, auction.getCurrentPrice());
            assertEquals(5, auction.getTotalBids());
            assertEquals(99, auction.getCurrentWinnerId());
            assertEquals(start, auction.getStartTime());
            assertEquals(end, auction.getEndTime());
            assertEquals(now, auction.getCreatedAt());
        }

        @Test
        void testConstructor_WithNullCurrentWinnerId() {
            // Given & When: Giả lập trường hợp phiên đấu giá mới tạo, chưa có ai vào trả giá (WinnerId = null)
            Auction auction = new Auction(
                    2, 102, 55, "OPEN",
                    200.0, 200.0, 0,
                    null, start, end, now
            );

            // Then: WinnerId phải trả về null đúng thiết kế hệ thống
            assertNull(auction.getCurrentWinnerId());
        }
    }

    @Nested
    class SettersTest {

        @Test
        void testSetters_ModifyDataCorrectly() {
            // Given: Tạo một đối tượng trống bằng No-Arg Constructor
            Auction auction = new Auction();

            // When: Thay đổi trạng thái dữ liệu bằng các Setter
            auction.setAuctionId(5);
            auction.setAuctionStatus("SOLD");
            auction.setStartingPrice(500.0);
            auction.setCurrentPrice(750.0);
            auction.setTotalBids(12);
            auction.setCurrentWinnerId(88);
            auction.setStartTime(start);
            auction.setEndTime(end);
            auction.setCreatedAt(now);

            // Then: Xác nhận các giá trị mới đã được cập nhật thành công vào đối tượng
            assertEquals(5, auction.getAuctionId());
            assertEquals("SOLD", auction.getAuctionStatus());
            assertEquals(500.0, auction.getStartingPrice());
            assertEquals(750.0, auction.getCurrentPrice());
            assertEquals(12, auction.getTotalBids());
            assertEquals(88, auction.getCurrentWinnerId());
            assertEquals(start, auction.getStartTime());
            assertEquals(end, auction.getEndTime());
            assertEquals(now, auction.getCreatedAt());
        }
    }

    @Nested
    class HelperMethodsTest {

        @Test
        void isWaitingForAdmin_ShouldReturnTrue_WhenStatusMatches() {
            // Given
            Auction auction = new Auction();

            // When: Trạng thái viết hoa chuẩn chỉnh
            auction.setAuctionStatus("WAITING_FOR_ADMIN");
            // Then
            assertTrue(auction.isWaitingForAdmin());

            // When: Trạng thái viết thường (Kiểm tra tính năng không phân biệt chữ hoa/thường)
            auction.setAuctionStatus("waiting_for_admin");
            // Then
            assertTrue(auction.isWaitingForAdmin());
        }

        @Test
        void isWaitingForAdmin_ShouldReturnFalse_WhenStatusDoesNotMatch() {
            // Given
            Auction auction = new Auction();

            // When: Trạng thái bất kỳ khác
            auction.setAuctionStatus("RUNNING");
            // Then
            falseConditionCheck(auction);

            // When: Trạng thái bị null hoàn toàn
            auction.setAuctionStatus(null);
            // Then
            falseConditionCheck(auction);
        }

        private void falseConditionCheck(Auction auction) {
            assertFalse(auction.isWaitingForAdmin());
        }
    }
}