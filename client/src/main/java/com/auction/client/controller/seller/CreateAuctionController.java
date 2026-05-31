package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Item;
import com.auction.common.network.CreateAuctionDTO; // SỬA: Đổi import từ Auction sang CreateAuctionDTO
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform; // THÊM: Import để an toàn khi cập nhật UI
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * CreateAuctionController — Refactored với Networking.
 */
public class CreateAuctionController {

  // ── PRODUCT PREVIEW ──
  @FXML private ImageView imgItemPreview;
  @FXML private Label     lblItemName;
  @FXML private Label     lblItemMeta;
  @FXML private Label     lblStartingPrice;

  // ── FORM FIELDS ──
  @FXML private DatePicker       dtpStartTime;
  @FXML private DatePicker       dtpEndTime;
  @FXML private TextField        txtBuyNowPrice;
  @FXML private TextField        txtNote;

  // ── BUTTONS ──
  @FXML private Button btnConfirm;
  @FXML private Button btnCancel;

  // ── STATE ──
  private Item     currentItem;
  private Runnable onSubmitCallback;

  // ── Handler references ──
  private final Consumer<Message> onCreated     = this::handleAuctionCreated;
  private final Consumer<Message> onCreateFailed = this::handleAuctionCreateFailed;

  @FXML
  public void initialize() {
    registerNetworkHandlers();
    dtpStartTime.setValue(LocalDate.now().plusDays(0));
    dtpEndTime  .setValue(LocalDate.now().plusDays(2));

    addNumberOnlyListener(txtBuyNowPrice);
  }

  // ════════════════════════════════════════
  // ĐĂNG KÝ / HUỶ HANDLER
  // ════════════════════════════════════════

  private void registerNetworkHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.register(ResponseCode.SELLER_AUCTION_CREATED,      onCreated);
    r.register(ResponseCode.SELLER_AUCTION_CREATE_FAILED, onCreateFailed);
  }

  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.SELLER_AUCTION_CREATED);
    r.unregister(ResponseCode.SELLER_AUCTION_CREATE_FAILED);
  }

  // ════════════════════════════════════════
  // SETTER — gọi từ AuctionManagementController
  // ════════════════════════════════════════

  public void setItem(Item item) {
    this.currentItem = item;
    lblItemName    .setText(item.getName());
    lblItemMeta    .setText(item.getItemType() + " · Giá khởi điểm đã đặt");
    lblStartingPrice.setText(formatMoney(item.getStartingPrice()) + " UETệ");
    loadImage(item.getImgItem());
  }

  public void setOnSubmitCallback(Runnable callback) {
    this.onSubmitCallback = callback;
  }

  // ════════════════════════════════════════
  // XỬ LÝ RESPONSE TỪ SERVER
  // ════════════════════════════════════════

  private void handleAuctionCreated(Message msg) {
    // THÊM: Bọc Platform.runLater để tránh crash luồng JavaFX
    Platform.runLater(() -> {
      resetConfirmButton();
      cleanupHandlers();
      showSuccess();
      if (onSubmitCallback != null) onSubmitCallback.run();
      closeDialog();
    });
  }

  private void handleAuctionCreateFailed(Message msg) {
    // THÊM: Bọc Platform.runLater
    Platform.runLater(() -> {
      resetConfirmButton();
      String reason = msg.getMessage() != null ? msg.getMessage() : "Vui lòng thử lại.";
      showError("Tạo phiên thất bại: " + reason);
    });
  }

  // ════════════════════════════════════════
  // ACTION HANDLERS
  // ════════════════════════════════════════

  @FXML
  private void onConfirm() {
    if (!validateForm()) return;

    // Disable nút tránh double-click trong lúc chờ server
    btnConfirm.setDisable(true);
    btnConfirm.setText("Đang gửi...");

    // Build DTO object từ form
    CreateAuctionDTO dto = buildAuctionDTO();
    System.out.println("DEBUG CLIENT - Chuẩn bị gửi ItemID: " + dto.getItemId());
    // Gửi DTO lên server
    SocketClient.getInstance().sendRequest(RequestCode.SELLER_CREATE_AUCTION, dto);
  }

  @FXML
  private void onCancel() {
    cleanupHandlers();
    closeDialog();
  }

  // ════════════════════════════════════════
  // BUILD DTO OBJECT
  // ════════════════════════════════════════

  // SỬA: Thay hàm buildAuction() thành buildAuctionDTO() trả về CreateAuctionDTO
  private CreateAuctionDTO buildAuctionDTO() {
    LocalDateTime startTime = dtpStartTime.getValue().atTime(LocalTime.of(8, 0));
    LocalDateTime endTime   = dtpEndTime  .getValue().atTime(LocalTime.of(20, 0));

    CreateAuctionDTO dto = new CreateAuctionDTO();
    dto.setItemId(currentItem.getId());
    dto.setSellerId(currentItem.getSellerId());
    dto.setStartingPrice(currentItem.getStartingPrice());
    dto.setStartTime(startTime);
    dto.setEndTime(endTime);

    return dto;
  }

  // ════════════════════════════════════════
  // VALIDATE
  // ════════════════════════════════════════

  private boolean validateForm() {
    if (currentItem == null) {
      showError("Không tìm thấy thông tin sản phẩm!"); return false;
    }
    if (dtpStartTime.getValue() == null) {
      showError("Vui lòng chọn ngày bắt đầu!"); dtpStartTime.requestFocus(); return false;
    }
    if (dtpEndTime.getValue() == null) {
      showError("Vui lòng chọn ngày kết thúc!"); dtpEndTime.requestFocus(); return false;
    }
    if (!dtpEndTime.getValue().isAfter(dtpStartTime.getValue())) {
      showError("Ngày kết thúc phải sau ngày bắt đầu!"); dtpEndTime.requestFocus(); return false;
    }
    if (!dtpStartTime.getValue().isAfter(LocalDate.now())) {
      showError("Ngày bắt đầu phải từ ngày mai trở đi!"); dtpStartTime.requestFocus(); return false;
    }
    String buyNowText = txtBuyNowPrice.getText().trim();
    if (!buyNowText.isEmpty()) {
      double buyNow = parseDoubleSafe(buyNowText, -1);
      if (buyNow <= 0) {
        showError("Giá mua ngay phải là số dương!"); txtBuyNowPrice.requestFocus(); return false;
      }
      if (buyNow <= currentItem.getStartingPrice()) {
        showError("Giá mua ngay phải lớn hơn giá khởi điểm!"); txtBuyNowPrice.requestFocus(); return false;
      }
    }
    return true;
  }

  // ════════════════════════════════════════
  // HELPERS
  // ════════════════════════════════════════

  private void loadImage(String path) {
    if (imgItemPreview == null || path == null || path.isBlank()) return;
    File file = new File(path);
    if (file.exists()) imgItemPreview.setImage(new Image(file.toURI().toString()));
  }

  private double parseDoubleSafe(String text, double def) {
    try { return Double.parseDouble(text.replaceAll("[,.]", "").replaceAll("\\s+", "")); }
    catch (NumberFormatException e) { return def; }
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format((long) amount);
  }

  private void addNumberOnlyListener(TextField field) {
    field.textProperty().addListener((obs, o, n) -> {
      if (!n.matches("\\d*")) field.setText(n.replaceAll("[^\\d]", ""));
    });
  }

  private void closeDialog() {
    Stage stage = (Stage) btnCancel.getScene().getWindow();
    stage.close();
  }

  private void resetConfirmButton() {
    btnConfirm.setDisable(false);
    btnConfirm.setText("Gửi tạo phiên →");
  }

  private void showError(String msg) {
    new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
  }

  private void showSuccess() {
    new Alert(Alert.AlertType.INFORMATION,
        "Phiên đấu giá đã được gửi!\nAdmin sẽ duyệt và kích hoạt phiên cho bạn.",
        ButtonType.OK).showAndWait();
  }
}