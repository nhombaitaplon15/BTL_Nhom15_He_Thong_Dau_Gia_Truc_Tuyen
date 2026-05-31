package com.auction.common.exception;

// thiết lập các mã lỗi
public enum ErrorCode {

    // auction
    AUCTION_NOT_FOUND,
    AUCTION_INVALID_STATE,
    AUCTION_ALREADY_ENDED,

    // bidder
    BID_TOO_LOW,
    INVALID_BID,

    // item
    INVALID_ITEM,
    ITEM_DUPLICATE,
    ITEM_NOT_FOUND,

    // user
    USER_NOT_FOUND,
    UNAUTHORIZED,

    // system
    CONCURRENCY,
    INVALID_INPUT,
    INTERNAL_ERROR, // Thêm dấu phẩy ở đây

    // transaction (Thêm mới cho nạp/rút)

    TRANSACTION_FAILED,
    INSUFFICIENT_BALANCE
}