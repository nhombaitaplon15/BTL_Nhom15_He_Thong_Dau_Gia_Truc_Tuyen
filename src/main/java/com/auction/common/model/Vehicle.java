package com.auction.common.model;

public class Vehicle extends Items implements java.io.Serializable{
    public Vehicle(int id,String producer,int startPrice, String description, String name, String imgItem){
        super(id, producer, startPrice, description, name, imgItem);
    }
}
