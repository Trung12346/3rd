package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.service.ChiTietDatPhongService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.NguoiDungService;
import su26sd09.su26sd09.service.PhongService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/gio-hang")
public class GioHangController {

    @Autowired
    DatPhongService datPhongService;

    @Autowired
    PhongService PhongService;

    @Autowired
    ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    NguoiDungService nguoiDungService;

    @GetMapping("")
    public String GetDanhSachPhong(){
        return "gio-hang";
    }

    @PostMapping("/checkout")
    public String checkoutCart(
            @RequestParam("roomIds") List<Integer> roomIds,
            @RequestParam("ngayNhan") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ngayNhan,
            @RequestParam("ngayTra")  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ngayTra,
            @RequestParam("nguoiLon") Integer nguoiLon,
            @RequestParam("treEm")    Integer treEm,
            @RequestParam(value = "ma_cccd",required = false) String ma_cccd,
            @RequestParam Map<String,String> allParamsCCCD,
            RedirectAttributes redirectAttributes,
            Authentication authentication
    ) {
        if (roomIds == null || roomIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("bookingError", "Gio hang dang trong. Vui long chon phong truoc khi dat.");
            return "redirect:/gio-hang";
        }

        long soDem = ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate());
        if (soDem < 1) {
            redirectAttributes.addFlashAttribute("bookingError", "Ngay tra phong phai sau ngay nhan phong it nhat 1 ngay.");
            return "redirect:/gio-hang";
        }

        List<Phong> ListPhong = new ArrayList<>();
        int tongSucChua = 0;
        for (int p : roomIds) {
            Phong phong = PhongService.findPhongById(p);
            if (phong == null) {
                redirectAttributes.addFlashAttribute("bookingError", "Co phong khong ton tai trong gio hang. Vui long chon lai.");
                return "redirect:/gio-hang";
            }

            RoomBookingGuardDTO guard = PhongService.buildRoomGuardFor(p);
            String guardError = validateRoomBookingGuard(guard, ngayNhan, ngayTra);

            System.out.println("===== USER INPUT =====");
            System.out.println("Ngay nhan: " + ngayNhan);
            System.out.println("Ngay tra  : " + ngayTra);
            System.out.println("===== GUARD (phong " + p + ") =====");
            System.out.println("So khoang bi khoa: " + guard.getDanhSachKhoaLich().size());
            for (KhoangNgayBiKhoaDTO k : guard.getDanhSachKhoaLich()) {
                System.out.println("  - " + k.getNgayBatDau() + " -> " + k.getNgayKetThuc() + " (" + k.getTrangThaiDon() + ")");
            }

            if (guardError != null) {
                redirectAttributes.addFlashAttribute("bookingError", "Phong " + phong.getSoPhong() + ": " + guardError);
                return "redirect:/gio-hang";
            }

            if (phong.getLoaiPhong() != null) {
                tongSucChua += phong.getLoaiPhong().getSucChuaToiDa();
            }
            ListPhong.add(phong);
        }

        int tongNguoi = (nguoiLon != null ? nguoiLon : 0) + (treEm != null ? treEm : 0);
        if (tongSucChua > 0 && tongNguoi > tongSucChua) {
            redirectAttributes.addFlashAttribute("bookingError",
                    "So luong nguoi (" + tongNguoi + ") vuot qua suc chua cua cac phong trong gio (" + tongSucChua + " nguoi).");
            return "redirect:/gio-hang";
        }

        authentication = SecurityContextHolder.getContext().getAuthentication();
        String email;
        if (authentication != null && authentication.isAuthenticated() && !isNhanVienOrAdmin(authentication)) {
            email = authentication.getName();
        } else {
            email = null;
        }
        KhachHang n = nguoiDungService.findByEmail(email);
        Map<Integer, String> cccdTheoPhong = allParamsCCCD.entrySet().stream().
                filter(e -> e.getKey().startsWith("cccdPhong_")).
                collect(Collectors.toMap(
                        e -> Integer.parseInt(e.getKey().substring("cccdPhong_".length())),
                        Map.Entry::getValue
                ));
        DatPhong datPhong = new DatPhong();
        datPhong.setN(n);
        if (ma_cccd != null) {
            datPhong.setMa_cccd(ma_cccd);
        } else {
            datPhong.setMa_cccd(null);
        }
        datPhong.setNgaydatPhong(ngayNhan);
        datPhong.setNgaytraPhong(ngayTra);
        datPhong.setSonguoiLon(nguoiLon);
        datPhong.setSotreEm(treEm);
        datPhong.setYeuCauThem(buildGuardNotes(ListPhong, ngayNhan, ngayTra));
        datPhong.setTrangThai("Chua thanh toan");
        datPhong.setNgayTao(LocalDateTime.now());
        datPhong.setNgayCapNhat(null);
        datPhong.setSdt(null);
        datPhongService.save(datPhong);

        long resThue = soDem;
        BigDecimal amount = BigDecimal.ZERO;
        for (Phong p : ListPhong) {
            if (p == null) {
                System.out.println("Null");
            }
            List<BigDecimal> price = new ArrayList<>();
            price.add(p.getGiaMoiDem());
            for (BigDecimal i : price) {
                amount = amount.add(i);
            }
            System.out.println("Total " + amount);
        }
        amount = amount.multiply(BigDecimal.valueOf(resThue));
        System.out.println(amount);

        for (Phong p : ListPhong) {
            BigDecimal amount2 = p.getGiaMoiDem();
            amount2 = amount2.multiply(BigDecimal.valueOf(resThue));
            amount2 = amount2.add(calculateExtraFee(PhongService.buildRoomGuardFor(p.getMaPhong()), ngayNhan, ngayTra));
            ChiTietDatPhong chiTietDatPhong = new ChiTietDatPhong();
            chiTietDatPhong.setP(p);
            chiTietDatPhong.setGiaMoiDem(p.getGiaMoiDem());
            chiTietDatPhong.setMa_cccd(cccdTheoPhong.get(p.getMaPhong()));
            System.out.println("ma_cccd cua phong: " + p.getMaPhong() + "la: " + cccdTheoPhong.get(p.getMaPhong()));
            chiTietDatPhong.setGiaKhiDat(amount2);
            chiTietDatPhong.setPhuPhi(calculateExtraFee(PhongService.buildRoomGuardFor(p.getMaPhong()), ngayNhan, ngayTra));
            chiTietDatPhong.setD(datPhong);
            System.out.println("Cac phong: " + p.getSoPhong() + "Gia la: " + amount2);

            chiTietDatPhongService.save(chiTietDatPhong);
        }
        return "redirect:/phong/dat-phong/xac-nhan/" + datPhong.getId();
    }

    /**
     * Validate theo Cách 1: duyệt TỪNG khoảng trong danhSachKhoaLich (không
     * gộp min-max), chặn nếu overlap với BẤT KỲ khoảng nào. Không còn phụ
     * thuộc vào 1 "đơn gần nhất" duy nhất như trước — nhờ vậy phòng có nhiều
     * đơn đang giữ chỗ (kể cả đơn ở tương lai gần lẫn tương lai xa) đều được
     * kiểm tra đầy đủ.
     */
    private String validateRoomBookingGuard(RoomBookingGuardDTO guard,
                                            LocalDateTime ngayNhan,
                                            LocalDateTime ngayTra) {

        if (guard == null || !guard.isCoTheDat()) {
            return "Phong khong kha dung, vui long chon phong khac.";
        }

        for (KhoangNgayBiKhoaDTO khoang : guard.getDanhSachKhoaLich()) {
            LocalDateTime batDau = khoang.getNgayBatDau();
            LocalDateTime ketThuc = khoang.getNgayKetThuc();
            if (batDau == null || ketThuc == null) continue;

            boolean overlap = ngayNhan.isBefore(ketThuc) && ngayTra.isAfter(batDau);
            if (overlap) {
                return "Phong da co lich dat tu "
                        + batDau.toLocalDate()
                        + " den "
                        + ketThuc.toLocalDate()
                        + ". Vui long chon khoang ngay khac.";
            }
        }

        return null;
    }

    private String buildGuardNotes(List<Phong> phongs, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        List<String> notes = new ArrayList<>();
        for (Phong phong : phongs) {
            RoomBookingGuardDTO guard = PhongService.buildRoomGuardFor(phong.getMaPhong());
            if (guard == null) {
                continue;
            }

            BigDecimal phuPhi = calculateExtraFee(guard, ngayNhan, ngayTra);
            if (BigDecimal.ZERO.compareTo(phuPhi) < 0) {
                notes.add("[PHU_PHI_NGOAI_GIO_PHONG_" + phong.getMaPhong() + "="
                        + phuPhi.toPlainString() + "]");
            }
        }

        if (notes.isEmpty()) {
            return null;
        }
        return String.join(" ", notes);
    }

    private BigDecimal calculateExtraFee(RoomBookingGuardDTO guard, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        if (guard == null) {
            return BigDecimal.ZERO;
        }

        LocalTime gioNhan = ngayNhan.toLocalTime();
        LocalTime gioTra = ngayTra.toLocalTime();
        boolean ngoaiGioNhan = gioNhan.isBefore(guard.getGioNhanToiThieu()) || gioNhan.isAfter(guard.getGioNhanToiDa());
        boolean ngoaiGioTra = gioTra.isAfter(guard.getGioTraToiDa());
        return (ngoaiGioNhan || ngoaiGioTra) ? guard.getPhuPhiNgoaiGioVND() : BigDecimal.ZERO;
    }

    private boolean isNhanVienOrAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority()));
    }

}