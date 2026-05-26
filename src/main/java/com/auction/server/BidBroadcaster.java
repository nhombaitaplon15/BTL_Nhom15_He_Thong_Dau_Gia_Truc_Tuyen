package com.auction.server;

import com.google.gson.JsonObject;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BidBroadcaster {

  private static BidBroadcaster instance;

  private final Map<Integer, List<PrintWriter>> observers =
      new ConcurrentHashMap<>();

  public static BidBroadcaster getInstance() {
    if (instance == null) instance = new BidBroadcaster();
    return instance;
  }

  public void subscribe(int auctionId, PrintWriter clientOut) {
    observers.computeIfAbsent(auctionId, k -> Collections.synchronizedList(new ArrayList<>()))
        .add(clientOut);
  }

  public void unsubscribe(int auctionId, PrintWriter clientOut) {
    List<PrintWriter> list = observers.get(auctionId);
    if (list != null) list.remove(clientOut);
  }

  public void broadcast(int auctionId, double newPrice,
                        int bidderId, int totalBids) {
    List<PrintWriter> list = observers.get(auctionId);
    if (list == null || list.isEmpty()) return;

    JsonObject push = new JsonObject();
    push.addProperty("action",     "BID_UPDATE");
    push.addProperty("auctionId",  auctionId);
    push.addProperty("newPrice",   newPrice);
    push.addProperty("bidderId",   bidderId);
    push.addProperty("totalBids",  totalBids);

    String msg = push.toString();

    synchronized (list) {
      Iterator<PrintWriter> it = list.iterator();
      while (it.hasNext()) {
        PrintWriter pw = it.next();
        try {
          pw.println(msg);
          if (pw.checkError()) it.remove();
        } catch (Exception e) {
          it.remove();
        }
      }
    }
  }
}