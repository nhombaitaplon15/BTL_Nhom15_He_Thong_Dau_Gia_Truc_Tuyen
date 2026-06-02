package com.auction.common.network;

/**
 * ResponseCode — ĐÃ THÊM:
 *  - WINNER_NOTIFICATION: Server push riêng tới người thắng sau khi phiên kết thúc.
 *    Payload: Object[] {auctionId, finalPrice}
 *    Dùng để hiển thị thông báo 1 lần duy nhất cho winner ngay cả khi không ở
 *    trong phòng đấu giá (đã thoát phòng nhưng vẫn còn kết nối socket).
 *
 * Phiên bản đầy đủ — giữ nguyên tất cả các code cũ.
 */
public enum ResponseCode {
    // ===================== AUTH =====================
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    REGISTER_SUCCESS,
    REGISTER_FAILED,
    AUCTION_STATUS_CHANGED,

    // ===================== BIDDER =====================
    ROOM_LIST_RESULT,
    ROOM_JOIN_SUCCESS,
    ROOM_JOIN_FAILED,
    FETCH_ITEMS_RESULT,
    BID_HISTORY_RESULT,
    AUCTION_DETAIL_RESULT,
    AUCTION_DETAIL_FAILED,
    REJECT_WIN_SUCCESS,
    REJECT_WIN_FAILED,
    NEW_BID_UPDATE,
    BID_SUCCESS,
    BID_FAILED,
    AUCTION_ENDED,
    AUCTION_TIME_EXTENDED,
    CHAT_BROADCAST,
    DEPOSIT_SUCCESS,
    DEPOSIT_FAILED,
    WITHDRAW_SUCCESS,
    WITHDRAW_FAILED,
    PROFILE_RESULT,
    PROFILE_UPDATED,
    PASSWORD_CHANGED,
    PASSWORD_CHANGE_FAILED,
    REPORT_SENT,
    WALLET_UPDATE_RESULT,
    TRANSACTION_HISTORY_RESULT,

    /**
     * ✅ MỚI: Push cá nhân tới người thắng ngay khi phiên kết thúc.
     * Payload: Object[] {Integer auctionId, Double finalPrice, String itemName}
     * Được gửi bởi server trong handleAutoCloseAuction / ManagerService.
     * Client lắng nghe để hiện thông báo 1 lần (Alert) và cập nhật trạng thái
     * dòng trong lịch sử đặt giá thành "WINNER - Chờ xác nhận".
     */
    WINNER_NOTIFICATION,

    // ===================== SELLER =====================
    SELLER_ITEMS_RESULT,
    SELLER_AUCTION_CREATED,
    SELLER_AUCTION_CREATE_FAILED,
    SELLER_AUCTIONS_RESULT,
    SELLER_CANCEL_SUCCESS,
    SELLER_CANCEL_FAILED,
    SELLER_CONFIRM_SALE_SUCCESS,
    SELLER_CONFIRM_SALE_FAILED,
    SELLER_AUCTION_APPROVED,
    SELLER_AUCTION_REJECTED,
    SELLER_AUCTION_SOLD,

    // ===================== ADMIN =====================
    ADMIN_ALL_AUCTIONS_RESULT,
    ADMIN_APPROVE_SUCCESS,
    ADMIN_APPROVE_FAILED,
    ADMIN_REJECT_SUCCESS,
    ADMIN_REJECT_FAILED,
    ADMIN_BLOCK_SUCCESS,
    ADMIN_BLOCK_FAILED,
    ADMIN_ALL_TRANSACTIONS_RESULT,
    ADMIN_TRANSACTION_APPROVED,
    ADMIN_TRANSACTION_REJECTED,
    ADMIN_TRANSACTION_CREATED,
    ADMIN_TRANSACTION_FAILED,
    ADMIN_USERS_RESULT,
    ADMIN_BAN_SUCCESS,
    ADMIN_UNBAN_SUCCESS,
    ADMIN_NEW_PENDING_AUCTION,
    ADMIN_AUCTION_APPROVED,
    ADMIN_AUCTION_REJECTED,
    ERROR_MESSAGE,
    SELLER_EDIT_SUCCESS,
    SELLER_EDIT_FAILED,
    ADMIN_DELETE_BLOCKED_SUCCESS,

    // ===================== ISSUES / REPORTS =====================
    REPORT_ISSUE_SUCCESS,
    REPORT_ISSUE_FAILED,
    ADMIN_ISSUES_RESULT,
    ADMIN_NEW_ISSUE
}