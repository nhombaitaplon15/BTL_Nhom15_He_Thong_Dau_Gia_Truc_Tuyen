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
    // kiểm tra đã tồn tại các thông tin như Username, Email, Phone chưa
    public boolean isFieldExists(String fieldName, String value) {
        // nếu dữ liệu tồn tại thì trả vể số 1
        String sql = "SELECT 1 FROM users WHERE " + fieldName + " = ?";
        try (Connection conn = DBConnection.getConnection(); // mở cổng kết nối với database
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value); // điền giá trị vado dấu hỏi chấm
            try (ResultSet rs = ps.executeQuery()) { // kiểm tra kết quả trả về từ database có gì ko
                return rs.next(); }
        } catch (SQLException e) { return false; }
    }


    // xác thực tài khoản người dùng vầ trả về dữ liệu
    public User checkLogin(String username, String password) {
        // * là lấy tất cả giữ liệu khi đã khớp username và password
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) { // bảng dữ liệu đc database trả về
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("username");
                    String email = rs.getString("email");
                    String pass = rs.getString("password");
                    String phone = rs.getString("phone");
                    String status = rs.getString("status");
                    String role = rs.getString("user_role"); // Cột role trong DB: ADMIN/ BIDDER/ SELLER
                    double balance = rs.getDouble("balance");
                    //  tùy vào role để tạo đối tượng
                    if ("ADMIN".equalsIgnoreCase(role)) {
                        return new Admin(id, name, email, pass, phone, status);
                    } else if ("BIDDER".equalsIgnoreCase(role)) {
                        return new Bidder(id, name, email, pass, phone, status, balance);
                    } else if ("SELLER".equalsIgnoreCase(role)) {
                        return new Seller(id, name, email, pass, phone, status, balance);
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn Database: " + e.getMessage());
        }
        return null; // trả về null nếu không khớp tài khoản
    }

    // khi người dùng đăng kí thì lưu giữ liệu về database
    public boolean register(User user) {
        String sql = "INSERT INTO users (username, password, email, phone, role, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole()); // lấy SELLER / BIDDER từ đối tượng con
            return ps.executeUpdate() > 0; // nếu thay đổi dữ liệu thành công thì ps.executeUpdate() > 0 hàm trả về true
        } catch (SQLException e) { return false; }
    }

    // thay đổi mật khẩu
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