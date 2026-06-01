package com.auction.server.core;

import com.auction.common.model.Auction;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionRoomManagerTest {

  private AuctionRoomManager manager;

  @Mock
  private ClientHandler mockClientHandler;

  @BeforeEach
  void setUp() throws Exception {
    // Lấy thể hiện Singleton
    manager = AuctionRoomManager.getInstance();

    // QUAN TRỌNG: Làm sạch bộ nhớ HashMap tĩnh (activeRooms) trước mỗi test case
    // Vì Singleton giữ trạng thái (state) xuyên suốt các test, nếu không clear sẽ bị lỗi đụng độ dữ liệu
    Field activeRoomsField = AuctionRoomManager.class.getDeclaredField("activeRooms");
    activeRoomsField.setAccessible(true);
    ConcurrentHashMap<?, ?> activeRooms = (ConcurrentHashMap<?, ?>) activeRoomsField.get(manager);
    activeRooms.clear();
  }

  // =================================================================================
  // 1. TEST CƠ CHẾ KHỞI TẠO VÀ LẤY PHÒNG
  // =================================================================================

  @Test
  @DisplayName("Singleton - Trả về cùng một instance duy nhất")
  void testSingletonInstance() {
    AuctionRoomManager instance1 = AuctionRoomManager.getInstance();
    AuctionRoomManager instance2 = AuctionRoomManager.getInstance();
    assertSame(instance1, instance2, "Chỉ được phép có duy nhất một đối tượng Manager tồn tại trong RAM");
  }

  @Test
  @DisplayName("createRoom - Tạo phòng thành công bằng ID và Giá khởi điểm")
  void testCreateRoom_Success() {
    // Sử dụng MockedConstruction để bẫy lệnh 'new AuctionRoom'
    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class)) {
      manager.createRoom(101, 5000.0);

      // Xác minh phòng đã được tạo và nằm trong danh sách quản lý
      assertEquals(1, mocked.constructed().size(), "Một thực thể AuctionRoom giả lập phải được tạo ra");
      assertNotNull(manager.getRoom(101));
    }
  }

  @Test
  @DisplayName("openRoom - Mở phòng thông qua đối tượng thực thể Auction")
  void testOpenRoom_Success() {
    Auction mockAuction = mock(Auction.class);
    when(mockAuction.getAuctionId()).thenReturn(102);
    when(mockAuction.getCurrentPrice()).thenReturn(15000.0);

    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class)) {
      manager.openRoom(mockAuction);

      assertEquals(1, mocked.constructed().size());
      assertNotNull(manager.getRoom(102));
    }
  }

  // =================================================================================
  // 2. TEST CƠ CHẾ ĐÓNG PHÒNG VÀ DỌN DẸP
  // =================================================================================

  @Test
  @DisplayName("closeRoom - Bị Admin đóng khẩn cấp, phát Broadcast và tiêu hủy luồng phòng")
  void testCloseRoom_ByAdmin() {
    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class)) {
      manager.createRoom(103, 1000.0);
      AuctionRoom mockRoom = mocked.constructed().get(0); // Lấy đối tượng phòng vừa được mock

      // Thực thi lệnh đóng phòng
      manager.closeRoom(103);

      // Xác minh 1: Lệnh broadcast AUCTION_ENDED đã được gửi đến những người trong phòng
      verify(mockRoom).broadcastToAll(argThat(msg ->
          msg.getResponseCode() == ResponseCode.AUCTION_ENDED &&
              msg.getMessage().contains("đóng khẩn cấp")
      ));

      // Xác minh 2: Lệnh giải phóng luồng ExecutorService của phòng đã được gọi
      verify(mockRoom).destroyRoom();

      // Xác minh 3: Phòng đã biến mất khỏi HashMap
      assertNull(manager.getRoom(103));
    }
  }

  @Test
  @DisplayName("removeRoom - Gỡ bỏ âm thầm (Không Broadcast)")
  void testRemoveRoom() {
    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class)) {
      manager.createRoom(104, 200.0);
      AuctionRoom mockRoom = mocked.constructed().get(0);

      manager.removeRoom(104);

      verify(mockRoom, never()).broadcastToAll(any());
      verify(mockRoom).destroyRoom();
      assertNull(manager.getRoom(104));
    }
  }

  // =================================================================================
  // 3. TEST CƠ CHẾ CHAT REALTIME
  // =================================================================================

  @Test
  @DisplayName("broadcastChatToUserRooms - Tìm thấy phòng chứa người gửi và broadcast")
  void testBroadcastChatToUserRooms_FoundRoom() {
    // Cấu hình mock: Khi manager hỏi "Client này có trong phòng không?", trả về CÓ (true)
    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class, (mock, context) -> {
      when(mock.containsViewer(mockClientHandler)).thenReturn(true);
    })) {
      manager.createRoom(201, 1000.0);
      AuctionRoom mockRoom = mocked.constructed().get(0);

      manager.broadcastChatToUserRooms(99, "User99: Xin chào phòng!", mockClientHandler);

      // Xác minh phòng chứa User này đã phát tin nhắn CHAT_BROADCAST
      verify(mockRoom).broadcastToAll(argThat(msg ->
          msg.getResponseCode() == ResponseCode.CHAT_BROADCAST &&
              msg.getMessage().equals("User99: Xin chào phòng!")
      ));
    }
  }

  @Test
  @DisplayName("broadcastChatToUserRooms - Người gửi không ở phòng nào (Bỏ qua an toàn)")
  void testBroadcastChatToUserRooms_NotInAnyRoom() {
    // Cấu hình mock: Khi manager hỏi, trả về KHÔNG (false)
    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class, (mock, context) -> {
      when(mock.containsViewer(mockClientHandler)).thenReturn(false);
    })) {
      manager.createRoom(202, 1000.0);
      AuctionRoom mockRoom = mocked.constructed().get(0);

      manager.broadcastChatToUserRooms(99, "User99: Có ai không?", mockClientHandler);

      // Xác minh không có lệnh broadcast nào bị gọi sai lệch
      verify(mockRoom, never()).broadcastToAll(any());
    }
  }

  // =================================================================================
  // 4. TEST XỬ LÝ SỰ CỐ BẢO TRÌ VÀ NGẮT KẾT NỐI
  // =================================================================================

  @Test
  @DisplayName("removeUserFromAllRooms - Quét tất cả phòng để rút client bị rớt mạng ra ngoài")
  void testRemoveUserFromAllRooms() {
    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class)) {
      // Tạo 2 phòng song song
      manager.createRoom(301, 100.0);
      manager.createRoom(302, 200.0);

      manager.removeUserFromAllRooms(mockClientHandler);

      // Xác minh Manager đã gõ cửa từng phòng và bắt ClientHandler phải leave
      for (AuctionRoom room : mocked.constructed()) {
        verify(room).leaveRoom(mockClientHandler);
      }
    }
  }

  @Test
  @DisplayName("shutdownAllRooms - Tắt Server, tiêu hủy toàn bộ phòng")
  void testShutdownAllRooms() {
    try (MockedConstruction<AuctionRoom> mocked = mockConstruction(AuctionRoom.class)) {
      manager.createRoom(401, 100.0);
      manager.createRoom(402, 200.0);

      manager.shutdownAllRooms();

      for (AuctionRoom room : mocked.constructed()) {
        verify(room).destroyRoom();
      }
      assertNull(manager.getRoom(401));
      assertNull(manager.getRoom(402));
    }
  }
}