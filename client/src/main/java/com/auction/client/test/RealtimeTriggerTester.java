package com.auction.client.test;

import com.auction.common.network.LoginDTO;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
// Nhớ import LoginDTO của bạn vào đây nhé

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class RealtimeTriggerTester {
  public static void main(String[] args) {
    try {
      System.out.println("🔌 Đang kết nối Server để tạo 'cú hích' Realtime...");
      Socket socket = new Socket("127.0.0.1", 8888);
      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      out.flush();
      ObjectInputStream in = new ObjectInputStream(socket.getInputStream()); // Khởi tạo để Server ko báo lỗi

      // 1. ĐĂNG NHẬP VỚI QUYỀN ADMIN
      System.out.println(">>> Đang đăng nhập Admin...");
      // Lưu ý: Thay "admin_x" và "pass" bằng tài khoản Admin CÓ THẬT trong DB của bạn
      out.writeObject(new Message(RequestCode.LOGIN, new LoginDTO("Nguyễn Thúy Diệp", "25021671")));
      out.flush();
      Thread.sleep(2000); // Dừng 2s đợi Server xử lý login

      // 2. BẮN LỆNH DUYỆT PHIÊN ĐẤU GIÁ
      // Lưu ý: Đổi số 1 thành ID phiên đấu giá đang ở trạng thái CHỜ DUYỆT của chính Seller đang mở trên giao diện
      int auctionIdToApprove = 1;
      System.out.println(">>> Bắn lệnh duyệt phiên số " + auctionIdToApprove + "...");
      out.writeObject(new Message(RequestCode.ADMIN_APPROVE_AUCTION, auctionIdToApprove));
      out.flush();

      System.out.println("🚀 ĐÃ BẮN LỆNH! Bạn mau mở giao diện JavaFX của Seller lên xem có nảy thông báo chưa nhé!");

      Thread.sleep(2000);
      socket.close(); // Xong việc thì rút êm

    } catch (Exception e) {
      System.err.println("❌ Lỗi: " + e.getMessage());
      e.printStackTrace();
    }
  }
}