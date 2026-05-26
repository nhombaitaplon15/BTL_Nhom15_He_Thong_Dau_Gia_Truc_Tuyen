package com.auction.common.network;

public final class Actions {
  private Actions() {}

  // ── AUCTION ACTIONS ──
  public static final String CREATE_AUCTION         = "CREATE_AUCTION";
  public static final String GET_MY_AUCTIONS        = "GET_MY_AUCTIONS";
  public static final String GET_APPROVED_ITEMS     = "GET_APPROVED_ITEMS";
  public static final String GET_FINISHED_AUCTIONS  = "GET_FINISHED_AUCTIONS";
  public static final String CANCEL_AUCTION         = "CANCEL_AUCTION";
  public static final String GET_AUCTION_DETAIL     = "GET_AUCTION_DETAIL";

  // ── ITEM ACTIONS ──
  public static final String CREATE_ITEM            = "CREATE_ITEM";
  public static final String GET_MY_ITEMS           = "GET_MY_ITEMS";
  public static final String DELETE_ITEM            = "DELETE_ITEM";

  // ── BID ACTIONS ──
  public static final String PLACE_BID              = "PLACE_BID";
  public static final String GET_BID_HISTORY        = "GET_BID_HISTORY";

  // ── AUTH ACTIONS ──
  public static final String LOGIN                  = "LOGIN";
  public static final String REGISTER               = "REGISTER";
  public static final String GET_PROFILE            = "GET_PROFILE";
  public static final String UPDATE_PROFILE         = "UPDATE_PROFILE";
  public static final String CHANGE_PASSWORD        = "CHANGE_PASSWORD";

  // ── WALLET ACTIONS ──
  public static final String GET_BALANCE            = "GET_BALANCE";
  public static final String GET_TRANSACTIONS       = "GET_TRANSACTIONS";
  public static final String TOP_UP                 = "TOP_UP";

  // ── REALTIME (Push từ server về client) ──
  public static final String BID_UPDATE             = "BID_UPDATE";
  public static final String AUCTION_STATUS_CHANGED = "AUCTION_STATUS_CHANGED";
}