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

public class BiddingServiceTest {

    private ItemService itemService;
    private ManagerService managerService;
    private BiddingService biddingService;

    @BeforeEach
    void setUp() {
        itemService = new ItemService();
        managerService = new ManagerService(itemService); // truyền cùng itemService vào
        biddingService = new BiddingService(managerService);

        // addItem vào itemService này -> managerService dùng chung -> scheduleAuction tìm thấy
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
    void placeBid_success() {
        boolean result = biddingService.placeBid(1, "Alice", 2000000);

        assertTrue(result);
        assertEquals("Alice", getAuction().getHighestBidder());
        assertEquals(2000000, getAuction().getCurrentPrice());
    }

    // khi nhiều người bid, người trả cao nhất dẫn đầu
    @Test
    void placeBid_multipleUsers_highestWins() {
        biddingService.placeBid(1, "Alice", 2000000);
        biddingService.placeBid(1, "Bob", 3000000);

        assertEquals("Bob", getAuction().getHighestBidder());
        assertEquals(3000000, getAuction().getCurrentPrice());
    }
    // cùng một người bid lại với giá cao hơn và hợp lệ
    @Test
    void placeBid_sameUserBidHigher_success() {
        biddingService.placeBid(1, "Alice", 2000000);
        biddingService.placeBid(1, "Alice", 3000000);

        assertEquals(3000000, getAuction().getCurrentPrice());
    }


    // những trường hợp có lỗi
    // khi auctionId không tồn tại
    @Test
    void placeBid_auctionNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(999, "Alice", 2000000));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }
    // khi phiên chưa RUNNING đang PENDING
    @Test
    void placeBid_auctionNotRunning_shouldThrow() {
        itemService.addItem(new Vehicle(2, "Producer", 1000, "Mô tả", "Xe đạp", "img.jpg"));
        managerService.scheduleAuction(2, 2, LocalDateTime.now().plusHours(1));

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(2, "Alice", 5000));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    // khi EndTime đã qua
    @Test
    void placeBid_auctionAlreadyEnded_shouldThrow() {
        getAuction().setEndTime(LocalDateTime.now().minusSeconds(1));

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(1, "Alice", 2000000));
        assertEquals(ErrorCode.AUCTION_ALREADY_ENDED.name(), ex.getCode());
    }
    // khi giá bid thấp hơn currentPrice
    @Test
    void placeBid_priceTooLow_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(1, "Alice", 500000));
        assertEquals(ErrorCode.BID_TOO_LOW.name(), ex.getCode());
    }
    // khi giá bid bằng đúng currentPrice
    @Test
    void placeBid_priceEqualCurrent_shouldThrow() {
        double currentPrice = getAuction().getCurrentPrice();
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.placeBid(1, "Alice", currentPrice));
        assertEquals(ErrorCode.BID_TOO_LOW.name(), ex.getCode());
    }


    // chức năng nâng cao
    // khi bid lúc còn 30s
    @Test
    void placeBid_inLast60Seconds_shouldExtendTime() {
        LocalDateTime shortEnd = LocalDateTime.now().plusSeconds(30);
        getAuction().setEndTime(shortEnd);

        biddingService.placeBid(1, "Alice", 2000000);

        assertTrue(getAuction().getEndTime().isAfter(shortEnd));
    }
    // khi bid lúc còn 20 phút
    @Test
    void placeBid_notInLast60Seconds_shouldNotExtendTime() {
        LocalDateTime originalEnd = getAuction().getEndTime();

        biddingService.placeBid(1, "Alice", 2000000);

        assertEquals(originalEnd, getAuction().getEndTime());
    }
    // bid nhiều lần liên tiếp trong 60s cuối
    @Test
    void placeBid_multipleSnipesInLastMinute_eachExtends() {
        getAuction().setEndTime(LocalDateTime.now().plusSeconds(30));
        biddingService.placeBid(1, "Alice", 2000000);
        LocalDateTime afterFirst = getAuction().getEndTime();

        getAuction().setEndTime(LocalDateTime.now().plusSeconds(30));
        biddingService.placeBid(1, "Bob", 3000000);

        assertTrue(getAuction().getEndTime().isAfter(afterFirst));
    }
    // bid khi còn đúng 59s
    @Test
    void placeBid_exactlyAt60SecondsLeft_shouldExtend() {
        getAuction().setEndTime(LocalDateTime.now().plusSeconds(59));

        biddingService.placeBid(1, "Alice", 2000000);

        assertTrue(getAuction().getEndTime().isAfter(LocalDateTime.now().plusSeconds(59)));
    }

    // xác nhận
    // đúng người thắng, trạng thái DELIVERING
    @Test
    void confirmReceived_success() {
        biddingService.placeBid(1, "Alice", 2000000);
        getAuction().setAuctionStatus("DELIVERING");

        boolean result = biddingService.confirmReceived(1, "Alice");

        assertTrue(result);
        assertEquals("COMPLETED", getAuction().getAuctionStatus());
    }
    // khi người xác nhận không phải người thắng
    @Test
    void confirmReceived_wrongBidder_shouldThrow() {
        biddingService.placeBid(1, "Alice", 2000000);
        getAuction().setAuctionStatus("DELIVERING");

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.confirmReceived(1, "Bob"));
        assertEquals(ErrorCode.UNAUTHORIZED.name(), ex.getCode());
    }
    // khi phiên chưa ở trạng thái DELIVERING
    @Test
    void confirmReceived_wrongState_shouldThrow() {
        biddingService.placeBid(1, "Alice", 2000000);

        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.confirmReceived(1, "Alice"));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    // khi auctionId không tồn tại
    @Test
    void confirmReceived_auctionNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                biddingService.confirmReceived(999, "Alice"));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }
}
