package com.auction.server.core;

import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
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

public class RequestDispatcher {
  private static final RequestDispatcher instance = new RequestDispatcher();

  private final UserService userService = new UserService();
  private final ItemService itemService = new ItemService();
  private final ManagerService managerService = new ManagerService(itemService);
  private final BiddingService biddingService = new BiddingService(managerService);
  private final SellerService sellerService = new SellerService(managerService);
  private final AdminService adminService = new AdminService(managerService);
  private final TransactionService transactionService = new TransactionService(managerService);

  private RequestDispatcher() {}
  public static RequestDispatcher getInstance() { return instance; }

  public void dispatch(ClientHandler client, Message request) {
    if (request == null || request.getRequestCode() == null) return;
    try {
      switch (request.getRequestCode()) {
        case LOGIN:            handleLogin(client, request);           break;
        case REGISTER:         handleRegister(client, request);        break;
        case FORGOT_PASSWORD:  handleForgotPassword(client, request);  break;
        case SWITCH_ROLE:      handleSwitchRole(client, request);      break;
        case LOGOUT:           handleLogout(client);                   break;

        case FETCH_ROOMS:      handleFetchRooms(client);               break;
        case FETCH_ITEMS:      handleFetchItems(client, request);      break;
        case JOIN_ROOM:        handleJoinRoom(client, request);        break;
        case LEAVE_ROOM:       handleLeaveRoom(client, request);       break;
        case PLACE_BID:        handlePlaceBid(client, request);        break;
        case CHAT_MESSAGE:     handleChat(client, request);            break;
        case FETCH_BID_HISTORY:handleFetchBidHistory(client, request);          break;
        case DEPOSIT_REQUEST:  handleDeposit(client, request);         break;
        case WITHDRAW_REQUEST: handleWithdraw(client, request);        break;
        case GET_PROFILE:      handleGetProfile(client);               break;
        case UPDATE_PROFILE:   handleUpdateProfile(client, request);   break;
        case CHANGE_PASSWORD:  handleChangePassword(client, request);  break;
        case REPORT_ISSUE:     handleReportIssue(client, request);     break;
        case GET_USER_TRANSACTIONS: handleGetUserTransactions(client, request); break;
        case FETCH_AUCTION_DETAIL:  handleFetchAuctionDetail(client, request);  break;
        case BIDDER_PAY_AUCTION:    handleBidderPayAuction(client, request);    break;
        case BIDDER_CANCEL_AUCTION: handleBidderCancelAuction(client, request); break;

        case SELLER_GET_MY_ITEMS:     handleSellerGetMyItems(client, request);    break;
        case SELLER_CREATE_AUCTION:   handleSellerCreateAuction(client, request); break;
        case SELLER_GET_MY_AUCTIONS:  handleSellerGetMyAuctions(client);          break;
        case SELLER_CANCEL_AUCTION:   handleSellerCancelAuction(client, request); break;
        case SELLER_CONFIRM_SALE:     handleSellerConfirmSale(client, request);   break;
        case SELLER_ADD_ITEM:         handleSellerAddItem(client, request);       break;
        case SELLER_EDIT_AUCTION:     handleSellerEditAuction(client, request);   break;

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
        case ADMIN_DELETE_BLOCKED_AUCTION:handleAdminDeleteBlockedAuction(client, request); break;
        case ADMIN_GET_ALL_ISSUES:        handleAdminGetAllIssues(client);                break;

        default:
          System.out.println("[DISPATCHER] Unknown code: " + request.getRequestCode());
      }
    } catch (Exception e) {
      System.err.println("[DISPATCHER ERROR] " + request.getRequestCode() + ": " + e.getMessage());
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi máy chủ: " + e.getMessage(), null));
    }
  }

  private void handleLogout(ClientHandler client) {
    Integer userId = client.getLoggedInUserId();
    if (userId != null) {
      SessionManager.getInstance().removeSession(userId);
      client.setLoggedInUserId(null);
      System.out.println("[LOGOUT] User#" + userId + " đã đăng xuất và giải phóng bộ nhớ session thành công.");
    }
  }

  private void handleFetchAuctionDetail(ClientHandler client, Message request) {
    try {
      Integer auctionId = (Integer) request.getPayload();
      Auction auction = managerService.getAuctionOrThrow(auctionId);
      Item item = itemService.getItemById(auction.getItemId());
      AuctionItemDTO dto = new AuctionItemDTO(item, auction);
      client.sendMessage(new Message(ResponseCode.AUCTION_DETAIL_RESULT, "OK", dto));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi lấy chi tiết phiên: " + e.getMessage(), null));
    }
  }

  private void handleBidderPayAuction(ClientHandler client, Message request) {
    try {
      Integer auctionId = (Integer) request.getPayload();
      Integer userId = client.getLoggedInUserId();
      if (userId == null) {
        throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn chưa đăng nhập!");
      }

      transactionService.processAuctionWinnerPayment(auctionId, userId);
      client.sendMessage(new Message(ResponseCode.BIDDER_PAY_SUCCESS, "Thanh toán thành công", null));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.BIDDER_PAY_FAILED, e.getMessage(), null));
    }
  }

  private void handleBidderCancelAuction(ClientHandler client, Message request) {
    try {
      Integer auctionId = (Integer) request.getPayload();
      Integer userId = client.getLoggedInUserId();
      if (userId == null) {
        throw new AuctionException(ErrorCode.UNAUTHORIZED.name(), "Bạn chưa đăng nhập!");
      }

      transactionService.processAuctionWinnerPenalty(auctionId, userId);
      client.sendMessage(new Message(ResponseCode.BIDDER_CANCEL_SUCCESS, "Hủy và phạt cọc thành công", null));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.BIDDER_CANCEL_FAILED, e.getMessage(), null));
    }
  }

  private void handleGetUserTransactions(ClientHandler client, Message request) {
    try {
      Integer userId = client.getLoggedInUserId();
      if (userId == null) userId = (Integer) request.getPayload();
      if (userId == null) return;
      List<TransactionRequest> transactions = transactionService.getTransactionsByUserId(userId);
      client.sendMessage(new Message(ResponseCode.TRANSACTIONS_RESULT, "OK", transactions));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được lịch sử giao dịch", null));
    }
  }

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

  private void handleForgotPassword(ClientHandler client, Message request) {
    try {
      String[] payload = (String[]) request.getPayload();
      String username = payload[0];
      String phone = payload[1];
      String newPass = payload[2];

      userService.handleForgotPassword(username, phone, newPass);
      client.sendMessage(new Message(ResponseCode.FORGOT_PASSWORD_SUCCESS, "Khôi phục mật khẩu thành công!", null));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.FORGOT_PASSWORD_FAILED, e.getMessage(), null));
    }
  }

  private void handleSwitchRole(ClientHandler client, Message request) {
    try {
      Integer userId = client.getLoggedInUserId();
      User user = userService.getUserById(userId);
      String targetRole = (String) request.getPayload();

      userService.handleSwitchRole(user, targetRole);
      client.sendMessage(new Message(ResponseCode.SWITCH_ROLE_SUCCESS, "OK", user.getRole()));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.SWITCH_ROLE_FAILED, e.getMessage(), null));
    }
  }

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

    // BẢN VÁ: Tự động khôi phục phòng trên RAM nếu Server vừa khởi động lại
    if (room == null) {
      try {
        Auction auction = managerService.getAuctionOrThrow(roomId);
        if ("OPEN".equals(auction.getAuctionStatus()) || "RUNNING".equals(auction.getAuctionStatus())) {
          AuctionRoomManager.getInstance().openRoom(auction);
          room = AuctionRoomManager.getInstance().getRoom(roomId);
          System.out.println("[DISPATCHER] Đã khôi phục phòng #" + roomId + " từ Database.");
        }
      } catch (Exception e) {
        System.err.println("[DISPATCHER] Không thể khôi phục phòng: " + e.getMessage());
      }
    }

    if (room != null) {
      room.joinRoom(client);
      client.sendMessage(new Message(ResponseCode.ROOM_JOIN_SUCCESS, "Đã vào phòng", roomId));
    } else {
      client.sendMessage(new Message(ResponseCode.ROOM_JOIN_FAILED, "Phòng đấu giá không tồn tại hoặc đã kết thúc!", null));
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
      room.processBid(client, userId, dto.getBidAmount(), biddingService);
    } else {
      client.sendMessage(new Message(ResponseCode.BID_FAILED, "Phiên đấu giá đã kết thúc!", null));
    }
  }

  private void handleChat(ClientHandler client, Message request) {
    try {
      String msg = (String) request.getPayload();
      Integer userId = client.getLoggedInUserId();
      if (userId == null || msg == null || msg.trim().isEmpty()) return;
      String fullMsg = "User#" + userId + ": " + msg.trim();
      AuctionRoomManager.getInstance().broadcastChatToUserRooms(userId, fullMsg, client);
    } catch (Exception e) {
      System.err.println("[CHAT ERROR] " + e.getMessage());
    }
  }

  private void handleFetchBidHistory(ClientHandler client, Message request) {
    try {
      Integer auctionId = (Integer) request.getPayload();
      if (auctionId == null) return;

      com.auction.server.dao.BidDAO bidDAO = new com.auction.server.dao.BidDAO();
      List<com.auction.server.dao.BidDAO.BidRow> history = bidDAO.getBidHistory(auctionId);

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

      // Gửi báo cáo thành công cho cá nhân Bidder
      client.sendMessage(new Message(ResponseCode.DEPOSIT_SUCCESS, "Yêu cầu nạp tiền đã gửi, chờ Admin duyệt", null));

      // [THÊM MỚI]: Phát loa thông báo cho toàn bộ Admin đang online
      SessionManager.getInstance().broadcastToAdmins(
          new Message(ResponseCode.ADMIN_TRANSACTION_CREATED, "Có lệnh nạp tiền mới", null)
      );
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.DEPOSIT_FAILED, "Lỗi nạp tiền: " + e.getMessage(), null));
    }
  }

  private void handleWithdraw(ClientHandler client, Message request) {
    try {
      Double amount = (Double) request.getPayload();
      Integer userId = client.getLoggedInUserId();
      User user = userService.getUserById(userId);
      String bankInfo = "PENDING_BANK_INFO";
      transactionService.handleWithdrawRequest(user, amount, bankInfo);

      // Gửi báo cáo thành công cho cá nhân Bidder
      client.sendMessage(new Message(ResponseCode.WITHDRAW_SUCCESS, "Yêu cầu rút tiền đã gửi", null));

      // [THÊM MỚI]: Phát loa thông báo cho toàn bộ Admin đang online
      SessionManager.getInstance().broadcastToAdmins(
          new Message(ResponseCode.ADMIN_TRANSACTION_CREATED, "Có lệnh rút tiền mới", null)
      );
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
      Object[] payload = (Object[]) request.getPayload();
      int auctionId = (int) payload[0];
      String issueType = (String) payload[1];
      String description = (String) payload[2];
      Integer userId = client.getLoggedInUserId();

      com.auction.server.dao.IssueDAO issueDAO = new com.auction.server.dao.IssueDAO();
      boolean isSuccess = issueDAO.insertIssue(userId, auctionId, issueType, description);

      if (isSuccess) {
        System.out.println("[REPORT] User#" + userId + " reported auction#" + auctionId);
        client.sendMessage(new Message(ResponseCode.REPORT_SENT, "Báo cáo đã được gửi", null));
      } else {
        client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không thể lưu báo cáo vào Database", null));
      }
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi gửi báo cáo: " + e.getMessage(), null));
    }
  }

  private void handleSellerGetMyItems(ClientHandler client, Message request) {
    try {
      Integer sellerId = (Integer) request.getPayload();
      List<Item> items = itemService.getItemsBySeller(sellerId);
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

      sellerService.requestCreateAuction(seller, dto.getItemId(), dto.getStartTime(), dto.getEndTime());

      List<Auction> myAuctions = managerService.getAuctionsBySeller(sellerId);
      int newAuctionId = myAuctions.isEmpty() ? -1 : myAuctions.get(myAuctions.size() - 1).getAuctionId();

      client.sendMessage(new Message(ResponseCode.SELLER_AUCTION_CREATED, "Phiên đã gửi lên Admin để duyệt!", newAuctionId));

      Auction newAuction = myAuctions.isEmpty() ? null : myAuctions.get(myAuctions.size() - 1);
      if (newAuction != null) {
        SessionManager.getInstance().broadcastToAdmins(
            new Message(ResponseCode.ADMIN_NEW_PENDING_AUCTION, "Phiên mới cần duyệt!", newAuction),
            userService
        );
      }
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.SELLER_AUCTION_CREATE_FAILED, "Tạo phiên thất bại: " + e.getMessage(), null));
    }
  }

  private void handleSellerGetMyAuctions(ClientHandler client) {
    try {
      Integer sellerId = client.getLoggedInUserId();
      List<AuctionItemDTO> combinedAuctions = managerService.getAuctionItemsBySeller(sellerId);
      client.sendMessage(new Message(ResponseCode.SELLER_AUCTIONS_RESULT, "OK", combinedAuctions));
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

  private void handleSellerAddItem(ClientHandler client, Message request) {
    try {
      Integer currentUserId = client.getLoggedInUserId();
      if (currentUserId == null) {
        client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Phiên đăng nhập đã hết hạn. Vui lòng tắt ứng dụng và đăng nhập lại!", null));
        return;
      }

      Item item = (Item) request.getPayload();
      item.setSellerId(currentUserId);
      itemService.addItem(item);
      client.sendMessage(new Message(ResponseCode.SELLER_ITEMS_RESULT, "OK", null));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE,"Lỗi Server: " + e.getMessage(), null));
    }
  }

  private void handleSellerEditAuction(ClientHandler client, Message request) {
    try {
      Auction updatedAuction = (Auction) request.getPayload();
      Integer sellerId = client.getLoggedInUserId();
      User seller = userService.getUserById(sellerId);

      sellerService.editAuction(seller, updatedAuction);

      client.sendMessage(new Message(ResponseCode.SELLER_EDIT_SUCCESS, "Cập nhật thành công!", null));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.SELLER_EDIT_FAILED, e.getMessage(), null));
    }
  }

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
        client.sendMessage(new Message(ResponseCode.ADMIN_APPROVE_SUCCESS, "Đã duyệt phiên #" + auctionId, updatedAuction));

        Auction auction = managerService.getAuctionOrThrow(auctionId);
        SessionManager.getInstance().sendToUserIfOnline(auction.getSellerId(),
            new Message(ResponseCode.SELLER_AUCTION_APPROVED, "Phiên của bạn đã được duyệt!", auctionId));

        AuctionRoomManager.getInstance().openRoom(auction);
        SessionManager.getInstance().broadcastGlobal(
            new Message(ResponseCode.AUCTION_STATUS_CHANGED, "Có phòng đấu giá mới mở!", null)
        );
      } else {
        client.sendMessage(new Message(ResponseCode.ADMIN_APPROVE_FAILED, "Không thể duyệt phiên này", null));
      }
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ADMIN_APPROVE_FAILED, e.getMessage(), null));
    }
  }

  private void handleAdminRejectAuction(ClientHandler client, Message request) {
    try {
      Object[] payload = (Object[]) request.getPayload();
      int auctionId = (int) payload[0];
      String reason = (String) payload[1];
      adminService.rejectAuction(auctionId, reason);
      client.sendMessage(new Message(ResponseCode.ADMIN_REJECT_SUCCESS, "Đã từ chối phiên #" + auctionId, auctionId));

      Auction auction = managerService.getAuctionOrThrow(auctionId);
      SessionManager.getInstance().sendToUserIfOnline(auction.getSellerId(),
          new Message(ResponseCode.SELLER_AUCTION_REJECTED, "Phiên của bạn bị từ chối!", new Object[]{auctionId, reason}));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ADMIN_REJECT_FAILED, e.getMessage(), null));
    }
  }

  private void handleAdminBlockAuction(ClientHandler client, Message request) {
    try {
      int auctionId;
      String reason = "";
      Object payload = request.getPayload();
      if (payload instanceof Object[] arr) {
        auctionId = (int) arr[0];
        reason    = arr.length > 1 && arr[1] != null ? (String) arr[1] : "";
      } else {
        auctionId = (Integer) payload;
      }
      final int finalAuctionId = auctionId;
      final String finalReason = reason;

      boolean success = adminService.blockAuction(finalAuctionId, finalReason);
      if (success) {
        client.sendMessage(new Message(ResponseCode.ADMIN_BLOCK_SUCCESS, "Đã phong tỏa phiên #" + finalAuctionId, finalAuctionId));

        AuctionRoomManager.getInstance().closeRoom(finalAuctionId);

        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
          try {
            boolean deleted = adminService.deleteBlockedAuction(finalAuctionId);
            if (deleted) {
              Message deleteMsg = new Message(ResponseCode.ADMIN_DELETE_BLOCKED_SUCCESS, "Phiên đã bị xóa hoàn toàn", finalAuctionId);
              SessionManager.getInstance().broadcastToAdmins(deleteMsg);
            }
          } catch (Exception ex) {}
        }, 5, java.util.concurrent.TimeUnit.MINUTES);
      } else {
        client.sendMessage(new Message(ResponseCode.ADMIN_BLOCK_FAILED, "Không thể phong tỏa", null));
      }
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ADMIN_BLOCK_FAILED, e.getMessage(), null));
    }
  }

  private void handleAdminDeleteBlockedAuction(ClientHandler client, Message request) {
    try {
      Integer auctionId = (Integer) request.getPayload();
      boolean deleted = adminService.deleteBlockedAuction(auctionId);
      if (deleted) {
        client.sendMessage(new Message(ResponseCode.ADMIN_DELETE_BLOCKED_SUCCESS, "Đã xóa phiên BLOCKED #" + auctionId, auctionId));
      }
    } catch (Exception e) {}
  }

  private void handleAdminGetAllTransactions(ClientHandler client) {
    try {
      List<TransactionRequest> transactions = transactionService.getAllTransactions();
      client.sendMessage(new Message(ResponseCode.ADMIN_ALL_TRANSACTIONS_RESULT, "OK", transactions));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được giao dịch", null));
    }
  }

  private void handleAdminApproveTransaction(ClientHandler client, Message request) {
    try {
      Integer txId = (Integer) request.getPayload();
      transactionService.approveTransaction(txId);

      Message msg = new Message(ResponseCode.ADMIN_TRANSACTION_APPROVED, "Đã duyệt giao dịch", txId);
      SessionManager.getInstance().broadcastToAdmins(msg);
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi duyệt giao dịch: " + e.getMessage(), null));
    }
  }

  private void handleAdminRejectTransaction(ClientHandler client, Message request) {
    try {
      Integer txId = (Integer) request.getPayload();
      transactionService.rejectTransaction(txId);

      Message msg = new Message(ResponseCode.ADMIN_TRANSACTION_REJECTED, "Đã từ chối giao dịch", txId);
      SessionManager.getInstance().broadcastToAdmins(msg);
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Lỗi từ chối giao dịch: " + e.getMessage(), null));
    }
  }

  private void handleAdminCreateTransaction(ClientHandler client, Message request) {
    try {
      Object[] payload = (Object[]) request.getPayload();
      int auctionId  = (int)    payload[0];
      int winnerId   = (int)    payload[1];
      double price   = (double) payload[2];

      transactionService.createTransactionFromAuction(auctionId, winnerId, price);
      client.sendMessage(new Message(ResponseCode.ADMIN_TRANSACTION_CREATED, "Giao dịch đã được tạo!", null));

      User refreshed = userService.getUserById(winnerId);
      if (refreshed != null) {
        client.sendMessage(new Message(ResponseCode.PROFILE_RESULT, "OK", refreshed));
      }
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

  private void handleAdminGetAllIssues(ClientHandler client) {
    try {
      com.auction.server.dao.IssueDAO issueDAO = new com.auction.server.dao.IssueDAO();
      java.util.List<com.auction.common.model.IssueRecord> issues = issueDAO.getAllIssues();
      client.sendMessage(new Message(ResponseCode.ADMIN_ISSUES_RESULT, "OK", (java.io.Serializable) issues));
    } catch (Exception e) {
      client.sendMessage(new Message(ResponseCode.ERROR_MESSAGE, "Không lấy được báo cáo: " + e.getMessage(), null));
    }
  }
}