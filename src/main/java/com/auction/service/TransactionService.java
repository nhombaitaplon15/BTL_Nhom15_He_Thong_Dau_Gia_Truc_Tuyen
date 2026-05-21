package com.auction.service;

import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.DatabaseConnection;
import com.auction.service.*;

import java.sql.Connection;
import java.sql.SQLException;

public class TransactionService {
    private final TransactionDAO transDAO = new TransactionDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ManagerService managerService; // Dùng để tìm Object User gốc trên RAM Server

    public TransactionService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // 1. Người dùng gửi yêu cầu nạp tiền (Chờ duyệt)
    public void handleDepositRequest(User currentUser, double amount) {
        if (amount <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền phải lớn hơn 0");
        }

        try {
            // Gọi hàm createTransaction loại KHÔNG CÓ CONNECTION (tự lấy conn nội bộ trong DAO)
            // Trạng thái lưu xuống ban đầu bắt buộc là "PENDING"
            boolean success = transDAO.createTransaction(currentUser.getId(), amount, "DEPOSIT", "PENDING");

            if (!success) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể gửi yêu cầu nạp tiền!");
            }
            System.out.println(">>> Đã gửi yêu cầu nạp " + amount + ". Chờ Admin duyệt.");
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi tạo yêu cầu nạp: " + e.getMessage());
        }
    }
    public void handleWithdrawRequest(User currentUser, double amount) {
        if (amount <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền phải lớn hơn 0");
        }
        try {
            // Gọi hàm createTransaction loại KHÔNG CÓ CONNECTION (tự lấy conn nội bộ trong DAO)
            // Trạng thái lưu xuống ban đầu bắt buộc là "PENDING"
            boolean success = transDAO.createTransaction(currentUser.getId(), amount, "WITHDRAW", "PENDING");

            if (!success) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể gửi yêu cầu nạp tiền!");
            }
            System.out.println(">>> Đã gửi yêu cầu rút " + amount + ". Chờ Admin duyệt.");
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi tạo yêu cầu rút: " + e.getMessage());
        }
    }

    // 2. Admin phê duyệt nạp / rút tiền
    public void handleApproveTransaction(User adminUser, int transId, int targetUserId, double amount, String type) {
        // Kiểm tra quyền Admin
        if (!adminUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không có quyền duyệt!");
        }

        // Lấy Object User gốc đang chạy trên RAM của Server ra để lát nữa đồng bộ UI
        User liveUser = managerService.getUserById(targetUserId);
        if (liveUser == null) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Không tìm thấy người dùng này trên hệ thống!");
        }

        // Kiểm tra số dư nếu là lệnh rút tiền
        if (type.equalsIgnoreCase("WITHDRAW") && liveUser.getBalance() < amount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư người dùng không đủ để rút!");
        }

        // Mở một Transaction bọc luồng duyệt tiền để đảm bảo: Cộng tiền + Đổi trạng thái SUCCESS phải đi liền nhau
        try (Connection conn =DatabaseConnection.connect()) {
            conn.setAutoCommit(false);
            try {
                // Bước A: Cập nhật số dư ví trong DB
                String operator = type.equalsIgnoreCase("DEPOSIT") ? "+" : "-";
                if (!paymentDAO.updateBalance(conn, targetUserId, amount, operator)) {
                    throw new SQLException("Cập nhật số dư tài khoản thất bại!");
                }

                // Bước B: Chuyển trạng thái giao dịch từ 'PENDING' sang 'SUCCESS' trong DB
                if (!transDAO.updateTransactionStatus(conn, transId, "SUCCESS")) {
                    throw new SQLException("Không thể cập nhật trạng thái giao dịch thành SUCCESS!");
                }

                // Chốt giao dịch DB
                conn.commit();

                // Bước C: Cập nhật RAM (Chỉ cập nhật khi DB đã lưu thành công)
                double newBalance = type.equalsIgnoreCase("DEPOSIT")
                        ? liveUser.getBalance() + amount
                        : liveUser.getBalance() - amount;

                liveUser.setBalance(newBalance); // Set vào Object liveUser (RAM gốc)

                System.out.println(">>> [DUYỆT THÀNH CÔNG] " + type + " " + amount + " cho " + liveUser.getUsername());

            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi xử lý dòng tiền: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }
}