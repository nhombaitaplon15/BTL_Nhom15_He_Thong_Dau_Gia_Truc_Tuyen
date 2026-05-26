package com.auction.server.dao;

import java.sql.*;

public class DBConnection {  // cầu nối với database
    // thông tin kêt nối
    private static final String URL = "jdbc:postgresql://kodama.proxy.rlwy.net:46536/railway";
    private static final String USER = "postgres";
    private static final String PASSWORD = "eNJOOrTZxDClFPrAggDhzicWhwDNlhUI";


    // gọi đến cơ sở dữ liệu
    public static Connection getConnection() throws SQLException {
        try {
            // khai báo driver để kết nối với cơ sở dữ liệu
            Class.forName("org.postgresql.Driver");

            // thiết lập kết nối
            return DriverManager.getConnection(URL, USER, PASSWORD);

        } catch (ClassNotFoundException e) {System.err.println("Lỗi: Không tìm thấy Driver PostgreSQL JDBC!");
            e.printStackTrace();
            return null;
        }
    }
}
