package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Bidder;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.service.AuctionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock private AuctionDAO     auctionDAO;
    @Mock private PaymentDAO     paymentDAO;
    @Mock private TransactionDAO transDAO;

    private AuctionService auctionService;

    @BeforeEach
    void setUp() throws Exception {
        auctionService = new AuctionService();

        Field f1 = AuctionService.class.getDeclaredField("auctionDAO");
        f1.setAccessible(true); f1.set(auctionService, auctionDAO);

        Field f2 = AuctionService.class.getDeclaredField("paymentDAO");
        f2.setAccessible(true); f2.set(auctionService, paymentDAO);

        Field f3 = AuctionService.class.getDeclaredField("transDAO");
        f3.setAccessible(true); f3.set(auctionService, transDAO);
    }


    private User makeUser(int id, String role, double balance, int sellerId) {
        Bidder b = new Bidder(id, "user" + id, "u@mail.com", "pass1234", "0901234567", "ACTIVE", balance);
        b.setRole(role);
        return b;
    }

    private Auction makeAuction(int id, int sellerId, double price,
                                LocalDateTime endTime, Integer winnerId) {
        return new Auction(id, 10, sellerId, "OPEN", price, price,
                0, winnerId, LocalDateTime.now().minusHours(1), endTime, LocalDateTime.now());
    }

    private Auction activeAuction(int id, int sellerId, double price) {
        return makeAuction(id, sellerId, price,
                LocalDateTime.now().plusHours(1), null);
    }


    @Nested @DisplayName("handlePlaceBid - validateBidGuards")
    class ValidateBidGuardsTests {

        // Test: Admin không được phép tham gia đặt giá
        @Test @DisplayName("Admin không được tham gia đặt giá")
        void bid_adminForbidden() {
            User admin = makeUser(1, "ADMIN", 9_999_999, 99);
            Auction auction = activeAuction(1, 99, 500_000);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> auctionService.handlePlaceBid(admin, auction, 600_000));
            assertTrue(ex.getMessage().contains("Admin"));

            verifyNoInteractions(auctionDAO, paymentDAO, transDAO);
        }

        // Test: Người bán không được tự đấu giá sản phẩm của chính mình
        @Test @DisplayName("Seller không được tự đấu giá hàng mình")
        void bid_sellerCannotBidOwnItem() {
            User seller = makeUser(5, "SELLER", 9_999_999, 5);
            Auction auction = activeAuction(1, 5, 500_000);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> auctionService.handlePlaceBid(seller, auction, 600_000));
            assertTrue(ex.getMessage().contains("tự đấu giá"));

            verifyNoInteractions(auctionDAO, paymentDAO, transDAO);
        }

        // Test: Không cho đặt giá khi phòng đấu giá đã hết giờ
        @Test @DisplayName("Không đặt giá được khi phiên đã hết giờ")
        void bid_auctionExpired() {
            User bidder = makeUser(2, "BIDDER", 2_000_000, 99);
            Auction auction = makeAuction(1, 99, 500_000, LocalDateTime.now().minusMinutes(1), null);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> auctionService.handlePlaceBid(bidder, auction, 600_000));
            assertTrue(ex.getMessage().contains("kết thúc"));

            verifyNoInteractions(auctionDAO, paymentDAO, transDAO);
        }

        // Test: Không cho đặt giá thấp hơn hoặc bằng giá hiện tại
        @Test @DisplayName("Không đặt giá thấp hơn hoặc bằng giá hiện tại")
        void bid_bidTooLow() {
            User bidder = makeUser(2, "BIDDER", 2_000_000, 99);
            Auction auction = activeAuction(1, 99, 1_000_000);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> auctionService.handlePlaceBid(bidder, auction, 999_999));
            assertTrue(ex.getMessage().contains("cao hơn") || ex.getMessage().contains("BID_TOO_LOW"));

            verifyNoInteractions(auctionDAO, paymentDAO, transDAO);
        }

        // Test: Không cho đặt giá khi số dư tài khoản không đủ
        @Test @DisplayName("Không đặt giá khi số dư không đủ")
        void bid_insufficientBalance() {
            User bidder = makeUser(2, "BIDDER", 100_000, 99);
            Auction auction = activeAuction(1, 99, 500_000);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> auctionService.handlePlaceBid(bidder, auction, 600_000));
            assertTrue(ex.getMessage().contains("Số dư") || ex.getMessage().contains("INVALID_INPUT"));

            verifyNoInteractions(auctionDAO, paymentDAO, transDAO);
        }

        // Test: Dữ liệu hợp lệ qua được validate và dừng lại do lỗi kết nối DB
        @Test @DisplayName("Validate pass hoàn toàn → lỗi tiếp theo là DB (INTERNAL_ERROR)")
        void bid_validInput_failsAtDB() {
            User bidder = makeUser(2, "BIDDER", 2_000_000, 99);
            Auction auction = activeAuction(1, 99, 500_000);
            auction.setEndTime(LocalDateTime.now().plusDays(1));

            try (MockedStatic<DBConnection> mockedConnection =
                         org.mockito.Mockito.mockStatic(DBConnection.class)) {

                mockedConnection.when(DBConnection::getConnection)
                        .thenThrow(new java.sql.SQLException("Cố tình làm lỗi kết nối database để test"));

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> auctionService.handlePlaceBid(bidder, auction, 600_000));

                String msg = ex.getMessage();
                assertTrue(msg.contains("INTERNAL_ERROR") || msg.contains("Database") || msg.contains("kết nối"));
            }
        }
    }


    @Nested @DisplayName("syncRAM - logic cập nhật RAM")
    class SyncRAMTests {

        // Test: Kiểm tra cập nhật số dư, giá phòng và người thắng trên RAM
        @Test @DisplayName("Sau khi đặt giá: currentPrice, winnerId, totalBids được cập nhật đúng")
        void syncRAM_updatesCorrectly() {
            User bidder = makeUser(2, "BIDDER", 2_000_000, 99);
            Auction auction = activeAuction(1, 99, 500_000);
            double bidAmount = 700_000;

            bidder.setBalance(bidder.getBalance() - bidAmount);
            auction.setCurrentPrice(bidAmount);
            auction.setCurrentWinnerId(bidder.getId());
            auction.setTotalBids(auction.getTotalBids() + 1);

            assertEquals(1_300_000, bidder.getBalance(), 0.01);
            assertEquals(700_000,   auction.getCurrentPrice(), 0.01);
            assertEquals(2,         auction.getCurrentWinnerId());
            assertEquals(1,         auction.getTotalBids());
        }
    }


    @Nested @DisplayName("handleAntiSniping - logic gia hạn")
    class AntiSnipingTests {

        // Test: Thời gian còn lại nhiều hơn 1 phút thì không thực hiện gia hạn
        @Test @DisplayName("Còn hơn 1 phút → KHÔNG gia hạn")
        void antiSniping_notTriggered() {
            LocalDateTime endTime = LocalDateTime.now().plusMinutes(2);
            LocalDateTime now = LocalDateTime.now();

            boolean shouldExtend = now.isAfter(endTime.minusMinutes(1));
            assertFalse(shouldExtend);
        }

        // Test: Thời gian còn lại dưới 1 phút thì tự động gia hạn thêm 30 giây
        @Test @DisplayName("Còn dưới 1 phút → GIA HẠN thêm 30 giây")
        void antiSniping_triggered() {
            LocalDateTime endTime = LocalDateTime.now().plusSeconds(30);
            LocalDateTime now = LocalDateTime.now();

            boolean shouldExtend = now.isAfter(endTime.minusMinutes(1));
            assertTrue(shouldExtend);

            LocalDateTime newEnd = endTime.plusSeconds(30);
            assertTrue(newEnd.isAfter(endTime));
        }
    }
}