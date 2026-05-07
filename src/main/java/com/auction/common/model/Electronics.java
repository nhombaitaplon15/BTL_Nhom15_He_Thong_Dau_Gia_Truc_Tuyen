package com.auction.common.model;

import java.time.LocalDate;

public class Electronics extends Items implements java.io.Serializable {

    protected String brand;
    protected String condition;
    protected LocalDate manufactureDate; // Ngày sản xuất (Cố định)
    protected int warrantyMonths;    // Số tháng bảo hành (ví dụ: 12)
    public Electronics (int id, String name, String producer, int startPrice, String description, String imgitem,
                        String brand, String condition, LocalDate manufactureDate, int warrantyMonths){
        super(id, producer, startPrice, description, name, imgitem);
        this.brand = brand;
        this.condition = condition;
        this.manufactureDate = manufactureDate;
        this.warrantyMonths = warrantyMonths;
    }
    // Getter và Setter
    public LocalDate getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }

    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}