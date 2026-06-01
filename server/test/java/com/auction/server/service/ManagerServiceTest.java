package com.auction.server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.exception.AuctionException;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.service.ItemService;
import com.auction.server.service.ManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    @Mock private ItemService itemService;
    @Mock private AuctionDAO  auctionDAO;
    @Mock private UserDAO     userDAO;

    private ManagerService managerService;

    @BeforeEach
    void setUp() throws Exception {
        managerService = new ManagerService(itemService);

        Field auctionField = ManagerService.class.getDeclaredField("auctionDAO");
        auctionField.setAccessible(true);
        auctionField.set(managerService, auctionDAO);

        Field userField = ManagerService.class.getDeclaredField("userDAO");
        userField.setAccessible(true);
        userField.set(managerService, userDAO);
    }

    // Tạo nhanh đối tượng phòng đấu giá có cấu hình thời gian bắt đầu
    private Auction makeAuction(int id, String status, LocalDateTime startTime) {
        return new Auction(id, 10, 2, status, 500_000, 500_000, 0, null,
                startTime, LocalDateTime.now().plusDays(1), LocalDateTime.now());
    }

    // Tạo nhanh đối tượng phòng đấu giá mặc định
    private Auction makeAuction(int id, String status) {
        return makeAuction(id, status, LocalDateTime.now().minusHours(1));
    }

    // Tạo nhanh đối tượng sản phẩm để phục vụ test
    private Item makeItem(int id, double price) {
        return new Electronics(id, "Laptop", "Desc",
                price, "NEW", 2, "img.jpg", LocalDateTime.now(), "Dell", "XPS", 12);
    }

    @Nested @DisplayName("getAuction")
    class GetAuctionTests {

        // Test: Trả về null khi tìm kiếm phòng đấu giá với ID không tồn tại
        @Test @DisplayName("Trả về null khi không tìm thấy")
        void getAuction_notFound() {
            when(auctionDAO.getAuctionById(99)).thenReturn(null);
            assertNull(managerService.getAuction(99));
        }

        // Test: Lấy thông tin phòng đấu giá thành công khi tìm thấy ID hợp lệ
        @Test @DisplayName("Trả về đúng auction khi tìm thấy")
        void getAuction_found() {
            Auction a = makeAuction(1, "OPEN");
            when(auctionDAO.getAuctionById(1)).thenReturn(a);
            assertEquals("OPEN", managerService.getAuction(1).getAuctionStatus());
        }
    }

    @Nested @DisplayName("getAuctionOrThrow")
    class GetAuctionOrThrowTests {

        // Test: Ném lỗi hệ thống bắt buộc khi phòng đấu giá không tồn tại
        @Test @DisplayName("Ném lỗi AUCTION_NOT_FOUND khi không tìm thấy")
        void getAuctionOrThrow_notFound() {
            when(auctionDAO.getAuctionById(99)).thenReturn(null);
            AuctionException ex = assertThrows(AuctionException.class,
                    () -> managerService.getAuctionOrThrow(99));
            assertTrue(ex.getMessage().contains("AUCTION_NOT_FOUND")
                    || ex.getMessage().contains("không tồn tại"));
        }

        // Test: Trả về thông tin phòng đấu giá hợp lệ mà không ném lỗi
        @Test @DisplayName("Trả về auction hợp lệ khi tìm thấy")
        void getAuctionOrThrow_found() {
            when(auctionDAO.getAuctionById(1)).thenReturn(makeAuction(1, "RUNNING"));
            assertNotNull(managerService.getAuctionOrThrow(1));
        }
    }

    @Nested @DisplayName("getAllAuctions")
    class GetAllTests {

        // Test: Lấy ra toàn bộ danh sách phiên đấu giá hiện có từ cơ sở dữ liệu
        @Test @DisplayName("Trả về đúng danh sách từ DAO")
        void getAllAuctions_returnsList() {
            when(auctionDAO.getAll()).thenReturn(Arrays.asList(
                    makeAuction(1, "OPEN"),
                    makeAuction(2, "RUNNING")
            ));
            assertEquals(2, managerService.getAllAuctions().size());
        }

        // Test: Trả về danh sách trống khi hệ thống chưa khởi tạo phiên nào
        @Test @DisplayName("Trả về rỗng khi không có phiên nào")
        void getAllAuctions_empty() {
            when(auctionDAO.getAll()).thenReturn(List.of());
            assertTrue(managerService.getAllAuctions().isEmpty());
        }
    }

    @Nested @DisplayName("getUserById")
    class GetUserByIdTests {



        // Test: Trả về null khi tìm kiếm người dùng với ID không tồn tại
        @Test @DisplayName("Trả về null khi không tìm thấy")
        void getUserById_notFound() {
            when(userDAO.getUserById(99)).thenReturn(null);
            assertNull(managerService.getUserById(99));
        }
    }

    @Nested @DisplayName("scheduleAuction")
    class ScheduleTests {

        // Test: Thiết lập lịch đấu giá thành công, phiên mặc định ở trạng thái chờ duyệt
        @Test @DisplayName("Tạo phiên thành công, trạng thái WAITING_FOR_ADMIN")
        void schedule_success() {
            Item item = makeItem(10, 500_000);
            when(itemService.getItemById(10)).thenReturn(item);
            when(auctionDAO.insertAuction(any())).thenReturn(true);

            assertDoesNotThrow(() -> managerService.scheduleAuction(10,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(2)));

            verify(auctionDAO, times(1)).insertAuction(argThat(a ->
                    "WAITING_FOR_ADMIN".equals(a.getAuctionStatus())));
        }

        // Test: Chặn tạo lịch đấu giá nếu sản phẩm liên kết không tồn tại
        @Test @DisplayName("Ném lỗi khi item không tồn tại")
        void schedule_itemNotFound() {
            when(itemService.getItemById(99))
                    .thenThrow(new AuctionException("ITEM_NOT_FOUND", "Không tìm thấy"));

            assertThrows(AuctionException.class, () -> managerService.scheduleAuction(99,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(2)));

            verify(auctionDAO, never()).insertAuction(any());
        }

        // Test: Ném lỗi hệ thống nếu quá trình ghi nhận lịch đấu giá vào DB thất bại
        @Test @DisplayName("Ném lỗi khi DAO insertAuction thất bại")
        void schedule_daoFails() {
            Item item = makeItem(10, 500_000);
            when(itemService.getItemById(10)).thenReturn(item);
            when(auctionDAO.insertAuction(any())).thenReturn(false);

            assertThrows(AuctionException.class, () -> managerService.scheduleAuction(10,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(2)));
        }
    }

    @Nested @DisplayName("openAuction")
    class OpenAuctionTests {

        // Test: Mở phòng đấu giá thành công, chuyển từ trạng thái chờ duyệt sang công khai
        @Test @DisplayName("Chuyển WAITING_FOR_ADMIN → OPEN thành công")
        void openAuction_success() {
            Auction a = makeAuction(1, "WAITING_FOR_ADMIN");
            when(auctionDAO.getAuctionById(1)).thenReturn(a);
            when(auctionDAO.updateStatus(1, "OPEN")).thenReturn(true);

            assertDoesNotThrow(() -> managerService.openAuction(1));
            verify(auctionDAO, times(1)).updateStatus(1, "OPEN");
        }

        // Test: Chặn yêu cầu mở phòng nếu phiên không nằm ở trạng thái chờ duyệt
        @Test @DisplayName("Ném lỗi khi trạng thái không phải WAITING_FOR_ADMIN")
        void openAuction_wrongStatus() {
            Auction a = makeAuction(1, "OPEN");
            when(auctionDAO.getAuctionById(1)).thenReturn(a);

            assertThrows(AuctionException.class, () -> managerService.openAuction(1));
            verify(auctionDAO, never()).updateStatus(anyInt(), eq("OPEN"));
        }

        // Test: Chặn yêu cầu mở phòng đấu giá nếu ID phiên không tồn tại
        @Test @DisplayName("Ném lỗi khi phiên không tồn tại")
        void openAuction_notFound() {
            when(auctionDAO.getAuctionById(99)).thenReturn(null);
            assertThrows(AuctionException.class, () -> managerService.openAuction(99));
        }
    }

    @Nested @DisplayName("activateAuction")
    class ActivateAuctionTests {

        // Test: Kích hoạt phiên đấu giá sang trạng thái đang chạy khi đã đến giờ cấu hình
        @Test @DisplayName("Chuyển OPEN → RUNNING khi đã đến giờ bắt đầu")
        void activateAuction_success() {
            Auction a = makeAuction(1, "OPEN", LocalDateTime.now().minusHours(1));
            when(auctionDAO.getAuctionById(1)).thenReturn(a);
            when(auctionDAO.updateStatus(1, "RUNNING")).thenReturn(true);

            assertDoesNotThrow(() -> managerService.activateAuction(1));
            verify(auctionDAO, times(1)).updateStatus(1, "RUNNING");
        }

        // Test: Chặn kích hoạt phiên đấu giá nếu chưa tới thời gian bắt đầu quy định
        @Test @DisplayName("Ném lỗi khi chưa đến giờ bắt đầu")
        void activateAuction_notYetTime() {
            Auction a = makeAuction(1, "OPEN", LocalDateTime.now().plusHours(1));
            when(auctionDAO.getAuctionById(1)).thenReturn(a);

            assertThrows(AuctionException.class, () -> managerService.activateAuction(1));
            verify(auctionDAO, never()).updateStatus(anyInt(), eq("RUNNING"));
        }

        // Test: Chặn kích hoạt nếu phiên đấu giá chưa được Admin phê duyệt mở công khai
        @Test @DisplayName("Ném lỗi khi trạng thái không phải OPEN")
        void activateAuction_wrongStatus() {
            Auction a = makeAuction(1, "WAITING_FOR_ADMIN", LocalDateTime.now().minusHours(1));
            when(auctionDAO.getAuctionById(1)).thenReturn(a);

            assertThrows(AuctionException.class, () -> managerService.activateAuction(1));
        }

        // Test: Chặn yêu cầu kích hoạt nếu không tìm thấy phiên đấu giá tương ứng
        @Test @DisplayName("Ném lỗi khi phiên không tồn tại")
        void activateAuction_notFound() {
            when(auctionDAO.getAuctionById(99)).thenReturn(null);
            assertThrows(AuctionException.class, () -> managerService.activateAuction(99));
        }
    }
}