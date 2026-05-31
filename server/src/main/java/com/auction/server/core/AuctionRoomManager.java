package com.auction.server.core;



import com.auction.common.model.Auction;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AuctionRoomManager - Quản lý toàn bộ phòng đấu giá đang hoạt động.
 *
 * PHIÊN BẢN ĐẦY ĐỦ: Bổ sung:
 *  - openRoom(Auction): Admin duyệt -> tự động mở phòng
 *  - closeRoom(auctionId): Admin block -> đóng phòng + broadcast kết thúc
 *  - broadcastChatToUserRooms(): Chat realtime trong phòng đấu giá
 *
 * ĐẶT TẠI: server/src/main/java/com/auction/server/core/AuctionRoomManager.java
 */
public class AuctionRoomManager {
    private static final AuctionRoomManager instance = new AuctionRoomManager();

    // auctionId -> AuctionRoom
    private final ConcurrentHashMap<Integer, AuctionRoom> activeRooms = new ConcurrentHashMap<>();

    private AuctionRoomManager() {}
    public static AuctionRoomManager getInstance() { return instance; }

    // =========================================================
    // ROOM LIFECYCLE
    // =========================================================

    /**
     * Tạo phòng từ thông tin Auction đơn giản (dùng khi khởi động server).
     */
    public void createRoom(int roomId, double startingPrice) {
        activeRooms.computeIfAbsent(roomId, id -> new AuctionRoom(id, startingPrice));
    }

    /**
     * [THÊM] Mở phòng từ Auction object - được gọi sau khi Admin duyệt phiên.
     * Đây là điểm kết nối: Admin approve -> openRoom() -> phòng bắt đầu nhận bid.
     */
    public void openRoom(Auction auction) {
        int roomId = auction.getAuctionId();
        activeRooms.computeIfAbsent(roomId, id -> {
            AuctionRoom room = new AuctionRoom(id, auction.getCurrentPrice());
            System.out.println("[ROOM_MANAGER] Đã mở phòng đấu giá #" + roomId
                    + " (startPrice=" + auction.getCurrentPrice() + ")");
            return room;
        });
    }

    /**
     * [THÊM] Đóng phòng theo lệnh Admin Block - broadcast AUCTION_ENDED rồi destroy.
     */
    public void closeRoom(int auctionId) {
        AuctionRoom room = activeRooms.remove(auctionId);
        if (room != null) {
            // Broadcast tới tất cả viewer trong phòng: phiên bị đóng khẩn cấp
            room.broadcastToAll(new Message(ResponseCode.AUCTION_ENDED,
                    "Phiên đấu giá #" + auctionId + " đã bị Admin đóng khẩn cấp!", auctionId));
            room.destroyRoom();
            System.out.println("[ROOM_MANAGER] Đã đóng phòng #" + auctionId + " theo lệnh Admin.");
        }
    }

    public AuctionRoom getRoom(int roomId) {
        return activeRooms.get(roomId);
    }

    public void removeRoom(int roomId) {
        AuctionRoom room = activeRooms.remove(roomId);
        if (room != null) {
            room.destroyRoom();
        }
    }

    // =========================================================
    // CHAT REALTIME
    // =========================================================

    /**
     * [THÊM] Broadcast tin nhắn chat tới tất cả viewer trong phòng mà user đang ở.
     * Một user chỉ ở 1 phòng tại một thời điểm (JOIN_ROOM rồi mới chat được).
     *
     * @param senderUserId  userId người gửi
     * @param fullMessage   Nội dung đã format: "username: message"
     * @param sender        ClientHandler của người gửi (để tìm phòng đang ở)
     */
    public void broadcastChatToUserRooms(int senderUserId, String fullMessage, ClientHandler sender) {
        Message chatMsg = new Message(ResponseCode.CHAT_BROADCAST, fullMessage, null);
        boolean found = false;
        for (AuctionRoom room : activeRooms.values()) {
            if (room.containsViewer(sender)) {
                room.broadcastToAll(chatMsg);
                found = true;
                break; // User chỉ ở 1 phòng tại 1 thời điểm
            }
        }
        if (!found) {
            System.out.println("[CHAT] User#" + senderUserId + " không ở trong phòng nào.");
        }
    }

    // =========================================================
    // MAINTENANCE
    // =========================================================

    /**
     * Khi client rớt mạng ngang, quét qua các phòng để loại ra,
     * tránh memory leak trong danh sách viewers.
     */
    public void removeUserFromAllRooms(ClientHandler handler) {
        for (AuctionRoom room : activeRooms.values()) {
            room.leaveRoom(handler);
        }
    }

    public void shutdownAllRooms() {
        for (AuctionRoom room : activeRooms.values()) {
            room.destroyRoom();
        }
        activeRooms.clear();
    }
}