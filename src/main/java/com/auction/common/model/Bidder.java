package com.auction.common.model;

public class Bidder extends User {
    private double balance;
    public Bidder(int id, String name, String email, String password, String phone, String status, double balance) {
        super(id, name, email, password, phone, status, "BIDDER");
        this.balance = balance;
    }

    // hàm xác minh vai trò Bidder
    private void validateRole() throws Exception {
        if (!"BIDDER".equals(this.getRole())) {
            throw new IllegalAccessException("Chỉ người dùng có vai trò BIDDER mới được thực hiện!");
        }

    }

}
