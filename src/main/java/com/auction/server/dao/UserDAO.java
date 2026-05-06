package com.auction.server.dao;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {
    // 1. Kiểm tra tồn tại thông tin (Username, Email, Phone) để chặn trùng
    public boolean isFieldExists(String fieldName, String value) {
        String sql = "SELECT 1 FROM users WHERE " + fieldName + " = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }
    public User checkLogin(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        // Sử dụng Try-with-resources để tự động đóng kết nối
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("username");
                    String email = rs.getString("email");
                    String pass = rs.getString("password");
                    String phone = rs.getString("phone");
                    String status = rs.getString("status");
                    String role = rs.getString("user_role"); // Cột role trong DB: 'ADMIN' hoặc 'USER'

                    // Nếu là ADMIN thì tạo đối tượng Admin, hoặc còn lại
                    if ("ADMIN".equalsIgnoreCase(role)) {
                        return new Admin(id, name, email, pass, phone, status);
                    } else if ("BIDDER".equalsIgnoreCase(role)) {
                        return new Bidder(id, name, email, pass, phone, status);
                    } else if ("SELLER".equalsIgnoreCase(role)) {
                        return new Seller(id, name, email, pass, phone, status);
                    } else {
                        return null; // Hoặc một loại mặc định nào đó
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn Database: " + e.getMessage());
        }
        return null; // Trả về null nếu không khớp tài khoản
    }
    // 3. Logic Đăng ký (ID tự tăng nên không cần truyền vào)
    public boolean register(User user) {
        String sql = "INSERT INTO users (username, password, email, phone, role, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole()); // Lấy "SELLER" hoặc "BIDDER" từ đối tượng con
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
    // 4. Đổi mật khẩu
    public boolean updatePassword(String username, String oldPass, String newPass) {
        String sql = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPass);
            ps.setString(2, username);
            ps.setString(3, oldPass);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
}