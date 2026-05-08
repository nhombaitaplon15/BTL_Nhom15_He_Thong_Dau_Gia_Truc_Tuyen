package com.auction.common.model;

import java.time.LocalDateTime;

public class Auction {
    private int auctionId;
    private Items item;              // Sản phẩm đấu giá
    private LocalDateTime startTime; // Thời gian bắt đầu
    private LocalDateTime endTime;   // Thời gian kết thúc
    private double currentPrice;       // Giá hiện tại
    private String highestBidder;    // Tên người trả giá cao nhất
    private String auctionStatus;    // Trạng thái: PENDING -> OPEN → RUNNING → FINISHED → PAID /CANCELED


    public Auction(int auctionId, Items item) {
        this.auctionId = auctionId;
        this.item = item;
        this.currentPrice = item.getStartPrice(); // Ban đầu giá hiện tại = giá khởi điểm
        this.auctionStatus = "PENDING";           // Mặc định là đang chờ
        this.highestBidder = "Chưa có";
    }

    public int getAuctionId() {
        return auctionId;
    }

    public Items getItem() {
        return item;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setAuctionSchedule(LocalDateTime startTime) {
        this.startTime = startTime;
        // giả sử mỗi phiên đấu giá là 30 phút
        this.endTime = startTime.plusMinutes(30);
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    //cập nhật giá khi có người trả giá cao hơn
    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getHighestBidder() {
        return highestBidder;
    }

    //Ghi tên người đang dẫn đầu
    public void setHighestBidder(String highestBidder) {
        this.highestBidder = highestBidder;
    }

    public String getAuctionStatus() {
        return auctionStatus;
    }

    public void setAuctionStatus(String status) {
        this.auctionStatus = status;
    }
}