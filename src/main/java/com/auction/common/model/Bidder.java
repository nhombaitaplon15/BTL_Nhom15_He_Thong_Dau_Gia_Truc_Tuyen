package com.auction.common.model;


public class Bidder extends User {
    private double balance;

    public Bidder(int id, String name, String email, String password, String phone, String status,role) {
        super(id, name, email, password, phone, status, role);
        this.balance=0.0    }
    public double getBalance() {
        return balance;
    }
    public void setBanlace(double balance){
        this.balance= balance
    }
    private void validateRole() throws Exception {                                 // hàm xác minh vai trò Bidder
        if (!"BIDDER".equals(this.getRole())) {
            throw new IllegalAccessException("Chỉ người dùng có vai trò BIDDER mới được thực hiện!");
        }

    }
    public void deposit(double amount) {                                            // nạp tiền vào ví
        if (amount > 0) {
            this.balance += amount;
        } else {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }
    }
    public void withdraw(double amount) {                                           // rút tiển khỏi ví
        if (amount > 0 && this.balance >= amount) {
            this.balance -= amount;
        } else {
            throw new IllegalArgumentException("Số dư không hợp vệ hoặc không đủ để rút ");
        }
    }
    public void makePayment(double amount) {                                        // thanh toán đơn hàng
        if (amount > 0 && this.balance >= amount) {
            this.balance -= amount;
        } else {
            throw new IllegalArgumentException("Số tiền ko đủ để thanh toán");
        }
    }
    public void transfer(User receiver, double amount){                             // Chuyển tiền cho người dùng khác
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền chuyển phải lớn hơn 0");
        }
        if (this.balance < amount) {
            throw new IllegalArgumentException("Số dư giao dịch ko đủ để thực hiện");
        }
        this.balance -= amount;
        receiver.deposit(amount);
    }
    public void placeBid( Auction auction ,double bidAmount) throws Exception{          // Trả giá cho mặt hàng trong phiên đấu giá
        validateRole();
        if ( bidAmount <<= auction.currentPrice){
            throw new IllegalArgumentException("Mức giá phải trả phải cao hơn giá hiện tại của phiên đấu giá");
        }
        auction.setCurrentPrice((int) bidAmount);
        auction.setHighestBidder(this.getName());
    }



}
