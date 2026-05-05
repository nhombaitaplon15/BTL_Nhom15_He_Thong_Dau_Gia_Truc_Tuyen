package com.auction.service;

import com.auction.common.model.TransactionRequest;
import com.auction.common.model.User;
import com.auction.server.dao.TransactionDAO;

public class TransactionService {
    // khai báo kho chứa RAM
    private TransactionDAO  transaction;

    // constructor truyền kho chứa vào
    public TransactionService(TransactionDAO transaction) {
        this.transaction = transaction;
    }

    // nạp tiền vào ví
    public void deposit(User user, double amount) {
        if (amount > 0) {
            TransactionRequest request = new TransactionRequest(user,"DEPOSIT", amount,"CHƯA CÓ","PENDING");
            transaction.addTransaction(request);
        } else {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }
    }

    // rút tiển khỏi ví
    public void withdraw(User user ,double amount,String bankInfo) {
        if (amount > 0 && user.getBalance() >= amount) {
            user.setBalance(user.getBalance() - amount);
            TransactionRequest request = new TransactionRequest(user,"WITHDRAW", amount, bankInfo,"PENDING");
            transaction.addTransaction(request);
        } else {
            throw new IllegalArgumentException("Số dư không hợp vệ hoặc không đủ để rút ");
        }
    }

    // hàm này được gọi khi Admin bấm "Duyệt" hoặc "Từ chối" trên giao diện
    public void executeTransactionDecision(int id, boolean isApproved) throws Exception {
        TransactionRequest request = transaction.getTransactionById(id);
        if (request.getTransactionStatus().equals("PENDING")) {
            if (isApproved) {// admin duyệt giao dịch
                if (request.getType().equals("DEPOSIT")) {
                    User owner = request.getUser();
                    owner.setBalance(owner.getBalance() + request.getAmount());
                }
                request.setTransactionStatus("APPROVED");

            } else { // admin không duyệt giao dịch
                if (request.getType().equals("WITHDRAW")) {
                    User owner = request.getUser();
                    owner.setBalance(owner.getBalance() + request.getAmount());
                }
                request.setTransactionStatus("REJECTED");
            }
        } else {
            throw new IllegalStateException("Giao dịch này đã được xử lý từ trước!");
        }
    }

}
