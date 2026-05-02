package com.auction.common.model;

public abstract class User extends Entity implements java.io.Serializable {
    private String username;
    private String email;
    private String password;
    private String phone;
    private String status;
    private String role;// vai tro
    private double balance;
    public User(int id, String name, String email, String password, String phone, String status, String role) {
        super(id);
        this.username = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.status = status;
        this.role = role;
    }

    // Các Getters và Setters
    public String getUsername() { return username; }
    public void setUsername(String name) { this.username = name; }

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

    public double getBalance() { return balance; }
    public void setBalance(double role) { this.balance = balance; }
}