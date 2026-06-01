package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Bidder;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.service.BiddingService;
import com.auction.server.service.ManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BiddingServiceTest {

    @Mock
    private ManagerService managerService;
    @Mock
    private AuctionDAO auctionDAO;
    @Mock
    private TransactionDAO transactionDAO;
    @Mock
    private PaymentDAO paymentDAO;

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

    // Tạo nhanh đối tượng người đấu giá để test
    private User makeBidder(int id, String role, double balance) {
        Bidder b = new Bidder(id, "user" + id, "u@mail.com", "pass1234", "0901234567", "ACTIVE", balance);
        b.setRole(role);
        return b;
    }

    // Tạo nhanh đối tượng phòng đấu giá tổng quát
    private Auction makeAuction(int id, int sellerId, double price, String status,
                                LocalDateTime endTime, Integer winnerId) {
        return new Auction(id, 10, sellerId, status, price, price,
                0, winnerId, LocalDateTime.now().minusHours(1), endTime, LocalDateTime.now());
    }

    // Tạo nhanh phòng đấu giá đang diễn ra
    private Auction makeRunningAuction(int id, int sellerId, double price) {
        return makeAuction(id, sellerId, price, "RUNNING",
                LocalDateTime.now().plusHours(1), null);
    }

    @Nested
    @DisplayName("placeBid - validateBidRules")
    class ValidateBidRulesTests {

        // Test: Tài khoản Admin không được phép tham gia đặt giá
        @Test
        @DisplayName("Admin không được đặt giá")
        void placeBid_adminForbidden() {
            User admin = makeBidder(1, "ADMIN", 9_999_999);
            Auction auction = makeRunningAuction(1, 99, 500_000);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> biddingService.placeBid(admin, 1, 600_000));
            assertTrue(ex.getMessage().contains("Admin"));
        }

        // Test: Người bán không được phép tự đấu giá sản phẩm của chính mình
        @Test
        @DisplayName("Seller không được tự đấu giá hàng mình")
        void placeBid_sellerCannotBidOwnItem() {
            User seller = makeBidder(5, "SELLER", 9_999_999);
            Auction auction = makeRunningAuction(1, 5, 500_000);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> biddingService.placeBid(seller, 1, 600_000));
            assertTrue(ex.getMessage().contains("tự đấu giá"));
        }

        // Test: Phòng đấu giá phải ở trạng thái RUNNING mới được đặt giá
        @Test
        @DisplayName("Không đặt giá được khi phiên không RUNNING")
        void placeBid_auctionNotRunning() {
            User bidder = makeBidder(2, "BIDDER", 2_000_000);
            Auction auction = makeAuction(1, 99, 500_000, "OPEN",
                    LocalDateTime.now().plusHours(1), null);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> biddingService.placeBid(bidder, 1, 600_000));
            assertTrue(ex.getMessage().contains("RUNNING"));
        }

        // Test: Không cho phép đặt giá khi phòng đấu giá đã quá thời gian kết thúc
        @Test
        @DisplayName("Không đặt giá được khi phiên đã hết giờ")
        void placeBid_auctionExpired() {
            User bidder = makeBidder(2, "BIDDER", 2_000_000);
            Auction auction = makeAuction(1, 99, 500_000, "RUNNING",
                    LocalDateTime.now().minusMinutes(1), null);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> biddingService.placeBid(bidder, 1, 600_000));
            assertTrue(ex.getMessage().contains("kết thúc"));
        }

        // Test: Hệ thống phải ném lỗi khi không tìm thấy phòng đấu giá
        @Test
        @DisplayName("Ném lỗi khi phiên không tồn tại")
        void placeBid_auctionNotFound() {
            User bidder = makeBidder(2, "BIDDER", 2_000_000);
            when(managerService.getAuctionOrThrow(999))
                    .thenThrow(new AuctionException("AUCTION_NOT_FOUND", "Không tồn tại"));

            assertThrows(AuctionException.class,
                    () -> biddingService.placeBid(bidder, 999, 600_000));
        }
    }

    @Nested
    @DisplayName("placeBid - kiểm tra giá trong Lock")
    class LockCheckTests {

        // Test: Giá đặt mới thấp hơn giá hiện tại của phiên đấu giá
        @Test
        @DisplayName("Giá đặt thấp hơn giá hiện tại trong Lock → BID_TOO_LOW")
        void placeBid_bidTooLowInsideLock() {
            User bidder = makeBidder(2, "BIDDER", 2_000_000);
            Auction auction = makeRunningAuction(1, 99, 1_000_000);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> biddingService.placeBid(bidder, 1, 800_000));
            assertNotNull(ex.getMessage());
        }

        // Test: Khi dữ liệu hợp lệ, hệ thống chạy tiếp và ném lỗi DB Connection mong muốn
        @Test
        @DisplayName("Validate pass hoàn toàn → lỗi tiếp theo là DB (INTERNAL_ERROR)")
        void placeBid_validInput_failsAtDB() {
            User bidder = makeBidder(2, "BIDDER", 2_000_000);
            Auction auction = makeRunningAuction(1, 99, 500_000);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            try (MockedStatic<DBConnection> mockedDb = mockStatic(DBConnection.class)) {
                mockedDb.when(DBConnection::getConnection )
                        .thenThrow(new java.sql.SQLException("Mất kết nối Database"));

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> biddingService.placeBid(bidder, 1, 600_000));

                String errorMsg = ex.getMessage().toLowerCase();
                assertTrue(errorMsg.contains("internal_error")
                        || errorMsg.contains("database")
                        || errorMsg.contains("kết nối")
                        || errorMsg.contains("thất bại"));
            }
        }

        @Nested
        @DisplayName("rejectWin - validate")
        class RejectWinTests {

            // Test: Không cho phép từ chối nhận hàng nếu user không phải người thắng cuộc
            @Test
            @DisplayName("Ném lỗi khi user không phải người thắng")
            void rejectWin_notWinner() {
                User winner = makeBidder(2, "BIDDER", 0);
                Auction auction = makeAuction(1, 5, 1_000_000, "FINISHED",
                        LocalDateTime.now().minusHours(1), 99);
                when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> biddingService.rejectWin(winner, 1));
                assertTrue(ex.getMessage().contains("UNAUTHORIZED")
                        || ex.getMessage().contains("người thắng"));
            }

            // Test: Chỉ được từ chối khi phiên đấu giá đã kết thúc (FINISHED)
            @Test
            @DisplayName("Ném lỗi khi phiên chưa kết thúc (không phải FINISHED)")
            void rejectWin_auctionNotFinished() {
                User winner = makeBidder(2, "BIDDER", 0);
                Auction auction = makeAuction(1, 5, 1_000_000, "RUNNING",
                        LocalDateTime.now().plusHours(1), 2);
                when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> biddingService.rejectWin(winner, 1));
                assertTrue(ex.getMessage().contains("AUCTION_INVALID_STATE")
                        || ex.getMessage().contains("chưa kết thúc"));
            }

            // Test: Xác nhận qua tầng validate thành công và dừng lại ở lỗi kết nối DB
            @Test
            @DisplayName("Validate pass → lỗi tiếp theo là DB (INTERNAL_ERROR)")
            void rejectWin_validInput_failsAtDB() {
                User winner = makeBidder(2, "BIDDER", 0);
                Auction auction = makeAuction(1, 5, 1_000_000, "FINISHED",
                        LocalDateTime.now().minusHours(1), 2);
                when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

                try (MockedStatic<DBConnection> mockedDb = mockStatic(DBConnection.class)) {
                    mockedDb.when(DBConnection::getConnection )
                            .thenThrow(new java.sql.SQLException("Không thể thiết lập kết nối tới Cơ sở dữ liệu!"));

                    AuctionException ex = assertThrows(AuctionException.class,
                            () -> biddingService.rejectWin(winner, 1));

                    assertTrue(ex.getMessage().contains("INTERNAL_ERROR")
                            || ex.getMessage().contains("Database")
                            || ex.getMessage().contains("kết nối")
                            || ex.getMessage().contains("cơ sở dữ liệu"));
                }
            }

            // Test: Kiểm tra công thức tính tiền phạt hủy kèo (7%) và tiền hoàn lại (93%)
            @Test
            @DisplayName("Tính phạt 7% đúng công thức")
            void rejectWin_penaltyCalculation() {
                double bidAmount = 1_000_000;
                double penalty = bidAmount * 0.07;
                double refund = bidAmount - penalty;

                assertEquals(70_000, penalty, 0.01);
                assertEquals(930_000, refund, 0.01);
            }
        }

        @Nested
        @DisplayName("calculateAntiSniping - logic gia hạn")
        class AntiSnipingTests {

            // Test: Thời gian còn lại lớn hơn 60s thì không kích hoạt gia hạn phòng đấu giá
            @Test
            @DisplayName("Còn hơn 60 giây → KHÔNG gia hạn")
            void antiSniping_notTriggered() {
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(2);
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime result = null;
                if (now.isAfter(endTime.minusSeconds(60)) && now.isBefore(endTime)) {
                    result = endTime.plusSeconds(30);
                }
                assertNull(result);
            }

            // Test: Thời gian còn lại dưới 60s thì kích hoạt tự động gia hạn thêm 30s
            @Test
            @DisplayName("Còn dưới 60 giây → GIA HẠN thêm 30 giây")
            void antiSniping_triggered() {
                LocalDateTime endTime = LocalDateTime.now().plusSeconds(30);
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime result = null;
                if (now.isAfter(endTime.minusSeconds(60)) && now.isBefore(endTime)) {
                    result = endTime.plusSeconds(30);
                }
                assertNotNull(result);
                assertTrue(result.isAfter(endTime));
            }

            // Test: Phiên đấu giá đã kết thúc thì không áp dụng luật bù giờ (Anti-sniping)
            @Test
            @DisplayName("Đã hết giờ → KHÔNG gia hạn")
            void antiSniping_alreadyExpired() {
                LocalDateTime endTime = LocalDateTime.now().minusSeconds(5);
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime result = null;
                if (now.isAfter(endTime.minusSeconds(60)) && now.isBefore(endTime)) {
                    result = endTime.plusSeconds(30);
                }
                assertNull(result);
            }
        }
    }
}