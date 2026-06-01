package com.auction.server.service;

import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.TransactionDAO ;
import com.auction.server.dao.PaymentDAO ;
import com.auction.server.dao.DBConnection ;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class TransactionService {
    private final TransactionDAO transDAO = new TransactionDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ManagerService managerService; // Điều phối đồng bộ User gốc trên RAM Server

    public TransactionService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // --- 1. NGƯỜI DÙNG ĐĂNG KÝ YÊU CẦU (NẠP / RÚT) ---

    /** Người dùng gửi yêu cầu nạp tiền (Chờ duyệt) */
    public void handleDepositRequest(User currentUser, double amount) {
        if (amount <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền nạp phải lớn hơn 0");
        }

        try {
            // Lưu xuống DB với trạng thái ban đầu bắt buộc là "PENDING"
            boolean success = transDAO.createTransaction(currentUser.getId(), amount, "DEPOSIT", "PENDING");

            if (!success) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể gửi yêu cầu nạp tiền!");
            }
            System.out.println(">>> [DEPOSIT] Đã gửi yêu cầu nạp " + amount + " từ User ID " + currentUser.getId() + ". Chờ Admin duyệt.");
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi tạo yêu cầu nạp: " + e.getMessage());
        }
    }

    /** Người dùng gửi yêu cầu rút tiền (Chờ duyệt) - Bản nâng cấp bảo mật chặn Spam số dư */
    public void handleWithdrawRequest(User user, double amount, String bankInfo) {
        if (user == null) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Người dùng không hợp lệ!");
        }
        if (amount <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền rút phải lớn hơn 0!");
        }
        if (user.getBalance() < amount) {
            throw new AuctionException(ErrorCode.INSUFFICIENT_BALANCE.name(), "Số dư tài khoản không đủ để thực hiện lệnh rút!");
        }

        String description = (bankInfo == null || bankInfo.isBlank())
                ? "WITHDRAW"
                : "WITHDRAW - Ngân hàng: " + bankInfo;

        try {
            boolean success = transDAO.createTransaction(user.getId(), amount, description, "PENDING");
            if (!success) {
                throw new AuctionException(ErrorCode.TRANSACTION_FAILED.name(), "Không thể tạo yêu cầu rút tiền!");
            }
            System.out.println(">>> [WITHDRAW] Đã gửi yêu cầu rút " + amount + " từ User ID " + user.getId() + ". Chờ Admin duyệt.");
        } catch (Exception e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi khi tạo yêu cầu rút tiền: " + e.getMessage());
        }
    }

    /** Overload hàm rút tiền 2 tham số để tránh làm lỗi các module cũ của bạn bạn */
    public void handleWithdrawRequest(User currentUser, double amount) {
        handleWithdrawRequest(currentUser, amount, "Khác");
    }

    // --- 2. HÓA ĐƠN TỰ ĐỘNG TỪ HỆ THỐNG ---

    /** Tạo hóa đơn thanh toán tự động khi chốt phiên đấu giá thành công */
    public void createTransactionFromAuction(int auctionId, int winnerId, double amount) {
        if (winnerId <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "ID người thắng không hợp lệ!");
        }

        try {
            String description = "Thanh toán phiên đấu giá #" + auctionId;
            boolean success = transDAO.createTransaction(winnerId, amount, description, "PENDING");

            if (!success) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể tạo hóa đơn giao dịch đấu giá!");
            }
            System.out.println(">>> [TRANSACTION] Đã tạo hóa đơn " + amount + " cho User ID: " + winnerId);

        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi tạo giao dịch đấu giá: " + e.getMessage());
        }
    }

    // --- 3. ĐIỀU KHIỂN & PHÊ DUYỆT TỪ ADMIN ---

    /** Admin phê duyệt trực tiếp dòng tiền (Bọc Transaction nguyên tử an toàn tuyệt đối) */
    public void handleApproveTransaction(User adminUser, int transId, int targetUserId, double amount, String type) {
        if (!adminUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không có quyền duyệt giao dịch này!");
        }

        // Lấy Object User gốc chạy trên RAM ra để đồng bộ dữ liệu Realtime
        User liveUser = managerService.getUserById(targetUserId);
        if (liveUser == null) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Không tìm thấy người dùng này trên hệ thống RAM Server!");
        }

        if (type.equalsIgnoreCase("WITHDRAW") && liveUser.getBalance() < amount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư người dùng trên hệ thống không đủ để rút!");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước A: Cập nhật số dư ví dưới Database SQL
                String operator = type.equalsIgnoreCase("DEPOSIT") ? "+" : "-";
                if (!paymentDAO.updateBalance(conn, targetUserId, amount, operator)) {
                    throw new SQLException("Cập nhật số dư tài khoản thất bại!");
                }

                // Bước B: Đổi trạng thái lịch sử giao dịch thành 'SUCCESS'
                if (!transDAO.updateTransactionStatus(conn, transId, "SUCCESS")) {
                    throw new SQLException("Không thể cập nhật trạng thái giao dịch sang SUCCESS!");
                }

                conn.commit(); // Chốt giao dịch DB hoàn tất

                // Bước C: Đồng bộ tức thời lên RAM để Client nhận diện thay đổi qua Socket
                double newBalance = type.equalsIgnoreCase("DEPOSIT")
                        ? liveUser.getBalance() + amount
                        : liveUser.getBalance() - amount;
                liveUser.setBalance(newBalance);

                System.out.println(">>> [DUYỆT THÀNH CÔNG] " + type + " số tiền " + amount + " cho tài khoản " + liveUser.getUsername());

            } catch (SQLException e) {
                conn.rollback(); // Hoàn tác lập tức nếu xảy ra xung đột dữ liệu
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi xử lý dòng tiền: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    /** Lấy toàn bộ danh sách giao dịch hiển thị cho trang quản trị Admin */
    public List<TransactionRequest> getAllTransactions() {
        return transDAO.getAllTransactions();
    }

    /** Phê duyệt giao dịch thông qua đối tượng yêu cầu (TransactionRequest Mapping) */
    public void approveTransaction(Integer txId) {
        if (txId == null) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Transaction ID không hợp lệ!");
        }

        TransactionRequest target = null;
        for (TransactionRequest tx : transDAO.getAllTransactions()) {
            if (tx.getRequestId() == txId) {
                target = tx;
                break;
            }
        }

        if (target == null) {
            throw new AuctionException(ErrorCode.TRANSACTION_FAILED.name(), "Không tìm thấy giao dịch yêu cầu!");
        }

        boolean success = transDAO.processApproval(
                target.getRequestId(),
                target.getUser().getId(),
                target.getAmount(),
                target.getType()
        );

        if (!success) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Duyệt giao dịch thất bại!");
        }
    }

    /** Từ chối phê duyệt yêu cầu nạp/rút tiền */
    public void rejectTransaction(Integer txId) {
        if (txId == null) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Transaction ID không hợp lệ!");
        }

        boolean success = transDAO.rejectTransaction(txId);

        if (!success) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Từ chối giao dịch thất bại!");
        }
    }

    /** Lấy danh sách lịch sử giao dịch của một user cụ thể */
    public java.util.List<com.auction.common.model.TransactionRequest> getTransactionsByUser(int userId) {
        return transDAO.getTransactionsByUserId(userId);
    }

    /**
     * Tính tổng tiền đang bị tạm giữ (escrow) của user
     * = tổng bid amount ở các phiên user đang dẫn đầu (RUNNING auctions)
     */
    public double getUserEscrowAmount(int userId) {
        try {
            return transDAO.getUserEscrowAmount(userId);
        } catch (Exception e) {
            return 0.0;
        }
    }

}