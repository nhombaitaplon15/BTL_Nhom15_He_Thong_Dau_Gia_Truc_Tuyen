package com.auction.common.factory;

import com.auction.common.model.Art;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class ItemFactory {
    /**
     * Chuyển trực tiếp ResultSet thành Object mà không cần if-else bên ngoài DAO
     */
    public static Item createFromResultSet(ResultSet rs) throws SQLException {
        String type = rs.getString("item_type").toUpperCase();

        // Lấy thông tin chung
        int id = rs.getInt("item_id");
        String name = rs.getString("name");
        String desc = rs.getString("description");
        double price = rs.getDouble("starting_price");
        String cond = rs.getString("item_condition");
        int sellerId = rs.getInt("seller_id");
        String img = rs.getString("img_item");// Ví dụ sửa trong ItemFactory.java
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        LocalDateTime created = (createdAtTs != null) ? createdAtTs.toLocalDateTime() : LocalDateTime.now();
        // hạn chế if else
        return switch (type) {
            case "VEHICLE" -> new Vehicle(id, name, desc, price, cond, sellerId, img, created,
                    rs.getString("make"), rs.getString("model_vehicle"), rs.getInt("manufacture_year"),
                    rs.getInt("mileage"), rs.getString("fuel_type"), rs.getString("license_plate"));

            case "ELECTRONICS" -> new Electronics(id, name, desc, price, cond, sellerId, img, created,
                    rs.getString("brand"), rs.getString("model"), rs.getInt("warranty_months"));

            case "ART" -> new Art(id, name, desc, price, cond, sellerId, img, created,
                    rs.getString("artist"), rs.getInt("year_created"), rs.getString("medium"), rs.getString("has_certificate"));

            default -> throw new IllegalArgumentException("Loại hàng lạ: " + type);
        };
    }
}