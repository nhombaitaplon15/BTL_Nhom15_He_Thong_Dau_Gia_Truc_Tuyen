package com.auction.client.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;

public class BidUpdateListener implements Runnable {

  private final BufferedReader in;
  private BidUpdateCallback callback;
  private volatile boolean running = true;

  public interface BidUpdateCallback {
    void onBidUpdate(int auctionId, double newPrice, int totalBids);
  }

  public BidUpdateListener(BufferedReader in) {
    this.in = in;
  }

  public void setCallback(BidUpdateCallback cb) { this.callback = cb; }
  public void stop() { this.running = false; }

  @Override
  public void run() {
    try {
      String line;
      while (running && (line = in.readLine()) != null) {
        JsonObject msg = JsonParser.parseString(line).getAsJsonObject();

        if ("BID_UPDATE".equals(msg.get("action").getAsString())) {
          int    auctionId = msg.get("auctionId").getAsInt();
          double newPrice  = msg.get("newPrice").getAsDouble();
          int    totalBids = msg.get("totalBids").getAsInt();

          Platform.runLater(() -> {
            if (callback != null) callback.onBidUpdate(auctionId, newPrice, totalBids);
          });
        }
      }
    } catch (IOException e) {
      System.out.println("[CLIENT] BidUpdateListener dừng: " + e.getMessage());
    }
  }
}