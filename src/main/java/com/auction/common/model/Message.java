package com.auction.common.model;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L; // Thêm dòng này để tránh lỗi version khi chạy trên GitHub

    private String command; // Lệnh yêu cầu (LOGIN, BID, CREATE...)
    private Object data;    // Dữ liệu đính kèm (User, Auction, List...)
    private String status;  // Trạng thái phản hồi (SUCCESS, FAILED)
    private String note;    // Chú thích hoặc thông báo lỗi

    // Constructor dùng để gửi Request (Client -> Server)
    public Message(String command, Object data) {
        this.command = command;
        this.data = data;
    }

    // Constructor dùng để gửi Response (Server -> Client)
    public Message(String status, String note, Object data) {
        this.status = status;
        this.note = note;
        this.data = data;
    }

    // Getter và Setter đầy đủ
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}