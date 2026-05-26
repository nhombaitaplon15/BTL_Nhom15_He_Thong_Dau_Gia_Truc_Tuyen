package com.auction.common.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {

    @Nested
    class ClientRequestConstructorTest {
        @Test
        void testRequestConstructor_Success() {
            // Given
            String command = "LOGIN";
            String testData = "SamplePayloadData";

            // When: Dùng constructor Client -> Server
            Message message = new Message(command, testData);

            // Then: Các trường còn lại (status, note) phải mặc định là null
            assertNotNull(message);
            assertEquals("LOGIN", message.getCommand());
            assertEquals("SamplePayloadData", message.getData());
            assertNull(message.getStatus());
            assertNull(message.getNote());
        }
    }

    @Nested
    class ServerResponseConstructorTest {
        @Test
        void testResponseConstructor_Success() {
            // Given
            String status = "SUCCESS";
            String note = "Đăng nhập thành công!";
            String responseData = "UserDataObject";

            // When: Dùng constructor Server -> Client
            Message message = new Message(status, note, responseData);

            // Then: Trường command phải mặc định là null
            assertNotNull(message);
            assertEquals("SUCCESS", message.getStatus());
            assertEquals("Đăng nhập thành công!", message.getNote());
            assertEquals("UserDataObject", message.getData());
            assertNull(message.getCommand());
        }
    }

    @Nested
    class SettersAndGettersTest {
        @Test
        void testSettersAndGetters_ModifyDataCorrectly() {
            // Given: Khởi tạo thông qua một constructor bất kỳ
            Message message = new Message("TEMP_CMD", null);

            // When: Thay đổi toàn bộ dữ liệu bằng Setter
            message.setCommand("CREATE_AUCTION");
            message.setStatus("FAILED");
            message.setNote("Số dư không đủ để tạo đấu giá");
            message.setData(500000); // Thử nghiệm truyền một kiểu dữ liệu dạng số (Object)

            // Then: Kiểm tra Getters lấy ra đúng giá trị mới cập nhật
            assertEquals("CREATE_AUCTION", message.getCommand());
            assertEquals("FAILED", message.getStatus());
            assertEquals("Số dư không đủ để tạo đấu giá", message.getNote());
            assertEquals(500000, message.getData());
        }
    }
}