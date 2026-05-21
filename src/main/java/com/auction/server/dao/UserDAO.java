package com.auction.server.dao;

import com.auction.common.model.*;
import com.auction.factory.UserFactory;

import java.sql.*;

public class UserDAO {

    // 1. Kiểm tra người dùng đã tồn tại
    public boolean isFieldExists(String fieldName, String value) {
        String sql = "SELECT 1 FROM users WHERE " + fieldName + " = ?";
        try (Connection conn = DatabaseConnection.connect();
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
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Sử dụng UserFactory để tạo đối tượng, ID và Balance được giữ nguyên
                    return UserFactory.createUser(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("phone"),
                            rs.getString("status"),
                            rs.getString("role"),
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
    // 3. Đăng ký tài khoản mới (ĐÃ FIX LỖI NHẬN ID TỰ TĂNG POSTGRESQL)
    public boolean register(User user) {
        String sql = "INSERT INTO users (username, password, email, phone, role, status, balance) VALUES (?, ?, ?, ?, ?, ?, ?)";

        // CẢI TIẾN QUAN TRỌNG: Thêm Statement.RETURN_GENERATED_KEYS vào đây
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole());
            ps.setString(6, user.getStatus());
            ps.setDouble(7, user.getBalance());

            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                // Bây giờ generatedKeys chắc chắn sẽ có dữ liệu từ Postgres trả về
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int generatedId = generatedKeys.getInt(1);
                        user.setId(generatedId); // Nạp ID vào Object để dùng luôn
                        return true; // Trả về THÀNH CÔNG chuẩn xác!
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký tài khoản: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // 4. Cập nhật mật khẩu (Dùng cho cả đổi và quên mật khẩu)
    public boolean updatePassword(String username, String newPass) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPass);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // 5. Cập nhật vai trò (Giữ nguyên ID, chỉ đổi nhãn role trong SQL)
    public boolean updateRole(int userId, String newRole) {
        String sql = "UPDATE users SET user_role = ? WHERE id = ?";
        try (Connection conn =DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }
    // 6. Lấy người dùng theo id
    public User getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?"; // Nhớ đúng tên cột user_id nhé shop
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Trả về object User đầy đủ (nhớ map đúng các cột)
                return UserFactory.createUser(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password"),
                        rs.getString("phone"),
                        rs.getString("status"),
                        rs.getString("role"),
                        rs.getDouble("balance")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public String getUserName(int userId) {
        String sql = "SELECT username FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username"); // Lấy dữ liệu từ cột username trong DB
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối DB khi lấy Username: " + e.getMessage());
            e.printStackTrace();
        }
        return "Unknown"; // Trả về mặc định nếu không tìm thấy hoặc lỗi kết nối
    }

    // 2. Kết nối DB để lấy Số dư tài khoản (Balance) theo ID
    public double getBalance(int userId) {
        String sql = "SELECT balance FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance"); // Lấy số tiền từ cột balance trong DB
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối DB khi lấy Balance: " + e.getMessage());
            e.printStackTrace();
        }
        return 0.0; // Trả về 0 nếu lỗi kết nối
    }
}