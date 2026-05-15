package com.auction.server.dao;

import com.auction.server.dao.DBConnection;
import java.sql.*;

public class TransactionDAO {
    public boolean updateTransactionStatus(Connection conn, int transId, String status) throws SQLException {
        String sql = "UPDATE transaction SET status=? WHERE transaction_id=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, transId);
            pstmt.setString(2, status);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái giao dịch: " + e.getMessage());
            throw e; // Quăng lỗi ra ngoài để Service quản lý việc rollback số dư
        }
    }
    // tạo lệnh nạp rút tiền mặc định là pending(tạo ra khi thực hiện một chuỗi hành động)
    public boolean createTransaction(Connection conn, int userId, double amount, String type, String status) throws SQLException {
        String sql = "INSERT INTO transactions (user_id, amount, transaction_type, status, created_at) VALUES (?, ?, ?, ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, type);
            ps.setString(4, status);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi tạo giao dịch: " + e.getMessage());
            throw e; // Quăng lỗi lên để BiddingService rollback tiền khi có sự cố
        }
    }
    // tạo ra khi nạp rút thông thường
    public boolean createTransaction(int userId, double amount, String type,  String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return createTransaction(conn, userId, amount, type, status);
        } catch (SQLException e) {
            e.printStackTrace();
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
        // Thêm điều kiện chặn nếu là rút tiền
        if (type.equalsIgnoreCase("WITHDRAW")) {
            sqlUpdateUser += " AND balance >= ?";
        }
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: Cập nhật phiếu
                try (PreparedStatement ps1 = conn.prepareStatement(sqlUpdateTrans)) {
                    ps1.setInt(1, transId);
                    if (ps1.executeUpdate() == 0) throw new SQLException("Giao dịch không tồn tại hoặc đã xử lý!");
                }

                // Bước 2: Cập nhật ví
                try (PreparedStatement ps2 = conn.prepareStatement(sqlUpdateUser)) {
                    ps2.setDouble(1, amount);
                    ps2.setInt(2, userId);
                    if (type.equalsIgnoreCase("WITHDRAW")) {
                        ps2.setDouble(3, amount); // Set giá trị cho balance >= ?
                    }

                    if (ps2.executeUpdate() == 0) throw new SQLException("Số dư không đủ hoặc User không tồn tại!");
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("Lỗi xử lý duyệt: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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