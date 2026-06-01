package com.auction.server.core;

import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import com.auction.server.dao.UserDAO;
import com.auction.server.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

  private SessionManager sessionManager;

  @Mock private ClientHandler mockHandler1;
  @Mock private ClientHandler mockHandler2;
  @Mock private UserService mockUserService;

  @BeforeEach
  void setUp() throws Exception {
    sessionManager = SessionManager.getInstance();

    // [QUAN TRỌNG] Phải dọn dẹp các collection tĩnh của Singleton trước mỗi bài test
    // Nếu không clear, UserID đã đăng nhập ở test trước sẽ làm sai lệch kết quả test sau
    clearSingletonState("activeConnections");
    clearSingletonState("loggedInUsers");
  }

  private void clearSingletonState(String fieldName) throws Exception {
    Field field = SessionManager.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    Object collection = field.get(sessionManager);
    if (collection instanceof Set) {
      ((Set<?>) collection).clear();
    } else if (collection instanceof ConcurrentHashMap) {
      ((ConcurrentHashMap<?, ?>) collection).clear();
    }
  }

  // =================================================================================
  // 1. TEST QUẢN LÝ KẾT NỐI (CONNECTION LIFECYCLE)
  // =================================================================================

  @Test
  @DisplayName("registerConnection - Thêm thành công ClientHandler vào danh sách hoạt động")
  void testRegisterConnection() {
    sessionManager.registerConnection(mockHandler1);
    assertTrue(sessionManager.getAllConnections().contains(mockHandler1));
    assertEquals(1, sessionManager.getAllConnections().size());
  }

  @Test
  @DisplayName("removeConnection - Gỡ khách vãng lai (Chưa đăng nhập)")
  void testRemoveConnection_UnauthenticatedUser() {
    sessionManager.registerConnection(mockHandler1);
    when(mockHandler1.getLoggedInUserId()).thenReturn(null);

    sessionManager.removeConnection(mockHandler1);

    assertTrue(sessionManager.getAllConnections().isEmpty());
  }

  @Test
  @DisplayName("removeConnection - Gỡ user đã đăng nhập, xóa sạch bộ nhớ phiên")
  void testRemoveConnection_AuthenticatedUser() {
    sessionManager.registerConnection(mockHandler1);
    when(mockHandler1.getLoggedInUserId()).thenReturn(99);
    sessionManager.loginUser(99, mockHandler1);

    sessionManager.removeConnection(mockHandler1);

    assertTrue(sessionManager.getAllConnections().isEmpty());
    assertNull(sessionManager.getConnectionByUserId(99), "Phải xóa sạch RAM session của user");
  }

  // =================================================================================
  // 2. TEST ĐĂNG NHẬP / ĐĂNG XUẤT & BẢO MẬT
  // =================================================================================

  @Test
  @DisplayName("loginUser - Chống Double Login (Chặn đăng nhập ở nơi khác)")
  void testLoginUser_DoubleLoginPrevention() {
    // Luồng 1 đăng nhập thành công
    boolean firstLogin = sessionManager.loginUser(100, mockHandler1);
    assertTrue(firstLogin);
    verify(mockHandler1).setLoggedInUserId(100);

    // Luồng 2 cố gắng đăng nhập cùng ID
    boolean secondLogin = sessionManager.loginUser(100, mockHandler2);
    assertFalse(secondLogin, "Phải từ chối đăng nhập nếu tài khoản đã online chỗ khác");

    // Kiểm tra giữ lại phiên gốc
    assertEquals(mockHandler1, sessionManager.getConnectionByUserId(100));
  }

  @Test
  @DisplayName("removeSession - Xóa phiên chủ động bằng ID")
  void testRemoveSession() {
    sessionManager.loginUser(55, mockHandler1);
    sessionManager.removeSession(55);
    assertNull(sessionManager.getConnectionByUserId(55));
  }

  @Test
  @DisplayName("forceLogout - Khóa tài khoản và đá văng User ngay lập tức")
  void testForceLogout() {
    sessionManager.loginUser(77, mockHandler1);

    sessionManager.forceLogout(77);

    // Xác minh 1: Lệnh nhắn tin báo lỗi được đẩy về client
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(mockHandler1).sendMessage(messageCaptor.capture());
    assertEquals(ResponseCode.ERROR_MESSAGE, messageCaptor.getValue().getResponseCode());
    assertTrue(messageCaptor.getValue().getMessage().contains("bị khóa bởi Admin"));

    // Xác minh 2: Socket bị ép đóng (thông qua hàm cleanUp)
    verify(mockHandler1).cleanUp();

    // Xác minh 3: Không còn tồn tại trên RAM
    assertNull(sessionManager.getConnectionByUserId(77));
  }

  // =================================================================================
  // 3. TEST CÁC TÍNH NĂNG BROADCAST & PUSH NOTIFICATION
  // =================================================================================

  @Test
  @DisplayName("broadcastGlobal - Phát loa tới tất cả tài khoản đang online")
  void testBroadcastGlobal() {
    sessionManager.loginUser(1, mockHandler1);
    sessionManager.loginUser(2, mockHandler2);

    Message globalMsg = new Message(ResponseCode.ERROR_MESSAGE, "Bảo trì", null);
    sessionManager.broadcastGlobal(globalMsg);

    verify(mockHandler1).sendMessage(globalMsg);
    verify(mockHandler2).sendMessage(globalMsg);
  }

  @Test
  @DisplayName("sendToUserIfOnline - Push thông báo cá nhân thành công")
  void testSendToUserIfOnline_UserOnline() {
    sessionManager.loginUser(1, mockHandler1);
    Message privateMsg = new Message(ResponseCode.SELLER_AUCTION_APPROVED, "Duyệt", null);

    sessionManager.sendToUserIfOnline(1, privateMsg);
    sessionManager.sendToUserIfOnline(2, privateMsg); // User 2 không online

    verify(mockHandler1).sendMessage(privateMsg);
    verify(mockHandler2, never()).sendMessage(any()); // Không ném lỗi, chỉ bỏ qua an toàn
  }

  @Test
  @DisplayName("broadcastToAdmins (Dùng UserService) - Chỉ gửi tin cho role ADMIN")
  void testBroadcastToAdmins_UsingUserService() {
    sessionManager.loginUser(1, mockHandler1); // Admin
    sessionManager.loginUser(2, mockHandler2); // Bidder

    User adminUser = mock(User.class);
    when(adminUser.getRole()).thenReturn("ADMIN");

    User normalUser = mock(User.class);
    when(normalUser.getRole()).thenReturn("BIDDER");

    when(mockUserService.getUserById(1)).thenReturn(adminUser);
    when(mockUserService.getUserById(2)).thenReturn(normalUser);

    Message adminMsg = new Message(ResponseCode.ADMIN_NEW_PENDING_AUCTION, "Duyệt đi", null);
    sessionManager.broadcastToAdmins(adminMsg, mockUserService);

    verify(mockHandler1).sendMessage(adminMsg);
    verify(mockHandler2, never()).sendMessage(any());
  }

  @Test
  @DisplayName("broadcastToAdmins (Dùng UserDAO trực tiếp) - Bẫy constructor UserDAO")
  void testBroadcastToAdmins_UsingUserDAO() {
    sessionManager.loginUser(1, mockHandler1); // Admin
    sessionManager.loginUser(2, mockHandler2); // Bidder

    // Vì hàm này gọi lệnh "new UserDAO()", ta dùng MockedConstruction để giả lập đối tượng đó
    try (MockedConstruction<UserDAO> mockedDAO = mockConstruction(UserDAO.class, (mock, context) -> {
      User adminUser = mock(User.class);
      when(adminUser.getRole()).thenReturn("ADMIN");

      User normalUser = mock(User.class);
      when(normalUser.getRole()).thenReturn("BIDDER");

      when(mock.getUserById(1)).thenReturn(adminUser);
      when(mock.getUserById(2)).thenReturn(normalUser);
    })) {
      Message adminMsg = new Message(ResponseCode.ADMIN_NEW_PENDING_AUCTION, "Báo cáo khẩn", null);

      // Act
      sessionManager.broadcastToAdmins(adminMsg);

      // Assert
      verify(mockHandler1).sendMessage(adminMsg);
      verify(mockHandler2, never()).sendMessage(any());
    }
  }
}