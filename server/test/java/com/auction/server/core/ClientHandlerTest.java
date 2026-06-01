package com.auction.server.core;

import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientHandlerTest {

  private ClientHandler clientHandler;

  @Mock
  private Socket mockSocket;

  private MockedStatic<SessionManager> mockedSessionManager;
  private MockedStatic<AuctionRoomManager> mockedAuctionRoomManager;
  private MockedStatic<RequestDispatcher> mockedRequestDispatcher;

  private SessionManager mockSessionManagerInstance;
  private AuctionRoomManager mockAuctionRoomManagerInstance;
  private RequestDispatcher mockRequestDispatcherInstance;

  @BeforeEach
  void setUp() {
    mockSessionManagerInstance = mock(SessionManager.class);
    mockedSessionManager = mockStatic(SessionManager.class);
    mockedSessionManager.when(SessionManager::getInstance).thenReturn(mockSessionManagerInstance);

    mockAuctionRoomManagerInstance = mock(AuctionRoomManager.class);
    mockedAuctionRoomManager = mockStatic(AuctionRoomManager.class);
    mockedAuctionRoomManager.when(AuctionRoomManager::getInstance).thenReturn(mockAuctionRoomManagerInstance);

    mockRequestDispatcherInstance = mock(RequestDispatcher.class);
    mockedRequestDispatcher = mockStatic(RequestDispatcher.class);
    mockedRequestDispatcher.when(RequestDispatcher::getInstance).thenReturn(mockRequestDispatcherInstance);

    clientHandler = new ClientHandler(mockSocket);
  }

  @AfterEach
  void tearDown() {
    mockedSessionManager.close();
    mockedAuctionRoomManager.close();
    mockedRequestDispatcher.close();
  }

  // =================================================================================
  // 1. TEST VÒNG LẶP LẮNG NGHE GÓI TIN (RUN & READ OBJECT)
  // =================================================================================

  @Test
  @DisplayName("run - Đọc thành công gói tin, bỏ qua PING và điều hướng chuẩn xác")
  void testRun_ReadMessagesAndDispatch() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream fakeClientOut = new ObjectOutputStream(baos);

    // ĐÃ FIX: Gọi chuẩn constructor 2 tham số (RequestCode, payload) khớp hoàn toàn với Message.java của bạn
    Message pingMsg = new Message(RequestCode.PING, null);
    Message fetchRoomsMsg = new Message(RequestCode.FETCH_ROOMS, null);

    fakeClientOut.writeObject(pingMsg);
    fakeClientOut.writeObject(fetchRoomsMsg);
    fakeClientOut.close();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    when(mockSocket.getInputStream()).thenReturn(bais);
    when(mockSocket.getOutputStream()).thenReturn(new ByteArrayOutputStream());

    clientHandler.run();

    verify(mockSessionManagerInstance).registerConnection(clientHandler);
    verify(mockRequestDispatcherInstance, never()).dispatch(any(), argThat(msg -> msg.getRequestCode() == RequestCode.PING));
    verify(mockRequestDispatcherInstance).dispatch(eq(clientHandler), argThat(msg -> msg.getRequestCode() == RequestCode.FETCH_ROOMS));
    verify(mockSessionManagerInstance).removeConnection(clientHandler);
  }

  // =================================================================================
  // 2. TEST GỬI TIN NHẮN (SEND MESSAGE)
  // =================================================================================

  @Test
  @DisplayName("sendMessage - Ghi gói tin thành công, flush và reset luồng để chống rò rỉ RAM")
  void testSendMessage_Success() throws Exception {
    ObjectOutputStream mockOut = mock(ObjectOutputStream.class);
    Field outField = ClientHandler.class.getDeclaredField("out");
    outField.setAccessible(true);
    outField.set(clientHandler, mockOut);

    // Phía Server -> Client dùng ResponseCode thì vẫn truyền 3 tham số chuẩn chỉ
    Message testMsg = new Message(ResponseCode.LOGIN_SUCCESS, "Thành công", null);

    clientHandler.sendMessage(testMsg);

    verify(mockOut).writeObject(testMsg);
    verify(mockOut).flush();
    verify(mockOut).reset();
  }

  @Test
  @DisplayName("sendMessage - Bắt lỗi IOException và tự động kích hoạt dọn dẹp (CleanUp)")
  void testSendMessage_ThrowsIOException_TriggersCleanUp() throws Exception {
    ObjectOutputStream mockOut = mock(ObjectOutputStream.class);
    Field outField = ClientHandler.class.getDeclaredField("out");
    outField.setAccessible(true);
    outField.set(clientHandler, mockOut);

    doThrow(new IOException("Đứt mạng đột ngột")).when(mockOut).writeObject(any());

    Message testMsg = new Message(ResponseCode.LOGIN_SUCCESS, "Gửi đi...", null);

    clientHandler.sendMessage(testMsg);

    verify(mockSessionManagerInstance).removeConnection(clientHandler);
    verify(mockSocket).close();
  }

  // =================================================================================
  // 3. TEST CƠ CHẾ DỌN DẸP AN TOÀN (CLEAN UP)
  // =================================================================================

  @Test
  @DisplayName("cleanUp - Dọn dẹp đầy đủ khi User đã đăng nhập (Tháo session + Kick khỏi phòng)")
  void testCleanUp_WithLoggedInUser() throws IOException {
    when(mockSocket.isClosed()).thenReturn(false);
    clientHandler.setLoggedInUserId(50);

    clientHandler.cleanUp();

    verify(mockSessionManagerInstance).removeSession(50);
    verify(mockAuctionRoomManagerInstance).removeUserFromAllRooms(clientHandler);
    verify(mockSessionManagerInstance).removeConnection(clientHandler);
    verify(mockSocket).close();
  }

  @Test
  @DisplayName("cleanUp - Dọn dẹp cơ bản khi User chưa đăng nhập (Khách vãng lai)")
  void testCleanUp_WithoutLogin() throws IOException {
    when(mockSocket.isClosed()).thenReturn(false);

    clientHandler.cleanUp();

    verify(mockSessionManagerInstance, never()).removeSession(anyInt());
    verify(mockAuctionRoomManagerInstance, never()).removeUserFromAllRooms(any());
    verify(mockSessionManagerInstance).removeConnection(clientHandler);
    verify(mockSocket).close();
  }
}