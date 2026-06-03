package com.auction.server.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:postgresql://kodama.proxy.rlwy.net:46536/railway";
    private static final String USER = "postgres";
    private static final String PASSWORD = "eNJOOrTZxDClFPrAggDhzicWhwDNlhUI";

    // Tạo hồ chứa kết nối (Chỉ khởi tạo 1 lần duy nhất)
    private static final HikariDataSource dataSource;

    static {
        try {
            Class.forName("org.postgresql.Driver");
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(URL);
            config.setUsername(USER);
            config.setPassword(PASSWORD);

            // Cấu hình tối ưu để không bị Railway chặn
            config.setMaximumPoolSize(15);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);

            // ─── THÊM 2 DÒNG NÀY ĐỂ FIX LỖI TIME-OUT VỚI RAILWAY ───
            // Ép Hikari tự hủy kết nối và làm mới sau mỗi 1 phút (60000ms),
            // đảm bảo luôn dùng kết nối tươi mới trước khi Railway kịp ngắt.
            config.setMaxLifetime(60000);

            // Bắn tín hiệu giữ kết nối mỗi 30 giây để mạng không bị đóng băng
            config.setKeepaliveTime(0);
            config.setInitializationFailTimeout(0);

            dataSource = new HikariDataSource(config);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Lỗi: Không tìm thấy Driver PostgreSQL JDBC!", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        // Mượn kết nối từ hồ chứa siêu tốc độ
        return dataSource.getConnection();
    }
}