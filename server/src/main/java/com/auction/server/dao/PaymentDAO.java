package server.dao;

import java.sql.*;

public class PaymentDAO {

    // 1. Cập nhật số dư (Dùng Connection từ Service)
    public boolean updateBalance(Connection conn, int userId, double amount, String operator) throws SQLException {
        if (!"+".equals(operator) && !"-".equals(operator)) return false;

        String sql = "UPDATE users SET balance = balance " + operator + " ? WHERE user_id = ?";
        if ("-".equals(operator)) {
            sql += " AND balance >= ?";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setInt(2, userId);
            if ("-".equals(operator)) {
                ps.setDouble(3, amount);
            }
            return ps.executeUpdate() > 0;
        }
    }

    // 2. Cập nhật quỹ Admin (Dùng Connection từ Service)
    public boolean updateAdminFunds(Connection conn, int adminId, double escrowAmount, String escrowOp, double revenueAmount) throws SQLException {
        if (!"+".equals(escrowOp) && !"-".equals(escrowOp)) return false;

        String sql = "UPDATE users SET escrow_balance = escrow_balance " + escrowOp + " ?, " +
                "system_revenue = system_revenue + ? WHERE user_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, escrowAmount);
            ps.setDouble(2, revenueAmount);
            ps.setInt(3, adminId);
            return ps.executeUpdate() > 0;
        }
    }

    // 3. Lấy số dư (Hàm này chỉ đọc, nên tự tạo Connection cũng được cho tiện)
    public double getBalance(int userId) {
        String sql = "SELECT balance FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1.0;
    }
}