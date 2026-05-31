package com.auction.server.dao;

import com.auction.common.factory.ItemFactory;
import com.auction.common.model.Auction;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory;
import com.auction.server.core.AuctionItemDTO;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.auction.server.dao.DBConnection.getConnection;

public class AuctionDAO {

    // --- CÁC HÀM TRUY VẤN (QUERIES) ---

    public List<Auction> getAuctionsByStatus(String status) {
        // Đồng bộ lọc theo thời gian thực tế phiên còn chạy
        return queryList("SELECT * FROM public.auctions WHERE auction_status = ? AND end_time > NOW()", status);
    }

    public List<Auction> getAll() {
        return queryList("SELECT * FROM public.auctions");
    }

    public Auction getAuctionById(int id) {
        List<Auction> results = queryList("SELECT * FROM public.auctions WHERE auction_id = ?", id);
        return results.isEmpty() ? null : results.get(0);
    }

    public Auction getAuctionByItemId(int itemId) {
        List<Auction> results = queryList("SELECT * FROM public.auctions WHERE item_id = ? AND auction_status = 'RUNNING' LIMIT 1", itemId);
        if (results.isEmpty()) {
            results = queryList("SELECT * FROM public.auctions WHERE item_id = ? ORDER BY created_at DESC LIMIT 1", itemId);
        }
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Auction> getAuctionsBySeller(int sellerId) {
        return queryList("SELECT * FROM public.auctions WHERE seller_id = ?", sellerId);
    }

    public List<Auction> getAuctionsByWinner(int winnerId) {
        return queryList("SELECT * FROM public.auctions WHERE current_winner_id = ? AND auction_status = 'FINISHED'", winnerId);
    }

    public List<Auction> getLiveAuctionsByCategory(String category) {
        String sql = """
                 SELECT * FROM public.auctions 
                 WHERE item_id IN (SELECT item_id FROM public.items WHERE item_type = ?) 
                   AND auction_status = 'RUNNING' 
                   AND end_time > NOW()
                 ORDER BY end_time ASC
                 """;
        return queryList(sql, category);
    }

    // --- CÁC HÀM CẬP NHẬT & THAO TÁC CƠ SỞ DỮ LIỆU ---

    public boolean insertAuction(Auction a) {
        String sql = "INSERT INTO auctions (item_id, seller_id, auction_status, starting_price, current_price, total_bids, start_time, end_time, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        return executeUpdate(sql,
                a.getItemId(),
                a.getSellerId(),
                a.getAuctionStatus(),
                a.getStartingPrice(),
                a.getCurrentPrice(),
                a.getTotalBids(),
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
        String sql = "UPDATE public.auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 WHERE auction_id = ? AND current_price < ?";
        return executeUpdate(sql, amount, winnerId, id, amount);
    }

    public boolean updateEndTime(Connection conn, int id, LocalDateTime end) throws SQLException {
        return executeUpdate(conn, "UPDATE public.auctions SET end_time = ? WHERE auction_id = ?", Timestamp.valueOf(end), id);
    }

    public boolean updateEndTime(int id, LocalDateTime end) {
        String sql = "UPDATE public.auctions SET end_time = ? WHERE auction_id = ?";
        return executeUpdate(sql, Timestamp.valueOf(end), id);
    }

    // --- LOGIC GIAO DỊCH ĐẶT GIÁ NGUYÊN TỬ VÀ CHỐT PHIÊN ---

    /** Thực hiện đặt giá: Trừ tiền ví + Cập nhật thực thể đấu giá + Ghi lịch sử đặt giá */
    public boolean executePlaceBidTransaction(int auctionId, int userId, double bidAmount) {
        String updateWalletSql = "UPDATE public.users SET balance = balance - ? WHERE user_id = ?";
        String updateAuctionSql = "UPDATE public.auctions SET current_price = ?, current_winner_id = ?, total_bids = total_bids + 1 WHERE auction_id = ? AND current_price < ?";
        String insertHistorySql = "INSERT INTO public.bidding_history (auction_id, item_name, bidder_id, bidder_name, bid_amount, bid_time, status) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'SUCCESS')";

        String queryItemNameSql = "SELECT name FROM public.items WHERE item_id = (SELECT item_id FROM public.auctions WHERE auction_id = ?)";
        String queryBidderNameSql = "SELECT username FROM public.users WHERE user_id = ?";

        try (Connection conn = getConnection()) {
            if (conn == null) return false;
            conn.setAutoCommit(false);

            String itemName = "Vật phẩm";
            String bidderName = "Người dùng";

            try (PreparedStatement psItem = conn.prepareStatement(queryItemNameSql)) {
                psItem.setInt(1, auctionId);
                try (ResultSet rs = psItem.executeQuery()) {
                    if (rs.next()) itemName = rs.getString("name");
                }
            }

            try (PreparedStatement psUser = conn.prepareStatement(queryBidderNameSql)) {
                psUser.setInt(1, userId);
                try (ResultSet rs = psUser.executeQuery()) {
                    if (rs.next()) bidderName = rs.getString("username");
                }
            }

            try (PreparedStatement psWallet = conn.prepareStatement(updateWalletSql);
                 PreparedStatement psAuction = conn.prepareStatement(updateAuctionSql);
                 PreparedStatement psHistory = conn.prepareStatement(insertHistorySql)) {

                psWallet.setDouble(1, bidAmount);
                psWallet.setInt(2, userId);
                psWallet.executeUpdate();

                psAuction.setDouble(1, bidAmount);
                psAuction.setInt(2, userId);
                psAuction.setInt(3, auctionId);
                psAuction.setDouble(4, bidAmount);
                int affectedRows = psAuction.executeUpdate();

                if (affectedRows == 0) {
                    conn.rollback();
                    return false;
                }

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

    /** Đóng phiên đấu giá khi hết giờ và tìm ra người trả giá cao nhất */
    public void closeAuctionAndDetermineWinner(int auctionId) {
        String sqlFindWinner = """
                SELECT bidder_id, bid_amount FROM public.bidding_history 
                WHERE auction_id = ? 
                ORDER BY bid_amount DESC, bid_time DESC LIMIT 1
                """;

        String sqlUpdateAuction = """
                UPDATE public.auctions 
                SET auction_status = 'FINISHED', current_winner_id = ? 
                WHERE auction_id = ? AND auction_status != 'FINISHED'
                """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            int winnerId = 0;
            try (PreparedStatement ps1 = conn.prepareStatement(sqlFindWinner)) {
                ps1.setInt(1, auctionId);
                try (ResultSet rs = ps1.executeQuery()) {
                    if (rs.next()) {
                        winnerId = rs.getInt("bidder_id");
                    }
                }
            }

            try (PreparedStatement ps2 = conn.prepareStatement(sqlUpdateAuction)) {
                if (winnerId > 0) {
                    ps2.setInt(1, winnerId);
                } else {
                    ps2.setNull(1, java.sql.Types.INTEGER);
                }
                ps2.setInt(2, auctionId);
                ps2.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- TRUY VẤN LỊCH SỬ ĐẶT GIÁ (BID HISTORY) ---

    public List<BidHistoryRow> getBidHistoryByAuction(int auctionId) {
        List<BidHistoryRow> list = new ArrayList<>();
        String sql = "SELECT id, auction_id, bidder_name, bid_amount, bid_time, status " +
                "FROM public.bidding_history WHERE auction_id = ? ORDER BY bid_time DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String timeStr = rs.getTimestamp("bid_time") != null ? rs.getTimestamp("bid_time").toString() : "";
                    BidHistoryRow row = new BidHistoryRow(
                            rs.getInt("id"),
                            rs.getInt("auction_id"),
                            rs.getString("bidder_name"),
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

    public List<BidHistoryRow> getBidHistoryByBidder(int bidderId) {
        List<BidHistoryRow> list = new ArrayList<>();
        String sql = """
                SELECT bh.id, bh.auction_id, bh.item_name, bh.bid_amount, bh.bid_time, 
                       a.end_time, a.current_winner_id, a.auction_status
                FROM public.bidding_history bh
                JOIN public.auctions a ON bh.auction_id = a.auction_id
                WHERE bh.bidder_id = ?
                ORDER BY bh.bid_time DESC
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int auctionId = rs.getInt("auction_id");
                    String itemName = rs.getString("item_name");
                    double bidAmount = rs.getDouble("bid_amount");
                    String bidTimeStr = rs.getTimestamp("bid_time") != null ? rs.getTimestamp("bid_time").toString() : "";

                    LocalDateTime endTime = rs.getTimestamp("end_time").toLocalDateTime();
                    int currentWinnerId = rs.getInt("current_winner_id");
                    String auctionStatus = rs.getString("auction_status");

                    // Tính toán trạng thái hiển thị động cho User Client
                    String dynamicStatus = "THẤT BẠI";
                    LocalDateTime now = LocalDateTime.now();

                    if ("FINISHED".equalsIgnoreCase(auctionStatus) || "SOLD".equalsIgnoreCase(auctionStatus) || now.isAfter(endTime)) {
                        dynamicStatus = (bidderId == currentWinnerId) ? "THẮNG CUỘC" : "THẤT BẠI";
                    } else {
                        dynamicStatus = (bidderId == currentWinnerId) ? "ĐANG DẪN ĐẦU" : "BỊ ĐÈ GIÁ";
                    }

                    BidHistoryRow row = new BidHistoryRow(
                            id, auctionId, itemName, bidAmount, bidTimeStr, dynamicStatus
                    );
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<BiddingHistory> getBiddingHistoryByAuctionId(int auctionId) {
        List<BiddingHistory> list = new ArrayList<>();
        String sql = "SELECT id, auction_id, bidder_id, bid_amount, bid_time FROM public.bidding_history " +
                "WHERE auction_id = ? ORDER BY bid_amount DESC, bid_time DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BiddingHistory bh = new BiddingHistory();
                    bh.setId(rs.getInt("id"));
                    bh.setAuctionId(rs.getInt("auction_id"));
                    bh.setBidderId(rs.getInt("bidder_id"));
                    bh.setBidAmount(rs.getDouble("bid_amount"));

                    if (rs.getTimestamp("bid_time") != null) {
                        bh.setBidTime(rs.getTimestamp("bid_time").toLocalDateTime());
                    }
                    list.add(bh);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi lấy lịch sử đặt giá của phiên #" + auctionId);
            e.printStackTrace();
        }
        return list;
    }

    // --- THÔNG TIN VẬT PHẨM BỔ TRỢ ---

    public String getItemDescription(int itemId) {
        String sql = "SELECT description FROM public.items WHERE item_id = ?";
        try (Connection conn = getConnection();
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
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("img_item");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    // --- BỘ MÁY THỰC THI CHUNG (HELPER METHODS) ---

    private List<Auction> queryList(String sql, Object... params) {
        List<Auction> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = prepare(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    private boolean executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection()) {
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

    public boolean deleteAll() {
        return executeUpdate("DELETE FROM public.auctions");
    }
    public List<AuctionItemDTO> getAuctionsBySellerStatusAndKeyword(int sellerId, String status, String keyword) {
        List<AuctionItemDTO> list = new ArrayList<>();

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
                com.auction.common.model.Item item = ItemFactory.createFromResultSet(rs);
                Auction auction = map(rs);
                list.add(new AuctionItemDTO(item, auction));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
    public List<AuctionItemDTO> getFinishedAuctionsBySeller(int sellerId, String keyword) {
        List<AuctionItemDTO> list = new ArrayList<>();

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
                com.auction.common.model.Item item    = ItemFactory.createFromResultSet(rs);
                Auction auction = map(rs);
                list.add(new AuctionItemDTO(item, auction));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
    // --- CẬP NHẬT THỜI GIAN KHI SỬA PHIÊN ---
    public boolean updateAuction(Auction a) {
        String sql = "UPDATE auctions SET start_time = ?, end_time = ? WHERE auction_id = ?";
        return executeUpdate(sql,
            Timestamp.valueOf(a.getStartTime()),
            Timestamp.valueOf(a.getEndTime()),
            a.getAuctionId());
    }

    // --- XÓA PHIÊN ĐẤU GIÁ ---
    public boolean deleteAuction(int auctionId) {
        return executeUpdate("DELETE FROM auctions WHERE auction_id = ? AND auction_status = 'BLOCKED'", auctionId);
    }
}