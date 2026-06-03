package com.auction.server.core;

import com.auction.common.network.AuctionRoomDTO;
import com.auction.common.model.Auction;
import com.auction.common.model.BiddingHistory;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.common.network.Message;
import com.auction.common.network.ResponseCode;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.PaymentDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.service.BiddingService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class AuctionRoom {
    private int auctionId;
    private final AtomicReference<Double> currentPrice;
    private volatile Integer currentWinnerId;
    private volatile String itemName = "";
    private final Set<ClientHandler> viewers = ConcurrentHashMap.newKeySet();

    private final ExecutorService roomQueueProcessor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AuctionRoom-" + auctionId + "-Queue");
        t.setDaemon(true);
        return t;
    });

    private final UserDAO userDAO = new UserDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();

    private Auction cachedAuction;
    private Item cachedItem;
    private final Map<Integer, String> cachedUsernames = new ConcurrentHashMap<>();

    public AuctionRoom(int auctionId, double startingPrice) {
        this.auctionId = auctionId;
        this.currentPrice = new AtomicReference<>(startingPrice);
    }

    public void setItemName(String itemName) {
        if (itemName != null) this.itemName = itemName;
    }

    public void joinRoom(ClientHandler handler) {
        viewers.add(handler);
        System.out.println("[ROOM-" + auctionId + "] Client tham gia. Tổng: " + viewers.size());
        sendRoomStateTo(handler);
    }

    public void leaveRoom(ClientHandler handler) {
        viewers.remove(handler);
        System.out.println("[ROOM-" + auctionId + "] Client rời phòng. Còn: " + viewers.size());
    }

    private void preloadRoomData() {
        if (cachedAuction == null) {
            cachedAuction = auctionDAO.getAuctionById(this.auctionId);
            if (cachedAuction != null) {
                cachedItem = itemDAO.getItemById(cachedAuction.getItemId());
            }
        }
    }

    private void sendRoomStateTo(ClientHandler target) {
        roomQueueProcessor.submit(() -> {
            try {
                preloadRoomData();
                List<BiddingHistory> historyList = auctionDAO.getBiddingHistoryByAuctionId(this.auctionId);

                if (historyList != null) {
                    for (BiddingHistory bid : historyList) {
                        int bId = bid.getBidderId();
                        cachedUsernames.computeIfAbsent(bId, id -> {
                            User u = userDAO.getUserById(id);
                            return (u != null) ? u.getUsername() : "User#" + id;
                        });
                    }
                }

                double balance = 0;
                Integer uId = target.getLoggedInUserId();
                if (uId != null) {
                    balance = paymentDAO.getBalance(uId);
                }

                AuctionRoomDTO dto = new AuctionRoomDTO(cachedAuction, cachedItem, historyList, cachedUsernames, balance);
                target.sendMessage(new Message(ResponseCode.ROOM_STATE_UPDATE, "LOAD", dto));
            } catch (Exception e) {
                System.err.println("[ROOM-" + auctionId + "] Lỗi đóng gói DTO: " + e.getMessage());
            }
        });
    }

    private void broadcastRoomState() {
        roomQueueProcessor.submit(() -> {
            try {
                preloadRoomData();
                List<BiddingHistory> historyList = auctionDAO.getBiddingHistoryByAuctionId(this.auctionId);

                if (historyList != null) {
                    for (BiddingHistory bid : historyList) {
                        int bId = bid.getBidderId();
                        cachedUsernames.computeIfAbsent(bId, id -> {
                            User u = userDAO.getUserById(id);
                            return (u != null) ? u.getUsername() : "User#" + id;
                        });
                    }
                }

                for (ClientHandler client : viewers) {
                    double balance = 0;
                    Integer uId = client.getLoggedInUserId();
                    if (uId != null) balance = paymentDAO.getBalance(uId);
                    AuctionRoomDTO dto = new AuctionRoomDTO(cachedAuction, cachedItem, historyList, cachedUsernames, balance);
                    client.sendMessage(new Message(ResponseCode.ROOM_STATE_UPDATE, "UPDATE", dto));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void processBid(ClientHandler handler, int userId, double bidAmount, BiddingService biddingService) {
        roomQueueProcessor.submit(() -> {
            try {
                if (bidAmount <= currentPrice.get()) {
                    handler.sendMessage(new Message(ResponseCode.BID_FAILED, "Giá không hợp lệ!", null));
                    return;
                }

                User user = userDAO.getUserById(userId);
                if (user == null) return;

                biddingService.placeBid(user, auctionId, bidAmount);

                currentPrice.set(bidAmount);
                currentWinnerId = userId;

                if (cachedAuction != null) {
                    cachedAuction.setCurrentPrice(bidAmount);
                }

                String bidderName = user.getUsername();
                cachedUsernames.putIfAbsent(userId, bidderName);

                broadcastRoomState();

                Object[] deltaPayload = {auctionId, bidAmount, bidderName, userId};
                Message deltaMsg = new Message(ResponseCode.NEW_BID_UPDATE, "Giá mới", deltaPayload);
                SessionManager.getInstance().broadcastToAdmins(deltaMsg);

                double newBalance = paymentDAO.getBalance(userId);
                handler.sendMessage(new Message(ResponseCode.BID_SUCCESS, "Bạn đang dẫn đầu!", newBalance));

                if (cachedAuction != null && cachedAuction.getEndTime() != null) {
                    Auction updatedAuction = biddingService.getManagerService().getAuction(auctionId);
                    if (updatedAuction != null && updatedAuction.getEndTime().isAfter(cachedAuction.getEndTime())) {
                        cachedAuction.setEndTime(updatedAuction.getEndTime());
                        Message extendMsg = new Message(ResponseCode.AUCTION_TIME_EXTENDED,
                            "⏱ Phiên được gia hạn thêm 30 giây!",
                            updatedAuction.getEndTime());
                        broadcastToAll(extendMsg);
                    }
                }

                System.out.println("[ROOM-" + auctionId + "] BID thành công: " + bidderName + " = " + bidAmount);
            } catch (Exception e) {
                handler.sendMessage(new Message(ResponseCode.BID_FAILED, e.getMessage(), null));
            }
        });
    }

    public void broadcastChat(String chatText) {
        broadcastToAll(new Message(ResponseCode.CHAT_BROADCAST, chatText, null));
    }

    public void closeRoom(Integer winnerId, double finalPrice) {
        roomQueueProcessor.submit(() -> {
            String winnerUsername = null;
            if (winnerId != null && winnerId > 0) {
                winnerUsername = cachedUsernames.getOrDefault(winnerId, "User#" + winnerId);
            }

            Object[] endPayload = {auctionId, winnerUsername, finalPrice};
            String endMessage = winnerUsername != null
                ? String.format("🏆 Phiên kết thúc! Người thắng: %s với giá %,.0f đ", winnerUsername, finalPrice)
                : "⛔ Phiên kết thúc mà không có người thắng.";

            broadcastToAll(new Message(ResponseCode.AUCTION_ENDED, endMessage, endPayload));

            if (winnerId != null && winnerId > 0) {
                try {
                    Object[] winnerPayload = {auctionId, finalPrice, itemName};
                    Message winnerMsg = new Message(
                        ResponseCode.WINNER_NOTIFICATION,
                        "🎉 Chúc mừng! Bạn đã thắng phiên đấu giá #" + auctionId,
                        winnerPayload
                    );
                    SessionManager.getInstance().sendToUserIfOnline(winnerId, winnerMsg);
                } catch (Exception e) {
                    System.err.println("[ROOM-" + auctionId + "] Lỗi WINNER_NOTIFICATION: " + e.getMessage());
                }
            }
        });
    }

    public void broadcastToAll(Message message) {
        for (ClientHandler viewer : viewers) {
            viewer.sendMessage(message);
        }
    }

    public void destroyRoom() {
        roomQueueProcessor.shutdownNow();
        viewers.clear();
    }

    public double getCurrentPrice() { return currentPrice.get(); }
    public int getAuctionId() { return auctionId; }
    public Integer getCurrentWinnerId() { return currentWinnerId; }
    public boolean containsViewer(ClientHandler sender) { return viewers.contains(sender); }
}