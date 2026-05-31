package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import java.time.LocalDateTime;

public class SellerService {
    private final ManagerService managerService;
    private final AuctionDAO auctionDAO = new AuctionDAO();

    public SellerService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // Kiểm tra quyền Seller
    private void validateSeller(User user) {
        if (!"SELLER".equals(user.getRole())) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Chỉ Seller mới có quyền này!");
        }
    }

    // 1. Yêu cầu tạo phiên
    public void requestCreateAuction(User seller, int itemId, LocalDateTime startTime, LocalDateTime endTime) {
        validateSeller(seller);

        // Gọi manager để tạo (trạng thái mặc định nên là WAITING_FOR_ADMIN từ lúc insert)
        managerService.scheduleAuction(itemId, startTime, endTime);
        System.out.println("[SELLER] Đã gửi yêu cầu duyệt phiên cho sản phẩm: " + itemId);
    }

    // 2. Xác nhận bán (Khi phiên kết thúc)
    public void confirmSale(User seller, int auctionId) {
        validateSeller(seller);
        Auction auction = managerService.getAuctionOrThrow(auctionId);

        if (auction.getSellerId() != seller.getId()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải chủ phiên này!");
        }

        if (auctionDAO.updateStatus(auctionId, "SOLD")) {
            auction.setAuctionStatus("SOLD");
            System.out.println("[SELLER] Xác nhận bán thành công phiên: " + auctionId);
        }
    }

    // 3. Yêu cầu hủy phiên
    public void requestCancelAuction(User seller, int auctionId) {
        validateSeller(seller);
        Auction auction = managerService.getAuctionOrThrow(auctionId);

        // Chặn các trạng thái không được hủy
        String status = auction.getAuctionStatus();
        if ("RUNNING".equals(status) || "SOLD".equals(status)) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên đang chạy hoặc đã bán, không thể hủy!");
        }

        // Chuyển về chờ Admin duyệt hủy
        if (auctionDAO.updateStatus(auctionId, "WAITING_FOR_ADMIN")) {
            auction.setAuctionStatus("WAITING_FOR_ADMIN");
            System.out.println("[SELLER] Đã gửi yêu cầu HỦY phiên: " + auctionId);
        }
    }
}
