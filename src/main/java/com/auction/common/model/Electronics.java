package com.auction.common.model;

public class Electronics extends Items implements java.io.Serializable{
    protected int date;
    protected int warrantyExpiryDate;
    public Electronics (int id,String producer,int startPrice, String description, String name, String imgItem, int date, int warrantyExpiryDate){
        super(id, producer, startPrice, description, name, imgItem);
        this.date= date;
        this.warrantyExpiryDate= warrantyExpiryDate;
    }
}

