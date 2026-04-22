package com.auction.common;

// Kế thừa lớp User
public class Admin extends User {

    // Constructor của Admin
    public Admin(int id, String name, String email, String password, String phone, String status) {
        // Gọi super() và mặc định truyền chữ "ADMIN" cho tham số role
        super(id, name, email, password, phone, status, "ADMIN");
    }

    // Nơi đây sau này em có thể viết thêm các hàm đặc thù chỉ Admin mới có
    // Ví dụ: public void banUser() { ... }
    // hoặc public void cancelAuction() { ... }
}