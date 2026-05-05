package com.auction.service;

import com.auction.service.ItemService;
import com.auction.service.ManagerService;
import com.auction.common.model.Auction;
import com.auction.common.model.Vehicle;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BiddingService - Đặt giá và Anti-sniping")
public class BiddingServiceTest {

    private ItemService itemService;
    private ManagerService managerService;
    private BiddingService biddingService;

    @BeforeEach
    void setUp() {
        itemService = new ItemService();
        managerService = new ManagerService(itemService); // truyền CÙNG itemService vào
        biddingService = new BiddingService(managerService);

        // addItem vào itemService này → managerService dùng chung → scheduleAuction tìm thấy
        itemService.addItem(new Vehicle(1, "Producer", 1000000, "Mô tả", "Xe máy Honda", "img.jpg"));
        managerService.scheduleAuction(1, 1, LocalDateTime.now().minusMinutes(10));
        managerService.openAuction(1);
        managerService.activateAuction(1);
    }

    private Auction getAuction() {
        return managerService.getAuction(1);
    }
    // những trường hợp hợp lệ
    @Test
    @DisplayName("placeBid | HỢP LỆ | Giá cao hơn currentPrice → cập nhật người dẫn đầu và giá mới")
    void placeBid_success() {
        boolean result = biddingService.placeBid(1, "Alice", 2000000);

        assertTrue(result);
        assertEquals("Alice", getAuction().getHighestBidder());
        assertEquals(2000000, getAuction().getCurrentPrice());
    }

    @Test
    @DisplayName("placeBid | HỢP LỆ | Nhiều người bid, người trả cao nhất dẫn đầu")
    void placeBid_multipleUsers_highestWins() {
        biddingService.placeBid(1, "Alice", 2000000);
        biddingService.placeBid(1, "Bob", 3000000);

        assertEquals("Bob", getAuction().getHighestBidder());
        assertEquals(3000000, getAuction().getCurrentPrice());
    }

    @Test
    @DisplayName("placeBid | HỢP LỆ | Cùng một người bid lại với giá cao hơn → hợp lệ")
    void placeBid_sameUserBidHigher_success() {
        biddingService.placeBid(1, "Alice", 2000000);
        biddingService.placeBid(1, "Alice", 3000000);

        assertEquals(3000000, getAuction().getCurrentPrice());
    }
    // những trường hợp có lỗi
    @Test
    @DisplayName("placeBid | LỖI AUCTION_NOT_FOUND | AuctionId không tồn tại → báo lỗi")
    void placeBid_auctionNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(999, "Alice", 2000000));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }

    @Test
    @DisplayName("placeBid | LỖI AUCTION_INVALID_STATE | Phiên chưa RUNNING (đang PENDING) → không cho bid")
    void placeBid_auctionNotRunning_shouldThrow() {
        itemService.addItem(new Vehicle(2, "Producer", 1000, "Mô tả", "Xe đạp", "img.jpg"));
        managerService.scheduleAuction(2, 2, LocalDateTime.now().plusHours(1));

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(2, "Alice", 5000));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    @Test
    @DisplayName("placeBid | LỖI AUCTION_ALREADY_ENDED | EndTime đã qua → phiên kết thúc, không cho bid")
    void placeBid_auctionAlreadyEnded_shouldThrow() {
        getAuction().setEndTime(LocalDateTime.now().minusSeconds(1));

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(1, "Alice", 2000000));
        assertEquals(ErrorCode.AUCTION_ALREADY_ENDED.name(), ex.getCode());
    }

    @Test
    @DisplayName("placeBid | LỖI BID_TOO_LOW | Giá bid thấp hơn currentPrice → không hợp lệ")
    void placeBid_priceTooLow_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(1, "Alice", 500000));
        assertEquals(ErrorCode.BID_TOO_LOW.name(), ex.getCode());
    }

    @Test
    @DisplayName("placeBid | LỖI BID_TOO_LOW | Giá bid bằng đúng currentPrice → phải cao hơn mới hợp lệ")
    void placeBid_priceEqualCurrent_shouldThrow() {
        long currentPrice = getAuction().getCurrentPrice();
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(1, "Alice", currentPrice));
        assertEquals(ErrorCode.BID_TOO_LOW.name(), ex.getCode());
    }
    // chức năng nâng cao
    @Test
    @DisplayName("antiSniping | HỢP LỆ | Bid khi còn 30s → nằm trong 60s cuối, endTime được gia hạn +30s")
    void placeBid_inLast60Seconds_shouldExtendTime() {
        LocalDateTime shortEnd = LocalDateTime.now().plusSeconds(30);
        getAuction().setEndTime(shortEnd);

        biddingService.placeBid(1, "Alice", 2000000);

        assertTrue(getAuction().getEndTime().isAfter(shortEnd));
    }

    @Test
    @DisplayName("antiSniping | HỢP LỆ | Bid khi còn 20 phút → không trong 60s cuối, endTime không đổi")
    void placeBid_notInLast60Seconds_shouldNotExtendTime() {
        LocalDateTime originalEnd = getAuction().getEndTime();

        biddingService.placeBid(1, "Alice", 2000000);

        assertEquals(originalEnd, getAuction().getEndTime());
    }

    @Test
    @DisplayName("antiSniping | HỢP LỆ | Bid nhiều lần liên tiếp trong 60s cuối → mỗi lần đều được gia hạn")
    void placeBid_multipleSnipesInLastMinute_eachExtends() {
        getAuction().setEndTime(LocalDateTime.now().plusSeconds(30));
        biddingService.placeBid(1, "Alice", 2000000);
        LocalDateTime afterFirst = getAuction().getEndTime();

        getAuction().setEndTime(LocalDateTime.now().plusSeconds(30));
        biddingService.placeBid(1, "Bob", 3000000);

        assertTrue(getAuction().getEndTime().isAfter(afterFirst));
    }

    @Test
    @DisplayName("antiSniping | HỢP LỆ | Bid khi còn đúng 59s → vẫn nằm trong vùng gia hạn")
    void placeBid_exactlyAt60SecondsLeft_shouldExtend() {
        getAuction().setEndTime(LocalDateTime.now().plusSeconds(59));

        biddingService.placeBid(1, "Alice", 2000000);

        assertTrue(getAuction().getEndTime().isAfter(LocalDateTime.now().plusSeconds(59)));
    }
    // xác nhận
    @Test
    @DisplayName("confirmReceived | HỢP LỆ | Đúng người thắng, trạng thái DELIVERING → xác nhận thành công, chuyển COMPLETED")
    void confirmReceived_success() {
        biddingService.placeBid(1, "Alice", 2000000);
        getAuction().setAuctionStatus("DELIVERING");

        boolean result = biddingService.confirmReceived(1, "Alice");

        assertTrue(result);
        assertEquals("COMPLETED", getAuction().getAuctionStatus());
    }

    @Test
    @DisplayName("confirmReceived | LỖI UNAUTHORIZED | Người xác nhận không phải người thắng → bị chặn")
    void confirmReceived_wrongBidder_shouldThrow() {
        biddingService.placeBid(1, "Alice", 2000000);
        getAuction().setAuctionStatus("DELIVERING");

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.confirmReceived(1, "Bob"));
        assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
    }
    @Test
    @DisplayName("confirmReceived | LỖI AUCTION_INVALID_STATE | Phiên chưa ở trạng thái DELIVERING → không xác nhận được")
    void confirmReceived_wrongState_shouldThrow() {
        biddingService.placeBid(1, "Alice", 2000000);

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.confirmReceived(1, "Alice"));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }

    @Test
    @DisplayName("confirmReceived | LỖI AUCTION_NOT_FOUND | AuctionId không tồn tại → báo lỗi")
    void confirmReceived_auctionNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.confirmReceived(999, "Alice"));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }
}
