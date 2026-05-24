package server.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.UserDAO;

import java.time.LocalDateTime;
import java.util.List;

public class ManagerService {

    private final ItemService itemService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final UserDAO userDAO = new UserDAO();
    private volatile boolean running = true;

    public ManagerService(ItemService itemService) {
        this.itemService = itemService;
    }

    // --- 1. LẤY DỮ LIỆU ---

    public Auction getAuction(int auctionId) {
        return auctionDAO.getAuctionById(auctionId);
    }

    public User getUserById(int userId) {
        return userDAO.getUserById(userId);
    }

    public List<Auction> getAllAuctions() {
        return auctionDAO.getAll();
    }

    public Auction getAuctionOrThrow(int auctionId) {
        Auction auction = auctionDAO.getAuctionById(auctionId);
        if (auction == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND.name(), "Phiên đấu giá không tồn tại");
        }
        return auction;
    }

    // --- 2. THIẾT LẬP PHIÊN YÊU CẦU ---

    public void scheduleAuction(int itemId, LocalDateTime startTime, LocalDateTime endTime) {
        Item item = itemService.getItemById(itemId);
        if (item == null) {
            throw new AuctionException(ErrorCode.ITEM_NOT_FOUND.name(), "Sản phẩm không tồn tại");
        }

        // Tạo Auction mặc định đẩy trạng thái vào WAITING_FOR_ADMIN để chờ duyệt
        Auction auction = new Auction(
                0, item.getItemId(), item.getSellerId(), "WAITING_FOR_ADMIN",
                item.getStartingPrice(), item.getStartingPrice(),
                0, null, startTime, endTime, LocalDateTime.now()
        );

        if (!auctionDAO.insertAuction(auction)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi khi lưu phiên đấu giá vào Database");
        }
        System.out.println("[MANAGER] Đã lên lịch chờ duyệt (Scheduled) cho Item: " + itemId);
    }

    // --- 3. ĐIỀU KHIỂN TRẠNG THÁI (STATUS) ---

    public void openAuction(int auctionId) {
        // 🛠️ SỬA LỖI: Trạng thái gốc phải là WAITING_FOR_ADMIN khớp với lúc schedule
        transitStatus(auctionId, "WAITING_FOR_ADMIN", "OPEN");
        System.out.println("[MANAGER] Đã DUYỆT (OPEN) phiên đấu giá " + auctionId);
    }

    public void activateAuction(int auctionId) {
        Auction auction = getAuctionOrThrow(auctionId);
        if (!"OPEN".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Auction chưa được duyệt (OPEN)");
        }
        if (LocalDateTime.now().isBefore(auction.getStartTime())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Chưa đến giờ bắt đầu");
        }
        transitStatus(auctionId, "OPEN", "RUNNING");
        System.out.println("[MANAGER] Phiên " + auctionId + " đang CHẠY (RUNNING)");
    }

    private void transitStatus(int id, String from, String to) {
        Auction a = getAuctionOrThrow(id);
        if (!from.equals(a.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Trạng thái hiện tại không hợp lệ. Cần: " + from);
        }
        auctionDAO.updateStatus(id, to);
    }

    // --- 4. TỰ ĐỘNG HÓA QUÉT SQL (BOT BACKGROUND) ---

    public void autoCloseAuction() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000); // Quét mỗi giây 1 lần
                    LocalDateTime now = LocalDateTime.now();

                    // NHIỆM VỤ 1: Kiểm tra xem đã ĐẾN GIỜ MỞ CHẠY chưa (OPEN -> RUNNING)
                    List<Auction> openAuctions = auctionDAO.getAuctionsByStatus("OPEN");
                    for (Auction auction : openAuctions) {
                        if (!now.isBefore(auction.getStartTime())) {
                            auctionDAO.updateStatus(auction.getAuctionId(), "RUNNING");
                            System.out.println("[AUTO-BOT] Đã đến giờ! Phiên " + auction.getAuctionId() + " -> RUNNING");
                        }
                    }

                    // NHIỆM VỤ 2: Kiểm tra xem đã HẾT GIỜ KẾT THÚC chưa (RUNNING -> SOLD/ENDED)
                    List<Auction> activeAuctions = auctionDAO.getAuctionsByStatus("RUNNING");
                    for (Auction auction : activeAuctions) {
                        if (now.isAfter(auction.getEndTime())) {
                            // Cẩn thận lỗi Integer Null
                            String finalStatus = (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId() > 0) ? "SOLD" : "ENDED";
                            auctionDAO.updateStatus(auction.getAuctionId(), finalStatus);
                            System.out.println("[AUTO-BOT] Hết giờ! Phiên " + auction.getAuctionId() + " -> " + finalStatus);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Bắt Exception tổng để nếu có 1 phiên bị lỗi thì Bot không bị chết hẳn
                    System.err.println("[AUTO-BOT LỖI] " + e.getMessage());
                }
            }
        });
        t.setDaemon(true); // Thread tự chết khi tắt Server chính
        t.start();
    }

    // --- 5. DỌN DẸP HỆ THỐNG ---

    public void stopAutoClose() {
        this.running = false;
        System.out.println("[MANAGER] Đã tắt Bot quét phiên đấu giá.");
    }

    public void clearData() {
        System.out.println("[MANAGER] Dữ liệu lưu an toàn dưới SQL, hệ thống hoạt động ổn định.");
    }
}