package com.auction.common.model;
public abstract class Items extends Entity implements java.io.Serializable{
    protected String producer;
    protected int price;
    protected String show;   //mô tả của sản phẩm
    protected String name;
    protected  String imgItem;
    public Items (int id,String producer,int price,String show,String name, String imgItem){
        super(id);
        this.producer=producer;
        this.price=price;
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
    public void setPrice(int price) {
        this.price = price;
    }
    public int getPrice() {
        return price;
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