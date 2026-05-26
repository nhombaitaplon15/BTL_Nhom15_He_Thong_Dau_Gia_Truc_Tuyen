package com.auction.server;

import com.auction.common.network.Actions;
import com.auction.common.model.*;
import com.auction.server.dao.*;
import com.auction.service.*;
import com.google.gson.*;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;

public class ClientHandler1 implements Runnable {

  private final Socket clientSocket;
  private PrintWriter  out;
  private BufferedReader in;
  private final Gson gson = new Gson();

  // Các Service của bạn (đã import com.auction.server.service.*)
  private final AuctionService auctionService = new AuctionService();
  private final ItemService    itemService    = new ItemService();
  private final ManagerService managerService = new ManagerService(itemService );
  private final AdminService    adminService    = new AdminService(managerService );
  private final BiddingService    biddingService    = new BiddingService(managerService );
  private final PaymentService paymentService = new PaymentService() ;
  private final SellerService sellerService = new SellerService(managerService );
  private final TransactionService transactionService = new TransactionService(managerService );
  private final UserService userService = new UserService() ;

  // Các DAO của bạn (đã import com.auction.server.dao.*)

  private final AuctionDAO auctionDAO = new AuctionDAO();
  private final ItemDAO    itemDAO    = new ItemDAO();
  private final DBConnection dbConnection = new DBConnection();
  private final PaymentDAO paymentDAO = new PaymentDAO() ;
  private final TransactionDAO transactionDAO = new TransactionDAO() ;
  private final UserDAO userDAO = new UserDAO() ;

  private User currentUser;

  public ClientHandler1(Socket socket) {
    this.clientSocket = socket;
  }

  @Override
  public void run() {
    try {
      out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
      in  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));

      String line;
      while ((line = in.readLine()) != null) {
        JsonObject request  = JsonParser.parseString(line).getAsJsonObject();
        JsonObject response = handleRequest(request);
        out.println(gson.toJson(response));
      }
    } catch (IOException e) {
      System.out.println("[SERVER] Client ngắt kết nối: " + e.getMessage());
    } finally {
      try { clientSocket.close(); } catch (IOException ignored) {}
    }
  }

  private JsonObject handleRequest(JsonObject request) {
    String action = request.get("action").getAsString();
    JsonObject data = request.has("data")
        ? request.get("data").getAsJsonObject()
        : new JsonObject();

    return switch (action) {
      case Actions.LOGIN                -> handleLogin(data);
      case Actions.CREATE_AUCTION       -> handleCreateAuction(data);
      case Actions.PLACE_BID            -> handlePlaceBid(data);
      // Thêm các case khác tùy theo file Actions.java
      default -> errorResponse("Action không hợp lệ: " + action);
    };
  }

  private JsonObject handleCreateAuction(JsonObject data) {
    try {
      // Tạm thời comment logic DB, bạn gọi DAO của bạn ở đây
      // boolean ok = auctionDAO.insertAuction(...);
      return successResponse("Tạo phiên thành công!", null);
    } catch (Exception e) {
      return errorResponse("Lỗi server: " + e.getMessage());
    }
  }

  private JsonObject handlePlaceBid(JsonObject data) {
    try {
      int    bidderId  = data.get("bidderId").getAsInt();
      int    auctionId = data.get("auctionId").getAsInt();
      double bidAmount = data.get("bidAmount").getAsDouble();

      // Gọi logic từ BiddingService của bạn ở đây

      return successResponse("Đặt giá thành công!", null);
    } catch (Exception e) {
      return errorResponse(e.getMessage());
    }
  }

  private JsonObject handleLogin(JsonObject data) { return successResponse("Login OK", null); }

  private JsonObject successResponse(String message, JsonElement data) {
    JsonObject r = new JsonObject();
    r.addProperty("success", true);
    r.addProperty("message", message);
    if (data != null) r.add("data", data);
    return r;
  }

  private JsonObject errorResponse(String message) {
    JsonObject r = new JsonObject();
    r.addProperty("success", false);
    r.addProperty("message", message);
    return r;
  }
}