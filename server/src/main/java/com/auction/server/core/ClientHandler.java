package com.auction.server.core;

import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicLong;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final AtomicLong lastHeartbeat;
    private Integer loggedInUserId = null; // Gắn định danh khi user login

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Bắt buộc flush header của ObjectOutputStream
            in = new ObjectInputStream(socket.getInputStream());

            SessionManager.getInstance().registerConnection(this);

            while (!socket.isClosed()) {
                Message request = (Message) in.readObject();
                lastHeartbeat.set(System.currentTimeMillis()); // Update heartbeat

                if (request.getRequestCode() == RequestCode.PING) {
                    // Xử lý ngầm, không in ra log để tránh spam
                    continue;
                }

                // Chuyển giao request cho Dispatcher xử lý logic
                RequestDispatcher.getInstance().dispatch(this, request);
            }
        } catch (EOFException | SocketException e) {
            // Client ngắt kết nối đột ngột
            System.err.println("[CLIENT HANDLER] Client disconnect: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[CLIENT HANDLER ERROR] " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanUp();
        }
    }

    public void sendMessage(Message msg) {
        try {
            synchronized (out) { // Synchronized out để tránh 2 thread (VD: room broadcast và response login) cùng ghi 1 lúc
                out.writeObject(msg);
                out.flush();
                out.reset(); // Ngăn Memory Leak của ObjectOutputStream
            }
        } catch (IOException e) {
            cleanUp(); // Nếu gửi lỗi -> Socket đã chết, dọn rác ngay
        }
    }

    public long getLastHeartbeat() { return lastHeartbeat.get(); }
    public void setLoggedInUserId(Integer userId) { this.loggedInUserId = userId; }
    public Integer getLoggedInUserId() { return loggedInUserId; }

    public void cleanUp() {
        try {
            if (loggedInUserId != null) {
                SessionManager.getInstance().removeSession(loggedInUserId);
                AuctionRoomManager.getInstance().removeUserFromAllRooms(this);
            }
            SessionManager.getInstance().removeConnection(this);
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}