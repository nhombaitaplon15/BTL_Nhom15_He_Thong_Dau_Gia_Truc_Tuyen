package com.auction.service;

import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;

public class PaymentService {
    private PaymentDAO paymentDAO = new PaymentDAO();
    private TransactionDAO transDAO = new TransactionDAO();

    // ID của Admin hệ thống (thường cố định trong DB, ví dụ là 1)
    private static final int ADMIN_ID = 1;
    // Phí sàn 15%
    private static final double SYSTEM_FEE_RATE = 0.15;

    /**
     * BƯỚC 1: Tạm giữ tiền (Hold Funds)
     * Thực hiện khi có người thắng cuộc đấu giá.
     * Tiền đi từ: Ví người mua -> Kho tạm giữ (Escrow) của Admin.
     */
    public void holdFunds(int bidderId, double amount, int auctionId) {
        // 1. Kiểm tra số dư trước khi trừ
        double currentBalance = paymentDAO.getBalance(bidderId);
        if (currentBalance < amount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư không đủ để thanh toán!");
        }

        // 2. Trừ tiền người mua
        boolean step1 = paymentDAO.updateBalance(bidderId, amount, "-");

        // 3. Cộng vào kho Escrow của Admin
        boolean step2 = paymentDAO.updateAdminFunds(ADMIN_ID, amount, "+", 0);

        if (step1 && step2) {
            // Ghi nhật ký giao dịch
            transDAO.createTransaction(bidderId, amount, "ESCROW_HOLD_AUCTION_" + auctionId);
            System.out.println(">>> Đã tạm giữ " + amount + " từ User " + bidderId + " cho phiên " + auctionId);
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống khi xử lý thanh toán trung gian!");
        }
    }

    /**
     * BƯỚC 2: Giải ngân cho người bán (Release Funds)
     * Thực hiện sau khi người mua xác nhận đã nhận hàng thành công.
     * Tiền đi từ: Kho Escrow của Admin -> Chia làm 2 phần (Phí cho Admin & Tiền cho Người bán).
     */
    public void releaseFunds(int sellerId, double totalAmount, int auctionId) {
        double fee = totalAmount * SYSTEM_FEE_RATE;
        double finalAmount = totalAmount - fee;

        // 1. Rút tiền từ kho Escrow Admin, đồng thời ghi nhận doanh thu (Revenue)
        boolean step1 = paymentDAO.updateAdminFunds(ADMIN_ID, totalAmount, "-", fee);

        // 2. Cộng tiền thực nhận cho người bán
        boolean step2 = paymentDAO.updateBalance(sellerId, finalAmount, "+");

        if (step1 && step2) {
            // Ghi nhật ký
            transDAO.createTransaction(sellerId, finalAmount, "SELLER_RECEIVE_AUCTION_" + auctionId);
            System.out.println(">>> Đã giải ngân cho Người bán " + sellerId + ": " + finalAmount + " (Phí: " + fee + ")");
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống khi giải ngân tiền!");
        }
    }

    /**
     * BƯỚC 3: Hoàn tiền (Refund)
     * Trường hợp đơn hàng bị hủy hoặc người mua không nhận được hàng.
     * Tiền đi từ: Kho Escrow của Admin -> Trả lại ví người mua.
     */
    public void refundBuyer(int bidderId, double amount, int auctionId) {
        // 1. Rút từ kho Escrow Admin
        boolean step1 = paymentDAO.updateAdminFunds(ADMIN_ID, amount, "-", 0);

        // 2. Trả lại ví người mua
        boolean step2 = paymentDAO.updateBalance(bidderId, amount, "+");

        if (step1 && step2) {
            transDAO.createTransaction(bidderId, amount, "REFUND_AUCTION_" + auctionId);
            System.out.println(">>> Đã hoàn tiền cho User " + bidderId + ": " + amount);
        }
    }
}