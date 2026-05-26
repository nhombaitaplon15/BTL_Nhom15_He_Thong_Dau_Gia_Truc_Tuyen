package com.auction.common.model;
import java.time.LocalDateTime;

public class TransactionRequest implements java.io.Serializable{ // Yêu cầu nạp/rút tiền trong ví
    private int requestId;
    private User user;
    private String type; // DEPOSIT / WITHDRAW
    private double amount;
    private String bankInfo; // lưu số tài khoản của khách (nếu là rút tiền)
    private String transactionStatus; // PENDING , APPROVED/ REJECTED
    private LocalDateTime requestDate;

    public int getRequestId() {return requestId;}
    public void setRequestId(int requestId) {this.requestId = requestId;}
    public User getUser() {return user;}
    public void setUser(User username) {this.user = username;}
    public String getType() {return type;}
    public void setType(String type) {this.type = type;}
    public double getAmount() {return amount;}
    public void setAmount(double amount) {this.amount = amount;}
    public String getBankInfo() {return bankInfo;}
    public void setBankInfo(String bankInfo) {this.bankInfo = bankInfo;}
    public String getTransactionStatus() {return transactionStatus;}
    public void setTransactionStatus(String status) {this.transactionStatus = status;}
    public LocalDateTime getRequestDate() {return requestDate;}
    public void setRequestDate(LocalDateTime requestDate) {this.requestDate = requestDate;}

    public TransactionRequest(User user, String type, double amount ,String bankInfo, String status){
        requestId = 0;
        this.user = user;
        this.type = type;
        this.amount = amount;
        this.bankInfo = bankInfo;
        this.transactionStatus = status ;
        this.requestDate = LocalDateTime.now();
    }
}
