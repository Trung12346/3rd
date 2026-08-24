package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.service.BookingDraftService;
import su26sd09.su26sd09.service.VnpayService;

import java.security.Principal;

@Controller
public class VnpayController {

    @Autowired
    VnpayService vnpayService;

    @Autowired
    BookingDraftService bookingDraftService;

    @GetMapping("/API/payment/vnpay-payment")
    public String GetVnpayPayment(HttpServletRequest request,
                                  HttpServletResponse response,
                                  RedirectAttributes redirectAttributes,
                                  Authentication authentication) {
        String orderInfo = request.getParameter("vnp_OrderInfo");
        boolean laHoanTien = "HoanTienChoKhach".equals(orderInfo);

        int paymentStatus = vnpayService.orderReturn(request, authentication);

        String vnp_TxnRef = request.getParameter("vnp_TxnRef");
        String paymentTime   = request.getParameter("vnp_PayDate");
        String transactionId = request.getParameter("vnp_TransactionNo");
        String totalPrice    = request.getParameter("vnp_Amount");

        // ===== Callback cho luồng HOÀN TIỀN =====
        if (laHoanTien) {
            // TxnRef dạng: REFUND_{maHoaDon}_{rand}
            int maHoaDon = 0;
            try {
                String[] parts = vnp_TxnRef.split("_");
                maHoaDon = Integer.parseInt(parts[1]);
            } catch (Exception ignore) { }
            if (maHoaDon > 0) {
                if (paymentStatus == 1) {
                    redirectAttributes.addFlashAttribute("success",
                            "VNPay da xac nhan giao dich hoan tien. Ma GD: " + transactionId);
                } else {
                    redirectAttributes.addFlashAttribute("error",
                            "VNPay giao dich hoan tien that bai hoac chu ky khong hop le.");
                }
                // FIX: phai redirect ve trang nhan-vien (STAFF) /nhan-su/hoan-tien/...
                // chu khong phai /nhan-su/admin/hoan-tien/... (chi ADMIN moi vao
                // duoc). Truoc day dung nham path admin, STAFF sau khi callback
                // VNPay se bi SecurityConfig deny -> trang access denied 403.
                return "redirect:/nhan-su/hoan-tien/chi-tiet/" + maHoaDon;
            }
            return "redirect:/nhan-su/hoan-tien";
        }

        // ===== Callback cho các luồng khác (giữ nguyên logic cũ) =====
        int maDatPhong = Integer.parseInt(vnp_TxnRef.split("_")[0]);
        boolean laThuThemDichVu = "ThuThemDichVu".equals(orderInfo);

        redirectAttributes.addFlashAttribute("orderId", orderInfo);
        redirectAttributes.addFlashAttribute("totalPrice", totalPrice);
        redirectAttributes.addFlashAttribute("paymentTime", paymentTime);
        redirectAttributes.addFlashAttribute("transactionId", transactionId);

        if (paymentStatus == 1) {
            if (laThuThemDichVu) {
                redirectAttributes.addFlashAttribute("success", "Thanh toán chuyển khoản thành công.");
                return "redirect:/nhan-su/dat-phong/chi-tiet/" + maDatPhong;
            }
            // Thanh toán VNPay thành công cho đơn của khách -> XÓA COOKIE backup.
            // Sau bước này đơn đã hoàn tất (đã thanh toán + đủ thông tin), không
            // cần backup nữa -> tránh hiển thị chuông "đơn đang chờ thanh toán"
            // cho đơn đã xong.
            bookingDraftService.consume(request, response);
            return "redirect:/thanh-toan/thanh-cong/" + maDatPhong;
        } else {
            if (laThuThemDichVu) {
                redirectAttributes.addFlashAttribute("error", "Chuyển khoản thất bại hoặc số tiền không khớp.");
                return "redirect:/nhan-su/dat-phong/chi-tiet/" + maDatPhong;
            }
            redirectAttributes.addFlashAttribute("bookingError", "Thanh toán thất bại hoặc số tiền không khớp.");
            return "redirect:/thanh-toan/dat-phong/" + maDatPhong;
        }
    }
}
