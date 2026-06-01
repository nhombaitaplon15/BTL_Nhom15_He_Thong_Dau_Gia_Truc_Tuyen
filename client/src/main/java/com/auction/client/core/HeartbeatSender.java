package com.auction.client.core;

import com.auction.client.core.SocketClient;
import com.auction.common.network.RequestCode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Module chịu trách nhiệm duy trì sự sống của kết nối (Keep-Alive).
 * Đảm bảo Server không hiểu nhầm Client đã chết (do rớt mạng ngang hoặc lag I/O).
 */
public class HeartbeatSender {
    private ScheduledExecutorService scheduler;
    private final SocketClient socketClient;
    private boolean isRunning = false;

    public HeartbeatSender(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;

        // SingleThread là đủ để gửi tín hiệu Ping nhẹ nhàng
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Client-Heartbeat-Thread");
            t.setDaemon(true); // Để tắt khi JavaFX Application tắt
            return t;
        });

        // Cứ 10 giây gửi PING lên Server một lần
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (socketClient.isConnected()) {
                    // Truyền payload = null vì tín hiệu PING chỉ cần Header
                    socketClient.sendRequest(RequestCode.PING, null);
                } else {
                    stop(); // Tự động hủy nếu đã đứt kết nối
                }
            } catch (Exception e) {
                System.err.println("[HEARTBEAT] Lỗi khi gửi Ping: " + e.getMessage());
                stop();
            }
        }, 2, 10, TimeUnit.SECONDS);
        // Initial delay 2s để chắc chắn Socket đã setup xong
    }

    public void stop() {
        isRunning = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}