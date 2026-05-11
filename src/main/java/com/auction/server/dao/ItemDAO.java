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
    public boolean insertItem(Item item) {
        String sql = "INSERT INTO items (name, description, item_type, starting_price, item_condition, " +
                "seller_id, img_item, created_at, brand, model, warranty_years, artist, " +
                "year_created, medium, has_certificate, make, model_vehicle, manufacture_year, " +
                "mileage, fuel_type, license_plate) VALUES (?,?,?,?,?,?,?,NOW(),?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // Set các trường chung từ lớp Item
            ps.setString(1, item.getName());
            ps.setString(2, item.getDescription());
            ps.setString(3, item.getItemType());
            ps.setDouble(4, item.getStartingPrice());
            ps.setString(5, item.getItemCondition());
            ps.setInt(6, item.getSellerId());
            ps.setString(7, item.getImgItem());

            // Set tất cả các trường đặc thù về NULL trước
            for (int i = 8; i <= 20; i++) ps.setNull(i, Types.NULL);

            // Set dữ liệu riêng dựa trên loại con (Pattern Matching Java 16+)
            if (item instanceof Vehicle v) {
                ps.setString(15, v.getMake());
                ps.setString(16, v.getModelVehicle());
                ps.setInt(17, v.getManufactureYear());
                ps.setInt(18, v.getMileage());
                ps.setString(19, v.getFuelType());
                ps.setString(20, v.getLicensePlate());
            } else if (item instanceof Electronics e) {
                ps.setString(8, e.getBrand());
                ps.setString(9, e.getModel());
                ps.setInt(10, e.getWarrantyMonths());
            } else if (item instanceof Art a) {
                ps.setString(11, a.getArtist());
                ps.setInt(12, a.getYearCreated());
                ps.setString(13, a.getMedium());
                ps.setBoolean(14, a.isHasCertificate());
            }

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 4. Xóa Item
    public boolean deleteItem(int id) {
        String sql = "DELETE FROM items WHERE item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}