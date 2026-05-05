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

@DisplayName("ManagerService - Quản lý phiên đấu giá")
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
    // SCHEDULE AUCTION
    @Test
    @DisplayName("scheduleAuction | HỢP LỆ | Item tồn tại → tạo phiên với trạng thái PENDING")
    void scheduleAuction_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));

        Auction auction = managerService.getAuction(1);
        assertNotNull(auction);
        assertEquals("PENDING", auction.getAuctionStatus());
    }

    @Test
    @DisplayName("scheduleAuction | LỖI ITEM_NOT_FOUND | ItemId không tồn tại → không tạo được phiên")
    void scheduleAuction_itemNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.scheduleAuction(1, 999, LocalDateTime.now().plusHours(1)));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }

    @Test
    @DisplayName("scheduleAuction | HỢP LỆ | EndTime tự động = startTime + 30 phút")
    void scheduleAuction_endTimeIsStartTimePlus30Min() {
        addItem(1, "Xe máy Honda", 5000000);
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        managerService.scheduleAuction(1, 1, start);

        Auction auction = managerService.getAuction(1);
        assertEquals(start.plusMinutes(30), auction.getEndTime());
    }
    // OPEN AUCTION
    @Test
    @DisplayName("openAuction | HỢP LỆ | Phiên đang PENDING → chuyển sang OPEN")
    void openAuction_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));
        managerService.openAuction(1);

        assertEquals("OPEN", managerService.getAuction(1).getAuctionStatus());
    }

    @Test
    @DisplayName("openAuction | LỖI AUCTION_INVALID_STATE | Phiên đã OPEN rồi, gọi open lần 2 → bị chặn")
    void openAuction_notPending_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));
        managerService.openAuction(1);

        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.openAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }

    @Test
    @DisplayName("openAuction | LỖI AUCTION_NOT_FOUND | AuctionId không tồn tại → báo lỗi")
    void openAuction_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.openAuction(999));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }
    // ACTIVATE AUCTION
    @Test
    @DisplayName("activateAuction | HỢP LỆ | Phiên OPEN và đã đến giờ → chuyển sang RUNNING")
    void activateAuction_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().minusMinutes(5));
        managerService.openAuction(1);
        managerService.activateAuction(1);

        assertEquals("RUNNING", managerService.getAuction(1).getAuctionStatus());
    }

    @Test
    @DisplayName("activateAuction | LỖI AUCTION_INVALID_STATE | Phiên vẫn PENDING, chưa OPEN → không kích hoạt được")
    void activateAuction_notOpen_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().minusMinutes(5));

        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.activateAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }

    @Test
    @DisplayName("activateAuction | LỖI AUCTION_INVALID_STATE | Phiên OPEN nhưng chưa đến giờ bắt đầu → bị chặn")
    void activateAuction_tooEarly_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(2));
        managerService.openAuction(1);

        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.activateAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    // SETUP START PRICE
    @Test
    @DisplayName("setupStartPrice | HỢP LỆ | Item tồn tại, giá > 0 → cập nhật giá thành công")
    void setupStartPrice_success() {
        addItem(1, "Xe máy Honda", 5000000);
        managerService.setupStartPrice(1, 8000000);

        assertEquals(8000000, itemService.getItemById(1).getStartPrice());
    }

    @Test
    @DisplayName("setupStartPrice | LỖI INVALID_INPUT | Giá = 0 → không hợp lệ, phải > 0")
    void setupStartPrice_zeroPrice_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.setupStartPrice(1, 0));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }

    @Test
    @DisplayName("setupStartPrice | LỖI INVALID_INPUT | Giá âm → không hợp lệ")
    void setupStartPrice_negativePrice_shouldThrow() {
        addItem(1, "Xe máy Honda", 5000000);
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.setupStartPrice(1, -1000));
        assertEquals(ErrorCode.INVALID_INPUT.name(), ex.getCode());
    }

    @Test
    @DisplayName("setupStartPrice | LỖI ITEM_NOT_FOUND | ItemId không tồn tại → báo lỗi")
    void setupStartPrice_itemNotFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                managerService.setupStartPrice(999, 5000000));
        assertEquals(ErrorCode.ITEM_NOT_FOUND.name(), ex.getCode());
    }

    // GET ALL AUCTIONS
    @Test
    @DisplayName("getAllAuctions | HỢP LỆ | Đã tạo 2 phiên → trả về đủ 2 phiên")
    void getAllAuctions_returnsAllScheduled() {
        addItem(1, "Item A", 1000);
        addItem(2, "Item B", 2000);
        managerService.scheduleAuction(1, 1, LocalDateTime.now().plusHours(1));
        managerService.scheduleAuction(2, 2, LocalDateTime.now().plusHours(2));

        assertEquals(2, managerService.getAllAuctions().size());
    }
}
