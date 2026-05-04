package com.auction.service;

import com.auction.common.model.Admin;
import com.auction.common.model.Auction;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.server.dao.PaymentLogDAO;

import javax.swing.*;

public class PaymentService {
    // Khai báo kho chứa RAM
    private PaymentLogDAO logRepo;

    // Constructor truyền kho chứa vào
    public PaymentService(PaymentLogDAO log) {
        this.logRepo = log;
    }
    public void holdFunds(Bidder bidder,Auction auction, Admin admin) {
        if (auction.getAuctionStatus().equals("PAID") && bidder.getUsername().equals(auction.getHighestBidder())) {
            double amount = auction.getCurrentPrice();
            bidder.setBalance(bidder.getBalance() - amount);
            admin.setEscrowBalance(admin.getEscrowBalance() + amount);
            logRepo.saveLog("HOLD FUNDS", bidder.getUsername(), admin.getUsername(), amount,0.0);
        }
    }
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
