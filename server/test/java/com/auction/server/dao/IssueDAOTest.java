package com.auction.server.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IssueDAOTest {

  private IssueDAO issueDAO;
  private Connection mockConnection;
  private PreparedStatement mockPreparedStatement;
  private MockedStatic<DBConnection> mockedDBConnection;

  @BeforeEach
  void setUp() throws SQLException {
    issueDAO = new IssueDAO();
    mockConnection = mock(Connection.class);
    mockPreparedStatement = mock(PreparedStatement.class);

    // Giả lập kết nối cơ sở dữ liệu tĩnh
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);

    // Mặc định trả về mockPreparedStatement khi gọi prepareStatement
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
  }

  @AfterEach
  void tearDown() {
    // Đóng mock static để tránh ảnh hưởng đến các class test khác
    mockedDBConnection.close();
  }

  @Test
  @DisplayName("insertIssue - Thêm báo cáo khiếu nại thành công")
  void testInsertIssue_Success() throws SQLException {
    // Giả lập executeUpdate thành công trả về 1 dòng bị ảnh hưởng
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    boolean result = issueDAO.insertIssue(1, 100, "FRAUD", "Người dùng có dấu hiệu đẩy giá ảo");

    assertTrue(result, "Hàm phải trả về true khi dữ liệu được chèn thành công");

    // Xác minh các tham số được truyền vào PreparedStatement đúng thứ tự và giá trị
    verify(mockPreparedStatement).setInt(1, 1);
    verify(mockPreparedStatement).setInt(2, 100);
    verify(mockPreparedStatement).setString(3, "FRAUD");
    verify(mockPreparedStatement).setString(4, "Người dùng có dấu hiệu đẩy giá ảo");
  }

  @Test
  @DisplayName("insertIssue - Thất bại khi không có dòng nào được chèn")
  void testInsertIssue_Fail_NoRowsAffected() throws SQLException {
    // Giả lập executeUpdate trả về 0 dòng bị ảnh hưởng
    when(mockPreparedStatement.executeUpdate()).thenReturn(0);

    boolean result = issueDAO.insertIssue(2, 200, "SYSTEM_ERROR", "Lỗi không bấm được nút đấu giá");

    assertFalse(result, "Hàm phải trả về false khi affected rows bằng 0");
  }

  @Test
  @DisplayName("insertIssue - Thất bại và bắt lỗi khi xảy ra lỗi SQLException")
  void testInsertIssue_Fail_ThrowsSQLException() throws SQLException {
    // Giả lập câu lệnh thực thi quăng ra một lỗi ngoại lệ hệ thống dữ liệu
    when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Database connection timeout"));

    boolean result = issueDAO.insertIssue(3, 300, "PAYMENT", "Lỗi trừ tiền ví");

    assertFalse(result, "Hàm phải bắt lỗi catch và trả về false thay vì làm sập hệ thống");
  }
}