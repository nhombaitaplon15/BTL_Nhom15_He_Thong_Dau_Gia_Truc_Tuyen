package com.auction.server.dao;

import com.auction.common.model.*;
import com.auction.factory.ItemFactory;
import com.auction.server.dao.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // 1. Lấy toàn bộ danh sách Item
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(ItemFactory.createFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    // 2. Lấy 1 Item theo ID cụ thể
    public Item getItemById(int id) {
        String sql = "SELECT * FROM items WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return ItemFactory.createFromResultSet(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 3. Thêm mới một Item (Xử lý đa hình)
// 3. Thêm mới một Item (SỬA: Nhận conn, trả về item_id tự tăng, chuẩn hóa vị trí index)
    public int insertItem(Connection conn, Item item) throws SQLException {
        String sql = "INSERT INTO items (name, description, item_type, starting_price, item_condition, " +
                "seller_id, img_item, brand, model, warranty_months, artist, " + // Sửa warranty_years thành warranty_months theo class
                "year_created, medium, has_certificate, make, model_vehicle, manufacture_year, " +
                "mileage, fuel_type, license_plate, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())";

        // Thêm cờ RETURN_GENERATED_KEYS để lấy ID tự tăng từ DB
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Set các trường chung (Vị trí 1 -> 7)
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getItemType());
            ps.setDouble(4, item.getStartingPrice());
            ps.setString(5, item.getItemCondition());
            ps.setInt(6, item.getSellerId());
            ps.setString(7, item.getImgItem());

            // Set tất cả các trường đặc thù từ vị trí 8 đến 20 về NULL mặc định trước
            for (int i = 8; i <= 20; i++) {
                ps.setNull(i, Types.NULL);
            }

            // Set dữ liệu chuẩn hóa chính xác theo số thứ tự dấu '?' trong câu SQL trên:
            // 8:brand, 9:model, 10:warranty_months, 11:artist, 12:year_created, 13:medium, 14:has_certificate
            // 15:make, 16:model_vehicle, 17:manufacture_year, 18:mileage, 19:fuel_type, 20:license_plate
            if (item instanceof Electronics e) {
                ps.setString(8, e.getBrand());
                ps.setString(9, e.getModel());
                ps.setInt(10, e.getWarrantyMonths());
            } else if (item instanceof Art a) {
                ps.setString(11, a.getArtist());
                ps.setInt(12, a.getYearCreated());
                ps.setString(13, a.getMedium());
                ps.setString(14, a.isHasCertificate());
            } else if (item instanceof Vehicle v) {
                ps.setString(15, v.getMake());
                ps.setString(16, v.getModelVehicle());
                ps.setInt(17, v.getManufactureYear());
                ps.setInt(18, v.getMileage());
                ps.setString(19, v.getFuelType());
                ps.setString(20, v.getLicensePlate());
            }

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Tạo sản phẩm thất bại, không có dòng nào được thêm!");
            }

            // Bóc tách lấy ID tự động tăng ra ngoài để trả về cho Service
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Tạo sản phẩm thất bại, không lấy được ID tự sinh.");
                }
            }
        }
    }

    // 4. Xóa Item
    public boolean deleteItem(int itemId) {
        String sql = "DELETE FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // Truyền ID cần xóa vào dấu ?
            ps.setInt(1, itemId);

            // executeUpdate() trả về số dòng bị ảnh hưởng. Nếu > 0 nghĩa là xóa thành công.
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi xóa sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // 5. Lấy danh sách Item theo trạng thái và từ khóa tìm kiếm (Dành cho bộ lọc UI)
    public List<AuctionItemDAO> getSellerProductsByStatusAndKeyword(int sellerId, String status, String keyword) {
        List<AuctionItemDAO> resultList = new ArrayList<>();

        // Dùng LEFT JOIN để lấy thông tin item và auction đi kèm.
        // Chỉ lấy sản phẩm của đúng seller_id
        // Đổi i.status thành a.auction_status (hoặc a.status tùy theo tên cột trong bảng auctions của bạn)
        String sql = "SELECT i.*, a.auction_id, a.auction_status, a.current_price, a.total_bids, a.current_winner_id " +
            "FROM items i " +
            "INNER JOIN auctions a ON i.item_id = a.item_id " +
            "WHERE i.seller_id = ? AND a.auction_status = ? AND i.name ILIKE ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // Gán giá trị cho các dấu ?
            ps.setInt(1, sellerId);
            ps.setString(2, status);
            // Xử lý từ khóa tìm kiếm (nếu keyword rỗng thì "%%" sẽ lấy tất cả)
            String searchKeyword = (keyword == null) ? "" : keyword;
            ps.setString(3, "%" + searchKeyword + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // 1. Tận dụng sức mạnh của Factory của bạn để parse Item
                    Item item = ItemFactory.createFromResultSet(rs);

                    // 2. Tạo đối tượng Auction nếu có phiên đấu giá đi kèm
                    Auction auction = new Auction();
                    int auctionId = rs.getInt("auction_id");

                    // Kiểm tra xem bảng auctions có dữ liệu cho item này không
                    if (!rs.wasNull()) {
                        auction.setAuctionId(auctionId);
                        auction.setCurrentPrice(rs.getDouble("current_price"));
                        auction.setTotalBids(rs.getInt("total_bids"));
                        auction.setCurrentWinnerId(rs.getInt("current_winner_id"));

                        // Nếu database của bạn có cột end_time, bạn lấy thêm ở đây:
                        // Timestamp endTime = rs.getTimestamp("end_time");
                        // Nếu dùng LocalDateTime: auction.setEndTime(endTime.toLocalDateTime());
                    }

                    // 3. Đóng gói vào DTO và ném vào List
                    resultList.add(new AuctionItemDAO(item, auction));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lọc sản phẩm của seller: " + e.getMessage());
            e.printStackTrace();
        }

        return resultList;
    }
    public List<Item> getApprovedItemsWithoutAuction(int sellerId, String keyword) {
        List<Item> list = new ArrayList<>();

        // LEFT JOIN auctions rồi kiểm tra auction_id IS NULL
        // → Chỉ lấy item chưa từng có phiên đấu giá nào
        String sql = "SELECT i.* " +
            "FROM items i " +
            "LEFT JOIN auctions a ON i.item_id = a.item_id " +
            "WHERE i.seller_id = ? " +
            //"  AND a.auction_status = 'NULL' " +
            "  AND a.auction_id IS NULL " +       // Chưa có phiên nào
            "  AND i.name ILIKE ? " +
            "ORDER BY i.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);
            ps.setString(2, "%" + (keyword == null ? "" : keyword) + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(ItemFactory.createFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi getApprovedItemsWithoutAuction: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}