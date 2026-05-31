package com.auction.client.core;

import com.auction.common.model.User;

/**
 * ClientSession — Lưu thông tin User đang đăng nhập phía Client.
 *
 * Thay thế cho các chỗ hard-code "SELLER_ID = 11" trong controller.
 * Được set sau khi server trả về LOGIN_SUCCESS.
 *
 * Cách dùng:
 *   // Sau khi login thành công:
 *   ClientSession.getInstance().setCurrentUser(user);
 *
 *   // Trong controller:
 *   int myId = ClientSession.getInstance().getUserId();
 *
 * ĐẶT TẠI: client/src/main/java/com/auction/client/network/ClientSession.java
 */
public class ClientSession {

  private static final ClientSession instance = new ClientSession();

  private User currentUser;

  private ClientSession() {}

  public static ClientSession getInstance() { return instance; }

  public void setCurrentUser(User user) { this.currentUser = user; }

  public User getCurrentUser() { return currentUser; }

  public int getUserId() {
    return currentUser != null ? currentUser.getId() : -1;
  }

  public String getUsername() {
    return currentUser != null ? currentUser.getUsername() : "Unknown";
  }

  public String getRole() {
    return currentUser != null ? currentUser.getRole() : "";
  }

  public boolean isLoggedIn() { return currentUser != null; }

  public void clear() { currentUser = null; }
}