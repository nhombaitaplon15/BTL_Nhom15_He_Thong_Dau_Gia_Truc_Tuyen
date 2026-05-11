package com.auction.common.model;

public class Bidder extends User {
    public Bidder(int id, String name, String email, String password, String phone, String status, double balance) {
        super(id, name, email, password, phone, status, "BIDDER", balance);
    }
}
