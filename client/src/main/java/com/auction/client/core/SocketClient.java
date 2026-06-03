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
            socket.setKeepAlive(true);

            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            isConnected = true;

            // Bắt đầu luồng lắng nghe dữ liệu từ Server
            startListening();
            startHeartbeat();

            System.out.println("[CLIENT] Kết nối thành công tới Server.");
        } catch (Exception e) {
            System.err.println("[CLIENT] Lỗi kết nối: " + e.getMessage());
        }
    }

    private void startListening() {
        Thread listenThread = new Thread(() -> {
            try {
                while (isConnected) {
                    Message msg = (Message) in.readObject();
                    // TỐI ƯU: Chuyển dữ liệu cho Router xử lý (MessageRouter sẽ tự route trên luồng FX nếu cần)
                    MessageRouter.getInstance().route(msg);
                }
            } catch (Exception e) {
                if (isConnected) System.err.println("[CLIENT] Mất kết nối server!");
            }
        });
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public synchronized void sendRequest(RequestCode code, Object payload) {
        try {
            if (out != null) {
                out.writeObject(new Message(code, payload));
                out.flush();
                out.reset();
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Lỗi gửi request: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Client-Heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected) sendRequest(RequestCode.PING, null);
        }, 5, 10, TimeUnit.SECONDS);
    }

    public boolean isConnected() { return isConnected; }

    public void disconnect() {
        isConnected = false;
        try {
            if (socket != null) socket.close();
            if (heartbeatScheduler != null) heartbeatScheduler.shutdownNow();
        } catch (Exception e) { e.printStackTrace(); }
    }
}