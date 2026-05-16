package com.auction.client.controller;

import com.auction.common.model.Art;
import com.auction.common.model.Electronics;
import com.auction.common.model.Item;
import com.auction.common.model.Vehicle;
import com.auction.server.dao.ItemDAO;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
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

public class InsertItemController implements Initializable {

  @FXML
  private ImageView imgPreview;
  private String savedImagePath = null;
  private ItemDAO itemDAO = new ItemDAO();

  // thuộc tính chung
  @FXML private TextField txtName;
  @FXML private TextArea txtDescription;
  @FXML private TextField txtCondition;
  @FXML private TextField txtStartingPrice;

  // thuộc tính của vehicle
  @FXML private TextField txtHangXe;
  @FXML private TextField txtDongXe;
  @FXML private TextField txtNamSanXuat;
  @FXML private TextField txtSoKm;
  @FXML private TextField txtNhienLieu;
  @FXML private TextField txtBienSo;

  // thuộc tính của electronics
  @FXML private TextField txtHang;
  @FXML private TextField txtDongMay;
  @FXML private TextField txtBaoHanh;

  // thuộc tính của art
  @FXML private TextField txtTacGia;
  @FXML private TextField txtNamSangTac;
  @FXML private TextField txtChatLieu;
  @FXML private TextField txtGiayChungNhan;

  @FXML
  private ComboBox<String> cbItemType;
  @FXML
  private VBox vboxVehicle;
  @FXML
  private VBox vboxElectronics;
  @FXML
  private VBox vboxArt;

  // mặc định các vbox là ẩn
  private void hideAllSpecificFields() {
    vboxVehicle.setVisible(false);
    vboxVehicle.setManaged(false);

    vboxElectronics.setVisible(false);
    vboxElectronics.setManaged(false);

    vboxArt.setVisible(false);
    vboxArt.setManaged(false);
  }
  // chọn vbox để hiện
  private void hideAllDynamicBoxes() {
    if (vboxVehicle != null) {
      vboxVehicle.setVisible(false);
      vboxVehicle.setManaged(false);
    }
    if (vboxElectronics != null) {
      vboxElectronics.setVisible(false);
      vboxElectronics.setManaged(false);
    }
    if (vboxArt != null) {
      vboxArt.setVisible(false);
      vboxArt.setManaged(false);
    }
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    // thêm lựa chọn cho loại sản phẩm
    cbItemType.getItems().addAll("Phương tiện ", "Đồ điện tử ", "Nghệ thuật ");
    hideAllDynamicBoxes();
    cbItemType.setOnAction(this::handleItemTypeChange);
  }

  // 4.  hàm ân hiện vbox
  private void handleItemTypeChange(ActionEvent event) {
    String selectedType = cbItemType.getValue();
    hideAllSpecificFields();

    if (selectedType == null) return;

    // bật vbox tương ứng với lựa chọn
    if (selectedType.equals("Phương tiện ")) {
      vboxVehicle.setVisible(true);
      vboxVehicle.setManaged(true);
    } else if (selectedType.equals("Đồ điện tử ")) {
      vboxElectronics.setVisible(true);
      vboxElectronics.setManaged(true);
    } else if (selectedType.equals("Nghệ thuật ")) {
      vboxArt.setVisible(true);
      vboxArt.setManaged(true);
    }
  }

  // người dùng chọn ảnh
  @FXML
  public void handleChooseImage(ActionEvent event) {
    // hộp thoại chọn ảnh
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Chọn ảnh sản phẩm");

    // định dạng ảnh cho phép chọn
    fileChooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
    );

    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
    File selectedFile = fileChooser.showOpenDialog(stage);

    if (selectedFile != null) {
      try {
        // hiển thị ảnh xem trước
        Image image = new Image(selectedFile.toURI().toString());
        imgPreview.setImage(image);

        // 3. Copy ảnh vào thư mục dự án của bạn (ví dụ: folder "images" nằm trong thư mục gốc)
        // Tạo thư mục nếu chưa có
        File destDir = new File("images");
        if (!destDir.exists()) destDir.mkdirs();

        // Tạo đường dẫn file đích (Tên file gốc)
        Path targetPath = Paths.get("images", selectedFile.getName());

        // Copy file từ máy người dùng vào thư mục "images" của app
        Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 4. Lưu lại tên file/đường dẫn vào biến String để lát nữa nhét vào constructor Item
        savedImagePath = "images/" + selectedFile.getName();

      } catch (IOException e) {
        e.printStackTrace();
        System.out.println("Lỗi khi tải ảnh lên!");
      }
    }
  }
  @FXML
  public void handleSubmit(ActionEvent event) {
    String selectedType = cbItemType.getValue();
    Item newItem = null;

    String name = txtName.getText();
    String description = txtDescription.getText();
    String condition = txtCondition.getText();
    double startprice = Double.parseDouble(txtStartingPrice.getText());

    if (selectedType.equals("Phương tiện ")) {
      String hang = txtHangXe.getText();
      String dong = txtDongXe.getText();
      int nam = Integer.parseInt(txtNamSanXuat.getText());
      int sokmdi = Integer.parseInt(txtSoKm.getText());
      String nhienlieu = txtNhienLieu.getText();
      String bien = txtBienSo.getText();

      newItem = new Vehicle(0,name, description , startprice, condition, 2, savedImagePath,LocalDateTime.now() , hang ,dong ,nam , sokmdi, nhienlieu , bien );

    }else if (selectedType.equals("Đồ điện tử ")) {
      String hang = txtHang.getText();
      String dongmay =  txtDongMay.getText();
      int baohanh = Integer.parseInt(txtBaoHanh.getText());

      newItem = new Electronics(1,name, description, startprice, condition, 2, savedImagePath,LocalDateTime.now() , hang, dongmay, baohanh);

    }else{
      String tacgia = txtTacGia.getText();
      int namsangtac =Integer.parseInt( txtNamSangTac.getText() );
      String chatlieu =  txtChatLieu.getText();
      String giaychungnhan = txtGiayChungNhan.getText();

      newItem = new Art(2,name, description, startprice, condition, 2, savedImagePath,LocalDateTime.now(),tacgia ,namsangtac ,chatlieu ,giaychungnhan) ;

    }


    itemDAO.insertItem(newItem);
  }
}
