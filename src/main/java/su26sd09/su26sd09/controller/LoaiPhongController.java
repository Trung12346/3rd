package su26sd09.su26sd09.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.entity.PhongAnh;
import su26sd09.su26sd09.repository.PhongAnhRepository;
import su26sd09.su26sd09.service.BookingEmailService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.NguoiDungService;
import su26sd09.su26sd09.service.PhongService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/loai-phong")
public class LoaiPhongController {

    private static final String ANH_MAC_DINH =
            "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80";

    @Autowired
    private PhongService phongService;

    @Autowired
    private PhongAnhRepository phongAnhRepository;

    @Autowired
    private DatPhongService datPhongService;

    @Autowired
    private NguoiDungService nguoiDungService;

    @Autowired
    private BookingEmailService bookingEmailService;

    @GetMapping
    public String index(Model model) {
        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
        loadLoaiPhongList(model, loaiPhongs);
        model.addAttribute("anhLoaiPhong", buildAnhLoaiPhong(loaiPhongs));
        return "loai-phong";
    }

    @GetMapping("/tim-kiem")
    public String timKiem(
            @RequestParam(name = "ngayNhan", required = false) String ngayNhan,
            @RequestParam(name = "ngayTra", required = false) String ngayTra,
            @RequestParam(name = "nguoiLon", required = false) Integer nguoiLon,
            @RequestParam(name = "treEm", required = false) Integer treEm,
            @RequestParam(name = "mucGia", required = false) String mucGia,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        // Trang ket qua chi hoat dong dung khi co du ngay nhan/tra (nut "Dat phong"
        // can 2 moc thoi gian nay de tao don). Neu thieu, dua nguoi dung ve trang
        // chon loai phong / chon ngay thay vi hien thi ket qua khong the dat duoc.
        if (ngayNhan == null || ngayNhan.isBlank() || ngayTra == null || ngayTra.isBlank()) {
            redirectAttributes.addFlashAttribute("timKiemError",
                    "Vui lòng chọn ngày nhận và ngày trả phòng trước khi tìm kiếm.");
            return "redirect:/home";
        }

        model.addAttribute("ngayNhan", ngayNhan);
        model.addAttribute("ngayTra", ngayTra);
        model.addAttribute("nguoiLon", nguoiLon);
        model.addAttribute("treEm", treEm);
        model.addAttribute("mucGia", mucGia);

        LocalDateTime ngayNhanPhong = null;
        LocalDateTime ngayTraPhong = null;
        boolean coNgay = ngayNhan != null && !ngayNhan.isBlank() && ngayTra != null && !ngayTra.isBlank();

        if (coNgay) {
            try {
                ngayNhanPhong = LocalDate.parse(ngayNhan.trim()).atStartOfDay();
                ngayTraPhong = LocalDate.parse(ngayTra.trim()).atStartOfDay();
            } catch (DateTimeParseException e) {
                model.addAttribute("timKiemError", "Định dạng ngày không hợp lệ.");
                model.addAttribute("loaiPhongs", List.of());
                model.addAttribute("soPhongTrongTheoLoai", Map.of());
                model.addAttribute("anhLoaiPhong", Map.of());
                return "loai-phong-ket-qua";
            }
            if (!ngayTraPhong.isAfter(ngayNhanPhong)) {
                model.addAttribute("timKiemError", "Ngày trả phòng phải sau ngày nhận phòng.");
                model.addAttribute("loaiPhongs", List.of());
                model.addAttribute("soPhongTrongTheoLoai", Map.of());
                model.addAttribute("anhLoaiPhong", Map.of());
                return "loai-phong-ket-qua";
            }
        }

        PhongService.LoaiPhongSearchResult ketQua =
                phongService.searchLoaiPhongKhaDung(ngayNhanPhong, ngayTraPhong, nguoiLon, treEm, mucGia);

        model.addAttribute("loaiPhongs", ketQua.getLoaiPhongs());
        model.addAttribute("soPhongTrongTheoLoai", ketQua.getSoPhongKhaDungTheoLoai());
        model.addAttribute("anhLoaiPhong", buildAnhLoaiPhong(ketQua.getLoaiPhongs()));
        // Ten tien nghi dai dien cho moi loai phong (lay tu phong dau tien cua loai),
        // dung de hien thi chip tien nghi trong card tren trang ket qua.
        model.addAttribute("tienNghiTheoLoai", buildTienNghiTheoLoai(ketQua.getLoaiPhongs()));

        if (coNgay) {
            long soDem = java.time.temporal.ChronoUnit.DAYS.between(ngayNhanPhong.toLocalDate(), ngayTraPhong.toLocalDate());
            model.addAttribute("soDem", soDem);
        }

        return "loai-phong-ket-qua";
    }

    /**
     * Tien nghi cua loai phong = tien nghi cua phong DAI DIEN (phong dau tien
     * thuoc loai do, hoatDong=true). Vi bang tien_nghi_phong map theo Phong
     * chu khong theo LoaiPhong, nen lay 1 phong lam dai dien cung du cho
     * muc dich hien thi chip tien nghi tren trang ket qua.
     */
    private Map<Integer, List<String>> buildTienNghiTheoLoai(List<LoaiPhong> loaiPhongs) {
        Map<Integer, List<String>> result = new HashMap<>();
        if (loaiPhongs == null) return result;
        for (LoaiPhong lp : loaiPhongs) {
            List<Phong> phongs = phongService.findPhongTheoLoai(lp.getId());
            if (phongs != null && !phongs.isEmpty()) {
                result.put(lp.getId(),
                        phongService.findTenTienNghiByPhong(phongs.get(0).getMaPhong()));
            } else {
                result.put(lp.getId(), List.of());
            }
        }
        return result;
    }

    /**
     * BOOKING ENGINE: dat nhanh 1 phong theo loai, khong can khach tu chon
     * phong cu the. Engine (PhongService.assignRoomsForType) tu chon 1 phong
     * con trong hop le cua loaiPhongId trong khoang [ngayNhan, ngayTra), roi
     * tao don DatPhong va chuyen thang toi trang xac nhan/thanh toan co san
     * (giong het luong gio-hang cu, chi khac o buoc chon phong).
     *
     * Luong gio-hang / chon phong thu cong cho khach da bi ngung su dung va
     * KHONG duoc goi tu day nua.
     */
    // Dinh dang CCCD 12 so (theo mau moi tu 2021) hoac CMND 9 so (mau cu, van con
    // luu hanh voi mot so khach chua doi). Chi kiem tra do dai/dang so, khong xac
    // thuc that.
    private static final java.util.regex.Pattern CCCD_PATTERN =
            java.util.regex.Pattern.compile("^\\d{9}(\\d{3})?$");

    @PostMapping("/dat-nhanh")
    public String datNhanh(
            @RequestParam("loaiPhongId") int loaiPhongId,
            @RequestParam(name = "soLuong", defaultValue = "1") int soLuong,
            @RequestParam("ngayNhan") String ngayNhanStr,
            @RequestParam("ngayTra") String ngayTraStr,
            @RequestParam(name = "nguoiLon", required = false) String nguoiLonStr,
            @RequestParam(name = "treEm", required = false) String treEmStr,
            @RequestParam(name = "mucGia", required = false) String mucGia,
            @RequestParam(name = "maCccd") String maCccdStr,
            RedirectAttributes redirectAttributes
    ) {
        Integer nguoiLon = parseIntOrNull(nguoiLonStr);
        Integer treEm = parseIntOrNull(treEmStr);

        String maCccd = maCccdStr == null ? "" : maCccdStr.trim();
        if (!CCCD_PATTERN.matcher(maCccd).matches()) {
            redirectAttributes.addFlashAttribute("timKiemError",
                    "So CCCD/CMND khong hop le. Vui long nhap 9 hoac 12 chu so.");
            return redirectTimKiem(ngayNhanStr, ngayTraStr, nguoiLon, treEm, mucGia);
        }

        if (soLuong <= 0) soLuong = 1;

        LocalDateTime ngayNhan;
        LocalDateTime ngayTra;
        try {
            // Gio nhan/tra phong co dinh cho luong dat nhanh: nhan phong 14:00 (2PM),
            // tra phong 11:00 (11AM), khong phu thuoc gio nguoi dung bam nut.
            ngayNhan = LocalDate.parse(ngayNhanStr.trim()).atTime(14, 0);
            ngayTra = LocalDate.parse(ngayTraStr.trim()).atTime(11, 0);
        } catch (DateTimeParseException | NullPointerException e) {
            redirectAttributes.addFlashAttribute("timKiemError", "Vui long chon ngay nhan va ngay tra phong.");
            return redirectTimKiem(ngayNhanStr, ngayTraStr, nguoiLon, treEm, mucGia);
        }

        if (!ngayTra.isAfter(ngayNhan)) {
            redirectAttributes.addFlashAttribute("timKiemError", "Ngay tra phong phai sau ngay nhan phong.");
            return redirectTimKiem(ngayNhanStr, ngayTraStr, nguoiLon, treEm, mucGia);
        }

        try {
            // Booking engine tu chon (soLuong) phong con trong thuc su cua loai
            // -> assignRoomsForType se nem IllegalStateException neu khong du.
            List<Phong> phongDuocChon = phongService.assignRoomsForType(loaiPhongId, soLuong, ngayNhan, ngayTra);

            // Validate suc chua: neu tong nguoi (NL + TE) vuot suc chua tong cua cac phong duoc chon,
            // chi canh bao de nhan vien xep loai phong lon hon luc xac nhan yeu cau.
            // KHONG chan dat yeu cau vi khach van co quyen gui yeu cau, nhan vien se xu ly.
            if (phongDuocChon != null && !phongDuocChon.isEmpty()) {
                int tongSucChua = phongDuocChon.stream()
                        .filter(p -> p.getLoaiPhong() != null)
                        .mapToInt(p -> p.getLoaiPhong().getSucChuaToiDa())
                        .sum();
                int tongNguoi = (nguoiLon != null ? nguoiLon : 0) + (treEm != null ? treEm : 0);
                if (tongNguoi > tongSucChua) {
                    redirectAttributes.addFlashAttribute("canhBaoSucChua",
                            "Tong nguoi (" + tongNguoi + ") vuot suc chua toi da (" + tongSucChua
                                    + ") cua " + phongDuocChon.size() + " phong dang chon. "
                                    + "Yeu cau se duoc nhan vien xu ly.");
                }
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            KhachHang khachHang = null;
            if (authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    && !isNhanVienOrAdmin(authentication)) {
                khachHang = nguoiDungService.findByEmail(authentication.getName());
            }

            // createAutoAssignedBooking da vong lap tao 1 ChiTietDatPhong cho MOI
            // phong trong danh sach, nen dat nhieu phong se tao dong dang N dong.
            // Dat phong voi trangThai="Yeu cau dat phong" — NV xac nhan + xep phong sau.
            // Sau do KHACH van di tiep qua flow xac nhan (chọn DV, KM, thanh toan VNPay)
            // giong het guest checkout. Sau khi thanh toan, trangThai giu nguyen de NV xu ly.
            DatPhong datPhong = datPhongService.createAutoAssignedBooking(
                    phongDuocChon, khachHang, ngayNhan, ngayTra,
                    nguoiLon != null ? nguoiLon : 0, treEm != null ? treEm : 0, maCccd);

            // Gui email xac nhan cho khach (async, khong block redirect)
            try {
                bookingEmailService.guiEmailYeuCauDatPhong(datPhong.getId());
            } catch (Exception ex) {
                // Khong block luong dat phong neu gui mail loi
                ex.printStackTrace();
            }

            return "redirect:/phong/dat-phong/xac-nhan/" + datPhong.getId();
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("timKiemError", e.getMessage());
            return redirectTimKiem(ngayNhanStr, ngayTraStr, nguoiLon, treEm, mucGia);
        } catch (Exception e) {
            // Bat moi loi khong luong truoc de tranh forward toi /error (endpoint nay
            // khong permitAll trong SecurityConfig -> khach vang lai se bi bat ve /login).
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("timKiemError",
                    "Khong the dat phong tu dong luc nay, vui long thu lai. [" + e.getClass().getSimpleName()
                            + ": " + e.getMessage() + "]");
            return redirectTimKiem(ngayNhanStr, ngayTraStr, nguoiLon, treEm, mucGia);
        }
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String redirectTimKiem(String ngayNhan, String ngayTra, Integer nguoiLon, Integer treEm, String mucGia) {
        StringBuilder sb = new StringBuilder("redirect:/loai-phong/tim-kiem?");
        if (ngayNhan != null) sb.append("ngayNhan=").append(ngayNhan).append("&");
        if (ngayTra != null) sb.append("ngayTra=").append(ngayTra).append("&");
        if (nguoiLon != null) sb.append("nguoiLon=").append(nguoiLon).append("&");
        if (treEm != null) sb.append("treEm=").append(treEm).append("&");
        if (mucGia != null) sb.append("mucGia=").append(mucGia).append("&");
        return sb.toString();
    }

    private boolean isNhanVienOrAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority()));
    }

    @GetMapping("/{id}")
    public String phongTheoLoai(
            @PathVariable("id") int id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        LoaiPhong loaiPhong = phongService.findLoaiPhongById(id);
        if (loaiPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy loại phòng");
            return "redirect:/loai-phong";
        }

        List<Phong> phongs = phongService.findPhongTheoLoai(id);
        Map<Integer, List<String>> tienNghiTheoPhong = new HashMap<>();
        for (Phong phong : phongs) {
            tienNghiTheoPhong.put(phong.getMaPhong(), phongService.findTenTienNghiByPhong(phong.getMaPhong()));
        }

        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();

        HashMap<Integer, UUID> thumbAnhs = new HashMap<>();
        for (Phong p: phongs
        ) {
            Integer pid = p.getMaPhong();
            PhongAnh pa = phongAnhRepository.findByMaPhongFirst(p.getMaPhong());
            thumbAnhs.put(
                    pid,
                    pa != null ? pa.maAnh.maAnh : null
            );
        }

        List<LoaiPhong> tatCaLoaiPhong = phongService.findAllLoai();
        Map<Integer, String> anhLoaiPhong = buildAnhLoaiPhong(tatCaLoaiPhong);

        Map<Integer, RoomBookingGuardDTO> bookingGuardByPhong = phongService.buildRoomGuards(phongs);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Map<Integer, String> khoaLichJsonByPhong = new HashMap<>();

        for (Map.Entry<Integer, RoomBookingGuardDTO> entry : bookingGuardByPhong.entrySet()) {
            try {
                khoaLichJsonByPhong.put(
                        entry.getKey(),
                        mapper.writeValueAsString(entry.getValue().getDanhSachKhoaLich())
                );
            } catch (Exception e) {
                khoaLichJsonByPhong.put(entry.getKey(), "[]");
            }
        }
        model.addAttribute("khoaLichJsonByPhong", khoaLichJsonByPhong);

        model.addAttribute("loaiPhong", loaiPhong);
        model.addAttribute("thumbAnhs", thumbAnhs);
        model.addAttribute("phongs", phongs);
        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
        model.addAttribute("loaiPhongs", tatCaLoaiPhong);
        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
        model.addAttribute("bookingGuardByPhong", bookingGuardByPhong);
        model.addAttribute("gioNhanToiDaMacDinh", LocalTime.of(11,0));
        model.addAttribute("gioTraToiDaMacDinh", LocalTime.of(18,30));
        return "phong-theo-loai";
    }

    private void loadLoaiPhongList(Model model, List<LoaiPhong> loaiPhongs) {
        Map<Integer, Long> soPhongTrongTheoLoai = new HashMap<>();
        for (LoaiPhong loaiPhong : loaiPhongs) {
            soPhongTrongTheoLoai.put(loaiPhong.getId(), phongService.countPhongTrongTheoLoai(loaiPhong.getId()));
        }

        model.addAttribute("loaiPhongs", loaiPhongs);
        model.addAttribute("soPhongTrongTheoLoai", soPhongTrongTheoLoai);
    }

    private Map<Integer, String> buildAnhLoaiPhong(List<LoaiPhong> loaiPhongs) {
        Map<Integer, String> anhLoaiPhong = new HashMap<>();
        for (LoaiPhong lp : loaiPhongs) {
            if (lp.getMaAnh() != null) {
                anhLoaiPhong.put(lp.getId(), "/media/" + lp.getMaAnh().getMaAnh());
            } else {
                anhLoaiPhong.put(lp.getId(), ANH_MAC_DINH);
            }
        }
        return anhLoaiPhong;
    }
}