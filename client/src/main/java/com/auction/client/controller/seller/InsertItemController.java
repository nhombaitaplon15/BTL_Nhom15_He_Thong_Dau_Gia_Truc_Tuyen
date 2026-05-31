package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient; // Cập nhật import SocketClient
import com.auction.client.core.ClientSession;

import com.auction.common.model.Art;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class InsertItemController implements Initializable {

  @FXML private ImageView imgPreview;
  private String savedImagePath = null;

  // Thuộc tính chung
  @FXML private TextField txtName;
  @FXML private TextArea txtDescription;
  @FXML private TextField txtCondition;
  @FXML private TextField txtStartingPrice;

  // Thuộc tính Vehicle
  @FXML private TextField txtHangXe;
  @FXML private TextField txtDongXe;
  @FXML private TextField txtNamSanXuat;
  @FXML private TextField txtSoKm;
  @FXML private TextField txtNhienLieu;
  @FXML private TextField txtBienSo;

  // Thuộc tính Electronics
  @FXML private TextField txtHang;
  @FXML private TextField txtDongMay;
  @FXML private TextField txtBaoHanh;

  // Thuộc tính Art
  @FXML private TextField txtTacGia;
  @FXML private TextField txtNamSangTac;
  @FXML private TextField txtChatLieu;
  @FXML private TextField txtGiayChungNhan;

  @FXML private ComboBox<String> cmbCategory;
  @FXML private VBox vboxVehicle;
  @FXML private VBox vboxElectronics;
  @FXML private VBox vboxArt;

  // ── Handler references ──
  private final Consumer<Message> onItemAdded = this::handleItemAdded;
  private final Consumer<Message> onItemError = this::handleItemError;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    registerNetworkHandlers();

    cmbCategory.setOnAction(e -> {
      String selected = cmbCategory.getValue();
      vboxVehicle.setVisible(false); vboxVehicle.setManaged(false);
      vboxElectronics.setVisible(false); vboxElectronics.setManaged(false);
      vboxArt.setVisible(false); vboxArt.setManaged(false);

      if ("Phương tiện".equals(selected)) {
        vboxVehicle.setVisible(true); vboxVehicle.setManaged(true);
      } else if ("Đồ điện tử".equals(selected)) {
        vboxElectronics.setVisible(true); vboxElectronics.setManaged(true);
      } else if ("Nghệ thuật".equals(selected)) {
        vboxArt.setVisible(true); vboxArt.setManaged(true);
      }
    });
  }

  // ════════════════════════════════════════════════════
  // ĐĂNG KÝ / HUỶ HANDLER
  // ════════════════════════════════════════════════════
  private void registerNetworkHandlers() {
    MessageRouter.getInstance().register(ResponseCode.SELLER_ITEMS_RESULT, onItemAdded);
    MessageRouter.getInstance().register(ResponseCode.ERROR_MESSAGE, onItemError);
  }

  /** GỌI KHI VIEW BỊ ĐÓNG */
  public void cleanupHandlers() {
    // SỬA: Hàm unregister chỉ nhận 1 tham số là ResponseCode theo thiết kế của MessageRouter.java
    MessageRouter.getInstance().unregister(ResponseCode.SELLER_ITEMS_RESULT);
    MessageRouter.getInstance().unregister(ResponseCode.ERROR_MESSAGE);
  }

  // ════════════════════════════════════════════════════
  // XỬ LÝ RESPONSE
  // ════════════════════════════════════════════════════
  private void handleItemAdded(Message msg) {
    showSuccess("Thêm sản phẩm thành công!");
    clearForm();
  }

  private void handleItemError(Message msg) {
    // SỬA: Dùng getMessage() thay vì getContent() để lấy chuỗi thông báo từ Message.java
    showError("Thêm sản phẩm thất bại: " + msg.getMessage());
  }

  // ════════════════════════════════════════════════════
  // ACTIONS
  // ════════════════════════════════════════════════════

  @FXML
  public void handleChooseImage(ActionEvent event) {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Chọn ảnh sản phẩm");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
    File selectedFile = fileChooser.showOpenDialog(stage);

    if (selectedFile != null) {
      try {
        imgPreview.setImage(new Image(selectedFile.toURI().toString()));

        File destDir = new File("images");
        if (!destDir.exists()) destDir.mkdirs();

        Path targetPath = Paths.get("images", selectedFile.getName());
        Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        savedImagePath = "images/" + selectedFile.getName();

      } catch (IOException e) {
        e.printStackTrace();
        showError("Lỗi khi tải ảnh lên!");
      }
    }
  }

  @FXML
  public void handleSubmit(ActionEvent event) {
    String selectedType = cmbCategory.getValue();
    if (selectedType == null || selectedType.isBlank()) {
      showError("Vui lòng chọn danh mục sản phẩm!"); return;
    }

    // SỬA: Lấy sellerId từ ClientSession ở phía Client.
    // Tuyệt đối không gọi ClientHandler ở đây vì ClientHandler nằm ở bộ nhớ của Server.
    // Cần đảm bảo ClientSession.getInstance().getLoggedInUserId() (hoặc tương tự) đã được định nghĩa.
    int sellerId = ClientSession.getInstance().getUserId();

    if (sellerId <= 0) { // Sửa lại logic check ID tuỳ vào dự án của bạn (thường ID > 0)
      showError("Lỗi phiên đăng nhập! Vui lòng đăng nhập lại."); return;
    }

    try {
      String name = txtName.getText().trim();
      String description = txtDescription.getText().trim();
      String condition = txtCondition.getText().trim();
      double startPrice = Double.parseDouble(txtStartingPrice.getText().trim());

      if (name.isEmpty() || description.isEmpty() || condition.isEmpty()) {
        showError("Vui lòng điền đầy đủ thông tin!"); return;
      }

      Item newItem;

      switch (selectedType) {
        case "Phương tiện" -> {
          String hang = txtHangXe.getText().trim();
          String dong = txtDongXe.getText().trim();
          int nam = Integer.parseInt(txtNamSanXuat.getText().trim());
          int soKm = Integer.parseInt(txtSoKm.getText().trim());
          String nhienLieu = txtNhienLieu.getText().trim();
          String bien = txtBienSo.getText().trim();
          newItem = new Vehicle(0, name, description, startPrice, condition,
              sellerId, savedImagePath, LocalDateTime.now(),
              hang, dong, nam, soKm, nhienLieu, bien);
        }
        case "Đồ điện tử" -> {
          String hang = txtHang.getText().trim();
          String dongMay = txtDongMay.getText().trim();
          int baoHanh = Integer.parseInt(txtBaoHanh.getText().trim());
          newItem = new Electronics(0, name, description, startPrice, condition,
              sellerId, savedImagePath, LocalDateTime.now(),
              hang, dongMay, baoHanh);
        }
        default -> { // Nghệ thuật
          String tacGia = txtTacGia.getText().trim();
          int namSangTac = Integer.parseInt(txtNamSangTac.getText().trim());
          String chatLieu = txtChatLieu.getText().trim();
          Boolean coGiayChungNhan = Boolean.valueOf(txtGiayChungNhan.getText().trim());
          newItem = new Art(0, name, description, startPrice, condition,
              sellerId, savedImagePath, LocalDateTime.now(),
              tacGia, namSangTac, chatLieu, coGiayChungNhan);
        }
      }

      // SỬA: Gửi request thông qua SocketClient với phương thức chuẩn `sendRequest(RequestCode, Object)`
      SocketClient.getInstance().sendRequest(RequestCode.SELLER_ADD_ITEM, newItem);

      // Ghi chú: Nếu server của bạn đã bổ sung SELLER_ADD_ITEM vào enum RequestCode,
      // hãy sửa tham số trên thành RequestCode.SELLER_ADD_ITEM.

    } catch (NumberFormatException e) {
      showError("Giá trị số không hợp lệ! Kiểm tra lại giá, năm SX, số km...");
    } catch (Exception e) {
      showError("Lỗi khi tạo sản phẩm: " + e.getMessage());
    }
  }

  // ════════════════════════════════════════════════════
  // HELPERS
  // ════════════════════════════════════════════════════
  private void clearForm() {
    txtName.clear(); txtDescription.clear(); txtCondition.clear(); txtStartingPrice.clear();
    txtHangXe.clear(); txtDongXe.clear(); txtNamSanXuat.clear();
    txtSoKm.clear(); txtNhienLieu.clear(); txtBienSo.clear();
    txtHang.clear(); txtDongMay.clear(); txtBaoHanh.clear();
    txtTacGia.clear(); txtNamSangTac.clear(); txtChatLieu.clear(); txtGiayChungNhan.clear();
    cmbCategory.setValue(null);
    savedImagePath = null;
    if (imgPreview != null) imgPreview.setImage(null);
  }

  private void showError(String msg) {
    Alert a = new Alert(Alert.AlertType.ERROR);
    a.setTitle("Lỗi"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
  }

  private void showSuccess(String msg) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.setTitle("Thành công"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
  }
}