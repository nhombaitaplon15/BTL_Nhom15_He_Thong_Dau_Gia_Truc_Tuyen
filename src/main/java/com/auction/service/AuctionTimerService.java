package com.auction.service;

public class AuctionTimerService {                  //hàm để đếm ngược thời gian đấu giá
    private int time;                               // thời gian còn lại
    public AuctionTimerService(int time){
        this.time= time;
    }
    public int getTime(){
        return time;
    }
    public void startCountdown(){                   // hàm đếm ngược thời gian của phiên đấu giá
        while(time >0){
            System.out.println("Còn: " + time +" giây ");
            time= time-1;
            try{
                Thread.sleep(1000);           // cho thời gian ngủ để nhịp đếm đúng 1s
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }
        System.out.println(" Hết thời gian đấu giá ! ");
    }
}
