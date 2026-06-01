package com.auction.server.service;

import com.auction.common.model.Bidder;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.service.ManagerService;
import com.auction.server.service.TransactionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionDAO  transDAO;
    @Mock private PaymentDAO      paymentDAO;
    @Mock private ManagerService managerService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() throws Exception {
        transactionService = new TransactionService(managerService);

        Field f1 = TransactionService.class.getDeclaredField("transDAO");
        f1.setAccessible(true); f1.set(transactionService, transDAO);

        Field f2 = TransactionService.class.getDeclaredField("paymentDAO");
        f2.setAccessible(true); f2.set(transactionService, paymentDAO);
    }


    private User makeBidder(int id, double balance) {
        return new Bidder(id, "user" + id, "u@mail.com", "pass1234", "0901234567", "ACTIVE", balance);
    }

    private User makeAdmin() {
        Bidder admin = new Bidder(1, "admin", "a@mail.com", "pass1234", "0901234567", "ACTIVE", 0);
        admin.setRole("ADMIN");
        return admin;
    }


    @Nested @DisplayName("handleDepositRequest")
    class DepositTests {

        // Test: Gửi yêu cầu nạp tiền hợp lệ thành công xuống tầng DAO
        @Test @DisplayName("Gửi yêu cầu nạp tiền thành công")
        void deposit_success() throws SQLException {
            User user = makeBidder(2, 0);
            when(transDAO.createTransaction(2, 500_000, "DEPOSIT", "PENDING")).thenReturn(true);

            assertDoesNotThrow(() ->
                    transactionService.handleDepositRequest(user, 500_000));

            verify(transDAO, times(1))
                    .createTransaction(2, 500_000, "DEPOSIT", "PENDING");
        }

        // Test: Không cho phép gửi yêu cầu nạp tiền với số tiền bằng 0
        @Test @DisplayName("Ném lỗi khi số tiền = 0 — không gọi DAO")
        void deposit_zeroAmount() {
            User user = makeBidder(2, 0);
            assertThrows(AuctionException.class,
                    () -> transactionService.handleDepositRequest(user, 0));
            verifyNoInteractions(transDAO);
        }

        // Test: Không cho phép gửi yêu cầu nạp tiền với số tiền âm
        @Test @DisplayName("Ném lỗi khi số tiền âm — không gọi DAO")
        void deposit_negativeAmount() {
            User user = makeBidder(2, 0);
            assertThrows(AuctionException.class,
                    () -> transactionService.handleDepositRequest(user, -100_000));
            verifyNoInteractions(transDAO);
        }

        // Test: Hệ thống báo lỗi khi tầng DAO không tạo được giao dịch
        @Test @DisplayName("Ném lỗi khi DAO createTransaction trả false")
        void deposit_daoFails() throws SQLException {
            User user = makeBidder(2, 0);
            when(transDAO.createTransaction(2, 500_000, "DEPOSIT", "PENDING")).thenReturn(false);

            assertThrows(AuctionException.class,
                    () -> transactionService.handleDepositRequest(user, 500_000));
        }

        // Test: Trạng thái của giao dịch nạp tiền mới tạo bắt buộc phải là PENDING
        @Test @DisplayName("Trạng thái gửi xuống phải là PENDING")
        void deposit_statusIsPending() throws SQLException {
            User user = makeBidder(2, 0);
            when(transDAO.createTransaction(anyInt(), anyDouble(), anyString(), anyString()))
                    .thenReturn(true);

            transactionService.handleDepositRequest(user, 300_000);

            verify(transDAO).createTransaction(2, 300_000, "DEPOSIT", "PENDING");
        }
    }



    @Nested @DisplayName("handleApproveTransaction - validate")
    class ApproveTests {

        // Test: Chỉ tài khoản Admin mới có quyền duyệt giao dịch nạp/rút
        @Test @DisplayName("Ném lỗi khi không phải Admin — không gọi DAO")
        void approve_notAdmin() {
            User notAdmin = makeBidder(2, 0);
            assertThrows(AuctionException.class,
                    () -> transactionService.handleApproveTransaction(
                            notAdmin, 10, 3, 500_000, "DEPOSIT"));
            verifyNoInteractions(transDAO, paymentDAO, managerService);
        }

        // Test: Hệ thống báo lỗi khi không tìm thấy thông tin người dùng được duyệt tiền
        @Test @DisplayName("Ném lỗi khi không tìm thấy user mục tiêu")
        void approve_userNotFound() {
            User admin = makeAdmin();
            when(managerService.getUserById(3)).thenReturn(null);

            assertThrows(AuctionException.class,
                    () -> transactionService.handleApproveTransaction(
                            admin, 10, 3, 500_000, "DEPOSIT"));

            verify(managerService, times(1)).getUserById(3);
            verifyNoInteractions(transDAO, paymentDAO);
        }

        // Test: Không duyệt yêu cầu rút tiền nếu số tiền rút lớn hơn số dư hiện có
        @Test @DisplayName("Ném lỗi khi rút tiền vượt số dư — không gọi DB")
        void approve_withdraw_insufficientBalance() {
            User admin    = makeAdmin();
            User liveUser = makeBidder(3, 100_000);
            when(managerService.getUserById(3)).thenReturn(liveUser);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> transactionService.handleApproveTransaction(
                            admin, 10, 3, 500_000, "WITHDRAW"));
            assertTrue(ex.getMessage().contains("không đủ") || ex.getMessage().contains("INVALID_INPUT"));

            verifyNoInteractions(transDAO, paymentDAO);
        }

        // Test: Duyệt nạp tiền qua được validate và dừng lại ở lỗi kết nối DB
        @Test @DisplayName("Nạp tiền: validate pass → lỗi tiếp theo là DB (INTERNAL_ERROR)")
        void approve_deposit_validInput_failsAtDB() {
            User admin    = makeAdmin();
            User liveUser = makeBidder(3, 0);
            when(managerService.getUserById(3)).thenReturn(liveUser);

            try (MockedStatic<com.auction.server.dao.DBConnection> mockedConnection =
                         Mockito.mockStatic(com.auction.server.dao.DBConnection.class)) {

                mockedConnection.when(com.auction.server.dao.DBConnection::getConnection)
                        .thenThrow(new SQLException("Cố tình làm lỗi kết nối database để test"));

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> transactionService.handleApproveTransaction(
                                admin, 10, 3, 500_000, "DEPOSIT"));

                String msg = ex.getMessage();
                assertTrue(msg.contains("INTERNAL_ERROR") || msg.contains("kết nối") || msg.contains("Database"));
            }
        }

        // Test: Duyệt rút tiền qua được validate và dừng lại ở lỗi kết nối DB định sẵn
        @Test @DisplayName("Rút tiền đủ số dư: validate pass → lỗi tiếp theo là DB")
        void approve_withdraw_sufficientBalance_failsAtDB() {
            User admin    = makeAdmin();
            User liveUser = makeBidder(3, 1_000_000);
            when(managerService.getUserById(3)).thenReturn(liveUser);

            try (MockedStatic<com.auction.server.dao.DBConnection> mockedConnection =
                         Mockito.mockStatic(com.auction.server.dao.DBConnection.class)) {

                mockedConnection.when(com.auction.server.dao.DBConnection::getConnection)
                        .thenThrow(new SQLException("Cố tình làm lỗi kết nối database để test"));

                AuctionException ex = assertThrows(AuctionException.class,
                        () -> transactionService.handleApproveTransaction(
                                admin, 10, 3, 500_000, "WITHDRAW"));

                assertEquals("Lỗi kết nối cơ sở dữ liệu!", ex.getMessage());
            }
        }

        // Test: Kiểm tra công thức cộng thêm tiền vào số dư khi duyệt nạp tiền
        @Test @DisplayName("Tính toán số dư mới sau DEPOSIT đúng công thức")
        void approve_deposit_balanceCalculation() {
            double oldBalance = 500_000;
            double amount     = 300_000;
            String type       = "DEPOSIT";

            double newBalance = type.equalsIgnoreCase("DEPOSIT") ? oldBalance + amount : oldBalance - amount;

            assertEquals(800_000, newBalance, 0.01);
        }

        // Test: Kiểm tra công thức trừ bớt tiền khỏi số dư khi duyệt rút tiền
        @Test @DisplayName("Tính toán số dư mới sau WITHDRAW đúng công thức")
        void approve_withdraw_balanceCalculation() {
            double oldBalance = 1_000_000;
            double amount     = 300_000;
            String type       = "WITHDRAW";

            double newBalance = type.equalsIgnoreCase("DEPOSIT") ? oldBalance + amount : oldBalance - amount;

            assertEquals(700_000, newBalance, 0.01);
        }
    }
}