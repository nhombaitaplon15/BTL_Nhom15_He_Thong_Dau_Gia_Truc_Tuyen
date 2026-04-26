package com.auction.common.model;

public class Electronics extends Items implements java.io.Serializable{
    protected String date;
    protected int warrantyExpiryDate;
    public Electronics (String name,int id,String producer,int price, String show,String imgitem, String date, int warrantyExpiryDate ){
        super(id,producer,price,show,name, imgitem);
        this.date= date;
        this.warrantyExpiryDate= warrantyExpiryDate;
    }
}



//public class Electronics extends Items implements java.io.Serializable{
//   protected int date;
//   protected int warrantyExpiryDate;
//   public Electronics (String name,int id,String producer,int price, String show,int date, int warrantyExpiryDate ){
//       this.date= date;
//      this.warrantyExpiryDate= warrantyExpiryDate;
//      super(id,producer,price,show,name);
//}
//}
