package com.auction.server.dao;

import com.auction.common.model.Auction;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {

    // --- CÁC HÀM TRUY VẤN (QUERIES) ---

    public List<Auction> getAuctionsByStatus(String status) {
        return queryList("SELECT * FROM auctions WHERE auction_status = ?", status);
    }

    public List<Auction> getAll() {
        return queryList("SELECT * FROM auctions");
    }

    public Auction getAuctionById(int id) {
        List<Auction> results = queryList("SELECT * FROM auctions WHERE auction_id = ?", id);
        return results.isEmpty() ? null : results.get(0);
    }

    // --- CÁC HÀM CẬP NHẬT (UPDATES) ---

    public boolean updateStatus(int id, String status) {
        return executeUpdate("UPDATE auctions SET auction_status = ? WHERE auction_id = ?", status, id);
    }

    public boolean updateBid(Connection conn, int id, int winnerId, double amount) throws SQLException {
        return executeUpdate(conn, "UPDATE auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 WHERE auction_id = ? AND current_price < ?",
                amount, winnerId, id, amount);
    }

    public boolean updateEndTime(Connection conn, int id, LocalDateTime end) throws SQLException {
        return executeUpdate(conn, "UPDATE auctions SET end_time = ? WHERE auction_id = ?", Timestamp.valueOf(end), id);
    }

    // --- BỘ MÁY THỰC THI (HELPER METHODS) ---

    private List<Auction> queryList(String sql, Object... params) {
        List<Auction> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = prepare(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private boolean executeUpdate(String sql, Object... params) {
        try (Connection conn = DBConnection.getConnection()) {
            return executeUpdate(conn, sql, params);
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    private boolean executeUpdate(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(conn, sql, params)) {
            return ps.executeUpdate() > 0;
        }
    }

    private PreparedStatement prepare(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    private Auction map(ResultSet rs) throws SQLException {
        int winnerId = rs.getInt("current_winner_id");
        return new Auction(
                rs.getInt("auction_id"), rs.getInt("item_id"), rs.getInt("seller_id"),
                rs.getString("auction_status"), rs.getDouble("starting_price"),
                rs.getDouble("current_price"), rs.getInt("total_bids"),
                rs.wasNull() ? null : winnerId,
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}