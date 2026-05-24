package server.core;

import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
// import com.auction.server.dao.UserDAO;

public class RequestDispatcher {
    private static final RequestDispatcher instance = new RequestDispatcher();

    private RequestDispatcher() {}
    public static RequestDispatcher getInstance() { return instance; }

    /**
     * Hàm điều hướng trung tâm. Bắt mọi Exception để không làm chết Client Thread.
     */
    public void dispatch(ClientHandler client, Message request) {
        try {
            switch (request.getRequestCode()) {
                case LOGIN:
                    handleLogin(client, request);
                    break;
                case JOIN_ROOM:
                    handleJoinRoom(client, request);
                    break;
                case LEAVE_ROOM:
                    handleLeaveRoom(client, request);
                    break;
                case PLACE_BID:
                    handlePlaceBid(client, request);
                    break;
                case CHAT_MESSAGE:
                    handleChat(client, request);
                    break;
                default:
                    System.out.println("[DISPATCHER] Unknown request code: " + request.getRequestCode());
            }
        } catch (Exception e) {
            System.err.println("[DISPATCHER ERROR] Lỗi khi xử lý request: " + e.getMessage());
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi máy chủ nội bộ!", null));
        }
    }

    private void handleLogin(ClientHandler client, Message request) {
        // Giả lập DTO được gửi lên
        // LoginDTO loginData = (LoginDTO) request.getPayload();
        // User user = UserDAO.login(loginData.getUsername(), loginData.getPassword());

        int mockUserId = 1; // Giả sử query DB thành công trả về userId = 1
        boolean success = true;

        if (success) {
            boolean isRegistered = SessionManager.getInstance().loginUser(mockUserId, client);
            if (isRegistered) {
                client.sendMessage(new Message(ResponseCode.LOGIN_SUCCESS, "Đăng nhập thành công", mockUserId));
            } else {
                client.sendMessage(new Message(ResponseCode.LOGIN_FAILED, "Tài khoản đang được đăng nhập ở nơi khác", null));
            }
        } else {
            client.sendMessage(new Message(ResponseCode.LOGIN_FAILED, "Sai tài khoản hoặc mật khẩu", null));
        }
    }

    private void handleJoinRoom(ClientHandler client, Message request) {
        Integer roomId = (Integer) request.getPayload();
        AuctionRoom room = AuctionRoomManager.getInstance().getRoom(roomId);

        if (room != null) {
            room.joinRoom(client);
            client.sendMessage(new Message(ResponseCode.ROOM_JOIN_SUCCESS, "Đã vào phòng", roomId));
        } else {
            client.sendMessage(new Message(ResponseCode.ROOM_JOIN_FAILED, "Phòng đấu giá không tồn tại hoặc đã đóng", null));
        }
    }

    private void handleLeaveRoom(ClientHandler client, Message request) {
        Integer roomId = (Integer) request.getPayload();
        AuctionRoom room = AuctionRoomManager.getInstance().getRoom(roomId);
        if (room != null) room.leaveRoom(client);
    }

    private void handlePlaceBid(ClientHandler client, Message request) {
        Integer userId = client.getLoggedInUserId();
        if (userId == null) return; // Bảo mật: Chưa login thì không cho Bid

        // Cần 1 DTO chứa {roomId, bidAmount}. Ở đây giả lập lấy mảng Object.
        Object[] payload = (Object[]) request.getPayload();
        int roomId = (int) payload[0];
        double bidAmount = (double) payload[1];

        AuctionRoom room = AuctionRoomManager.getInstance().getRoom(roomId);
        if (room != null) {
            // Ném vào Queue của phòng, THOÁT NGAY LẬP TỨC KHÔNG BLOCKING!
            room.processBid(client, userId, bidAmount);
        } else {
            client.sendMessage(new Message(ResponseCode.BID_FAILED, "Phiên đấu giá đã kết thúc!", null));
        }
    }

    private void handleChat(ClientHandler client, Message request) {
        // Broadcast tin nhắn chat trong AuctionRoom
        // Tương tự handlePlaceBid, lấy room và gọi room.broadcastChat(...)
    }
}
