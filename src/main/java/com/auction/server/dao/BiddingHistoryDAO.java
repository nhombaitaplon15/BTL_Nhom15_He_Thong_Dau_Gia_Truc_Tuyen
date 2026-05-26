package com.auction.server.dao;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory; // Đảm bảo đã import model này
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BiddingHistoryDAO {

    // ========================================================================
    // HÀM THÊM MỚI 1: Phục vụ ghi nhận lịch sử ngay trong cụm Transaction đặt giá
    // ========================================================================
    public void saveBidRecordWithConnection(Connection conn, int auctionId, String itemName, int bidderId, String username, double amount) throws SQLException {
        String sql = """
                INSERT INTO bidding_history (auction_id, item_name, bidder_id, bidder_name, bid_amount, bid_time, status)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'SUCCESS')
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            ps.setString(2, itemName);
            ps.setInt(3, bidderId);
            ps.setString(4, username);
            ps.setDouble(5, amount);
            ps.executeUpdate();
        }
    }

    // ========================================================================
    // HÀM THÊM MỚI 2: Đồng bộ kiểu dữ liệu BiddingHistory trả về cho BiddingService
    // ========================================================================
    public List<BiddingHistory> getHistoryByBidderId(int bidderId) {
        List<BiddingHistory> list = new ArrayList<>();
        String sql = """
                SELECT id, auction_id, bidder_id, bid_amount, bid_time 
                FROM bidding_history 
                WHERE bidder_id = ? 
                ORDER BY bid_time DESC
                """;

        // Sử dụng chung lớp kết nối DatabaseConnection để đồng bộ với BiddingService
        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BiddingHistory history = new BiddingHistory();
                    history.setId(rs.getInt("id"));
                    history.setAuctionId(rs.getInt("auction_id"));
                    history.setBidderId(rs.getInt("bidder_id"));
                    history.setBidAmount(rs.getDouble("bid_amount"));
                    if (rs.getTimestamp("bid_time") != null) {
                        history.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                    }
                    list.add(history);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi lấy BiddingHistory từ DB: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // ========================================================================
    // HÀM CŨ: Giữ nguyên để phục vụ hiển thị BidHistoryRow lên UI TableView JavaFX
    // ========================================================================
    public List<BidHistoryRow> getHistoryByUser(int userId) {
        List<BidHistoryRow> list = new ArrayList<>();
        String sql = """
                SELECT id, auction_id, item_name, bid_amount, bid_time, status 
                FROM bidding_history 
                WHERE bidder_id = ? 
                ORDER BY bid_time DESC
                """;

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String timeStr = "";
                    if (rs.getTimestamp("bid_time") != null) {
                        timeStr = rs.getTimestamp("bid_time").toString();
                    }

                    BidHistoryRow row = new BidHistoryRow(
                            rs.getInt("id"),
                            rs.getInt("auction_id"),
                            rs.getString("item_name"),
                            rs.getDouble("bid_amount"),
                            timeStr,
                            rs.getString("status")
                    );
                    list.add(row);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi lấy lịch sử đặt giá từ DB: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }
}