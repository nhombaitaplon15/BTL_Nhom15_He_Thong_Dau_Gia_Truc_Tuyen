package com.auction.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Lớp trừu tượng đại diện cho một vật phẩm đấu giá.
 * Chứa các trường chung khớp với bảng 'items' trong Database.
 */
public abstract class Item extends Entity implements java.io.Serializable {
    private int itemId;
    private String name;
    private String description;
    private String itemType;      // Loại: ELECTRONICS, ART, VEHICLE
    private double startingPrice;
    private String itemCondition; // Tình trạng: NEW, LIKE_NEW, GOOD...
    private int sellerId;
    private String imgItem;
    private LocalDateTime createdAt;

    public Item(int itemId, String name, String description, String itemType,
                double startingPrice, String itemCondition, int sellerId,
                String imgItem, LocalDateTime createdAt) {
        super(itemId);
        this.name = name;
        this.description = description;
        this.itemType = itemType;
        this.startingPrice = startingPrice;
        this.itemCondition = itemCondition;
        this.sellerId = sellerId;
        this.imgItem = imgItem;
        this.createdAt = createdAt;
    }

    // --- Getters & Setters ---
    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImgItem() { return imgItem; }
    public void setImgItem(String imgItem) { this.imgItem = imgItem; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }
    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getItemCondition() { return itemCondition; }
    public void setItemCondition(String itemCondition) { this.itemCondition = itemCondition; }
    public abstract String getDetailedSpecs();
}