package com.auction.common.model;

// Kế thừa lớp User
public class Seller extends User {

    // Constructor của Seller
    public Seller(int id, String name, String email, String password, String phone, String status) {
        super(id, name, email, password, phone, status, "SELLER");
    }

    // Nơi đây sau này em có thể viết thêm các hàm đặc thù chỉ Seller mới có
    // Ví dụ: public void addAuctionItem() { ... }
}