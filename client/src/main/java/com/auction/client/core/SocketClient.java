package client.core;

import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform; // Cực quan trọng cho JavaFX

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
            socket.setTcpNoDelay(true);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            isConnected = true;

            System.out.println("[CLIENT] Kết nối Server thành công.");

            // Bắt đầu gửi Ping
            startHeartbeat();

            // Thread riêng để lắng nghe phản hồi từ Server liên tục
            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true); // Thread tự chết khi đóng App
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("[CLIENT] Không thể kết nối. Thử lại sau 5s...");
            scheduleReconnect();
        }
    }

    public void sendRequest(RequestCode code, Object payload) {
        if (!isConnected) return;
        try {
            out.writeObject(new Message(code, payload));
            out.flush();
            out.reset();
        } catch (Exception e) {
            isConnected = false;
        }
    }

    private void listenToServer() {
        try {
            while (isConnected) {
                Message response = (Message) in.readObject();
                // Vì giao diện JavaFX không cho phép update từ Thread ngoài, ta phải bọc trong Platform.runLater
                Platform.runLater(() -> processResponse(response));
            }
        } catch (Exception e) {
            isConnected = false;
            scheduleReconnect(); // Disconnect đột ngột thì tự động gọi Reconnect Storm protection
        }
    }

    private void processResponse(Message response) {
        // Tùy vào mã ResponseCode mà kích hoạt UI tương ứng
        // Ví dụ: Broadcast giá thay đổi lên Label của thẻ ItemCard
        // switch (response.getResponseCode()) { ... }
    }

    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected) sendRequest(RequestCode.PING, null);
        }, 5, 10, TimeUnit.SECONDS);
    }

    private void scheduleReconnect() {
        if (heartbeatScheduler != null) heartbeatScheduler.shutdownNow();
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Đợi 5s rồi thử lại tránh Reconnect Storm sập Server
                connect();
            } catch (InterruptedException ignored) {}
        }).start();
    }
    public boolean isConnected() {
        return isConnected;
    }
}