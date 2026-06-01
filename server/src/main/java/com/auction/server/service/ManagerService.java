package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException ;
import com.auction.common.exception.ErrorCode ;
import com.auction.server.core.AuctionRoom;
import com.auction.server.core.AuctionRoomManager;
import com.auction.server.dao.AuctionDAO ;
import com.auction.common.network.AuctionItemDTO;
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

    /** Lấy danh sách phiên theo trạng thái - Dùng cho handleFetchRooms của Socket Server */
    public List<Auction> getAuctionsByStatus(String status) {
        return auctionDAO.getAuctionsByStatus(status);
    }

    /** Tìm danh sách các phiên do một Người bán cụ thể đăng lên */
    public List<Auction> getAuctionsBySeller(int sellerId) {
        return auctionDAO.getAuctionsBySeller(sellerId);
    }


    public Auction getAuctionOrThrow(int auctionId) {
        Auction auction = auctionDAO.getAuctionById(auctionId);
        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Phiên đấu giá không tồn tại"); //  Đã sửa thành "throw new"
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
        System.out.println("[MANAGER] Đã lên lịch chờ duyệt (Scheduled) cho Item: " + itemId);
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

        // Tạo phòng kết nối realtime trong AuctionRoomManager phục vụ mạng Socket
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
                    Thread.sleep(1000); // Quét chu kỳ mỗi giây một lần
                    LocalDateTime now = LocalDateTime.now();

                    // NHIỆM VỤ 1: Tự động kích hoạt phòng khi đến giờ mở (OPEN -> RUNNING)
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
                            System.out.println("[AUTO-BOT] Hết giờ! Phiên " + auction.getAuctionId() + " -> " + finalStatus);

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
        t.setDaemon(true); // Luồng tự hủy khi Server chính dừng hoạt động
        t.start();
    }

    // --- 5. DỌN DẸP HỆ THỐNG ---

    public void stopAutoClose() {
        this.running = false;
        System.out.println("[MANAGER] Đã tắt Bot quét phiên đấu giá.");
    }

    /** Giữ lại hàm bổ trợ này từ code của bạn bạn để đồng bộ cấu trúc */
    public void clearData() {
        System.out.println("[MANAGER] Dữ liệu lưu an toàn dưới SQL, hệ thống hoạt động ổn định.");
    }
    public Auction getAuctionById(int auctionId) {return auctionDAO.getAuctionById(auctionId);}

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
        // ManagerService hiện dùng DB làm source of truth, không giữ cache riêng.
        // AuctionRoomManager có thể đã removeRoom khi block, đảm bảo lại ở đây.
        AuctionRoomManager.getInstance().removeRoom(auctionId);
        System.out.println("[MANAGER] Đã dọn dẹp cache/room cho phiên #" + auctionId);
    }
}