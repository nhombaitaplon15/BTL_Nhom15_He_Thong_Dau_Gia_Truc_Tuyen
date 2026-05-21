package com.auction.server.dao;

import com.auction.common.model.*;
import com.auction.factory.ItemFactory;
import com.auction.server.dao.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // 1. Lấy toàn bộ danh sách Item
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Connection conn = DatabaseConnection.connect();
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
        try (Connection conn = DatabaseConnection.connect();
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
                ps.setBoolean(14, a.isHasCertificate());
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

        try (Connection conn = DatabaseConnection.connect();
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
}