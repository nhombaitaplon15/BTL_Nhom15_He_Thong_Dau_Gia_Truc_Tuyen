package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import com.auction.common.network.AuctionItemDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class CancelAuctionController {

  @FXML private Label lblItemName;
  @FXML private Label lblAuctionId;
  @FXML private Label lblStartTime;
  @FXML private Button btnKeep;
  @FXML private Button btnConfirmCancel;

  private AuctionItemDTO auctionItem;
  private Runnable onSuccessCallback;

  // Handler Realtime
  private final Consumer<Message> onCancelSuccess = this::handleCancelSuccess;
  private final Consumer<Message> onCancelFailed = this::handleCancelFailed;

  @FXML
  public void initialize() {
    MessageRouter.getInstance().register(ResponseCode.SELLER_CANCEL_SUCCESS, onCancelSuccess);
    MessageRouter.getInstance().register(ResponseCode.SELLER_CANCEL_FAILED, onCancelFailed);
  }

  public void cleanupHandlers() {
    MessageRouter.getInstance().unregister(ResponseCode.SELLER_CANCEL_SUCCESS);
    MessageRouter.getInstance().unregister(ResponseCode.SELLER_CANCEL_FAILED);
  }

  public void setData(AuctionItemDTO item, Runnable onSuccess) {
    this.auctionItem = item;
    this.onSuccessCallback = onSuccess;

    lblItemName.setText(item.getItem().getName());

    String status = item.getAuction().getAuctionStatus();
    String statusVN = status.equals("WAITING_FOR_ADMIN") ? "CHỜ DUYỆT" : status;
    lblAuctionId.setText(String.format("#A%04d - %s", item.getAuction().getAuctionId(), statusVN));

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    lblStartTime.setText(item.getAuction().getStartTime().format(formatter));
  }

  @FXML
  private void onConfirmCancel() {
    btnConfirmCancel.setDisable(true);
    btnConfirmCancel.setText("Đang xử lý...");
    SocketClient.getInstance().sendRequest(RequestCode.SELLER_CANCEL_AUCTION, auctionItem.getAuction().getAuctionId());
  }

  @FXML
  private void onKeep() {
    cleanupHandlers();
    closeDialog();
  }

  private void handleCancelSuccess(Message msg) {
    Platform.runLater(() -> {
      cleanupHandlers();
      if (onSuccessCallback != null) onSuccessCallback.run();
      closeDialog();
    });
  }

  private void handleCancelFailed(Message msg) {
    Platform.runLater(() -> {
      btnConfirmCancel.setDisable(false);
      btnConfirmCancel.setText("✕ Xác nhận huỷ");
      new Alert(Alert.AlertType.ERROR, "Huỷ phiên thất bại: " + msg.getMessage()).showAndWait();
    });
  }

  private void closeDialog() {
    Stage stage = (Stage) btnKeep.getScene().getWindow();
    stage.close();
  }
}