package com.auction.service;

import com.auction.common.model.*;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.factory.UserFactory;
import com.auction.server.dao.UserDAO;

public class UserService {
    private UserDAO userDAO = new UserDAO();

    // 1. XỬ LÝ LOGIC ĐĂNG KÝ (Nhận String trực tiếp từ Controller - Gọn gàng, không lo lớp Abstract)
    public boolean handleRegister(String username, String password, String email, String phone) {
        try {
            // Kiểm tra định dạng (Truyền các chuỗi đã lấy ra)
            validateFormat(password, phone);

            // Kiểm tra trùng lặp trong DB
            checkDuplicates(username, email, phone);
            User finalUser = UserFactory.createUser(0, username, email, password, phone, "ACTIVE", "BIDDER", 0.0);

            // Lưu vào SQL
            if (!userDAO.register(finalUser)) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống: Không thể lưu tài khoản!");
            }

            System.out.println(">>> [REGISTER SUCCESS] User: " + username);
            return true;

        } catch (AuctionException e) {
            throw e;
        } catch (Exception e) {
            // CẢI TIẾN CRITICAL: Không nuốt lỗi nữa, ném thẳng thông điệp gốc từ SQL/System lên Giao diện để debug
            System.err.println("Lỗi đăng ký tại Service: " + e.getMessage());
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database/Hệ thống gốc: " + e.getMessage());
        }
    }
    // 2. XỬ LÝ ĐĂNG NHẬP (Đã sửa: Nhận String trực tiếp thay vì Object User phiền phức)
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

    // 3. ĐỔI MẬT KHẨU
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
        currentUser.setPassword(newP); // Cập nhật đối tượng trong RAM để tránh lệch giao diện
    }

    // 4. XỬ LÝ LOGIC QUÊN MẬT KHẨU
    public void handleForgotPassword(String username, String phone, String newPass) {
        // Guard: Kiểm tra username có tồn tại không
        if (!userDAO.isFieldExists("username", username)) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Tên đăng nhập không tồn tại!");
        }

        // Thực thi reset mật khẩu
        if (!userDAO.updatePassword(username, newPass)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi: Không thể reset mật khẩu!");
        }
    }

    // 5. LẤY USER THEO ID
    public User getUserById(int userId) {
        try {
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

    // 6. CHUYỂN ĐỔI VAI TRÒ (Giữ nguyên ID)
    public void handleSwitchRole(User currentUser) {
        // Guard: Admin không được đổi vai
        if (currentUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không thể thực hiện chức năng này!");
        }
        String targetRole = currentUser.getRole().equalsIgnoreCase("BIDDER") ? "SELLER" : "BIDDER";

        // Cập nhật SQL
        if (userDAO.updateRole(currentUser.getId(), targetRole)) {
            currentUser.setRole(targetRole); // Đồng bộ đối tượng Java trong phiên làm việc
            System.out.println(">>> Đã chuyển sang vai trò: " + targetRole);
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi: Không thể cập nhật vai trò!");
        }
    }

    // CÁC HÀM HỖ TRỢ TRÁNH CODE SMELLS (VALIDATION)
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