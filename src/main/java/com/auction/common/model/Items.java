package com.auction.common.model;

public abstract class Items extends Entity implements java.io.Serializable{
    protected String producer; // nhà sản xuất
    protected double startPrice;
    protected String description;   //mô tả của sản phẩm
    protected String name;
    protected String imgItem;
    protected String itemStatus; // trạng thái của mặt hàng: PENDING -> OPEN -> RUNNING -> FINISHED/UNSOLD -> PAID -> COMPLETED, CANCELED
    public Items (int id,String producer,double startPrice, String description, String name, String imgItem){
        super(id);
        this.producer = producer;
        this.startPrice = startPrice;
        this.description = description;
        this.name = name;
        this.imgItem = imgItem;
        this.itemStatus = "PENDING";
    }
    public String getProducer() {
        return producer;
    }
    public void setProducer(String producer) {
        this.producer = producer;
    }
    public String getName(){
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public double getStartPrice() {
        return startPrice;
    }
    public void setStartPrice(double startPrice) {
        this.startPrice = startPrice;
    }
    public String getDescription(){
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getImgItem(){
        return imgItem;
    }
    public void setImgItem(String imgItem) {this.imgItem = imgItem;}
    public String getItemStatus() {return itemStatus;}
    public void setItemStatus(String itemStatus) {this.itemStatus = itemStatus;}
}