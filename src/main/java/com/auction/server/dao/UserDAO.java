package com.auction.server.dao;

import com.auction.common.model.User;
import java.sql.*;

public class UserDAO {
    public boolean checkLogin(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            return rs.next(); // Nếu có dữ liệu trả về thì đăng nhập đúng
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
