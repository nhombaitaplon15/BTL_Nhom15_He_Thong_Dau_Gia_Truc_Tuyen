package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.DBConnection;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

/**
 * Controller cho CreateAuctionDialog.fxml
 *
 * Luồng hoạt động:
 *   1. AuctionManagementController gọi setItem(item) để truyền item vào
 *   2. initialize() hiển thị thông tin item lên preview
 *   3. Người dùng điền form → bấm "Gửi tạo phiên"
 *   4. onConfirm() validate → insert vào DB → gọi onSubmitCallback
 *   5. AuctionManagementController refresh tab Chờ duyệt
 */
public class CreateAuctionController {

  // ══════════════════════════════════════════════════
  // FXML — PRODUCT PREVIEW
  // ══════════════════════════════════════════════════
  @FXML private ImageView imgItemPreview;
  @FXML private Label     lblItemName;
  @FXML private Label     lblItemMeta;
  @FXML private Label     lblStartingPrice;

  // ══════════════════════════════════════════════════
  // FXML — FORM FIELDS
  // ══════════════════════════════════════════════════
  @FXML private DatePicker dtpStartTime;
  @FXML private DatePicker dtpEndTime;
  @FXML private TextField  txtMinIncrement;
  @FXML private TextField  txtBuyNowPrice;
  @FXML private TextField  txtNote;
  @FXML private ComboBox<String> cmbAntiSnipeWindow;
  @FXML private ComboBox<String> cmbAntiSnipeExtend;

  // ══════════════════════════════════════════════════
  // FXML — BUTTONS
  // ══════════════════════════════════════════════════
  @FXML private Button btnConfirm;
  @FXML private Button btnCancel;

  // ══════════════════════════════════════════════════
  // STATE
  // ══════════════════════════════════════════════════
  /** Item được truyền vào từ AuctionManagementController */
  private Item currentItem;

  /**
   * Callback gọi sau khi tạo phiên thành công.
   * AuctionManagementController truyền vào để refresh danh sách.
   */
  private Runnable onSubmitCallback;

  private final AuctionDAO auctionDAO = new AuctionDAO();

  // ══════════════════════════════════════════════════
  // INITIALIZE
  // ══════════════════════════════════════════════════
  @FXML
  public void initialize() {
    // Set giá trị mặc định cho ComboBox
    cmbAntiSnipeWindow.getSelectionModel().selectFirst(); // "Tắt"
    cmbAntiSnipeExtend.getSelectionModel().selectFirst(); // "60 giây"

    // Gợi ý ngày bắt đầu = ngày mai, kết thúc = 3 ngày sau
    dtpStartTime.setValue(LocalDate.now().plusDays(1));
    dtpEndTime.setValue(LocalDate.now().plusDays(3));

    // Chỉ cho nhập số vào txtMinIncrement và txtBuyNowPrice
    addNumberOnlyListener(txtMinIncrement);
    addNumberOnlyListener(txtBuyNowPrice);
  }

  // ══════════════════════════════════════════════════
  // SETTER — gọi từ AuctionManagementController
  // ══════════════════════════════════════════════════

  /**
   * Nhận Item từ màn hình cha, điền thông tin preview.
   * Phải gọi trước khi dialog hiển thị.
   */
  public void setItem(Item item) {
    this.currentItem = item;

    // Điền thông tin lên preview
    lblItemName.setText(item.getName());
    lblItemMeta.setText(item.getItemType()
        + " · Giá khởi điểm đã đặt");
    lblStartingPrice.setText(formatMoney(item.getStartingPrice()) + "đ");

    // Load ảnh nếu có
    String imagePathFromDB = item.getImgItem(); // Lấy đường dẫn bạn đã lưu trong CSDL

    if (imagePathFromDB != null && !imagePathFromDB.trim().isEmpty()) {
      File imageFile = new File(imagePathFromDB);

      // Kiểm tra xem file có thực sự tồn tại trong ổ cứng không
      if (imageFile.exists()) {
        // Lệnh toURI().toString() sẽ tự động thêm "file:/" và chuyển đổi các ký tự khoảng trắng cho chuẩn
        Image image = new Image(imageFile.toURI().toString());

        // imageViewProduct là cái biến ImageView trên giao diện của bạn
        imgItemPreview.setImage(image);
      } else {
        System.out.println("Lỗi: Không tìm thấy file ảnh tại đường dẫn: " + imagePathFromDB);
        // Ở đây bạn có thể set một ảnh mặc định (default image) nếu không tìm thấy ảnh
      }
    }
  }

  /** Callback để AuctionManagementController refresh sau khi tạo phiên xong */
  public void setOnSubmitCallback(Runnable callback) {
    this.onSubmitCallback = callback;
  }

  // ══════════════════════════════════════════════════
  // ACTION HANDLERS
  // ══════════════════════════════════════════════════

  @FXML
  private void onConfirm() {
    // 1. Validate form
    if (!validateForm()) return;

    // 2. Đọc dữ liệu từ form
    LocalDateTime startTime = dtpStartTime.getValue().atTime(LocalTime.of(8, 0));
    LocalDateTime endTime   = dtpEndTime.getValue().atTime(LocalTime.of(20, 0));

    double minIncrement = parseDoubleSafe(txtMinIncrement.getText(), 0);
    double buyNowPrice  = parseDoubleSafe(txtBuyNowPrice.getText(), 0);
    String note         = txtNote.getText().trim();

    // 3. Parse anti-snipe
    int antiSnipeWindowSecs = parseAntiSnipeWindow(
        cmbAntiSnipeWindow.getValue());
    int antiSnipeExtendSecs = parseAntiSnipeExtend(
        cmbAntiSnipeExtend.getValue());

    // 4. Tạo đối tượng Auction
    Auction newAuction = new Auction();
    newAuction.setItemId(currentItem.getItemId());
    newAuction.setSellerId(currentItem.getSellerId());
    newAuction.setAuctionStatus("WAITING_FOR_ADMIN"); // Luôn chờ duyệt
    newAuction.setStartingPrice(currentItem.getStartingPrice());
    newAuction.setCurrentPrice(currentItem.getStartingPrice()); // Giá HT = KĐ ban đầu
    newAuction.setTotalBids(0);
    newAuction.setCurrentWinnerId(null);
    newAuction.setStartTime(startTime);
    newAuction.setEndTime(endTime);
    newAuction.setCreatedAt(LocalDateTime.now());

    // TODO: Nếu model Auction có thêm field minIncrement, buyNowPrice, note
    // thì set ở đây:
    // newAuction.setMinIncrement(minIncrement);
    // newAuction.setBuyNowPrice(buyNowPrice > 0 ? buyNowPrice : null);
    // newAuction.setNote(note);
    // newAuction.setAntiSnipeWindow(antiSnipeWindowSecs);
    // newAuction.setAntiSnipeExtend(antiSnipeExtendSecs);

    // 5. Insert vào DB qua AuctionDAO
    btnConfirm.setDisable(true); // Tránh double-click
    btnConfirm.setText("Đang gửi...");

    boolean success = false;
    try (Connection conn = DBConnection.getConnection()) {
      conn.setAutoCommit(false);
      try {
        success = auctionDAO.insertAuction(newAuction);
        conn.commit();
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      showError("Lỗi kết nối database: " + e.getMessage());
      resetConfirmButton();
      return;
    } catch (Exception e) {
      showError("Lỗi tạo phiên: " + e.getMessage());
      resetConfirmButton();
      return;
    }

    if (success) {
      showSuccess();
      // 6. Gọi callback để màn cha refresh
      if (onSubmitCallback != null) onSubmitCallback.run();
      // 7. Đóng dialog
      closeDialog();
    } else {
      showError("Tạo phiên thất bại. Vui lòng thử lại.");
      resetConfirmButton();
    }
  }

  @FXML
  private void onCancel() {
    closeDialog();
  }

  // ══════════════════════════════════════════════════
  // VALIDATE
  // ══════════════════════════════════════════════════
  private boolean validateForm() {
    // Kiểm tra item
    if (currentItem == null) {
      showError("Không tìm thấy thông tin sản phẩm!");
      return false;
    }

    // Kiểm tra ngày bắt đầu
    if (dtpStartTime.getValue() == null) {
      showError("Vui lòng chọn ngày bắt đầu phiên!");
      dtpStartTime.requestFocus();
      return false;
    }

    // Kiểm tra ngày kết thúc
    if (dtpEndTime.getValue() == null) {
      showError("Vui lòng chọn ngày kết thúc phiên!");
      dtpEndTime.requestFocus();
      return false;
    }

    // Kết thúc phải sau bắt đầu
    if (!dtpEndTime.getValue().isAfter(dtpStartTime.getValue())) {
      showError("Ngày kết thúc phải sau ngày bắt đầu!");
      dtpEndTime.requestFocus();
      return false;
    }

    // Ngày bắt đầu phải từ ngày mai trở đi
    if (!dtpStartTime.getValue().isAfter(LocalDate.now())) {
      showError("Ngày bắt đầu phải từ ngày mai trở đi!");
      dtpStartTime.requestFocus();
      return false;
    }

    // Bước giá bắt buộc nhập
    String incText = txtMinIncrement.getText().trim();
    if (incText.isEmpty()) {
      showError("Vui lòng nhập bước giá tối thiểu!");
      txtMinIncrement.requestFocus();
      return false;
    }

    double minIncrement = parseDoubleSafe(incText, -1);
    if (minIncrement <= 0) {
      showError("Bước giá phải là số dương!");
      txtMinIncrement.requestFocus();
      return false;
    }

    // Giá mua ngay (nếu có) phải > giá khởi điểm
    String buyNowText = txtBuyNowPrice.getText().trim();
    if (!buyNowText.isEmpty()) {
      double buyNow = parseDoubleSafe(buyNowText, -1);
      if (buyNow <= 0) {
        showError("Giá mua ngay phải là số dương!");
        txtBuyNowPrice.requestFocus();
        return false;
      }
      if (buyNow <= currentItem.getStartingPrice()) {
        showError("Giá mua ngay phải lớn hơn giá khởi điểm ("
            + formatMoney(currentItem.getStartingPrice()) + "đ)!");
        txtBuyNowPrice.requestFocus();
        return false;
      }
    }

    return true;
  }

  // ══════════════════════════════════════════════════
  // HELPER METHODS
  // ══════════════════════════════════════════════════

  /** Parse chuỗi "30 giây" → 30, "Tắt" → 0 */
  private int parseAntiSnipeWindow(String value) {
    if (value == null || value.equals("Tắt")) return 0;
    try {
      return Integer.parseInt(value.replace(" giây", "").trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** Parse chuỗi "60 giây" → 60 */
  private int parseAntiSnipeExtend(String value) {
    if (value == null) return 60;
    try {
      return Integer.parseInt(value.replace(" giây", "").trim());
    } catch (NumberFormatException e) {
      return 60;
    }
  }

  /** Parse số an toàn, trả về defaultValue nếu lỗi */
  private double parseDoubleSafe(String text, double defaultValue) {
    try {
      // Xóa dấu phẩy nếu user nhập theo kiểu 500,000
      return Double.parseDouble(text.replaceAll("[,.]", "")
          .replaceAll("\\s+", ""));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /** Format số thành tiền VN */
  private String formatMoney(double amount) {
    return NumberFormat.getNumberInstance(new Locale("vi", "VN"))
        .format((long) amount);
  }

  /** Chỉ cho nhập số vào TextField */
  private void addNumberOnlyListener(TextField field) {
    field.textProperty().addListener((obs, oldVal, newVal) -> {
      if (!newVal.matches("\\d*")) {
        field.setText(newVal.replaceAll("[^\\d]", ""));
      }
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

  private void showError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Lỗi");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  private void showSuccess() {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Thành công");
    alert.setHeaderText(null);
    alert.setContentText("Phiên đấu giá đã được gửi!\nAdmin sẽ duyệt và kích hoạt phiên cho bạn.");
    alert.showAndWait();
  }
}
