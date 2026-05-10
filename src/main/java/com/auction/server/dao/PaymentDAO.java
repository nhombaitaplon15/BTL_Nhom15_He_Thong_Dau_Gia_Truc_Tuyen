package com.auction.server.dao;

import com.auction.server.dao.DBConnection;
import java.sql.*;

public class PaymentDAO {

    /**
     * 1. Cập nhật số dư tài khoản (Dùng cho Bidder và Seller)
     * @param userId ID người dùng cần cập nhật
     * @param amount Số tiền biến động
     * @param operator "+" để cộng tiền (Seller nhận tiền), "-" để trừ tiền (Bidder đặt giá/mua hàng)
     */
    public boolean updateBalance(int userId, double amount, String operator) {
        if (!"+".equals(operator) && !"-".equals(operator)) return false;

        String sql = "UPDATE users SET balance = balance " + operator + " ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, amount);
            ps.setInt(2, userId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi updateBalance: " + e.getMessage());
            return false;
        }
    }

    /**
     * 2. Quản lý kho tiền trung gian và doanh thu cho Admin (Escrow System)
     * @param adminId ID của tài khoản Admin hệ thống
     * @param escrowAmount Số tiền chuyển vào hoặc rút ra khỏi kho giữ hộ
     * @param escrowOp "+" khi tạm giữ tiền người mua, "-" khi giải ngân cho người bán
     * @param revenueAmount Tiền phí hệ thống (15%) cộng vào doanh thu Admin
     */
    public boolean updateAdminFunds(int adminId, double escrowAmount, String escrowOp, double revenueAmount) {
        if (!"+".equals(escrowOp) && !"-".equals(escrowOp)) return false;

        // Cập nhật đồng thời tiền đang giữ (Escrow) và tiền doanh thu thực tế (Revenue)
        String sql = "UPDATE users SET escrow_balance = escrow_balance " + escrowOp + " ?, " +
                "system_revenue = system_revenue + ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, escrowAmount);
            ps.setDouble(2, revenueAmount);
            ps.setInt(3, adminId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi updateAdminFunds: " + e.getMessage());
            return false;
        }
    }

    /**
     * 3. Kiểm tra khả năng thanh toán (Dùng trước khi thực hiện giao dịch)
     */
    public double getBalance(int userId) {
        String sql = "SELECT balance FROM users WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }
}