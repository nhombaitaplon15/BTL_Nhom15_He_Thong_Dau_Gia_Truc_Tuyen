package com.auction.common.model;
import java.time.LocalDateTime;

public class Payment { // dữ liệu thanh toán
    private int logId;
    private String transactionType; // "HOLD FUNDS" / "RELEASE FUNDS"
    private String fromUser;
    private String toUser;
    private double amount;
    private double fee;  // tiền phí hệ thống
    private LocalDateTime timestamp;

    public int getLogId() {return logId;}
    public String getTransactionType() {return transactionType;}
    public String getFromUser() {return fromUser;}
    public String getToUser() {return toUser;}
    public double getAmount() {return amount;}
    public double getFee() {return fee;}
    public LocalDateTime getTimestamp() {return timestamp;}

    public Payment(int logId, String transactionType, String fromUser, String toUser, double amount, double fee) {
        this.logId = logId;
        this.transactionType = transactionType;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
        this.fee = fee;
        this.timestamp = LocalDateTime.now();
    }
    @Override
    public String toString() {
        return "[" + timestamp + "] " + transactionType + ": " + fromUser + " -> " + toUser + " | Tiền: " + amount + " | Phí: " + fee;
    }
}
