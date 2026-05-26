package com.auction.factory;

import com.auction.common.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ItemFactoryTest {

    @Mock
    private ResultSet resultSet;

    private final Timestamp fixedTimestamp = Timestamp.valueOf("2026-05-26 10:00:00");

    @BeforeEach
    void setUpCommonFields() throws SQLException {
        // Cấu hình các trường dữ liệu chung của bảng Item
        when(resultSet.getInt("item_id")).thenReturn(101);
        when(resultSet.getString("name")).thenReturn("Sản phẩm mẫu");
        when(resultSet.getString("description")).thenReturn("Mô tả sản phẩm mẫu");
        when(resultSet.getDouble("starting_price")).thenReturn(1500.0);
        when(resultSet.getString("item_condition")).thenReturn("NEW");
        when(resultSet.getInt("seller_id")).thenReturn(99);
        when(resultSet.getString("img_item")).thenReturn("image_url.png");
        when(resultSet.getTimestamp("created_at")).thenReturn(fixedTimestamp);
    }

    @Nested
    class VehicleTypeTest {
        @Test
        void createFromResultSet_Success_WhenTypeIsVehicle() throws SQLException {
            // Given
            when(resultSet.getString("item_type")).thenReturn("VEHICLE");
            when(resultSet.getString("make")).thenReturn("Toyota");
            when(resultSet.getString("model_vehicle")).thenReturn("Camry");
            when(resultSet.getInt("manufacture_year")).thenReturn(2022);
            when(resultSet.getInt("mileage")).thenReturn(15000);
            when(resultSet.getString("fuel_type")).thenReturn("PETROL");
            when(resultSet.getString("license_plate")).thenReturn("30A-12345");

            // When
            Item result = ItemFactory.createFromResultSet(resultSet);

            // Then
            assertNotNull(result);
            assertInstanceOf(Vehicle.class, result); // Đã sửa theo gợi ý của IDE

            Vehicle vehicle = (Vehicle) result;
            assertEquals(101, vehicle.getId());
            assertEquals("Toyota", vehicle.getMake());
            assertEquals("30A-12345", vehicle.getLicensePlate());
            assertEquals(fixedTimestamp.toLocalDateTime(), vehicle.getCreatedAt()); // Sửa thành getCreatedAt
        }
    }

    @Nested
    class ElectronicsTypeTest {
        @Test
        void createFromResultSet_Success_WhenTypeIsElectronics() throws SQLException {
            // Given
            when(resultSet.getString("item_type")).thenReturn("ELECTRONICS");
            when(resultSet.getString("brand")).thenReturn("Apple");
            when(resultSet.getString("model")).thenReturn("iPhone 15 Pro");
            when(resultSet.getInt("warranty_months")).thenReturn(12);

            // When
            Item result = ItemFactory.createFromResultSet(resultSet);

            // Then
            assertNotNull(result);
            assertInstanceOf(Electronics.class, result);

            Electronics electronics = (Electronics) result;
            assertEquals("Sản phẩm mẫu", electronics.getName());
            assertEquals("Apple", electronics.getBrand());
            assertEquals("iPhone 15 Pro", electronics.getModel());
            assertEquals(12, electronics.getWarrantyMonths());
        }
    }

    @Nested
    class ArtTypeTest {
        @Test
        void createFromResultSet_Success_WhenTypeIsArt() throws SQLException {
            // Given
            when(resultSet.getString("item_type")).thenReturn("ART");
            when(resultSet.getString("artist")).thenReturn("Leonardo da Vinci");
            when(resultSet.getInt("year_created")).thenReturn(1503);
            when(resultSet.getString("medium")).thenReturn("Oil on poplar panel");
            when(resultSet.getBoolean("has_certificate")).thenReturn(true);

            // When
            Item result = ItemFactory.createFromResultSet(resultSet);

            // Then
            assertNotNull(result);
            assertInstanceOf(Art.class, result);

            Art art = (Art) result;
            assertEquals(1500.0, art.getStartingPrice());
            assertEquals("Leonardo da Vinci", art.getArtist());
            assertTrue(art.isHasCertificate());
        }
    }

    @Nested
    class EdgeCasesTest {
        @Test
        void createFromResultSet_Success_WhenCreatedAtIsNull() throws SQLException {
            // Given
            when(resultSet.getString("item_type")).thenReturn("ELECTRONICS");
            when(resultSet.getTimestamp("created_at")).thenReturn(null);

            when(resultSet.getString("brand")).thenReturn("Samsung");
            when(resultSet.getString("model")).thenReturn("S24 Ultra");
            when(resultSet.getInt("warranty_months")).thenReturn(24);

            // When
            Item result = ItemFactory.createFromResultSet(resultSet);

            // Then
            assertNotNull(result);
            assertNotNull(result.getCreatedAt()); // Sửa thành getCreatedAt
        }

        @Test
        void createFromResultSet_ThrowsException_WhenTypeIsUnknown() throws SQLException {
            // Given
            when(resultSet.getString("item_type")).thenReturn("UNKNOWN_TYPE");

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                ItemFactory.createFromResultSet(resultSet);
            });

            assertTrue(exception.getMessage().contains("Loại hàng lạ: UNKNOWN_TYPE"));
        }
    }
}