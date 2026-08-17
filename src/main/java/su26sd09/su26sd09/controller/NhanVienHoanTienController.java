package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.RefundDraft;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/nhan-su/hoan-tien")   // STAFF + ADMIN cùng vào
public class NhanVienHoanTienController {

    @Autowired HuyDonService huyDonService;
    @Autowired HoaDonService hoaDonService;
    @Autowired ThanhToanService thanhToanService;
    @Autowired NhanVienService nhanSuService;
    @Autowired VnpayService vnpayService;

    @GetMapping
    public String danhSach(@RequestParam(required = false) String trangThaiHoanTien,
                           @RequestParam(required = false) Integer maHoaDon,
                           @RequestParam(required = false) Integer maDatPhong,
                           @RequestParam(required = false) String tenKhach,
                           Model model) {
        model.addAttribute("dsHoaDon", hoaDonService.findAll().stream()
                .filter(hd -> hd.getTrangThaiHoanTien() != null)
                .filter(hd -> trangThaiHoanTien == null || trangThaiHoanTien.isEmpty()
                        || trangThaiHoanTien.equals(hd.getTrangThaiHoanTien()))
                .filter(hd -> maHoaDon == null || (hd.getId() != null && hd.getId() == maHoaDon))
                .filter(hd -> maDatPhong == null || (hd.getD() != null && hd.getD().getId() != null && hd.getD().getId() == maDatPhong))
                .filter(hd -> {
                    if (tenKhach == null || tenKhach.isBlank()) return true;
                    String q = tenKhach.toLowerCase().trim();
                    String hoTen = hd.getD() != null && hd.getD().getN() != null ? hd.getD().getN().getHoTen() : null;
                    String vangLai = hd.getD() != null ? hd.getD().getHoten() : null;
                    return (hoTen != null && hoTen.toLowerCase().contains(q))
                        || (vangLai != null && vangLai.toLowerCase().contains(q));
                })
                .toList());
        model.addAttribute("trangThaiHoanTien", trangThaiHoanTien);
        model.addAttribute("maHoaDon", maHoaDon);
        model.addAttribute("maDatPhong", maDatPhong);
        model.addAttribute("tenKhach", tenKhach);
        return "nhan-vien/hoan-tien-list";
    }

    @GetMapping("/chi-tiet/{id}")
    public String chiTiet(@PathVariable Integer id, Model model, RedirectAttributes ra) {
        HoaDon hd = hoaDonService.findById(id);
        if (hd == null) {
            ra.addFlashAttribute("error", "Khong tim thay hoa don #" + id);
            return "redirect:/nhan-su/hoan-tien";
        }
        DatPhong dp = hd.getD();

        // Neu hoa don chua co tyLeHoan (don cu, checkout...) -> tinh lai theo rule moi
        // de trang chi tiet LUON hien thi % hoan chinh xac (0% thay vi "-").
        if (dp != null) {
            // Đảm bảo hd.ngayYeuCauHoan đã có (đơn cũ có thể null) trước khi tính lại tỷ lệ
            if (hd.getNgayYeuCauHoan() == null) {
                hd.setNgayYeuCauHoan(LocalDateTime.now());
            }
            java.math.BigDecimal tyLeTinhLai = huyDonService.tinhTyLeHoan(dp, hd);
            if (tyLeTinhLai != null) {
                hd.setTyLeHoan(tyLeTinhLai);
                java.math.BigDecimal daThu = hd.getDaThanhToan() == null
                        ? java.math.BigDecimal.ZERO : hd.getDaThanhToan();
                hd.setSoTienHoan(daThu.multiply(tyLeTinhLai).setScale(0, java.math.RoundingMode.HALF_UP));
            }
        }

        model.addAttribute("hoaDon", hd);
        model.addAttribute("datPhong", dp);
        model.addAttribute("lichSuGiaoDich", thanhToanService.findAllByHoaDonId(id));
        // Thời gian từ lúc tạo đơn đến hiện tại (hh:mm:ss hoặc "Qua han tao yeu cau huy")
        model.addAttribute("thoiGianXuLyHuy", huyDonService.tinhThoiGianXuLyYeuCauHuy(dp));
        // Khoảng cách từ "ngày tạo yêu cầu hủy" đến ngày check-in (căn cứ tính % hoàn)
        model.addAttribute("khoangCachNgayCheckIn", huyDonService.tinhKhoangCachNgayCheckIn(dp, hd));
        model.addAttribute("moTaKhoangCachNgay", huyDonService.moTaKhoangCachNgayCheckIn(dp, hd));
        return "nhan-vien/hoan-tien-chi-tiet";
    }

        @PostMapping("/{id}/chuyen-khoan")
        public String taoUrlChuyenKhoan(@PathVariable Integer id,
                                        @RequestParam String stkNhanHoan,
                                        @RequestParam String tenNganHang,
                                        @RequestParam(required = false) String ghiChu,
                                        Authentication auth,
                                        HttpServletRequest request,
                                        RedirectAttributes ra) {

            HoaDon hd = hoaDonService.findById(id);
            if (hd == null) {
                ra.addFlashAttribute("error", "Khong tim thay hoa don");
                return "redirect:/nhan-su/hoan-tien";
            }
            if (!"Cho xu ly".equals(hd.getTrangThaiHoanTien())) {
                ra.addFlashAttribute("error", "Yeu cau nay da duoc xu ly truoc do");
                return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
            }
            if (stkNhanHoan == null || stkNhanHoan.isBlank()
                    || tenNganHang == null || tenNganHang.isBlank()) {
                ra.addFlashAttribute("error", "Vui long nhap so tai khoan va ten ngan hang nhan hoan");
                return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
            }
            if (hd.getSoTienHoan() == null || hd.getSoTienHoan().signum() <= 0) {
                ra.addFlashAttribute("error", "So tien hoan khong hop le");
                return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
            }

            // Luu draft vao session de VNPay callback su dung
            RefundDraft draft = new RefundDraft(
                    hd.getId(), hd.getSoTienHoan(),
                    stkNhanHoan.trim(), tenNganHang.trim(),
                    ghiChu == null ? null : ghiChu.trim(),
                    auth == null ? null : auth.getName());
            request.getSession(true).setAttribute("refundDraft_" + hd.getId(), draft);

            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            String vnpayUrl = vnpayService.createRefundOrder(
                    hd.getId(), hd.getSoTienHoan().longValue(), baseUrl);
            return "redirect:" + vnpayUrl;
        }

    @PostMapping("/{id}/xac-nhan")
    public String xacNhan(@PathVariable Integer id,
                          @RequestParam String phuongThucHoan,
                          @RequestParam(required = false) String maGiaoDichHoan,
                          @RequestParam(required = false) String stkNhanHoan,
                          @RequestParam(required = false) String tenNganHang,
                          @RequestParam(required = false) String ghiChu,
                          @RequestParam(required = false) BigDecimal soTienHoanNhap,
                          Authentication auth,
                          RedirectAttributes ra) {

        HoaDon hd = hoaDonService.findById(id);
        if (hd == null) {
            ra.addFlashAttribute("error", "Khong tim thay hoa don");
            return "redirect:/nhan-su/hoan-tien";
        }

        if (!"Cho xu ly".equals(hd.getTrangThaiHoanTien())) {
            ra.addFlashAttribute("error", "Yeu cau nay da duoc xu ly truoc do, khong the xac nhan lai");
            return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
        }

        if ("Chuyen Khoan".equals(phuongThucHoan)
                && (stkNhanHoan == null || stkNhanHoan.isBlank())) {
            ra.addFlashAttribute("error", "Vui long nhap so tai khoan nhan hoan truoc khi xac nhan");
            return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
        }

        if ("Tien Mat".equals(phuongThucHoan)
                && (soTienHoanNhap == null || soTienHoanNhap.signum() < 0)) {
            ra.addFlashAttribute("error", "Vui long nhap gia hoan tien hop le (>= 0)");
            return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
        }

        NhanSu nvXuLy = nhanSuService.FindByemail(auth.getName());

        huyDonService.xacNhanHoanTien(id, phuongThucHoan, maGiaoDichHoan,
                stkNhanHoan, tenNganHang, ghiChu, soTienHoanNhap, nvXuLy);

        ra.addFlashAttribute("success", "Da xac nhan hoan tien cho hoa don #" + id);
        return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
    }

    @PostMapping("/{id}/tu-choi")
    public String tuChoi(@PathVariable Integer id,
                         @RequestParam String lyDo,
                         RedirectAttributes ra) {

        HoaDon hd = hoaDonService.findById(id);
        if (hd == null) {
            ra.addFlashAttribute("error", "Khong tim thay hoa don");
            return "redirect:/nhan-su/hoan-tien";
        }

        if (!"Cho xu ly".equals(hd.getTrangThaiHoanTien())) {
            ra.addFlashAttribute("error", "Yeu cau nay da duoc xu ly truoc do, khong the tu choi lai");
            return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
        }

        huyDonService.tuChoiHoanTien(id, lyDo);
        ra.addFlashAttribute("success", "Da tu choi yeu cau hoan tien");
        return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
    }

    /**
     * Bước 2 (luồng hủy đơn không hoàn): NV xác nhận hủy đơn nhưng không hoàn tiền.
     * Áp dụng khi tỷ lệ hoàn = 0% theo rule, hoặc NV chọn không hoàn.
     * Đơn chuyển "Da huy", hóa đơn cập nhật "Huy khong hoan" + số tiền hoàn = 0,
     * hóa đơn vẫn được phép xuất PDF cho khách cầm về (minh bạch).
     */
    @PostMapping("/{id}/huy-khong-hoan")
    public String huyKhongHoan(@PathVariable Integer id,
                               @RequestParam(required = false) String lyDo,
                               Authentication auth,
                               RedirectAttributes ra) {

        HoaDon hd = hoaDonService.findById(id);
        if (hd == null) {
            ra.addFlashAttribute("error", "Khong tim thay hoa don");
            return "redirect:/nhan-su/hoan-tien";
        }

        if (!"Cho xu ly".equals(hd.getTrangThaiHoanTien())) {
            ra.addFlashAttribute("error", "Yeu cau nay da duoc xu ly truoc do, khong the thuc hien lai");
            return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
        }

        NhanSu nvXuLy = nhanSuService.FindByemail(auth.getName());
        huyDonService.xacNhanHuyKhongHoan(id, lyDo, nvXuLy);

        ra.addFlashAttribute("success", "Da huy don khong hoan tien. Hoa don van duoc phep xuat PDF de khach can minh bach.");
        return "redirect:/nhan-su/hoan-tien/chi-tiet/" + id;
    }
}