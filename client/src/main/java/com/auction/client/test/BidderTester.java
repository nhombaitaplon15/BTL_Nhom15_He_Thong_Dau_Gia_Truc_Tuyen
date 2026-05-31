package com.auction.client.test;

import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;

// THAY BẰNG IMPORT DTO CỦA BẠN
import com.auction.common.network.LoginDTO;
import com.auction.common.network.BidPlaceDTO;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class BidderTester {

  public static void main(String[] args) {
    try {
      System.out.println("🔌 Đang kết nối Server để giả lập Người Mua...");
      Socket socket = new Socket("127.0.0.1", 8888);
      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      out.flush();
      ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

      // Bước 1: ĐĂNG NHẬP VỚI TÀI KHOẢN BIDDER (Người Mua)
      System.out.println(">>> Đang đăng nhập tài khoản Bidder...");
      // Nhớ mở comment và điền tài khoản người mua có thật trong DB
      out.writeObject(new Message(RequestCode.LOGIN, new LoginDTO("Nguyễn Thị Hà", "21031984")));
      out.flush();
      Thread.sleep(2000);

      // Cấu hình mã phiên đấu giá bạn muốn ném tiền vào
      // (Phải là phiên ĐANG MỞ của Seller trên giao diện JavaFX)
      int targetAuctionId = 7;

      // Bước 2: VÀO PHÒNG ĐẤU GIÁ
      // Server của bạn yêu cầu phải vào phòng (JOIN_ROOM) thì mới đặt giá và nghe được chat/bid
      System.out.println(">>> Đang lạch bạch chạy vào phòng số " + targetAuctionId + "...");
      out.writeObject(new Message(RequestCode.JOIN_ROOM, targetAuctionId));
      out.flush();
      Thread.sleep(1000);

      // Bước 3: NÉM TIỀN (PLACE_BID)
      double soTienDatGia = 150000000.0; // Điền số tiền to to một chút cho máu
      System.out.println(">>> Đang quăng " + soTienDatGia + " UETệ vào phiên đấu giá!");

      // Nhớ mở comment và sửa lại tên class BidPlaceDTO hoặc constructor cho đúng với nhóm bạn
      out.writeObject(new Message(RequestCode.PLACE_BID, new BidPlaceDTO(targetAuctionId, soTienDatGia)));
      out.flush();

      System.out.println("🚀 ĐÃ CHỐT GIÁ! Mau quay sang UI của Seller xem thông báo nảy lên chưa!");

      Thread.sleep(2000);
      socket.close();

    } catch (Exception e) {
      System.err.println("❌ Có biến rồi: " + e.getMessage());
      e.printStackTrace();
    }
  }
}