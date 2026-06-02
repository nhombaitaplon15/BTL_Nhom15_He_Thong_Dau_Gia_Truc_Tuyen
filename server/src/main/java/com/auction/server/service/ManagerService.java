package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.server.core.AuctionRoom;
import com.auction.server.core.AuctionRoomManager;
import com.auction.server.dao.AuctionDAO;
import com.auction.common.network.AuctionItemDTO;
import com.auction.server.dao.UserDAO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ManagerService - Điều phối toàn bộ vòng đời phiên đấu giá.
 *
 * ĐÃ SỬA BUG RACE CONDITION trong autoCloseAuction():
 *   LỖI CŨ: room.closeRoom() là async (submit vào roomQueueProcessor),
 *   nhưng ngay sau đó AuctionRoomManager.removeRoom() gọi destroyRoom()
 *   → shutdownNow() cancel task broadcast trước khi kịp gửi → client
 *   KHÔNG BAO GIỜ nhận được AUCTION_ENDED và WINNER_NOTIFICATION.
 *
 *   SỬA: Dùng AuctionRoomManager.closeRoomNaturally() — hàm này remove
 *   phòng khỏi map ngay (không nhận bid mới) nhưng delay destroyRoom()
 *   3 giây để broadcast hoàn tất trước khi shutdown thread pool.
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

    public List<Auction> getAuctionsBySeller(int sellerId) {
        return auctionDAO.getAuctionsBySeller(sellerId);
    }

    public List<Auction> getLiveAuctionsByCategory(String category) {
        List<Auction> auctions = auctionDAO.getLiveAuctionsByCategory(category);
        for (Auction auction : auctions) {
            try {
                Item item = itemService.getItemById(auction.getItemId());
                auction.setItem(item);
            } catch (Exception e) {
                System.err.println("[MANAGER] Không load được item cho auction #" + auction.getAuctionId());
            }
        }
        return auctions;
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

        Auction auction = new Auction(
                0,
                itemId,
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
        System.out.println("[MANAGER] Phiên " + auctionId + " -> RUNNING. Phòng realtime đã tạo thành công.");
    }

    private void transitStatus(int id, String from, String to) {
        Auction a = getAuctionOrThrow(id);
        if (!from.equals(a.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(),
                    "Trạng thái hiện tại không hợp lệ. Cần: " + from);
        }
        auctionDAO.updateStatus(id, to);
    }

    // --- 4. BOT TỰ ĐỘNG HÓA QUÉT SQL (BACKGROUND THREAD) ---

    public void autoCloseAuction() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                    LocalDateTime now = LocalDateTime.now();

                    // NHIỆM VỤ 1: OPEN -> RUNNING khi đến giờ bắt đầu
                    List<Auction> openAuctions = auctionDAO.getAuctionsByStatus("OPEN");
                    for (Auction auction : openAuctions) {
                        if (!now.isBefore(auction.getStartTime())) {
                            auctionDAO.updateStatus(auction.getAuctionId(), "RUNNING");
                            AuctionRoomManager.getInstance().createRoom(
                                    auction.getAuctionId(), auction.getCurrentPrice());
                            System.out.println("[AUTO-BOT] Phiên " + auction.getAuctionId() + " -> RUNNING");
                        }
                    }

                    // NHIỆM VỤ 2: RUNNING -> SOLD/ENDED khi hết giờ
                    List<Auction> activeAuctions = auctionDAO.getAuctionsByStatus("RUNNING");
                    for (Auction auction : activeAuctions) {
                        if (now.isAfter(auction.getEndTime())) {
                            String finalStatus = (auction.getCurrentWinnerId() != null
                                    && auction.getCurrentWinnerId() > 0) ? "SOLD" : "ENDED";
                            auctionDAO.updateStatus(auction.getAuctionId(), finalStatus);
                            System.out.println("[AUTO-BOT] Hết giờ! Phiên " + auction.getAuctionId() + " -> " + finalStatus);

                            // ĐÃ SỬA: Dùng closeRoomNaturally thay vì closeRoom + removeRoom tách biệt.
                            // closeRoomNaturally() = remove khỏi map ngay (không nhận bid mới)
                            // + broadcast AUCTION_ENDED + WINNER_NOTIFICATION async
                            // + delay 3s trước khi destroyRoom() để broadcast hoàn tất.
                            AuctionRoomManager.getInstance().closeRoomNaturally(
                                    auction.getAuctionId(),
                                    auction.getCurrentWinnerId(),
                                    auction.getCurrentPrice()
                            );
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

    // --- 5. DỌN DẸP HỆ THỐNG ---

    public void stopAutoClose() {
        this.running = false;
        System.out.println("[MANAGER] Đã tắt Bot quét phiên đấu giá.");
    }

    public void clearData() {
        System.out.println("[MANAGER] Dữ liệu lưu an toàn dưới SQL, hệ thống hoạt động ổn định.");
    }

    public Auction getAuctionById(int auctionId) { return auctionDAO.getAuctionById(auctionId); }

    public List<AuctionItemDTO> getAuctionItemsBySeller(int sellerId) {
        List<Auction> auctions = auctionDAO.getAuctionsBySeller(sellerId);
        List<AuctionItemDTO> combinedList = new java.util.ArrayList<>();
        for (Auction auction : auctions) {
            try {
                Item item = itemService.getItemById(auction.getItemId());
                AuctionItemDTO combined = new AuctionItemDTO(item, auction);
                combinedList.add(combined);
            } catch (Exception e) {
                System.err.println("[MANAGER] Không tìm thấy item cho phiên: " + auction.getAuctionId());
                e.printStackTrace();
            }
        }
        return combinedList;
    }

    public void removeAuctionFromCache(int auctionId) {
        AuctionRoomManager.getInstance().removeRoom(auctionId);
        System.out.println("[MANAGER] Đã dọn dẹp cache/room cho phiên #" + auctionId);
    }
}
