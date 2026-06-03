package com.auction.client.controller.seller;

import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.common.network.AuctionItemDTO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MyProductsController {

  @FXML private ScrollPane paneAuctioning;
  @FXML private ScrollPane panePending;
  @FXML private ScrollPane paneSold;

  @FXML private ListView<AuctionItemDTO> listAuctioning;
  @FXML private ListView<AuctionItemDTO> listPending;
  @FXML private ListView<AuctionItemDTO> listSold;

  @FXML private Label lblCountAuc;
  @FXML private Label badgeAuctioning;
  @FXML private Label lblCountPend;
  @FXML private Label badgePending;
  @FXML private Label lblCountSold;

  @FXML private HBox btnAuctioning;
  @FXML private HBox btnPending;
  @FXML private HBox btnSold;

  @FXML private TextField searchAuctioning;
  @FXML private TextField searchPending;
  @FXML private TextField searchSold;

  @FXML private StackPane mainContentArea;
  @FXML private Parent insertItemNode = null;

  private List<AuctionItemDTO> cachedAuctioning = List.of();
  private List<AuctionItemDTO> cachedPending = List.of();
  private List<AuctionItemDTO> cachedSold = List.of();

  private final Consumer<Message> onAuctionsResult = this::handleAuctionsResult;
  private final Consumer<Message> onAuctionApproved = msg -> refreshAll();
  private final Consumer<Message> onAuctionRejected = msg -> refreshAll();
  private final Consumer<Message> onAuctionSold = msg -> refreshAll();

  private static MyProductsController instance;
  public static boolean requestOpenInsertItem = false;

  int myId = ClientSession.getInstance().getUserId();

  @FXML
  public void initialize() {
    instance = this;

    setupCellFactories();
    registerNetworkHandlers();

    btnAuctioning.setOnMouseClicked(e -> switchTab(btnAuctioning, paneAuctioning));
    btnPending.setOnMouseClicked(e -> switchTab(btnPending, panePending));
    btnSold.setOnMouseClicked(e -> switchTab(btnSold, paneSold));
    switchTab(btnAuctioning, paneAuctioning);

    searchAuctioning.textProperty().addListener((obs, o, kw) ->
        filterLocal(cachedAuctioning, kw, listAuctioning, lblCountAuc, badgeAuctioning));
    searchPending.textProperty().addListener((obs, o, kw) ->
        filterLocal(cachedPending, kw, listPending, lblCountPend, badgePending));
    searchSold.textProperty().addListener((obs, o, kw) ->
        filterLocal(cachedSold, kw, listSold, lblCountSold, null));

    refreshAll();

    Platform.runLater(() -> {
      if (requestOpenInsertItem) {
        showInsertItemView(null);
        requestOpenInsertItem = false;
      }
    });
  }

  public static void openInsertFormDirectly() {
    if (instance != null) {
      Platform.runLater(() -> instance.showInsertItemView(null));
    } else {
      requestOpenInsertItem = true;
    }
  }

  private void registerNetworkHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.SELLER_AUCTIONS_RESULT, onAuctionsResult);
    r.register(ResponseCode.SELLER_AUCTION_APPROVED, onAuctionApproved);
    r.register(ResponseCode.SELLER_AUCTION_REJECTED, onAuctionRejected);
    r.register(ResponseCode.SELLER_AUCTION_SOLD, onAuctionSold);
  }

  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.SELLER_AUCTIONS_RESULT);
    r.unregister(ResponseCode.SELLER_AUCTION_APPROVED);
    r.unregister(ResponseCode.SELLER_AUCTION_REJECTED);
    r.unregister(ResponseCode.SELLER_AUCTION_SOLD);
  }

  private void refreshAll() {
    // TỐI ƯU: Đẩy tác vụ Socket xuống luồng nền
    CompletableFuture.runAsync(() -> {
      SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_AUCTIONS, myId);
    });
  }

  @SuppressWarnings("unchecked")
  private void handleAuctionsResult(Message msg) {
    if (!(msg.getPayload() instanceof List<?> rawList)) return;
    List<AuctionItemDTO> all = (List<AuctionItemDTO>) rawList;

    cachedAuctioning = all.stream()
        .filter(a -> "RUNNING".equalsIgnoreCase(a.getAuction().getAuctionStatus()))
        .toList();
    cachedPending = all.stream()
        .filter(a -> "WAITING_FOR_ADMIN".equalsIgnoreCase(a.getAuction().getAuctionStatus()))
        .toList();
    cachedSold = all.stream()
        .filter(a -> {
          String s = a.getAuction().getAuctionStatus();
          return "SOLD".equalsIgnoreCase(s) || "FINISHED".equalsIgnoreCase(s)
              || "PAID".equalsIgnoreCase(s) || "CANCELED".equalsIgnoreCase(s)
              || "REJECTED".equalsIgnoreCase(s);
        })
        .toList();

    Platform.runLater(() -> {
      String kw1 = searchAuctioning.getText();
      String kw2 = searchPending.getText();
      String kw3 = searchSold.getText();
      filterLocal(cachedAuctioning, kw1, listAuctioning, lblCountAuc, badgeAuctioning);
      filterLocal(cachedPending, kw2, listPending, lblCountPend, badgePending);
      filterLocal(cachedSold, kw3, listSold, lblCountSold, null);
    });
  }

  private void filterLocal(List<AuctionItemDTO> source, String keyword,
                           ListView<AuctionItemDTO> listView, Label lblCount, Label badge) {
    List<AuctionItemDTO> filtered = (keyword == null || keyword.isBlank())
        ? source
        : source.stream()
        .filter(a -> a.getItem().getName().toLowerCase()
            .contains(keyword.toLowerCase()))
        .toList();

    listView.setItems(FXCollections.observableArrayList(filtered));
    if (lblCount != null) lblCount.setText("· " + filtered.size() + " sản phẩm");
    if (badge != null) badge.setText(String.valueOf(filtered.size()));
  }

  private void setupCellFactories() {
    setupCellFactory(listAuctioning, ProductsCardController.CardType.AUCTIONING,
        "/view/view/seller/ProductsCardView.fxml");
    setupCellFactory(listPending, ProductsCardController.CardType.PENDING,
        "/view/view/seller/ProductsCardView.fxml");
    setupCellFactory(listSold, ProductsCardController.CardType.SOLD,
        "/view/view/seller/ProductsCardView.fxml");
  }

  private void setupCellFactory(ListView<AuctionItemDTO> listView,
                                ProductsCardController.CardType type, String fxmlPath) {
    listView.setCellFactory(lv -> new ListCell<>() {
      private javafx.scene.Node graphic;
      private ProductsCardController controller;
      {
        try {
          FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
          graphic = loader.load();
          controller = loader.getController();

          if (controller != null) {
            controller.setOnDetailCallback(ignored -> {
              AuctionItemDTO currentItem = getItem();
              if (currentItem != null) {
                if (type == ProductsCardController.CardType.PENDING) {
                  openEditDialog(currentItem);
                } else {
                  openDetailView(currentItem);
                }
              }
            });

            controller.setOnCancelCallback(ignored -> {
              AuctionItemDTO currentItem = getItem();
              if (currentItem != null && type == ProductsCardController.CardType.PENDING) {
                openCancelDialog(currentItem);
              }
            });
          }
        } catch (Exception e) {
          System.err.println("LỖI LOAD FXML LISTVIEW: " + fxmlPath);
          e.printStackTrace();
        }
      }

      @Override
      protected void updateItem(AuctionItemDTO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null); setGraphic(null);
        } else {
          if (controller != null) {
            try {
              controller.setData(item, type);
              setGraphic(graphic);
            } catch (Exception e) {
              System.err.println("LỖI KHI SET DATA: " + item.getItem().getId());
              e.printStackTrace();
              setText("Lỗi hiển thị dữ liệu!");
            }
          } else {
            setText("Lỗi: Không tìm thấy file FXML!");
          }
        }
        setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
      }
    });
  }

  private void openEditDialog(AuctionItemDTO item) {
    if (item == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/view/seller/EditAuctionDialog.fxml"));
      Parent root = loader.load();

      EditAuctionController ctrl = loader.getController();
      ctrl.setAuctionData(item);
      ctrl.setOnSuccessCallback(() -> refreshAll());

      Stage dialog = new Stage();
      dialog.initModality(Modality.APPLICATION_MODAL);
      dialog.setTitle("Sửa phiên đấu giá — " + item.getItem().getName());
      dialog.setScene(new Scene(root));
      dialog.showAndWait();

    } catch (IOException e) {
      System.err.println("Không thể mở dialog sửa phiên: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void openDetailView(AuctionItemDTO auctionItem) {
    if (auctionItem == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/view/seller/AuctionDetailView.fxml"));
      Parent root = loader.load();

      AuctionDetailController ctrl = loader.getController();
      ctrl.setAuctionItem(auctionItem);

      Stage stage = new Stage();
      stage.initModality(Modality.APPLICATION_MODAL);
      stage.setTitle("Chi tiết phiên — " + auctionItem.getItem().getName());
      stage.setScene(new Scene(root, 1100, 680));
      stage.showAndWait();

      refreshAll();

    } catch (IOException e) {
      System.err.println("Không thể mở chi tiết phiên: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void openCancelDialog(AuctionItemDTO item) {
    if (item == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/view/seller/CancelAuctionDialog.fxml"));
      Parent root = loader.load();

      CancelAuctionController ctrl = loader.getController();
      ctrl.setData(item, this::refreshAll);

      Stage dialog = new Stage();
      dialog.initModality(Modality.APPLICATION_MODAL);
      dialog.setTitle("Xác nhận huỷ phiên");
      dialog.setScene(new Scene(root));
      dialog.showAndWait();

    } catch (IOException e) {
      System.err.println("Không thể mở dialog huỷ phiên: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void switchTab(HBox activeBtn, ScrollPane activePane) {
    paneAuctioning.setVisible(false); paneAuctioning.setManaged(false);
    panePending.setVisible(false); panePending.setManaged(false);
    paneSold.setVisible(false); paneSold.setManaged(false);
    if (insertItemNode != null) {
      insertItemNode.setVisible(false); insertItemNode.setManaged(false);
    }

    activePane.setVisible(true); activePane.setManaged(true);

    String def = "-fx-background-color:transparent;-fx-border-color:transparent;"
        + "-fx-border-width:0 0 0 3;-fx-padding:11 18 11 18;-fx-cursor:hand;";
    String active = "-fx-background-color:rgba(215,168,89,0.14);"
        + "-fx-border-color:transparent transparent transparent #D7A859;"
        + "-fx-border-width:0 0 0 3;-fx-padding:11 18 11 18;-fx-cursor:hand;";
    btnAuctioning.setStyle(def); btnPending.setStyle(def); btnSold.setStyle(def);
    activeBtn.setStyle(active);
  }

  @FXML
  void showInsertItemView(ActionEvent event) {
    try {
      if (insertItemNode == null) {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/view/view/seller/InsertItemView.fxml"));
        insertItemNode = loader.load();
        mainContentArea.getChildren().add(insertItemNode);
      }
      paneAuctioning.setVisible(false); paneAuctioning.setManaged(false);
      panePending.setVisible(false); panePending.setManaged(false);
      paneSold.setVisible(false); paneSold.setManaged(false);

      insertItemNode.setVisible(true);
      insertItemNode.setManaged(true);
      insertItemNode.toFront();

    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Không thể tải InsertItemView.fxml");
    }
  }
}