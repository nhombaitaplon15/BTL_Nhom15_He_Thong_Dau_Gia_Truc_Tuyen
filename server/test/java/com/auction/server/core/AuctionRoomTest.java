package com.auction.server.core;

import com.auction.common.exception.AuctionException;
import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import com.auction.server.dao.UserDAO;
import com.auction.server.service.BiddingService;
import com.auction.server.service.ManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionRoomTest {

  private AuctionRoom auctionRoom;
  private final int ROOM_ID = 100;
  private final double STARTING_PRICE = 1000.0;

  @Mock private ClientHandler mockClientHandler;
  @Mock private ClientHandler mockViewer2;
  @Mock private UserDAO mockUserDAO;
  @Mock private BiddingService mockBiddingService;
  @Mock private ManagerService mockManagerService;

  @BeforeEach
  void setUp() throws Exception {
    auctionRoom = new AuctionRoom(ROOM_ID, STARTING_PRICE);

    // Dùng Reflection để tráo đối tượng UserDAO bên trong AuctionRoom bằng Mock
    Field userDaoField = AuctionRoom.class.getDeclaredField("userDAO");
    userDaoField.setAccessible(true);
    userDaoField.set(auctionRoom, mockUserDAO);

    // ĐÃ XÓA: MockedStatic của SessionManager để tránh xung đột luồng ngầm (Thread-local mock issue)
  }

  @AfterEach
  void tearDown() {
    auctionRoom.destroyRoom(); // Dọn dẹp luồng Executor sau mỗi test
  }

  // =================================================================================
  // 1. TEST CƠ CHẾ QUẢN LÝ PHÒNG (VIEWERS)
  // =================================================================================

  @Test
  @DisplayName("Quản lý phòng: Join và Leave phòng hoạt động chính xác")
  void testRoomManagement_JoinAndLeave() {
    assertFalse(auctionRoom.containsViewer(mockClientHandler));

    auctionRoom.joinRoom(mockClientHandler);
    assertTrue(auctionRoom.containsViewer(mockClientHandler));

    auctionRoom.leaveRoom(mockClientHandler);
    assertFalse(auctionRoom.containsViewer(mockClientHandler));
  }

  @Test
  @DisplayName("broadcastChat - Đẩy tin nhắn đến toàn bộ người trong phòng")
  void testBroadcastChat() {
    auctionRoom.joinRoom(mockClientHandler);
    auctionRoom.joinRoom(mockViewer2);

    auctionRoom.broadcastChat("Hello Room!");

    verify(mockClientHandler).sendMessage(argThat(msg -> msg.getResponseCode() == ResponseCode.CHAT_BROADCAST));
    verify(mockViewer2).sendMessage(argThat(msg -> msg.getResponseCode() == ResponseCode.CHAT_BROADCAST));
  }


  // =================================================================================
  // 2. TEST LOGIC ĐẶT GIÁ (PROCESS BID) - Các kịch bản lỗi (Thất bại)
  // =================================================================================

  @Test
  @DisplayName("processBid [LỖI] - Fast-fail do giá đặt thấp hơn hoặc bằng giá hiện tại")
  void testProcessBid_Fail_PriceTooLow() {
    auctionRoom.processBid(mockClientHandler, 1, 500.0, mockBiddingService);

    verify(mockClientHandler, timeout(1000)).sendMessage(argThat(msg ->
        msg.getResponseCode() == ResponseCode.BID_FAILED &&
            msg.getMessage().contains("không cao hơn giá hiện tại")
    ));
    verifyNoInteractions(mockUserDAO);
    verifyNoInteractions(mockBiddingService);
  }

  @Test
  @DisplayName("processBid [LỖI] - Không tìm thấy thông tin tài khoản")
  void testProcessBid_Fail_UserNotFound() {
    when(mockUserDAO.getUserById(99)).thenReturn(null);

    auctionRoom.processBid(mockClientHandler, 99, 2000.0, mockBiddingService);

    verify(mockClientHandler, timeout(1000)).sendMessage(argThat(msg ->
        msg.getResponseCode() == ResponseCode.BID_FAILED &&
            msg.getMessage().contains("Không tìm thấy tài khoản")
    ));
  }

  @Test
  @DisplayName("processBid [LỖI] - Thất bại do Service chặn (VD: Không đủ tiền, tự buff giá)")
  void testProcessBid_Fail_ServiceThrowsException() {
    User mockUser = new User(1, "diep_nguyen", "e", "p", "012", "ACTIVE", "BIDDER", 100) {};
    when(mockUserDAO.getUserById(1)).thenReturn(mockUser);

    when(mockBiddingService.getManagerService()).thenReturn(mockManagerService);
    doThrow(new AuctionException("Lỗi", "Số dư không đủ!"))
        .when(mockBiddingService).placeBid(mockUser, ROOM_ID, 2000.0);

    auctionRoom.processBid(mockClientHandler, 1, 2000.0, mockBiddingService);

    verify(mockClientHandler, timeout(1000)).sendMessage(argThat(msg ->
        msg.getResponseCode() == ResponseCode.BID_FAILED &&
            msg.getMessage().contains("Số dư không đủ")
    ));
  }

  // =================================================================================
  // 3. TEST LOGIC ĐẶT GIÁ (PROCESS BID) - Thành công và Anti-Sniping
  // =================================================================================

  @Test
  @DisplayName("processBid [THÀNH CÔNG] - Đặt giá chuẩn, cập nhật RAM và Broadcast cho phòng")
  void testProcessBid_Success_Standard() {
    User mockUser = new User(1, "diep_nguyen", "e", "p", "012", "ACTIVE", "BIDDER", 100) {};
    Auction mockAuction = new Auction();

    when(mockUserDAO.getUserById(1)).thenReturn(mockUser);
    when(mockBiddingService.getManagerService()).thenReturn(mockManagerService);
    when(mockManagerService.getAuctionOrThrow(ROOM_ID)).thenReturn(mockAuction);

    auctionRoom.joinRoom(mockClientHandler);
    auctionRoom.joinRoom(mockViewer2);

    auctionRoom.processBid(mockClientHandler, 1, 2500.0, mockBiddingService);

    verify(mockBiddingService, timeout(1000)).placeBid(mockUser, ROOM_ID, 2500.0);
    assertEquals(2500.0, auctionRoom.getCurrentPrice());

    verify(mockClientHandler, timeout(1000)).sendMessage(argThat(msg ->
        msg.getResponseCode() == ResponseCode.BID_SUCCESS
    ));

    // Kiểm tra xem message gửi ra có chứa đúng username "diep_nguyen" không (Theo logic mới update của bạn)
    verify(mockViewer2, timeout(1000)).sendMessage(argThat(msg ->
        msg.getResponseCode() == ResponseCode.NEW_BID_UPDATE &&
            msg.getMessage().contains("diep_nguyen")
    ));

    // ĐÃ XÓA: Lệnh verify(mockSessionManager...) vì chúng ta dùng SessionManager thật cho luồng ngầm
  }

  @Test
  @DisplayName("processBid [ANTI-SNIPING] - Kích hoạt chống bắn tỉa, gia hạn thêm thời gian")
  void testProcessBid_Success_WithAntiSnipingExtendedTime() {
    User mockUser = new User(1, "sniper", "e", "p", "012", "ACTIVE", "BIDDER", 100) {};

    LocalDateTime oldTime = LocalDateTime.now().plusSeconds(10);
    LocalDateTime newExtendedTime = oldTime.plusSeconds(30);

    Auction oldAuction = new Auction();
    oldAuction.setEndTime(oldTime);

    Auction extendedAuction = new Auction();
    extendedAuction.setEndTime(newExtendedTime);

    when(mockUserDAO.getUserById(1)).thenReturn(mockUser);
    when(mockBiddingService.getManagerService()).thenReturn(mockManagerService);

    when(mockManagerService.getAuctionOrThrow(ROOM_ID)).thenReturn(oldAuction);
    when(mockManagerService.getAuction(ROOM_ID)).thenReturn(extendedAuction);

    auctionRoom.joinRoom(mockClientHandler);

    auctionRoom.processBid(mockClientHandler, 1, 5000.0, mockBiddingService);

    verify(mockClientHandler, timeout(1000)).sendMessage(argThat(msg ->
        msg.getResponseCode() == ResponseCode.AUCTION_TIME_EXTENDED &&
            msg.getMessage().contains("được gia hạn thêm")
    ));
  }
}