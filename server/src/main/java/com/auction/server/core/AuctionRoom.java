package com.auction.server.core;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import com.auction.server.dao.UserDAO;
import com.auction.server.service.BiddingService;
import com.auction.server.core.SessionManager;
import com.auction.server.service.ManagerService;

import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AuctionRoom — ĐÃ SỬA / BỔ SUNG:
 *
 * 1. closeRoom(winnerId, finalPrice):
 *    - Broadcast AUCTION_ENDED tới tất cả viewer trong phòng (như cũ).
 *    - ✅ MỚI: Gửi WINNER_NOTIFICATION riêng tới người thắng (nếu họ đã thoát phòng
 *      nhưng vẫn còn kết nối socket) — đảm bảo winner LUÔN nhận được thông báo 1 lần.
 *      Payload: Object[] {auctionId, finalPrice, itemName(nếu có)}
 *
 * Mọi logic bid giữ nguyên, chỉ mở rộng closeRoom().
 */
public class AuctionRoom {
    private int auctionId;
    private final AtomicReference<Double> currentPrice;
    private volatile Integer currentWinnerId;

    // Tên sản phẩm — được set từ ManagerService khi mở phòng (tùy chọn, để push thông báo)
    private volatile String itemName = "";

    private final Set<ClientHandler> viewers = ConcurrentHashMap.newKeySet();

    private final ExecutorService roomQueueProcessor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AuctionRoom-" + auctionId + "-Queue");
        t.setDaemon(true);
        return t;
    });

    private final UserDAO userDAO = new UserDAO();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    public AuctionRoom(int auctionId, double startingPrice) {
        this.auctionId = auctionId;
        this.currentPrice = new AtomicReference<>(startingPrice);
    }

    public void setItemName(String itemName) {
        if (itemName != null) this.itemName = itemName;
    }

    public void joinRoom(ClientHandler handler) {
        viewers.add(handler);
        System.out.println("[ROOM-" + auctionId + "] Client tham gia. Tổng: " + viewers.size());
    }

    public void leaveRoom(ClientHandler handler) {
        viewers.remove(handler);
        System.out.println("[ROOM-" + auctionId + "] Client rời phòng. Còn: " + viewers.size());
    }

    public void processBid(ClientHandler handler, int userId, double bidAmount, BiddingService biddingService) {
        roomQueueProcessor.submit(() -> {
            try {
                if (bidAmount <= currentPrice.get()) {
                    handler.sendMessage(new Message(ResponseCode.BID_FAILED,
                            String.format("Giá bạn đặt (%.0f) không cao hơn giá hiện tại (%.0f)!",
                                    bidAmount, currentPrice.get()), null));
                    return;
                }

                User user = userDAO.getUserById(userId);
                if (user == null) {
                    handler.sendMessage(new Message(ResponseCode.BID_FAILED, "Không tìm thấy tài khoản!", null));
                    return;
                }

                // FIX: Lấy auction từ DB một lần duy nhất rồi truyền thẳng vào service,
                // tránh placeBid() fetch lại lần 2 (double fetch không nhất quán).
                // executeBidTransaction() bên trong vẫn tự đọc lại winner/price
                // bằng FOR UPDATE trong transaction để đảm bảo nguyên tử.
                Auction auction = biddingService.getManagerService().getAuctionOrThrow(auctionId);
                biddingService.placeBidWithAuction(user, auction, bidAmount);

                currentPrice.set(bidAmount);
                currentWinnerId = userId;

                String bidderName = user.getUsername();
                // FIX: Thêm userId (payload[3]) vào broadcast để client đồng bộ currentWinnerId chính xác,
                // không phụ thuộc vào so sánh username có thể bị sai khi tên trùng nhau
                Object[] broadcastPayload = {auctionId, bidAmount, bidderName, userId};

                Message broadcastMsg = new Message(ResponseCode.NEW_BID_UPDATE,
                        String.format("Giá mới: %,.0f đ (bởi %s)", bidAmount, bidderName),
                        broadcastPayload);
                broadcastToAll(broadcastMsg);

                SessionManager.getInstance().broadcastToAdmins(broadcastMsg);

                handler.sendMessage(new Message(ResponseCode.BID_SUCCESS,
                        String.format("✅ Bạn đang dẫn đầu với giá %,.0f đ!", bidAmount), bidAmount));

                if (auction.getEndTime() != null) {
                    Auction updatedAuction = biddingService.getManagerService().getAuction(auctionId);
                    if (updatedAuction != null && updatedAuction.getEndTime().isAfter(auction.getEndTime())) {
                        Message extendMsg = new Message(ResponseCode.AUCTION_TIME_EXTENDED,
                                "⏱ Phiên được gia hạn thêm 30 giây! Kết thúc lúc: "
                                        + updatedAuction.getEndTime().toString(),
                                updatedAuction.getEndTime());
                        broadcastToAll(extendMsg);
                    }
                }

                System.out.println("[ROOM-" + auctionId + "] BID thành công: " + bidderName + " = " + bidAmount);

            } catch (Exception e) {
                System.err.println("[ROOM-" + auctionId + "] Lỗi BID: " + e.getMessage());
                handler.sendMessage(new Message(ResponseCode.BID_FAILED,
                        e.getMessage() != null ? e.getMessage() : "Giao dịch thất bại, vui lòng thử lại!", null));
            }
        });
    }

    public void broadcastChat(String chatText) {
        Message chatMsg = new Message(ResponseCode.CHAT_BROADCAST, chatText, null);
        broadcastToAll(chatMsg);
    }

    /**
     * Đóng phòng khi phiên kết thúc tự nhiên (ManagerService) hoặc Admin block.
     *
     * ✅ MỚI: Sau khi broadcast AUCTION_ENDED tới người trong phòng,
     * gửi thêm WINNER_NOTIFICATION riêng tới winner qua SessionManager
     * (bao gồm cả trường hợp winner đã thoát phòng nhưng vẫn online).
     * Điều này đảm bảo winner LUÔN nhận được thông báo đúng 1 lần.
     *
     * @param winnerId   ID người thắng (null nếu không ai đặt giá)
     * @param finalPrice Giá cuối cùng
     */
    public void closeRoom(Integer winnerId, double finalPrice) {
        roomQueueProcessor.submit(() -> {
            String winnerUsername = null;
            if (winnerId != null && winnerId > 0) {
                try {
                    User winner = userDAO.getUserById(winnerId);
                    if (winner != null) winnerUsername = winner.getUsername();
                } catch (Exception e) {
                    winnerUsername = "User#" + winnerId;
                }
            }

            // 1. Broadcast AUCTION_ENDED tới tất cả viewer trong phòng
            Object[] endPayload = {auctionId, winnerUsername, finalPrice};
            String endMessage = winnerUsername != null
                    ? String.format("🏆 Phiên kết thúc! Người thắng: %s với giá %,.0f đ", winnerUsername, finalPrice)
                    : "⛔ Phiên kết thúc mà không có người thắng.";
            broadcastToAll(new Message(ResponseCode.AUCTION_ENDED, endMessage, endPayload));
            System.out.println("[ROOM-" + auctionId + "] Đã broadcast AUCTION_ENDED.");

            // 2. ✅ MỚI: Push WINNER_NOTIFICATION riêng tới winner (kể cả đã thoát phòng)
            if (winnerId != null && winnerId > 0) {
                try {
                    // Payload: Object[] {auctionId, finalPrice, itemName}
                    Object[] winnerPayload = {auctionId, finalPrice, itemName};
                    Message winnerMsg = new Message(
                            ResponseCode.WINNER_NOTIFICATION,
                            "🎉 Chúc mừng! Bạn đã thắng phiên đấu giá #" + auctionId,
                            winnerPayload
                    );
                    SessionManager.getInstance().sendToUserIfOnline(winnerId, winnerMsg);
                    System.out.println("[ROOM-" + auctionId + "] Đã gửi WINNER_NOTIFICATION tới User#" + winnerId);
                } catch (Exception e) {
                    System.err.println("[ROOM-" + auctionId + "] Lỗi gửi WINNER_NOTIFICATION: " + e.getMessage());
                }
            }
        });
    }

    public void broadcastToAll(Message message) {
        for (ClientHandler viewer : viewers) {
            viewer.sendMessage(message);
        }
    }

    public void destroyRoom() {
        roomQueueProcessor.shutdownNow();
        viewers.clear();
    }

    public double getCurrentPrice()     { return currentPrice.get(); }
    public int getAuctionId()           { return auctionId; }
    public Integer getCurrentWinnerId() { return currentWinnerId; }

    public boolean containsViewer(ClientHandler sender) {
        return viewers != null && viewers.contains(sender);
    }
}