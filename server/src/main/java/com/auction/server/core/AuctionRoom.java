package server.core;

import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class AuctionRoom {
    private final int auctionId;
    private final AtomicReference<Double> currentPrice;
    private volatile int currentWinnerId;

    // DANH SÁCH KHÁN GIẢ TRONG PHÒNG (Dùng cấu trúc Thread-safe cao cấp)
    private final Set<ClientHandler> viewers = ConcurrentHashMap.newKeySet();

    // QUEUE BẢO VỆ CHỐNG RACE-CONDITION TỐI THƯỢNG
    private final ExecutorService roomQueueProcessor = Executors.newSingleThreadExecutor();

    public AuctionRoom(int auctionId, double startingPrice) {
        this.auctionId = auctionId;
        this.currentPrice = new AtomicReference<>(startingPrice);
    }

    public void joinRoom(ClientHandler handler) {
        viewers.add(handler);
    }

    public void leaveRoom(ClientHandler handler) {
        viewers.remove(handler);
    }

    /**
     * Logic Bid CHỐNG LỖI CONCURRENCY.
     * Khi 1000 users gọi hàm này cùng lúc 1 mili-giây, chúng sẽ bị tống vào Queue.
     * 1 Thread duy nhất của roomQueueProcessor sẽ kéo ra xử lý từng cái.
     * Hoàn toàn không bao giờ có chuyện 2 user cùng mua được 1 giá.
     */
    public void processBid(ClientHandler handler, int userId, double bidAmount) {
        roomQueueProcessor.submit(() -> {
            try {
                // 1. Pessimistic check trên RAM cực nhanh
                if (bidAmount <= currentPrice.get()) {
                    handler.sendMessage(new Message(ResponseCode.BID_FAILED, "Giá bị thay đổi, vui lòng thử lại!", null));
                    return;
                }

                // 2. Giao tiếp DB (Chỉ xuống DB khi RAM pass) - Anti double-submit
                // boolean dbSuccess = auctionDAO.updateBidTransaction(auctionId, userId, bidAmount);
                boolean dbSuccess = true; // Giả lập DB trả về thành công

                if (dbSuccess) {
                    // 3. Update State
                    currentPrice.set(bidAmount);
                    currentWinnerId = userId;

                    // 4. Realtime Broadcast Notification (Bắn cho tất cả)
                    Message broadcastMsg = new Message(ResponseCode.NEW_BID_UPDATE, "Giá mới: " + bidAmount, bidAmount);
                    for (ClientHandler viewer : viewers) {
                        viewer.sendMessage(broadcastMsg);
                    }

                    handler.sendMessage(new Message(ResponseCode.BID_SUCCESS, "Bạn đã dẫn đầu!", null));
                } else {
                    // Rollback strategy if DB fails
                    handler.sendMessage(new Message(ResponseCode.BID_FAILED, "Lỗi giao dịch từ máy chủ", null));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void destroyRoom() {
        roomQueueProcessor.shutdownNow();
        viewers.clear();
    }
}