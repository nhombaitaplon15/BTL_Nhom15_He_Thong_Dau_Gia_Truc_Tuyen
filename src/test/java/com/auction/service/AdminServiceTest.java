package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Vehicle;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminServiceTest {

    private ItemService itemService;
    private ManagerService managerService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        itemService = new ItemService();
        managerService = new ManagerService(itemService);
        adminService = new AdminService(managerService);
    }

    // Helper: tạo item + schedule auction ở trạng thái PENDING
    private void createPendingAuction(int auctionId, int itemId) {
        itemService.addItem(new Vehicle(itemId, "Producer", 1000000, "Mô tả", "Item " + itemId, "img.jpg"));
        managerService.scheduleAuction(auctionId, itemId, LocalDateTime.now().plusHours(1));
        // Status sau scheduleAuction = PENDING → đúng yêu cầu của AdminService
    }
    //TEST PENDING
    @Test
    void getPendingAuctions_returnsOnlyPending() {
        createPendingAuction(1, 1);
        createPendingAuction(2, 2);

        // Approve auction 1 → không còn PENDING
        adminService.approveAuction(1);

        List<Auction> pending = adminService.getPendingAuctions();
        assertEquals(1, pending.size());
        assertEquals(2, pending.get(0).getAuctionId());
    }

    @Test
    void getPendingAuctions_emptyWhenNone() {
        assertTrue(adminService.getPendingAuctions().isEmpty());
    }
    //TEST CHẤP NHẬN
    @Test
    void approveAuction_success() {
        createPendingAuction(1, 1);

        boolean result = adminService.approveAuction(1);

        assertTrue(result);
        assertEquals("RUNNING", managerService.getAuction(1).getAuctionStatus());
    }

    @Test
    void approveAuction_auditLogUpdated() {
        createPendingAuction(1, 1);
        adminService.approveAuction(1);

        assertEquals("APPROVED", adminService.getAudit(1));
    }

    @Test
    void approveAuction_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                adminService.approveAuction(999));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }

    @Test
    void approveAuction_notPending_shouldThrow() {
        createPendingAuction(1, 1);
        adminService.approveAuction(1); // PENDING → RUNNING

        // Approve lần 2 → không còn PENDING → throw
        AuctionException ex = assertThrows(AuctionException.class, () ->
                adminService.approveAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    //TEST VIỆC TỪ CHỐI
    @Test
    void rejectAuction_success() {
        createPendingAuction(1, 1);
        adminService.rejectAuction(1, "Vi phạm điều khoản");

        assertEquals("REJECTED", managerService.getAuction(1).getAuctionStatus());
    }

    @Test
    void rejectAuction_auditLogContainsReason() {
        createPendingAuction(1, 1);
        adminService.rejectAuction(1, "Hàng giả");

        String log = adminService.getAudit(1);
        assertTrue(log.contains("REJECTED"));
        assertTrue(log.contains("Hàng giả"));
    }

    @Test
    void rejectAuction_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                adminService.rejectAuction(999, "Lý do"));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }

    @Test
    void rejectAuction_notPending_shouldThrow() {
        createPendingAuction(1, 1);
        adminService.approveAuction(1); // → RUNNING

        AuctionException ex = assertThrows(AuctionException.class, () ->
                adminService.rejectAuction(1, "Lý do"));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    //TEST BULK APPROVE
    @Test
    void bulkApprove_allValid_success() {
        createPendingAuction(1, 1);
        createPendingAuction(2, 2);
        createPendingAuction(3, 3);

        adminService.bulkApprove(List.of(1, 2, 3));

        // Tất cả đều RUNNING
        assertEquals("RUNNING", managerService.getAuction(1).getAuctionStatus());
        assertEquals("RUNNING", managerService.getAuction(2).getAuctionStatus());
        assertEquals("RUNNING", managerService.getAuction(3).getAuctionStatus());
    }

    @Test
    void bulkApprove_partialFail_othersStillApproved() {
        createPendingAuction(1, 1);
        createPendingAuction(3, 3);
        // auctionId 2 không tồn tại → sẽ fail nhưng 1 và 3 vẫn được approve

        adminService.bulkApprove(List.of(1, 2, 3)); // 2 sẽ throw nhưng bị catch bên trong

        assertEquals("RUNNING", managerService.getAuction(1).getAuctionStatus());
        assertEquals("RUNNING", managerService.getAuction(3).getAuctionStatus());
    }
    //TEST AUDIT LOG

    @Test
    void getAudit_noAction_returnsNoAction() {
        // Auction chưa được approve/reject → log trả "NO ACTION"
        assertEquals("NO ACTION", adminService.getAudit(999));
    }

    // STATISTICS (printStats không throw là đủ)
    @Test
    void printStats_doesNotThrow() {
        createPendingAuction(1, 1);
        createPendingAuction(2, 2);
        adminService.approveAuction(1);
        adminService.rejectAuction(2, "Lý do");

        assertDoesNotThrow(() -> adminService.printStats());
    }
}
