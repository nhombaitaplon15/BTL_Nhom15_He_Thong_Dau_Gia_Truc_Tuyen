package com.auction.server.dao;

import com.auction.common.model.BiddingHistory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BiddingHistoryDAO {

    /**
     * Hàm lưu lịch sử đấu giá - DÙNG CHUNG CONNECTION với Transaction của Service.
     * Thêm ném "throws SQLException" để nếu lỗi, hệ thống tự động Rollback dòng tiền.
     */
    public void saveBidRecordWithConnection(Connection conn, int auctionId, String itemName, int bidderId, String bidderName, double bidAmount) throws SQLException {
        String sql = "INSERT INTO bidding_history (auction_id, item_name, bidder_id, bidder_name, bid_amount, bid_time, status) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), ?)";

        // Sử dụng trực tiếp 'conn' được truyền vào, tuyệt đối không dùng try-with-resources cho Connection ở đây
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            pstmt.setString(2, itemName);
            pstmt.setInt(3, bidderId);
            pstmt.setString(4, bidderName);
            pstmt.setDouble(5, bidAmount);
            pstmt.setString(6, "Đang dẫn đầu");

            pstmt.executeUpdate();
        }

        // Tự động cập nhật các lượt đặt giá "Đang dẫn đầu" trước đó của phiên này thành "Bị vượt mặt"
        String updateSql = "UPDATE bidding_history SET status = 'Bị vượt mặt' " +
                "WHERE auction_id = ? AND bidder_id != ? AND status = 'Đang dẫn đầu'";
        try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
            updatePstmt.setInt(1, auctionId);
            updatePstmt.setInt(2, bidderId);
            updatePstmt.executeUpdate();
        }
    }

    /**
     * Hàm đọc lịch sử từ PostgreSQL dựa theo ID của người dùng để đổ lên TableView giao diện
     */
    public List<BiddingHistory> getHistoryByBidderId(int bidderId) {
        List<BiddingHistory> list = new ArrayList<>();
        String sql = "SELECT id, auction_id, item_name, bidder_id, bidder_name, bid_amount, bid_time, status " +
                "FROM bidding_history WHERE bidder_id = ? ORDER BY bid_time DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, bidderId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    BiddingHistory history = new BiddingHistory(
                            rs.getInt("id"),
                            rs.getInt("auction_id"),
                            rs.getString("item_name"),
                            rs.getInt("bidder_id"),
                            rs.getString("bidder_name"),
                            rs.getDouble("bid_amount"),
                            rs.getTimestamp("bid_time").toLocalDateTime(),
                            rs.getString("status")
                    );
                    list.add(history);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}