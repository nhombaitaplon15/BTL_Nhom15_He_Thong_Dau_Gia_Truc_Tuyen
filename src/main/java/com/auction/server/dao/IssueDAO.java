package com.auction.server.dao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
public class IssueDAO {
    public boolean insertIssue(int userId, int auctionId, String issueType, String description) {
        String sql = "INSERT INTO issues (user_id, auction_id, issue_type, description, created_at) VALUES (?, ?, ?, ?, NOW())";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, auctionId);
            stmt.setString(3, issueType);
            stmt.setString(4, description);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0; // Trả về true nếu chèn thành công dòng dữ liệu

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi thực hiện INSERT vào bảng issues!");
            e.printStackTrace();
            return false;
        }
    }
}
