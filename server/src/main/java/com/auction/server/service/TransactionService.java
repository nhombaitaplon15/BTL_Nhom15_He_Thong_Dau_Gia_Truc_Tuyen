package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.TransactionDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.AuctionDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * TransactionService — ĐÃ SỬA TOÀN DIỆN:
 *
 * ❌ LỖI CŨ 1: createTransactionFromAuction() chỉ tạo bản ghi PENDING trong DB,
 *    KHÔNG gọi paymentDAO.processAcceptPayment() → tiền không bao giờ chuyển thực sự.
 *
 * ❌ LỖI CŨ 2: Sync RAM (liveUser.setBalance) là vô nghĩa vì ManagerService.getUserById()
 *    gọi DB mỗi lần, không giữ cache → object bị GC ngay sau đó.
 *    Đây là hệ thống DB-centric + socket realtime, không cần sync RAM.
 *
 * ✅ SỬA 1: acceptAuctionPayment() thực sự giải ngân trong 1 transaction nguyên tử:
 *    - paymentDAO.processAcceptPayment(): trừ escrow_balance admin, +15% system_revenue, +85% balance seller
 *    - auctionDAO.updateStatus → "SOLD"
 *    - Ghi 2 bản ghi lịch sử: COMMISSION + SALE_REVENUE
 *    - Xóa bỏ hoàn toàn sync RAM (không cần, không đúng)
 *
 * ✅ SỬA 2: createTransactionFromAuction() chuyển hướng sang acceptAuctionPayment()
 *    để RequestDispatcher không cần sửa.
 */
public class TransactionService {
    private final TransactionDAO transDAO   = new TransactionDAO();
    private final PaymentDAO     paymentDAO = new PaymentDAO();
    private final AuctionDAO     auctionDAO = new AuctionDAO();
    private final ManagerService managerService;

    // Phải khớp với BiddingService và PaymentDAO
    private static final int[] ADMIN_IDS = {1, 2, 3, 4};

    public TransactionService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // ─── 1. USER: NẠP / RÚT ────────────────────────────────────────────────

    public void handleDepositRequest(User currentUser, double amount) {
        if (amount <= 0)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền nạp phải lớn hơn 0");
        try {
            boolean ok = transDAO.createTransaction(currentUser.getId(), amount, "DEPOSIT", "PENDING");
            if (!ok)
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể gửi yêu cầu nạp tiền!");
            System.out.println(">>> [DEPOSIT] Yêu cầu nạp " + amount + " từ User#" + currentUser.getId());
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi DB nạp tiền: " + e.getMessage());
        }
    }

    public void handleWithdrawRequest(User user, double amount, String bankInfo) {
        if (user == null)
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Người dùng không hợp lệ!");
        if (amount <= 0)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số tiền rút phải lớn hơn 0!");
        if (user.getBalance() < amount)
            throw new AuctionException(ErrorCode.INSUFFICIENT_BALANCE.name(), "Số dư không đủ để rút!");

        String description = (bankInfo == null || bankInfo.isBlank()) ? "WITHDRAW" : "WITHDRAW - Ngân hàng: " + bankInfo;
        try {
            boolean ok = transDAO.createTransaction(user.getId(), amount, description, "PENDING");
            if (!ok)
                throw new AuctionException(ErrorCode.TRANSACTION_FAILED.name(), "Không thể tạo yêu cầu rút tiền!");
            System.out.println(">>> [WITHDRAW] Yêu cầu rút " + amount + " từ User#" + user.getId());
        } catch (Exception e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi rút tiền: " + e.getMessage());
        }
    }

    public void handleWithdrawRequest(User currentUser, double amount) {
        handleWithdrawRequest(currentUser, amount, "Khác");
    }

    // ─── 2. CHẤP NHẬN MUA — GIẢI NGÂN NGUYÊN TỬ ────────────────────────────

    /**
     * ✅ PHƯƠNG THỨC CHÍNH khi winner bấm "Chấp nhận mua".
     *
     * Dòng tiền (1 transaction DB):
     *   escrow_balance[admin]  -= finalPrice         (giải phóng cọc)
     *   system_revenue[admin]  += finalPrice * 15%   (hoa hồng sàn)
     *   balance[seller]        += finalPrice * 85%   (tiền bán hàng)
     *   auction_status         → "SOLD"
     *   + 2 bản ghi lịch sử: COMMISSION + SALE_REVENUE
     *
     * Không có sync RAM vì ManagerService đọc DB mỗi lần (DB = source of truth).
     */
    public void acceptAuctionPayment(int auctionId, int winnerId, double price) {
        if (winnerId <= 0)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "ID người thắng không hợp lệ!");
        if (price <= 0)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Giá thắng phải lớn hơn 0!");

        Auction auction = auctionDAO.getAuctionById(auctionId);
        if (auction == null)
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không tìm thấy phiên #" + auctionId);

        // Xác nhận người gọi đúng là winner của phiên
        if (auction.getCurrentWinnerId() == null || auction.getCurrentWinnerId() != winnerId)
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải người thắng phiên #" + auctionId + "!");

        // Chỉ chấp nhận khi phiên đã đóng (SOLD là status autobot đặt khi có winner)
        String status = auction.getAuctionStatus();
        if (!"SOLD".equalsIgnoreCase(status) && !"FINISHED".equalsIgnoreCase(status))
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Phiên chưa kết thúc (trạng thái hiện tại: " + status + ")");

        int sellerId        = auction.getSellerId();
        int assignedAdminId = ADMIN_IDS[auctionId % ADMIN_IDS.length];
        double adminCommission = price * 0.15;
        double sellerReceived  = price * 0.85;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Giải ngân: trừ escrow admin, +15% system_revenue, +85% balance seller
                boolean paid = paymentDAO.processAcceptPayment(conn, sellerId, assignedAdminId, price);
                if (!paid)
                    throw new SQLException("processAcceptPayment thất bại — escrow Admin#" + assignedAdminId + " không đủ?");

                // Đánh dấu phiên đã thanh toán
                if (!auctionDAO.updateStatus(conn, auctionId, "PAID"))
                    throw new SQLException("Cập nhật status → PAID thất bại!");

                // Ghi lịch sử giao dịch
                transDAO.createTransaction(conn, assignedAdminId, adminCommission,
                        "COMMISSION_AUCTION_" + auctionId, "SUCCESS");
                transDAO.createTransaction(conn, sellerId, sellerReceived,
                        "SALE_REVENUE_AUCTION_" + auctionId, "SUCCESS");

                conn.commit();
                System.out.printf(">>> [ACCEPT] Phiên #%d: Admin#%d +15%%(%.0f), Seller#%d +85%%(%.0f)%n",
                        auctionId, assignedAdminId, adminCommission, sellerId, sellerReceived);

            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi giải ngân: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối DB khi giải ngân!");
        }
    }

    /**
     * Backward-compat: RequestDispatcher gọi hàm này, chuyển sang acceptAuctionPayment().
     */
    public void createTransactionFromAuction(int auctionId, int winnerId, double amount) {
        acceptAuctionPayment(auctionId, winnerId, amount);
    }

    // ─── 3. ADMIN: DUYỆT / TỪ CHỐI NẠP/RÚT ─────────────────────────────────

    public void handleApproveTransaction(User adminUser, int transId, int targetUserId, double amount, String type) {
        if (!adminUser.isAdmin())
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Không có quyền duyệt giao dịch!");

        // Lấy fresh từ DB (không dùng RAM cache)
        User targetUser = managerService.getUserById(targetUserId);
        if (targetUser == null)
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Không tìm thấy người dùng #" + targetUserId);

        if (type.equalsIgnoreCase("WITHDRAW") && targetUser.getBalance() < amount)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư không đủ để rút!");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String operator = type.equalsIgnoreCase("DEPOSIT") ? "+" : "-";
                if (!paymentDAO.updateBalance(conn, targetUserId, amount, operator))
                    throw new SQLException("Cập nhật số dư thất bại!");
                if (!transDAO.updateTransactionStatus(conn, transId, "SUCCESS"))
                    throw new SQLException("Cập nhật trạng thái giao dịch thất bại!");
                conn.commit();
                System.out.println(">>> [DUYỆT] " + type + " " + amount + " cho User#" + targetUserId);
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi duyệt: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối DB!");
        }
    }

    public List<TransactionRequest> getAllTransactions() {
        return transDAO.getAllTransactions();
    }

    public void approveTransaction(Integer txId) {
        if (txId == null)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Transaction ID không hợp lệ!");
        TransactionRequest target = null;
        for (TransactionRequest tx : transDAO.getAllTransactions()) {
            if (tx.getRequestId() == txId) { target = tx; break; }
        }
        if (target == null)
            throw new AuctionException(ErrorCode.TRANSACTION_FAILED.name(), "Không tìm thấy giao dịch!");
        boolean ok = transDAO.processApproval(target.getRequestId(), target.getUser().getId(),
                target.getAmount(), target.getType());
        if (!ok)
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Duyệt giao dịch thất bại!");
    }

    public void rejectTransaction(Integer txId) {
        if (txId == null)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Transaction ID không hợp lệ!");
        if (!transDAO.rejectTransaction(txId))
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Từ chối giao dịch thất bại!");
    }

    public List<TransactionRequest> getTransactionsByUser(int userId) {
        return transDAO.getTransactionsByUserId(userId);
    }

    public double getUserEscrowAmount(int userId) {
        try { return transDAO.getUserEscrowAmount(userId); } catch (Exception e) { return 0.0; }
    }
}
