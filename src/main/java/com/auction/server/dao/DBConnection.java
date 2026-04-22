package com.auction.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnection {
    // Nhớ thay tên database, user, pass của sếp vào đây nhé
    private static final String URL = "jdbc:mysql://localhost:3306/auction_db";
    private static final String USER = "root";
    private static final String PASS = "";

    public static Connection getConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (Exception e) {
            System.err.println("Lỗi kết nối DB: " + e.getMessage());
            return null;
        }
    }
}