package com.auction.client.core;

import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageRouterTest {

  private MessageRouter router;

  // Khai báo các đối tượng giả lập (Mock) cho quá trình test
  private ResponseCode mockCodeA;
  private ResponseCode mockCodeB;
  private Message mockMessage;
  private Consumer<Message> mockHandler;

  @BeforeEach
  void setUp() {
    // Lấy instance của Singleton
    router = MessageRouter.getInstance();

    // Khởi tạo mock các dependency bằng Mockito
    mockCodeA = mock(ResponseCode.class);
    mockCodeB = mock(ResponseCode.class);
    mockMessage = mock(Message.class);
    mockHandler = mock(Consumer.class);
  }

  @AfterEach
  void tearDown() {
    // QUAN TRỌNG: Vì Router là Singleton, ta phải xóa sạch các handler đã đăng ký
    // sau mỗi hàm test để đảm bảo môi trường độc lập cho hàm test tiếp theo.
    router.unregister(mockCodeA);
    router.unregister(mockCodeB);
  }

  @Test
  void testGetInstance_ShouldAlwaysReturnSameInstance() {
    MessageRouter instance1 = MessageRouter.getInstance();
    MessageRouter instance2 = MessageRouter.getInstance();

    assertNotNull(instance1, "Instance không được null");
    assertSame(instance1, instance2, "MessageRouter phải tuân thủ đúng mẫu Singleton (chỉ có duy nhất 1 instance)");
  }

  @Test
  void testRoute_WithRegisteredHandler_ShouldInvokeAccept() {
    // GIVEN: Cài đặt khi tin nhắn có mã CodeA
    when(mockMessage.getResponseCode()).thenReturn(mockCodeA);

    // Đăng ký handler xử lý cho CodeA
    router.register(mockCodeA, mockHandler);

    // WHEN: Thực hiện định tuyến tin nhắn
    router.route(mockMessage);

    // THEN: Xác thực xem hàm accept() của handler có được kích hoạt và truyền đúng message vào không
    verify(mockHandler, times(1)).accept(mockMessage);
  }

  @Test
  void testRoute_AfterUnregister_ShouldNotInvokeHandler() {
    // GIVEN: Đăng ký handler rồi hủy ngay lập tức
    when(mockMessage.getResponseCode()).thenReturn(mockCodeA);
    router.register(mockCodeA, mockHandler);
    router.unregister(mockCodeA);

    // WHEN: Định tuyến tin nhắn
    router.route(mockMessage);

    // THEN: Handler tuyệt đối không được gọi trúng
    verify(mockHandler, never()).accept(any());
  }

  @Test
  void testRoute_WithNullMessage_ShouldReturnSafely() {
    // GIVEN & WHEN: Truyền một message rỗng (null) vào hàm route
    // THEN: Hàm phải tự thoát an toàn mà không ném ra NullPointerException
    assertDoesNotThrow(() -> router.route(null), "Hàm route() phải xử lý an toàn khi truyền vào null message");
  }

  @Test
  void testRoute_WithMessageHavingNullResponseCode_ShouldReturnSafely() {
    // GIVEN: Tin nhắn hợp lệ nhưng ResponseCode bên trong bị null
    when(mockMessage.getResponseCode()).thenReturn(null);

    // WHEN & THEN: Định tuyến tin nhắn
    assertDoesNotThrow(() -> router.route(mockMessage), "Hàm route() phải xử lý an toàn khi getResponseCode() trả về null");
  }

  @Test
  void testRoute_WithUnregisteredCode_ShouldNotCrashOrInvokeOtherHandlers() {
    // GIVEN: Đăng ký handler cho CodeA, nhưng tin nhắn mạng đổ về lại mang CodeB
    router.register(mockCodeA, mockHandler);
    when(mockMessage.getResponseCode()).thenReturn(mockCodeB);

    // WHEN: Định tuyến tin nhắn mang CodeB
    router.route(mockMessage);

    // THEN: Handler của CodeA không được phép chạy ké
    verify(mockHandler, never()).accept(any());
  }

  @Test
  void testRoute_WhenHandlerThrowsException_ShouldCatchAndNotCrashSystem() {
    // GIVEN: Giả lập tình huống Controller viết code lỗi dẫn tới ném ra RuntimeException khi nhận message
    when(mockMessage.getResponseCode()).thenReturn(mockCodeA);
    doThrow(new RuntimeException("Lỗi xử lý UI lạc hướng")).when(mockHandler).accept(mockMessage);

    router.register(mockCodeA, mockHandler);

    // WHEN & THEN: Định tuyến tin nhắn.
    // Bộ định tuyến Router phải có cơ chế try-catch bọc quanh để bảo vệ ứng dụng Client không bị sập (crash)
    assertDoesNotThrow(() -> router.route(mockMessage),
            "Router phải tự catch exception của handler để tránh làm sập luồng nhận tin nhắn chính");
  }
  @Test
  void testMultiHandlerRegistration() {
    Consumer<Message> handler1 = mock(Consumer.class);
    Consumer<Message> handler2 = mock(Consumer.class);
    router.register(mockCodeA, handler1);
    router.register(mockCodeA, handler2);
    when(mockMessage.getResponseCode()).thenReturn(mockCodeA);

    router.route(mockMessage);

    verify(handler1).accept(mockMessage);
    verify(handler2).accept(mockMessage);
  }
}
