package com.auction.server.dao;

import java.sql.*;

public class PaymentDAO {

    // 🎯 Danh sách ID các tài khoản Admin trong hệ thống được quyền giữ tiền cọc (Escrow)
    private static final int[] ADMIN_IDS = {1, 2, 3, 4};

    /**
     * Cập nhật số dư người dùng (Trừ tiền đóng băng ký quỹ hoặc Hoàn tiền khi bị đè giá)
     * Sử dụng chung Connection từ Service để đảm bảo tính toàn vẹn dữ liệu (Transaction)
     */
    public boolean updateBalance(Connection conn, int userId, double amount, String operator) throws SQLException {
        if (!"+".equals(operator) && !"-".equals(operator)) return false;

        if ("-".equals(operator)) {
            // --- BƯỚC 1: KIỂM TRA SỐ DƯ NGƯỜI ĐẶT MỚI (CHẶN ĐẶT GIÁ LUI LIU) ---
            String sqlCheck = "SELECT balance FROM public.users WHERE user_id = ? AND balance >= ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlCheck)) {
                ps.setInt(1, userId);
                ps.setDouble(2, amount);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false; // Không đủ tiền cọc -> Chặn đứng giao dịch
                }
            }

            // --- BƯỚC 2: KHẤU TRỪ VÍ CHÍNH CỦA NGƯỜI MUA MỚI ---
            String sqlDeductUser = "UPDATE public.users SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlDeductUser)) {
                ps.setDouble(1, amount);
                ps.setInt(2, userId);
                ps.setDouble(3, amount);
                if (ps.executeUpdate() == 0) return false;
            }

            // --- BƯỚC 3: ĐÓNG BĂNG KÝ QUỸ VÀO VÍ TẠM CỦA 1 ADMIN NGẪU NHIÊN ---
            int randomAdminId = ADMIN_IDS[new java.util.Random().nextInt(ADMIN_IDS.length)];
            String sqlAddAdminEscrow = "UPDATE public.users SET escrow_balance = escrow_balance + ? WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlAddAdminEscrow)) {
                ps.setDouble(1, amount);
                ps.setInt(2, randomAdminId);
                return ps.executeUpdate() > 0;
            }

        } else if ("+".equals(operator)) {
            // --- BƯỚC 4: HOÀN TIỀN VÀO VÍ CHÍNH CHO NGƯỜI BỊ ĐÈ GIÁ ---
            String sqlRefundUser = "UPDATE public.users SET balance = balance + ? WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlRefundUser)) {
                ps.setDouble(1, amount);
                ps.setInt(2, userId);
                return ps.executeUpdate() > 0;
            }
        }
        return false;
    }

    /**
     * GIẢI NGÂN DÒNG TIỀN PHIÊN ĐẤU GIÁ THÀNH CÔNG (15% Admin Hoa hồng - 85% Người bán)
     */
    public boolean processAcceptPayment(Connection conn, int sellerId, int adminId, double currentAuctionPrice) throws SQLException {
        double adminCommission = currentAuctionPrice * 0.15;
        double sellerAmount = currentAuctionPrice * 0.85;

        // BƯỚC 1: Trừ tiền ví tạm Admin xử lý phiên này, cộng 15% hoa hồng thẳng vào doanh thu hệ thống (system_revenue)
        String sqlAdmin = "UPDATE public.users SET escrow_balance = escrow_balance - ?, system_revenue = system_revenue + ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlAdmin)) {
            ps.setDouble(1, currentAuctionPrice);
            ps.setDouble(2, adminCommission);
            ps.setInt(3, adminId);
            ps.executeUpdate();
        }

        // BƯỚC 2: Cộng chuẩn xác 85% số tiền đấu giá vào ví chính (balance) của Seller (Người bán)
        String sqlSeller = "UPDATE public.users SET balance = balance + ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlSeller)) {
            ps.setDouble(1, sellerAmount);
            ps.setInt(2, sellerId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * PHẠT BÙNG KÈO ĐẤU GIÁ (Thu phí phạt 7% cho Admin - Hoàn trả 93% số còn lại cho Winner)
     */
    public boolean processPenalty7Percent(Connection conn, int userId, int adminId, double currentAuctionPrice) throws SQLException {
        double penaltyFee = currentAuctionPrice * 0.07;
        double refundAmount = currentAuctionPrice * 0.93;

        // BƯỚC 1: Giải phóng ví tạm Admin, trích thu 7% tiền phạt vào doanh thu hệ thống
        String sqlAdmin = "UPDATE public.users SET escrow_balance = escrow_balance - ?, system_revenue = system_revenue + ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlAdmin)) {
            ps.setDouble(1, currentAuctionPrice);
            ps.setDouble(2, penaltyFee);
            ps.setInt(3, adminId);
            ps.executeUpdate();
        }

        // BƯỚC 2: Hoàn trả lại 93% số tiền còn lại về ví chính cho Bidder thắng cuộc
        String sqlUser = "UPDATE public.users SET balance = balance + ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlUser)) {
            ps.setDouble(1, refundAmount);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cập nhật thủ công quỹ Admin (Dùng cho các trường hợp điều chỉnh số dư đặc biệt từ Service)
     */
    public boolean updateAdminFunds(Connection conn, int adminId, double escrowAmount, String escrowOp, double revenueAmount) throws SQLException {
        if (!"+".equals(escrowOp) && !"-".equals(escrowOp)) return false;

        String sql = "UPDATE public.users SET escrow_balance = escrow_balance " + escrowOp + " ?, system_revenue = system_revenue + ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, escrowAmount);
            ps.setDouble(2, revenueAmount);
            ps.setInt(3, adminId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Lấy số dư ví chính (balance) của người dùng
     */
    public double getBalance(int userId) {
        String sql = "SELECT balance FROM public.users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1.0;
    }

    /**
     * Lấy số dư ví ký quỹ tạm thời (escrow_balance) của tài khoản Admin
     */
    public double getEscrowBalance(int userId) {
        String sql = "SELECT escrow_balance FROM public.users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("escrow_balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }
}
