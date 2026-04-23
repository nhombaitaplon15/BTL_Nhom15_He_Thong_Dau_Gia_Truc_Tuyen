package com.auction.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // Sếp kiểm tra đúng tên db là auction_db nhé
    private static final String URL = "jdbc:mysql://localhost:3306/auction_db?useUnicode=true&characterEncoding=UTF-8";
    private static final String USER = "root";
    private static final String PASS = "";

    public static Connection getConnection() throws SQLException {
        try {
            // Với Java 21 và MySQL Connector mới, dòng này có thể không cần nhưng viết vào cho chắc sếp ạ
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Thiếu Driver MySQL rồi sếp ơi!");
        }
    }

    // Hàm chạy thử ngay tại đây
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            if (conn != null) {
                System.out.println("==========================================");
                System.out.println("   KẾT NỐI THÀNH CÔNG RỒI!   ");
                System.out.println("   Server Java 25 đã thông với MySQL!    ");
                System.out.println("==========================================");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối: " + e.getMessage());
            System.err.println("Nhớ bật Start MySQL trong XAMPP nhé!");
        }
    }
}