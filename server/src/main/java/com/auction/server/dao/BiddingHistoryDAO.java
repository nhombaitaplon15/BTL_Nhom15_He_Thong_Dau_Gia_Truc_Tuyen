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

    /**
     * Thêm mới một bản ghi lịch sử đặt giá thành công (Dùng trong Transaction đặt giá)
     */
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

    /**
     * Lấy lịch sử đặt giá theo ID người dùng (Trả về model thực thể gốc BiddingHistory)
     */
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
                    history.setBidAmount(rs.getDouble("bid_amount"));
                    if (rs.getTimestamp("bid_time") != null) {
                        history.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                    }
                    list.add(history);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getHistoryByBidderId: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Lấy lịch sử đặt giá theo ID người dùng (Trả về model giao diện hiển thị BidHistoryRow)
     */
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
                            rs.getDouble("bid_amount"),
                            timeStr,
                            rs.getString("status")
                    );
                    list.add(row);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getHistoryByUser: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 🎯 ĐÃ HOÀN THIỆN LOGIC: Truy vấn lịch sử đặt giá theo mã phiên đấu giá (Auction ID)
     */
    public List<BidHistoryRow> getHistoryByAuction(int auctionId) {
        List<BidHistoryRow> list = new ArrayList<>();
        String sql = """
            SELECT id, auction_id, item_name, bid_amount, bid_time, status 
            FROM public.bidding_history 
            WHERE auction_id = ? 
            ORDER BY bid_time DESC
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String timeStr = rs.getTimestamp("bid_time") != null ? rs.getTimestamp("bid_time").toString() : "";

                    // Điền logic map dữ liệu trọn vẹn vào model hiển thị dòng lịch sử đặt giá
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
        } catch (SQLException e) {
            System.err.println("❌ Lỗi getHistoryByAuction tại phiên #" + auctionId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}
