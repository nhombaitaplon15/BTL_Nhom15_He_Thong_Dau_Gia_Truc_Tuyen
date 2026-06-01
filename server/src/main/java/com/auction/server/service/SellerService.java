package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO ;
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

    // 4. Cập nhật (Sửa) phiên đấu giá
    public void editAuction(User seller, Auction updatedAuction) {
        validateSeller(seller);
        Auction existingAuction = managerService.getAuctionOrThrow(updatedAuction.getAuctionId());

        if (existingAuction.getSellerId() != seller.getId()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không có quyền sửa phiên này!");
        }

        if (!"WAITING_FOR_ADMIN".equals(existingAuction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Chỉ có thể sửa phiên đang chờ duyệt!");
        }

        if (!auctionDAO.updateAuction(updatedAuction)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi khi lưu thay đổi vào Database.");
        }
    }

    // 3. Yêu cầu hủy/xóa phiên (ĐÃ SỬA LẠI LOGIC)
    public void requestCancelAuction(User seller, int auctionId) {
        validateSeller(seller);
        Auction auction = managerService.getAuctionOrThrow(auctionId);

        if (auction.getSellerId() != seller.getId()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải chủ phiên này!");
        }

        String status = auction.getAuctionStatus();
        if ("WAITING_FOR_ADMIN".equals(status)) {
            // Đang chờ duyệt -> Xóa vĩnh viễn khỏi Database để item quay lại tab "Tạo phiên mới"
            auctionDAO.deleteAuction(auctionId);
            System.out.println("[SELLER] Đã XÓA phiên chờ duyệt: " + auctionId);
        } else if ("OPEN".equals(status)) {
            // Đã duyệt nhưng chưa tới giờ -> Chuyển thành CANCELED
            auctionDAO.updateStatus(auctionId, "CANCELED");
            System.out.println("[SELLER] Đã HỦY phiên đang mở: " + auctionId);
        } else {
            // Đang chạy hoặc đã bán -> Chặn
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Không thể hủy phiên đang chạy hoặc đã kết thúc!");
        }
    }
}