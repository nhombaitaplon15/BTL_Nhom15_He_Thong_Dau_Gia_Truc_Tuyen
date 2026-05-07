package com.auction.common.model;

public class Admin extends User {
    private double escrowBalance;       // ví tạm
    private double systemRevenue;       // doanh thu hệ thống
<<<<<<< HEAD
    public Admin(int id, String name, String email, String password, String phone, String status, double balance) {
        super( id,name, email, password, phone, status, "ADMIN", 0);
=======
    public Admin(int id, String name, String email, String password, String phone, String status) {
        super( id,name, email, password, phone, status, "ADMIN");
>>>>>>> 67c68da03d601caff64228481442a69f17c843fc
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