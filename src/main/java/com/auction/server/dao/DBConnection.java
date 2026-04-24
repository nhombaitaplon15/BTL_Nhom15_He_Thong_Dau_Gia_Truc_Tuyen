package com.auction.server.dao;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DBConnection {
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
                    String name = rs.getString("name");
                    String email = rs.getString("email");
                    String pass = rs.getString("password");
                    String phone = rs.getString("phone");
                    String status = rs.getString("status");
                    String role = rs.getString("role"); // Cột role trong DB: 'ADMIN' hoặc 'USER'

                    // Nếu là ADMIN thì tạo đối tượng Admin, ngược lại tạo User
                    if ("ADMIN".equalsIgnoreCase(role)) {
                        return new Admin(id, name, email, pass, phone, status);
                    } else if ("BIDDER".equalsIgnoreCase(role)) {
                        return new Bidder(id, name, email, pass, phone, status);
                    } else if ("SELLER".equalsIgnoreCase(role)) {
                        return new Seller(id, name, email, pass, phone, status);
                    } else {
                        return null; // Hoặc một loại mặc định nào đó sếp quy định
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn Database: " + e.getMessage());
        }
        return null; // Trả về null nếu không khớp tài khoản
    }
}