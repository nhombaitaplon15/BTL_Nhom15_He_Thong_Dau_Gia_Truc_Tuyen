package com.auction.common.model;

public class Auction {
    private Items item;     // phiên đấu giá có chứa sản phẩm của class items
    private int duration;    // thời gian của 1 phiên đấu giá
    public Auction(Items item, int duration){
        this.item= item;
        this.duration= duration;
    }
    public Items getItem(){
        return item;
    }
    public int getDuration(){
        return duration;
    }
}
