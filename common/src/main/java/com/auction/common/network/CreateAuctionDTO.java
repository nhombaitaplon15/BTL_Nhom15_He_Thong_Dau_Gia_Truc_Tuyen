package com.auction.common.network;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO dùng khi Seller gửi yêu cầu tạo phiên đấu giá mới.
 *
 * ĐẶT TẠI: common/src/main/java/com/auction/common/network/CreateAuctionDTO.java
 */
public class CreateAuctionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int itemId;
    private double startingPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public CreateAuctionDTO() {}

    public CreateAuctionDTO(int itemId, double startingPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this.itemId = itemId;
        this.startingPrice = startingPrice;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    @Override
    public String toString() {
        return "CreateAuctionDTO{itemId=" + itemId + ", startingPrice=" + startingPrice + "}";
    }
}