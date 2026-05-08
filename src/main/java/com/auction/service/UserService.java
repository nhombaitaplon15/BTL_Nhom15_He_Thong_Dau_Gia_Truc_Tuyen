package com.auction.service;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;

import java.util.HashMap;
import java.util.Map;

public class UserService {

  private static Map<String, User> userMap = new HashMap<>();

  // kiểm tra dữ liệu đã trùng chưa và đăng kí
  public void handleRegister(String user, String pass, String mail, String phone, String role, double balance) {

    if (!phone.matches("^\\d{10}$")) {
      throw new AuctionException(
          ErrorCode.INVALID_INPUT.name(),
          "Số điện thoại phải đúng 10 chữ số!"
      );
    }

    if (pass.length() < 8) {
      throw new AuctionException(
          ErrorCode.INVALID_INPUT.name(),
          "Mật khẩu quá ngắn!"
      );
    }

    if (userMap.containsKey(user)) {
      throw new AuctionException(
          ErrorCode.UNAUTHORIZED.name(),
          "Tên đăng nhập đã tồn tại!"
      );
    }

    for (User u : userMap.values()) {
      if (u.getEmail().equals(mail)) {
        throw new AuctionException(
            ErrorCode.UNAUTHORIZED.name(),
            "Email đã được sử dụng!"
        );
      }
      if (u.getPhone().equals(phone)) {
        throw new AuctionException(
            ErrorCode.UNAUTHORIZED.name(),
            "Số điện thoại đã đăng ký!"
        );
      }
    }

    int newId = userMap.size() + 1;
    User newUser;

    if ("SELLER".equalsIgnoreCase(role)) {
      newUser = new Seller(newId, user, mail, pass, phone, "ACTIVE", balance);
    } else {
      newUser = new Bidder(newId, user, mail, pass, phone, "ACTIVE", balance);
    }

    userMap.put(user, newUser);
    System.out.println("Đăng ký thành công: " + user);
  }

  // kiểm tra dữ liệu khi đăng nhập
  public User handleLogin(String username, String password) {

    User user = userMap.get(username);

    if (user == null) {
      throw new AuctionException(
          ErrorCode.USER_NOT_FOUND.name(),
          "Người dùng không tồn tại"
      );
    }

    if (!user.getPassword().equals(password)) {
      throw new AuctionException(
          ErrorCode.UNAUTHORIZED.name(),
          "Sai mật khẩu!"
      );
    }

    System.out.println("Login OK: " + username);
    return user;
  }

  // thay đổi mật khẩu
  public void handleChangePassword(User currentUser, String oldP, String newP, String confirmP) {

    if (!currentUser.getPassword().equals(oldP)) {
      throw new AuctionException(
          ErrorCode.UNAUTHORIZED.name(),
          "Mật khẩu cũ không đúng!"
      );
    }

    if (newP.equals(oldP)) {
      throw new AuctionException(
          ErrorCode.INVALID_INPUT.name(),
          "Mật khẩu mới không được trùng mật khẩu cũ!"
      );
    }

    if (newP.length() < 8) {
      throw new AuctionException(
          ErrorCode.INVALID_INPUT.name(),
          "Mật khẩu phải >= 8 ký tự!"
      );
    }

    if (!newP.equals(confirmP)) {
      throw new AuctionException(
          ErrorCode.INVALID_INPUT.name(),
          "Xác nhận mật khẩu không khớp!"
      );
    }

    currentUser.setPassword(newP);

    System.out.println("Đổi mật khẩu thành công!");
  }
  public void clearData() {
    userMap.clear();
  }
}