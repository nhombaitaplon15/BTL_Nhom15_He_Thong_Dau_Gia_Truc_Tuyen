package com.auction.server.dao;

import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BiddingHistoryDAO {

    public void saveBidRecordWithConnection(Connection conn, int auctionId, String itemName, int bidderId, String username, double amount) throws SQLException {
        String sql = """
                INSERT INTO public.bidding_history (auction_id, item_name, bidder_id, bidder_name, bid_amount, bid_time, status)
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

    public List<BiddingHistory> getHistoryByBidderId(int bidderId) {
        List<BiddingHistory> list = new ArrayList<>();
        String sql = """
                SELECT id, auction_id, bidder_id, bid_amount, bid_time 
                FROM public.bidding_history 
                WHERE bidder_id = ? 
                ORDER BY bid_time DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BiddingHistory history = new BiddingHistory();
                    history.setId(rs.getInt("id"));
                    history.setAuctionId(rs.getInt("auction_id"));
                    history.setBidderId(rs.getInt("bidder_id"));
                    history.setBidAmount(rs.getDouble("bid_amount")); // 🎯 Đã đồng bộ cột dữ liệu
                    if (rs.getTimestamp("bid_time") != null) {
                        history.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                    }
                    list.add(history);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<BidHistoryRow> getHistoryByUser(int userId) {
        List<BidHistoryRow> list = new ArrayList<>();
        String sql = """
                SELECT id, auction_id, item_name, bid_amount, bid_time, status 
                FROM public.bidding_history 
                WHERE bidder_id = ? 
                ORDER BY bid_time DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String timeStr = rs.getTimestamp("bid_time") != null ? rs.getTimestamp("bid_time").toString() : "";
                    BidHistoryRow row = new BidHistoryRow(
                            rs.getInt("id"),
                            rs.getInt("auction_id"),
                            rs.getString("item_name"),
                            rs.getDouble("bid_amount"), // 🎯 Đã đồng bộ cột dữ liệu
                            timeStr,
                            rs.getString("status")
                    );
                    list.add(row);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}