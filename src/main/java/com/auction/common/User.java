package com.auction.common;

public abstract class User extends Entity {
    private String name;
    private String email;
    private String password;
    private String phone;
    private String status;
    private String role;// vai tro
    public User(int id, String name, String email, String password, String phone, String status, String role) {
        super(id);
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.status = status;
        this.role = role;
    }

    // Các Getters và Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; } // Đã thêm setter này

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
