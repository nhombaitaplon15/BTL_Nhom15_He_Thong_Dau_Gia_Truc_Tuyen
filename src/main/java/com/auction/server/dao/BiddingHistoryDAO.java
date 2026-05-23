package com.auction.server.dao;
import com.auction.common.model.BidHistoryRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
public class BiddingHistoryDAO {
    public List<BidHistoryRow> getHistoryByUser(int userId) {

        List<BidHistoryRow> list = new ArrayList<>();

        String sql = """
                SELECT 
                    bh.id,
                    bh.auction_id,
                    i.name AS item_name,
                    bh.bid_amount,
                    bh.bid_time,
                    auction_status
                FROM bidding_history bh
                JOIN auctions a
                    ON bh.auction_id = a.auction_id
                JOIN items i
                    ON a.item_id = i.item_id
                WHERE bh.user_id = ?
                ORDER BY bh.bid_time DESC
                """;

        try (
                Connection conn = DatabaseConnection.connect();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                BidHistoryRow row = new BidHistoryRow(
                        rs.getInt("id"),
                        rs.getInt("auction_id"),
                        rs.getString("item_name"),
                        rs.getDouble("bid_amount"),
                        rs.getString("bid_time"),
                        rs.getString("status")
                );

                list.add(row);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}
