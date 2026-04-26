package com.auction.service;
import com.auction.common.model.Items;
import com.auction.common.model.User;
import com.auction.common.model.Auction;
public class AuctionService {                                                // để kiểm tra giá mới + check role Admin, Bidder
    public void checkPrice(Items item, double newPrice){
        double currentPrice = item.getPrice();                              // giá hiện tại chính bằng giá của đồ vật
        if (newPrice <= currentPrice){
            throw new IllegalArgumentException("Giá mới phải lớn hơn giá cũ!");
        }
    }
    public void checkAdmin(User user){                                      // nếu khác admin thì ném ngoại lệ in ra
        if(user.getRole() != ("ADMIN")){
            throw new IllegalArgumentException(" Chỉ ADMIN được thực hiện hành động này !");
        }
    }
    public void checkBidder(User user){                                     // nếu khác bidder thì ném ngoại lệ in ra
        if(user.getRole() != ("BIDDER")){
            throw new IllegalArgumentException(" Chỉ BIDDER được thực hiện hành động này !");
        }
    }
    public void startAuction(User user, Auction auction){                  // chỉ admin được bắt đầu, nếu không phải admin throw và không bắt đầu đấu giá được
        checkAdmin(user);
        System.out.println("Phiên đấu giá bắt đầu cho: " + auction.getItem().getName());
    }
    public void endAuction(User user, Auction auction) {                   // chỉ admin được bắt đầu, nếu không phải admin throw và không kết thúc đấu giá được
        checkAdmin(user);
        System.out.println("Phiên đấu giá kết thúc cho: " + auction.getItem().getName());
    }
}

