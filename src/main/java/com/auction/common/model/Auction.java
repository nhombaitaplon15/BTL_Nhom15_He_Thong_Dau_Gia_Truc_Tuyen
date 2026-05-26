package com.auction.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Auction implements Serializable {
    private int auctionId;
    private int itemId;
    private int sellerId;
    private String auctionStatus; // OPEN, WAITING_FOR_ADMIN, RUNNING, REJECTED, SOLD...
    private double startingPrice;
    private double currentPrice;
    private int totalBids;
    private Integer currentWinnerId; // Dùng Integer để có thể nhận giá trị NULL (như trong ảnh DBeaver)
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    private Item item;

    public Auction(){}
    // Constructor đầy đủ để nhận dữ liệu từ DAO (ResultSet)
    public Auction(int auctionId, int itemId, int sellerId, String auctionStatus,
                   double startingPrice, double currentPrice, int totalBids,
                   Integer currentWinnerId, LocalDateTime startTime,
                   LocalDateTime endTime, LocalDateTime createdAt) {
        this.auctionId = auctionId;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.auctionStatus = auctionStatus;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.totalBids = totalBids;
        this.currentWinnerId = currentWinnerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = createdAt;
    }
    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getItemId() { return itemId; }
    public int getSellerId() { return sellerId; }

    public String getAuctionStatus() { return auctionStatus; }
    public void setAuctionStatus(String auctionStatus) { this.auctionStatus = auctionStatus; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public int getTotalBids() { return totalBids; }
    public void setTotalBids(int totalBids) { this.totalBids = totalBids; }

    public Integer getCurrentWinnerId() { return currentWinnerId; }
    public void setCurrentWinnerId(Integer currentWinnerId) { this.currentWinnerId = currentWinnerId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    // Các hàm bổ trợ cho logic chặn
    public boolean isWaitingForAdmin() {
        return "WAITING_FOR_ADMIN".equalsIgnoreCase(this.auctionStatus);
    }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public void setItemId(int itemId) {
        this.itemId = itemId;
    }
}