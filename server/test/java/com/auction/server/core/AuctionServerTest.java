package com.auction.server.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServerTest {

  private MockedStatic<HeartbeatMonitor> mockedHeartbeatMonitor;
  private MockedStatic<AuctionRoomManager> mockedAuctionRoomManager;

  private HeartbeatMonitor mockHeartbeatInstance;
  private AuctionRoomManager mockRoomManagerInstance;

  @BeforeEach
  void setUp() {
    mockHeartbeatInstance = mock(HeartbeatMonitor.class);
    mockedHeartbeatMonitor = Mockito.mockStatic(HeartbeatMonitor.class);
    mockedHeartbeatMonitor.when(HeartbeatMonitor::getInstance).thenReturn(mockHeartbeatInstance);

    mockRoomManagerInstance = mock(AuctionRoomManager.class);
    mockedAuctionRoomManager = Mockito.mockStatic(AuctionRoomManager.class);
    mockedAuctionRoomManager.when(AuctionRoomManager::getInstance).thenReturn(mockRoomManagerInstance);
  }

  @AfterEach
  void tearDown() {
    mockedHeartbeatMonitor.close();
    mockedAuctionRoomManager.close();
  }

  // =================================================================================
  // 1. TEST KHỞI ĐỘNG SERVER (VÀ CHẤP NHẬN CLIENT)
  // =================================================================================

  @Test
  @DisplayName("start - Server khởi động thành công, nhận 1 Client rồi dừng an toàn")
  void testStart_SuccessAndAcceptClient() throws Exception {
    try (MockedConstruction<ClientHandler> mockedHandler = mockConstruction(ClientHandler.class);

         MockedConstruction<ServerSocket> mockedServer = mockConstruction(ServerSocket.class, (mockServer, context) -> {
           Socket mockSocket = mock(Socket.class);

           when(mockServer.accept())
               .thenReturn(mockSocket)
               .thenThrow(new IOException("Cố tình ném lỗi để phá vỡ vòng lặp vô hạn của Server!"));
         })) {

      AuctionServer server = new AuctionServer();
      server.start();

      verify(mockHeartbeatInstance).startMonitoring();

      ServerSocket capturedServer = mockedServer.constructed().get(0);
      verify(capturedServer, atLeastOnce()).accept();

      assertEquals(1, mockedHandler.constructed().size(), "Phải có đúng 1 ClientHandler được khởi tạo");
    }
  }

  // ĐÃ FIX LỖI: Dùng ServerSocket thật để khóa port thay vì dùng Mockito ép lỗi
  @Test
  @DisplayName("start - Xử lý êm đẹp khi không thể khởi tạo ServerSocket (Port đã bị chiếm)")
  void testStart_PortInUse_HandledGracefully() {
    AuctionServer server = new AuctionServer();
    ServerSocket blockerSocket = null;

    try {
      // Cố tình mở port 8888 trước để chiếm dụng tài nguyên hệ điều hành
      blockerSocket = new ServerSocket(8888);
    } catch (IOException e) {
      // Nếu port 8888 đã bị ứng dụng khác chiếm sẵn trên máy tính,
      // thì mục tiêu làm cho cổng mạng bị khóa đã hoàn thành, ta chỉ việc bỏ qua lỗi này.
    }

    try {
      // Gọi hàm start() -> Code sẽ chạy dòng "new ServerSocket(8888)" và tông trúng blockerSocket
      // Dẫn đến tự văng IOException văng vào khối catch mà không làm Crash test
      assertDoesNotThrow(() -> server.start());

      // Heartbeat vẫn phải được chạy trước khi sập mạng
      verify(mockHeartbeatInstance).startMonitoring();
    } finally {
      // Cleanup: Đóng port trả lại cho hệ thống
      if (blockerSocket != null && !blockerSocket.isClosed()) {
        try {
          blockerSocket.close();
        } catch (IOException ignored) {}
      }
    }
  }

  // =================================================================================
  // 2. TEST TẮT SERVER AN TOÀN (SHUTDOWN HOOK)
  // =================================================================================

  @Test
  @DisplayName("shutdown - Tắt an toàn: Dọn phòng, tắt luồng, dừng nhịp tim")
  void testShutdown() throws Exception {
    AuctionServer server = new AuctionServer();

    Method shutdownMethod = AuctionServer.class.getDeclaredMethod("shutdown");
    shutdownMethod.setAccessible(true);

    shutdownMethod.invoke(server);

    verify(mockHeartbeatInstance).stop();
    verify(mockRoomManagerInstance).shutdownAllRooms();

    Field poolField = AuctionServer.class.getDeclaredField("clientPool");
    poolField.setAccessible(true);
    ExecutorService pool = (ExecutorService) poolField.get(server);

    assertTrue(pool.isShutdown(), "Hệ thống ExecutorService ThreadPool phải được đóng lại để giải phóng RAM");
  }
}