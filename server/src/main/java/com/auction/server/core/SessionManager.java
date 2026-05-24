package com.auction.server.core;

import com.auction.common.network.Message;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final SessionManager instance = new SessionManager();

    // Lưu trữ toàn bộ các connection vật lý (chưa login và đã login)
    private final Set<ClientHandler> activeConnections = ConcurrentHashMap.newKeySet();

    // Ánh xạ UserId -> ClientHandler (Chỉ lưu những user đã login thành công)
    private final ConcurrentHashMap<Integer, ClientHandler> loggedInUsers = new ConcurrentHashMap<>();

    private SessionManager() {}
    public static SessionManager getInstance() { return instance; }

    public void registerConnection(ClientHandler handler) {
        activeConnections.add(handler);
    }

    public void removeConnection(ClientHandler handler) {
        activeConnections.remove(handler);
    }

    /**
     * Logic Login chống Double-Login (1 tài khoản đăng nhập 2 nơi).
     * Thread-safe bằng cách dùng putIfAbsent.
     */
    public boolean loginUser(int userId, ClientHandler handler) {
        // Kiểm tra xem User này đã đăng nhập ở đâu chưa
        ClientHandler existingHandler = loggedInUsers.putIfAbsent(userId, handler);
        if (existingHandler != null) {
            // Trường hợp tài khoản đang được dùng ở máy khác
            // Tùy chiến lược: Ở đây ta chọn từ chối đăng nhập mới (Pessimistic)
            // Hoặc có thể đá văng connection cũ (existingHandler.cleanUp())
            return false;
        }

        handler.setLoggedInUserId(userId);
        return true;
    }

    public void removeSession(int userId) {
        loggedInUsers.remove(userId);
    }

    public ClientHandler getConnectionByUserId(int userId) {
        return loggedInUsers.get(userId);
    }

    public Collection<ClientHandler> getAllConnections() {
        return activeConnections;
    }

    /**
     * Hàm tiện ích: Bắn thông báo Global cho toàn server (Ví dụ: Server sắp bảo trì)
     */
    public void broadcastGlobal(Message message) {
        for (ClientHandler handler : loggedInUsers.values()) {
            handler.sendMessage(message);
        }
    }
}