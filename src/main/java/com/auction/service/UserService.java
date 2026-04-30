package com.auction.service;

import com.auction.common.model.*;
import java.util.HashMap;
import java.util.Map;

public class UserService {
    // Quản lý danh sách người dùng trên RAM (Key là username)
    private Map<String, User> userMap = new HashMap<>();

    // --- LOGIC ĐĂNG KÝ (In-Memory) ---
    public String handleRegister(String user, String pass, String mail, String phone, String role) {
        // 1. Kiểm tra định dạng cơ bản
        if (!phone.matches("^\\d{10}$")) return "LỖI: Số điện thoại phải đúng 10 chữ số!";
        if (pass.length() < 8) return "LỖI: Mật khẩu quá ngắn!";

        // 2. Kiểm tra trùng lặp trên RAM (thay vì gọi DAO)
        if (userMap.containsKey(user)) return "LỖI: Tên đăng nhập đã tồn tại!";

        // Kiểm tra trùng Email/Phone bằng cách duyệt Map
        for (User u : userMap.values()) {
            if (u.getEmail().equals(mail)) return "LỖI: Email đã được sử dụng!";
            if (u.getPhone().equals(phone)) return "LỖI: Số điện thoại đã đăng ký!";
        }

        // 3. Khởi tạo đúng đối tượng con (Abstract User không new được)
        User newUser;
        int newId = userMap.size() + 1; // Tạm thời tự tăng ID trên RAM

        if ("SELLER".equalsIgnoreCase(role)) {
            newUser = new Seller(newId, user, mail, pass, phone, "ACTIVE");
        } else {
            newUser = new Bidder(newId, user, mail, pass, phone, "ACTIVE");
        }

        // 4. Lưu vào Map
        userMap.put(user, newUser);
        System.out.println("Đã đăng ký thành công " + role + ": " + user);
        return "SUCCESS";
    }

    // --- LOGIC ĐĂNG NHẬP (In-Memory) ---
    public User handleLogin(String username, String password) {
        // Lấy user từ Map ra check
        User user = userMap.get(username);

        if (user != null && user.getPassword().equals(password)) {
            System.out.println("Người dùng " + username + " (Vai trò: " + user.getRole() + ") đã đăng nhập.");
            return user; // Trả về đúng đối tượng (Admin/Seller/Bidder)
        }

        System.out.println("Đăng nhập thất bại: Sai username hoặc password.");
        return null;
    }

    // --- LOGIC ĐỔI MẬT KHẨU ---
    public String handleChangePassword(User currentUser, String oldP, String newP, String confirmP) {

        // 1. Check mật khẩu cũ (vẫn phải check để đảm bảo chính chủ đang ngồi máy)
        if (!currentUser.getPassword().equals(oldP)) {
            return "LỖI: Mật khẩu cũ không chính xác!";
        }

        // 2. Check trùng cũ - mới
        if (newP.equals(oldP)) {
            return "LỖI: Mật khẩu mới không được trùng mật khẩu cũ!";
        }

        // 3. Check độ dài
        if (newP.length() < 8) {
            return "LỖI: Mật khẩu mới phải từ 8 ký tự trở lên!";
        }

        // 4. Check khớp xác nhận
        if (!newP.equals(confirmP)) {
            return "LỖI: Xác nhận mật khẩu không khớp!";
        }

        // 5. Cập nhật
        currentUser.setPassword(newP);
        return "SUCCESS";
    }
}