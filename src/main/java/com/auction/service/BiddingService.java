package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BiddingService {

    private final ManagerService managerService;

    // tạo cho mỗi phiên đấu giá 1 cái khóa để ko sai lệch nếu nhiều người truy cập cùng lúc
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public BiddingService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // xử lí đa luồng cho đặt giá
    public boolean placeBid(int auctionId, String bidderName, double newPrice) {

        Auction auction = managerService.getAuction(auctionId);

        if (auction == null) {
            throw new AuctionException(
                    ErrorCode.AUCTION_NOT_FOUND.name(),
                    "Auction không tồn tại"
            );
        }
        // lấy khóa của auction
        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());

        lock.lock();
        try {
            if (!"RUNNING".equals(auction.getAuctionStatus())) {
                throw new AuctionException(
                        ErrorCode.AUCTION_INVALID_STATE.name(),
                        "Auction chưa RUNNING!"
                );
            }
            if (LocalDateTime.now().isAfter(auction.getEndTime())) {
                throw new AuctionException(
                        ErrorCode.AUCTION_ALREADY_ENDED.name(),
                        "Auction đã end"
                );
            }
            if (newPrice <= auction.getCurrentPrice()) {
                throw new AuctionException(
                        ErrorCode.BID_TOO_LOW.name(),
                        "Giá thấp quá"
                );
            }
            auction.setCurrentPrice(newPrice);
            auction.setHighestBidder(bidderName);
            System.out.println( bidderName + " đang dẫn đầu với giá " + newPrice);
            extendAuctionTime(auction);
            return true;
        } finally {
            lock.unlock();
        }
    }

    // nếu đặt giá sát giờ kết thúc thì tăng thời gian lên
    private void extendAuctionTime(Auction auction) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = auction.getEndTime();

        if (now.isAfter(end.minusSeconds(60)) && now.isBefore(end)) {
            auction.setEndTime(end.plusSeconds(30));
            System.out.println("=== ANTI-SNIPING EXTEND 30s ===");
        }
    }

    // xác nhận đã nhận hàng
    public boolean confirmReceived(int auctionId, String bidderName) {

        Auction auction = managerService.getAuction(auctionId);
        if (auction == null) {
            throw new AuctionException(
                    ErrorCode.AUCTION_NOT_FOUND.name(),
                    "Auction không tồn tại"
            );
        }
        if (!bidderName.equals(auction.getHighestBidder())) {
            throw new AuctionException(
                    ErrorCode.UNAUTHORIZED.name(),
                    "Bạn không phải người thắng!"
            );
        }
        synchronized (auction) {

            if (!"PAID".equals(auction.getItem().getItemStatus())) {
                throw new AuctionException(
                        ErrorCode.AUCTION_INVALID_STATE.name(),
                        "Chưa ở trạng thái PAID!"
                );
            }
            auction.getItem().setItemStatus("COMPLETED");
            System.out.println( bidderName + " đã xác nhận nhận hàng!");
            return true;
        }
    }
}