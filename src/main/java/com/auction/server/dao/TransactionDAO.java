package com.auction.server.dao;

import com.auction.server.dao.DBConnection;
import java.sql.*;

public class TransactionDAO {

    // tạo lệnh nạp rút tiền mặc định là pending
    public boolean createTransaction(int userId, double amount, String type) {
        String sql = "INSERT INTO transactions (user_id, amount, type, status, created_at) VALUES (?, ?, ?, 'PENDING', NOW())";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, type.toUpperCase()); // "DEPOSIT" hoặc "WITHDRAW"

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi tạo giao dịch: " + e.getMessage());
            return false;
        }
    }
    // phê duyệt giao dịch (Chỉ admin gọi)
    public boolean processApproval(int transId, int userId, double amount, String type) {
        // SQL 1: Cập nhật trạng thái phiếu giao dịch
        String sqlUpdateTrans = "UPDATE transactions SET status = 'APPROVED' WHERE id = ?";

        // SQL 2: Cập nhật số dư User (Logic toán tử linh hoạt)
        // Nếu type là DEPOSIT thì dùng +, nếu là WITHDRAW thì dùng -
        String operator = type.equalsIgnoreCase("DEPOSIT") ? "+" : "-";
        String sqlUpdateUser = "UPDATE users SET balance = balance " + operator + " ? WHERE id = ?";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu giao dịch an toàn

            // Thực hiện Bước 1: Cập nhật phiếu
            try (PreparedStatement ps1 = conn.prepareStatement(sqlUpdateTrans)) {
                ps1.setInt(1, transId);
                int updated1 = ps1.executeUpdate();
                if (updated1 == 0) throw new SQLException("Không tìm thấy mã giao dịch!");

                // Thực hiện Bước 2: Cập nhật ví tiền
                try (PreparedStatement ps2 = conn.prepareStatement(sqlUpdateUser)) {
                    ps2.setDouble(1, amount);
                    ps2.setInt(2, userId);
                    int updated2 = ps2.executeUpdate();

                    if (updated2 == 0) throw new SQLException("Cập nhật ví tiền thất bại!");

                    // Nếu cả 2 cùng OK thì mới ghi vào DB
                    conn.commit();
                    System.out.println(">>> Duyệt thành công: " + type + " " + amount + " cho User " + userId);
                    return true;
                }
            }
        } catch (SQLException e) {
            // Nếu có bất kỳ lỗi gì (ví dụ: mất mạng, sai ID) thì hủy toàn bộ thao tác
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.err.println("Lỗi xử lý duyệt: " + e.getMessage());
            return false;
        } finally {
            // Đóng kết nối thủ công vì chúng ta cần quản lý Commit/Rollback
            try { if (conn != null) conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    // từ chối giao dịch
    public boolean rejectTransaction(int transId) {
        String sql = "UPDATE transactions SET status = 'REJECTED' WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}