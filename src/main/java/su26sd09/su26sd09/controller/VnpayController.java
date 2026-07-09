package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.service.VnpayService;

import java.security.Principal;

@Controller
public class VnpayController {

    @Autowired
    VnpayService vnpayService;


    @GetMapping("/API/payment/vnpay-payment")
    public String GetVnpayPayment(HttpServletRequest request, RedirectAttributes redirectAttributes, Authentication authentication) {
        int paymentStatus = vnpayService.orderReturn(request, authentication);

        String vnp_TxnRef = request.getParameter("vnp_TxnRef");
        int maDatPhong = Integer.parseInt(vnp_TxnRef.split("_")[0]);

        String orderInfo     = request.getParameter("vnp_OrderInfo");
        String paymentTime   = request.getParameter("vnp_PayDate");
        String transactionId = request.getParameter("vnp_TransactionNo");
        String totalPrice    = request.getParameter("vnp_Amount");

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
