package su26sd09.su26sd09.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;
import su26sd09.su26sd09.entity.DatPhong;

import java.util.List;

/**
 * Quản lý "bản nháp" đặt phòng cho khách vãng lai (không có tài khoản).
 *
 * Lưu bằng COOKIE ở trình duyệt (tên GUEST_BOOKING_DRAFT) thay vì session
 * vì session bị mất khi server restart trong khi cookie sống ở client và
 * KHÔNG bị ảnh hưởng bởi restart server — dữ liệu của khách vẫn an toàn
 * kể cả khi nhân viên dev restart app.
 *
 * - Khi khách vãng lai checkout giỏ hàng (tạo DatPhong) hoặc đi qua bước
 *   chọn dịch vụ bổ sung, ta ghi mã đơn vào cookie GUEST_BOOKING_DRAFT.
 * - Khi khách truy cập lại trang chủ / trang khác, controller gọi peek()
 *   để đọc cookie, validate bằng cách truy vấn DB, rồi hiển thị banner
 *   "Đơn đang dở" cùng nút Tiếp tục đặt.
 * - Khi khách bấm Tiếp tục đặt và được đưa về đúng trang thao tác, hoặc
 *   khi khách hoàn tất thông tin đặt phòng, controller gọi consume() để
 *   xóa cookie.
 */
@Service
public class BookingDraftService {

    public static final String COOKIE_NAME = "GUEST_BOOKING_DRAFT";

    /** Cookie sống 30 ngày ở trình duyệt. */
    private static final int COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

    @Autowired
    DatPhongService datPhongService;

    @Autowired
    ChiTietDichVuService chiTietDichVuService;

    /**
     * Ghi nhớ đơn đang dở vào cookie (ghi đè nếu đã có).
     * Cookie có path=/, maxAge=30 ngày — sống ở trình duyệt và KHÔNG bị mất
     * khi restart server.
     */
    public void remember(HttpServletRequest request,
                         HttpServletResponse response,
                         Integer datPhongId) {
        if (response == null || datPhongId == null) {
            return;
        }
        Cookie cookie = new Cookie(COOKIE_NAME, String.valueOf(datPhongId));
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        // Thêm SameSite=Lax thủ công (Servlet Cookie API không có setSameSite)
        // để Chrome/Firefox hiện đại chấp nhận cookie khi điều hướng từ /home về /phong/...
        response.addHeader("Set-Cookie",
                String.format("%s=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                        COOKIE_NAME,
                        String.valueOf(datPhongId),
                        COOKIE_MAX_AGE_SECONDS));
    }

    /** Xóa cookie (setMaxAge(0)) — gọi sau khi khách đã hoàn tất luồng. */
    public void consume(HttpServletRequest request, HttpServletResponse response) {
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        // Cũng xóa qua header để chắc chắn Chrome xóa cả cookie có SameSite=Lax
        response.addHeader("Set-Cookie",
                String.format("%s=; Path=/; Max-Age=0; SameSite=Lax", COOKIE_NAME));
    }

    /**
     * Đọc cookie GUEST_BOOKING_DRAFT, parse về Integer, rồi xác thực bằng
     * cách truy vấn DB:
     * - Đơn phải tồn tại.
     * - Trạng thái phải là "Chua thanh toan" (chưa qua thanh toán).
     * - Đơn phải là của khách vãng lai (không gắn với KhachHang nào).
     *
     * @return DatPhong hợp lệ hoặc null nếu cookie không có / giá trị
     *         không parse được / đơn không hợp lệ.
     */
    public DatPhong peek(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Cookie cookie = findCookie(request.getCookies(), COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        Integer id;
        try {
            id = Integer.parseInt(cookie.getValue().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            return null;
        }
        String tt = dp.getTrangThai();
        // Chấp nhận các trạng thái "đang xử lý / đang dở":
        //  - "Chua thanh toan"      : đơn từ /gio-hang/checkout hoặc /phong/dat-phong/quick (chọn phòng cụ thể)
        //  - "Yeu cau dat phong"   : đơn từ /loai-phong/dat-nhanh (chọn cả loại → NV xác nhận sau)
        //  - "Cho xac nhan"        : NV đã tiếp nhận, chờ KH hoàn tất thanh toán
        // Các trạng thái còn lại (Da xac nhan, Da nhan phong, Da huy, Da thanh toan,...) -> không hiện.
        if (tt == null
                || (!"Chua thanh toan".equalsIgnoreCase(tt)
                    && !"Yeu cau dat phong".equalsIgnoreCase(tt)
                    && !"Cho xac nhan".equalsIgnoreCase(tt))) {
            return null;
        }
        if (dp.getN() != null) {
            // Đơn đã gắn với user -> không thuộc luồng khách vãng lai
            return null;
        }
        return dp;
    }

    /**
     * Phân loại đơn đang xử lý (peek() != null) để template popup chuông
     * hiển thị nội dung phù hợp + nút bấm đúng bước.
     *
     * @return "pending"  — đơn vừa gửi yêu cầu, NV chưa xét ("Yeu cau dat phong").
     *                       Hiện thông báo + mã đơn + mã tra cứu.
     *         "draft"    — đơn đang dở ("Chua thanh toan"), KH chưa hoàn tất.
     *                       Hiện "Bước hiện tại..." + nút Tiếp tục đặt.
     *         "approved" — NV đã xét duyệt ("Cho xac nhan"), KH có thể tiếp tục thanh toán.
     *         null       — không xác định.
     */
    public String currentMode(DatPhong dp) {
        if (dp == null || dp.getTrangThai() == null) {
            return null;
        }
        String tt = dp.getTrangThai().toLowerCase();
        if (tt.contains("yeu cau")) return "pending";
        if (tt.contains("cho xac")) return "approved";
        if (tt.contains("chua thanh toan")) return "draft";
        return null;
    }

    /**
     * Xác định đơn đang dở ở bước nào (chọn dịch vụ hay thanh toán).
     *
     * @return "xac-nhan" nếu chưa có ChiTietDichVu nào (cần về trang chọn DV),
     *         "thanh-toan" nếu đã chọn DV nhưng chưa thanh toán.
     */
    public String currentStep(DatPhong dp) {
        if (dp == null) {
            return null;
        }
        List<Chi_tiet_dich_vu> dsDichVu = chiTietDichVuService.findByDatPhongId(dp.getId());
        if (dsDichVu == null || dsDichVu.isEmpty()) {
            return "xac-nhan";
        }
        return "thanh-toan";
    }

    private static Cookie findCookie(Cookie[] cookies, String name) {
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }
}
