package com.auction.common.model;
import com.auction.service.ManagerService;

import java.time.LocalDateTime;

public class Seller extends User {
    public Seller(int id, String name, String email, String password, String phone, String status) {
        super(id, name, email, password, phone, status, "SELLER");
    }
    private void validateRole() throws Exception {                                 // hàm xác minh vai trò Seller
        if (!"SELLER".equals(this.getRole())) {
            throw new IllegalAccessException("Chỉ người dùng có vai trò SELLER mới được thực hiện!");
        }
    }
    public void requestCreateAuction(ManagerService manager, int auctionId, int itemId, LocalDateTime startTime) throws Exception{//hàm yêu cầu admin tạo phiên đấu giá diễn ra
        validateRole();                                                           // check vai trò
        manager.scheduleAuction(auctionId, itemId, startTime);                    // lên lịch cho phiên đấu giá
        Auction auction = manager.getAuction(auctionId);                          // gọi 1 phiên đấu giá theo Id trong danh sách các phiên
        if (auction == null) {                                                    // check bắt đầu
            throw new Exception("Không thể tạo phiên đấu giá !");
        }
        auction.setStatus("WAITING_FOR_ADMIN");
        System.out.println("Đã gửi yêu cầu duyệt phiên đấu giá!");

    }
    public void requestStartPrice(ManagerService manager, int itemId, int newPrice) throws Exception{ // hàm set giá ban đầu, set từ ManagerService
        validateRole();
        manager.setupStartPrice(itemId, newPrice);
        System.out.println("Đặt giá khởi điểm thành công cho sản phẩm ID: " + itemId);
    }
    public void confirmSale(ManagerService manager, int auctionId) throws Exception{                  //hàm xác nhận bán
        validateRole();
        Auction auction = manager.getAuction(auctionId);
        if(auction == null){
            throw new Exception("Không tồn tại phiên đấu giá!");
        }
        auction.setStatus("SOLD");                                                                   // chuyển trạng thái nếu bán
        System.out.println("Xác nhận bán hàng thành công cho phiên: " + auctionId);
    }
    public void requestCancelAuction(ManagerService manager, int auctionId) throws Exception{        // hàm yêu cầu hủy phiên đấu
        validateRole();
        Auction auction = manager.getAuction(auctionId);
        if(auction == null){
            throw new Exception("Không tồn tại phiên đấu giá!");
        }
        if("RUNNING".equals(auction.getStatus())){                                                   // check trạng thái đang diễn ra thì ko hủy được
            throw new Exception("Không thể yêu cầu hủy phiên đấu giá đang diễn ra!");
        }
        if("SOLD".equals(auction.getStatus())){                                                      // đã bán thì không hủy được
            throw new Exception("Không thể yêu cầu hủy phiên đấu giá đã diễn ra!");
        }
        auction.setStatus("WAITING_FOR_ADMIN");
        System.out.println("Đã gửi yêu cầu HỦY phiên đấu giá ID: " + auctionId);
    }
    public void requestDeposit(int amount) throws Exception {
        validateRole();
        if (amount <= 0) {
            throw new Exception("Số tiền nạp phải lớn hơn 0!");
        }
        System.out.println("[Yêu cầu Nạp]: Seller " + this.getUsername() + " gửi yêu cầu nạp " + amount + " VNĐ. Đang chờ Admin duyệt...");
    }
    public void requestWithdraw(int amount) throws Exception {
        validateRole();
        if (amount <= getBalance()) {
            throw new Exception("Số tiền rút phải lớn hơn 0!");
        }
        System.out.println("[Yêu cầu Rút]: Seller " + this.getUsername() + " gửi yêu cầu rút " + amount + " VNĐ. Đang chờ Admin kiểm tra số dư và chuyển khoản...");
    }

    public void requestTransfer(String receiverName, int amount) throws Exception {
        validateRole();
        if (amount >getBalance()) {
            throw new Exception("Số tiền chuyển phải lớn hơn 0!");
        }
        if (this.getUsername().equals(receiverName)) {
            throw new Exception("Không thể tự chuyển tiền cho chính mình!");
        }
        System.out.println("[Yêu cầu Chuyển]: Seller " + this.getUsername() + " yêu cầu chuyển " + amount + " VNĐ tới " + receiverName + ". Đang chờ Admin xác thực giao dịch...");
    }
}