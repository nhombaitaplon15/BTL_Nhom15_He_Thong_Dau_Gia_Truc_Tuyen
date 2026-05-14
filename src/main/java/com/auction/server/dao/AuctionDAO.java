package com.auction.server.dao;

import com.auction.common.model.Auction;
import com.auction.common.model.Items;
import com.auction.server.dao.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {
  Connection conn = null;
  PreparedStatement ps = null;
  ResultSet rs = null;

  // 1. Lưu một phiên đấu giá mới vào Database
  public void insertAuction(Auction a) {
    String query = "INSERT INTO auctions (item_id, starting_price, current_price, auction_status, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?)";
    try {
      conn = new DBConnection().getConnection();
      ps = conn.prepareStatement(query);
      ps.setInt(1, a.getItem().getId()); // Lấy ID của item
      ps.setDouble(2, a.getItem().getStartPrice());
      ps.setDouble(3, a.getCurrentPrice());
      ps.setString(4, a.getAuctionStatus());
      ps.setTimestamp(5, Timestamp.valueOf(a.getStartTime()));
      ps.setTimestamp(6, Timestamp.valueOf(a.getEndTime()));
      ps.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // 2. Cập nhật khi có người đặt giá cao hơn (Bid)
  public void updateHighestBid(int auctionId, long price, String bidderName) {
    // Lưu ý: Trong SQL gốc là user_id, nếu bạn lưu tên (String) vào cột đó sẽ lỗi.
    // Bạn nên cân nhắc đổi highestBidder trong Java thành int bidderId.
    String query = "UPDATE auctions SET current_price = ?, total_bids = total_bids + 1 WHERE auction_id = ?";
    try {
      conn = new DBConnection().getConnection();
      ps = conn.prepareStatement(query);
      ps.setLong(1, price);
      ps.setInt(2, auctionId);
      ps.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // 3. Lấy tất cả các phiên đấu giá đang diễn ra (RUNNING)
  public List<Auction> getRunningAuctions() {
    List<Auction> list = new ArrayList<>();
    String query = "SELECT * FROM auctions WHERE auction_status = 'RUNNING'";
    try {
      conn = new DBConnection().getConnection();
      ps = conn.prepareStatement(query);
      rs = ps.executeQuery();
      while (rs.next()) {
        // Ở đây bạn cần lấy thêm thông tin Item để khởi tạo object Auction
        // Auction a = new Auction(rs.getInt("auction_id"), itemObject);
        // a.setAuctionStatus(rs.getString("auction_status"));
        // list.add(a);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return list;
  }
}