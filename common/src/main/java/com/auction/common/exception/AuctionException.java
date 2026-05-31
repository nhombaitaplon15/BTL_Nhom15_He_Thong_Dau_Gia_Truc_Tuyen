package com.auction.common.exception;


public class AuctionException extends RuntimeException { // lớp ngoại lệ tùy chỉnh của hệ thống đấu giá
    private final String code; // mã lỗi

    public AuctionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}