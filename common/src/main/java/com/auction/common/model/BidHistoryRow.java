package com.auction.common.model;

import java.io.Serializable;

/**
 * BidHistoryRow — ĐÃ THÊM:
 *  - setStatus(): để BiddingHistoryController cập nhật trạng thái dòng thắng
 *    thành "WINNER - Chờ xác nhận" khi nhận push AUCTION_ENDED realtime.
 */
public class BidHistoryRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int auctionId;
    private String itemName;
    private double bidAmount;
    private String bidTime;
    private String status;

    public BidHistoryRow(int id, int auctionId, String itemName,
                         double bidAmount, String bidTime, String status) {
        this.id = id;
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.bidAmount = bidAmount;
        this.bidTime = bidTime;
        this.status = status;
    }

    // ─── Getters ─────────────────────────────────────────────
    public int getId()           { return id; }
    public int getAuctionId()    { return auctionId; }
    public String getItemName()  { return itemName; }
    public double getBidAmount() { return bidAmount; }
    public String getBidTime()   { return bidTime; }
    public String getStatus()    { return status; }

    // ─── Setters ─────────────────────────────────────────────
    /** Dùng để cập nhật trạng thái dòng thắng sau khi đấu giá kết thúc. */
    public void setStatus(String status) { this.status = status; }
}