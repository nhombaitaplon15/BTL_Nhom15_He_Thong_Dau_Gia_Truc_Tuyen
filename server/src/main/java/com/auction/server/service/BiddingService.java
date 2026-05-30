package src.main.java.com.auction.server.service;  // [SỬA DÒNG 1] server.service -> com.auction.server.service

import com.auction.common.model.Auction;
import com.auction.common.model.BidHistoryRow;
import com.auction.common.model.BiddingHistory;
import com.auction.common.model.User;
import com.auction.common.exception.AuctionException ;  // [SỬA] com.auction.exception -> com.auction.common.exception
import com.auction.common.exception.ErrorCode ;         // [SỬA] com.auction.exception -> com.auction.common.exception
import src.main.java.com.auction.server.dao.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BiddingService - Xử lý toàn bộ logic nghiệp vụ đặt giá và hủy kèo.
 *
 * ĐÃ SỬA:
 * 1. Dòng 1: package server.service -> com.auction.server.service
 * 2. Import exception: com.auction.exception -> com.auction.common.exception
 * 3. Thêm getManagerService() để AuctionRoom.processBid() có thể inject
 *
 * Đặt tại: server/src/main/java/com/auction/server/service/BiddingService.java
 */
public class BiddingService {

    private final ManagerService managerService;
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final Map<Integer, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private final BiddingHistoryDAO biddingHistoryDAO = new BiddingHistoryDAO();

    // Danh sách 4 ID Admin hệ thống quản lý ví tạm giữ cọc từ file của bạn bạn
    private static final int[] ADMIN_IDS = {1, 2, 3, 4};

    /** Constructor đầy đủ dành cho môi trường chạy qua ManagerService tập trung */
    public BiddingService(ManagerService managerService) {
        this.managerService = managerService;
    }

    /** Constructor mặc định dành cho kết nối Database độc lập */
    public BiddingService() {
        this.managerService = null;
    }

    /** [GIỮ LẠI TỪ FILE 1] Accessor để AuctionRoom.processBid() từ hệ thống mạng truy cập */
    public ManagerService getManagerService() {
        return managerService;
    }

    /** Luồng đặt giá qua cơ chế lưu RAM tập trung (ManagerService) */
    public void placeBid(User user, int auctionId, double bidAmount) {
        if (managerService == null) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "ManagerService mạng chưa được khởi tạo!");
        }

        Auction auction = managerService.getAuctionOrThrow(auctionId);
        validateBidRules(user, auction, bidAmount);

        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            if (bidAmount <= auction.getCurrentPrice()) {
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đã bị thay đổi, vui lòng đặt cao hơn!");
            }

            executeBidTransaction(user, auction, bidAmount);
            System.out.println("[BID] " + user.getUsername() + " đặt giá " + bidAmount + " thành công!");
        } finally {
            lock.unlock();
        }
    }

    /** LUỒNG ĐẶT GIÁ TRỰC TIẾP: Đọc trực tiếp từ DB để đồng bộ giao diện */
    public void placeBidDirectFromDB(User user, int auctionId, double bidAmount) {
        Auction freshAuction = auctionDAO.getAuctionById(auctionId);
        if (freshAuction == null) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Không tìm thấy phiên đấu giá này!");
        }

        validateBidRules(user, freshAuction, bidAmount);

        ReentrantLock lock = lockMap.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            if (bidAmount <= freshAuction.getCurrentPrice()) {
                throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đặt phải cao hơn giá hiện tại!");
            }

            executeBidTransaction(user, freshAuction, bidAmount);
            System.out.println("[BID DIRECT] " + user.getUsername() + " đặt giá " + bidAmount + " thành công!");
        } finally {
            lock.unlock();
        }
    }

    /** TRANSACTION DÒNG TIỀN NGUYÊN TỬ (Giữ nguyên thuật toán Admin Escrow của bạn bạn) */
    private void executeBidTransaction(User user, Auction auction, double amount) {
        try (Connection conn = DBConnection.getConnection()) { // Dùng DBConnection thống nhất
            conn.setAutoCommit(false);
            try {
                // BƯỚC 1: HOÀN TIỀN CHO NGƯỜI ĐANG GIỮ GIÁ CAO NHẤT CŨ (NẾU CÓ)
                if (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId() > 0) {
                    int oldWinnerId = auction.getCurrentWinnerId();
                    double oldPrice = auction.getCurrentPrice();

                    if (oldWinnerId != user.getId() && oldPrice > 0) {
                        int oldAdminId = ADMIN_IDS[auction.getAuctionId() % ADMIN_IDS.length];

                        // Khấu trừ hoàn trả: Giảm ví tạm (escrow_balance) của Admin cũ
                        String sqlMinusAdminEscrow = "UPDATE users SET escrow_balance = escrow_balance - ? WHERE user_id = ? AND escrow_balance >= ?";
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(sqlMinusAdminEscrow)) {
                            ps.setDouble(1, oldPrice);
                            ps.setInt(2, oldAdminId);
                            ps.setDouble(3, oldPrice);
                            ps.executeUpdate();
                        }

                        // Hoàn trả tiền vào ví chính cho người bị đè giá
                        boolean refundOk = paymentDAO.updateBalance(conn, oldWinnerId, oldPrice, "+");

                        if (!refundOk) {
                            String bypassSql = "UPDATE users SET balance = balance + ? WHERE user_id = ?";
                            try (java.sql.PreparedStatement ps = conn.prepareStatement(bypassSql)) {
                                ps.setDouble(1, oldPrice);
                                ps.setInt(2, oldWinnerId);
                                if (ps.executeUpdate() <= 0) {
                                    throw new SQLException("Tài khoản người giữ giá cũ không tồn tại trên hệ thống!");
                                }
                            }
                        }
                        transactionDAO.createTransaction(conn, oldWinnerId, oldPrice, "REFUND_OVERBID_" + auction.getAuctionId(), "SUCCESS");
                    }
                }

                // BƯỚC 2: TRỪ TIỀN NGƯỜI ĐẶT MỚI VÀ TĂNG VÍ TẠM ADMIN QUẢN LÝ PHÒNG
                String sqlCheckBalance = "SELECT balance FROM users WHERE user_id = ? AND balance >= ?";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sqlCheckBalance)) {
                    ps.setInt(1, user.getId());
                    ps.setDouble(2, amount);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Số dư tài khoản chính không đủ để thực hiện đặt giá!");
                        }
                    }
                }

                if (!paymentDAO.updateBalance(conn, user.getId(), amount, "-")) {
                    throw new SQLException("Lỗi hệ thống khi thực hiện trừ ví chính!");
                }

                int assignedAdminId = ADMIN_IDS[auction.getAuctionId() % ADMIN_IDS.length];

                // Nạp tiền đặt giá mới vào ví tạm giữ cọc (escrow_balance) của Admin
                String sqlAddAdminEscrow = "UPDATE users SET escrow_balance = escrow_balance + ? WHERE user_id = ?";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sqlAddAdminEscrow)) {
                    ps.setDouble(1, amount);
                    ps.setInt(2, assignedAdminId);
                    if (ps.executeUpdate() <= 0) {
                        throw new SQLException("Không thể chuyển tiền cọc mới vào ví tạm của Admin!");
                    }
                }

                transactionDAO.createTransaction(conn, user.getId(), amount, "BID_PLACED_" + auction.getAuctionId(), "SUCCESS");

                // BƯỚC 3: CẬP NHẬT GIÁ VÀ WINNER MỚI VÀO BẢNG AUCTIONS
                if (!auctionDAO.updateBid(conn, auction.getAuctionId(), user.getId(), amount)) {
                    throw new SQLException("Cập nhật giá mới vào phiên thất bại!");
                }

                // Xử lý Anti-sniping
                LocalDateTime newEndTime = calculateAntiSniping(auction.getEndTime());
                if (newEndTime != null) {
                    auctionDAO.updateEndTime(conn, auction.getAuctionId(), newEndTime);
                    auction.setEndTime(newEndTime);
                }

                // BƯỚC 4: Lưu lịch sử đấu giá động vật phẩm
                String itemName = "Vật phẩm đấu giá #" + auction.getItemId();
                try {
                    ItemDAO itemDAO = new ItemDAO();
                    com.auction.common.model.Item realItem = itemDAO.getItemById(auction.getItemId());
                    if (realItem != null) {
                        itemName = realItem.getName();
                    }
                } catch (Exception e) {
                    System.out.println("[WARN] Không tìm thấy tên vật phẩm, dùng tên mặc định.");
                }

                biddingHistoryDAO.saveBidRecordWithConnection(conn, auction.getAuctionId(), itemName, user.getId(), user.getUsername(), amount);

                conn.commit(); // Xác nhận chốt thành công toàn vẹn dòng tiền!

                // Cập nhật bộ nhớ RAM phục vụ hiển thị Socket realtime
                auction.setCurrentPrice(amount);
                auction.setCurrentWinnerId(user.getId());
                auction.setTotalBids(auction.getTotalBids() + 1);

            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Giao dịch thất bại: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối Database!");
        }
    }

    public void rejectWin(User winner, int auctionId) {
        if (managerService == null) throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Chức năng yêu cầu ManagerService!");
        Auction auction = managerService.getAuctionOrThrow(auctionId);

        if (winner.getId() != auction.getCurrentWinnerId()) {
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn không phải người thắng phiên này!");
        }
        if (!"FINISHED".equals(auction.getAuctionStatus())) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên đấu giá chưa kết thúc!");
        }

        double bidAmount = auction.getCurrentPrice();
        double penaltyAmount = bidAmount * 0.07;
        double refundAmount = bidAmount - penaltyAmount;
        int adminId = 1;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                paymentDAO.updateBalance(conn, winner.getId(), refundAmount, "+");
                transactionDAO.createTransaction(conn, winner.getId(), refundAmount, "REFUND_REJECT_ITEM_" + auctionId, "SUCCESS");

                paymentDAO.updateBalance(conn, adminId, penaltyAmount, "+");
                transactionDAO.createTransaction(conn, adminId, penaltyAmount, "PENALTY_REVENUE_AUCTION_" + auctionId, "SUCCESS");

                auctionDAO.updateStatus(conn, auctionId, "REJECTED");

                conn.commit();
                auction.setAuctionStatus("REJECTED");
            } catch (SQLException e) {
                conn.rollback();
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi dòng tiền khi hủy kèo: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    private void validateBidRules(User user, Auction auction, double amount) {
        if (user.isAdmin()) throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không được đấu giá!");
        if (user.getId() == auction.getSellerId()) throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Không được tự đấu giá!");
        if (!"RUNNING".equalsIgnoreCase(auction.getAuctionStatus())) throw new AuctionException(ErrorCode.AUCTION_INVALID_STATE.name(), "Phiên không trong trạng thái RUNNING!");
        if (LocalDateTime.now().isAfter(auction.getEndTime())) throw new AuctionException(ErrorCode.AUCTION_ALREADY_ENDED.name(), "Phiên đã kết thúc!");
    }

    private LocalDateTime calculateAntiSniping(LocalDateTime currentEnd) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(currentEnd.minusSeconds(60)) && now.isBefore(currentEnd)) {
            return currentEnd.plusSeconds(30);
        }
        return null;
    }

    /** Trả về danh sách lịch sử đấu giá đồng bộ theo Model hệ thống mạng */
    public List<BiddingHistory> getBiddingHistory(int bidderId) {
        return biddingHistoryDAO.getHistoryByBidderId(bidderId);
    }
}