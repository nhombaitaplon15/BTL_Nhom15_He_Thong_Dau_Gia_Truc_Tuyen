package com.auction.server.dao;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;

import java.sql.*;

public class DBConnection {
    // Thay đổi tên database thành auction_db
// 1. Đổi URL: Thêm đuôi ?options... để diệt lỗi múi giờ mặc định
    private static final String URL = "jdbc:postgresql://shinkansen.proxy.rlwy.net:36856/railway?options=-c%20timezone=UTC";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "VvQwsVyCLGuitsfXKuTpbLpRemIBxsIa";

    public static Connection getConnection() throws SQLException {
        try {
            // 4. Đổi Driver từ mysql sang postgresql
            Class.forName("org.postgresql.Driver");

            return DriverManager.getConnection(URL, USERNAME, PASSWORD);
        } catch (ClassNotFoundException e) {
            // Sửa lại dòng thông báo lỗi cho đúng loại database
            System.err.println("Lỗi: Không tìm thấy Driver PostgreSQL JDBC!");
            e.printStackTrace();
            return null;
        }
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
                    String Dbusername = rs.getString("username");
                    String email = rs.getString("email");
                    String pass = rs.getString("password");
                    String phone = rs.getString("phone");
                    String status = rs.getString("status");
                    String role = rs.getString("role"); // Cột role trong DB: 'ADMIN' hoặc 'USER'

                    // Nếu là ADMIN thì tạo đối tượng Admin, ngược lại tạo User
                    if ("ADMIN".equalsIgnoreCase(role)) {
                        return new Admin(id, Dbusername, email, pass, phone, status);
                    } else if ("BIDDER".equalsIgnoreCase(role)) {
                        return new Bidder(id, Dbusername, email, pass, phone, status);
                    } else if ("SELLER".equalsIgnoreCase(role)) {
                        return new Seller(id, Dbusername, email, pass, phone, status);
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