package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Bidder;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BiddingServiceTest {

    @Mock private ManagerService managerService;
    @Mock private AuctionDAO auctionDAO;
    @Mock private TransactionDAO transactionDAO;
    @Mock private PaymentDAO paymentDAO;

    private BiddingService biddingService;

    @BeforeEach
    void setUp() throws Exception {
        biddingService = new BiddingService(managerService);

        Field f1 = BiddingService.class.getDeclaredField("auctionDAO");
        f1.setAccessible(true);
        f1.set(biddingService, auctionDAO);

        Field f2 = BiddingService.class.getDeclaredField("transactionDAO");
        f2.setAccessible(true);
        f2.set(biddingService, transactionDAO);

        Field f3 = BiddingService.class.getDeclaredField("paymentDAO");
        f3.setAccessible(true);
        f3.set(biddingService, paymentDAO);
    }

    private User makeBidder(int id, String role, double balance) {
        Bidder b = new Bidder(id, "user" + id, "u@mail.com", "pass1234", "0901234567", "ACTIVE", balance);
        b.setRole(role);
        return b;
    }

    private Auction makeAuction(int id, int sellerId, double price, String status, LocalDateTime endTime, Integer winnerId) {
        return new Auction(id, 10, sellerId, status, price, price, 0, winnerId, LocalDateTime.now().minusHours(1), endTime, LocalDateTime.now());
    }

    private Auction makeRunningAuction(int id, int sellerId, double price) {
        return makeAuction(id, sellerId, price, "RUNNING", LocalDateTime.now().plusHours(1), null);
    }

    @Nested
    @DisplayName("ValidateBidRules - Quy tắc đấu giá")
    class ValidateBidRulesTests {
        @Test
        @DisplayName("Admin không được đặt giá")
        void placeBid_adminForbidden() {
            User admin = makeBidder(1, "ADMIN", 9_999_999);
            Auction auction = makeRunningAuction(1, 99, 500_000);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            assertThrows(AuctionException.class, () -> biddingService.placeBid(admin, 1, 600_000));
        }

        @Test
        @DisplayName("Seller không được tự đấu giá")
        void placeBid_sellerCannotBidOwnItem() {
            User seller = makeBidder(5, "SELLER", 9_999_999);
            Auction auction = makeRunningAuction(1, 5, 500_000);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            assertThrows(AuctionException.class, () -> biddingService.placeBid(seller, 1, 600_000));
        }
    }

    @Nested
    @DisplayName("Transaction - Xử lý dòng tiền")
    class TransactionTests {

        @Test
        @DisplayName("placeBid: Hoàn tiền người cũ và trừ tiền người mới")
        void placeBid_mustRefundOldWinner() throws Exception {
            User bidderNew = makeBidder(2, "BIDDER", 2_000_000);
            Auction auction = makeAuction(1, 5, 500_000, "RUNNING", LocalDateTime.now().plusHours(1), 99);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            // 1. Mock các DAO trả về true
            when(paymentDAO.updateBalance(any(), anyInt(), anyDouble(), anyString())).thenReturn(true);
            when(auctionDAO.updateBid(any(), anyInt(), anyInt(), anyDouble())).thenReturn(true);

            try (MockedStatic<DBConnection> db = mockStatic(DBConnection.class);
                 Connection mockConn = mock(Connection.class);
                 PreparedStatement ps = mock(PreparedStatement.class);
                 ResultSet rs = mock(ResultSet.class)) {

                db.when(DBConnection::getConnection).thenReturn(mockConn);
                when(mockConn.prepareStatement(anyString())).thenReturn(ps);

                // CẤU HÌNH MOCK ĐỂ PASS CÁC ĐIỀU KIỆN SQL:

                // 1. Bước kiểm tra số dư (SELECT balance...)
                when(ps.executeQuery()).thenReturn(rs);
                when(rs.next()).thenReturn(true); // Có số dư

                // 2. Bước cập nhật ví Admin (UPDATE users SET escrow_balance...)
                // Bắt buộc phải cho nó trả về 1 (cập nhật thành công 1 dòng)
                when(ps.executeUpdate()).thenReturn(1);

                // Thực hiện hành động
                biddingService.placeBid(bidderNew, 1, 600_000);

                // Verify
                verify(paymentDAO).updateBalance(eq(mockConn), eq(99), eq(500000.0), eq("+"));
                verify(paymentDAO).updateBalance(eq(mockConn), eq(2), eq(600000.0), eq("-"));
                verify(mockConn).commit();
            }
        }

        @Test
        @DisplayName("placeBid: Rollback khi số dư không đủ")
        void placeBid_insufficientBalance() throws Exception {
            User bidder = makeBidder(2, "BIDDER", 100_000);
            Auction auction = makeRunningAuction(1, 99, 500_000);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            try (MockedStatic<DBConnection> db = mockStatic(DBConnection.class);
                 Connection conn = mock(Connection.class);
                 PreparedStatement ps = mock(PreparedStatement.class);
                 ResultSet rs = mock(ResultSet.class)) {

                db.when(DBConnection::getConnection).thenReturn(conn);
                when(conn.prepareStatement(anyString())).thenReturn(ps);
                when(ps.executeQuery()).thenReturn(rs);
                when(rs.next()).thenReturn(false);

                assertThrows(AuctionException.class, () -> biddingService.placeBid(bidder, 1, 600_000));
                verify(conn).rollback();
            }
        }
    }

    @Nested
    @DisplayName("RejectWin - Hủy kèo")
    class RejectWinTests {
        @Test
        @DisplayName("Ném lỗi nếu user không phải người thắng")
        void rejectWin_notWinner() {
            User winner = makeBidder(2, "BIDDER", 0);
            Auction auction = makeAuction(1, 5, 1_000_000, "FINISHED", LocalDateTime.now().minusHours(1), 99);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            assertThrows(AuctionException.class, () -> biddingService.rejectWin(winner, 1));
        }
    }
}
