package com.auction.server.dao;



import java.sql.*;

import com.auction.common.factory.UserFactory;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;

import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    /**
     * Cập nhật trạng thái giao dịch (Dùng Connection từ Service bọc giao dịch)
     */
    public boolean updateTransactionStatus(Connection conn, int transId, String status) throws SQLException {
        String sql = "UPDATE public.transactions SET status=? WHERE transaction_id=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, transId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi cập nhật trạng thái giao dịch: " + e.getMessage());
            throw e; // Quăng lỗi ra ngoài để Service thực hiện Rollback dòng tiền khi gặp sự cố
        }
    }

    /**
     * Tạo phiếu nạp/rút tiền (Nghiệp vụ chuỗi hành động - Nhận Connection từ tầng Service)
     */
    public boolean createTransaction(Connection conn, int userId, double amount, String type, String status) throws SQLException {
        String sql = "INSERT INTO public.transactions (user_id, amount, transaction_type, status, created_at) VALUES (?, ?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, type);
            ps.setString(4, status);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi tạo giao dịch (bọc txn): " + e.getMessage());
            throw e;
        }
    }

    /**
     * Tạo phiếu nạp/rút tiền thông thường (Tự đóng ngắt Connection tự động)
     */
    public boolean createTransaction(int userId, double amount, String type, String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return createTransaction(conn, userId, amount, type, status);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Phê duyệt giao dịch nạp/rút tiền (Chỉ có quyền Admin gọi xử lý)
     * Sử dụng Transaction cục bộ (Commit/Rollback) để đảm bảo phiếu duyệt và số dư ví luôn đồng nhất
     */
    public boolean processApproval(int transId, int userId, double amount, String type) {
        String sqlUpdateTrans = "UPDATE public.transactions SET status = 'APPROVED' WHERE transaction_id = ?";

        // Logic toán tử linh hoạt: Nếu type là DEPOSIT thì dùng '+', nếu là WITHDRAW thì dùng '-'
        String operator = type.equalsIgnoreCase("DEPOSIT") ? "+" : "-";
        String sqlUpdateUser = "UPDATE public.users SET balance = balance " + operator + " ? WHERE user_id = ?";

        // Thêm điều kiện chặn âm tài khoản nếu thực hiện hành động rút tiền
        if (type.equalsIgnoreCase("WITHDRAW")) {
            sqlUpdateUser += " AND balance >= ?";
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // Bật chế độ quản lý Transaction thủ công
            try {
                // BƯỚC 1: Cập nhật trạng thái phiếu giao dịch sang APPROVED
                try (PreparedStatement ps1 = conn.prepareStatement(sqlUpdateTrans)) {
                    ps1.setInt(1, transId);
                    if (ps1.executeUpdate() == 0) {
                        throw new SQLException("Giao dịch không tồn tại hoặc đã xử lý trước đó!");
                    }
                }

                // BƯỚC 2: Cập nhật biến động số dư tài khoản người dùng
                try (PreparedStatement ps2 = conn.prepareStatement(sqlUpdateUser)) {
                    ps2.setDouble(1, amount);
                    ps2.setInt(2, userId);
                    if (type.equalsIgnoreCase("WITHDRAW")) {
                        ps2.setDouble(3, amount); // Gán giá trị cho điều kiện chặn 'balance >= ?'
                    }

                    if (ps2.executeUpdate() == 0) {
                        throw new SQLException("Số dư không đủ để rút hoặc tài khoản người dùng không tồn tại!");
                    }
                }

                conn.commit(); // Thành công toàn bộ -> Xác nhận lưu vào Database
                return true;
            } catch (SQLException e) {
                conn.rollback(); // Có bất kỳ lỗi gì phát sinh -> Thu hồi lại trạng thái tiền ban đầu
                System.err.println("❌ Lỗi xử lý phê duyệt giao dịch: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Từ chối đơn yêu cầu nạp/rút tiền
     */
    public boolean rejectTransaction(int transId) {
        String sql = "UPDATE public.transactions SET status = 'REJECTED' WHERE transaction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi từ chối giao dịch: " + e.getMessage());
            return false;
        }
    }

    /**
     * 🎯 KHÔI PHỤC THÀNH CÔNG: Lấy toàn bộ danh sách lịch sử yêu cầu giao dịch hệ thống
     * Phục vụ chức năng hiển thị trên giao diện quản trị của Admin
     */
    public List<TransactionRequest> getAllTransactions() {
        List<TransactionRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM public.transactions ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                // Sử dụng UserFactory dựng thực thể User rỗng tạm thời để bọc dữ liệu cho Model hiển thị
                User user = UserFactory.createUser(
                        rs.getInt("user_id"),
                        "", // username
                        "", // email
                        "", // password
                        "", // phone
                        "", // status
                        "USER", // role tạm thời
                        0.0 // balance
                );

                TransactionRequest tx = new TransactionRequest(
                        user,
                        rs.getString("transaction_type"),
                        rs.getDouble("amount"),
                        "",
                        rs.getString("status")
                );
                tx.setRequestId(rs.getInt("transaction_id"));
                tx.setRequestDate(rs.getTimestamp("created_at").toLocalDateTime());
                list.add(tx);
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi hàm getAllTransactions: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}
