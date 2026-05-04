package com.auction.server.dao;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;

import java.sql.*;

public class DBConnection {
    // Thay đổi tên database thành auction_db
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
}