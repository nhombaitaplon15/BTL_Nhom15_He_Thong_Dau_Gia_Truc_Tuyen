package com.auction.client.controller;

import com.auction.common.model.Item;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

import java.io.IOException;

public class AuctionCreateListCell extends ListCell<Item> {

  private HBox root;
  private AuctionCreateCardController cellController;

  public AuctionCreateListCell() {
    try {
      FXMLLoader loader = new FXMLLoader(
          getClass().getResource("/view/AuctionCreateCell.fxml"));
      root = loader.load();
      cellController = loader.getController();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void updateItem(Item item, boolean empty) {
    super.updateItem(item, empty);
    if (empty || item == null) {
      setGraphic(null);
    } else {
      cellController.setData(item);
      setGraphic(root);
    }
    setStyle("-fx-background-color: transparent; -fx-padding: 4 0 4 0;");
  }
}
