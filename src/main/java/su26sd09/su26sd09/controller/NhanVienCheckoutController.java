package su26sd09.su26sd09.controller;

import com.lowagie.text.pdf.BaseFont;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Autowired private su26sd09.su26sd09.repository.ThanhToanRepo thanhToanRepo;
    @Autowired private NhanVienService nhanVienService;
    @Autowired private TemplateEngine templateEngine;
    @Autowired private su26sd09.su26sd09.service.LichSuHoatDongService lichSuHoatDongService;
    @Autowired private InvoicePricingService invoicePricingService;

    // ================= QUYEN TRUY CAP =================

    /**
     * Cho phep ROLE_ADMIN hoac nhan vien bo phan "Le Tan" (giong quy uoc dang
     * dung o NhanVienDatPhongController) truy cap nghiep vu tra phong.
     *
     * Luu y: authentication.getName() voi nhan vien la email trong bang nhan_su
     * (khong phai khach_hang), nen khong check qua nguoiDungService (bảng
     * khach_hang) — se luon tra null voi tai khoan nhan vien.
     * Su dung laLeTanDangHoatDong() de chap nhan ca "Lễ Tân" va "Le Tan".
     */
    private boolean coQuyenCheckout(Authentication authentication) {
        if (authentication == null) return false;
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        NhanSu nv = nhanVienService.FindByemail(authentication.getName());
        return nhanVienService.laLeTanDangHoatDong(nv);
    }

    private NhanSu nhanVienHienTai(Authentication authentication) {
        return nhanVienService.FindByemail(authentication.getName());
    }

    // ================= TINH TOAN FOLIO =================

    /**
     * VIEW: xem folio cua don (khong luu DB). Doc gia tu bang trung gian
     * (chi_tiet_dat_phong + chi_tiet_dich_vu) va tinh bang cong thuc CHUAN
     * dung chung voi moi luong khac (xem InvoicePricingService) - VAT tren
     * gia SAU giam.
     */
    private Map<String, BigDecimal> tinhFolio(DatPhong dp) {
        su26sd09.su26sd09.dto.InvoicePricingResult gia =
                invoicePricingService.previewInvoice(dp.getId(), dp.getKm());

        Map<String, BigDecimal> ketQua = new LinkedHashMap<>();
        ketQua.put("tienPhong", gia.getTienPhong());
        ketQua.put("tienDichVu", gia.getTienDichVu());
        ketQua.put("tienGiam", gia.getTienGiam());
        ketQua.put("tienVat", gia.getTienVat());
        ketQua.put("tongTien", gia.getTongTien());
        return ketQua;
    }

    /**
     * Tính phụ phí TRẢ MUỘN so với giờ đã đặt (late checkout) cho từng phòng của
     * đơn. So sánh {@code gioTraHienTai} (thường là {@code LocalDateTime.now()})
     * với {@code dp.getNgaytraPhong()} (giờ đã đặt trên booking) — chỉ tính phí
     * khi khách thực sự TRẢ SAU giờ đã đặt.
     *
     * <p>Quan trọng: KHÔNG dùng {@code calculateExtraFeeFor(...)} ở đây vì hàm
     * đó so sánh với giờ chuẩn của phòng (vd 11:00) → sẽ tính phí SAI cho mọi
     * khách checkout trong khung giờ từ sau 11:00 đến đúng giờ đã đặt.</p>
     */
    private BigDecimal tinhPhuPhiTraMuon(DatPhong dp, LocalDateTime gioTraHienTai) {
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(dp.getId());
        BigDecimal phuPhiTraMuon = BigDecimal.ZERO;
        for (ChiTietDatPhong ct : phongList) {
            if (ct != null && ct.getP() != null) {
                BigDecimal fee = phongService.calculateLateCheckoutFeeFor(
                        ct.getP().getMaPhong(),
                        dp.getNgaydatPhong(),
                        dp.getNgaytraPhong(),
                        gioTraHienTai);
                if (fee != null && fee.signum() > 0) {
                    phuPhiTraMuon = phuPhiTraMuon.add(fee);
                }
            }
        }
        return phuPhiTraMuon;
    }

    private void napModelChiTiet(DatPhong dp, Model model) {
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(dp.getId());
        List<Chi_tiet_dich_vu> dichVuList = ctdvService.findByDatPhongId(dp.getId());

        // Phu phi tra muon: cong don calculateExtraFeeFor cho tung phong (theo thoi gian hien tai)
        BigDecimal phuPhiTraMuon = tinhPhuPhiTraMuon(dp, LocalDateTime.now());

        Map<String, BigDecimal> folio = tinhFolio(dp);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(dp.getId());
        BigDecimal daThu = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal daHoanTra = (hoaDon != null && hoaDon.getDaHoanTra() != null)
                ? hoaDon.getDaHoanTra() : BigDecimal.ZERO;
        // QUAN TRONG: phai cong phuPhiTraMuon vao tongTien o day, vi day la cung
        // mot cong thuc duoc dung khi chot tra phong that su (xem chotTraPhong /
        // Phase 2 ben duoi: tongTien = tongTienGoc.add(phuPhiTraMuon)). Neu khong
        // cong vao day, trang nay se hien thi "da thu du, co the chot tra phong"
        // trong khi luc bam chot thuc su lai bi validate bao con thieu tien phu phi.
        BigDecimal tongTien = folio.get("tongTien").add(phuPhiTraMuon);

        // soDu > 0: con no ; soDu < 0: thua tien can hoan ; soDu == 0: da can bang
        BigDecimal soDu = tongTien.subtract(daThu).add(daHoanTra);
        BigDecimal canThu = soDu.compareTo(BigDecimal.ZERO) > 0 ? soDu : BigDecimal.ZERO;
        BigDecimal canHoan = soDu.compareTo(BigDecimal.ZERO) < 0 ? soDu.negate() : BigDecimal.ZERO;

        // conLai giu nguyen tuong thich voi template (luon >= 0)
        BigDecimal conLai = canThu;

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
        model.addAttribute("phuPhiTraMuon", phuPhiTraMuon);
        model.addAttribute("tongTien", tongTien);
        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("daThu", daThu);
        model.addAttribute("daHoanTra", daHoanTra);
        model.addAttribute("soDu", soDu);
        model.addAttribute("canThu", canThu);
        model.addAttribute("canHoan", canHoan);
        model.addAttribute("conLai", conLai);
        model.addAttribute("trangThaiHoanTien",
                hoaDon != null && hoaDon.getTrangThaiHoanTien() != null
                        ? hoaDon.getTrangThaiHoanTien() : "");

        // Lich su thanh toan (neu co hoa don)
        List<ThanhToan> lichSuThanhToan = new ArrayList<>();
        if (hoaDon != null) {
            lichSuThanhToan = thanhToanRepo.findByH_IdOrderByNgaythanhToanAsc(hoaDon.getId());
            if (lichSuThanhToan == null) lichSuThanhToan = new ArrayList<>();
        }
        model.addAttribute("lichSuThanhToan", lichSuThanhToan);

        // Text-format sẵn các số tiền có dấu +/- để template khỏi vướng literal Thymeleaf
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"));
        BigDecimal tienGiamLocal = (BigDecimal) model.getAttribute("tienGiam");
        if (tienGiamLocal == null) tienGiamLocal = BigDecimal.ZERO;
        model.addAttribute("tienGiamText", "- " + nf.format(tienGiamLocal) + " VND");
        model.addAttribute("daThuText", "- " + nf.format(daThu) + " VND");
        model.addAttribute("daHoanTraText", "+ " + nf.format(daHoanTra) + " VND");
    }

    // ================= TRANG TONG HOP CHECK-OUT (LIST + DETAIL) =================

    /**
     * Trang tong hop check-out: gop list + detail tren 1 view (giong check-in).
     * - idRaw co the la so don ("42"), hoac "today"/"hom-nay" (khong co chi tiet).
     * - Co the override bang ?id=N trong query string.
     * - ?ngay=YYYY-MM-DD loc don theo ngay tra phong.
     * - ?thang=YYYY-MM chuyen lich sang thang khac.
     */
    @GetMapping({"", "/{id}"})
    public String checkoutDp(@PathVariable(value = "id", required = false) String idRaw,
                             @RequestParam(value = "id", required = false) Integer idOverride,
                             @RequestParam(value = "ngay", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayChon,
                             @RequestParam(value = "thang", required = false) String thangRaw,
                             @RequestParam(value = "q", required = false) String q,
                             @RequestParam(value = "tuNgay", required = false) String tuNgayRaw,
                             @RequestParam(value = "denNgay", required = false) String denNgayRaw,
                             Authentication authentication,
                             Model model,
                             RedirectAttributes redirectAttributes) {

        if (!coQuyenCheckout(authentication)) {
            return "redirect:/home";
        }

        Integer maDon = (idOverride != null) ? idOverride : parseIdParam(idRaw);

        buildCheckoutList(model, ngayChon, thangRaw, q, tuNgayRaw, denNgayRaw);

        if (maDon != null) {
            DatPhong dp = datPhongService.findById(maDon);
            if (dp == null) {
                redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + maDon);
                return "redirect:/nhan-su/checkout/today";
            }
            // Chi cho checkout voi don dang luu tru hoac da tra phong (xem hoa don)
            if (!"Da nhan phong".equals(dp.getTrangThai())
                    && !"Da tra phong".equals(dp.getTrangThai())) {
                redirectAttributes.addFlashAttribute("error",
                        "Don #" + dp.getId() + " dang o trang thai '" + dp.getTrangThai()
                                + "' — khong the thuc hien thao tac checkout.");
                return "redirect:/nhan-su/checkout/today";
            }
            napModelChiTiet(dp, model);
        }

        return "nhan-vien/checkout-chi-tiet";
    }

    /**
     * Parse path variable idRaw -> Integer maDon.
     * Tra ve null neu idRaw la null / "today" / "hom-nay" / khong phai so.
     */
    private Integer parseIdParam(String idRaw) {
        if (idRaw == null || idRaw.isBlank()) return null;
        String s = idRaw.trim();
        if ("today".equalsIgnoreCase(s) || "hom-nay".equalsIgnoreCase(s)) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Parse ?thang=YYYY-MM trên URL. Trả về null nếu rỗng/sai định dạng. */
    private LocalDate parseCheckoutThangParam(String thangRaw) {
        if (thangRaw == null || thangRaw.isBlank()) return null;
        try {
            YearMonth ym = YearMonth.parse(thangRaw.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
            return ym.atDay(1);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** Build tháng trước / tháng sau dạng YYYY-MM cho URL. */
    private String checkoutThangLienKe(LocalDate thangHienThi, int delta) {
        if (thangHienThi == null) return null;
        YearMonth ym = YearMonth.from(thangHienThi).plusMonths(delta);
        return ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /**
     * Build phan list (lich ngay tra + danh sach don) cho trang check-out.
     * Loc don co ngaytraPhong trong khoang [tuNgay, denNgay] va trang thai
     * "Da nhan phong" (chua tra) hoac "Da tra phong" (xem lai hoa don).
     */
    private void buildCheckoutList(Model model, LocalDate ngayChon,
                                    String thangRaw, String q,
                                    String tuNgayRaw, String denNgayRaw) {
        boolean dangLocKhoangNgay = tuNgayRaw != null && !tuNgayRaw.isBlank();
        // Uu tien: 1) ?ngay=... (user click 1 ngay), 2) ?thang=YYYY-MM (chuyen thang),
        // 3) hom nay neu khong co gi ca.
        LocalDate thangNgay;
        if (ngayChon != null) {
            thangNgay = ngayChon;
        } else if (thangRaw != null && !thangRaw.isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(thangRaw.trim(),
                        DateTimeFormatter.ofPattern("yyyy-MM"));
                thangNgay = ym.atDay(1);
            } catch (DateTimeParseException ex) {
                thangNgay = LocalDate.now();
            }
        } else {
            thangNgay = LocalDate.now();
        }
        LocalDateTime thangHienThi = thangNgay.withDayOfMonth(1).atTime(LocalTime.now());

        // tuNgay/denNgay: khoang loc don cho danh sach
        LocalDate tuNgay;
        LocalDate denNgay;
        if (dangLocKhoangNgay) {
            tuNgay = LocalDate.parse(tuNgayRaw);
            denNgay = LocalDate.parse(denNgayRaw);
        } else if (ngayChon != null) {
            // User da click 1 ngay tren lich -> chi loc don dung ngay do
            tuNgay = ngayChon;
            denNgay = ngayChon;
        } else {
            // Mac dinh: hien thi toan bo thang hien tai (khong co ngay click)
            tuNgay = thangNgay.withDayOfMonth(1);
            denNgay = thangNgay.withDayOfMonth(thangNgay.lengthOfMonth());
        }

        // Khoang ngay cho DAI NGAY (lich): luon la toan bo thang hien tai de nguoi
        // dung co the chon sang ngay khac ma khong mat luoi.
        LocalDate tuNgayLich = thangNgay.withDayOfMonth(1);
        LocalDate denNgayLich = thangNgay.withDayOfMonth(thangNgay.lengthOfMonth());

        String tuKhoa = (q == null) ? "" : q.trim().toLowerCase();

        // Loc don theo ngay tra phong + trang thai hop le cho checkout
        List<DatPhong> dsDon = datPhongService.findAll().stream()
                .filter(dp -> dp.getNgaytraPhong() != null)
                .filter(dp -> "Da nhan phong".equals(dp.getTrangThai())
                        || "Da tra phong".equals(dp.getTrangThai()))
                .filter(dp -> !dp.getNgaytraPhong().toLocalDate().isBefore(tuNgay)
                        && !dp.getNgaytraPhong().toLocalDate().isAfter(denNgay))
                .filter(dp -> tuKhoa.isEmpty()
                        || (dp.getHoten() != null && dp.getHoten().toLowerCase().contains(tuKhoa))
                        || (dp.getN() != null && dp.getN().getHoTen() != null
                                && dp.getN().getHoTen().toLowerCase().contains(tuKhoa))
                        || (dp.getSdt() != null && dp.getSdt().contains(tuKhoa))
                        || String.valueOf(dp.getId()).contains(tuKhoa))
                .sorted(Comparator.comparing(DatPhong::getNgaytraPhong))
                .collect(Collectors.toList());

        Map<Integer, List<ChiTietDatPhong>> mapCtdp = new HashMap<>();
        for (DatPhong d : dsDon) {
            mapCtdp.put(d.getId(), chiTietDatPhongService.findByDatPhongId(d.getId()));
        }

        // Dải ngày trong tháng (đếm số đơn sắp trả mỗi ngày)
        // Luon duyet theo tuNgayLich..denNgayLich (toan bo thang) de khong mat luoi
        // khi user da click 1 ngay bat ky.
        List<Map<String, Object>> dsNgayTrongThang = new ArrayList<>();
        LocalDate homNay = LocalDate.now();
        for (LocalDate d = tuNgayLich; !d.isAfter(denNgayLich); d = d.plusDays(1)) {
            LocalDate finalD = d;
            long soDon = datPhongService.findAll().stream()
                    .filter(x -> x.getNgaytraPhong() != null)
                    .filter(x -> "Da nhan phong".equals(x.getTrangThai())
                            || "Da tra phong".equals(x.getTrangThai()))
                    .filter(x -> x.getNgaytraPhong().toLocalDate().equals(finalD))
                    .count();
            Map<String, Object> ng = new LinkedHashMap<>();
            ng.put("ngay", finalD);
            ng.put("laHomNay", finalD.equals(homNay));
            ng.put("dangChon", finalD.equals(ngayChon));
            ng.put("soDon", soDon);
            dsNgayTrongThang.add(ng);
        }

        model.addAttribute("danhSachDon", dsDon);
        model.addAttribute("mapCtdp", mapCtdp);
        model.addAttribute("ngayChon", ngayChon);
        model.addAttribute("q", q);
        model.addAttribute("thangHienThi", thangHienThi);
        model.addAttribute("dsNgayTrongThang", dsNgayTrongThang);
        model.addAttribute("dangLocKhoangNgay", dangLocKhoangNgay);
        model.addAttribute("tuNgay", tuNgayRaw);
        model.addAttribute("denNgay", denNgayRaw);
        model.addAttribute("thangTruoc", checkoutThangLienKe(thangNgay, -1));
        model.addAttribute("thangSau", checkoutThangLienKe(thangNgay, 1));
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

        // NEW: dich vu vua duoc gan vao don lan dau -> lay don gia truc tiep tu Dich_vu.
        Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
        ct.setDatPhong(dp);
        ct.setDv(dv);
        ct.setSoluong(soLuong);
        ct.setDonGia(invoicePricingService.createServiceLineItemPrice(dv, soLuong));
        ct.setNgay_su_dung(LocalDateTime.now());
        ct.setGhichu("Phát sinh lúc trả phòng");
        ctdvService.save(ct);

        // Dong bo lai tienDichVu/tienVat/tongTien cua hoa don theo tong don_gia
        // moi nhat trong chi_tiet_dich_vu (bao gom dich vu vua them o tren),
        // tranh hoa don bi lech - thieu khoan dich vu vua phat sinh nay.
        hoaDonService.dongBoTienDichVuTuChiTiet(id);

        redirectAttributes.addFlashAttribute("success", "Đã thêm dịch vụ \"" + dv.getTen_dich_vu() + "\" vào đơn #" + id);
        return "redirect:/nhan-su/checkout/" + id;
    }

    // ================= PHASE 1: THU TIEN (xu ly so du > 0) =================

    /**
     * Phase 1a: thu phan con no cua khach (soDu > 0).
     * Tao ThanhToan loaiGiaoDich = "Thu tien", cap nhat HoaDon.daThanhToan,
     * luu qua saveWithPaymentStatusCheck() de dong bo trangThai hoa don.
     * Redirect ve trang chi tiet de render lai 3-case.
     */
    @PostMapping("/{id}/thu-tien")
    public String thuTien(@PathVariable Integer id,
                          @RequestParam(defaultValue = "Tien mat") String phuongThuc,
                          @RequestParam(required = false) String ghiChu,
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
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể thu tiền.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        Map<String, BigDecimal> folio = tinhFolio(dp);
        // Phai cong phu phi tra muon vao day, cung cong thuc voi trang xem
        // (napModelChiTiet) va voi luc chot tra phong that su (xacNhanTraPhong),
        // neu khong "khách còn nợ 100.000" tren man hinh se khong the thu duoc.
        BigDecimal phuPhiTraMuon = tinhPhuPhiTraMuon(dp, LocalDateTime.now());
        BigDecimal tongTien = folio.get("tongTien").add(phuPhiTraMuon);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        if (hoaDon == null) {
            // Khoi tao hoa don tam de ghi nhan giao dich
            NhanSu nguoiXuLy = nhanVienService.FindByemail(authentication.getName());
            hoaDon = new HoaDon();
            hoaDon.setD(dp);
            hoaDon.setDaThanhToan(BigDecimal.ZERO);
            hoaDon.setTienPhong(folio.get("tienPhong"));
            hoaDon.setTienDichVu(folio.get("tienDichVu"));
            hoaDon.setTienGiam(folio.get("tienGiam"));
            hoaDon.setTienVat(folio.get("tienVat"));
            hoaDon.setTongTien(tongTien);
            hoaDon.setK(dp.getKm());
            hoaDon.setN(nguoiXuLy);
            hoaDon.setNgayXuat(LocalDateTime.now());
            hoaDon.setGhiChu("Hóa đơn trả phòng cho đơn #" + id);
        } else {
            hoaDon.setNgayCapNhat(LocalDateTime.now());
        }
        hoaDon = hoaDonService.saveWithPaymentStatusCheck(hoaDon);

        BigDecimal daThanhToan = hoaDon.getDaThanhToan() == null ? BigDecimal.ZERO : hoaDon.getDaThanhToan();
        BigDecimal daHoanTra = hoaDon.getDaHoanTra() == null ? BigDecimal.ZERO : hoaDon.getDaHoanTra();
        BigDecimal soDu = tongTien.subtract(daThanhToan).add(daHoanTra);
        BigDecimal canThu = soDu.compareTo(BigDecimal.ZERO) > 0 ? soDu : BigDecimal.ZERO;

        if (canThu.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "Khách đã thanh toán đủ, không còn khoản phải thu.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        NhanSu nvHienTai = nhanVienHienTai(authentication);
        ThanhToan tt = new ThanhToan();
        tt.setH(hoaDon);
        tt.setPhuongThuc(phuongThuc);
        tt.setSoTien(canThu);
        tt.setLoaiGiaoDich("Thu tien");
        tt.setTrangThai("Thanh cong");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setNv(nvHienTai);
        tt.setGichu(ghiChu != null && !ghiChu.isBlank() ? ghiChu : "Thu tiền còn lại khi trả phòng #" + id);
        thanhToanService.save(tt);

        hoaDon.setDaThanhToan(daThanhToan.add(canThu));
        hoaDonService.saveWithPaymentStatusCheck(hoaDon);

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_THU_TIEN,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_HOA_DON,
                hoaDon.getId(),
                "Thu " + canThu.toPlainString() + " VND (" + phuongThuc + ") cho đơn #" + id);

        redirectAttributes.addFlashAttribute("success",
                "Đã thu " + canThu.toPlainString() + " VND cho đơn #" + id + ".");
        return "redirect:/nhan-su/checkout/" + id;
    }

    // ================= PHASE 1: GHI NHAN HOAN TIEN (xu ly so du < 0) =================

    /**
     * Phase 1b: ghi nhan hoan tien khi khach tra thua (soDu < 0).
     * Tao ThanhToan loaiGiaoDich = "Hoan tien" (trangThai "Cho xu ly"),
     * cap nhat HoaDon.daHoanTra va set trangThaiHoanTien = "Cho xu ly".
     * Redirect ve trang chi tiet de render lai 3-case.
     */
    @PostMapping("/{id}/hoan-tien")
    public String hoanTien(@PathVariable Integer id,
                           @RequestParam(defaultValue = "Tien mat") String hinhThuc,
                           @RequestParam(required = false) String ghiChu,
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
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể hoàn tiền.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        if (hoaDon == null) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " chưa có hóa đơn, không thể ghi nhận hoàn tiền.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        Map<String, BigDecimal> folio = tinhFolio(dp);
        // Cong phu phi tra muon (cung cong thuc voi cac diem tinh soDu khac) de
        // khong tinh nham la "thua tien" trong khi thuc ra dang bu cho phu phi.
        BigDecimal phuPhiTraMuon = tinhPhuPhiTraMuon(dp, LocalDateTime.now());
        BigDecimal tongTien = folio.get("tongTien").add(phuPhiTraMuon);
        BigDecimal daThanhToan = hoaDon.getDaThanhToan() == null ? BigDecimal.ZERO : hoaDon.getDaThanhToan();
        BigDecimal daHoanTra = hoaDon.getDaHoanTra() == null ? BigDecimal.ZERO : hoaDon.getDaHoanTra();
        BigDecimal soDu = tongTien.subtract(daThanhToan).add(daHoanTra);
        BigDecimal canHoan = soDu.compareTo(BigDecimal.ZERO) < 0 ? soDu.negate() : BigDecimal.ZERO;

        if (canHoan.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "Khách không có khoản thừa cần hoàn.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        NhanSu nvHienTai = nhanVienHienTai(authentication);
        ThanhToan tt = new ThanhToan();
        tt.setH(hoaDon);
        tt.setPhuongThuc(hinhThuc);
        tt.setSoTien(canHoan);
        tt.setLoaiGiaoDich("Hoan tien");
        tt.setTrangThai("Cho xu ly");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setNv(nvHienTai);
        tt.setGichu(ghiChu != null && !ghiChu.isBlank() ? ghiChu : "Ghi nhận hoàn tiền khi trả phòng #" + id);
        thanhToanService.save(tt);

        hoaDon.setDaHoanTra(daHoanTra.add(canHoan));
        hoaDon.setTrangThaiHoanTien("Cho xu ly");
        hoaDon.setNgayYeuCauHoan(LocalDateTime.now());
        hoaDon.setNgayCapNhat(LocalDateTime.now());
        // Khong goi saveWithPaymentStatusCheck: trangThai hoa don van giu nguyen (Da thanh toan hoac Cho thanh toan),
        // vi ban than no da phan anh tong tien - daThanhToan, khong lien quan den daHoanTra.
        hoaDonService.save(hoaDon);

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_HOAN_TIEN,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_HOA_DON,
                hoaDon.getId(),
                "Ghi nhận hoàn " + canHoan.toPlainString() + " VND (" + hinhThuc + ") cho đơn #" + id);

        redirectAttributes.addFlashAttribute("success",
                "Đã ghi nhận hoàn " + canHoan.toPlainString() + " VND cho đơn #" + id +
                        ". Yêu cầu đang chờ xử lý.");
        return "redirect:/nhan-su/checkout/" + id;
    }

    // ================= PHASE 2: XAC NHAN TRA PHONG (CHOT DON) =================

    /**
     * Phase 2: chot tra phong - chi doi trang thai don + giai phong phong.
     * Fail-fast neu soDu != 0 (phai thu hoac hoan truoc khi chot).
     * Su dung hasBookingNotCheckout() de quyet dinh phong giai phong ve
     * "Trong" hay "Da dat truoc" neu co don khac da dat truoc cho phong do.
     */
    @PostMapping("/{id}/xac-nhan")
    public String xacNhanTraPhong(@PathVariable Integer id,
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
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể trả phòng.");
            return "redirect:/nhan-su/checkout/" + id;
        }

        Map<String, BigDecimal> folio = tinhFolio(dp);
        BigDecimal tienPhong = folio.get("tienPhong");
        BigDecimal tienDichVu = folio.get("tienDichVu");
        BigDecimal tienVat = folio.get("tienVat");
        BigDecimal tongTienGoc = folio.get("tongTien");

        // Lay danh sach phong (can cho tinh phu phi tra muon)
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);

        // ===== Phụ phí trả muộn (late checkout) =====
        // So sánh giờ trả thực tế (now) với giờ đã đặt trên booking → chỉ tính
        // khi khách trả SAU giờ đã đặt. Trả trước hoặc đúng giờ → 0.
        BigDecimal phuPhiTraMuon = BigDecimal.ZERO;
        LocalDateTime gioTraHienTai = LocalDateTime.now();
        for (ChiTietDatPhong ct : phongList) {
            if (ct != null && ct.getP() != null) {
                BigDecimal fee = phongService.calculateLateCheckoutFeeFor(
                        ct.getP().getMaPhong(),
                        dp.getNgaydatPhong(),
                        dp.getNgaytraPhong(),
                        gioTraHienTai);
                if (fee != null && fee.signum() > 0) {
                    phuPhiTraMuon = phuPhiTraMuon.add(fee);
                }
            }
        }

        // Tong tien cuoi cung = tien phong + dich vu + VAT + phu phi tra muon (neu co)
        BigDecimal tongTien = tongTienGoc.add(phuPhiTraMuon);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        BigDecimal daThanhToan = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal daHoanTra = (hoaDon != null && hoaDon.getDaHoanTra() != null)
                ? hoaDon.getDaHoanTra() : BigDecimal.ZERO;
        BigDecimal soDu = tongTien.subtract(daThanhToan).add(daHoanTra);

        // Fail-fast: phai can bang so du truoc khi chot
        if (soDu.compareTo(BigDecimal.ZERO) != 0) {
            String lyDo = phuPhiTraMuon.signum() > 0
                    ? " (bao gom phu phi tra muon " + phuPhiTraMuon.toPlainString() + " VND)"
                    : "";
            String message = soDu.compareTo(BigDecimal.ZERO) > 0
                    ? "Khách còn nợ " + soDu.toPlainString() + " VND" + lyDo
                            + ". Vui lòng thu tiền trước khi chốt trả phòng."
                    : "Khách đã trả thừa " + soDu.negate().toPlainString() + " VND"
                            + ". Vui lòng ghi nhận hoàn tiền trước khi chốt trả phòng.";
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/nhan-su/checkout/" + id;
        }

        // ===== Cap nhat hoa don neu co phu phi tra muon =====
        // Luu phu phi vao HoaDon.ghiChu de audit + cap nhat tongTien (neu chua tinh phu phi truoc do).
        if (hoaDon != null && phuPhiTraMuon.signum() > 0) {
            BigDecimal tongTienHienTai = hoaDon.getTongTien() == null ? BigDecimal.ZERO : hoaDon.getTongTien();
            // Chi cap nhat neu tongTien hien tai KHONG bao gom phu phi tra muon (so sanh)
            // De don gian, cu add phu phi vao tongTien neu tongTien < tongTienGoc + phuPhi
            BigDecimal tongTienMongDoi = tongTienGoc.add(phuPhiTraMuon);
            if (tongTienHienTai.compareTo(tongTienMongDoi) < 0) {
                hoaDon.setTongTien(tongTienMongDoi.setScale(2, java.math.RoundingMode.HALF_UP));
                String ghiChuCu = hoaDon.getGhiChu() == null ? "" : hoaDon.getGhiChu();
                String phuPhiText = "[Phu phi tra muon " + phuPhiTraMuon.toPlainString()
                        + " VND luc " + gioTraHienTai.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + "]";
                hoaDon.setGhiChu(ghiChuCu.isEmpty() ? phuPhiText : (ghiChuCu + " | " + phuPhiText));
                hoaDon.setNgayCapNhat(LocalDateTime.now());
                hoaDonService.saveWithPaymentStatusCheck(hoaDon);
            }
        }

        // Cap nhat trang thai don + giai phong phong (co the giu "Da dat truoc" neu co don khac)
        dp.setTrangThai("Da tra phong");
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        List<ChiTietDatPhong> phongListForRelease = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ct : phongListForRelease) {
            Phong p = ct.getP();
            if (p == null) continue;
            if (datPhongService.hasBookingNotCheckout(p.getMaPhong(), dp.getId())) {
                p.setTrangThai("Da dat truoc");
            } else {
                p.setTrangThai("Trong");
            }
            phongService.save1(p);
        }

        String hoaDonInfo = (hoaDon != null)
                ? " Hóa đơn #" + hoaDon.getId() + " - Tổng tiền: " + tongTien.toPlainString() + " VND."
                : "";
        String phuPhiInfo = phuPhiTraMuon.signum() > 0
                ? " Đã tính phụ phí trả muộn: " + phuPhiTraMuon.toPlainString() + " VND."
                : "";

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_OUT,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Trả phòng cho đơn #" + id + ". Tổng tiền: " + tongTien.toPlainString() + " VND."
                        + (phuPhiTraMuon.signum() > 0 ? (" Phụ phí trả muộn: " + phuPhiTraMuon.toPlainString() + " VND.") : ""));

        redirectAttributes.addFlashAttribute("success",
                "Trả phòng thành công cho đơn #" + id + "." + phuPhiInfo + hoaDonInfo);
        return "redirect:/nhan-su/checkout/" + id;
    }

    // ================= XUAT HOA DON PDF =================

    @GetMapping("/{id}/xuat-pdf")
    public void xuatPdf(@PathVariable Integer id, Authentication authentication,
                         HttpServletRequest request, HttpServletResponse response) throws Exception {

        if (!coQuyenCheckout(authentication)) {
            response.sendRedirect("/home");
            return;
        }

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        if (hoaDon == null) {
            response.sendRedirect("/nhan-su/checkout/" + id);
            return;
        }

        // Validate: chỉ cho xuất PDF khi đơn đã ở trạng thái "Da huy" hoặc "Da tra phong"
        String trangThaiDon = hoaDon.getD() != null ? hoaDon.getD().getTrangThai() : null;
        boolean hopLe = "Da huy".equals(trangThaiDon) || "Da tra phong".equals(trangThaiDon);
        if (!hopLe) {
            request.getSession().setAttribute("toastWarning",
                    "Đơn đặt phòng #" + id
                            + " đang trong quá trình sử dụng phòng. Vui lòng hoàn tất trả phòng hoặc xử lý hủy đơn trước khi xuất hóa đơn.");
            String referer = request.getHeader("Referer");
            String redirect = (referer != null && !referer.isBlank())
                    ? referer
                    : ("/nhan-su/checkout/" + id);
            response.sendRedirect(redirect);
            return;
        }

        // Tinh tong phu phi ngoai gio tu cac phong trong don
        BigDecimal tongPhuThu = BigDecimal.ZERO;
        List<ChiTietDatPhong> phongListForRelease = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ct : phongListForRelease) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThu = tongPhuThu.add(ct.getPhuPhi());
            }
        }

        // Tinh tong phu thu check-in som / check-out muon tu dich vu loaiDv = "Phu thu"
        // (phan biet voi tongPhuThu - day la phu phi phong). Day la dong tien rieng
        // se hien thi tren PDF hoa don de minh bach nghiep vu penalty.
        BigDecimal tongPhuThuCheckInSom = BigDecimal.ZERO;
        BigDecimal tongDichVuThuong = BigDecimal.ZERO;
        List<Chi_tiet_dich_vu> dichVuListForRelease = ctdvService.findByDatPhongId(id);
        for (Chi_tiet_dich_vu ctdv : dichVuListForRelease) {
            if (ctdv == null || ctdv.getDonGia() == null) continue;
            String loai = ctdv.getDv() != null ? ctdv.getDv().getLoaiDv() : null;
            if ("Phu thu".equalsIgnoreCase(loai)) {
                tongPhuThuCheckInSom = tongPhuThuCheckInSom.add(ctdv.getDonGia());
            } else {
                tongDichVuThuong = tongDichVuThuong.add(ctdv.getDonGia());
            }
        }

        // Lay lich su giao dich va tach rieng phan hoan tien
        java.util.List<ThanhToan> thanhToans = thanhToanRepo.findByH_IdOrderByNgaythanhToanAsc(hoaDon.getId());
        java.util.List<ThanhToan> hoanTienList = new java.util.ArrayList<>();
        for (ThanhToan t : thanhToans) {
            if (t != null && "Hoan tien".equalsIgnoreCase(t.getLoaiGiaoDich())) {
                hoanTienList.add(t);
            }
        }
        BigDecimal tongHoan = hoaDon.getDaHoanTra() != null ? hoaDon.getDaHoanTra() : BigDecimal.ZERO;

        Context context = new Context();
        context.setVariable("hoaDon", hoaDon);
        context.setVariable("tongPhuThu", tongPhuThu);
        context.setVariable("tongPhuThuCheckInSom", tongPhuThuCheckInSom);
        context.setVariable("tongDichVuThuong", tongDichVuThuong);
        context.setVariable("hoanTienList", hoanTienList);
        context.setVariable("tongHoan", tongHoan);

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
