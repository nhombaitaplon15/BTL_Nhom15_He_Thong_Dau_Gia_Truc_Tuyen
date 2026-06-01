package com.auction.server.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

class DBConnectionTest {

  @Test
  @DisplayName("getConnection - Thiết lập kết nối thành công trả về đối tượng Connection")
  void testGetConnection_Success() throws SQLException {
    Connection mockConnection = mock(Connection.class);

    try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {
      mockedDriverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
          .thenReturn(mockConnection);

      Connection conn = DBConnection.getConnection();

      assertNotNull(conn, "Đối tượng kết nối trả về không được phép null");
      assertEquals(mockConnection, conn, "Hàm phải trả về chính xác thực thể Connection đã thiết lập");
    }
  }

  @Test
  @DisplayName("getConnection - Ném ngoại lệ SQLException ra ngoài khi sai cấu hình hoặc mất mạng")
  void testGetConnection_Fail_ThrowsSQLException() {

    // 🎯 ĐÃ FIX TRIỆT ĐỂ: Tạo ngoại lệ ở NGOÀI khối mockStatic.
    // Việc này ngăn không cho SQLException gọi ngầm DriverManager lúc đang bị Mockito khóa.
    SQLException fakeException = new SQLException("Lỗi từ chối quyền truy cập: Cửa sổ kết nối timeout");

    try (MockedStatic<DriverManager> mockedDriverManager = Mockito.mockStatic(DriverManager.class)) {

      mockedDriverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
          .thenThrow(fakeException);

      SQLException exception = assertThrows(SQLException.class, () -> {
        DBConnection.getConnection();
      }, "Hàm phải đẩy lỗi SQLException lên tầng trên xử lý thay vì tự nuốt lỗi");

      assertTrue(exception.getMessage().contains("Lỗi từ chối quyền truy cập"));
    }
  }
}