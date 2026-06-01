package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.service.AdminService;
import com.auction.server.service.ManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ManagerService managerService;

    @Mock
    private AuctionDAO auctionDAO;

    private AdminService adminService;

    @BeforeEach
    void setUp() throws Exception {
        adminService = new AdminService(managerService);

        Field field = AdminService.class.getDeclaredField("auctionDAO");
        field.setAccessible(true);
        field.set(adminService, auctionDAO);
    }

    // Tạo nhanh đối tượng phòng đấu giá để test
    private Auction makeAuction(int id, String status) {
        return new Auction(id, 10, 2, status, 500_000, 500_000, 0, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), LocalDateTime.now());
    }

    @Nested
    @DisplayName("approveAuction")
    class ApproveTests {

        // Test: Duyệt phiên thành công và trạng thái được cập nhật sang OPEN
        @Test
        @DisplayName("Duyệt phiên thành công từ WAITING_FOR_ADMIN → OPEN")
        void approve_success() {
            Auction auction = makeAuction(1, "WAITING_FOR_ADMIN");
            when(managerService.getAuction(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "OPEN")).thenReturn(true);

            assertTrue(adminService.approveAuction(1));
            assertEquals("OPEN", auction.getAuctionStatus());
        }

        // Test: Hệ thống ghi nhận trạng thái APPROVED vào lịch sử kiểm toán sau khi duyệt
        @Test
        @DisplayName("Ghi audit log sau khi duyệt thành công")
        void approve_logsAudit() {
            Auction auction = makeAuction(1, "WAITING_FOR_ADMIN");
            when(managerService.getAuction(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "OPEN")).thenReturn(true);

            adminService.approveAuction(1);
            assertEquals("APPROVED", adminService.getAudit(1));
        }

        // Test: Ném lỗi hệ thống nếu phiên cần duyệt không tồn tại
        @Test
        @DisplayName("Ném lỗi khi phiên không tồn tại (null)")
        void approve_auctionNotFound() {
            when(managerService.getAuction(99)).thenReturn(null);

            assertThrows(AuctionException.class,
                    () -> adminService.approveAuction(99));
        }

        // Test: Chặn duyệt nếu phiên không ở trạng thái chờ phê duyệt của Admin
        @Test
        @DisplayName("Ném lỗi khi phiên không ở trạng thái WAITING_FOR_ADMIN")
        void approve_wrongStatus() {
            Auction auction = makeAuction(1, "OPEN");
            when(managerService.getAuction(1)).thenReturn(auction);

            assertThrows(AuctionException.class, () -> adminService.approveAuction(1));
        }

        // Test: Ném lỗi hệ thống khi DB cập nhật trạng thái duyệt thất bại
        @Test
        @DisplayName("Ném lỗi khi DAO updateStatus trả về false")
        void approve_daoReturnsFalse() {
            Auction auction = makeAuction(1, "WAITING_FOR_ADMIN");
            when(managerService.getAuction(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "OPEN")).thenReturn(false);

            assertThrows(AuctionException.class, () -> adminService.approveAuction(1));
        }
    }

    @Nested
    @DisplayName("rejectAuction")
    class RejectTests {

        // Test: Từ chối duyệt thành công khi có lý do hợp lệ
        @Test
        @DisplayName("Từ chối phiên thành công với lý do hợp lệ")
        void reject_success() {
            Auction auction = makeAuction(1, "WAITING_FOR_ADMIN");
            when(managerService.getAuction(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "REJECTED")).thenReturn(true);

            assertDoesNotThrow(() -> adminService.rejectAuction(1, "Vi phạm quy định"));
            assertEquals("REJECTED", auction.getAuctionStatus());
        }

        // Test: Không cho phép từ chối duyệt nếu lý do bị bỏ trống null
        @Test
        @DisplayName("Ném lỗi khi lý do từ chối là null")
        void reject_nullReason() {
            assertThrows(AuctionException.class,
                    () -> adminService.rejectAuction(1, null));
        }

        // Test: Không cho phép từ chối duyệt nếu lý do chỉ toàn khoảng trắng
        @Test
        @DisplayName("Ném lỗi khi lý do từ chối là chuỗi rỗng hoặc toàn khoảng trắng")
        void reject_emptyReason() {
            assertThrows(AuctionException.class,
                    () -> adminService.rejectAuction(1, "   "));
        }

        // Test: Ném lỗi hệ thống nếu phiên cần từ chối không tồn tại
        @Test
        @DisplayName("Ném lỗi khi phiên không tồn tại")
        void reject_auctionNotFound() {
            when(managerService.getAuction(99)).thenReturn(null);
            assertThrows(AuctionException.class,
                    () -> adminService.rejectAuction(99, "Lý do hợp lệ"));
        }

        // Test: Chặn từ chối nếu phiên không ở trạng thái chờ phê duyệt của Admin
        @Test
        @DisplayName("Ném lỗi khi phiên không ở trạng thái WAITING_FOR_ADMIN")
        void reject_wrongStatus() {
            Auction auction = makeAuction(1, "RUNNING");
            when(managerService.getAuction(1)).thenReturn(auction);
            assertThrows(AuctionException.class,
                    () -> adminService.rejectAuction(1, "Lý do"));
        }

        // Test: Ném lỗi hệ thống khi DB cập nhật trạng thái từ chối thất bại
        @Test
        @DisplayName("Ném lỗi khi DAO updateStatus trả về false")
        void reject_daoReturnsFalse() {
            Auction auction = makeAuction(1, "WAITING_FOR_ADMIN");
            when(managerService.getAuction(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "REJECTED")).thenReturn(false);
            assertThrows(AuctionException.class,
                    () -> adminService.rejectAuction(1, "Lý do"));
        }
    }

    @Nested
    @DisplayName("getPendingAuctions")
    class PendingAuctionsTests {

        // Test: Lọc và trả về chính xác danh sách các phiên đang ở trạng thái chờ duyệt
        @Test
        @DisplayName("Trả về đúng danh sách phiên đang chờ duyệt")
        void getPending_returnsCorrectList() {
            List<Auction> all = Arrays.asList(
                    makeAuction(1, "WAITING_FOR_ADMIN"),
                    makeAuction(2, "OPEN"),
                    makeAuction(3, "WAITING_FOR_ADMIN")
            );
            when(managerService.getAllAuctions()).thenReturn(all);

            List<Auction> result = adminService.getPendingAuctions();
            assertEquals(2, result.size());
            assertTrue(result.stream()
                    .allMatch(a -> "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())));
        }

        // Test: Trả về danh sách trống khi tất cả các phiên đều đã được mở công khai
        @Test
        @DisplayName("Trả về danh sách rỗng khi không có phiên nào chờ duyệt")
        void getPending_emptyWhenNonePending() {
            when(managerService.getAllAuctions()).thenReturn(
                    Collections.singletonList(makeAuction(1, "OPEN"))
            );
            assertTrue(adminService.getPendingAuctions().isEmpty());
        }

        // Test: Trả về danh sách trống khi hệ thống chưa có bất kỳ phiên đấu giá nào
        @Test
        @DisplayName("Trả về danh sách rỗng khi không có phiên nào")
        void getPending_emptyWhenNoAuctions() {
            when(managerService.getAllAuctions()).thenReturn(Collections.emptyList());
            assertTrue(adminService.getPendingAuctions().isEmpty());
        }
    }

    @Nested
    @DisplayName("getAudit / clearData")
    class AuditTests {

        // Test: Trả về thông báo mặc định nếu phiên được chọn chưa từng có lịch sử tác động
        @Test
        @DisplayName("Trả về thông báo mặc định khi chưa có log")
        void getAudit_noHistory() {
            assertEquals("Không có lịch sử cho phiên này.", adminService.getAudit(999));
        }

        // Test: Đảm bảo dữ liệu lịch sử kiểm toán bị xóa sạch hoàn toàn sau khi gọi hàm dọn dẹp
        @Test
        @DisplayName("clearData xóa toàn bộ audit log")
        void clearData_clearsAllLogs() {
            Auction auction = makeAuction(1, "WAITING_FOR_ADMIN");
            when(managerService.getAuction(1)).thenReturn(auction);
            when(auctionDAO.updateStatus(1, "OPEN")).thenReturn(true);

            adminService.approveAuction(1);
            assertEquals("APPROVED", adminService.getAudit(1));

            adminService.clearData();
            assertEquals("Không có lịch sử cho phiên này.", adminService.getAudit(1));
        }
    }
}