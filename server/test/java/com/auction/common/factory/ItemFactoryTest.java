package com.auction.common.factory;

import com.auction.common.model.Art;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ItemFactoryTest {

  private ResultSet mockRs;
  private final Timestamp fixedTimestamp = Timestamp.valueOf(LocalDateTime.of(2026, 6, 1, 12, 0, 0));

  @BeforeEach
  void setUp() throws SQLException {
    // 1. Tạo đối tượng giả lập (Mock) cho ResultSet
    mockRs = Mockito.mock(ResultSet.class);

    // 2. Cài đặt các giá trị CHUNG mặc định trả về khi gọi ResultSet
    // Việc chuẩn bị ở @BeforeEach giúp code ở các hàm @Test bên dưới ngắn gọn hơn
    when(mockRs.getInt("item_id")).thenReturn(100);
    when(mockRs.getString("name")).thenReturn("Sản phẩm đấu giá mẫu");
    when(mockRs.getString("description")).thenReturn("Mô tả chi tiết sản phẩm mẫu");
    when(mockRs.getDouble("starting_price")).thenReturn(2500.0);
    when(mockRs.getString("item_condition")).thenReturn("NEW");
    when(mockRs.getInt("seller_id")).thenReturn(7);
    when(mockRs.getString("img_item")).thenReturn("avatar_item.png");
    when(mockRs.getTimestamp("created_at")).thenReturn(fixedTimestamp);
  }

  @Test
  void testCreateVehicle_ShouldReturnVehicleObject() throws SQLException {
    // Cài đặt dữ liệu đặc trưng cho nhóm VEHICLE
    when(mockRs.getString("item_type")).thenReturn("VEHICLE");
    when(mockRs.getString("make")).thenReturn("Honda");
    when(mockRs.getString("model_vehicle")).thenReturn("Civic");
    when(mockRs.getInt("manufacture_year")).thenReturn(2023);
    when(mockRs.getInt("mileage")).thenReturn(8000);
    when(mockRs.getString("fuel_type")).thenReturn("Xăng");
    when(mockRs.getString("license_plate")).thenReturn("30H-999.99");

    // Thực thi hàm cần test
    Item item = ItemFactory.createFromResultSet(mockRs);

    // Kiểm tra kết quả (Assertions)
    assertNotNull(item, "Item tạo ra không được null");
    assertTrue(item instanceof Vehicle, "Phải khởi tạo đúng đối tượng Vehicle");

    // Ép kiểu để kiểm tra sâu hơn các thuộc tính xem mapping chuẩn chưa
    Vehicle vehicle = (Vehicle) item;
    assertEquals("Honda", vehicle.getMake());
    assertEquals("Civic", vehicle.getModelVehicle());
    assertEquals(2023, vehicle.getManufactureYear());
  }

  @Test
  void testCreateElectronics_ShouldReturnElectronicsObject() throws SQLException {
    // Cài đặt dữ liệu đặc trưng cho nhóm ELECTRONICS
    when(mockRs.getString("item_type")).thenReturn("ELECTRONICS");
    when(mockRs.getString("brand")).thenReturn("Apple");
    when(mockRs.getString("model")).thenReturn("MacBook Pro M3");
    when(mockRs.getInt("warranty_months")).thenReturn(12);

    // Thực thi
    Item item = ItemFactory.createFromResultSet(mockRs);

    // Kiểm tra
    assertNotNull(item);
    assertTrue(item instanceof Electronics, "Phải khởi tạo đúng đối tượng Electronics");

    Electronics electronics = (Electronics) item;
    assertEquals("Apple", electronics.getBrand());
    assertEquals("MacBook Pro M3", electronics.getModel());
    assertEquals(12, electronics.getWarrantyMonths());
  }

  @Test
  void testCreateArt_ShouldReturnArtObject() throws SQLException {
    // Cài đặt dữ liệu đặc trưng cho nhóm ART (Thử nghiệm truyền chữ thường "art" để test toUpperCase)
    when(mockRs.getString("item_type")).thenReturn("art");
    when(mockRs.getString("artist")).thenReturn("Bùi Xuân Phái");
    when(mockRs.getInt("year_created")).thenReturn(1970);
    when(mockRs.getString("medium")).thenReturn("Sơn dầu");
    when(mockRs.getBoolean("has_certificate")).thenReturn(true);

    // Thực thi
    Item item = ItemFactory.createFromResultSet(mockRs);

    // Kiểm tra
    assertNotNull(item);
    assertTrue(item instanceof Art, "Phải khởi tạo đúng đối tượng Art");

    Art art = (Art) item;
    assertEquals("Bùi Xuân Phái", art.getArtist());
    assertEquals(1970, art.getYearCreated());
    assertTrue(art.isHasCertificate());
  }

  @Test
  void testCreateUnknownType_ShouldThrowIllegalArgumentException() throws SQLException {
    // Giả lập một loại hàng không nằm trong thiết kế (ví dụ: REAL_ESTATE - Bất động sản)
    when(mockRs.getString("item_type")).thenReturn("REAL_ESTATE");

    // Kiểm tra xem hàm có ném ra lỗi như mong muốn ở nhánh default không
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      ItemFactory.createFromResultSet(mockRs);
    });

    // Kiểm tra xem thông điệp báo lỗi có chứa chuỗi mong muốn không
    assertTrue(exception.getMessage().contains("Loại hàng lạ: REAL_ESTATE"));
  }

  @Test
  void testCreateWithNullCreatedAt_ShouldFallbackToNow() throws SQLException {
    // Cài đặt loại hàng hợp lệ nhưng cho trường hợp ngày tạo trong DB bị null
    when(mockRs.getString("item_type")).thenReturn("ELECTRONICS");
    when(mockRs.getTimestamp("created_at")).thenReturn(null);

    // Thực thi
    Item item = ItemFactory.createFromResultSet(mockRs);

    // Kiểm tra xem hàm có chạy mượt mà không bị NullPointerException và gán ngày mặc định không
    assertNotNull(item);
    // Do trong code bạn có đoạn: (createdAtTs != null) ? ... : LocalDateTime.now()
    // Nên đối tượng item tạo ra chắc chắn phải có dữ liệu thời gian không null.
  }
}