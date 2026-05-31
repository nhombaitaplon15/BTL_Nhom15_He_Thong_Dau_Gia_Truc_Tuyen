package com.auction.client.test;

import com.auction.common.network.LoginDTO;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;

// Chú ý: Bạn hãy import các DTO tương ứng của dự án vào đây nhé
// import com.auction.common.network.LoginDTO;
// import com.auction.common.network.CreateAuctionDTO;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SellerBackendTester {

  private static final String SERVER_IP = "127.0.0.1";
  private static final int PORT = 8888;

  private Socket socket;
  private ObjectOutputStream out;
  private ObjectInputStream in;

  public static void main(String[] args) {
    SellerBackendTester tester = new SellerBackendTester();
    tester.runTest();
  }

  public void runTest() {
    try {
      System.out.println("⏳ Đang kết nối tới Server...");
      socket = new Socket(SERVER_IP, PORT);
      out = new ObjectOutputStream(socket.getOutputStream());
      out.flush();
      in = new ObjectInputStream(socket.getInputStream());

      System.out.println("✅ Kết nối thành công! Bắt đầu luồng test Seller...\n");

      // 1. Khởi động luồng lắng nghe phản hồi từ Server
      startListening();

      // 2. KỊCH BẢN TEST CÁC TÍNH NĂNG CỦA SELLER

      // Bước 1: Login với tài khoản Seller
      System.out.println(">>> GỬI YÊU CẦU: LOGIN");
      // Giả sử LoginDTO có constructor (username, password)
      out.writeObject(new Message(RequestCode.LOGIN, new LoginDTO("Nguyễn Văn Tét", "testthoi")));
      out.flush();
      Thread.sleep(2000); // Tạm dừng 2s để chờ server phản hồi rồi test tiếp

      // Bước 2: Lấy danh sách sản phẩm của Seller
      System.out.println(">>> GỬI YÊU CẦU: LẤY DANH SÁCH SẢN PHẨM");
      out.writeObject(new Message(RequestCode.SELLER_GET_MY_ITEMS, null));
      out.flush();
      Thread.sleep(2000);

      // Bước 3: Tạo phiên đấu giá mới
      System.out.println(">>> GỬI YÊU CẦU: TẠO PHIÊN ĐẤU GIÁ");
      // Giả sử bạn cần truyền ItemId, Thời gian bắt đầu, Thời gian kết thúc
      // LocalDateTime startTime = LocalDateTime.now().plusMinutes(10);
      // LocalDateTime endTime = LocalDateTime.now().plusDays(1);
      // out.writeObject(new Message(RequestCode.SELLER_CREATE_AUCTION,
      //         new CreateAuctionDTO(1, startTime, endTime))); // 1 là ID của Item giả định
      out.flush();
      Thread.sleep(2000);

      // Bước 4: Lấy danh sách các phiên đấu giá đang quản lý
      System.out.println(">>> GỬI YÊU CẦU: LẤY DANH SÁCH PHIÊN ĐANG QUẢN LÝ");
      out.writeObject(new Message(RequestCode.SELLER_GET_MY_AUCTIONS, null));
      out.flush();
      Thread.sleep(2000);

      // Tùy chọn: Thử hủy phiên số 1 (Mở comment nếu muốn test)
      // System.out.println(">>> GỬI YÊU CẦU: HỦY PHIÊN SỐ 1");
      // out.writeObject(new Message(RequestCode.SELLER_CANCEL_AUCTION, 1));
      // out.flush();
      // Thread.sleep(2000);

      System.out.println("\n🎉 HOÀN THÀNH KỊCH BẢN TEST! Giữ kết nối để nghe Realtime...");

      // Giữ cho main thread không bị tắt để tiếp tục nhận tin nhắn từ Server
      while (true) {
        Thread.sleep(1000);
      }

    } catch (Exception e) {
      System.err.println("❌ Lỗi trong quá trình test: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Luồng chạy ngầm liên tục in ra các gói tin Server trả về
   */
  private void startListening() {
    Thread listenerThread = new Thread(() -> {
      try {
        while (!socket.isClosed()) {
          Message response = (Message) in.readObject();

          // In phản hồi ra Console cho dễ debug
          System.out.println("   [SERVER TRẢ VỀ] Code: " + response.getResponseCode());
          System.out.println("   [SERVER TRẢ VỀ] Lời nhắn: " + response.getMessage());

          if (response.getPayload() != null) {
            System.out.println("   [SERVER TRẢ VỀ] Data: " + response.getPayload().toString());
          }
          System.out.println("---------------------------------------------------");
        }
      } catch (Exception e) {
        System.out.println("🔌 Đã ngắt kết nối Server lắng nghe.");
        e.printStackTrace();
      }
    });
    listenerThread.setDaemon(true);
    listenerThread.start();
  }
  private void startHeartbeat() {
    Thread heartbeatThread = new Thread(() -> {
      try {
        while (!socket.isClosed()) {
          synchronized (out) {
            out.writeObject(new Message(RequestCode.PING, null));
            out.flush();
            out.reset();
          }
          // Cứ 5 giây đập nhịp tim 1 lần giống hệt UI
          Thread.sleep(5000);
        }
      } catch (Exception e) {
        // Bỏ qua lỗi ngắt kết nối
      }
    });
    heartbeatThread.setDaemon(true);
    heartbeatThread.start();
  }
}