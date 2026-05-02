package com.auction.server.dao;

import com.auction.common.model.*;
import com.auction.server.factory.ItemFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {

    // Lấy tất cả auction PENDING
    public List<Auction> getPendingAuctions() {

        List<Auction> list = new ArrayList<>();

        String sql = """
                SELECT
                    a.auction_id,
                    a.current_price,
                    a.status,

                    i.id,
                    i.name,
                    i.producer,
                    i.start_price,
                    i.description,
                    i.img_item

                FROM auctions a

                JOIN items i
                ON a.item_id = i.id

                WHERE a.status = 'PENDING'
                """;

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            while (rs.next()) {

                Items item = ItemFactory.createItem(rs);

                Auction auction = new Auction(
                        rs.getInt("auction_id"),
                        item
                );

                auction.setCurrentPrice(
                        rs.getInt("current_price")
                );

                auction.setStatus(
                        rs.getString("status")
                );

                list.add(auction);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // Approve auction
    public boolean approveAuction(int auctionId) {

        String sql = """
                UPDATE auctions
                SET status='OPEN'
                WHERE auction_id=?
                """;

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, auctionId);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean updateStatus(int auctionId, String status) {

        String sql = """
            UPDATE auctions
            SET status = ?
            WHERE auction_id = ?
            """;

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, status);
            ps.setInt(2, auctionId);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
    public boolean placeBid(int auctionId, long newPrice, String bidder) {

        String sql = """
        UPDATE auctions
        SET current_price = ?, highest_bidder = ?
        WHERE auction_id = ?
        AND current_price < ?
        AND status = 'RUNNING'
    """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, newPrice);
            ps.setString(2, bidder);
            ps.setInt(3, auctionId);
            ps.setLong(4, newPrice);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}