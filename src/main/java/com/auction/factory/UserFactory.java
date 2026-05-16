package com.auction.factory;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;

public class UserFactory {
    public static User createUser(int id, String name, String email, String password, String phone, String status, String role, double balance) {
        if (role == null) return null;
        switch (role.toUpperCase()) {
            case "ADMIN":
                return new Admin(id, name, email, password, phone, status, 0.0);
            case "SELLER":
                return new Seller(id, name, email, password, phone, status, 0.0);
            case "BIDDER":
                return new Bidder(id, name, email, password, phone, status, 0.0);
            default:
                throw new IllegalArgumentException("Vai trò không hợp lệ: " + role);
        }
    }
}
