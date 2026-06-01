package com.auction.common.network;

/**
 * Enum định nghĩa các loại Response từ Server -> Client.
 *
 * PHIÊN BẢN ĐẦY ĐỦ (Bidder + Seller + Admin).
 *
 * ĐẶT TẠI: common/src/main/java/com/auction/common/network/ResponseCode.java
 */
public enum ResponseCode {
    // ===================== AUTH =====================
    LOGIN_SUCCESS,          // payload: User object (đã gán role)
    LOGIN_FAILED,           // payload: null
    REGISTER_SUCCESS,       // payload: null
    REGISTER_FAILED,        // payload: null
    AUCTION_STATUS_CHANGED,

    // ===================== BIDDER =====================
    ROOM_LIST_RESULT,           // payload: List<Auction>
    ROOM_JOIN_SUCCESS,          // payload: Auction (full data)
    ROOM_JOIN_FAILED,           // payload: null
    FETCH_ITEMS_RESULT,         // payload: List<Auction>
    BID_HISTORY_RESULT,         // payload: List<BidHistoryRow>
    AUCTION_DETAIL_RESULT,      // ✅ MỚI: payload: Auction (chi tiết 1 phiên kèm Item)
    AUCTION_DETAIL_FAILED,      // ✅ MỚI: payload: String reason
    REJECT_WIN_SUCCESS,         // ✅ MỚI: Hủy kèo thành công (payload: null)
    REJECT_WIN_FAILED,          // ✅ MỚI: Hủy kèo thất bại (payload: String reason)
    NEW_BID_UPDATE,             // (Broadcast) payload: Object[] {auctionId, newPrice, winnerUsername}
    BID_SUCCESS,                // (Cá nhân) payload: Double newPrice
    BID_FAILED,                 // (Cá nhân) payload: String reason
    AUCTION_ENDED,              // (Broadcast) payload: Object[] {auctionId, winnerUsername, finalPrice}
    AUCTION_TIME_EXTENDED,      // (Broadcast) Anti-sniping: payload: LocalDateTime newEndTime
    CHAT_BROADCAST,             // (Broadcast) payload: String "username: message"
    DEPOSIT_SUCCESS,            // payload: null
    DEPOSIT_FAILED,             // payload: null
    WITHDRAW_SUCCESS,           // payload: null
    WITHDRAW_FAILED,            // payload: null
    PROFILE_RESULT,             // payload: User
    PROFILE_UPDATED,            // payload: null
    PASSWORD_CHANGED,           // payload: null
    PASSWORD_CHANGE_FAILED,     // payload: null
    REPORT_SENT,                // payload: null
    WALLET_UPDATE_RESULT,       // payload: Map<String, Double> {balance, escrow}
    TRANSACTION_HISTORY_RESULT, // payload: List<TransactionRequest>

    // ===================== SELLER =====================
    SELLER_ITEMS_RESULT,        // payload: List<Item>
    SELLER_AUCTION_CREATED,     // payload: Integer auctionId
    SELLER_AUCTION_CREATE_FAILED,
    SELLER_AUCTIONS_RESULT,     // payload: List<AuctionItemDTO>
    SELLER_CANCEL_SUCCESS,
    SELLER_CANCEL_FAILED,
    SELLER_CONFIRM_SALE_SUCCESS,
    SELLER_CONFIRM_SALE_FAILED,
    SELLER_AUCTION_APPROVED,    // (Push) payload: Integer auctionId
    SELLER_AUCTION_REJECTED,    // (Push) payload: Object[] {auctionId, reason}
    SELLER_AUCTION_SOLD,        // (Push) payload: Object[] {auctionId, finalPrice, buyerName}

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
    SELLER_EDIT_SUCCESS, SELLER_EDIT_FAILED, ADMIN_DELETE_BLOCKED_SUCCESS,

    // ===================== ISSUES / REPORTS =====================
    REPORT_ISSUE_SUCCESS,
    REPORT_ISSUE_FAILED,
    ADMIN_ISSUES_RESULT,
    ADMIN_NEW_ISSUE
}