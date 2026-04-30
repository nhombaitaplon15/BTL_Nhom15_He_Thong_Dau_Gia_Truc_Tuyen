package com.auction.service;

import com.auction.common.model.Auction;
import java.time.LocalDateTime;

public class BiddingService {
    private ManagerService managerService;

    public BiddingService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // --- 1. validateBidPrice(): Check giá mới ---
    public String validateBidPrice(int auctionId, int newPrice) {
        Auction auction = managerService.getAuction(auctionId);
        if (auction == null) return "LỖI: Phiên không tồn tại!";

        if (!"RUNNING".equals(auction.getStatus())) {
            return "LỖI: Phiên hiện không trong trạng thái RUNNING!";
        }
        // 2. Tự do tăng giá: Chỉ cần lớn hơn giá hiện tại là OK
        if (newPrice <= auction.getCurrentPrice()) {
            return "LỖI: Giá đặt mới (" + newPrice + ") phải lớn hơn giá hiện tại (" + auction.getCurrentPrice() + ")!";
        }

        return "VALID";
    }

    // --- 2. updateTopBidder(): Cập nhật người dẫn đầu ---
    public void updateTopBidder(int auctionId, String bidderName, int newPrice) {
        String check = validateBidPrice(auctionId, newPrice);

        if ("VALID".equals(check)) {
            Auction auction = managerService.getAuction(auctionId);

            auction.setCurrentPrice(newPrice);
            auction.setHighestBidder(bidderName);
            System.out.println("Cập nhật: " + bidderName + " đang dẫn đầu phiên " + auctionId);

            // 3. Gọi hàm kiểm tra gia hạn (Anti-sniping)
            extendAuctionTime(auction);
        } else {
            System.out.println(check);
        }
    }

    // --- 3. extendAuctionTime(): Anti-sniping ---
    private void extendAuctionTime(Auction auction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = auction.getEndTime();

        // Kiểm tra xem có đang ở "Vùng đỏ" (60 giây cuối) hay không
        if (now.isAfter(endTime.minusSeconds(60)) && now.isBefore(endTime)) {
            // Cộng thêm 30 giây vào endTime hiện tại
            auction.setEndTime(endTime.plusSeconds(30));
            System.out.println("=== ANTI-SNIPING: Tự động gia hạn thêm 30s cho phiên " + auction.getAuctionId() + " ===");
        }
    }
}