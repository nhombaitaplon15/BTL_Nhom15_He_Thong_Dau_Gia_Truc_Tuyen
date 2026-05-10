package com.auction.server.dao;

import com.auction.common.model.Auction;
import com.auction.server.dao.DBConnection;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {
    // dùng cho admin duyệt giá
    public boolean updateStatus(int auctionId, String newStatus) {
        String sql = "UPDATE auctions SET auction_status = ? WHERE auction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // cập nhật giá mới, người mới ra giá
    public boolean updateBid(int auctionId, int winnerId, double bidAmount) {
        String sql = "UPDATE auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 WHERE auction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, bidAmount);
            ps.setInt(2, winnerId);
            ps.setInt(3, auctionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // lấy toàn bộ danh sách
    public List<Auction> getAll() {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT * FROM auctions";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                // Xử lý current_winner_id vì trong DB nó có thể là NULL
                Integer winnerId = rs.getInt("current_winner_id");
                if (rs.wasNull()) winnerId = null;

                list.add(new Auction(
                        rs.getInt("auction_id"),
                        rs.getInt("item_id"),
                        rs.getInt("seller_id"),
                        rs.getString("auction_status"),
                        rs.getDouble("starting_price"),
                        rs.getDouble("current_price"),
                        rs.getInt("total_bids"),
                        winnerId,
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
    //Cập nhật thời gian kết thúc (Gia hạn thêm 30s)

    public boolean updateEndTime(int auctionId, LocalDateTime newEndTime) {
        String sql = "UPDATE auctions SET end_time = ? WHERE auction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(newEndTime));
            ps.setInt(2, auctionId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public Auction getAuctionById(int auctionId) {
        String sql = "SELECT * FROM auctions WHERE auction_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Bạn phải dùng rs.get... để lấy giá trị từ các cột trong SQL
                    return new Auction(
                            rs.getInt("auction_id"),
                            rs.getInt("item_id"),
                            rs.getInt("seller_id"),
                            rs.getString("auction_status"), // Giả sử tên cột là auction_status
                            rs.getDouble("starting_price"),
                            rs.getDouble("current_price"),
                            rs.getInt("total_bids"),
                            rs.getInt("current_winner_id"), // Nếu cột này cho phép NULL, cần xử lý cẩn thận
                            rs.getTimestamp("start_time").toLocalDateTime(),
                            rs.getTimestamp("end_time").toLocalDateTime(),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

}