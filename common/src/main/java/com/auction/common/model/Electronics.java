package com.auction.common.model;



import java.time.LocalDateTime;

/**
 * Đại diện cho nhóm hàng điện tử (iPhone, Laptop...).
 */
public class Electronics extends Item {
    private String brand;         // Hãng (VD: Apple)
    private String model;         // Dòng máy (VD: iPhone 15 Pro Max)
    private int warrantyMonths;    // Số năm bảo hành

    public Electronics(int itemId, String name, String description, double startingPrice,
                       String condition, int sellerId, String imgItem, LocalDateTime createdAt,
                       String brand, String model, int warrantyMonths) {
        super(itemId, name, description, "ELECTRONICS", startingPrice, condition, sellerId, imgItem, createdAt);
        this.brand = brand;
        this.model = model;
        this.warrantyMonths = warrantyMonths;
    }
    public String getBrand() {
        return brand;
    }
    public void setBrand(String brand) {
        this.brand = brand;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public int getWarrantyMonths() {
        return warrantyMonths;
    }
    public void setWarrantyMonths(int warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    @Override
    public String getDetailedSpecs() {
        return String.format("%s %s | Bảo hành: %d tháng", brand, model, warrantyMonths);
    }
}
