package com.auction.server;

import com.auction.common.model.Message;
import com.auction.common.model.User;
import com.auction.common.model.Auction;
import com.auction.exception.AuctionException;
import com.auction.service.BiddingService;
import com.auction.service.UserService;
import com.auction.service.ManagerService;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket socket; // kết nối vật lý giữa Client và Server
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
                if (!(received instanceof Message)) continue; // Nếu sai định dạng thì bỏ qua

                Message request = (Message) received;
                String command = request.getCommand(); // lấy lệnh muốn thực hiện từ Client

                // ĐIỀU HƯỚNG LỆNH (Dispatcher), chuyển đúng bộ phận chuyên môn
                switch (command) {
                    case "LOGIN" -> handleLogin(request);
                    case "REGISTER" -> handleRegister(request);
                    case "PLACE_BID" -> handlePlaceBid(request);
                    case "GET_AUCTIONS" -> handleGetAuctions();
                    case "GET_ITEMS_BY_TYPE" -> handleGetItemsByType(request);
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

    // 🛠️ ĐÃ SỬA: HÀM ĐĂNG NHẬP (Bóc tách dữ liệu và bọc try-catch bắt lỗi từ Service gửi về)
    private void handleLogin(Message request) {
        try {
            // Data từ Client gửi lên app thường là đối tượng chứa thông tin thô hoặc Map thông tin đăng nhập
            // Vì Client vẫn có thể gửi một bọc thông tin (ví dụ mượn class con nào đó hoặc Map), ta lấy thông tin ra:
            User creds = (User) request.getData();
            String username = creds.getUsername();
            String password = creds.getPassword();

            // Gọi hàm handleLogin mới nhận 2 tham số kiểu String từ UserService
            User user = userService.handleLogin(username, password);

            // Nếu không ném ra ngoại lệ, tức là đăng nhập thành công!
            this.currentUser = user; // Lưu lại phiên làm việc cho Thread này
            sendResponse("SUCCESS", "Đăng nhập thành công", user);
            System.out.println("[+] [LOGIN SUCCESS] User: " + username + " với quyền: " + user.getRole());

        } catch (AuctionException e) {
            // Bắt các lỗi cụ thể như: SAI TÀI KHOẢN, MẬT KHẨU, TÀI KHOẢN BỊ KHÓA
            sendResponse("FAILED", e.getMessage(), null);
        } catch (Exception e) {
            sendResponse("FAILED", "Lỗi hệ thống đăng nhập bất ngờ!", null);
            e.printStackTrace();
        }
    }

    // 🛠️ ĐÃ SỬA: HÀM ĐĂNG KÝ (Bóc tách dữ liệu String trực tiếp gửi xuống UserService)
    private void handleRegister(Message request) {
        try {
            User newUser = (User) request.getData();

            String username = newUser.getUsername();
            String password = newUser.getPassword();
            String email = newUser.getEmail();
            String phone = newUser.getPhone();

            // Gọi hàm handleRegister mới nhận các chuỗi String trực tiếp từ UserService
            if (userService.handleRegister(username, password, email, phone)) {
                sendResponse("SUCCESS", "Đăng ký thành công", null);
            } else {
                sendResponse("FAILED", "Đăng ký thất bại, lỗi hệ thống!", null);
            }

        } catch (AuctionException e) {
            // Bắt các lỗi logic định dạng, trùng tên đăng nhập, email, số điện thoại từ Service ném ra
            sendResponse("FAILED", e.getMessage(), null);
        } catch (Exception e) {
            sendResponse("FAILED", "Lỗi xử lý đăng ký hệ thống!", null);
            e.printStackTrace();
        }
    }

    // hàm xử lý đặt giá
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

    // Hàm gửi tin nhắn phản hồi về Client cho nhanh
    private void sendResponse(String status, String note, Object data) {

        try {
            Message response = new Message(status, note, data);
            out.writeObject(response);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
    private void handleGetItemsByType(Message request) {
        try {
            // 1. Client gửi lên danh mục dạng String (VD: "VEHICLE", "ART", "ELECTRONICS")
            String itemType = (String) request.getData();

            // 2. Khởi tạo ItemService để nói chuyện với Database qua ItemDAO
            com.auction.service.ItemService itemService = new com.auction.service.ItemService();

            // 3. Gọi hàm getItemsByType mà bạn vừa thêm ở ItemDAO lúc nãy
            List<com.auction.common.model.Item> items = itemService.getItemsByType(itemType);

            // 4. Trả danh sách sản phẩm này về cho Client qua Socket mạng
            sendResponse("SUCCESS", "Lấy danh sách sản phẩm thành công", items);

        } catch (Exception e) {
            sendResponse("FAILED", "Lỗi Server khi lấy danh sách sản phẩm: " + e.getMessage(), null);
            e.printStackTrace();
        }
    }
    private void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}