package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Item;
import com.auction.common.network.CreateAuctionDTO;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.application.Platform;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class CreateAuctionController {

  @FXML private ImageView imgItemPreview;
  @FXML private Label     lblItemName;
  @FXML private Label     lblItemMeta;
  @FXML private Label     lblStartingPrice;

  @FXML private DatePicker       dtpStartTime;
  @FXML private DatePicker       dtpEndTime;
  @FXML private TextField        txtBuyNowPrice;
  @FXML private TextField        txtNote;

  @FXML private Button btnConfirm;
  @FXML private Button btnCancel;

  @FXML private TextField txtStartTime;
  @FXML private TextField txtEndTime;

  private Item     currentItem;
  private Runnable onSubmitCallback;

  private final Consumer<Message> onCreated     = this::handleAuctionCreated;
  private final Consumer<Message> onCreateFailed = this::handleAuctionCreateFailed;

  @FXML
  public void initialize() {
    registerNetworkHandlers();
    dtpStartTime.setValue(LocalDate.now());
    dtpEndTime.setValue(LocalDate.now().plusDays(2));

    txtStartTime.setText("08:00");
    txtEndTime.setText("20:00");

    addNumberOnlyListener(txtBuyNowPrice);
  }

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

  public void setItem(Item item) {
    this.currentItem = item;
    lblItemName    .setText(item.getName());
    lblItemMeta    .setText(item.getItemType() + " · Giá khởi điểm đã đặt");
    lblStartingPrice.setText(formatMoney(item.getStartingPrice()) + " UETệ");
    CardUtils.loadImage(imgItemPreview, item.getImgItem());
  }

  public void setOnSubmitCallback(Runnable callback) {
    this.onSubmitCallback = callback;
  }

  private void handleAuctionCreated(Message msg) {
    Platform.runLater(() -> {
      resetConfirmButton();
      cleanupHandlers();
      showSuccess();
      if (onSubmitCallback != null) onSubmitCallback.run();
      closeDialog();
    });
  }

  private void handleAuctionCreateFailed(Message msg) {
    Platform.runLater(() -> {
      resetConfirmButton();
      String reason = msg.getMessage() != null ? msg.getMessage() : "Vui lòng thử lại.";
      showError("Tạo phiên thất bại: " + reason);
    });
  }

  @FXML
  private void onConfirm() {
    if (!validateForm()) return;

    btnConfirm.setDisable(true);
    btnConfirm.setText("Đang gửi...");

    CreateAuctionDTO dto = buildAuctionDTO();
    System.out.println("DEBUG CLIENT - Chuẩn bị gửi ItemID: " + dto.getItemId());

    CompletableFuture.runAsync(() -> {
      SocketClient.getInstance().sendRequest(RequestCode.SELLER_CREATE_AUCTION, dto);
    });
  }

  @FXML
  private void onCancel() {
    cleanupHandlers();
    closeDialog();
  }

  private CreateAuctionDTO buildAuctionDTO() {
    LocalDateTime startTime = parseDateTime(dtpStartTime.getValue(), txtStartTime.getText());
    LocalDateTime endTime = parseDateTime(dtpEndTime.getValue(), txtEndTime.getText());

    CreateAuctionDTO dto = new CreateAuctionDTO();
    dto.setItemId(currentItem.getId());
    dto.setSellerId(currentItem.getSellerId());
    dto.setStartingPrice(currentItem.getStartingPrice());
    dto.setStartTime(startTime);
    dto.setEndTime(endTime);

    return dto;
  }

  private boolean validateForm() {
    if (currentItem == null) {
      showError("Không tìm thấy thông tin sản phẩm!"); return false;
    }
    if (dtpStartTime.getValue() == null || txtStartTime.getText().trim().isEmpty()) {
      showError("Vui lòng chọn ngày và giờ bắt đầu!"); dtpStartTime.requestFocus(); return false;
    }
    if (dtpEndTime.getValue() == null || txtEndTime.getText().trim().isEmpty()) {
      showError("Vui lòng chọn ngày và giờ kết thúc!"); dtpEndTime.requestFocus(); return false;
    }

    if (!isValidTimeFormat(txtStartTime.getText()) || !isValidTimeFormat(txtEndTime.getText())) {
      showError("Định dạng giờ không hợp lệ! Vui lòng nhập theo định dạng HH:mm (ví dụ: 08:30).");
      return false;
    }

    LocalDateTime startDateTime = parseDateTime(dtpStartTime.getValue(), txtStartTime.getText());
    LocalDateTime endDateTime = parseDateTime(dtpEndTime.getValue(), txtEndTime.getText());

    if (startDateTime.isBefore(LocalDateTime.now())) {
      showError("Thời gian bắt đầu phải từ thời điểm hiện tại trở đi!");
      txtStartTime.requestFocus();
      return false;
    }
    if (!endDateTime.isAfter(startDateTime)) {
      showError("Thời gian kết thúc phải sau thời gian bắt đầu!");
      dtpEndTime.requestFocus();
      return false;
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

  private double parseDoubleSafe(String text, double def) {
    try { return Double.parseDouble(text.replaceAll("[,.]", "").replaceAll("\\s+", "")); }
    catch (NumberFormatException e) { return def; }
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format((long) amount);
  }
  private boolean isValidTimeFormat(String time) {
    return time != null && time.matches("^([01]\\d|2[0-3]):([0-5]\\d)$");
  }

  private LocalDateTime parseDateTime(LocalDate date, String timeStr) {
    String[] parts = timeStr.trim().split(":");
    int hour = Integer.parseInt(parts[0]);
    int minute = Integer.parseInt(parts[1]);
    return date.atTime(LocalTime.of(hour, minute));
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