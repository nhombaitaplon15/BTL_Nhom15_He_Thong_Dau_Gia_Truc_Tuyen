package com.auction.server.service;  // [SỬA DÒNG 1] server.service -> com.auction.server.service

import com.auction.common.model.Auction;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException ;  // [SỬA] com.auction.exception -> com.auction.common.exception
import com.auction.common.exception.ErrorCode ;         // [SỬA] com.auction.exception -> com.auction.common.exception
import com.auction.server.dao.AuctionDAO ;
import com.auction.server.dao.DatabaseConnection ;
import com.auction.server.dao.TransactionDAO ;
import com.auction.server.dao.PaymentDAO ;
import com.auction.server.dao.BiddingHistoryDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BiddingService - Xử lý toàn bộ logic nghiệp vụ đặt giá và hủy kèo.
 *
 * ĐÃ SỬA:
 * 1. Dòng 1: package server.service -> com.auction.server.service
 * 2. Import exception: com.auction.exception -> com.auction.common.exception
 * 3. Thêm getManagerService() để AuctionRoom.processBid() có thể inject
 *
 * Đặt tại: server/src/main/java/com/auction/server/service/BiddingService.java
 */
public class BiddingService {

    private final ManagerService managerService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private final BiddingHistoryDAO biddingHistoryDAO = new BiddingHistoryDAO();

    public BiddingService(ManagerService managerService) {
        this.managerService = managerService;
    }

    /** [ĐÃ THÊM] Accessor để AuctionRoom.processBid() truy cập ManagerService */
    public ManagerService getManagerService() {
        return managerService;
    }

    public void placeBid(User user, int auctionId, double bidAmount) {
        Auction auction = managerService.getAuctionOrThrow(auctionId);
        validateBidRules(user, auction, bidAmount);

        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            if (bidAmount <= auction.getCurrentPrice()) {
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đã bị thay đổi, vui lòng đặt cao hơn!");
            }
            executeBidTransaction(user, auction, bidAmount);
            System.out.println("[BID] " + user.getUsername() + " đặt giá " + bidAmount + " thành công!");
        } finally {
            lock.unlock();
        }
    }

    private void executeBidTransaction(User user, Auction auction, double amount) {
        try (Connection conn = DatabaseConnection.connect()) {
            conn.setAutoCommit(false);
            try {
                if (auction.getCurrentWinnerId() != null) {
                    int oldWinnerId = auction.getCurrentWinnerId();
                    double oldPrice = auction.getCurrentPrice();
                    if (!paymentDAO.updateBalance(conn, oldWinnerId, oldPrice, "+")) {
                        throw new SQLException("Lỗi hoàn tiền cho người giữ giá cũ (ID: " + oldWinnerId + ")");
                    }
                    transactionDAO.createTransaction(conn, oldWinnerId, oldPrice,
                            "REFUND_OVERBID_" + auction.getAuctionId(), "SUCCESS");
                }
                if (!paymentDAO.updateBalance(conn, user.getId(), amount, "-")) {
                    throw new SQLException("Số dư tài khoản không đủ để thực hiện đặt giá!");
                }
                transactionDAO.createTransaction(conn, user.getId(), amount,
                        "BID_PLACED_" + auction.getAuctionId(), "SUCCESS");
                if (!auctionDAO.updateBid(conn, auction.getAuctionId(), user.getId(), amount)) {
                    throw new SQLException("Cập nhật giá mới không thành công!");
                }
                LocalDateTime newEndTime = calculateAntiSniping(auction.getEndTime());
                if (newEndTime != null) {
                    auctionDAO.updateEndTime(conn, auction.getAuctionId(), newEndTime);
                    auction.setEndTime(newEndTime);
                }
                conn.commit();
                auction.setCurrentPrice(amount);
                auction.setCurrentWinnerId(user.getId());
                auction.setTotalBids(auction.getTotalBids() + 1);
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Giao dịch thất bại: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối Database!");
        }
    }

    public void rejectWin(User winner, int auctionId) {
        Auction auction = managerService.getAuctionOrThrow(auctionId);

        if (winner.getId() != auction.getCurrentWinnerId()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải người thắng phiên này!");
        }
        if (!"FINISHED".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên đấu giá chưa kết thúc!");
        }

        double bidAmount = auction.getCurrentPrice();
        double penaltyAmount = bidAmount * 0.07;
        double refundAmount = bidAmount - penaltyAmount;
        int adminId = 1;

        try (Connection conn = DatabaseConnection.connect()) {
            conn.setAutoCommit(false);
            try {
                paymentDAO.updateBalance(conn, winner.getId(), refundAmount, "+");
                transactionDAO.createTransaction(conn, winner.getId(), refundAmount,
                        "REFUND_REJECT_ITEM_" + auctionId, "SUCCESS");
                paymentDAO.updateBalance(conn, adminId, penaltyAmount, "+");
                transactionDAO.createTransaction(conn, adminId, penaltyAmount,
                        "PENALTY_REVENUE_AUCTION_" + auctionId, "SUCCESS");
                auctionDAO.updateStatus(conn, auctionId, "REJECTED");
                conn.commit();
                auction.setAuctionStatus("REJECTED");
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi dòng tiền khi hủy kèo: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    private void validateBidRules(User user, Auction auction, double amount) {
        if (user.isAdmin()) throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không được đấu giá!");
        if (user.getId() == auction.getSellerId()) throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Không được tự đấu giá!");
        if (!"RUNNING".equals(auction.getAuctionStatus())) throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên không trong trạng thái RUNNING!");
        if (LocalDateTime.now().isAfter(auction.getEndTime())) throw new AuctionException(ErrorCode.AUCTION_ALREADY_ENDED.name(), "Phiên đã kết thúc!");
        if (user.getBalance() < amount) throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư không đủ!");
    }

    private LocalDateTime calculateAntiSniping(LocalDateTime currentEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(currentEnd.minusSeconds(60)) && now.isBefore(currentEnd)) {
            return currentEnd.plusSeconds(30);
        }
        return null;
    }
    public List<BidHistoryRow> getBidHistory(int userId) {
        if (userId <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "User ID không hợp lệ!");
        }
        return biddingHistoryDAO.getHistoryByUser(userId);
    }
}
