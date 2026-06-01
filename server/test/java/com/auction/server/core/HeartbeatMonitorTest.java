package com.auction.server.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

  private HeartbeatMonitor heartbeatMonitor;

  @Mock
  private ScheduledExecutorService mockScheduler;

  private MockedStatic<SessionManager> mockedSessionManager;
  private SessionManager mockSessionManagerInstance;

  @BeforeEach
  void setUp() throws Exception {
    // Lấy instance Singleton của HeartbeatMonitor
    heartbeatMonitor = HeartbeatMonitor.getInstance();

    // 1. Dùng Reflection thay thế ScheduledExecutorService thật bằng Mock
    // Điều này giúp chúng ta tránh phải chờ 30 giây trong lúc chạy Unit Test
    Field schedulerField = HeartbeatMonitor.class.getDeclaredField("scheduler");
    schedulerField.setAccessible(true);
    schedulerField.set(heartbeatMonitor, mockScheduler);

    // 2. Giả lập SessionManager để cung cấp danh sách Client ảo
    mockSessionManagerInstance = mock(SessionManager.class);
    mockedSessionManager = mockStatic(SessionManager.class);
    mockedSessionManager.when(SessionManager::getInstance).thenReturn(mockSessionManagerInstance);
  }

  @AfterEach
  void tearDown() {
    mockedSessionManager.close();
  }

  // =================================================================================
  // 1. TEST KHỞI ĐỘNG VÀ LÊN LỊCH QUÉT
  // =================================================================================

  @Test
  @DisplayName("startMonitoring - Task được lên lịch cấu hình chuẩn (Delay 10s, quét 5s/lần)")
  void testStartMonitoring_SchedulesTask() {
    heartbeatMonitor.startMonitoring();

    // Xác minh scheduler được gọi với các tham số độ trễ chính xác
    verify(mockScheduler).scheduleAtFixedRate(any(Runnable.class), eq(10L), eq(5L), eq(TimeUnit.SECONDS));
  }

  // =================================================================================
  // 2. TEST LOGIC PHÁT HIỆN VÀ TIÊU DIỆT ZOMBIE CLIENT
  // =================================================================================

  @Test
  @DisplayName("Logic giám sát - Quét và tự động kích (CleanUp) Client mất mạng trên 30s")
  void testMonitoringLogic_CleansUpZombies() {
    // BƯỚC 1: Dùng ArgumentCaptor để "bắt" khối lệnh Runnable thay vì để nó tự chạy ngầm
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    heartbeatMonitor.startMonitoring();
    verify(mockScheduler).scheduleAtFixedRate(runnableCaptor.capture(), eq(10L), eq(5L), eq(TimeUnit.SECONDS));

    Runnable monitoringTask = runnableCaptor.getValue();

    // BƯỚC 2: Tạo 2 người dùng giả lập
    ClientHandler aliveClient = mock(ClientHandler.class);
    ClientHandler zombieClient = mock(ClientHandler.class);

    long now = System.currentTimeMillis();

    // Người dùng khỏe mạnh: Vừa ping cách đây 10 giây
    when(aliveClient.getLastHeartbeat()).thenReturn(now - 10000);

    // Người dùng rớt mạng (Zombie): Ping cuối cùng cách đây 40 giây (> giới hạn 30000ms)
    when(zombieClient.getLastHeartbeat()).thenReturn(now - 40000);

    when(mockSessionManagerInstance.getAllConnections()).thenReturn(Arrays.asList(aliveClient, zombieClient));

    // BƯỚC 3: Kích hoạt chạy hàm quét thủ công trong test
    monitoringTask.run();

    // BƯỚC 4: Thẩm định xem thuật toán có xử lý chuẩn xác không
    // Xác minh Zombie đã bị dọn dẹp để chống tràn RAM
    verify(zombieClient).cleanUp();

    // Xác minh người dùng đang có mạng không bị hàm cleanUp chạm tới
    verify(aliveClient, never()).cleanUp();
  }

  // =================================================================================
  // 3. TEST DỪNG HỆ THỐNG AN TOÀN
  // =================================================================================

  @Test
  @DisplayName("stop - Tắt luồng ngầm thành công khi hệ thống Server Shutdown")
  void testStop() {
    heartbeatMonitor.stop();
    // Xác minh lệnh tắt khẩn cấp đã được truyền tới Scheduler
    verify(mockScheduler).shutdownNow();
  }
}