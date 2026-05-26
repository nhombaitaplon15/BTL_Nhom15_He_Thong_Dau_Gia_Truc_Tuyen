package com.auction.server.dao;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.factory.ItemFactory;

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
    // --- 1. THÊM MỚI PHIÊN ĐẤU GIÁ (Dùng cho scheduleAuction) ---
    public boolean insertAuction(Auction a) {
        String sql = "INSERT INTO auctions (item_id, seller_id, auction_status, starting_price, current_price, total_bids, current_winner_id, start_time, end_time, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        return executeUpdate(sql,
                a.getItemId(),
                a.getSellerId(),
                a.getAuctionStatus(),
                a.getStartingPrice(),
                a.getCurrentPrice(),
                a.getTotalBids(),
                a.getCurrentWinnerId(),
                Timestamp.valueOf(a.getStartTime()),
                Timestamp.valueOf(a.getEndTime()),
                Timestamp.valueOf(a.getCreatedAt())
        );
    }
    public boolean updateStatus(int id, String status) {
        return executeUpdate("UPDATE auctions SET auction_status = ? WHERE auction_id = ?", status, id);
    }
    public boolean updateStatus(Connection conn, int auctionId, String status) throws SQLException {
        String sql = "UPDATE auctions SET auction_status = ? WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        }
    }
    public boolean updateBid(Connection conn, int id, int winnerId, double amount) throws SQLException {
        return executeUpdate(conn, "UPDATE auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 WHERE auction_id = ? AND current_price < ?",
                amount, winnerId, id, amount);
    }
    // --- 2. CẬP NHẬT GIÁ (Bản tự đóng connection - Overload) ---
    public boolean updateBid(int id, int winnerId, double amount) {
        String sql = "UPDATE auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 " +
                "WHERE auction_id = ? AND current_price < ?";
        return executeUpdate(sql, amount, winnerId, id, amount);
    }

    public boolean updateEndTime(Connection conn, int id, LocalDateTime end) throws SQLException {
        return executeUpdate(conn, "UPDATE auctions SET end_time = ? WHERE auction_id = ?", Timestamp.valueOf(end), id);
    }
    // --- 3. CẬP NHẬT THỜI GIAN (Bản tự đóng connection - Overload) ---
    public boolean updateEndTime(int id, LocalDateTime end) {
        String sql = "UPDATE auctions SET end_time = ? WHERE auction_id = ?";
        return executeUpdate(sql, Timestamp.valueOf(end), id);
    }
    public List<Auction> getAuctionsByWinner(int winnerId) {
        return queryList("SELECT * FROM auctions WHERE current_winner_id = ? AND auction_status = 'FINISHED'", winnerId);
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

        // Lấy an toàn để tránh NullPointerException khi ngày tháng bị rỗng (NULL)
        Timestamp start = rs.getTimestamp("start_time");
        Timestamp end = rs.getTimestamp("end_time");
        Timestamp created = rs.getTimestamp("created_at");

        return new Auction(
            rs.getInt("auction_id"), rs.getInt("item_id"), rs.getInt("seller_id"),
            rs.getString("auction_status"), rs.getDouble("starting_price"),
            rs.getDouble("current_price"), rs.getInt("total_bids"),
            rs.wasNull() ? null : winnerId,
            start != null ? start.toLocalDateTime() : null,
            end != null ? end.toLocalDateTime() : null,
            created != null ? created.toLocalDateTime() : null
        );
    }
    public boolean deleteAll() {
        return executeUpdate("DELETE FROM auctions");
    }

  public List<AuctionItemDAO> getAuctionsBySellerStatusAndKeyword(int sellerId, String status, String keyword) {
    List<AuctionItemDAO> list = new ArrayList<>();

    // Thêm điều kiện a.seller_id = ? vào câu truy vấn
    String sql = "SELECT i.*, a.* " +
        "FROM items i " +
        "INNER JOIN auctions a ON i.item_id = a.item_id " +
        "WHERE a.seller_id = ? AND a.auction_status = ? AND i.name ILIKE ?";

    // Truyền thêm sellerId vào hàm prepare
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = prepare(conn, sql, sellerId, status, "%" + keyword + "%");
         ResultSet rs = ps.executeQuery()) {

      while (rs.next()) {
        Item item = ItemFactory.createFromResultSet(rs);
        Auction auction = map(rs);
        list.add(new AuctionItemDAO(item, auction));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return list;
  }
    public List<AuctionItemDAO> getFinishedAuctionsBySeller(int sellerId, String keyword) {
        List<AuctionItemDAO> list = new ArrayList<>();

        String sql = "SELECT i.*, a.* " +
            "FROM items i " +
            "INNER JOIN auctions a ON i.item_id = a.item_id " +
            "WHERE a.seller_id = ? " +
            "  AND a.auction_status IN ('FINISHED', 'PAID', 'CANCELED') " +
            "  AND i.name ILIKE ? " +
            "ORDER BY a.end_time DESC";  // Mới kết thúc hiện trên đầu

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = prepare(conn, sql, sellerId, "%" + keyword + "%");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Item item    = ItemFactory.createFromResultSet(rs);
                Auction auction = map(rs);
                list.add(new AuctionItemDAO(item, auction));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

}