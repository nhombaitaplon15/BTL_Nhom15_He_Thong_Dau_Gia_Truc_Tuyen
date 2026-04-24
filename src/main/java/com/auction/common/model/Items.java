package com.auction.common.model;
public abstract class Items extends Entity implements java.io.Serializable{
    protected String producer;
    protected int price;
    protected String show;
    public Items (int id,String producer,int price,String show,String name){
        super(id);
        this.producer=producer;
        this.price=price;
        this.show=show;
    }
}