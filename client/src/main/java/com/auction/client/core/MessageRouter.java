package com.auction.client.core;

import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * MessageRouter — ĐÃ SỬA CRITICAL BUG:
 *
 * ❌ LỖI CŨ: handlers là Map<ResponseCode, Consumer> — chỉ giữ 1 handler per code.
 *    Khi nhiều màn hình cùng lắng nghe 1 ResponseCode (ví dụ WINNER_NOTIFICATION
 *    được đăng ký ở AuctionRoomController, BiddingHistoryController, AuctionDetailController),
 *    controller nào register() SAU CÙNG sẽ OVERRIDE hoàn toàn controller trước.
 *    → Chỉ 1 trong 3 controller nhận được message, 2 cái kia bị câm hoàn toàn.
 *
 * ✅ ĐÃ SỬA: handlers là Map<ResponseCode, List<Consumer>> — hỗ trợ multi-listener.
 *    - register(): thêm vào list (không override)
 *    - unregister(code, handler): xóa đúng handler theo reference
 *    - unregister(code): xóa toàn bộ handler của code đó (backward-compat)
 *    - route(): gọi toàn bộ handler trong list theo thứ tự đăng ký
 *
 * Cách dùng (không đổi với code hiện tại):
 *   router.register(ResponseCode.WINNER_NOTIFICATION, this::handleWinner);
 *   router.unregister(ResponseCode.WINNER_NOTIFICATION, this::handleWinner); // xóa đúng handler
 *   router.unregister(ResponseCode.WINNER_NOTIFICATION);                      // xóa tất cả
 */
public class MessageRouter {
    private static final MessageRouter instance = new MessageRouter();

    // Multi-listener: 1 code → nhiều handler
    private final Map<ResponseCode, List<Consumer<Message>>> handlers = new ConcurrentHashMap<>();

    private MessageRouter() {}
    public static MessageRouter getInstance() { return instance; }

    /**
     * Đăng ký handler cho ResponseCode.
     * Nhiều controller có thể đăng ký cùng 1 code — tất cả đều nhận được message.
     */
    public void register(ResponseCode code, Consumer<Message> handler) {
        handlers.computeIfAbsent(code, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Hủy đăng ký 1 handler cụ thể theo object reference.
     * Dùng khi controller đóng: router.unregister(code, this::handleXxx)
     *
     * ⚠️ Lưu ý: phải giữ reference đến lambda/method-ref, không tạo mới inline.
     *    Sai:  router.unregister(code, this::handleXxx)  // lambda mới, không match
     *    Đúng: lưu field: Consumer<Message> myHandler = this::handleXxx;
     *          router.register(code, myHandler);
     *          router.unregister(code, myHandler);
     *
     * Hoặc dùng unregister(code) để xóa tất cả handler của code đó (đơn giản hơn,
     * nhưng sẽ xóa cả handler của màn hình khác đang mở).
     */
    public void unregister(ResponseCode code, Consumer<Message> handler) {
        List<Consumer<Message>> list = handlers.get(code);
        if (list != null) {
            list.remove(handler);
            if (list.isEmpty()) handlers.remove(code);
        }
    }

    /**
     * Hủy đăng ký TOÀN BỘ handler của 1 ResponseCode.
     * Backward-compatible với code hiện tại (controller gọi unregister(code) khi đóng màn hình).
     * Dùng an toàn khi biết chắc chỉ có 1 màn hình đang lắng nghe code đó.
     */
    public void unregister(ResponseCode code) {
        handlers.remove(code);
    }

    /**
     * Định tuyến message tới TẤT CẢ handler đã đăng ký cho code đó.
     * Được gọi bởi SocketClient trên JavaFX Application Thread.
     */
    public void route(Message message) {
        if (message == null || message.getResponseCode() == null) return;
        List<Consumer<Message>> list = handlers.get(message.getResponseCode());
        if (list == null || list.isEmpty()) {
            System.out.println("[ROUTER] Không có handler cho: " + message.getResponseCode());
            return;
        }
        for (Consumer<Message> handler : list) {
            try {
                handler.accept(message);
            } catch (Exception e) {
                System.err.println("[ROUTER] Lỗi handler " + message.getResponseCode() + ": " + e.getMessage());
            }
        }
    }
}


