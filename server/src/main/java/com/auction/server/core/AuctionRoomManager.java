package com.auction.server.core;

import com.auction.common.model.Auction;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AuctionRoomManager — ĐÃ SỬA:
 *
 * openRoom(Auction): Khi mở phòng, truyền tên sản phẩm (itemName) vào AuctionRoom
 * để server có thể đưa vào payload WINNER_NOTIFICATION khi phiên kết thúc.
 *
 * Mọi logic khác giữ nguyên.
 */
public class AuctionRoomManager {
    private static final AuctionRoomManager instance = new AuctionRoomManager();

    private final ConcurrentHashMap<Integer, AuctionRoom> activeRooms = new ConcurrentHashMap<>();

    private AuctionRoomManager() {}
    public static AuctionRoomManager getInstance() { return instance; }

    // =========================================================
    // ROOM LIFECYCLE
    // =========================================================

    public void createRoom(int roomId, double startingPrice) {
        activeRooms.computeIfAbsent(roomId, id -> new AuctionRoom(id, startingPrice));
    }

    /**
     * Mở phòng từ Auction object - được gọi sau khi Admin duyệt phiên.
     *
     * ✅ SỬA: Set itemName vào phòng nếu Auction đã có Item nạp sẵn,
     * để WINNER_NOTIFICATION có thể đưa tên sản phẩm vào thông báo.
     */
    public void openRoom(Auction auction) {
        int roomId = auction.getAuctionId();
        activeRooms.computeIfAbsent(roomId, id -> {
            AuctionRoom room = new AuctionRoom(id, auction.getCurrentPrice());

            // Set tên sản phẩm nếu có
            if (auction.getItem() != null && auction.getItem().getName() != null) {
                room.setItemName(auction.getItem().getName());
            }

            System.out.println("[ROOM_MANAGER] Đã mở phòng đấu giá #" + roomId
                    + " (startPrice=" + auction.getCurrentPrice() + ")");
            return room;
        });
    }

    /**
     * Đóng phòng theo lệnh Admin Block - broadcast AUCTION_ENDED rồi destroy.
     */
    public void closeRoom(int auctionId) {
        AuctionRoom room = activeRooms.remove(auctionId);
        if (room != null) {
            room.broadcastToAll(new Message(ResponseCode.AUCTION_ENDED,
                    "Phiên đấu giá #" + auctionId + " đã bị Admin đóng khẩn cấp!", auctionId));
            room.destroyRoom();
            System.out.println("[ROOM_MANAGER] Đã đóng phòng #" + auctionId + " theo lệnh Admin.");
        }
    }

    /**
     * Đóng phòng tự nhiên khi phiên hết giờ - dùng closeRoom(winnerId, finalPrice)
     * của AuctionRoom để broadcast đầy đủ và gửi WINNER_NOTIFICATION.
     *
     * @param auctionId  ID phiên cần đóng
     * @param winnerId   ID người thắng (null nếu không ai đặt)
     * @param finalPrice Giá cuối cùng
     */
    public void closeRoomNaturally(int auctionId, Integer winnerId, double finalPrice) {
        AuctionRoom room = activeRooms.remove(auctionId);
        if (room != null) {
            room.closeRoom(winnerId, finalPrice);
            // Hủy thread pool sau khi broadcast (đủ thời gian gửi)
            new java.util.Timer(true).schedule(new java.util.TimerTask() {
                @Override public void run() { room.destroyRoom(); }
            }, 3000); // Đợi 3 giây
            System.out.println("[ROOM_MANAGER] Đã đóng phòng #" + auctionId + " tự nhiên.");
        }
    }

    public AuctionRoom getRoom(int roomId) {
        return activeRooms.get(roomId);
    }

    public void removeRoom(int roomId) {
        AuctionRoom room = activeRooms.remove(roomId);
        if (room != null) room.destroyRoom();
    }

    // =========================================================
    // CHAT REALTIME
    // =========================================================

    public void broadcastChatToUserRooms(int senderUserId, String fullMessage, ClientHandler sender) {
        Message chatMsg = new Message(ResponseCode.CHAT_BROADCAST, fullMessage, null);
        boolean found = false;
        for (AuctionRoom room : activeRooms.values()) {
            if (room.containsViewer(sender)) {
                room.broadcastToAll(chatMsg);
                found = true;
                break;
            }
        }
        if (!found) {
            System.out.println("[CHAT] User#" + senderUserId + " không ở trong phòng nào.");
        }
    }

    // =========================================================
    // MAINTENANCE
    // =========================================================

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