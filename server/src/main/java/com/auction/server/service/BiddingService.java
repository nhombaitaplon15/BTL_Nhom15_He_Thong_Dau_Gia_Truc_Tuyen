package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BiddingService — ĐÃ SỬA TOÀN DIỆN:
 *
 * ❌ LỖI CŨ 1 (rejectWin): Gọi paymentDAO.updateBalance(adminId, penalty, "+") thay vì
 *    processPenalty7Percent() → escrow_balance admin không bao giờ được trừ → tiền bị kẹt.
 *
 * ❌ LỖI CŨ 2 (rejectWin): Kiểm tra status == "FINISHED" nhưng AutoBot đặt "SOLD"
 *    khi phiên kết thúc có winner → winner KHÔNG BAO GIỜ hủy được (AuctionException mọi lần).
 *
 * ❌ LỖI CŨ 3: auction.setAuctionStatus() là sync RAM vô nghĩa vì object này bị GC ngay,
 *    không ảnh hưởng gì đến DB hay các request sau.
 *
 * ✅ SỬA 1: rejectWin() chấp nhận cả "SOLD" và "FINISHED" (align với AutoBot).
 * ✅ SỬA 2: Gọi paymentDAO.processPenalty7Percent() đúng chuẩn:
 *    - escrow_balance[admin] -= bidAmount   (giải phóng cọc)
 *    - system_revenue[admin] += 7%          (phí phạt)
 *    - balance[winner]       += 93%         (hoàn tiền)
 * ✅ SỬA 3: Xóa bỏ sync RAM (auction.setAuctionStatus, liveUser.setBalance) vì DB = truth.
 * ✅ SỬA 4: Ghi đủ 2 bản ghi lịch sử: PENALTY + REFUND.
 */
public class BiddingService {

    private final ManagerService    managerService;
    private final AuctionDAO        auctionDAO        = new AuctionDAO();
    private final TransactionDAO    transactionDAO    = new TransactionDAO();
    private final PaymentDAO        paymentDAO        = new PaymentDAO();
    private final BiddingHistoryDAO biddingHistoryDAO = new BiddingHistoryDAO();
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    // Phải khớp với PaymentDAO và TransactionService
    private static final int[] ADMIN_IDS = {1, 2, 3, 4};

    public BiddingService(ManagerService managerService) { this.managerService = managerService; }
    public BiddingService() { this.managerService = null; }
    public ManagerService getManagerService() { return managerService; }

    // ─── PLACE BID ───────────────────────────────────────────────────────────

    public void placeBid(User user, int auctionId, double bidAmount) {
        if (managerService == null)
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "ManagerService chưa khởi tạo!");

        Auction auction = managerService.getAuctionOrThrow(auctionId);
        validateBidRules(user, auction, bidAmount);

        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            if (bidAmount <= auction.getCurrentPrice())
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đã thay đổi, vui lòng đặt cao hơn!");
            executeBidTransaction(user, auction, bidAmount);
            System.out.println("[BID] " + user.getUsername() + " đặt " + bidAmount + " thành công!");
        } finally {
            lock.unlock();
        }
    }

    public void placeBidDirectFromDB(User user, int auctionId, double bidAmount) {
        Auction freshAuction = auctionDAO.getAuctionById(auctionId);
        if (freshAuction == null)
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không tìm thấy phiên #" + auctionId);
        validateBidRules(user, freshAuction, bidAmount);

        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            if (bidAmount <= freshAuction.getCurrentPrice())
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đặt phải cao hơn giá hiện tại!");
            executeBidTransaction(user, freshAuction, bidAmount);
            System.out.println("[BID DIRECT] " + user.getUsername() + " đặt " + bidAmount + " thành công!");
        } finally {
            lock.unlock();
        }
    }

    private void executeBidTransaction(User user, Auction auction, double amount) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // BƯỚC 1: Hoàn tiền cho người dẫn đầu cũ (nếu có)
                if (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId() > 0) {
                    if (!paymentDAO.updateBalance(conn, auction.getCurrentWinnerId(), auction.getCurrentPrice(), "+"))
                        throw new SQLException("Lỗi hoàn tiền cho người dẫn đầu cũ (ID=" + auction.getCurrentWinnerId() + ")");
                }

                // BƯỚC 2: Kiểm tra số dư người đặt mới
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT balance FROM users WHERE user_id = ? AND balance >= ?")) {
                    ps.setInt(1, user.getId());
                    ps.setDouble(2, amount);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Số dư không đủ để đặt giá!");
                    }
                }

                // BƯỚC 3: Trừ ví người đặt mới, nạp vào escrow admin
                if (!paymentDAO.updateBalance(conn, user.getId(), amount, "-"))
                    throw new SQLException("Lỗi trừ ví người đặt!");

                int assignedAdminId = ADMIN_IDS[auction.getAuctionId() % ADMIN_IDS.length];
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET escrow_balance = escrow_balance + ? WHERE user_id = ?")) {
                    ps.setDouble(1, amount);
                    ps.setInt(2, assignedAdminId);
                    if (ps.executeUpdate() <= 0)
                        throw new SQLException("Không thể nạp cọc vào ví tạm Admin#" + assignedAdminId);
                }

                transactionDAO.createTransaction(conn, user.getId(), amount,
                        "BID_PLACED_" + auction.getAuctionId(), "SUCCESS");

                // BƯỚC 4: Cập nhật giá và winner vào DB
                if (!auctionDAO.updateBid(conn, auction.getAuctionId(), user.getId(), amount))
                    throw new SQLException("Cập nhật giá mới vào phiên thất bại!");

                // BƯỚC 5: Anti-sniping
                LocalDateTime newEndTime = calculateAntiSniping(auction.getEndTime());
                if (newEndTime != null) {
                    auctionDAO.updateEndTime(conn, auction.getAuctionId(), newEndTime);
                    auction.setEndTime(newEndTime); // Cập nhật object local để AuctionRoom broadcast đúng
                }

                // BƯỚC 6: Lịch sử đặt giá
                String itemName = "Vật phẩm #" + auction.getItemId();
                try {
                    com.auction.common.model.Item item = new ItemDAO().getItemById(auction.getItemId());
                    if (item != null) itemName = item.getName();
                } catch (Exception ignored) {}

                biddingHistoryDAO.saveBidRecordWithConnection(conn, auction.getAuctionId(),
                        itemName, user.getId(), user.getUsername(), amount);

                conn.commit();

                // Cập nhật object Auction local — chỉ dùng cho AuctionRoom.processBid()
                // để broadcast price đúng ra các viewer trong phòng ngay lập tức.
                // AutoBot và GET_AUCTION_DETAIL luôn đọc lại từ DB.
                auction.setCurrentPrice(amount);
                auction.setCurrentWinnerId(user.getId());
                auction.setTotalBids(auction.getTotalBids() + 1);

            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Giao dịch thất bại: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối DB!");
        }
    }

    // ─── REJECT WIN (HỦY KÈEO) ───────────────────────────────────────────────

    /**
     * ✅ ĐÃ SỬA HOÀN TOÀN: Hủy kèo — phạt cọc 7%, hoàn 93%.
     *
     * Dòng tiền (1 transaction DB):
     *   escrow_balance[admin] -= bidAmount   (giải phóng toàn bộ cọc)
     *   system_revenue[admin] += 7%          (phí phạt vào doanh thu)
     *   balance[winner]       += 93%         (hoàn tiền về ví)
     *   auction_status        → "REJECTED"
     *   + 2 bản ghi lịch sử: PENALTY + REFUND
     *
     * Không sync RAM vì hệ thống dùng DB làm source of truth.
     *
     * FIX STATUS: Chấp nhận cả "SOLD" lẫn "FINISHED"
     *   AutoBot đặt "SOLD" khi phiên kết thúc có winner.
     *   Code cũ check "FINISHED" → winner KHÔNG BAO GIỜ hủy được.
     */
    public void rejectWin(User winner, int auctionId) {
        if (managerService == null)
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Chức năng yêu cầu ManagerService!");

        Auction auction = managerService.getAuctionOrThrow(auctionId);

        if (auction.getCurrentWinnerId() == null || auction.getCurrentWinnerId() != winner.getId())
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải người thắng phiên #" + auctionId + "!");

        // ✅ FIX: Chấp nhận "SOLD" (autobot) VÀ "FINISHED" (edge case)
        String status = auction.getAuctionStatus();
        if (!"SOLD".equalsIgnoreCase(status) && !"FINISHED".equalsIgnoreCase(status))
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Phiên chưa kết thúc (trạng thái: " + status + "). Chỉ có thể hủy phiên đã kết thúc.");

        double bidAmount    = auction.getCurrentPrice();
        double penaltyFee   = bidAmount * 0.07;
        double refundAmount = bidAmount * 0.93;
        int    adminId      = ADMIN_IDS[auctionId % ADMIN_IDS.length];

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // ✅ processPenalty7Percent: trừ escrow_balance, +7% system_revenue, +93% balance winner
                boolean ok = paymentDAO.processPenalty7Percent(conn, winner.getId(), adminId, bidAmount);
                if (!ok)
                    throw new SQLException("processPenalty7Percent thất bại — escrow Admin#" + adminId + " không đủ?");

                // Cập nhật trạng thái phiên
                if (!auctionDAO.updateStatus(conn, auctionId, "REJECTED"))
                    throw new SQLException("Cập nhật status → REJECTED thất bại!");

                // Ghi lịch sử
                transactionDAO.createTransaction(conn, adminId, penaltyFee,
                        "PENALTY_REVENUE_AUCTION_" + auctionId, "SUCCESS");
                transactionDAO.createTransaction(conn, winner.getId(), refundAmount,
                        "REFUND_REJECT_ITEM_" + auctionId, "SUCCESS");

                conn.commit();
                System.out.printf(">>> [REJECT] Phiên #%d: Admin#%d phạt 7%%(%.0f), Winner#%d hoàn 93%%(%.0f)%n",
                        auctionId, adminId, penaltyFee, winner.getId(), refundAmount);

            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hủy kèo: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối DB!");
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private void validateBidRules(User user, Auction auction, double amount) {
        if (user.isAdmin())
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không được đấu giá!");
        if (user.getId() == auction.getSellerId())
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Không được tự đấu giá sản phẩm của mình!");
        if (!"RUNNING".equalsIgnoreCase(auction.getAuctionStatus()))
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên không trong trạng thái RUNNING!");
        if (LocalDateTime.now().isAfter(auction.getEndTime()))
            throw new AuctionException(ErrorCode.AUCTION_ALREADY_ENDED.name(), "Phiên đã kết thúc!");
    }

    private LocalDateTime calculateAntiSniping(LocalDateTime currentEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(currentEnd.minusSeconds(60)) && now.isBefore(currentEnd))
            return currentEnd.plusSeconds(30);
        return null;
    }

    public List<BidHistoryRow> getBiddingHistory(int bidderId) {
        return biddingHistoryDAO.getHistoryByUser(bidderId);
    }

    public List<BiddingHistory> getAuctionBids(int auctionId) {
        return auctionDAO.getBiddingHistoryByAuctionId(auctionId);
    }
}
