package com.auction.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Khớp hoàn toàn với kiến trúc hệ thống của nhóm.
 * Kế thừa Entity (lấy thuộc tính id làm mã lịch sử) và triển khai Serializable.
 */
public class BiddingHistory extends Entity implements Serializable {

    private int auctionId;       // Liên quan đến mã phiên đấu giá
    private String itemName;     // Tên vật phẩm lấy từ bảng Item (Dùng để hiển thị lên UI TableView)
    private int bidderId;        // Mã người tham gia đặt giá (Liên quan đến id của User/Bidder)
    private String bidderName;   // Tên tài khoản đặt giá (Để tiện hiển thị hoặc kiểm tra nhanh)
    private double bidAmount;    // Số tiền đặt tại lượt đó
    private LocalDateTime bidTime; // Thời gian bấm đặt giá thành công
    private String status;       // Trạng thái: "Đang dẫn đầu", "Bị vượt mặt", "Thắng cuộc", "Thất bại"

    // Constructor đầy đủ để nhận dữ liệu từ ResultSet (BiddingHistoryDAO)
    public BiddingHistory(int id, int auctionId, String itemName, int bidderId,
                          String bidderName, double bidAmount, LocalDateTime bidTime, String status) {
        super(id); // Truyền id về cho lớp cha Entity xử lý
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.bidAmount = bidAmount;
        this.bidTime = bidTime;
        this.status = status;
    }

    // --- Các hàm Getters & Setters theo đúng chuẩn của dự án ---

    public int getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(int auctionId) {
        this.auctionId = auctionId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getBidderId() {
        return bidderId;
    }

    public void setBidderId(int bidderId) {
        this.bidderId = bidderId;
    }

    public String getBidderName() {
        return bidderName;
    }

    public void setUsername(String bidderName) {
        this.bidderName = bidderName;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(double bidAmount) {
        this.bidAmount = bidAmount;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }

    public void setBidTime(LocalDateTime bidTime) {
        this.bidTime = bidTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("[Lịch sử #%d] Phiên: %d (%s) | Người đặt ID: %d (%s) | Giá: %.2f | Trạng thái: %s",
                getId(), auctionId, itemName, bidderId, bidderName, bidAmount, status);
    }
}