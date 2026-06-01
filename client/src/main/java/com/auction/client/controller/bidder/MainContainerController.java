package com.auction.client.controller.bidder;

import com.auction.common.model.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainContainerController implements Initializable {

    @FXML private VBox sideMenu;
    @FXML private AnchorPane contentArea;
    @FXML private Label lblAccountName;
    @FXML private Label lblBalance;
    @FXML private Button btnMenuLive;
    @FXML private Button btnMenuHistory;

    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (lblAccountName != null) lblAccountName.setText("Đang tải...");
        if (lblBalance != null) lblBalance.setText("0 đ");
    }

    public void setUserData(User user) {
        this.currentUser = user;

        if (lblAccountName != null) {
            String displayName = (user.getUsername() != null && !user.getUsername().trim().isEmpty())
                    ? user.getUsername()
                    : user.getUsername();
            lblAccountName.setText(displayName);
        }

        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f đ", user.getBalance()));
        }

        setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
    }

    public void updateBalance(double newBalance) {
        if (this.currentUser != null) {
            this.currentUser.setBalance(newBalance);
        }
        if (lblBalance != null) {
            lblBalance.setText(String.format("%,.0f đ", newBalance));
        }
    }

    public void setPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent node = loader.load();

            Object childController = loader.getController();

            if (childController instanceof BiddingHistoryController) {
                BiddingHistoryController historyCtrl = (BiddingHistoryController) childController;
                historyCtrl.setMainContainer(this);
                // FIX: Truyền user xuống để controller gửi request FETCH_BID_HISTORY lên server
                if (this.currentUser != null) {
                    historyCtrl.setUserData(this.currentUser);
                }
            } else if (childController instanceof The_Home_Page_Bidder_View_Controller) {
                The_Home_Page_Bidder_View_Controller homeCtrl = (The_Home_Page_Bidder_View_Controller) childController;
                homeCtrl.setMainContainer(this);
                if (this.currentUser != null) {
                    homeCtrl.setUserData(this.currentUser);
                }
            }

            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);

            AnchorPane.setTopAnchor(node, 0.0);
            AnchorPane.setBottomAnchor(node, 0.0);
            AnchorPane.setLeftAnchor(node, 0.0);
            AnchorPane.setRightAnchor(node, 0.0);

        } catch (IOException e) {
            System.err.println("LỖI ĐƯỜNG DẪN: Không tìm thấy file FXML tại: " + fxmlPath);
            e.printStackTrace();
        }
    }

    public void toggleSidebar() {
        if (sideMenu == null) return;
        if (sideMenu.isVisible()) {
            sideMenu.setVisible(false);
            sideMenu.setManaged(false);
        } else {
            sideMenu.setVisible(true);
            sideMenu.setManaged(true);
        }
    }

    @FXML
    void onLiveMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        btnMenuHistory.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        setPage("/view/view/bidder/The_Home_Page_Bidder_View.fxml");
    }

    @FXML
    void onHistoryMenuClick(ActionEvent event) {
        btnMenuLive.setStyle("-fx-background-color: transparent; -fx-text-fill: #FFFFFF;");
        btnMenuHistory.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #2563EB; -fx-background-radius: 8;");
        setPage("/view/view/bidder/BiddingHistoryView.fxml");
    }

    @FXML private Button btnMenuTransaction;
    @FXML private Button btnMenuWallet;
    @FXML private Button btnMenuProfile;

    @FXML
    void onTransactionMenuClick(ActionEvent event) {
        setPage("/view/view/bidder/TransactionHistoryView.fxml");
    }

    @FXML
    void onWalletMenuClick(ActionEvent event) {
        setPage("/view/view/bidder/DepositWithdrawView.fxml");
    }

    @FXML
    void onProfileMenuClick(ActionEvent event) {
        setPage("/view/view/bidder/ProfileView.fxml");
    }
}