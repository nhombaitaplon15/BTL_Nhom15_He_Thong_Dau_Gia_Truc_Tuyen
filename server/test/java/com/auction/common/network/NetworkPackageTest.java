package com.auction.common.network;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NetworkPackageTest {

  /**
   * Hàm Helper: Giả lập quá trình đóng gói và gửi object qua mạng Socket (Serialization)
   */
  private Object simulateNetworkTransfer(Serializable originalObject) throws IOException, ClassNotFoundException {
    // 1. Đóng gói object thành luồng Byte (Giống hệt cách ObjectOutputStream làm)
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(originalObject);
    oos.close();

    // 2. Giải nén luồng Byte ngược lại thành Object (Giống hệt cách ObjectInputStream làm)
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);
    return ois.readObject();
  }

  // =================================================================================
  // 1. TEST CLASS MESSAGE (LÕI TRUYỀN TẢI CHÍNH)
  // =================================================================================

  @Test
  @DisplayName("Message - Gói tin Client gửi Server (Request)")
  void testMessage_ClientToServer() throws Exception {
    LoginDTO loginPayload = new LoginDTO("diep_nguyen", "123456");
    Message originalMsg = new Message(RequestCode.LOGIN, loginPayload);

    // Giả lập gửi qua mạng
    Message receivedMsg = (Message) simulateNetworkTransfer(originalMsg);

    assertEquals(RequestCode.LOGIN, receivedMsg.getRequestCode());
    assertNull(receivedMsg.getResponseCode(), "Gói tin Request không được có ResponseCode");

    // Kiểm tra payload bên trong
    LoginDTO receivedPayload = (LoginDTO) receivedMsg.getPayload();
    assertEquals("diep_nguyen", receivedPayload.getUsername());
  }

  @Test
  @DisplayName("Message - Gói tin Server gửi Client (Response)")
  void testMessage_ServerToClient() throws Exception {
    Message originalMsg = new Message(ResponseCode.LOGIN_SUCCESS, "Đăng nhập thành công", 99);

    // Giả lập gửi qua mạng
    Message receivedMsg = (Message) simulateNetworkTransfer(originalMsg);

    assertEquals(ResponseCode.LOGIN_SUCCESS, receivedMsg.getResponseCode());
    assertEquals("Đăng nhập thành công", receivedMsg.getMessage());
    assertEquals(99, receivedMsg.getPayload());
    assertNull(receivedMsg.getRequestCode());
  }

  // =================================================================================
  // 2. TEST CÁC LỚP DTO (DATA TRANSFER OBJECTS)
  // =================================================================================

  @Test
  @DisplayName("RegisterDTO - Chứa đúng thông tin và truyền tải thành công")
  void testRegisterDTO() throws Exception {
    RegisterDTO dto = new RegisterDTO("diep", "pass123", "diep@test.com", "0123456789", "BIDDER");

    RegisterDTO received = (RegisterDTO) simulateNetworkTransfer(dto);

    assertEquals("diep", received.getUsername());
    assertEquals("pass123", received.getPassword());
    assertEquals("BIDDER", received.getRole());
  }

  @Test
  @DisplayName("BidPlaceDTO - Đóng gói dữ liệu đặt giá chuẩn xác")
  void testBidPlaceDTO() throws Exception {
    BidPlaceDTO dto = new BidPlaceDTO(505, 150000.0);

    BidPlaceDTO received = (BidPlaceDTO) simulateNetworkTransfer(dto);

    assertEquals(505, received.getAuctionId());
    assertEquals(150000.0, received.getBidAmount());
  }

  @Test
  @DisplayName("CreateAuctionDTO - Getter/Setter và Constructor đã hoạt động chuẩn")
  void testCreateAuctionDTO() throws Exception {
    LocalDateTime start = LocalDateTime.now();
    LocalDateTime end = start.plusDays(3);

    CreateAuctionDTO dto = new CreateAuctionDTO(10, 999, 5000.0, start, end);

    CreateAuctionDTO received = (CreateAuctionDTO) simulateNetworkTransfer(dto);

    assertEquals(10, received.getSellerId());
    assertEquals(999, received.getItemId());
    assertEquals(5000.0, received.getStartingPrice());
    assertEquals(start, received.getStartTime());

    received.setSellerId(20);
    assertEquals(20, received.getSellerId(), "Hàm setSellerId phải gán đúng vào sellerId, không phải itemId");
  }

  // =================================================================================
  // FIX LỖI: TẠO STATIC CLASS ĐỂ KHÔNG BỊ KÉO THEO NETWORKPACKAGETEST KHI SERIALIZE
  // =================================================================================
  private static class ConcreteTestItem extends Item {
    public ConcreteTestItem(int itemId, String name, String description, String itemType, double startingPrice, String itemCondition, int sellerId, String imgItem, LocalDateTime createdAt) {
      super(itemId, name, description, itemType, startingPrice, itemCondition, sellerId, imgItem, createdAt);
    }

    @Override
    public String getDetailedSpecs() {
      return "Kích thước: 20x30cm, Niên đại: Thế kỷ 19";
    }
  }

  @Test
  @DisplayName("AuctionItemDTO - Truyền tải tổ hợp đối tượng Item và Auction")
  void testAuctionItemDTO() throws Exception {
    // ĐÃ FIX: Sử dụng ConcreteTestItem (Static Class) thay vì Anonymous Class để tránh lỗi Serializable
    Item mockItem = new ConcreteTestItem(1, "Bình gốm", "Đồ cổ", "ART", 100.0, "Cũ", 5, "anh_gom.jpg", LocalDateTime.now());

    Auction mockAuction = new Auction();
    mockAuction.setAuctionId(123);
    mockAuction.setCurrentPrice(200.0);

    AuctionItemDTO dto = new AuctionItemDTO(mockItem, mockAuction);

    AuctionItemDTO received = (AuctionItemDTO) simulateNetworkTransfer(dto);

    assertNotNull(received.getItem());
    assertNotNull(received.getAuction());
    assertEquals("Bình gốm", received.getItem().getName());
    assertEquals(123, received.getAuction().getAuctionId());
  }
}