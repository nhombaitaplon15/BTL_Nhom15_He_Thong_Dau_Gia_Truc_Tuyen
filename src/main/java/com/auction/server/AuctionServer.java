package com.auction.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {

  private static final int PORT = 9999;

  public static void main(String[] args) throws IOException {
    ExecutorService pool = Executors.newFixedThreadPool(50);

    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
      System.out.println("[SERVER] Đang chạy trên port " + PORT);

      while (true) {
        Socket clientSocket = serverSocket.accept();
        System.out.println("[SERVER] Client mới: " + clientSocket.getInetAddress());
        pool.execute(new ClientHandler1(clientSocket));
      }
    }
  }
}