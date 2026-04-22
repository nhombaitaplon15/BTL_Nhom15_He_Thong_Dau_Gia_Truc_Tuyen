package com.auction.common;

// Kế thừa lớp User
public class Bidder extends User {

    // Constructor của Bidder
    public Bidder(int id, String name, String email, String password, String phone, String status) {
        // Gọi super() để truyền dữ liệu lên constructor của User
        // Vì đây là Bidder, ta mặc định truyền cứng chữ "BIDDER" vào vị trí của tham số role
        super(id, name, email, password, phone, status, "BIDDER");
    }

    // Nơi đây sau này em có thể viết thêm các hàm đặc thù chỉ Bidder mới có
    // Ví dụ: public void placeBid() { ... }
}
