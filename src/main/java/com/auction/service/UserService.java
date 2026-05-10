package com.auction.service;

import com.auction.common.model.*;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.factory.UserFactory;
import com.auction.server.dao.UserDAO;
public class UserService {
    private UserDAO userDAO = new UserDAO();
    // xử lí logic đăng kí mặc định là bidder không cho phép đăng kí là admin
    public void handleRegister(String user, String pass, String mail, String phone) {
        // kiểm tra định dạng
        validateFormat(pass, phone);
        // kiểu tra trùng lặp
        checkDuplicates(user, mail, phone);
        // khởi tạo đối tượng bidder
        // ID = 0 (DB tự tăng), Role = BIDDER, Status = ACTIVE, Balance = 0.0
        User newUser = UserFactory.createUser(0, user, mail, pass, phone, "ACTIVE", "BIDDER", 0.0);
        // lưu vào sql
        if (!userDAO.register(newUser)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống: Không thể lưu tài khoản!");
        }
        System.out.println(">>> Đăng ký thành công User: " + user);
    }
    // xử lí đăng nhập
    public User handleLogin(String username, String password) {
        User user = userDAO.checkLogin(username, password);
        // Guard: Không tìm thấy user hoặc sai pass
        if (user == null) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Tài khoản hoặc mật khẩu không chính xác!");
        }
        // Guard: Tài khoản bị khóa
        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Tài khoản hiện đang bị khóa bởi Admin!");
        }
        return user;
    }
    // đổi mật khẩu
    public void handleChangePassword(User currentUser, String oldP, String newP, String confirmP) {
        // Guards: Kiểm tra logic mật khẩu
        if (!currentUser.getPassword().equals(oldP))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Mật khẩu cũ không đúng!");
        if (newP.equals(oldP))
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Mật khẩu mới không được giống mật khẩu cũ!");
        if (newP.length() < 8)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Mật khẩu mới phải từ 8 ký tự!");
        if (!newP.equals(confirmP))
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Xác nhận mật khẩu không khớp!");
        // Update SQL
        if (!userDAO.updatePassword(currentUser.getUsername(), newP)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database: Không thể cập nhật mật khẩu!");
        }
        currentUser.setPassword(newP); // Cập nhật đối tượng trong RAM
        // do người dùng thao tác trực tiếp trên ứng dụng nên nếu k lưu thì gây lag hoặc sai lệch giao diện
    }
    // xử lí logic quên mật khẩu
    public void handleForgotPassword(String username, String phone, String newPass) {
        // Guard: Kiểm tra username và phone có khớp trong DB không
        // Lưu ý: Bạn cần dùng isFieldExists hoặc viết thêm hàm checkPhoneMatch trong DAO
        if (!userDAO.isFieldExists("username", username)) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Tên đăng nhập không tồn tại!");
        }

        // Thực thi reset
        if (!userDAO.updatePassword(username, newPass)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi: Không thể reset mật khẩu!");
        }
    }
    // chuyển đổi vai trò, giữ nguyên id
    public void handleSwitchRole(User currentUser) {
        // Guard: Admin không được đổi vai
        if (currentUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không thể thực hiện chức năng này!");
        }
        String targetRole = currentUser.getRole().equalsIgnoreCase("BIDDER") ? "SELLER" : "BIDDER";
        // Cập nhật SQL
        if (userDAO.updateRole(currentUser.getId(), targetRole)) {
            currentUser.setRole(targetRole); // Đồng bộ đối tượng Java
            System.out.println(">>> Đã chuyển sang vai trò: " + targetRole);
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi: Không thể cập nhật vai trò!");
        }
    }
    // hàm hỗ trợ tránh code smells
    private void validateFormat(String pass, String phone) {
        if (!phone.matches("^\\d{10}$")) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số điện thoại phải có đúng 10 chữ số!");
        }
        if (pass.length() < 8) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Mật khẩu phải từ 8 ký tự trở lên!");
        }
    }
    private void checkDuplicates(String user, String mail, String phone) {
        if (userDAO.isFieldExists("username", user))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Tên đăng nhập đã tồn tại!");
        if (userDAO.isFieldExists("email", mail))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Email đã được sử dụng!");
        if (userDAO.isFieldExists("phone", phone))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Số điện thoại đã đăng ký!");
    }

}