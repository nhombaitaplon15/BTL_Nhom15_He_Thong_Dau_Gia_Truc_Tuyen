package com.auction.server.dao;
import java.sql.*;

public class ItemDAO {
  private Connection conn;

  public ItemDAO(Connection conn) {
    this.conn = conn;
  }

  public void insertItem(int id, String producer, int startPrice, String desc, String name, String img) throws SQLException {
    String sql = "INSERT INTO items (item_id, producer, starting_price, description, name, img_item) VALUES (?, ?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, id);
      pstmt.setString(2, producer);
      pstmt.setInt(3, startPrice);
      pstmt.setString(4, desc);
      pstmt.setString(5, name);
      pstmt.setString(6, img);
      pstmt.executeUpdate();
    }
  }
}