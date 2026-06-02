package com.auction.common.network;

/**
 * Enum định nghĩa các loại Response từ Server -> Client.
 *
 * PHIÊN BẢN ĐẦY ĐỦ (Bidder + Seller + Admin).
 * Mỗi ResponseCode là một sự kiện mà MessageRouter sẽ định tuyến
 * đến đúng Controller đang lắng nghe.
 *
 * ĐẶT TẠI: common/src/main/java/com/auction/common/network/ResponseCode.java
 */
public enum ResponseCode {
    // ===================== AUTH =====================
    LOGIN_SUCCESS,          // payload: User object (đã gán role)
    LOGIN_FAILED,           // payload: null
    REGISTER_SUCCESS,       // payload: null
    REGISTER_FAILED,        // payload: null
    FORGOT_PASSWORD_SUCCESS,// payload: null
    FORGOT_PASSWORD_FAILED, // payload: String reason
    AUCTION_STATUS_CHANGED,
    SWITCH_ROLE_SUCCESS,
    SWITCH_ROLE_FAILED,

    // ===================== BIDDER =====================
    ROOM_LIST_RESULT,           // payload: List<Auction>
    ROOM_JOIN_SUCCESS,          // payload: Integer auctionId
    ROOM_JOIN_FAILED,           // payload: null
    FETCH_ITEMS_RESULT,         // payload: List<Item>
    BID_HISTORY_RESULT,         // payload: List<BidHistoryRow>
    NEW_BID_UPDATE,             // (Broadcast) payload: Object[] {auctionId, newPrice, winnerUsername}
    BID_SUCCESS,                // (Cá nhân) payload: Double newPrice
    BID_FAILED,                 // (Cá nhân) payload: String reason
    AUCTION_ENDED,              // (Broadcast) payload: Object[] {auctionId, winnerUsername, finalPrice}
    AUCTION_TIME_EXTENDED,      // (Broadcast) Anti-sniping: payload: Object[] {auctionId, newEndTime}
    CHAT_BROADCAST,             // (Broadcast) payload: String "username: message"
    DEPOSIT_SUCCESS,            // payload: Double newBalance
    DEPOSIT_FAILED,             // payload: null
    WITHDRAW_SUCCESS,           // payload: Double newBalance
    WITHDRAW_FAILED,            // payload: null
    PROFILE_RESULT,             // payload: User
    PROFILE_UPDATED,            // payload: null
    PASSWORD_CHANGED,           // payload: null
    PASSWORD_CHANGE_FAILED,     // payload: null
    REPORT_SENT,                // payload: null
    TRANSACTIONS_RESULT,        // payload: List<TransactionRequest> (Lịch sử giao dịch cá nhân)
    AUCTION_DETAIL_RESULT,      // payload: AuctionItemDTO
    BIDDER_PAY_SUCCESS,         // payload: null
    BIDDER_PAY_FAILED,          // payload: String reason
    BIDDER_CANCEL_SUCCESS,      // payload: null
    BIDDER_CANCEL_FAILED,       // payload: String reason
    WINNER_NOTIFICATION,

    // ===================== SELLER =====================
    SELLER_ITEMS_RESULT,        // payload: List<Item> (items của seller)
    SELLER_AUCTION_CREATED,     // payload: Integer auctionId vừa tạo
    SELLER_AUCTION_CREATE_FAILED,// payload: String reason
    SELLER_AUCTIONS_RESULT,     // payload: List<Auction> (phiên của seller)
    SELLER_CANCEL_SUCCESS,      // payload: null
    SELLER_CANCEL_FAILED,       // payload: String reason
    SELLER_CONFIRM_SALE_SUCCESS,// payload: null
    SELLER_CONFIRM_SALE_FAILED, // payload: String reason
    SELLER_AUCTION_APPROVED,    // (Push) payload: Integer auctionId
    SELLER_AUCTION_REJECTED,    // (Push) payload: Object[] {auctionId, reason}
    SELLER_AUCTION_SOLD,        // (Push) payload: Object[] {auctionId, finalPrice, buyerName}
    SELLER_EDIT_SUCCESS,
    SELLER_EDIT_FAILED,

    // ===================== ADMIN =====================
    ADMIN_ALL_AUCTIONS_RESULT,  // payload: List<Auction>
    ADMIN_APPROVE_SUCCESS,      // payload: Integer auctionId
    ADMIN_APPROVE_FAILED,       // payload: String reason
    ADMIN_REJECT_SUCCESS,       // payload: Integer auctionId
    ADMIN_REJECT_FAILED,        // payload: String reason
    ADMIN_BLOCK_SUCCESS,        // payload: Integer auctionId
    ADMIN_BLOCK_FAILED,         // payload: String reason
    ADMIN_ALL_TRANSACTIONS_RESULT,  // payload: List<TransactionRequest>
    ADMIN_TRANSACTION_APPROVED,     // payload: Integer transactionId
    ADMIN_TRANSACTION_REJECTED,     // payload: Integer transactionId
    ADMIN_TRANSACTION_CREATED,      // payload: null
    ADMIN_TRANSACTION_FAILED,       // payload: String reason
    ADMIN_USERS_RESULT,             // payload: List<User>
    ADMIN_BAN_SUCCESS,              // payload: Integer userId
    ADMIN_UNBAN_SUCCESS,            // payload: Integer userId
    ADMIN_NEW_PENDING_AUCTION,      // (Broadcast to Admin) payload: Auction
    ADMIN_AUCTION_APPROVED,
    ADMIN_AUCTION_REJECTED,
    ADMIN_DELETE_BLOCKED_SUCCESS,   // payload: Integer auctionId (đã xóa)

    // ===================== ERROR =====================
    ERROR_MESSAGE,                  // payload: String errorDetail

    // ===================== ISSUES / REPORTS (Thêm mới từ đoạn code) =====================
    REPORT_ISSUE_SUCCESS,
    REPORT_ISSUE_FAILED,
    ADMIN_ISSUES_RESULT,
    ADMIN_NEW_ISSUE
}