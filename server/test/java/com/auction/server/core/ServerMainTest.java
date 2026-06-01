package com.auction.server.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerMainTest {

  // Bộ bắt luồng in lỗi Console (System.err)
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private final PrintStream originalErr = System.err;

  @BeforeEach
  public void setUpStreams() {
    // Chuyển hướng luồng in lỗi từ Console vào biến errContent để kiểm tra
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  public void restoreStreams() {
    // Trả lại luồng in lỗi nguyên bản cho hệ thống
    System.setErr(originalErr);
  }

  // =================================================================================
  // 1. TEST KHỞI CHẠY THÀNH CÔNG
  // =================================================================================

  @Test
  @DisplayName("main - Khởi động ServerMain thành công và gọi lệnh start() của AuctionServer")
  void testMain_Success() {
    // Chặn lệnh new AuctionServer() bên trong hàm main
    try (MockedConstruction<AuctionServer> mockedServer = mockConstruction(AuctionServer.class)) {

      // Thực thi hàm main khởi động
      ServerMain.main(new String[]{});

      // Xác minh 1: Chắc chắn rằng có 1 đối tượng AuctionServer đã được tạo ra
      assertEquals(1, mockedServer.constructed().size(), "Phải khởi tạo đúng 1 đối tượng AuctionServer");

      // Xác minh 2: Lệnh start() phải được gọi chính xác 1 lần
      AuctionServer capturedServer = mockedServer.constructed().get(0);
      verify(capturedServer, times(1)).start();
    }
  }

  // =================================================================================
  // 2. TEST XỬ LÝ NGOẠI LỆ (CATCH EXCEPTION)
  // =================================================================================

  @Test
  @DisplayName("main - Bắt lỗi an toàn khi AuctionServer ném ngoại lệ trong lúc start()")
  void testMain_CatchException() {
    // Cấu hình Mock: Gài bẫy để khi gọi hàm start(), đối tượng giả sẽ ném ra lỗi Runtime
    try (MockedConstruction<AuctionServer> mockedServer = mockConstruction(AuctionServer.class, (mock, context) -> {
      doThrow(new RuntimeException("Lỗi cổng mạng 8888 đang bị chiếm")).when(mock).start();
    })) {

      // Thực thi hàm main: Dù lỗi văng ra nhưng chương trình KHÔNG được phép crash nhờ có try-catch
      assertDoesNotThrow(() -> ServerMain.main(new String[]{}));

      // Xác minh 1: Đảm bảo start() vẫn bị gọi
      AuctionServer capturedServer = mockedServer.constructed().get(0);
      verify(capturedServer, times(1)).start();

      // Xác minh 2: Hệ thống bắt buộc phải in ra dòng log báo lỗi như đã lập trình trong file gốc
      assertTrue(errContent.toString().contains("[SERVER ERROR] Không thể khởi động server!"),
          "Thông báo lỗi phải được in ra System.err đúng như kịch bản catch");
    }
  }
}