package com.auction.server.dao;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL = "jdbc:postgresql://kodama.proxy.rlwy.net:46536/railway";
    private static final String USER = "postgres";
    private static final String PASSWORD = "eNJOOrTZxDClFPrAggDhzicWhwDNlhUI";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
    public static void main(String[] args) {
        try {
            Connection conn = DatabaseConnection.connect();
            System.out.println("Kết nối thành công");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

