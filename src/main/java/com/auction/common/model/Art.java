package com.auction.common.model;

import java.time.LocalDateTime;

/**
 * Đại diện cho tranh ảnh, đồ cổ.
 */
public class Art extends Item {
    private String artist;         // Tên họa sĩ/Tác giả
    private int yearCreated;       // Năm sáng tác
    private String medium;         // Chất liệu (Sơn dầu, Màu nước...)
    private String hasCertificate;// Có giấy chứng nhận không?

    public Art(int itemId, String name, String description, double startingPrice,
                   String condition, int sellerId, String imgItem, LocalDateTime createdAt,
                   String artist, int yearCreated, String medium, String hasCertificate) {
        super(itemId, name, description, "ART", startingPrice, condition, sellerId, imgItem, createdAt);
        this.artist = artist;
        this.yearCreated = yearCreated;
        this.medium = medium;
        this.hasCertificate = hasCertificate;
    }
    public String getArtist() {
        return artist;
    }
    public void setArtist(String artist) {
        this.artist = artist;
    }
    public int getYearCreated() {
        return yearCreated;
    }
    public void setYearCreated(int yearCreated) {
        this.yearCreated = yearCreated;
    }
    public String getMedium() {
        return medium;
    }
    public void setMedium(String medium) {
        this.medium = medium;
    }
    public String isHasCertificate() {
        return hasCertificate;
    }
    public void setHasCertificate(String hasCertificate) {
        this.hasCertificate = hasCertificate;
    }

    @Override
    public String getDetailedSpecs() {
        String cert = true ? "Có chứng chỉ" : "Không chứng chỉ";
        return String.format("Tác giả: %s | Năm: %d | Chất liệu: %s | [%s]",
                artist, yearCreated, medium, cert);
    }
}
