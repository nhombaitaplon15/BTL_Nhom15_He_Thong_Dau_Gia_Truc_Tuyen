package com.auction.exception;
//ĐỂ ĐỊNH NGHĨA NHỮNG LỖI GẶP TRONG HỆ THỐNG
public enum ErrorCode {

    // Auction
    AUCTION_NOT_FOUND,
    AUCTION_INVALID_STATE,
    AUCTION_ALREADY_ENDED,

    // Bid
    BID_TOO_LOW,
    INVALID_BID,

    // Item
    INVALID_ITEM,
    ITEM_DUPLICATE,
    ITEM_NOT_FOUND,

    // User
    USER_NOT_FOUND,
    UNAUTHORIZED,

    // System
    CONCURRENCY,
    INVALID_INPUT
}