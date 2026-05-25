package com.auction.client.core;

import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/**
 * SocketClient - Singleton quản lý toàn bộ kết nối TCP với Server.
 *
 * ĐÃ SỬA LỖI CHÍNH:
 * - processResponse() trước đây RỖng hoàn toàn => KHÔNG có gì update UI khi server gửi broadcast.
 * - Nay processResponse() gọi MessageRouter.route(message) để phân phối tới đúng Controller.
 *
 * Đặt tại: client/src/main/java/com/auction/client/core/SocketClient.java
 */
public class SocketClient {
    private static final SocketClient instance = new SocketClient();
    private static final String SERVER_IP = "127.0.0.1";
    private static final int PORT = 8888;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean isConnected = false;
    private ScheduledExecutorService heartbeatScheduler;

    private SocketClient() {}

    public static SocketClient getInstance() { return instance; }

    public void connect() {
        try {
            socket = new Socket(SERVER_IP, PORT);
            socket.setTcpNoDelay(true); // Tắt Nagle để giảm latency realtime
            socket.setKeepAlive(true);

            // QUAN TRỌNG: flush out TRƯỚC khi tạo in để tránh deadlock kẻ chờ header
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            isConnected = true;
            System.out.println("[CLIENT] Kết nối Server thành công tại " + SERVER_IP + ":" + PORT);

            startHeartbeat();

            // Thread lắng nghe server liên tục (blocking read)
            Thread listenerThread = new Thread(this::listenToServer, "Client-Listener-Thread");
            listenerThread.setDaemon(true);
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("[CLIENT] Không thể kết nối server. Thử lại sau 5s...");
            scheduleReconnect();
        }
    }

    /**
     * Gửi request lên server.
     * Thread-safe: synchronized trên out.
     */
    public void sendRequest(RequestCode code, Object payload) {
        if (!isConnected || out == null) return;
        try {
            synchronized (out) {
                out.writeObject(new Message(code, payload));
                out.flush();
                out.reset(); // Tránh memory leak của ObjectOutputStream
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Lỗi gửi request: " + e.getMessage());
            isConnected = false;
            scheduleReconnect();
        }
    }

    /**
     * Vòng lặp lắng nghe response từ server (chạy trên thread riêng).
     * Mỗi message nhận được đưa vào JavaFX Application Thread qua Platform.runLater.
     */
    private void listenToServer() {
        try {
            while (isConnected) {
                Message response = (Message) in.readObject();
                // BẮT BUỘC: JavaFX không cho phép update UI từ thread ngoài Application Thread
                Platform.runLater(() -> processResponse(response));
            }
        } catch (Exception e) {
            if (isConnected) {
                System.err.println("[CLIENT] Mất kết nối với server: " + e.getMessage());
                isConnected = false;
                scheduleReconnect();
            }
        }
    }

    /**
     * ĐÃ SỬA: Phân phối message đến đúng Controller qua MessageRouter.
     *
     * Trước đây: hàm này rỗng => TOÀN BỘ thông báo realtime từ server bị nuốt,
     * không có gì hiển thị ra UI (giá bid mới, phiên kết thúc, chat...).
     *
     * Sau khi sửa: gọi MessageRouter.route() để tự động điều hướng đến
     * Controller đang đăng ký lắng nghe ResponseCode tương ứng.
     *
     * Chạy trên JavaFX Application Thread (do Platform.runLater bọc bên ngoài).
     */
    private void processResponse(Message response) {
        MessageRouter.getInstance().route(response);
    }

    private void startHeartbeat() {
        stopHeartbeat(); // Tránh tạo nhiều scheduler khi reconnect
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Client-Heartbeat-Thread");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected) {
                sendRequest(RequestCode.PING, null);
            } else {
                stopHeartbeat();
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
    }

    /**
     * Tự động thử kết nối lại sau 5 giây nếu bị ngắt đột ngột.
     * Exponential backoff có thể thêm vào sau để tránh spam server.
     */
    private void scheduleReconnect() {
        stopHeartbeat();
        Thread reconnectThread = new Thread(() -> {
            try {
                System.out.println("[CLIENT] Đang thử kết nối lại sau 5 giây...");
                Thread.sleep(5000);
                connect();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "Client-Reconnect-Thread");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    public boolean isConnected() { return isConnected; }

    public void disconnect() {
        isConnected = false;
        stopHeartbeat();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }
}
