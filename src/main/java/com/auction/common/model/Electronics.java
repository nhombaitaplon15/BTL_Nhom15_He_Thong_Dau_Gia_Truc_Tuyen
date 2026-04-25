package com.auction.common.model;

public class Electronics extends Items implements java.io.Serializable{
    protected int date;
    protected int warrantyExpiryDate;
    public Electronics (String name,int id,String producer,int price, String show,int date, int warrantyExpiryDate ){
        super(id,producer,price,show,name);
        this.date= date;
        this.warrantyExpiryDate= warrantyExpiryDate;
    }
}

