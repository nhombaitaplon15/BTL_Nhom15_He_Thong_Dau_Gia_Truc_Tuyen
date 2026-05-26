package com.auction.exception

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ErrorCodeTest {
    @Test
    fun testEnumValuesCount() {
        // Thay vì fix cứng số lượng, ta kiểm tra danh sách mã lỗi không được rỗng
        val values: Array<ErrorCode?> = ErrorCode.entries.toTypedArray()
        Assertions.assertTrue(values.size > 0, "Danh sách ErrorCode không được trống!")
        println(">>> Tổng số mã lỗi thực tế trong hệ thống: " + values.size)
    }

    @Test
    fun testEnumConstantsExist() {
        // Đảm bảo các mã lỗi cốt lõi của hệ thống luôn luôn tồn tại đúng tên gọi

        // 1. Nhóm Đấu giá (Auction)

        Assertions.assertNotNull(ErrorCode.valueOf("AUCTION_NOT_FOUND"))
        Assertions.assertNotNull(ErrorCode.valueOf("AUCTION_INVALID_STATE"))
        Assertions.assertNotNull(ErrorCode.valueOf("AUCTION_ALREADY_ENDED"))

        // 2. Nhóm Người đấu giá (Bidder)
        Assertions.assertNotNull(ErrorCode.valueOf("BID_TOO_LOW"))
        Assertions.assertNotNull(ErrorCode.valueOf("INVALID_BID"))

        // 3. Nhóm Mặt hàng (Item)
        Assertions.assertNotNull(ErrorCode.valueOf("INVALID_ITEM"))
        Assertions.assertNotNull(ErrorCode.valueOf("ITEM_DUPLICATE"))
        Assertions.assertNotNull(ErrorCode.valueOf("ITEM_NOT_FOUND"))

        // 4. Nhóm Người dùng (User)
        Assertions.assertNotNull(ErrorCode.valueOf("USER_NOT_FOUND"))
        Assertions.assertNotNull(ErrorCode.valueOf("UNAUTHORIZED"))

        // 5. Nhóm Hệ thống (System)
        Assertions.assertNotNull(ErrorCode.valueOf("CONCURRENCY"))
        Assertions.assertNotNull(ErrorCode.valueOf("INVALID_INPUT"))
        Assertions.assertNotNull(ErrorCode.valueOf("INTERNAL_ERROR"))

        // 6. Nhóm Giao dịch (Transaction)
        Assertions.assertNotNull(ErrorCode.valueOf("TRANSACTION_FAILED"))
        Assertions.assertNotNull(ErrorCode.valueOf("INSUFFICIENT_BALANCE"))
    }

    @Test
    fun testEnumNameMatches() {
        // Kiểm tra tính đồng bộ chuỗi đặt tên
        Assertions.assertEquals("USER_NOT_FOUND", ErrorCode.USER_NOT_FOUND.name)
        Assertions.assertEquals("INTERNAL_ERROR", ErrorCode.INTERNAL_ERROR.name)
        Assertions.assertEquals("INSUFFICIENT_BALANCE", ErrorCode.INSUFFICIENT_BALANCE.name)
    }
}