package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.UserDAO;

import java.time.LocalDateTime;
import java.util.List;

public class ManagerService {

    private final ItemService itemService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final UserDAO userDAO = new UserDAO();
    private volatile boolean running = true;

    public ManagerService(ItemService itemService) {
        this.itemService = itemService;
    }

    // --- 1. LẤY DỮ LIỆU (Thay cho việc lấy từ List RAM) ---

    public Auction getAuction(int auctionId) {
        return auctionDAO.getAuctionById(auctionId);
    }
    public User getUserById(int userId) {
        return userDAO.getUserById(userId);
    }

    public List<Auction> getAllAuctions() {
        return auctionDAO.getAll();
    }

    public Auction getAuctionOrThrow(int auctionId) {
        Auction auction = auctionDAO.getAuctionById(auctionId);
        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Phiên đấu giá không tồn tại");
        }
        return auction;
    }

    // --- 2. THIẾT LẬP PHIÊN (Khôi phục lại scheduleAuction của bạn) ---

    public void scheduleAuction(int itemId, LocalDateTime startTime, LocalDateTime endTime) {
        Item item = itemService.getItemById(itemId);
        if (item == null) {
            throw new AuctionException(ErrorCode.ITEM_NOT_FOUND.name(), "Sản phẩm không tồn tại");
        }

        // Tạo Auction và lưu thẳng xuống SQL
        Auction auction = new Auction(
                0, item.getItemId(), item.getSellerId(), "WAITING_FOR_ADMIN",
                item.getStartingPrice(), item.getStartingPrice(),
                0, null, startTime, endTime, LocalDateTime.now()
        );

        if (!auctionDAO.insertAuction(auction)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi khi lưu phiên đấu giá vào Database");
        }
        System.out.println("[MANAGER] Scheduled auction cho Item: " + itemId);
    }

    // --- 3. ĐẶT GIÁ KHỞI ĐIỂM (Khôi phục logic setupStartPrice của bạn) ---

    public void setupStartPrice(int itemId, double newPrice) {
        Item item = itemService.getItemById(itemId);
        if (item == null) {
            throw new AuctionException(ErrorCode.ITEM_NOT_FOUND.name(), "Sản phẩm không tồn tại");
        }
        if (newPrice <= 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Giá khởi điểm phải > 0");
        }

        // Cập nhật giá vào Database (ItemDAO/Service cần có hàm này)
        item.setStartingPrice(newPrice);
        // Lưu ý: Bạn cần gọi itemDAO.update(item) ở đây để lưu xuống SQL bền vững
        System.out.println("[MANAGER] Set start price: " + newPrice);
    }

    // --- 4. ĐIỀU KHIỂN TRẠNG THÁI (STATUS) ---

    public void openAuction(int auctionId) {
        transitStatus(auctionId, "PENDING", "OPEN");
        System.out.println("[MANAGER] OPEN auction " + auctionId);
    }

    public void activateAuction(int auctionId) {
        Auction auction = getAuctionOrThrow(auctionId);
        if (!"OPEN".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Auction chưa OPEN");
        }
        if (LocalDateTime.now().isBefore(auction.getStartTime())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Chưa đến giờ bắt đầu");
        }
        transitStatus(auctionId, "OPEN", "RUNNING");
        System.out.println("[MANAGER] RUNNING auction " + auctionId);
    }

    private void transitStatus(int id, String from, String to) {
        Auction a = getAuctionOrThrow(id);
        if (!from.equals(a.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Trạng thái hiện tại không hợp lệ");
        }
        auctionDAO.updateStatus(id, to);
    }

    // --- 5. TỰ ĐỘNG ĐÓNG PHIÊN (QUÉT SQL) ---

    public void autoCloseAuction() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                    // Quét các phiên RUNNING trong Database
                    List<Auction> activeAuctions = auctionDAO.getAuctionsByStatus("RUNNING");
                    for (Auction auction : activeAuctions) {
                        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
                            String finalStatus = (auction.getCurrentWinnerId() != null) ? "SOLD" : "ENDED";
                            auctionDAO.updateStatus(auction.getAuctionId(), finalStatus);
                            System.out.println("[AUTO] Auction " + auction.getAuctionId() + " -> " + finalStatus);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // --- 6. DỌN DẸP HỆ THỐNG ---

    public void stopAutoClose() {
        this.running = false;
    }

    public void clearData() {
        // Trên SQL, hàm này thường dùng để xóa các bảng tạm hoặc log cũ
        System.out.println("[MANAGER] Dữ liệu trên SQL ổn định, không cần clear RAM.");
    }
}