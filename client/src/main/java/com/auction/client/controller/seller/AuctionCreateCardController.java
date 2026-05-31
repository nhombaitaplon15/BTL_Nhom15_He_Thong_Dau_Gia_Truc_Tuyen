package com.auction.client.controller.seller;

import com.auction.common.model.Item;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.control.Label;
import java.awt.*;
import  javafx.scene.control.Button;

/**
 * AuctionCreateCardController — Không cần networking.
 *
 * Class này chỉ hiển thị thông tin Item (đã có sẵn từ cache của
 * AuctionManagementController) và gọi callback khi nhấn "Tạo phiên".
 * Không cần gọi server hay ItemService vì Item đã được truyền vào qua setData().
 *
 * Tất cả networking nằm ở tầng trên (AuctionManagementController + CreateAuctionController).
 */
public class AuctionCreateCardController {

  @FXML
  private ImageView imgProduct;
  @FXML private Label  lblName;
  @FXML private Label     lblMeta;
  @FXML private Label     lblPrice;
  @FXML private Button btnCreate;

  private Item     currentItem;
  private Runnable onCreateCallback;

  public void setData(Item item) {
    this.currentItem = item;
    lblName .setText(item.getName());
    lblMeta .setText(item.getItemType() + " · Đã được admin duyệt");
    lblPrice.setText(CardUtils.formatMoney(item.getStartingPrice()) + " UETệ");
    CardUtils.loadImage(imgProduct, item.getImgItem());
  }

  public void setOnCreateCallback(Runnable callback) {
    this.onCreateCallback = callback;
  }

  @FXML
  private void onCreateAuction() {
    if (onCreateCallback != null) onCreateCallback.run();
  }
}