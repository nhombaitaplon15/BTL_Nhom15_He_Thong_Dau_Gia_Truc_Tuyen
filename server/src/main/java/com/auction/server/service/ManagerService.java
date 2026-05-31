package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException ;
import com.auction.common.exception.ErrorCode ;
import com.auction.server.core.AuctionRoom;
import com.auction.server.core.AuctionRoomManager ;
import com.auction.server.dao.AuctionDAO  ;
import com.auction.server.dao.UserDAO ;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ManagerService - Điều phối toàn bộ vòng đời phiên đấu giá.
 *
 * ĐÃ SỬA:
 * 1. Package: server.service -> com.auction.server.service
 * 2. Import: com.auction.exception -> com.auction.common.exception
 * 3. autoCloseAuction() gọi AuctionRoom.closeRoom() để broadcast AUCTION_ENDED realtime
 * 4. Thêm getAuctionsByStatus() dùng trong RequestDispatcher.handleFetchRooms()
 *
 * Đặt tại: server/src/main/java/com/auction/server/service/ManagerService.java
 */
public class ManagerService {

    private final ItemService itemService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final UserDAO userDAO = new UserDAO();
    private volatile boolean running = true;

    public ManagerService(ItemService itemService) {
        this.itemService = itemService;
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

        Auction auction = new Auction(
                0, item.getItemId(), item.getSellerId(), "WAITING_FOR_ADMIN",
                item.getStartingPrice(), item.getStartingPrice(),
                0, null, startTime, endTime, LocalDateTime.now()
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

                            // Mở phòng Realtime trên RAM phục vụ Socket Client
                            AuctionRoomManager.getInstance().createRoom(
                                    auction.getAuctionId(), auction.getCurrentPrice());
                            System.out.println("[AUTO-BOT] Đã đến giờ! Phiên " + auction.getAuctionId() + " -> RUNNING (Room Live Open)");
                        }
                    }

                    // NHIỆM VỤ 2: Tự động đóng phòng và chốt kết quả khi hết giờ (RUNNING -> SOLD/ENDED)
                    List<Auction> activeAuctions = auctionDAO.getAuctionsByStatus("RUNNING");
                    for (Auction auction : activeAuctions) {
                        if (now.isAfter(auction.getEndTime())) {
                            String finalStatus = (auction.getCurrentWinnerId() != null
                                    && auction.getCurrentWinnerId() > 0) ? "SOLD" : "ENDED";
                            auctionDAO.updateStatus(auction.getAuctionId(), finalStatus);
                            System.out.println("[AUTO-BOT] Hết giờ! Phiên " + auction.getAuctionId() + " -> " + finalStatus);

                            // Đẩy gói tin AUCTION_ENDED realtime thông báo cho tất cả Client đang xem phòng
                            AuctionRoom room = AuctionRoomManager.getInstance().getRoom(auction.getAuctionId());
                            if (room != null) {
                                room.closeRoom(auction.getCurrentWinnerId(), auction.getCurrentPrice());
                            }

                            // Giải phóng, hủy bỏ bộ nhớ phòng trên RAM Server sau khi kết thúc
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
}
