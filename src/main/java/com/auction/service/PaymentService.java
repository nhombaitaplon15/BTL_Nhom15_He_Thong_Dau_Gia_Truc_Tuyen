package com.auction.service;

import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.dao.DBConnection;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import java.sql.Connection;
import java.sql.SQLException;

public class PaymentService {
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final TransactionDAO transDAO = new TransactionDAO();
    private static final int ADMIN_ID = 1;
    private static final double SYSTEM_FEE_RATE = 0.15;
    // tạm giữ tiền
    public void holdFunds(int bidderId, double amount, int auctionId) {
        // Kiểm tra số dư trước khi mở Transaction nặng
        if (paymentDAO.getBalance(bidderId) < amount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư không đủ!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // BẮT ĐẦU GIAO DỊCH
            try {
                // Bước 1: Trừ tiền người mua
                if (!paymentDAO.updateBalance(conn, bidderId, amount, "-"))
                    throw new SQLException("Không thể trừ tiền người mua (Số dư ảo?)");

                // Bước 2: Cộng vào kho giữ hộ Admin
                if (!paymentDAO.updateAdminFunds(conn, ADMIN_ID, amount, "+", 0))
                    throw new SQLException("Không thể cộng tiền vào kho Admin");

                // Bước 3: Ghi Log
                transDAO.createTransaction(conn, bidderId, amount, "HOLD_AUCTION_" + auctionId);

                conn.commit(); // CHỐT DỮ LIỆU XUỐNG DB
            } catch (SQLException e) {
                conn.rollback(); // LỖI LÀ HỦY TẤT CẢ, TIỀN VỀ VỊ TRÍ CŨ
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi thanh toán: " + e.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    // giải ngân
    public void releaseFunds(int sellerId, double totalAmount, int auctionId) {
        double fee = totalAmount * SYSTEM_FEE_RATE;
        double finalAmount = totalAmount - fee;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!paymentDAO.updateAdminFunds(conn, ADMIN_ID, totalAmount, "-", fee))
                    throw new SQLException("Lỗi rút tiền kho Admin");

                if (!paymentDAO.updateBalance(conn, sellerId, finalAmount, "+"))
                    throw new SQLException("Lỗi cộng tiền người bán");

                transDAO.createTransaction(conn, sellerId, finalAmount, "RELEASE_AUCTION_" + auctionId);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi giải ngân: " + e.getMessage());
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }
    // hoàn tiền
    public void refundBuyer(int bidderId, double amount, int auctionId) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!paymentDAO.updateAdminFunds(conn, ADMIN_ID, amount, "-", 0))
                    throw new SQLException("Lỗi rút tiền kho Admin");

                if (!paymentDAO.updateBalance(conn, bidderId, amount, "+"))
                    throw new SQLException("Lỗi hoàn tiền người mua");

                transDAO.createTransaction(conn, bidderId, amount, "REFUND_AUCTION_" + auctionId);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hoàn tiền: " + e.getMessage());
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }
}