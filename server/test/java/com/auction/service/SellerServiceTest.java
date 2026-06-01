package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.service.ManagerService;
import com.auction.server.service.SellerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock
    private ManagerService managerService;
    @Mock private AuctionDAO auctionDAO;

    private SellerService sellerService;

    @BeforeEach
    void setUp() throws Exception {
        sellerService = new SellerService(managerService);

        Field field = SellerService.class.getDeclaredField("auctionDAO");
        field.setAccessible(true);
        field.set(sellerService, auctionDAO);
    }


    private User makeSeller(int id) {
        return new Seller(id, "seller" + id, "s@mail.com", "pass1234", "0901234567", "ACTIVE", 0);
    }

    private User makeBidder(int id) {
        return new Bidder(id, "bidder" + id, "b@mail.com", "pass1234", "0901234567", "ACTIVE", 0);
    }

    private Auction makeAuction(int id, int sellerId, String status) {
        return new Auction(id, 10, sellerId, status, 500_000, 500_000, 0, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), LocalDateTime.now());
    }


    @Nested
    @DisplayName("requestCreateAuction")
    class RequestCreateTests {

        // Test: Người bán gửi yêu cầu tạo phòng đấu giá hợp lệ thành công
        @Test
        @DisplayName("Seller tạo phiên thành công — gọi scheduleAuction")
        void requestCreate_success() {
            User seller = makeSeller(1);
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end   = LocalDateTime.now().plusDays(2);
            doNothing().when(managerService).scheduleAuction(10, start, end);

            assertDoesNotThrow(() ->
                    sellerService.requestCreateAuction(seller, 10, start, end));

            verify(managerService, times(1)).scheduleAuction(10, start, end);
        }

        // Test: Không cho phép tài khoản người mua gửi yêu cầu tạo phòng đấu giá
        @Test @DisplayName("Bidder không được tạo phiên — không gọi scheduleAuction")
        void requestCreate_bidderForbidden() {
            User bidder = makeBidder(2);
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end   = LocalDateTime.now().plusDays(2);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> sellerService.requestCreateAuction(bidder, 10, start, end));
            assertTrue(ex.getMessage().contains("Seller"));

            verify(managerService, never()).scheduleAuction(anyInt(), any(), any());
        }

        // Test: Không cho phép tài khoản quản trị viên gửi yêu cầu tạo phòng đấu giá
        @Test @DisplayName("Admin không được tạo phiên — không gọi scheduleAuction")
        void requestCreate_adminForbidden() {
            User admin = makeBidder(1);
            admin.setRole("ADMIN");
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end   = LocalDateTime.now().plusDays(2);

            assertThrows(AuctionException.class,
                    () -> sellerService.requestCreateAuction(admin, 10, start, end));
            verify(managerService, never()).scheduleAuction(anyInt(), any(), any());
        }

        // Test: Hệ thống truyền lỗi ra ngoài khi tầng quản lý lập lịch phòng thất bại
        @Test @DisplayName("ManagerService throw → lỗi truyền ra ngoài")
        void requestCreate_managerFails() {
            User seller = makeSeller(1);
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end   = LocalDateTime.now().plusDays(2);
            doThrow(new AuctionException("ITEM_NOT_FOUND", "Không tìm thấy item"))
                    .when(managerService).scheduleAuction(99, start, end);

            assertThrows(AuctionException.class,
                    () -> sellerService.requestCreateAuction(seller, 99, start, end));
        }
    }



    @Nested @DisplayName("confirmSale")
    class ConfirmSaleTests {

        // Test: Xác nhận bán thành công và cập nhật trạng thái phòng thành SOLD trên RAM
        @Test @DisplayName("Seller xác nhận bán thành công — RAM cập nhật SOLD")
        void confirmSale_success() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "FINISHED");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "SOLD")).thenReturn(true);

            assertDoesNotThrow(() -> sellerService.confirmSale(seller, 1));
            assertEquals("SOLD", auction.getAuctionStatus());
            verify(auctionDAO, times(1)).updateStatus(1, "SOLD");
        }

        // Test: Không cho phép tài khoản người mua xác nhận bán hàng
        @Test @DisplayName("Bidder không được xác nhận bán")
        void confirmSale_bidderForbidden() {
            User bidder = makeBidder(2);
            assertThrows(AuctionException.class,
                    () -> sellerService.confirmSale(bidder, 1));
            verifyNoInteractions(auctionDAO, managerService);
        }

        // Test: Báo lỗi khi người bán không phải chủ sở hữu của phòng đấu giá
        @Test @DisplayName("Ném lỗi khi Seller không phải chủ phiên")
        void confirmSale_notOwner() {
            User seller = makeSeller(2);
            Auction auction = makeAuction(1, 99, "FINISHED");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> sellerService.confirmSale(seller, 1));
            assertTrue(ex.getMessage().contains("chủ phiên") || ex.getMessage().contains("UNAUTHORIZED"));

            verify(auctionDAO, never()).updateStatus(anyInt(), anyString());
        }

        // Test: Tầng DAO cập nhật thất bại thì hệ thống không thay đổi trạng thái trên RAM
        @Test @DisplayName("DAO updateStatus trả false → không cập nhật RAM")
        void confirmSale_daoReturnsFalse() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "FINISHED");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "SOLD")).thenReturn(false);

            assertDoesNotThrow(() -> sellerService.confirmSale(seller, 1));
            assertNotEquals("SOLD", auction.getAuctionStatus());
        }

        // Test: Hệ thống ném lỗi khi không tìm thấy phòng đấu giá cần xác nhận bán
        @Test @DisplayName("Phiên không tồn tại → ném lỗi từ managerService")
        void confirmSale_auctionNotFound() {
            User seller = makeSeller(1);
            when(managerService.getAuctionOrThrow(99))
                    .thenThrow(new AuctionException("AUCTION_NOT_FOUND", "Không tồn tại"));

            assertThrows(AuctionException.class,
                    () -> sellerService.confirmSale(seller, 99));
        }
    }



    @Nested @DisplayName("requestCancelAuction")
    class RequestCancelTests {

        // Test: Hủy thành công phòng chưa diễn ra và chuyển trạng thái thành chờ Admin duyệt
        @Test @DisplayName("Hủy phiên OPEN thành công — RAM cập nhật WAITING_FOR_ADMIN")
        void cancel_openAuction_success() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "OPEN");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "WAITING_FOR_ADMIN")).thenReturn(true);

            assertDoesNotThrow(() -> sellerService.requestCancelAuction(seller, 1));
            assertEquals("WAITING_FOR_ADMIN", auction.getAuctionStatus());
        }

        // Test: Chấp nhận gửi lại yêu cầu hủy đối với phòng đã ở trạng thái chờ duyệt
        @Test @DisplayName("Hủy phiên WAITING_FOR_ADMIN thành công")
        void cancel_waitingAuction_success() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "WAITING_FOR_ADMIN");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "WAITING_FOR_ADMIN")).thenReturn(true);

            assertDoesNotThrow(() -> sellerService.requestCancelAuction(seller, 1));
        }

        // Test: Không cho phép hủy phòng đấu giá khi phiên đấu giá đang diễn ra
        @Test @DisplayName("Không được hủy phiên đang RUNNING")
        void cancel_runningAuction_forbidden() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "RUNNING");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> sellerService.requestCancelAuction(seller, 1));
            assertTrue(ex.getMessage().contains("đang chạy") || ex.getMessage().contains("AUCTION_INVALID_STATE"));

            verify(auctionDAO, never()).updateStatus(anyInt(), anyString());
        }

        // Test: Không cho phép gửi yêu cầu hủy phòng đấu giá đã bán thành công
        @Test @DisplayName("Không được hủy phiên đã SOLD")
        void cancel_soldAuction_forbidden() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "SOLD");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            assertThrows(AuctionException.class,
                    () -> sellerService.requestCancelAuction(seller, 1));
            verify(auctionDAO, never()).updateStatus(anyInt(), anyString());
        }

        // Test: Không cho phép tài khoản người mua gửi yêu cầu hủy phòng đấu giá
        @Test @DisplayName("Bidder không được hủy phiên")
        void cancel_bidderForbidden() {
            User bidder = makeBidder(2);
            assertThrows(AuctionException.class,
                    () -> sellerService.requestCancelAuction(bidder, 1));
            verifyNoInteractions(auctionDAO, managerService);
        }

        // Test: Hệ thống ném lỗi khi không tìm thấy phòng đấu giá cần gửi yêu cầu hủy
        @Test @DisplayName("Phiên không tồn tại → ném lỗi từ managerService")
        void cancel_auctionNotFound() {
            User seller = makeSeller(1);
            when(managerService.getAuctionOrThrow(99))
                    .thenThrow(new AuctionException("AUCTION_NOT_FOUND", "Không tồn tại"));

            assertThrows(AuctionException.class,
                    () -> sellerService.requestCancelAuction(seller, 99));
        }

        // Test: Tầng DAO cập nhật thất bại thì trạng thái phòng trên RAM được giữ nguyên
        @Test @DisplayName("DAO updateStatus trả false → không cập nhật RAM")
        void cancel_daoReturnsFalse() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "OPEN");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "WAITING_FOR_ADMIN")).thenReturn(false);

            assertDoesNotThrow(() -> sellerService.requestCancelAuction(seller, 1));
            assertNotEquals("WAITING_FOR_ADMIN", auction.getAuctionStatus());
        }
    }
}