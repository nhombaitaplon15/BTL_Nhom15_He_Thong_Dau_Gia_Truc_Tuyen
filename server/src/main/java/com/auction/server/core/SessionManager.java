package src.main.java.com.auction.server.core;

import com.auction.common.model.User;
import com.auction.common.network.Message;
import src.main.java.com.auction.server.service.UserService;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionManager - Quản lý toàn bộ session đang online.
 *
 * PHIÊN BẢN ĐẦY ĐỦ: Thêm các phương thức phục vụ Seller và Admin:
 *  - broadcastToAdmins(): Push thông báo cho tất cả Admin đang online
 *  - sendToUserIfOnline(): Push thông báo cá nhân (ví dụ báo Seller phiên được duyệt)
 *  - forceLogout(): Kick user bị ban ra khỏi hệ thống
 *
 * ĐẶT TẠI: server/src/main/java/com/auction/server/core/SessionManager.java
 */
public class SessionManager {
    private static final SessionManager instance = new SessionManager();

    // Lưu toàn bộ connection vật lý (kể cả chưa login)
    private final Set<ClientHandler> activeConnections = ConcurrentHashMap.newKeySet();

    // UserId -> ClientHandler (chỉ user đã login thành công)
    private final ConcurrentHashMap<Integer, ClientHandler> loggedInUsers = new ConcurrentHashMap<>();

    private SessionManager() {}
    public static SessionManager getInstance() { return instance; }

    // =========================================================
    // CONNECTION LIFECYCLE
    // =========================================================

    public void registerConnection(ClientHandler handler) {
        activeConnections.add(handler);
    }

    public void removeConnection(ClientHandler handler) {
        activeConnections.remove(handler);
        Integer userId = handler.getLoggedInUserId();
        if (userId != null) {
            loggedInUsers.remove(userId);
            System.out.println("[SESSION] User#" + userId + " đã ngắt kết nối và được dọn khỏi session.");
        }
    }

    // =========================================================
    // LOGIN / LOGOUT
    // =========================================================

    /**
     * Chống Double-Login: 1 tài khoản chỉ được đăng nhập 1 nơi.
     * Dùng putIfAbsent để thread-safe.
     */
    public boolean loginUser(int userId, ClientHandler handler) {
        ClientHandler existing = loggedInUsers.putIfAbsent(userId, handler);
        if (existing != null) {
            // Từ chối login mới (chiến lược Pessimistic - giữ session cũ)
            return false;
        }
        handler.setLoggedInUserId(userId);
        System.out.println("[SESSION] User#" + userId + " đã đăng nhập.");
        return true;
    }

    public void removeSession(int userId) {
        loggedInUsers.remove(userId);
    }

    /**
     * [THÊM] Force-kick user bị Admin ban: đóng connection ngay lập tức.
     */
    public void forceLogout(int userId) {
        ClientHandler handler = loggedInUsers.remove(userId);
        if (handler != null) {
            handler.sendMessage(new Message(
                    com.auction.common.network.ResponseCode.ERROR_MESSAGE,
                    "Tài khoản của bạn đã bị khóa bởi Admin!", null));
            handler.cleanUp(); // Đóng socket
            System.out.println("[SESSION] User#" + userId + " đã bị force-logout.");
        }
    }

    // =========================================================
    // QUERY
    // =========================================================

    public ClientHandler getConnectionByUserId(int userId) {
        return loggedInUsers.get(userId);
    }

    public Collection<ClientHandler> getAllConnections() {
        return activeConnections;
    }

    // =========================================================
    // BROADCAST UTILITIES
    // =========================================================

    /**
     * Broadcast toàn bộ user đang online (bất kể role).
     * Dùng cho: thông báo server bảo trì, sự kiện toàn hệ thống.
     */
    public void broadcastGlobal(Message message) {
        for (ClientHandler handler : loggedInUsers.values()) {
            handler.sendMessage(message);
        }
    }

    /**
     * [THÊM] Gửi thông báo tới 1 user cụ thể nếu đang online.
     * Dùng cho: Push "phiên của bạn được duyệt" đến Seller.
     *
     * @param userId  ID của user cần nhận
     * @param message Message cần gửi
     */
    public void sendToUserIfOnline(int userId, Message message) {
        ClientHandler handler = loggedInUsers.get(userId);
        if (handler != null) {
            handler.sendMessage(message);
            System.out.println("[SESSION] Push tới User#" + userId + ": " + message.getResponseCode());
        }
        // Nếu offline thì bỏ qua (có thể mở rộng lưu notification DB)
    }

    /**
     * [THÊM] Broadcast tới tất cả Admin đang online.
     * Dùng cho: Seller tạo phiên mới -> thông báo ngay cho Admin duyệt.
     *
     * @param message     Message cần gửi
     * @param userService Để tra cứu role của từng user đang online
     */
    public void broadcastToAdmins(Message message, UserService userService) {
        int count = 0;
        for (java.util.Map.Entry<Integer, ClientHandler> entry : loggedInUsers.entrySet()) {
            try {
                User user = userService.getUserById(entry.getKey());
                if (user != null && "ADMIN".equalsIgnoreCase(user.getRole())) {
                    entry.getValue().sendMessage(message);
                    count++;
                }
            } catch (Exception e) {
                System.err.println("[SESSION] Lỗi khi broadcast đến Admin#" + entry.getKey() + ": " + e.getMessage());
            }
        }
        System.out.println("[SESSION] Broadcast Admin (" + count + " admin online): " + message.getResponseCode());
    }
}