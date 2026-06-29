package su26sd09.su26sd09.controller;


import com.lowagie.text.pdf.BaseFont;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.repository.HoaDonRepo;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/hoa-don")
public class adminHoaDonController {

    @Autowired
    HoaDonService hoaDonService;

    @Autowired
    ChiTietDichVuService chiTietDichVuService;

    @Autowired
    DatPhongService datPhongService;

    @Autowired
    ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    NguoiDungService nguoiDungService;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private khuyenMaiService khuyenMaiService;

    @GetMapping("")
    public String getHoaDon(
            @RequestParam(required = false) Integer maHoaDon,
            @RequestParam(required = false) Integer maDatPhong,
            @RequestParam(required = false) String tenKhach,
            @RequestParam(required = false) String maKhuyenMai,
            @RequestParam(required = false) String ngayXuatTu,
            @RequestParam(required = false) String ngayXuatDen,
            @RequestParam(required = false) BigDecimal tongTienTu,
            @RequestParam(required = false) BigDecimal tongTienDen,
            @RequestParam(required = false) String trangThaiThanhToan,
            Model model) {

        List<HoaDon> hoaDons = hoaDonService.findAll().stream()
                .filter(hd -> {
                    if (maHoaDon != null && hd.getId() != maHoaDon) return false;

                    if (maDatPhong != null &&
                            (hd.getD() == null || hd.getD().getId() != maDatPhong))
                        return false;

                    if (tenKhach != null && !tenKhach.isEmpty() &&
                            (hd.getD() == null ||
                                    hd.getD().getN() == null ||
                                    !hd.getD().getN().getHoTen()
                                            .toLowerCase()
                                            .contains(tenKhach.toLowerCase())))
                        return false;

                    if (maKhuyenMai != null && !maKhuyenMai.isEmpty() &&
                            (hd.getK() == null ||
                                    !hd.getK().getPromoCode()
                                            .toLowerCase()
                                            .contains(maKhuyenMai.toLowerCase())))
                        return false;

                    if (tongTienTu != null &&
                            hd.getTongTien().compareTo(tongTienTu) < 0)
                        return false;
                    if (tongTienDen != null &&
                            hd.getTongTien().compareTo(tongTienDen) > 0)
                        return false;

                    if (ngayXuatTu != null && !ngayXuatTu.isEmpty()) {
                        LocalDateTime tu =
                                LocalDate.parse(ngayXuatTu).atStartOfDay();

                        if (hd.getNgayXuat().isBefore(tu))
                            return false;
                    }

                    if (ngayXuatDen != null && !ngayXuatDen.isEmpty()) {
                        LocalDateTime den =
                                LocalDate.parse(ngayXuatDen)
                                        .atTime(23, 59, 59);
                        if (hd.getNgayXuat().isAfter(den))
                            return false;
                    }
                    if (trangThaiThanhToan != null &&
                            !trangThaiThanhToan.isEmpty()) {
                        BigDecimal conNo =
                                hd.getTongTien()
                                        .subtract(hd.getDaThanhToan());
                        if (trangThaiThanhToan.equals("chua") &&
                                hd.getDaThanhToan()
                                        .compareTo(BigDecimal.ZERO) != 0)
                            return false;
                        if (trangThaiThanhToan.equals("mot_phan") &&
                                conNo.compareTo(BigDecimal.ZERO) <= 0)
                            return false;
                        if (trangThaiThanhToan.equals("du") &&
                                conNo.compareTo(BigDecimal.ZERO) != 0)
                            return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        Map<Integer, List<Chi_tiet_dich_vu>> dvMap = new HashMap<>();

        for (HoaDon hd : hoaDons) {
            if (hd.getD() != null) {
                dvMap.put(
                        hd.getId(),
                        chiTietDichVuService.findByDatPhongId(
                                hd.getD().getId()
                        )
                );
            }
        }

        model.addAttribute("dvMap", dvMap);

        model.addAttribute("hoaDons", hoaDons);
        model.addAttribute("hoaDon", new HoaDon());
        model.addAttribute("datPhongs", datPhongService.findAll());

        model.addAttribute("nguoiDungs", nguoiDungService.findAll());

        model.addAttribute("maHoaDon", maHoaDon);
        model.addAttribute("maDatPhong", maDatPhong);
        model.addAttribute("tenKhach", tenKhach);
        model.addAttribute("maKhuyenMai", maKhuyenMai);
        model.addAttribute("ngayXuatTu", ngayXuatTu);
        model.addAttribute("ngayXuatDen", ngayXuatDen);
        model.addAttribute("tongTienTu", tongTienTu);
        model.addAttribute("tongTienDen", tongTienDen);
        model.addAttribute("trangThaiThanhToan", trangThaiThanhToan);

        model.addAttribute("title", "Thêm hóa đơn");

        return "admin/hoa-don-list";
    }
    @GetMapping("/create-from-dat-phong")
    public String createFromDatPhong(@RequestParam Integer maDatPhong,RedirectAttributes redirectAttributes, Model model) {
        DatPhong datPhong = datPhongService.findById(maDatPhong);

        HoaDon hoaDonExist = hoaDonService.findByDatPhongId(maDatPhong);
        List<Chi_tiet_dich_vu> chiTietDichVus = chiTietDichVuService.findByDatPhongId(maDatPhong);
        if (hoaDonExist != null) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + maDatPhong + " đã có hóa đơn rồi!");
            return "redirect:/admin/dat-phong";
        }
        BigDecimal amountDv = BigDecimal.ZERO;
        if(chiTietDichVus.size() !=0) {
            for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
                amountDv = amountDv.add(ctdv.getDonGia());
            }
        }
        BigDecimal tienPhong = chiTietDatPhongService.findByDatPhongId(maDatPhong)
                .stream()
                .map(ChiTietDatPhong::getGiaKhiDat)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal tienVat = tienPhong.multiply(BigDecimal.valueOf(0.1)).
                setScale(0,RoundingMode.HALF_UP);
        BigDecimal TongTien = tienPhong.add(tienVat).setScale(0,RoundingMode.HALF_UP);
        TongTien = TongTien.add(amountDv);
        HoaDon hoaDon = new HoaDon();
        hoaDon.setD(datPhong);
        hoaDon.setTienPhong(tienPhong);
        hoaDon.setTienDichVu(amountDv);
        hoaDon.setTienGiam(BigDecimal.ZERO);
        hoaDon.setTienVat(tienVat);
        hoaDon.setTongTien(TongTien);
        hoaDon.setDaThanhToan(TongTien);



        model.addAttribute("hoaDons",hoaDonService.findAll());
        model.addAttribute("nguoiDungs",nguoiDungService.findWhereRoleNV());
        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("datPhongs", datPhongService.findAll());
        model.addAttribute("title", "Tạo hóa đơn từ đơn #" + maDatPhong);

        return "admin/hoa-don-list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute HoaDon hoaDon,
                       @RequestParam(required = false) Integer maDatPhong,
                       @RequestParam(required = false) Integer maKhuyenMai,
                       @RequestParam(required = false) Integer maNhanVienXuat,
                       RedirectAttributes redirectAttributes) {

        if (maDatPhong != null) {
            DatPhong datPhong = datPhongService.findById(maDatPhong);
            hoaDon.setD(datPhong);
        }
        if (maKhuyenMai != null) {
            hoaDon.setK(khuyenMaiService.findbyId(maKhuyenMai));
        }

        if (hoaDon.getId() == 0) {
            hoaDon.setNgayXuat(LocalDateTime.now());
        } else {
            hoaDon.setNgayCapNhat(LocalDateTime.now());
        }

        if (maNhanVienXuat != null) {
            hoaDon.setN(nguoiDungService.findById(maNhanVienXuat));
            System.out.println("Ma nhan vien xuat :"+maNhanVienXuat);
        }
        hoaDonService.save(hoaDon);
        redirectAttributes.addFlashAttribute("success", "Lưu hóa đơn thành công!");
        return "redirect:/admin/hoa-don";
    }

    @GetMapping("/edit/{id}")
    public String editHoaDon(@PathVariable Integer id,
                             @RequestParam(required = false) String keyword,
                             Model model) {
        HoaDon hoaDon = hoaDonService.findById(id);
        if (hoaDon == null) {
            return "redirect:/admin/hoa-don";
        }

        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("hoaDons", hoaDonService.findAll());
        model.addAttribute("datPhongs", datPhongService.findAll());
        model.addAttribute("nguoiDungs", nguoiDungService.findWhereRoleNV());
        model.addAttribute("keyword", keyword);
        model.addAttribute("title", "Sửa hóa đơn #" + id);

        return "admin/hoa-don-list";
    }

    @GetMapping("/search")
    public String searchDatPhong(
            @RequestParam(required = false) Integer maDatPhong,
            @RequestParam(required = false) String tenKhach,
            @RequestParam(required = false) Integer maNhanVien,
            @RequestParam(required = false) String ma_cccd,
            @RequestParam(required = false) String ngayNhanTu,
            @RequestParam(required = false) String ngayNhanDen,
            @RequestParam(required = false) String ngayTraTu,
            @RequestParam(required = false) String ngayTraDen,
            @RequestParam(required = false) Integer soNguoiLon,
            @RequestParam(required = false) Integer soTreEm,
            @RequestParam(required = false) String trangThai,
            @RequestParam(required = false) String yeuCauThem,
            @RequestParam(required = false) String ngayTaoTu,
            @RequestParam(required = false) String ngayTaoDen,
            @RequestParam(required = false) String ngayCapNhatTu,
            @RequestParam(required = false) String ngayCapNhatDen,
            Model model) {

        List<DatPhong> datPhongs = datPhongService.search(
                maDatPhong, tenKhach, maNhanVien, ma_cccd,
                ngayNhanTu, ngayNhanDen, ngayTraTu, ngayTraDen,
                soNguoiLon, soTreEm, trangThai, yeuCauThem,
                ngayTaoTu, ngayTaoDen, ngayCapNhatTu, ngayCapNhatDen
        );

        Map<Integer, List<Phong>> phongTheoDon = new HashMap<>();
        for (DatPhong dp : datPhongs) {
            phongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
        }

        List<Integer> daDatHoaDon = hoaDonService.findAll()
                .stream()
                .map(hd -> hd.getD().getId())
                .collect(Collectors.toList());

        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("phongTheoDon", phongTheoDon);
        model.addAttribute("daDatHoaDon", daDatHoaDon);

        model.addAttribute("maDatPhong", maDatPhong);
        model.addAttribute("tenKhach", tenKhach);
        model.addAttribute("maNhanVien", maNhanVien);
        model.addAttribute("ma_cccd", ma_cccd);
        model.addAttribute("ngayNhanTu", ngayNhanTu);
        model.addAttribute("ngayNhanDen", ngayNhanDen);
        model.addAttribute("ngayTraTu", ngayTraTu);
        model.addAttribute("ngayTraDen", ngayTraDen);
        model.addAttribute("soNguoiLon", soNguoiLon);
        model.addAttribute("soTreEm", soTreEm);
        model.addAttribute("trangThai", trangThai);
        model.addAttribute("yeuCauThem", yeuCauThem);
        model.addAttribute("ngayTaoTu", ngayTaoTu);
        model.addAttribute("ngayTaoDen", ngayTaoDen);
        model.addAttribute("ngayCapNhatTu", ngayCapNhatTu);
        model.addAttribute("ngayCapNhatDen", ngayCapNhatDen);

        return "admin/dat-phong-list";
    }
    @GetMapping("/xuat-pdf/{id}")
    public void xuatPdf(@PathVariable int id, HttpServletResponse response) throws Exception {

        HoaDon hoaDon = hoaDonService.findById(id);

        Context context = new Context();
        context.setVariable("hoaDon", hoaDon);

        String html = templateEngine.process("admin/hoa-don-pdf", context);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=hoa-don-" + id + ".pdf");

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
