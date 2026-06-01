package com.auction.client.core;

import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MessageRouter {
    private static final MessageRouter instance = new MessageRouter();
    private final Map<ResponseCode, Consumer<Message>> handlers = new ConcurrentHashMap<>();

    private MessageRouter() {}

    public static MessageRouter getInstance() { return instance; }

    public void register(ResponseCode code, Consumer<Message> handler) {
        handlers.put(code, handler);
    }

    public void unregister(ResponseCode code) {
        handlers.remove(code);
    }

    /**
     * Định tuyến message đến đúng handler.
     * Chạy an toàn bất đồng bộ.
     */
    public void route(Message message) {
        if (message == null || message.getResponseCode() == null) return;

        Consumer<Message> handler = handlers.get(message.getResponseCode());
        if (handler != null) {
            // ✅ ĐÃ SỬA: Chỉ bọc Platform.runLater tại thời điểm kích hoạt Handler UI
            // Điều này giúp tách biệt luồng nhận gói tin mạng thô ra khỏi luồng xử lý tương tác chuột.
            Platform.runLater(() -> {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    System.err.println("[ROUTER] Lỗi khi xử lý " + message.getResponseCode() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            System.out.println("[ROUTER] Không có handler cho: " + message.getResponseCode());
        }
    }
}