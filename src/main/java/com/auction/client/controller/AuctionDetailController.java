package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.User;
import com.auction.server.dao.AuctionDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.io.InputStream;

public class AuctionDetailController {
    @FXML private Label lblAuctionId;
    @FXML private Label lblItemName;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblStartTime;
    @FXML private Label lblEndTime;
    @FXML private Label lblStatus;
    @FXML private Label lblDescription;
    @FXML private ImageView imgProduct;

    private final AuctionDAO auctionDAO = new AuctionDAO();

    public void loadAuctionDetail(int auctionId, String itemName, User user) {
        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName != null) lblItemName.setText(itemName);

        new Thread(() -> {
            try {
                Auction auction = auctionDAO.getAuctionById(auctionId);

                if (auction != null) {
                    // 1. Lấy mô tả và link ảnh từ bảng items bằng itemId của phiên đấu giá
                    String descriptionFromDB = auctionDAO.getItemDescription(auction.getItemId());
                    String imagePathFromDB = auctionDAO.getItemImagePath(auction.getItemId());

                    // 2. Xử lý khởi tạo đối tượng Image linh hoạt (Hỗ trợ cả ảnh nội bộ project và URL internet)
                    Image itemImage = null;
                    if (imagePathFromDB != null && !imagePathFromDB.trim().isEmpty()) {
                        try {
                            String trimmedPath = imagePathFromDB.trim();
                            // Nếu đường dẫn bắt đầu bằng http/https thì tải ảnh từ mạng internet về
                            if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
                                itemImage = new Image(trimmedPath, true);
                            } else {
                                // Nếu là đường dẫn file nội bộ, đảm bảo có dấu "/" ở đầu để tìm kiếm trong thư mục resources
                                String resourcePath = trimmedPath.startsWith("/") ? trimmedPath : "/" + trimmedPath;
                                InputStream imageStream = getClass().getResourceAsStream(resourcePath);

                                if (imageStream != null) {
                                    itemImage = new Image(imageStream);
                                } else {
                                    System.err.println("❌ Không tìm thấy file ảnh trong thư mục resources: " + resourcePath);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Lỗi xảy ra khi nạp cấu trúc ảnh từ nguồn: " + imagePathFromDB);
                        }
                    }

                    // 3. Đẩy toàn bộ dữ liệu thật lên màn hình hiển thị của JavaFX
                    Image finalImage = itemImage;
                    Platform.runLater(() -> {
                        lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
                        lblStartTime.setText(auction.getStartTime().toString().replace("T", " "));
                        lblEndTime.setText(auction.getEndTime().toString().replace("T", " "));

                        String statusStr = auction.getAuctionStatus() != null ? auction.getAuctionStatus().toUpperCase() : "UNKNOWN";
                        lblStatus.setText(statusStr);
                        lblDescription.setText(descriptionFromDB);

                        // Hiển thị hình ảnh sản phẩm lên khung ảnh nếu quá trình nạp thành công
                        if (finalImage != null && imgProduct != null) {
                            imgProduct.setImage(finalImage);
                        }

                        // Đồng bộ đổi màu sắc của khung trạng thái dựa trên dữ liệu thời gian thực
                        if ("FINISHED".equalsIgnoreCase(statusStr)) {
                            lblStatus.setStyle("-fx-background-color: #fed7d7; -fx-text-fill: #9b2c2c; -fx-background-radius: 5; -fx-padding: 4 12 4 12;");
                        } else {
                            lblStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-background-radius: 5; -fx-padding: 4 12 4 12;");
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi đồng bộ dữ liệu động từ DBeaver lên giao diện!");
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleClose() {
        if (lblAuctionId != null && lblAuctionId.getScene() != null) {
            lblAuctionId.getScene().getWindow().hide();
        }
    }
}