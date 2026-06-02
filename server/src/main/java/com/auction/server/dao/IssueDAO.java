package com.auction.server.dao;

import com.auction.common.model.IssueRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * IssueDAO — xử lý tất cả thao tác CSDL liên quan đến bảng issues.
 *
 * Schema bảng cần có:
 *   CREATE TABLE IF NOT EXISTS issues (
 *       id          SERIAL PRIMARY KEY,
 *       user_id     INT NOT NULL,
 *       auction_id  INT NOT NULL,
 *       issue_type  VARCHAR(200),
 *       description TEXT,
 *       created_at  TIMESTAMP DEFAULT NOW()
 *   );
 */
public class IssueDAO {

    /**
     * Ghi một báo cáo mới vào DB.
     * @return true nếu insert thành công, false nếu lỗi
     */
    public boolean insertIssue(int userId, int auctionId, String issueType, String description) {
        String sql = "INSERT INTO issues (user_id, auction_id, issue_type, description, created_at)"
                + " VALUES (?, ?, ?, ?, NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, auctionId);
            stmt.setString(3, issueType);
            stmt.setString(4, description);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("❌ IssueDAO.insertIssue: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy toàn bộ báo cáo trong DB, sắp xếp mới nhất trước.
     */
    public List<IssueRecord> getAllIssues() {
        List<IssueRecord> list = new ArrayList<>();
        String sql = "SELECT id, user_id, auction_id, issue_type, description, created_at"
                + " FROM issues ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                IssueRecord rec = new IssueRecord(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getInt("auction_id"),
                        rs.getString("issue_type"),
                        rs.getString("description"),
                        rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime()
                                : null
                );
                list.add(rec);
            }
        } catch (SQLException e) {
            System.err.println("❌ IssueDAO.getAllIssues: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Lấy danh sách báo cáo cho một phiên đấu giá cụ thể.
     */
    public List<IssueRecord> getIssuesByAuction(int auctionId) {
        List<IssueRecord> list = new ArrayList<>();
        String sql = "SELECT id, user_id, auction_id, issue_type, description, created_at"
                + " FROM issues WHERE auction_id = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, auctionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    IssueRecord rec = new IssueRecord(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getInt("auction_id"),
                            rs.getString("issue_type"),
                            rs.getString("description"),
                            rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toLocalDateTime()
                                    : null
                    );
                    list.add(rec);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ IssueDAO.getIssuesByAuction: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Đếm tổng số báo cáo hiện có trong DB (dùng cho KPI Disputes ở Admin HomePage).
     */
    public int countAllIssues() {
        String sql = "SELECT COUNT(*) FROM issues";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("❌ IssueDAO.countAllIssues: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Kiểm tra một auction có báo cáo hay không.
     */
    public boolean hasIssues(int auctionId) {
        String sql = "SELECT 1 FROM issues WHERE auction_id = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, auctionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("❌ IssueDAO.hasIssues: " + e.getMessage());
        }
        return false;
    }
}
