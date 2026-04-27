package com.auction.common.model;
public abstract class Items extends Entity implements java.io.Serializable{
    protected String producer;
    protected int startPrice;
    protected int currentPrice;
    protected String show;   //mô tả của sản phẩm
    protected String name;
    protected  String imgItem;
    public Items (int id,String producer,int startPrice, int currentPrice,String show,String name, String imgItem){
        super(id);
        this.producer=producer;
        this.startPrice=startPrice;
        this.currentPrice= currentPrice;
        this.show=show;
        this.name= name;
        this.imgItem= imgItem;
    }
    public void setProducer(String producer) {
        this.producer = producer;
    }
    public String getProducer() {
        return producer;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getName(){
        return name;
    }
    public void setStartPrice(int startPrice) {
        this.startPrice = startPrice;
    }
    public int getStartPrice() {
        return startPrice;
    }
    public void setCurrentPrice(int currentPrice) {
        this.currentPrice = currentPrice;
    }
    public int getCurrentPrice() {
        return currentPrice;
    }
    public void setShow(String show) {
        this.show = show;
    }
    public String getShow(){
        return show;
    }
    public void setimgItem(String imgItem) {
        this.imgItem = imgItem;
    }
    public String getimgItem(){
        return imgItem;
    }
}