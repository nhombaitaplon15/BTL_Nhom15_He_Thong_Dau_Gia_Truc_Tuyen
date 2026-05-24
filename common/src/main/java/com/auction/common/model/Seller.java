package com.auction.common.model;

public class Seller extends User {
    public Seller(int id, String name, String email, String password, String phone, String status, double balance) {
        super(id, name, email, password, phone, status, "SELLER", balance);
    }
}