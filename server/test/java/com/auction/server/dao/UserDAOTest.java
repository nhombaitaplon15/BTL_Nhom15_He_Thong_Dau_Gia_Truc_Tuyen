package com.auction.server.dao;

import com.auction.common.model.Bidder;
import com.auction.common.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserDAOTest {

  private UserDAO userDAO;
  private Connection mockConnection;
  private PreparedStatement mockPreparedStatement;
  private ResultSet mockResultSet;
  private MockedStatic<DBConnection> mockedDBConnection;

  @BeforeEach
  void setUp() throws SQLException {
    userDAO = new UserDAO();
    mockConnection = mock(Connection.class);
    mockPreparedStatement = mock(PreparedStatement.class);
    mockResultSet = mock(ResultSet.class);

    // Chặn gọi DBConnection thật, thay bằng mockConnection
    mockedDBConnection = Mockito.mockStatic(DBConnection.class);
    mockedDBConnection.when(DBConnection::getConnection).thenReturn(mockConnection);

    // Giả lập prepareStatement mặc định
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
  }

  @AfterEach
  void tearDown() {
    mockedDBConnection.close();
  }

  // --- 1. Test isFieldExists ---
  @Test
  void testIsFieldExists_ReturnsTrue_WhenExists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);

    boolean result = userDAO.isFieldExists("username", "diep_nguyen");
    assertTrue(result);
    verify(mockPreparedStatement).setString(1, "diep_nguyen");
  }

  @Test
  void testIsFieldExists_ReturnsFalse_WhenNotExists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(false);

    boolean result = userDAO.isFieldExists("email", "diep@example.com");
    assertFalse(result);
  }

  // --- 2. Test checkLogin ---
  @Test
  void testCheckLogin_Success() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);

    // Mock dữ liệu trả về cho UserFactory
    when(mockResultSet.getInt("user_id")).thenReturn(1);
    when(mockResultSet.getString("username")).thenReturn("diep_nguyen");
    when(mockResultSet.getString("role")).thenReturn("USER");

    User user = userDAO.checkLogin("diep_nguyen", "123456");

    assertNotNull(user);
    assertEquals(1, user.getId());
    assertEquals("diep_nguyen", user.getUsername());
  }

  @Test
  void testCheckLogin_Fail_WrongCredentials() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(false);

    User user = userDAO.checkLogin("wrong", "wrong");
    assertNull(user);
  }

  // --- 3. Test register ---
  @Test
  void testRegister_Success_ReturnsGeneratedId() throws SQLException {
    // Hàm register dùng Statement.RETURN_GENERATED_KEYS
    when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
        .thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    when(mockPreparedStatement.getGeneratedKeys()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getInt(1)).thenReturn(99);

    User newUser = new Bidder();
    newUser.setId(1);
    newUser.setEmail("new@test.com");
    newUser.setPhone("0123456789");

    boolean result = userDAO.register(newUser);

    assertTrue(result);
    assertEquals(99, newUser.getId());
  }

  @Test
  void testRegister_Fail() throws SQLException {
    when(mockConnection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
        .thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeUpdate()).thenReturn(0);

    User newUser = new Bidder();
    boolean result = userDAO.register(newUser);

    assertFalse(result);
  }

  // --- 4. Test updatePassword ---
  @Test
  void testUpdatePassword_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = userDAO.updatePassword("diep_nguyen", "newPass");
    assertTrue(result);
  }

  // --- 5. Test updateRole ---
  @Test
  void testUpdateRole_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = userDAO.updateRole(1, "ADMIN");
    assertTrue(result);
  }

  // --- 6. Test getUserById ---
  // --- 6. Test getUserById ---
  @Test
  void testGetUserById_Exists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);

    when(mockResultSet.getInt("user_id")).thenReturn(5);
    when(mockResultSet.getString("username")).thenReturn("user5");

    // CẬP NHẬT: Thêm giả lập cột role để UserFactory có thể phân loại và tạo Object
    when(mockResultSet.getString("role")).thenReturn("BIDDER");

    User user = userDAO.getUserById(5);

    assertNotNull(user, "User trả về không được null");
    assertEquals(5, user.getId());
    assertEquals("user5", user.getUsername());
  }

  // --- 7. Test getUserName ---
  @Test
  void testGetUserName_Exists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getString("username")).thenReturn("diep_nguyen");

    String name = userDAO.getUserName(1);
    assertEquals("diep_nguyen", name);
  }

  @Test
  void testGetUserName_NotExists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(false);

    String name = userDAO.getUserName(999);
    assertEquals("Unknown", name);
  }

  // --- 8. Test getBalance ---
  @Test
  void testGetBalance_Exists() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getDouble("balance")).thenReturn(150000.0);

    double balance = userDAO.getBalance(1);
    assertEquals(150000.0, balance);
  }

  // --- 9. Test updateProfile ---
  @Test
  void testUpdateProfile_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);

    User newUser = new Bidder();
    newUser.setId(1);
    newUser.setEmail("new@test.com");
    newUser.setPhone("0123456789");

    boolean result = userDAO.updateProfile(newUser);
    assertTrue(result);
    verify(mockPreparedStatement).setString(1, "new@test.com");
    verify(mockPreparedStatement).setString(2, "0123456789");
  }

  // --- 10. Test getAllUsers ---
  @Test
  void testGetAllUsers_ReturnsList() throws SQLException {
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    // Giả lập database trả về 2 dòng, dòng thứ 3 là hết dữ liệu
    when(mockResultSet.next()).thenReturn(true, true, false);

    when(mockResultSet.getInt("user_id")).thenReturn(1, 2);
    when(mockResultSet.getString("username")).thenReturn("user1", "user2");

    // CẬP NHẬT: Thêm giả lập cột role lần lượt cho 2 dòng dữ liệu
    when(mockResultSet.getString("role")).thenReturn("BIDDER", "SELLER");

    List<User> list = userDAO.getAllUsers();

    assertEquals(2, list.size());
    assertEquals("user1", list.get(0).getUsername());
    assertEquals("user2", list.get(1).getUsername());
  }

  // --- 11. Test updateStatus ---
  @Test
  void testUpdateStatus_Success() throws SQLException {
    when(mockPreparedStatement.executeUpdate()).thenReturn(1);
    boolean result = userDAO.updateStatus(1, "BLOCKED");
    assertTrue(result);
    verify(mockPreparedStatement).setString(1, "BLOCKED");
    verify(mockPreparedStatement).setInt(2, 1);
  }
}