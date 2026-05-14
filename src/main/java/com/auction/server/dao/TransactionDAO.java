package com.auction.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class TransactionDAO {
  Connection conn = null;
  PreparedStatement ps = null;

  public void insertBid(int auctionId, int bidderId, long amount) {
    String query = "INSERT INTO bid_transactions (auction_id, bidder_id, amount, bid_time) VALUES (?, ?, ?, NOW())";
    try {
      conn = new DBConnection().getConnection();
      ps = conn.prepareStatement(query);
      ps.setInt(1, auctionId);
      ps.setInt(2, bidderId);
      ps.setLong(3, amount);
      ps.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}