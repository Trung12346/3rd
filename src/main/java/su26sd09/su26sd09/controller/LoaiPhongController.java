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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
import su26sd09.su26sd09.entity.Anh;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.LoaiPhongAnh;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.entity.PhongAnh;
import su26sd09.su26sd09.repository.LoaiPhongAnhRepository;
import su26sd09.su26sd09.service.BookingDraftService;
import su26sd09.su26sd09.service.BookingEmailService;
import su26sd09.su26sd09.service.DanhGiaService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.NguoiDungService;
import su26sd09.su26sd09.service.PhongService;
import su26sd09.su26sd09.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/loai-phong")
public class LoaiPhongController {

    private static final String ANH_MAC_DINH =
            "ed2d10ce-680a-467e-a83c-c0781f53a5fd";

    @Autowired
    private PhongService phongService;


    @Autowired
    private LoaiPhongAnhRepository loaiPhongAnhRepository;

    @Autowired
    private DatPhongService datPhongService;

    @Autowired
    private NguoiDungService nguoiDungService;

    @Autowired
    private BookingEmailService bookingEmailService;

    @Autowired
    private BookingDraftService bookingDraftService;

    @Autowired
    private su26sd09.su26sd09.service.PendingBookingService pendingBookingService;

    @Autowired
    private DanhGiaService danhGiaService;

    @Autowired
    private ReviewService reviewService;

    /**
     * Trang chi tiet LOAI PHONG (khong phai 1 phong cu the). Copy cau truc tu
     * PhongController#detail (trang /phong/{id}) nhung du lieu duoc GOP
     * (aggregate) tu tat ca cac phong vat ly thuoc loai nay: tien nghi la hop
     * cua tien nghi cac phong, anh la hop cac anh cac phong, danh gia la gop
     * danh gia da duyet cua tat ca cac phong thuoc loai. Dat phong o trang nay
     * di qua booking engine theo loai (POST /loai-phong/dat-nhanh), khong gan
     * cung 1 phong vat ly cu the nhu trang /phong/{id}.
     */
    @GetMapping("/{id}")
    public String detail(
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

        // Tien nghi thiet yeu cua loai phong = giao (chi giu tien nghi xuat hien
        // o TAT CA) tien nghi cua cac phong vat ly thuoc loai nay, vi bang
        // tien_nghi_phong map theo Phong.
        LinkedHashSet<String> tienNghiSet = null;
        for (Phong p : phongs) {
            List<String> tenTienNghi = phongService.findTenTienNghiByPhong(p.getMaPhong());
            if (tienNghiSet == null) {
                tienNghiSet = new LinkedHashSet<>(tenTienNghi);
            } else {
                tienNghiSet.retainAll(tenTienNghi);
            }
        }
        List<String> tienNghi = tienNghiSet == null ? new ArrayList<>() : new ArrayList<>(tienNghiSet);

        // Danh gia cua loai phong = gop danh gia DA DUYET cua tat ca phong thuoc
        // loai nay. Tai su dung nguyen he thong danh gia cua trang /phong/{id}
        // (ReviewService + fragment fragments/room-reviews) nhung o PHAM VI LOAI
        // PHONG: xem duoc danh gia gop tu moi phong thuoc loai, va gui danh gia
        // moi se tu dong gan voi lan dat phong gan nhat (thuoc bat ky phong nao
        // cua loai nay) cua khach dang dang nhap.
        List<su26sd09.su26sd09.dto.RoomReviewViewDTO> roomReviews =
                reviewService.findApprovedReviewsByLoaiPhong(id);
        double diemTrungBinh = roomReviews.stream()
                .mapToInt(su26sd09.su26sd09.dto.RoomReviewViewDTO::getDiemDanhGia)
                .average()
                .orElse(0);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken))
                ? authentication.getName()
                : null;
        model.addAttribute("maLoaiPhong", id);
        model.addAttribute("roomReviews", roomReviews);
        model.addAttribute("reviewEligibility", reviewService.getEligibilityForLoaiPhong(id, currentEmail));

        // Anh cua loai phong = uu tien anh rieng cua loai phong (bang
        // loai_phong_anh, upload/quan ly nhieu anh o trang admin loai phong),
        // gop them (khong trung) anh cua tat ca phong thuoc loai lam du phong.
        List<LoaiPhongAnh> lpas = loaiPhongAnhRepository.findByMaLoaiPhong_Id(id);
        List<Anh> anhs = new ArrayList<>();
        Set<UUID> maAnhDaThem = new HashSet<>();
        for (LoaiPhongAnh lpa : lpas) {
            if (lpa.maAnh != null && maAnhDaThem.add(lpa.maAnh.maAnh)) {
                anhs.add(lpa.maAnh);
            }
        }
        // Anh dai dien: anh dau tien trong danh sach gop duoc o tren.
        Anh thumbAnh = !anhs.isEmpty() ? anhs.get(0) : null;

        long soPhongTrong = phongService.countPhongTrongTheoLoai(id);

        // Cac loai phong khac de goi y (carousel cuoi trang), giong trang /phong/{id}.
        List<LoaiPhong> loaiPhongKhac = phongService.findLoaiPhongKhac(id);
        Map<Integer, String> anhLoaiPhongKhac = buildAnhLoaiPhong(loaiPhongKhac);

        model.addAttribute("loaiPhong", loaiPhong);
        model.addAttribute("thumbAnh", thumbAnh);
        model.addAttribute("anhs", anhs);
        model.addAttribute("tienNghi", tienNghi);
        model.addAttribute("tongDanhGia", roomReviews.size());
        model.addAttribute("diemTrungBinh", diemTrungBinh);
        model.addAttribute("soPhongTrong", soPhongTrong);
        model.addAttribute("loaiPhongKhac", loaiPhongKhac);
        model.addAttribute("anhLoaiPhongKhac", anhLoaiPhongKhac);

        return "loai-phong-detail";
    }

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
            @RequestParam(name = "checkOutTime", required = false, defaultValue = "12:00") String checkOutTime,
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

        // Gio tra phong chuan (mac dinh 12:00). FE (index.html / loai-phong-ket-qua.html)
        // forward gia tri nay tu form tim kiem, va no se duoc forward tiep xuong
        // dat-nhanh ben duoi de tao don voi dung gio tra da hien thi cho khach.
        LocalTime gioTraChuan;
        try {
            gioTraChuan = LocalTime.parse(checkOutTime.trim());
        } catch (Exception e) {
            gioTraChuan = LocalTime.of(12, 0);
        }

        model.addAttribute("ngayNhan", ngayNhan);
        model.addAttribute("ngayTra", ngayTra);
        model.addAttribute("nguoiLon", nguoiLon);
        model.addAttribute("treEm", treEm);
        model.addAttribute("mucGia", mucGia);
        model.addAttribute("checkOutTime", gioTraChuan.toString());

        LocalDateTime ngayNhanPhong = null;
        LocalDateTime ngayTraPhong = null;
        boolean coNgay = ngayNhan != null && !ngayNhan.isBlank() && ngayTra != null && !ngayTra.isBlank();

        if (coNgay) {
            try {
                // Ap dung dung quy tac nhan phong 14:00 / tra phong 12:00 (gioTraChuan,
                // forward tu FE) khi tinh khoang tim kiem, dong bo voi luc tao don
                // thuc su (dat-nhanh ben duoi) de tranh sai lech gio gay chong lan gia
                // (khoa lich nham).
                ngayNhanPhong = LocalDate.parse(ngayNhan.trim()).atTime(14, 0);
                ngayTraPhong = LocalDate.parse(ngayTra.trim()).atTime(gioTraChuan);
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
            @RequestParam(name = "checkOutTime", required = false, defaultValue = "12:00") String checkOutTimeStr,
            @RequestParam(name = "maCccd") String maCccdStr,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response
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

        // Gio tra phong: forward tu FE (index.html / loai-phong-ket-qua.html), mac dinh
        // 12:00 neu khong duoc gui kem (vi du goi truc tiep endpoint khong qua form).
        LocalTime gioTraChuan;
        try {
            gioTraChuan = LocalTime.parse(checkOutTimeStr == null ? "" : checkOutTimeStr.trim());
        } catch (Exception e) {
            gioTraChuan = LocalTime.of(12, 0);
        }

        LocalDateTime ngayNhan;
        LocalDateTime ngayTra;
        try {
            // Gio nhan phong co dinh cho luong dat nhanh: nhan phong 14:00 (2PM).
            // Gio tra phong lay tu checkOutTime FE gui len (mac dinh 12:00/12PM).
            ngayNhan = LocalDate.parse(ngayNhanStr.trim()).atTime(14, 0);
            ngayTra = LocalDate.parse(ngayTraStr.trim()).atTime(gioTraChuan);
        } catch (DateTimeParseException | NullPointerException e) {
            redirectAttributes.addFlashAttribute("timKiemError", "Vui long chon ngay nhan va ngay tra phong.");
            return redirectTimKiem(ngayNhanStr, ngayTraStr, nguoiLon, treEm, mucGia);
        }

        if (!ngayTra.isAfter(ngayNhan)) {
            redirectAttributes.addFlashAttribute("timKiemError", "Ngay tra phong phai sau ngay nhan phong.");
            return redirectTimKiem(ngayNhanStr, ngayTraStr, nguoiLon, treEm, mucGia);
        }

        try {
            // CHUA tao DatPhong o day nua. Chi kiem tra con du (soLuong) phong
            // trong thuc su cua loai (dung chung engine voi luc tao that o
            // buoc "Hoan tat dat phong") de bao loi som cho khach neu het
            // phong, nhung KHONG giu/khoa phong nao ca - assignRoomsForType()
            // chi doc, khong ghi DB. Ket qua o day bi bo qua, chi de bat
            // IllegalStateException giong het hanh vi cu.
            phongService.assignRoomsForType(loaiPhongId, soLuong, ngayNhan, ngayTra);

            // ===== Dong goi thong tin da nhap thanh 1 ban nhap (PendingBookingDraft)
            // va luu trong SESSION (khong dung DB) de forward qua cac buoc tiep theo
            // (chon dich vu bo sung -> nhap thong tin lien he). DatPhong THAT chi
            // duoc tao khi khach bam "Hoan tat dat phong" o buoc cuoi, tranh tao
            // "don rac" (khong co thong tin lien he) giu phong truoc khach khac. =====
            su26sd09.su26sd09.dto.PendingBookingDraft draft = new su26sd09.su26sd09.dto.PendingBookingDraft();
            draft.setLoaiPhongId(loaiPhongId);
            draft.setSoLuong(soLuong);
            draft.setNgayNhan(ngayNhan);
            draft.setNgayTra(ngayTra);
            draft.setNguoiLon(nguoiLon != null ? nguoiLon : 0);
            draft.setTreEm(treEm != null ? treEm : 0);
            draft.setMucGia(mucGia);
            draft.setCheckOutTime(gioTraChuan.toString());
            draft.setMaCccd(maCccd);

            int pendingId = pendingBookingService.create(request, draft);

// Backup PendingBooking ngay sau khi nhập CCCD
            pendingBookingService.remember(response, pendingId);

            return "redirect:/phong/dat-phong/xac-nhan/" + pendingId;
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

    /**
     * MOI - endpoint chi doc (khong dat phong), tra ve JSON danh sach cac
     * khoang ngay ma LOAI PHONG (id) da het sach phong hoat dong trong 1
     * THANG cu the. Dung cho calendar cua trang chi tiet loai phong
     * (loai-phong-detail.html) de disable cac o ngay tuong ung. FE goi lai
     * endpoint nay moi khi nguoi dung chuyen sang thang khac tren calendar
     * (1 request / 1 thang dang xem), khong tai toan bo nam cung luc.
     *
     * @param thang dang "yyyy-MM" (vi du "2026-08"). Mac dinh la thang hien
     *              tai neu khong truyen.
     */
    @GetMapping("/{id}/ngay-het-phong")
    @ResponseBody
    public List<su26sd09.su26sd09.dto.NgayHetPhongDTO> ngayHetPhong(
            @PathVariable("id") int id,
            @RequestParam(name = "thang", required = false) String thang
    ) {
        java.time.YearMonth ym;
        try {
            ym = (thang == null || thang.isBlank())
                    ? java.time.YearMonth.now()
                    : java.time.YearMonth.parse(thang.trim());
        } catch (java.time.format.DateTimeParseException e) {
            ym = java.time.YearMonth.now();
        }
        return phongService.tinhNgayHetPhongTheoLoai(id, ym);
    }

    /**
     * MOI - endpoint chi doc, tra ve so phong con trong THUC SU cua loai
     * phong (id) trong khoang [ngayNhan, ngayTra) ma khach dang chon tren
     * calendar cua trang chi tiet loai phong. Dung chung "room availability
     * checking engine" voi searchLoaiPhongKhaDung()/assignRoomsForType()
     * (PhongService.soPhongKhaDungTheoLoaiVaNgay) nen ket qua luon khop voi
     * so phong ma booking engine thuc su co the gan khi khach bam "Dat Loai
     * Phong Nay". FE goi lai endpoint nay moi khi khach chon xong ngay
     * nhan/tra de cap nhat max cua o "So luong phong", tranh cho khach chon
     * so luong vuot qua so phong thuc su con trong ngay do.
     */
    @GetMapping("/{id}/so-phong-kha-dung")
    @ResponseBody
    public Map<String, Object> soPhongKhaDung(
            @PathVariable("id") int id,
            @RequestParam(name = "ngayNhan", required = false) String ngayNhan,
            @RequestParam(name = "ngayTra", required = false) String ngayTra
    ) {
        LocalDateTime ngayNhanPhong = null;
        LocalDateTime ngayTraPhong = null;
        try {
            if (ngayNhan != null && !ngayNhan.isBlank()) {
                ngayNhanPhong = LocalDate.parse(ngayNhan.trim()).atTime(14, 0);
            }
            if (ngayTra != null && !ngayTra.isBlank()) {
                ngayTraPhong = LocalDate.parse(ngayTra.trim()).atTime(11, 0);
            }
        } catch (DateTimeParseException e) {
            ngayNhanPhong = null;
            ngayTraPhong = null;
        }

        long soPhongKhaDung = phongService.soPhongKhaDungTheoLoaiVaNgay(id, ngayNhanPhong, ngayTraPhong);

        Map<String, Object> result = new HashMap<>();
        result.put("soPhongKhaDung", soPhongKhaDung);
        return result;
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

//    @GetMapping("/deprecated/{id}")
//    public String phongTheoLoai(
//            @PathVariable("id") int id,
//            Model model,
//            RedirectAttributes redirectAttributes
//    ) {
//        LoaiPhong loaiPhong = phongService.findLoaiPhongById(id);
//        if (loaiPhong == null) {
//            redirectAttributes.addFlashAttribute("error", "Không tìm thấy loại phòng");
//            return "redirect:/loai-phong";
//        }
//
//        List<Phong> phongs = phongService.findPhongTheoLoai(id);
//        Map<Integer, List<String>> tienNghiTheoPhong = new HashMap<>();
//        for (Phong phong : phongs) {
//            tienNghiTheoPhong.put(phong.getMaPhong(), phongService.findTenTienNghiByPhong(phong.getMaPhong()));
//        }
//
//        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
//
//        HashMap<Integer, UUID> thumbAnhs = new HashMap<>();
//        for (Phong p: phongs
//        ) {
//            Integer pid = p.getMaPhong();
//            PhongAnh pa = phongAnhRepository.findByMaPhongFirst(p.getMaPhong());
//            thumbAnhs.put(
//                    pid,
//                    pa != null ? pa.maAnh.maAnh : null
//            );
//        }
//
//        List<LoaiPhong> tatCaLoaiPhong = phongService.findAllLoai();
//        Map<Integer, String> anhLoaiPhong = buildAnhLoaiPhong(tatCaLoaiPhong);
//
//        Map<Integer, RoomBookingGuardDTO> bookingGuardByPhong = phongService.buildRoomGuards(phongs);
//
//        ObjectMapper mapper = new ObjectMapper()
//                .registerModule(new JavaTimeModule())
//                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//        Map<Integer, String> khoaLichJsonByPhong = new HashMap<>();
//
//        for (Map.Entry<Integer, RoomBookingGuardDTO> entry : bookingGuardByPhong.entrySet()) {
//            try {
//                khoaLichJsonByPhong.put(
//                        entry.getKey(),
//                        mapper.writeValueAsString(entry.getValue().getDanhSachKhoaLich())
//                );
//            } catch (Exception e) {
//                khoaLichJsonByPhong.put(entry.getKey(), "[]");
//            }
//        }
//        model.addAttribute("khoaLichJsonByPhong", khoaLichJsonByPhong);
//
//        model.addAttribute("loaiPhong", loaiPhong);
//        model.addAttribute("thumbAnhs", thumbAnhs);
//        model.addAttribute("phongs", phongs);
//        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
//        model.addAttribute("loaiPhongs", tatCaLoaiPhong);
//        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
//        model.addAttribute("bookingGuardByPhong", bookingGuardByPhong);
//        model.addAttribute("gioNhanToiDaMacDinh", LocalTime.of(11,0));
//        model.addAttribute("gioTraToiDaMacDinh", LocalTime.of(18,30));
//        return "phong-theo-loai";
//    }

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
            LoaiPhongAnh anhRieng = loaiPhongAnhRepository.findByMaLoaiPhongFirst(lp.getId());
            UUID maAnh = null;
            if (!(anhRieng == null) && anhRieng.getMaAnh() != null) {
                maAnh = anhRieng.getMaAnh().getMaAnh();
            }
            anhLoaiPhong.put(lp.getId(), maAnh != null ? "/media/" + maAnh : "/media/" + ANH_MAC_DINH);
        }
        return anhLoaiPhong;
    }
}