package com.auction.common.model;
import com.auction.service.ManagerService;

import java.time.LocalDateTime;

public class Seller extends User {
    public Seller(int id, String name, String email, String password, String phone, String status){
        super(id,name,email,password, phone,status,"SELLER");
    }

    // hàm xác minh vai trò Seller
    private void validateRole() throws Exception {
        if (!"SELLER".equals(this.getRole())) {
            throw new IllegalAccessException("Chỉ người dùng có vai trò SELLER mới được thực hiện!");
        }
    }

    //hàm yêu cầu admin tạo phiên đấu giá diễn ra
    public void requestCreateAuction(ManagerService manager, int auctionId, int itemId, LocalDateTime startTime) throws Exception{
        validateRole();
        manager.scheduleAuction(auctionId, itemId, startTime);                    // lên lịch cho phiên đấu giá
        Auction auction = manager.getAuction(auctionId);                          // gọi 1 phiên đấu giá theo Id trong danh sách các phiên
        if (auction == null) {                                                    // check bắt đầu
            throw new Exception("Không thể tạo phiên đấu giá !");
        }
        auction.setAuctionStatus("WAITING_FOR_ADMIN");
        System.out.println("Đã gửi yêu cầu duyệt phiên đấu giá!");

    }

    // hàm set giá ban đầu, set từ ManagerService
    public void requestStartPrice(ManagerService manager, int itemId, int newPrice) throws Exception{
        validateRole();
        manager.setupStartPrice(itemId, newPrice);
        System.out.println("Đặt giá khởi điểm thành công cho sản phẩm ID: " + itemId);
    }

    //hàm xác nhận bán
    public void confirmSale(ManagerService manager, int auctionId) throws Exception{
        validateRole();
        Auction auction = manager.getAuction(auctionId);
        if(auction == null){
            throw new Exception("Không tồn tại phiên đấu giá!");
        }
        auction.setAuctionStatus("SOLD");         // chuyển trạng thái nếu bán
        System.out.println("Xác nhận bán hàng thành công cho phiên: " + auctionId);
    }

    // hàm yêu cầu hủy phiên đấu
    public void requestCancelAuction(ManagerService manager, int auctionId) throws Exception{
        validateRole();
        Auction auction = manager.getAuction(auctionId);
        if(auction == null){
            throw new Exception("Không tồn tại phiên đấu giá!");
        }
        if("RUNNING".equals(auction.getAuctionStatus())){      // check trạng thái đang diễn ra thì ko hủy được
            throw new Exception("Không thể yêu cầu hủy phiên đấu giá đang diễn ra!");
        }
        if("SOLD".equals(auction.getAuctionStatus())){         // đã bán thì không hủy được
            throw new Exception("Không thể yêu cầu hủy phiên đấu giá đã diễn ra!");
        }
        auction.setAuctionStatus("WAITING_FOR_ADMIN");
        System.out.println("Đã gửi yêu cầu HỦY phiên đấu giá ID: " + auctionId);
    }
}