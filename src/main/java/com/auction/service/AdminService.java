package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AdminService {
    private ManagerService managerService;
    private AuctionDAO auctionDAO = new AuctionDAO(); // Thêm DAO để lưu xuống DB

    private Map<Integer, String> auditLog = new ConcurrentHashMap<>();

    public AdminService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // --- LOGIC QUẢN TRỊ VIÊN ---

    /**
     * Duyệt phiên: Chuyển từ WAITING_FOR_ADMIN -> OPEN (hoặc RUNNING tùy bạn đặt tên)
     */
    public boolean approveAuction(int auctionId) {
        Auction auction = validatePendingAuction(auctionId);

        // Bước 1: Cập nhật SQL trước
        if (auctionDAO.updateStatus(auctionId, "OPEN")) {
            // Bước 2: Cập nhật RAM để đồng bộ giao diện
            auction.setAuctionStatus("OPEN");
            logAction(auctionId, "APPROVED");
            System.out.println(">>> Auction " + auctionId + " đã được mở!");
            return true;
        }
        throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database khi duyệt phiên!");
    }

    /**
     * Từ chối phiên đấu giá
     */
    public void rejectAuction(int auctionId, String reason) {
        Auction auction = validatePendingAuction(auctionId);

        if (auctionDAO.updateStatus(auctionId, "REJECTED")) {
            auction.setAuctionStatus("REJECTED");
            logAction(auctionId, "REJECTED: " + reason);
        }
    }

    /**
     * Hàm hỗ trợ tránh code smell: Kiểm tra tồn tại và trạng thái chờ duyệt
     */
    private Auction validatePendingAuction(int auctionId) {
        Auction auction = managerService.getAuction(auctionId);
        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Auction không tồn tại!");
        }
        // Khớp với DB của bạn: WAITING_FOR_ADMIN
        if (!"WAITING_FOR_ADMIN".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Chỉ phiên WAITING_FOR_ADMIN mới được thao tác!");
        }
        return auction;
    }

    // --- THỐNG KÊ & STREAM API ---

    public List<Auction> getPendingAuctions() {
        return managerService.getAllAuctions().stream()
                .filter(a -> "WAITING_FOR_ADMIN".equals(a.getAuctionStatus()))
                .collect(Collectors.toList());
    }

    public void printStats() {
        Map<String, Long> stats = managerService.getAllAuctions().stream()
                .collect(Collectors.groupingBy(Auction::getAuctionStatus, Collectors.counting()));

        System.out.println("--- THỐNG KÊ PHIÊN ĐẤU GIÁ ---");
        stats.forEach((status, count) -> System.out.println(status + ": " + count));
    }

    private void logAction(int auctionId, String action) {
        auditLog.put(auctionId, action);
    }

    public String getAudit(int auctionId) {
        return auditLog.getOrDefault(auctionId, "NO ACTION");
    }

    public void clearData() {
        auditLog.clear();
    }
}