package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Vehicle;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdminService {   // admin quản lí các phiên đấu giá

    private ManagerService managerService;

    //  lưu dữ liệu về các phiên đấu giá
    private Map<Integer, String> auditLog = new ConcurrentHashMap<>();

    public AdminService(ManagerService managerService) {
        this.managerService = managerService;
    }

    // thêm thông tin vào mục lưu dữ liệu auction
    private void logAction(int auctionId, String action) {
        auditLog.put(auctionId, action);
    }
    // đọc thông tin auction từ mục lưu dữ liệu
    public String getAudit(int auctionId) {
        return auditLog.getOrDefault(auctionId, "NO ACTION");
    }

    // lấy danh sách chờ duyệt , sử dụng java stream API
    public List<Auction> getPendingAuctions() {
        return managerService.getAllAuctions()
                .stream()
                .filter(a -> "PENDING".equals(a.getAuctionStatus()))  // lọc auction có trạng thái "PENDING" thôi
                .toList();
    }

    // duyệt 1 auction
    public boolean approveAuction(int auctionId) {

        Auction auction = managerService.getAuction(auctionId);

        if (auction == null) {
            throw new AuctionException(
                    ErrorCode.AUCTION_NOT_FOUND.name(),
                    "Auction không tồn tại"
            );
        }

        if (!"PENDING".equals(auction.getAuctionStatus())) {
            throw new AuctionException(
                    ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Chỉ PENDING mới được duyệt!"
            );
        }

        auction.setAuctionStatus("RUNNING");

        logAction(auctionId, "APPROVED");  // ghi lại dữ liệu về auction này

        System.out.println("Auction " + auctionId + " đã được duyệt");
        return true;
    }

    // từ chối auction
    public void rejectAuction(int auctionId, String reason) {

        Auction auction = managerService.getAuction(auctionId);

        if (auction == null) {
            throw new AuctionException(
                    ErrorCode.AUCTION_NOT_FOUND.name(),
                    "Auction không tồn tại"
            );
        }

        if (!"PENDING".equals(auction.getAuctionStatus())) {
            throw new AuctionException(
                    ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Chỉ PENDING mới được duyệt!"
            );
        }

        auction.setAuctionStatus("REJECTED");

        logAction(auctionId, "REJECTED: " + reason);

        System.out.println("Auction " + auctionId + " bị từ chối vì: " + reason);
    }

    // duyệt auction hàng loạt
    public void bulkApprove(List<Integer> auctionIds) {

        for (int id : auctionIds) {
            try {
                approveAuction(id);
            } catch (Exception e) {
                System.out.println("Không duyệt được auction " + id + ": " + e.getMessage());
            }
        }
    }

    // in ra thống kê từng loại auction
    public void printStats() {

        long pending = managerService.getAllAuctions().stream()
                .filter(a -> "PENDING".equals(a.getAuctionStatus()))
                .count();

        long running = managerService.getAllAuctions().stream()
                .filter(a -> "RUNNING".equals(a.getAuctionStatus()))
                .count();

        long rejected = managerService.getAllAuctions().stream()
                .filter(a -> "REJECTED".equals(a.getAuctionStatus()))
                .count();

        long sold = managerService.getAllAuctions().stream()
                .filter(a -> "SOLD".equals(a.getAuctionStatus()))
                .count();

        System.out.println("PENDING: " + pending);
        System.out.println("RUNNING: " + running);
        System.out.println("REJECTED: " + rejected);
        System.out.println("SOLD: " + sold);
    }
    public void clearData() {
        auditLog.clear();
    }

}




