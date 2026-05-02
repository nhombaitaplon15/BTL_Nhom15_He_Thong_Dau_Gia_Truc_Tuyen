package com.auction.server.factory;

import com.auction.common.model.*;
import java.sql.ResultSet;

public class ItemFactory {

    public static Items createItem(ResultSet rs) throws Exception {

        String type = rs.getString("type");

        switch (type) {

            case "ART":
                return new Art(
                        rs.getInt("id"),
                        rs.getString("producer"),
                        rs.getInt("start_price"),
                        rs.getString("description"),
                        rs.getString("name"),
                        rs.getString("img_item"),
                        rs.getInt("yearCreated"),
                        rs.getBoolean("isOriginal")
                );

            case "ELECTRONICS":
                return new Electronics(
                        rs.getInt("id"),
                        rs.getString("producer"),
                        rs.getInt("start_price"),
                        rs.getString("description"),
                        rs.getString("name"),
                        rs.getString("img_item"),
                        rs.getInt("date"),
                        rs.getInt("warrantyExpiryDate")
                );

            case "VEHICLE":
            default:
                return new Vehicle(
                        rs.getInt("id"),
                        rs.getString("producer"),
                        rs.getInt("start_price"),
                        rs.getString("description"),
                        rs.getString("name"),
                        rs.getString("img_item")
                );
        }
    }
}