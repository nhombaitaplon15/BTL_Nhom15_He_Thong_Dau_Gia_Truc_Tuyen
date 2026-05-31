package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.model.Auction;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.server.core.AuctionItemDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.function.Consumer;

public class EditAuctionController {

  // ── PREVIEW ──
  @FXML private ImageView imgItemPreview;
  @FXML private Label lblItemName;
  @FXML private Label lblItemMeta;
  @FXML private Label lblStartingPrice;

  // ── FORM FIELDS ──
  @FXML private DatePicker dtpStartTime;
  @FXML private DatePicker dtpEndTime;
  @FXML private TextField txtBuyNowPrice;

  // ── BUTTONS ──
  @FXML private Button btnSave;

  // ── STATE ──
  private AuctionItemDTO currentAuctionItem;
  private Runnable onSuccessCallback;

  // ── HANDLERS ──
  // LƯU Ý: Đảm bảo bạn đã khai báo SELLER_EDIT_SUCCESS và SELLER_EDIT_FAILED trong ResponseCode
  private final Consumer<Message> onEditSuccess = this::handleEditSuccess;
  private final Consumer<Message> onEditFailed = this::handleEditFailed;

  @FXML
  public void initialize() {
    registerNetworkHandlers();
    addNumberOnlyListener(txtBuyNowPrice);
  }

  private void registerNetworkHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    // Giả định bạn dùng ResponseCode này, bạn có thể đổi theo chuẩn của hệ thống
    r.register(ResponseCode.SELLER_EDIT_SUCCESS, onEditSuccess);
    r.register(ResponseCode.SELLER_EDIT_FAILED, onEditFailed);
  }

  public void cleanupHandlers() {
    MessageRouter r = MessageRouter.getInstance();
    r.unregister(ResponseCode.SELLER_EDIT_SUCCESS);
    r.unregister(ResponseCode.SELLER_EDIT_FAILED);
  }

  // Nạp dữ liệu cũ vào form để người dùng sửa
  public void setAuctionData(AuctionItemDTO itemDAO) {
    this.currentAuctionItem = itemDAO;
    Auction auction = itemDAO.getAuction();

    // 1. Đổ dữ liệu Preview
    lblItemName.setText(itemDAO.getItem().getName());
    lblItemMeta.setText(itemDAO.getItem().getItemType() + " · Phiên #A"
        + String.format("%04d", auction.getAuctionId()) + " · CHỜ DUYỆT");
    lblStartingPrice.setText(formatMoney(auction.getStartingPrice()) + "đ");
    loadImage(itemDAO.getItem().getImgItem());

    // 2. Đổ dữ liệu Form (thời gian)
    if (auction.getStartTime() != null) {
      dtpStartTime.setValue(auction.getStartTime().toLocalDate());
    }
    if (auction.getEndTime() != null) {
      dtpEndTime.setValue(auction.getEndTime().toLocalDate());
    }
  }

  public void setOnSuccessCallback(Runnable callback) {
    this.onSuccessCallback = callback;
  }

  @FXML
  private void onSave() {
    if (!validateForm()) return;

    btnSave.setDisable(true);
    btnSave.setText("Đang lưu...");

    // 1. Cập nhật lại object Auction với dữ liệu mới từ form
    Auction updatedAuction = currentAuctionItem.getAuction();
    updatedAuction.setStartTime(dtpStartTime.getValue().atTime(LocalTime.of(8, 0)));
    updatedAuction.setEndTime(dtpEndTime.getValue().atTime(LocalTime.of(20, 0)));
    // updatedAuction.setNote(txtNote.getText()); // Cập nhật các trường khác nếu có

    // 2. Gửi request lên Server (Đảm bảo có mã RequestCode.SELLER_EDIT_AUCTION)
    SocketClient.getInstance().sendRequest(RequestCode.SELLER_EDIT_AUCTION, updatedAuction);
  }

  @FXML
  private void onCancel() {
    cleanupHandlers();
    closeDialog();
  }

  private void handleEditSuccess(Message msg) {
    Platform.runLater(() -> {
      cleanupHandlers();
      new Alert(Alert.AlertType.INFORMATION, "Cập nhật phiên đấu giá thành công!", ButtonType.OK).showAndWait();
      if (onSuccessCallback != null) onSuccessCallback.run();
      closeDialog();
    });
  }

  private void handleEditFailed(Message msg) {
    Platform.runLater(() -> {
      btnSave.setDisable(false);
      btnSave.setText("💾 Lưu thay đổi");
      String reason = msg.getMessage() != null ? msg.getMessage() : "Vui lòng thử lại sau.";
      new Alert(Alert.AlertType.ERROR, "Cập nhật thất bại: " + reason, ButtonType.OK).showAndWait();
    });
  }

  private boolean validateForm() {
    if (dtpStartTime.getValue() == null || dtpEndTime.getValue() == null) {
      showError("Vui lòng chọn đầy đủ thời gian!"); return false;
    }
    if (!dtpEndTime.getValue().isAfter(dtpStartTime.getValue())) {
      showError("Ngày kết thúc phải sau ngày bắt đầu!"); return false;
    }
    if (!dtpStartTime.getValue().isAfter(LocalDate.now().minusDays(1))) {
      showError("Ngày bắt đầu không hợp lệ!"); dtpStartTime.requestFocus(); return false;
    }
    return true;
  }

  private void closeDialog() {
    Stage stage = (Stage) btnSave.getScene().getWindow();
    stage.close();
  }

  private void showError(String msg) {
    new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
  }

  private void loadImage(String path) {
    if (imgItemPreview == null || path == null || path.isBlank()) return;
    File file = new File(path);
    if (file.exists()) imgItemPreview.setImage(new Image(file.toURI().toString()));
  }

  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format((long) amount);
  }

  private void addNumberOnlyListener(TextField field) {
    field.textProperty().addListener((obs, o, n) -> {
      if (!n.matches("\\d*")) field.setText(n.replaceAll("[^\\d]", ""));
    });
  }
}