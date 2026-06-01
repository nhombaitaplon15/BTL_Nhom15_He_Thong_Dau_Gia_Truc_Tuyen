package com.auction.common.network;

import java.io.Serializable;
import java.time.LocalDateTime;

public class CreateAuctionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int sellerId;
    private int itemId;
    private double startingPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public CreateAuctionDTO() {}

    // ĐÃ FIX: Thêm tham số sellerId vào constructor
    public CreateAuctionDTO(int sellerId, int itemId, double startingPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this.sellerId = sellerId;
        this.itemId = itemId;
        this.startingPrice = startingPrice;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public int getSellerId() { return sellerId; }
    // ĐÃ FIX: Gán đúng sellerId thay vì itemId
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}