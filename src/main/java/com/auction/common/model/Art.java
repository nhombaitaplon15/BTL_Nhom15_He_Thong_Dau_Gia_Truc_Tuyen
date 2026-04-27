package com.auction.common.model;

class Art extends Items implements java.io.Serializable{
    public Art (int id,String producer,int startPrice, String description, String name, String imgItem){
        super(id, producer, startPrice, description, name, imgItem);
    }
}
