package com.auction.service;

import com.auction.common.model.*;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.factory.UserFactory;
import com.auction.server.dao.UserDAO;
public class UserService {
    private UserDAO userDAO = new UserDAO();
    // xử lí logic đăng kí mặc định là bidder không cho phép đăng kí là admin
    public boolean handleRegister(User userRequest) {
        try {
            // 1. Lấy dữ liệu từ Object userRequest mà Client gửi lên
            String username = userRequest.getUsername();
            String password = userRequest.getPassword();
            String email = userRequest.getEmail();
            String phone = userRequest.getPhone();

            // 2. Kiểm tra định dạng (Truyền các chuỗi đã lấy ra)
            validateFormat(password, phone);

            // 3. Kiểm tra trùng lặp trong DB
            checkDuplicates(username, email, phone);

            // 4. Khởi tạo đối tượng "xịn" thông qua Factory
            // (Sử dụng tên biến khác đi, ví dụ: finalUser)
            User finalUser = UserFactory.createUser(0, username, email, password, phone, "ACTIVE", "BIDDER", 0.0);

            // 5. Lưu vào SQL
            if (!userDAO.register(finalUser)) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống: Không thể lưu tài khoản!");
            }

            System.out.println(">>> [REGISTER SUCCESS] User: " + username);
            return true; // Trả về true để ClientHandler biết mà gửi SUCCESS

        } catch (AuctionException e) {
            // Ném lỗi logic (sai định dạng, trùng lặp) ra để ClientHandler bắt và gửi về Client
            throw e;
        } catch (Exception e) {
            System.err.println("Lỗi đăng ký: " + e.getMessage());
            return false;
        }
    }
    // xử lí đăng nhập
    public User handleLogin(User credentials) {
        User user = userDAO.checkLogin(credentials.getUsername(), credentials.getPassword());
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
    public void handleForgotPassword(String username, String phone, String email,String newPass) {
        // Kiểm tra username,phone và email có khớp trong DB không
        if (!userDAO.isFieldExists("username", username)) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Tên đăng nhập không tồn tại!");
        }
        if (!userDAO.isFieldExists("phone", phone)) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Số điện thoại không khớp với tài khoản");
        }
        if (!userDAO.isFieldExists("email", email)) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Email không khớp với tài khoản");
        }
        // Thực thi reset
        if (!userDAO.updatePassword(username, newPass)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi: Không thể reset mật khẩu!");
        }
    }
    public User getUserById(int userId) {
        try {
            // gọi DAO để lấy thông tin mới nhất ừ Database
            User user = userDAO.getUserById(userId);
            if (user == null) {
                System.out.println(">>> [LỖI] Không tìm thấy User với ID: " + userId);
            }
            return user;
        } catch (Exception e) {
            System.err.println(">>> [LỖI] getUserById: " + e.getMessage());
            return null;
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

    // Hàm 1: Chỉ làm nhiệm vụ kiểm tra xem có đúng người không
    public void verifyIdentityForReset(String username, String phone, String email) {
        if (!userDAO.checkUserIdentity(username, phone, email)) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Thông tin không khớp. Vui lòng kiểm tra lại!");
        }
    }

    // Hàm 2: Gọi hàm này khi người dùng đã nhập mật khẩu mới (ở giao diện sau)
    public boolean executeResetPassword(String username, String newPass) {
        if (!userDAO.updatePassword(username, newPass)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống: Không thể đặt lại mật khẩu!");
        }
        return true;
    }

}