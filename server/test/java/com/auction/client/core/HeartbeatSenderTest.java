package com.auction.client.core;

import com.auction.common.network.RequestCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeartbeatSenderTest {

  private SocketClient mockSocketClient;
  private ScheduledExecutorService mockScheduler;
  private HeartbeatSender heartbeatSender;
  private MockedStatic<Executors> executorsMockedStatic;

  @BeforeEach
  void setUp() {
    // 1. Mock SocketClient kết nối mạng
    mockSocketClient = mock(SocketClient.class);

    // 2. Mock ScheduledExecutorService để kiểm soát luồng chạy ngầm
    mockScheduler = mock(ScheduledExecutorService.class);

    // 3. Khởi tạo đối tượng cần test
    heartbeatSender = new HeartbeatSender(mockSocketClient);

    // 4. Bắt đầu giả lập static method của class Executors
    // Điều này giúp ép hàm Executors.newSingleThreadScheduledExecutor() trả về mockScheduler của chúng ta
    executorsMockedStatic = Mockito.mockStatic(Executors.class);
    executorsMockedStatic.when(() -> Executors.newSingleThreadScheduledExecutor(any(ThreadFactory.class)))
        .thenReturn(mockScheduler);
  }

  @AfterEach
  void tearDown() {
    // QUAN TRỌNG: Phải đóng mock static sau mỗi hàm test để không làm ảnh hưởng các class test khác
    if (executorsMockedStatic != null) {
      executorsMockedStatic.close();
    }
  }

  @Test
  void testStart_ShouldInitializeSchedulerWithCorrectTiming() {
    // WHEN: Kích hoạt sender
    heartbeatSender.start();

    // THEN: Xác thực xem scheduler có được cấu hình đúng: delay 2s, chu kỳ 10s không
    verify(mockScheduler, times(1)).scheduleAtFixedRate(
        any(Runnable.class),
        eq(2L),
        eq(10L),
        eq(TimeUnit.SECONDS)
    );
  }

  @Test
  void testStart_WhenAlreadyRunning_ShouldNotRecreateScheduler() {
    // GIVEN: Gọi start lần 1
    heartbeatSender.start();

    // WHEN: Cố tình gọi start lần 2 khi đang chạy
    heartbeatSender.start();

    // THEN: Hàm bẫy `if (isRunning) return;` phải hoạt động, Executors chỉ được gọi tạo luồng đúng 1 lần
    executorsMockedStatic.verify(() -> Executors.newSingleThreadScheduledExecutor(any(ThreadFactory.class)), times(1));
  }

  @Test
  void testPeriodicTask_WhenSocketIsConnected_ShouldSendPingRequest() throws Exception {
    // GIVEN: Giả lập trạng thái Socket đang kết nối tốt
    when(mockSocketClient.isConnected()).thenReturn(true);

    // Dùng ArgumentCaptor để "bắt" lấy đoạn code Lambda nằm bên trong scheduleAtFixedRate
    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    heartbeatSender.start();
    verify(mockScheduler).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong(), any());

    // Lấy đoạn code chạy ngầm ra luồng chính để thực thi
    Runnable heartbeatTask = taskCaptor.getValue();

    // WHEN: Giả lập sự kiện chu kỳ 10 giây đến (Kích hoạt chạy hàm)
    heartbeatTask.run();

    // THEN: Kiểm tra xem Client có gửi gói tin PING lên với payload rỗng (null) không
    verify(mockSocketClient, times(1)).sendRequest(RequestCode.PING, null);
  }

  @Test
  void testPeriodicTask_WhenSocketIsDisconnected_ShouldAutoStop() {
    // GIVEN: Giả lập mất kết nối mạng
    when(mockSocketClient.isConnected()).thenReturn(false);
    when(mockScheduler.isShutdown()).thenReturn(false);

    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    heartbeatSender.start();
    verify(mockScheduler).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong(), any());
    Runnable heartbeatTask = taskCaptor.getValue();

    // WHEN: Chu kỳ ping chạy trúng lúc mất mạng
    heartbeatTask.run();

    // THEN: Hệ thống phải tự động gọi hàm `stop()`, hủy bỏ và tắt luồng scheduler ngay lập tức
    verify(mockScheduler, times(1)).shutdownNow();
  }

  @Test
  void testPeriodicTask_WhenSendThrowsException_ShouldCatchAndStopSafely() throws Exception {
    // GIVEN: Giả lập mạng bị lỗi đột ngột văng ra Exception khi đang truyền gói tin
    when(mockSocketClient.isConnected()).thenReturn(true);
    doThrow(new RuntimeException("Mất kết nối vật lý (Đứt cáp)")).when(mockSocketClient).sendRequest(any(), any());
    when(mockScheduler.isShutdown()).thenReturn(false);

    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    heartbeatSender.start();
    verify(mockScheduler).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong(), any());
    Runnable heartbeatTask = taskCaptor.getValue();

    // WHEN & THEN: Thực thi tác vụ. Cụm try-catch trong luồng của bạn phải bắt được lỗi,
    // in ra log hệ thống và tự hủy luồng để giải phóng tài nguyên chứ không được làm sập app
    assertDoesNotThrow(heartbeatTask::run, "Luồng gửi Ping phải tự catch Exception để tránh crash ứng dụng");
    verify(mockScheduler, times(1)).shutdownNow();
  }

  @Test
  void testStop_ShouldShutdownActiveScheduler() {
    // GIVEN: Khởi động trước
    heartbeatSender.start();
    when(mockScheduler.isShutdown()).thenReturn(false);

    // WHEN: Chủ động gọi stop (Ví dụ khi tắt ứng dụng hoặc đăng xuất)
    heartbeatSender.stop();

    // THEN: Luồng ngầm phải bị triệt tiêu bằng shutdownNow()
    verify(mockScheduler, times(1)).shutdownNow();
  }
}