package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AdminService {
    private final ManagerService managerService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final Map<Integer, String> auditLog = new ConcurrentHashMap<>();

    public AdminService(ManagerService managerService) {
        this.managerService = managerService;
    }
    // duyệt phiên
    public boolean approveAuction(int auctionId) {
        // Tận dụng hàm validate để bắt lỗi ID không tồn tại hoặc sai trạng thái
        Auction auction = validatePendingAuction(auctionId);

        try {
            if (auctionDAO.updateStatus(auctionId, "OPEN")) {
                auction.setAuctionStatus("OPEN"); // Cập nhật RAM
                logAction(auctionId, "APPROVED");
                System.out.println(">>> [ADMIN] Auction " + auctionId + " đã được duyệt thành công!");
                return true;
            } else {
                // Trường hợp ID đúng nhưng DB không update được (ví dụ do kết nối)
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể cập nhật trạng thái trên Database!");
            }
        } catch (Exception e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống khi duyệt phiên: " + e.getMessage());
        }
    }

    //  từ chối phiên đấu giá kèm lí do
    public void rejectAuction(int auctionId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Lý do từ chối không được để trống!");
        }

        Auction auction = validatePendingAuction(auctionId);

        if (auctionDAO.updateStatus(auctionId, "REJECTED")) {
            auction.setAuctionStatus("REJECTED");
            logAction(auctionId, "REJECTED: " + reason);
            System.out.println(">>> [ADMIN] Đã từ chối Auction " + auctionId);
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi từ chối phiên!");
        }
    }

    // Bắt lỗi tập trung: Kiểm tra tồn tại và trạng thái WAITING_FOR_ADMIN
    private Auction validatePendingAuction(int auctionId) {
        // Dùng ManagerService để lấy Object (nhớ ManagerService của bạn phải có hàm getAuction)
        Auction auction = managerService.getAuction(auctionId);

        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Không tìm thấy phiên đấu giá có ID: " + auctionId);
        }

        if (!"WAITING_FOR_ADMIN".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Hành động không hợp lệ! Phiên đang ở trạng thái: " + auction.getAuctionStatus());
        }
        return auction;
    }
    // THỐNG KÊ (Sử dụng Stream tối ưu)
    public List<Auction> getPendingAuctions() {
        return managerService.getAllAuctions().stream()
                .filter(a -> "WAITING_FOR_ADMIN".equals(a.getAuctionStatus()))
                .collect(Collectors.toList());
    }

    public void printStats() {
        // GroupingBy giúp đếm cực nhanh từ danh sách tổng
        Map<String, Long> stats = managerService.getAllAuctions().stream()
                .collect(Collectors.groupingBy(Auction::getAuctionStatus, Collectors.counting()));

        System.out.println("\n--- BÁO CÁO TRẠNG THÁI PHIÊN ---");
        if (stats.isEmpty()) {
            System.out.println("Hiện không có dữ liệu đấu giá.");
        } else {
            stats.forEach((status, count) -> System.out.println(status + ": " + count));
        }
    }

    //  LOGGING

    private void logAction(int auctionId, String action) {
        auditLog.put(auctionId, action);
    }

    public String getAudit(int auctionId) {
        return auditLog.getOrDefault(auctionId, "Không có lịch sử cho phiên này.");
    }

    public void clearData() {
        auditLog.clear();
    }

    //Hàm để Admin chặn phiên đấu giá
    public boolean blockAuction(int auctionId) {
        // Có thể bổ sung kiểm tra trạng thái tại đây nếu cần
        try {
            if (auctionDAO.updateStatus(auctionId, "BLOCKED")) {
                logAction(auctionId, "BLOCKED");
                System.out.println(">>> [ADMIN] Đã phong tỏa khẩn cấp phiên đấu giá: " + auctionId);
                return true;
            }
        } catch (Exception e) {
            throw new com.auction.exception.AuctionException(com.auction.exception.ErrorCode.INTERNAL_ERROR.name(),
                    "Lỗi hệ thống khi chặn phiên: " + e.getMessage());
        }
        return false;
    }
}
