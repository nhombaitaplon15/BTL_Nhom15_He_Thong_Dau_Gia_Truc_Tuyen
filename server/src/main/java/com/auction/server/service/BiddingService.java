package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BiddingService — ĐÃ SỬA TOÀN DIỆN:
 *
 * FIX QUAN TRỌNG — DB không cập nhật khi đặt giá:
 *
 * ❌ LỖI CŨ: placeBid() đọc Auction từ DB (lần 1) để validate, nhưng khi vào
 *    executeBidTransaction(), bước hoàn tiền người cũ dùng auction.getCurrentWinnerId()
 *    và auction.getCurrentPrice() từ object đó — object này đã STALE vì được đọc
 *    TRƯỚC KHI lock. Nếu người khác đặt giá chen vào giữa lúc đọc và lúc lock,
 *    winnerId và currentPrice đều sai → hoàn tiền sai người, sai số tiền.
 *
 * ❌ LỖI CŨ 2: AuctionRoom.processBid() gọi getAuctionOrThrow() lấy object auction
 *    rồi bỏ đi, sau đó placeBid() lại gọi getAuctionOrThrow() lần nữa bên trong
 *    → double fetch DB không nhất quán.
 *
 * ✅ FIX: executeBidTransaction() đọc lại current_winner_id và current_price
 *    TRỰC TIẾP TỪ DB trong cùng transaction với FOR UPDATE lock — đảm bảo
 *    giá trị luôn là mới nhất tại thời điểm transaction chạy, không bị race condition.
 *
 * ✅ FIX: Thêm placeBidWithAuction(user, auction, bidAmount) để AuctionRoom
 *    truyền object Auction vào thay vì để placeBid() fetch lại lần 2 — tránh double fetch.
 *    Validate vẫn dùng object auction từ tham số, còn executeBidTransaction() tự
 *    đọc lại winner/price từ DB trong transaction nên không bị stale.
 */
public class BiddingService {

    private final ManagerService    managerService;
    private final AuctionDAO        auctionDAO        = new AuctionDAO();
    private final TransactionDAO    transactionDAO    = new TransactionDAO();
    private final PaymentDAO        paymentDAO        = new PaymentDAO();
    private final BiddingHistoryDAO biddingHistoryDAO = new BiddingHistoryDAO();
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private static final int[] ADMIN_IDS = {1, 2, 3, 4};

    public BiddingService(ManagerService managerService) { this.managerService = managerService; }
    public BiddingService() { this.managerService = null; }
    public ManagerService getManagerService() { return managerService; }

    // ─── PLACE BID ───────────────────────────────────────────────────────────

    /**
     * Được gọi từ AuctionRoom.processBid() — truyền auction vào thay vì fetch lại lần 2.
     * Validate dùng auction từ tham số, còn DB transaction tự đọc lại winner/price.
     */
    public void placeBidWithAuction(User user, Auction auction, double bidAmount) {
        validateBidRules(user, auction, bidAmount);

        ReentrantLock lock = lockMap.computeIfAbsent(auction.getAuctionId(), k -> new ReentrantLock());
        lock.lock();
        try {
            // Kiểm tra lại giá sau khi có lock (tránh race condition giữa validate và execute)
            if (bidAmount <= auction.getCurrentPrice())
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đã thay đổi, vui lòng đặt cao hơn!");
            executeBidTransaction(user, auction, bidAmount);
            System.out.println("[BID] " + user.getUsername() + " đặt " + bidAmount + " thành công!");
        } finally {
            lock.unlock();
        }
    }

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
                // BƯỚC 1 (FIX): Đọc lại current_winner_id và current_price TRỰC TIẾP TỪ DB
                // trong cùng transaction với FOR UPDATE — tránh dùng object Auction có thể stale.
                // Nếu ai đó đặt giá chen vào giữa lúc validate và lúc lock,
                // ta vẫn hoàn tiền đúng người và đúng số tiền.
                Integer actualWinnerId = null;
                double  actualCurrentPrice = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT current_winner_id, current_price FROM public.auctions WHERE auction_id = ? FOR UPDATE")) {
                    ps.setInt(1, auction.getAuctionId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int wid = rs.getInt("current_winner_id");
                            actualWinnerId    = rs.wasNull() ? null : wid;
                            actualCurrentPrice = rs.getDouble("current_price");
                        }
                    }
                }

                // Kiểm tra lại giá từ DB — tránh race condition khi nhiều client cùng đặt
                if (amount <= actualCurrentPrice)
                    throw new SQLException("Giá đã bị người khác đặt cao hơn, vui lòng thử lại!");

                // BƯỚC 2: Hoàn tiền cho người dẫn đầu cũ (dùng dữ liệu đọc từ DB, không từ object)
                if (actualWinnerId != null && actualWinnerId > 0) {
                    if (!paymentDAO.updateBalance(conn, actualWinnerId, actualCurrentPrice, "+"))
                        throw new SQLException("Lỗi hoàn tiền cho người dẫn đầu cũ (ID=" + actualWinnerId + ")");
                }

                // BƯỚC 3: Kiểm tra và trừ ví người đặt mới
                if (!paymentDAO.updateBalance(conn, user.getId(), amount, "-"))
                    throw new SQLException("Số dư không đủ hoặc lỗi trừ ví người đặt!");

                // BƯỚC 4: Nạp vào escrow admin
                int assignedAdminId = ADMIN_IDS[auction.getAuctionId() % ADMIN_IDS.length];
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE public.users SET escrow_balance = escrow_balance + ? WHERE user_id = ?")) {
                    ps.setDouble(1, amount);
                    ps.setInt(2, assignedAdminId);
                    if (ps.executeUpdate() <= 0)
                        throw new SQLException("Không thể nạp cọc vào ví tạm Admin#" + assignedAdminId);
                }

                // BƯỚC 5: Ghi transaction lịch sử nạp cọc
                transactionDAO.createTransaction(conn, user.getId(), amount,
                        "BID_PLACED_" + auction.getAuctionId(), "SUCCESS");

                // BƯỚC 6: Cập nhật current_price, current_winner_id, total_bids vào DB
                // Dùng điều kiện current_price = actualCurrentPrice để đảm bảo nguyên tử
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE public.auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 " +
                                "WHERE auction_id = ? AND current_price = ?")) {
                    ps.setDouble(1, amount);
                    ps.setInt(2, user.getId());
                    ps.setInt(3, auction.getAuctionId());
                    ps.setDouble(4, actualCurrentPrice);
                    if (ps.executeUpdate() == 0)
                        throw new SQLException("Cập nhật giá thất bại — giá đã thay đổi đồng thời!");
                }

                // BƯỚC 7: Anti-sniping
                LocalDateTime newEndTime = calculateAntiSniping(auction.getEndTime());
                if (newEndTime != null) {
                    auctionDAO.updateEndTime(conn, auction.getAuctionId(), newEndTime);
                    auction.setEndTime(newEndTime);
                }

                // BƯỚC 8: Ghi lịch sử đặt giá (bidding_history)
                String itemName = "Vật phẩm #" + auction.getItemId();
                try {
                    com.auction.common.model.Item item = new ItemDAO().getItemById(auction.getItemId());
                    if (item != null) itemName = item.getName();
                } catch (Exception ignored) {}

                biddingHistoryDAO.saveBidRecordWithConnection(conn, auction.getAuctionId(),
                        itemName, user.getId(), user.getUsername(), amount);

                // COMMIT — tất cả hoặc không gì cả
                conn.commit();

                // Cập nhật object Auction local CHỈ SAU KHI commit thành công
                // để AuctionRoom.processBid() broadcast giá đúng ngay lập tức.
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

    // ─── REJECT WIN ──────────────────────────────────────────────────────────

    public void rejectWin(User winner, int auctionId) {
        if (managerService == null)
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Chức năng yêu cầu ManagerService!");

        Auction auction = managerService.getAuctionOrThrow(auctionId);

        if (auction.getCurrentWinnerId() == null || auction.getCurrentWinnerId() != winner.getId())
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải người thắng phiên #" + auctionId + "!");

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
                boolean ok = paymentDAO.processPenalty7Percent(conn, winner.getId(), adminId, bidAmount);
                if (!ok)
                    throw new SQLException("processPenalty7Percent thất bại — escrow Admin#" + adminId + " không đủ?");

                if (!auctionDAO.updateStatus(conn, auctionId, "REJECTED"))
                    throw new SQLException("Cập nhật status → REJECTED thất bại!");

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