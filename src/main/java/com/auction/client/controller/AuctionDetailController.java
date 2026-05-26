package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.common.model.User;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.ItemDAO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import java.io.InputStream;

public class AuctionDetailController {

    // 🎯 ĐỒNG BỘ 100% FX:ID VỚI FILE FXML CỦA BẠN
    @FXML private Label lblAuctionId;
    @FXML private ImageView imgProduct;
    @FXML private Label lblItemName;       // Đã sửa từ lblProductName thành lblItemName
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblStartTime;      // Thêm mới khớp FXML
    @FXML private Label lblEndTime;        // Thêm mới khớp FXML
    @FXML private Label lblStatus;
    @FXML private Label lblDescription;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();

    /**
     * Hàm nhận dữ liệu đồng bộ từ Database dội sang
     */
    public void loadAuctionDetail(int auctionId, String fallbackName, User user) {
        // Set thông tin cơ bản tạm thời trước khi luồng DB hoàn thành
        if (lblAuctionId != null) lblAuctionId.setText("#" + auctionId);
        if (lblItemName != null) lblItemName.setText(fallbackName);

        // Chạy Thread kết nối Database để lấy thông số thực tế tránh đơ UI chính
        new Thread(() -> {
            try {
                Auction auction = auctionDAO.getAuctionById(auctionId);
                if (auction != null) {
                    Item item = itemDAO.getItemById(auction.getItemId());

                    Platform.runLater(() -> {
                        // 1. Đổ dữ liệu thật từ bảng public.auctions
                        if (lblCurrentPrice != null) {
                            lblCurrentPrice.setText(String.format("%,.0f UETệ", auction.getCurrentPrice()));
                        }

                        // Đồng bộ các nhãn thời gian từ Database lên giao diện mới
                        if (lblStartTime != null && auction.getStartTime() != null) {
                            lblStartTime.setText(auction.getStartTime().toString());
                        }
                        if (lblEndTime != null && auction.getEndTime() != null) {
                            lblEndTime.setText(auction.getEndTime().toString());
                        }

                        if (lblStatus != null) {
                            String statusText = "ĐANG DIỄN RA";
                            if ("FINISHED".equalsIgnoreCase(auction.getAuctionStatus())) {
                                statusText = "ĐÃ KẾT THÚC";
                                lblStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 5; -fx-padding: 4 12 4 12;");
                            } else if ("PENDING".equalsIgnoreCase(auction.getAuctionStatus())) {
                                statusText = "CHỜ KÍCH HOẠT";
                                lblStatus.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-background-radius: 5; -fx-padding: 4 12 4 12;");
                            } else {
                                // Mặc định ĐANG DIỄN RA (Style xanh lá có sẵn trong FXML)
                                lblStatus.setText(statusText);
                            }
                        }

                        // 2. Đổ dữ liệu thật từ bảng public.items
                        if (item != null) {
                            if (lblItemName != null) lblItemName.setText(item.getName());
                            if (lblDescription != null) lblDescription.setText(item.getDescription());

                            // Xử lý nạp ảnh tĩnh từ đường dẫn lưu trong DB PostgreSQL
                            if (item.getImgItem() != null && !item.getImgItem().trim().isEmpty()) {
                                try {
                                    String path = item.getImgItem().trim();
                                    if (!path.startsWith("/")) path = "/" + path;
                                    InputStream is = getClass().getResourceAsStream(path);
                                    if (is != null) imgProduct.setImage(new Image(is));
                                } catch (Exception e) {
                                    System.err.println("❌ Lỗi hiển thị ảnh chi tiết vật phẩm: " + e.getMessage());
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("❌ Lỗi kết nối dữ liệu chi tiết phiên đấu giá!");
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 🎯 XỬ LÝ SỰ KIỆN: Đóng popup khi bấm nút ĐÓNG
     */
    @FXML
    void handleClose(ActionEvent event) {
        // Lấy Stage của cửa sổ hiện tại từ sự kiện Click và đóng nó lại
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}