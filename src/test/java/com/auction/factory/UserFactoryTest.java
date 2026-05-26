package com.auction.factory;

import com.auction.common.model.Admin;
import com.auction.common.model.Bidder;
import com.auction.common.model.Seller;
import com.auction.common.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

// --- ĐÂY LÀ KHU VỰC IMPORT STATIC QUYẾT ĐỊNH ĐỂ HẾT BÁO ĐỎ ---
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserFactoryTest {

    // Dữ liệu giả lập để test
    private final int id = 1;
    private final String name = "Nguyen Van A";
    private final String email = "test@auction.com";
    private final String password = "password123";
    private final String phone = "0987654321";
    private final String status = "ACTIVE";
    private final double balance = 1000.0;

    @Test
    @DisplayName("Test logic thực tế: Tạo đúng các đối tượng Admin, Seller, Bidder")
    void testRealLogicWithFactory() {
        // 1. Test ADMIN
        User admin = UserFactory.createUser(id, name, email, password, phone, status, "ADMIN", balance);
        assertNotNull(admin);
        assertInstanceOf(Admin.class, admin);

        // 2. Test SELLER (chữ thường để check toUpperCase)
        User seller = UserFactory.createUser(id, name, email, password, phone, status, "seller", balance);
        assertNotNull(seller);
        assertInstanceOf(Seller.class, seller);

        // 3. Test BIDDER
        User bidder = UserFactory.createUser(id, name, email, password, phone, status, "BIDDER", balance);
        assertNotNull(bidder);
        assertInstanceOf(Bidder.class, bidder);

        // 4. Test trường hợp role lạ (Default quay về Bidder)
        User unknown = UserFactory.createUser(id, name, email, password, phone, status, "SUPERMAN", balance);
        assertNotNull(unknown);
        assertInstanceOf(Bidder.class, unknown);

        // 5. Test trường hợp role bị null
        User nullRole = UserFactory.createUser(id, name, email, password, phone, status, null, balance);
        assertNull(nullRole);
    }

    @Test
    @DisplayName("Dùng Mockito mockStatic: Ép hàm static trả về đối tượng mock theo ý muốn")
    void testUserFactoryWithMockitoStatic() {
        // Tạo một đối tượng Admin giả lập bằng Mockito
        Admin mockAdmin = mock(Admin.class);
        when(mockAdmin.getUsername()).thenReturn("Admin Đã Được Mock thành công!");

        // Mở vùng không gian mock static cho UserFactory
        try (MockedStatic<UserFactory> mockedFactory = Mockito.mockStatic(UserFactory.class)) {

            // Định nghĩa: Khi gọi UserFactory.createUser với bất kỳ tham số nào, ép trả về mockAdmin
            mockedFactory.when(() -> UserFactory.createUser(
                    anyInt(), anyString(), anyString(), anyString(), anyString(), anyString(), eq("ADMIN"), anyDouble()
            )).thenReturn(mockAdmin);

            // Thực thi hàm test
            User result = UserFactory.createUser(id, name, email, password, phone, status, "ADMIN", balance);

            // Kiểm tra kết quả xem có ăn theo đối tượng mock không
            assertNotNull(result);
            assertEquals("Admin Đã Được Mock thành công!", result.getUsername());

            // Xác minh phương thức static thực sự đã được gọi đúng 1 lần
            mockedFactory.verify(() -> UserFactory.createUser(id, name, email, password, phone, status, "ADMIN", balance), times(1));
        }
    }
}
