package com.auction.exception;

public class AuctionException extends RuntimeException {
    private final String code;

    public AuctionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}