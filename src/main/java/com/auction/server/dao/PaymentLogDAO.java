package com.auction.server.dao;
import com.auction.common.model.PaymentLog;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class PaymentLogDAO {
    // Kho chứa dữ liệu trên RAM
    private List<PaymentLog> memoryLogs = new CopyOnWriteArrayList<>();

    // Bộ đếm ID tự động tăng an toàn
    private AtomicInteger idCounter = new AtomicInteger(1);

    // Hàm ghi log mới vào RAM
    public void saveLog(String type, String from, String to, double amount, double fee) {
        int newId = idCounter.getAndIncrement();
        PaymentLog log = new PaymentLog(newId, type, from, to, amount, fee);
        memoryLogs.add(log);
    }

    // Hàm lấy toàn bộ lịch sử
    public List<PaymentLog> getAllLogs() {
        return memoryLogs;
    }
}