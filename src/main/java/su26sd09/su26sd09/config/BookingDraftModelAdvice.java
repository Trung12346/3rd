package su26sd09.su26sd09.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import su26sd09.su26sd09.dto.PendingBookingDraft;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.service.BookingDraftService;

import jakarta.servlet.http.HttpServletRequest;
import su26sd09.su26sd09.service.PendingBookingService;

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

    @Autowired
    private PendingBookingService pendingBookingService;
    /**
     * Luôn có mặt trong model; null nếu không có cookie / đơn không hợp lệ.
     * Template dùng th:if="${bookingDraft != null}" để tránh NPE.
     */
    @ModelAttribute("bookingDraft")
    public DatPhong bookingDraft(HttpServletRequest request) {
        return bookingDraftService.peek(request);
    }
    @ModelAttribute("pendingBookingDraft")
    public PendingBookingDraft pendingBookingDraft(
            HttpServletRequest request
    ) {
        return pendingBookingService.peek(request);
    }
    @ModelAttribute("bookingDraftStep")
    public String bookingDraftStep(HttpServletRequest request) {
        DatPhong dp = bookingDraftService.peek(request);
        return bookingDraftService.currentStep(dp);
    }
    @ModelAttribute("pendingBookingId")
    public Integer pendingBookingId(
            HttpServletRequest request
    ) {
        return pendingBookingService.peekId(request);
    }

    /** pending | draft | approved | success — phân loại trạng thái để popup chuông hiển thị. */
    @ModelAttribute("bookingDraftMode")
    public String bookingDraftMode(HttpServletRequest request) {
        DatPhong dp = bookingDraftService.peek(request);
        return bookingDraftService.currentMode(dp);
    }

    /**
     * True nếu URL hiện tại thuộc luồng thanh toán (chọn DV, điền thông tin khách,
     * chọn PTTT, gọi VNPay). Khi đang ở luồng thì KHÔNG hiển thị chuông để tránh
     * popup đè lên giao diện thanh toán; chỉ hiện khi khách thoát ra trang khác
     * (vd: trang chủ) thì chuông mới xuất hiện để backup.
     */
    @ModelAttribute("isInBookingFlow")
    public boolean isInBookingFlow(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return false;
        return uri.startsWith("/phong/dat-phong/")
                || uri.startsWith("/thanh-toan/")
                || uri.startsWith("/phong/dat-phong/tiep-tuc-dat");
    }
}
