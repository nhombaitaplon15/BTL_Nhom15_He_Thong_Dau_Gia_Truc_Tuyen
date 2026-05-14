package com.auction.service;

import com.auction.common.model.Bidder;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName(" Xử lý nạp và rút tiền")
public class TransactionServiceTest {
    private Transaction transaction;
    private TransactionService transactionService;
    private User bidder;
    @BeforeEach
    void setUp() {
        transaction        = new Transaction();
        transactionService = new TransactionService(transaction);
        bidder = new Bidder(1, "Alice", "alice@mail.com", "password123", "0901234567", "ACTIVE");
        bidder.setBalance(2000000);
    }
    // Test chuyển tiền
    // số tiền chuyển phải lớn hơn 0
    @Test
    @DisplayName("trường hợp hợp lệ:số tiền > 0 nên tạo yêu cầu thành Pending")
    void deposit_success() {
        transactionService.deposit(bidder, 500000);
        List<TransactionRequest> pending = transaction.getPendingTransactions();
        assertEquals(1, pending.size());
        assertEquals("DEPOSIT", pending.get(0).getType());
        assertEquals("PENDING", pending.get(0).getTransactionStatus());
    }
    //test : chuyển boa nhiêu thì vào tài khoản đúng như thế
    @Test
    @DisplayName("trường hợp hợp lệ: số tiền nạp được ghi đúng vào request")
    void deposit_correctAmount() {
        transactionService.deposit(bidder, 300000);
        assertEquals(300000, transaction.getPendingTransactions().get(0).getAmount(), 0.01);
    }
    //lỗi tiền chuyển =0
    @Test
    @DisplayName("trường hợp không hợp lệ: Số tiền = 0 throw IllegalArgumentException")
    void deposit_zeroAmount_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.deposit(bidder, 0));
    }
    // lỗi tền chuyển âm
    @Test
    @DisplayName("trường hợp không hợp lệ:Số tiền âm throw IllegalArgumentException")
    void deposit_negativeAmount_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.deposit(bidder, -100000));
    }
    // Test: tiền chuyển phải chờ duyệt
    @Test
    @DisplayName("trường hợp hợp lệ: balance không thay đổi ngay mà phải chờ admin duyệt")
    void deposit_balanceUnchangedBeforeApproval() {
        transactionService.deposit(bidder, 500000);
        assertEquals(2000000, bidder.getBalance(), 0.01);
    }
    //Test rút tiền
    //test số dư phải đủ tiền để chuyển đi
    @Test
    @DisplayName("trường hợp hợp lệ: số tiền hợp lệ, đủ số dư , tạo request và trừ balance ngay")
    void withdraw_success() {
        transactionService.withdraw(bidder, 500000, "0001234567890");
        assertEquals(1500000, bidder.getBalance(), 0.01);
        assertEquals(1, transaction.getPendingTransactions().size());
        assertEquals("WITHDRAW", transaction.getPendingTransactions().get(0).getType());
    }
    //Test tiền chuyển đi thì số dư phải đủ
    @Test
    @DisplayName("trường hợp không hợp lệ:Số tiền > số dư throw IllegalArgumentException")
    void withdraw_insufficientBalance_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.withdraw(bidder, 5000000, "bank123"));
    }
    //test số tiền rút không được =0
    @Test
    @DisplayName("trường hợp không hợp lệ:số tiền = 0 throw IllegalArgumentException")
    void withdraw_zeroAmount_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.withdraw(bidder, 0, "bank123"));
    }
    //test số tiền rút không được âm
    @Test
    @DisplayName("trường hợp không hợp lệ:số tiền âm → ném IllegalArgumentException")
    void withdraw_negativeAmount_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.withdraw(bidder, -1, "bank123"));
    }
    // Test hợp lệ: số tiền rút và được ghi vào hệ thống
    @Test
    @DisplayName("trường hợp hợp lệ:thông tin tài khoản ngân hàng được ghi vào request")
    void withdraw_bankInfoSaved() {
        transactionService.withdraw(bidder, 100000, "MB123456789");
        assertEquals("MB123456789",
                transaction.getPendingTransactions().get(0).getBankInfo());
    }
    //Test admin duyệt thì thực hiện nhiệm vụ vào số dư
    //Test admin duyệt thì cộng tiền vào số dư
    @Test
    @DisplayName("trường hợp hợp lệ: Admin duyệt thì cộng tiền vào balance")
    void executeDecision_approveDeposit_addsBalance() throws Exception {
        transactionService.deposit(bidder, 500000);
        int id = transaction.getPendingTransactions().get(0).getRequestId();
        transactionService.executeTransactionDecision(id, true);
        assertEquals(2500000, bidder.getBalance(), 0.01);
        assertEquals("APPROVED", transaction.getTransactionById(id).getTransactionStatus());
    }
    //test admin từ chối cộng tiền
    @Test
    @DisplayName("trường hợp hợp lệ: Admin từ chối cộng thì balance không thay đổi")
    void executeDecision_rejectDeposit_noChange() throws Exception {
        transactionService.deposit(bidder, 500000);
        int id = transaction.getPendingTransactions().get(0).getRequestId();

        transactionService.executeTransactionDecision(id, false);

        assertEquals(2000000, bidder.getBalance(), 0.01);
        assertEquals("REJECTED", transaction.getTransactionById(id).getTransactionStatus());
    }

    @Test
    @DisplayName("trường hợp hợp lệ: Admin duyệt trừ tiền thì status APPROVED, balance không hoàn lại")
    void executeDecision_approveWithdraw() throws Exception {
        transactionService.withdraw(bidder, 500000, "bank123");     // balance còn 1500000
        int id = transaction.getPendingTransactions().get(0).getRequestId();
        transactionService.executeTransactionDecision(id, true);
        assertEquals(1500000, bidder.getBalance(), 0.01);            // không cộng lại
        assertEquals("APPROVED", transaction.getTransactionById(id).getTransactionStatus());
    }
    //Test admin từ chối trừ tiền
    @Test
    @DisplayName("trường hợp hợp lệ: Admin từ chối trừ tiền hoàn tiền lại cho user")
    void executeDecision_rejectWithdraw_refundsBalance() throws Exception {
        transactionService.withdraw(bidder, 500000, "bank123"); // balance còn 1500000
        int id = transaction.getPendingTransactions().get(0).getRequestId();
        transactionService.executeTransactionDecision(id, false);
        assertEquals(2000000, bidder.getBalance(), 0.01);        // hoàn lại
        assertEquals("REJECTED", transaction.getTransactionById(id).getTransactionStatus());
    }
    // Test và ném lỗi để giao dịch chỉ được duyệt 1 lần chứ không phải 2 lần
    @Test
    @DisplayName(" trường hợp không hợp lệ: giao dịch đã xử lý thì ném IllegalStateException")
    void executeDecision_alreadyProcessed_shouldThrow() throws Exception {
        transactionService.deposit(bidder, 300000);
        int id = transaction.getPendingTransactions().get(0).getRequestId();
        transactionService.executeTransactionDecision(id, true);                 // lần 1

        assertThrows(IllegalStateException.class,
                () -> transactionService.executeTransactionDecision(id, false)); // lần 2
    }







}
