package com.auction.server.dao;

import com.auction.common.model.*;
import com.auction.common.factory.ItemFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // --- 1. LẤY TOÀN BỘ DANH SÁCH ITEM ---
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM public.items";
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

    // --- 2. LẤY 1 ITEM THEO ID CỤ THỂ ---
    public Item getItemById(int id) {
        String sql = "SELECT * FROM public.items WHERE item_id = ?";
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

    // --- 3. THÊM MỚI MỘT ITEM (XỬ LÝ ĐA HÌNH SINGLE TABLE) ---
    public int insertItem(Connection conn, Item item) throws SQLException {
        String sql = "INSERT INTO public.items (name, description, item_type, starting_price, item_condition, " +
                "seller_id, brand, model, warranty_months, artist, year_created, medium, has_certificate, " +
                "make, model_vehicle, manufacture_year, mileage, fuel_type, img_item, license_plate, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Tham số chung (Vị trí từ 1 -> 6)
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getItemType());
            ps.setDouble(4, item.getStartingPrice());
            ps.setString(5, item.getItemCondition());
            ps.setInt(6, item.getSellerId());

            // Làm sạch các ô đặc thù từ 7 tới 20 về NULL mặc định để tránh lỗi rác dữ liệu chéo loại sản phẩm
            for (int i = 7; i <= 20; i++) {
                ps.setNull(i, Types.NULL);
            }

            // Gán giá trị đặc thù dựa trên mô hình thừa kế của đối tượng
            if (item instanceof Electronics e) {
                ps.setString(7, e.getBrand());
                ps.setString(8, e.getModel());
                ps.setInt(9, e.getWarrantyMonths());
            } else if (item instanceof Art a) {
                ps.setString(11, a.getArtist());
                ps.setInt(12, a.getYearCreated());
                ps.setString(13, a.getMedium());
                ps.setBoolean(14, a.isHasCertificate());
            } else if (item instanceof Vehicle v) {
                ps.setString(14, v.getMake());
                ps.setString(15, v.getModelVehicle());
                ps.setInt(16, v.getManufactureYear());
                ps.setInt(17, v.getMileage());
                ps.setString(18, v.getFuelType());
            }

            // Gán các trường nằm cuối câu lệnh SQL (19: img_item, 20: license_plate)
            ps.setString(19, item.getImgItem());
            if (item instanceof Vehicle v) {
                ps.setString(20, v.getLicensePlate());
            }

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Tạo sản phẩm thất bại, không có dòng nào được thêm!");
            }

            // Bóc tách lấy ID tự động tăng (Primary Key) trả về cho phía Service bọc giao dịch
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Tạo sản phẩm thất bại, không lấy được ID tự sinh.");
                }
            }
        }
    }

    // --- 4. XÓA ITEM ---
    public boolean deleteItem(int itemId) {
        String sql = "DELETE FROM public.items WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- 5. LẤY DANH SÁCH SẢN PHẨM THEO LOẠI (VEHICLE, ART, ELECTRONICS) ---
    public List<Item> getItemsByType(String itemType) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM public.items WHERE item_type = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(ItemFactory.createFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    // --- 6. LẤY DANH SÁCH SẢN PHẨM THEO SELLER (GIỮ LẠI TỪ FILE 1) ---
    public List<Item> getItemsBySeller(int sellerId) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM public.items WHERE seller_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(ItemFactory.createFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi lấy danh sách item theo seller: " + e.getMessage());
            e.printStackTrace();
        }
        return items;
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