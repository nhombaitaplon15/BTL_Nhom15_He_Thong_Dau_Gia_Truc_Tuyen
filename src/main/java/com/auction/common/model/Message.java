package com.auction.common.model;

import java.io.Serializable;
//implements Serializable để Java có thể biến đối tượng này thành byte và truyền qua mạng
public class Message implements Serializable {
    private String command;// lệnh yêu cầu
    private Object data; // dữ liệu đính kèm
    public Message(String command, Object data) {
        this.command = command;
        this.data = data;
    }
    public String getCommand() {
        return command;
    }
    public void setCommand(String command) {
        this.command = command;
    }
    public Object getData() {
        return data;
    }
    public void setData(Object data) {
        this.data = data;
    }
}
