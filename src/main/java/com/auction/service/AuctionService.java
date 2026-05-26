package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.DBConnection;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.TransactionDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class AuctionService {
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final TransactionDAO transDAO = new TransactionDAO();

    public void handlePlaceBid(User currentUser, Auction auction, double bidAmount) {

        // 1. CÁC CHỐT CHẶN (Check nhanh trên RAM để tránh mở DB vô ích)
        validateBidGuards(currentUser, auction, bidAmount);

        // 2. THỰC THI GIAO DỊCH (SQL TRANSACTION)
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // BẮT ĐẦU CHUỖI AN TOÀN

            try {
                // Bước A: Cập nhật giá và người thắng trong bảng Auction
                // Sử dụng hàm updateBid(conn, ...)
                if (!auctionDAO.updateBid(conn, auction.getAuctionId(), currentUser.getId(), bidAmount)) {
                    throw new SQLException("Đặt giá thất bại (có người vừa trả giá cao hơn bạn)!");
                }

                // Bước B: Hoàn tiền cho người thắng cũ (nếu có)
                if (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId() != 0) {
                    if (!paymentDAO.updateBalance(conn, auction.getCurrentWinnerId(), auction.getCurrentPrice(), "+")) {
                        throw new SQLException("Lỗi hoàn tiền cho người thắng trước đó!");
                    }
                }

                // Bước C: Trừ tiền người mới (Tạm giữ)
                if (!paymentDAO.updateBalance(conn, currentUser.getId(), bidAmount, "-")) {
                    throw new SQLException("Số dư không đủ để thực hiện đặt giá!");
                }

                // Bước D: Ghi Log giao dịch để Admin đối soát
                transDAO.createTransaction(conn, currentUser.getId(), bidAmount, "BID_AUCTION_" + auction.getAuctionId(), "SUCCESS");

                // Bước E: Logic Anti-sniping (Gia hạn thời gian)
                handleAntiSniping(conn, auction);

                // Lưu mọi thứ
                conn.commit();

                //  cập nhật Ram khi đã xong trong DB
                syncRAM(currentUser, auction, bidAmount);

                System.out.println(">>> Đặt giá thành công: " + currentUser.getUsername() + " bid " + bidAmount);

            } catch (SQLException e) {
                conn.rollback(); // HỦY BỎ TẤT CẢ nếu có bất kỳ bước nào lỗi
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), e.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    // Tách riêng logic kiểm tra cho sạch code
    private void validateBidGuards(User currentUser, Auction auction, double bidAmount) {
        if (currentUser.isAdmin())
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Admin không được tham gia!");

        if (currentUser.getId() == auction.getSellerId())
            throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Không thể tự đấu giá hàng của mình!");

        if (LocalDateTime.now().isAfter(auction.getEndTime()))
            throw new AuctionException(ErrorCode.AUCTION_ALREADY_ENDED.name(), "Phiên đấu giá đã kết thúc!");

        if (bidAmount <= auction.getCurrentPrice())
            throw new AuctionException(ErrorCode.BID_TOO_LOW.name(), "Giá đặt phải cao hơn giá hiện tại!");

        if (currentUser.getBalance() < bidAmount)
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Số dư không đủ!");
    }

    // Gia hạn phiên đấu giá (Dùng chung Connection để tránh Deadlock)
    private void handleAntiSniping(Connection conn, Auction auction) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(auction.getEndTime().minusMinutes(1))) {
            LocalDateTime newEndTime = auction.getEndTime().plusSeconds(30);
            if (auctionDAO.updateEndTime(conn, auction.getAuctionId(), newEndTime)) {
                // Gán tạm vào object để lát nữa commit xong thì UI có dữ liệu mới
                auction.setEndTime(newEndTime);
                System.out.println("=== ANTI-SNIPING: Gia hạn thêm 30s ===");
            }
        }
    }

    // Cập nhật các biến trong RAM để hiển thị UI
    private void syncRAM(User currentUser, Auction auction, double bidAmount) {
        currentUser.setBalance(currentUser.getBalance() - bidAmount);
        auction.setCurrentPrice(bidAmount);
        auction.setCurrentWinnerId(currentUser.getId());
        auction.setTotalBids(auction.getTotalBids() + 1);
    }
}