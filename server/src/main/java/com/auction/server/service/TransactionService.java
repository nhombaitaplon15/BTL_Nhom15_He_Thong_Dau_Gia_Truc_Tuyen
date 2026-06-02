package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.DBConnection;
import com.auction.server.core.SessionManager;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class TransactionService {
    private final TransactionDAO transDAO = new TransactionDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ManagerService managerService;

    public TransactionService(ManagerService managerService) {
        this.managerService = managerService;
    }

    public void handleDepositRequest(User currentUser, double amount) {
        if (amount <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền nạp phải lớn hơn 0");
        }
        try {
            boolean success = transDAO.createTransaction(currentUser.getId(), amount, "DEPOSIT", "PENDING", "Nạp tiền vào tài khoản");

            if (!success) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể gửi yêu cầu nạp tiền!");
            }
            System.out.println(">>> [DEPOSIT] Đã gửi yêu cầu nạp " + amount + " từ User ID " + currentUser.getId() + ". Chờ Admin duyệt.");
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi tạo yêu cầu nạp: " + e.getMessage());
        }
    }

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
            ? "Rút tiền về tài khoản"
            : "Rút tiền - Ngân hàng: " + bankInfo;

        try {
            boolean success = transDAO.createTransaction(user.getId(), amount, "WITHDRAW", "PENDING", description);
            if (!success) {
                throw new AuctionException(ErrorCode.TRANSACTION_FAILED.name(), "Không thể tạo yêu cầu rút tiền!");
            }
            System.out.println(">>> [WITHDRAW] Đã gửi yêu cầu rút " + amount + " từ User ID " + user.getId() + ". Chờ Admin duyệt.");
        } catch (Exception e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi khi tạo yêu cầu rút tiền: " + e.getMessage());
        }
    }

    public void handleWithdrawRequest(User currentUser, double amount) {
        handleWithdrawRequest(currentUser, amount, "Khác");
    }

    public void createTransactionFromAuction(int auctionId, int winnerId, double amount) {
        if (winnerId <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "ID người thắng không hợp lệ!");
        }

        try {
            String description = "Thanh toán phiên đấu giá #" + auctionId;
            boolean success = transDAO.createTransaction(winnerId, amount, "AUCTION_PAY", "PENDING", description);

            if (!success) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể tạo hóa đơn giao dịch đấu giá!");
            }
            System.out.println(">>> [TRANSACTION] Đã tạo hóa đơn " + amount + " cho User ID: " + winnerId);

        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi tạo giao dịch đấu giá: " + e.getMessage());
        }
    }

    public void handleApproveTransaction(User adminUser, int transId, int targetUserId, double amount, String type) {
        if (!adminUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không có quyền duyệt giao dịch này!");
        }

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
                String operator = type.equalsIgnoreCase("DEPOSIT") ? "+" : "-";
                if (!paymentDAO.updateBalance(conn, targetUserId, amount, operator)) {
                    throw new SQLException("Cập nhật số dư tài khoản thất bại!");
                }

                if (!transDAO.updateTransactionStatus(conn, transId, "SUCCESS")) {
                    throw new SQLException("Không thể cập nhật trạng thái giao dịch sang SUCCESS!");
                }

                conn.commit();

                double newBalance = type.equalsIgnoreCase("DEPOSIT")
                    ? liveUser.getBalance() + amount
                    : liveUser.getBalance() - amount;
                liveUser.setBalance(newBalance);

                System.out.println(">>> [DUYỆT THÀNH CÔNG] " + type + " số tiền " + amount + " cho tài khoản " + liveUser.getUsername());

            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi xử lý dòng tiền: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    public List<TransactionRequest> getAllTransactions() {
        return transDAO.getAllTransactions();
    }

    public List<TransactionRequest> getTransactionsByUserId(int userId) {
        return transDAO.getTransactionsByUserId(userId);
    }

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

        User liveUser = managerService.getUserById(target.getUser().getId());
        if (liveUser != null) {
            double amount = target.getAmount();
            if (target.getType().equalsIgnoreCase("DEPOSIT")) {
                liveUser.setBalance(liveUser.getBalance() + amount);
            } else if (target.getType().equalsIgnoreCase("WITHDRAW")) {
                liveUser.setBalance(liveUser.getBalance() - amount);
            }

            System.out.println(">>> [ĐỒNG BỘ RAM] Cập nhật số dư mới cho User#" + liveUser.getId() + ": " + liveUser.getBalance());

            try {
                SessionManager.getInstance().sendToUserIfOnline(
                    liveUser.getId(),
                    new Message(ResponseCode.PROFILE_RESULT, "Cập nhật số dư", liveUser)
                );
            } catch (Exception ignored) {}
        }
    }

    public void rejectTransaction(Integer txId) {
        if (txId == null) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Transaction ID không hợp lệ!");
        }

        boolean success = transDAO.rejectTransaction(txId);

        if (!success) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Từ chối giao dịch thất bại!");
        }
    }

    public void processAuctionWinnerPayment(int auctionId, int userId) {
        Auction auction;
        try {
            auction = managerService.getAuctionOrThrow(auctionId);
        } catch (Exception e) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), e.getMessage());
        }

        if (auction.getCurrentWinnerId() == null || auction.getCurrentWinnerId() != userId) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Lỗi bảo mật: Bạn không phải là người chiến thắng phiên này.");
        }

        int[] ESCROW_ADMIN_IDS = {1, 2, 3, 4};
        int assignedAdminId = ESCROW_ADMIN_IDS[auctionId % ESCROW_ADMIN_IDS.length];

        AuctionDAO auctionDAO = new AuctionDAO();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            boolean success = paymentDAO.processAcceptPayment(conn, auction.getSellerId(), assignedAdminId, auction.getCurrentPrice());

            if (success) {
                auctionDAO.updateStatus(auctionId, "SOLD");
                conn.commit();
            } else {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi xử lý dòng tiền nội bộ. Giao dịch bị hủy.");
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối CSDL: " + e.getMessage());
        }
    }

    public void processAuctionWinnerPenalty(int auctionId, int userId) {
        Auction auction;
        try {
            auction = managerService.getAuctionOrThrow(auctionId);
        } catch (Exception e) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), e.getMessage());
        }

        if (auction.getCurrentWinnerId() == null || auction.getCurrentWinnerId() != userId) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Lỗi bảo mật: Bạn không phải là người chiến thắng phiên này.");
        }

        int[] ESCROW_ADMIN_IDS = {1, 2, 3, 4};
        int assignedAdminId = ESCROW_ADMIN_IDS[auctionId % ESCROW_ADMIN_IDS.length];

        AuctionDAO auctionDAO = new AuctionDAO();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            boolean success = paymentDAO.processPenalty7Percent(conn, userId, assignedAdminId, auction.getCurrentPrice());

            if (success) {
                auctionDAO.updateStatus(auctionId, "REJECTED");
                conn.commit();
            } else {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi trừ tiền phạt cọc. Giao dịch bị hủy.");
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối CSDL: " + e.getMessage());
        }
    }
}