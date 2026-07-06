package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/nhan-su")
public class NhanVienDatPhongController {

    @Autowired private PhongService phongService;
    @Autowired private DatPhongService datPhongService;
    @Autowired private ChiTietDatPhongService chiTietDatPhongService;
    @Autowired private DichVuService dichVuService;
    @Autowired private ChiTietDichVuService ctdvService;
    @Autowired private khuyenMaiService khuyenMaiService;
    @Autowired private HoaDonService hoaDonService;
    @Autowired private ThanhToanService thanhToanService;
    @Autowired private NguoiDungService nguoiDungService;
    @Autowired private NhanVienService nhanVienService;
    @Autowired private VnpayService vnpayService;

    @GetMapping("/dat-phong")
    public String getAllDatPhong(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<DatPhong> datPhongPage = datPhongService.findAll(pageable);
        List<DatPhong> datPhongs = datPhongPage.getContent();

        Map<Integer, List<ChiTietDatPhong>> mapCtdp = new HashMap<>();
        Map<Integer, List<Phong>> phongTheoDon = new HashMap<>();
        for (DatPhong dp : datPhongs) {
            mapCtdp.put(dp.getId(), chiTietDatPhongService.findByDatPhongId(dp.getId()));
            phongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
        }

        List<Integer> daDatHoaDon = hoaDonService.findAll()
                .stream()
                .filter(hd -> hd.getD() != null)
                .map(hd -> hd.getD().getId())
                .collect(Collectors.toList());

        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("MapCtdp", mapCtdp);
        model.addAttribute("phongTheoDon", phongTheoDon);
        model.addAttribute("daDatHoaDon", daDatHoaDon);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", datPhongPage.getTotalPages());
        model.addAttribute("totalItems", datPhongPage.getTotalElements());
        model.addAttribute("pageSize", size);

        return "nhan-vien/dat-phong-list";
    }

    @GetMapping("/dat-phong/chi-tiet/{id}")
    public String chiTietDatPhong(@PathVariable Integer id,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }

        model.addAttribute("hoaDon", hoaDonService.findByDatPhongId(id)); // <-- đã thêm chưa?

        model.addAttribute("datPhong", datPhong);
        model.addAttribute("chiTietDatPhongList", chiTietDatPhongService.findByDatPhongId(id));
        model.addAttribute("chiTietDichVuList", ctdvService.findByDatPhongId(id));
        model.addAttribute("dichVuList", dichVuService.findAll());
        model.addAttribute("kmJson", buildKhuyenMaiJson());

        return "nhan-vien/chi-tiet-dat-phong";
    }

    @PostMapping("/dat-phong/chi-tiet/{id}/update")
    public String updateChiTietDatPhong(@PathVariable Integer id,
                                        @RequestParam("ngayNhan")
                                        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime ngayNhan,
                                        @RequestParam("ngayTra")
                                        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime ngayTra,
                                        @RequestParam("nguoiLon") Integer nguoiLon,
                                        @RequestParam("treEm") Integer treEm,
                                        @RequestParam(value = "tongTienPhong", required = false) BigDecimal tongTienPhong,
                                        @RequestParam(value = "tongTienDichVu", required = false) BigDecimal tongTienDichVu,
                                        @RequestParam(value = "tongTienGiam", required = false) BigDecimal tongTienGiam,
                                        @RequestParam(value = "tongTienVat", required = false) BigDecimal tongTienVat,
                                        @RequestParam(value = "tongCong", required = false) BigDecimal tongCong,
                                        @RequestParam(value = "maKhuyenMai", required = false) Integer maKhuyenMai,
                                        @RequestParam(value = "dichVuIds", required = false) List<Integer> dichVuIds,
                                        @RequestParam Map<String, String> allParams,
                                        RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }
        List<String> loiCapNhat = validateChiTietDatPhong(ngayNhan, ngayTra, nguoiLon, treEm, dichVuIds, allParams);
        if (!loiCapNhat.isEmpty()) {
            redirectAttributes.addFlashAttribute("soLoi", loiCapNhat.size());
            redirectAttributes.addFlashAttribute("loiCapNhat", String.join(" ", loiCapNhat));
            return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }

        datPhong.setNgaydatPhong(ngayNhan);
        datPhong.setNgaytraPhong(ngayTra);
        datPhong.setSonguoiLon(nguoiLon);
        datPhong.setSotreEm(treEm);
        datPhong.setNgayCapNhat(LocalDateTime.now());
        KhuyenMai km = maKhuyenMai == null ? null : khuyenMaiService.findbyId(maKhuyenMai);
        datPhong.setKm(km);
        datPhongService.save(datPhong);

        capNhatGiaPhongTheoNgay(id, ngayNhan, ngayTra);
        capNhatDichVuDatPhong(datPhong, dichVuIds, allParams);
        capNhatHoaDonNeuCo(id, tongTienPhong, tongTienDichVu, tongTienGiam, tongTienVat, tongCong, km);

        redirectAttributes.addFlashAttribute("thanhCongCapNhat", "Cap nhat chi tiet dat phong #" + id + " thanh cong.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    private void capNhatGiaPhongTheoNgay(Integer maDatPhong, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        long soDem = Math.max(1, ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate()));
        for (ChiTietDatPhong chiTiet : chiTietDatPhongService.findByDatPhongId(maDatPhong)) {
            BigDecimal giaMoiDem = chiTiet.getGiaMoiDem() != null ? chiTiet.getGiaMoiDem() : BigDecimal.ZERO;
            chiTiet.setGiaKhiDat(giaMoiDem.multiply(BigDecimal.valueOf(soDem)));
            chiTietDatPhongService.save(chiTiet);
        }
    }

    private List<String> validateChiTietDatPhong(LocalDateTime ngayNhan, LocalDateTime ngayTra,
                                                 Integer nguoiLon, Integer treEm,
                                                 List<Integer> dichVuIds, Map<String, String> allParams) {
        List<String> errors = new ArrayList<>();
        if (ngayNhan == null || ngayTra == null || !ngayTra.isAfter(ngayNhan)) {
            errors.add("Ngay tra phong phai sau ngay nhan phong.");
        }
        if (nguoiLon == null || nguoiLon < 1) {
            errors.add("So nguoi lon phai lon hon hoac bang 1.");
        }
        if (treEm == null || treEm < 0) {
            errors.add("So tre em khong duoc am.");
        }
        if (ngayNhan != null && ngayTra != null && dichVuIds != null) {
            for (Integer maDichVu : dichVuIds) {
                String ngaySuDungStr = allParams.get("ngaySuDung_" + maDichVu);
                if (ngaySuDungStr == null || ngaySuDungStr.isBlank()) {
                    continue;
                }
                LocalDateTime ngaySuDung = LocalDateTime.parse(ngaySuDungStr);
                if (ngaySuDung.isBefore(ngayNhan) || ngaySuDung.isAfter(ngayTra)) {
                    errors.add("Ngay su dung dich vu phai nam trong khoang luu tru.");
                    break;
                }
            }
        }
        return errors;
    }

    private void capNhatDichVuDatPhong(DatPhong datPhong, List<Integer> dichVuIds, Map<String, String> allParams) {
        ctdvService.deleteByDatPhongId(datPhong.getId());
        if (dichVuIds == null) {
            return;
        }

        for (Integer maDichVu : dichVuIds) {
            Dich_vu dichVu = dichVuService.findById(maDichVu);
            if (dichVu == null) {
                continue;
            }

            int soLuong = parseIntOrDefault(allParams.get("soLuong_" + maDichVu), 1);
            LocalDateTime ngaySuDung = parseDateTimeOrNow(allParams.get("ngaySuDung_" + maDichVu));

            Chi_tiet_dich_vu chiTiet = new Chi_tiet_dich_vu();
            chiTiet.setDatPhong(datPhong);
            chiTiet.setDv(dichVu);
            chiTiet.setSoluong(soLuong);
            chiTiet.setNgay_su_dung(ngaySuDung);
            chiTiet.setDonGia(dichVu.getGia().multiply(BigDecimal.valueOf(soLuong)));
            ctdvService.save(chiTiet);
        }
    }

    private void capNhatHoaDonNeuCo(Integer maDatPhong, BigDecimal tienPhong, BigDecimal tienDichVu,
                                    BigDecimal tienGiam, BigDecimal tienVat, BigDecimal tongCong, KhuyenMai km) {
        HoaDon hoaDon = hoaDonService.findByDatPhongId(maDatPhong);
        if (hoaDon == null) {
            return; // chưa có hóa đơn thì chưa cần làm gì, hóa đơn sẽ được tạo ở bước thanh toán lần đầu
        }

        hoaDon.setK(km);
        hoaDon.setTienPhong(defaultMoney(tienPhong));
        hoaDon.setTienDichVu(defaultMoney(tienDichVu));
        hoaDon.setTienGiam(defaultMoney(tienGiam));
        hoaDon.setTienVat(defaultMoney(tienVat));
        hoaDon.setTongTien(defaultMoney(tongCong));
        // thông qua endpoint /thu-tien (thanh toán thật, tiền mặt hoặc VNPay).
        hoaDon.setNgayCapNhat(LocalDateTime.now());
        hoaDonService.save(hoaDon);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return (value == null || value.isBlank()) ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private LocalDateTime parseDateTimeOrNow(String value) {
        return (value == null || value.isBlank()) ? LocalDateTime.now() : LocalDateTime.parse(value);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildKhuyenMaiJson() {
        List<KhuyenMai> kmList = khuyenMaiService.findAllActive().collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < kmList.size(); i++) {
            KhuyenMai km = kmList.get(i);
            if (i > 0) {
                sb.append(",");
            }
            BigDecimal dieuKien = km.getGiaToiThieuDuocGiam() == null ? BigDecimal.ZERO : km.getGiaToiThieuDuocGiam();
            sb.append("{")
                    .append("\"id\":").append(km.getId()).append(",")
                    .append("\"code\":\"").append(escapeJson(km.getPromoCode())).append("\",")
                    .append("\"loaiGiam\":\"").append(escapeJson(km.getLoaiGiam())).append("\",")
                    .append("\"giatriGiam\":").append(km.getGiatriGiam() == null ? "0" : km.getGiatriGiam().toPlainString()).append(",")
                    .append("\"dieuKien\":").append(dieuKien.toPlainString())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    @GetMapping("/dat-phong/search")
    public String searchDatPhong(
            @RequestParam(required = false) Integer maDatPhong,
            @RequestParam(required = false) String tenKhach,
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
                maDatPhong, tenKhach, null, ma_cccd,
                ngayNhanTu, ngayNhanDen, ngayTraTu, ngayTraDen,
                soNguoiLon, soTreEm, trangThai, yeuCauThem,
                ngayTaoTu, ngayTaoDen, ngayCapNhatTu, ngayCapNhatDen
        );

        Map<Integer, List<ChiTietDatPhong>> mapCtdp = new HashMap<>();
        Map<Integer, List<Phong>> phongTheoDon = new HashMap<>();
        for (DatPhong dp : datPhongs) {
            mapCtdp.put(dp.getId(), chiTietDatPhongService.findByDatPhongId(dp.getId()));
            phongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
        }

        List<Integer> daDatHoaDon = hoaDonService.findAll()
                .stream()
                .filter(hd -> hd.getD() != null)
                .map(hd -> hd.getD().getId())
                .collect(Collectors.toList());

        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("MapCtdp", mapCtdp);
        model.addAttribute("phongTheoDon", phongTheoDon);
        model.addAttribute("daDatHoaDon", daDatHoaDon);
        model.addAttribute("maDatPhong", maDatPhong);
        model.addAttribute("tenKhach", tenKhach);
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

        return "nhan-vien/dat-phong-list";
    }

    @PostMapping("/dat-phong/update-trang-thai")
    public String updateTrangThai(
            @RequestParam Integer id,
            @RequestParam String trangThai,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
        }

        if (dp.getMa_cccd() == null || dp.getMa_cccd().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Đơn đặt phòng chưa có CCCD, không thể xác nhận.");
            return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
        }

        dp.setTrangThai(trangThai);
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        if ("Da tra phong".equals(trangThai)) {
            List<ChiTietDatPhong> ctdpList = chiTietDatPhongService.findByDatPhongId(id);
            for (ChiTietDatPhong ct : ctdpList) {
                Phong p = ct.getP();
                p.setTrangThai("Trong");
                phongService.save1(p);
            }
        }

        redirectAttributes.addFlashAttribute("success", "Cập nhật trạng thái đơn #" + id + " thành công.");
        return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
    }

    @PostMapping("/dat-phong/cancel")
    public String cancelDatPhong(
            @RequestParam("id") Integer id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
        }

        if (!"Chua thanh toan".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Chi co the huy don khi dang o trang thai Chua thanh toan");
            return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
        }

        dp.setTrangThai("Da huy");
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        redirectAttributes.addFlashAttribute("success", "Da huy don dat phong #" + id);
        return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
    }

    @GetMapping("/dat-phong-quay")
    public String NvDatPhongQuay(Model model, Authentication authentication){
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {

            NhanSu nv = nhanVienService.FindByemail(authentication.getName());
            if (!nhanVienService.laLeTanDangHoatDong(nv)) {
                return "redirect:/home"; //TODO: THEM URL DASHBOARD VAO DAY
            }
        }

        List<KhuyenMai> kmList = khuyenMaiService.findAllActive().collect(Collectors.toList());
        model.addAttribute("phongTrongList", phongService.findByTrangThai("Trong"));
        model.addAttribute("dichVuList", dichVuService.findAll());
        model.addAttribute("khuyenMaiList", kmList);

        // Tự build JSON để tránh phụ thuộc Jackson 3 (List<Map> đôi khi serialize rỗng không rõ nguyên nhân)
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < kmList.size(); i++) {
            KhuyenMai km = kmList.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"id\":").append(km.getId()).append(",")
              .append("\"code\":\"").append(escapeJson(km.getPromoCode())).append("\",")
              .append("\"loaiGiam\":\"").append(escapeJson(km.getLoaiGiam())).append("\",")
              .append("\"giatriGiam\":").append(km.getGiatriGiam() == null ? "0" : km.getGiatriGiam().toPlainString())
              .append("}");
        }
        sb.append("]");
        model.addAttribute("kmJson", sb.toString());

        return "nhan-vien/dat-phong-quay";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    @PostMapping("/dat-phong-quay/submit")
    public String submit(@RequestParam(required = false) String hoten,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String sdt,
                         @RequestParam("ma_cccd") String maCccd,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngaydatPhong,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngaytraPhong,
                         @RequestParam Integer songuoiLon,
                         @RequestParam Integer sotreEm,
                         @RequestParam(required = false) String yeuCauThem,
                         @RequestParam(required = false) Integer maKhuyenMai,
                         @RequestParam(value = "maPhongList", required = false) List<Integer> maPhongList,
                         @RequestParam(value = "dichVuIds", required = false) List<Integer> dichVuIds,
                         @RequestParam Map<String, String> allParams,
                         Model model,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        int soLoi = 0;
        NhanSu nvCheck = authentication == null ? null : nhanVienService.FindByemail(authentication.getName());
        NhanSu nhanVienXuLy = nhanVienService.laLeTanDangHoatDong(nvCheck)
                ? nvCheck
                : nhanVienService.findLeTanDangHoatDongMacDinh();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (nhanVienXuLy == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay nhan vien Le Tan dang hoat dong");
            return "redirect:/nhan-su/dat-phong-quay";
        }

        if (!isAdmin && !nhanVienService.laLeTanDangHoatDong(nvCheck)) {
            System.out.println("khong khop bo phan");
            return "redirect:/home"; //TODO: THEM URL DASHBOARD VAO DAY
        }

        if (maCccd == null || maCccd.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "CCCD khong duoc de trong");
            return "redirect:/nhan-su/dat-phong-quay";
        }
        if (maPhongList == null || maPhongList.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui long chon it nhat 1 phong");
            return "redirect:/nhan-su/dat-phong-quay";
        }
        int TongNguoi = songuoiLon + sotreEm;
        int sucChua = 0;
        for(Integer i : maPhongList){
            sucChua +=phongService.findPhongById(i).getLoaiPhong().sucChuaToiDa;
        }
        if(TongNguoi > sucChua){
            redirectAttributes.addFlashAttribute("error",  "Số lượng người vượt quá Sức Chứa phòng");
            return "redirect:/nhan-su/dat-phong-quay";
        }
        System.out.println("Tong Nguoi: "+TongNguoi + "Suc Chua: "+sucChua);
        DatPhong dp = new DatPhong();
        dp.setHoten(hoten);
        dp.setEmail(email);
        dp.setSdt(sdt);
        dp.setMa_cccd(maCccd);
        dp.setNgaydatPhong(ngaydatPhong.atStartOfDay());
        dp.setNgaytraPhong(ngaytraPhong.atTime(12, 0));
        dp.setSonguoiLon(songuoiLon);
        dp.setSotreEm(sotreEm);
        dp.setYeuCauThem(yeuCauThem);
        dp.setTrangThai("Da nhan phong");
        dp.setNgayTao(LocalDateTime.now());


        if (maKhuyenMai != null) {
            KhuyenMai km = khuyenMaiService.findbyId(maKhuyenMai);
            dp.setKm(km);
        }

        dp.setNv(nhanVienXuLy);

        DatPhong savedDp = datPhongService.save(dp);

        BigDecimal amountPhong = BigDecimal.ZERO;

        for (Integer maPhong : maPhongList) {
            Phong phong = phongService.findById(maPhong);
            if (phong == null || !"Trong".equals(phong.getTrangThai())) {
                continue;
            }
            Map<Integer , String> cccdPhong = allParams.entrySet()
                    .stream().filter(cccdP -> cccdP.getKey().startsWith("cccdPhong_")).
                    collect(Collectors.toMap(e -> Integer.parseInt(e.getKey().substring("cccdPhong_".length())),
                            Map.Entry::getValue));

            ChiTietDatPhong ctdp = new ChiTietDatPhong();
            ctdp.setD(savedDp);
            ctdp.setP(phong);
            ctdp.setMa_cccd(cccdPhong.get(phong.getMaPhong()));
            ctdp.setGiaMoiDem(phong.getGiaMoiDem());

            BigDecimal giaApDung = phong.getGiaMoiDem();

            if (maKhuyenMai != null) {
                KhuyenMai kmDon = khuyenMaiService.findbyId(maKhuyenMai);
                if (kmDon != null) {
                    giaApDung = tinhGiaSauGiam(giaApDung, kmDon);
                }
            }

            ctdp.setGiaKhiDat(giaApDung);
            chiTietDatPhongService.save(ctdp);

            amountPhong = amountPhong.add(giaApDung);

            phong.setTrangThai("Dang su dung");
            phongService.save1(phong);
        }

        BigDecimal amountDv = BigDecimal.ZERO;

        if (dichVuIds != null) {
            for (Integer maDichVu : dichVuIds) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;

                String slStr = allParams.get("soLuong_" + maDichVu);
                int sl = (slStr != null && !slStr.isBlank()) ? Integer.parseInt(slStr) : 1;

                BigDecimal thanhTien = dv.getGia().multiply(BigDecimal.valueOf(sl));

                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setDatPhong(savedDp);
                ct.setDv(dv);
                ct.setSoluong(sl);
                ct.setDonGia(thanhTien);
                ct.setNgay_su_dung(LocalDateTime.now());
                ctdvService.save(ct);

                amountDv = amountDv.add(thanhTien);
            }
        }

        BigDecimal VATCD = new BigDecimal("0.10");
        BigDecimal tongTienTruocVat = amountPhong.add(amountDv);
        BigDecimal tienVat = tongTienTruocVat.multiply(VATCD).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tongCong = tongTienTruocVat.add(tienVat);

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(savedDp);
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(BigDecimal.ZERO);
        hd.setTienVat(tienVat);
        hd.setTongTien(tongCong);
        hd.setDaThanhToan(tongCong);
        hd.setGhiChu("Dat phong va thanh toan tien mat tai quay ma don: " + savedDp.getId());
        hoaDonService.save(hd);

        ThanhToan tt = new ThanhToan();
        tt.setH(hd);
        tt.setPhuongThuc("Tien Mat");
        tt.setSoTien(tongCong);
        tt.setTrangThai("Thanh cong");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setGichu("Thu tien mat tai quay da nhan du 100%");

        tt.setNv(nhanVienXuLy);
        thanhToanService.save(tt);
        redirectAttributes.addFlashAttribute("success",
                "Tao don thanh cong, ma don: " + savedDp.getId() + ", tong tien da thu: " + tongCong + " VND");
        return "redirect:/nhan-su/dat-phong";
    }

    private BigDecimal tinhGiaSauGiam(BigDecimal giaGoc, KhuyenMai km) {
        if ("PERCENT".equals(km.getLoaiGiam())) {
            BigDecimal phanTramGiam = km.getGiatriGiam().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal heSoConLai = BigDecimal.ONE.subtract(phanTramGiam);
            return giaGoc.multiply(heSoConLai);
        } else if ("AMOUNT".equals(km.getLoaiGiam()) || "FIXED".equals(km.getLoaiGiam())) {
            BigDecimal giaSauGiam = giaGoc.subtract(km.getGiatriGiam());
            return giaSauGiam.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : giaSauGiam;
        }
        return giaGoc;
    }
    @PostMapping("/dat-phong/chi-tiet/{id}/khach-hang")
    public String capNhatKhachHang(@PathVariable Integer id,
                                   @RequestParam(required = false) String hoten,
                                   @RequestParam(required = false) String email,
                                   @RequestParam(required = false) String sdt,
                                   @RequestParam(required = false) String maCccd,
                                   RedirectAttributes redirectAttributes) {
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }

        dp.setMa_cccd(maCccd);

        KhachHang n = dp.getN();
        if (n == null) {
            dp.setHoten(hoten);
            dp.setEmail(email);
            dp.setSdt(sdt);
        } else {
            n.setHoTen(hoten);
            n.setEmail(email);
            n.setSoDienThoai(sdt);
            nguoiDungService.save(n);
        }

        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        redirectAttributes.addFlashAttribute("thanhCongCapNhat", "Cap nhat thong tin khach hang thanh cong.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }
    @PostMapping("/dat-phong/chi-tiet/{id}/thu-tien")
    public String thuTien(@PathVariable Integer id, @RequestParam BigDecimal soTien,
                          @RequestParam("phuongThuc") String phuongthuc, HttpServletRequest request, RedirectAttributes redirectAttributes){
        HoaDon hd = hoaDonService.findByDatPhongId(id);
        DatPhong dp = datPhongService.findById(id);
        if(hd == null&&dp==null){
            redirectAttributes.addFlashAttribute("error","don dat phong chua co hd");
            return "redirect:/nhan-su/dat-phong/"+id;
        }

        BigDecimal daThanhToan = hd.getDaThanhToan() ==null ? BigDecimal.ZERO : hd.getDaThanhToan();
        BigDecimal conNo = hd.getTongTien().subtract(daThanhToan);
        if(soTien.compareTo(conNo) > 0){
            redirectAttributes.addFlashAttribute("error","Số tiền vượt quá số tiền còn thiếu");
            return "redirect:/nhan-su/dat-phong/"+id;
        }
        if("Chuyen Khoan".equalsIgnoreCase(phuongthuc)){
            String baseUrl = request.getScheme() + "://"+request.getServerName() + ":"+request.getServerPort();
            String vnPayUrl = vnpayService.createOrder(soTien.intValue(),id,"ThuThemDichVu",baseUrl);
            return "redirect:"+vnPayUrl;
        }
        ThanhToan tt = new ThanhToan();
        tt.setH(hd);
        tt.setPhuongThuc("Tien Mat");
        tt.setSoTien(soTien);
        tt.setTrangThai("Thanh cong");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setGichu("Thu tien mat dich vu phat sinh, ma don: " + id);
        thanhToanService.save(tt);

        hd.setDaThanhToan(daThanhToan.add(soTien));
        hd.setNgayCapNhat(LocalDateTime.now());
        hoaDonService.save(hd);

        redirectAttributes.addFlashAttribute("success", "Đã thu " + soTien + " VND tiền mặt.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;

    }
}
