package com.auction.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class BiddingHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int auctionId;
    private int bidderId;
    private double bidAmount;
    private LocalDateTime bidTime;

    // Constructor mặc định (Không tham số)
    public BiddingHistory() {
    }

    // Constructor đầy đủ tham số
    public BiddingHistory(int id, int auctionId, int bidderId, double bidAmount, LocalDateTime bidTime) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidAmount = bidAmount;
        this.bidTime = bidTime;
    }

    // --- Hệ thống Getter và Setter đầy đủ để phục vụ ép logic và cày code coverage ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public double getBidAmount() { return bidAmount; }
    public void setBidAmount(double bidAmount) { this.bidAmount = bidAmount; }

    public LocalDateTime getBidTime() { return bidTime; }
    public void setBidTime(LocalDateTime bidTime) { this.bidTime = bidTime; }
}