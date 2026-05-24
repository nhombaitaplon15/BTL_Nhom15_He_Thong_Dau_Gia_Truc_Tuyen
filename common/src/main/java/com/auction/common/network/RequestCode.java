package com.auction.common.network;

/**
 * Enum định nghĩa các loại Request từ Client -> Server.
 * Giúp RequestDispatcher phân loại và định tuyến chính xác.
 */
public enum RequestCode {
    PING,               // Heartbeat giữ connection
    LOGIN,              // Yêu cầu đăng nhập
    REGISTER,           // Yêu cầu đăng ký
    FETCH_ROOMS,        // Lấy danh sách các phòng đấu giá đang mở
    JOIN_ROOM,          // Yêu cầu tham gia phòng đấu giá
    LEAVE_ROOM,         // Rời phòng đấu giá
    PLACE_BID,          // Đặt giá (Bid)
    CHAT_MESSAGE        // Gửi tin nhắn realtime trong phòng
}
