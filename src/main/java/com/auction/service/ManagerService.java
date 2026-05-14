package com.auction.service;

import com.auction.common.model.Auction;
import com.auction.common.model.Items;
import com.auction.exception.AuctionException;
import com.auction.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ManagerService { // quản lí, điều phối hệ thống đấu giá

  private final ItemService itemService;
  private final Map<Integer, Auction> auctionList = new ConcurrentHashMap<>();  // map này để an toàn cho nhiều luồng truy cập cùng lúc
  private volatile boolean running = true; // khóa an toàn cho hệ thống

  public ManagerService(ItemService itemService) {
    this.itemService = itemService;
  }

  //Lấy phiên
  public Auction getAuction(int auctionId) {
    return auctionList.get(auctionId);
  }

  public List<Auction> getAllAuctions() {
    return new ArrayList<>(auctionList.values());
  }

  private Auction getAuctionOrThrow(int auctionId) {
    Auction auction = auctionList.get(auctionId);
    if (auction == null) {
      throw new AuctionException(
          ErrorCode.AUCTION_NOT_FOUND.name(),
          "Phiên đấu giá không tồn tại"
      );
    }
    return auction;
  }

  //Thêm phiên đấu
  public void addAuction(Auction auction) {
    auctionList.put(auction.getAuctionId(), auction);
  }

  //Đặt giá khởi điểm
  public void setupStartPrice(int itemId, int newPrice) {

    Items item = itemService.getItemById(itemId);

    if (item == null) {
      throw new AuctionException(
          ErrorCode.ITEM_NOT_FOUND.name(),
          "Sản phẩm không tồn tại"
      );
    }

    if (newPrice <= 0) {
      throw new AuctionException(
          ErrorCode.INVALID_INPUT.name(),
          "Giá khởi điểm phải > 0"
      );
    }

    item.setStartPrice(newPrice);

    System.out.println("[MANAGER] Set start price: " + newPrice);
  }

  //Đặt lịch đấu giá
  public void scheduleAuction(int auctionId, int itemId, LocalDateTime startTime) {

    Items item = itemService.getItemById(itemId);

    if (item == null) {
      throw new AuctionException(
          ErrorCode.ITEM_NOT_FOUND.name(),
          "Sản phẩm không tồn tại"
      );
    }

    Auction auction = new Auction(auctionId, item);
    auction.setAuctionSchedule(startTime);
    auction.setAuctionStatus("PENDING");

    auctionList.put(auctionId, auction);

    System.out.println("[MANAGER] Scheduled auction " + auctionId);
  }

  //Mở phiên
  public void openAuction(int auctionId) {

    Auction auction = getAuctionOrThrow(auctionId);

    if (!"PENDING".equals(auction.getAuctionStatus())) {
      throw new AuctionException(
          ErrorCode.AUCTION_INVALID_STATE.name(),
          "Chỉ PENDING mới được OPEN"
      );
    }

    auction.setAuctionStatus("OPEN");

    System.out.println("[MANAGER] OPEN auction " + auctionId);
  }

  //Kích hoạt phiên
  public void activateAuction(int auctionId) {

    Auction auction = getAuctionOrThrow(auctionId);

    if (!"OPEN".equals(auction.getAuctionStatus())) {
      throw new AuctionException(
          ErrorCode.AUCTION_INVALID_STATE.name(),
          "Auction chưa OPEN"
      );
    }

    if (LocalDateTime.now().isBefore(auction.getStartTime())) {
      throw new AuctionException(
          ErrorCode.AUCTION_INVALID_STATE.name(),
          "Chưa đến giờ bắt đầu"
      );
    }

    auction.setAuctionStatus("RUNNING");

    System.out.println("[MANAGER] RUNNING auction " + auctionId);
  }


  //Dừng running
  public void stopAutoClose() {
    running = false;
  }

  //Tự động đóng phiên
  public void autoCloseAuction() {

    Thread t = new Thread(() -> {

      while (running) {
        try {
          Thread.sleep(1000);

          for (Auction auction : auctionList.values()) {

            synchronized (auction) {

              if (!"RUNNING".equals(auction.getAuctionStatus())) {
                continue;
              }

              if (LocalDateTime.now().isAfter(auction.getEndTime())) {

                auction.setAuctionStatus("ENDED");

                System.out.println("[AUTO] Auction " + auction.getAuctionId() + " ENDED");

                if (auction.getHighestBidder() != null
                    && !"Chưa có".equals(auction.getHighestBidder())) {

                  auction.setAuctionStatus("SOLD");

                  System.out.println("[AUTO] SOLD to "
                      + auction.getHighestBidder());
                }
              }
            }
          }

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });

    t.setDaemon(true);
    t.start();
  }
  public void clearData() {
    auctionList.clear();
  }
}