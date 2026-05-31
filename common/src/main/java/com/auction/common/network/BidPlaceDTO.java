package com.auction.common.network;



import java.io.Serializable;

/**
 * DTO dùng để gói dữ liệu đặt giá gửi từ Client -> Server.
 * Thay thế Object[] payload mảng thô không an toàn trong RequestDispatcher cũ.
 * Đặt tại: common/src/main/java/com/auction/common/network/BidPlaceDTO.java
 */
public class BidPlaceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int auctionId;
    private double bidAmount;

    public BidPlaceDTO(int auctionId, double bidAmount) {
        this.auctionId = auctionId;
        this.bidAmount = bidAmount;
    }

    public int getAuctionId() { return auctionId; }
    public double getBidAmount() { return bidAmount; }
}