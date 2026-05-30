package src.main.java.com.auction.server.service;

import com.auction.common.model.User;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import com.auction.common.factory.UserFactory;
import src.main.java.com.auction.server.dao.UserDAO;

import java.util.List;

public class UserService {
    private UserDAO userDAO = new UserDAO();

    // --- 1. HỆ THỐNG ĐĂNG KÝ & ĐĂNG NHẬP ---

    /** Xử lý đăng ký tài khoản mới trực tiếp từ dữ liệu chuỗi chu chuyển */
    public boolean handleRegister(String username, String password, String email, String phone) {
        try {
            // Kiểm tra định dạng dữ liệu đầu vào
            validateFormat(password, phone, email);

            // Kiểm tra trùng lặp thuộc tính trong Database SQL
            checkDuplicates(username, email, phone);
            User finalUser = UserFactory.createUser(0, username, email, password, phone, "ACTIVE", "BIDDER", 0.0);

            // Lưu dữ liệu xuống SQL
            if (!userDAO.register(finalUser)) {
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi hệ thống: Không thể lưu tài khoản!");
            }

            System.out.println(">>> [REGISTER SUCCESS] User: " + username);
            return true;

        } catch (AuctionException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Lỗi đăng ký tại Service: " + e.getMessage());
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database/Hệ thống gốc: " + e.getMessage());
        }
    }

    /** Xử lý xác thực đăng nhập và kiểm tra trạng thái hoạt động */
    public User handleLogin(String username, String password) {
        User user = userDAO.checkLogin(username, password);

        // Guard: Không tìm thấy user hoặc nhập sai mật khẩu
        if (user == null) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Tài khoản hoặc mật khẩu không chính xác!");
        }

        // Guard: Tài khoản bị vô hiệu hóa
        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Tài khoản hiện đang bị khóa bởi Admin!");
        }

        return user;
    }

    // --- 2. QUẢN LÝ THÔNG TIN TÀI KHOẢN ---

    /** Thực thi quy trình đổi mật khẩu an toàn và đồng bộ bộ nhớ đệm RAM */
    public void handleChangePassword(User currentUser, String oldP, String newP, String confirmP) {
        if (!currentUser.getPassword().equals(oldP))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Mật khẩu cũ không đúng!");
        if (newP.equals(oldP))
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Mật khẩu mới không được giống mật khẩu cũ!");
        if (newP.length() < 8)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Mật khẩu mới phải từ 8 ký tự!");
        if (!newP.equals(confirmP))
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Xác nhận mật khẩu không khớp!");

        // Cập nhật xuống SQL
        if (!userDAO.updatePassword(currentUser.getUsername(), newP)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi Database: Không thể cập nhật mật khẩu!");
        }
        currentUser.setPassword(newP); // Cập nhật RAM gốc để tránh lệch dữ liệu hiển thị trên Client
    }

    /** Xử lý logic khôi phục mật khẩu khi người dùng quên */
    public void handleForgotPassword(String username, String phone, String newPass) {
        if (!userDAO.isFieldExists("username", username)) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Tên đăng nhập không tồn tại!");
        }

        if (!userDAO.updatePassword(username, newPass)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi: Không thể reset mật khẩu!");
        }
    }

    /** Truy vấn thực thể User dựa vào ID định danh */
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

    /** Chuyển đổi qua lại giữa vai trò Người mua (Bidder) và Người bán (Seller) */
    public void handleSwitchRole(User currentUser) {
        if (currentUser.isAdmin()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin hệ thống không thể thực hiện chức năng này!");
        }
        String targetRole = currentUser.getRole().equalsIgnoreCase("BIDDER") ? "SELLER" : "BIDDER";

        if (userDAO.updateRole(currentUser.getId(), targetRole)) {
            currentUser.setRole(targetRole); // Đồng bộ vai trò thực thể trên RAM Java Session
            System.out.println(">>> Đã chuyển sang vai trò: " + targetRole);
        } else {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi: Không thể cập nhật vai trò!");
        }
    }

    // --- 3. [CỤM TÍNH NĂNG ĐỘC QUYỀN] QUẢN TRỊ ADMIN & PROFILE ---

    /** Chỉnh sửa cập nhật hồ sơ cá nhân người dùng (Email, Số điện thoại) */
    public void updateProfile(User updatedUser) {
        if (updatedUser == null) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Dữ liệu người dùng không hợp lệ!");
        }
        User current = userDAO.getUserById(updatedUser.getId());
        if (current == null) {
            throw new AuctionException(ErrorCode.USER_NOT_FOUND.name(), "Không tìm thấy người dùng trên hệ thống!");
        }
        current.setEmail(updatedUser.getEmail());
        current.setPhone(updatedUser.getPhone());

        if (!userDAO.updateProfile(current)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không thể cập nhật hồ sơ cá nhân dưới SQL!");
        }
    }

    /** Lấy toàn bộ danh sách tài khoản phục vụ màn hình Admin Dashboard */
    public List<User> getAllUsers() {
        return userDAO.getAllUsers();
    }

    /** Khóa tài khoản người dùng vi phạm quy chế sàn (BAN USER) */
    public void banUser(Integer userId) {
        if (!userDAO.updateStatus(userId, "LOCKED")) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Thao tác khóa tài khoản thất bại!");
        }
    }

    /** Gỡ lệnh khóa, kích hoạt lại tài khoản người dùng (UNBAN USER) */
    public void unbanUser(Integer userId) {
        if (!userDAO.updateStatus(userId, "ACTIVE")) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Thao tác mở khóa tài khoản thất bại!");
        }
    }

    // --- 4. BỘ TIỀN XỬ LÝ DỮ LIỆU ĐẦU VÀO (VALIDATION) ---

    private void validateFormat(String pass, String phone, String email) {
        if (!phone.matches("^\\d{10}$")) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số điện thoại phải có đúng 10 chữ số!");
        }
        if (pass.length() < 8) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Mật khẩu phải từ 8 ký tự trở lên!");
        }
        String lowerEmail = email.toLowerCase().trim();
        if (!lowerEmail.contains("@") || !lowerEmail.contains(".")) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Cú pháp địa chỉ Email không hợp lệ!");
        }
        if (!lowerEmail.endsWith(".com") && !lowerEmail.endsWith(".net") && !lowerEmail.endsWith(".vn") && !lowerEmail.endsWith(".org") && !lowerEmail.endsWith(".edu.vn")) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Hệ thống chỉ chấp nhận các email có đuôi: .com, .net, .vn, .org, .edu.vn");
        }
    }

    private void checkDuplicates(String user, String mail, String phone) {
        if (userDAO.isFieldExists("username", user))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Tên đăng nhập này đã tồn tại!");
        if (userDAO.isFieldExists("email", mail))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Địa chỉ Email này đã được sử dụng!");
        if (userDAO.isFieldExists("phone", phone))
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Số điện thoại này đã được đăng ký!");
    }
}