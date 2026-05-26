package com.auction.server.dao;

import com.auction.common.model.Auction;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {

    // --- CÁC HÀM TRUY VẤN (QUERIES) ---

    public List<Auction> getAuctionsByStatus(String status) {
        return queryList("SELECT * FROM public.auctions WHERE auction_status = ?", status);
    }

    public List<Auction> getAll() {
        return queryList("SELECT * FROM public.auctions");
    }

    public Auction getAuctionById(int id) {
        List<Auction> results = queryList("SELECT * FROM public.auctions WHERE auction_id = ?", id);
        return results.isEmpty() ? null : results.get(0);
    }

    // ⚡ THÊM MỚI: Phục vụ kéo giá Realtime hiển thị trực tiếp lên Card Trang chủ
    public Auction getAuctionByItemId(int itemId) {
        List<Auction> results = queryList("SELECT * FROM public.auctions WHERE item_id = ? AND auction_status = 'RUNNING' LIMIT 1", itemId);
        if (results.isEmpty()) {
            results = queryList("SELECT * FROM public.auctions WHERE item_id = ? ORDER BY created_at DESC LIMIT 1", itemId);
        }
        return results.isEmpty() ? null : results.get(0);
    }

    // --- CÁC HÀM CẬP NHẬT (UPDATES) ---

    public boolean insertAuction(Auction a) {
        String sql = "INSERT INTO public.auctions (item_id, seller_id, auction_status, starting_price, current_price, total_bids, current_winner_id, start_time, end_time, created_at) " +
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
        return executeUpdate("UPDATE public.auctions SET auction_status = ? WHERE auction_id = ?", status, id);
    }

    public boolean updateStatus(Connection conn, int auctionId, String status) throws SQLException {
        String sql = "UPDATE public.auctions SET auction_status = ? WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, auctionId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateBid(Connection conn, int id, int winnerId, double amount) throws SQLException {
        return executeUpdate(conn, "UPDATE public.auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 WHERE auction_id = ? AND current_price < ?",
                amount, winnerId, id, amount);
    }

    public boolean updateBid(int id, int winnerId, double amount) {
        String sql = "UPDATE public.auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 " +
                "WHERE auction_id = ? AND current_price < ?";
        return executeUpdate(sql, amount, winnerId, id, amount);
    }

    public boolean updateEndTime(Connection conn, int id, LocalDateTime end) throws SQLException {
        return executeUpdate(conn, "UPDATE public.auctions SET end_time = ? WHERE auction_id = ?", Timestamp.valueOf(end), id);
    }

    public boolean updateEndTime(int id, LocalDateTime end) {
        String sql = "UPDATE public.auctions SET end_time = ? WHERE auction_id = ?";
        return executeUpdate(sql, Timestamp.valueOf(end), id);
    }

    public List<Auction> getAuctionsByWinner(int winnerId) {
        return queryList("SELECT * FROM public.auctions WHERE current_winner_id = ? AND auction_status = 'FINISHED'", winnerId);
    }

    // ========================================================================
    // ⚡ HÀM TRANSACTION ĐẶT GIÁ REALTIME: Đã đồng bộ chuẩn 100% Postgres
    // ========================================================================
    public boolean executePlaceBidTransaction(int auctionId, int userId, double bidAmount) {
        String updateWalletSql = "UPDATE public.users SET balance = balance - ? WHERE user_id = ?";
        String updateAuctionSql = "UPDATE public.auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 WHERE auction_id = ? AND current_price < ?";
        String insertHistorySql = "INSERT INTO public.bidding_history (auction_id, item_name, bidder_id, bidder_name, bid_amount, bid_time, status) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'SUCCESS')";

        String queryItemNameSql = "SELECT name FROM public.items WHERE item_id = (SELECT item_id FROM public.auctions WHERE auction_id = ?)";
        String queryBidderNameSql = "SELECT username FROM public.users WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;
            conn.setAutoCommit(false);

            String itemName = "Vật phẩm";
            String bidderName = "Người dùng";

            // 1. Lấy tên sản phẩm
            try (PreparedStatement psItem = conn.prepareStatement(queryItemNameSql)) {
                psItem.setInt(1, auctionId);
                try (ResultSet rs = psItem.executeQuery()) {
                    if (rs.next()) itemName = rs.getString("name");
                }
            }

            // 2. Lấy tên người đấu
            try (PreparedStatement psUser = conn.prepareStatement(queryBidderNameSql)) {
                psUser.setInt(1, userId);
                try (ResultSet rs = psUser.executeQuery()) {
                    if (rs.next()) bidderName = rs.getString("username");
                }
            }

            try (PreparedStatement psWallet = conn.prepareStatement(updateWalletSql);
                 PreparedStatement psAuction = conn.prepareStatement(updateAuctionSql);
                 PreparedStatement psHistory = conn.prepareStatement(insertHistorySql)) {

                // 3. Khấu trừ tiền ví thành viên
                psWallet.setDouble(1, bidAmount);
                psWallet.setInt(2, userId);
                psWallet.executeUpdate();

                // 4. Nâng giá đỉnh phiên đấu giá
                psAuction.setDouble(1, bidAmount);
                psAuction.setInt(2, userId);
                psAuction.setInt(3, auctionId);
                psAuction.setDouble(4, bidAmount);
                int affectedRows = psAuction.executeUpdate();

                // Nếu có người khác đặt giá cao hơn trước, lệnh sẽ tự hủy phòng vệ
                if (affectedRows == 0) {
                    conn.rollback();
                    return false;
                }

                // 5. Ghi biên bản vào lịch sử
                psHistory.setInt(1, auctionId);
                psHistory.setString(2, itemName);
                psHistory.setInt(3, userId);
                psHistory.setString(4, bidderName);
                psHistory.setDouble(5, bidAmount);
                psHistory.executeUpdate();

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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

    public String getItemDescription(int itemId) {
        String sql = "SELECT description FROM public.items WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("description");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return "Không có mô tả cho vật phẩm này.";
    }

    public String getItemImagePath(int itemId) {
        String sql = "SELECT img_item FROM public.items WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("img_item");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean deleteAll() {
        return executeUpdate("DELETE FROM public.auctions");
    }
    public List<com.auction.common.model.BidHistoryRow> getBidHistoryByAuction(int auctionId) {
        List<com.auction.common.model.BidHistoryRow> list = new ArrayList<>();
        String sql = "SELECT id, auction_id, bidder_name, bid_amount, bid_time, status " +
                "FROM public.bidding_history WHERE auction_id = ? ORDER BY bid_time DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String timeStr = rs.getTimestamp("bid_time") != null ? rs.getTimestamp("bid_time").toString() : "";
                    com.auction.common.model.BidHistoryRow row = new com.auction.common.model.BidHistoryRow(
                            rs.getInt("id"),
                            rs.getInt("auction_id"),
                            rs.getString("bidder_name"), // Ánh xạ bidder_name vào biến đặt để hiển thị tên người trả giá
                            rs.getDouble("bid_amount"),
                            timeStr,
                            rs.getString("status")
                    );
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}