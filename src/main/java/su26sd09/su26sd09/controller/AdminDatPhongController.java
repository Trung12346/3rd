package su26sd09.su26sd09.controller;

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
import su26sd09.su26sd09.dto.*;
import su26sd09.su26sd09.constants.HuyDonConstants;
import su26sd09.su26sd09.dto.InvoicePricingResult;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.service.*;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

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
    HuyDonService huyDonService;

    @Autowired
    khuyenMaiService khuyenMaiService;

    @Autowired
    private ThanhToanService thanhToanServiceCheckout;

    @Autowired
    private NhanVienService nhanVienServiceCheckout;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private su26sd09.su26sd09.repository.TienNghiPhongRepository tienNghiPhongRepository;

    @Autowired
    private su26sd09.su26sd09.repository.GiayToRepo giayToRepo;

    @Autowired
    private InvoicePricingService invoicePricingService;

    @Autowired
    private su26sd09.su26sd09.service.LichSuHoatDongService lichSuHoatDongService;

    @Autowired
    private CheckInExpirationCacheService checkInExpirationCacheService;

    @Autowired
    private BookingEmailService bookingEmailService;

    @Autowired
    private su26sd09.su26sd09.repository.KhachHangRepository khachHangRepository;

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
     * Tinh tong hoa don "thuc te" (dung de tinh Con no / gioi han so tien duoc thu)
     * cho mot don dat phong.
     */
    private BigDecimal tinhTongTienThucTe(Integer datPhongId, HoaDon hoaDon,
                                          List<ChiTietDatPhong> chiTietDatPhongList,
                                          BigDecimal tongPhuThu) {
        InvoicePricingResult gia = invoicePricingService.previewInvoice(
                datPhongId, hoaDon != null ? hoaDon.getK() : null);
        BigDecimal tongTienKyVong = gia.getTongTien()
                .add(tongPhuThu == null ? BigDecimal.ZERO : tongPhuThu);

        BigDecimal tongTienDaLuu = (hoaDon != null && hoaDon.getTongTien() != null)
                ? hoaDon.getTongTien() : BigDecimal.ZERO;

        return tongTienDaLuu.max(tongTienKyVong);
    }

    @GetMapping("")
    public String GetDatPhong(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "edit", required = false) Integer editId,
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

        List<DatPhongDTO> dto = new ArrayList<>();
        for (DatPhong dp: datPhongs) {
            HoaDon hoaDon = hoaDonService.findByDatPhongId(dp.getId());
            dto.add(new DatPhongDTO(dp, hoaDon != null ? hoaDon.getTrangThai() : null));
        }
        model.addAttribute("MapCtdp",Mapctdp);
        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("dto", dto);
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
                return "admin/chi-tiet-dat-phong-notfound";
            }
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/admin/dat-phong";
        }

        model.addAttribute("embed", isEmbed);

        List<ChiTietDatPhong> chiTietDatPhongList = chiTietDatPhongService.findByDatPhongId(id);

        List<Chi_tiet_dich_vu> chiTietDichVuList = chiTietDichVuService.findByDatPhongId(id);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("hoaDonDaXuat", hoaDonService.isDaXuat(id));

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

        Map<Integer, String> loaiDichVuMap = new HashMap<>();
        Map<Integer, BigDecimal> giaDonViMap = new HashMap<>();
        for (Chi_tiet_dich_vu ct : chiTietDichVuList) {
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
        model.addAttribute("chiTietDichVuList", chiTietDichVuList);
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

        return "admin/chi-tiet-dat-phong";
    }

    @PostMapping("/chi-tiet/{id}/gia-han-checkin")
    public String giaHanCheckIn(@PathVariable Integer id,
                                @RequestParam("hanCheckInMoi") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime hanCheckInMoi,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/admin/dat-phong";
        }
        if (!HuyDonConstants.DP_TRANG_THAI_AP_DUNG_KHACH_VANG.contains(datPhong.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Chi co the gia han check-in cho don dang cho check-in.");
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        if (hanCheckInMoi.isBefore(LocalDateTime.now())) {
            redirectAttributes.addFlashAttribute("error", "Han check-in moi phai o trong tuong lai.");
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }

        checkInExpirationCacheService.giaHan(id, hanCheckInMoi);

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Gia han check-in cho don #" + id + " den " + hanCheckInMoi);

        redirectAttributes.addFlashAttribute("thanhCongCapNhat",
                "Da gia han check-in cho don #" + id + " den " + hanCheckInMoi);
        return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
    }

    @PostMapping("/chi-tiet/{id}/doi-phong")
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
            return "redirect:/nhan-su/admin/dat-phong";
        }
        String trangThai = datPhong.getTrangThai();
        if (!"Yeu cau dat phong".equals(trangThai)
                && !"Cho xac nhan".equals(trangThai)
                && !"Da xac nhan".equals(trangThai)
                && !"Da nhan phong".equals(trangThai)) {
            redirectAttributes.addFlashAttribute("error",
                    "Trang thai don '" + trangThai + "' khong cho phep doi phong.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the doi phong.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        if (lyDoDoi == null || lyDoDoi.trim().length() < 5) {
            redirectAttributes.addFlashAttribute("error", "Ly do doi phong phai co it nhat 5 ky tu.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        if (ctdpIds == null || ctdpIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui long chon it nhat 1 phong de doi.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        if (newRoomIds == null || newRoomIds.size() != ctdpIds.size()) {
            redirectAttributes.addFlashAttribute("error", "Danh sach phong moi khong khop.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
            BigDecimal phuPhiMoi = phongService.calculateExtraFeeFor(
                    phongMoi.getMaPhong(), datPhong.getNgaydatPhong(), datPhong.getNgaytraPhong());
            BigDecimal giaKhiDatMoi = invoicePricingService.createRoomLineItemPrice(
                    phongMoi, datPhong.getNgaydatPhong(), datPhong.getNgaytraPhong(), phuPhiMoi);

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
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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

            if (chenhLechTong.signum() < 0) {
                datPhong.setTienThuaDoDoiPhong(chenhLechTong.abs().setScale(2, RoundingMode.HALF_UP));
                datPhong.setTrangThaiTienThua("CHO_HOAN");
            } else {
                datPhong.setTienThuaDoDoiPhong(chenhLechTong.negate().setScale(2, RoundingMode.HALF_UP));
                datPhong.setTrangThaiTienThua("KHACH_NO_THEM");
            }
        } else {
            datPhong.setTienThuaDoDoiPhong(null);
            datPhong.setTrangThaiTienThua(null);
        }

        datPhong.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(datPhong);

        String chenhLechStr = chenhLechTong.signum() > 0
                ? "+ " + defaultMoney(chenhLechTong).toPlainString() + " VND"
                : defaultMoney(chenhLechTong).toPlainString() + " VND";
        redirectAttributes.addFlashAttribute("thanhCongCapNhat",
                "Da doi thanh cong " + soPhongDoi + " phong. Chenh lech: " + chenhLechStr + ". Ly do: " + lyDoDoi.trim());

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Doi " + soPhongDoi + " phong cho don #" + id + ", chenh lech " + chenhLechStr + ", ly do: " + lyDoDoi.trim());

        if (fromCheckin) {
            return "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id;
        }
        if ("Yeu cau dat phong".equals(trangThai)) {
            return "redirect:/nhan-su/yeu-cau-dat-phong/chi-tiet/" + id;
        }
        return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
            return "redirect:/nhan-su/admin/dat-phong";
        }
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the chinh sua.");
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        List<String> loiCapNhat = validateChiTietDatPhong(ngayNhan, ngayTra, nguoiLon, treEm, dichVuIds,
                phatSinhTenList, phatSinhDonGiaList, phatSinhGhiChuList, allParams);
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
        String loiKm = khuyenMaiService.validateGanKhuyenMai(datPhong, km);
        if (loiKm != null) {
            redirectAttributes.addFlashAttribute("error", loiKm);
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
        return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
        chiTietDichVuService.deleteByDatPhongId(datPhong.getId());

        if (dichVuIds != null) {
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
                chiTiet.setDonGia(invoicePricingService.createServiceLineItemPrice(dichVu, soLuong));
                chiTietDichVuService.save(chiTiet);
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

            var dichVuPhatSinh = dichVuService.findPhatSinhTheoTenVaGia(ten, donGia)
                    .orElseGet(() -> dichVuService.taoDichVuPhatSinhMoi(ten, donGia));

            Chi_tiet_dich_vu chiTiet = new Chi_tiet_dich_vu();
            chiTiet.setDatPhong(datPhong);
            chiTiet.setDv(dichVuPhatSinh);
            chiTiet.setSoluong(soLuong);
            chiTiet.setNgay_su_dung(ngaySuDung);
            chiTiet.setDonGia(invoicePricingService.createServiceLineItemPrice(dichVuPhatSinh, soLuong));
            chiTiet.setGhichu(ghiChu);
            chiTietDichVuService.save(chiTiet);
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

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @PostMapping("/huy")
    public String huyDonAdmin(@RequestParam Integer id,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {

        KetQuaHuyDonDTO ketQua = huyDonService.huyDon(id);
        redirectAttributes.addFlashAttribute("thongBao", ketQua.getThongBao());

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_HUY_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Huy don dat phong #" + id + ": " + ketQua.getThongBao());

        if (ketQua.isCanHoanTien()) {
            return "redirect:/nhan-su/admin/hoan-tien/chi-tiet/" + ketQua.getHoaDonId();
        }

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
            @RequestParam(required = false) String maTraCuu,
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

        Map<Integer, List<Phong>> phongTheoDon = new HashMap<>();
        Map<Integer, List<ChiTietDatPhong>> MapCtdp = new HashMap<>();
        for (DatPhong dp : datPhongs) {
            phongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
            MapCtdp.put(dp.getId(), chiTietDatPhongService.findByDatPhongId(dp.getId()));
        }

        List<DatPhongDTO> dto = new ArrayList<>();
        for (DatPhong dp : datPhongs) {
            HoaDon hd = hoaDonService.findByDatPhongId(dp.getId());
            String hoaDonTrangThai = hd == null ? "Chua xuat" : hd.getTrangThai();
            dto.add(new DatPhongDTO(dp, hoaDonTrangThai));
        }

        model.addAttribute("datPhongs", datPhongs);
        model.addAttribute("dto", dto);
        model.addAttribute("MapCtdp", MapCtdp);
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
        model.addAttribute("maTraCuu", maTraCuu);

        return "admin/dat-phong-list";
    }

    @PostMapping("/update-trang-thai")
    public String updateTrangThai(@RequestParam Integer id,
                                  @RequestParam String trangThai,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime gioKhachTaiQuay,
                                  @RequestParam(required = false, defaultValue = "0") BigDecimal phuPhiTre,
                                  @RequestParam(required = false) Integer maDichVuThem,
                                  @RequestParam(required = false, defaultValue = "1") Integer soLuongDichVuThem,
                                  jakarta.servlet.http.HttpServletRequest request,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);

        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong");
            return "redirect:/nhan-su/admin/dat-phong";
        }

        if (dp.getMa_cccd() == null || dp.getMa_cccd().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Don dat phong chua co CCCD");
            return "redirect:/nhan-su/admin/dat-phong";
        }

        if ("Da tra phong".equals(trangThai)) {
            return "redirect:/nhan-su/checkout/" + id;
        }

        dp.setTrangThai(trangThai);
        datPhongService.save(dp);

        lichSuHoatDongService.ghiLogAn(authentication,
                "Da nhan phong".equals(trangThai)
                        ? su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_IN
                        : su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Chuyen trang thai don #" + id + " sang \"" + trangThai + "\"");

        List<ChiTietDatPhong> chiTietDatPhongs =
                chiTietDatPhongService.findByDatPhongId(id);

        if ("Da nhan phong".equals(trangThai)) {
            for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
                Phong p = ctdp.getP();
                p.setTrangThai("Dang su dung");
                phongService.save1(p);
            }

            if (phuPhiTre != null && phuPhiTre.signum() > 0 && !chiTietDatPhongs.isEmpty()) {
                ChiTietDatPhong first = chiTietDatPhongs.get(0);
                BigDecimal current = first.getPhuPhi() == null ? BigDecimal.ZERO : first.getPhuPhi();
                first.setPhuPhi(current.add(phuPhiTre));
                chiTietDatPhongService.save(first);
            }
        }

        if ("Da tra phong".equals(trangThai)) {
            for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
                Phong p = ctdp.getP();
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
                chiTietDichVuService.save(chiTiet);
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
            redirectAttributes.addFlashAttribute("success", "Cap nhat trang thai thanh cong");
        }
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
            Authentication authentication,
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CAP_NHAT_DAT_PHONG,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Cap nhat don dat phong #" + id + " (admin)");

        redirectAttributes.addFlashAttribute("success", "Cập nhật đơn đặt phòng #" + id + " thành công");
        return "redirect:/nhan-su/admin/dat-phong";
    }

    @PostMapping("/chi-tiet/{id}/khach-hang")
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
            return "redirect:/nhan-su/admin/dat-phong";
        }
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the chinh sua.");
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
        return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
    }

    @PostMapping("/chi-tiet/{id}/thu-tien")
    public String thuTien(@PathVariable Integer id, @RequestParam BigDecimal soTien,
                          @RequestParam String phuongthuc, HttpServletRequest request,
                          Authentication authentication, RedirectAttributes redirectAttributes){
        HoaDon hd = hoaDonService.findByDatPhongId(id);
        DatPhong dp = datPhongService.findById(id);
        if(hd == null&&dp==null){
            redirectAttributes.addFlashAttribute("error","don dat phong chua co hd");
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/"+id;
        }
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the chinh sua.");
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/"+id;
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
        return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
    }

    @GetMapping({"/{id}/check-in", "/check-in"})
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
            return "admin/dat-phong-check-in";
        }

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong";
        }

        buildCheckinList(model, ngayChonSauCung, q, tuNgayRaw, denNgayRaw);
        buildCheckinChiTiet(dp, model);

        return "admin/dat-phong-check-in";
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
        List<Chi_tiet_dich_vu> dichVuList = chiTietDichVuService.findByDatPhongId(id);

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
                .setScale(0, java.math.RoundingMode.HALF_UP);
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

    // =================================================================
    // ============ CHECK-OUT (tra phong) — admin view ==================
    // =================================================================

    private static final BigDecimal ADMIN_VAT = new BigDecimal("0.10");

    @GetMapping({"/checkout/{id}", "/checkout"})
    public String checkoutDp(@PathVariable(value = "id", required = false) String idRaw,
                             @RequestParam(value = "ngay", required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayChon,
                             @RequestParam(value = "thang", required = false) String thangRaw,
                             @RequestParam(value = "q", required = false) String q,
                             @RequestParam(value = "tuNgay", required = false) String tuNgayRaw,
                             @RequestParam(value = "denNgay", required = false) String denNgayRaw,
                             Model model, RedirectAttributes redirectAttributes) {

        Integer maDon = parseAdminCheckoutIdParam(idRaw);

        buildAdminCheckoutList(model, ngayChon, thangRaw, q, tuNgayRaw, denNgayRaw);

        if (maDon != null) {
            DatPhong dp = datPhongService.findById(maDon);
            if (dp == null) {
                redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + maDon);
                return "redirect:/nhan-su/admin/dat-phong/checkout/today";
            }
            if (!"Da nhan phong".equals(dp.getTrangThai())
                    && !"Da tra phong".equals(dp.getTrangThai())) {
                redirectAttributes.addFlashAttribute("error",
                        "Don #" + dp.getId() + " dang o trang thai '" + dp.getTrangThai()
                                + "' — khong the thuc hien thao tac checkout.");
                return "redirect:/nhan-su/admin/dat-phong/checkout/today";
            }
            napAdminCheckoutModelChiTiet(dp, model);
        }

        return "admin/checkout-chi-tiet";
    }

    private Integer parseAdminCheckoutIdParam(String idRaw) {
        if (idRaw == null || idRaw.isBlank()) return null;
        String s = idRaw.trim();
        if ("today".equalsIgnoreCase(s) || "hom-nay".equalsIgnoreCase(s)) return null;
        try { return Integer.parseInt(s); }
        catch (NumberFormatException ex) { return null; }
    }

    private void buildAdminCheckoutList(Model model, LocalDate ngayChon,
                                        String thangRaw, String q,
                                        String tuNgayRaw, String denNgayRaw) {
        boolean dangLocKhoangNgay = tuNgayRaw != null && !tuNgayRaw.isBlank();
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
                .sorted(comparing(DatPhong::getNgaytraPhong))
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
        model.addAttribute("thangTruoc", thangLienKe(thangNgay, -1));
        model.addAttribute("thangSau", thangLienKe(thangNgay, 1));
    }

    private void napAdminCheckoutModelChiTiet(DatPhong dp, Model model) {
        int id = dp.getId();
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> dichVuList = chiTietDichVuService.findByDatPhongId(id);

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

        BigDecimal tienPhong = phongList.stream()
                .map(ChiTietDatPhong::getGiaKhiDat)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienDichVu = dichVuList.stream()
                .map(Chi_tiet_dich_vu::getDonGia)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienGiam = BigDecimal.ZERO;
        BigDecimal tienVat = tienPhong.add(tienDichVu)
                .multiply(ADMIN_VAT)
                .setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal tongTien = tienPhong.add(tienDichVu).add(tienVat).subtract(tienGiam);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        BigDecimal daThu = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal daHoanTra = (hoaDon != null && hoaDon.getDaHoanTra() != null)
                ? hoaDon.getDaHoanTra() : BigDecimal.ZERO;

        BigDecimal soDu = tongTien.subtract(daThu).add(daHoanTra);
        BigDecimal canThu = soDu.compareTo(BigDecimal.ZERO) > 0 ? soDu : BigDecimal.ZERO;
        BigDecimal canHoan = soDu.compareTo(BigDecimal.ZERO) < 0 ? soDu.negate() : BigDecimal.ZERO;

        long soDem = 1;
        if (dp.getNgaydatPhong() != null && dp.getNgaytraPhong() != null) {
            soDem = ChronoUnit.DAYS.between(dp.getNgaydatPhong().toLocalDate(), dp.getNgaytraPhong().toLocalDate());
            if (soDem <= 0) soDem = 1;
        }

        List<ThanhToan> lichSuThanhToan = new ArrayList<>();
        if (hoaDon != null) {
            lichSuThanhToan = thanhToanServiceCheckout.findAllByHoaDonId(hoaDon.getId());
            if (lichSuThanhToan == null) lichSuThanhToan = new ArrayList<>();
        }

        model.addAttribute("dp", dp);
        model.addAttribute("phongList", phongList);
        model.addAttribute("dichVuList", dichVuList);
        model.addAttribute("dichVuOptions", dichVuService.findAll());
        model.addAttribute("soDem", soDem);
        model.addAttribute("tienPhong", tienPhong);
        model.addAttribute("tienDichVu", tienDichVu);
        model.addAttribute("tienGiam", tienGiam);
        model.addAttribute("tienVat", tienVat);
        model.addAttribute("phuPhiTraMuon", phuPhiTraMuon);
        model.addAttribute("tongTien", tongTien);
        model.addAttribute("hoaDon", hoaDon);
        model.addAttribute("daThu", daThu);
        model.addAttribute("daHoanTra", daHoanTra);
        model.addAttribute("soDu", soDu);
        model.addAttribute("canThu", canThu);
        model.addAttribute("canHoan", canHoan);
        model.addAttribute("trangThaiHoanTien",
                hoaDon != null && hoaDon.getTrangThaiHoanTien() != null
                        ? hoaDon.getTrangThaiHoanTien() : "");
        model.addAttribute("lichSuThanhToan", lichSuThanhToan);

        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"));
        model.addAttribute("tienGiamText", "- " + nf.format(tienGiam) + " VND");
        model.addAttribute("daThuText", "- " + nf.format(daThu) + " VND");
        model.addAttribute("daHoanTraText", "+ " + nf.format(daHoanTra) + " VND");
    }

    @PostMapping("/checkout/{id}/them-dich-vu")
    public String adminCheckoutThemDichVu(@PathVariable Integer id,
                                          @RequestParam Integer maDichVu,
                                          @RequestParam(defaultValue = "1") Integer soLuong,
                                          RedirectAttributes redirectAttributes) {
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong/checkout";
        }
        if (!"Da nhan phong".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể thêm dịch vụ.");
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        Dich_vu dv = dichVuService.findById(maDichVu);
        if (dv == null) {
            redirectAttributes.addFlashAttribute("error", "Dịch vụ không tồn tại.");
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }
        if (soLuong == null || soLuong < 1) soLuong = 1;

        Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
        ct.setDatPhong(dp);
        ct.setDv(dv);
        ct.setSoluong(soLuong);
        ct.setDonGia(invoicePricingService.createServiceLineItemPrice(dv, soLuong));
        ct.setNgay_su_dung(LocalDateTime.now());
        ct.setGhichu("Phát sinh lúc trả phòng (admin)");
        chiTietDichVuService.save(ct);

        hoaDonService.dongBoTienDichVuTuChiTiet(id);

        redirectAttributes.addFlashAttribute("success",
                "Đã thêm dịch vụ \"" + dv.getTen_dich_vu() + "\" vào đơn #" + id);
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    @PostMapping("/checkout/{id}/thu-tien")
    public String adminCheckoutThuTien(@PathVariable Integer id,
                                       @RequestParam(defaultValue = "Tien mat") String phuongThuc,
                                       @RequestParam(required = false) String ghiChu,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong/checkout";
        }
        if (!"Da nhan phong".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể thu tiền.");
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        List<ChiTietDatPhong> phongListTmp = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> dichVuListTmp = chiTietDichVuService.findByDatPhongId(id);
        BigDecimal tienPhongTmp = phongListTmp.stream().map(ChiTietDatPhong::getGiaKhiDat)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienDichVuTmp = dichVuListTmp.stream().map(Chi_tiet_dich_vu::getDonGia)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienVatTmp = tienPhongTmp.add(tienDichVuTmp)
                .multiply(ADMIN_VAT).setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal tongTien = tienPhongTmp.add(tienDichVuTmp).add(tienVatTmp);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        if (hoaDon == null) {
            NhanSu nguoiXuLy = nhanVienServiceCheckout.FindByemail(authentication.getName());
            hoaDon = new HoaDon();
            hoaDon.setD(dp);
            hoaDon.setDaThanhToan(BigDecimal.ZERO);
            hoaDon.setTienPhong(tienPhongTmp);
            hoaDon.setTienDichVu(tienDichVuTmp);
            hoaDon.setTienVat(tienVatTmp);
            hoaDon.setTongTien(tongTien);
            hoaDon.setK(dp.getKm());
            hoaDon.setN(nguoiXuLy);
            hoaDon.setNgayXuat(LocalDateTime.now());
            hoaDon.setGhiChu("Hóa đơn trả phòng (admin) cho đơn #" + id);
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
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        NhanSu nvHienTai = nhanVienServiceCheckout.FindByemail(authentication.getName());
        ThanhToan tt = new ThanhToan();
        tt.setH(hoaDon);
        tt.setPhuongThuc(phuongThuc);
        tt.setSoTien(canThu);
        tt.setLoaiGiaoDich("Thu tien");
        tt.setTrangThai("Thanh cong");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setNv(nvHienTai);
        tt.setGichu(ghiChu != null && !ghiChu.isBlank() ? ghiChu : "Thu tiền còn lại khi trả phòng (admin) #" + id);
        thanhToanServiceCheckout.save(tt);

        hoaDon.setDaThanhToan(daThanhToan.add(canThu));
        hoaDonService.saveWithPaymentStatusCheck(hoaDon);

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_THU_TIEN,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_HOA_DON,
                id,
                "Thu " + canThu.toPlainString() + " VND khi tra phong (admin), don #" + id);

        redirectAttributes.addFlashAttribute("success",
                "Đã thu " + canThu.toPlainString() + " VND cho đơn #" + id + ".");
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    @PostMapping("/checkout/{id}/hoan-tien")
    public String adminCheckoutHoanTien(@PathVariable Integer id,
                                        @RequestParam(defaultValue = "Tien mat") String hinhThuc,
                                        @RequestParam(required = false) String ghiChu,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong/checkout";
        }
        if (!"Da nhan phong".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể hoàn tiền.");
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        if (hoaDon == null) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " chưa có hóa đơn, không thể ghi nhận hoàn tiền.");
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        List<ChiTietDatPhong> phongListTmp = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> dichVuListTmp = chiTietDichVuService.findByDatPhongId(id);
        BigDecimal tienPhongTmp = phongListTmp.stream().map(ChiTietDatPhong::getGiaKhiDat)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienDichVuTmp = dichVuListTmp.stream().map(Chi_tiet_dich_vu::getDonGia)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienVatTmp = tienPhongTmp.add(tienDichVuTmp)
                .multiply(ADMIN_VAT).setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal tongTien = tienPhongTmp.add(tienDichVuTmp).add(tienVatTmp);

        BigDecimal daThanhToan = hoaDon.getDaThanhToan() == null ? BigDecimal.ZERO : hoaDon.getDaThanhToan();
        BigDecimal daHoanTra = hoaDon.getDaHoanTra() == null ? BigDecimal.ZERO : hoaDon.getDaHoanTra();
        BigDecimal soDu = tongTien.subtract(daThanhToan).add(daHoanTra);
        BigDecimal canHoan = soDu.compareTo(BigDecimal.ZERO) < 0 ? soDu.negate() : BigDecimal.ZERO;

        if (canHoan.compareTo(BigDecimal.ZERO) <= 0) {
            redirectAttributes.addFlashAttribute("error", "Khách không có khoản thừa cần hoàn.");
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        NhanSu nvHienTai = nhanVienServiceCheckout.FindByemail(authentication.getName());
        ThanhToan tt = new ThanhToan();
        tt.setH(hoaDon);
        tt.setPhuongThuc(hinhThuc);
        tt.setSoTien(canHoan);
        tt.setLoaiGiaoDich("Hoan tien");
        tt.setTrangThai("Cho xu ly");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setNv(nvHienTai);
        tt.setGichu(ghiChu != null && !ghiChu.isBlank() ? ghiChu : "Ghi nhận hoàn tiền khi trả phòng (admin) #" + id);
        thanhToanServiceCheckout.save(tt);

        hoaDon.setDaHoanTra(daHoanTra.add(canHoan));
        hoaDon.setTrangThaiHoanTien("Cho xu ly");
        hoaDon.setNgayYeuCauHoan(LocalDateTime.now());
        hoaDon.setNgayCapNhat(LocalDateTime.now());
        hoaDonService.save(hoaDon);

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_HOAN_TIEN,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_HOA_DON,
                id,
                "Ghi nhan hoan " + canHoan.toPlainString() + " VND khi tra phong (admin), don #" + id);

        redirectAttributes.addFlashAttribute("success",
                "Đã ghi nhận hoàn " + canHoan.toPlainString() + " VND cho đơn #" + id
                        + ". Yêu cầu đang chờ xử lý.");
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    @PostMapping("/checkout/{id}/xac-nhan")
    public String adminCheckoutXacNhan(@PathVariable Integer id,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong/checkout";
        }
        if (!"Da nhan phong".equals(dp.getTrangThai())) {
            redirectAttributes.addFlashAttribute("error", "Đơn #" + id + " không ở trạng thái đang lưu trú, không thể trả phòng.");
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        List<ChiTietDatPhong> phongListTmp = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> dichVuListTmp = chiTietDichVuService.findByDatPhongId(id);
        BigDecimal tienPhongTmp = phongListTmp.stream().map(ChiTietDatPhong::getGiaKhiDat)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienDichVuTmp = dichVuListTmp.stream().map(Chi_tiet_dich_vu::getDonGia)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tienVatTmp = tienPhongTmp.add(tienDichVuTmp)
                .multiply(ADMIN_VAT).setScale(0, java.math.RoundingMode.HALF_UP);
        BigDecimal tongTien = tienPhongTmp.add(tienDichVuTmp).add(tienVatTmp);

        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        BigDecimal daThanhToan = (hoaDon != null && hoaDon.getDaThanhToan() != null)
                ? hoaDon.getDaThanhToan() : BigDecimal.ZERO;
        BigDecimal daHoanTra = (hoaDon != null && hoaDon.getDaHoanTra() != null)
                ? hoaDon.getDaHoanTra() : BigDecimal.ZERO;
        BigDecimal soDu = tongTien.subtract(daThanhToan).add(daHoanTra);

        // 🔥 XỬ LÝ HOÀN TIỀN THỪA - LƯU VÀO DatPhong
        if (soDu.compareTo(BigDecimal.ZERO) < 0) {
            // Khách đã trả thừa tiền
            BigDecimal tienThua = soDu.negate();

            // LƯU SỐ TIỀN THỪA VÀO DatPhong
            dp.setTienThuaDoDoiPhong(tienThua);
            dp.setTrangThaiTienThua("CHO_HOAN");
            dp.setNgayCapNhat(LocalDateTime.now());
            datPhongService.save(dp);

            // CẬP NHẬT HÓA ĐƠN
            if (hoaDon != null) {
                hoaDon.setTrangThaiHoanTien("CHO_XU_LY");
                hoaDon.setNgayYeuCauHoan(LocalDateTime.now());
                hoaDon.setNgayCapNhat(LocalDateTime.now());
                hoaDonService.save(hoaDon);
            }

            redirectAttributes.addFlashAttribute("error",
                    "Khách đã trả thừa " + tienThua.toPlainString() + " VND. Vui lòng xử lý hoàn tiền trước khi chốt trả phòng.");
            return "redirect:/nhan-su/admin/hoan-tien/chi-tiet/" + (hoaDon != null ? hoaDon.getId() : id);
        }

        if (soDu.compareTo(BigDecimal.ZERO) != 0) {
            String message = soDu.compareTo(BigDecimal.ZERO) > 0
                    ? "Khách còn nợ " + soDu.toPlainString() + " VND. Vui lòng thu tiền trước khi chốt trả phòng."
                    : "Khách đã trả thừa " + soDu.negate().toPlainString() + " VND. Vui lòng ghi nhận hoàn tiền trước khi chốt trả phòng.";
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
        }

        dp.setTrangThai("Da tra phong");
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ct : phongList) {
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

        lichSuHoatDongService.ghiLogAn(authentication,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.HD_CHECK_OUT,
                su26sd09.su26sd09.constants.LichSuHoatDongConstants.DT_DAT_PHONG,
                id,
                "Tra phong (admin) cho don #" + id + "." + hoaDonInfo);

        redirectAttributes.addFlashAttribute("success",
                "Trả phòng thành công cho đơn #" + id + "." + hoaDonInfo);
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    @GetMapping("/checkout/{id}/xuat-pdf")
    public void adminCheckoutXuatPdf(@PathVariable Integer id,
                                     HttpServletRequest request, HttpServletResponse response) throws Exception {
        HoaDon hoaDon = hoaDonService.findByDatPhongId(id);
        if (hoaDon == null) {
            response.sendRedirect("/nhan-su/admin/dat-phong/checkout/" + id);
            return;
        }

        String trangThaiDon = hoaDon.getD() != null ? hoaDon.getD().getTrangThai() : null;
        boolean hopLe = "Da huy".equals(trangThaiDon) || "Da tra phong".equals(trangThaiDon);
        if (!hopLe) {
            request.getSession().setAttribute("toastWarning",
                    "Đơn đặt phòng #" + id
                            + " đang trong quá trình sử dụng phòng. Vui lòng hoàn tất trả phòng hoặc xử lý hủy đơn trước khi xuất hóa đơn.");
            String referer = request.getHeader("Referer");
            String redirect = (referer != null && !referer.isBlank())
                    ? referer
                    : ("/nhan-su/admin/dat-phong/checkout/" + id);
            response.sendRedirect(redirect);
            return;
        }

        BigDecimal tongPhuThu = BigDecimal.ZERO;
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ct : phongList) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThu = tongPhuThu.add(ct.getPhuPhi());
            }
        }

        BigDecimal tongPhuThuCheckInSom = BigDecimal.ZERO;
        BigDecimal tongDichVuThuong = BigDecimal.ZERO;
        List<Chi_tiet_dich_vu> dichVuListForPdf = chiTietDichVuService.findByDatPhongId(id);
        for (Chi_tiet_dich_vu ctdv : dichVuListForPdf) {
            if (ctdv == null || ctdv.getDonGia() == null) continue;
            String loai = ctdv.getDv() != null ? ctdv.getDv().getLoaiDv() : null;
            if ("Phu thu".equalsIgnoreCase(loai)) {
                tongPhuThuCheckInSom = tongPhuThuCheckInSom.add(ctdv.getDonGia());
            } else {
                tongDichVuThuong = tongDichVuThuong.add(ctdv.getDonGia());
            }
        }

        List<ThanhToan> thanhToans = thanhToanServiceCheckout.findAllByHoaDonId(hoaDon.getId());
        List<ThanhToan> hoanTienList = new ArrayList<>();
        for (ThanhToan t : thanhToans) {
            if (t != null && "Hoan tien".equalsIgnoreCase(t.getLoaiGiaoDich())) {
                hoanTienList.add(t);
            }
        }
        BigDecimal tongHoan = hoaDon.getDaHoanTra() != null ? hoaDon.getDaHoanTra() : BigDecimal.ZERO;

        org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
        context.setVariable("hoaDon", hoaDon);
        context.setVariable("tongPhuThu", tongPhuThu);
        context.setVariable("tongPhuThuCheckInSom", tongPhuThuCheckInSom);
        context.setVariable("tongDichVuThuong", tongDichVuThuong);
        context.setVariable("hoanTienList", hoanTienList);
        context.setVariable("tongHoan", tongHoan);



        String html = templateEngine.process("nhan-vien/hoa-don-pdf", context);



        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=hoa-don-admin-" + hoaDon.getId() + ".pdf");

        org.xhtmlrenderer.pdf.ITextRenderer renderer = new org.xhtmlrenderer.pdf.ITextRenderer();
        renderer.getFontResolver().addFont(
                "C:/Windows/Fonts/arial.ttf",
                com.lowagie.text.pdf.BaseFont.IDENTITY_H,
                com.lowagie.text.pdf.BaseFont.EMBEDDED
        );
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(response.getOutputStream());
    }
}