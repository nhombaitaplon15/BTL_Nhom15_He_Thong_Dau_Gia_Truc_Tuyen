package com.auction.server.service;

import com.auction.server.dao.DBConnection;
import com.auction.server.dao.PaymentDAO ;
import com.auction.server.dao.TransactionDAO ;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import java.sql.Connection;
import java.sql.SQLException;

public class PaymentService {
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final TransactionDAO transDAO = new TransactionDAO();
    private static final int ADMIN_ID = 1;
    private static final double SYSTEM_FEE_RATE = 0.15;

    // 1. TẠM GIỮ TIỀN NGUỜI MUA
    public void holdFunds(int bidderId, double amount, int auctionId) {
        if (paymentDAO.getBalance(bidderId) < amount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư không đủ!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: Trừ tiền người mua
                if (!paymentDAO.updateBalance(conn, bidderId, amount, "-"))
                    throw new SQLException("Không thể trừ tiền người mua");

                // Bước 2: Cộng vào kho giữ hộ Admin
                if (!paymentDAO.updateAdminFunds(conn, ADMIN_ID, amount, "+", 0))
                    throw new SQLException("Không thể cộng tiền vào kho Admin");

                // Bước 3: Ghi Log (SỬA: Thêm tham số trạng thái "SUCCESS")
                transDAO.createTransaction(conn, bidderId, amount, "HOLD_AUCTION_" + auctionId, "SUCCESS");

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi thanh toán: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    // 2. GIẢI NGÂN TIỀN CHO NGƯỜI BÁN
    public void releaseFunds(int sellerId, double totalAmount, int auctionId) {
        double fee = totalAmount * SYSTEM_FEE_RATE;
        double finalAmount = totalAmount - fee;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: Trừ tổng số tiền đang tạm giữ ra khỏi kho giữ hộ của Admin
                if (!paymentDAO.updateAdminFunds(conn, ADMIN_ID, totalAmount, "-", fee))
                    throw new SQLException("Lỗi rút tiền kho Admin");

                // Bước 2: Cộng tiền thực nhận (đã trừ phí) vào tài khoản người bán
                if (!paymentDAO.updateBalance(conn, sellerId, finalAmount, "+"))
                    throw new SQLException("Lỗi cộng tiền người bán");

                // Bước 3: FIX LỖI DÒNG TIỀN - Cộng tiền phí (15%) vào ví khả dụng (Doanh thu thực) của Admin
                if (!paymentDAO.updateBalance(conn, ADMIN_ID, fee, "+")) {
                    throw new SQLException("Lỗi cộng tiền phí vào ví doanh thu Admin");
                }

                // Bước 4: Ghi Log cho người bán (SỬA: Thêm tham số "SUCCESS")
                transDAO.createTransaction(conn, sellerId, finalAmount, "RELEASE_AUCTION_" + auctionId, "SUCCESS");

                // Bước 5: Ghi Log doanh thu sàn cho Admin (SỬA: Thêm tham số "SUCCESS")
                transDAO.createTransaction(conn, ADMIN_ID, fee, "PROFIT_AUCTION_" + auctionId, "SUCCESS");

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi giải ngân: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    // 3. HOÀN TIỀN LẠI CHO NGƯỜI MUA
    public void refundBuyer(int bidderId, double amount, int auctionId) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: Trừ tiền từ kho giữ hộ của Admin
                if (!paymentDAO.updateAdminFunds(conn, ADMIN_ID, amount, "-", 0))
                    throw new SQLException("Lỗi rút tiền kho Admin");

                // Bước 2: Cộng lại tiền vào ví người mua
                if (!paymentDAO.updateBalance(conn, bidderId, amount, "+"))
                    throw new SQLException("Lỗi hoàn tiền người mua");

                // Bước 3: Ghi Log (SỬA: Thêm tham số "SUCCESS")
                transDAO.createTransaction(conn, bidderId, amount, "REFUND_AUCTION_" + auctionId, "SUCCESS");

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hoàn tiền: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }
}