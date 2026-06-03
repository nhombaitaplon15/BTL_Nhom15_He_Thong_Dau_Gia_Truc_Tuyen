package com.auction.client.core;

import com.auction.common.network.RequestCode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatSender {
    private ScheduledExecutorService scheduler;
    private final SocketClient socketClient;

    public HeartbeatSender(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Client-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            if (socketClient.isConnected()) {
                socketClient.sendRequest(RequestCode.PING, null);
            }
        }, 2, 10, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}