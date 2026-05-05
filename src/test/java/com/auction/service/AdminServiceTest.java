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

    // trc mỗi lần test thì khởi tạo lại 3 service
    @BeforeEach
    void setUp() {
        itemService = new ItemService();
        managerService = new ManagerService(itemService);
        adminService = new AdminService(managerService);
    }

    // tạo các dữ liệu giả để test
    private void createPendingAuction(int auctionId, int itemId) {
        itemService.addItem(new Vehicle(itemId, "Producer", 1000000, "Mô tả", "Item " + itemId, "img.jpg"));
        managerService.scheduleAuction(auctionId, itemId, LocalDateTime.now().plusHours(1));
    }

    // bài test tuân theo 3 bước Arrange( chuẩn bị) -> Act( hành động) -> Assert( xác nhận)
    // test auction có thay đổi status sau duyệt ko
    @Test
    void getPendingAuctions_returnsOnlyPending() {
        // tạo dữ liệu giả
        createPendingAuction(1, 1);
        createPendingAuction(2, 2);

        // đã duyệt auction 1 nên không còn PENDING
        adminService.approveAuction(1);

        List<Auction> pending = adminService.getPendingAuctions();
        assertEquals(1, pending.size());
        assertEquals(2, pending.get(0).getAuctionId());
    }

    // khi danh sách duyệt trống
    @Test
    void getPendingAuctions_emptyWhenNone() {
        assertTrue(adminService.getPendingAuctions().isEmpty());
    }

    // test sau duyệt có đổi status sang RUNNING ko
    @Test
    void approveAuction_success() {
        createPendingAuction(1, 1);

        boolean result = adminService.approveAuction(1);

        assertTrue(result);
        assertEquals("RUNNING", managerService.getAuction(1).getAuctionStatus());
    }

    // test xem đã ghi lại dữ liệu chưa
    @Test
    void approveAuction_auditLogUpdated() {
        createPendingAuction(1, 1);
        adminService.approveAuction(1);

        assertEquals("APPROVED", adminService.getAudit(1));
    }
    // khi đơn ko tồn tại
    @Test
    void approveAuction_notFound_shouldThrow() {
        AuctionException ex = assertThrows(AuctionException.class, () ->
                adminService.approveAuction(999));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND.name(), ex.getCode());
    }
    // khi status ko phải là PENDING
    @Test
    void approveAuction_notPending_shouldThrow() {
        createPendingAuction(1, 1);
        adminService.approveAuction(1); // PENDING → RUNNING

        // Approve lần 2 → không còn PENDING → throw
        AuctionException ex = assertThrows(AuctionException.class, () ->
                adminService.approveAuction(1));
        assertEquals(ErrorCode.AUCTION_INVALID_STATE.name(), ex.getCode());
    }
    //test việc từ chối
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

    // test hàng loạt
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
    // khi có đơn đc duyệt đơn ko
    @Test
    void bulkApprove_partialFail_othersStillApproved() {
        createPendingAuction(1, 1);
        createPendingAuction(3, 3);

        adminService.bulkApprove(List.of(1, 2, 3));

        assertEquals("RUNNING", managerService.getAuction(1).getAuctionStatus());
        assertEquals("RUNNING", managerService.getAuction(3).getAuctionStatus());
    }

    //test nhật kí kiểm tra
    // auction chưa được approve/reject
    @Test
    void getAudit_noAction_returnsNoAction() {
        assertEquals("NO ACTION", adminService.getAudit(999));
    }

    // test danh sách thống kê có in lỗi hay ko
    @Test
    void printStats_doesNotThrow() {
        createPendingAuction(1, 1);
        createPendingAuction(2, 2);
        adminService.approveAuction(1);
        adminService.rejectAuction(2, "Lý do");

        assertDoesNotThrow(() -> adminService.printStats());
    }
}
