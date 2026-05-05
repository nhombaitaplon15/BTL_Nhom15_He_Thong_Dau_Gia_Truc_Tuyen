package com.auction.client.controller;

import com.auction.common.model.Auction;
import com.auction.service.AdminService;

import com.auction.service.ItemService;
import com.auction.service.ManagerService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import javafx.scene.control.*;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class AdminController implements Initializable {

    @FXML private TableView<Auction> table;
    @FXML private TableColumn<Auction, Integer> id;
    @FXML private TableColumn<Auction, String> name;
    @FXML private TableColumn<Auction, Integer> price;
    @FXML private TableColumn<Auction, String> status;
    @FXML private Label statusLabel;

    private ObservableList<Auction> auctionList;

    private ItemService itemService = new ItemService();
    private ManagerService managerService = new ManagerService(itemService);
    private AdminService adminService = new AdminService(managerService);

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        id.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(
                        cellData.getValue().getAuctionId()
                ).asObject()
        );

        name.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().getItem().getName()
                )
        );

        price.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(
                        (int) cellData.getValue().getCurrentPrice()
                ).asObject()
        );

        status.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().getAuctionStatus()
                )
        );

        loadAuctions();
    }

    private void loadAuctions() {

        auctionList = FXCollections.observableArrayList(
                adminService.getPendingAuctions()
        );

        table.setItems(auctionList);
    }

    @FXML
    private void handleApprove() {

        Auction selectedAuction =
                table.getSelectionModel().getSelectedItem();

        if (selectedAuction == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Thông báo");
            alert.setContentText("Bạn chưa chọn auction");
            alert.showAndWait();
            return;
        }

        Alert confirmation =
                new Alert(Alert.AlertType.CONFIRMATION);

        confirmation.setTitle("Approve Auction");
        confirmation.setHeaderText("Xác nhận duyệt phiên đấu giá?");
        confirmation.setContentText(
                selectedAuction.getItem().getName()
        );

        Optional<ButtonType> result =
                confirmation.showAndWait();

        if (result.isPresent()
                && result.get() == ButtonType.OK) {

            boolean success =
                    adminService.approveAuction(
                            selectedAuction.getAuctionId()
                    );

            if (success) {

                statusLabel.setText(
                        "Auction đã chuyển sang RUNNING"
                );

                statusLabel.setStyle(
                        "-fx-text-fill: green; -fx-font-weight: bold;"
                );

                loadAuctions();

            } else {
                statusLabel.setText("Approve thất bại");
            }
        }
    }
}