package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BiddingService {

    private final ManagerService managerService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public BiddingService(ManagerService managerService) {
        this.managerService = managerService;
    }

    public void placeBid(User user, int auctionId, double bidAmount) {
        // 1. Kiểm tra nhanh (Fast-fail) trước khi lấy Lock
        Auction auction = managerService.getAuctionOrThrow(auctionId);
        validateBidRules(user, auction, bidAmount);

        // 2. Lock theo từng phiên để tránh tranh chấp (Race Condition)
        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            // Kiểm tra lại lần nữa trong Lock để đảm bảo giá chưa bị ai khác đẩy lên
            if (bidAmount <= auction.getCurrentPrice()) {
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đã bị thay đổi, vui lòng đặt cao hơn!");
            }

            // 3. Thực hiện Transaction xuống Database
            executeBidTransaction(user, auction, bidAmount);

            System.out.println("[BID] " + user.getUsername() + " đặt giá " + bidAmount + " thành công!");
        } finally {
            lock.unlock();
        }
    }

    private void executeBidTransaction(User user, Auction auction, double amount) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // Bắt đầu Transaction
            try {
                // Cập nhật giá và Winner
                if (!auctionDAO.updateBid(conn, auction.getAuctionId(), user.getId(), amount)) {
                    throw new SQLException("Update bid failed");
                }

                // Xử lý Anti-sniping: Gia hạn 30s nếu đặt giá vào phút chót
                LocalDateTime newEndTime = calculateAntiSniping(auction.getEndTime());
                if (newEndTime != null) {
                    auctionDAO.updateEndTime(conn, auction.getAuctionId(), newEndTime);
                    auction.setEndTime(newEndTime); // Cập nhật Object RAM
                }

                conn.commit(); // Thành công hết thì chốt

                // Cập nhật Object RAM sau khi DB đã OK
                auction.setCurrentPrice(amount);
                auction.setCurrentWinnerId(user.getId());
                auction.setTotalBids(auction.getTotalBids() + 1);

            } catch (SQLException e) {
                conn.rollback(); // Lỗi bất cứ bước nào là hủy hết
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Giao dịch thất bại: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối Database!");
        }
    }

    private void validateBidRules(User user, Auction auction, double amount) {
        if (user.isAdmin()) throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không được đấu giá!");
        if (user.getId() == auction.getSellerId()) throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Không được tự đấu giá!");
        if (!"RUNNING".equals(auction.getAuctionStatus())) throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên không trong trạng thái RUNNING!");
        if (LocalDateTime.now().isAfter(auction.getEndTime())) throw new AuctionException(ErrorCode.AUCTION_ALREADY_ENDED.name(), "Phiên đã kết thúc!");
    }

    private LocalDateTime calculateAntiSniping(LocalDateTime currentEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(currentEnd.minusSeconds(60)) && now.isBefore(currentEnd)) {
            return currentEnd.plusSeconds(30);
        }
        return null;
    }
}