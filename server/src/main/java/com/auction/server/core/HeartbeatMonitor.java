package com.auction.server.core;



import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.auction.server.core.ClientHandler;

public class HeartbeatMonitor {
    private static final HeartbeatMonitor instance = new HeartbeatMonitor();
    // Chạy ngầm 1 luồng riêng không ảnh hưởng luồng chính
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private HeartbeatMonitor() {}
    public static HeartbeatMonitor getInstance() { return instance; }

    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (ClientHandler handler : SessionManager.getInstance().getAllConnections()) {
                // Nếu quá 30 giây không nhận được ping -> Xác định là Zombie (rớt mạng ngang, rút cáp)
                if (now - handler.getLastHeartbeat() > 30000) {
                    System.out.println("[HEARTBEAT] Phát hiện Client chết vĩnh viễn. Đang dọn dẹp...");
                    handler.cleanUp();
                }
            }
        }, 10, 5, TimeUnit.SECONDS); // Delay 10s, quét 5s/lần
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}