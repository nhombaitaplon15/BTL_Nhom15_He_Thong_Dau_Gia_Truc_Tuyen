package com.auction.common.model;

public class Vehicle extends Items implements java.io.Serializable{
    public Vehicle (int id,String producer,int price,String show,String name,String imgitem){
        super(id,producer,price,show,name, imgitem);
    }
}
