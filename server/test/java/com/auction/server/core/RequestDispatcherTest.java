package com.auction.server.core;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.common.network.*;
import com.auction.server.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestDispatcherTest {

  private RequestDispatcher dispatcher;

  @Mock private ClientHandler mockClientHandler;
  @Mock private Message mockMessage;

  @Mock private UserService mockUserService;
  @Mock private ItemService mockItemService;
  @Mock private ManagerService mockManagerService;
  @Mock private BiddingService mockBiddingService;
  @Mock private SellerService mockSellerService;
  @Mock private AdminService mockAdminService;
  @Mock private TransactionService mockTransactionService;

  private MockedStatic<SessionManager> mockedSessionManager;
  private MockedStatic<AuctionRoomManager> mockedAuctionRoomManager;
  private SessionManager mockSessionManagerInstance;
  private AuctionRoomManager mockAuctionRoomManagerInstance;

  @BeforeEach
  void setUp() throws Exception {
    dispatcher = RequestDispatcher.getInstance();

    injectMockField("userService", mockUserService);
    injectMockField("itemService", mockItemService);
    injectMockField("managerService", mockManagerService);
    injectMockField("biddingService", mockBiddingService);
    injectMockField("sellerService", mockSellerService);
    injectMockField("adminService", mockAdminService);
    injectMockField("transactionService", mockTransactionService);

    mockSessionManagerInstance = mock(SessionManager.class);
    mockedSessionManager = mockStatic(SessionManager.class);
    mockedSessionManager.when(SessionManager::getInstance).thenReturn(mockSessionManagerInstance);

    mockAuctionRoomManagerInstance = mock(AuctionRoomManager.class);
    mockedAuctionRoomManager = mockStatic(AuctionRoomManager.class);
    mockedAuctionRoomManager.when(AuctionRoomManager::getInstance).thenReturn(mockAuctionRoomManagerInstance);
  }

  @AfterEach
  void tearDown() {
    mockedSessionManager.close();
    mockedAuctionRoomManager.close();
  }

  private void injectMockField(String fieldName, Object mockObj) throws Exception {
    Field field = RequestDispatcher.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(dispatcher, mockObj);
  }

  private User createDummyUser(int id, String username, String role) {
    return new User(id, username, username + "@test.com", "pass", "0123456789", "ACTIVE", role, 1000.0) {};
  }

  // =================================================================================
  // 1. TEST CHỨC NĂNG XÁC THỰC (AUTH)
  // =================================================================================

  @Test
  @DisplayName("LOGIN - Đăng nhập thành công")
  void testDispatch_Login_Success() {
    LoginDTO loginDTO = new LoginDTO("diep_nguyen", "password123");
    User dummyUser = createDummyUser(1, "diep_nguyen", "BIDDER");

    when(mockMessage.getRequestCode()).thenReturn(RequestCode.LOGIN);
    when(mockMessage.getPayload()).thenReturn(loginDTO);
    when(mockUserService.handleLogin("diep_nguyen", "password123")).thenReturn(dummyUser);
    when(mockSessionManagerInstance.loginUser(eq(1), any(ClientHandler.class))).thenReturn(true);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    verify(mockClientHandler).setLoggedInUserId(1);
    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.LOGIN_SUCCESS, responseCaptor.getValue().getResponseCode());
  }

  @Test
  @DisplayName("LOGIN - Đăng nhập thất bại")
  void testDispatch_Login_Failed() {
    LoginDTO loginDTO = new LoginDTO("wrong_user", "pass");

    when(mockMessage.getRequestCode()).thenReturn(RequestCode.LOGIN);
    when(mockMessage.getPayload()).thenReturn(loginDTO);
    when(mockUserService.handleLogin("wrong_user", "pass")).thenReturn(null);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.LOGIN_FAILED, responseCaptor.getValue().getResponseCode());
  }

  @Test
  @DisplayName("REGISTER - Đăng ký tài khoản mới thành công")
  void testDispatch_Register_Success() {
    // ĐÃ FIX: Truyền đủ 5 tham số đầu vào cho RegisterDTO để khớp hoàn toàn với mã nguồn của bạn
    RegisterDTO registerDTO = new RegisterDTO("new_user", "pass1234", "new@test.com", "0987654321", "BIDDER");

    when(mockMessage.getRequestCode()).thenReturn(RequestCode.REGISTER);
    when(mockMessage.getPayload()).thenReturn(registerDTO);
    when(mockUserService.handleRegister("new_user", "pass1234", "new@test.com", "0987654321")).thenReturn(true);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.REGISTER_SUCCESS, responseCaptor.getValue().getResponseCode());
  }

  // =================================================================================
  // 2. TEST CHỨC NĂNG NGƯỜI ĐẤU GIÁ (BIDDER)
  // =================================================================================

  @Test
  @DisplayName("FETCH_ROOMS - Lấy danh sách phòng thành công")
  void testDispatch_FetchRooms_Success() {
    List<Auction> mockAuctions = Arrays.asList(new Auction(), new Auction());
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.FETCH_ROOMS);
    when(mockManagerService.getAllAuctions()).thenReturn(mockAuctions);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.ROOM_LIST_RESULT, responseCaptor.getValue().getResponseCode());
  }

  @Test
  @DisplayName("PLACE_BID - Chặn đặt giá khi chưa đăng nhập")
  void testDispatch_PlaceBid_Unauthenticated() {
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.PLACE_BID);
    when(mockClientHandler.getLoggedInUserId()).thenReturn(null);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.BID_FAILED, responseCaptor.getValue().getResponseCode());
  }

  @Test
  @DisplayName("PLACE_BID - Đẩy lệnh đặt giá vào phòng realtime thành công")
  void testDispatch_PlaceBid_Success() {
    BidPlaceDTO bidDTO = new BidPlaceDTO(5, 2500.0);
    AuctionRoom mockRoom = mock(AuctionRoom.class);

    when(mockMessage.getRequestCode()).thenReturn(RequestCode.PLACE_BID);
    when(mockMessage.getPayload()).thenReturn(bidDTO);
    when(mockClientHandler.getLoggedInUserId()).thenReturn(10);
    when(mockAuctionRoomManagerInstance.getRoom(5)).thenReturn(mockRoom);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    verify(mockRoom).processBid(mockClientHandler, 10, 2500.0, mockBiddingService);
  }

  // =================================================================================
  // 3. TEST CHỨC NĂNG NGƯỜI BÁN & QUẢN TRỊ (SELLER & ADMIN)
  // =================================================================================

  @Test
  @DisplayName("SELLER_ADD_ITEM - Chặn thêm sản phẩm khi mất session đăng nhập")
  void testDispatch_SellerAddItem_SessionExpired() {
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.SELLER_ADD_ITEM);
    when(mockClientHandler.getLoggedInUserId()).thenReturn(null);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.ERROR_MESSAGE, responseCaptor.getValue().getResponseCode());
  }

  @Test
  @DisplayName("ADMIN_BAN_USER - Khóa tài khoản và kích ngắt kết nối thành công")
  void testDispatch_AdminBanUser_Success() {
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.ADMIN_BAN_USER);
    when(mockMessage.getPayload()).thenReturn(99);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    verify(mockUserService).banUser(99);
    verify(mockSessionManagerInstance).forceLogout(99);

    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.ADMIN_BAN_SUCCESS, responseCaptor.getValue().getResponseCode());
  }

  @Test
  @DisplayName("GLOBAL EXCEPTION - Đảm bảo Server luôn đứng vững trước mọi lỗi phát sinh")
  void testDispatch_GlobalExceptionHandling() {
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.ADMIN_GET_ALL_USERS);
    when(mockUserService.getAllUsers()).thenThrow(new RuntimeException("Fatal SQL Crash"));

    assertDoesNotThrow(() -> dispatcher.dispatch(mockClientHandler, mockMessage));

    ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(responseCaptor.capture());
    assertEquals(ResponseCode.ERROR_MESSAGE, responseCaptor.getValue().getResponseCode());
  }
  @Test
  @DisplayName("ADMIN_APPROVE_AUCTION - Thành công và thông báo cho Seller")
  void testDispatch_AdminApproveAuction_Success() {
    Auction mockAuction = mock(Auction.class);
    when(mockAuction.getSellerId()).thenReturn(20);

    when(mockMessage.getRequestCode()).thenReturn(RequestCode.ADMIN_APPROVE_AUCTION);
    when(mockMessage.getPayload()).thenReturn(1);
    when(mockAdminService.approveAuction(1)).thenReturn(true);
    when(mockManagerService.getAuctionOrThrow(1)).thenReturn(mockAuction);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    verify(mockAuctionRoomManagerInstance).openRoom(mockAuction);
    // Kiểm tra đã gửi message thông báo cho seller
    verify(mockSessionManagerInstance).sendToUserIfOnline(eq(20), any(Message.class));
  }

  @Test
  @DisplayName("ADMIN_BLOCK_AUCTION - Test logic xóa sau 5 phút (Simulate thread)")
  void testDispatch_AdminBlockAuction_Success() {
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.ADMIN_BLOCK_AUCTION);
    when(mockMessage.getPayload()).thenReturn(10);
    when(mockAdminService.blockAuction(10, "")).thenReturn(true);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    verify(mockAuctionRoomManagerInstance).closeRoom(10);
    verify(mockClientHandler).sendMessage(any(Message.class));
  }

  @Test
  @DisplayName("HANDLE_ADMIN_BAN_USER - Xử lý ngoại lệ khi DAO lỗi")
  void testDispatch_AdminBanUser_Exception() {
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.ADMIN_BAN_USER);
    when(mockMessage.getPayload()).thenReturn(5);
    // Giả lập Service ném lỗi
    doThrow(new RuntimeException("DB Error")).when(mockUserService).banUser(5);

    dispatcher.dispatch(mockClientHandler, mockMessage);

    // Kiểm tra phản hồi lỗi về client
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(captor.capture());
    assertEquals(ResponseCode.ERROR_MESSAGE, captor.getValue().getResponseCode());
  }

  @Test
  @DisplayName("ADMIN_CREATE_TRANSACTION - Xử lý payload không hợp lệ")
  void testDispatch_AdminCreateTransaction_MalformedPayload() {
    when(mockMessage.getRequestCode()).thenReturn(RequestCode.ADMIN_CREATE_TRANSACTION);
    // Gửi sai định dạng (thiếu payload hoặc sai kiểu)
    when(mockMessage.getPayload()).thenReturn("wrong_type");

    dispatcher.dispatch(mockClientHandler, mockMessage);

    // Đảm bảo không bị crash và báo lỗi
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(mockClientHandler).sendMessage(captor.capture());
    assertEquals(ResponseCode.ADMIN_TRANSACTION_FAILED, captor.getValue().getResponseCode());
  }
}
