package com.auction.service;

import com.auction.common.model.Auction;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ManageService {
    private Map<Integer, Auction> auctionList = new HashMap<>();             // đấu giá có id của buổi đấu giá và has-a với class Auction
    public void setAuctionSchedule(int auctionId, LocalDateTime startTime) { // chuyển trạng thái
        Auction auction = auctionList.get(auctionId);                        // duyệt id của Auction
        if (auction == null) {                                              // không có buổi đấu giá thì in và thoát hàm
            System.out.println("Không có phiên đấu giá này !");
            return;
        }
        auction.setAuctionSchedule(startTime);
        System.out.println("Trạng thái hiện tại: " + auction.getStatus()); // có thì in trạng thái theo quy định hàm ở class Auction rồi
    }
}
