package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BiddingService {

    private final ManagerService managerService;
    private final AuctionDAO auctionDAO = new AuctionDAO(); // Thêm DAO để lưu SQL

    // Khóa cho từng phiên để tránh race condition (nhiều người cùng đặt giá 1 lúc)
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public BiddingService(ManagerService managerService) {
        this.managerService = managerService;
    }

    /**
     * Xử lý đặt giá: Tích hợp logic chặn và lưu SQL
     */
    public boolean placeBid(User currentUser, int auctionId, double newPrice) {
        Auction auction = managerService.getAuction(auctionId);

        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Auction không tồn tại");
        }

        // Lấy khóa cho phiên này
        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());

        lock.lock();
        try {
            // 1. Logic chặn (Mới bổ sung theo yêu cầu)
            if (currentUser.isAdmin()) {
                throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không được tham gia!");
            }
            if (currentUser.getId() == auction.getSellerId()) {
                throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Không được tự đấu giá sản phẩm của mình!");
            }

            // 2. Kiểm tra trạng thái và thời gian (Khớp với OPEN trong DB của bạn)
            if (!"OPEN".equals(auction.getAuctionStatus())) {
                throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên không mở!");
            }
            if (LocalDateTime.now().isAfter(auction.getEndTime())) {
                throw new AuctionException(ErrorCode.AUCTION_ALREADY_ENDED.name(), "Phiên đã kết thúc!");
            }

            // 3. Kiểm tra giá
            if (newPrice <= auction.getCurrentPrice()) {
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đặt phải cao hơn giá hiện tại!");
            }

            // 4. Lưu vào SQL trước (Rất quan trọng)
            if (auctionDAO.updateBid(auctionId, currentUser.getId(), newPrice)) {

                // 5. Nếu SQL ok thì mới cập nhật RAM
                auction.setCurrentPrice(newPrice);
                auction.setCurrentWinnerId(currentUser.getId());
                auction.setTotalBids(auction.getTotalBids() + 1);

                System.out.println(currentUser.getUsername() + " đang dẫn đầu với giá " + newPrice);

                // Mở rộng thời gian nếu cần (Anti-sniping)
                extendAuctionTime(auction);
                return true;
            } else {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống: Không thể cập nhật giá vào DB!");
            }

        } finally {
            lock.unlock();
        }
    }

    private void extendAuctionTime(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = auction.getEndTime();

        // Nếu còn chưa đến 60s mà có người đặt giá -> tăng thêm 30s
        if (now.isAfter(end.minusSeconds(60)) && now.isBefore(end)) {
            LocalDateTime newEnd = end.plusSeconds(30);

            // Cập nhật cả SQL nữa (Cần thêm hàm updateEndTime trong DAO)
            if (auctionDAO.updateEndTime(auction.getAuctionId(), newEnd)) {
                auction.setEndTime(newEnd);
                System.out.println("=== ANTI-SNIPING: Gia hạn thêm 30s ===");
            }
        }
    }
    public void clearData() { lockMap.clear(); }
}