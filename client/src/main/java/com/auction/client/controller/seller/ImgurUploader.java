package com.auction.client.controller.seller;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImgurUploader {

  // THAY MÃ BẠN COPY TRÊN TRANG IMGBB VÀO ĐÂY:
  private static final String IMGBB_API_KEY = "519ab293e40f8ba6a4e320b311271d98";

  public static String uploadImageToCloud(File file) {
    if (file == null || !file.exists()) return null;

    try {
      // 1. Chuyển file ảnh thành chuỗi mã hóa Base64
      byte[] fileContent = Files.readAllBytes(file.toPath());
      String encodedString = Base64.getEncoder().encodeToString(fileContent);

      // 2. Đóng gói dữ liệu gửi lên ImgBB
      String requestBody = "key=" + IMGBB_API_KEY + "&image=" + java.net.URLEncoder.encode(encodedString, "UTF-8");

      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.imgbb.com/1/upload"))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build();

      // 3. Nhận phản hồi từ máy chủ
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      System.out.println(">>> Mã trạng thái ImgBB: " + response.statusCode());

      // 4. Lấy link ảnh trực tiếp từ chuỗi JSON trả về
      Pattern pattern = Pattern.compile("\"display_url\":\"(.*?)\"");
      Matcher matcher = pattern.matcher(response.body());

      if (matcher.find()) {
        String finalUrl = matcher.group(1).replace("\\/", "/");
        System.out.println(">>> Upload thành công. Link ảnh: " + finalUrl);
        return finalUrl;
      } else {
        System.out.println(">>> Lỗi JSON: " + response.body());
      }
    } catch (Exception e) {
      System.err.println(">>> Ngoại lệ khi upload ảnh lên ImgBB: " + e.getMessage());
    }

    return null;
  }
}