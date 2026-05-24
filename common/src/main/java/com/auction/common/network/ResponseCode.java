package com.auction.common.network;

/**
 * Enum định nghĩa các loại Response từ Server -> Client.
 * Giúp Client biết cần cập nhật UI nào (JavaFX Platform.runLater)
 */
public enum ResponseCode {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ROOM_LIST_RESULT,   // Trả về danh sách phòng
    ROOM_JOIN_SUCCESS,
    ROOM_JOIN_FAILED,
    NEW_BID_UPDATE,     // (Broadcast) Có người vừa Bid giá mới thành công
    BID_SUCCESS,        // (Cá nhân) Chúc mừng bạn đã dẫn đầu
    BID_FAILED,         // (Cá nhân) Bid hụt (do người khác nhanh tay hơn hoặc lỗi mạng)
    AUCTION_ENDED,      // (Broadcast) Phiên đấu giá kết thúc
    CHAT_BROADCAST,     // (Broadcast) Tin nhắn chat từ người khác
    ERROR_MESSAGE       // Lỗi hệ thống chung
}
