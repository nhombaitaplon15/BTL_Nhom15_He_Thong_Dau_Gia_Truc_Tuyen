package com.auction.common.factory;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;

public class UserFactory {
    public static User createUser(int id, String name, String email, String password, String phone, String status, String role, double balance) {
        if (role == null) return null;
        switch (role.toUpperCase()) {
            case "ADMIN":
                return new Admin(id, name, email, password, phone, status, balance);
            case "SELLER":
                return new Seller(id, name, email, password, phone, status, balance);
            case "BIDDER":
                return new Bidder(id, name, email, password, phone, status, balance);
            default: // Mọi trường hợp lạ khác cũng cho thành Bidder cho an toàn
                return new Bidder(id, name, email, password, phone, status, balance);
        }
    }
}
