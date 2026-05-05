package com.auction.common.model;

// Kế thừa lớp User
public class Admin extends User {
    private double escrowBalance;       // ví tạm
    private double systemRevenue;       // doanh thu hệ thống
    // Constructor của Admin
    public Admin(int id, String name, String email, String password, String phone, String status) {
        super( id,name, email, password, phone, status, "ADMIN");
        this.escrowBalance = 0;
        this.systemRevenue = 0;
    }

    public double getEscrowBalance() {
        return escrowBalance;
    }

    public void setEscrowBalance(double escrowBalance) {
        this.escrowBalance = escrowBalance;
    }

    public double getSystemRevenue() {
        return systemRevenue;
    }

    public void setSystemRevenue(double systemRevenue) {
        this.systemRevenue = systemRevenue;
    }

}