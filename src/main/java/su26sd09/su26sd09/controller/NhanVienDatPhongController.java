package su26sd09.su26sd09.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        DatPhong datPhong = datPhongService.findById(id);
        if (datPhong == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong #" + id);
            return "redirect:/nhan-su/dat-phong";
        }

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
        model.addAttribute("roomStatusJson", "[" + phongService.buildRoomStatusJson(tatCaPhong) + "]");
        // Số đêm để hiển thị chênh lệch trong form đổi phòng
        long soDem = Math.max(1, ChronoUnit.DAYS.between(
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
        model.addAttribute("chiTietDichVuList", ctdvService.findByDatPhongId(id));
        model.addAttribute("dichVuList", dichVuService.findActiveThuong());
        model.addAttribute("kmJson", buildKhuyenMaiJson());
        model.addAttribute("tongPhuThu", tongPhuThu);

        return "nhan-vien/chi-tiet-dat-phong";
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
        BigDecimal vatGoc = tienPhongGoc.add(tienDichVuGoc)
                .multiply(new BigDecimal("0.10"))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal tongTienKyVong = tienPhongGoc.add(tienDichVuGoc).add(vatGoc)
                .subtract(tienGiamGoc).add(tongPhuThu == null ? BigDecimal.ZERO : tongPhuThu);

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

        dp.setTrangThai("Da xac nhan");
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
            RedirectAttributes redirectAttributes) {

        // Dùng chung luồng với admin: tạo yêu cầu hủy + set "Cho xu ly" để NV/Admin xử lý thủ công
        KetQuaHuyDonDTO ketQua = huyDonService.huyDon(id);
        redirectAttributes.addFlashAttribute("thongBao", ketQua.getThongBao());

        if (ketQua.isCanHoanTien()) {
            // Có phát sinh hoàn tiền -> đi sang trang xử lý hoàn tiền của nhân viên
            return "redirect:/nhan-su/hoan-tien/chi-tiet/" + ketQua.getHoaDonId();
        }

        // Không phát sinh hoàn tiền -> quay lại danh sách đặt phòng
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
            // Validate CCCD mới (nếu nhập)
            String cccdMoi = (cccdMoiRaw == null || cccdMoiRaw.trim().isEmpty())
                    ? ct.getMa_cccd() // mặc định giữ nguyên
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