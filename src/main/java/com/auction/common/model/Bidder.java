package com.auction.common.model;


import com.auction.server.dao.TransactionDAO;

public class Bidder extends User {
    private double balance;

    public Bidder(int id, String name, String email, String password, String phone, String status) {
        super(id, name, email, password, phone, status, "BIDDER");
        this.balance=0.0 ;   }
    public double getBalance() {
        return balance;
    }
    public void setBanlace(double balance){
        this.balance= balance;
    }
    private void validateRole() throws Exception {                                 // hàm xác minh vai trò Bidder
        if (!"BIDDER".equals(this.getRole())) {
            throw new IllegalAccessException("Chỉ người dùng có vai trò BIDDER mới được thực hiện!");
        }

    }
    public void placeBid( Auction auction ,double bidAmount) throws Exception{          // Trả giá cho mặt hàng trong phiên đấu giá
        validateRole();
        if ( bidAmount <= auction.getCurrentPrice()){
            throw new IllegalArgumentException("Mức giá phải trả phải cao hơn giá hiện tại của phiên đấu giá");
        }
        auction.setCurrentPrice((int) bidAmount);
        auction.setHighestBidder(this.getUsername());
    }



}
