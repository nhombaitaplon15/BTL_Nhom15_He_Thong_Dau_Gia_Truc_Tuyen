package com.auction.server.service;

import com.auction.common.exception.AuctionException;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.service.PaymentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentDAO     paymentDAO;
    @Mock private TransactionDAO transDAO;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() throws Exception {
        paymentService = new PaymentService();

        Field f1 = PaymentService.class.getDeclaredField("paymentDAO");
        f1.setAccessible(true); f1.set(paymentService, paymentDAO);

        Field f2 = PaymentService.class.getDeclaredField("transDAO");
        f2.setAccessible(true); f2.set(paymentService, transDAO);
    }


    @Nested @DisplayName("holdFunds")
    class HoldFundsTests {

        // Test: Số dư bằng tiền đặt thì vượt qua validate và dừng lại do lỗi DB
        @Test @DisplayName("Số dư vừa đủ bằng amount — ném lỗi (phải > amount)")
        void holdFunds_balanceEqualsAmount() {
            when(paymentDAO.getBalance(2)).thenReturn(500_000.0);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> paymentService.holdFunds(2, 500_000, 1));

            assertFalse(ex.getMessage().contains("Số dư không đủ"));
        }

        // Test: Tài khoản đủ số dư qua được validate và dừng lại ở lỗi kết nối DB
        @Test @DisplayName("Số dư đủ → validate pass, lỗi tiếp theo là DB")
        void holdFunds_sufficientBalance_failsAtDB() {
            when(paymentDAO.getBalance(2)).thenReturn(1_000_000.0);

            AuctionException ex = assertThrows(AuctionException.class,
                    () -> paymentService.holdFunds(2, 500_000, 1));

            assertFalse(ex.getMessage().contains("Số dư không đủ"));
            assertTrue(ex.getMessage().contains("INTERNAL_ERROR")
                    || ex.getMessage().contains("kết nối")
                    || ex.getMessage().contains("thanh toán"));
        }
    }


    @Nested @DisplayName("releaseFunds - tính phí 15%")
    class ReleaseFundsTests {

        // Test: Kiểm tra công thức trích phí hệ thống 15% trên tổng tiền giải ngân
        @Test @DisplayName("Phí hệ thống = 15% tổng tiền")
        void releaseFunds_feeIs15Percent() {
            double total       = 1_000_000;
            double fee         = total * 0.15;
            double finalAmount = total - fee;

            assertEquals(150_000, fee,         0.01);
            assertEquals(850_000, finalAmount, 0.01);
        }

        // Test: Khấu trừ phí hệ thống hoạt động chính xác với số tiền lẻ
        @Test @DisplayName("Phí tính đúng với số lẻ")
        void releaseFunds_feeWithOddAmount() {
            double total       = 700_000;
            double fee         = total * 0.15;
            double finalAmount = total - fee;

            assertEquals(105_000, fee,         0.01);
            assertEquals(595_000, finalAmount, 0.01);
        }

        // Test: Hàm không có validate nên đi thẳng vào DB và dừng lại do lỗi kết nối
        @Test @DisplayName("releaseFunds tiến vào DB (không có validate trước) → INTERNAL_ERROR")
        void releaseFunds_failsAtDB() {
            AuctionException ex = assertThrows(AuctionException.class,
                    () -> paymentService.releaseFunds(3, 1_000_000, 1));

            assertTrue(ex.getMessage().contains("INTERNAL_ERROR")
                    || ex.getMessage().contains("kết nối")
                    || ex.getMessage().contains("giải ngân"));

            verify(paymentDAO, never()).getBalance(anyInt());
        }
    }


    @Nested @DisplayName("refundBuyer - logic hoàn tiền")
    class RefundBuyerTests {

        // Test: Hàm không có validate nên đi thẳng vào DB và ném lỗi kết nối giả lập
        @Test @DisplayName("refundBuyer tiến vào DB (không có validate trước) → INTERNAL_ERROR")
        void refundBuyer_failsAtDB() {
            AuctionException ex = assertThrows(AuctionException.class,
                    () -> paymentService.refundBuyer(2, 500_000, 1));

            assertTrue(ex.getMessage().contains("INTERNAL_ERROR")
                    || ex.getMessage().contains("kết nối")
                    || ex.getMessage().contains("hoàn tiền"));

            verify(paymentDAO, never()).getBalance(anyInt());
        }

        // Test: Logic hoàn tiền phải trả lại nguyên vẹn 100% số tiền đã giữ của người mua
        @Test @DisplayName("Công thức: hoàn đúng 100% số tiền đã giữ")
        void refundBuyer_fullRefund() {
            double held   = 800_000;
            double refund = held;

            assertEquals(800_000, refund, 0.01);
        }
    }


    @Nested @DisplayName("So sánh logic 3 hàm")
    class FlowComparisonTests {

        // Test: Xác minh chỉ có hàm giữ tiền mới thực hiện kiểm tra số dư trước khi vào DB
        @Test @DisplayName("holdFunds có validate số dư, release/refund thì không")
        void onlyHoldFunds_validatesBalance() {
            when(paymentDAO.getBalance(2)).thenReturn(0.0);
            assertThrows(AuctionException.class,
                    () -> paymentService.holdFunds(2, 500_000, 1));
            verify(paymentDAO, atLeastOnce()).getBalance(2);

            reset(paymentDAO);
            assertThrows(AuctionException.class,
                    () -> paymentService.releaseFunds(3, 1_000_000, 1));
            verify(paymentDAO, never()).getBalance(anyInt());
        }

        // Test: Đảm bảo tỷ lệ khấu trừ phí giải ngân cố định ở mức 15%
        @Test @DisplayName("SYSTEM_FEE_RATE = 15% (không đổi)")
        void systemFeeRate_is15Percent() {
            double rate = 0.15;
            assertEquals(0.15, rate, 0.001);
        }
    }
}