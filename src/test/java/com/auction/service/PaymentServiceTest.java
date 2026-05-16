package com.auction.service;

import com.auction.common.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Xử lí thanh toán đấu giá")
public class PaymentServiceTest {
    private PaymentLog paymentLog;
    private PaymentService paymentService;
    private ItemService itemService;
    private Admin admin;
    private Bidder bidder;
    private Seller seller;

    @BeforeEach
    void setup() {
        paymentLog = new PaymentLog();
        paymentService = new PaymentService(paymentLog);
        itemService = new ItemService();
        itemService.clearData();                                               // xóa bộ nhớ, chắc chăn tạo ra 1 đối tượng mới
        admin = new Admin(1, "admin", "Hoa@gmail.com", "Hoa123", "0111111111", "ACTIVE");
        bidder = new Bidder(2, "bidder", "Hi@gmail.com", "Hi123", "0222222222", "ACTIVE");
        seller = new Seller(3, "seller", "Ha@gmail.com", "Ha123", "0333333333", "ACTIVE");
    }
    // tạo 1 phiên đấu giá giả ở trạng thái Paid
    private Auction makePaidAuction(int id, double price) {
        itemService.addItem(new Vehicle(id, "Honda",(int) price, "màu đen", "xe máy honda", "img.jpg"));
        Items item = itemService.getItemById(id);
        Auction auction = new Auction(id, item);
        auction.setAuctionStatus("PAID");
        auction.setCurrentPrice(price);
        auction.setHighestBidder(bidder.getUsername());
        return auction;
    }
    //Test HoldFund: hàm giữ tiền của bidder bởi admin
    // người mua chuyển cho admin 100% tiền đấu giá và admin giữ, sau đó check ví từng người
    @Test
    @DisplayName("trường hợp hợp lệ: trừ balance bidder, cộng escrow admin")
    void holdFunds_success() {
        bidder.setBalance(10000000);
        admin.setEscrowBalance(0);
        Auction auction = makePaidAuction(1, 5000000);
        paymentService.holdFunds(bidder, auction, admin);
        assertEquals(5000000, bidder.getBalance(),0.01);
        assertEquals(5000000, admin.getEscrowBalance(), 0.01);
    }
    // log là 1 cuộc trả tiền, ghi vào trong đó dữ kiện
    @Test
    @DisplayName("trường hợp hợp lệ: Ghi đúng 1 bản ghi HOLD FUNDS vào log")
    void holdFunds_logsOneRecord() {
        bidder.setBalance(10000000);
        Auction auction = makePaidAuction(2, 3000000);
        paymentService.holdFunds(bidder, auction, admin);
        assertEquals(1, paymentLog.getAllLogs().size());
        assertEquals("HOLD FUNDS", paymentLog.getAllLogs().get(0).getTransactionType());
    }
    // hàm test người nhận phải là admin và người gửi là bidder
    @Test
    @DisplayName("trường hợp hợp lê:Log ghi đúng fromUser là bidder, toUser là admin")
    void holdFunds_logFromAndTo() {
        bidder.setBalance(10000000);
        Auction auction = makePaidAuction(3, 2000000);
        paymentService.holdFunds(bidder, auction, admin);
        assertEquals(bidder.getUsername(), paymentLog.getAllLogs().get(0).getFromUser());
        assertEquals(admin.getUsername(),  paymentLog.getAllLogs().get(0).getToUser());
    }
    //hàm test: nếu chưa trang thái PAID(đã trả) thì tiền sẽ không được chuyển cho admin giữ
    @Test
    @DisplayName("trường hợp không hợp lệ: Auction chưa PAID ")
    void holdFunds_notPaid_noEffect() {
        bidder.setBalance(10000000);
        itemService.addItem(new Vehicle(4, "P", 1000000, "d", "n", "i"));
        Items item = itemService.getItemById(4);
        Auction auction = new Auction(4, item);
        auction.setAuctionStatus("RUNNING");
        auction.setCurrentPrice(1000000);
        auction.setHighestBidder(bidder.getUsername());
        paymentService.holdFunds(bidder, auction, admin);
        assertEquals(10000000, bidder.getBalance(), 0.01);             // không bị trừ
        assertTrue(paymentLog.getAllLogs().isEmpty());
    }
    // hàm test: người thắng kp là bidder
    @Test
    @DisplayName("trường hợp không hợp lệ: Bidder không phải người thắng thì không thực hiện chuyển tiền")
    void holdFunds_wrongBidder_noEffect() {
        bidder.setBalance(10000000);
        Auction auction = makePaidAuction(5, 2000000);
        auction.setHighestBidder("SomeoneElse");

        paymentService.holdFunds(bidder, auction, admin);

        assertEquals(10000000, bidder.getBalance(), 0.01);
        assertTrue(paymentLog.getAllLogs().isEmpty());
    }
    //Test ReleaseFund: admin chuyển tiền cho người bán theo % đã đưa ra của hệ thống
    //mọi thứ đều phù hợp thì chuyển cho seller
    @Test
    @DisplayName("trường hợp hợp lệ:  chuyển tiền trừ phí 15% cho seller")
    void releaseFundsToSeller_success() {
        double price = 5000000;
        double fee = price * 0.15;
        admin.setEscrowBalance(price);
        itemService.addItem(new Vehicle(10, "P", (int) price, "d", "n", "i"));
        Items item = itemService.getItemById(10);
        item.setItemStatus("COMPLETED");
        Auction auction = new Auction(10, item);
        auction.setCurrentPrice(price);
        paymentService.releaseFundsToSeller(seller, auction, admin);
        assertEquals(price - fee, seller.getBalance(), 0.01);
        assertEquals(fee, admin.getSystemRevenue(), 0.01);
        assertEquals(0, admin.getEscrowBalance(), 0.01);
    }
    // chỉ 1 người ược nhận, ghi vào trong log là 1 cuộc chuyển tiền
    @Test
    @DisplayName("trường hợp hợp lệ: ghi đúng 1 bản ghi chuyển vào log")
    void releaseFundsToSeller_logsOneRecord() {
        double price = 4000000;
        admin.setEscrowBalance(price);
        itemService.addItem(new Vehicle(11, "P", (int) price, "d", "n", "i"));
        Items item = itemService.getItemById(11);
        item.setItemStatus("COMPLETED");
        Auction auction = new Auction(11, item);
        auction.setCurrentPrice(price);
        paymentService.releaseFundsToSeller(seller, auction, admin);
        assertEquals(1, paymentLog.getAllLogs().size());
        assertEquals("RELEASE FUNDS", paymentLog.getAllLogs().get(0).getTransactionType());
    }
    // nhận phí 15%
    @Test
    @DisplayName("trường hợp hợp lệ: phí 15% được ghi đúng vào log")
    void releaseFundsToSeller_logFeeCorrect() {
        double price = 10000000;
        admin.setEscrowBalance(price);
        itemService.addItem(new Vehicle(12, "P", (int) price, "d", "n", "i"));
        Items item = itemService.getItemById(12);
        item.setItemStatus("COMPLETED");
        Auction auction = new Auction(12, item);
        auction.setCurrentPrice(price);
        paymentService.releaseFundsToSeller(seller, auction, admin);
        assertEquals(price * 0.15, paymentLog.getAllLogs().get(0).getFee(), 0.01);
    }
    //test : item chưa chuyển đã đấu giá xong thì ko chuyển cho seller
    @Test
    @DisplayName("trường hợp không hợp lệ: Item chưa ở trạng thái completed")
    void releaseFundsToSeller_notCompleted_noEffect() {
        double price = 5000000;
        admin.setEscrowBalance(price);
        itemService.addItem(new Vehicle(13, "P", (int) price, "d", "n", "i"));
        Items item = itemService.getItemById(13);
        item.setItemStatus("PAID");
        Auction auction = new Auction(13, item);
        auction.setCurrentPrice(price);
        paymentService.releaseFundsToSeller(seller, auction, admin);
        assertEquals(0, seller.getBalance(), 0.01);         // không cộng
        assertEquals(price, admin.getEscrowBalance(),  0.01);        // không trừ
        assertTrue(paymentLog.getAllLogs().isEmpty());
    }
    // test full quá trình
    @Test
    @DisplayName("trường hợp hợp lệ: chạy cae quá trình")
    void fullPaymentFlow_holdThenRelease() {
        double price = 6000000;
        bidder.setBalance(price);
        admin.setEscrowBalance(0);

        Auction auction = makePaidAuction(20, price);            // phần để admin giữ tiền của bidder
        paymentService.holdFunds(bidder, auction, admin);
        assertEquals(0,     bidder.getBalance(),0.01);
        assertEquals(price, admin.getEscrowBalance(), 0.01);

        auction.getItem().setItemStatus("COMPLETED");               // đấu giá chuyển trạng thái thì mới chuyển sang cho seller được

        paymentService.releaseFundsToSeller(seller, auction, admin);// chuyển cho seller
        double fee = price * 0.15;
        assertEquals(price - fee, seller.getBalance(),      0.01);
        assertEquals(fee,admin.getSystemRevenue(), 0.01);
        assertEquals(0,admin.getEscrowBalance(), 0.01);
        assertEquals(2,paymentLog.getAllLogs().size());
    }
}