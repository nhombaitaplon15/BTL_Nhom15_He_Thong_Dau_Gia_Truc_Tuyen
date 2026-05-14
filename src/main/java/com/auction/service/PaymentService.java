package com.auction.service;

import com.auction.common.model.Admin;
import com.auction.common.model.Auction;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;

public class PaymentService {
    // khai báo kho chứa RAM
    private PaymentLog logRepo;

    // constructor truyền kho chứa vào
    public PaymentService(PaymentLog log) {
        this.logRepo = log;
    }

    // tạm giữ tiền người bán trả cho mặt hàng đã mua
    public void holdFunds(Bidder bidder,Auction auction, Admin admin) {
        if (auction.getAuctionStatus().equals("PAID") && bidder.getUsername().equals(auction.getHighestBidder())) {
            double amount = auction.getCurrentPrice();
            bidder.setBalance(bidder.getBalance() - amount);
            admin.setEscrowBalance(admin.getEscrowBalance() + amount);
            logRepo.saveLog("HOLD FUNDS", bidder.getUsername(), admin.getUsername(), amount,0.0);
        }
    }

    // chuyển tiền đã trừ phí hệ thống cho người bán sau khi người mua xác nhận đã nhận hàng
    public void releaseFundsToSeller(Seller seller,Auction auction, Admin admin ) {
        if (auction.getItem().getItemStatus().equals("COMPLETED")) {
            double amount = auction.getCurrentPrice();
            double fee = amount * 0.15;    // Phí hệ thống 15% :>
            admin.setEscrowBalance(admin.getEscrowBalance() - amount);
            admin.setSystemRevenue(admin.getSystemRevenue() + fee);
            seller.setBalance(seller.getBalance() + (amount - fee));
            logRepo.saveLog("RELEASE FUNDS", seller.getUsername(), admin.getUsername(), amount,fee);
        }
    }
}
