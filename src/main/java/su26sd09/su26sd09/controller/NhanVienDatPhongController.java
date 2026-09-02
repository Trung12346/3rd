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
import su26sd09.su26sd09.dto.InvoicePricingResult;
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
    @Autowired private su26sd09.su26sd09.service.LichSuHoatDongService lichSuHoatDongService;
    @Autowired private JanitorCacheService janitorCacheService;
    @Autowired private su26sd09.su26sd09.repository.KhachHangRepository khachHangRepository;
    @Autowired private InvoicePricingService invoicePricingService;

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

        Sort sort = Sort.by(Sort.Order.desc("ngayTao"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        List<DatPhong> allFiltered = datPhongService.findAll(sort).stream()
                .filter(dp -> HuyDonConstants.DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT.contains(dp.getTrangThai()))
                .collect(Collectors.toList());

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

        BigDecimal tongPhuThu = BigDecimal.ZERO;
        for (ChiTietDatPhong ct : chiTietDatPhongList) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThu = tongPhuThu.add(ct.getPhuPhi());
            }
        }

        BigDecimal tongTienThucTe = tinhTongTienThucTe(id, hoaDon, chiTietDatPhongList, tongPhuThu);
        BigDecimal daThanhToanHd = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal conNoThucTe = tongTienThucTe.subtract(daThanhToanHd);

        model.addAttribute("tongTienThucTe", tongTienThucTe);
        model.addAttribute("conNoThucTe", conNoThucTe);
        model.addAttribute("daThanhToanHd", daThanhToanHd);

        List<Phong> tatCaPhong = phongService.findAllPhong();
        // Chi hien cac phong con TRONG thuc su trong dung khoang ngay o cua
        // don nay (dung chung "room availability engine" voi
        // searchLoaiPhongKhaDung()/assignRoomsForType() ben PhongService,
        // dua tren chong lan lich qua findMaPhongDaKhoaTrongKhoang), thay vi
        // liet ke toan bo phong dang hoat dong nhu truoc.
        List<Phong> phongAvailableList = tatCaPhong;
        if (datPhong.getNgaydatPhong() != null && datPhong.getNgaytraPhong() != null) {
            Set<Integer> maPhongDaKhoaLich = phongService.findMaPhongDaKhoaTrongKhoang(
                    datPhong.getNgaydatPhong(), datPhong.getNgaytraPhong());
            phongAvailableList = tatCaPhong.stream()
                    .filter(p -> !maPhongDaKhoaLich.contains(p.getMaPhong()))
                    .collect(Collectors.toList());
        }
        List<Integer> phongDangDungTrongDon = new ArrayList<>();
        for (ChiTietDatPhong ct : chiTietDatPhongList) {
            if (ct != null && ct.getP() != null) {
                phongDangDungTrongDon.add(ct.getP().getMaPhong());
            }
        }
        model.addAttribute("phongAvailableList", phongAvailableList);
        model.addAttribute("phongDangDungTrongDon", phongDangDungTrongDon);

        Map<Integer, String> cccdPhongMap = new HashMap<>();
        for (ChiTietDatPhong ct : chiTietDatPhongList) {
            if (ct != null) {
                cccdPhongMap.put(ct.getId(), layCccdDaiDienPhong(ct.getId()));
            }
        }
        model.addAttribute("cccdPhongMap", cccdPhongMap);
        model.addAttribute("roomStatusJson", "[" + phongService.buildRoomStatusJson(tatCaPhong) + "]");

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

        long soDem = Math.max(1, ChronoUnit.DAYS.between(
                datPhong.getNgaydatPhong().toLocalDate(),
                datPhong.getNgaytraPhong().toLocalDate()));
        model.addAttribute("soDem", soDem);

        boolean choPhepDoiPhong = "Yeu cau dat phong".equals(datPhong.getTrangThai())
                || "Cho xac nhan".equals(datPhong.getTrangThai())
                || "Da xac nhan".equals(datPhong.getTrangThai())
                || "Da nhan phong".equals(datPhong.getTrangThai());
        model.addAttribute("choPhepDoiPhong", choPhepDoiPhong);

        model.addAttribute("datPhong", datPhong);
        model.addAttribute("chiTietDatPhongList", chiTietDatPhongList);

        List<su26sd09.su26sd09.entity.Chi_tiet_dich_vu> ctdvList = ctdvService.findByDatPhongId(id);
        Map<Integer, String> loaiDichVuMap = new HashMap<>();
        Map<Integer, BigDecimal> giaDonViMap = new HashMap<>();
        for (su26sd09.su26sd09.entity.Chi_tiet_dich_vu ct : ctdvList) {
            String loai = "THUONG";
            String dvTen = "(null)";
            String dvLoai = "(null)";
            if (ct != null && ct.getDv() != null) {
                dvTen = ct.getDv().getTen_dich_vu();
                dvLoai = ct.getDv().getLoaiDv();
                if ("PHAT_SINH".equalsIgnoreCase(dvLoai)) {
                    loai = "PHAT_SINH";
                } else if ("Phu thu".equalsIgnoreCase(dvLoai)) {
                    loai = "PHU_THU";
                }
            }
            loaiDichVuMap.put(ct.getId(), loai);

            int soLuong = (ct.getSoluong() != null && ct.getSoluong() > 0) ? ct.getSoluong() : 1;
            BigDecimal giaDonVi = (ct.getDonGia() != null)
                    ? ct.getDonGia().divide(BigDecimal.valueOf(soLuong), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            giaDonViMap.put(ct.getId(), giaDonVi);
        }
        model.addAttribute("chiTietDichVuList", ctdvList);
        model.addAttribute("loaiDichVuMap", loaiDichVuMap);
        model.addAttribute("giaDonViMap", giaDonViMap);
        model.addAttribute("dichVuList", dichVuService.findActiveThuong());
        model.addAttribute("kmJson", buildKhuyenMaiJson());
        model.addAttribute("tongPhuThu", tongPhuThu);

        boolean apDungKhachVang = HuyDonConstants.DP_TRANG_THAI_AP_DUNG_KHACH_VANG.contains(datPhong.getTrangThai());
        model.addAttribute("apDungChinhSachKhachVang", apDungKhachVang);
        if (apDungKhachVang) {
            model.addAttribute("hanCheckInHieuLuc", checkInExpirationCacheService.hanHieuLuc(datPhong));
            model.addAttribute("hanCheckInMacDinh", checkInExpirationCacheService.hanMacDinh(datPhong));
            model.addAttribute("daGiaHanCheckIn", checkInExpirationCacheService.coGiaHan(datPhong.getId()));
        }

        return "nhan-vien/chi-tiet-dat-phong";
    }

    @PostMapping("/dat-phong/chi-tiet/{id}/gia-han-checkin")
    public String giaHanCheckIn(@PathVariable Integer id,
                                @RequestParam("hanCheckInMoi") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime hanCheckInMoi,
                                Authentication authentication,
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Gia han check-in cho don #" + id + " den " + hanCheckInMoi);

        redirectAttributes.addFlashAttribute("thanhCongCapNhat",
                "Da gia han check-in cho don #" + id + " den " + hanCheckInMoi);
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    private BigDecimal tinhTongTienThucTe(Integer datPhongId, HoaDon hoaDon,
                                          List<ChiTietDatPhong> chiTietDatPhongList,
                                          BigDecimal tongPhuThu) {
        InvoicePricingResult gia = invoicePricingService.previewInvoice(
                datPhongId, hoaDon != null ? hoaDon.getK() : null);
        return gia.getTongTien()
                .add(tongPhuThu == null ? BigDecimal.ZERO : tongPhuThu);
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
                                        Authentication authentication,
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Cap nhat chi tiet don dat phong #" + id);

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

    private void capNhatDichVuDatPhong(DatPhong datPhong, List<Integer> dichVuIds,
                                       List<String> phatSinhTenList,
                                       List<String> phatSinhDonGiaList,
                                       List<String> phatSinhSoLuongList,
                                       List<String> phatSinhNgayList,
                                       List<String> phatSinhGhiChuList,
                                       Map<String, String> allParams) {
        ctdvService.deleteByDatPhongId(datPhong.getId());

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
                chiTiet.setDonGia(invoicePricingService.createServiceLineItemPrice(dichVu, soLuong));
                ctdvService.save(chiTiet);
            }
        }

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
                continue;
            }
            if (donGia == null || donGia.signum() <= 0) continue;

            int soLuong = parseIntOrDefault(soLuongStr, 1);
            LocalDateTime ngaySuDung = parseDateTimeOrNow(ngayStr);

            Dich_vu dichVuPhatSinh = dichVuService.findPhatSinhTheoTenVaGia(ten, donGia)
                    .orElseGet(() -> dichVuService.taoDichVuPhatSinhMoi(ten, donGia));

            Chi_tiet_dich_vu chiTiet = new Chi_tiet_dich_vu();
            chiTiet.setDatPhong(datPhong);
            chiTiet.setDv(dichVuPhatSinh);
            chiTiet.setSoluong(soLuong);
            chiTiet.setNgay_su_dung(ngaySuDung);
            chiTiet.setDonGia(invoicePricingService.createServiceLineItemPrice(dichVuPhatSinh, soLuong));
            chiTiet.setGhichu(ghiChu);
            ctdvService.save(chiTiet);
        }
    }

    private void capNhatHoaDonNeuCo(Integer maDatPhong, BigDecimal tienPhong, BigDecimal tienDichVu,
                                    BigDecimal tienGiam, BigDecimal tienVat, BigDecimal tongCong, KhuyenMai km) {
        HoaDon hoaDon = hoaDonService.findByDatPhongId(maDatPhong);
        if (hoaDon == null) {
            return;
        }

        hoaDon.setK(km);
        hoaDon.setTienPhong(defaultMoney(tienPhong));
        hoaDon.setTienDichVu(defaultMoney(tienDichVu));
        hoaDon.setTienGiam(defaultMoney(tienGiam));
        hoaDon.setTienVat(defaultMoney(tienVat));
        hoaDon.setTongTien(defaultMoney(tongCong));
        hoaDon.setNgayCapNhat(LocalDateTime.now());
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
                .filter(dp ->
                        (maTraCuu != null && !maTraCuu.trim().isEmpty())
                                || HuyDonConstants.DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT.contains(dp.getTrangThai()))
                .collect(Collectors.toList());

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
            @RequestParam(required = false) Integer maDichVuThem,
            @RequestParam(required = false, defaultValue = "1") Integer soLuongDichVuThem,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication,
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

        if ("Da tra phong".equals(trangThai)) {
            return "redirect:/nhan-su/checkout/" + id;
        }

        dp.setTrangThai(trangThai);
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        lichSuHoatDongService.ghiLogAn(authentication,
                "Da nhan phong".equals(trangThai)
                        ? su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_IN
                        : su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Chuyen trang thai don #" + id + " sang \"" + trangThai + "\"");

        List<ChiTietDatPhong> ctdpList = chiTietDatPhongService.findByDatPhongId(id);

        if ("Da nhan phong".equals(trangThai)) {
            for (ChiTietDatPhong ct : ctdpList) {
                Phong p = ct.getP();
                if (p == null) continue;
                p.setTrangThai("Dang su dung");
                phongService.save1(p);
            }

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
                chiTiet.setDonGia(invoicePricingService.createServiceLineItemPrice(dichVu, sl));
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

    @GetMapping("/yeu-cau-dat-phong")
    public String quanLyYeuCauDatPhong(Model model) {
        List<DatPhong> dsYeuCau = datPhongService.findAll().stream()
                .filter(dp -> su26sd09.su26sd09.constants.HuyDonConstants.DP_YEU_CAU_DAT_PHONG
                        .equals(dp.getTrangThai()))
                .sorted(comparing(DatPhong::getNgayTao,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .collect(Collectors.toList());

        List<Map<String, Object>> yeuCauList = new ArrayList<>();
        for (DatPhong dp : dsYeuCau) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("datPhong", dp);
            item.put("chiTiet", chiTietDatPhongService.findByDatPhongId(dp.getId()));
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

    @GetMapping("/yeu-cau-dat-phong/chi-tiet/{id}")
    public String chiTietYeuCau(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay yeu cau #" + id);
            return "redirect:/nhan-su/yeu-cau-dat-phong";
        }

        List<ChiTietDatPhong> chiTietList = chiTietDatPhongService.findByDatPhongId(id);
        int tongSucChua = chiTietList.stream()
                .filter(ct -> ct.getP() != null && ct.getP().getLoaiPhong() != null)
                .mapToInt(ct -> ct.getP().getLoaiPhong().getSucChuaToiDa())
                .sum();
        int tongNguoi = (datPhong.getSonguoiLon() != 0 ? datPhong.getSonguoiLon() : 0)
                + (datPhong.getSotreEm() != 0 ? datPhong.getSotreEm() : 0);
        boolean canhBaoSucChua = tongNguoi > tongSucChua;

        List<Chi_tiet_dich_vu> dichVuList = ctdvService.findByDatPhongId(id);
        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        List<ThanhToan> lichSuThanhToan = new ArrayList<>();
        if (hoaDon != null) {
            lichSuThanhToan = thanhToanService.findAllByHoaDonId(hoaDon.getId());
        }
        KhuyenMai khuyenMai = datPhong.getKm();

        List<Phong> tatCaPhong = phongService.findAllPhong();

        Map<Integer, RoomBookingGuardDTO> bookingGuardByPhong = phongService.buildRoomGuards(tatCaPhong);
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

    @PostMapping("/dat-phong/xac-nhan-yeu-cau/{id}")
    public String xacNhanYeuCau(@PathVariable Integer id, Authentication authentication, RedirectAttributes redirectAttributes) {
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

        dp.setTrangThai(su26sd09.su26sd09.constants.HuyDonConstants.DP_CHO_XAC_NHAN);
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        try {
            bookingEmailService.guiEmailXacNhanYeuCau(id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_XAC_NHAN_YEU_CAU,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Xác nhận yêu cầu đặt phòng #" + id);

        redirectAttributes.addFlashAttribute("success",
                "Da xac nhan yeu cau dat phong #" + id + ". Don da chuyen sang trang thai 'Cho xac nhan'.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    @PostMapping("/dat-phong/huy-yeu-cau/{id}")
    public String huyYeuCau(@PathVariable Integer id, Authentication authentication, RedirectAttributes redirectAttributes) {
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_HUY_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Từ chối/hủy yêu cầu đặt phòng #" + id + " và giải phóng phòng");

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

        KetQuaHuyDonDTO ketQua = huyDonService.huyDon(id);

        lichSuHoatDongService.ghiLogAn(auth,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_HUY_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Huy don dat phong #" + id + ": " + ketQua.getThongBao());

        if (ketQua.isCanHoanTien()) {
            String pt = (phuongThucHoan == null || phuongThucHoan.isBlank())
                    ? HuyDonConstants.PT_TIEN_MAT
                    : phuongThucHoan.trim();
            NhanSu nvXuLy = auth == null ? null : nhanVienService.FindByemail(auth.getName());

            huyDonService.xacNhanHoanTien(
                    ketQua.getHoaDonId(),
                    pt,
                    null,
                    null,
                    null,
                    "Tu dong xac nhan hoan tien khi Huy phong (nhan vien)",
                    null,
                    nvXuLy);

            redirectAttributes.addFlashAttribute("thongBao",
                    ketQua.getThongBao() + " Da tu dong xac nhan hoan tien (" + pt + ").");
        } else {
            redirectAttributes.addFlashAttribute("thongBao", ketQua.getThongBao());
        }

        return "redirect:/nhan-su/dat-phong?page=" + page + "&size=" + size;
    }

    // ==================== CHECK-IN / CHECK-OUT / SƠ ĐỒ PHÒNG ====================

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

    @PostMapping("/so-do-phong/check-in/{maDatPhong}/giay-to")
    @ResponseBody
    public Map<String, Object> luuGiayToTuSoDoPhong(@PathVariable int maDatPhong,
                                                    @RequestParam(name = "data") String dataJson,
                                                    Authentication authentication) {
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

        List<Integer> chiTietHopLe = chiTietDatPhongService.findByDatPhongId(maDatPhong)
                .stream().map(ChiTietDatPhong::getId).collect(Collectors.toList());

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

        // ===== Canh bao doi soat CCCD (KHONG chan check-in) =====
        // Chi so sanh khi don la 1 nguoi lon, 1 phong (de tranh nham lan khi
        // nguoi dat khong phai la khach o - vd: dat ho, dat qua dai ly...),
        // va giay to dai dien la CCCD (khong ap dung cho ho chieu/nuoc ngoai).
        // Day CHI la canh bao + ghi log, khong chan luu giay to / check-in,
        // vi ma_cccd tren DatPhong chi la du lieu nguoi dat tu khai (khong
        // xac thuc that) nen khong du tin cay de chan cung.
        try {
            if (dp.getSonguoiLon() == 1 && chiTietHopLe.size() == 1
                    && dp.getMa_cccd() != null && !dp.getMa_cccd().isBlank()) {
                Integer chiTietId = chiTietHopLe.get(0);
                List<GiayTo> dsGiayToPhong = giayToRepo.findByChiTietDatPhong_Id(chiTietId);
                GiayTo daiDien = dsGiayToPhong.stream()
                        .filter(gt -> Boolean.TRUE.equals(gt.getCoDaiDien())
                                && "CCCD".equalsIgnoreCase(gt.getLoaiGiayTo()))
                        .findFirst()
                        .orElse(dsGiayToPhong.stream()
                                .filter(gt -> "CCCD".equalsIgnoreCase(gt.getLoaiGiayTo()))
                                .findFirst()
                                .orElse(null));

                if (daiDien != null && daiDien.getSoDinhDanh() != null && !daiDien.getSoDinhDanh().isBlank()) {
                    String maCccdDatPhong = dp.getMa_cccd().trim();
                    String soDinhDanhGiayTo = daiDien.getSoDinhDanh().trim();
                    if (!maCccdDatPhong.equalsIgnoreCase(soDinhDanhGiayTo)) {
                        String ghiChu = "Canh bao doi soat CCCD: so CCCD luc dat phong (\"" + maCccdDatPhong
                                + "\") khong khop voi so CCCD giay to check-in cua khach dai dien (\""
                                + soDinhDanhGiayTo + "\") - don #" + maDatPhong
                                + ", phong (chi_tiet_dat_phong #" + chiTietId + ").";

                        lichSuHoatDongService.ghiLogAn(authentication,
                                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_IN,
                                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                                maDatPhong, ghiChu);

                        result.put("canhBaoCccd", true);
                        result.put("canhBaoCccdMessage",
                                "Số CCCD/CMND lúc đặt phòng không khớp với giấy tờ check-in của khách." +
                                "Vui lòng kiểm tra lại trước khi tiếp tục.");
                    }
                }
            }
        } catch (Exception ignored) {
            // Canh bao chi la nghiep vu phu tro, khong duoc lam hong luong luu giay to chinh.
        }

        result.put("ok", true);
        return result;
    }

    @PostMapping("/so-do-phong/check-in/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkInTuSoDoPhong(@PathVariable int maDatPhong,
                                                  @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan,
                                                  Authentication authentication) {
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

        dp.setNgaydatPhongThuc(now);
        dp.setTrangThai("Da nhan phong");
        datPhongService.save(dp);
        checkInExpirationCacheService.xoaKhoiTheoDoi(dp.getId());

        phong.setTrangThai("Dang su dung");
        phong.setNgayCapNhat(now);
        phongService.save1(phong);

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_IN,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                maDatPhong,
                "Check-in phòng " + phong.getSoPhong() + " cho đơn #" + maDatPhong
                        + (viPham ? (" (phụ thu nhận sớm " + soTien.toPlainString() + " VND)") : ""));

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang sử dụng");
        return result;
    }

    @PostMapping("/so-do-phong/check-in-nhom/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkInNhomTuSoDoPhong(@PathVariable int maDatPhong,
                                                      @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan,
                                                      Authentication authentication) {
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_IN,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                maDatPhong,
                "Check-in ca doan (" + dsPhong.size() + " phong) cho don #" + maDatPhong
                        + (viPham ? (" (phu thu nhan som " + tongPhuThu.toPlainString() + " VND)") : ""));

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang sử dụng");
        result.put("maPhongDaNhan", maPhongDaNhan);
        return result;
    }

    @PostMapping("/so-do-phong/check-out/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkOutTuSoDoPhong(@PathVariable int maDatPhong,
                                                   @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan,
                                                   @RequestParam(required = false) BigDecimal tienKhachTra,
                                                   Authentication authentication) {
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

        BigDecimal conLaiCu = tinhConLaiHienTai(maDatPhong);
        BigDecimal tongPhaiThu = conLaiCu.add(soTien);
        boolean canThuTien = tongPhaiThu.compareTo(BigDecimal.ZERO) > 0;
        boolean canHoanTien = tongPhaiThu.compareTo(BigDecimal.ZERO) < 0;

        if ((viPham || canThuTien || canHoanTien) && !xacNhan) {
            result.put("ok", true);
            result.put("viPham", viPham);
            result.put("canThuTien", canThuTien);
            result.put("canHoanTien", canHoanTien);
            result.put("daApDung", false);
            result.put("soTien", soTien);
            result.put("soTienConLai", conLaiCu);
            result.put("tongPhaiThu", tongPhaiThu);
            result.put("moTaChinhSach", kq.moTa);
            return result;
        }

        if (canThuTien) {
            if (tienKhachTra == null || tienKhachTra.compareTo(tongPhaiThu) != 0) {
                result.put("ok", false);
                result.put("loi", "Số tiền nhập (" + (tienKhachTra == null ? "trống" : tienKhachTra)
                        + ") không khớp với số tiền còn phải thu (" + tongPhaiThu + "). Vui lòng thu đủ trước khi trả phòng.");
                result.put("tongPhaiThu", tongPhaiThu);
                return result;
            }
        }

        // XỬ LÝ HOÀN TIỀN: Nếu canHoanTien = true, không cần nhập tiền, chỉ cần xác nhận
        if (canHoanTien) {
            //  LƯU SỐ TIỀN THỪA VÀO DATPHONG (KHÔNG SET VỀ 0)
            dp.setTienThuaDoDoiPhong(tongPhaiThu.abs()); // tongPhaiThu đang là số âm, lấy giá trị tuyệt đối
            dp.setTrangThaiTienThua("CHO_HOAN");
        } else {
            // Trường hợp thu tiền
            if (viPham) {
                luuChiTietDichVuPhuThu(dp, "check-out muon", soTien);
                result.put("soTien", soTien);
            }
            if (canThuTien) {
                ghiNhanThanhToanTraPhong(maDatPhong, tongPhaiThu, authentication);
            }
        }

        dp.setTrangThai("Da tra phong");
        dp.setNgaytraPhongThuc(now);
        datPhongService.save(dp);

        phong.setTrangThai("Dang don");
        phong.setNgayCapNhat(now);
        phongService.save1(phong);

        // SỬA LỖI: dùng tongPhaiThu.abs() thay vì Math.abs()
        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_OUT,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                maDatPhong,
                "Tra phong " + phong.getSoPhong() + " cho don #" + maDatPhong
                        + (viPham ? (" (phu thu tra muon " + soTien.toPlainString() + " VND)") : "")
                        + (canHoanTien ? (" (hoan tien thua " + tongPhaiThu.abs().toPlainString() + " VND)") : ""));

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("canHoanTien", canHoanTien);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang dọn");
        return result;
    }

    @PostMapping("/so-do-phong/check-out-nhom/{maDatPhong}")
    @ResponseBody
    public Map<String, Object> checkOutNhomTuSoDoPhong(@PathVariable int maDatPhong,
                                                       @RequestParam(name = "xacNhan", defaultValue = "false") boolean xacNhan,
                                                       @RequestParam(required = false) BigDecimal tienKhachTra,
                                                       Authentication authentication) {
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

        BigDecimal conLaiCu = tinhConLaiHienTai(maDatPhong);
        BigDecimal tongPhaiThu = conLaiCu.add(tongPhuThu);
        boolean canThuTien = tongPhaiThu.compareTo(BigDecimal.ZERO) > 0;
        boolean canHoanTien = tongPhaiThu.compareTo(BigDecimal.ZERO) < 0;

        if ((viPham || canThuTien || canHoanTien) && !xacNhan) {
            result.put("ok", true);
            result.put("viPham", viPham);
            result.put("canThuTien", canThuTien);
            result.put("canHoanTien", canHoanTien);
            result.put("daApDung", false);
            result.put("soTien", tongPhuThu);
            result.put("soTienConLai", conLaiCu);
            result.put("tongPhaiThu", tongPhaiThu);
            result.put("moTaChinhSach", kq.moTa + " (áp dụng cho cả " + dsPhong.size() + " phòng của đoàn)");
            return result;
        }

        if (canThuTien) {
            if (tienKhachTra == null || tienKhachTra.compareTo(tongPhaiThu) != 0) {
                result.put("ok", false);
                result.put("loi", "Số tiền nhập (" + (tienKhachTra == null ? "trống" : tienKhachTra)
                        + ") không khớp với số tiền còn phải thu (" + tongPhaiThu + "). Vui lòng thu đủ trước khi trả phòng.");
                result.put("tongPhaiThu", tongPhaiThu);
                return result;
            }
        }

        // 🔥 XỬ LÝ HOÀN TIỀN CHO CẢ ĐOÀN
        if (canHoanTien) {
            //  LƯU SỐ TIỀN THỪA VÀO DATPHONG (KHÔNG SET VỀ 0)
            dp.setTienThuaDoDoiPhong(tongPhaiThu.abs()); // tongPhaiThu đang là số âm, lấy giá trị tuyệt đối
            dp.setTrangThaiTienThua("CHO_HOAN");
        } else {
            if (viPham) {
                luuChiTietDichVuPhuThu(dp, "check-out muon", tongPhuThu);
                result.put("soTien", tongPhuThu);
            }

            if (canThuTien) {
                ghiNhanThanhToanTraPhong(maDatPhong, tongPhaiThu, authentication);
            }
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_OUT,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                maDatPhong,
                "Tra phong ca doan (" + dsPhong.size() + " phong) cho don #" + maDatPhong
                        + (viPham ? (" (phu thu tra muon " + tongPhuThu.toPlainString() + " VND)") : "")
                        + (canHoanTien ? (" (hoan tien thua " + tongPhaiThu.abs().toPlainString() + " VND)") : ""));

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("daApDung", true);
        result.put("canHoanTien", canHoanTien);
        result.put("moTaChinhSach", kq.moTa);
        result.put("trangThaiMoi", "Đang dọn");
        result.put("maPhongDaTra", maPhongDaTra);
        return result;
    }

    @PostMapping("/so-do-phong/xac-nhan-hoan-tien/{maDatPhong}")
    @ResponseBody
    @Transactional
    public Map<String, Object> xacNhanHoanTien(@PathVariable Integer maDatPhong,
                                               @RequestParam(required = false) BigDecimal soTienHoan,
                                               Authentication authentication) {
        Map<String, Object> result = new LinkedHashMap<>();
        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            result.put("ok", false);
            result.put("loi", "Khong tim thay don dat phong #" + maDatPhong);
            return result;
        }
        System.out.println("=== xacNhanHoanTien START ===");
        System.out.println("maDatPhong: " + maDatPhong);
        System.out.println("soTienHoan tu frontend: " + soTienHoan);
        System.out.println("authentication: " + (authentication != null ? authentication.getName() : "null"));

        // LUON lay nhan vien xu ly
        NhanSu nhanVienXuLy = null;
        if (authentication != null) {
            nhanVienXuLy = nhanVienService.FindByemail(authentication.getName());
        }
        if (nhanVienXuLy == null) {
            nhanVienXuLy = nhanVienService.findLeTanDangHoatDongMacDinh();
        }
        if (nhanVienXuLy == null) {
            nhanVienXuLy = nhanVienService.findAll().stream()
                    .filter(nv -> "ADMIN".equals(nv.getVaitro()) || "ROLE_ADMIN".equals(nv.getVaitro()))
                    .findFirst()
                    .orElse(null);
        }
        if (nhanVienXuLy == null) {
            result.put("ok", false);
            result.put("loi", "Khong tim thay nhan vien xu ly.");
            return result;
        }

        // ✅ LAY SO TIEN THUA: ƯU TIÊN TỪ FRONTEND, NẾU NULL THÌ LẤY TỪ DB
        BigDecimal soTienThua = soTienHoan != null ? soTienHoan : dp.getTienThuaDoDoiPhong();
        if (soTienThua == null) {
            soTienThua = BigDecimal.ZERO;
        }
        System.out.println("soTienThua: " + soTienThua);

        // 1. Cap nhat trang thai don
        dp.setTienThuaDoDoiPhong(BigDecimal.ZERO);
        dp.setTrangThaiTienThua("Da hoan du");
        dp.setTrangThai("Da tra phong");
        dp.setNgaytraPhongThuc(LocalDateTime.now());
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        // 2. Giai phong phong
        List<Phong> dsPhong = datPhongService.findPhongByDatPhongId(maDatPhong);
        if (dsPhong != null && !dsPhong.isEmpty()) {
            for (Phong p : dsPhong) {
                p.setTrangThai("Dang don");
                p.setNgayCapNhat(LocalDateTime.now());
                phongService.save1(p);
            }
        }

        // 3. Cap nhat hoa don va tao thanh toan hoan tien
        HoaDon hd = hoaDonService.findByDatPhongId(maDatPhong);
        if (hd != null) {
            hd.setTrangThai("Da thanh toan");
            hd.setTrangThaiHoanTien("Da hoan du");
            hd.setNgayCapNhat(LocalDateTime.now());

            // TAO BAN GHI HOAN TIEN
            if (soTienThua.compareTo(BigDecimal.ZERO) > 0) {
                ThanhToan tt = new ThanhToan();
                tt.setH(hd);
                tt.setPhuongThuc("Tien Mat");
                tt.setSoTien(soTienThua);
                tt.setLoaiGiaoDich("Hoan tien");
                tt.setTrangThai("Thanh cong");
                tt.setNgaythanhToan(LocalDateTime.now());
                tt.setGichu("Hoan tien thua cho don #" + maDatPhong + " - So tien: " + soTienThua.toPlainString() + " VND");
                tt.setNv(nhanVienXuLy);

                // LUU THANH TOAN
                thanhToanService.save(tt);

                // Cap nhat daHoanTra tren hoa don
                BigDecimal daHoanTraCu = hd.getDaHoanTra() != null ? hd.getDaHoanTra() : BigDecimal.ZERO;
                hd.setDaHoanTra(daHoanTraCu.add(soTienThua));
            }

            hoaDonService.save(hd);
        }

        // 4. Ghi lich su
        lichSuHoatDongService.ghiLogAn(authentication,
                "HOAN_TIEN_TRA_PHONG",
                "DatPhong",
                maDatPhong,
                "Xac nhan hoan tien thua " + soTienThua.toPlainString() + " VND va tra phong cho don #" + maDatPhong);

        result.put("ok", true);
        result.put("message", "Da hoan tien thua " + soTienThua.toPlainString() + " VND va xac nhan tra phong thanh cong.");
        result.put("trangThaiMoi", "Dang don");
        result.put("soTienHoan", soTienThua);
        return result;
    }

    private static final class KetQuaPhuThu {
        final BigDecimal tyLe;
        final String moTa;
        KetQuaPhuThu(BigDecimal tyLe, String moTa) { this.tyLe = tyLe; this.moTa = moTa; }
    }

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

    private void luuChiTietDichVuPhuThu(DatPhong dp, String ghiChu, BigDecimal donGia) {
        String tenDv = "check-in som".equals(ghiChu) ? "Phụ thu nhận phòng sớm" : "Phụ thu trả phòng muộn";
        Dich_vu dv = taoMoiDichVuPhuThu(tenDv, donGia);

        Chi_tiet_dich_vu ctdv = new Chi_tiet_dich_vu();
        ctdv.setDatPhong(dp);
        ctdv.setDv(dv);
        ctdv.setSoluong(1);
        ctdv.setNgay_su_dung(LocalDateTime.now());
        ctdv.setGhichu(ghiChu);
        ctdv.setDonGia(donGia);
        ctdvService.save(ctdv);

        capNhatHoaDonSauKhiThemPhuThu(dp.getId(), donGia);
    }

    private void capNhatHoaDonSauKhiThemPhuThu(Integer maDatPhong, BigDecimal soTienPhuThu) {
        if (soTienPhuThu == null || soTienPhuThu.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        HoaDon hoaDonDaLuu = hoaDonService.dongBoTienDichVuTuChiTiet(maDatPhong);
        if (hoaDonDaLuu == null) {
            return;
        }

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

    private BigDecimal tinhConLaiHienTai(Integer maDatPhong) {
        if (maDatPhong == null) {
            return BigDecimal.ZERO;
        }
        HoaDon hd = hoaDonService.findByDatPhongId(maDatPhong);
        if (hd != null) {
            return defaultMoney(hd.getTongTien()).subtract(defaultMoney(hd.getDaThanhToan()));
        }
        // Don chua tung co hoa don (vd nhan phong tai quay chua phat sinh hoa
        // don) - KHONG duoc coi nhu "da tra du" (con lai = 0), phai tinh tam
        // tong tien phai thu tu InvoicePricingService de con-lai hien thi va
        // buoc thu-tien luc tra phong khop voi so tien thuc te khach con no.
        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            return BigDecimal.ZERO;
        }
        return defaultMoney(invoicePricingService.previewInvoice(maDatPhong, dp.getKm()).getTongTien());
    }

    /**
     * Tra ve hoa don hien co cua don dat phong, hoac TAO MOI neu chua co
     * (vd: don duoc dat/nhan phong tai quay nhung chua tung phat sinh hoa
     * don truoc do). Truoc day cac noi goi ghiNhanThanhToanTraPhong() khi
     * hd == null se im lang bo qua, khien tien thu tai buoc tra phong o So
     * Do Phong KHONG duoc ghi vao hoa don/CSDL du giao dien da bao "da thu
     * tien". Ham nay dam bao luon co 1 HoaDon that su truoc khi ghi nhan
     * thanh toan, dung cong thuc chuan tu InvoicePricingService (giong het
     * cach NhanVienCheckoutController#thuTien dang khoi tao hoa don tam).
     */
    private HoaDon layHoacTaoHoaDon(Integer maDatPhong, Authentication authentication) {
        HoaDon hd = hoaDonService.findByDatPhongId(maDatPhong);
        if (hd != null) {
            return hd;
        }
        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            return null;
        }
        InvoicePricingResult gia = invoicePricingService.previewInvoice(maDatPhong, dp.getKm());

        hd = new HoaDon();
        hd.setD(dp);
        hd.setDaThanhToan(BigDecimal.ZERO);
        hd.setTienPhong(gia.getTienPhong());
        hd.setTienDichVu(gia.getTienDichVu());
        hd.setTienGiam(gia.getTienGiam());
        hd.setTienVat(gia.getTienVat());
        hd.setTongTien(gia.getTongTien());
        hd.setK(dp.getKm());
        if (authentication != null) {
            hd.setN(nhanVienService.FindByemail(authentication.getName()));
        }
        hd.setNgayXuat(LocalDateTime.now());
        hd.setGhiChu("Hóa đơn tự động khởi tạo lúc trả phòng (Sơ đồ phòng) cho đơn #" + maDatPhong);
        return hoaDonService.saveWithPaymentStatusCheck(hd);
    }

    private void ghiNhanThanhToanTraPhong(Integer maDatPhong, BigDecimal soTienThu, Authentication authentication) {
        if (soTienThu == null || soTienThu.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        HoaDon hd = layHoacTaoHoaDon(maDatPhong, authentication);
        if (hd == null) {
            return;
        }
        ThanhToan tt = new ThanhToan();
        tt.setH(hd);
        tt.setPhuongThuc("Tien Mat");
        tt.setSoTien(soTienThu);
        tt.setTrangThai("Thanh cong");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setGichu("Thu tien luc tra phong tai So Do Phong (gom con lai truoc do + phu thu tra phong muon neu co), ma don: " + maDatPhong);
        thanhToanService.save(tt);

        hd.setDaThanhToan(defaultMoney(hd.getDaThanhToan()).add(soTienThu));
        hd.setNgayCapNhat(LocalDateTime.now());
        hoaDonService.saveWithPaymentStatusCheck(hd);
    }

    private String formatTienPhuThu(BigDecimal tien) {
        if (tien == null) tien = BigDecimal.ZERO;
        return String.format("%,.0f", tien.doubleValue()) + " VND";
    }

    private Dich_vu taoMoiDichVuPhuThu(String ten, BigDecimal donGia) {
        Dich_vu dv = new Dich_vu();
        dv.setTen_dich_vu(ten);
        dv.setGia(donGia != null ? donGia : BigDecimal.ZERO);
        dv.setDonVi("lần");
        dv.setHoatDong(true);
        dv.setLoaiDv("Phu thu");
        return dichVuService.save(dv);
    }

    @GetMapping("/so-do-phong/khach-hang/goi-y")
    @ResponseBody
    public List<Map<String, Object>> goiYKhachHang(@RequestParam(required = false) String q) {
        if (q == null || q.trim().length() < 2) {
            return java.util.Collections.emptyList();
        }
        return khachHangRepository.search(q.trim()).stream()
                .limit(8)
                .map(kh -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", kh.getMa_khach_hang());
                    m.put("hoTen", kh.getHoTen());
                    m.put("email", kh.getEmail());
                    m.put("sdt", kh.getSoDienThoai());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/so-do-phong/giay-to/goi-y")
    @ResponseBody
    public List<Map<String, Object>> goiYGiayTo(@RequestParam(required = false) String q) {
        if (q == null || q.trim().length() < 3) {
            return java.util.Collections.emptyList();
        }
        return giayToRepo.findTop8BySoDinhDanhStartingWithOrderByIdDesc(q.trim()).stream()
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("loaiGiayTo", g.getLoaiGiayTo());
                    m.put("hoTen", g.getHoTen());
                    m.put("soDinhDanh", g.getSoDinhDanh());
                    m.put("ngaySinh", g.getNgaySinh() != null ? g.getNgaySinh().toString() : null);
                    m.put("gioiTinh", g.getGioiTinh());
                    m.put("quocTich", g.getQuocTich());
                    m.put("queQuan", g.getQueQuan());
                    m.put("noiThuongTru", g.getNoiThuongTru());
                    m.put("noiCuTru", g.getNoiCuTru());
                    m.put("noiTamTru", g.getNoiTamTru());
                    m.put("noiLuuTru", g.getNoiLuuTru());
                    m.put("ngayCap", g.getNgayCap() != null ? g.getNgayCap().toString() : null);
                    m.put("giaTriDen", g.getGiaTriDen() != null ? g.getGiaTriDen().toString() : null);
                    m.put("quocGiaCapPhat", g.getQuocGiaCapPhat());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/so-do-phong/thanh-toan-qr/{maDatPhong}")
    public String thanhToanQr(@PathVariable Integer maDatPhong, Model model) {
        model.addAttribute("maDatPhong", maDatPhong);
        return "nhan-vien/thanh-toan-qr";
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

        Map<Integer, Integer> soNguoiLonHienTai = new HashMap<>();
        Map<Integer, Integer> maDonDangSuDung = new HashMap<>();
        for (Phong p : tatCaPhong) {
            if (!"Dang su dung".equals(p.getTrangThai())) continue;
            List<DatPhong> dsDangO = datPhongService.findUsingBookings(p.getMaPhong());
            if (dsDangO.isEmpty()) continue;

            DatPhong donDangO = dsDangO.get(0);
            maDonDangSuDung.put(p.getMaPhong(), donDangO.getId());
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
        model.addAttribute("maDonDangSuDung", maDonDangSuDung);

        Map<Integer, Integer> soPhongTheoDon = new HashMap<>();
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
        model.addAttribute("kmJson", buildKhuyenMaiJson());

        model.addAttribute("theoTang", theoTang);
        model.addAttribute("nhanTrangThai", nhanTrangThai);
        model.addAttribute("lopTrangThai", lopTrangThai);
        model.addAttribute("demTrangThai", demTrangThai);
        model.addAttribute("tongSoPhong", tatCaPhong.size());
        model.addAttribute("bookingsByRoomJson", bkJson.toString());
        model.addAttribute("svrNowIso", LocalDateTime.now().toString());
        model.addAttribute("dichVuOptions", dichVuService.findAll().stream()
                .filter(Dich_vu::isHoatDong)
                .filter(dv -> !"Phu thu".equals(dv.getLoaiDv()))
                .collect(Collectors.toList()));

        Set<String> sdpListTrangThaiHienThi = HuyDonConstants.DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT.stream()
                .filter(ts -> !"Da tra phong".equals(ts) && !"Da huy".equals(ts) && !"Khach vang".equals(ts))
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

        Map<Integer, Boolean> phongChoXacNhanVeSinh = new HashMap<>();
        Map<Integer, String> phongVeSinhTenNhanVien = new HashMap<>();
        for (su26sd09.su26sd09.dto.PhongVeSinhAssignment a : janitorCacheService.getAll()) {
            if (su26sd09.su26sd09.dto.PhongVeSinhAssignment.DA_UPLOAD.equals(a.getTrangThai())) {
                phongChoXacNhanVeSinh.put(a.getMaPhong(), true);
            }
            phongVeSinhTenNhanVien.put(a.getMaPhong(), a.getTenNhanVien());
        }
        model.addAttribute("phongChoXacNhanVeSinh", phongChoXacNhanVeSinh);
        model.addAttribute("phongVeSinhTenNhanVien", phongVeSinhTenNhanVien);

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

    // ==================== LEN LICH DAT PHONG / DAT TAI QUAY ====================

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
            @RequestParam(required = false) Integer khachHangId,
            Authentication authentication) {

        Map<String, Object> result = new LinkedHashMap<>();

        KhachHang khachHangDaChon = khachHangId != null ? khachHangRepository.findById(khachHangId).orElse(null) : null;

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
        if (hoTen == null || hoTen.isBlank()) {
            result.put("ok", false);
            result.put("loi", "Tên khách không được để trống.");
            return result;
        }
        if (email == null || email.isBlank()) {
            result.put("ok", false);
            result.put("loi", "Email không được để trống.");
            return result;
        }
        if (!email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            result.put("ok", false);
            result.put("loi", "Email không hợp lệ.");
            return result;
        }
        if (sdt == null || sdt.isBlank()) {
            result.put("ok", false);
            result.put("loi", "Số điện thoại không được để trống.");
            return result;
        }
        if (!sdt.trim().replaceAll("[\\s.-]", "").matches("\\d{8,12}")) {
            result.put("ok", false);
            result.put("loi", "Số điện thoại không hợp lệ.");
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

        long soDem = Math.max(1, ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate()));
        BigDecimal phuPhiNgoaiGio = phongService.calculateExtraFeeFor(maPhong, ngayNhan, ngayTra);
        BigDecimal amountPhong = invoicePricingService.createRoomLineItemPrice(phong, ngayNhan, ngayTra, phuPhiNgoaiGio);

        // Gom nhom theo maDichVu (dung LinkedHashMap de giu thu tu chon dau
        // tien) - danh sach dichVuIds co the co id lap lai (nguoi dung bam
        // cung 1 dich vu nhieu lan tren so-do-phong), moi id lap lai tuong
        // ung 1 don vi so luong, gop lai thanh 1 dong voi so_luong = so lan lap.
        Map<Integer, Integer> soLuongTheoDichVu = new LinkedHashMap<>();
        if (dichVuIds != null) {
            for (Integer maDichVu : dichVuIds) {
                if (maDichVu == null) continue;
                soLuongTheoDichVu.merge(maDichVu, 1, Integer::sum);
            }
        }
        Map<Integer, Dich_vu> dsDichVuHopLe = new LinkedHashMap<>();
        BigDecimal amountDv = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> e : soLuongTheoDichVu.entrySet()) {
            Dich_vu dv = dichVuService.findById(e.getKey());
            if (dv == null) continue;
            dsDichVuHopLe.put(e.getKey(), dv);
            amountDv = amountDv.add(invoicePricingService.createServiceLineItemPrice(dv, e.getValue()));
        }

        if (km != null) {
            BigDecimal dieuKienKm = km.getGiaToiThieuDuocGiam() == null ? BigDecimal.ZERO : km.getGiaToiThieuDuocGiam();
            BigDecimal tongTruocGiamKm = amountPhong.add(amountDv);
            if (dieuKienKm.compareTo(BigDecimal.ZERO) > 0 && tongTruocGiamKm.compareTo(dieuKienKm) < 0) {
                result.put("ok", false);
                result.put("loi", "Đơn phải đạt tối thiểu " + dieuKienKm.toPlainString()
                        + " đ để áp dụng mã khuyến mại \"" + km.getPromoCode() + "\". Vui lòng bỏ mã hoặc thêm dịch vụ/đêm ở lại.");
                return result;
            }
        }

        InvoicePricingResult giaTruoc = invoicePricingService.computeTotals(amountPhong, amountDv, km);
        BigDecimal tienGiam = giaTruoc.getTienGiam();
        BigDecimal tienVat = giaTruoc.getTienVat();
        BigDecimal tongCong = giaTruoc.getTongTien();

        BigDecimal daThu = tienKhachTra != null ? tienKhachTra : BigDecimal.ZERO;
        boolean laDaTraDu = daThu.compareTo(tongCong) == 0;
        boolean laChuaTraGi = daThu.compareTo(BigDecimal.ZERO) == 0;
        if (!laDaTraDu && !laChuaTraGi) {
            result.put("ok", false);
            result.put("loi", "Số tiền nhập (" + daThu + ") không hợp lệ. Lên lịch đặt phòng chỉ chấp nhận "
                    + "thanh toán đủ toàn bộ (" + tongCong + ") hoặc không thanh toán (0), không chấp nhận trả một phần.");
            result.put("tongTien", tongCong);
            return result;
        }

        DatPhong savedDp;
        synchronized (phongService) {
            java.util.Set<Integer> maPhongDaKhoa = phongService.findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra);
            if (maPhongDaKhoa.contains(maPhong)) {
                result.put("ok", false);
                result.put("loi", "Phòng đã có lịch trong khoảng ngày đã chọn. Vui lòng chọn phòng hoặc khoảng ngày khác.");
                return result;
            }

            DatPhong dp = new DatPhong();
            dp.setHoten(hoTen);
            dp.setEmail(email);
            dp.setSdt(sdt);
            dp.setMa_cccd(cccd.trim());
            dp.setN(khachHangDaChon);
            dp.setNgaydatPhong(ngayNhan);
            dp.setNgaytraPhong(ngayTra);
            dp.setSonguoiLon(slNguoiLon);
            dp.setSotreEm(slTreEm);
            dp.setYeuCauThem(ghiChu);
            dp.setTrangThai(laDaTraDu ? "Da xac nhan" : "Yeu cau dat phong");
            dp.setNgayTao(LocalDateTime.now());
            dp.setKm(km);
            dp.setNv(nhanVienXuLy);

            savedDp = datPhongService.save(dp);

            ChiTietDatPhong ctdp = new ChiTietDatPhong();
            ctdp.setD(savedDp);
            ctdp.setP(phong);
            ctdp.setGiaMoiDem(phong.getGiaMoiDem());
            ctdp.setGiaKhiDat(amountPhong);
            ctdp.setPhuPhi(phuPhiNgoaiGio);
            chiTietDatPhongService.save(ctdp);
        }

        long tongPhut = java.time.Duration.between(ngayNhan, ngayTra).toMinutes();
        LocalDateTime ngaySuDungFiller = ngayNhan.plusMinutes(tongPhut / 2);

        for (Map.Entry<Integer, Dich_vu> e : dsDichVuHopLe.entrySet()) {
            Dich_vu dv = e.getValue();
            int soLuong = soLuongTheoDichVu.getOrDefault(e.getKey(), 1);
            Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
            ct.setDatPhong(savedDp);
            ct.setDv(dv);
            ct.setSoluong(soLuong);
            ct.setDonGia(invoicePricingService.createServiceLineItemPrice(dv, soLuong));
            ct.setNgay_su_dung(ngaySuDungFiller);
            ctdvService.save(ct);
        }

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(savedDp);
        hd.setK(km);
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(tienGiam);
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

        lichSuHoatDongService.ghiLog(nhanVienXuLy,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_TAO_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                savedDp.getId(),
                "Len lich dat phong tu So Do Phong, ma don: " + savedDp.getId() + ", tong tien: " + tongCong.toPlainString() + " VND");

        result.put("ok", true);
        result.put("maDatPhong", savedDp.getId());
        result.put("tongTien", tongCong);
        result.put("daThu", daThu);
        return result;
    }

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
            @RequestParam(required = false) Integer khachHangId,
            Authentication authentication) {

        Map<String, Object> result = new LinkedHashMap<>();

        KhachHang khachHangDaChon = khachHangId != null ? khachHangRepository.findById(khachHangId).orElse(null) : null;

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
        if (hoTen == null || hoTen.isBlank()) {
            result.put("ok", false);
            result.put("loi", "Tên khách không được để trống.");
            return result;
        }
        if (email == null || email.isBlank()) {
            result.put("ok", false);
            result.put("loi", "Email không được để trống.");
            return result;
        }
        if (!email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            result.put("ok", false);
            result.put("loi", "Email không hợp lệ.");
            return result;
        }
        if (sdt == null || sdt.isBlank()) {
            result.put("ok", false);
            result.put("loi", "Số điện thoại không được để trống.");
            return result;
        }
        if (!sdt.trim().replaceAll("[\\s.-]", "").matches("\\d{8,12}")) {
            result.put("ok", false);
            result.put("loi", "Số điện thoại không hợp lệ.");
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

        LocalDateTime ngayNhan = checkin.atTime(14, 0);
        LocalDateTime ngayNhanThuc = LocalDateTime.now();
        LocalDateTime ngayTra = checkout.atTime(12, 0);

        KetQuaPhuThu kqPhuThuSom = tinhPhuThuNhanSom(ngayNhanThuc, ngayNhan);
        boolean viPhamNhanSom = kqPhuThuSom.tyLe.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal soTienPhuThuSom = viPhamNhanSom
                ? phong.getGiaMoiDem().multiply(kqPhuThuSom.tyLe).setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

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

        long soDemTruoc = Math.max(1, ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate()));
        BigDecimal phuPhiNgoaiGioTruoc = phongService.calculateExtraFeeFor(maPhong, ngayNhan, ngayTra);
        BigDecimal amountPhongTruoc = invoicePricingService.createRoomLineItemPrice(phong, ngayNhan, ngayTra, phuPhiNgoaiGioTruoc);

        // Gom theo maDichVu truoc khi tinh gia xem truoc (giu dung cong thuc
        // gia*soLuong cho tung dich vu, tranh cong don le tung don vi roi lam
        // tron nhieu lan gay sai so lam tron nho).
        Map<Integer, Integer> soLuongTheoDichVuTruoc = new LinkedHashMap<>();
        if (dichVuIds != null) {
            for (Integer maDichVu : dichVuIds) {
                if (maDichVu == null) continue;
                soLuongTheoDichVuTruoc.merge(maDichVu, 1, Integer::sum);
            }
        }
        BigDecimal amountDvTruoc = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> e : soLuongTheoDichVuTruoc.entrySet()) {
            Dich_vu dv = dichVuService.findById(e.getKey());
            if (dv == null) continue;
            amountDvTruoc = amountDvTruoc.add(invoicePricingService.createServiceLineItemPrice(dv, e.getValue()));
        }
        amountDvTruoc = amountDvTruoc.add(soTienPhuThuSom);

        if (km != null) {
            BigDecimal dieuKienKm = km.getGiaToiThieuDuocGiam() == null ? BigDecimal.ZERO : km.getGiaToiThieuDuocGiam();
            BigDecimal tongTruocGiamKm = amountPhongTruoc.add(amountDvTruoc);
            if (dieuKienKm.compareTo(BigDecimal.ZERO) > 0 && tongTruocGiamKm.compareTo(dieuKienKm) < 0) {
                result.put("ok", false);
                result.put("loi", "Đơn phải đạt tối thiểu " + dieuKienKm.toPlainString()
                        + " đ để áp dụng mã khuyến mại \"" + km.getPromoCode() + "\". Vui lòng bỏ mã hoặc thêm dịch vụ.");
                return result;
            }
        }

        InvoicePricingResult giaTruocQuay = invoicePricingService.computeTotals(amountPhongTruoc, amountDvTruoc, km);
        BigDecimal tongCongTruoc = giaTruocQuay.getTongTien();

        if (tienKhachTra == null || tienKhachTra.compareTo(tongCongTruoc) != 0) {
            result.put("ok", false);
            result.put("loi", "Số tiền nhập (" + (tienKhachTra == null ? "trống" : tienKhachTra)
                    + ") không khớp với giá phòng phải thu (" + tongCongTruoc + "). Đặt phòng tại quầy yêu cầu thanh toán đủ 100% trước khi nhận phòng.");
            result.put("tongTien", tongCongTruoc);
            return result;
        }

        DatPhong savedDp;
        BigDecimal giaApDung;
        BigDecimal phuPhiNgoaiGio;
        synchronized (phongService) {
            java.util.Set<Integer> maPhongDaKhoa = phongService.findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra);
            if (maPhongDaKhoa.contains(maPhong)) {
                result.put("ok", false);
                result.put("loi", "Phòng đã có lịch trong khoảng ngày đã chọn. Vui lòng chọn phòng hoặc khoảng ngày khác.");
                return result;
            }

            DatPhong dp = new DatPhong();
            dp.setHoten(hoTen);
            dp.setEmail(email);
            dp.setSdt(sdt);
            dp.setMa_cccd(cccd.trim());
            dp.setN(khachHangDaChon);
            dp.setNgaydatPhong(ngayNhan);
            dp.setNgaydatPhongThuc(ngayNhanThuc);
            dp.setNgaytraPhong(ngayTra);
            dp.setSonguoiLon(slNguoiLon);
            dp.setSotreEm(slTreEm);
            dp.setYeuCauThem(ghiChu);
            dp.setTrangThai("Da nhan phong");
            dp.setNgayTao(LocalDateTime.now());
            dp.setKm(km);
            dp.setNv(nhanVienXuLy);

            savedDp = datPhongService.save(dp);

            long soDem = Math.max(1, ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate()));
            phuPhiNgoaiGio = phongService.calculateExtraFeeFor(maPhong, ngayNhan, ngayTra);
            giaApDung = invoicePricingService.createRoomLineItemPrice(phong, ngayNhan, ngayTra, BigDecimal.ZERO);

            ChiTietDatPhong ctdp = new ChiTietDatPhong();
            ctdp.setD(savedDp);
            ctdp.setP(phong);
            ctdp.setGiaMoiDem(phong.getGiaMoiDem());
            ctdp.setGiaKhiDat(giaApDung.add(phuPhiNgoaiGio));
            ctdp.setPhuPhi(phuPhiNgoaiGio);
            chiTietDatPhongService.save(ctdp);

            phong.setTrangThai("Dang su dung");
            phongService.save1(phong);
        }

        BigDecimal amountPhong = giaApDung.add(phuPhiNgoaiGio);
        BigDecimal amountDv = BigDecimal.ZERO;

        long tongPhut = java.time.Duration.between(ngayNhan, ngayTra).toMinutes();
        LocalDateTime ngaySuDungFiller = ngayNhan.plusMinutes(tongPhut / 2);

        // Gom theo maDichVu (giong tinh xem truoc o tren) truoc khi luu xuong
        // Chi_tiet_dich_vu, moi dich vu chi 1 dong voi so_luong = so lan chon.
        Map<Integer, Integer> soLuongTheoDichVu = new LinkedHashMap<>();
        if (dichVuIds != null) {
            for (Integer maDichVu : dichVuIds) {
                if (maDichVu == null) continue;
                soLuongTheoDichVu.merge(maDichVu, 1, Integer::sum);
            }
        }
        for (Map.Entry<Integer, Integer> e : soLuongTheoDichVu.entrySet()) {
            Dich_vu dv = dichVuService.findById(e.getKey());
            if (dv == null) continue;

            int soLuong = e.getValue();
            BigDecimal thanhTien = invoicePricingService.createServiceLineItemPrice(dv, soLuong);
            Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
            ct.setDatPhong(savedDp);
            ct.setDv(dv);
            ct.setSoluong(soLuong);
            ct.setDonGia(thanhTien);
            ct.setNgay_su_dung(ngaySuDungFiller);
            ctdvService.save(ct);

            amountDv = amountDv.add(thanhTien);
        }

        if (viPhamNhanSom) {
            Dich_vu dvPhuThuSom = taoMoiDichVuPhuThu("Phụ thu nhận phòng sớm", soTienPhuThuSom);
            Chi_tiet_dich_vu ctPhuThuSom = new Chi_tiet_dich_vu();
            ctPhuThuSom.setDatPhong(savedDp);
            ctPhuThuSom.setDv(dvPhuThuSom);
            ctPhuThuSom.setSoluong(1);
            ctPhuThuSom.setDonGia(soTienPhuThuSom);
            ctPhuThuSom.setNgay_su_dung(ngayNhanThuc);
            ctPhuThuSom.setGhichu("check-in som");
            ctdvService.save(ctPhuThuSom);

            amountDv = amountDv.add(soTienPhuThuSom);
        }

        InvoicePricingResult giaSau = invoicePricingService.computeTotals(amountPhong, amountDv, km);
        BigDecimal tienGiam = giaSau.getTienGiam();
        BigDecimal tienVat = giaSau.getTienVat();
        BigDecimal tongCong = giaSau.getTongTien();

        BigDecimal daThu = tienKhachTra;

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(savedDp);
        hd.setK(km);
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(tienGiam);
        hd.setTienVat(tienVat);
        hd.setTongTien(tongCong);
        hd.setDaThanhToan(daThu);
        hd.setGhiChu("Dat phong tai quay tu So Do Phong, ma don: " + savedDp.getId());
        hoaDonService.saveWithPaymentStatusCheck(hd);

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

        lichSuHoatDongService.ghiLog(nhanVienXuLy,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_DAT_PHONG_TAI_QUAY,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                savedDp.getId(),
                "Dat phong tai quay tu So Do Phong, ma don: " + savedDp.getId() + ", thu " + daThu.toPlainString() + " VND");

        result.put("ok", true);
        result.put("maDatPhong", savedDp.getId());
        result.put("tongTien", tongCong);
        result.put("daThu", daThu);
        result.put("viPhamNhanSom", viPhamNhanSom);
        result.put("soTienPhuThuSom", soTienPhuThuSom);
        if (viPhamNhanSom) {
            result.put("moTaChinhSachPhuThuSom", kqPhuThuSom.moTa);
        }
        return result;
    }

    @GetMapping("/so-do-phong/dat-tai-quay/phu-thu-som")
    @ResponseBody
    public Map<String, Object> xemTruocPhuThuNhanSomTaiQuay(@RequestParam Integer maPhong) {
        Map<String, Object> result = new LinkedHashMap<>();

        Phong phong = maPhong == null ? null : phongService.findById(maPhong);
        if (phong == null || phong.getGiaMoiDem() == null) {
            result.put("ok", false);
            result.put("loi", "Không tìm thấy phòng #" + maPhong);
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ngayNhanChuan = LocalDate.now().atTime(14, 0);
        KetQuaPhuThu kq = tinhPhuThuNhanSom(now, ngayNhanChuan);
        boolean viPham = kq.tyLe.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal soTien = viPham
                ? phong.getGiaMoiDem().multiply(kq.tyLe).setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        result.put("ok", true);
        result.put("viPham", viPham);
        result.put("soTien", soTien);
        result.put("moTaChinhSach", kq.moTa);
        return result;
    }

    // ==================== BAO TRI / KET THUC BAO TRI ====================

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

    // ==================== UTILITY ====================

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

    private String escapeJson(String s) {
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

    // ==================== DAT PHONG QUAY (LE TAN) ====================

    @GetMapping("/dat-phong-quay")
    public String NvDatPhongQuay(Model model, Authentication authentication){
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            NhanSu nv = nhanVienService.FindByemail(authentication.getName());
            if (!nhanVienService.laLeTanDangHoatDong(nv)) {
                return "redirect:/home";
            }
        }

        List<KhuyenMai> kmList = khuyenMaiService.findAllActive().collect(Collectors.toList());

        List<Phong> tatCaPhong = phongService.findAllPhong();
        Map<Integer, RoomBookingGuardDTO> roomGuards = phongService.buildRoomGuards(tatCaPhong);

        model.addAttribute("phongTrongList", tatCaPhong);
        model.addAttribute("roomGuards", roomGuards);
        model.addAttribute("dichVuList", dichVuService.findAll());
        model.addAttribute("khuyenMaiList", kmList);

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

        StringBuilder rb = new StringBuilder("[");
        for (int i = 0; i < tatCaPhong.size(); i++) {
            Phong p = tatCaPhong.get(i);
            RoomBookingGuardDTO guard = roomGuards.get(p.getMaPhong());
            String trangThaiDon = guard != null ? guard.getTrangThaiDonGanNhat() : null;

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
            return "redirect:/home";
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
            sucChua += phongService.findPhongById(i).getLoaiPhong().sucChuaToiDa;
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
        LocalDateTime ngayNhanCt = ngaydatPhong.atStartOfDay();
        LocalDateTime ngayTraCt = ngaytraPhong.atTime(12, 0);
        KhuyenMai kmDon = maKhuyenMai != null ? khuyenMaiService.findbyId(maKhuyenMai) : null;

        for (Integer maPhong : maPhongList) {
            Phong phong = phongService.findById(maPhong);
            if (phong == null) {
                continue;
            }
            RoomBookingGuardDTO guard = phongService.buildRoomGuardFor(maPhong);
            if (guard == null || !guard.isCoTheDat()) {
                continue;
            }
            ChiTietDatPhong ctdp = new ChiTietDatPhong();
            ctdp.setD(savedDp);
            ctdp.setP(phong);
            ctdp.setGiaMoiDem(phong.getGiaMoiDem());

            BigDecimal phuPhiNgoaiGio = phongService.calculateExtraFeeFor(phong.getMaPhong(), ngayNhanCt, ngayTraCt);
            BigDecimal giaApDung = invoicePricingService.createRoomLineItemPrice(
                    phong, ngayNhanCt, ngayTraCt, BigDecimal.ZERO);

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

                BigDecimal thanhTien = invoicePricingService.createServiceLineItemPrice(dv, sl);

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

        InvoicePricingResult gia = invoicePricingService.computeTotals(amountPhong, amountDv, kmDon);
        BigDecimal tienGiam = gia.getTienGiam();
        BigDecimal tienVat = gia.getTienVat();
        BigDecimal tongCong = gia.getTongTien();

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(savedDp);
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(tienGiam);
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

        lichSuHoatDongService.ghiLog(nhanVienXuLy,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_DAT_PHONG_TAI_QUAY,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                savedDp.getId(),
                "Tạo đơn đặt phòng tại quầy #" + savedDp.getId() + ", thu " + tongCong.toPlainString() + " VND tiền mặt");

        redirectAttributes.addFlashAttribute("success",
                "Tao don thanh cong, ma don: " + savedDp.getId() + ", tong tien da thu: " + tongCong + " VND");
        return "redirect:/nhan-su/dat-phong";
    }

    // ==================== DOI PHONG ====================

    @PostMapping("/dat-phong/chi-tiet/{id}/doi-phong")
    @Transactional
    public String doPhong(@PathVariable Integer id,
                          @RequestParam("ctdpIds") List<Integer> ctdpIds,
                          @RequestParam("newRoomIds") List<Integer> newRoomIds,
                          @RequestParam(value = "newCccds", required = false) List<String> newCccds,
                          @RequestParam("lyDoDoi") String lyDoDoi,
                          @RequestParam(value = "fromCheckin", required = false, defaultValue = "false") boolean fromCheckin,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }
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
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the doi phong.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        if (lyDoDoi == null || lyDoDoi.trim().length() < 5) {
            redirectAttributes.addFlashAttribute("error", "Ly do doi phong phai co it nhat 5 ky tu.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        if (ctdpIds == null || ctdpIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui long chon it nhat 1 phong de doi.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }
        if (newRoomIds == null || newRoomIds.size() != ctdpIds.size()) {
            redirectAttributes.addFlashAttribute("error", "Danh sach phong moi khong khop.");
            return fromCheckin ? "redirect:/nhan-su/check-in?id=" + id : "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
        }

        long soDem = Math.max(1, ChronoUnit.DAYS.between(
                datPhong.getNgaydatPhong().toLocalDate(),
                datPhong.getNgaytraPhong().toLocalDate()));

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
            StringBuilder overlapErr = new StringBuilder();
            if (coOverlapPhongMoi(phongMoi.getMaPhong(), id,
                    datPhong.getNgaydatPhong(), datPhong.getNgaytraPhong(), overlapErr)) {
                loiTheoDong.add(overlapErr.toString());
                continue;
            }
            BigDecimal giaKhiDatCu = ct.getGiaKhiDat() != null ? ct.getGiaKhiDat() : BigDecimal.ZERO;
            BigDecimal phuPhiCu = ct.getPhuPhi() != null ? ct.getPhuPhi() : BigDecimal.ZERO;

            Phong phongCu = ct.getP();

            BigDecimal giaMoiDemMoi = phongMoi.getGiaMoiDem();
            BigDecimal giaKhiDatMoi = giaMoiDemMoi.multiply(BigDecimal.valueOf(soDem));
            BigDecimal phuPhiMoi = phongService.calculateExtraFeeFor(
                    phongMoi.getMaPhong(), datPhong.getNgaydatPhong(), datPhong.getNgaytraPhong());

            ct.setP(phongMoi);
            ct.setGiaMoiDem(giaMoiDemMoi);
            ct.setGiaKhiDat(giaKhiDatMoi);
            ct.setPhuPhi(phuPhiMoi);
            chiTietDatPhongService.save(ct);

            if (phongCu != null) {
                if (datPhongService.hasBookingNotCheckout(phongCu.getMaPhong(), id)) {
                    phongCu.setTrangThai("Da dat truoc");
                } else {
                    phongCu.setTrangThai("Trong");
                }
                phongService.save1(phongCu);
            }
            if ("Da nhan phong".equals(trangThai)) {
                phongMoi.setTrangThai("Dang su dung");
            } else {
                phongMoi.setTrangThai("Trong");
            }
            phongService.save1(phongMoi);

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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Doi " + soPhongDoi + " phong cho don #" + id + ", ly do: " + lyDoDoi.trim());

        String chenhLechStr = chenhLechTong.signum() > 0
                ? "+ " + defaultMoney(chenhLechTong).toPlainString() + " VND"
                : defaultMoney(chenhLechTong).toPlainString() + " VND";
        redirectAttributes.addFlashAttribute("thanhCongCapNhat",
                "Da doi thanh cong " + soPhongDoi + " phong. Chenh lech: " + chenhLechStr + ". Ly do: " + lyDoDoi.trim());

        if (fromCheckin) {
            return "redirect:/nhan-su/check-in?id=" + id;
        }
        if ("Yeu cau dat phong".equals(trangThai)) {
            return "redirect:/nhan-su/yeu-cau-dat-phong/chi-tiet/" + id;
        }
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    private boolean coOverlapPhongMoi(int maPhong, int maDatPhongHienTai,
                                      LocalDateTime ngayDat, LocalDateTime ngayTra,
                                      StringBuilder errorOut) {
        List<DatPhong> bookings = datPhongService.findRecentBookingsForPhong(maPhong);
        if (bookings == null || bookings.isEmpty()) return false;
        for (DatPhong dp : bookings) {
            if (dp == null || dp.getId() == maDatPhongHienTai) continue;
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

    // ==================== CAP NHAT KHACH HANG / THU TIEN ====================

    @PostMapping("/dat-phong/chi-tiet/{id}/khach-hang")
    public String capNhatKhachHang(@PathVariable Integer id,
                                   @RequestParam(required = false) String hoten,
                                   @RequestParam(required = false) String email,
                                   @RequestParam(required = false) String sdt,
                                   @RequestParam(required = false) String maCccd,
                                   Authentication authentication,
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Cap nhat thong tin khach hang cho don #" + id);

        redirectAttributes.addFlashAttribute("thanhCongCapNhat", "Cap nhat thong tin khach hang thanh cong.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    @PostMapping("/dat-phong/chi-tiet/{id}/thu-tien")
    public String thuTien(@PathVariable Integer id, @RequestParam BigDecimal soTien,
                          @RequestParam("phuongThuc") String phuongthuc, HttpServletRequest request,
                          Authentication authentication, RedirectAttributes redirectAttributes){
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_THU_TIEN,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_HOA_DON,
                id,
                "Thu " + soTien.toPlainString() + " VND tien mat dich vu phat sinh, don #" + id);

        redirectAttributes.addFlashAttribute("success", "Đã thu " + soTien + " VND tiền mặt.");
        return "redirect:/nhan-su/dat-phong/chi-tiet/" + id;
    }

    // ==================== CHECK-IN (NHAN VIEN) ====================

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

    private LocalDate parseThangParam(String thangRaw) {
        if (thangRaw == null || thangRaw.isBlank()) return null;
        try {
            YearMonth ym = YearMonth.parse(thangRaw.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
            return ym.atDay(1);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

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

        LocalDate tuNgay;
        LocalDate denNgay;
        if (dangLocKhoangNgay) {
            tuNgay = LocalDate.parse(tuNgayRaw);
            denNgay = LocalDate.parse(denNgayRaw);
        } else if (ngayChon != null) {
            tuNgay = ngayChon;
            denNgay = ngayChon;
        } else {
            tuNgay = thangNgay.withDayOfMonth(1);
            denNgay = thangNgay.withDayOfMonth(thangNgay.lengthOfMonth());
        }

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
        model.addAttribute("dichVuOptionsThuong", dichVuService.findActiveThuong());
        model.addAttribute("dichVuOptionsPhatSinh", dichVuService.findActivePhatSinh());
        model.addAttribute("dichVuOptions", dichVuService.findActivePhatSinh());
        model.addAttribute("tomTat", tomTat);
    }
}