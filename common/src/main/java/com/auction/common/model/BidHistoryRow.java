package com.auction.common.model;



public class BidHistoryRow {
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

    public int getId() {
        return id;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public String getItemName() {
        return itemName;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public String getBidTime() {
        return bidTime;
    }

    public String getStatus() {
        return status;
    }
}
