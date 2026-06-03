package com.auction.client.controller.bidder;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.common.network.AuctionRoomDTO;
import com.auction.common.model.*;
import com.auction.common.network.BidPlaceDTO;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.auction.client.controller.seller.CardUtils;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AuctionRoomController {

  @FXML private Label lblProductName;
  @FXML private ImageView imgProduct;
  @FXML private Label lblDescription;
  @FXML private Label lblCountdown;
  @FXML private Label lblUserBalance;
  @FXML private Label lblUserEscrow;
  @FXML private Label lblStartPrice;
  @FXML private Label lblCurrentPrice;
  @FXML private TextField txtBidAmount;
  @FXML private Button btnPlaceBid;

  @FXML private VBox listHistoryContainer;
  @FXML private VBox vboxProperties;

  private int activeAuctionId;
  private User currentOnlineUser;
  private double currentAuctionPrice;
  private Timeline roomCountdownTimeline;

  @FXML
  public void initialize() {
    MessageRouter.getInstance().register(ResponseCode.BID_SUCCESS, this::handleBidSuccess);
    MessageRouter.getInstance().register(ResponseCode.BID_FAILED, this::handleBidFailed);
    MessageRouter.getInstance().register(ResponseCode.ROOM_STATE_UPDATE, this::handleRoomStateUpdate);
    MessageRouter.getInstance().register(ResponseCode.ROOM_JOIN_SUCCESS, msg -> System.out.println("[CLIENT] Đã vào phòng thành công."));
    MessageRouter.getInstance().register(ResponseCode.ROOM_JOIN_FAILED, this::handleRoomJoinFailed);
    MessageRouter.getInstance().register(ResponseCode.AUCTION_ENDED, msg -> {
      Platform.runLater(() -> {
        // Khóa nút đặt giá
        if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
        // Dừng đếm ngược
        if (roomCountdownTimeline != null) roomCountdownTimeline.stop();
        // Hiện thông báo đỏ rực
        if (lblCountdown != null) {
          lblCountdown.setText("ĐÃ BỊ ADMIN CHẶN!");
          lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
        }
        showAlert("Thông báo khẩn", msg.getMessage(), Alert.AlertType.WARNING);
      });
    });
  }

  private void handleBidSuccess(Message msg) {
    Platform.runLater(() -> {
      txtBidAmount.clear();
    });
  }

  private void handleBidFailed(Message msg) {
    Platform.runLater(() -> showAlert("Thất bại", msg.getMessage() != null ? msg.getMessage() : "Lỗi đặt giá", Alert.AlertType.ERROR));
  }

  private void handleRoomStateUpdate(Message msg) {
    Object payload = msg.getPayload();
    if (payload instanceof AuctionRoomDTO) {
      AuctionRoomDTO dto = (AuctionRoomDTO) payload;
      if (dto.getAuction() != null && dto.getAuction().getAuctionId() == activeAuctionId) {
        Platform.runLater(() -> updateRoomUI(dto));
      }
    }
  }

  public void loadAuctionDetail(int auctionId, String itemName, User user) {
    this.activeAuctionId = auctionId;
    this.currentOnlineUser = user;

    if (lblProductName != null) {
      lblProductName.setText(itemName);
    }

    CompletableFuture.runAsync(() -> {
      SocketClient.getInstance().sendRequest(RequestCode.JOIN_ROOM, auctionId);
    });
  }

  private void updateRoomUI(AuctionRoomDTO dto) {
    Auction auction = dto.getAuction();
    Item item = dto.getItem();
    List<BiddingHistory> historyList = dto.getHistoryList();
    Map<Integer, String> usernameMap = dto.getUsernameMap();
    double realBalance = dto.getUserBalance();

    this.currentAuctionPrice = auction.getCurrentPrice();
    currentOnlineUser.setBalance(realBalance);

    if (lblCurrentPrice != null) lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
    if (lblStartPrice != null) lblStartPrice.setText(String.format("%,.0f UETệ", auction.getStartingPrice()));
    if (lblUserBalance != null) lblUserBalance.setText(String.format("%,.0f UETệ", realBalance));

    if (lblUserEscrow != null) {
      double bidderEscrowInThisRoom = 0;
      if (auction.getCurrentWinnerId() != null && auction.getCurrentWinnerId() == currentOnlineUser.getId()) {
        bidderEscrowInThisRoom = auction.getCurrentPrice();
      }
      lblUserEscrow.setText(String.format("Tiền cọc của bạn: %,.0f UETệ", bidderEscrowInThisRoom));
    }

    if (item != null) {
      if (lblProductName != null) lblProductName.setText(item.getName());
      if (lblDescription != null) lblDescription.setText(item.getDescription());
      loadProductProperties(item);

      if (imgProduct != null && item != null) {
        CardUtils.loadImage(imgProduct, item.getImgItem());
      }
    }

    populateBidHistory(historyList, usernameMap);
    startRoomCountdown(auction.getEndTime());

    if (!"RUNNING".equalsIgnoreCase(auction.getAuctionStatus())) {
      btnPlaceBid.setDisable(true);
      if (roomCountdownTimeline != null) roomCountdownTimeline.stop();
      lblCountdown.setText("ĐÃ KẾT THÚC!");
      lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
    }
  }

  private void populateBidHistory(List<BiddingHistory> historyList, Map<Integer, String> usernameMap) {
    if (listHistoryContainer == null) return;
    listHistoryContainer.getChildren().clear();

    if (historyList == null || historyList.isEmpty()) {
      Label lblEmpty = new Label("Chưa có lượt đặt giá nào trong phòng này.");
      lblEmpty.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic; -fx-font-size: 13px;");
      listHistoryContainer.getChildren().add(lblEmpty);
      return;
    }

    for (BiddingHistory bid : historyList) {
      HBox row = new HBox(10);
      int bId = bid.getBidderId();
      String bidderName = "Người dùng #" + bId;

      if (bId == currentOnlineUser.getId()) {
        bidderName = "Bạn (Tôi)";
      } else if (usernameMap != null && usernameMap.containsKey(bId)) {
        bidderName = usernameMap.get(bId);
      }

      Label lblUser = new Label(bidderName);

      if (bId == currentOnlineUser.getId()) {
        lblUser.setStyle("-fx-text-fill: #0F172A; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
        row.setStyle("-fx-padding: 8 12; -fx-background-color: #EFF6FF; -fx-background-radius: 8; -fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-alignment: center-left;");
      } else {
        lblUser.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 13px; -fx-min-width: 120px;");
        row.setStyle("-fx-padding: 8 12; -fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-alignment: center-left;");
      }

      Label lblPrice = new Label(String.format("%,.0f UETệ", bid.getBidAmount()));
      if (bid.getBidAmount() >= currentAuctionPrice) {
        lblPrice.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 13px;");
      } else {
        lblPrice.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
      }

      HBox rightContainer = new HBox();
      javafx.scene.layout.HBox.setHgrow(rightContainer, javafx.scene.layout.Priority.ALWAYS);
      rightContainer.setStyle("-fx-alignment: center-right;");
      rightContainer.getChildren().add(lblPrice);

      row.getChildren().addAll(lblUser, rightContainer);
      listHistoryContainer.getChildren().add(row);
    }
  }

  private void loadProductProperties(Item item) {
    if (vboxProperties == null || item == null) return;
    vboxProperties.getChildren().clear();

    if (item instanceof Vehicle v) {
      addPropertyRow("Hãng sản xuất:", v.getMake());
      addPropertyRow("Dòng xe (Model):", v.getModelVehicle());
      addPropertyRow("Năm sản xuất:", v.getManufactureYear() > 0 ? String.valueOf(v.getManufactureYear()) : null);
      addPropertyRow("Số KM đã đi (ODO):", v.getMileage() > 0 ? String.format("%,d km", v.getMileage()) : "0 km");
      addPropertyRow("Loại nhiên liệu:", v.getFuelType());
      addPropertyRow("Biển số xe:", v.getLicensePlate());
    } else if (item instanceof Art a) {
      addPropertyRow("Họa sĩ / Tác giả:", a.getArtist());
      addPropertyRow("Năm sáng tác:", a.getYearCreated() > 0 ? String.valueOf(a.getYearCreated()) : null);
      addPropertyRow("Chất liệu tác phẩm:", a.getMedium());
      addPropertyRow("Chứng nhận bản quyền:", a.isHasCertificate() ? "Đã cấp chứng chỉ (Xác thực)" : "Chưa xác minh");
    } else if (item instanceof Electronics e) {
      addPropertyRow("Thương hiệu:", e.getBrand());
      addPropertyRow("Mã Model máy:", e.getModel());
      addPropertyRow("Thời hạn bảo hành:", e.getWarrantyMonths() > 0 ? e.getWarrantyMonths() + " tháng" : "Hết bảo hành");
      addPropertyRow("Tình trạng thiết bị:", e.getItemCondition());
    }
  }

  private void addPropertyRow(String attrName, String value) {
    if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("[NULL]")) return;
    HBox row = new HBox(15);
    Label lblTitle = new Label(attrName);
    lblTitle.setStyle("-fx-text-fill: #64748B; -fx-font-size: 14px; -fx-min-width: 150px;");
    Label lblValue = new Label(value);
    lblValue.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 14px; -fx-font-weight: bold;");
    row.getChildren().addAll(lblTitle, lblValue);
    vboxProperties.getChildren().add(row);
  }

  private void startRoomCountdown(LocalDateTime endTime) {
    if (roomCountdownTimeline != null) roomCountdownTimeline.stop();
    if (lblCountdown == null) return;
    if (endTime == null) {
      lblCountdown.setText("Không giới hạn");
      return;
    }

    roomCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
      LocalDateTime now = LocalDateTime.now();
      if (now.isAfter(endTime)) {
        lblCountdown.setText("ĐÃ KẾT THÚC!");
        lblCountdown.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold; -fx-font-size: 24px;");
        if (btnPlaceBid != null) btnPlaceBid.setDisable(true);
        roomCountdownTimeline.stop();
        return;
      }
      long totalSeconds = java.time.Duration.between(now, endTime).getSeconds();
      long hours = totalSeconds / 3600;
      long minutes = (totalSeconds % 3600) / 60;
      long seconds = totalSeconds % 60;
      lblCountdown.setText(String.format("%02dh : %02dm : %02ds", hours, minutes, seconds));
    }));
    roomCountdownTimeline.setCycleCount(Animation.INDEFINITE);
    roomCountdownTimeline.play();
  }

  private void handleRoomJoinFailed(Message msg) {
    Platform.runLater(() -> {
      showAlert("Lỗi vào phòng", msg.getMessage() != null ? msg.getMessage() : "Phòng không tồn tại!", Alert.AlertType.ERROR);
      Stage stage = (Stage) lblProductName.getScene().getWindow();
      if (stage != null) stage.close();
    });
  }

  @FXML
  void handleBackToMarket(ActionEvent event) {
    if (roomCountdownTimeline != null) roomCountdownTimeline.stop();

    CompletableFuture.runAsync(() -> {
      SocketClient.getInstance().sendRequest(RequestCode.LEAVE_ROOM, activeAuctionId);
    });

    MessageRouter.getInstance().unregister(ResponseCode.BID_SUCCESS);
    MessageRouter.getInstance().unregister(ResponseCode.BID_FAILED);
    MessageRouter.getInstance().unregister(ResponseCode.ROOM_STATE_UPDATE);
    MessageRouter.getInstance().unregister(ResponseCode.ROOM_JOIN_SUCCESS);
    MessageRouter.getInstance().unregister(ResponseCode.ROOM_JOIN_FAILED); // NHỚ HỦY ĐĂNG KÝ

    Stage stage = (Stage) lblProductName.getScene().getWindow();
    stage.close();
  }

  @FXML
  void onSubmitBid(ActionEvent event) {
    String inputStr = txtBidAmount.getText().trim();
    if (inputStr.isEmpty()) {
      showAlert("Thông báo", "Vui lòng nhập số tiền bạn muốn trả giá!", Alert.AlertType.WARNING);
      return;
    }

    try {
      double bidAmount = Double.parseDouble(inputStr);
      if (bidAmount <= currentAuctionPrice) {
        showAlert("Lỗi đặt giá", "Giá đặt phải lớn hơn giá hiện tại!", Alert.AlertType.ERROR);
        return;
      }
      CompletableFuture.runAsync(() -> {
        SocketClient.getInstance().sendRequest(RequestCode.PLACE_BID, new BidPlaceDTO(activeAuctionId, bidAmount));
      });
    } catch (NumberFormatException e) {
      showAlert("Lỗi định dạng", "Vui lòng nhập số tiền hợp lệ!", Alert.AlertType.ERROR);
    }
  }

  private void showAlert(String title, String content, Alert.AlertType type) {
    Platform.runLater(() -> {
      Alert alert = new Alert(type);
      alert.setTitle(title);
      alert.setHeaderText(null);
      alert.setContentText(content);
      alert.show();
    });
  }

}