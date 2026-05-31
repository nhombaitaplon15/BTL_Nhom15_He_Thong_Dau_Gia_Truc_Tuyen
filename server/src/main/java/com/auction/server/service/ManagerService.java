package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException ;
import com.auction.common.exception.ErrorCode ;
import com.auction.server.core.AuctionRoom;
import com.auction.server.core.AuctionRoomManager;
import com.auction.server.dao.AuctionDAO ;
import com.auction.server.dao.AuctionItemDAO;
import com.auction.server.dao.UserDAO ;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ManagerService - Điều phối toàn bộ vòng đời phiên đấu giá.
 */
public class ManagerService {

    private final ItemService itemService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final UserDAO userDAO = new UserDAO();
    private volatile boolean running = true;

    public ManagerService(ItemService itemService) {
        this.itemService = itemService;
        List<Auction> activeAuctions = auctionDAO.getAuctionsByStatus("RUNNING");
        for (Auction auction : activeAuctions) {
            AuctionRoomManager.getInstance().createRoom(auction.getAuctionId(), auction.getCurrentPrice());
            System.out.println("[MANAGER] Đã khôi phục phòng Real-time cho phiên #" + auction.getAuctionId());
        }
        autoCloseAuction();
    }

    // --- 1. LẤY DỮ LIỆU ---

    public Auction getAuction(int auctionId) {
        return auctionDAO.getAuctionById(auctionId);
    }

    public User getUserById(int userId) {
        return userDAO.getUserById(userId);
    }

    public List<Auction> getAllAuctions() {
        return auctionDAO.getAll();
    }

    public List<Auction> getAuctionsByStatus(String status) {
        return auctionDAO.getAuctionsByStatus(status);
    }

    public Auction getAuctionOrThrow(int auctionId) {
        Auction auction = auctionDAO.getAuctionById(auctionId);
        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Phiên đấu giá không tồn tại");
        }
        return auction;
    }

    // --- 2. THIẾT LẬP PHIÊN ĐẤU GIÁ ---

    public void scheduleAuction(int itemId, LocalDateTime startTime, LocalDateTime endTime) {
        Item item = itemService.getItemById(itemId);
        if (item == null) {
            throw new AuctionException(ErrorCode.ITEM_NOT_FOUND.name(), "Sản phẩm không tồn tại");
        }

        // ĐÃ FIX Ở ĐÂY: Sử dụng trực tiếp tham số 'itemId' thay vì gọi 'item.getId()' đang bị trả về 0
        Auction auction = new Auction(
            0,
            itemId,              // <-- Fix: Tránh được lỗi vi phạm khóa ngoại
            item.getSellerId(),
            "WAITING_FOR_ADMIN",
            item.getStartingPrice(),
            item.getStartingPrice(),
            0,
            null,
            startTime,
            endTime,
            LocalDateTime.now()
        );

        if (!auctionDAO.insertAuction(auction)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi khi lưu phiên đấu giá vào Database");
        }
        System.out.println("[MANAGER] Đã lên lịch chờ duyệt cho Item: " + itemId);
    }

    // --- 3. ĐIỀU KHIỂN TRẠNG THÁI ---

    public void openAuction(int auctionId) {
        transitStatus(auctionId, "WAITING_FOR_ADMIN", "OPEN");
        System.out.println("[MANAGER] Đã DUYỆT (OPEN) phiên đấu giá " + auctionId);
    }

    public void activateAuction(int auctionId) {
        Auction auction = getAuctionOrThrow(auctionId);
        if (!"OPEN".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Auction chưa được duyệt (OPEN)");
        }
        if (LocalDateTime.now().isBefore(auction.getStartTime())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Chưa đến giờ bắt đầu");
        }
        transitStatus(auctionId, "OPEN", "RUNNING");

        AuctionRoomManager.getInstance().createRoom(auctionId, auction.getCurrentPrice());
        System.out.println("[MANAGER] Phiên " + auctionId + " -> RUNNING. Phòng realtime đã tạo.");
    }

    private void transitStatus(int id, String from, String to) {
        Auction a = getAuctionOrThrow(id);
        if (!from.equals(a.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(),
                "Trạng thái hiện tại không hợp lệ. Cần: " + from);
        }
        auctionDAO.updateStatus(id, to);
    }

    // --- 4. BOT TỰ ĐỘNG HÓA ---

    public void autoCloseAuction() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                    LocalDateTime now = LocalDateTime.now();

                    // NHIỆM VỤ 1: OPEN -> RUNNING
                    List<Auction> openAuctions = auctionDAO.getAuctionsByStatus("OPEN");
                    for (Auction auction : openAuctions) {
                        if (!now.isBefore(auction.getStartTime())) {
                            auctionDAO.updateStatus(auction.getAuctionId(), "RUNNING");
                            AuctionRoomManager.getInstance().createRoom(
                                auction.getAuctionId(), auction.getCurrentPrice());
                            System.out.println("[AUTO-BOT] Phiên " + auction.getAuctionId() + " -> RUNNING");
                        }
                    }

                    // NHIỆM VỤ 2: RUNNING -> SOLD/ENDED
                    List<Auction> activeAuctions = auctionDAO.getAuctionsByStatus("RUNNING");
                    for (Auction auction : activeAuctions) {
                        if (now.isAfter(auction.getEndTime())) {
                            String finalStatus = (auction.getCurrentWinnerId() != null
                                && auction.getCurrentWinnerId() > 0) ? "SOLD" : "ENDED";
                            auctionDAO.updateStatus(auction.getAuctionId(), finalStatus);
                            System.out.println("[AUTO-BOT] Phiên " + auction.getAuctionId() + " -> " + finalStatus);

                            AuctionRoom room = AuctionRoomManager.getInstance().getRoom(auction.getAuctionId());
                            if (room != null) {
                                room.closeRoom(auction.getCurrentWinnerId(), auction.getCurrentPrice());
                            }

                            AuctionRoomManager.getInstance().removeRoom(auction.getAuctionId());
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[AUTO-BOT LỖI] " + e.getMessage());
                }
            }
        }, "AuctionBot-Thread");
        t.setDaemon(true);
        t.start();
    }

    public void stopAutoClose() {
        this.running = false;
        System.out.println("[MANAGER] Đã tắt Bot quét phiên đấu giá.");
    }
    public List<Auction> getAuctionsBySeller(int sellerId) {
        return auctionDAO.getAuctionsBySeller(sellerId);
    }
    public Auction getAuctionById(int auctionId) {return auctionDAO.getAuctionById(auctionId);}

    public List<AuctionItemDAO> getAuctionItemsBySeller(int sellerId) {
        List<Auction> auctions = auctionDAO.getAuctionsBySeller(sellerId);
        List<AuctionItemDAO> combinedList = new java.util.ArrayList<>();

        for (Auction auction : auctions) {
            try {
                Item item = itemService.getItemById(auction.getItemId());
                AuctionItemDAO combined = new AuctionItemDAO(item, auction);
                combinedList.add(combined);
            } catch (Exception e) {
                System.err.println("[MANAGER] Không tìm thấy item cho phiên: " + auction.getAuctionId());
                e.printStackTrace();
            }
        }
        return combinedList;
    }
}