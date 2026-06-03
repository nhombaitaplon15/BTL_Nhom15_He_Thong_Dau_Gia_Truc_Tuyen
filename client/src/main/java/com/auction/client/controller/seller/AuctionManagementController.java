package com.auction.client.controller.seller;

import com.auction.client.core.ClientSession;
import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Item;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.common.network.AuctionItemDTO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AuctionManagementController {

  @FXML private HBox btnCreate, btnPending, btnOpen, btnRunning, btnFinished;
  @FXML private ScrollPane paneCreate, panePending, paneOpen, paneRunning, paneFinished;
  @FXML private ListView<Item> listCreate;
  @FXML private ListView<AuctionItemDTO> listPending, listOpen, listRunning, listFinished;
  @FXML private Label lblCreate, lblPending, badgePending, lblOpen, badgeOpen, lblRunning, badgeRunning, lblFinished;
  @FXML private TextField searchCreate, searchPending, searchOpen, searchRunning, searchFinished;
  @FXML private VBox emptyCreate, emptyPending, emptyOpen, emptyRunning, emptyFinished;

  private List<Item> cachedItems = List.of();
  private List<AuctionItemDTO> cachedAuctions = List.of();

  private boolean isItemsLoaded = false;
  private boolean isAuctionsLoaded = false;

  private final Consumer<Message> onItemsResult = this::handleItemsResult;
  private final Consumer<Message> onAuctionsResult = this::handleAuctionsResult;
  private final Consumer<Message> onCancelSuccess = this::handleCancelSuccess;
  private final Consumer<Message> onCancelFailed = msg -> Platform.runLater(() -> AlertUtils.error("Huỷ phiên thất bại: " + msg.getMessage()));
  private final Consumer<Message> onCreateSuccess = this::handleAuctionCreated;
  private final Consumer<Message> onCreateFailed = msg -> Platform.runLater(() -> AlertUtils.error("Tạo phiên thất bại: " + msg.getMessage()));
  private final Consumer<Message> onAuctionApproved = msg -> refreshAllData();
  private final Consumer<Message> onAuctionRejected = msg -> refreshAllData();
  private final Consumer<Message> onAuctionSold = msg -> refreshAllData();

  int myId = ClientSession.getInstance().getUserId();

  @FXML
  public void initialize() {
    setupCellFactories();
    registerNetworkHandlers();

    btnCreate.setOnMouseClicked(e -> switchTab(btnCreate, paneCreate));
    btnPending.setOnMouseClicked(e -> switchTab(btnPending, panePending));
    btnOpen.setOnMouseClicked(e -> switchTab(btnOpen, paneOpen));
    btnRunning.setOnMouseClicked(e -> switchTab(btnRunning, paneRunning));
    btnFinished.setOnMouseClicked(e -> switchTab(btnFinished, paneFinished));

    searchCreate.textProperty().addListener((obs, o, kw) -> filterCreateLocal(kw));
    searchPending.textProperty().addListener((obs, o, kw) -> filterAuctionsLocal("WAITING_FOR_ADMIN", kw, listPending, lblPending, badgePending, emptyPending));
    searchOpen.textProperty().addListener((obs, o, kw) -> filterAuctionsLocal("OPEN", kw, listOpen, lblOpen, badgeOpen, emptyOpen));
    searchRunning.textProperty().addListener((obs, o, kw) -> filterAuctionsLocal("RUNNING", kw, listRunning, lblRunning, badgeRunning, emptyRunning));
    searchFinished.textProperty().addListener((obs, o, kw) -> filterFinishedLocal(kw));

    switchTab(btnCreate, paneCreate);
    refreshAllData();
  }

  private void registerNetworkHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.SELLER_ITEMS_RESULT, onItemsResult);
    r.register(ResponseCode.SELLER_AUCTIONS_RESULT, onAuctionsResult);
    r.register(ResponseCode.SELLER_CANCEL_SUCCESS, onCancelSuccess);
    r.register(ResponseCode.SELLER_CANCEL_FAILED, onCancelFailed);
    r.register(ResponseCode.SELLER_AUCTION_CREATED, onCreateSuccess);
    r.register(ResponseCode.SELLER_AUCTION_CREATE_FAILED, onCreateFailed);
    r.register(ResponseCode.SELLER_AUCTION_APPROVED, onAuctionApproved);
    r.register(ResponseCode.SELLER_AUCTION_REJECTED, onAuctionRejected);
    r.register(ResponseCode.SELLER_AUCTION_SOLD, onAuctionSold);
  }

  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.SELLER_ITEMS_RESULT);
    r.unregister(ResponseCode.SELLER_AUCTIONS_RESULT);
    r.unregister(ResponseCode.SELLER_CANCEL_SUCCESS);
    r.unregister(ResponseCode.SELLER_CANCEL_FAILED);
    r.unregister(ResponseCode.SELLER_AUCTION_CREATED);
    r.unregister(ResponseCode.SELLER_AUCTION_CREATE_FAILED);
    r.unregister(ResponseCode.SELLER_AUCTION_APPROVED);
    r.unregister(ResponseCode.SELLER_AUCTION_REJECTED);
    r.unregister(ResponseCode.SELLER_AUCTION_SOLD);
  }

  private void refreshAllData() {
    isItemsLoaded = false;
    isAuctionsLoaded = false;
    // ĐÃ SỬA: Đẩy tác vụ mạng xuống luồng nền
    CompletableFuture.runAsync(() -> {
      SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_ITEMS, myId);
      SocketClient.getInstance().sendRequest(RequestCode.SELLER_GET_MY_AUCTIONS, myId);
    });
  }

  @SuppressWarnings("unchecked")
  private void handleItemsResult(Message msg) {
    if (msg.getPayload() instanceof List<?> list) {
      cachedItems = (List<Item>) list;
      isItemsLoaded = true;
      if (isAuctionsLoaded) {
        Platform.runLater(() -> filterCreateLocal(searchCreate.getText()));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void handleAuctionsResult(Message msg) {
    if (!(msg.getPayload() instanceof List<?> list)) return;
    cachedAuctions = (List<AuctionItemDTO>) list;
    isAuctionsLoaded = true;
    Platform.runLater(() -> {
      filterAuctionsLocal("WAITING_FOR_ADMIN", searchPending.getText(), listPending, lblPending, badgePending, emptyPending);
      filterAuctionsLocal("OPEN", searchOpen.getText(), listOpen, lblOpen, badgeOpen, emptyOpen);
      filterAuctionsLocal("RUNNING", searchRunning.getText(), listRunning, lblRunning, badgeRunning, emptyRunning);
      filterFinishedLocal(searchFinished.getText());

      if (isItemsLoaded) {
        filterCreateLocal(searchCreate.getText());
      }
    });
  }

  private void handleCancelSuccess(Message msg) {
    Platform.runLater(this::refreshAllData);
  }

  private void handleAuctionCreated(Message msg) {
    Platform.runLater(this::refreshAllData);
  }

  private void filterCreateLocal(String keyword) {
    List<Integer> activeItemIds = cachedAuctions.stream()
        .filter(a -> {
          String status = a.getAuction().getAuctionStatus();
          return "WAITING_FOR_ADMIN".equalsIgnoreCase(status)
              || "OPEN".equalsIgnoreCase(status)
              || "RUNNING".equalsIgnoreCase(status);
        })
        .map(a -> a.getAuction().getItemId())
        .toList();

    List<Item> filtered = cachedItems.stream()
        .filter(i -> !activeItemIds.contains(i.getId()))
        .filter(i -> keyword == null || keyword.isBlank()
            || i.getName().toLowerCase().contains(keyword.toLowerCase()))
        .toList();

    listCreate.setItems(FXCollections.observableArrayList(filtered));
    if (lblCreate != null) lblCreate.setText("· " + filtered.size() + " sản phẩm");
    toggleEmpty(emptyCreate, listCreate, filtered.isEmpty());
  }

  private void filterAuctionsLocal(String status, String keyword,
                                   ListView<AuctionItemDTO> listView,
                                   Label lblCount, Label badge, VBox emptyBox) {
    List<AuctionItemDTO> filtered = cachedAuctions.stream()
        .filter(a -> status.equalsIgnoreCase(a.getAuction().getAuctionStatus()))
        .filter(a -> keyword == null || keyword.isBlank()
            || a.getItem().getName().toLowerCase().contains(keyword.toLowerCase()))
        .toList();

    listView.setItems(FXCollections.observableArrayList(filtered));
    if (lblCount != null) lblCount.setText("· " + filtered.size() + " phiên");
    if (badge != null) badge.setText(String.valueOf(filtered.size()));
    toggleEmpty(emptyBox, listView, filtered.isEmpty());
  }

  private void filterFinishedLocal(String keyword) {
    List<AuctionItemDTO> filtered = cachedAuctions.stream()
        .filter(a -> {
          String s = a.getAuction().getAuctionStatus();
          return "SOLD".equalsIgnoreCase(s) || "FINISHED".equalsIgnoreCase(s)
              || "PAID".equalsIgnoreCase(s) || "CANCELED".equalsIgnoreCase(s)
              || "REJECTED".equalsIgnoreCase(s);
        })
        .filter(a -> keyword == null || keyword.isBlank()
            || a.getItem().getName().toLowerCase().contains(keyword.toLowerCase()))
        .toList();

    listFinished.setItems(FXCollections.observableArrayList(filtered));
    if (lblFinished != null) lblFinished.setText("· " + filtered.size() + " phiên");
    toggleEmpty(emptyFinished, listFinished, filtered.isEmpty());
  }

  private void setupCellFactories() {
    listCreate.setCellFactory(lv -> new ListCell<>() {
      private Node graphic;
      private AuctionCreateCardController ctrl;
      {
        try {
          FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/view/seller/AuctionCreateCell.fxml"));
          graphic = loader.load();
          ctrl = loader.getController();
          ctrl.setOnCreateCallback(() -> openCreateDialog(getItem()));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      @Override
      protected void updateItem(Item item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
        } else {
          ctrl.setData(item);
          setGraphic(graphic);
        }
        setStyle("-fx-background-color:transparent;-fx-padding:4 0 4 0;");
      }
    });

    listPending.setCellFactory(lv -> buildAuctionCell(
        "/view/view/seller/AuctionPendingCell.fxml",
        AuctionPendingCardController.class,
        (ctrl, cell) -> {
          ctrl.setOnEditCallback(() -> openEditDialog(cell.getItem()));
          ctrl.setOnCancelCallback(() -> cancelAuction(cell.getItem()));
        },
        (ctrl, dto) -> ctrl.setData(dto), null
    ));

    listOpen.setCellFactory(lv -> buildAuctionCell(
        "/view/view/seller/AuctionOpenCell.fxml",
        AuctionOpenCardController.class,
        (ctrl, cell) -> ctrl.setOnDetailCallback(() -> openDetailView(cell.getItem())),
        (ctrl, dto) -> ctrl.setData(dto),
        AuctionOpenCardController::stopTimer
    ));

    listRunning.setCellFactory(lv -> buildAuctionCell(
        "/view/view/seller/AuctionRunningCell.fxml",
        AuctionRunningCardController.class,
        (ctrl, cell) -> ctrl.setOnFollowCallback(() -> openDetailView(cell.getItem())),
        (ctrl, dto) -> ctrl.setData(dto),
        AuctionRunningCardController::stopTimer
    ));

    listFinished.setCellFactory(lv -> buildAuctionCell(
        "/view/view/seller/AuctionFinishedCell.fxml",
        AuctionFinishedCardController.class,
        (ctrl, cell) -> {
          ctrl.setOnRemindCallback(() -> remindPayment(cell.getItem()));
          ctrl.setOnViewCallback(() -> openDetailView(cell.getItem()));
        },
        (ctrl, dto) -> ctrl.setData(dto), null
    ));
  }

  @SuppressWarnings("unchecked")
  private <C> ListCell<AuctionItemDTO> buildAuctionCell(
      String fxmlPath, Class<C> ctrlClass,
      BiConsumer<C, ListCell<AuctionItemDTO>> callbackSetup,
      BiConsumer<C, AuctionItemDTO> dataSetup,
      Consumer<C> stopTimerFn) {

    return new ListCell<>() {
      private Node graphic;
      private C ctrl;

      {
        try {
          FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
          graphic = loader.load();
          ctrl = loader.getController();
          callbackSetup.accept(ctrl, this);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      @Override
      protected void updateItem(AuctionItemDTO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          if (stopTimerFn != null && ctrl != null) stopTimerFn.accept(ctrl);
          setText(null);
          setGraphic(null);
        } else {
          dataSetup.accept(ctrl, item);
          setGraphic(graphic);
        }
        setStyle("-fx-background-color:transparent;-fx-padding:4 0 4 0;");
      }
    };
  }

  private void switchTab(HBox activeBtn, ScrollPane activePane) {
    for (ScrollPane p : new ScrollPane[]{paneCreate, panePending, paneOpen, paneRunning, paneFinished}) {
      p.setVisible(false);
      p.setManaged(false);
    }
    activePane.setVisible(true);
    activePane.setManaged(true);

    String def = "-fx-background-color:transparent;-fx-border-color:transparent;"
        + "-fx-border-width:0 0 0 3;-fx-padding:10 16 10 16;-fx-cursor:hand;";
    String active = "-fx-background-color:rgba(215,168,89,0.14);"
        + "-fx-border-color:transparent transparent transparent #D7A859;"
        + "-fx-border-width:0 0 0 3;-fx-padding:10 16 10 16;-fx-cursor:hand;";

    for (HBox btn : new HBox[]{btnCreate, btnPending, btnOpen, btnRunning, btnFinished}) {
      btn.setStyle(def);
      updateSidebarLabelColor(btn, "#8BA8D4");
    }
    activeBtn.setStyle(active);
    updateSidebarLabelColor(activeBtn, "#FFD691");
  }

  private void updateSidebarLabelColor(HBox btn, String hexColor) {
    btn.getChildren().stream()
        .filter(n -> n instanceof Label)
        .map(n -> (Label) n)
        .skip(1)
        .findFirst()
        .ifPresent(lbl -> lbl.setStyle("-fx-text-fill:" + hexColor + ";-fx-font-size:12;"));
  }

  private void openCreateDialog(Item item) {
    if (item == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/view/seller/CreateAuctionDialog.fxml"));
      javafx.scene.Parent root = loader.load();
      CreateAuctionController ctrl = loader.getController();
      ctrl.setItem(item);

      javafx.stage.Stage dialog = new javafx.stage.Stage();
      dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
      dialog.setTitle("Tạo phiên đấu giá — " + item.getName());
      dialog.setScene(new javafx.scene.Scene(root));

      dialog.showAndWait();
      refreshAllData();

    } catch (IOException e) {
      AlertUtils.error("Không thể mở dialog tạo phiên: " + e.getMessage());
    }
  }

  private void openEditDialog(AuctionItemDTO item) {
    if (item == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/view/seller/EditAuctionDialog.fxml"));
      javafx.scene.Parent root = loader.load();

      EditAuctionController ctrl = loader.getController();
      ctrl.setAuctionData(item);
      ctrl.setOnSuccessCallback(this::refreshAllData);

      javafx.stage.Stage dialog = new javafx.stage.Stage();
      dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
      dialog.setTitle("Sửa phiên đấu giá — " + item.getItem().getName());
      dialog.setScene(new javafx.scene.Scene(root));
      dialog.showAndWait();

    } catch (IOException e) {
      AlertUtils.error("Không thể mở dialog sửa phiên: " + e.getMessage());
    }
  }

  private void cancelAuction(AuctionItemDTO auctionItem) {
    if (auctionItem == null || auctionItem.getAuction() == null) return;

    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/view/seller/CancelAuctionDialog.fxml"));
      javafx.scene.Parent root = loader.load();

      CancelAuctionController ctrl = loader.getController();
      ctrl.setData(auctionItem, this::refreshAllData);

      javafx.stage.Stage dialog = new javafx.stage.Stage();
      dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
      dialog.setTitle("Xác nhận huỷ phiên");
      dialog.setScene(new javafx.scene.Scene(root));
      dialog.showAndWait();

    } catch (IOException e) {
      AlertUtils.error("Không thể mở dialog huỷ phiên: " + e.getMessage());
    }
  }

  private void openDetailView(AuctionItemDTO auctionItem) {
    if (auctionItem == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/view/seller/AuctionDetailView.fxml"));
      javafx.scene.Parent root = loader.load();
      AuctionDetailController ctrl = loader.getController();
      ctrl.setAuctionItem(auctionItem);

      javafx.stage.Stage stage = new javafx.stage.Stage();
      stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
      stage.setTitle("Chi tiết phiên — " + auctionItem.getItem().getName());
      stage.setScene(new javafx.scene.Scene(root, 1100, 680));
      stage.showAndWait();
    } catch (IOException e) {
      AlertUtils.error("Không thể mở chi tiết phiên: " + e.getMessage());
    }
  }

  private void remindPayment(AuctionItemDTO auctionItem) {
    if (auctionItem == null) return;
    AlertUtils.success("Đã gửi nhắc thanh toán cho người thắng phiên: "
        + auctionItem.getItem().getName());
  }

  private void toggleEmpty(VBox emptyBox, ListView<?> listView, boolean isEmpty) {
    if (emptyBox == null) return;
    emptyBox.setVisible(isEmpty);
    emptyBox.setManaged(isEmpty);
    listView.setVisible(!isEmpty);
    listView.setManaged(!isEmpty);
  }
}