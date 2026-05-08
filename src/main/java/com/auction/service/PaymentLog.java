package com.auction.service;
import com.auction.common.model.Payment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class
PaymentLog {
    // kho chứa dữ liệu trên RAM
    private List<Payment> memoryLogs = new CopyOnWriteArrayList<>();

    // bộ đếm ID tự động tăng an toàn
    private AtomicInteger idCounter = new AtomicInteger(1);

    // hàm ghi log mới vào RAM
    public void saveLog(String type, String from, String to, double amount, double fee) {
        int newId = idCounter.getAndIncrement();
        Payment log = new Payment(newId, type, from, to, amount, fee);
        memoryLogs.add(log);
    }

    // hàm lấy toàn bộ lịch sử
    public List<Payment> getAllLogs() {
        return memoryLogs;
    }
}