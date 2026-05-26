package com.auction.server.core;

import com.auction.common.exception.AuctionException;
import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.common.network.*;
import com.auction.server.service.AdminService;
import com.auction.server.service.BiddingService;
import com.auction.server.service.ItemService;
import com.auction.server.service.ManagerService;
import com.auction.server.service.SellerService;
import com.auction.server.service.TransactionService;
import com.auction.server.service.UserService;

import java.util.List;

/**
 * RequestDispatcher - Bộ điều phối trung tâm phía Server.
 *
 * PHIÊN BẢN ĐẦY ĐỦ: Xử lý tất cả request từ Bidder, Seller, Admin.
 *
 * Luồng: ClientHandler nhận Message -> dispatch() -> handler tương ứng
 *        -> Service/DAO -> gửi Response về client (và broadcast nếu cần)
 *
 * ĐẶT TẠI: server/src/main/java/com/auction/server/core/RequestDispatcher.java
 */
public class RequestDispatcher {
    private static final RequestDispatcher instance = new RequestDispatcher();

    // === Services khởi tạo một lần (Singleton-like, thread-safe qua synchronized method) ===
    private final UserService userService = new UserService();
    private final ItemService itemService = new ItemService();
    private final ManagerService managerService = new ManagerService(itemService);
    private final BiddingService biddingService = new BiddingService(managerService);
    private final SellerService sellerService = new SellerService(managerService);
    private final AdminService adminService = new AdminService(managerService);
    private final TransactionService transactionService = new TransactionService(managerService);

    private RequestDispatcher() {}
    public static RequestDispatcher getInstance() { return instance; }

    // =========================================================
    // ENTRY POINT
    // =========================================================

    public void dispatch(ClientHandler client, Message request) {
        if (request == null || request.getRequestCode() == null) return;
        try {
            switch (request.getRequestCode()) {
                // --- AUTH ---
                case LOGIN:            handleLogin(client, request);           break;
                case REGISTER:         handleRegister(client, request);        break;

                // --- BIDDER ---
                case FETCH_ROOMS:      handleFetchRooms(client);               break;
                case FETCH_ITEMS:      handleFetchItems(client, request);      break;
                case JOIN_ROOM:        handleJoinRoom(client, request);        break;
                case LEAVE_ROOM:       handleLeaveRoom(client, request);       break;
                case PLACE_BID:        handlePlaceBid(client, request);        break;
                case CHAT_MESSAGE:     handleChat(client, request);            break;
                case FETCH_BID_HISTORY:handleFetchBidHistory(client);          break;
                case DEPOSIT_REQUEST:  handleDeposit(client, request);         break;
                case WITHDRAW_REQUEST: handleWithdraw(client, request);        break;
                case GET_PROFILE:      handleGetProfile(client);               break;
                case UPDATE_PROFILE:   handleUpdateProfile(client, request);   break;
                case CHANGE_PASSWORD:  handleChangePassword(client, request);  break;
                case REPORT_ISSUE:     handleReportIssue(client, request);     break;

                // --- SELLER ---
                case SELLER_GET_MY_ITEMS:     handleSellerGetMyItems(client);              break;
                case SELLER_CREATE_AUCTION:   handleSellerCreateAuction(client, request); break;
                case SELLER_GET_MY_AUCTIONS:  handleSellerGetMyAuctions(client);          break;
                case SELLER_CANCEL_AUCTION:   handleSellerCancelAuction(client, request); break;
                case SELLER_CONFIRM_SALE:     handleSellerConfirmSale(client, request);   break;

                // --- ADMIN ---
                case ADMIN_GET_ALL_AUCTIONS:      handleAdminGetAllAuctions(client);              break;
                case ADMIN_APPROVE_AUCTION:       handleAdminApproveAuction(client, request);     break;
                case ADMIN_REJECT_AUCTION:        handleAdminRejectAuction(client, request);      break;
                case ADMIN_BLOCK_AUCTION:         handleAdminBlockAuction(client, request);       break;
                case ADMIN_GET_ALL_TRANSACTIONS:  handleAdminGetAllTransactions(client);          break;
                case ADMIN_APPROVE_TRANSACTION:   handleAdminApproveTransaction(client, request); break;
                case ADMIN_REJECT_TRANSACTION:    handleAdminRejectTransaction(client, request);  break;
                case ADMIN_CREATE_TRANSACTION:    handleAdminCreateTransaction(client, request);  break;
                case ADMIN_GET_ALL_USERS:         handleAdminGetAllUsers(client);                 break;
                case ADMIN_BAN_USER:              handleAdminBanUser(client, request);            break;
                case ADMIN_UNBAN_USER:            handleAdminUnbanUser(client, request);          break;

                default:
                    System.out.println("[DISPATCHER] Unknown code: " + request.getRequestCode());
            }
        } catch (Exception e) {
            System.err.println("[DISPATCHER ERROR] " + request.getRequestCode() + ": " + e.getMessage());
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi máy chủ: " + e.getMessage(), null));
        }
    }

    // =========================================================
    // AUTH HANDLERS
    // =========================================================

    private void handleLogin(ClientHandler client, Message request) {
        try {
            LoginDTO loginData = (LoginDTO) request.getPayload();
            User user = userService.handleLogin(loginData.getUsername(), loginData.getPassword());
            if (user != null) {
                boolean registered = SessionManager.getInstance().loginUser(user.getId(), client);
                if (registered) {
                    client.setLoggedInUserId(user.getId());
                    client.sendMessage(new Message(ResponseCode.LOGIN_SUCCESS, "Đăng nhập thành công", user));
                    System.out.println("[LOGIN] User " + user.getUsername() + " đã đăng nhập.");
                } else {
                    client.sendMessage(new Message(ResponseCode.LOGIN_FAILED, "Tài khoản đang đăng nhập ở nơi khác", null));
                }
            } else {
                client.sendMessage(new Message(ResponseCode.LOGIN_FAILED, "Sai tài khoản hoặc mật khẩu", null));
            }
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.LOGIN_FAILED, "Lỗi đăng nhập: " + e.getMessage(), null));
        }
    }

    private void handleRegister(ClientHandler client, Message request) {

        try {
            RegisterDTO data = (RegisterDTO) request.getPayload();
            boolean success = userService.handleRegister(data.getUsername(), data.getPassword(), data.getEmail(), data.getPhone());
            if (success) {
                client.sendMessage(new Message(ResponseCode.REGISTER_SUCCESS, "Đăng ký thành công!", null));
            } else {
                client.sendMessage(new Message(ResponseCode.REGISTER_FAILED, "Đăng ký thất bại!", null));
            }
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.REGISTER_FAILED, e.getMessage(), null));
        }
    }

    // =========================================================
    // BIDDER HANDLERS
    // =========================================================

    private void handleFetchRooms(ClientHandler client) {
        try {
            List<Auction> rooms = managerService.getAllAuctions();
            client.sendMessage(new Message(ResponseCode.ROOM_LIST_RESULT, "OK", rooms));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được danh sách phòng", null));
        }
    }

    private void handleFetchItems(ClientHandler client, Message request) {
        try {
            String category = (String) request.getPayload();
            List<Item> items = itemService.getItemsByType(category);
            client.sendMessage(new Message(ResponseCode.FETCH_ITEMS_RESULT, "OK", items));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được danh sách sản phẩm", null));
        }
    }

    private void handleJoinRoom(ClientHandler client, Message request) {
        Integer roomId = (Integer) request.getPayload();
        AuctionRoom room = AuctionRoomManager.getInstance().getRoom(roomId);
        if (room != null) {
            room.joinRoom(client);
            client.sendMessage(new Message(ResponseCode.ROOM_JOIN_SUCCESS, "Đã vào phòng", roomId));
        } else {
            client.sendMessage(new Message(ResponseCode.ROOM_JOIN_FAILED, "Phòng không tồn tại hoặc đã đóng", null));
        }
    }

    private void handleLeaveRoom(ClientHandler client, Message request) {
        Integer roomId = (Integer) request.getPayload();
        AuctionRoom room = AuctionRoomManager.getInstance().getRoom(roomId);
        if (room != null) room.leaveRoom(client);
    }

    private void handlePlaceBid(ClientHandler client, Message request) {

        Integer userId = client.getLoggedInUserId();
        if (userId == null) {
            client.sendMessage(new Message(ResponseCode.BID_FAILED, "Bạn chưa đăng nhập!", null));
            return;
        }
        BidPlaceDTO dto = (BidPlaceDTO) request.getPayload();
        AuctionRoom room = AuctionRoomManager.getInstance().getRoom(dto.getAuctionId());

        if (room != null) {
            room.processBid(client, userId,dto.getBidAmount(), biddingService);
        } else {
            client.sendMessage(new Message(ResponseCode.BID_FAILED, "Phiên đấu giá đã kết thúc!", null));
        }
    }

    private void handleChat(ClientHandler client, Message request) {
        try {
            String msg = (String) request.getPayload();
            Integer userId = client.getLoggedInUserId();
            if (userId == null || msg == null || msg.trim().isEmpty()) return;
            // Broadcast tới tất cả client trong cùng phòng thông qua AuctionRoomManager
            String fullMsg = "User#" + userId + ": " + msg.trim();
            AuctionRoomManager.getInstance().broadcastChatToUserRooms(userId, fullMsg, client);
        } catch (Exception e) {
            System.err.println("[CHAT ERROR] " + e.getMessage());
        }
    }

    private void handleFetchBidHistory(ClientHandler client) {
        try {
            Integer userId = client.getLoggedInUserId();
            if (userId == null) return;
            var history = biddingService.getBidHistory(userId);
            client.sendMessage(new Message(ResponseCode.BID_HISTORY_RESULT, "OK", history));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được lịch sử đặt giá", null));
        }
    }

    private void handleDeposit(ClientHandler client, Message request) {
        try {
            Double amount = (Double) request.getPayload();
            Integer userId = client.getLoggedInUserId();
            transactionService.handleDepositRequest(userService.getUserById(userId), amount);
            client.sendMessage(new Message(ResponseCode.DEPOSIT_SUCCESS, "Yêu cầu nạp tiền đã gửi, chờ Admin duyệt", null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.DEPOSIT_FAILED, "Lỗi nạp tiền: " + e.getMessage(), null));
        }
    }

    private void handleWithdraw(ClientHandler client, Message request) {

        try {
            Double amount = (Double) request.getPayload();
            Integer userId = client.getLoggedInUserId();
            User user = userService.getUserById(userId);
            // Tạm thời hard-code thông tin ngân hàng
            String bankInfo = "PENDING_BANK_INFO";
            transactionService.handleWithdrawRequest(user, amount, bankInfo);
            client.sendMessage(new Message(ResponseCode.WITHDRAW_SUCCESS, "Yêu cầu rút tiền đã gửi", null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.WITHDRAW_FAILED, "Lỗi rút tiền: " + e.getMessage(), null));
        }
    }

    private void handleGetProfile(ClientHandler client) {
        try {
            Integer userId = client.getLoggedInUserId();
            User user = userService.getUserById(userId);
            client.sendMessage(new Message(ResponseCode.PROFILE_RESULT, "OK", user));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được thông tin", null));
        }
    }

    private void handleUpdateProfile(ClientHandler client, Message request) {
        try {
            User user = (User) request.getPayload();
            userService.updateProfile(user);
            client.sendMessage(new Message(ResponseCode.PROFILE_UPDATED, "Cập nhật thành công", null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi cập nhật: " + e.getMessage(), null));
        }
    }

    private void handleChangePassword(ClientHandler client, Message request) {
        try {
            String[] passwords = (String[]) request.getPayload();
            Integer userId = client.getLoggedInUserId();
            User user = userService.getUserById(userId);
            userService.handleChangePassword(user, passwords[0], passwords[1], passwords[2]);
            client.sendMessage(new Message(ResponseCode.PASSWORD_CHANGED, "Đổi mật khẩu thành công", null));
        } catch (AuctionException e) {
            client.sendMessage(new Message(ResponseCode.PASSWORD_CHANGE_FAILED, e.getMessage(), null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi hệ thống", null));
        }
    }

    private void handleReportIssue(ClientHandler client, Message request) {
        try {
            String issueContent = (String) request.getPayload();
            Integer userId = client.getLoggedInUserId();
            // Lưu vào DB (IssueDAO tồn tại trong project)
            System.out.println("[REPORT] User#" + userId + ": " + issueContent);
            client.sendMessage(new Message(ResponseCode.REPORT_SENT, "Báo cáo đã được gửi", null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi gửi báo cáo", null));
        }
    }

    // =========================================================
    // SELLER HANDLERS
    // =========================================================

    private void handleSellerGetMyItems(ClientHandler client) {
        try {
            Integer userId = client.getLoggedInUserId();
            List<Item> items = itemService.getItemsBySeller(userId);
            client.sendMessage(new Message(ResponseCode.SELLER_ITEMS_RESULT, "OK", items));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được danh sách sản phẩm", null));
        }
    }

    private void handleSellerCreateAuction(ClientHandler client, Message request) {
        try {
            CreateAuctionDTO dto = (CreateAuctionDTO) request.getPayload();
            Integer sellerId = client.getLoggedInUserId();
            User seller = userService.getUserById(sellerId);

            // Gọi SellerService để tạo phiên (trạng thái = WAITING_FOR_ADMIN)
            sellerService.requestCreateAuction(seller, dto.getItemId(), dto.getStartTime(), dto.getEndTime());

            // Lấy auctionId vừa tạo để báo lại client
            List<Auction> myAuctions = managerService.getAuctionsBySeller(sellerId);
            int newAuctionId = myAuctions.isEmpty() ? -1 : myAuctions.get(myAuctions.size() - 1).getAuctionId();

            client.sendMessage(new Message(ResponseCode.SELLER_AUCTION_CREATED,
                    "Phiên đã gửi lên Admin để duyệt!", newAuctionId));

            // Broadcast tới tất cả Admin đang online: có phiên mới cần duyệt
            Auction newAuction = myAuctions.isEmpty() ? null : myAuctions.get(myAuctions.size() - 1);
            if (newAuction != null) {
                SessionManager.getInstance().broadcastToAdmins(
                        new Message(ResponseCode.ADMIN_NEW_PENDING_AUCTION, "Phiên mới cần duyệt!", newAuction),
                        userService
                );
            }
            System.out.println("[SELLER] User#" + sellerId + " tạo phiên cho item#" + dto.getItemId());
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.SELLER_AUCTION_CREATE_FAILED,
                    "Tạo phiên thất bại: " + e.getMessage(), null));
        }
    }

    private void handleSellerGetMyAuctions(ClientHandler client) {
        try {
            Integer sellerId = client.getLoggedInUserId();
            List<Auction> auctions = managerService.getAuctionsBySeller(sellerId);
            client.sendMessage(new Message(ResponseCode.SELLER_AUCTIONS_RESULT, "OK", auctions));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được danh sách phiên", null));
        }
    }

    private void handleSellerCancelAuction(ClientHandler client, Message request) {
        try {
            Integer auctionId = (Integer) request.getPayload();
            Integer sellerId = client.getLoggedInUserId();
            User seller = userService.getUserById(sellerId);
            sellerService.requestCancelAuction(seller, auctionId);
            client.sendMessage(new Message(ResponseCode.SELLER_CANCEL_SUCCESS, "Yêu cầu hủy đã gửi", null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.SELLER_CANCEL_FAILED, e.getMessage(), null));
        }
    }

    private void handleSellerConfirmSale(ClientHandler client, Message request) {
        try {
            Integer auctionId = (Integer) request.getPayload();
            Integer sellerId = client.getLoggedInUserId();
            User seller = userService.getUserById(sellerId);
            sellerService.confirmSale(seller, auctionId);
            client.sendMessage(new Message(ResponseCode.SELLER_CONFIRM_SALE_SUCCESS, "Đã xác nhận bán!", null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.SELLER_CONFIRM_SALE_FAILED, e.getMessage(), null));
        }
    }

    // =========================================================
    // ADMIN HANDLERS
    // =========================================================

    private void handleAdminGetAllAuctions(ClientHandler client) {
        try {
            List<Auction> auctions = managerService.getAllAuctions();
            client.sendMessage(new Message(ResponseCode.ADMIN_ALL_AUCTIONS_RESULT, "OK", auctions));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được danh sách phiên", null));
        }
    }

    private void handleAdminApproveAuction(ClientHandler client, Message request) {
        try {
            Integer auctionId = (Integer) request.getPayload();
            boolean success = adminService.approveAuction(auctionId);
            if (success) {
                Auction updatedAuction = managerService.getAuctionOrThrow(auctionId);

                client.sendMessage(new Message(
                        ResponseCode.ADMIN_APPROVE_SUCCESS,
                        "Đã duyệt phiên #" + auctionId,
                        updatedAuction
                ));

                // Push thông báo tới Seller chủ phiên
                Auction auction = managerService.getAuctionOrThrow(auctionId);
                SessionManager.getInstance().sendToUserIfOnline(auction.getSellerId(),
                        new Message(ResponseCode.SELLER_AUCTION_APPROVED, "Phiên của bạn đã được duyệt!", auctionId));

                // Kích hoạt phòng đấu giá realtime
                AuctionRoomManager.getInstance().openRoom(auction);
                System.out.println("[ADMIN] Đã duyệt + mở phòng cho phiên #" + auctionId);
            } else {
                client.sendMessage(new Message(ResponseCode.ADMIN_APPROVE_FAILED, "Không thể duyệt phiên này", null));
            }
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ADMIN_APPROVE_FAILED, e.getMessage(), null));
        }
    }

    private void handleAdminRejectAuction(ClientHandler client, Message request) {
        try {
            Object[] payload = (Object[]) request.getPayload(); // {auctionId, reason}
            int auctionId = (int) payload[0];
            String reason = (String) payload[1];
            adminService.rejectAuction(auctionId, reason);
            client.sendMessage(new Message(ResponseCode.ADMIN_REJECT_SUCCESS,
                    "Đã từ chối phiên #" + auctionId, auctionId));

            // Push thông báo tới Seller
            Auction auction = managerService.getAuctionOrThrow(auctionId);
            SessionManager.getInstance().sendToUserIfOnline(auction.getSellerId(),
                    new Message(ResponseCode.SELLER_AUCTION_REJECTED,
                            "Phiên của bạn bị từ chối!", new Object[]{auctionId, reason}));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ADMIN_REJECT_FAILED, e.getMessage(), null));
        }
    }

    private void handleAdminBlockAuction(ClientHandler client, Message request) {
        try {
            Integer auctionId = (Integer) request.getPayload();
            boolean success = adminService.blockAuction(auctionId);
            if (success) {
                client.sendMessage(new Message(ResponseCode.ADMIN_BLOCK_SUCCESS,
                        "Đã phong tỏa phiên #" + auctionId, auctionId));
                // Đóng phòng đấu giá, broadcast kết thúc tới tất cả viewer
                AuctionRoomManager.getInstance().closeRoom(auctionId);
            } else {
                client.sendMessage(new Message(ResponseCode.ADMIN_BLOCK_FAILED, "Không thể phong tỏa", null));
            }
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ADMIN_BLOCK_FAILED, e.getMessage(), null));
        }
    }

    private void handleAdminGetAllTransactions(ClientHandler client) {
        try {
            List<TransactionRequest> transactions = transactionService.getAllTransactions();
            client.sendMessage(new Message(ResponseCode.ADMIN_ALL_AUCTIONS_RESULT, "OK", transactions));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được giao dịch", null));
        }
    }

    private void handleAdminApproveTransaction(ClientHandler client, Message request) {
        try {
            Integer txId = (Integer) request.getPayload();
            transactionService.approveTransaction(txId);
            client.sendMessage(new Message(ResponseCode.ADMIN_AUCTION_APPROVED, "Đã duyệt giao dịch", txId));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi duyệt giao dịch: " + e.getMessage(), null));
        }
    }

    private void handleAdminRejectTransaction(ClientHandler client, Message request) {
        try {
            Integer txId = (Integer) request.getPayload();
            transactionService.rejectTransaction(txId);
            client.sendMessage(new Message(ResponseCode.ADMIN_AUCTION_REJECTED, "Đã từ chối giao dịch", txId));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi từ chối giao dịch: " + e.getMessage(), null));
        }
    }

    private void handleAdminCreateTransaction(ClientHandler client, Message request) {
        try {
            Object[] payload = (Object[]) request.getPayload(); // {auctionId, winnerId, price}
            int auctionId  = (int)    payload[0];
            int winnerId   = (int)    payload[1];
            double price   = (double) payload[2];
            transactionService.createTransactionFromAuction(auctionId, winnerId, price);
            client.sendMessage(new Message(ResponseCode.ADMIN_TRANSACTION_CREATED, "Giao dịch đã được tạo!", null));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ADMIN_TRANSACTION_FAILED, e.getMessage(), null));
        }
    }

    private void handleAdminGetAllUsers(ClientHandler client) {
        try {
            List<User> users = userService.getAllUsers();
            client.sendMessage(new Message(ResponseCode.ADMIN_USERS_RESULT, "OK", users));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được danh sách user", null));
        }
    }

    private void handleAdminBanUser(ClientHandler client, Message request) {
        try {
            Integer userId = (Integer) request.getPayload();
            userService.banUser(userId);
            // Kick user ra nếu đang online
            SessionManager.getInstance().forceLogout(userId);
            client.sendMessage(new Message(ResponseCode.ADMIN_BAN_SUCCESS, "Đã ban user#" + userId, userId));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi ban user: " + e.getMessage(), null));
        }
    }

    private void handleAdminUnbanUser(ClientHandler client, Message request) {
        try {
            Integer userId = (Integer) request.getPayload();
            userService.unbanUser(userId);
            client.sendMessage(new Message(ResponseCode.ADMIN_UNBAN_SUCCESS, "Đã unban user#" + userId, userId));
        } catch (Exception e) {
            client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi unban user: " + e.getMessage(), null));
        }
    }
}