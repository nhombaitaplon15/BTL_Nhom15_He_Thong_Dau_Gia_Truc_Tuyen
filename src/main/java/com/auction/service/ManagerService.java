package com.auction.service;
import com.auction.common.model.Items;
import com.auction.common.model.Auction;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ManagerService {
    private ItemService itemService;                                            // gọi 1 kho quản lí sản phẩm
    private Map<Integer, Auction> auctionList = new HashMap<>();                // đấu giá có id của buổi đấu giá và has-a với class Auction
    public ManagerService(ItemService itemService) {
        this.itemService = itemService;
    }
    public void setupStartPrice(int itemId, int newPrice) throws Exception{     // hàm thiết lập giá khởi điểm
        try{
            Items item = itemService.findItem(itemId);                          // gọi 1 sản phẩm trong kho theo ID
            if( item == null){
                throw new Exception(" Không tìm thấy sản phẩm!");
            }
            if (newPrice <= 0) {
                throw new Exception("Giá khởi điểm phải lớn hơn 0!");
            }
            item.setStartPrice(newPrice);                                       // nếu không có lỗi gì thì set giá
            System.out.println("Đã đặt giá khởi điểm cho " + item.getName() + " là: " + newPrice);
        }catch(Exception e){
            System.out.println("[Lỗi]: " + e.getMessage());
            throw e;
        }
    }
    public void scheduleAuction(int auctionId, int itemId, LocalDateTime startTime) throws Exception{  // hàm cài đặt thời gian
        try{
            Items item = itemService.findItem(itemId);                           // lấy 1 sản phẩm trong kho sản phẩm theo ID
            Auction newAuction = new Auction(auctionId, item);                   // tạo 1 phiên đấu giá
            newAuction.setAuctionSchedule(startTime);                            // set thời gian đấu giá
            newAuction.setAuctionStatus("PENDING");                                     // set thời gian ban đầu: cố định ở lớp Auction
            auctionList.put(auctionId, newAuction);                              // set xong rồi thì cho lại vào danh sách các phiên đấu giá
            System.out.println("Đã lên lịch đấu giá cho " + item.getName() + " vào lúc " + startTime);
        }catch(Exception e){
            System.out.println("[Lỗi]: " + e.getMessage());
            throw e;
        }
    }
    public void activateAuction(int auctionId) throws Exception{                // hàm chuyển trạng thái
        try{
            Auction auction = auctionList.get(auctionId);                       // lấy 1 phiên đấu giá trong danh sách các phiên đấu giá
            LocalDateTime now = LocalDateTime.now();
            if (auction == null) {
                throw new Exception("Phiên đấu giá không tồn tại!");
            }
            if ("PENDING".equals(auction.getAuctionStatus())) {                        // check xem phiên đấu giá có trạng thái ban đầu không
                if (now.isAfter(auction.getStartTime())) {                      // check xem thời gian đã bắt đầu chưa
                    auction.setAuctionStatus("RUNNING");
                    System.out.println(" Phiên đấu giá số " + auctionId + " đã chính thức BẮT ĐẦU!");
                } else {
                    System.out.println("Chưa đến giờ bắt đầu. Vui lòng đợi đến: " + auction.getStartTime());
                }
            } else {
                System.out.println("Phiên đấu giá này không ở trạng thái PENDING nên không thể bắt đầu.");
            }
        }catch(Exception e){
            System.out.println("[Lỗi]: " + e.getMessage());
            throw e;
        }
    }
    // lấy phiên đấu giá ra để kiểm tra
    public Auction getAuction(int auctionId) {
        return auctionList.get(auctionId);
    }
}