package server.dao;

import com.auction.common.model.BidHistoryRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class BiddingHistoryDAO {
    public List<BidHistoryRow> getHistoryByUser(int userId) {
        List<BidHistoryRow> list = new ArrayList<>();

        // Truy vấn trực tiếp từ bảng bidding_history theo chuẩn cột trong Postgres của bạn
        String sql = """
                SELECT id, auction_id, item_name, bid_amount, bid_time, status 
                FROM bidding_history 
                WHERE bidder_id = ? 
                ORDER BY bid_time DESC
                """;

        try (
                Connection conn = DatabaseConnection.connect();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            // bidder_id nhận giá trị từ userId truyền vào từ phiên đăng nhập
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Chuyển đổi dữ liệu Thời gian sang chuỗi String để hiển thị gọn gàng
                    String timeStr = "";
                    if (rs.getTimestamp("bid_time") != null) {
                        timeStr = rs.getTimestamp("bid_time").toString();
                    }

                    BidHistoryRow row = new BidHistoryRow(
                            rs.getInt("id"),
                            rs.getInt("auction_id"),
                            rs.getString("item_name"),
                            rs.getDouble("bid_amount"),
                            timeStr,
                            rs.getString("status")
                    );
                    list.add(row);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi lấy lịch sử đặt giá từ DB: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }
}