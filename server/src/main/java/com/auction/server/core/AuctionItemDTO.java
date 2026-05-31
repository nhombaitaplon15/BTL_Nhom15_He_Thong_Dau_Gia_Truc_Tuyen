package com.auction.server.core;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;

import java.io.Serializable;

public class AuctionItemDTO implements Serializable {
  private static final long serialVersionUID = 1L; // Bắt buộc phải có để truyền qua mạng
  private Item item;
  private Auction auction;

  public AuctionItemDTO(Item item, Auction auction) {
    this.item = item;
    this.auction = auction;
  }

  public Item getItem() { return item; }
  public Auction getAuction() { return auction; }
  public void setData(AuctionItemDTO auctionItemDAO ) {}
}
