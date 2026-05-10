package com.auction.server.dao;

import com.auction.common.model.*;
import com.auction.factory.UserFactory;

import java.sql.*;

public class UserDAO {

    // 1. Kiểm tra người dùng đã tồn tại
    public boolean isFieldExists(String fieldName, String value) {
        String sql = "SELECT 1 FROM users WHERE " + fieldName + " = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) { return false; }
    }
    // 2. Đăng nhập - dùng UserFactory
    public User checkLogin(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Sử dụng UserFactory để tạo đối tượng, ID và Balance được giữ nguyên
                    return UserFactory.createUser(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("phone"),
                            rs.getString("status"),
                            rs.getString("user_role"),
                            rs.getDouble("balance")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn Database: " + e.getMessage());
        }
        return null;
    }

    // 3. Đăng ký tài khoản mới
    public boolean register(User user) {
        // Thêm cột balance vào SQL để đồng bộ
        String sql = "INSERT INTO users (username, password, email, phone, user_role, status, balance) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole());
            ps.setString(6, user.getStatus());
            ps.setDouble(7, user.getBalance());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // 4. Cập nhật mật khẩu (Dùng cho cả đổi và quên mật khẩu)
    public boolean updatePassword(String username, String newPass) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPass);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // 5. Cập nhật vai trò (Giữ nguyên ID, chỉ đổi nhãn role trong SQL)
    public boolean updateRole(int userId, String newRole) {
        String sql = "UPDATE users SET user_role = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
}