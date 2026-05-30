package src.main.java.com.auction.server.core;

/**
 * ServerMain - Điểm khởi động của Server.
 *
 * Server KHÔNG dùng JavaFX.
 * Chỉ khởi động socket server và lắng nghe kết nối từ client.
 */
public class ServerMain {

    public static void main(String[] args) {

        try {
            // Khởi động server socket
            AuctionServer server = new AuctionServer();

            System.out.println("====================================");
            System.out.println("  ELITE AUCTION SERVER STARTED");
            System.out.println("  Port: 8888");
            System.out.println("====================================");

            // Bắt đầu lắng nghe client
            server.start();

        } catch (Exception e) {
            System.err.println("[SERVER ERROR] Không thể khởi động server!");
            e.printStackTrace();
        }
    }
}