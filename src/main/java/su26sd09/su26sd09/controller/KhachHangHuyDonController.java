package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.KetQuaHuyDonDTO;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.HoaDonService;
import su26sd09.su26sd09.service.HuyDonService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cho phép khách hàng tự hủy đơn đặt phòng của chính mình từ mục
 * "Đơn đặt phòng" trong trang Quản lý tài khoản (customer-setting.html).
 *
 * Tái sử dụng đúng cơ chế + chính sách hủy đơn đã có sẵn ở HuyDonService
 * (dùng chung cho admin/nhân viên): tính tỷ lệ hoàn theo thời gian đã trôi
 * qua kể từ lúc đặt, không cho hủy nếu đã nhận/trả phòng, chuyển đơn sang
 * trạng thái trung gian "Cho huy" (hoặc "Da huy" thẳng nếu chưa có hóa đơn)
 * để nhân viên/admin xử lý hoàn tiền thủ công ở bước sau.
 *
 * Khác với luồng nhân viên/admin, khách hàng KHÔNG được điều hướng sang
 * trang xử lý hoàn tiền nội bộ (trang đó chỉ dành cho NV/Admin) — sau khi
 * gửi yêu cầu hủy, khách sẽ được đưa trở lại tab "Đơn đặt phòng" kèm thông
 * báo kết quả.
 */
@Controller
public class KhachHangHuyDonController {

    @Autowired
    private HuyDonService huyDonService;

    @Autowired
    private DatPhongService datPhongService;

    @Autowired
    private HoaDonService hoaDonService;

    private boolean laChuDon(DatPhong dp, Authentication auth) {
        return dp != null
                && dp.getN() != null
                && auth != null
                && dp.getN().getEmail() != null
                && dp.getN().getEmail().equalsIgnoreCase(auth.getName());
    }

    /**
     * Trả về thông tin xem trước (preview) chính sách hoàn tiền cho modal xác nhận hủy
     * đơn ở phía khách hàng, dùng đúng công thức đang áp dụng trong
     * HuyDonService.tinhTyLeHoan (tính theo số phút đã trôi qua từ lúc đặt), để khách
     * biết trước sẽ được hoàn bao nhiêu % / bao nhiêu tiền TRƯỚC KHI bấm xác nhận.
     * Chỉ đọc dữ liệu, không thay đổi trạng thái đơn/hóa đơn.
     */
    @GetMapping("/profiles/dat-phong/{id}/huy-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> xemTruocHuyDon(@PathVariable Integer id,
                                                              Authentication auth) {
        Map<String, Object> res = new LinkedHashMap<>();

        DatPhong dp = datPhongService.findById(id);
        if (!laChuDon(dp, auth)) {
            res.put("ok", false);
            res.put("message", "Không tìm thấy đơn đặt phòng hoặc bạn không có quyền xem đơn này!");
            return ResponseEntity.status(403).body(res);
        }

        boolean daNhanPhong = "Da nhan phong".equals(dp.getTrangThai()) || "Da tra phong".equals(dp.getTrangThai());
        boolean daHuyHoacChoHuy = "Da huy".equals(dp.getTrangThai()) || "Cho huy".equals(dp.getTrangThai());

        if (daHuyHoacChoHuy) {
            res.put("ok", false);
            res.put("message", "Đơn này đã được yêu cầu hủy trước đó.");
            return ResponseEntity.badRequest().body(res);
        }
        if (daNhanPhong) {
            res.put("ok", false);
            res.put("message", "Khách đã nhận phòng, không thể hủy theo chính sách này.");
            return ResponseEntity.badRequest().body(res);
        }

        HoaDon hd = hoaDonService.findByDatPhongId(id);
        BigDecimal tyLe = huyDonService.tinhTyLeHoan(dp);
        BigDecimal daThanhToan = (hd == null || hd.getDaThanhToan() == null) ? BigDecimal.ZERO : hd.getDaThanhToan();
        BigDecimal soTienHoanDuKien = daThanhToan.multiply(tyLe).setScale(0, RoundingMode.HALF_UP);

        long soPhutDaTroi = 0;
        if (dp.getNgayTao() != null) {
            soPhutDaTroi = java.time.Duration.between(dp.getNgayTao(), LocalDateTime.now()).toMinutes();
            if (soPhutDaTroi < 0) soPhutDaTroi = 0;
        }

        res.put("ok", true);
        res.put("maDatPhong", id);
        res.put("soPhutDaTroi", soPhutDaTroi);
        res.put("tyLePhanTram", tyLe.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP));
        res.put("daThanhToan", daThanhToan);
        res.put("soTienHoanDuKien", soTienHoanDuKien);
        res.put("coHoaDon", hd != null);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/profiles/dat-phong/{id}/huy")
    public String huyDonKhachHang(@PathVariable Integer id,
                                   Authentication auth,
                                   RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);

        if (!laChuDon(dp, auth)) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "Không tìm thấy đơn đặt phòng hoặc bạn không có quyền hủy đơn này!");
            return "redirect:/profiles?tab=bookings";
        }

        KetQuaHuyDonDTO ketQua = huyDonService.huyDon(id);
        redirectAttributes.addFlashAttribute("thongBao", ketQua.getThongBao());

        return "redirect:/profiles?tab=bookings";
    }
}
