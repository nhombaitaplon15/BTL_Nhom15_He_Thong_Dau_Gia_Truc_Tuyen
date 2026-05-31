package com.auction.client.controller.seller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Base class cho các card có countdown timer (OpenCard, RunningCard).
 *
 * Subclass chỉ cần:
 *   1. Khai báo @FXML lblCountdown và dotTimer
 *   2. Gọi startCountdown(endTime) trong setData()
 *   3. Gọi stopTimer() khi cell bị recycle (ListCell.updateItem với empty=true)
 */
public abstract class BaseTimerCardController {

  // Subclass khai báo @FXML — abstract accessor để base class dùng
  protected abstract Label getLblCountdown();
  protected abstract Circle getDotTimer();

  private Timeline countdownTimer;

  protected void startCountdown(LocalDateTime endTime) {
    if (countdownTimer != null) countdownTimer.stop();

    countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
      long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);

      if (secs <= 0) {
        getLblCountdown().setText("Đã kết thúc");
        if (getDotTimer() != null) getDotTimer().setFill(Color.web("#B0BEC5"));
        countdownTimer.stop();
        return;
      }

      long h = secs / 3600;
      long m = (secs % 3600) / 60;
      long s = secs % 60;
      getLblCountdown().setText(String.format("Còn %02d:%02d:%02d", h, m, s));

      // Cam khi còn < 1 giờ, xanh khi còn nhiều thời gian
      if (getDotTimer() != null) {
        getDotTimer().setFill(secs < 3600
            ? Color.web("#FF9800")
            : Color.web("#43A047"));
      }
    }));

    countdownTimer.setCycleCount(Animation.INDEFINITE);
    countdownTimer.play();
  }

  /** Gọi khi cell bị recycle để tránh timer chạy ngầm sau khi cell không còn hiển thị. */
  public void stopTimer() {
    if (countdownTimer != null) countdownTimer.stop();
  }
}