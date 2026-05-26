package com.auction.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.Socket;

public class NetworkClient {

  private static final String SERVER_HOST = "localhost";
  private static final int    SERVER_PORT = 9999;

  private static NetworkClient instance;

  private Socket socket;
  private PrintWriter  out;
  private BufferedReader in;

  private final Gson gson = new Gson();

  public static NetworkClient getInstance() {
    if (instance == null) instance = new NetworkClient();
    return instance;
  }

  public void connect() throws IOException {
    socket = new Socket(SERVER_HOST, SERVER_PORT);
    out    = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    in     = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
    System.out.println("[CLIENT] Đã kết nối server " + SERVER_HOST + ":" + SERVER_PORT);
  }

  public synchronized JsonObject sendRequest(String action, JsonObject data) throws IOException {
    JsonObject request = new JsonObject();
    request.addProperty("action", action);
    if (data != null) request.add("data", data);

    out.println(gson.toJson(request));

    String responseLine = in.readLine();
    if (responseLine == null) throw new IOException("Server đóng kết nối!");

    return JsonParser.parseString(responseLine).getAsJsonObject();
  }

  public boolean isSuccess(JsonObject response) {
    return response.has("success") && response.get("success").getAsBoolean();
  }

  public BufferedReader getReader() {
    return this.in;
  }

  public void disconnect() {
    try { if (socket != null) socket.close(); }
    catch (IOException ignored) {}
  }
}