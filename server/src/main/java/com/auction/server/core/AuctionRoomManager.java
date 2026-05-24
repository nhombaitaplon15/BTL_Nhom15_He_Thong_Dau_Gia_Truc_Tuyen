package com.auction.server.core;

import java.util.concurrent.ConcurrentHashMap;

public class AuctionRoomManager {
    private static final AuctionRoomManager instance = new AuctionRoomManager();

    // Map lưu trữ RoomId -> AuctionRoom instance
    private final ConcurrentHashMap<Integer, AuctionRoom> activeRooms = new ConcurrentHashMap<>();

    private AuctionRoomManager() {}
    public static AuctionRoomManager getInstance() { return instance; }

    public void createRoom(int roomId, double startingPrice) {
        // Chỉ tạo nếu chưa tồn tại, tránh ghi đè room đang chạy
        activeRooms.computeIfAbsent(roomId, id -> new AuctionRoom(id, startingPrice));
    }

    public AuctionRoom getRoom(int roomId) {
        return activeRooms.get(roomId);
    }

    public void removeRoom(int roomId) {
        AuctionRoom room = activeRooms.remove(roomId);
        if (room != null) {
            room.destroyRoom(); // Hủy Queue, giải phóng Thread bộ nhớ
        }
    }

    /**
     * Khi Client rớt mạng ngang, ta phải quét qua các phòng xem nó đang ở đâu để đá ra,
     * tránh memory leak ở danh sách viewers trong AuctionRoom.
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