package com.auction.server.service;

import com.auction.common.model.*;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionDAO transDAO;
    @Mock private PaymentDAO paymentDAO;
    @Mock private ManagerService managerService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() throws Exception {
        transactionService = new TransactionService(managerService);
        setPrivateField(transactionService, "transDAO", transDAO);
        setPrivateField(transactionService, "paymentDAO", paymentDAO);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private User makeBidder(int id, double balance) {
        return new Bidder(id, "user" + id, "u@mail.com", "pass1234", "0901234567", "ACTIVE", balance);
    }

    @Nested
    class DepositTests {
        @Test
        void deposit_success() throws SQLException {
            User user = makeBidder(2, 0);
            // Sửa lỗi: Khớp đúng 5 tham số của hàm transDAO.createTransaction
            when(transDAO.createTransaction(eq(2), eq(500_000.0), eq("DEPOSIT"), eq("PENDING"), anyString()))
                    .thenReturn(true);

            assertDoesNotThrow(() -> transactionService.handleDepositRequest(user, 500_000));
            verify(transDAO).createTransaction(eq(2), eq(500_000.0), eq("DEPOSIT"), eq("PENDING"), anyString());
        }

        @Test
        void deposit_invalidAmount() {
            User user = makeBidder(2, 0);
            assertThrows(AuctionException.class, () -> transactionService.handleDepositRequest(user, -100));
            verifyNoInteractions(transDAO);
        }
    }

    @Nested
    class ApprovalTests {
        @Test
        void approve_notAdmin_fails() {
            User bidder = makeBidder(2, 0);
            assertThrows(AuctionException.class, () ->
                    transactionService.handleApproveTransaction(bidder, 1, 2, 100, "DEPOSIT"));
            verifyNoInteractions(paymentDAO);
        }

        @Test
        void approve_deposit_success() throws Exception {
            User admin = makeBidder(1, 0);
            admin.setRole("ADMIN");
            User target = makeBidder(2, 100);

            when(managerService.getUserById(2)).thenReturn(target);

            try (var dbMock = mockStatic(DBConnection.class)) {
                var conn = mock(java.sql.Connection.class);
                dbMock.when(DBConnection::getConnection).thenReturn(conn);

                when(paymentDAO.updateBalance(any(), eq(2), eq(100.0), eq("+"))).thenReturn(true);
                when(transDAO.updateTransactionStatus(any(), eq(1), eq("SUCCESS"))).thenReturn(true);

                assertDoesNotThrow(() -> transactionService.handleApproveTransaction(admin, 1, 2, 100, "DEPOSIT"));
                assertEquals(200.0, target.getBalance());
            }
        }
    }

    @Nested
    class AuctionPaymentTests {
        @Test
        void processPayment_unauthorized_fails() {
            Auction auction = mock(Auction.class);
            when(auction.getCurrentWinnerId()).thenReturn(99);
            when(managerService.getAuctionOrThrow(1)).thenReturn(auction);

            assertThrows(AuctionException.class, () -> transactionService.processAuctionWinnerPayment(1, 10));
        }
    }
}
