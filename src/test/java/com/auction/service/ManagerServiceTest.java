package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Vehicle;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;


public class ManagerServiceTest {

    private ItemService itemService;
    private ManagerService managerService;

    private void addItem(int id, String name, int price) {
        itemService.addItem(new Vehicle(id, "Producer", price, "Mô tả", name, "img.jpg"));
    }

    @BeforeEach
    void setUp() {
        itemService = new ItemService();
        managerService = new ManagerService(itemService);
    }

    // test schedule auction
    // item tồn tại
    @Test
    void scheduleAuction_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));

        Auction auction = managerService.getAuction(1);
        assertNotNull(auction);
        assertEquals("PENDING", auction.getAuctionStatus());
    }
    // khi item ko tồn tại
    @Test
    void scheduleAuction_itemNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.scheduleAuction(1, 999, LocalDateTime.now().plusHours(1)));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }
    // EndTime tự động = startTime+ 30 phút
    @Test
    void scheduleAuction_endTimeIsStartTimePlus30Min() {
        addItem(1, "Xe máy Honda", 5000000);
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        managerService.scheduleAuction(1, 1, start);

        Auction auction = managerService.getAuction(1);
        assertEquals(start.plusMinutes(30), auction.getEndTime());
    }

    // test open auction
    // khi phiên đang PENDING
    @Test
    void openAuction_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));
        managerService.openAuction(1);

        assertEquals("OPEN", managerService.getAuction(1).getAuctionStatus());
    }
    // khi phiên đã OPEN rồi, gọi open lần 2
    @Test
    void openAuction_notPending_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));
        managerService.openAuction(1);

        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.openAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    // khi auctionId không tồn tại
    @Test
    void openAuction_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.openAuction(999));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }



    //  test activate auction
    // khi phiên OPEN và đã đến giờ
    @Test
    void activateAuction_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().minusMinutes(5));
        managerService.openAuction(1);
        managerService.activateAuction(1);

        assertEquals("RUNNING", managerService.getAuction(1).getAuctionStatus());
    }
    // khi phiên vẫn PENDING, chưa OPEN
    @Test
    void activateAuction_notOpen_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().minusMinutes(5));

        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.activateAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    // khi phiên OPEN nhưng chưa đến giờ bắt đầu
    @Test
    void activateAuction_tooEarly_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(2));
        managerService.openAuction(1);

        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.activateAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }



    // test setup start price
    // khi item tồn tại, giá > 0
    @Test
    void setupStartPrice_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.setupStartPrice(1, 8000000);

        assertEquals(8000000, itemService.getItemById(1).getStartPrice());
    }
    // khi giá = 0
    @Test
    void setupStartPrice_zeroPrice_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.setupStartPrice(1, 0));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
    // khi giá âm
    @Test
    void setupStartPrice_negativePrice_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.setupStartPrice(1, -1000));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }
    // khi item ko tồn tại
    @Test
    void setupStartPrice_itemNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.setupStartPrice(999, 5000000));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }



    // test get all auctions
    // đã tạo 2 phiên
    @Test
    void getAllAuctions_returnsAllScheduled() {
        addItem(1, "Item A", 1000);
        addItem(2, "Item B", 2000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));
        managerService.scheduleAuction(2, 2, LocalDateTime.now().plusHours(2));

        assertEquals(2, managerService.getAllAuctions().size());
    }
}
