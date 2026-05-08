package com.auction.server.dao;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;

import java.sql.*;

public class DBConnection {  // cầu nối với database
    // thông tin kêt nối
    private static final String URL = "jdbc:postgresql://shinkansen.proxy.rlwy.net:36856/railway?options=-c%20timezone=UTC";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "VvQwsVyCLGuitsfXKuTpbLpRemIBxsIa";


    // gọi đến cơ sở dữ liệu
    public static Connection getConnection() throws SQLException {
        try {
            // khai báo driver để kết nối với cơ sở dữ liệu
            Class.forName("org.postgresql.Driver");

            // thiết lập kết nối
            return DriverManager.getConnection(URL, USERNAME, PASSWORD);

        } catch (ClassNotFoundException e) {System.err.println("Lỗi: Không tìm thấy Driver PostgreSQL JDBC!");
            e.printStackTrace();
            return null;
        }
    }
}