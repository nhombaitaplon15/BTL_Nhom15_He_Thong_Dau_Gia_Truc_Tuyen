package com.auction.common.model;

public class Art extends Items implements java.io.Serializable{
    private int yearCreated;
    private boolean isOriginal;
    public Art (int id,String producer,int startPrice, String description, String name, String imgItem, int yearCreated, boolean isOriginal){
        super(id, producer, startPrice, description, name, imgItem);
        this.yearCreated = yearCreated;
        this.isOriginal = isOriginal;
    }
}
