package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.common.model.Item;
import com.auction.server.dao.AuctionDAO;
import com.auction.service.ItemService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

import javax.swing.text.TabableView;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class The_Home_Page_Seller_View_Controller implements Initializable {
    @FXML
    private AnchorPane mainAnchorPane;
    @FXML
    private BorderPane mainBorderPane;
    @FXML private TableView<Auction> tableViewAuction;

    // Chú ý: Cột Item và HighestBidder đổi về kiểu Integer
    @FXML private TableColumn<Auction, Integer> colAuctionId;
    @FXML private TableColumn<Auction, Integer> colItem;
    @FXML private TableColumn<Auction, LocalDateTime> colStartTime;
    @FXML private TableColumn<Auction, LocalDateTime> colEndTime;
    @FXML private TableColumn<Auction, Double> colCurrentPrice;
    @FXML private TableColumn<Auction, Integer> colHighestBidder;
    @FXML private TableColumn<Auction, String> colStatus;

    private AuctionDAO auctionDAO = new AuctionDAO();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cấu hình các cột (Tên chuỗi truyền vào phải TRÙNG với tên biến trong class Auction)
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItem.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        colStartTime.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        colEndTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colHighestBidder.setCellValueFactory(new PropertyValueFactory<>("currentWinnerId"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("auctionStatus"));

        // Load dữ liệu
        loadAuction();
    }

    public void loadAuction() {
        // Giả sử ID của người bán đang đăng nhập là 1
        int currentSellerId = 2;

        // Lưu ý: Bạn cần đảm bảo trong AuctionDAO đã có hàm getAuctionsBySeller nhé
        List<Auction> auctions = auctionDAO.getAuctionsBySeller(currentSellerId);

        // Đổ dữ liệu vào bảng
        ObservableList<Auction> observableList = FXCollections.observableArrayList(auctions);
        tableViewAuction.setItems(observableList);
    }
    private void loadView(String fxmlFileName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + fxmlFileName + ".fxml"));
            Parent view = loader.load();
            mainBorderPane.setCenter(view);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Không tìm thấy file giao diện: " + fxmlFileName);
        }
    }

    @FXML
    public void showSearchItem(ActionEvent event){
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/SearchItem.fxml"));
            Parent searchView = loader.load();

            // Chỉnh tọa độ (X, Y) để form tìm kiếm nằm ở giữa hoặc vị trí bạn muốn
            searchView.setLayoutX(79);
            searchView.setLayoutY(23);

            // Thêm vào AnchorPane ngoài cùng và đẩy nó lên lớp trên cùng
            mainAnchorPane.getChildren().add(searchView);
            searchView.toFront();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    @FXML
    public void showInsertItem(ActionEvent event){
        try {
            loadView("InsertItemView");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void Welcome_back(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/WelcomeView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}