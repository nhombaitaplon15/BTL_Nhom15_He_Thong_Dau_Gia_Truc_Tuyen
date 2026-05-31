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
 * AuctionRoom - Phòng đấu giá realtime.
 *
 * ĐÃ SỬA CÁC LỖI CHÍNH:
 * 1. processBid() cũ: boolean dbSuccess = true (MOCK!) => Nay kết nối BiddingService thực.
 * 2. Thêm broadcastChat() để chat hoạt động.
 * 3. Thêm closeRoom() khi phiên kết thúc: broadcast AUCTION_ENDED đến tất cả viewers.
 * 4. Anti-sniping kết quả (AUCTION_TIME_EXTENDED) được broadcast tới phòng.
 * 5. processBid() nhận thêm tham số BiddingService (inject từ RequestDispatcher).
 *
 * Đặt tại: server/src/main/java/com/auction/server/core/AuctionRoom.java
 */
public class AuctionRoom {
    private int auctionId;
    private final AtomicReference<Double> currentPrice;
    private volatile Integer currentWinnerId;

    // Danh sách người đang xem trong phòng - Thread-safe
    private final Set<ClientHandler> viewers = ConcurrentHashMap.newKeySet();

    // Queue đơn luồng: GIẢI PHÁP CHỐNG RACE CONDITION KHI 1000 USER BID CÙNG LÚC
    // Mọi request bid được xếp hàng và xử lý tuần tự => tuyệt đối không double-bid
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

    public void joinRoom(ClientHandler handler) {
        viewers.add(handler);
        System.out.println("[ROOM-" + auctionId + "] Client tham gia. Tổng: " + viewers.size());
    }

    public void leaveRoom(ClientHandler handler) {
        viewers.remove(handler);
        System.out.println("[ROOM-" + auctionId + "] Client rời phòng. Còn: " + viewers.size());
    }

    /**
     * [ĐÃ SỬA TRỌNG TÂM] Xử lý BID với BiddingService thực.
     *
     * Logic cũ: boolean dbSuccess = true (mock) => LUÔN thành công, không xuống DB,
     * không trừ tiền, không ghi lịch sử => HOÀN TOÀN SAI.
     *
     * Logic mới:
     * 1. Fast-fail trên RAM (AtomicReference) - không cần lock DB
     * 2. Giao toàn bộ Transaction DB cho BiddingService (trừ tiền, hoàn tiền, ghi log)
     * 3. Broadcast NEW_BID_UPDATE đến tất cả viewers nếu thành công
     * 4. Nếu có anti-sniping, broadcast AUCTION_TIME_EXTENDED
     *
     * @param biddingService Inject từ RequestDispatcher để tái sử dụng instance
     */
//    public void processBid(ClientHandler handler, int userId, double bidAmount, BiddingService biddingService) {
//        roomQueueProcessor.submit(() -> {
//            try {
//                // --- BƯỚC 1: Fast-fail trên RAM (cực nhanh, không cần DB) ---
//                if (bidAmount <= currentPrice.get()) {
//                    handler.sendMessage(new Message(ResponseCode.BID_FAILED,
//                            String.format("Giá bạn đặt (%.0f) không cao hơn giá hiện tại (%.0f)!",
//                                    bidAmount, currentPrice.get()), null));
//                    return;
//                }
//
//                // --- BƯỚC 2: Lấy thông tin User để BiddingService validate đầy đủ ---
//                User user = userDAO.getUserById(userId);
//                if (user == null) {
//                    handler.sendMessage(new Message(ResponseCode.BID_FAILED, "Không tìm thấy tài khoản!", null));
//                    return;
//                }
//
//                // --- BƯỚC 3: Thực thi toàn bộ DB transaction qua BiddingService ---
//                // BiddingService xử lý: validate rules, trừ tiền, hoàn tiền cũ,
//                // ghi lịch sử, anti-sniping, commit/rollback
//                Auction auction = biddingService.getManagerService().getAuctionOrThrow(auctionId);
//                biddingService.placeBid(user, auctionId, bidAmount);
//
//                // --- BƯỚC 4: DB thành công => Cập nhật RAM của phòng ---
//                currentPrice.set(bidAmount);
//                currentWinnerId = userId;
//
//                // --- BƯỚC 5: Broadcast NEW_BID_UPDATE đến TẤT CẢ người trong phòng ---
//                // Payload: Object[] {auctionId, newPrice, winnerId} để client parse
//                Object[] broadcastPayload = {auctionId, bidAmount, userId};
//                Message broadcastMsg = new Message(ResponseCode.NEW_BID_UPDATE,
//                        String.format("Giá mới: %,.0f đ (bởi User#%d)", bidAmount, userId),
//                        broadcastPayload);
//                broadcastToAll(broadcastMsg);
//
//                // --- BƯỚC 6: Riêng người thắng nhận BID_SUCCESS ---
//                handler.sendMessage(new Message(ResponseCode.BID_SUCCESS,
//                        String.format("✅ Bạn đang dẫn đầu với giá %,.0f đ!", bidAmount), bidAmount));
//
//                // --- BƯỚC 7: Kiểm tra anti-sniping - nếu end_time được gia hạn => broadcast ---
//                if (auction.getEndTime() != null) {
//                    Auction updatedAuction = biddingService.getManagerService().getAuction(auctionId);
//                    if (updatedAuction != null && updatedAuction.getEndTime().isAfter(auction.getEndTime())) {
//                        Message extendMsg = new Message(ResponseCode.AUCTION_TIME_EXTENDED,
//                                "⏱ Phiên được gia hạn thêm 30 giây! Kết thúc lúc: "
//                                        + updatedAuction.getEndTime().format(FORMATTER),
//                                updatedAuction.getEndTime());
//                        broadcastToAll(extendMsg);
//                    }
//                }
//
//                System.out.println("[ROOM-" + auctionId + "] BID thành công: User#" + userId + " = " + bidAmount);
//
//            } catch (Exception e) {
//                // BiddingService throws AuctionException với message mô tả lỗi
//                System.err.println("[ROOM-" + auctionId + "] Lỗi BID: " + e.getMessage());
//                handler.sendMessage(new Message(ResponseCode.BID_FAILED,
//                        e.getMessage() != null ? e.getMessage() : "Giao dịch thất bại, vui lòng thử lại!", null));
//            }
//        });
//    }
    public void processBid(ClientHandler handler, int userId, double bidAmount, BiddingService biddingService) {
        roomQueueProcessor.submit(() -> {
            try {
                // --- BƯỚC 1: Fast-fail trên RAM (cực nhanh, không cần DB) ---
                if (bidAmount <= currentPrice.get()) {
                    handler.sendMessage(new Message(ResponseCode.BID_FAILED,
                            String.format("Giá bạn đặt (%.0f) không cao hơn giá hiện tại (%.0f)!",
                                    bidAmount, currentPrice.get()), null));
                    return;
                }

                // --- BƯỚC 2: Lấy thông tin User để BiddingService validate đầy đủ ---
                User user = userDAO.getUserById(userId);
                if (user == null) {
                    handler.sendMessage(new Message(ResponseCode.BID_FAILED, "Không tìm thấy tài khoản!", null));
                    return;
                }

                // --- BƯỚC 3: Thực thi toàn bộ DB transaction qua BiddingService ---
                // BiddingService xử lý: validate rules, trừ tiền, hoàn tiền cũ,
                // ghi lịch sử, anti-sniping, commit/rollback
                Auction auction = biddingService.getManagerService().getAuctionOrThrow(auctionId);
                biddingService.placeBid(user, auctionId, bidAmount);

                // --- BƯỚC 4: DB thành công => Cập nhật RAM của phòng ---
                currentPrice.set(bidAmount);
                currentWinnerId = userId;

                // --- BƯỚC 5: Broadcast NEW_BID_UPDATE đến TẤT CẢ người trong phòng ---
                // [ĐÃ SỬA]: Chuyển userId thành Tên người dùng (String) để UI không bị lỗi ClassCast
                String bidderName = user.getUsername();
                Object[] broadcastPayload = {auctionId, bidAmount, bidderName};

                Message broadcastMsg = new Message(ResponseCode.NEW_BID_UPDATE,
                        String.format("Giá mới: %,.0f đ (bởi %s)", bidAmount, bidderName),
                        broadcastPayload);
                broadcastToAll(broadcastMsg);

                // --- BƯỚC 5b: Gửi thêm tới Admin đang online để LiveFeed cập nhật ---
                SessionManager.getInstance().broadcastToAdmins(broadcastMsg);

                // --- BƯỚC 6: Riêng người thắng nhận BID_SUCCESS ---
                handler.sendMessage(new Message(ResponseCode.BID_SUCCESS,
                        String.format("✅ Bạn đang dẫn đầu với giá %,.0f đ!", bidAmount), bidAmount));

                // --- BƯỚC 7: Kiểm tra anti-sniping - nếu end_time được gia hạn => broadcast ---
                if (auction.getEndTime() != null) {
                    // Nhớ import formatter nếu báo lỗi đỏ: DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
                // BiddingService throws AuctionException với message mô tả lỗi
                System.err.println("[ROOM-" + auctionId + "] Lỗi BID: " + e.getMessage());
                handler.sendMessage(new Message(ResponseCode.BID_FAILED,
                        e.getMessage() != null ? e.getMessage() : "Giao dịch thất bại, vui lòng thử lại!", null));
            }
        });
    }
    /**
     * [ĐÃ THÊM] Broadcast tin nhắn chat đến toàn bộ người trong phòng.
     * Gọi từ RequestDispatcher.handleChat()
     */
    public void broadcastChat(String chatText) {
        Message chatMsg = new Message(ResponseCode.CHAT_BROADCAST, chatText, null);
        broadcastToAll(chatMsg);
    }

    /**
     * [ĐÃ THÊM] Đóng phòng khi phiên kết thúc (gọi từ ManagerService.autoCloseAuction).
     * Broadcast AUCTION_ENDED đến tất cả viewers để client tự cập nhật UI.
     */
    public void closeRoom(Integer winnerId, double finalPrice) {
        roomQueueProcessor.submit(() -> {
            // Payload: Object[] {auctionId, finalPrice, winnerId}
            Object[] endPayload = {auctionId, finalPrice, winnerId};
            String endMessage = winnerId != null
                    ? String.format("🏆 Phiên kết thúc! Người thắng: User#%d với giá %,.0f đ", winnerId, finalPrice)
                    : "⛔ Phiên kết thúc mà không có người thắng.";
            broadcastToAll(new Message(ResponseCode.AUCTION_ENDED, endMessage, endPayload));
            System.out.println("[ROOM-" + auctionId + "] Đã broadcast AUCTION_ENDED.");
        });
    }

    /**
     * Helper: Gửi cùng 1 message đến tất cả viewers trong phòng.
     * ClientHandler.sendMessage() đã synchronized nên không cần lock ở đây.
     */
    public void broadcastToAll(Message message) {
        for (ClientHandler viewer : viewers) {
            viewer.sendMessage(message);
        }
    }

    public void destroyRoom() {
        roomQueueProcessor.shutdownNow();
        viewers.clear();
    }

    public double getCurrentPrice() { return currentPrice.get(); }
    public int getAuctionId() { return auctionId; }

    public boolean containsViewer(ClientHandler sender) {
        if(viewers == null){
            return false;
        }else{
            return  viewers.contains(sender);
        }
    }
}