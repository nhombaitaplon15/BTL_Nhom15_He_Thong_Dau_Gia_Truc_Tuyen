package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
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

    // --- 1. DUYỆT / TỪ CHỐI PHIÊN ---

    /** Duyệt phiên đấu giá */
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

    /** Từ chối phiên đấu giá kèm lí do */
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

    /** Bắt lỗi tập trung: Kiểm tra tồn tại và trạng thái WAITING_FOR_ADMIN */
    private Auction validatePendingAuction(int auctionId) {
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

    // --- 2. THỐNG KÊ (Sử dụng Stream tối ưu) ---

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

    // --- 3. QUẢN LÝ PHONG TỎA (BLOCK) & XÓA ---

    /** * [GỘP TỪ BẢN 1] Hàm để Admin chặn phiên đấu giá nhanh không cần lý do.
     * Tránh làm lỗi các component cũ đang gọi hàm blockAuction(id).
     */
    public boolean blockAuction(int auctionId) {
        return blockAuction(auctionId, "Không có lý do cụ thể");
    }

    /** * [TỪ BẢN 2] Hàm chặn phiên đấu giá khẩn cấp kèm lý do và kiểm tra trạng thái chặt chẽ.
     */
    public boolean blockAuction(int auctionId, String reason) {
        Auction auction = managerService.getAuction(auctionId);
        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(),
                    "Không tìm thấy phiên đấu giá #" + auctionId);
        }

        String status = auction.getAuctionStatus();
        // Chỉ cho phép block phiên ở các trạng thái chưa kết thúc
        if (!"OPEN".equals(status) && !"RUNNING".equals(status) && !"WAITING_FOR_ADMIN".equals(status)) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Không thể chặn phiên ở trạng thái: " + status
                            + ". Chỉ có thể chặn phiên đang WAITING_FOR_ADMIN, OPEN hoặc RUNNING.");
        }

        try {
            if (auctionDAO.updateStatus(auctionId, "BLOCKED")) {
                auction.setAuctionStatus("BLOCKED"); // Đồng bộ dữ liệu local nếu cần
                String logMsg = (reason != null && !reason.trim().isEmpty()) ? "BLOCKED: " + reason : "BLOCKED";
                logAction(auctionId, logMsg);
                System.out.println(">>> [ADMIN] Đã phong tỏa khẩn cấp phiên #" + auctionId + " | Lý do: " + reason);
                return true;
            }
        } catch (Exception e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(),
                    "Lỗi hệ thống khi chặn phiên: " + e.getMessage());
        }
        return false;
    }

    /** [TỪ BẢN 2] Hủy bỏ và dọn dẹp bộ nhớ/DB hoàn toàn cho một phiên bị Block */
    public boolean deleteBlockedAuction(int auctionId) {
        Auction auction = managerService.getAuction(auctionId);
        if (auction == null) {
            System.out.println("[ADMIN] deleteBlockedAuction: phiên #" + auctionId + " không tồn tại, bỏ qua.");
            return false;
        }
        if (!"BLOCKED".equals(auction.getAuctionStatus())) {
            System.out.println("[ADMIN] deleteBlockedAuction: phiên #" + auctionId
                    + " không ở trạng thái BLOCKED (hiện: " + auction.getAuctionStatus() + "), bỏ qua.");
            return false;
        }
        try {
            boolean deleted = auctionDAO.deleteAuction(auctionId);
            if (deleted) {
                // Dọn dẹp phòng realtime trên RAM thông qua ManagerService vừa gộp
                managerService.removeAuctionFromCache(auctionId);
                logAction(auctionId, "DELETED_AFTER_BLOCK");
                System.out.println(">>> [ADMIN] Đã XÓA HOÀN TOÀN phiên BLOCKED #" + auctionId + " khỏi DB.");
            }
            return deleted;
        } catch (Exception e) {
            System.err.println("[ADMIN] Lỗi khi xóa phiên BLOCKED #" + auctionId + ": " + e.getMessage());
            return false;
        }
    }

    // --- 4. AUDIT LOGGING ---

    private void logAction(int auctionId, String action) {
        auditLog.put(auctionId, action);
    }

    public String getAudit(int auctionId) {
        return auditLog.getOrDefault(auctionId, "Không có lịch sử cho phiên này.");
    }

    public void clearData() {
        auditLog.clear();
    }
}