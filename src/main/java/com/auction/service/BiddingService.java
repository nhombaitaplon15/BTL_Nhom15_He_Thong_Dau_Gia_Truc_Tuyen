package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.BiddingHistory;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BiddingService {

    private final ManagerService managerService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private final com.auction.server.dao.BiddingHistoryDAO biddingHistoryDAO = new com.auction.server.dao.BiddingHistoryDAO();

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

    // Thực hiện đấu giá và lưu lịch sử cùng một cụm giao dịch (Transaction)

    private void executeBidTransaction(User user, Auction auction, double amount) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // Bắt đầu Transaction
            try {
                // 1. Hoàn tiền cho người đang giữ giá cao nhất cũ (nếu có)
                if (auction.getCurrentWinnerId() != null) {
                    int oldWinnerId = auction.getCurrentWinnerId();
                    double oldPrice = auction.getCurrentPrice();
                    // hoàn lại tiền vào tài khoản người cũ
                    if (!paymentDAO.updateBalance(conn, oldWinnerId, oldPrice, "+")) {
                        throw new SQLException("Lỗi hoàn tiền cho người giữ giá cũ (ID: " + oldWinnerId + ")");
                    }
                    // ghi lịch sử ví hoàn tiền
                    transactionDAO.createTransaction(conn, oldWinnerId, oldPrice, "REFUND_OVERBID_" + auction.getAuctionId(), "SUCCESS");
                }

                // 2. Trừ tiền người đặt giá mới (Giam tiền tạm giữ)
                if (!paymentDAO.updateBalance(conn, user.getId(), amount, "-")) {
                    throw new SQLException("Số dư tài khoản không đủ để thực hiện đặt giá!");
                }
                // ghi lịch sử ví trừ tiền đặt giá
                transactionDAO.createTransaction(conn, user.getId(), amount, "BID_PLACED_" + auction.getAuctionId(), "SUCCESS");

                // 3. Cập nhật giá và Winner mới vào bảng đấu giá
                if (!auctionDAO.updateBid(conn, auction.getAuctionId(), user.getId(), amount)) {
                    throw new SQLException("Cập nhật giá mới không thành công!");
                }

                // Xử lý Anti-sniping: Gia hạn 30s nếu đặt giá vào phút chót
                LocalDateTime newEndTime = calculateAntiSniping(auction.getEndTime());
                if (newEndTime != null) {
                    auctionDAO.updateEndTime(conn, auction.getAuctionId(), newEndTime);
                    auction.setEndTime(newEndTime); // Cập nhật Object RAM
                }

                // ====================================================================
                // === CẬP NHẬT: GHI LỊCH SỬ ĐẤU GIÁ (LẤY TÊN VẬT PHẨM ĐỘNG) ===
                // Thiết lập tên mặc định phòng trường hợp xảy ra lỗi đọc dữ liệu phụ
                String itemName = "Vật phẩm đấu giá #" + auction.getItemId();

                if (this.managerService != null) {
                    try {
                        // Lấy thông tin phiên đấu giá hiện hành để kiểm tra dữ liệu Item đính kèm
                        Auction currentAuction = this.managerService.getAuction(auction.getAuctionId());
                        if (currentAuction != null) {
                            // MẸO CỦA NHÓM: Nếu class 'Auction' của bạn có ôm một object Item hoặc hàm lấy tên,
                            // bạn hãy mở ghi chú dòng bên dưới ra và sửa lại cho khớp nhé:
                            // itemName = currentAuction.getItemName();
                            // hoặc: itemName = currentAuction.getItem().getName();
                        }
                    } catch (Exception e) {
                        System.out.println("[WARN] Không bóc tách được tên vật phẩm chi tiết, dùng tên ID mặc định.");
                    }
                }

                // Gọi DAO ghi nhận bản ghi mới (Dùng chung conn của Transaction hiện tại)
                biddingHistoryDAO.saveBidRecordWithConnection(
                        conn,
                        auction.getAuctionId(),
                        itemName,
                        user.getId(),
                        user.getUsername(),
                        amount
                );
                // ====================================================================

                conn.commit(); // Thành công hết sạch thì chốt luồng tiền, thông tin và lịch sử cùng lúc!

                // Cập nhật Object RAM sau khi DB đã OK để đồng bộ hiển thị dữ liệu
                auction.setCurrentPrice(amount);
                auction.setCurrentWinnerId(user.getId());
                auction.setTotalBids(auction.getTotalBids() + 1);

            } catch (SQLException e) {
                conn.rollback(); // Lỗi bất cứ bước nào (Kể cả lưu lịch sử thất bại) là hủy toàn bộ luồng tiền!
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Giao dịch thất bại: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối Database!");
        }
    }

    public void rejectWin(User winner, int auctionId) {
        Auction auction = managerService.getAuctionOrThrow(auctionId);

        // Kiểm tra tính hợp lệ
        if (winner.getId() != auction.getCurrentWinnerId()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải người thắng phiên này!");
        }
        if (!"FINISHED".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên đấu giá chưa kết thúc!");
        }

        double bidAmount = auction.getCurrentPrice();
        double penaltyAmount = bidAmount * 0.07;
        double refundAmount = bidAmount - penaltyAmount;
        int adminId = 1; // ID tài khoản doanh thu hệ thống

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Trả lại 93% cho người mua
                paymentDAO.updateBalance(conn, winner.getId(), refundAmount, "+");
                transactionDAO.createTransaction(conn, winner.getId(), refundAmount, "REFUND_REJECT_ITEM_" + auctionId, "SUCCESS");

                // 2. Nộp 7% vào doanh thu sàn (Admin)
                paymentDAO.updateBalance(conn, adminId, penaltyAmount, "+");
                transactionDAO.createTransaction(conn, adminId, penaltyAmount, "PENALTY_REVENUE_AUCTION_" + auctionId, "SUCCESS");

                // 3. Đổi trạng thái phiên đấu giá trong DB thành REJECTED
                auctionDAO.updateStatus(conn, auctionId, "REJECTED");

                conn.commit();
                // 4. Cập nhật RAM
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
    }

    private LocalDateTime calculateAntiSniping(LocalDateTime currentEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(currentEnd.minusSeconds(60)) && now.isBefore(currentEnd)) {
            return currentEnd.plusSeconds(30);
        }
        return null;
    }

    public List<BiddingHistory> getBiddingHistory(int bidderId) {
        return biddingHistoryDAO.getHistoryByBidderId(bidderId);
    }
}