package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.AuctionDAO;
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

    @Mock private ManagerService managerService;
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

    private Auction makeAuction(int id, int sellerId, String status) {
        return new Auction(id, 10, sellerId, status, 500_000, 500_000, 0, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), LocalDateTime.now());
    }

    @Nested
    @DisplayName("Request Create Auction")
    class RequestCreateTests {
        @Test @DisplayName("Seller tạo phiên thành công")
        void requestCreate_success() {
            User seller = makeSeller(1);
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end   = LocalDateTime.now().plusDays(2);
            sellerService.requestCreateAuction(seller, 10, start, end);
            verify(managerService, times(1)).scheduleAuction(10, start, end);
        }
    }

    @Nested @DisplayName("Confirm Sale")
    class ConfirmSaleTests {
        @Test @DisplayName("Xác nhận bán thành công")
        void confirmSale_success() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "FINISHED");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "SOLD")).thenReturn(true);

            sellerService.confirmSale(seller, 1);
            assertEquals("SOLD", auction.getAuctionStatus());
        }
    }

    @Nested @DisplayName("Edit Auction")
    class EditAuctionTests {
        @Test @DisplayName("Edit phiên WAITING thành công")
        void editAuction_success() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "WAITING_FOR_ADMIN");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateAuction(any())).thenReturn(true);

            assertDoesNotThrow(() -> sellerService.editAuction(seller, auction));
        }

        @Test @DisplayName("Edit phiên đã chạy -> Lỗi")
        void editAuction_invalidState() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "OPEN");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            assertThrows(AuctionException.class, () -> sellerService.editAuction(seller, auction));
        }
    }

    @Nested @DisplayName("Request Cancel Auction")
    class RequestCancelTests {
        @Test @DisplayName("Hủy phiên WAITING -> Xóa vĩnh viễn")
        void cancel_waiting_delete() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "WAITING_FOR_ADMIN");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            sellerService.requestCancelAuction(seller, 1);
            verify(auctionDAO).deleteAuction(1);
        }

        @Test @DisplayName("Hủy phiên OPEN -> Cập nhật CANCELED")
        void cancel_open_update() {
            User seller = makeSeller(1);
            Auction auction = makeAuction(1, 1, "OPEN");
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "CANCELED")).thenReturn(true);

            sellerService.requestCancelAuction(seller, 1);
            verify(auctionDAO).updateStatus(1, "CANCELED");
        }
    }
}
