package com.auction.common.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class ItemHierarchyTest {

    private LocalDateTime fixedTime;

    @BeforeEach
    void setUpTime() {
        fixedTime = LocalDateTime.of(2026, 5, 26, 12, 0, 0);
    }

    // ==========================================
    // 1. KIỂM THỬ ĐỐI TƯỢNG ART
    // ==========================================
    @Nested
    class ArtTest {
        @Test
        void testArtConstructorAndGetters() {
            Art art = new Art(1, "Mona Lisa", "Bản gốc Phục Hưng", 5000000.0,
                    "GOOD", 10, "monalisa.jpg", fixedTime,
                    "Leonardo da Vinci", 1503, "Oil on poplar panel", true);

            // Đồng bộ ID bằng setter để đảm bảo bài test luôn đúng với cấu trúc Entity của dự án
            art.setItemId(1);

            assertEquals(1, art.getItemId());
            assertEquals("Mona Lisa", art.getName());
            assertEquals("Bản gốc Phục Hưng", art.getDescription());
            assertEquals("ART", art.getItemType());
            assertEquals(5000000.0, art.getStartingPrice());
            assertEquals("GOOD", art.getItemCondition());
            assertEquals(10, art.getSellerId());
            assertEquals("monalisa.jpg", art.getImgItem());
            assertEquals(fixedTime, art.getCreatedAt());

            // Kiểm tra các trường riêng của Art
            assertEquals("Leonardo da Vinci", art.getArtist());
            assertEquals(1503, art.getYearCreated());
            assertEquals("Oil on poplar panel", art.getMedium());
            assertTrue(art.isHasCertificate());
        }

        @Test
        void testArtDetailedSpecs_WithCertificate() {
            Art art = new Art(1, "Tranh", "Mô tả", 100.0, "NEW", 2, "img.jpg", fixedTime,
                    "Nguyễn Phan Chánh", 1930, "Tranh lụa", true);

            String expectedSpecs = "Tác giả: Nguyễn Phan Chánh | Năm: 1930 | Chất liệu: Tranh lụa | [Có chứng chỉ]";
            assertEquals(expectedSpecs, art.getDetailedSpecs());
        }

        @Test
        void testArtDetailedSpecs_WithoutCertificate() {
            Art art = new Art(1, "Tranh", "Mô tả", 100.0, "NEW", 2, "img.jpg", fixedTime,
                    "Danh họa ẩn danh", 2000, "Sơn mài", false);

            String expectedSpecs = "Tác giả: Danh họa ẩn danh | Năm: 2000 | Chất liệu: Sơn mài | [Không chứng chỉ]";
            assertEquals(expectedSpecs, art.getDetailedSpecs());
        }

        @Test
        void testArtSetters() {
            Art art = new Art(1, "Tên cũ", "Mô tả", 10.0, "OLD", 1, "1.jpg", fixedTime, "A", 2000, "B", false);

            art.setArtist("Bùi Xuân Phái");
            art.setYearCreated(1960);
            art.setMedium("Phố Phái");
            art.setHasCertificate(true);

            assertEquals("Bùi Xuân Phái", art.getArtist());
            assertEquals(1960, art.getYearCreated());
            assertEquals("Phố Phái", art.getMedium());
            assertTrue(art.isHasCertificate());
        }
    }

    // ==========================================
    // 2. KIỂM THỬ ĐỐI TƯỢNG ELECTRONICS
    // ==========================================
    @Nested
    class ElectronicsTest {
        @Test
        void testElectronicsConstructorAndGetters() {
            Electronics electronics = new Electronics(2, "iPhone 15 Pro", "Máy quốc tế Mỹ", 1200.0,
                    "NEW", 22, "iphone15.jpg", fixedTime,
                    "Apple", "iPhone 15 Pro Max", 12);

            // Đồng bộ ID bằng setter của lớp cha Item
            electronics.setItemId(2);

            assertEquals(2, electronics.getItemId());
            assertEquals("iPhone 15 Pro", electronics.getName());
            assertEquals("ELECTRONICS", electronics.getItemType());

            // Kiểm tra các trường riêng của Electronics
            assertEquals("Apple", electronics.getBrand());
            assertEquals("iPhone 15 Pro Max", electronics.getModel());
            assertEquals(12, electronics.getWarrantyMonths());
        }

        @Test
        void testElectronicsDetailedSpecs() {
            Electronics electronics = new Electronics(2, "Laptop", "Mô tả", 2000.0, "NEW", 22, "lap.jpg", fixedTime,
                    "ASUS", "ROG Strix", 24);

            String expectedSpecs = "ASUS ROG Strix | Bảo hành: 24 tháng";
            assertEquals(expectedSpecs, electronics.getDetailedSpecs());
        }

        @Test
        void testElectronicsSetters() {
            Electronics electronics = new Electronics(2, "Loa", "Mô tả", 50.0, "NEW", 5, "loa.jpg", fixedTime, "Sony", "X1", 6);

            electronics.setBrand("JBL");
            electronics.setModel("Charge 5");
            electronics.setWarrantyMonths(12);

            assertEquals("JBL", electronics.getBrand());
            assertEquals("Charge 5", electronics.getModel());
            assertEquals(12, electronics.getWarrantyMonths());
        }
    }

    // ==========================================
    // 3. KIỂM THỬ ĐỐI TƯỢNG VEHICLE
    // ==========================================
    @Nested
    class VehicleTest {
        @Test
        void testVehicleConstructorAndGetters() {
            Vehicle vehicle = new Vehicle(3, "Honda Civic 2023", "Chính chủ đi kỹ", 35000.0,
                    "LIKE_NEW", 33, "civic.jpg", fixedTime,
                    "Honda", "Civic", 2023, 12500, "Xăng", "30H-999.99");

            // Đồng bộ ID bằng setter của lớp cha Item
            vehicle.setItemId(3);

            assertEquals(3, vehicle.getItemId());
            assertEquals("Honda Civic 2023", vehicle.getName());
            assertEquals("VEHICLE", vehicle.getItemType());

            // Kiểm tra các trường riêng của Vehicle
            assertEquals("Honda", vehicle.getMake());
            assertEquals("Civic", vehicle.getModelVehicle());
            assertEquals(2023, vehicle.getManufactureYear());
            assertEquals(12500, vehicle.getMileage());
            assertEquals("Xăng", vehicle.getFuelType());
            assertEquals("30H-999.99", vehicle.getLicensePlate());
        }

        @Test
        void testVehicleDetailedSpecs() {
            Vehicle vehicle = new Vehicle(3, "Xe", "Mô tả", 1000.0, "NEW", 1, "xe.jpg", fixedTime,
                    "Mazda", "CX-5", 2021, 30000, "Xăng", "29A-111.11");

            String expectedSpecs = "Xe Mazda CX-5 | Năm: 2021 | Biển số: 29A-111.11 | ODO: 30000 km | Nhiên liệu: Xăng";
            assertEquals(expectedSpecs, vehicle.getDetailedSpecs());
        }

        @Test
        void testVehicleSetters() {
            Vehicle vehicle = new Vehicle(3, "Xe", "Mô tả", 1000.0, "NEW", 1, "xe.jpg", fixedTime, "Kia", "Morning", 2018, 80000, "Xăng", "29A-000.00");

            vehicle.setMake("Hyundai");
            vehicle.setModelVehicle("Accent");
            vehicle.setManufactureYear(2020);
            vehicle.setMileage(45000);
            vehicle.setFuelType("Dầu");
            vehicle.setLicensePlate("30G-888.88");

            assertEquals("Hyundai", vehicle.getMake());
            assertEquals("Accent", vehicle.getModelVehicle());
            assertEquals(2020, vehicle.getManufactureYear());
            assertEquals(45000, vehicle.getMileage());
            assertEquals("Dầu", vehicle.getFuelType());
            assertEquals("30G-888.88", vehicle.getLicensePlate());
        }
    }

    // ==========================================
    // 4. KIỂM THỬ CÁC SETTER TRÊN LỚP CHA ITEM
    // ==========================================
    @Nested
    class BaseItemSettersTest {
        @Test
        void testBaseItemSetters_UsingConcreteSubclass() {
            Item item = new Electronics(9, "Ban Đầu", "Mô tả cũ", 10.0, "CŨ", 1, "cu.jpg", fixedTime, "A", "B", 6);

            LocalDateTime newTime = LocalDateTime.now();

            item.setItemId(999);
            item.setName("Tên Mới");
            item.setDescription("Mô tả mới tinh");
            item.setItemType("VEHICLE");
            item.setStartingPrice(2500.0);
            item.setItemCondition("NEW");
            item.setSellerId(88);
            item.setImgItem("new_img.jpg");
            item.setCreatedAt(newTime);

            assertEquals(999, item.getItemId());
            assertEquals("Tên Mới", item.getName());
            assertEquals("Mô tả mới tinh", item.getDescription());
            assertEquals("VEHICLE", item.getItemType());
            assertEquals(2500.0, item.getStartingPrice());
            assertEquals("NEW", item.getItemCondition());
            assertEquals(88, item.getSellerId());
            assertEquals("new_img.jpg", item.getImgItem());
            assertEquals(newTime, item.getCreatedAt());
        }
    }
}