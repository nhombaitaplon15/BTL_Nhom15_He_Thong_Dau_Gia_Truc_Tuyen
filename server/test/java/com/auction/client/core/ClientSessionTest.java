package com.auction.client.core;

import com.auction.common.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientSessionTest {

  private ClientSession session;
  private User mockUser;

  @BeforeEach
  void setUp() {
    // Lấy instance của Singleton
    session = ClientSession.getInstance();

    // CỰC KỲ QUAN TRỌNG: Xóa sạch dữ liệu cũ trước mỗi hàm test
    // để đảm bảo các test case hoàn toàn độc lập với nhau
    session.setCurrentUser(null);

    // Tạo một đối tượng User giả lập (Mock)
    mockUser = mock(User.class);
  }

  @Test
  void testGetInstance_ShouldReturnSameInstance() {
    ClientSession instance1 = ClientSession.getInstance();
    ClientSession instance2 = ClientSession.getInstance();

    assertNotNull(instance1, "Instance không được null");
    assertSame(instance1, instance2, "ClientSession phải tuân thủ mẫu Singleton (chỉ có 1 instance duy nhất)");
  }

  @Test
  void testInitialState_WhenNotLoggedIn() {
    // Kiểm tra các giá trị mặc định khi chưa có ai đăng nhập (currentUser == null)
    assertNull(session.getCurrentUser(), "User mặc định phải là null");
    assertFalse(session.isLoggedIn(), "isLoggedIn phải trả về false");
    assertEquals(-1, session.getUserId(), "getUserId phải trả về -1 để tránh lỗi lấy nhầm dữ liệu");
    assertEquals("Unknown", session.getUsername(), "getUsername phải trả về 'Unknown'");
    assertEquals("", session.getRole(), "getRole phải trả về chuỗi rỗng");
  }

  @Test
  void testSetCurrentUser_ShouldUpdateSessionData() {
    // GIVEN: Giả lập User có thông tin đầy đủ
    when(mockUser.getId()).thenReturn(11);
    when(mockUser.getUsername()).thenReturn("testseller");
    when(mockUser.getRole()).thenReturn("SELLER");

    // WHEN: Đăng nhập thành công và lưu vào session
    session.setCurrentUser(mockUser);

    // THEN: Kiểm tra xem session có lấy đúng thông tin từ đối tượng User ra không
    assertTrue(session.isLoggedIn(), "Trạng thái đăng nhập phải là true");
    assertEquals(mockUser, session.getCurrentUser(), "getCurrentUser phải trả về đúng đối tượng vừa set");
    assertEquals(11, session.getUserId(), "Hàm getUserId phải map chuẩn");
    assertEquals("testseller", session.getUsername(), "Hàm getUsername phải map chuẩn");
    assertEquals("SELLER", session.getRole(), "Hàm getRole phải map chuẩn");
  }

  @Test
  void testClearSession_ShouldResetAllData() {
    // GIVEN: Đăng nhập trước
    session.setCurrentUser(mockUser);
    assertTrue(session.isLoggedIn());

    // WHEN: Người dùng bấm Đăng xuất (Logout) - giả sử bạn sẽ gọi truyền null
    session.setCurrentUser(null);

    // THEN: Mọi thông tin phải bị xóa sạch, trở về trạng thái như chưa đăng nhập
    assertFalse(session.isLoggedIn(), "Đã xóa session thì isLoggedIn phải là false");
    assertNull(session.getCurrentUser(), "User trong session phải bị xóa thành null");
    assertEquals(-1, session.getUserId(), "ID phải reset về -1");
  }
}