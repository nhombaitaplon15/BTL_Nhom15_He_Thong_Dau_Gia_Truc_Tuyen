package com.auction.common.model;

public class Electronics extends Items implements java.io.Serializable{

    protected String date; // ngày sản xuất
    protected int warrantyExpiryDate; // ngày hết hạn bảo hành
    protected String brand; // nhãn hiệu
    protected String condition; // tình trạng
    public Electronics (String name,int id,String producer,int startPrice, String description,String imgitem, String date, int warrantyExpiryDate ){
        super(id,producer,startPrice,description,name, imgitem);
        this.date= date;
        this.warrantyExpiryDate= warrantyExpiryDate;
        this.brand= brand;
        this.condition= condition;
    }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getWarrantyExpiryDate() { return warrantyExpiryDate; }
    public void setWarrantyExpiryDate(int warrantyExpiryDate) { this.warrantyExpiryDate = warrantyExpiryDate; }


}