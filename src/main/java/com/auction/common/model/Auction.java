package com.auction.common.model;

import java.time.LocalDateTime;

public class Auction {
    private Items item;                                    // phiên đấu giá có chứa sản phẩm của class items
    private LocalDateTime startTime;                       //thời gian bắt đầu phiên đấu giá
    private String status;                                 // trang thái của phiên đấu giá
    public Auction (Items item){
        this.item = item;
    }
    public Items getItem(){
        return item;
    }
    public String getStatus(){                            // hàm viết quy định về trạng thái phiên đấu giá
        if(startTime == null){                            //chưa set thời gian ban đầu thì chỉ mở thôi
            return "OPEN";
        }
        if(LocalDateTime.now().isAfter(startTime)){
            return "RUNNING";
        }
        return "OPEN";                                    //các trường hợp khác chỉ đang mở thôi
    }
    public void setAuctionSchedule(LocalDateTime startTime){// hàm lấy thời điểm bắt đầu đấu giá
        this.startTime= startTime;
    }
}
