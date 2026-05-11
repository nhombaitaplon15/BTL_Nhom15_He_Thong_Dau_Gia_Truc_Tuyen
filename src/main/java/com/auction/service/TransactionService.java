package com.auction.service;

import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.TransactionDAO;

public class TransactionService {
    private final TransactionDAO transDAO = new TransactionDAO();

    /**
     * Gửi yêu cầu nạp tiền (Chờ duyệt)
     */
    public void handleDepositRequest(User currentUser, double amount) {
        if (amount <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền phải lớn hơn 0");
        }

        // Gọi đúng DAO để lưu log PENDING
        boolean success = transDAO.createTransaction(currentUser.getId(), amount, "DEPOSIT");

        if (!success) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể gửi yêu cầu nạp tiền!");
        }
        System.out.println(">>> Đã gửi yêu cầu nạp " + amount + ". Chờ Admin duyệt.");
    }

    /**
     * Admin phê duyệt nạp/rút
     */
    public void handleApproveTransaction(User adminUser, int transId, User targetUser, double amount, String type) {
        // 1. Kiểm tra quyền Admin
        if (!adminUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không có quyền duyệt!");
        }

        // 2. Kiểm tra sơ bộ số dư nếu là rút tiền
        if (type.equalsIgnoreCase("WITHDRAW") && targetUser.getBalance() < amount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư User không đủ!");
        }

        // 3. Thực hiện cập nhật SQL (Dùng Transaction trong DAO)
        boolean isSuccessSQL = transDAO.processApproval(transId, targetUser.getId(), amount, type);

        // 4. Cập nhật RAM nếu SQL thành công
        if (isSuccessSQL) {
            double newBalance = type.equalsIgnoreCase("DEPOSIT")
                    ? targetUser.getBalance() + amount
                    : targetUser.getBalance() - amount;

            targetUser.setBalance(newBalance);
            System.out.println(">>> [DUYỆT THÀNH CÔNG] Đã cập nhật số dư cho " + targetUser.getUsername());
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Duyệt tiền thất bại tại Database!");
        }
    }
}