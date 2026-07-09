package su26sd09.su26sd09.controller;

import com.lowagie.text.pdf.BaseFont;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quy trinh tra phong (checkout) tai quay danh cho Le Tan / Admin.
 *
 * Cac buoc chuan cua mot nghiep vu checkout khach san:
 *  1. Chon don dang o trang thai "Da nhan phong" (khach dang luu tru).
 *  2. Xem lai folio: tien phong + cac dich vu phat sinh (co the bo sung dich vu
 *     truoc khi chot so).
 *  3. He thong tinh lai tong tien (tien phong, tien dich vu, VAT, giam gia neu co).
 *  4. Thu no con lai, ghi nhan thanh toan va xuat/ cap nhat hoa don.
 *  5. Chuyen trang thai don sang "Da tra phong" va giai phong (tra phong ve trang
 *     thai "Trong") de co the ban/ cho thue tiep.
 */
@Controller
@RequestMapping("/nhan-su/checkout")
public class NhanVienCheckoutController {

    @Autowired private DatPhongService datPhongService;
    @Autowired private ChiTietDatPhongService chiTietDatPhongService;
    @Autowired private ChiTietDichVuService ctdvService;
    @Autowired private DichVuService dichVuService;
    @Autowired private PhongService phongService;
    @Autowired private HoaDonService hoaDonService;
    @Autowired private ThanhToanService thanhToanService;
    @Autowired private NguoiDungService nguoiDungService;
    @Autowired private NhanVienService nhanVienService;
    @Autowired private TemplateEngine templateEngine;

    private static final BigDecimal VAT = new BigDecimal("0.10");

    // ================= QUYEN TRUY CAP =================

    /**
     * Cho phep ROLE_ADMIN hoac nhan vien bo phan "Le Tan" (giong quy uoc dang
     * dung o NhanVienDatPhongController) truy cap nghiep vu tra phong.
     */
    private boolean coQuyenCheckout(Authentication authentication) {
        if (authentication == null) return false;
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        KhachHang n = nguoiDungService.findByEmail(authentication.getName());
        if (n == null) return false;
        NhanSu nv = nhanVienService.FindByemail(authentication.getName());
        return nv != null && "Lễ Tân".equals(nv.getBoPhan());
    }

    private NhanSu nhanVienHienTai(Authentication authentication) {
        return nhanVienService.FindByemail(authentication.getName());
    }

    // ================= TINH TOAN FOLIO =================

    private Map<String, BigDecimal> tinhFolio(DatPhong dp) {
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(dp.getId());
        List<Chi_tiet_dich_vu> dichVuList = ctdvService.findByDatPhongId(dp.getId());

        BigDecimal tienPhong = phongList.stream()
                .map(ChiTietDatPhong::getGiaKhiDat)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tienDichVu = dichVuList.stream()
                .map(Chi_tiet_dich_vu::getDonGia)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tienGiam = BigDecimal.ZERO; // giam gia (neu co) da duoc ap vao gia tung phong luc dat

        BigDecimal tienVat = tienPhong.add(tienDichVu)
                .multiply(VAT)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal tongTien = tienPhong.add(tienDichVu).add(tienVat).subtract(tienGiam);

        Map<String, BigDecimal> ketQua = new LinkedHashMap<>();
        ketQua.put("tienPhong", tienPhong);
        ketQua.put("tienDichVu", tienDichVu);
        ketQua.put("tienGiam", tienGiam);
        ketQua.put("tienVat", tienVat);
        ketQua.put("tongTien", tongTien);
        return ketQua;
    }

    private void napModelChiTiet(DatPhong dp, Model model) {
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(dp.getId());
        List<Chi_tiet_dich_vu> dichVuList = ctdvService.findByDatPhongId(dp.getId());
        Map<String, BigDecimal> folio = tinhFolio(dp);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(dp.getId());
        BigDecimal daThu = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal conLai = folio.get("tongTien").subtract(daThu);
        if (conLai.compareTo(BigDecimal.ZERO) < 0) conLai = BigDecimal.ZERO;

        long soDem = 1;
        if (dp.getNgaydatPhong() != null && dp.getNgaytraPhong() != null) {
            soDem = ChronoUnit.DAYS.between(dp.getNgaydatPhong().toLocalDate(), dp.getNgaytraPhong().toLocalDate());
            if (soDem <= 0) soDem = 1;
        }

        model.addAttribute("dp", dp);
        model.addAttribute("phongList", phongList);
        model.addAttribute("dichVuList", dichVuList);
        model.addAttribute("dichVuOptions", dichVuService.findAll());
        model.addAttribute("soDem", soDem);
        model.addAttribute("tienPhong", folio.get("tienPhong"));
        model.addAttribute("tienDichVu", folio.get("tienDichVu"));
        model.addAttribute("tienGiam", folio.get("tienGiam"));
        model.addAttribute("tienVat", folio.get("tienVat"));
        model.addAttribute("tongTien", folio.get("tongTien"));
        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("daThu", daThu);
        model.addAttribute("conLai", conLai);
    }

    // ================= DANH SACH DON CAN TRA PHONG =================

    @GetMapping("")
    public String danhSach(
            @RequestParam(required = false) Integer maDatPhong,
            @RequestParam(required = false) String tenKhach,
            @RequestParam(required = false) String maCccd,
            Authentication authentication,
            Model model) {

        if (!coQuyenCheckout(authentication)) {
            return "redirect:/home"; //TODO: THEM URL DASHBOARD VAO DAY
        }

        List<DatPhong> dsCanTra = datPhongService.findAll().stream()
                .filter(dp -> "Da nhan phong".equals(dp.getTrangThai()))
                .filter(dp -> maDatPhong == null || maDatPhong.equals(dp.getId()))
                .filter(dp -> tenKhach == null || tenKhach.isBlank() ||
                        (dp.getN() != null && dp.getN().getHoTen() != null &&
                                dp.getN().getHoTen().toLowerCase().contains(tenKhach.trim().toLowerCase())) ||
                        (dp.getHoten() != null &&
                                dp.getHoten().toLowerCase().contains(tenKhach == null ? "" : tenKhach.trim().toLowerCase())))
                .filter(dp -> maCccd == null || maCccd.isBlank() ||
                        (dp.getMa_cccd() != null && dp.getMa_cccd().contains(maCccd.trim())))
                .sorted((a, b) -> Integer.compare(b.getId(), a.getId()))
                .toList();

        Map<Integer, List<Phong>> phongTheoDon = new LinkedHashMap<>();
        Map<Integer, BigDecimal> tongTienTheoDon = new LinkedHashMap<>();
        for (DatPhong dp : dsCanTra) {
            phongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
            tongTienTheoDon.put(dp.getId(), tinhFolio(dp).get("tongTien"));
        }

        model.addAttribute("datPhongs", dsCanTra);
        model.addAttribute("phongTheoDon", phongTheoDon);
        model.addAttribute("tongTienTheoDon", tongTienTheoDon);
        model.addAttribute("maDatPhong", maDatPhong);
        model.addAttribute("tenKhach", tenKhach);
        model.addAttribute("maCccd", maCccd);

        return "nhan-vien/checkout-list";
    }

    // ================= CHI TIET / PHIEU TRA PHONG =================

    @GetMapping("/{id}")
    public String chiTiet(@PathVariable Integer id, Authentication authentication,
                           Model model, RedirectAttributes redirectAttributes) {

        if (!coQuyenCheckout(authentication)) {
            return "redirect:/home"; //TODO: THEM URL DASHBOARD VAO DAY
        }

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/checkout";
        }

        napModelChiTiet(dp, model);
        return "nhan-vien/checkout-chi-tiet";
    }

    // ================= THEM DICH VU PHAT SINH TRUOC KHI CHOT SO =================

    @PostMapping("/{id}/them-dich-vu")
    public String themDichVu(@PathVariable Integer id,
                              @RequestParam Integer maDichVu,
                              @RequestParam(defaultValue = "1") Integer soLuong,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {

        if (!coQuyenCheckout(authentication)) {
            return "redirect:/home";
        }

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/checkout";
        }
        if (!"Da nhan phong".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể thêm dịch vụ.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        Dich_vu dv = dichVuService.findById(maDichVu);
        if (dv == null) {
            redirectAttributes.addFlashAttribute("error", "Dịch vụ không tồn tại.");
            return "redirect:/nhan-su/checkout/" + id;
        }
        if (soLuong == null || soLuong < 1) soLuong = 1;

        Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
        ct.setDatPhong(dp);
        ct.setDv(dv);
        ct.setSoluong(soLuong);
        ct.setDonGia(dv.getGia().multiply(BigDecimal.valueOf(soLuong)));
        ct.setNgay_su_dung(LocalDateTime.now());
        ct.setGhichu("Phát sinh lúc trả phòng");
        ctdvService.save(ct);

        redirectAttributes.addFlashAttribute("success", "Đã thêm dịch vụ \"" + dv.getTen_dich_vu() + "\" vào đơn #" + id);
        return "redirect:/nhan-su/checkout/" + id;
    }

    // ================= XAC NHAN TRA PHONG + THU TIEN =================

    @PostMapping("/{id}/xac-nhan")
    public String xacNhanTraPhong(@PathVariable Integer id,
                                   @RequestParam(defaultValue = "Tien mat") String phuongThucThanhToan,
                                   @RequestParam(required = false) String ghiChu,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {

        if (!coQuyenCheckout(authentication)) {
            return "redirect:/home"; //TODO: THEM URL DASHBOARD VAO DAY
        }

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/checkout";
        }
        if (!"Da nhan phong".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể trả phòng.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        Map<String, BigDecimal> folio = tinhFolio(dp);
        BigDecimal tienPhong = folio.get("tienPhong");
        BigDecimal tienDichVu = folio.get("tienDichVu");
        BigDecimal tienGiam = folio.get("tienGiam");
        BigDecimal tienVat = folio.get("tienVat");
        BigDecimal tongTien = folio.get("tongTien");

        NhanSu nguoiXuLy = nhanVienService.FindByemail(authentication.getName());
        NhanSu nvHienTai = nhanVienHienTai(authentication);

        // Tao moi hoac cap nhat hoa don gan voi don dat phong nay
        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        boolean laHoaDonMoi = (hoaDon == null);
        if (laHoaDonMoi) {
            hoaDon = new HoaDon();
            hoaDon.setD(dp);
            hoaDon.setDaThanhToan(BigDecimal.ZERO);
            hoaDon.setNgayXuat(LocalDateTime.now());
        } else {
            hoaDon.setNgayCapNhat(LocalDateTime.now());
        }
        hoaDon.setTienPhong(tienPhong);
        hoaDon.setTienDichVu(tienDichVu);
        hoaDon.setTienGiam(tienGiam);
        hoaDon.setTienVat(tienVat);
        hoaDon.setTongTien(tongTien);
        hoaDon.setK(dp.getKm());
        hoaDon.setN(nguoiXuLy);
        if (hoaDon.getGhiChu() == null || hoaDon.getGhiChu().isBlank()) {
            hoaDon.setGhiChu("Hóa đơn trả phòng cho đơn #" + id);
        }

        BigDecimal daThanhToanTruoc = hoaDon.getDaThanhToan() == null ? BigDecimal.ZERO : hoaDon.getDaThanhToan();
        BigDecimal conLai = tongTien.subtract(daThanhToanTruoc);

        if (conLai.compareTo(BigDecimal.ZERO) > 0) {
            ThanhToan tt = new ThanhToan();
            tt.setH(hoaDon);
            tt.setPhuongThuc(phuongThucThanhToan);
            tt.setSoTien(conLai);
            tt.setTrangThai("Thanh cong");
            tt.setNgaythanhToan(LocalDateTime.now());
            tt.setNv(nvHienTai);
            tt.setGichu(ghiChu != null && !ghiChu.isBlank() ? ghiChu : "Thu tiền còn lại khi trả phòng #" + id);

            hoaDon = hoaDonService.save(hoaDon);
            tt.setH(hoaDon);
            thanhToanService.save(tt);

            hoaDon.setDaThanhToan(tongTien);
            hoaDon = hoaDonService.save(hoaDon);
        } else {
            hoaDon = hoaDonService.save(hoaDon);
        }

        // Cap nhat trang thai don va giai phong cac phong da o
        dp.setTrangThai("Da tra phong");
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ct : phongList) {
            Phong p = ct.getP();
            if (p != null) {
                p.setTrangThai("Trong");
                phongService.save1(p);
            }
        }

        redirectAttributes.addFlashAttribute("success",
                "Trả phòng thành công cho đơn #" + id + ". Hóa đơn #" + hoaDon.getId() +
                        " - Tổng tiền: " + tongTien.toPlainString() + " VND.");
        return "redirect:/nhan-su/checkout/" + id;
    }

    // ================= XUAT HOA DON PDF =================

    @GetMapping("/{id}/xuat-pdf")
    public void xuatPdf(@PathVariable Integer id, Authentication authentication,
                         HttpServletResponse response) throws Exception {

        if (!coQuyenCheckout(authentication)) {
            response.sendRedirect("/home");
            return;
        }

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        if (hoaDon == null) {
            response.sendRedirect("/nhan-su/checkout/" + id);
            return;
        }

        // Tinh tong phu phi ngoai gio tu cac phong trong don
        BigDecimal tongPhuThu = BigDecimal.ZERO;
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ct : phongList) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThu = tongPhuThu.add(ct.getPhuPhi());
            }
        }

        Context context = new Context();
        context.setVariable("hoaDon", hoaDon);
        context.setVariable("tongPhuThu", tongPhuThu);

        String html = templateEngine.process("nhan-vien/hoa-don-pdf", context);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=hoa-don-" + hoaDon.getId() + ".pdf");

        ITextRenderer renderer = new ITextRenderer();
        renderer.getFontResolver().addFont(
                "C:/Windows/Fonts/arial.ttf",
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED
        );
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(response.getOutputStream());
    }
}
