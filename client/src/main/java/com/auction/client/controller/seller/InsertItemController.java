package com.auction.client.controller.seller;

import com.auction.client.core.MessageRouter;
import com.auction.client.core.SocketClient;
import com.auction.client.core.ClientSession;

import com.auction.common.model.Art;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;
import com.auction.common.network.Message;
import com.auction.common.network.RequestCode;
import com.auction.common.network.ResponseCode;

import javafx.application.Platform;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class InsertItemController implements Initializable {

  @FXML private ComboBox<String> cmbCategory;
  @FXML private VBox vboxVehicle, vboxArt, vboxElectronics;

  @FXML private TextField txtName, txtStartingPrice;
  @FXML private TextArea txtDescription;
  @FXML private TextField txtCondition;
  @FXML private ImageView imgPreview;

  @FXML private TextField txtHangXe, txtDongXe, txtNamSanXuat, txtSoKm, txtNhienLieu, txtBienSo;
  @FXML private TextField txtHang, txtDongMay, txtBaoHanh;
  @FXML private TextField txtTacGia, txtNamSangTac, txtChatLieu, txtGiayChungNhan;

  private String savedImagePath = null;
  private File selectedImageFile = null;

  private final Consumer<Message> onInsertSuccess = this::handleInsertSuccess;
  private final Consumer<Message> onInsertFailed = this::handleInsertFailed;

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    // SỬA ĐỔI: Thay vì dùng setOnAction, chúng ta lắng nghe trực tiếp sự thay đổi của ValueProperty
    cmbCategory.valueProperty().addListener((observable, oldValue, newValue) -> {
      handleCategoryChange(newValue);
    });

    MessageRouter.getInstance().register(ResponseCode.SELLER_ITEMS_RESULT, onInsertSuccess);
    MessageRouter.getInstance().register(ResponseCode.ERROR_MESSAGE, onInsertFailed);
  }

  // SỬA ĐỔI: Hàm nhận trực tiếp giá trị chuỗi mới để xử lý
  private void handleCategoryChange(String selected) {
    vboxVehicle.setVisible(false); vboxVehicle.setManaged(false);
    vboxArt.setVisible(false);     vboxArt.setManaged(false);
    vboxElectronics.setVisible(false); vboxElectronics.setManaged(false);

    if (selected == null) return;

    if (selected.contains("Phương tiện")) {
      vboxVehicle.setVisible(true);
      vboxVehicle.setManaged(true);
    }
    else if (selected.contains("Nghệ thuật")) {
      vboxArt.setVisible(true);
      vboxArt.setManaged(true);
    }
    else if (selected.contains("Điện tử")) {
      vboxElectronics.setVisible(true);
      vboxElectronics.setManaged(true);
    }
  }

  public void cleanupHandlers() {
    MessageRouter.getInstance().unregister(ResponseCode.SELLER_ITEMS_RESULT);
    MessageRouter.getInstance().unregister(ResponseCode.ERROR_MESSAGE);
  }



  @FXML
  private void handleChooseImage(ActionEvent event) {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Chọn ảnh sản phẩm");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
    );
    File file = fileChooser.showOpenDialog(((Node) event.getSource()).getScene().getWindow());

    if (file != null) {
      this.selectedImageFile = file;
      imgPreview.setImage(new Image(file.toURI().toString(), true));

      String fileName = System.currentTimeMillis() + "_" + file.getName();
      this.savedImagePath = "src/main/resources/img/" + fileName;
    }
  }

  @FXML
  private void handleSave(ActionEvent event) {
    String category = cmbCategory.getValue();
    if (category == null) {
      showError("Vui lòng chọn phân loại sản phẩm!");
      return;
    }

    String name = txtName.getText().trim();
    String desc = txtDescription.getText().trim();
    String condition = txtCondition.getText().trim();
    String priceStr = txtStartingPrice.getText().trim();

    if (name.isEmpty() || desc.isEmpty() || condition.isEmpty() || priceStr.isEmpty()) {
      showError("Vui lòng điền đầy đủ các thông tin chung!");
      return;
    }

    try {
      double price = Double.parseDouble(priceStr);
      int currentUserId = ClientSession.getInstance().getUserId();
      Item newItem = null;

      if (category.contains("Phương tiện")) {
        Vehicle v = new Vehicle();
        v.setMake(txtHangXe.getText().trim());
        v.setModelVehicle(txtDongXe.getText().trim());
        v.setManufactureYear(Integer.parseInt(txtNamSanXuat.getText().trim()));
        v.setMileage(Integer.parseInt(txtSoKm.getText().trim()));
        v.setFuelType(txtNhienLieu.getText().trim());
        v.setLicensePlate(txtBienSo.getText().trim());
        v.setItemType("VEHICLE");
        newItem = v;
      } else if (category.contains("Nghệ thuật")) {
        Art a = new Art();
        a.setArtist(txtTacGia.getText().trim());
        a.setYearCreated(Integer.parseInt(txtNamSangTac.getText().trim()));
        a.setMedium(txtChatLieu.getText().trim());
        a.setHasCertificate(txtGiayChungNhan.getText().trim().equalsIgnoreCase("có") || txtGiayChungNhan.getText().trim().equalsIgnoreCase("true"));
        a.setItemType("ART");
        newItem = a;
      } else if (category.contains("Điện tử")) {
        Electronics e = new Electronics();
        e.setBrand(txtHang.getText().trim());
        e.setModel(txtDongMay.getText().trim());
        e.setWarrantyMonths(Integer.parseInt(txtBaoHanh.getText().trim()));
        e.setItemType("ELECTRONICS");
        newItem = e;
      }

      if (newItem != null) {
        newItem.setName(name);
        newItem.setDescription(desc);
        newItem.setItemCondition(condition);
        newItem.setStartingPrice(price);
        newItem.setSellerId(currentUserId);
        newItem.setCreatedAt(LocalDateTime.now());

        Item finalItem = newItem;

        // Bắt đầu luồng chạy ngầm để không làm đơ giao diện khi tải ảnh lên mạng
        CompletableFuture.runAsync(() -> {
          try {
            // 1. Kiểm tra xem người dùng có chọn ảnh từ máy tính hay không
            if (selectedImageFile != null && selectedImageFile.exists()) {
              // Gọi công cụ tải ảnh lên Imgur đã chuẩn bị
              String imgurUrl = ImgurUploader.uploadImageToCloud(selectedImageFile);

              if (imgurUrl != null) {
                // Tải thành công -> Gắn link mạng trực tiếp vào Item
                finalItem.setImgItem(imgurUrl);
              } else {
                // Tải thất bại do mất mạng hoặc Imgur bảo trì
                Platform.runLater(() -> showError("Không thể tải ảnh lên hệ thống đám mây. Vui lòng kiểm tra mạng!"));
                return; // Ngắt ngang, không gửi sản phẩm lên Server nữa
              }
            }

            // 2. Gửi đối tượng Item (lúc này đã chứa link http://...) lên Database
            SocketClient.getInstance().sendRequest(RequestCode.SELLER_ADD_ITEM, finalItem);

          } catch (Exception ex) {
            Platform.runLater(() -> showError("Lỗi hệ thống trong quá trình đăng tải: " + ex.getMessage()));
          }
        });
      }

    } catch (NumberFormatException e) {
      showError("Giá trị số không hợp lệ! Kiểm tra lại giá, năm SX, số km...");
    } catch (Exception e) {
      showError("Lỗi khi tạo sản phẩm: " + e.getMessage());
    }
  }

  private void handleInsertSuccess(Message msg) {
    Platform.runLater(() -> {
      showSuccess("Đã thêm sản phẩm thành công!");
      clearForm();
    });
  }

  private void handleInsertFailed(Message msg) {
    Platform.runLater(() -> showError("Thêm sản phẩm thất bại: " + msg.getMessage()));
  }

  private void clearForm() {
    txtName.clear(); txtDescription.clear(); txtCondition.clear(); txtStartingPrice.clear();
    txtHangXe.clear(); txtDongXe.clear(); txtNamSanXuat.clear();
    txtSoKm.clear(); txtNhienLieu.clear(); txtBienSo.clear();
    txtHang.clear(); txtDongMay.clear(); txtBaoHanh.clear();
    txtTacGia.clear(); txtNamSangTac.clear(); txtChatLieu.clear(); txtGiayChungNhan.clear();
    cmbCategory.setValue(null);
    savedImagePath = null;
    selectedImageFile = null;
    if (imgPreview != null) imgPreview.setImage(null);
  }

  private void showError(String msg) {
    Alert a = new Alert(Alert.AlertType.ERROR);
    a.setTitle("Lỗi");
    a.setHeaderText(null);
    a.setContentText(msg);
    a.showAndWait();
  }

  private void showSuccess(String msg) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.setTitle("Thành công");
    a.setHeaderText(null);
    a.setContentText(msg);
    a.showAndWait();
  }
}