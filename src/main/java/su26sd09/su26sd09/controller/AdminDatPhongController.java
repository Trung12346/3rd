package su26sd09.su26sd09.controller;


import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/nhan-su/admin/dat-phong")
public class AdminDatPhongController {

    @Autowired
    NguoiDungService nguoiDungService;

    @Autowired
    DatPhongService datPhongService;

    @Autowired
    ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    HoaDonService hoaDonService;

    @Autowired
    ChiTietDichVuService chiTietDichVuService;

    @Autowired
    DichVuService dichVuService;

    @Autowired
    PhongService phongService;

    @Autowired
    VnpayService vnpayService;

    @Autowired
    ThanhToanService thanhToanService;

    @Autowired
    khuyenMaiService khuyenMaiService;

    @GetMapping("")
    public String GetDatPhong(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(value = "edit", required = false) Integer editId,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<DatPhong> datPhongPage = datPhongService.findAll(pageable);
        List<DatPhong> datPhongs = datPhongPage.getContent();
        Map<Integer,List<ChiTietDatPhong>> Mapctdp = new HashMap<>();
        for(DatPhong dp : datPhongs){
                Mapctdp.put(dp.getId(),chiTietDatPhongService.findByDatPhongId(dp.getId()));

        }

        List<Integer> daDatHoaDon = hoaDonService.findAll()
                .stream()
                .filter(hd -> hd.getD() != null)
                .map(hd -> hd.getD().getId())
                .collect(Collectors.toList());
        model.addAttribute("daDatHoaDon", daDatHoaDon);

        Map<Integer, List<Phong>> PhongTheoDon = new HashMap<>();
        for (DatPhong dp : datPhongs) {
            PhongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
        }
        model.addAttribute("MapCtdp",Mapctdp);
        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("phongTheoDon", PhongTheoDon);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", datPhongPage.getTotalPages());
        model.addAttribute("totalItems", datPhongPage.getTotalElements());
        model.addAttribute("pageSize", size);

        if (editId != null) {
            model.addAttribute("dpEdit", datPhongService.findById(editId));
        }

        return "admin/dat-phong-list";
    }

    @GetMapping("/chi-tiet/{id}")
    public String chiTietDatPhong(@PathVariable Integer id,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/admin/dat-phong";
        }

        List<ChiTietDatPhong> chiTietDatPhongList = chiTietDatPhongService.findByDatPhongId(id);

        List<Chi_tiet_dich_vu> chiTietDichVuList = chiTietDichVuService.findByDatPhongId(id);

        model.addAttribute("hoaDon", hoaDonService.findByDatPhongId(id)); // <-- đã thêm chưa?

        model.addAttribute("datPhong", datPhong);
        model.addAttribute("chiTietDatPhongList", chiTietDatPhongList);
        model.addAttribute("chiTietDichVuList", chiTietDichVuList);
        model.addAttribute("dichVuList", dichVuService.findAll());
        model.addAttribute("kmJson", buildKhuyenMaiJson());

        return "admin/chi-tiet-dat-phong";
    }

    @PostMapping("/chi-tiet/{id}/update")
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
            return "redirect:/nhan-su/admin/dat-phong";
        }
        List<String> loiCapNhat = validateChiTietDatPhong(ngayNhan, ngayTra, nguoiLon, treEm, dichVuIds, allParams);
        if (!loiCapNhat.isEmpty()) {
            redirectAttributes.addFlashAttribute("soLoi", loiCapNhat.size());
            redirectAttributes.addFlashAttribute("loiCapNhat", String.join(" ", loiCapNhat));
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
        return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
        chiTietDichVuService.deleteByDatPhongId(datPhong.getId());
        if (dichVuIds == null) {
            return;
        }

        for (Integer maDichVu : dichVuIds) {
            var dichVu = dichVuService.findById(maDichVu);
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
            chiTietDichVuService.save(chiTiet);
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

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @PostMapping("/cancel")
    public String cancel(
            @RequestParam("id") Integer id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong?page=" + page + "&size=" + size;
        }

        if (!"Chua thanh toan".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error",
                    "Chỉ có thể hủy đơn khi đang ở trạng thái Chưa thanh toán");
            return "redirect:/nhan-su/admin/dat-phong?page=" + page + "&size=" + size;
        }

        dp.setTrangThai("Da huy");
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        redirectAttributes.addFlashAttribute("success", "Đã hủy đơn đặt phòng #" + id);
        return "redirect:/nhan-su/admin/dat-phong?page=" + page + "&size=" + size;
    }

    @GetMapping("/search")
    public String getSearchDatPhong(
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

        List<Integer> daDatHoaDon = hoaDonService.findAll()
                .stream()
                .filter(hd -> hd.getD() != null)  
                .map(hd -> hd.getD().getId())
                .collect(Collectors.toList());

        model.addAttribute("daDatHoaDon", daDatHoaDon);


        List<DatPhong> datPhongs = datPhongService.search(
                maDatPhong, tenKhach, maNhanVien, ma_cccd,
                ngayNhanTu, ngayNhanDen, ngayTraTu, ngayTraDen,
                soNguoiLon, soTreEm, trangThai, yeuCauThem,
                ngayTaoTu, ngayTaoDen, ngayCapNhatTu, ngayCapNhatDen
        );

        if(tenKhach!=null){
            System.out.println("Found!");
        }else{
            System.out.println("NOt Found: "+tenKhach);
        }

        Map<Integer, List<Phong>> phongTheoDon = new HashMap<>();
        for (DatPhong dp : datPhongs) {
            phongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
        }

        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("phongTheoDon", phongTheoDon);

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

    @PostMapping("/update-trang-thai")
    public String updateTrangThai(@RequestParam Integer id,
                                  @RequestParam String trangThai,
                                  RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);

        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "khong tim thay don dat phong");
            return "redirect:/nhan-su/admin/dat-phong";
        }

        if (dp.getMa_cccd() == null || dp.getMa_cccd().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "don dat phong chua co CCCD");
            return "redirect:/nhan-su/admin/dat-phong";
        }

        dp.setTrangThai(trangThai);
        datPhongService.save(dp);

        if ("Da tra phong".equals(dp.getTrangThai())) {
            List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
            for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
                Phong p = ctdp.getP();
                p.setTrangThai("Trong");
                System.out.println("Phong da chuyen trang thai: " + p.getSoPhong() + " Trang thai cu: " + p.getTrangThai());

                phongService.save1(p);
            }
        }

        redirectAttributes.addFlashAttribute("success", "Cap nhat trang thai thanh cong");
        return "redirect:/nhan-su/admin/dat-phong";
    }
    @PostMapping("/update")
    public String update(
            @RequestParam("id") Integer id,
            @RequestParam(value = "hoten", required = false) String hoten,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "sdt", required = false) String sdt,
            @RequestParam(value = "ma_cccd", required = false) String maCccd,
            @RequestParam(value = "ngaydatPhong", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime ngayDatPhong,
            @RequestParam(value = "ngaytraPhong", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime ngayTraPhong,
            @RequestParam("songuoiLon") int songuoiLon,
            @RequestParam("sotreEm") int sotreEm,
            @RequestParam(value = "yeuCauThem", required = false) String yeuCauThem,
            @RequestParam("trangThai") String trangThai,
            RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong";
        }

        if (dp.getN() == null) {
            dp.setHoten(hoten);
            dp.setEmail(email);
            dp.setSdt(sdt);
        }

        dp.setMa_cccd(maCccd);
        dp.setNgaydatPhong(ngayDatPhong);
        dp.setNgaytraPhong(ngayTraPhong);
        dp.setSonguoiLon(songuoiLon);
        dp.setSotreEm(sotreEm);
        dp.setYeuCauThem(yeuCauThem);
        dp.setTrangThai(trangThai);
        dp.setNgayCapNhat(LocalDateTime.now());

        datPhongService.save(dp);

        redirectAttributes.addFlashAttribute("success", "Cập nhật đơn đặt phòng #" + id + " thành công");
        return "redirect:/nhan-su/admin/dat-phong";
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
            return "redirect:/nhan-su/nhan-vien/dat-phong";
        }

        dp.setMa_cccd(maCccd);

        KhachHang n = dp.getN();
        if (n == null) {
            dp.setHoten(hoten);
            dp.setEmail(email);
            dp.setSdt(sdt);
        } else {
            n.setHoTen(hoten);        // TODO: đổi tên nếu entity NguoiDung dùng getter/setter khác
            n.setEmail(email);
            n.setSoDienThoai(sdt);            // TODO: có thể là setSoDienThoai(...)
            nguoiDungService.save(n); // TODO: xác nhận đúng tên method trong NguoiDungService
        }

        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        redirectAttributes.addFlashAttribute("thanhCongCapNhat", "Cap nhat thong tin khach hang thanh cong.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }
    @PostMapping("/dat-phong/chi-tiet/{id}/thu-tien")
    public String thuTien(@PathVariable Integer id, @RequestParam BigDecimal soTien,
                          @RequestParam String phuongthuc, HttpServletRequest request, RedirectAttributes redirectAttributes){
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


