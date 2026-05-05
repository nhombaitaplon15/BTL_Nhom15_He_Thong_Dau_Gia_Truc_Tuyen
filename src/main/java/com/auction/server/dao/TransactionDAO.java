package com.auction.server.dao;

import com.auction.common.model.TransactionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionDAO {
    //Kho lưu trữ trên RAM: Key là ID, Value là Object giao dịch
    private Map<Integer, TransactionRequest> transactionTable = new ConcurrentHashMap<>();

    //Bộ đếm tự động tăng ID
    private AtomicInteger idCounter = new AtomicInteger(1);

    // Thêm một yêu cầu nạp/rút mới vào RAM
    public void addTransaction(TransactionRequest request) {
        int newId = idCounter.getAndIncrement();
        request.setRequestId(newId);
        transactionTable.put(newId, request);
    }

    // Admin lấy danh sách các giao dịch đang chờ duyệt (PENDING)
    public List<TransactionRequest> getPendingTransactions() {
        List<TransactionRequest> pendingList = new ArrayList<>();
        for (TransactionRequest req : transactionTable.values()) {
            if ("PENDING".equals(req.getTransactionStatus())) {
                pendingList.add(req);
            }
        }
        return pendingList;
    }

    // Lấy ra 1 giao dịch cụ thể để Admin duyệt
    public TransactionRequest getTransactionById(int id) {
        return transactionTable.get(id);
    }
}