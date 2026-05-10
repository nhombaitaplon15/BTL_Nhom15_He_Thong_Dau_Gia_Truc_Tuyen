package com.auction.service;

import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.dao.UserDAO;

public class TransactionService {
    private PaymentDAO paymentDAO = new PaymentDAO(); // Dùng cái mới này
    private TransactionDAO transDAO = new TransactionDAO();
    // yêu cầu nạp tiền chỉ lưu vào sql chưa đổi số dư
    public void handleDepositRequest(User currentUser, double amount) {
        if (amount <= 0) throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền phải > 0");

        // Lưu vào SQL (Yêu cầu chờ duyệt)
        boolean success = PaymentDAO.createTransaction(currentUser.getId(), amount, "DEPOSIT");

        if (!success) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống: Không thể gửi yêu cầu nạp!");
        }
        System.out.println("Đã gửi yêu cầu nạp tiền vào hệ thống. Vui lòng chờ Admin duyệt.");
    }

    // admin phê duyệt nạp rút
    public void handleApproveTransaction(User adminUser, int transId, User targetUser, double amount, String type) {
        // 1. Kiểm tra quyền Admin (Sử dụng hàm isAdmin())
        if (!adminUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Chỉ Admin mới có quyền duyệt tiền!");
        }
        // kiểm tra số dư
        if (type.equalsIgnoreCase("WITHDRAW") && targetUser.getBalance() < amount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư người dùng không đủ để thực hiện rút tiền!");
        }
        // 2. Cập nhật SQL (Quan trọng nhất)
        // Gọi DAO để thực hiện logic Transaction SQL bên trên
        boolean isSuccessSQL = userDAO.processApproval(transId, targetUser.getId(), amount, type);

        // 3. Cập nhật RAM (Chỉ khi SQL đã thành công)
        if (isSuccessSQL) {
            double newBalance = type.equalsIgnoreCase("DEPOSIT")
                    ? targetUser.getBalance() + amount
                    : targetUser.getBalance() - amount;

            targetUser.setBalance(newBalance); // Đồng bộ RAM để UI hiển thị đúng
            System.out.println("Duyệt thành công! SQL và RAM đã được cập nhật.");
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database: Duyệt tiền thất bại!");
        }
    }

}
