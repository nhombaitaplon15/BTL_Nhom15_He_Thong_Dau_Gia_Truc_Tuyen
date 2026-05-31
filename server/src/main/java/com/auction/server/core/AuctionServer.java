package com.auction.server.core;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.auction.server.core.AuctionRoomManager;

public class AuctionServer {
    private static final int PORT = 8888;
    // CachedThreadPool tái sử dụng thread cho client, scale tốt cho hàng vạn connection I/O
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private boolean isRunning = false;

    public void start() {
        isRunning = true;
        // Khởi động các Manager
        HeartbeatMonitor.getInstance().startMonitoring();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] Khởi động tại port " + PORT + " - Sẵn sàng nhận kết nối...");

            // Graceful Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true); // Tắt thuật toán Nagle để giảm latency (rất quan trọng cho realtime)
                clientSocket.setKeepAlive(true);

                ClientHandler handler = new ClientHandler(clientSocket);
                clientPool.execute(handler); // Giao việc cho ThreadPool, Main thread tiếp tục accept
            }
        } catch (IOException e) {
            System.err.println("[SERVER FATAL] Lỗi ServerSocket: " + e.getMessage());
        }
    }

    private void shutdown() {
        System.out.println("[SERVER] Đang tắt hệ thống an toàn...");
        isRunning = false;
        clientPool.shutdown();
        AuctionRoomManager.getInstance().shutdownAllRooms();
        HeartbeatMonitor.getInstance().stop();
    }
}