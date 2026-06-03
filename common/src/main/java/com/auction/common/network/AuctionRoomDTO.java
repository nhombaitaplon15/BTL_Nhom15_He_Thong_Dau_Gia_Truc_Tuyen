package com.auction.common.network;

import com.auction.common.model.Auction;
import com.auction.common.model.BiddingHistory;
import com.auction.common.model.Item;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class AuctionRoomDTO implements Serializable {
  private static final long serialVersionUID = 1L;

  private Auction auction;
  private Item item;
  private List<BiddingHistory> historyList;
  private Map<Integer, String> usernameMap;
  private double userBalance;

  public AuctionRoomDTO() {}

  public AuctionRoomDTO(Auction auction, Item item, List<BiddingHistory> historyList, Map<Integer, String> usernameMap, double userBalance) {
    this.auction = auction;
    this.item = item;
    this.historyList = historyList;
    this.usernameMap = usernameMap;
    this.userBalance = userBalance;
  }

  public Auction getAuction() { return auction; }
  public void setAuction(Auction auction) { this.auction = auction; }

  public Item getItem() { return item; }
  public void setItem(Item item) { this.item = item; }

  public List<BiddingHistory> getHistoryList() { return historyList; }
  public void setHistoryList(List<BiddingHistory> historyList) { this.historyList = historyList; }

  public Map<Integer, String> getUsernameMap() { return usernameMap; }
  public void setUsernameMap(Map<Integer, String> usernameMap) { this.usernameMap = usernameMap; }

  public double getUserBalance() { return userBalance; }
  public void setUserBalance(double userBalance) { this.userBalance = userBalance; }
}