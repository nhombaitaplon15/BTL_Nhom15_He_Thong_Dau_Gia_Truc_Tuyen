package src.main.java.com.auction.server.dao;

import com.auction.common.model.User;
import com.auction.common.factory.UserFactory;

import java.sql.*;
import java.util.*;

public class UserDAO {

    /**
     * 1. Kiểm tra trường dữ liệu (Username/Email/Phone) đã tồn tại hay chưa
     */
    public boolean isFieldExists(String fieldName, String value) {
        String sql = "SELECT 1 FROM public.users WHERE " + fieldName + " = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 2. Xác thực Đăng nhập - Sử dụng UserFactory map dữ liệu thực thể
     */
    public User checkLogin(String username, String password) {
        String sql = "SELECT * FROM public.users WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UserFactory.createUser(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("phone"),
                            rs.getString("status"),
                            rs.getString("role"),
                            rs.getDouble("balance")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi truy vấn checkLogin: " + e.getMessage());
        }
        return null;
    }

    /**
     * 3. Đăng ký tài khoản mới (Tự động nạp ID sinh tự động từ Postgres vào đối tượng)
     */
    public boolean register(User user) {
        String sql = "INSERT INTO public.users (username, password, email, phone, role, status, balance) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole());
            ps.setString(6, user.getStatus());
            ps.setDouble(7, user.getBalance());

            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int generatedId = generatedKeys.getInt(1);
                        user.setId(generatedId); // Gán ngược ID vào đối tượng User để tầng Service sử dụng trực tiếp
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi đăng ký tài khoản: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 4. Cập nhật mật khẩu (Dùng cho cả chức năng Đổi mật khẩu và Quên mật khẩu)
     */
    public boolean updatePassword(String username, String newPass) {
        String sql = "UPDATE public.users SET password = ? WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPass);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 5. Cập nhật vai trò người dùng (Đã sửa tên cột chuẩn: role và user_id)
     */
    public boolean updateRole(int userId, String newRole) {
        String sql = "UPDATE public.users SET role = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 6. Tìm kiếm thông tin thực thể User theo ID
     */
    public User getUserById(int userId) {
        String sql = "SELECT * FROM public.users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UserFactory.createUser(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("phone"),
                            rs.getString("status"),
                            rs.getString("role"),
                            rs.getDouble("balance")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 7. Lấy nhanh tên hiển thị (Username) qua ID người dùng
     */
    public String getUserName(int userId) {
        String sql = "SELECT username FROM public.users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm getUserName: " + e.getMessage());
            e.printStackTrace();
        }
        return "Unknown";
    }

    /**
     * 8. Lấy nhanh số dư ví tài khoản (Balance) qua ID người dùng
     */
    public double getBalance(int userId) {
        String sql = "SELECT balance FROM public.users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm getBalance: " + e.getMessage());
            e.printStackTrace();
        }
        return 0.0;
    }

    /**
     * 🎯 KHÔI PHỤC THÀNH CÔNG: Cập nhật hồ sơ thông tin cá nhân (Email, Số điện thoại)
     */
    public boolean updateProfile(User user) {
        String sql = "UPDATE public.users SET email = ?, phone = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPhone());
            ps.setInt(3, user.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm updateProfile: " + e.getMessage());
        }
        return false;
    }

    /**
     * 🎯 KHÔI PHỤC THÀNH CÔNG: Lấy toàn bộ danh sách User hệ thống phục vụ Admin quản lý
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM public.users";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User user = UserFactory.createUser(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password"),
                        rs.getString("phone"),
                        rs.getString("status"),
                        rs.getString("role"),
                        rs.getDouble("balance")
                );
                users.add(user);
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm getAllUsers: " + e.getMessage());
        }
        return users;
    }

    /**
     * 🎯 KHÔI PHỤC THÀNH CÔNG: Khóa hoặc kích hoạt trạng thái hoạt động của tài khoản
     */
    public boolean updateStatus(int userId, String status) {
        String sql = "UPDATE public.users SET status = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm updateStatus: " + e.getMessage());
        }
        return false;
    }
}