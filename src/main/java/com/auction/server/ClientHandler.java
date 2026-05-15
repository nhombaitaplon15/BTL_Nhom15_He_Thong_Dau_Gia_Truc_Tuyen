package com.auction.server;

import com.auction.common.model.Message;
import com.auction.common.model.User;
import com.auction.common.model.Auction;
import com.auction.service.BiddingService;
import com.auction.service.UserService;
import com.auction.service.ManagerService;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket socket; // kết nối vật lí giữa Client và Server
    private ObjectInputStream in; // luồng để nhận đối tượng (Message) từ client
    private ObjectOutputStream out; // luồng để gửi đối tượng (Message) về client

    // Các Service để xử lý logic: Đấu giá, người dùng, quản lý
    private final BiddingService biddingService;
    private final UserService userService;
    private final ManagerService managerService;

    private User currentUser; // thẻ dùng lưu danh tính của người dùng sau khi login
    // sau khi Login thành công thì biến sẽ hết NULL, giúp Server biết ai đang thực hiện lệnh
    public ClientHandler(Socket socket, BiddingService bidSvc, UserService userSvc, ManagerService mgrSvc) {
        this.socket = socket;
        this.biddingService = bidSvc;
        this.userService = userSvc;
        this.managerService = mgrSvc;
    }

    @Override
    public void run() {
        try {
            // Khởi tạo luồng (Stream) để nói chuyện với Client
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                // Đợi nhận gói tin Message từ Client
                Object received = in.readObject();
                if (!(received instanceof Message)) continue;// Nếu sai định dạng thì bỏ qua

                Message request = (Message) received;
                String command = request.getCommand(); // lấy lệnh muốn thực hiện từ Client

                // ĐIỀU HƯỚNG LỆNH (Dispatcher), chuyển đúng bộ phận chuyên môn
                switch (command) {
                    case "LOGIN" -> handleLogin(request);
                    case "REGISTER" -> handleRegister(request);
                    case "PLACE_BID" -> handlePlaceBid(request);
                    case "GET_AUCTIONS" -> handleGetAuctions();
                    case "LOGOUT" -> {
                        currentUser = null; // xóa danh tính khi đăng xuất
                        sendResponse("SUCCESS", "Đã đăng xuất", null);
                    }
                    // 1. Lấy chi tiết một phiên (bao gồm lịch sử trả giá)
                    case "GET_AUCTION_DETAIL" -> handleGetDetail(request);

                    // 2. Lấy thông tin cá nhân mới nhất (số dư, tên...)
                    case "GET_MY_INFO" -> handleGetMyInfo();
                    case "REJECT_WIN" -> {
                        int auctionId = (Integer) request.getData(); // Client gửi auctionId lên
                        biddingService.rejectWin(currentUser, auctionId);
                        sendResponse("SUCCESS", "Hủy sản phẩm thành công. Bạn bị phạt 7% giá trị đấu giá!", null);
                    }
                    default -> sendResponse("FAILED", "Lệnh không hợp lệ", null);
                }
            }
        } catch (Exception e) {
            System.out.println("[-] Client ngắt kết nối: " + socket.getInetAddress());
        } finally {
            close();
        }
    }
    // hàm xử lí chi tiết
    // hàm đăng nhập
    private void handleLogin(Message request) {
        // Data gửi từ Client thường là 1 Map hoặc Object User chứa email/pass
        User creds = (User) request.getData();// lấy tài khoản và mật khẩu
        User user = userService.handleLogin(creds);// nhờ UserService kiểm tra

        if (user != null) {
            this.currentUser = user; // Lưu lại để biết ai đang dùng Thread này
            sendResponse("SUCCESS", "Đăng nhập thành công", user);
        } else {
            sendResponse("FAILED", "Sai tài khoản hoặc mật khẩu", null);
        }
    }
    // hàm xử lí đặt giá
    private void handlePlaceBid(Message request) {
        try {
            // 1. Bóc gói dữ liệu lấy ID phiên và Giá đặt
            Map<String, Object> data = (Map<String, Object>) request.getData();
            int auctionId = (int) data.get("auctionId");
            double amount = (double) data.get("amount");

            // 2. Gọi thẳng Service xử lý (currentUser đã được gán lúc handleLogin thành công rồi)
            biddingService.placeBid(currentUser, auctionId, amount);

            // 3. Báo tin vui về cho Client
            sendResponse("SUCCESS", "Đặt giá thành công!", null);

        } catch (Exception e) {
            // Nếu có lỗi (ví dụ: đặt giá thấp hơn giá hiện tại), bắn cái Note về cho Client hiện Alert
            sendResponse("FAILED", e.getMessage(), null);
        }
    }
    // hàm lấy danh sách
    private void handleGetAuctions() {
        List<Auction> list = managerService.getAllAuctions();
        sendResponse("SUCCESS", "Lấy danh sách thành công", list);
    }
    // hàm xử lí đăng kí
    private void handleRegister(Message request) {
        User newUser = (User) request.getData();

        // 🛠️ CHẶN BẢO MẬT: Nếu client cố tình gửi quyền ADMIN lên, hạ cấp xuống BIDDER ngay
        if ("ADMIN".equalsIgnoreCase(newUser.getRole())) {
            newUser.setRole("BIDDER");
        }

        if (userService.handleRegister(newUser)) {
            sendResponse("SUCCESS", "Đăng ký thành công", null);
        } else {
            sendResponse("FAILED", "Đăng ký thất bại (Email hoặc Username đã tồn tại)", null);
        }
    }
    // Hàm gửi tin nhắn phản hồi về Client cho nhanh
    private void sendResponse(String status, String note, Object data) {
        try {
            Message response = new Message(status, note, data);
            out.writeObject(response);
            out.flush();
        } catch (IOException e) { e.printStackTrace(); }
    }
    private void handleGetDetail(Message request) {
        int auctionId = (Integer) request.getData();
        Auction detail = managerService.getAuction(auctionId);
        sendResponse("SUCCESS", "OK", detail);
    }

    private void handleGetMyInfo() {
        // Lấy lại user từ DB để có số dư mới nhất
        User updatedUser = userService.getUserById(currentUser.getId());
        this.currentUser = updatedUser;
        sendResponse("SUCCESS", "OK", updatedUser);
    }

    private void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
