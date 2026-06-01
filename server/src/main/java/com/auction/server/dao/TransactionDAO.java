package com.auction.server.dao;

import java.sql.*;
import com.auction.common.factory.UserFactory;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    public boolean updateTransactionStatus(Connection conn, int transId, String status) throws SQLException {
        String sql = "UPDATE public.transactions SET status=? WHERE transaction_id=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, transId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi cập nhật trạng thái giao dịch: " + e.getMessage());
            throw e;
        }
    }

    // --- HÀM MỚI: Hỗ trợ thêm cột description ---
    public boolean createTransaction(Connection conn, int userId, double amount, String type, String status, String description) throws SQLException {
        String sql = "INSERT INTO public.transactions (user_id, amount, transaction_type, status, description, created_at) VALUES (?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, type);
            ps.setString(4, status);
            ps.setString(5, description);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi tạo giao dịch (bọc txn): " + e.getMessage());
            throw e;
        }
    }

    public boolean createTransaction(int userId, double amount, String type, String status, String description) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return createTransaction(conn, userId, amount, type, status, description);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- HÀM CŨ: Giữ nguyên để không làm lỗi code BiddingService của bạn ---
    public boolean createTransaction(Connection conn, int userId, double amount, String type, String status) throws SQLException {
        return createTransaction(conn, userId, amount, type, status, null);
    }

    public boolean createTransaction(int userId, double amount, String type, String status) throws SQLException {
        return createTransaction(userId, amount, type, status, null);
    }

    public boolean processApproval(int transId, int userId, double amount, String type) {
        String sqlUpdateTrans = "UPDATE public.transactions SET status = 'APPROVED' WHERE transaction_id = ?";
        String operator = type.equalsIgnoreCase("DEPOSIT") ? "+" : "-";
        String sqlUpdateUser = "UPDATE public.users SET balance = balance " + operator + " ? WHERE user_id = ?";

        if (type.equalsIgnoreCase("WITHDRAW")) {
            sqlUpdateUser += " AND balance >= ?";
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps1 = conn.prepareStatement(sqlUpdateTrans)) {
                    ps1.setInt(1, transId);
                    if (ps1.executeUpdate() == 0) {
                        throw new SQLException("Giao dịch không tồn tại hoặc đã xử lý trước đó!");
                    }
                }

                try (PreparedStatement ps2 = conn.prepareStatement(sqlUpdateUser)) {
                    ps2.setDouble(1, amount);
                    ps2.setInt(2, userId);
                    if (type.equalsIgnoreCase("WITHDRAW")) {
                        ps2.setDouble(3, amount);
                    }

                    if (ps2.executeUpdate() == 0) {
                        throw new SQLException("Số dư không đủ để rút hoặc tài khoản người dùng không tồn tại!");
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ Lỗi xử lý phê duyệt giao dịch: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean rejectTransaction(int transId) {
        String sql = "UPDATE public.transactions SET status = 'REJECTED' WHERE transaction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi từ chối giao dịch: " + e.getMessage());
            return false;
        }
    }

    public List<TransactionRequest> getAllTransactions() {
        List<TransactionRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM public.transactions ORDER BY created_at DESC";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                User user = UserFactory.createUser(
                    rs.getInt("user_id"), "", "", "", "", "", "USER", 0.0
                );

                TransactionRequest tx = new TransactionRequest(
                    user,
                    rs.getString("transaction_type"),
                    rs.getDouble("amount"),
                    rs.getString("description"), // Lấy nội dung ghi chú từ Database
                    rs.getString("status")
                );
                tx.setRequestId(rs.getInt("transaction_id"));
                tx.setRequestDate(rs.getTimestamp("created_at").toLocalDateTime());
                list.add(tx);
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm getAllTransactions: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public List<TransactionRequest> getTransactionsByUserId(int userId) {
        List<TransactionRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM public.transactions WHERE user_id = ? ORDER BY created_at DESC";

        try (
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User user = UserFactory.createUser(
                        rs.getInt("user_id"), "", "", "", "", "", "USER", 0.0
                    );

                    TransactionRequest tx = new TransactionRequest(
                        user,
                        rs.getString("transaction_type"),
                        rs.getDouble("amount"),
                        rs.getString("description"), // Lấy nội dung ghi chú từ Database
                        rs.getString("status")
                    );
                    tx.setRequestId(rs.getInt("transaction_id"));
                    tx.setRequestDate(rs.getTimestamp("created_at").toLocalDateTime());
                    list.add(tx);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm getTransactionsByUserId: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}