package com.auction.common.model;

import java.time.LocalDateTime;

public class Payment implements java.io.Serializable {
    private int logId;
    private String transactionType; // "HOLD_FUNDS", "RELEASE_FUNDS", "REFUND"
    private int fromUserId;         // Đổi sang ID để khớp với DB
    private int toUserId;           // Đổi sang ID
    private int auctionId;          // Thêm mã phiên đấu giá
    private double amount;
    private double fee;
    private LocalDateTime timestamp;

    // Constructor đầy đủ để dùng khi lấy dữ liệu từ ResultSet (DAO)
    public Payment(int logId, String transactionType, int fromUserId, int toUserId, int auctionId, double amount, double fee, LocalDateTime timestamp) {
        this.logId = logId;
        this.transactionType = transactionType;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.auctionId = auctionId;
        this.amount = amount;
        this.fee = fee;
        this.timestamp = timestamp;
    }

    // Constructor rút gọn để dùng khi tạo Log mới (Service)
    public Payment(String transactionType, int fromUserId, int toUserId, int auctionId, double amount, double fee) {
        this.transactionType = transactionType;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.auctionId = auctionId;
        this.amount = amount;
        this.fee = fee;
        this.timestamp = LocalDateTime.now();
    }

    // Getter
    public int getLogId() { return logId; }
    public String getTransactionType() { return transactionType; }
    public int getFromUserId() { return fromUserId; }
    public int getToUserId() { return toUserId; }
    public int getAuctionId() { return auctionId; }
    public double getAmount() { return amount; }
    public double getFee() { return fee; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s: User %d -> User %d (Auction: %d) | Tiền: %.2f | Phí: %.2f",
                timestamp, transactionType, fromUserId, toUserId, auctionId, amount, fee);
    }
}