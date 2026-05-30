package src.main.java.com.auction.server.service;

import com.auction.common.model.Item;
import com.auction.common.exception.AuctionException;
import com.auction.common.exception.ErrorCode;
import src.main.java.com.auction.server.dao.DBConnection;
import src.main.java.com.auction.server.dao.ItemDAO;
import src.main.java.com.auction.server.dao.DatabaseConnection; // Bổ sung để mở Connection

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class ItemService {
    private final ItemDAO itemDAO = new ItemDAO();

    /**
     * Lấy danh sách hàng hóa - Trả về List các đối tượng con thực tế (Vehicle, Art...)
     */
    public List<Item> getAllItems() {
        return itemDAO.getAllItems();
    }

    /**
     * Tìm hàng theo ID
     */
    public Item getItemById(int id) {
        Item item = itemDAO.getItemById(id);
        if (item == null) {
            throw new AuctionException(ErrorCode.ITEM_NOT_FOUND.name(), "Sản phẩm không tồn tại!");
        }
        return item;
    }

    /**
     * Thêm hàng mới (Dùng cho cả Admin và Seller)
     * Bọc SQL Transaction để hứng ID tự động tăng an toàn
     */
    public void addItem(Item item) {
        // 1. Kiểm tra dữ liệu đầu vào
        validateItem(item);

        // 2. Mở Connection qua DBConnection chung để chạy luồng lưu trữ có ID tự sinh
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false); // Bật chế độ quản lý giao dịch an toàn

            try {
                // Truyền conn vào hàm insertItem mới, hứng về item_id tự tăng kiểu int
                int generatedItemId = itemDAO.insertItem(conn, item);

                // Nạp ID vừa nhận từ DB ngược lại vào Object trên RAM để đồng bộ hiển thị mạng Socket
                item.setItemId(generatedItemId);

                conn.commit(); // Lưu vĩnh viễn dữ liệu xuống SQL
                System.out.println("[SERVICE] Đã thêm sản phẩm thành công với ID tự tăng: " + generatedItemId);

            } catch (SQLException e) {
                conn.rollback(); // Nếu dính lỗi dọc đường, hủy bỏ ngay lập tức để tránh dữ liệu rác
                throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi thực thi SQL: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Lỗi kết nối cơ sở dữ liệu!");
        }
    }

    /**
     * Xóa hàng
     */
    public void deleteItem(int id) {
        // Check xem có tồn tại không trước khi xóa
        getItemById(id);
        if (!itemDAO.deleteItem(id)) {
            throw new AuctionException(ErrorCode.INTERNAL_ERROR.name(), "Xóa thất bại (có thể hàng đang được đấu giá)!");
        }
    }

    private void validateItem(Item item) {
        if (item == null) throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Item trống!");
        if (item.getName() == null || item.getName().isEmpty()) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Tên sản phẩm không hợp lệ!");
        }
        if (item.getStartingPrice() < 0) {
            throw new AuctionException(ErrorCode.INVALID_INPUT.name(), "Giá khởi điểm không được âm!");
        }
    }

    /** Tìm sản phẩm theo phân loại danh mục (Xe, Tranh ảnh...) */
    public List<Item> getItemsByType(String itemType) {
        return itemDAO.getItemsByType(itemType);
    }

    /** [BÙ TÍNH NĂNG] Tìm danh sách các sản phẩm do một Người bán cụ thể đăng lên */
    public List<Item> getItemsBySeller(int sellerId) {
        return itemDAO.getItemsBySeller(sellerId);
    }
}