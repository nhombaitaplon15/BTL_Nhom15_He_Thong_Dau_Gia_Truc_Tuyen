package com.auction.common.model;

public class Electronics extends Items implements java.io.Serializable{
    protected String date;
    protected int warrantyExpiryDate;
    protected String brand;
    protected String condition;
    public Electronics (String name,int id,String producer,int price, String show,String imgitem, String date, int warrantyExpiryDate, String brand, String condition ){
        super(id,producer,price,show,name, imgitem);
        this.date= date;
        this.warrantyExpiryDate= warrantyExpiryDate;
        this.brand= brand;
        this.condition= condition;
    }
    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }

    public String getBrand() {
        return brand;
    }
    public void setBrand(String brand) {
        this.brand = brand;
    }

    public int getWarrantyExpiryDate() {
        return warrantyExpiryDate;
    }
    public void setWarrantyExpiryDate(int warrantyExpiryDate) {
        this.warrantyExpiryDate = warrantyExpiryDate;
    }

    public String getCondition() {
        return condition;
    }
    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getWarrantyStatus() {
        if (warrantyExpiryDate > 0) {
            return "Còn bảo hành: ";
        }
        return "Hết bảo hành";
    }
}



