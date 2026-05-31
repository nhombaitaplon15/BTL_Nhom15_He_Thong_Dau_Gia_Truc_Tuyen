package com.auction.common.network;



import java.io.Serializable;

public class RegisterDTO implements Serializable {

    private String username;
    private String password;
    private String email;
    private String phone;
    private String role;

    public RegisterDTO(String username,
                       String password,
                       String email,
                       String phone,
                       String role) {

        this.username = username;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }
}
