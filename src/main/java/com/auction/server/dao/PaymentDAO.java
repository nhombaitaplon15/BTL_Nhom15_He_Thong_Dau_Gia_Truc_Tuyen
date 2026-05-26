package com.auction.server.dao;

import java.sql.*;

public class PaymentDAO {

    /**
     * 1. Cập nhật số dư (Dùng Connection từ Service)
     * ĐÃ SỬA: Đưa cơ chế ví ký quỹ (escrow_balance) vào để tự động luân chuyển dòng tiền
     * - Toán tử "-": Đặt giá mới -> Trừ ví chính (balance), cộng vào ví tạm (escrow_balance)
     * - Toán tử "+": Bị đè giá -> Hoàn tiền về ví chính (balance), trừ bớt ở ví tạm (escrow_balance)
     */
    public boolean updateBalance(Connection conn, int userId, double amount, String operator) throws SQLException {
        if (!"+".equals(operator) && !"-".equals(operator)) return false;

        String sql = "";
        if ("-".equals(operator)) {
            // ĐẶT GIÁ: Trừ tiền ví chính, đẩy tiền vào ví tạm giữ cọc (escrow_balance)
            sql = "UPDATE users SET balance = balance - ?, escrow_balance = escrow_balance + ? WHERE user_id = ? AND balance >= ?";
        } else if ("+".equals(operator)) {
            // BỊ ĐÈ GIÁ/HOÀN TIỀN: Trả lại tiền vào ví chính, rút tiền từ ví tạm ra
            sql = "UPDATE users SET balance = balance + ?, escrow_balance = escrow_balance - ? WHERE user_id = ? AND escrow_balance >= ?";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setDouble(2, amount);
            ps.setInt(3, userId);
            ps.setDouble(4, amount); // Đảm bảo đủ số dư/số tiền đóng băng tương ứng mới thực hiện

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

    // 3. Lấy số dư ví chính (Hàm này chỉ đọc, nên tự tạo Connection cũng được cho tiện)
    public double getBalance(int userId) {
        String sql = "SELECT balance FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1.0;
    }

    /**
     * 4. 🌟 THÊM MỚI: Lấy số dư ví tạm giữ ký quỹ (Giải quyết lỗi đỏ code ở file Home)
     */
    public double getEscrowBalance(int userId) {
        String sql = "SELECT escrow_balance FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("escrow_balance");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0.0; // Mặc định trả về 0 nếu chưa có dữ liệu hoặc lỗi
    }
}