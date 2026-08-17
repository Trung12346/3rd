package su26sd09.su26sd09.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.service.BookingDraftService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tự động gắn biến "bookingDraft" + "bookingDraftStep" vào Model của MỌI
 * controller trả về view Thymeleaf. Trước đây chỉ Home mới gắn — dẫn đến
 * header (fragment guest-header) không hiện chuông ở các trang khác
 * (vd: /phong/dat-phong/xac-nhan/..., /loai-phong/... ...).
 *
 * Đặt ở @ControllerAdvice nên:
 *  - Áp dụng cho tất cả controller trả về template Thymeleaf.
 *  - Không cần sửa từng controller.
 *  - Nếu cookie GUEST_BOOKING_DRAFT không tồn tại / đơn không hợp lệ ->
 *    peek() trả null -> banner không hiện (null-safe trong template).
 *
 * Lưu ý: tham số HttpServletRequest được Spring tự inject vào @ModelAttribute
 * method (chỉ với @ControllerAdvice, không cho global filter thông thường).
 */
@ControllerAdvice
public class BookingDraftModelAdvice {

    @Autowired
    BookingDraftService bookingDraftService;

    /**
     * Luôn có mặt trong model; null nếu không có cookie / đơn không hợp lệ.
     * Template dùng th:if="${bookingDraft != null}" để tránh NPE.
     */
    @ModelAttribute("bookingDraft")
    public DatPhong bookingDraft(HttpServletRequest request) {
        return bookingDraftService.peek(request);
    }

    @ModelAttribute("bookingDraftStep")
    public String bookingDraftStep(HttpServletRequest request) {
        DatPhong dp = bookingDraftService.peek(request);
        return bookingDraftService.currentStep(dp);
    }

    /** pending | draft | approved — phân loại trạng thái để popup chuông hiển thị. */
    @ModelAttribute("bookingDraftMode")
    public String bookingDraftMode(HttpServletRequest request) {
        DatPhong dp = bookingDraftService.peek(request);
        return bookingDraftService.currentMode(dp);
    }
}
