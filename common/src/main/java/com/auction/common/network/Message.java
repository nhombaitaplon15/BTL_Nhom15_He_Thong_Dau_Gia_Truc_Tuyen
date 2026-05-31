package com.auction.common.network;


import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private RequestCode requestCode;
    private ResponseCode responseCode;
    private String message;
    private Object payload; // Chứa Data (User, BidAmount, ItemId...)

    // Dùng cho Client -> Server (Chỉ cần mã lệnh và dữ liệu)
    public Message(RequestCode requestCode, Object payload) {
        this.requestCode = requestCode;
        this.payload = payload;
    }

    // Dùng cho Server -> Client (Cần mã phản hồi, câu thông báo và dữ liệu nếu có)
    public Message(ResponseCode responseCode, String message, Object payload) {
        this.responseCode = responseCode;
        this.message = message;
        this.payload = payload;
    }

    // ==========================================
    // GETTERS VÀ SETTERS (Đã bổ sung đầy đủ)
    // ==========================================

    public RequestCode getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(RequestCode requestCode) {
        this.requestCode = requestCode;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(ResponseCode responseCode) {
        this.responseCode = responseCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}