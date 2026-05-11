package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import java.time.LocalDateTime;
import java.util.List;

public class ManagerService {

    private final ItemService itemService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private volatile boolean running = true;

    public ManagerService(ItemService itemService) {
        this.itemService = itemService;
    }

    // --- Các hàm nghiệp vụ chính (Rất ngắn gọn) ---

    public void openAuction(int id) {
        transitStatus(id, "PENDING", "OPEN");
    }

    public void activateAuction(int id) {
        Auction a = getAuctionOrThrow(id);
        if (LocalDateTime.now().isBefore(a.getStartTime())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Chưa đến giờ!");
        }
        transitStatus(id, "OPEN", "RUNNING");
    }

     // Lấy 1 phiên cụ thể (trả về null nếu không thấy, không văng lỗi)
    public Auction getAuction(int id) {
        return auctionDAO.getAuctionById(id);
    }

     //Lấy toàn bộ danh sách từ Database
    public List<Auction> getAllAuctions() {
        return auctionDAO.getAll();
    }

     // Lấy danh sách theo trạng thái (Ví dụ: để hiển thị các phiên đang chạy lên Web)
    public List<Auction> getAuctionsByStatus(String status) {
        return auctionDAO.getAuctionsByStatus(status);
    }

    // --- Hàm bổ trợ để tái sử dụng (Đây là chìa khóa làm gọn code) ---

    private void transitStatus(int id, String fromStatus, String toStatus) {
        Auction a = getAuctionOrThrow(id);
        if (!fromStatus.equals(a.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Trạng thái sai!");
        }
        if (!auctionDAO.updateStatus(id, toStatus)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database!");
        }
    }

    public Auction getAuctionOrThrow(int id) {
        Auction a = auctionDAO.getAuctionById(id);
        if (a == null) throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Không thấy phiên!");
        return a;
    }

    // --- Luồng tự động (Tách riêng logic xử lý từng phiên) ---

    public void startAutoManager() {
        new Thread(() -> {
            while (running) {
                try {
                    auctionDAO.getAuctionsByStatus("RUNNING").forEach(this::processAutoClose);
                    Thread.sleep(1000);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }

    private void processAutoClose(Auction a) {
        if (LocalDateTime.now().isAfter(a.getEndTime())) {
            String status = (a.getCurrentWinnerId() != null) ? "SOLD" : "ENDED";
            auctionDAO.updateStatus(a.getAuctionId(), status);
            System.out.println("[AUTO] Closed Auction " + a.getAuctionId() + " as " + status);
        }
    }
}