package com.auction.common.model;



import java.time.LocalDateTime;

public class Vehicle extends Item {
    private String make;             // Hãng xe (VD: Honda)
    private String modelVehicle;     // Dòng xe (VD: Civic)
    private int manufactureYear;     // Năm sản xuất
    private int mileage;             // Số km đã đi (ODO)
    private String fuelType;         // Loại nhiên liệu (Xăng/Dầu)
    private String licensePlate;     // Biển số xe

    public Vehicle(int itemId, String name, String description, double startingPrice,
                   String condition, int sellerId, String imgItem, LocalDateTime createdAt,
                   String make, String modelVehicle, int manufactureYear,
                   int mileage, String fuelType, String licensePlate) {
        // Gọi Constructor lớp cha
        super(itemId, name, description, "VEHICLE", startingPrice, condition, sellerId, imgItem, createdAt);
        this.make = make;
        this.modelVehicle = modelVehicle;
        this.manufactureYear = manufactureYear;
        this.mileage = mileage;
        this.fuelType = fuelType;
        this.licensePlate = licensePlate;
    }
    public String getMake() {
        return make;
    }
    public void setMake(String make) {
        this.make = make;
    }
    public String getModelVehicle() {
        return modelVehicle;
    }
    public void setModelVehicle(String modelVehicle) {
        this.modelVehicle = modelVehicle;
    }
    public int getManufactureYear() {
        return manufactureYear;
    }
    public void setManufactureYear(int manufactureYear) {
        this.manufactureYear = manufactureYear;
    }
    public int getMileage() {
        return mileage;
    }
    public void setMileage(int mileage) {
        this.mileage = mileage;
    }
    public String getFuelType() {
        return fuelType;
    }
    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }
    public String getLicensePlate() {
        return licensePlate;
    }
    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }
    @Override
    public String getDetailedSpecs() {
        return String.format("Xe %s %s | Năm: %d | Biển số: %s | ODO: %d km | Nhiên liệu: %s",
                make, modelVehicle, manufactureYear, licensePlate, mileage, fuelType);
    }
}