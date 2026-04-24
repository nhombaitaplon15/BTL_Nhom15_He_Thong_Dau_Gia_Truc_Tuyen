package com.auction.common.model;

class Art extends Items implements java.io.Serializable{
    public Art (int id,String producer,int price,String show,String name){
        super(id,producer,price,show,name);
    }
}
