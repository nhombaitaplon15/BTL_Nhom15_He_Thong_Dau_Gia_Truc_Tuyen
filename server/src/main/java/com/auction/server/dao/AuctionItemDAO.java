package com.auction.server.dao;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;

import java.io.Serializable;

public class AuctionItemDAO implements Serializable {
  private static final long serialVersionUID = 1L; // Bắt buộc phải có để truyền qua mạng
  private Item item;
  private Auction auction;

  public AuctionItemDAO(Item item, Auction auction) {
    this.item = item;
    this.auction = auction;
  }

  public Item getItem() { return item; }
  public Auction getAuction() { return auction; }
  public void setData(AuctionItemDAO auctionItemDAO ) {}
}
