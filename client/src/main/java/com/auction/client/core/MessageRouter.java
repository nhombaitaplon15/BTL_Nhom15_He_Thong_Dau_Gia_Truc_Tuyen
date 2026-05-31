package com.auction.client.core;


import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MessageRouter {
    private static final MessageRouter instance = new MessageRouter();

    // Map: ResponseCode -> Consumer<Message> (handler của Controller)
    private final Map<ResponseCode, Consumer<Message>> handlers = new ConcurrentHashMap<>();

    private MessageRouter() {}

    public static MessageRouter getInstance() { return instance; }

    /**
     * Đăng ký handler để nhận message có responseCode tương ứng.
     * Gọi trong initialize() của Controller.
     *
     * @param code    ResponseCode cần lắng nghe
     * @param handler hàm xử lý (lambda hoặc method reference)
     */
    public void register(ResponseCode code, Consumer<Message> handler) {
        handlers.put(code, handler);
    }

    /**
     * Hủy đăng ký khi Controller đóng/rời màn hình.
     * Tránh memory leak và xử lý nhầm màn hình cũ.
     */
    public void unregister(ResponseCode code) {
        handlers.remove(code);
    }

    /**
     * Định tuyến message đến đúng handler.
     * Được gọi bởi SocketClient.processResponse() trên JavaFX Application Thread.
     */
    public void route(Message message) {
        if (message == null || message.getResponseCode() == null) return;
        Consumer<Message> handler = handlers.get(message.getResponseCode());
        if (handler != null) {
            try {
                handler.accept(message);
            } catch (Exception e) {
                System.err.println("[ROUTER] Lỗi khi xử lý " + message.getResponseCode() + ": " + e.getMessage());
            }
        } else {
            System.out.println("[ROUTER] Không có handler cho: " + message.getResponseCode());
        }
    }
}