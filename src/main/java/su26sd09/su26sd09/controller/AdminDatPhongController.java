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

    @GetMapping("")
    public String GetDatPhong(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(value = "edit", required = false) Integer editId,
            Model model) {


        Sort sort = Sort.by(Sort.Order.desc("ngayTao"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);

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
            // Đơn "Yeu cau dat phong" (mới thêm vào set hiển thị) chưa qua thanh toán
            // -> chưa có HoaDon -> findByDatPhongId() trả null. Tránh NPE bằng cách
            // truyền null trực tiếp vào DTO, template sẽ tự xử lý.
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
        model.addAttribute("hoaDonDaXuat", hoaDonService.isDaXuat(id));

        // Tinh tong phu phi ngoai gio tu cac phong trong don
        BigDecimal tongPhuThu = BigDecimal.ZERO;
        for (ChiTietDatPhong ct : chiTietDatPhongList) {
            if (ct != null && ct.getPhuPhi() != null && ct.getPhuPhi().signum() > 0) {
                tongPhuThu = tongPhuThu.add(ct.getPhuPhi());
            }
        }

        // ===== Data cho modal đổi phòng =====
        // Lấy tất cả phòng active để render danh sách phòng khả dụng trong modal đổi phòng.
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
        model.addAttribute("roomStatusJson", "[" + phongService.buildRoomStatusJson(tatCaPhong) + "]");
        // Số đêm để hiển thị chênh lệch trong modal
        long soDem = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(
                datPhong.getNgaydatPhong().toLocalDate(),
                datPhong.getNgaytraPhong().toLocalDate()));
        model.addAttribute("soDem", soDem);
        // Cho phép đổi phòng: trạng thái đơn thuộc nhóm này + hóa đơn chưa xuất PDF
        boolean choPhepDoiPhong = "Cho xac nhan".equals(datPhong.getTrangThai())
                || "Da xac nhan".equals(datPhong.getTrangThai())
                || "Da nhan phong".equals(datPhong.getTrangThai());
        model.addAttribute("choPhepDoiPhong", choPhepDoiPhong);

        model.addAttribute("datPhong", datPhong);
        model.addAttribute("chiTietDatPhongList", chiTietDatPhongList);
        model.addAttribute("chiTietDichVuList", chiTietDichVuList);
        model.addAttribute("dichVuList", dichVuService.findActiveThuong());
        model.addAttribute("kmJson", buildKhuyenMaiJson());
        model.addAttribute("tongPhuThu", tongPhuThu);

        return "admin/chi-tiet-dat-phong";
    }

    /**
     * Xử lý đổi phòng từ modal trong trang chi tiết đơn đặt phòng.
     *
     * Body:
     *   - ctdpIds: List&lt;Integer&gt; — id các ChiTietDatPhong muốn đổi
     *   - newRoomIds: List&lt;Integer&gt; — phòng mới tương ứng (cùng index)
     *   - newCccds: List&lt;String&gt; — CCCD mới (để trống = giữ nguyên)
     *   - lyDoDoi: String — lý do đổi phòng (bắt buộc)
     */
    @PostMapping("/chi-tiet/{id}/doi-phong")
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
            return "redirect:/nhan-su/admin/dat-phong";
        }
        // Validate trạng thái đơn
        String trangThai = datPhong.getTrangThai();
        if (!"Cho xac nhan".equals(trangThai)
                && !"Da xac nhan".equals(trangThai)
                && !"Da nhan phong".equals(trangThai)) {
            redirectAttributes.addFlashAttribute("error",
                    "Trang thai don '" + trangThai + "' khong cho phep doi phong.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        // Hóa đơn đã xuất PDF -> không cho sửa
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the doi phong.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        // Lý do bắt buộc
        if (lyDoDoi == null || lyDoDoi.trim().length() < 5) {
            redirectAttributes.addFlashAttribute("error", "Ly do doi phong phai co it nhat 5 ky tu.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        // Phải tick ít nhất 1 dòng và danh sách phòng mới khớp độ dài
        if (ctdpIds == null || ctdpIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui long chon it nhat 1 phong de doi.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }
        if (newRoomIds == null || newRoomIds.size() != ctdpIds.size()) {
            redirectAttributes.addFlashAttribute("error", "Danh sach phong moi khong khop.");
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
            String cccdMoi = (cccdMoiRaw == null || cccdMoiRaw.trim().isEmpty())
                    ? ct.getMa_cccd()
                    : cccdMoiRaw.trim();
            if (cccdMoi != null && !cccdMoi.isEmpty() && !cccdMoi.matches("^[0-9]{12}$")) {
                loiTheoDong.add("Phong '" + phongMoi.getSoPhong() + "': CCCD moi phai la 12 chu so.");
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
            ct.setMa_cccd(cccdMoi);
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
            return fromCheckin ? "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id : "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }

        // Cập nhật hóa đơn (nếu có) + ghi nhận chênh lệch tiền do đổi phòng trên ĐƠN ĐẶT PHÒNG
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

            // Ghi nhận tiền thừa lên ĐƠN ĐẶT PHÒNG (không phụ thuộc hóa đơn)
            if (chenhLechTong.signum() < 0) {
                // Đổi sang phòng rẻ hơn → khách dư tiền → cần hoàn
                datPhong.setTienThuaDoDoiPhong(chenhLechTong.abs().setScale(2, RoundingMode.HALF_UP));
                datPhong.setTrangThaiTienThua("CHO_HOAN");
            } else {
                // Đổi sang phòng đắt hơn → khách nợ thêm
                datPhong.setTienThuaDoDoiPhong(chenhLechTong.negate().setScale(2, RoundingMode.HALF_UP));
                datPhong.setTrangThaiTienThua("KHACH_NO_THEM");
            }
        } else {
            // Không chênh lệch → reset trạng thái tiền thừa
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
        
        // Nếu đổi phòng từ trang check-in, redirect về check-in, ngược lại về chi tiết
        if (fromCheckin) {
            return "redirect:/nhan-su/admin/dat-phong/check-in?id=" + id;
        }
        return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
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
            if (!"Cho xac nhan".equals(tt) && !"Da xac nhan".equals(tt) && !"Da nhan phong".equals(tt)) {
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
        chiTietDichVuService.deleteByDatPhongId(datPhong.getId());

        // ===== 1) Dịch vụ THƯỜNG (catalog có sẵn) =====
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
                chiTiet.setDonGia(dichVu.getGia().multiply(BigDecimal.valueOf(soLuong)));
                chiTietDichVuService.save(chiTiet);
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
            var dichVuPhatSinh = dichVuService.findPhatSinhTheoTenVaGia(ten, donGia)
                    .orElseGet(() -> dichVuService.taoDichVuPhatSinhMoi(ten, donGia));

            Chi_tiet_dich_vu chiTiet = new Chi_tiet_dich_vu();
            chiTiet.setDatPhong(datPhong);
            chiTiet.setDv(dichVuPhatSinh);
            chiTiet.setSoluong(soLuong);
            chiTiet.setNgay_su_dung(ngaySuDung);
            chiTiet.setDonGia(donGia.multiply(BigDecimal.valueOf(soLuong)));
            chiTiet.setGhichu(ghiChu); // ghi chú lý do cụ thể lưu ở line item
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
                              RedirectAttributes redirectAttributes) {

        KetQuaHuyDonDTO ketQua = huyDonService.huyDon(id);
        redirectAttributes.addFlashAttribute("thongBao", ketQua.getThongBao());

        if (ketQua.isCanHoanTien()) {
            // Có tiền cần hoàn -> đi thẳng sang trang xử lý hoàn tiền (AdminHoanTienController)
            return "redirect:/nhan-su/admin/hoan-tien/chi-tiet/" + ketQua.getHoaDonId();
        }

        // Không phát sinh hoàn tiền -> quay lại danh sách đặt phòng
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
                // Ẩn các đơn "Chua thanh toan" — chỉ hiển thị đơn đã có trạng thái
                // nghiệp vụ hợp lệ trên trang quản lý đơn đặt phòng admin.
                // Ngoại lệ: khi nhân viên/admin tra cứu chính xác theo "ma tra cuu",
                // phải trả về cả đơn "Chua thanh toan" (đơn của khách vãng lai đặt từ
                // giỏ nhưng chưa thanh toán — đối tượng chính dùng mã tra cứu).
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

        if(tenKhach!=null){
            System.out.println("Found!");
        }else{
            System.out.println("NOt Found: "+tenKhach);
        }

        Map<Integer, List<Phong>> phongTheoDon = new HashMap<>();
        Map<Integer, List<ChiTietDatPhong>> MapCtdp = new HashMap<>();
        for (DatPhong dp : datPhongs) {
            phongTheoDon.put(dp.getId(), datPhongService.findPhongByDatPhongId(dp.getId()));
            MapCtdp.put(dp.getId(), chiTietDatPhongService.findByDatPhongId(dp.getId()));
        }

        // View admin lặp theo DatPhongDTO (có sẵn trường hoaDonTrangThai), không phải
        // List<DatPhong> — nên cần build DTO tương ứng cho kết quả search.
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
                                  // Them dich vu (chon tu dropdown trong form check-in)
                                  @RequestParam(required = false) Integer maDichVuThem,
                                  @RequestParam(required = false, defaultValue = "1") Integer soLuongDichVuThem,
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

        // Forced redirect sang trang check-out khi chuyen sang "Da tra phong"
        // de nhan vien/admin phai chot tien va giai phong phong theo dung quy trinh.
        if ("Da tra phong".equals(trangThai)) {
            return "redirect:/nhan-su/checkout/" + id;
        }

        dp.setTrangThai(trangThai);
        datPhongService.save(dp);

        List<ChiTietDatPhong> chiTietDatPhongs =
                chiTietDatPhongService.findByDatPhongId(id);

        // Khi khách nhận phòng
        if ("Da nhan phong".equals(trangThai)) {

            for (ChiTietDatPhong ctdp : chiTietDatPhongs) {

                Phong p = ctdp.getP();
                p.setTrangThai("Dang su dung");

                phongService.save1(p);
            }

            // Cong phu phi check-in tre (neu co) vao DUNG 1 ChiTietDatPhong
            // (phong dau tien trong danh sach). Khong cong don / khong chia deu
            // — phu phi chi tinh 1 lan cho ca don, theo yeu cau cua user.
            // GioKhachTaiQuay duoc submit cung form, nhung hien tai chi dung de
            // backend validate nguong (se su dung o luong sau). Phu phi da duoc
            // template tinh san va gui qua hidden input phuPhiTre.
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

        // Them dich vu (neu co) — dropdown "Them dich vu" trong form check-in.
        // Ap dung cho moi trang thai de nhan vien co the bo sung dich vu phat
        // sinh bat ky khi nao can.
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
        if (hoaDonService.isDaXuat(id)) {
            redirectAttributes.addFlashAttribute("error",
                    "Hoa don cua don dat phong #" + id + " da duoc xuat PDF, khong the chinh sua.");
            return "redirect:/nhan-su/admin/dat-phong/chi-tiet/" + id;
        }

        BigDecimal daThanhToan = hd.getDaThanhToan() ==null ? BigDecimal.ZERO : hd.getDaThanhToan();
        BigDecimal conNo = hd.getTongTien().subtract(daThanhToan);
        if(soTien.compareTo(conNo) > 0){
            redirectAttributes.addFlashAttribute("error","Số tiền vượt quá số tiền còn thiếu");
            return "redirect:/nhan-su/dat-phong/"+id;
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

        // Nếu có ?thang=YYYY-MM thì override lựa chọn tháng hiện tại
        LocalDate thangDiChuyen = parseThangParam(thangRaw);
        LocalDate ngayChonSauCung = (thangDiChuyen != null) ? thangDiChuyen : ngayChon;

        // Khong co id -> chi hien thi danh sach don cho check-in de user chon
        if (id == null || id <= 0) {
            buildCheckinList(model, ngayChonSauCung, q, tuNgayRaw, denNgayRaw);
            return "admin/dat-phong-check-in";
        }

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy đơn đặt phòng #" + id);
            return "redirect:/nhan-su/admin/dat-phong";
        }

        // 1) Phần danh sách đơn (luôn hiển thị phía trên)
        buildCheckinList(model, ngayChonSauCung, q, tuNgayRaw, denNgayRaw);

        // 2) Phần chi tiết 1 đơn
        buildCheckinChiTiet(dp, model);

        return "admin/dat-phong-check-in";
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

    /** Build phần "danh sách đơn cần nhận phòng" + dải ngày trong tháng — dùng cho check-in. */
    private void buildCheckinList(Model model, LocalDate ngayChon, String q,
                                  String tuNgayRaw, String denNgayRaw) {
        LocalDate thangNgay = (ngayChon != null) ? ngayChon : LocalDate.now();
        // thangHienThi là LocalDateTime (ngày đầu tháng + giờ hiện tại) để view hiển thị full ngày-giờ-phút-giây
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
                .filter(dp -> "Cho xac nhan".equals(dp.getTrangThai())
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

        // mapCtdp: view cần mapCtdp.get(don.id)
        Map<Integer, List<ChiTietDatPhong>> mapCtdp = new HashMap<>();
        for (DatPhong d : dsDon) {
            mapCtdp.put(d.getId(), chiTietDatPhongService.findByDatPhongId(d.getId()));
        }

        // Dải ngày trong tháng (đếm số đơn sắp nhận mỗi ngày)
        // Luon duyet theo tuNgayLich..denNgayLich (toan bo thang) de khong mat luoi
        // khi user da click 1 ngay bat ky.
        List<Map<String, Object>> dsNgayTrongThang = new ArrayList<>();
        LocalDate homNay = LocalDate.now();
        for (LocalDate d = tuNgayLich; !d.isAfter(denNgayLich); d = d.plusDays(1)) {
            LocalDate finalD = d;
            long soDon = datPhongService.findAll().stream()
                    .filter(x -> x.getNgaydatPhong() != null)
                    .filter(x -> "Cho xac nhan".equals(x.getTrangThai())
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

    /** Build phần chi tiết 1 đơn (phòng, dịch vụ, tóm tắt). */
    private void buildCheckinChiTiet(DatPhong dp, Model model) {
        int id = dp.getId();
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> dichVuList = chiTietDichVuService.findByDatPhongId(id);

        Map<String, List<ChiTietDatPhong>> nhomTheoLoai = phongList.stream()
                .collect(Collectors.groupingBy(
                        ct -> (ct.getP() != null && ct.getP().getLoaiPhong() != null)
                                ? ct.getP().getLoaiPhong().getTenLoai() : "Chưa xác định",
                        LinkedHashMap::new, Collectors.toList()));

        List<NhomYeuCauPhongDTO> nhomYeuCauPhong = new ArrayList<>();
        for (Map.Entry<String, List<ChiTietDatPhong>> e : nhomTheoLoai.entrySet()) {
            List<SlotPhongDTO> slots = new ArrayList<>();
            for (ChiTietDatPhong ct : e.getValue()) {
                Phong pDaGan = ct.getP();
                boolean sanSang = pDaGan != null && "Trong".equals(pDaGan.getTrangThai());

                List<LoaiPhongDTO> loaiPhongOptions = new ArrayList<>();
                // LUON build options (ke ca khi phong hien dang Trong) de nhan vien co the
                // doi phong theo yeu cau khach (nang cap, doi view, hang re hon, khac loai, ...)
                if (pDaGan != null && pDaGan.getLoaiPhong() != null) {
                    // BO filter gia/loai: lay TAT CA phong Trong (ke ca re hon / khac loai)
                    // de khach co the doi xuong phong re hon (hoan tien thua) hoac len phong dat hon (tra them).
                    for (LoaiPhong lp : phongService.findAllLoai()) {
                        List<PhongTheoLoaiDTO> dsPhong = new ArrayList<>();
                        for (Phong p : phongService.findPhongTheoLoai(lp.getId())) {
                            // BO QUA phong hien tai (khong the doi sang chinh no)
                            if (p.getMaPhong() == pDaGan.getMaPhong()) continue;
                            dsPhong.add(new PhongTheoLoaiDTO(
                                    p.getMaPhong(), p.getSoPhong(), p.getSoTang(),
                                    p.getTrangThai(), "Trong".equals(p.getTrangThai()),
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
                        ct.getId(), pDaGan, sanSang, ct.getMa_cccd(), loaiPhongOptions, ct.getGiaKhiDat(), tienNghiList));
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
        // 2 list dich vu: thuong + phat sinh — cho combobox 2 tab
        model.addAttribute("dichVuOptionsThuong", dichVuService.findActiveThuong());
        model.addAttribute("dichVuOptionsPhatSinh", dichVuService.findActivePhatSinh());
        // Giu lai de tuong thich nguoc (trang khac co the dang dung)
        model.addAttribute("dichVuOptions", dichVuService.findActivePhatSinh());
        model.addAttribute("tomTat", tomTat);
    }

    // =================================================================
    // ============ CHECK-OUT (tra phong) — admin view ==================
    // =================================================================

    private static final BigDecimal ADMIN_VAT = new BigDecimal("0.10");

    /**
     * Trang check-out (admin): gop list + detail tren 1 view (giong nhan-vien).
     * - idRaw co the la so don ("42") hoac "today"/"hom-nay" (khong co chi tiet).
     * - ?ngay=YYYY-MM-DD loc don theo ngay tra phong.
     * - ?thang=YYYY-MM chuyen lich sang thang khac.
     */
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

    /** Parse path variable idRaw -> Integer maDon (null neu "today"/"hom-nay"/sai). */
    private Integer parseAdminCheckoutIdParam(String idRaw) {
        if (idRaw == null || idRaw.isBlank()) return null;
        String s = idRaw.trim();
        if ("today".equalsIgnoreCase(s) || "hom-nay".equalsIgnoreCase(s)) return null;
        try { return Integer.parseInt(s); }
        catch (NumberFormatException ex) { return null; }
    }

    /** Build phan list (lich ngay tra + danh sach don) cho trang admin check-out. */
    private void buildAdminCheckoutList(Model model, LocalDate ngayChon,
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
        // Luon duyet theo tuNgayLich..denNgayLich (toan bo thang) de khong mat luoi
        // khi user da click 1 ngay bat ky.
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

    /** Nap model chi tiet cho 1 don (admin checkout). */
    private void napAdminCheckoutModelChiTiet(DatPhong dp, Model model) {
        int id = dp.getId();
        List<ChiTietDatPhong> phongList = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> dichVuList = chiTietDichVuService.findByDatPhongId(id);

        // Phu phi tra muon: cong don calculateExtraFeeFor cho tung phong (theo thoi gian hien tai)
        BigDecimal phuPhiTraMuon = BigDecimal.ZERO;
        LocalDateTime gioTraHienTai = LocalDateTime.now();
        for (ChiTietDatPhong ct : phongList) {
            if (ct != null && ct.getP() != null) {
                BigDecimal fee = phongService.calculateExtraFeeFor(
                        ct.getP().getMaPhong(), dp.getNgaydatPhong(), gioTraHienTai);
                if (fee != null && fee.signum() > 0) {
                    phuPhiTraMuon = phuPhiTraMuon.add(fee);
                }
            }
        }

        // Folio (tien phong + dich vu + VAT - giam gia)
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

        // Lich su thanh toan (neu co hoa don)
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

        // Text-format sẵn các số tiền có dấu +/- để template khỏi vướng literal Thymeleaf
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"));
        model.addAttribute("tienGiamText", "- " + nf.format(tienGiam) + " VND");
        model.addAttribute("daThuText", "- " + nf.format(daThu) + " VND");
        model.addAttribute("daHoanTraText", "+ " + nf.format(daHoanTra) + " VND");
    }

    // ================= THEM DICH VU PHAT SINH =================

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
        ct.setDonGia(dv.getGia().multiply(BigDecimal.valueOf(soLuong)));
        ct.setNgay_su_dung(LocalDateTime.now());
        ct.setGhichu("Phát sinh lúc trả phòng (admin)");
        chiTietDichVuService.save(ct);

        redirectAttributes.addFlashAttribute("success",
                "Đã thêm dịch vụ \"" + dv.getTen_dich_vu() + "\" vào đơn #" + id);
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    // ================= THU TIEN (soDu > 0) =================

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

        redirectAttributes.addFlashAttribute("success",
                "Đã thu " + canThu.toPlainString() + " VND cho đơn #" + id + ".");
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    // ================= HOAN TIEN (soDu < 0) =================

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

        redirectAttributes.addFlashAttribute("success",
                "Đã ghi nhận hoàn " + canHoan.toPlainString() + " VND cho đơn #" + id
                        + ". Yêu cầu đang chờ xử lý.");
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    // ================= CHOT TRA PHONG =================

    @PostMapping("/checkout/{id}/xac-nhan")
    public String adminCheckoutXacNhan(@PathVariable Integer id,
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
        redirectAttributes.addFlashAttribute("success",
                "Trả phòng thành công cho đơn #" + id + "." + hoaDonInfo);
        return "redirect:/nhan-su/admin/dat-phong/checkout/" + id;
    }

    // ================= XUAT HOA DON PDF =================

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
        context.setVariable("hoanTienList", hoanTienList);
        context.setVariable("tongHoan", tongHoan);

        // Su dung cung template PDF voi nhan-vien (vi html khong phu thuoc role)
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


