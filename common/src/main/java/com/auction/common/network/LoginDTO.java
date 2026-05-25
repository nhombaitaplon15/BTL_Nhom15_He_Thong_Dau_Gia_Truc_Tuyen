package com.auction.common.network;

import java.io.Serializable;

/**
 * DTO dùng để gói dữ liệu đăng nhập gửi từ Client -> Server.
 * Phải implements Serializable để truyền qua ObjectOutputStream.
 * Đặt tại: common/src/main/java/com/auction/common/network/LoginDTO.java
 */
public class LoginDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;

    public LoginDTO(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
}
