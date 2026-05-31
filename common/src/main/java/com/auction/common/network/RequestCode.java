package com.auction.common.network;

/**
 * Enum định nghĩa các loại Request từ Client -> Server.
 *
 * PHIÊN BẢN ĐẦY ĐỦ (Bidder + Seller + Admin):
 *
 * ĐẶT TẠI: common/src/main/java/com/auction/common/network/RequestCode.java
 */
public enum RequestCode {
    // ===================== SHARED =====================
    PING,               // Heartbeat giữ connection

    // ===================== AUTH =====================
    LOGIN,              // Đăng nhập (payload: LoginDTO)
    REGISTER,           // Đăng ký (payload: LoginDTO)

    // ===================== BIDDER =====================
    FETCH_ROOMS,        // Lấy danh sách phòng đấu giá đang mở
    FETCH_ITEMS,        // Lấy items theo danh mục phòng (payload: String category)
    JOIN_ROOM,          // Vào phòng (payload: Integer auctionId)
    LEAVE_ROOM,         // Rời phòng (payload: Integer auctionId)
    PLACE_BID,          // Đặt giá (payload: BidPlaceDTO)
    CHAT_MESSAGE,       // Chat trong phòng (payload: String message)
    FETCH_BID_HISTORY,  // Lịch sử đặt giá của user (payload: Integer userId)
    DEPOSIT_REQUEST,    // Nạp tiền vào ví (payload: Double amount)
    WITHDRAW_REQUEST,   // Rút tiền từ ví (payload: Double amount)
    GET_PROFILE,        // Lấy thông tin cá nhân (payload: null)
    UPDATE_PROFILE,     // Cập nhật thông tin cá nhân (payload: User)
    CHANGE_PASSWORD,    // Đổi mật khẩu (payload: String[] {oldPwd, newPwd})
    REPORT_ISSUE,       // Gửi báo cáo sự cố (payload: String message)

    // ===================== SELLER =====================
    SELLER_GET_MY_ITEMS,       // Lấy danh sách item của seller (payload: null)
    SELLER_CREATE_AUCTION,     // Tạo yêu cầu phiên đấu giá (payload: CreateAuctionDTO)
    SELLER_GET_MY_AUCTIONS,    // Lấy danh sách phiên đấu giá của seller (payload: null)
    SELLER_CANCEL_AUCTION,     // Yêu cầu hủy phiên (payload: Integer auctionId)
    SELLER_CONFIRM_SALE,       // Xác nhận bán sau khi phiên kết thúc (payload: Integer auctionId)
    SELLER_ADD_ITEM,           // Thêm sản phẩm mới (payload: Item object)
    SELLER_EDIT_AUCTION,       // SỬA: Yêu cầu sửa phiên đấu giá (payload: Auction)
    // ===================== ADMIN =====================
    ADMIN_GET_ALL_AUCTIONS,    // Lấy tất cả phiên đấu giá (payload: null)
    ADMIN_APPROVE_AUCTION,     // Duyệt phiên (payload: Integer auctionId)
    ADMIN_REJECT_AUCTION,      // Từ chối phiên (payload: Object[] {auctionId, reason})
    ADMIN_BLOCK_AUCTION,       // Phong tỏa phiên đang chạy (payload: Integer auctionId)
    ADMIN_GET_ALL_TRANSACTIONS,// Lấy tất cả giao dịch (payload: null)
    ADMIN_APPROVE_TRANSACTION, // Duyệt giao dịch (payload: Integer transactionId)
    ADMIN_REJECT_TRANSACTION,  // Từ chối giao dịch (payload: Integer transactionId)
    ADMIN_CREATE_TRANSACTION,  // Tạo giao dịch từ phiên (payload: Object[] {auctionId, winnerId, price})
    ADMIN_GET_ALL_USERS,       // Lấy danh sách users (payload: null)
    ADMIN_BAN_USER,            // Ban user (payload: Integer userId)
    ADMIN_UNBAN_USER           // Unban user (payload: Integer userId)
}