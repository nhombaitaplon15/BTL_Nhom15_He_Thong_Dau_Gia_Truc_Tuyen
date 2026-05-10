package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.UserDAO; // Thêm UserDAO để xử lý tiền
import java.time.LocalDateTime;

public class AuctionService {
    private AuctionDAO auctionDAO = new AuctionDAO();
    private UserDAO userDAO = new UserDAO(); // Gánh tải phần tiền từ UserService sang đây

    public void handlePlaceBid(User currentUser, Auction auction, double bidAmount) {

        // --- 1. CÁC CHỐT CHẶN (GUARDS) ---

        if (currentUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không được tham gia đấu giá!");
        }

        if (currentUser.getId() == auction.getSellerId()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không thể đấu giá sản phẩm của chính mình!");
        }

        // Kiểm tra thời gian thực tế (Đề phòng RAM chưa cập nhật status)
        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
            throw new AuctionException(ErrorCode.AUCTION_ALREADY_ENDED.name(), "Phiên đấu giá đã kết thúc!");
        }

        if (!"OPEN".equalsIgnoreCase(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên đấu giá hiện không mở!");
        }

        if (bidAmount <= auction.getCurrentPrice()) {
            throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đặt phải cao hơn giá hiện tại!");
        }

        if (currentUser.getBalance() < bidAmount) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư trong ví không đủ!");
        }

        // --- 2. THỰC THI GIAO DỊCH (SQL TRANSACTION) ---

        // Bước A: Cập nhật giá và người thắng vào bảng auctions
        if (auctionDAO.updateBid(auction.getAuctionId(), currentUser.getId(), bidAmount)) {

            // Bước B: Logic hoàn tiền (Refund) cho người thắng cũ (nếu có)
            // Đây là chỗ giúp UserService bớt tải - xử lý dòng tiền đấu giá tập trung tại đây
            if (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId() != 0) {
                userDAO.updateBalance(auction.getCurrentWinnerId(), auction.getCurrentPrice(), "+");
            }

            // Bước C: Trừ tiền người mới (Tạm giữ/Freeze)
            userDAO.updateBalance(currentUser.getId(), bidAmount, "-");

            // --- 3. CẬP NHẬT RAM & LOGIC PHỤ ---

            // Cập nhật RAM cho User hiện tại để UI thay đổi số dư ngay
            currentUser.setBalance(currentUser.getBalance() - bidAmount);

            // Cập nhật RAM cho Auction
            auction.setCurrentPrice(bidAmount);
            auction.setCurrentWinnerId(currentUser.getId());
            auction.setTotalBids(auction.getTotalBids() + 1);

            // Logic Anti-sniping: Nếu đặt giá vào 1 phút cuối, gia hạn thêm 30s
            handleAntiSniping(auction);

            System.out.println(">>> Đặt giá thành công: " + currentUser.getUsername() + " bid " + bidAmount);
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Đặt giá thất bại do có người vừa nhanh tay hơn!");
        }
    }

    /**
     * Logic tự động gia hạn phiên khi có biến động sát giờ chót
     */
    private void handleAntiSniping(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(auction.getEndTime().minusMinutes(1))) {
            LocalDateTime newEndTime = auction.getEndTime().plusSeconds(30);
            if (auctionDAO.updateEndTime(auction.getAuctionId(), newEndTime)) {
                auction.setEndTime(newEndTime);
                System.out.println("=== ANTI-SNIPING: Gia hạn phiên thêm 30s ===");
            }
        }
    }
}