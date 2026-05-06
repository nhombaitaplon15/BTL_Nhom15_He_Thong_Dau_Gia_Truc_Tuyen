package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdminService {

    private ManagerService managerService;

    // log đơn giản (RAM)
    private Map<Integer, String> auditLog = new ConcurrentHashMap<>();

    public AdminService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // ---------------- 1. GET PENDING ----------------
    public List<Auction> getPendingAuctions() {
        return managerService.getAllAuctions()
                .stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .toList();
    }

    // ---------------- 2. APPROVE ----------------
    public boolean approveAuction(int auctionId) {

        Auction auction = managerService.getAuction(auctionId);

        if (auction == null) {
            throw new AuctionException(
                    ErrorCode.AUCTION_NOT_FOUND.name(),
                    "Auction không tồn tại"
            );
        }

        if (!"PENDING".equals(auction.getStatus())) {
            throw new AuctionException(
                    ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Chỉ PENDING mới được duyệt!"
            );
        }

        auction.setStatus("RUNNING");

        logAction(auctionId, "APPROVED");

        System.out.println("✔ Auction " + auctionId + " đã được duyệt");
        return true;
    }

    // ---------------- 3. REJECT (có lý do) ----------------
    public void rejectAuction(int auctionId, String reason) {

        Auction auction = managerService.getAuction(auctionId);

        if (auction == null) {
            throw new AuctionException(
                    ErrorCode.AUCTION_NOT_FOUND.name(),
                    "Auction không tồn tại"
            );
        }

        if (!"PENDING".equals(auction.getStatus())) {
            throw new AuctionException(
                    ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Chỉ PENDING mới được duyệt!"
            );
        }

        auction.setStatus("REJECTED");

        logAction(auctionId, "REJECTED: " + reason);

        System.out.println("✖ Auction " + auctionId + " bị từ chối vì: " + reason);
    }

    // ---------------- 4. BULK APPROVE (LEVEL NÂNG CAO) ----------------
    public void bulkApprove(List<Integer> auctionIds) {

        for (int id : auctionIds) {
            try {
                approveAuction(id);
            } catch (Exception e) {
                System.out.println("Không duyệt được auction " + id + ": " + e.getMessage());
            }
        }
    }

    // ---------------- 5. STATISTICS ----------------
    public void printStats() {

        long pending = managerService.getAllAuctions().stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .count();

        long running = managerService.getAllAuctions().stream()
                .filter(a -> "RUNNING".equals(a.getStatus()))
                .count();

        long rejected = managerService.getAllAuctions().stream()
                .filter(a -> "REJECTED".equals(a.getStatus()))
                .count();

        long sold = managerService.getAllAuctions().stream()
                .filter(a -> "SOLD".equals(a.getStatus()))
                .count();

        System.out.println("===== ADMIN STATISTICS =====");
        System.out.println("PENDING: " + pending);
        System.out.println("RUNNING: " + running);
        System.out.println("REJECTED: " + rejected);
        System.out.println("SOLD: " + sold);
    }

    // ---------------- 6. AUDIT LOG ----------------
    private void logAction(int auctionId, String action) {
        auditLog.put(auctionId, action);
    }

    public String getAudit(int auctionId) {
        return auditLog.getOrDefault(auctionId, "NO ACTION");
    }
}