package com.auction.server.service;

import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.dao.PaymentDAO ;
import com.auction.server.dao.DBConnection ;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

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
        try (Connection conn = DBConnection.getConnection()) {
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
    // 3. Tạo hóa đơn thanh toán khi chốt phiên đấu giá
    public void createTransactionFromAuction(int auctionId, int winnerId, double amount) {
        // winnerId = 0 nghĩa là phiên kết thúc không có người thắng — vẫn ghi nhận để audit
        try {
            String type = "AUCTION_PAYMENT";
            // Nếu không có người thắng, ghi vào user_id = 0 hoặc bỏ qua tùy chính sách
            if (winnerId <= 0) {
                System.out.println(">>> [TRANSACTION] Phiên #" + auctionId + " không có người thắng, bỏ qua tạo giao dịch.");
                return;
            }
            boolean success = transDAO.createTransaction(winnerId, amount, type, "PENDING");
            if (!success) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể tạo hóa đơn giao dịch!");
            }
            System.out.println(">>> [TRANSACTION] Đã tạo hóa đơn " + amount + " cho User ID: " + winnerId);
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi tạo giao dịch: " + e.getMessage());
        }
    }
    public List<TransactionRequest> getAllTransactions() {
        return transDAO.getAllTransactions();
    }

    public void approveTransaction(Integer txId) {

        if (txId == null) {
            throw new AuctionException(
                    ErrorCode.INVALID_INPUT.name(),
                    "Transaction ID không hợp lệ!"
            );
        }

        // Tìm transaction theo ID
        TransactionRequest target = null;

        for (TransactionRequest tx : transDAO.getAllTransactions()) {
            if (tx.getRequestId() == txId) {
                target = tx;
                break;
            }
        }

        if (target == null) {
            throw new AuctionException(
                    ErrorCode.TRANSACTION_FAILED.name(),
                    "Không tìm thấy giao dịch!"
            );
        }

        boolean success = transDAO.processApproval(
                target.getRequestId(),
                target.getUser().getId(),
                target.getAmount(),
                target.getType()
        );

        if (!success) {
            throw new AuctionException(
                    ErrorCode.INTERNAL_ERROR.name(),
                    "Duyệt giao dịch thất bại!"
            );
        }
    }

    public void rejectTransaction(Integer txId) {

        if (txId == null) {
            throw new AuctionException(
                    ErrorCode.INVALID_INPUT.name(),
                    "Transaction ID không hợp lệ!"
            );
        }

        boolean success = transDAO.rejectTransaction(txId);

        if (!success) {
            throw new AuctionException(
                    ErrorCode.INTERNAL_ERROR.name(),
                    "Từ chối giao dịch thất bại!"
            );
        }
    }
    public void handleWithdrawRequest(User user, double amount, String bankInfo) {
        if (user == null) {
            throw new AuctionException(
                    ErrorCode.UNAUTHORIZED.name(),
                    "Người dùng không hợp lệ!");
        }
        if (amount <= 0) {
            throw new AuctionException(
                    ErrorCode.INVALID_INPUT.name(),
                    "Số tiền rút phải lớn hơn 0!");
        }
        if (bankInfo == null || bankInfo.isBlank()) {
            throw new AuctionException(
                    ErrorCode.INVALID_INPUT.name(),
                    "Thông tin ngân hàng không được để trống!");
        }
        if (user.getBalance() < amount) {
            throw new AuctionException(
                    ErrorCode.INSUFFICIENT_BALANCE.name(),
                    "Số dư không đủ để rút!");
        }

        try {
            boolean success = transDAO.createTransaction(
                    user.getId(),
                    amount,
                    "WITHDRAW",
                    "PENDING");
            if (!success) {
                throw new AuctionException(
                        ErrorCode.TRANSACTION_FAILED.name(),
                        "Không thể tạo yêu cầu rút tiền!");
            }
        } catch (Exception e) {
            throw new AuctionException(
                    ErrorCode.INTERNAL_ERROR.name(),
                    e.getMessage());
        }
    }

}
