package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.AuctionItemDAO;
import com.auction.server.dao.ItemDAO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;

/**
 * Controller cho màn hình Quản lý Phiên Đấu Giá (AuctionManagementView.fxml)
 *
 * Pattern giống hệt MyProductsController:
 *   - 5 tab tương ứng 5 trạng thái phiên
 *   - Cell factory load FXML cho từng dòng ListView
 *   - Tìm kiếm realtime bằng textProperty listener
 *   - switchTab() highlight sidebar
 *
 * Trạng thái phiên trong DB (cột auction_status):
 *   "WAITING_FOR_ADMIN"  → Tab Chờ duyệt
 *   "OPEN"               → Tab Đang mở
 *   "RUNNING"            → Tab Đang diễn ra
 *   "FINISHED"           → Tab Đã kết thúc (gộp FINISHED + PAID + CANCELED)
 *
 * Tab "Tạo phiên mới" → lấy từ bảng items (item đã duyệt, chưa có phiên)
 *   dùng ItemDAO, status trong items = "APPROVED"
 */
public class AuctionManagementController {

  // ══════════════════════════════════════════════════════
  // FXML — SIDEBAR BUTTONS (HBox để highlight border-left)
  // ══════════════════════════════════════════════════════
  @FXML private HBox btnCreate;
  @FXML private HBox btnPending;
  @FXML private HBox btnOpen;
  @FXML private HBox btnRunning;
  @FXML private HBox btnFinished;

  // ══════════════════════════════════════════════════════
  // FXML — CONTENT PANES (ScrollPane ẩn/hiện)
  // ══════════════════════════════════════════════════════
  @FXML private ScrollPane paneCreate;
  @FXML private ScrollPane panePending;
  @FXML private ScrollPane paneOpen;
  @FXML private ScrollPane paneRunning;
  @FXML private ScrollPane paneFinished;

  // ══════════════════════════════════════════════════════
  // FXML — LISTVIEW (mỗi tab 1 list)
  // ══════════════════════════════════════════════════════
  /** Tab Tạo phiên mới — chứa Item (chưa có phiên, đã được duyệt) */
  @FXML private ListView<Item> listCreate;

  /** Tab Chờ duyệt — chứa AuctionItemDAO (phiên + item) */
  @FXML private ListView<AuctionItemDAO> listPending;

  /** Tab Đang mở (OPEN) */
  @FXML private ListView<AuctionItemDAO> listOpen;

  /** Tab Đang diễn ra (RUNNING) */
  @FXML private ListView<AuctionItemDAO> listRunning;

  /** Tab Đã kết thúc (FINISHED / PAID / CANCELED) */
  @FXML private ListView<AuctionItemDAO> listFinished;

  // ══════════════════════════════════════════════════════
  // FXML — LABEL ĐẾM SỐ LƯỢNG & BADGE SIDEBAR
  // ══════════════════════════════════════════════════════
  @FXML private Label lblCreate;
  @FXML private Label lblPending;
  @FXML private Label badgePending;
  @FXML private Label lblOpen;
  @FXML private Label badgeOpen;
  @FXML private Label lblRunning;
  @FXML private Label badgeRunning;
  @FXML private Label lblFinished;

  // ══════════════════════════════════════════════════════
  // FXML — Ô TÌM KIẾM
  // ══════════════════════════════════════════════════════
  @FXML private TextField searchCreate;
  @FXML private TextField searchPending;
  @FXML private TextField searchOpen;
  @FXML private TextField searchRunning;
  @FXML private TextField searchFinished;

  // ══════════════════════════════════════════════════════
  // FXML — EMPTY STATE (VBox hiện khi list rỗng)
  // ══════════════════════════════════════════════════════
  @FXML private VBox emptyCreate;
  @FXML private VBox emptyPending;
  @FXML private VBox emptyOpen;
  @FXML private VBox emptyRunning;
  @FXML private VBox emptyFinished;

  // ══════════════════════════════════════════════════════
  // DAO
  // ══════════════════════════════════════════════════════
  private final AuctionDAO auctionDAO = new AuctionDAO();
  private final ItemDAO    itemDAO    = new ItemDAO();

  /**
   * ID của seller đang đăng nhập.
   * TODO: Sau này thay bằng SessionManager.getCurrentUser().getId()
   *       giống MyProductsController dùng currentSellerId = 11
   */
  private final int currentSellerId = 11;

  // ══════════════════════════════════════════════════════
  // INITIALIZE
  // ══════════════════════════════════════════════════════
  @FXML
  public void initialize() {

    // 1. Gắn cell factory (khuôn FXML cho từng dòng)
    setupCellFactories();

    // 2. Load dữ liệu từ DB lên toàn bộ 5 tab
    loadAllTabs();

    // 3. Gắn sự kiện click sidebar
    btnCreate  .setOnMouseClicked(e -> switchTab(btnCreate,   paneCreate));
    btnPending .setOnMouseClicked(e -> switchTab(btnPending,  panePending));
    btnOpen    .setOnMouseClicked(e -> switchTab(btnOpen,     paneOpen));
    btnRunning .setOnMouseClicked(e -> switchTab(btnRunning,  paneRunning));
    btnFinished.setOnMouseClicked(e -> switchTab(btnFinished, paneFinished));

    // 4. Tìm kiếm realtime — giống MyProductsController
    searchCreate.textProperty().addListener((obs, oldVal, newVal) ->
        filterCreate(newVal));

    searchPending.textProperty().addListener((obs, oldVal, newVal) ->
        filterAndDisplay("WAITING_FOR_ADMIN", newVal,
            listPending, lblPending, badgePending));

    searchOpen.textProperty().addListener((obs, oldVal, newVal) ->
        filterAndDisplay("OPEN", newVal,
            listOpen, lblOpen, badgeOpen));

    searchRunning.textProperty().addListener((obs, oldVal, newVal) ->
        filterAndDisplay("RUNNING", newVal,
            listRunning, lblRunning, badgeRunning));

    searchFinished.textProperty().addListener((obs, oldVal, newVal) ->
        filterFinished(newVal));

    // 5. Mở tab mặc định
    switchTab(btnCreate, paneCreate);
  }

  // ══════════════════════════════════════════════════════
  // LOAD DỮ LIỆU — gọi khi khởi động
  // ══════════════════════════════════════════════════════

  /** Load cả 5 tab một lần */
  private void loadAllTabs() {
    filterCreate("");
    filterAndDisplay("WAITING_FOR_ADMIN", "", listPending,  lblPending,  badgePending);
    filterAndDisplay("OPEN",              "", listOpen,     lblOpen,     badgeOpen);
    filterAndDisplay("RUNNING",           "", listRunning,  lblRunning,  badgeRunning);
    filterFinished("");
  }

  /**
   * Tab "Tạo phiên mới":
   * Lấy các Item của seller đã được admin duyệt (item_status = 'APPROVED')
   * nhưng CHƯA có phiên đấu giá nào (chưa có bản ghi trong bảng auctions).
   *
   * Bạn cần thêm hàm này vào ItemDAO:
   *   getApprovedItemsWithoutAuction(int sellerId, String keyword)
   */
  private void filterCreate(String keyword) {
    List<Item> list = itemDAO.getApprovedItemsWithoutAuction(currentSellerId, keyword);
    listCreate.setItems(FXCollections.observableArrayList(list));

    // Cập nhật label đếm
    if (lblCreate != null) lblCreate.setText("· " + list.size() + " sản phẩm");

    // Hiện/ẩn empty state
    toggleEmpty(emptyCreate, listCreate, list.isEmpty());
  }

  /**
   * Tab Chờ duyệt / OPEN / RUNNING:
   * Dùng chung AuctionDAO.getAuctionsBySellerStatusAndKeyword()
   * — giống pattern filterAndDisplay trong MyProductsController.
   */
  private void filterAndDisplay(String status, String keyword,
                                ListView<AuctionItemDAO> listView,
                                Label lblCount, Label badge) {
    List<AuctionItemDAO> list = auctionDAO.getAuctionsBySellerStatusAndKeyword(
        currentSellerId, status, keyword);

    listView.setItems(FXCollections.observableArrayList(list));

    if (lblCount != null) lblCount.setText("· " + list.size() + " phiên");
    if (badge    != null) badge.setText(String.valueOf(list.size()));

    // Chọn đúng VBox empty tương ứng
    VBox emptyBox = getEmptyBoxForStatus(status);
    toggleEmpty(emptyBox, listView, list.isEmpty());
  }

  /**
   * Tab "Đã kết thúc": gộp 3 trạng thái FINISHED + PAID + CANCELED.
   *
   * Bạn cần thêm hàm này vào AuctionDAO:
   *   getFinishedAuctionsBySeller(int sellerId, String keyword)
   *   — SQL: WHERE seller_id = ? AND auction_status IN ('FINISHED','PAID','CANCELED')
   *          AND i.name ILIKE ?
   */
  private void filterFinished(String keyword) {
    List<AuctionItemDAO> list = auctionDAO.getFinishedAuctionsBySeller(
        currentSellerId, keyword);

    listFinished.setItems(FXCollections.observableArrayList(list));
    if (lblFinished != null) lblFinished.setText("· " + list.size() + " phiên");

    toggleEmpty(emptyFinished, listFinished, list.isEmpty());
  }

  // ══════════════════════════════════════════════════════
  // CELL FACTORIES — load FXML cho từng dòng ListView
  // Giống hệt pattern setupCellFactories() trong MyProductsController
  // ══════════════════════════════════════════════════════
  private void setupCellFactories() {

    // ── TAB 1: Tạo phiên mới ─────────────────────────
    listCreate.setCellFactory(lv -> new ListCell<Item>() {
      private Node graphicContent;
      private AuctionCreateCardController cardController;
      {
        try {
          FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionCreateCell.fxml"));
          graphicContent  = loader.load();
          cardController  = loader.getController();

          // Gắn callback: khi bấm "Tạo phiên" → mở dialog
          cardController.setOnCreateCallback(() ->
              openCreateDialog(getItem()));
        } catch (IOException e) { e.printStackTrace(); }
      }

      @Override
      protected void updateItem(Item item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText(null); setGraphic(null); }
        else {
          cardController.setData(item);
          setGraphic(graphicContent);
        }
        setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
      }
    });

    // ── TAB 2: Chờ duyệt ─────────────────────────────
    listPending.setCellFactory(lv -> new ListCell<AuctionItemDAO>() {
      private Node graphicContent;
      private AuctionPendingCardController cardController;
      {
        try {
          FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionPendingCell.fxml"));
          graphicContent = loader.load();
          cardController = loader.getController();

          // Callback Sửa
          cardController.setOnEditCallback(() ->
              openEditDialog(getItem()));

          // Callback Huỷ
          cardController.setOnCancelCallback(() ->
              cancelAuction(getItem()));
        } catch (IOException e) { e.printStackTrace(); }
      }

      @Override
      protected void updateItem(AuctionItemDAO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText(null); setGraphic(null); }
        else {
          Auction auction = item.getAuction() ;
          cardController.setData(auction );
          setGraphic(graphicContent);
        }
        setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
      }
    });

    // ── TAB 3: Đang mở (OPEN) ─────────────────────────
    listOpen.setCellFactory(lv -> new ListCell<AuctionItemDAO>() {
      private Node graphicContent;
      private AuctionOpenCardController cardController;
      {
        try {
          FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionOpenCell.fxml"));
          graphicContent = loader.load();
          cardController = loader.getController();

          cardController.setOnDetailCallback(() ->
              openDetailView(getItem()));
        } catch (IOException e) { e.printStackTrace(); }
      }

      @Override
      protected void updateItem(AuctionItemDAO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          // Dừng countdown timer khi cell bị recycle
          if (cardController != null) cardController.stopTimer();
          setText(null); setGraphic(null);
        } else {
          Auction auction = item.getAuction() ;
          cardController.setData(auction ) ;
          setGraphic(graphicContent);
        }
        setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
      }
    });

    // ── TAB 4: Đang diễn ra (RUNNING) ────────────────
    listRunning.setCellFactory(lv -> new ListCell<AuctionItemDAO>() {
      private Node graphicContent;
      private AuctionRunningCardController cardController;
      {
        try {
          FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionRunningCell.fxml"));
          graphicContent = loader.load();
          cardController = loader.getController();

          cardController.setOnFollowCallback(() ->
              openDetailView(getItem()));
        } catch (IOException e) { e.printStackTrace(); }
      }

      @Override
      protected void updateItem(AuctionItemDAO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          if (cardController != null) cardController.stopTimer();
          setText(null); setGraphic(null);
        } else {
          Auction auction = item.getAuction() ;
          cardController.setData(auction );
          setGraphic(graphicContent);
        }
        setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
      }
    });

    // ── TAB 5: Đã kết thúc ───────────────────────────
    listFinished.setCellFactory(lv -> new ListCell<AuctionItemDAO>() {
      private Node graphicContent;
      private AuctionFinishedCardController cardController;
      {
        try {
          FXMLLoader loader = new FXMLLoader(
              getClass().getResource("/view/AuctionFinishedCell.fxml"));
          graphicContent = loader.load();
          cardController = loader.getController();

          cardController.setOnRemindCallback(() ->
              remindPayment(getItem()));

          cardController.setOnViewCallback(() ->
              openDetailView(getItem()));
        } catch (IOException e) { e.printStackTrace(); }
      }

      @Override
      protected void updateItem(AuctionItemDAO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText(null); setGraphic(null); }
        else {
          Auction auction = item.getAuction() ;
          cardController.setData(auction );
          setGraphic(graphicContent);
        }
        setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // SWITCH TAB — highlight sidebar, giống MyProductsController
  // ══════════════════════════════════════════════════════
  private void switchTab(HBox activeBtn, ScrollPane activePane) {

    // Ẩn tất cả pane
    paneCreate  .setVisible(false); paneCreate  .setManaged(false);
    panePending .setVisible(false); panePending .setManaged(false);
    paneOpen    .setVisible(false); paneOpen    .setManaged(false);
    paneRunning .setVisible(false); paneRunning .setManaged(false);
    paneFinished.setVisible(false); paneFinished.setManaged(false);

    // Hiện pane được chọn
    activePane.setVisible(true);
    activePane.setManaged(true);

    // Reset style tất cả sidebar buttons — giống MyProductsController
    String defaultStyle =
        "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-border-width: 0 0 0 3;" +
            "-fx-padding: 10 16 10 16;" +
            "-fx-cursor: hand;";

    btnCreate  .setStyle(defaultStyle);
    btnPending .setStyle(defaultStyle);
    btnOpen    .setStyle(defaultStyle);
    btnRunning .setStyle(defaultStyle);
    btnFinished.setStyle(defaultStyle);

    // Highlight nút active
    String activeStyle =
        "-fx-background-color: rgba(215,168,89,0.14);" +
            "-fx-border-color: transparent transparent transparent #D7A859;" +
            "-fx-border-width: 0 0 0 3;" +
            "-fx-padding: 10 16 10 16;" +
            "-fx-cursor: hand;";

    activeBtn.setStyle(activeStyle);

    // Đổi màu label bên trong nút active thành #FFD691
    updateSidebarLabelColor(activeBtn, "#FFD691");

    // Reset màu label các nút không active về #8BA8D4
    for (HBox btn : new HBox[]{btnCreate, btnPending, btnOpen, btnRunning, btnFinished}) {
      if (btn != activeBtn) updateSidebarLabelColor(btn, "#8BA8D4");
    }
  }

  /**
   * Đổi màu chữ của Label đầu tiên bên trong HBox sidebar.
   * (Vì JavaFX không hỗ trợ CSS :hover/:active cho child label qua stylesheet bên ngoài)
   */
  private void updateSidebarLabelColor(HBox btn, String hexColor) {
    btn.getChildren().stream()
        .filter(n -> n instanceof Label)
        .map(n -> (Label) n)
        // Label thứ 2 (index 1) là label text, index 0 là dot
        .skip(1)
        .findFirst()
        .ifPresent(lbl -> lbl.setStyle(
            "-fx-text-fill: " + hexColor + "; -fx-font-size: 12;"));
  }

  // ══════════════════════════════════════════════════════
  // ACTION HANDLERS — gọi khi bấm nút trong cell
  // ══════════════════════════════════════════════════════

  /**
   * Mở dialog tạo phiên đấu giá cho Item được chọn.
   * Load CreateAuctionDialog.fxml vào một cửa sổ popup.
   */
  private void openCreateDialog(Item item) {
    if (item == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/CreateAuctionDialog.fxml"));
      javafx.scene.Parent dialogRoot = loader.load();

      CreateAuctionController dialogCtrl = loader.getController();
      dialogCtrl.setItem(item);  // Truyền item vào dialog để hiển thị preview

      // Gắn callback khi dialog submit thành công → reload tab
      dialogCtrl.setOnSubmitCallback(() -> {
        filterCreate("");                                        // Refresh tab Tạo phiên
        filterAndDisplay("WAITING_FOR_ADMIN", "",               // Refresh tab Chờ duyệt
            listPending, lblPending, badgePending);
      });

      javafx.stage.Stage dialog = new javafx.stage.Stage();
      dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
      dialog.setTitle("Tạo phiên đấu giá — " + item.getName());
      dialog.setScene(new javafx.scene.Scene(dialogRoot));
      dialog.showAndWait();

    } catch (IOException e) {
      e.printStackTrace();
      showAlert("Lỗi", "Không thể mở dialog tạo phiên: " + e.getMessage());
    }
  }

  /**
   * Mở dialog chỉnh sửa phiên đang chờ duyệt.
   * TODO: Tạo EditAuctionDialog.fxml tương tự CreateAuctionDialog.fxml
   */
  private void openEditDialog(AuctionItemDAO item) {
    if (item == null) return;
    showAlert("Sửa phiên",
        "Chức năng sửa phiên: " + item.getItem().getName()
            + "\n(Cần tạo EditAuctionDialog.fxml)");
  }

  /**
   * Huỷ phiên đang chờ duyệt.
   * Cập nhật auction_status = 'CANCELED' trong DB.
   */
  private void cancelAuction(AuctionItemDAO auctionItem) {
    if (auctionItem == null || auctionItem.getAuction() == null) return;

    // Confirm dialog trước khi huỷ
    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Xác nhận huỷ phiên");
    confirm.setHeaderText("Bạn chắc chắn muốn huỷ phiên đấu giá này?");
    confirm.setContentText("Sản phẩm: " + auctionItem.getItem().getName());
    confirm.showAndWait().ifPresent(response -> {
      if (response == ButtonType.OK) {
        int auctionId = auctionItem.getAuction().getAuctionId();
        boolean ok = auctionDAO.updateStatus(auctionId, "CANCELED");
        if (ok) {
          // Refresh lại tab Chờ duyệt
          filterAndDisplay("WAITING_FOR_ADMIN", searchPending.getText(),
              listPending, lblPending, badgePending);
          // Refresh tab Đã kết thúc (vì CANCELED cũng hiện ở đây)
          filterFinished(searchFinished.getText());
        } else {
          showAlert("Lỗi", "Huỷ phiên thất bại. Vui lòng thử lại.");
        }
      }
    });
  }

  /**
   * Mở màn hình chi tiết phiên (AuctionDetailView.fxml).
   * Dùng chung cho OPEN / RUNNING / FINISHED.
   */
  private void openDetailView(AuctionItemDAO auctionItem) {
    if (auctionItem == null) return;
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/AuctionDetailView.fxml"));
      javafx.scene.Parent detailRoot = loader.load();

      AuctionDetailController detailCtrl = loader.getController();
      detailCtrl.setAuctionItem(auctionItem); // Truyền data vào màn chi tiết

      javafx.stage.Stage stage = new javafx.stage.Stage();
      stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
      stage.setTitle("Chi tiết phiên — " + auctionItem.getItem().getName());
      stage.setScene(new javafx.scene.Scene(detailRoot, 1100, 680));
      stage.showAndWait();

    } catch (IOException e) {
      e.printStackTrace();
      showAlert("Lỗi", "Không thể mở chi tiết phiên: " + e.getMessage());
    }
  }

  /**
   * Nhắc người thắng thanh toán (khi status = FINISHED, chưa PAID).
   * TODO: Tích hợp gửi thông báo qua Socket/Observer tới bidder
   */
  private void remindPayment(AuctionItemDAO auctionItem) {
    if (auctionItem == null) return;
    showAlert("Nhắc thanh toán",
        "Đã gửi nhắc nhở thanh toán cho người thắng phiên: "
            + auctionItem.getItem().getName());
    // TODO: Gọi NotificationService.sendReminder(winnerId, auctionId)
  }

  // ══════════════════════════════════════════════════════
  // HELPER METHODS
  // ══════════════════════════════════════════════════════

  /**
   * Hiện/ẩn empty state VBox khi list rỗng.
   * Giống logic emptyAuctioning/emptyPending trong MyProductsView.
   */
  private void toggleEmpty(VBox emptyBox, ListView<?> listView, boolean isEmpty) {
    if (emptyBox == null) return;
    emptyBox.setVisible(isEmpty);
    emptyBox.setManaged(isEmpty);
    listView.setVisible(!isEmpty);
    listView.setManaged(!isEmpty);
  }

  /** Map status string → VBox empty state tương ứng */
  private VBox getEmptyBoxForStatus(String status) {
    return switch (status) {
      case "WAITING_FOR_ADMIN" -> emptyPending;
      case "OPEN"              -> emptyOpen;
      case "RUNNING"           -> emptyRunning;
      default                  -> null;
    };
  }

  /** Alert đơn giản — dùng chung */
  private void showAlert(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

}