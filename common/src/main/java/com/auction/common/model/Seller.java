package com.auction.common.model;

import java.io.Serializable;

public class Seller extends User implements Serializable {
    public Seller(int id, String name, String email, String password, String phone, String status, double balance) {
        super(id, name, email, password, phone, status, "SELLER", balance);
    }
    public Seller() {
        super();
    }
}