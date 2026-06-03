package com.auction.server.dao;

import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;
/**
 * DAO cho bảng bidding_history (và fallback sang transactions nếu cần).
 * Đã cập nhật theo cấu trúc database mới (không cần JOIN với bảng users).
 */
public class BidDAO {

  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final DateTimeFormatter DATETIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

  // ------------------------------------------------------------------ //
  //  Inner record — dữ liệu 1 dòng bid history (thay BidRow trong ctrl) //
  // ------------------------------------------------------------------ //
  public record BidRow(int rank, String username, double amount, String bidTime) implements Serializable {}
  // ------------------------------------------------------------------ //
  //  Lấy lịch sử bid                                                    //
  // ------------------------------------------------------------------ //

  /**
   * Lấy top 20 bid của phiên, sắp xếp giá giảm dần.
   * Dùng bảng `bidding_history`; nếu lỗi (hoặc chưa có) thì fallback sang `transactions`.
   */
  public List<BidRow> getBidHistory(int auctionId) {
    List<BidRow> result = queryFromBiddingHistory(auctionId);
    if (result.isEmpty()) {
      result = queryFromTransactions(auctionId);
    }
    return result;
  }

  private List<BidRow> queryFromBiddingHistory(int auctionId) {
    List<BidRow> list = new ArrayList<>();
    // Truy vấn trực tiếp từ bidding_history, không cần JOIN users vì đã có bidder_name
    String sql = """
                SELECT bidder_name, bid_amount, bid_time
                FROM bidding_history
                WHERE auction_id = ?
                ORDER BY bid_amount DESC
                LIMIT 20
                """;
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        int rank = 1;
        while (rs.next()) {
          Timestamp ts = rs.getTimestamp("bid_time");
          String time = ts != null ? ts.toLocalDateTime().format(TIME_FMT) : "—";
          // Lấy bidder_name trực tiếp từ bảng
          list.add(new BidRow(rank++, rs.getString("bidder_name"),
              rs.getDouble("bid_amount"), time));
        }
      }
    } catch (SQLException e) {
      // Bảng bidding_history chưa sẵn sàng → caller sẽ thử fallback
      System.err.println("Lỗi query bidding_history, fallback sang transactions: " + e.getMessage());
    }
    return list;
  }

  private List<BidRow> queryFromTransactions(int auctionId) {
    List<BidRow> list = new ArrayList<>();
    String sql = """
                SELECT u.username, t.amount, t.created_at
                FROM transactions t
                JOIN users u ON t.user_id = u.user_id
                WHERE t.transaction_type = ? AND t.status = 'SUCCESS'
                ORDER BY t.amount DESC
                LIMIT 20
                """;
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, "BID_AUCTION_" + auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        int rank = 1;
        while (rs.next()) {
          Timestamp ts = rs.getTimestamp("created_at");
          String time = ts != null ? ts.toLocalDateTime().format(TIME_FMT) : "—";
          list.add(new BidRow(rank++, rs.getString("username"),
              rs.getDouble("amount"), time));
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return list;
  }

  // ------------------------------------------------------------------ //
  //  Đếm số bidder                                                      //
  // ------------------------------------------------------------------ //

  /**
   * Đếm số người bid phân biệt trong 1 phiên.
   */
  public int countBidders(int auctionId) {
    int count = countBiddersFromBiddingHistory(auctionId);
    if (count == 0) count = countBiddersFromTransactions(auctionId);
    return count;
  }

  private int countBiddersFromBiddingHistory(int auctionId) {
    String sql = "SELECT COUNT(DISTINCT bidder_id) AS cnt FROM bidding_history WHERE auction_id = ?";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt("cnt");
      }
    } catch (SQLException e) {
      // Bảng chưa có → caller thử fallback
    }
    return 0;
  }

  private int countBiddersFromTransactions(int auctionId) {
    String sql = """
                SELECT COUNT(DISTINCT user_id) AS cnt
                FROM transactions
                WHERE transaction_type = ? AND status = 'SUCCESS'
                """;
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, "BID_AUCTION_" + auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt("cnt");
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return 0;
  }

  // ------------------------------------------------------------------ //
  //  Thời điểm bid thắng                                                //
  // ------------------------------------------------------------------ //

  /**
   * Lấy thời điểm bid có giá cao nhất (= bid thắng) của phiên.
   * Trả về null nếu không tìm thấy (caller dùng endTime làm fallback).
   */
  public String getWinBidTime(int auctionId) {
    String sql = """
                SELECT bid_time FROM bidding_history
                WHERE auction_id = ?
                ORDER BY bid_amount DESC
                LIMIT 1
                """;
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          Timestamp ts = rs.getTimestamp("bid_time");
          if (ts != null) return ts.toLocalDateTime().format(DATETIME_FMT);
        }
      }
    } catch (SQLException e) {
      // Bảng bidding_history chưa có → trả null, caller dùng endTime
    }
    return null;
  }
}