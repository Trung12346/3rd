package su26sd09.su26sd09.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Chuyển flash attribute dạng session ("toastWarning", "toastSuccess", "toastError")
 * thành model attribute để template có thể render, rồi xóa khỏi session để lần
 * refresh sau không hiện lại.
 *
 * Được dùng bởi các endpoint xuất PDF khi validate không hợp lệ (chuyển hướng
 * về trang trước thay vì ghi PDF).
 */
@ControllerAdvice
public class FlashToastAdvice {

    @ModelAttribute
    public void popToast(HttpServletRequest request,
                         org.springframework.ui.Model model) {
        consume(request, model, "toastWarning");
        consume(request, model, "toastSuccess");
        consume(request, model, "toastError");
    }

    private void consume(HttpServletRequest request,
                         org.springframework.ui.Model model,
                         String key) {
        Object value = request.getSession().getAttribute(key);
        if (value != null) {
            model.addAttribute(key, value);
            request.getSession().removeAttribute(key);
        }
    }
}
