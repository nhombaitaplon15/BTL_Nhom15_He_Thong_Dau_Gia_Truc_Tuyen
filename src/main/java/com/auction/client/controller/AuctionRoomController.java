package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import java.io.InputStream;
import javafx.stage.Stage;

public class AuctionRoomController {

    // 🎯 SỬA LỖI 2: Đồng bộ chuẩn chỉ fx:id từ file FXML AuctionRoom của bạn
    @FXML private Label lblProductName;
    @FXML private ImageView imgProduct;
    @FXML private Label lblDescription;
    @FXML private Label lblCountdown;
    @FXML private Label lblUserBalance;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private VBox listHistoryContainer;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final UserDAO userDAO = new UserDAO();

    private int activeAuctionId;
    private User currentOnlineUser;
    private double currentAuctionPrice;

    public void loadAuctionDetail(int auctionId, String itemName, User user) {
        this.activeAuctionId = auctionId;
        this.currentOnlineUser = user;

        if (lblProductName != null) lblProductName.setText(itemName);
        refreshRoomData();
    }

    private void refreshRoomData() {
        new Thread(() -> {
            try {
                Auction auction = auctionDAO.getAuctionById(activeAuctionId);
                double realBalance = userDAO.getBalance(currentOnlineUser.getId());
                currentOnlineUser.setBalance(realBalance);

                if (auction != null) {
                    this.currentAuctionPrice = auction.getCurrentPrice();
                    Item item = itemDAO.getItemById(auction.getItemId());

                    Platform.runLater(() -> {
                        if (lblCurrentPrice != null) lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
                        if (lblStartPrice != null) lblStartPrice.setText(String.format("%,.0f UETệ", auction.getStartingPrice()));
                        if (lblUserBalance != null) lblUserBalance.setText(String.format("%,.0f UETệ", realBalance));
                        if (lblCountdown != null) lblCountdown.setText("ĐANG LIVE");

                        if (item != null) {
                            if (lblProductName != null) lblProductName.setText(item.getName());
                            if (lblDescription != null) lblDescription.setText(item.getDescription());
                            if (imgProduct != null && item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                                try {
                                    String path = item.getImgItem().trim();
                                    if (!path.startsWith("/")) path = "/" + path;
                                    InputStream is = getClass().getResourceAsStream(path);
                                    if (is != null) imgProduct.setImage(new Image(is));
                                } catch (Exception e) { e.printStackTrace(); }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    void onSubmitBid(ActionEvent event) { // 🎯 Tên hàm phải trùng với onAction="#onSubmitBid" trong FXML phòng lớn
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

            if (currentOnlineUser.getBalance() < bidAmount) {
                showAlert("Lỗi tài khoản", "Số dư ví không đủ!", Alert.AlertType.ERROR);
                return;
            }

            boolean success = auctionDAO.executePlaceBidTransaction(activeAuctionId, currentOnlineUser.getId(), bidAmount);

            if (success) {
                showAlert("Thành công", "Đặt giá thành công!", Alert.AlertType.INFORMATION);
                txtBidAmount.clear();
                refreshRoomData();
            } else {
                showAlert("Thất bại", "Đặt giá thất bại! Đã có người trả giá cao hơn.", Alert.AlertType.ERROR);
                refreshRoomData();
            }

        } catch (NumberFormatException e) {
            showAlert("Lỗi định dạng", "Vui lòng nhập số hợp lệ!", Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleBackToMarket(ActionEvent event) {
        // Tắt popup phòng đấu giá để quay lại sàn
        Stage stage = (Stage) lblProductName.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
