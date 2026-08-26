package su26sd09.su26sd09.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.DatPhongDTO;
import su26sd09.su26sd09.dto.KetQuaHuyDonDTO;
import su26sd09.su26sd09.dto.LoaiPhongDTO;
import su26sd09.su26sd09.dto.NhomYeuCauPhongDTO;
import su26sd09.su26sd09.dto.PhongTheoLoaiDTO;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
import su26sd09.su26sd09.dto.SlotPhongDTO;
import su26sd09.su26sd09.dto.TomTatDto;
import su26sd09.su26sd09.constants.HuyDonConstants;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

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
    @Autowired private HuyDonService huyDonService;
    @Autowired private BookingEmailService bookingEmailService;
    @Autowired private su26sd09.su26sd09.repository.TienNghiPhongRepository tienNghiPhongRepository;
    @Autowired private su26sd09.su26sd09.repository.GiayToRepo giayToRepo;
    @Autowired private CheckInExpirationCacheService checkInExpirationCacheService;
    @Autowired private JanitorCacheService janitorCacheService;

    /**
     * Lay so giay to (CCCD/Ho chieu) cua khach dai dien cho 1 phong cu the
     * (chi_tiet_dat_phong), thu thap luc check-in tai "So do phong" — KHONG
     * phai ma_cccd tren DatPhong (chi dung de doi soat chong gian lan, dai
     * dien cho CA don, khong gan voi tung phong).
     * Uu tien nguoi co coDaiDien = true; neu chua co giay to nao thi tra null.
     */
    private String layCccdDaiDienPhong(int chiTietDatPhongId) {
        List<GiayTo> ds = giayToRepo.findByChiTietDatPhong_Id(chiTietDatPhongId);
        if (ds == null || ds.isEmpty()) return null;
        return ds.stream()
                .filter(gt -> Boolean.TRUE.equals(gt.getCoDaiDien()))
                .map(GiayTo::getSoDinhDanh)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElseGet(() -> ds.stream()
                        .map(GiayTo::getSoDinhDanh)
                        .filter(s -> s != null && !s.isBlank())
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Trả về thông tin xem trước (preview) chính sách hoàn tiền cho modal xác nhận hủy
     * đơn ở phía nhân viên/admin (trang sơ đồ phòng - danh sách đặt phòng), dùng CHUNG
     * đúng công thức hoàn tiền đang áp dụng cho khách hàng (HuyDonService.tinhTyLeHoan).
     * Chỉ đọc dữ liệu, không thay đổi trạng thái đơn/hóa đơn.
     */
    @GetMapping("/dat-phong/{id}/huy-preview")
    @ResponseBody
    public java.util.Map<String, Object> xemTruocHuyDonNhanVien(@PathVariable Integer id) {
        java.util.Map<String, Object> res = new java.util.LinkedHashMap<>();

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            res.put("ok", false);
            res.put("message", "Không tìm thấy đơn đặt phòng.");
            return res;
        }

        boolean daNhanPhong = "Da nhan phong".equals(dp.getTrangThai()) || "Da tra phong".equals(dp.getTrangThai());
        boolean daHuyHoacChoHuy = "Da huy".equals(dp.getTrangThai()) || "Cho huy".equals(dp.getTrangThai());

        if (daHuyHoacChoHuy) {
            res.put("ok", false);
            res.put("message", "Đơn này đã được yêu cầu hủy trước đó.");
            return res;
        }
        if (daNhanPhong) {
            res.put("ok", false);
            res.put("message", "Khách đã nhận phòng, không thể hủy theo chính sách này.");
            return res;
        }
        if (huyDonService.coKhuyenMai(dp)) {
            res.put("ok", false);
            res.put("message", "Đơn có áp dụng khuyến mại nên không thể hủy theo chính sách này.");
            return res;
        }

        HoaDon hd = hoaDonService.findByDatPhongId(id);
        BigDecimal tyLe = huyDonService.tinhTyLeHoan(dp, hd);
        BigDecimal daThanhToan = (hd == null || hd.getDaThanhToan() == null) ? BigDecimal.ZERO : hd.getDaThanhToan();
        BigDecimal soTienHoanDuKien = daThanhToan.multiply(tyLe).setScale(0, RoundingMode.HALF_UP);

        res.put("ok", true);
        res.put("maDatPhong", id);
        res.put("soNgayConLaiDenCheckIn", huyDonService.tinhKhoangCachNgayCheckIn(dp, hd));
        res.put("tyLePhanTram", tyLe.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP));
        res.put("daThanhToan", daThanhToan);
        res.put("soTienHoanDuKien", soTienHoanDuKien);
        res.put("coHoaDon", hd != null);
        return res;
    }

    @GetMapping("/dat-phong")
    public String getAllDatPhong(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        // Sort theo ngayTao desc (đơn vừa tạo lên đầu) thay vì theo id desc,
        // vì id có thể bị lủng do sequence/rollback còn ngayTao phản ánh đúng
        // thứ tự thời gian tạo đơn.
        Sort sort = Sort.by(Sort.Order.desc("ngayTao"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        // Lấy tất cả, lọc bỏ các đơn "Chua thanh toan" rồi mới paging — đảm bảo
        // trang quản lý đơn đặt phòng của nhân viên chỉ hiển thị đơn đã có
        // trạng thái nghiệp vụ hợp lệ.
        List<DatPhong> allFiltered = datPhongService.findAll(sort).stream()
                .filter(dp -> HuyDonConstants.DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT.contains(dp.getTrangThai()))
                .collect(Collectors.toList());

        // Đếm số đơn "Cho xac nhan" / "Da xac nhan" đã quá giờ nhận > 1 ngày
        // để hiển thị toast cảnh báo vàng 10s trên trang quản lý đơn.
        // KHÔNG tự động hủy — nhân viên tự xử lý.
        LocalDateTime nowForToast = LocalDateTime.now();
        LocalDateTime nguongTreToast = nowForToast.minusDays(HuyDonConstants.CANH_BAO_TRE_SONGAY);
        long soDonTreCanhBao = datPhongService.findAll().stream()
                .filter(dp -> HuyDonConstants.DP_TRANG_THAI_CHUA_NHAN_PHONG.contains(dp.getTrangThai()))
                .filter(dp -> dp.getNgaydatPhong() != null && dp.getNgaydatPhong().isBefore(nguongTreToast))
                .count();
        model.addAttribute("soDonTreCanhBao", soDonTreCanhBao);
        int total = allFiltered.size();
        int fromIndex = Math.min((int) pageable.getOffset(), total);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), total);
        List<DatPhong> datPhongs = allFiltered.subList(fromIndex, toIndex);
        Page<DatPhong> datPhongPage = new PageImpl<>(datPhongs, pageable, total);

        Map<Integer, List<  ChiTietDatPhong>> mapCtdp = new HashMap<>();
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
        List<DatPhongDTO> dto = new ArrayList<>();
        for (DatPhong dp: datPhongs) {
            dto.add(new DatPhongDTO(dp, hoaDonService.findByDatPhongId(dp.id) != null
                    ? hoaDonService.findByDatPhongId(dp.id).getTrangThai() : null));
        }
        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("dto", dto);
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
                                  @RequestParam(value = "embed", required = false, defaultValue = "false") Boolean embed,
                                  Model model,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletResponse response) {
        boolean isEmbed = embed != null && embed;
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            // Che do embed (popup trong so-do-phong): tra 404 HTML thay vi redirect,
            // tranh JS parse nham noi dung trang khac.
            if (isEmbed) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("error", "Khong tim thay don dat phong #" + id);
                return "nhan-vien/chi-tiet-dat-phong-notfound";
            }
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }

        model.addAttribute("embed", isEmbed);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("hoaDonDaXuat", hoaDonService.isDaXuat(id));

        List<ChiTietDatPhong> chiTietDatPhongList = chiTietDatPhongService.findByDatPhongId(id);

        // Tinh tong phu phi ngoai gio tu cac phong trong don
        BigDecimal tongPhuThu = BigDecimal.ZERO;
        for (ChiTietDatPhong ct : chiTietDatPhongList) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThu = tongPhuThu.add(ct.getPhuPhi());
            }
        }

        // ===== Tong hoa don "thuc te" dung de tinh Con no (xem tinhTongTienThucTe) =====
        BigDecimal tongTienThucTe = tinhTongTienThucTe(id, hoaDon, chiTietDatPhongList, tongPhuThu);
        BigDecimal daThanhToanHd = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal conNoThucTe = tongTienThucTe.subtract(daThanhToanHd);

        model.addAttribute("tongTienThucTe", tongTienThucTe);
        model.addAttribute("conNoThucTe", conNoThucTe);
        model.addAttribute("daThanhToanHd", daThanhToanHd);

        // ===== Data cho form đổi phòng =====
        // Lấy tất cả phòng active để render danh sách phòng khả dụng trong form đổi phòng.
        List<Phong> tatCaPhong = phongService.findAllPhong();
        // Lấy danh sách phòng mà chính đơn này đang dùng — sẽ bị loại ra khỏi danh sách chọn phòng mới.
        List<Integer> phongDangDungTrongDon = new ArrayList<>();
        for (ChiTietDatPhong ct : chiTietDatPhongList) {
            if (ct != null && ct.getP() != null) {
                phongDangDungTrongDon.add(ct.getP().getMaPhong());
            }
        }
        model.addAttribute("phongAvailableList", tatCaPhong);
        model.addAttribute("phongDangDungTrongDon", phongDangDungTrongDon);
        // CCCD/giay to cua khach dai dien tung phong (thu thap luc check-in), dung de
        // hien thi trong panel "Doi Phong" — KHONG dung datPhong.ma_cccd (do la ma
        // dung de doi soat chong gian lan cho CA don, khong gan voi tung phong).
        Map<Integer, String> cccdPhongMap = new HashMap<>();
        for (ChiTietDatPhong ct : chiTietDatPhongList) {
            if (ct != null) {
                cccdPhongMap.put(ct.getId(), layCccdDaiDienPhong(ct.getId()));
            }
        }
        model.addAttribute("cccdPhongMap", cccdPhongMap);
        model.addAttribute("roomStatusJson", "[" + phongService.buildRoomStatusJson(tatCaPhong) + "]");

        // ===== Du lieu lich cac phong dang dung trong don (cho calendar chon
        // ngay nhan/tra o panel "Thong Tin Luu Tru"): cung cau truc voi
        // bookingsByRoomJson cua so-do-phong (maPhong -> danh sach don dang/da
        // giu cho phong do), de tai su dung y het logic RoomAvailabilityCalendar
        // + kiem tra overlap ben so-do-phong.html. Diem khac duy nhat: khi kiem
        // tra overlap, JS se LOAI TRU chinh don dang xem (id === datPhong.id)
        // khoi danh sach khoa, vi doi ngay cho chinh no khong the coi la "trung
        // lich voi chinh no".
        StringBuilder donBkJson = new StringBuilder("{");
        boolean firstDonRoom = true;
        for (Integer maPhong : phongDangDungTrongDon) {
            List<DatPhong> dsDonPhong = datPhongService.findRecentBookingsForPhong(maPhong);
            if (!firstDonRoom) donBkJson.append(",");
            firstDonRoom = false;
            donBkJson.append("\"").append(maPhong).append("\":[");
            for (int i = 0; i < dsDonPhong.size(); i++) {
                DatPhong d = dsDonPhong.get(i);
                if (i > 0) donBkJson.append(",");
                donBkJson.append("{")
                        .append("\"id\":").append(d.getId()).append(",")
                        .append("\"checkin\":\"").append(d.getNgaydatPhong() != null ? d.getNgaydatPhong() : "").append("\",")
                        .append("\"checkout\":\"").append(d.getNgaytraPhong() != null ? d.getNgaytraPhong() : "").append("\",")
                        .append("\"trangThai\":\"").append(escapeJson(d.getTrangThai())).append("\"")
                        .append("}");
            }
            donBkJson.append("]");
        }
        donBkJson.append("}");
        model.addAttribute("bookingsByRoomJson", donBkJson.toString());
        // Số đêm để hiển thị chênh lệch trong form đổi phòng
        long soDem = Math.max(1, ChronoUnit.DAYS.between(
                datPhong.getNgaydatPhong().toLocalDate(),
                datPhong.getNgaytraPhong().toLocalDate()));
        model.addAttribute("soDem", soDem);
        // Cho phép đổi phòng: trạng thái đơn thuộc nhóm này + hóa đơn chưa xuất PDF
        boolean choPhepDoiPhong = "Yeu cau dat phong".equals(datPhong.getTrangThai())
                || "Cho xac nhan".equals(datPhong.getTrangThai())
                || "Da xac nhan".equals(datPhong.getTrangThai())
                || "Da nhan phong".equals(datPhong.getTrangThai());
        model.addAttribute("choPhepDoiPhong", choPhepDoiPhong);

        model.addAttribute("datPhong", datPhong);
        model.addAttribute("chiTietDatPhongList", chiTietDatPhongList);
        model.addAttribute("chiTietDichVuList", ctdvService.findByDatPhongId(id));
        model.addAttribute("dichVuList", dichVuService.findActiveThuong());
        model.addAttribute("kmJson", buildKhuyenMaiJson());
        model.addAttribute("tongPhuThu", tongPhuThu);

        // ===== Chinh sach no-show: han check-in hieu luc (mac dinh hoac da gia han) =====
        boolean apDungKhachVang = HuyDonConstants.DP_TRANG_THAI_AP_DUNG_KHACH_VANG.contains(datPhong.getTrangThai());
        model.addAttribute("apDungChinhSachKhachVang", apDungKhachVang);
        if (apDungKhachVang) {
            model.addAttribute("hanCheckInHieuLuc", checkInExpirationCacheService.hanHieuLuc(datPhong));
            model.addAttribute("hanCheckInMacDinh", checkInExpirationCacheService.hanMacDinh(datPhong));
            model.addAttribute("daGiaHanCheckIn", checkInExpirationCacheService.coGiaHan(datPhong.getId()));
        }

        return "nhan-vien/chi-tiet-dat-phong";
    }

    /**
     * Nhan vien gia han moc check-in cua 1 don (chinh sach no-show), thuong dung khi
     * khach goi dien xin den nhan phong tre hon moc mac dinh (12:00 ngay hom sau
     * ngay_nhan_phong). Ghi vao cache file (CheckInExpirationCacheService), scheduler
     * xu ly Khach vang se doc lai moc nay o lan quet tiep theo.
     */
    @PostMapping("/dat-phong/chi-tiet/{id}/gia-han-checkin")
    public String giaHanCheckIn(@PathVariable Integer id,
                                @RequestParam("hanCheckInMoi") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime hanCheckInMoi,
                                RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }
        if (!HuyDonConstants.DP_TRANG_THAI_AP_DUNG_KHACH_VANG.contains(datPhong.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Chi co the gia han check-in cho don dang cho check-in.");
            return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        if (hanCheckInMoi.isBefore(LocalDateTime.now())) {
            redirectAttributes.addFlashAttribute("error", "Han check-in moi phai o trong tuong lai.");
            return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }

        checkInExpirationCacheService.giaHan(id, hanCheckInMoi);
        redirectAttributes.addFlashAttribute("thanhCongCapNhat",
                "Da gia han check-in cho don #" + id + " den " + hanCheckInMoi);
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    /**
     * Tinh tong hoa don "thuc te" (dung de tinh Con no / gioi han so tien duoc thu)
     * cho mot don dat phong.
     *
     * hoaDon.tongTien duoc luu trong DB tu lan "Cap nhat" gan nhat va co the CHUA
     * bao gom phu phi gio nhan/tra moi phat sinh (vd: doi gio nhan/tra, check-in
     * tre...) neu nhan vien chua bam "Luu Thay Doi" lai. Neu cu dung thang
     * hoaDon.tongTien de tinh "Con no" thi so lieu se bi lech voi cot "Tom Tat
     * Chi Phi" (cot nay luon tinh lai phu phi theo gio nhan/tra hien tai), khien
     * nhan vien thay "da thu du" nhung backend van tu choi vi thieu tien.
     *
     * Ham nay tinh lai tong tien "ky vong" (tien phong + tien dich vu + VAT -
     * giam gia + phu phi gio nhan/tra) va lay gia tri LON HON giua no va
     * hoaDon.tongTien, de tranh cong don 2 lan phu phi trong truong hop
     * hoaDon.tongTien da duoc cap nhat co san.
     */
    private BigDecimal tinhTongTienThucTe(Integer datPhongId, HoaDon hoaDon,
                                          List<ChiTietDatPhong> chiTietDatPhongList,
                                          BigDecimal tongPhuThu) {
        BigDecimal tienPhongGoc = chiTietDatPhongList.stream()
                .map(ChiTietDatPhong::getGiaKhiDat)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Chi_tiet_dich_vu> dichVuListGoc = ctdvService.findByDatPhongId(datPhongId);
        BigDecimal tienDichVuGoc = dichVuListGoc.stream()
                .map(Chi_tiet_dich_vu::getDonGia)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienGiamGoc = (hoaDon != null && hoaDon.tienGiam != null) ? hoaDon.tienGiam : BigDecimal.ZERO;
        // KM ap tren TONG (phong + dich vu), VAT 10% tinh tren gia SAU GIAM
        BigDecimal tongTruocGiam = tienPhongGoc.add(tienDichVuGoc);
        BigDecimal tongSauGiam = tongTruocGiam.subtract(tienGiamGoc);
        BigDecimal vatGoc = tongSauGiam
                .multiply(new BigDecimal("0.10"))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal tongTienKyVong = tongSauGiam.add(vatGoc)
                .add(tongPhuThu == null ? BigDecimal.ZERO : tongPhuThu);

        BigDecimal tongTienDaLuu = (hoaDon != null && hoaDon.getTongTien() != null)
                ? hoaDon.getTongTien() : BigDecimal.ZERO;

        return tongTienDaLuu.max(tongTienKyVong);
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
                                        @RequestParam(value = "phatSinhTen", required = false) List<String> phatSinhTenList,
                                        @RequestParam(value = "phatSinhDonGia", required = false) List<String> phatSinhDonGiaList,
                                        @RequestParam(value = "phatSinhSoLuong", required = false) List<String> phatSinhSoLuongList,
                                        @RequestParam(value = "phatSinhNgay", required = false) List<String> phatSinhNgayList,
                                        @RequestParam(value = "phatSinhGhiChu", required = false) List<String> phatSinhGhiChuList,
                                        @RequestParam Map<String, String> allParams,
                                        RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the chinh sua.");
            return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        List<String> loiCapNhat = validateChiTietDatPhong(ngayNhan, ngayTra, nguoiLon, treEm, dichVuIds,
                phatSinhTenList, phatSinhDonGiaList, phatSinhGhiChuList, allParams);
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
        String loiKm = khuyenMaiService.validateGanKhuyenMai(datPhong, km);
        if (loiKm != null) {
            redirectAttributes.addFlashAttribute("error", loiKm);
            return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        datPhong.setKm(km);
        datPhongService.save(datPhong);

        capNhatGiaPhongTheoNgay(id, ngayNhan, ngayTra);
        capNhatDichVuDatPhong(datPhong, dichVuIds,
                phatSinhTenList, phatSinhDonGiaList, phatSinhSoLuongList, phatSinhNgayList, phatSinhGhiChuList,
                allParams);
        capNhatHoaDonNeuCo(id, tongTienPhong, tongTienDichVu, tongTienGiam, tongTienVat, tongCong, km);

        redirectAttributes.addFlashAttribute("thanhCongCapNhat", "Cap nhat chi tiet dat phong #" + id + " thanh cong.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    private void capNhatGiaPhongTheoNgay(Integer maDatPhong, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        long soDem = Math.max(1, ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate()));
        for (ChiTietDatPhong chiTiet : chiTietDatPhongService.findByDatPhongId(maDatPhong)) {
            BigDecimal giaMoiDem = chiTiet.getGiaMoiDem() != null ? chiTiet.getGiaMoiDem() : BigDecimal.ZERO;
            int maPhong = chiTiet.getP() != null ? chiTiet.getP().getMaPhong() : 0;
            BigDecimal phuPhiNgoaiGio = maPhong > 0
                    ? phongService.calculateExtraFeeFor(maPhong, ngayNhan, ngayTra)
                    : BigDecimal.ZERO;
            chiTiet.setGiaKhiDat(giaMoiDem.multiply(BigDecimal.valueOf(soDem)));
            chiTiet.setPhuPhi(phuPhiNgoaiGio);
            chiTietDatPhongService.save(chiTiet);
        }
    }

    private List<String> validateChiTietDatPhong(LocalDateTime ngayNhan, LocalDateTime ngayTra,
                                                 Integer nguoiLon, Integer treEm,
                                                 List<Integer> dichVuIds, Map<String, String> allParams) {
        return validateChiTietDatPhong(ngayNhan, ngayTra, nguoiLon, treEm, dichVuIds,
                null, null, null, allParams);
    }

    /** Phiên bản mở rộng: validate cả dịch vụ thường + dịch vụ phát sinh. */
    private List<String> validateChiTietDatPhong(LocalDateTime ngayNhan, LocalDateTime ngayTra,
                                                 Integer nguoiLon, Integer treEm,
                                                 List<Integer> dichVuIds,
                                                 List<String> phatSinhTenList,
                                                 List<String> phatSinhDonGiaList,
                                                 List<String> phatSinhGhiChuList,
                                                 Map<String, String> allParams) {
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
        // Validate dịch vụ phát sinh: nếu có tên thì bắt buộc đơn giá > 0 và ghi chú không rỗng
        if (phatSinhTenList != null && phatSinhDonGiaList != null) {
            int soPhatSinh = phatSinhTenList.size();
            for (int i = 0; i < soPhatSinh; i++) {
                String ten = phatSinhTenList.get(i);
                if (ten == null || ten.isBlank()) continue;
                String donGiaStr = i < phatSinhDonGiaList.size() ? phatSinhDonGiaList.get(i) : null;
                String ghiChu = (phatSinhGhiChuList != null && i < phatSinhGhiChuList.size())
                        ? phatSinhGhiChuList.get(i) : null;
                BigDecimal donGia = null;
                try {
                    donGia = (donGiaStr == null || donGiaStr.isBlank()) ? null : new BigDecimal(donGiaStr);
                } catch (NumberFormatException ex) {
                    donGia = null;
                }
                if (donGia == null || donGia.signum() <= 0) {
                    errors.add("Dich vu phat sinh '" + ten + "' can co don gia hop le (>0).");
                }
                if (ghiChu == null || ghiChu.isBlank()) {
                    errors.add("Dich vu phat sinh '" + ten + "' can co ghi chu / ly do cu the.");
                }
            }
        }
        return errors;
    }

    private void capNhatDichVuDatPhong(DatPhong datPhong, List<Integer> dichVuIds, Map<String, String> allParams) {
        capNhatDichVuDatPhong(datPhong, dichVuIds, null, null, null, null, null, allParams);
    }

    /** Phiên bản mở rộng: lưu cả dịch vụ thường + dịch vụ phát sinh.
     *  - Dịch vụ thường: dùng giá cố định từ catalog dich_vu.
     *  - Dịch vụ phát sinh: tự tạo/cập nhật 1 row master Dich_vu (loaiDichVu=PHAT_SINH) theo (tên + đơn giá)
     *    rồi gắn vào chi_tiet_dich_vu. Trùng tên + đơn giá sẽ dùng lại cùng 1 row master để thống kê "Lượt sử dụng" chính xác. */
    private void capNhatDichVuDatPhong(DatPhong datPhong, List<Integer> dichVuIds,
                                       List<String> phatSinhTenList,
                                       List<String> phatSinhDonGiaList,
                                       List<String> phatSinhSoLuongList,
                                       List<String> phatSinhNgayList,
                                       List<String> phatSinhGhiChuList,
                                       Map<String, String> allParams) {
        ctdvService.deleteByDatPhongId(datPhong.getId());

        // ===== 1) Dịch vụ THƯỜNG (catalog có sẵn) =====
        if (dichVuIds != null) {
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

        // ===== 2) Dịch vụ PHÁT SINH (nhập tay, master tự tạo/cập nhật theo tên + đơn giá) =====
        if (phatSinhTenList == null || phatSinhTenList.isEmpty()) {
            return;
        }
        int soPhatSinh = phatSinhTenList.size();
        for (int i = 0; i < soPhatSinh; i++) {
            String ten = phatSinhTenList.get(i);
            if (ten == null || ten.isBlank()) continue;

            String donGiaStr = (phatSinhDonGiaList != null && i < phatSinhDonGiaList.size())
                    ? phatSinhDonGiaList.get(i) : null;
            String soLuongStr = (phatSinhSoLuongList != null && i < phatSinhSoLuongList.size())
                    ? phatSinhSoLuongList.get(i) : null;
            String ngayStr = (phatSinhNgayList != null && i < phatSinhNgayList.size())
                    ? phatSinhNgayList.get(i) : null;
            String ghiChu = (phatSinhGhiChuList != null && i < phatSinhGhiChuList.size())
                    ? phatSinhGhiChuList.get(i) : null;

            BigDecimal donGia;
            try {
                donGia = (donGiaStr == null || donGiaStr.isBlank()) ? null : new BigDecimal(donGiaStr);
            } catch (NumberFormatException ex) {
                continue; // validate đã chặn trước, an toàn thì skip
            }
            if (donGia == null || donGia.signum() <= 0) continue;

            int soLuong = parseIntOrDefault(soLuongStr, 1);
            LocalDateTime ngaySuDung = parseDateTimeOrNow(ngayStr);

            // Tìm dịch vụ phát sinh đã có (cùng tên + cùng đơn giá) — nếu có thì dùng lại
            Dich_vu dichVuPhatSinh = dichVuService.findPhatSinhTheoTenVaGia(ten, donGia)
                    .orElseGet(() -> dichVuService.taoDichVuPhatSinhMoi(ten, donGia));

            Chi_tiet_dich_vu chiTiet = new Chi_tiet_dich_vu();
            chiTiet.setDatPhong(datPhong);
            chiTiet.setDv(dichVuPhatSinh);
            chiTiet.setSoluong(soLuong);
            chiTiet.setNgay_su_dung(ngaySuDung);
            chiTiet.setDonGia(donGia.multiply(BigDecimal.valueOf(soLuong)));
            chiTiet.setGhichu(ghiChu); // ghi chú lý do cụ thể lưu ở line item
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
        // Dùng helper để tự động đồng bộ trangThai:
        // - "Da thanh toan" nếu đã trả đủ.
        // - "Cho thanh toan" nếu tổng tiền vừa tăng lên vượt quá daThanhToan.
        // - Không động vào "Da xuat".
        hoaDonService.saveWithPaymentStatusCheck(hoaDon);
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
            @RequestParam(required = false) String maTraCuu,
            Model model) {

        List<DatPhong> datPhongs = datPhongService.search(
                        maDatPhong, tenKhach, null, ma_cccd,
                        ngayNhanTu, ngayNhanDen, ngayTraTu, ngayTraDen,
                        soNguoiLon, soTreEm, trangThai, yeuCauThem,
                        ngayTaoTu, ngayTaoDen, ngayCapNhatTu, ngayCapNhatDen,
                        maTraCuu
                ).stream()
                // Ẩn các đơn "Chua thanh toan" — chỉ hiển thị đơn đã có trạng thái
                // nghiệp vụ hợp lệ trên trang quản lý đơn đặt phòng nhân viên.
                // Ngoại lệ: khi tra cứu chính xác theo "ma tra cuu", phải trả về cả đơn
                // "Chua thanh toan" (đơn của khách vãng lai đặt từ giỏ nhưng chưa thanh toán).
                .filter(dp ->
                        (maTraCuu != null && !maTraCuu.trim().isEmpty())
                                || HuyDonConstants.DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT.contains(dp.getTrangThai()))
                .collect(Collectors.toList());

        // Đếm số đơn trễ > 1 ngày (chưa nhận phòng) để hiện toast cảnh báo.
        // Tính trên TOÀN BỘ đơn (không phụ thuộc filter search) vì đây là cảnh báo
        // tổng quan — nhân viên cần biết ngay cả khi đang lọc đơn khác.
        LocalDateTime nowForToastSearch = LocalDateTime.now();
        LocalDateTime nguongTreToastSearch = nowForToastSearch.minusDays(HuyDonConstants.CANH_BAO_TRE_SONGAY);
        long soDonTreCanhBao = datPhongService.findAll().stream()
                .filter(dp -> HuyDonConstants.DP_TRANG_THAI_CHUA_NHAN_PHONG.contains(dp.getTrangThai()))
                .filter(dp -> dp.getNgaydatPhong() != null && dp.getNgaydatPhong().isBefore(nguongTreToastSearch))
                .count();
        model.addAttribute("soDonTreCanhBao", soDonTreCanhBao);

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
        model.addAttribute("maTraCuu", maTraCuu);

        return "nhan-vien/dat-phong-list";
    }

    @PostMapping("/dat-phong/update-trang-thai")
    public String updateTrangThai(
            @RequestParam Integer id,
            @RequestParam String trangThai,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime gioKhachTaiQuay,
            @RequestParam(required = false, defaultValue = "0") BigDecimal phuPhiTre,
            // Them dich vu (chon tu dropdown trong form check-in)
            @RequestParam(required = false) Integer maDichVuThem,
            @RequestParam(required = false, defaultValue = "1") Integer soLuongDichVuThem,
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

        // Forced redirect sang trang check-out khi chuyen sang "Da tra phong"
        // de nhan vien phai chot tien va giai phong phong theo dung quy trinh.
        if ("Da tra phong".equals(trangThai)) {
            return "redirect:/nhan-su/checkout/" + id;
        }

        dp.setTrangThai(trangThai);
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        // Đồng bộ trạng thái TẤT CẢ phòng trong đơn theo trạng thái đơn mới
        List<ChiTietDatPhong> ctdpList = chiTietDatPhongService.findByDatPhongId(id);

        if ("Da nhan phong".equals(trangThai)) {
            for (ChiTietDatPhong ct : ctdpList) {
                Phong p = ct.getP();
                if (p == null) continue;
                p.setTrangThai("Dang su dung");
                phongService.save1(p);
            }

            // Cộng phụ phí check-in trễ (nếu có) vào ĐÚNG 1 ChiTietDatPhong
            // (phòng đầu tiên trong danh sách). Không cộng dồn / không chia đều
            // — phụ phí chỉ tính 1 lần cho cả đơn, theo yêu cầu của user.
            if (phuPhiTre != null && phuPhiTre.signum() > 0 && !ctdpList.isEmpty()) {
                ChiTietDatPhong first = ctdpList.get(0);
                BigDecimal current = first.getPhuPhi() == null ? BigDecimal.ZERO : first.getPhuPhi();
                first.setPhuPhi(current.add(phuPhiTre));
                chiTietDatPhongService.save(first);
            }
        }

        if ("Da tra phong".equals(trangThai)) {
            for (ChiTietDatPhong ct : ctdpList) {
                Phong p = ct.getP();
                if (p == null) continue;
                if (datPhongService.hasBookingNotCheckout(p.getMaPhong(), dp.getId())) {
                    p.setTrangThai("Da dat truoc");
                } else {
                    p.setTrangThai("Trong");
                }
                phongService.save1(p);
            }
        }

        // Them dich vu (neu co) — dropdown "Them dich vu" trong form check-in.
        // Ap dung cho moi trang thai (khong chi "Da nhan phong") de nhan vien
        // co the bo sung dich vu phat sinh bat ky khi nao can.
        String themDichVuMsg = null;
        if (maDichVuThem != null && maDichVuThem > 0) {
            Dich_vu dichVu = dichVuService.findById(maDichVuThem);
            if (dichVu != null) {
                int sl = (soLuongDichVuThem == null || soLuongDichVuThem <= 0) ? 1 : soLuongDichVuThem;
                Chi_tiet_dich_vu chiTiet = new Chi_tiet_dich_vu();
                chiTiet.setDatPhong(dp);
                chiTiet.setDv(dichVu);
                chiTiet.setSoluong(sl);
                chiTiet.setNgay_su_dung(LocalDateTime.now());
                chiTiet.setDonGia(dichVu.getGia().multiply(BigDecimal.valueOf(sl)));
                ctdvService.save(chiTiet);
                themDichVuMsg = "Đã thêm " + sl + " x " + dichVu.getTen_dich_vu() + " vào đơn.";
            } else {
                themDichVuMsg = "Không tìm thấy dịch vụ #" + maDichVuThem + " — bỏ qua.";
            }
        }

        if (phuPhiTre != null && phuPhiTre.signum() > 0 && themDichVuMsg != null) {
            redirectAttributes.addFlashAttribute("success",
                    "Cập nhật đơn #" + id + " thành công. Phụ phí check-in trễ: "
                            + phuPhiTre.toPlainString() + " VND. " + themDichVuMsg);
        } else if (phuPhiTre != null && phuPhiTre.signum() > 0) {
            redirectAttributes.addFlashAttribute("success",
                    "Cập nhật đơn #" + id + " thành công. Phụ phí check-in trễ: "
                            + phuPhiTre.toPlainString() + " VND.");
        } else if (themDichVuMsg != null) {
            redirectAttributes.addFlashAttribute("success",
                    "Cập nhật trạng thái đơn #" + id + " thành công. " + themDichVuMsg);
        } else {
            redirectAttributes.addFlashAttribute("success", "Cập nhật trạng thái đơn #" + id + " thành công.");
        }
        return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
    }

    /* ===================== YEU CAU DAT PHONG (KHACH ONLINE) ===================== */

    /**
     * Trang quan ly cac yeu cau dat phong moi tu khach online (trangThai = "Yeu cau dat phong").
     * Nhan vien vao day de:
     *   - Xem danh sach yeu cau cho xac nhan
     *   - Click vao tung yeu cau de xem chi tiet, check CCCD, check suc chua
     *   - Bam "Xac nhan yeu cau" de chuyen sang trangThai = "Cho xac nhan" (don di vao luong thanh toan)
     *
     * Trang tu dong reload bang polling 5 giay (JS o template yeu-cau-dat-phong.html).
     */
    @GetMapping("/yeu-cau-dat-phong")
    public String quanLyYeuCauDatPhong(Model model) {
        // Lay tat ca yeu cau, sap xep theo ngayTao giam dan (moi nhat len dau)
        List<DatPhong> dsYeuCau = datPhongService.findAll().stream()
                .filter(dp -> su26sd09.su26sd09.constants.HuyDonConstants.DP_YEU_CAU_DAT_PHONG
                        .equals(dp.getTrangThai()))
                .sorted(comparing(DatPhong::getNgayTao,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .collect(Collectors.toList());

        // Build danh sach chi tiet kem thong tin phong + thoi gian cho
        List<Map<String, Object>> yeuCauList = new ArrayList<>();
        for (DatPhong dp : dsYeuCau) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("datPhong", dp);
            item.put("chiTiet", chiTietDatPhongService.findByDatPhongId(dp.getId()));
            // Tinh so giay da cho de hien thi "X phut truoc" / "X gio truoc"
            if (dp.getNgayTao() != null) {
                long secondsWaiting = java.time.temporal.ChronoUnit.SECONDS
                        .between(dp.getNgayTao(), LocalDateTime.now());
                item.put("secondsWaiting", secondsWaiting);
            }
            yeuCauList.add(item);
        }

        model.addAttribute("yeuCauList", yeuCauList);
        model.addAttribute("totalCount", dsYeuCau.size());
        return "nhan-vien/yeu-cau-dat-phong";
    }

    /**
     * API dem so luong yeu cau dat phong dang cho xu ly.
     * Su dung cho badge sidebar + auto-reload trang yeu-cau-dat-phong.
     * Polling moi 5 giay tu JS.
     */
    @GetMapping("/api/yeu-cau/dat-phong/count")
    @ResponseBody
    public java.util.Map<String, Object> countYeuCauDatPhong() {
        long count = datPhongService.findAll().stream()
                .filter(dp -> su26sd09.su26sd09.constants.HuyDonConstants.DP_YEU_CAU_DAT_PHONG
                        .equals(dp.getTrangThai()))
                .count();
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("count", count);
        resp.put("timestamp", System.currentTimeMillis());
        return resp;
    }

    /**
     * Trang chi tiet yeu cau dat phong.
     * Hien thi day du thong tin khach + CCCD + cac phong da he thong tu chon + canh bao suc chua.
     * Nhan vien co the:
     *   - Bam "Xac nhan yeu cau" -> POST /xac-nhan-yeu-cau/{id}
     *   - Doi phong (neu muon) qua form doi-phong (endpoint da co)
     *   - Huy yeu cau neu khong the dap ung
     */
    @GetMapping("/yeu-cau-dat-phong/chi-tiet/{id}")
    public String chiTietYeuCau(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay yeu cau #" + id);
            return "redirect:/nhan-su/yeu-cau-dat-phong";
        }

        // Tinh canh bao suc chua: neu tong nguoi > tong suc chua cua cac phong da gan
        List<ChiTietDatPhong> chiTietList = chiTietDatPhongService.findByDatPhongId(id);
        int tongSucChua = chiTietList.stream()
                .filter(ct -> ct.getP() != null && ct.getP().getLoaiPhong() != null)
                .mapToInt(ct -> ct.getP().getLoaiPhong().getSucChuaToiDa())
                .sum();
        int tongNguoi = (datPhong.getSonguoiLon() != 0 ? datPhong.getSonguoiLon() : 0)
                + (datPhong.getSotreEm() != 0 ? datPhong.getSotreEm() : 0);
        boolean canhBaoSucChua = tongNguoi > tongSucChua;

        // Lay thong tin dich vu + hoa don + lich su thanh toan
        List<Chi_tiet_dich_vu> dichVuList = ctdvService.findByDatPhongId(id);
        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        List<ThanhToan> lichSuThanhToan = new ArrayList<>();
        if (hoaDon != null) {
            lichSuThanhToan = thanhToanService.findAllByHoaDonId(hoaDon.getId());
        }
        // KM + gia KM
        KhuyenMai khuyenMai = datPhong.getKm();

        // Lay tat ca phong (de form doi phong hoat dong neu muon)
        List<Phong> tatCaPhong = phongService.findAllPhong();

        // Build booking guards cho form doi phong (gioi han theo khoang ngay cua don)
        Map<Integer, RoomBookingGuardDTO> bookingGuardByPhong = phongService.buildRoomGuards(tatCaPhong);
        // JSON cho JS doi phong (tranh overlap khoang ngay voi don khac)
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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

        List<Integer> phongDangDungTrongDon = new ArrayList<>();
        for (ChiTietDatPhong ct : chiTietList) {
            if (ct != null && ct.getP() != null) {
                phongDangDungTrongDon.add(ct.getP().getMaPhong());
            }
        }

        // Thoi gian da cho (giay)
        long secondsWaiting = 0;
        if (datPhong.getNgayTao() != null) {
            secondsWaiting = java.time.temporal.ChronoUnit.SECONDS
                    .between(datPhong.getNgayTao(), LocalDateTime.now());
        }

        model.addAttribute("datPhong", datPhong);
        model.addAttribute("chiTietDatPhongList", chiTietList);
        model.addAttribute("chiTietDichVuList", dichVuList);
        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("lichSuThanhToan", lichSuThanhToan);
        model.addAttribute("khuyenMai", khuyenMai);
        model.addAttribute("tongSucChua", tongSucChua);
        model.addAttribute("tongNguoi", tongNguoi);
        model.addAttribute("canhBaoSucChua", canhBaoSucChua);
        model.addAttribute("phongAvailableList", tatCaPhong);
        model.addAttribute("phongDangDungTrongDon", phongDangDungTrongDon);
        model.addAttribute("bookingGuardByPhong", bookingGuardByPhong);
        model.addAttribute("khoaLichJsonByPhong", khoaLichJsonByPhong);
        model.addAttribute("roomStatusJson", "[" + phongService.buildRoomStatusJson(tatCaPhong) + "]");
        model.addAttribute("dichVuList", dichVuService.findActiveThuong());
        model.addAttribute("kmJson", buildKhuyenMaiJson());
        model.addAttribute("secondsWaiting", secondsWaiting);

        return "nhan-vien/yeu-cau-chi-tiet";
    }

    /**
     * Nhan vien xac nhan yeu cau dat phong tu khach online.
     * Doi trangThai tu "Yeu cau dat phong" -> "Cho xac nhan" (don di vao luong thanh toan).
     * Validate CCCD phai co (neu khong co thi khong cho xac nhan).
     */
    @PostMapping("/dat-phong/xac-nhan-yeu-cau/{id}")
    public String xacNhanYeuCau(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don #" + id);
            return "redirect:/nhan-su/yeu-cau-dat-phong";
        }
        if (!su26sd09.su26sd09.constants.HuyDonConstants.DP_YEU_CAU_DAT_PHONG.equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error",
                    "Don #" + id + " khong o trang thai yeu cau (hien tai: " + dp.getTrangThai() + ").");
            return "redirect:/nhan-su/yeu-cau-dat-phong";
        }
        if (dp.getMa_cccd() == null || dp.getMa_cccd().isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Don #" + id + " chua co CCCD, khong the xac nhan. Vui long them CCCD truoc.");
            return "redirect:/nhan-su/yeu-cau-dat-phong/chi-tiet/" + id;
        }

        // Model MOI: NV xac nhan yeu cau chi dua don vao luong thanh toan
        // ("Cho xac nhan"). Don CHI chuyen sang "Da xac nhan" tu dong khi
        // hoa don duoc thanh toan DU 100% (xem HoaDonService#saveWithPaymentStatusCheck).
        // Khong con buoc NV bam xac nhan thu cong de "chot" don nhu model cu.
        dp.setTrangThai(su26sd09.su26sd09.constants.HuyDonConstants.DP_CHO_XAC_NHAN);
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        // Gui email cho khach biet yeu cau da duoc NV xac nhan (async)
        try {
            bookingEmailService.guiEmailXacNhanYeuCau(id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        redirectAttributes.addFlashAttribute("success",
                "Da xac nhan yeu cau dat phong #" + id + ". Don da chuyen sang trang thai 'Cho xac nhan'.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    /**
     * Huy yeu cau dat phong (nhan vien tu choi khach online).
     * Set trangThai = "Da huy" (gia lap "cancel" vi chua qua luong thanh toan).
     */
    @PostMapping("/dat-phong/huy-yeu-cau/{id}")
    public String huyYeuCau(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don #" + id);
            return "redirect:/nhan-su/yeu-cau-dat-phong";
        }
        if (!su26sd09.su26sd09.constants.HuyDonConstants.DP_YEU_CAU_DAT_PHONG.equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error",
                    "Don #" + id + " khong o trang thai yeu cau.");
            return "redirect:/nhan-su/yeu-cau-dat-phong";
        }

        // Giai phong phong da gan (neu co) de tra ve trang thai "Trong"
        List<ChiTietDatPhong> ctdpList = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ct : ctdpList) {
            if (ct != null && ct.getP() != null) {
                Phong p = ct.getP();
                p.setTrangThai("Trong");
                phongService.save1(p);
            }
        }

        dp.setTrangThai("Da huy");
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        redirectAttributes.addFlashAttribute("success",
                "Da huy yeu cau dat phong #" + id + " va giai phong phong.");
        return "redirect:/nhan-su/yeu-cau-dat-phong";
    }

    @PostMapping("/dat-phong/cancel")
    public String cancelDatPhong(
            @RequestParam("id") Integer id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "phuongThucHoan", required = false) String phuongThucHoan,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        // Bước 1: dùng chung luồng với admin: tạo yêu cầu hủy + set "Cho xu ly"
        KetQuaHuyDonDTO ketQua = huyDonService.huyDon(id);

        if (ketQua.isCanHoanTien()) {
            // Bước 2: NV chọn sẵn phương thức hoàn (Tien Mat / Chuyen Khoan) ngay trong modal
            // Huỷ phòng => tự động xác nhận hoàn tiền luôn (tái sử dụng HuyDonService.xacNhanHoanTien),
            // không cần thao tác thủ công thêm ở trang Hoàn tiền nữa.
            String pt = (phuongThucHoan == null || phuongThucHoan.isBlank())
                    ? HuyDonConstants.PT_TIEN_MAT
                    : phuongThucHoan.trim();
            NhanSu nvXuLy = auth == null ? null : nhanVienService.FindByemail(auth.getName());

            huyDonService.xacNhanHoanTien(
                    ketQua.getHoaDonId(),
                    pt,
                    null,   // maGiaoDichHoan: không có (tiền mặt/chuyển khoản thủ công, không qua VNPay)
                    null,   // stkNhanHoan
                    null,   // tenNganHang
                    "Tu dong xac nhan hoan tien khi Huy phong (nhan vien)",
                    null,   // soTienHoanNhap: null -> dùng đúng số tiền hệ thống đã tính theo rule
                    nvXuLy);

            redirectAttributes.addFlashAttribute("thongBao",
                    ketQua.getThongBao() + " Da tu dong xac nhan hoan tien (" + pt + ").");
        } else {
            redirectAttributes.addFlashAttribute("thongBao", ketQua.getThongBao());
        }

        return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
    }

    /**
     * Trang "Đơn đặt phòng" — sơ đồ phòng dạng lưới, chia theo tab tầng bên cạnh.
     * Đây là bản dựng lại giao diện (theo tham chiếu sơ đồ phòng), CHỈ tập trung
     * vào phần hiển thị trạng thái phòng hiện tại, không phụ thuộc vào các trạng
     * thái cũ trong entity Phong (Trong/Dang su dung/Da dat truoc...).
     * 4 trạng thái hiển thị: Trống, Đang sử dụng, Đang dọn, Bảo trì.
     */
    /**
     * Nhận phòng (check-in) trực tiếp từ Sơ đồ phòng.
     * - Chỉ cho phép khi phòng đang ở trạng thái hiển thị "Trống".
     * - Áp dụng chính sách nhận phòng sớm: nếu vi phạm và CHƯA xác nhận (xacNhan=false),
     *   CHỈ trả về thông tin phụ thu để hiển thị modal cảnh báo — KHÔNG lưu chi_tiet_dich_vu
     *   phụ thu, KHÔNG cập nhật trạng thái phòng/đơn (daApDung=false).
     * - Nếu không vi phạm, hoặc vi phạm nhưng đã xác nhận (xacNhan=true, người dùng bấm
     *   "Đã hiểu"): tự tạo 1 chi_tiet_dich_vu phụ thu (ghi_chu = "check-in som") gắn vào
     *   đơn đặt phòng (nếu vi phạm) và chuyển trạng thái phòng sang "Đang sử dụng"
     *   (daApDung=true).
     */
    /**
     * Danh sach phong (moi phong = 1 section) cua 1 don dat phong, dung de ve
     * modal "Them giay to" truoc khi thuc su nhan phong o So do phong.
     */
    @GetMapping("/so-do-phong/check-in/{maDatPhong}/giay-to-info")
    @ResponseBody
    public Map<String, Object> giayToInfoTuSoDoPhong(@PathVariable int maDatPhong) {
        Map<String, Object> result = new LinkedHashMap<>();
        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy đơn đặt phòng #" + maDatPhong);
            return result;
        }

        List<ChiTietDatPhong> dsChiTiet = chiTietDatPhongService.findByDatPhongId(maDatPhong);
        List<Map<String, Object>> phongs = new ArrayList<>();
        for (ChiTietDatPhong ct : dsChiTiet) {
            Phong p = ct.getP();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chiTietId", ct.getId());
            item.put("soPhong", p == null ? "" : p.getSoPhong());
            item.put("tenLoai", (p == null || p.getLoaiPhong() == null) ? "" : p.getLoaiPhong().getTenLoai());
            item.put("sucChua", (p == null || p.getLoaiPhong() == null) ? 0 : p.getLoaiPhong().getSucChuaToiDa());

            List<Map<String, Object>> giayToList = new ArrayList<>();
            for (GiayTo gt : giayToRepo.findByChiTietDatPhong_Id(ct.getId())) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("id", gt.getId());
                g.put("coDaiDien", gt.getCoDaiDien());
                g.put("loaiGiayTo", gt.getLoaiGiayTo());
                g.put("hoTen", gt.getHoTen());
                g.put("soDinhDanh", gt.getSoDinhDanh());
                g.put("ngaySinh", gt.getNgaySinh());
                g.put("gioiTinh", gt.getGioiTinh());
                g.put("quocTich", gt.getQuocTich());
                g.put("queQuan", gt.getQueQuan());
                g.put("noiThuongTru", gt.getNoiThuongTru());
                g.put("noiCuTru", gt.getNoiCuTru());
                g.put("noiTamTru", gt.getNoiTamTru());
                g.put("noiLuuTru", gt.getNoiLuuTru());
                g.put("giaTriDen", gt.getGiaTriDen());
                g.put("ngayCap", gt.getNgayCap());
                g.put("quocGiaCapPhat", gt.getQuocGiaCapPhat());
                giayToList.add(g);
            }
            item.put("giayTo", giayToList);
            phongs.add(item);
        }

        result.put("ok", true);
        result.put("soNguoiLon", dp.getSonguoiLon());
        result.put("phongs", phongs);
        return result;
    }

    /**
     * Luu (ghi de) danh sach giay to (CCCD / Ho chieu) cho cac phong cua 1 don
     * dat phong. Duoc goi tu modal "Them giay to" TRUOC khi bam nut xac nhan
     * nhan phong that su (checkInTuSoDoPhong).
     */
    @PostMapping("/so-do-phong/check-in/{maDatPhong}/giay-to")
    @ResponseBody
    public Map<String, Object> luuGiayToTuSoDoPhong(@PathVariable int maDatPhong,
                                                    @RequestParam(name = "data") String dataJson) {
        Map<String, Object> result = new LinkedHashMap<>();
        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy đơn đặt phòng #" + maDatPhong);
            return result;
        }

        List<su26sd09.su26sd09.dto.GiayToCheckInDTO> danhSach;
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            danhSach = mapper.readValue(dataJson,
                    mapper.getTypeFactory().constructCollectionType(List.class, su26sd09.su26sd09.dto.GiayToCheckInDTO.class));
        } catch (Exception e) {
            result.put("ok", false);
            result.put("loi", "Dữ liệu giấy tờ không hợp lệ.");
            return result;
        }

        // Cac ma_chi_tiet hop le thuoc dung don nay (tranh ghi nham sang don khac).
        List<Integer> chiTietHopLe = chiTietDatPhongService.findByDatPhongId(maDatPhong)
                .stream().map(ChiTietDatPhong::getId).collect(Collectors.toList());

        // Day la API "ghi de": voi tung phong (ma_chi_tiet) co mat trong danh sach
        // gui len, xoa het giay to CU cua phong do truoc khi luu lai danh sach MOI.
        // Nho vay API co the goi lai nhieu lan an toan (vd: nhan vien sua/luu lai
        // giay to cua 1 phong tu khung sdp-giayto-room o sidebar "Xem don hien tai")
        // ma khong bi nhan doi ban ghi cu.
        Set<Integer> chiTietDaGuiLen = danhSach.stream()
                .map(su26sd09.su26sd09.dto.GiayToCheckInDTO::getChiTietId)
                .filter(id -> id != null && chiTietHopLe.contains(id))
                .collect(Collectors.toSet());
        for (Integer chiTietId : chiTietDaGuiLen) {
            List<GiayTo> cu = giayToRepo.findByChiTietDatPhong_Id(chiTietId);
            if (!cu.isEmpty()) giayToRepo.deleteAll(cu);
        }

        for (su26sd09.su26sd09.dto.GiayToCheckInDTO gt : danhSach) {
            if (gt.getChiTietId() == null || !chiTietHopLe.contains(gt.getChiTietId())) continue;
            ChiTietDatPhong ct = chiTietDatPhongService.findbyId(gt.getChiTietId());
            if (ct == null) continue;

            GiayTo entity = new GiayTo();
            entity.setChiTietDatPhong(ct);
            entity.setCoDaiDien(gt.getCoDaiDien());
            entity.setLoaiGiayTo(gt.getLoaiGiayTo());
            entity.setHoTen(gt.getHoTen());
            entity.setSoDinhDanh(gt.getSoDinhDanh());
            entity.setNgaySinh(gt.getNgaySinh());
            entity.setGioiTinh(gt.getGioiTinh());
            entity.setQuocTich(gt.getQuocTich());
            entity.setQueQuan(gt.getQueQuan());
            entity.setNoiThuongTru(gt.getNoiThuongTru());
            entity.setNoiCuTru(gt.getNoiCuTru());
            entity.setNoiTamTru(gt.getNoiTamTru());
            entity.setNoiLuuTru(gt.getNoiLuuTru());
            entity.setGiaTriDen(gt.getGiaTriDen());
            entity.setNgayCap(gt.getNgayCap());
            entity.setQuocGiaCapPhat(gt.getQuocGiaCapPhat());
            giayToRepo.save(entity);
        }

        result.put("ok", true);
        return result;
    }

    @PostMapping("/so-do-phong/check-in/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkInTuSoDoPhong(@PathVariable int maDatPhong,
                                                  @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan) {
        Map<String, Object> result = new LinkedHashMap<>();

        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy đơn đặt phòng #" + maDatPhong);
            return result;
        }

        List<Phong> dsPhong = datPhongService.findPhongByDatPhongId(maDatPhong);
        if (dsPhong == null || dsPhong.isEmpty()) {
            result.put("ok", false);
            result.put("loi", "Đơn đặt phòng chưa được gán phòng.");
            return result;
        }
        Phong phong = dsPhong.get(0);

        if (!"Trống".equals(suyRaTrangThaiHienThi(phong))) {
            result.put("ok", false);
            result.put("loi", "Không thể nhận phòng: phòng hiện không ở trạng thái \"Trống\".");
            return result;
        }

        if (!hoaDonService.isDaThanhToanDu(maDatPhong)) {
            result.put("ok", false);
            result.put("loi", "Không thể nhận phòng: đơn đặt phòng chưa được thanh toán đủ.");
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        KetQuaPhuThu kq = tinhPhuThuNhanSom(now, dp.getNgaydatPhong());

        boolean viPham = kq.tyLe.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal soTien = viPham
                ? (phong.getGiaMoiDem() == null ? BigDecimal.ZERO
                   : phong.getGiaMoiDem().multiply(kq.tyLe).setScale(0, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

        if (viPham && !xacNhan) {
            // Chi xem truoc: chua dung backend de cap nhat trang thai phong/don.
            result.put("ok", true);
            result.put("viPham", true);
            result.put("daApDung", false);
            result.put("soTien", soTien);
            result.put("moTaChinhSach", kq.moTa);
            return result;
        }

        if (viPham) {
            luuChiTietDichVuPhuThu(dp, "check-in som", soTien);
            result.put("soTien", soTien);
        }

        // Cap nhat gio nhan phong THUC TE ve thoi diem khach bam "Khach nhan phong",
        // dong thoi chuyen trang thai don sang "Da nhan phong".
        dp.setNgaydatPhongThuc(now);
        dp.setTrangThai("Da nhan phong");
        datPhongService.save(dp);
        checkInExpirationCacheService.xoaKhoiTheoDoi(dp.getId());

        phong.setTrangThai("Dang su dung");
        phong.setNgayCapNhat(now);
        phongService.save1(phong);

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang sử dụng");
        return result;
    }

    /**
     * Nhận phòng (check-in) CẢ ĐOÀN — dùng cho đơn đặt nhiều phòng cùng lúc.
     * Vì cả đoàn dùng chung 1 trạng thái đơn, KHÔNG cho phép nhận phòng từng
     * phòng riêng lẻ trong trường hợp này: bấm "Cả đoàn khách nhận phòng" sẽ
     * nhận TẤT CẢ các phòng được gán cho đơn cùng một lúc.
     * - Chỉ cho phép khi TẤT CẢ các phòng của đơn đang ở trạng thái hiển thị
     *   "Trống" (nếu có phòng nào không "Trống", từ chối và nêu rõ phòng nào).
     * - Áp dụng chính sách nhận phòng sớm 1 LẦN cho cả đơn (dựa theo giờ nhận
     *   phòng đã đặt chung của đơn — dp.getNgaydatPhong()), phụ thu tính RIÊNG
     *   theo giá từng phòng rồi cộng lại thành 1 khoản duy nhất.
     * - Nếu vi phạm và CHƯA xác nhận (xacNhan=false): chỉ trả về tổng phụ thu
     *   xem trước, KHÔNG cập nhật trạng thái phòng/đơn (daApDung=false).
     * - Nếu không vi phạm, hoặc đã xác nhận: ghi 1 chi_tiet_dich_vu phụ thu (nếu
     *   có) và chuyển TẤT CẢ phòng của đơn sang "Đang sử dụng".
     */
    @PostMapping("/so-do-phong/check-in-nhom/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkInNhomTuSoDoPhong(@PathVariable int maDatPhong,
                                                      @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan) {
        Map<String, Object> result = new LinkedHashMap<>();

        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy đơn đặt phòng #" + maDatPhong);
            return result;
        }

        List<Phong> dsPhong = datPhongService.findPhongByDatPhongId(maDatPhong);
        if (dsPhong == null || dsPhong.isEmpty()) {
            result.put("ok", false);
            result.put("loi", "Đơn đặt phòng chưa được gán phòng.");
            return result;
        }

        List<String> phongChuaTrong = new ArrayList<>();
        for (Phong p : dsPhong) {
            if (!"Trống".equals(suyRaTrangThaiHienThi(p))) {
                phongChuaTrong.add(p.getSoPhong());
            }
        }
        if (!phongChuaTrong.isEmpty()) {
            result.put("ok", false);
            result.put("loi", "Không thể nhận phòng cả đoàn: phòng " + String.join(", ", phongChuaTrong)
                    + " hiện không ở trạng thái \"Trống\".");
            return result;
        }

        if (!hoaDonService.isDaThanhToanDu(maDatPhong)) {
            result.put("ok", false);
            result.put("loi", "Không thể nhận phòng: đơn đặt phòng chưa được thanh toán đủ.");
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        KetQuaPhuThu kq = tinhPhuThuNhanSom(now, dp.getNgaydatPhong());
        boolean viPham = kq.tyLe.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal tongPhuThu = BigDecimal.ZERO;
        if (viPham) {
            for (Phong p : dsPhong) {
                if (p.getGiaMoiDem() != null) {
                    tongPhuThu = tongPhuThu.add(p.getGiaMoiDem().multiply(kq.tyLe).setScale(0, RoundingMode.HALF_UP));
                }
            }
        }

        if (viPham && !xacNhan) {
            result.put("ok", true);
            result.put("viPham", true);
            result.put("daApDung", false);
            result.put("soTien", tongPhuThu);
            result.put("moTaChinhSach", kq.moTa + " (áp dụng cho cả " + dsPhong.size() + " phòng của đoàn)");
            return result;
        }

        if (viPham) {
            luuChiTietDichVuPhuThu(dp, "check-in som", tongPhuThu);
            result.put("soTien", tongPhuThu);
        }

        dp.setNgaydatPhongThuc(now);
        dp.setTrangThai("Da nhan phong");
        datPhongService.save(dp);
        checkInExpirationCacheService.xoaKhoiTheoDoi(dp.getId());

        List<Integer> maPhongDaNhan = new ArrayList<>();
        for (Phong p : dsPhong) {
            p.setTrangThai("Dang su dung");
            p.setNgayCapNhat(now);
            phongService.save1(p);
            maPhongDaNhan.add(p.getMaPhong());
        }

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang sử dụng");
        result.put("maPhongDaNhan", maPhongDaNhan);
        return result;
    }

    /**
     * - Áp dụng chính sách trả phòng muộn: nếu vi phạm và CHƯA xác nhận (xacNhan=false),
     *   CHỈ trả về thông tin phụ thu để hiển thị modal cảnh báo — KHÔNG lưu chi_tiet_dich_vu
     *   phụ thu, KHÔNG cập nhật trạng thái phòng/đơn (daApDung=false).
     * - Nếu không vi phạm, hoặc vi phạm nhưng đã xác nhận (xacNhan=true, người dùng bấm
     *   "Đã hiểu"): tự tạo 1 chi_tiet_dich_vu phụ thu (ghi_chu = "check-out muon") gắn vào
     *   đơn đặt phòng (nếu vi phạm), chuyển trạng thái phòng sang "Đang dọn" và đơn sang
     *   "Da tra phong" (daApDung=true).
     */
    @PostMapping("/so-do-phong/check-out/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkOutTuSoDoPhong(@PathVariable int maDatPhong,
                                                   @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan) {
        Map<String, Object> result = new LinkedHashMap<>();

        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy đơn đặt phòng #" + maDatPhong);
            return result;
        }

        List<Phong> dsPhong = datPhongService.findPhongByDatPhongId(maDatPhong);
        if (dsPhong == null || dsPhong.isEmpty()) {
            result.put("ok", false);
            result.put("loi", "Đơn đặt phòng chưa được gán phòng.");
            return result;
        }
        Phong phong = dsPhong.get(0);

        if (!"Đang sử dụng".equals(suyRaTrangThaiHienThi(phong)) || !"Da nhan phong".equals(dp.getTrangThai())) {
            result.put("ok", false);
            result.put("loi", "Không thể trả phòng: phòng hoặc đơn đặt phòng hiện không ở trạng thái hợp lệ để trả phòng.");
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        KetQuaPhuThu kq = tinhPhuThuTraMuon(now, dp.getNgaytraPhong());

        boolean viPham = kq.tyLe.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal soTien = viPham
                ? (phong.getGiaMoiDem() == null ? BigDecimal.ZERO
                   : phong.getGiaMoiDem().multiply(kq.tyLe).setScale(0, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

        if (viPham && !xacNhan) {
            // Chi xem truoc: chua dung backend de cap nhat trang thai phong/don.
            result.put("ok", true);
            result.put("viPham", true);
            result.put("daApDung", false);
            result.put("soTien", soTien);
            result.put("moTaChinhSach", kq.moTa);
            return result;
        }

        if (viPham) {
            luuChiTietDichVuPhuThu(dp, "check-out muon", soTien);
            result.put("soTien", soTien);
        }

        dp.setTrangThai("Da tra phong");
        dp.setNgaytraPhongThuc(now);
        datPhongService.save(dp);

        phong.setTrangThai("Dang don");
        phong.setNgayCapNhat(now);
        phongService.save1(phong);

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang dọn");
        return result;
    }

    /**
     * Trả phòng (check-out) CẢ ĐOÀN — dùng cho đơn đặt nhiều phòng cùng lúc.
     * Vì cả đoàn dùng chung 1 trạng thái đơn, KHÔNG cho phép trả phòng từng
     * phòng riêng lẻ trong trường hợp này: bấm "Cả đoàn khách trả phòng" sẽ
     * trả TẤT CẢ các phòng được gán cho đơn cùng một lúc.
     * - Chỉ cho phép khi TẤT CẢ các phòng của đơn đang ở trạng thái hiển thị
     *   "Đang sử dụng" VÀ đơn đang ở trạng thái "Da nhan phong" (nếu có phòng
     *   nào không hợp lệ, từ chối và nêu rõ phòng nào).
     * - Áp dụng chính sách trả phòng muộn 1 LẦN cho cả đơn (dựa theo giờ trả
     *   phòng đã đặt chung của đơn — dp.getNgaytraPhong()), phụ thu tính RIÊNG
     *   theo giá từng phòng rồi cộng lại thành 1 khoản duy nhất.
     * - Nếu vi phạm và CHƯA xác nhận (xacNhan=false): chỉ trả về tổng phụ thu
     *   xem trước, KHÔNG cập nhật trạng thái phòng/đơn (daApDung=false).
     * - Nếu không vi phạm, hoặc đã xác nhận: ghi 1 chi_tiet_dich_vu phụ thu (nếu
     *   có), chuyển TẤT CẢ phòng của đơn sang "Đang dọn" và đơn sang "Da tra phong".
     */
    @PostMapping("/so-do-phong/check-out-nhom/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkOutNhomTuSoDoPhong(@PathVariable int maDatPhong,
                                                       @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan) {
        Map<String, Object> result = new LinkedHashMap<>();

        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy đơn đặt phòng #" + maDatPhong);
            return result;
        }

        List<Phong> dsPhong = datPhongService.findPhongByDatPhongId(maDatPhong);
        if (dsPhong == null || dsPhong.isEmpty()) {
            result.put("ok", false);
            result.put("loi", "Đơn đặt phòng chưa được gán phòng.");
            return result;
        }

        if (!"Da nhan phong".equals(dp.getTrangThai())) {
            result.put("ok", false);
            result.put("loi", "Không thể trả phòng cả đoàn: đơn hiện không ở trạng thái đang lưu trú.");
            return result;
        }

        List<String> phongChuaSuDung = new ArrayList<>();
        for (Phong p : dsPhong) {
            if (!"Đang sử dụng".equals(suyRaTrangThaiHienThi(p))) {
                phongChuaSuDung.add(p.getSoPhong());
            }
        }
        if (!phongChuaSuDung.isEmpty()) {
            result.put("ok", false);
            result.put("loi", "Không thể trả phòng cả đoàn: phòng " + String.join(", ", phongChuaSuDung)
                    + " hiện không ở trạng thái \"Đang sử dụng\".");
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        KetQuaPhuThu kq = tinhPhuThuTraMuon(now, dp.getNgaytraPhong());
        boolean viPham = kq.tyLe.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal tongPhuThu = BigDecimal.ZERO;
        if (viPham) {
            for (Phong p : dsPhong) {
                if (p.getGiaMoiDem() != null) {
                    tongPhuThu = tongPhuThu.add(p.getGiaMoiDem().multiply(kq.tyLe).setScale(0, RoundingMode.HALF_UP));
                }
            }
        }

        if (viPham && !xacNhan) {
            result.put("ok", true);
            result.put("viPham", true);
            result.put("daApDung", false);
            result.put("soTien", tongPhuThu);
            result.put("moTaChinhSach", kq.moTa + " (áp dụng cho cả " + dsPhong.size() + " phòng của đoàn)");
            return result;
        }

        if (viPham) {
            luuChiTietDichVuPhuThu(dp, "check-out muon", tongPhuThu);
            result.put("soTien", tongPhuThu);
        }

        dp.setTrangThai("Da tra phong");
        dp.setNgaytraPhongThuc(now);
        datPhongService.save(dp);

        List<Integer> maPhongDaTra = new ArrayList<>();
        for (Phong p : dsPhong) {
            p.setTrangThai("Dang don");
            p.setNgayCapNhat(now);
            phongService.save1(p);
            maPhongDaTra.add(p.getMaPhong());
        }

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang dọn");
        result.put("maPhongDaTra", maPhongDaTra);
        return result;
    }

    /** Kết quả tính phụ thu: tỷ lệ áp dụng (0 - 1) trên giá 1 đêm + mô tả chính sách. */
    private static final class KetQuaPhuThu {
        final BigDecimal tyLe;
        final String moTa;
        KetQuaPhuThu(BigDecimal tyLe, String moTa) { this.tyLe = tyLe; this.moTa = moTa; }
    }

    /**
     * Chính sách nhận phòng sớm — tính theo SỐ GIỜ nhận phòng trước thời điểm
     * check-in đã đặt của chính đơn đó (ngaydatPhong), không còn theo giờ cố định
     * trong ngày:
     *  - từ 7 giờ trở lên trước giờ check-in đã đặt  -> 100% giá 1 đêm
     *  - từ 4 giờ đến dưới 7 giờ trước giờ check-in   -> 50%  giá 1 đêm
     *  - từ 1 giờ đến dưới 4 giờ trước giờ check-in   -> 30%  giá 1 đêm
     *  - dưới 1 giờ trước (kể cả đúng giờ/sau giờ)    -> không phụ thu
     */
    private KetQuaPhuThu tinhPhuThuNhanSom(LocalDateTime thoiDiemNhan, LocalDateTime gioCheckInDaDat) {
        if (gioCheckInDaDat == null) {
            return new KetQuaPhuThu(BigDecimal.ZERO, "Đơn chưa có giờ nhận phòng đã đặt — không phụ thu.");
        }
        double gioTruoc = java.time.Duration.between(thoiDiemNhan, gioCheckInDaDat).toMinutes() / 60.0;

        if (gioTruoc >= 7) {
            return new KetQuaPhuThu(new BigDecimal("1.0"), "Nhận phòng từ 7 giờ trở lên trước giờ check-in đã đặt — tính 100% giá 1 đêm.");
        }
        if (gioTruoc >= 4) {
            return new KetQuaPhuThu(new BigDecimal("0.5"), "Nhận phòng từ 4 đến dưới 7 giờ trước giờ check-in đã đặt — tính 50% giá 1 đêm.");
        }
        if (gioTruoc >= 1) {
            return new KetQuaPhuThu(new BigDecimal("0.3"), "Nhận phòng từ 1 đến dưới 4 giờ trước giờ check-in đã đặt — tính 30% giá 1 đêm.");
        }
        return new KetQuaPhuThu(BigDecimal.ZERO, "Nhận phòng dưới 1 giờ trước giờ check-in đã đặt (hoặc đúng/sau giờ) — không phụ thu.");
    }

    /**
     * Chính sách trả phòng muộn — tính theo SỐ GIỜ trả phòng sau thời điểm
     * check-out đã đặt của chính đơn đó (ngaytraPhong), không còn theo giờ cố
     * định trong ngày:
     *  - từ 5 giờ trở lên sau giờ check-out đã đặt   -> 100% giá 1 đêm
     *  - từ 3 giờ đến dưới 5 giờ sau giờ check-out    -> 50%  giá 1 đêm
     *  - trên 0 giờ đến dưới 3 giờ sau giờ check-out  -> 30%  giá 1 đêm
     *  - đúng giờ hoặc trước giờ check-out đã đặt     -> không phụ thu
     */
    private KetQuaPhuThu tinhPhuThuTraMuon(LocalDateTime thoiDiemTra, LocalDateTime gioCheckOutDaDat) {
        if (gioCheckOutDaDat == null) {
            return new KetQuaPhuThu(BigDecimal.ZERO, "Đơn chưa có giờ trả phòng đã đặt — không phụ thu.");
        }
        double gioSau = java.time.Duration.between(gioCheckOutDaDat, thoiDiemTra).toMinutes() / 60.0;

        if (gioSau >= 5) {
            return new KetQuaPhuThu(new BigDecimal("1.0"), "Trả phòng từ 5 giờ trở lên sau giờ check-out đã đặt — tính 100% giá 1 đêm.");
        }
        if (gioSau >= 3) {
            return new KetQuaPhuThu(new BigDecimal("0.5"), "Trả phòng từ 3 đến dưới 5 giờ sau giờ check-out đã đặt — tính 50% giá 1 đêm.");
        }
        if (gioSau > 0) {
            return new KetQuaPhuThu(new BigDecimal("0.3"), "Trả phòng trên 0 đến dưới 3 giờ sau giờ check-out đã đặt — tính 30% giá 1 đêm.");
        }
        return new KetQuaPhuThu(BigDecimal.ZERO, "Trả phòng đúng giờ hoặc trước giờ check-out đã đặt — không phụ thu.");
    }

    /** Tạo 1 dòng chi_tiet_dich_vu phụ thu, gắn vào dịch vụ "Phụ thu nhận/trả phòng" (tự tạo nếu chưa có). */
    private void luuChiTietDichVuPhuThu(DatPhong dp, String ghiChu, BigDecimal donGia) {
        String tenDv = "check-in som".equals(ghiChu) ? "Phụ thu nhận phòng sớm" : "Phụ thu trả phòng muộn";
        Dich_vu dv = timHoacTaoDichVuPhuThu(tenDv);

        Chi_tiet_dich_vu ctdv = new Chi_tiet_dich_vu();
        ctdv.setDatPhong(dp);
        ctdv.setDv(dv);
        ctdv.setSoluong(1);
        ctdv.setNgay_su_dung(LocalDateTime.now());
        ctdv.setGhichu(ghiChu);
        ctdv.setDonGia(donGia);
        ctdvService.save(ctdv);

        // Chi_tiet_dich_vu phu thu vua tao lam tang tong tien dich vu cua don ->
        // phai cong don vao hoa_don tuong ung, neu khong hoa don se bi lech so
        // voi thuc te (thieu khoan phu thu nay).
        capNhatHoaDonSauKhiThemPhuThu(dp.getId(), donGia);
    }

    /** Cong them mot khoan phu thu (vua tao chi_tiet_dich_vu) vao hoa_don cua don dat phong,
     *  neu don da co hoa don. Dung saveWithPaymentStatusCheck de tu dong dong bo lai
     *  trangThai (vd: tu "Da thanh toan" chuyen ve "Cho thanh toan" khi tong tien tang len).
     *
     *  Phu thu duoc luu trong chi_tiet_dich_vu (dv.loaiDv = "Phu thu"). View se doc
     *  lai tu chi_tiet_dich_vu de tach rieng dong "Phu thu" tren hoa don, khong can
     *  cot rieng trong HoaDon. */
    private void capNhatHoaDonSauKhiThemPhuThu(Integer maDatPhong, BigDecimal soTienPhuThu) {
        if (soTienPhuThu == null || soTienPhuThu.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        HoaDon hoaDon = hoaDonService.findByDatPhongId(maDatPhong);
        if (hoaDon == null) {
            return; // chua co hoa don thi thoi, se duoc tinh o buoc thanh toan/chot so sau
        }
        // Phu thu: chi tang tongTien (gia dinh tienDichVu da tinh tu chi_tiet_dich_vu o
        // luot update form sau, hoac se duoc tinh o luot render hoa don tiep theo).
        // De tranh duplicate, KHONG add vao tienDichVu o day.
        hoaDon.setTongTien(defaultMoney(hoaDon.getTongTien()).add(soTienPhuThu));
        hoaDon.setNgayCapNhat(LocalDateTime.now());
        HoaDon hoaDonDaLuu = hoaDonService.saveWithPaymentStatusCheck(hoaDon);

        // Phu thu vua cong lam tang cong no -> gui email kem QR de khach thanh
        // toan ngay phan con lai, khong can doi den luc tra phong moi biet.
        BigDecimal conLai = defaultMoney(hoaDonDaLuu.getTongTien()).subtract(defaultMoney(hoaDonDaLuu.getDaThanhToan()));
        if (conLai.compareTo(BigDecimal.ZERO) > 0) {
            bookingEmailService.guiEmailYeuCauThanhToan(
                    maDatPhong,
                    "Phát sinh chi phí phụ thu - Đơn đặt phòng #" + maDatPhong,
                    "Đơn đặt phòng #" + maDatPhong + " vừa phát sinh thêm khoản phụ thu "
                            + formatTienPhuThu(soTienPhuThu) + ". Quý khách vui lòng thanh toán phần còn lại.",
                    conLai
            );
        }
    }

    private String formatTienPhuThu(BigDecimal tien) {
        if (tien == null) tien = BigDecimal.ZERO;
        return String.format("%,.0f", tien.doubleValue()) + " VND";
    }

    private Dich_vu timHoacTaoDichVuPhuThu(String ten) {
        for (Dich_vu dv : dichVuService.findAll()) {
            if (ten.equalsIgnoreCase(dv.getTen_dich_vu())) {
                return dv;
            }
        }
        Dich_vu dv = new Dich_vu();
        dv.setTen_dich_vu(ten);
        dv.setGia(BigDecimal.ZERO);
        dv.setDonVi("lần");
        dv.setHoatDong(true);
        dv.setLoaiDv("Phu thu");
        return dichVuService.save(dv);
    }

    @GetMapping("/so-do-phong")
    public String soDoPhong(Model model) {
        List<Phong> tatCaPhong = phongService.findAllPhong();

        Map<Integer, List<Phong>> theoTang = tatCaPhong.stream()
                .collect(Collectors.groupingBy(Phong::getSoTang, java.util.TreeMap::new, Collectors.toList()));

        Map<Integer, String> nhanTrangThai = new HashMap<>();
        Map<Integer, String> lopTrangThai = new HashMap<>();
        Map<String, Long> demTrangThai = new LinkedHashMap<>();
        demTrangThai.put("Trống", 0L);
        demTrangThai.put("Đang sử dụng", 0L);
        demTrangThai.put("Đang dọn", 0L);
        demTrangThai.put("Bảo trì", 0L);

        for (Phong p : tatCaPhong) {
            String hienThi = suyRaTrangThaiHienThi(p);
            nhanTrangThai.put(p.getMaPhong(), hienThi);
            lopTrangThai.put(p.getMaPhong(), lopCssTheoTrangThai(hienThi));
            demTrangThai.merge(hienThi, 1L, Long::sum);
        }

        // So khach THUC TE cua tung phong dang "Dang su dung", tinh tu giay_to
        // (CCCD/ho chieu) da thu thap luc check-in cho DUNG chi_tiet_dat_phong cua
        // phong do trong don dang giu phong: 1 giay_to = 1 khach (khong loc theo
        // tuoi). Phong khong co giay_to nao (chua thu thap / chua check-in xong)
        // thi khong hien so lieu nay (fallback ve suc chua).
        Map<Integer, Integer> soNguoiLonHienTai = new HashMap<>();
        for (Phong p : tatCaPhong) {
            if (!"Dang su dung".equals(p.getTrangThai())) continue;
            List<DatPhong> dsDangO = datPhongService.findUsingBookings(p.getMaPhong());
            if (dsDangO.isEmpty()) continue;

            DatPhong donDangO = dsDangO.get(0);
            List<ChiTietDatPhong> ctdpList = chiTietDatPhongService.findByDatPhongId(donDangO.getId());
            ChiTietDatPhong ctCuaPhongNay = ctdpList.stream()
                    .filter(ct -> ct.getP() != null && ct.getP().getMaPhong() == p.getMaPhong())
                    .findFirst().orElse(null);
            if (ctCuaPhongNay == null) continue;

            List<GiayTo> dsGiayTo = giayToRepo.findByChiTietDatPhong_Id(ctCuaPhongNay.getId());
            if (dsGiayTo.isEmpty()) continue;

            soNguoiLonHienTai.put(p.getMaPhong(), dsGiayTo.size());
        }
        model.addAttribute("soNguoiLonHienTai", soNguoiLonHienTai);

        // Danh sách đơn đặt phòng (còn hiệu lực / liên quan) của từng phòng, dùng cho
        // menu chuột phải: xác định "Đặt phòng ngay", "Xem đơn hiện tại", "Khách nhận
        // phòng" ... dựa theo khoảng [ngaydatPhong, ngaytraPhong) của từng đơn.
        Map<Integer, Integer> soPhongTheoDon = new HashMap<>(); // cache: maDatPhong -> so phong trong don (tranh query lap lai)
        StringBuilder bkJson = new StringBuilder("{");
        boolean firstRoom = true;
        for (Phong p : tatCaPhong) {
            List<DatPhong> dsDon = datPhongService.findRecentBookingsForPhong(p.getMaPhong());
            if (!firstRoom) bkJson.append(",");
            firstRoom = false;
            bkJson.append("\"").append(p.getMaPhong()).append("\":[");
            for (int i = 0; i < dsDon.size(); i++) {
                DatPhong d = dsDon.get(i);
                if (i > 0) bkJson.append(",");
                int soPhongTrongDon = soPhongTheoDon.computeIfAbsent(d.getId(),
                        id -> datPhongService.findPhongByDatPhongId(id).size());
                bkJson.append("{")
                        .append("\"id\":").append(d.getId()).append(",")
                        .append("\"checkin\":\"").append(d.getNgaydatPhong() != null ? d.getNgaydatPhong() : "").append("\",")
                        .append("\"checkout\":\"").append(d.getNgaytraPhong() != null ? d.getNgaytraPhong() : "").append("\",")
                        .append("\"checkinThuc\":\"").append(d.getNgaydatPhongThuc() != null ? d.getNgaydatPhongThuc() : "").append("\",")
                        .append("\"checkoutThuc\":\"").append(d.getNgaytraPhongThuc() != null ? d.getNgaytraPhongThuc() : "").append("\",")
                        .append("\"trangThai\":\"").append(escapeJson(d.getTrangThai())).append("\",")
                        .append("\"soPhong\":").append(soPhongTrongDon).append(",")
                        .append("\"daThanhToanDu\":").append(hoaDonService.isDaThanhToanDu(d.getId()))
                        .append("}");
            }
            bkJson.append("]");
        }
        bkJson.append("}");

        // JSON chi tiết đầy đủ từng phòng (dùng cho sidebar "Xem thông tin phòng"):
        // thông tin phòng, loại phòng, tiện nghi, mô tả, thời gian tạo/cập nhật.
        java.time.format.DateTimeFormatter fmtNgay = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder infoJson = new StringBuilder("{");
        boolean firstInfo = true;
        for (Phong p : tatCaPhong) {
            if (!firstInfo) infoJson.append(",");
            firstInfo = false;

            LoaiPhong lp = p.getLoaiPhong();
            List<TienNghi> dsTienNghi = tienNghiPhongRepository.findByPhongMaPhong(p.getMaPhong())
                    .stream().map(tnp -> tnp.getTienNghi()).collect(Collectors.toList());
            StringBuilder tnArr = new StringBuilder("[");
            for (int i = 0; i < dsTienNghi.size(); i++) {
                if (i > 0) tnArr.append(",");
                tnArr.append("\"").append(escapeJson(dsTienNghi.get(i).getTenTienNghi())).append("\"");
            }
            tnArr.append("]");

            infoJson.append("\"").append(p.getMaPhong()).append("\":{")
                    .append("\"maPhong\":").append(p.getMaPhong()).append(",")
                    .append("\"soPhong\":\"").append(escapeJson(p.getSoPhong())).append("\",")
                    .append("\"tang\":").append(p.getSoTang()).append(",")
                    .append("\"trangThai\":\"").append(escapeJson(nhanTrangThai.get(p.getMaPhong()))).append("\",")
                    .append("\"gia\":").append(p.getGiaMoiDem() == null ? "0" : p.getGiaMoiDem().toPlainString()).append(",")
                    .append("\"moTa\":\"").append(escapeJson(p.getMoTa())).append("\",")
                    .append("\"hoatDong\":").append(p.isHoatDong()).append(",")
                    .append("\"ngayTao\":\"").append(p.getNgayTao() != null ? p.getNgayTao().format(fmtNgay) : "").append("\",")
                    .append("\"ngayCapNhat\":\"").append(p.getNgayCapNhat() != null ? p.getNgayCapNhat().format(fmtNgay) : "").append("\",")
                    .append("\"loaiPhong\":").append(lp == null ? "null" : (
                            "{" +
                            "\"tenLoai\":\"" + escapeJson(lp.getTenLoai()) + "\"," +
                            "\"sucChuaToiDa\":" + lp.getSucChuaToiDa() + "," +
                            "\"giaCoBan\":" + (lp.getGiaCoBan() == null ? "0" : lp.getGiaCoBan().toPlainString()) + "," +
                            "\"moTa\":\"" + escapeJson(lp.getMota()) + "\"" +
                            "}"
                    )).append(",")
                    .append("\"tienNghi\":").append(tnArr)
                    .append("}");
        }
        infoJson.append("}");

        model.addAttribute("roomInfoJson", infoJson.toString());

        model.addAttribute("theoTang", theoTang);
        model.addAttribute("nhanTrangThai", nhanTrangThai);
        model.addAttribute("lopTrangThai", lopTrangThai);
        model.addAttribute("demTrangThai", demTrangThai);
        model.addAttribute("tongSoPhong", tatCaPhong.size());
        model.addAttribute("bookingsByRoomJson", bkJson.toString());
        model.addAttribute("svrNowIso", LocalDateTime.now().toString());
        // Danh sach dich vu dang hoat dong, dung cho form "Len lich dat phong" (sidebar).
        model.addAttribute("dichVuOptions", dichVuService.findAll().stream()
                .filter(Dich_vu::isHoatDong)
                .collect(Collectors.toList()));

        // ===== Chế độ xem thay thế: Danh sách đơn đặt phòng (dat_phong) =====
        // Chỉ hiển thị dạng bảng, dùng chung select box với "Sơ đồ phòng" ở đầu
        // trang. Chỉ hiển thị đơn "sắp tới" và "đang ở" — loại bỏ đơn đã trả phòng
        // (Da tra phong) và đã hủy hẳn (Da huy) khỏi danh sách này.
        Set<String> sdpListTrangThaiHienThi = HuyDonConstants.DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT.stream()
                .filter(ts -> !"Da tra phong".equals(ts) && !"Da huy".equals(ts))
                .collect(Collectors.toSet());
        List<DatPhong> dsDatPhongList = datPhongService
                .findAll(Sort.by(Sort.Order.desc("ngayTao"), Sort.Order.desc("id")))
                .stream()
                .filter(dp -> sdpListTrangThaiHienThi.contains(dp.getTrangThai()))
                .collect(Collectors.toList());
        Map<Integer, List<Phong>> phongTheoDonSoDo = new HashMap<>();
        for (DatPhong dp : dsDatPhongList) {
            phongTheoDonSoDo.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
        }
        model.addAttribute("dsDatPhongList", dsDatPhongList);
        model.addAttribute("phongTheoDonSoDo", phongTheoDonSoDo);

        // Phong dang cho lễ tân xac nhan da don sach (nhan vien ve sinh da
        // upload anh, chua duoc xac nhan) - dung de hien text "Xac nhan phong
        // sach" tren the phong o so do phong.
        Map<Integer, Boolean> phongChoXacNhanVeSinh = new HashMap<>();
        for (su26sd09.su26sd09.dto.PhongVeSinhAssignment a : janitorCacheService.getAll()) {
            if (su26sd09.su26sd09.dto.PhongVeSinhAssignment.DA_UPLOAD.equals(a.getTrangThai())) {
                phongChoXacNhanVeSinh.put(a.getMaPhong(), true);
            }
        }
        model.addAttribute("phongChoXacNhanVeSinh", phongChoXacNhanVeSinh);

        // ===== Panel bên phải: "Khách chờ check-in" =====
        // Lấy đơn status (Cho xac nhan / Da xac nhan) chưa nhận phòng, có
        // ngaydatPhong trong khoảng [now - 24h, now + 7 ngày] để bao gồm cả
        // đơn đã trễ hẹn trong 24h qua. Build JSON cho JS render countdown.
        LocalDateTime now = LocalDateTime.now();
        List<DatPhong> dsSapCheckIn = datPhongService.findUpcomingCheckIns(
                now.minusHours(24), now.plusDays(7));
        StringBuilder kcJson = new StringBuilder("[");
        boolean firstKc = true;
        for (DatPhong dp : dsSapCheckIn) {
            if (!firstKc) kcJson.append(",");
            firstKc = false;
            String ten = dp.getHoten();
            if ((ten == null || ten.isBlank()) && dp.getN() != null) ten = dp.getN().getHoTen();
            if (ten == null || ten.isBlank()) ten = "Chưa rõ tên";
            int soKhach = dp.getSonguoiLon() + dp.getSotreEm();

            // Lấy nhãn phòng kiểu "P101, P102" (gioi han 3 phong de khong vuot card)
            List<Phong> phongCuaDon = datPhongService.findPhongByDatPhongId(dp.getId());
            String phongLabel = phongCuaDon.stream()
                    .map(p -> "P" + p.getSoPhong())
                    .limit(3)
                    .collect(Collectors.joining(", "));
            if (phongCuaDon.size() > 3) phongLabel += ", …";

            kcJson.append("{")
                    .append("\"id\":").append(dp.getId()).append(",")
                    .append("\"hoten\":\"").append(escapeJson(ten)).append("\",")
                    .append("\"checkin\":\"").append(dp.getNgaydatPhong() != null ? dp.getNgaydatPhong() : "").append("\",")
                    .append("\"soKhach\":").append(soKhach).append(",")
                    .append("\"phongLabel\":\"").append(escapeJson(phongLabel)).append("\"")
                    .append("}");
        }
        kcJson.append("]");
        model.addAttribute("khachChoCheckInJson", kcJson.toString());

        return "nhan-vien/so-do-phong";
    }

    /**
     * Xu ly submit sidebar "Len lich dat phong" o trang So Do Phong (dat truoc/tai
     * cho 1 phong cu the, khac voi /dat-phong-quay/submit vi khong gioi han chi
     * "dang o quay" va khong ep thanh toan du 100%).
     * <p>
     * Ve dich vu them (checkbox trong sidebar): UI hien tai KHONG co truong chon
     * "ngay su dung" rieng cho tung dich vu (chi co 1 khoang [checkin, checkout]
     * chung cho ca don), nen ta dien mot moc thoi gian nam GIUA checkin/checkout
     * lam gia tri filler cho chi_tiet_dich_vu.ngay_su_dung de khong vi pham rang
     * buoc NOT NULL / cac kiem tra logic khac dua tren cot nay, ma khong can gia
     * lap them mot "ngay su dung dich vu" that su (UI khong thu thap thong tin do).
     */
    @PostMapping("/so-do-phong/len-lich")
    @ResponseBody
    public Map<String, Object> lenLichDatPhongTuSoDoPhong(
            @RequestParam Integer maPhong,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkin,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkout,
            @RequestParam(required = false) String hoTen,
            @RequestParam(required = false) String email,
            @RequestParam("cccd") String cccd,
            @RequestParam(required = false) String sdt,
            @RequestParam(required = false) Integer songuoiLon,
            @RequestParam(required = false) Integer sotreEm,
            @RequestParam(required = false) String khuyenMaiCode,
            @RequestParam(required = false) String ghiChu,
            @RequestParam(value = "dichVuIds", required = false) List<Integer> dichVuIds,
            @RequestParam(required = false) BigDecimal tienKhachTra,
            @RequestParam(required = false) String phuongThucThanhToan,
            Authentication authentication) {

        Map<String, Object> result = new LinkedHashMap<>();

        NhanSu nvCheck = authentication == null ? null : nhanVienService.FindByemail(authentication.getName());
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        NhanSu nhanVienXuLy = nhanVienService.laLeTanDangHoatDong(nvCheck)
                ? nvCheck
                : nhanVienService.findLeTanDangHoatDongMacDinh();

        if (!isAdmin && !nhanVienService.laLeTanDangHoatDong(nvCheck)) {
            result.put("ok", false);
            result.put("loi", "Tài khoản không có quyền lễ tân để lên lịch đặt phòng.");
            return result;
        }
        if (nhanVienXuLy == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy nhân viên Lễ Tân đang hoạt động.");
            return result;
        }

        if (cccd == null || cccd.isBlank()) {
            result.put("ok", false);
            result.put("loi", "CCCD không được để trống.");
            return result;
        }
        if (!cccd.trim().matches("\\d{9}(\\d{3})?")) {
            result.put("ok", false);
            result.put("loi", "Vui lòng nhập 9 hoặc 12 số CCCD/CMND hợp lệ.");
            return result;
        }

        if (maPhong == null) {
            result.put("ok", false);
            result.put("loi", "Vui lòng chọn phòng.");
            return result;
        }
        Phong phong = phongService.findById(maPhong);
        if (phong == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy phòng #" + maPhong);
            return result;
        }

        if (checkin == null || checkout == null) {
            result.put("ok", false);
            result.put("loi", "Vui lòng chọn ngày nhận và ngày trả phòng.");
            return result;
        }
        LocalDate homNay = LocalDate.now();
        if (checkin.isBefore(homNay)) {
            result.put("ok", false);
            result.put("loi", "Ngày nhận phòng không được ở quá khứ.");
            return result;
        }
        if (!checkout.isAfter(checkin)) {
            result.put("ok", false);
            result.put("loi", "Ngày trả phòng phải sau ngày nhận phòng ít nhất 1 ngày.");
            return result;
        }

        int slNguoiLon = songuoiLon != null ? songuoiLon : 1;
        int slTreEm = sotreEm != null ? sotreEm : 0;
        if (slNguoiLon < 1) {
            result.put("ok", false);
            result.put("loi", "Cần ít nhất 1 người lớn.");
            return result;
        }
        if (phong.getLoaiPhong() != null && phong.getLoaiPhong().sucChuaToiDa > 0
                && (slNguoiLon + slTreEm) > phong.getLoaiPhong().sucChuaToiDa) {
            result.put("ok", false);
            result.put("loi", "Số khách (" + (slNguoiLon + slTreEm) + ") vượt quá sức chứa tối đa của phòng ("
                    + phong.getLoaiPhong().sucChuaToiDa + ").");
            return result;
        }

        LocalDateTime ngayNhan = checkin.atTime(14, 0);
        LocalDateTime ngayTra = checkout.atTime(12, 0);

        // Overlap dung chinh xac cung 1 luat voi lich phong hien thi tren So Do Phong
        // (nua-mo [checkin, checkout)) thay vi tin vao trangThai tuc thoi cua phong.
        java.util.Set<Integer> maPhongDaKhoa = phongService.findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra);
        if (maPhongDaKhoa.contains(maPhong)) {
            result.put("ok", false);
            result.put("loi", "Phòng đã có lịch trong khoảng ngày đã chọn.");
            return result;
        }

        KhuyenMai km = null;
        if (khuyenMaiCode != null && !khuyenMaiCode.isBlank()) {
            List<KhuyenMai> kms = khuyenMaiService.findbyNameVoucher(khuyenMaiCode.trim());
            km = kms.isEmpty() ? null : kms.get(0);
            if (km == null) {
                result.put("ok", false);
                result.put("loi", "Mã khuyến mại \"" + khuyenMaiCode.trim() + "\" không tồn tại hoặc không còn hoạt động.");
                return result;
            }
        }

        DatPhong dp = new DatPhong();
        dp.setHoten(hoTen);
        dp.setEmail(email);
        dp.setSdt(sdt);
        dp.setMa_cccd(cccd.trim());
        dp.setNgaydatPhong(ngayNhan);
        dp.setNgaytraPhong(ngayTra);
        dp.setSonguoiLon(slNguoiLon);
        dp.setSotreEm(slTreEm);
        dp.setYeuCauThem(ghiChu);
        // Don duoc nhan vien tao truc tiep tu So Do Phong nen coi nhu da xac nhan
        // ngay (khac voi luong khach tu dat online phai qua "Cho xac nhan").
        dp.setTrangThai("Da xac nhan");
        dp.setNgayTao(LocalDateTime.now());
        dp.setKm(km);
        dp.setNv(nhanVienXuLy);

        DatPhong savedDp = datPhongService.save(dp);

        BigDecimal giaApDung = phong.getGiaMoiDem();
        if (km != null) {
            giaApDung = tinhGiaSauGiam(giaApDung, km);
        }
        BigDecimal phuPhiNgoaiGio = phongService.calculateExtraFeeFor(maPhong, ngayNhan, ngayTra);

        ChiTietDatPhong ctdp = new ChiTietDatPhong();
        ctdp.setD(savedDp);
        ctdp.setP(phong);
        ctdp.setGiaMoiDem(phong.getGiaMoiDem());
        ctdp.setGiaKhiDat(giaApDung);
        ctdp.setPhuPhi(phuPhiNgoaiGio);
        chiTietDatPhongService.save(ctdp);

        // Don duoc len lich (dat truoc) tu So Do Phong -> phong chi o trang thai
        // "Da dat truoc" (giu cho), GIONG KET QUA cua 1 don khach dat online binh
        // thuong. Phong chi chuyen sang "Dang su dung" khi nhan vien thuc su
        // CHECK-IN cho khach (xem updateTrangThai/"Da nhan phong" o tren), khac
        // voi "Dat phong ngay" (/dat-phong-quay) la luong nhan phong tuc thi.
        phong.setTrangThai("Da dat truoc");
        phongService.save1(phong);

        BigDecimal amountPhong = giaApDung;
        BigDecimal amountDv = BigDecimal.ZERO;

        // Filler cho ngay_su_dung: chinh giua khoang [ngayNhan, ngayTra] - xem javadoc
        // dau ham. KHONG dai dien cho ngay khach thuc su dung dich vu.
        long tongPhut = java.time.Duration.between(ngayNhan, ngayTra).toMinutes();
        LocalDateTime ngaySuDungFiller = ngayNhan.plusMinutes(tongPhut / 2);

        if (dichVuIds != null) {
            for (Integer maDichVu : dichVuIds) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;

                BigDecimal thanhTien = dv.getGia();
                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setDatPhong(savedDp);
                ct.setDv(dv);
                ct.setSoluong(1);
                ct.setDonGia(thanhTien);
                ct.setNgay_su_dung(ngaySuDungFiller);
                ctdvService.save(ct);

                amountDv = amountDv.add(thanhTien);
            }
        }

        BigDecimal vatCd = new BigDecimal("0.10");
        BigDecimal tongTienTruocVat = amountPhong.add(amountDv);
        BigDecimal tienVat = tongTienTruocVat.multiply(vatCd).setScale(0, RoundingMode.HALF_UP);
        BigDecimal tongCong = tongTienTruocVat.add(tienVat);

        BigDecimal daThu = tienKhachTra != null ? tienKhachTra : BigDecimal.ZERO;
        if (daThu.compareTo(BigDecimal.ZERO) < 0) daThu = BigDecimal.ZERO;
        if (daThu.compareTo(tongCong) > 0) daThu = tongCong; // khong nhan qua so tien phai thu

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(savedDp);
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(BigDecimal.ZERO);
        hd.setTienVat(tienVat);
        hd.setTongTien(tongCong);
        hd.setDaThanhToan(daThu);
        hd.setGhiChu("Len lich dat phong tu So Do Phong, ma don: " + savedDp.getId());
        HoaDon hoaDonDaLuu = hoaDonService.saveWithPaymentStatusCheck(hd);

        if (daThu.compareTo(BigDecimal.ZERO) > 0) {
            ThanhToan tt = new ThanhToan();
            tt.setH(hd);
            tt.setPhuongThuc("tien-mat".equals(phuongThucThanhToan) ? "Tien Mat" : "Chuyen Khoan");
            tt.setSoTien(daThu);
            tt.setTrangThai("Thanh cong");
            tt.setNgaythanhToan(LocalDateTime.now());
            tt.setGichu("Thu tien luc len lich dat phong tu So Do Phong");
            tt.setNv(nhanVienXuLy);
            thanhToanService.save(tt);
        }

        // Bao dat phong vua duoc len lich (tao) tu So Do Phong. Neu con no, gui kem
        // QR de khach thanh toan phan con lai qua /thanh-toan/pool.
        BigDecimal conLai = defaultMoney(hoaDonDaLuu.getTongTien()).subtract(defaultMoney(hoaDonDaLuu.getDaThanhToan()));
        if (conLai.compareTo(BigDecimal.ZERO) > 0) {
            bookingEmailService.guiEmailYeuCauThanhToan(
                    savedDp.getId(),
                    "Đặt phòng thành công - Đơn #" + savedDp.getId(),
                    "Đơn đặt phòng #" + savedDp.getId() + " của quý khách đã được lên lịch thành công. "
                            + "Quý khách vui lòng thanh toán phần còn lại để giữ phòng.",
                    conLai
            );
        }

        result.put("ok", true);
        result.put("maDatPhong", savedDp.getId());
        result.put("tongTien", tongCong);
        result.put("daThu", daThu);
        return result;
    }

    /**
     * Xu ly submit sidebar "Dat phong tai quay" o trang So Do Phong (mo tu cung
     * menu chuot phai voi "Len lich dat phong", tai su dung y het giao dien/JS,
     * chi khac cho ma nay la khach DANG O QUAY nen duoc NHAN PHONG NGAY LAP TUC
     * thay vi chi giu cho (khac voi lenLichDatPhongTuSoDoPhong o cho:
     *  - Bat buoc checkin = HOM NAY (khach dang truoc mat, khong the dat cho
     *    tuong lai qua luong nay - dung "Len lich dat phong" cho truong hop do).
     *  - DatPhong.trangThai = "Da nhan phong" va Phong.trangThai = "Dang su dung"
     *    ngay khi luu thanh cong, giong ket qua cuoi cung cua /dat-phong-quay
     *    (trang "Dat Phong Tai Quay" rieng) nhung ap dung cho DUNG 1 phong dang
     *    thao tac tren So Do Phong.
     */
    @PostMapping("/so-do-phong/dat-tai-quay")
    @ResponseBody
    public Map<String, Object> datPhongTaiQuayTuSoDoPhong(
            @RequestParam Integer maPhong,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkin,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkout,
            @RequestParam(required = false) String hoTen,
            @RequestParam(required = false) String email,
            @RequestParam("cccd") String cccd,
            @RequestParam(required = false) String sdt,
            @RequestParam(required = false) Integer songuoiLon,
            @RequestParam(required = false) Integer sotreEm,
            @RequestParam(required = false) String khuyenMaiCode,
            @RequestParam(required = false) String ghiChu,
            @RequestParam(value = "dichVuIds", required = false) List<Integer> dichVuIds,
            @RequestParam(required = false) BigDecimal tienKhachTra,
            @RequestParam(required = false) String phuongThucThanhToan,
            Authentication authentication) {

        Map<String, Object> result = new LinkedHashMap<>();

        NhanSu nvCheck = authentication == null ? null : nhanVienService.FindByemail(authentication.getName());
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        NhanSu nhanVienXuLy = nhanVienService.laLeTanDangHoatDong(nvCheck)
                ? nvCheck
                : nhanVienService.findLeTanDangHoatDongMacDinh();

        if (!isAdmin && !nhanVienService.laLeTanDangHoatDong(nvCheck)) {
            result.put("ok", false);
            result.put("loi", "Tài khoản không có quyền lễ tân để đặt phòng tại quầy.");
            return result;
        }
        if (nhanVienXuLy == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy nhân viên Lễ Tân đang hoạt động.");
            return result;
        }

        if (cccd == null || cccd.isBlank()) {
            result.put("ok", false);
            result.put("loi", "CCCD không được để trống.");
            return result;
        }
        if (!cccd.trim().matches("\\d{9}(\\d{3})?")) {
            result.put("ok", false);
            result.put("loi", "Vui lòng nhập 9 hoặc 12 số CCCD/CMND hợp lệ.");
            return result;
        }

        if (maPhong == null) {
            result.put("ok", false);
            result.put("loi", "Vui lòng chọn phòng.");
            return result;
        }
        Phong phong = phongService.findById(maPhong);
        if (phong == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy phòng #" + maPhong);
            return result;
        }
        if (!"Trong".equalsIgnoreCase(phong.getTrangThai()) && !"Trống".equalsIgnoreCase(phong.getTrangThai())) {
            result.put("ok", false);
            result.put("loi", "Phòng hiện không ở trạng thái Trống nên không thể đặt tại quầy (nhận phòng ngay).");
            return result;
        }

        if (checkin == null || checkout == null) {
            result.put("ok", false);
            result.put("loi", "Vui lòng chọn ngày nhận và ngày trả phòng.");
            return result;
        }
        LocalDate homNay = LocalDate.now();
        // Khac voi "Len lich dat phong": dat tai quay bat buoc nhan phong NGAY HOM
        // NAY vi khach dang co mat truc tiep, khong duoc chon ngay trong tuong lai.
        if (!checkin.isEqual(homNay)) {
            result.put("ok", false);
            result.put("loi", "Đặt phòng tại quầy chỉ áp dụng khi khách nhận phòng ngay hôm nay.");
            return result;
        }
        if (!checkout.isAfter(checkin)) {
            result.put("ok", false);
            result.put("loi", "Ngày trả phòng phải sau ngày nhận phòng ít nhất 1 ngày.");
            return result;
        }

        int slNguoiLon = songuoiLon != null ? songuoiLon : 1;
        int slTreEm = sotreEm != null ? sotreEm : 0;
        if (slNguoiLon < 1) {
            result.put("ok", false);
            result.put("loi", "Cần ít nhất 1 người lớn.");
            return result;
        }
        if (phong.getLoaiPhong() != null && phong.getLoaiPhong().sucChuaToiDa > 0
                && (slNguoiLon + slTreEm) > phong.getLoaiPhong().sucChuaToiDa) {
            result.put("ok", false);
            result.put("loi", "Số khách (" + (slNguoiLon + slTreEm) + ") vượt quá sức chứa tối đa của phòng ("
                    + phong.getLoaiPhong().sucChuaToiDa + ").");
            return result;
        }

        LocalDateTime ngayNhan = LocalDateTime.now();
        LocalDateTime ngayTra = checkout.atTime(12, 0);

        java.util.Set<Integer> maPhongDaKhoa = phongService.findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra);
        if (maPhongDaKhoa.contains(maPhong)) {
            result.put("ok", false);
            result.put("loi", "Phòng đã có lịch trong khoảng ngày đã chọn.");
            return result;
        }

        KhuyenMai km = null;
        if (khuyenMaiCode != null && !khuyenMaiCode.isBlank()) {
            List<KhuyenMai> kms = khuyenMaiService.findbyNameVoucher(khuyenMaiCode.trim());
            km = kms.isEmpty() ? null : kms.get(0);
            if (km == null) {
                result.put("ok", false);
                result.put("loi", "Mã khuyến mại \"" + khuyenMaiCode.trim() + "\" không tồn tại hoặc không còn hoạt động.");
                return result;
            }
        }

        DatPhong dp = new DatPhong();
        dp.setHoten(hoTen);
        dp.setEmail(email);
        dp.setSdt(sdt);
        dp.setMa_cccd(cccd.trim());
        dp.setNgaydatPhong(ngayNhan);
        dp.setNgaytraPhong(ngayTra);
        dp.setSonguoiLon(slNguoiLon);
        dp.setSotreEm(slTreEm);
        dp.setYeuCauThem(ghiChu);
        // Dat tai quay = khach nhan phong ngay lap tuc, khac voi "Len lich dat
        // phong" (chi "Da xac nhan", cho check-in sau).
        dp.setTrangThai("Da nhan phong");
        dp.setNgayTao(LocalDateTime.now());
        dp.setKm(km);
        dp.setNv(nhanVienXuLy);

        DatPhong savedDp = datPhongService.save(dp);

        BigDecimal giaApDung = phong.getGiaMoiDem();
        if (km != null) {
            giaApDung = tinhGiaSauGiam(giaApDung, km);
        }
        BigDecimal phuPhiNgoaiGio = phongService.calculateExtraFeeFor(maPhong, ngayNhan, ngayTra);

        ChiTietDatPhong ctdp = new ChiTietDatPhong();
        ctdp.setD(savedDp);
        ctdp.setP(phong);
        ctdp.setGiaMoiDem(phong.getGiaMoiDem());
        ctdp.setGiaKhiDat(giaApDung);
        ctdp.setPhuPhi(phuPhiNgoaiGio);
        chiTietDatPhongService.save(ctdp);

        // Phong chuyen thang sang "Dang su dung" (nhan phong tuc thi), khac voi
        // "Len lich dat phong" chi chuyen sang "Da dat truoc".
        phong.setTrangThai("Dang su dung");
        phongService.save1(phong);

        BigDecimal amountPhong = giaApDung;
        BigDecimal amountDv = BigDecimal.ZERO;

        long tongPhut = java.time.Duration.between(ngayNhan, ngayTra).toMinutes();
        LocalDateTime ngaySuDungFiller = ngayNhan.plusMinutes(tongPhut / 2);

        if (dichVuIds != null) {
            for (Integer maDichVu : dichVuIds) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;

                BigDecimal thanhTien = dv.getGia();
                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setDatPhong(savedDp);
                ct.setDv(dv);
                ct.setSoluong(1);
                ct.setDonGia(thanhTien);
                ct.setNgay_su_dung(ngaySuDungFiller);
                ctdvService.save(ct);

                amountDv = amountDv.add(thanhTien);
            }
        }

        BigDecimal vatCd = new BigDecimal("0.10");
        BigDecimal tongTienTruocVat = amountPhong.add(amountDv);
        BigDecimal tienVat = tongTienTruocVat.multiply(vatCd).setScale(0, RoundingMode.HALF_UP);
        BigDecimal tongCong = tongTienTruocVat.add(tienVat);

        BigDecimal daThu = tienKhachTra != null ? tienKhachTra : BigDecimal.ZERO;
        if (daThu.compareTo(BigDecimal.ZERO) < 0) daThu = BigDecimal.ZERO;
        if (daThu.compareTo(tongCong) > 0) daThu = tongCong;

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(savedDp);
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(BigDecimal.ZERO);
        hd.setTienVat(tienVat);
        hd.setTongTien(tongCong);
        hd.setDaThanhToan(daThu);
        hd.setGhiChu("Dat phong tai quay tu So Do Phong, ma don: " + savedDp.getId());
        HoaDon hoaDonDaLuu = hoaDonService.saveWithPaymentStatusCheck(hd);

        if (daThu.compareTo(BigDecimal.ZERO) > 0) {
            ThanhToan tt = new ThanhToan();
            tt.setH(hd);
            tt.setPhuongThuc("tien-mat".equals(phuongThucThanhToan) ? "Tien Mat" : "Chuyen Khoan");
            tt.setSoTien(daThu);
            tt.setTrangThai("Thanh cong");
            tt.setNgaythanhToan(LocalDateTime.now());
            tt.setGichu("Thu tien luc dat phong tai quay tu So Do Phong");
            tt.setNv(nhanVienXuLy);
            thanhToanService.save(tt);
        }

        BigDecimal conLai = defaultMoney(hoaDonDaLuu.getTongTien()).subtract(defaultMoney(hoaDonDaLuu.getDaThanhToan()));
        if (conLai.compareTo(BigDecimal.ZERO) > 0) {
            bookingEmailService.guiEmailYeuCauThanhToan(
                    savedDp.getId(),
                    "Đặt phòng thành công - Đơn #" + savedDp.getId(),
                    "Đơn đặt phòng #" + savedDp.getId() + " của quý khách đã được tạo tại quầy và đã nhận phòng. "
                            + "Quý khách vui lòng thanh toán phần còn lại.",
                    conLai
            );
        }

        result.put("ok", true);
        result.put("maDatPhong", savedDp.getId());
        result.put("tongTien", tongCong);
        result.put("daThu", daThu);
        return result;
    }

    /**
     * Chuyển 1 phòng sang trạng thái "Bảo trì" (dùng cho menu chuột phải ở Sơ đồ
     * phòng). Chỉ đổi trạng thái hiển thị, KHÔNG đổi cờ hoạt động của phòng.
     */
    @PostMapping("/so-do-phong/bao-tri/{maPhong}")
    public String batDauBaoTri(@PathVariable int maPhong, RedirectAttributes redirectAttributes) {
        Phong p = phongService.findById(maPhong);
        if (p == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy phòng #" + maPhong);
            return "redirect:/nhan-su/so-do-phong";
        }
        p.setTrangThai("Bao tri");
        p.setNgayCapNhat(LocalDateTime.now());
        phongService.save1(p);
        return "redirect:/nhan-su/so-do-phong";
    }

    /**
     * Kết thúc bảo trì, đưa phòng về trạng thái "Trống".
     */
    @PostMapping("/so-do-phong/ket-thuc-bao-tri/{maPhong}")
    public String ketThucBaoTri(@PathVariable int maPhong, RedirectAttributes redirectAttributes) {
        Phong p = phongService.findById(maPhong);
        if (p == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy phòng #" + maPhong);
            return "redirect:/nhan-su/so-do-phong";
        }
        p.setTrangThai("Trong");
        p.setNgayCapNhat(LocalDateTime.now());
        phongService.save1(p);
        return "redirect:/nhan-su/so-do-phong";
    }

    /**
     * Quy đổi trạng thái nội bộ của Phong (đang dùng cho nghiệp vụ đặt phòng) sang
     * 1 trong 4 trạng thái hiển thị của Sơ đồ phòng. Phòng ngưng hoạt động luôn
     * hiển thị "Bảo trì"; phòng có trạng thái nhắc tới "don"/"dọn" hiển thị
     * "Đang dọn"; các phòng đang được giữ/sử dụng hiển thị "Đang sử dụng";
     * còn lại mặc định "Trống".
     */
    private String suyRaTrangThaiHienThi(Phong p) {
        if (!p.isHoatDong()) {
            return "Bảo trì";
        }
        String tt = p.getTrangThai() == null ? "" : p.getTrangThai().toLowerCase();
        if (tt.contains("bao tri") || tt.contains("bảo trì") || tt.contains("maintenance")) {
            return "Bảo trì";
        }
        if (tt.contains("don") || tt.contains("dọn") || tt.contains("cleaning")) {
            return "Đang dọn";
        }
        if (tt.contains("su dung") || tt.contains("sử dụng") || tt.contains("dat truoc") || tt.contains("đặt trước")) {
            return "Đang sử dụng";
        }
        return "Trống";
    }

    private String lopCssTheoTrangThai(String trangThaiHienThi) {
        switch (trangThaiHienThi) {
            case "Đang sử dụng": return "sdp-status-inuse";
            case "Đang dọn": return "sdp-status-cleaning";
            case "Bảo trì": return "sdp-status-maintenance";
            default: return "sdp-status-empty";
        }
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

        // Hiển thị TẤT CẢ phòng (kể cả "Dang su dung") để có thể đặt trước cho khách,
        // kèm thông tin các khoảng đang bị giữ chỗ để nhân viên biết phòng nào đang trống/khi nào trống.
        List<Phong> tatCaPhong = phongService.findAllPhong();
        Map<Integer, RoomBookingGuardDTO> roomGuards = phongService.buildRoomGuards(tatCaPhong);

        model.addAttribute("phongTrongList", tatCaPhong);
        model.addAttribute("roomGuards", roomGuards);
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

        // JSON riêng cho danh sách phòng kèm TOÀN BỘ khoảng ngày đang bị giữ chỗ (không chỉ 1 đơn
        // "gần nhất" như trước), dùng để JS tìm kiếm phòng theo thời gian nhận phòng mong muốn
        // (input datetime-local) và validate overlap chính xác với từng đơn.
        StringBuilder rb = new StringBuilder("[");
        for (int i = 0; i < tatCaPhong.size(); i++) {
            Phong p = tatCaPhong.get(i);
            RoomBookingGuardDTO guard = roomGuards.get(p.getMaPhong());
            String trangThaiDon = guard != null ? guard.getTrangThaiDonGanNhat() : null;

            // Build mảng con "khoaLich": danh sách toàn bộ khoảng đang giữ chỗ của phòng này
            StringBuilder khoaLichArr = new StringBuilder("[");
            if (guard != null) {
                List<su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO> danhSach = guard.getDanhSachKhoaLich();
                for (int j = 0; j < danhSach.size(); j++) {
                    su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO k = danhSach.get(j);
                    if (j > 0) khoaLichArr.append(",");
                    khoaLichArr.append("{")
                            .append("\"tu\":\"").append(k.getNgayBatDau() != null ? k.getNgayBatDau() : "").append("\",")
                            .append("\"den\":\"").append(k.getNgayKetThuc() != null ? k.getNgayKetThuc() : "").append("\",")
                            .append("\"trangThai\":\"").append(escapeJson(k.getTrangThaiDon())).append("\"")
                            .append("}");
                }
            }
            khoaLichArr.append("]");

            if (i > 0) rb.append(",");
            rb.append("{")
                    .append("\"maPhong\":").append(p.getMaPhong()).append(",")
                    .append("\"trangThai\":\"").append(escapeJson(p.getTrangThai())).append("\",")
                    .append("\"trangThaiDon\":").append(trangThaiDon == null ? "null" : "\"" + escapeJson(trangThaiDon) + "\"").append(",")
                    .append("\"khoaLich\":").append(khoaLichArr)
                    .append("}");
        }
        rb.append("]");
        model.addAttribute("roomStatusJson", rb.toString());

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
            if (phong == null) {
                continue;
            }
            // Cho phép đặt cả phòng "Dang su dung" (đặt trước cho khách sắp tới),
            // miễn là guard cho biết phòng vẫn coTheDat (không bị khóa hẳn).
            RoomBookingGuardDTO guard = phongService.buildRoomGuardFor(maPhong);
            if (guard == null || !guard.isCoTheDat()) {
                continue;
            }
            ChiTietDatPhong ctdp = new ChiTietDatPhong();
            ctdp.setD(savedDp);
            ctdp.setP(phong);
            ctdp.setGiaMoiDem(phong.getGiaMoiDem());

            BigDecimal giaApDung = phong.getGiaMoiDem();

            if (maKhuyenMai != null) {
                KhuyenMai kmDon = khuyenMaiService.findbyId(maKhuyenMai);
                if (kmDon != null) {
                    giaApDung = tinhGiaSauGiam(giaApDung, kmDon);
                }
            }

            LocalDateTime ngayNhanCt = ngaydatPhong.atStartOfDay();
            LocalDateTime ngayTraCt = ngaytraPhong.atTime(12, 0);
            BigDecimal phuPhiNgoaiGio = phongService.calculateExtraFeeFor(phong.getMaPhong(), ngayNhanCt, ngayTraCt);

            ctdp.setGiaKhiDat(giaApDung);
            ctdp.setPhuPhi(phuPhiNgoaiGio);
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
        hoaDonService.saveWithPaymentStatusCheck(hd);

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
    /**
     * Xử lý đổi phòng từ form trong trang chi tiết đơn đặt phòng (nhân viên).
     * Mirror đầy đủ logic với AdminDatPhongController.doPhong — cùng validate,
     * cùng cập nhật ChiTietDatPhong + Phong.trangThai + HoaDon.
     *
     * Body:
     *   - ctdpIds: List<Integer> — id các ChiTietDatPhong muốn đổi
     *   - newRoomIds: List<Integer> — phòng mới tương ứng (cùng index)
     *   - newCccds: List<String> — CCCD mới (để trống = giữ nguyên)
     *   - lyDoDoi: String — lý do đổi phòng (bắt buộc)
     */
    @PostMapping("/dat-phong/chi-tiet/{id}/doi-phong")
    @Transactional
    public String doPhong(@PathVariable Integer id,
                          @RequestParam("ctdpIds") List<Integer> ctdpIds,
                          @RequestParam("newRoomIds") List<Integer> newRoomIds,
                          @RequestParam(value = "newCccds", required = false) List<String> newCccds,
                          @RequestParam("lyDoDoi") String lyDoDoi,
                          @RequestParam(value = "fromCheckin", required = false, defaultValue = "false") boolean fromCheckin,
                          RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }
        // Validate trạng thái đơn - cho phep doi phong o cac trang thai:
        //   - "Yeu cau dat phong" (NV doi truoc khi xac nhan yeu cau)
        //   - "Cho xac nhan", "Da xac nhan", "Da nhan phong" (flow binh thuong)
        String trangThai = datPhong.getTrangThai();
        boolean choPhepDoi =
                "Yeu cau dat phong".equals(trangThai)
                        || "Cho xac nhan".equals(trangThai)
                        || "Da xac nhan".equals(trangThai)
                        || "Da nhan phong".equals(trangThai);
        if (!choPhepDoi) {
            redirectAttributes.addFlashAttribute("error",
                    "Trang thai don '" + trangThai + "' khong cho phep doi phong.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        // Hóa đơn đã xuất PDF -> không cho sửa
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the doi phong.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        // Lý do bắt buộc
        if (lyDoDoi == null || lyDoDoi.trim().length() < 5) {
            redirectAttributes.addFlashAttribute("error", "Ly do doi phong phai co it nhat 5 ky tu.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        // Phải tick ít nhất 1 dòng và danh sách phòng mới khớp độ dài
        if (ctdpIds == null || ctdpIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui long chon it nhat 1 phong de doi.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        if (newRoomIds == null || newRoomIds.size() != ctdpIds.size()) {
            redirectAttributes.addFlashAttribute("error", "Danh sach phong moi khong khop.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }

        // Số đêm giữ nguyên (khoảng ngày đơn không đổi)
        long soDem = Math.max(1, ChronoUnit.DAYS.between(
                datPhong.getNgaydatPhong().toLocalDate(),
                datPhong.getNgaytraPhong().toLocalDate()));

        // Gom CCCD mới theo index
        Map<Integer, String> cccdMoiTheoIndex = new HashMap<>();
        if (newCccds != null) {
            for (int i = 0; i < newCccds.size(); i++) {
                cccdMoiTheoIndex.put(i, newCccds.get(i));
            }
        }

        BigDecimal chenhLechTong = BigDecimal.ZERO;
        int soPhongDoi = 0;
        List<String> loiTheoDong = new ArrayList<>();

        for (int i = 0; i < ctdpIds.size(); i++) {
            int ctdpId = ctdpIds.get(i);
            Integer newRoomId = newRoomIds.get(i);
            String cccdMoiRaw = cccdMoiTheoIndex.get(i);
            if (newRoomId == null) {
                loiTheoDong.add("Dong #" + ctdpId + ": chua chon phong moi.");
                continue;
            }
            ChiTietDatPhong ct = chiTietDatPhongService.findbyId(ctdpId);
            if (ct == null || ct.getD() == null || ct.getD().getId() != id) {
                loiTheoDong.add("Dong #" + ctdpId + ": khong thuoc don dat phong nay.");
                continue;
            }
            if (ct.getP() != null && ct.getP().getMaPhong() == newRoomId) {
                loiTheoDong.add("Dong '" + ct.getP().getSoPhong() + "': phong moi trung phong cu.");
                continue;
            }
            Phong phongMoi = phongService.findById(newRoomId);
            if (phongMoi == null || !phongMoi.isHoatDong()) {
                loiTheoDong.add("Phong moi #" + newRoomId + " khong ton tai hoac da ngung hoat dong.");
                continue;
            }
            // Check overlap khoang ngay voi don khac dang giu phong moi (Cho xac nhan /
            // Da xac nhan / Da nhan phong). Ham hasBookingNotCheckout() chi check Da nhan
            // phong nen bi "lot" cac don Cho/Da xac nhan — phai dung helper overlap moi.
            StringBuilder overlapErr = new StringBuilder();
            if (coOverlapPhongMoi(phongMoi.getMaPhong(), id,
                    datPhong.getNgaydatPhong(), datPhong.getNgaytraPhong(), overlapErr)) {
                loiTheoDong.add(overlapErr.toString());
                continue;
            }
            // Lưu giá cũ để tính chênh lệch
            BigDecimal giaKhiDatCu = ct.getGiaKhiDat() != null ? ct.getGiaKhiDat() : BigDecimal.ZERO;
            BigDecimal phuPhiCu = ct.getPhuPhi() != null ? ct.getPhuPhi() : BigDecimal.ZERO;

            // Lưu phòng cũ để cập nhật trạng thái phòng sau
            Phong phongCu = ct.getP();

            // Tính giá mới
            BigDecimal giaMoiDemMoi = phongMoi.getGiaMoiDem();
            BigDecimal giaKhiDatMoi = giaMoiDemMoi.multiply(BigDecimal.valueOf(soDem));
            BigDecimal phuPhiMoi = phongService.calculateExtraFeeFor(
                    phongMoi.getMaPhong(), datPhong.getNgaydatPhong(), datPhong.getNgaytraPhong());

            // Cập nhật ChiTietDatPhong
            ct.setP(phongMoi);
            ct.setGiaMoiDem(giaMoiDemMoi);
            ct.setGiaKhiDat(giaKhiDatMoi);
            ct.setPhuPhi(phuPhiMoi);
            chiTietDatPhongService.save(ct);

            // Cập nhật trạng thái phòng cũ: nếu còn đơn khác giữ -> "Da dat truoc", ngược lại -> "Trong"
            if (phongCu != null) {
                if (datPhongService.hasBookingNotCheckout(phongCu.getMaPhong(), id)) {
                    phongCu.setTrangThai("Da dat truoc");
                } else {
                    phongCu.setTrangThai("Trong");
                }
                phongService.save1(phongCu);
            }
            // Cập nhật trạng thái phòng mới
            if ("Da nhan phong".equals(trangThai)) {
                phongMoi.setTrangThai("Dang su dung");
            } else {
                phongMoi.setTrangThai("Trong");
            }
            phongService.save1(phongMoi);

            // Tính chênh lệch: phần chênh giữa tổng tiền phòng (tiền phòng + phụ phí) mới và cũ
            BigDecimal chenhPhong = giaKhiDatMoi.subtract(giaKhiDatCu);
            BigDecimal chenhPhuPhi = phuPhiMoi.subtract(phuPhiCu);
            chenhLechTong = chenhLechTong.add(chenhPhong).add(chenhPhuPhi);

            soPhongDoi++;
        }

        if (!loiTheoDong.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Khong the doi " + loiTheoDong.size() + " phong: " + String.join(" | ", loiTheoDong));
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }

        // Cập nhật hóa đơn (nếu có)
        if (chenhLechTong.signum() != 0) {
            HoaDon hd = hoaDonService.findByDatPhongId(id);
            if (hd != null) {
                hd.setTienPhong(hd.getTienPhong() == null ? BigDecimal.ZERO : hd.getTienPhong());
                hd.setTienPhong(hd.getTienPhong().add(chenhLechTong).setScale(2, RoundingMode.HALF_UP));
                hd.setTongTien(hd.getTongTien() == null ? BigDecimal.ZERO : hd.getTongTien());
                hd.setTongTien(hd.getTongTien().add(chenhLechTong).setScale(2, RoundingMode.HALF_UP));
                hd.setNgayCapNhat(LocalDateTime.now());
                hoaDonService.saveWithPaymentStatusCheck(hd);
            }
        }

        datPhong.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(datPhong);

        String chenhLechStr = chenhLechTong.signum() > 0
                ? "+ " + defaultMoney(chenhLechTong).toPlainString() + " VND"
                : defaultMoney(chenhLechTong).toPlainString() + " VND";
        redirectAttributes.addFlashAttribute("thanhCongCapNhat",
                "Da doi thanh cong " + soPhongDoi + " phong. Chenh lech: " + chenhLechStr + ". Ly do: " + lyDoDoi.trim());

        // Nếu đổi phòng từ trang check-in, redirect về check-in, ngược lại về chi tiết
        if (fromCheckin) {
            return "redirect:/nhan-su/check-in?id=" + id;
        }
        // Neu don dang o trang thai "Yeu cau dat phong" -> redirect ve trang chi tiet yeu cau
        if ("Yeu cau dat phong".equals(trangThai)) {
            return "redirect:/nhan-su/yeu-cau-dat-phong/chi-tiet/" + id;
        }
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    /**
     * Kiem tra khoang ngay [ngayDat, ngayTra) cua don dang doi co bi giao (overlap)
     * voi bat ky don nao khac dang giu phong moi khong. Tra ve true neu overlap,
     * dong thoi ghi thong bao loi vao errorOut (de hien thi trong loiTheoDong).
     *
     * So sanh voi TAT CA cac don con hieu luc (Cho xac nhan / Da xac nhan /
     * Da nhan phong) chu khong chi Da nhan phong nhu hasBookingNotCheckout().
     * Bo qua don hien tai (maDatPhongHienTai) de tranh tu overlap voi chinh minh.
     *
     * Logic overlap: aStart < bEnd && aEnd > bStart.
     */
    private boolean coOverlapPhongMoi(int maPhong, int maDatPhongHienTai,
                                      LocalDateTime ngayDat, LocalDateTime ngayTra,
                                      StringBuilder errorOut) {
        List<DatPhong> bookings = datPhongService.findRecentBookingsForPhong(maPhong);
        if (bookings == null || bookings.isEmpty()) return false;
        for (DatPhong dp : bookings) {
            if (dp == null || dp.getId() == maDatPhongHienTai) continue;
            // Chi xet cac trang thai dang giu phong that su (Da tra phong da giai phong)
            String tt = dp.getTrangThai();
            if (!"Yeu cau dat phong".equals(tt) && !"Cho xac nhan".equals(tt) && !"Da xac nhan".equals(tt) && !"Da nhan phong".equals(tt)) {
                continue;
            }
            LocalDateTime tu = dp.getNgaydatPhong();
            LocalDateTime den = dp.getNgaytraPhong();
            if (tu == null || den == null) continue;
            if (ngayDat.isBefore(den) && ngayTra.isAfter(tu)) {
                Phong p = phongService.findById(maPhong);
                String soPhong = p != null ? p.getSoPhong() : String.valueOf(maPhong);
                errorOut.append("Phong '").append(soPhong)
                        .append("' da bi don #").append(dp.getId())
                        .append(" (").append(tt).append(") giu tu ")
                        .append(tu.toLocalDate()).append(" den ")
                        .append(den.toLocalDate())
                        .append(", khong the doi vao khoang nay");
                return true;
            }
        }
        return false;
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
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the chinh sua.");
            return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
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
            return "redirect:/nhan-su/dat-phong/chi-tiet/"+id;
        }
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the chinh sua.");
            return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }

        List<ChiTietDatPhong> chiTietDatPhongListThu = chiTietDatPhongService.findByDatPhongId(id);
        BigDecimal tongPhuThuThu = BigDecimal.ZERO;
        for (ChiTietDatPhong ct : chiTietDatPhongListThu) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThuThu = tongPhuThuThu.add(ct.getPhuPhi());
            }
        }
        BigDecimal tongTienThucTeThu = tinhTongTienThucTe(id, hd, chiTietDatPhongListThu, tongPhuThuThu);

        BigDecimal daThanhToan = hd.getDaThanhToan() ==null ? BigDecimal.ZERO : hd.getDaThanhToan();
        BigDecimal conNo = tongTienThucTeThu.subtract(daThanhToan);
        if(soTien.compareTo(conNo) > 0){
            redirectAttributes.addFlashAttribute("error","Số tiền vượt quá số tiền còn thiếu");
            return "redirect:/nhan-su/dat-phong/chi-tiet/"+id;
        }
        // Dam bao hoa don phan anh dung tong tien thuc te (bao gom phu phi) truoc
        // khi ghi nhan khoan thu, de lan sau tinh "Con no" khong bi cong don phu phi.
        if (hd.getTongTien() == null || hd.getTongTien().compareTo(tongTienThucTeThu) < 0) {
            hd.setTongTien(tongTienThucTeThu);
        }
        if("Chuyen Khoan".equalsIgnoreCase(phuongthuc)){
            String baseUrl = request.getScheme() + "://"+request.getServerName() + ":"+request.getServerPort();
            String vnPayUrl = vnpayService.createOrder(soTien.longValue(),id,"ThuThemDichVu",baseUrl);
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
        hoaDonService.saveWithPaymentStatusCheck(hd);

        redirectAttributes.addFlashAttribute("success", "Đã thu " + soTien + " VND tiền mặt.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;

    }

    /* ===================== CHECK-IN (NHAN VIEN) ===================== */
    // Y nguyen AdminDatPhongController.checkinDp — chi khac return template path.
    @GetMapping({"/dat-phong/{id}/check-in", "/dat-phong/check-in"})
    public String checkinDp(@PathVariable(value = "id", required = false) Integer id,
                            @RequestParam(value = "ngay", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayChon,
                            @RequestParam(value = "thang", required = false) String thangRaw,
                            @RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "tuNgay", required = false) String tuNgayRaw,
                            @RequestParam(value = "denNgay", required = false) String denNgayRaw,
                            Model model,
                            RedirectAttributes redirectAttributes) {

        // Nếu có ?thang=YYYY-MM thì override lựa chọn tháng hiện tại
        LocalDate thangDiChuyen = parseThangParam(thangRaw);
        LocalDate ngayChonSauCung = (thangDiChuyen != null) ? thangDiChuyen : ngayChon;

        if (id == null || id <= 0) {
            buildCheckinList(model, ngayChonSauCung, q, tuNgayRaw, denNgayRaw);
            return "nhan-vien/check-in";
        }

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/dat-phong";
        }

        buildCheckinList(model, ngayChonSauCung, q, tuNgayRaw, denNgayRaw);
        buildCheckinChiTiet(dp, model);

        return "nhan-vien/check-in";
    }

    /** Parse ?thang=YYYY-MM trên URL. Trả về null nếu chuỗi rỗng / sai định dạng (fallback về tháng hiện tại). */
    private LocalDate parseThangParam(String thangRaw) {
        if (thangRaw == null || thangRaw.isBlank()) return null;
        try {
            YearMonth ym = YearMonth.parse(thangRaw.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
            return ym.atDay(1);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** Build tháng trước / tháng sau dạng chuỗi YYYY-MM để gắn vào URL của nút điều hướng. */
    private String thangLienKe(LocalDate thangHienThi, int delta) {
        if (thangHienThi == null) return null;
        YearMonth ym = YearMonth.from(thangHienThi).plusMonths(delta);
        return ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private void buildCheckinList(Model model, LocalDate ngayChon, String q,
                                  String tuNgayRaw, String denNgayRaw) {
        LocalDate thangNgay = (ngayChon != null) ? ngayChon : LocalDate.now();
        LocalDateTime thangHienThi = thangNgay.withDayOfMonth(1).atTime(LocalTime.now());
        boolean dangLocKhoangNgay = tuNgayRaw != null && !tuNgayRaw.isBlank();

        // tuNgay/denNgay: khoang loc don cho DANH SACH
        LocalDate tuNgay;
        LocalDate denNgay;
        if (dangLocKhoangNgay) {
            tuNgay = LocalDate.parse(tuNgayRaw);
            denNgay = LocalDate.parse(denNgayRaw);
        } else if (ngayChon != null) {
            // User da click 1 ngay tren lich -> chi loc don co ngaydatPhong = ngay do
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

        List<DatPhong> dsDon = datPhongService.findAll().stream()
                .filter(dp -> dp.getNgaydatPhong() != null)
                .filter(dp -> "Yeu cau dat phong".equals(dp.getTrangThai())
                        || "Cho xac nhan".equals(dp.getTrangThai())
                        || "Da xac nhan".equals(dp.getTrangThai())
                        || "Da nhan phong".equals(dp.getTrangThai()))
                .filter(dp -> !dp.getNgaydatPhong().toLocalDate().isBefore(tuNgay)
                        && !dp.getNgaydatPhong().toLocalDate().isAfter(denNgay))
                .filter(dp -> tuKhoa.isEmpty()
                        || (dp.getHoten() != null && dp.getHoten().toLowerCase().contains(tuKhoa))
                        || (dp.getSdt() != null && dp.getSdt().contains(tuKhoa))
                        || String.valueOf(dp.getId()).contains(tuKhoa))
                .sorted(comparing(DatPhong::getNgaydatPhong))
                .collect(Collectors.toList());

        Map<Integer, List<ChiTietDatPhong>> mapCtdp = new HashMap<>();
        for (DatPhong d : dsDon) {
            mapCtdp.put(d.getId(), chiTietDatPhongService.findByDatPhongId(d.getId()));
        }

        List<Map<String, Object>> dsNgayTrongThang = new ArrayList<>();
        LocalDate homNay = LocalDate.now();
        // Luon duyet theo tuNgayLich..denNgayLich (toan bo thang) de khong mat luoi
        // khi user da click 1 ngay bat ky.
        for (LocalDate d = tuNgayLich; !d.isAfter(denNgayLich); d = d.plusDays(1)) {
            LocalDate finalD = d;
            long soDon = datPhongService.findAll().stream()
                    .filter(x -> x.getNgaydatPhong() != null)
                    .filter(x -> "Yeu cau dat phong".equals(x.getTrangThai())
                            || "Cho xac nhan".equals(x.getTrangThai())
                            || "Da xac nhan".equals(x.getTrangThai())
                            || "Da nhan phong".equals(x.getTrangThai()))
                    .filter(x -> x.getNgaydatPhong().toLocalDate().equals(finalD))
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
        // Hai nút điều hướng tháng (chỉ dùng khi đang ở chế độ "Xem theo tháng")
        model.addAttribute("thangTruoc", thangLienKe(thangNgay, -1));
        model.addAttribute("thangSau", thangLienKe(thangNgay, 1));
    }

    private void buildCheckinChiTiet(DatPhong dp, Model model) {
        int id = dp.getId();
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> dichVuList = ctdvService.findByDatPhongId(id);

        Map<String, List<ChiTietDatPhong>> nhomTheoLoai = phongList.stream()
                .collect(Collectors.groupingBy(
                        ct -> (ct.getP() != null && ct.getP().getLoaiPhong() != null)
                                ? ct.getP().getLoaiPhong().getTenLoai() : "Chưa xác định",
                        LinkedHashMap::new, Collectors.toList()));

        // Phong nao dang bi khoa lich (chong lan ngay) voi dung khoang luu tru cua don
        // nay [ngaydatPhong, ngaytraPhong) (gio nhan 14:00 / tra 11:00 da duoc ap dung
        // luc tao don). Dung de xet phong nao THUC SU co the doi sang, thay vi chi
        // nhin trang thai tuc thoi (trangThai) cua phong.
        java.util.Set<Integer> maPhongDaKhoaLich = phongService.findMaPhongDaKhoaTrongKhoang(
                dp.getNgaydatPhong(), dp.getNgaytraPhong());

        List<NhomYeuCauPhongDTO> nhomYeuCauPhong = new ArrayList<>();
        for (Map.Entry<String, List<ChiTietDatPhong>> e : nhomTheoLoai.entrySet()) {
            List<SlotPhongDTO> slots = new ArrayList<>();
            for (ChiTietDatPhong ct : e.getValue()) {
                Phong pDaGan = ct.getP();
                boolean sanSang = pDaGan != null && "Trong".equals(pDaGan.getTrangThai());

                List<LoaiPhongDTO> loaiPhongOptions = new ArrayList<>();
                if (pDaGan != null && pDaGan.getLoaiPhong() != null) {
                    for (LoaiPhong lp : phongService.findAllLoai()) {
                        List<PhongTheoLoaiDTO> dsPhong = new ArrayList<>();
                        for (Phong p : phongService.findPhongTheoLoai(lp.getId())) {
                            if (p.getMaPhong() == pDaGan.getMaPhong()) continue;
                            // Kha dung theo chong lan ngay thuc su, khong con dua vao
                            // trangThai tuc thoi cua phong nua.
                            boolean khaDung = !maPhongDaKhoaLich.contains(p.getMaPhong());
                            dsPhong.add(new PhongTheoLoaiDTO(
                                    p.getMaPhong(), p.getSoPhong(), p.getSoTang(),
                                    p.getTrangThai(), khaDung,
                                    p.getGiaMoiDem()));
                        }
                        if (!dsPhong.isEmpty()) {
                            loaiPhongOptions.add(new LoaiPhongDTO(lp.getId(), lp.getTenLoai(), lp.getGiaCoBan(), dsPhong));
                        }
                    }
                }

                // Lấy danh sách tiện nghi của phòng
                List<TienNghi> tienNghiList = new ArrayList<>();
                if (pDaGan != null) {
                    tienNghiList = tienNghiPhongRepository.findByPhongMaPhong(pDaGan.getMaPhong())
                            .stream()
                            .map(tnp -> tnp.getTienNghi())
                            .collect(Collectors.toList());
                }

                slots.add(new SlotPhongDTO(
                        ct.getId(), pDaGan, sanSang, layCccdDaiDienPhong(ct.getId()), loaiPhongOptions, ct.getGiaKhiDat(), tienNghiList));
            }
            nhomYeuCauPhong.add(new NhomYeuCauPhongDTO(e.getKey(), slots));
        }

        BigDecimal tienPhong = phongList.stream()
                .map(ChiTietDatPhong::getGiaKhiDat)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienDichVu = dichVuList.stream()
                .map(Chi_tiet_dich_vu::getDonGia)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienGiam = BigDecimal.ZERO;
        BigDecimal tienVat = tienPhong.add(tienDichVu)
                .multiply(new BigDecimal("0.10"))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal tongTien = tienPhong.add(tienDichVu).add(tienVat).subtract(tienGiam);

        long soDem = 1;
        if (dp.getNgaydatPhong() != null && dp.getNgaytraPhong() != null) {
            soDem = ChronoUnit.DAYS.between(dp.getNgaydatPhong().toLocalDate(), dp.getNgaytraPhong().toLocalDate());
            if (soDem <= 0) soDem = 1;
        }

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        BigDecimal daCoc = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : null;

        TomTatDto tomTat = new TomTatDto(soDem, tienPhong, tienDichVu, tienGiam, tienVat, tongTien, daCoc);
        model.addAttribute("dp", dp);
        model.addAttribute("gioKhachTaiQuay", LocalDateTime.now());
        model.addAttribute("nhomYeuCauPhong", nhomYeuCauPhong);
        model.addAttribute("chiTietDichVuList", dichVuList);
        // 2 list dich vu: thuong + phat sinh — cho combobox 2 tab
        model.addAttribute("dichVuOptionsThuong", dichVuService.findActiveThuong());
        model.addAttribute("dichVuOptionsPhatSinh", dichVuService.findActivePhatSinh());
        // Giu lai de tuong thich nguoc (trang khac co the dang dung)
        model.addAttribute("dichVuOptions", dichVuService.findActivePhatSinh());
        model.addAttribute("tomTat", tomTat);
    }
}