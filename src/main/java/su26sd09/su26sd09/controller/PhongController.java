package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import su26sd09.su26sd09.dto.PendingBookingDraft;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
import su26sd09.su26sd09.entity.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Controller
@RequestMapping("/phong")
public class PhongController {

    @Autowired
    private PhongService phongService;

    @Autowired
    private DanhGiaService danhGiaService;

    @Autowired
    private ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    private DatPhongService datphongservice;

    @Autowired
    private DichVuService dichVuService;

    @Autowired
    private ChiTietDichVuService ctdvService;

    @Autowired
    private NguoiDungService nguoiDungService;

    @Autowired
    private NhanVienService nhanVienService;

    @Autowired
    private khuyenMaiService khuyenMaiService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private BookingDraftService bookingDraftService;

    @Autowired
    private PendingBookingService pendingBookingService;

    @Autowired
    private BookingEmailService bookingEmailService;
//
////    @GetMapping
////    public String index(Model model) {
////        // Lấy tất cả phòng
////        List<Phong> phongs = phongService.findAllPhong();
////
////        // Lấy tiện nghi cho từng phòng
////        Map<Integer, List<String>> tienNghiTheoPhong = new HashMap<>();
////        Map<Integer, String> tenLoaiTheoPhong = new HashMap<>();
////        for (Phong phong : phongs) {
////            tienNghiTheoPhong.put(phong.getMaPhong(), phongService.findTenTienNghiByPhong(phong.getMaPhong()));
////            if (phong.getLoaiPhong() != null) {
////                tenLoaiTheoPhong.put(phong.getMaPhong(), phong.getLoaiPhong().getTenLoai());
////            }
////        }
////
////        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
////
////        HashMap<Integer, UUID> thumbAnhs = new HashMap<>();
////        for (Phong p: phongs
////             ) {
////            Integer id = p.getMaPhong();
////            PhongAnh pa = phongAnhRepository.findByMaPhongFirst(p.getMaPhong());
////            thumbAnhs.put(
////                    id,
////                    pa != null ? pa.maAnh.maAnh : null
////            );
////        }
////
////        model.addAttribute("thumbAnhs", thumbAnhs);
////        model.addAttribute("phongs", phongs);
////        model.addAttribute("tienNghiTheoPhong", tienNghiTheoPhong);
////        model.addAttribute("tenLoaiTheoPhong", tenLoaiTheoPhong);
////        model.addAttribute("loaiPhongs", loaiPhongs);
////        model.addAttribute("bookingGuardByPhong", phongService.buildRoomGuards(phongs));
////
////        return "rooms";
////    }
//
//    @GetMapping("/{id}")
//    public String detail(
//            @PathVariable("id") int id,
//            Model model,
//            RedirectAttributes redirectAttributes
//    ) {
//        Phong phong = phongService.findPhongById(id);
//        if (phong == null) {
//            redirectAttributes.addFlashAttribute("error", "Không tìm thấy phòng");
//            return "redirect:/phong";
//        }
//
//        LoaiPhong loaiPhong = phong.getLoaiPhong();
//        List<String> tienNghi = phongService.findTenTienNghiByPhong(phong.getMaPhong());
//        List<DanhGia> danhGias = danhGiaService.findDaDuyetByPhong(phong.getMaPhong());
//        double diemTrungBinh = danhGias.stream()
//                .mapToInt(DanhGia::getDiemDanhGia)
//                .average()
//                .orElse(0);
//
//        // Lấy tất cả loại phòng cho dropdown menu và carousel
//        List<LoaiPhong> loaiPhongs = phongService.findAllLoai();
//        Map<Integer, String> anhLoaiPhong = new HashMap<>();
//        for (LoaiPhong lp : loaiPhongs) {
//            anhLoaiPhong.put(lp.getId(), "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=800&q=80");
//        }
//
//        RoomBookingGuardDTO guard = phongService.buildRoomGuardFor(phong.getMaPhong());
//
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String currentEmail = (authentication != null
//                && authentication.isAuthenticated()
//                && !(authentication instanceof AnonymousAuthenticationToken))
//                ? authentication.getName()
//                : null;
//        model.addAttribute("reviewEligibility", reviewService.getEligibility(phong.getMaPhong(), currentEmail));
//
//        List<Anh> anhs = new ArrayList<>();
//        for (PhongAnh pa: phongAnhRepository.findByMaPhong_MaPhong(id)
//             ) {
//            anhs.add(pa.maAnh);
//        }
//        Anh thumbAnh = anhs.size() != 0 ? anhs.get(0) : null;
//        model.addAttribute("thumbAnh", thumbAnh);
//        model.addAttribute("anhs", anhs);
//        model.addAttribute("phong", phong);
//        model.addAttribute("loaiPhong", loaiPhong);
//        model.addAttribute("tienNghi", tienNghi);
//        model.addAttribute("danhGias", danhGias);
//        model.addAttribute("tongDanhGia", danhGias.size());
//        model.addAttribute("diemTrungBinh", diemTrungBinh);
//        model.addAttribute("loaiPhongs", loaiPhongs);
//        model.addAttribute("anhLoaiPhong", anhLoaiPhong);
//        model.addAttribute("bookingGuard", guard);
//        model.addAttribute("bookingLockedRangesJson", buildLockedRangesJson(guard));
//
//        System.out.println("Render phong = " + phong.getMaPhong());
//        System.out.println("Guard = " + guard);
//        System.out.println("Trang thai don gan nhat = " + guard.getTrangThaiDonGanNhat());
//        System.out.println("So khoang bi khoa = " + guard.getDanhSachKhoaLich().size());
//        for (su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO k : guard.getDanhSachKhoaLich()) {
//            System.out.println("  - Khoang khoa: " + k.getNgayBatDau() + " -> " + k.getNgayKetThuc()
//                    + " (trangThai=" + k.getTrangThaiDon() + ", maDatPhong=" + k.getMaDatPhong() + ")");
//        }
//
//        return "room-detail";
//    }
//
    @GetMapping("/dat-phong/xac-nhan/{id}")
    public String ConfirmOrder(@PathVariable int id, Model model,
                               RedirectAttributes redirectAttributes,
                               HttpServletRequest request){
        // id < 0 => ban nhap (CHUA co DatPhong that trong DB) tu
        // /loai-phong/dat-nhanh. Xem PendingBookingService.
        if (pendingBookingService.isPending(id)) {
            return confirmOrderPending(id, model, redirectAttributes, request);
        }
        DatPhong dp =  datphongservice.findById(id);
        if (dp.getTrangThai().equals("Da xac nhan")){
            return "redirect:/home";
        }
        BigDecimal resthue = BigDecimal.valueOf(soDem(dp.getNgaydatPhong(), dp.getNgaytraPhong()));
        List<ChiTietDatPhong> listCt = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> listctdv = ctdvService.findByDatPhongId(id);
        BigDecimal amountDv = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal amountP = BigDecimal.ZERO;
        if (listctdv != null) {
            for (Chi_tiet_dich_vu dv : listctdv) {
                amountDv = amountDv.add(dv.getDonGia());

            }
        }
        Map<Integer, Chi_tiet_dich_vu> dichVuDaChonMap = new HashMap<>();
        List<Integer> dichVuDaChonIds = new ArrayList<>();
        if (listctdv != null) {
            for (Chi_tiet_dich_vu dv : listctdv) {
                if (dv.getDv() != null) {
                    dichVuDaChonMap.put(dv.getDv().getId(), dv);
                    dichVuDaChonIds.add(dv.getDv().getId());
                }
            }
        }
        for(ChiTietDatPhong ct : listCt){
            amountP = amountP.add(ct.getGiaKhiDat());
            System.out.println("Chi tiet phong dang dat: "+ct.getP().getSoPhong());
            amount = amount.add(ct.getGiaKhiDat());

            System.out.println("Amount: "+amount);

        }
        System.out.println("AmountDv: "+amountDv);
        amount = amount.add(amountDv);
        // KM: ap dung tren TONG (phong + dich vu), sau do VAT 10% tinh tren gia SAU GIAM
        BigDecimal tienGiam = tinhTienGiam(amountP.add(amountDv), dp.getKm());
        BigDecimal tongSauGiam = amountP.add(amountDv).subtract(tienGiam);
        BigDecimal tienVat = tongSauGiam.multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal tongCong = tongSauGiam.add(tienVat);
        model.addAttribute("TienDv",amountDv);

        model.addAttribute("TongTien",amount);
        model.addAttribute("TienPhong",amountP);
        model.addAttribute("TienGiam", tienGiam);
        model.addAttribute("TongSauGiam", tongSauGiam);
        model.addAttribute("TienVat", tienVat);
        model.addAttribute("TongCong", tongCong);
        model.addAttribute("TongPhuPhi", datphongservice.sumExtraFeeForDatPhong(id));
        model.addAttribute("datPhong",dp);
        model.addAttribute("chiTietDatPhongList",listCt);
        model.addAttribute("nightCount",resthue);
        // Lấy dịch vụ thường để khách chọn bổ sung. Chấp nhận cả "THUONG" (do form admin tạo)
        // lẫn "dich vu" (dữ liệu seed cũ trong DB), bỏ qua các dịch vụ phát sinh / ngừng hoạt động.
        model.addAttribute("dichVuList", dichVuService.findAll().stream()
                .filter(n -> n.isHoatDong())
                .filter(n -> {
                    String loai = n.getLoaiDv();
                    if (loai == null) return true;
                    String loaiUpper = loai.trim().toUpperCase();
                    return loaiUpper.equals("THUONG") || loaiUpper.equals("DICH VU");
                })
                .toList());
        model.addAttribute("dichVuDaChonIds", dichVuDaChonIds);
        model.addAttribute("dichVuDaChonMap", dichVuDaChonMap);
        model.addAttribute("kmJson", buildKhuyenMaiJson());

        return "dat-phong-xac-nhan";
    }

    /**
     * Ban xem truoc (PREVIEW) trang chon dich vu bo sung cho 1 ban nhap
     * dang cho (id < 0), tai su dung dung 1 template "dat-phong-xac-nhan"
     * nhu don that. KHONG dong nao duoc ghi vao DB o day -
     * phongService.assignRoomsForType() chi doc, dung de tinh gia hien thi
     * (phong cu the co the doi khi tao that neu co khach khac dat truoc).
     */
    private String confirmOrderPending(int id, Model model, RedirectAttributes redirectAttributes,
                                        HttpServletRequest request) {
        su26sd09.su26sd09.dto.PendingBookingDraft draft = pendingBookingService.get(request, id);
        if (draft == null) {
            redirectAttributes.addFlashAttribute("timKiemError",
                    "Phien dat phong da het han hoac khong hop le. Vui long dat lai.");
            return "redirect:/loai-phong";
        }

        List<Phong> phongDuocChon;
        try {
            phongDuocChon = phongService.assignRoomsForType(
                    draft.getLoaiPhongId(), draft.getSoLuong(), draft.getNgayNhan(), draft.getNgayTra());
        } catch (IllegalStateException | IllegalArgumentException e) {
            pendingBookingService.remove(request, id);
            redirectAttributes.addFlashAttribute("timKiemError", e.getMessage());
            return "redirect:/loai-phong/" + draft.getLoaiPhongId();
        }

        if (!phongDuocChon.isEmpty()) {
            int tongSucChua = phongDuocChon.stream()
                    .filter(p -> p.getLoaiPhong() != null)
                    .mapToInt(p -> p.getLoaiPhong().getSucChuaToiDa())
                    .sum();
            int tongNguoi = draft.getNguoiLon() + draft.getTreEm();
            if (tongNguoi > tongSucChua) {
                model.addAttribute("canhBaoSucChua",
                        "Tong nguoi (" + tongNguoi + ") vuot suc chua toi da (" + tongSucChua
                                + ") cua " + phongDuocChon.size() + " phong dang chon. "
                                + "Yeu cau se duoc nhan vien xu ly.");
            }
        }

        DatPhong dp = buildTransientDatPhong(id, draft);

        long soDemVal = soDem(draft.getNgayNhan(), draft.getNgayTra());
        BigDecimal amountP = BigDecimal.ZERO;
        BigDecimal tongPhuPhi = BigDecimal.ZERO;
        List<ChiTietDatPhong> listCt = new ArrayList<>();
        for (Phong p : phongDuocChon) {
            BigDecimal phuPhi = phongService.calculateExtraFeeFor(p.getMaPhong(), draft.getNgayNhan(), draft.getNgayTra());
            BigDecimal giaKhiDat = p.getGiaMoiDem().multiply(BigDecimal.valueOf(soDemVal)).add(phuPhi);
            ChiTietDatPhong ct = new ChiTietDatPhong();
            ct.setP(p);
            ct.setGiaMoiDem(p.getGiaMoiDem());
            ct.setGiaKhiDat(giaKhiDat);
            ct.setPhuPhi(phuPhi);
            ct.setD(dp);
            listCt.add(ct);
            amountP = amountP.add(giaKhiDat);
            tongPhuPhi = tongPhuPhi.add(phuPhi);
        }

        Map<Integer, Chi_tiet_dich_vu> dichVuDaChonMap = new HashMap<>();
        List<Integer> dichVuDaChonIds = new ArrayList<>();
        BigDecimal amountDv = BigDecimal.ZERO;
        if (draft.getDichVuIds() != null) {
            for (Integer maDichVu : draft.getDichVuIds()) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;
                String slStr = draft.getSoLuongDichVu().get(maDichVu);
                int sl = (slStr != null && !slStr.isBlank()) ? Integer.parseInt(slStr) : 1;
                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setSoluong(sl);
                ct.setDv(dv);
                // Tong tien dich vu = gia * soLuong * nightCount (moi ngay luu tru deu su dung)
                ct.setDonGia(dv.getGia().multiply(BigDecimal.valueOf(sl)).multiply(BigDecimal.valueOf(soDemVal)));
                ct.setNgay_su_dung(draft.getNgayNhan());
                dichVuDaChonMap.put(maDichVu, ct);
                dichVuDaChonIds.add(maDichVu);
                amountDv = amountDv.add(ct.getDonGia());
            }
        }

        KhuyenMai km = draft.getMaKhuyenMai() != null ? khuyenMaiService.findbyId(draft.getMaKhuyenMai()) : null;
        dp.setKm(km);
        // KM: ap dung tren TONG (phong + dich vu), sau do VAT 10% tinh tren gia SAU GIAM
        BigDecimal tienGiam = tinhTienGiam(amountP.add(amountDv), km);
        BigDecimal tongSauGiam = amountP.add(amountDv).subtract(tienGiam);
        BigDecimal tienVat = tongSauGiam.multiply(new BigDecimal("0.10")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal tongCong = tongSauGiam.add(tienVat);
        BigDecimal amount = amountP.add(amountDv);

        model.addAttribute("TienDv", amountDv);
        model.addAttribute("TongTien", amount);
        model.addAttribute("TienPhong", amountP);
        model.addAttribute("TienGiam", tienGiam);
        model.addAttribute("TongSauGiam", tongSauGiam);
        model.addAttribute("TienVat", tienVat);
        model.addAttribute("TongCong", tongCong);
        model.addAttribute("TongPhuPhi", tongPhuPhi);
        model.addAttribute("datPhong", dp);
        model.addAttribute("chiTietDatPhongList", listCt);
        model.addAttribute("nightCount", BigDecimal.valueOf(soDemVal));
        model.addAttribute("dichVuList", dichVuService.findAll().stream()
                .filter(n -> n.isHoatDong())
                .filter(n -> {
                    String loai = n.getLoaiDv();
                    if (loai == null) return true;
                    String loaiUpper = loai.trim().toUpperCase();
                    return loaiUpper.equals("THUONG") || loaiUpper.equals("DICH VU");
                })
                .toList());
        model.addAttribute("dichVuDaChonIds", dichVuDaChonIds);
        model.addAttribute("dichVuDaChonMap", dichVuDaChonMap);
        model.addAttribute("kmJson", buildKhuyenMaiJson());

        return "dat-phong-xac-nhan";
    }

    @GetMapping("/dat-phong/tiep-tuc-pending/{id}")
    public String tiepTucPending(
            @PathVariable int id,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        // Chỉ xử lý pending id (số âm)
        if (!pendingBookingService.isPending(id)) {
            return "redirect:/phong/dat-phong/xac-nhan/" + id;
        }

        PendingBookingDraft draft = pendingBookingService.get(request, id);

        // Draft không còn tồn tại
        if (draft == null) {
            redirectAttributes.addFlashAttribute(
                    "bookingError",
                    "Phiên đặt phòng của bạn đã hết hạn. Vui lòng đặt lại."
            );
            return "redirect:/";
        }

        String currentStep = draft.getCurrentStep();

        // Nếu đang dừng ở bước thông tin khách
        if ("thong-tin-khach".equals(currentStep)) {
            return "redirect:/phong/dat-phong/thong-tin-khach/" + id;
        }

        // Mặc định quay về bước xác nhận dịch vụ
        return "redirect:/phong/dat-phong/xac-nhan/" + id;
    }

    /** Doi tuong DatPhong CHUA LUU (transient) chi de hien thi template, dung id am cua ban nhap. */
    private DatPhong buildTransientDatPhong(int pendingId, su26sd09.su26sd09.dto.PendingBookingDraft draft) {
        DatPhong dp = new DatPhong();
        dp.setId(pendingId);
        dp.setNgaydatPhong(draft.getNgayNhan());
        dp.setNgaytraPhong(draft.getNgayTra());
        dp.setSonguoiLon(draft.getNguoiLon());
        dp.setSotreEm(draft.getTreEm());
        dp.setMa_cccd(draft.getMaCccd());
        dp.setTrangThai("Yeu cau dat phong");
        dp.setHoten(draft.getHoten());
        dp.setEmail(draft.getEmail());
        dp.setSdt(draft.getSdt());
        dp.setYeuCauThem(draft.getYeuCauThem());
        return dp;
    }
//
    @PostMapping("/dat-phong/xac-nhan/{id}")
    public String ConfirmDV(@PathVariable int id,
                            @RequestParam(value = "dichVuIds", required = false) List<Integer> dichvuid,
                            @RequestParam(value = "maKhuyenMai", required = false) Integer maKhuyenMai,
                            @RequestParam Map<String, String> allParams,
                            RedirectAttributes redirectAttributes,
                            HttpServletRequest request,
                            HttpServletResponse response) {

        if (pendingBookingService.isPending(id)) {
            return confirmDVPending(id, dichvuid, maKhuyenMai, allParams, redirectAttributes, request);
        }

        DatPhong dp = datphongservice.findById(id);
        if (dp == null) {
            return "redirect:/home";
        }

        // ===== Lưu khuyến mãi + dịch vụ (đã bỏ input "Ngày sử dụng" - số lần dùng = nightCount) =====
        dp.setKm(null);
        if (maKhuyenMai != null) {
            KhuyenMai km = khuyenMaiService.findbyId(maKhuyenMai);
            if (km != null && km.isHoatDong()) {
                dp.setKm(km);
            }
        }
        datphongservice.save(dp);

        ctdvService.deleteByDatPhongId(id);

        // nightCount = so dem luu tru (it nhat 1), dung de nhan gia dich vu
        long nightCount = soDem(dp.getNgaydatPhong(), dp.getNgaytraPhong());

        if (dichvuid != null) {
            for (Integer maDichVu : dichvuid) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;

                String slStr = allParams.get("soLuong_" + maDichVu);
                int sl = (slStr != null && !slStr.isBlank()) ? Integer.parseInt(slStr) : 1;

                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setSoluong(sl);
                ct.setDatPhong(dp);
                ct.setDv(dv);
                // Tong tien dich vu = gia * soLuong * nightCount
                ct.setDonGia(dv.getGia().multiply(BigDecimal.valueOf(sl)).multiply(BigDecimal.valueOf(nightCount)));
                // Filler: ngay su dung = ngay nhan phong (schema cot NOT NULL, nguoi dung khong chon nua)
                ct.setNgay_su_dung(dp.getNgaydatPhong());
                ctdvService.save(ct);
            }
        }

        if (dp.getN() != null && dp.getN().getVaiTro() != null && "ROLE_KHACHHANG".equals(dp.getN().getVaiTro().getTenVaiTro())){
            KhachHang kh = dp.getN();
            if (dp.getHoten() == null || dp.getHoten().isBlank()) dp.setHoten(kh.getHoTen());
            if (dp.getEmail() == null || dp.getEmail().isBlank())   dp.setEmail(kh.getEmail());
            if (dp.getSdt() == null || dp.getSdt().isBlank())       dp.setSdt(kh.getSoDienThoai());
            datphongservice.save(dp);
            return "redirect:/thanh-toan/dat-phong/"+dp.getId();
        }else{
            // Cập nhật COOKIE: sau khi đã qua bước DV, đơn vãng lai vẫn đang dở.
            // Cookie sống ở trình duyệt nên không bị mất khi server restart.
            if (dp.getN() == null) {
                bookingDraftService.remember(request, response, dp.getId());
            }
            return "redirect:/phong/dat-phong/thong-tin-khach/"+dp.getId();
        }
    }

    /**
     * Tuong duong ConfirmDV nhung cho ban nhap dang cho (id < 0): chi luu
     * lua chon dich vu bo sung + khuyen mai VAO SESSION (draft), KHONG dung
     * cham gi den DB - DatPhong that chi duoc tao o buoc "Hoan tat dat phong".
     */
    private String confirmDVPending(int id, List<Integer> dichvuid, Integer maKhuyenMai,
                                     Map<String, String> allParams, RedirectAttributes redirectAttributes,
                                     HttpServletRequest request) {
        su26sd09.su26sd09.dto.PendingBookingDraft draft = pendingBookingService.get(request, id);
        if (draft == null) {
            redirectAttributes.addFlashAttribute("timKiemError",
                    "Phien dat phong da het han hoac khong hop le. Vui long dat lai.");
            return "redirect:/loai-phong";
        }

        Map<Integer, String> soLuongMap = new HashMap<>();
        if (dichvuid != null) {
            for (Integer maDichVu : dichvuid) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;
                String slStr = allParams.get("soLuong_" + maDichVu);
                soLuongMap.put(maDichVu, slStr);
            }
        }

        draft.setDichVuIds(dichvuid);
        draft.setSoLuongDichVu(soLuongMap);
        draft.setNgaySuDungDichVu(new HashMap<>()); // khong con input ngay su dung
        draft.setMaKhuyenMai(maKhuyenMai);
        draft.setCurrentStep("thong-tin-khach");

        pendingBookingService.update(request, id, draft);

        return "redirect:/phong/dat-phong/thong-tin-khach/" + id;
    }

//    /**
//     * Phục hồi luồng đặt phòng cho khách VÃNG LAI (không có tài khoản).
//     *
//     * Khác với UserProfilesController.tiepTucDatPhong (cần đăng nhập + quyền sở hữu),
//     * endpoint này dựa vào COOKIE GUEST_BOOKING_DRAFT do GioHangController
//     * và PhongController.ConfirmDV ghi nhớ sau khi tạo đơn / qua bước DV.
//     * Cookie sống ở trình duyệt nên KHÔNG bị mất khi server restart.
//     */
    @GetMapping("/dat-phong/tiep-tuc-dat/{id}")
    public String tiepTucDatPhongVangLai(@PathVariable int id,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         RedirectAttributes redirectAttributes) {
        DatPhong dp = bookingDraftService.peek(request);
        if (dp == null || dp.getId() != id) {
            redirectAttributes.addFlashAttribute("bookingError",
                    "Đơn đặt phòng này không thuộc trình duyệt của bạn hoặc đã hết hạn. Vui lòng đặt lại từ đầu.");
            bookingDraftService.consume(request, response);

            return "redirect:/phong";
        }

        // ====== Backup thông minh: kiểm tra khách đang thiếu gì rồi redirect đúng trang ======
        // Quy tắc ưu tiên (theo thứ tự):
        //   1. Thiếu thông tin khách (hoten/email/sdt) -> về trang điền thông tin
        //   2. Đủ thông tin -> về trang thanh toán VNPay
        //   (DV bổ sung là optional, KHÔNG ép khách quay lại chọn DV)
        boolean thieuThongTin = dp.getHoten() == null || dp.getHoten().isBlank()
                || dp.getEmail() == null || dp.getEmail().isBlank()
                || dp.getSdt() == null || dp.getSdt().isBlank();

        if (thieuThongTin) {
            redirectAttributes.addFlashAttribute("thongBao",
                    "Tiếp tục đặt phòng: vui lòng hoàn tất thông tin khách trước khi thanh toán.");
            return "redirect:/phong/dat-phong/thong-tin-khach/" + id;
        }

        // Đủ thông tin rồi -> sang trang thanh toán VNPay
        redirectAttributes.addFlashAttribute("thongBao",
                "Tiếp tục đặt phòng: vui lòng hoàn tất thanh toán.");
        return "redirect:/thanh-toan/dat-phong/" + id;
    }
//
    @GetMapping("/dat-phong/thong-tin-khach/{id}")
    public String ConfirmCustomerInfor(@PathVariable int id, Model model, Authentication authentication,
                                       HttpServletRequest request){

        if (pendingBookingService.isPending(id)) {
            return confirmCustomerInforPending(id, model, authentication, request);
        }

        DatPhong dp = datphongservice.findById(id);
        if (dp == null) {
            return "redirect:/home";
        }
        if ("Da xac nhan".equals(dp.getTrangThai())){
            return "redirect:/home";
        }

        // ===== BỎ QUA BƯỚC NHẬP THÔNG TIN NẾU LÀ KHÁCH CÓ TÀI KHOẢN (ROLE_KHACHHANG) =====
        // Có 2 cách xác định: đơn đã gắn user (dp.getN() != null) HOẶC user hiện tại đang login là ROLE_KHACHHANG
        KhachHang currentKhach = null;
        boolean isRoleGuest = false;

        // Cách 1: đơn đã được gắn với user từ trước
        if (dp.getN() != null
                && dp.getN().getVaiTro() != null
                && "ROLE_KHACHHANG".equals(dp.getN().getVaiTro().getTenVaiTro())) {
            isRoleGuest = true;
            currentKhach = dp.getN();
        }

        // Cách 2: user hiện tại đang đăng nhập với role ROLE_KHACHHANG (kể cả khi đơn chưa gắn user)
        if (!isRoleGuest && authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            KhachHang nd = nguoiDungService.findByEmail(authentication.getName());
            if (nd != null && nd.getVaiTro() != null
                    && "ROLE_KHACHHANG".equals(nd.getVaiTro().getTenVaiTro())) {
                isRoleGuest = true;
                currentKhach = nd;
                // Gắn user vào đơn nếu đơn chưa gắn (đơn tạo trước khi login)
                if (dp.getN() == null) {
                    dp.setN(nd);
                }
            }
        }

        if (isRoleGuest && currentKhach != null) {
            // Khach da co tai khoan (ROLE_KHACHHANG): KHONG con bo qua buoc nay nua -
            // van hien thi trang xac nhan thong tin, nhung Ho ten/Email/SDT duoc
            // tu dong dien tu tai khoan va khoa (readonly) o giao dien - xem
            // "khachDaDangNhap" duoc truyen xuong view ben duoi.
            dp.setHoten(currentKhach.getHoTen());
            dp.setEmail(currentKhach.getEmail());
            dp.setSdt(currentKhach.getSoDienThoai());
        }
        model.addAttribute("khachDaDangNhap", isRoleGuest && currentKhach != null);

        List<ChiTietDatPhong> listCt = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> listctdv = ctdvService.findByDatPhongId(id);
        BigDecimal amountDv = BigDecimal.ZERO;
       model.addAttribute("datPhong",dp);
        System.out.println("Debug dat phong Ngay nhan phong: "+dp.getNgaydatPhong());
       long nightCount = ChronoUnit.DAYS.between(dp.getNgaydatPhong().toLocalDate(), dp.getNgaytraPhong().toLocalDate());
       model.addAttribute("nightCount", Math.max(1, nightCount));
       model.addAttribute("chiTietDatPhongList",listCt);
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal ThueVat = new BigDecimal("0.10");


        BigDecimal resThue = BigDecimal.valueOf(dp.getNgaytraPhong().getDayOfYear() - dp.getNgaydatPhong().getDayOfYear());
        for(Chi_tiet_dich_vu dv : listctdv){
            amountDv = amountDv.add(dv.getDonGia());
        }
        model.addAttribute("TienDv",amountDv);
        for (ChiTietDatPhong chiTietDatPhong : listCt){
            amount = amount.add(chiTietDatPhong.getGiaKhiDat());
            System.out.println("So tien: "+chiTietDatPhong.getGiaKhiDat() + "Amount: "+amount );

            System.out.println("In for each loops: "+chiTietDatPhong.getGiaMoiDem());

        }
        // KM: ap dung tren TONG (phong + dich vu), VAT 10% tinh tren gia SAU GIAM
        BigDecimal tienGiam = tinhTienGiam(amount.add(amountDv), dp.getKm());
        BigDecimal tongSauGiam = amount.add(amountDv).subtract(tienGiam);
        BigDecimal TienVat = tongSauGiam.multiply(ThueVat).setScale(2, RoundingMode.HALF_UP);
        BigDecimal TotalAmount = tongSauGiam.add(TienVat);
        System.out.println("Amount: "+ amount);
        model.addAttribute("TienVat",TienVat);
        model.addAttribute("TienPhong",amount);
        model.addAttribute("TienGiam", tienGiam);
        model.addAttribute("TongTien",TotalAmount);
        model.addAttribute("TongCong",TotalAmount);
        model.addAttribute("TongPhuPhi", datphongservice.sumExtraFeeForDatPhong(id));
        model.addAttribute("chiTietDichVuList", listctdv);
        return "dat-phong-thong-tin-khach";
    }

    /**
     * Ban nhap (id < 0). Luon hien thi trang xac nhan thong tin (preview),
     * chua tao gi trong DB o day. Neu khach hien dang dang nhap voi tai
     * khoan da co (ROLE_KHACHHANG) thi tu dong dien Ho ten/Email/SDT tu tai
     * khoan va khoa (readonly) cac truong nay o giao dien (xem
     * "khachDaDangNhap"). DatPhong that chi duoc tao khi khach bam nut
     * "Hoan tat dat phong" (xem SaveXacThucThongTin).
     */
    private String confirmCustomerInforPending(int id, Model model, Authentication authentication,
                                                HttpServletRequest request) {
        su26sd09.su26sd09.dto.PendingBookingDraft draft = pendingBookingService.get(request, id);
        if (draft == null) {
            return "redirect:/loai-phong";
        }

        KhachHang currentKhach = null;
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            KhachHang nd = nguoiDungService.findByEmail(authentication.getName());
            if (nd != null && nd.getVaiTro() != null
                    && "ROLE_KHACHHANG".equals(nd.getVaiTro().getTenVaiTro())) {
                currentKhach = nd;
            }
        }

        // Khach da co tai khoan (ROLE_KHACHHANG): KHONG con tao DatPhong that va
        // chuyen thang sang thanh toan nua - van hien thi trang xac nhan thong
        // tin (preview) nhu khach vang lai, nhung se tu dong dien + khoa (readonly)
        // Ho ten/Email/SDT tu tai khoan (xem buildTransientDatPhong ben duoi va
        // "khachDaDangNhap" duoc truyen xuong view).
        boolean khachDaDangNhap = currentKhach != null;

        // Khach vang lai / khach da co tai khoan: preview, chua tao gi trong DB
        // (DatPhong that chi duoc tao khi bam "Hoan tat dat phong" - xem
        // SaveXacThucThongTin).
        List<Phong> phongDuocChon;
        try {
            phongDuocChon = phongService.assignRoomsForType(
                    draft.getLoaiPhongId(), draft.getSoLuong(), draft.getNgayNhan(), draft.getNgayTra());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return "redirect:/loai-phong/" + draft.getLoaiPhongId();
        }

        DatPhong dp = buildTransientDatPhong(id, draft);
        if (khachDaDangNhap) {
            dp.setHoten(currentKhach.getHoTen());
            dp.setEmail(currentKhach.getEmail());
            dp.setSdt(currentKhach.getSoDienThoai());
        }
        long soDemVal = soDem(draft.getNgayNhan(), draft.getNgayTra());

        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tongPhuPhi = BigDecimal.ZERO;
        List<ChiTietDatPhong> listCt = new ArrayList<>();
        for (Phong p : phongDuocChon) {
            BigDecimal phuPhi = phongService.calculateExtraFeeFor(p.getMaPhong(), draft.getNgayNhan(), draft.getNgayTra());
            BigDecimal giaKhiDat = p.getGiaMoiDem().multiply(BigDecimal.valueOf(soDemVal)).add(phuPhi);
            ChiTietDatPhong ct = new ChiTietDatPhong();
            ct.setP(p);
            ct.setGiaMoiDem(p.getGiaMoiDem());
            ct.setGiaKhiDat(giaKhiDat);
            ct.setPhuPhi(phuPhi);
            ct.setD(dp);
            listCt.add(ct);
            amount = amount.add(giaKhiDat);
            tongPhuPhi = tongPhuPhi.add(phuPhi);
        }

        List<Chi_tiet_dich_vu> listctdv = new ArrayList<>();
        BigDecimal amountDv = BigDecimal.ZERO;
        if (draft.getDichVuIds() != null) {
            for (Integer maDichVu : draft.getDichVuIds()) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;
                String slStr = draft.getSoLuongDichVu().get(maDichVu);
                int sl = (slStr != null && !slStr.isBlank()) ? Integer.parseInt(slStr) : 1;
                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setSoluong(sl);
                ct.setDv(dv);
                ct.setDonGia(dv.getGia().multiply(BigDecimal.valueOf(sl)));
                listctdv.add(ct);
                amountDv = amountDv.add(ct.getDonGia());
            }
        }

        KhuyenMai km = draft.getMaKhuyenMai() != null ? khuyenMaiService.findbyId(draft.getMaKhuyenMai()) : null;
        dp.setKm(km);

        // KM: ap dung tren TONG (phong + dich vu), VAT 10% tinh tren gia SAU GIAM
        BigDecimal tienGiam = tinhTienGiam(amount.add(amountDv), km);
        BigDecimal tongSauGiam = amount.add(amountDv).subtract(tienGiam);
        BigDecimal thueVat = new BigDecimal("0.10");
        BigDecimal tienVat = tongSauGiam.multiply(thueVat).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = tongSauGiam.add(tienVat);

        model.addAttribute("datPhong", dp);
        model.addAttribute("nightCount", Math.max(1, soDemVal));
        model.addAttribute("chiTietDatPhongList", listCt);
        model.addAttribute("TienDv", amountDv);
        model.addAttribute("TienVat", tienVat);
        model.addAttribute("TienPhong", amount);
        model.addAttribute("TienGiam", tienGiam);
        model.addAttribute("TongTien", totalAmount);
        model.addAttribute("TongCong", totalAmount);
        model.addAttribute("TongPhuPhi", tongPhuPhi);
        model.addAttribute("chiTietDichVuList", listctdv);
        model.addAttribute("khachDaDangNhap", khachDaDangNhap);
        return "dat-phong-thong-tin-khach";
    }

    /**
     * Tao DatPhong THAT (+ ChiTietDatPhong, Chi_tiet_dich_vu, khuyen mai) tu
     * 1 ban nhap. Day la DIEM DUY NHAT ma booking that su duoc tao trong
     * luong /loai-phong -> /phong/dat-phong/... (khac voi truoc day, luc
     * DatPhong duoc tao ngay khi khach bam "Dat phong"/"Dat phong loai
     * nay"). Duoc goi tu SaveXacThucThongTin (khach bam "Hoan tat dat
     * phong") va tu confirmCustomerInforPending (khach da dang nhap, du
     * thong tin lien he nen duoc bo qua form).
     */
    private DatPhong createBookingFromDraft(su26sd09.su26sd09.dto.PendingBookingDraft draft, KhachHang khachHang) {
        // FIX (race condition): assignRoomsForType() chi "synchronized" cho BUOC
        // CHON phong (doc DB + so sanh trong bo nho), nhung viec THUC SU GHI
        // (ChiTietDatPhong) danh dau phong da bi giu cho lai nam o
        // createAutoAssignedBooking() - truoc day goi RIENG BEN NGOAI khoi
        // synchronized block. Giua 2 buoc do, mot request khac (tu chinh luong
        // nay, tu "Dat phong tai quay", hoac "Len lich dat phong" o So Do Phong)
        // co the xen vao va cung chon/giu duoc CUNG mot phong cho khoang ngay
        // trung nhau (double-booking). De dong lai ke ho nay, gop CA HAI buoc
        // "chon phong" + "ghi giu cho" vao CHUNG 1 khoi synchronized, dung
        // chung 1 khoa (phongService - CUNG mot bean/monitor voi
        // "synchronized" instance method assignRoomsForType(), nen khoa nay
        // se loai tru lan nhau voi TAT CA cac diem tao dat phong khac trong
        // he thong cung dang khoa tren phongService).
        List<Phong> phongDuocChon;
        DatPhong datPhong;
        synchronized (phongService) {
            phongDuocChon = phongService.assignRoomsForType(
                    draft.getLoaiPhongId(), draft.getSoLuong(), draft.getNgayNhan(), draft.getNgayTra());

            datPhong = datphongservice.createAutoAssignedBooking(
                    phongDuocChon, khachHang, draft.getNgayNhan(), draft.getNgayTra(),
                    draft.getNguoiLon(), draft.getTreEm(), draft.getMaCccd());
        }

        if (draft.getMaKhuyenMai() != null) {
            KhuyenMai km = khuyenMaiService.findbyId(draft.getMaKhuyenMai());
            if (km != null && km.isHoatDong()) {
                datPhong.setKm(km);
            }
        }

        if (draft.getDichVuIds() != null) {
            for (Integer maDichVu : draft.getDichVuIds()) {
                Dich_vu dv = dichVuService.findById(maDichVu);
                if (dv == null) continue;

                String slStr = draft.getSoLuongDichVu().get(maDichVu);
                int sl = (slStr != null && !slStr.isBlank()) ? Integer.parseInt(slStr) : 1;

                String ngayStr = draft.getNgaySuDungDichVu().get(maDichVu);
                LocalDateTime ngaySuDung = (ngayStr != null && !ngayStr.isBlank())
                        ? LocalDateTime.parse(ngayStr)
                        : LocalDateTime.now();

                Chi_tiet_dich_vu ct = new Chi_tiet_dich_vu();
                ct.setSoluong(sl);
                ct.setDatPhong(datPhong);
                ct.setDv(dv);
                ct.setDonGia(dv.getGia().multiply(BigDecimal.valueOf(sl)));
                ct.setNgay_su_dung(ngaySuDung);
                ctdvService.save(ct);
            }
        }

        datphongservice.save(datPhong);

        try {
            bookingEmailService.guiEmailYeuCauDatPhong(datPhong.getId());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return datPhong;
    }
//
    private BigDecimal tinhTienGiam(BigDecimal tienPhong, KhuyenMai km) {
        if (km == null || !km.isHoatDong() || km.getGiatriGiam() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal dieuKien = km.getGiaToiThieuDuocGiam() == null ? BigDecimal.ZERO : km.getGiaToiThieuDuocGiam();
        if (tienPhong.compareTo(dieuKien) < 0) {
            return BigDecimal.ZERO;
        }
        if ("PERCENT".equalsIgnoreCase(km.getLoaiGiam())) {
            return tienPhong.multiply(km.getGiatriGiam())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if ("AMOUNT".equalsIgnoreCase(km.getLoaiGiam()) || "FIXED".equalsIgnoreCase(km.getLoaiGiam())) {
            return km.getGiatriGiam().min(tienPhong);
        }
        return BigDecimal.ZERO;
    }
//
    private String buildKhuyenMaiJson() {
        List<KhuyenMai> kmList = khuyenMaiService.findAllActive().toList();
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
//
    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
//
    @PostMapping("/dat-phong/thong-tin-khach/{id}")
    public String SaveXacThucThongTin(Model model,
                                      @PathVariable int id,
                                      @RequestParam("hoTen") String hoten,
                                      @RequestParam("email") String email,
                                      @RequestParam("sdt")String sodienthoai,

                                      @RequestParam("yeuCauThem") String yeucauthem,
                                      Authentication authentication,
                                      HttpServletRequest request,
                                      HttpServletResponse response,
                                      RedirectAttributes redirectAttributes) {
            // ===== DIEM DUY NHAT tao DatPhong that cho luong /loai-phong: khach
            // vua bam "Hoan tat dat phong" va vua nhap du ho ten/email/sdt.
            // Truoc do (buoc /loai-phong/dat-nhanh, /phong/dat-phong/xac-nhan)
            // hoan toan CHUA co dong nao trong bang dat_phong - moi thu chi la
            // ban nhap trong SESSION - de tranh tao "don rac" giu phong truoc
            // khach khac khi khach chua he cung cap thong tin lien he. =====
            if (pendingBookingService.isPending(id)) {
                su26sd09.su26sd09.dto.PendingBookingDraft draft = pendingBookingService.get(request, id);
                if (draft == null) {
                    redirectAttributes.addFlashAttribute("timKiemError",
                            "Phien dat phong da het han hoac khong hop le. Vui long dat lai.");
                    return "redirect:/loai-phong";
                }

                Authentication auth0 = SecurityContextHolder.getContext().getAuthentication();
                KhachHang khachHang = null;
                if (auth0 != null && auth0.isAuthenticated()
                        && !(auth0 instanceof AnonymousAuthenticationToken)
                        && !isNhanVienOrAdmin(auth0)) {
                    khachHang = nguoiDungService.findByEmail(auth0.getName());
                }

                DatPhong dpThat;
                try {
                    dpThat = createBookingFromDraft(draft, khachHang);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    redirectAttributes.addFlashAttribute("timKiemError", e.getMessage());
                    return "redirect:/loai-phong/" + draft.getLoaiPhongId();
                }

                dpThat.setHoten(khachHang != null ? khachHang.getHoTen() : hoten);
                dpThat.setEmail(khachHang != null ? khachHang.getEmail() : email);
                dpThat.setSdt(khachHang != null ? khachHang.getSoDienThoai() : sodienthoai);
                dpThat.setYeuCauThem(yeucauthem);
                datphongservice.save(dpThat);

            // Xóa PendingBookingDraft khỏi Session
                pendingBookingService.remove(request, id);

            // Xóa cookie backup Pending
                pendingBookingService.consume(response);

            // Tạo cookie backup DatPhong thật
                if (dpThat.getN() == null) {

                    bookingDraftService.remember(
                            request,
                            response,
                            dpThat.getId()
                    );
                }

                return "redirect:/thanh-toan/dat-phong/" + dpThat.getId();
            }

            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal amountdv = BigDecimal.ZERO;
            DatPhong dp = datphongservice.findById(id);
            if(dp ==null) {
                return "dat-phong-thong-tin-khach";
            }
            List<NhanSu> listNv = nhanVienService.findAll();
            Stream<NhanSu> ListnvLeTan = listNv.stream().filter(nv -> nv.getBoPhan().equalsIgnoreCase("lễ tân"));

            List<ChiTietDatPhong> listCtdp = chiTietDatPhongService.findByDatPhongId(id);
            List<Chi_tiet_dich_vu> listCtdv = ctdvService.findByDatPhongId(id);
            for(ChiTietDatPhong ctdp : listCtdp){
                amount = amount.add(ctdp.getGiaKhiDat());
            }
            for(Chi_tiet_dich_vu ctdv : listCtdv){
                amountdv = amountdv.add(ctdv.getDonGia());
            }
            amount = amount.add(amountdv);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String  emailSearch = null;
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            emailSearch = auth.getName();
        } else {
            for (NhanSu nv : ListnvLeTan.toList()) {
                emailSearch = nv.getEmail();
            }
        }

        NhanSu n = nhanVienService.FindByemail(emailSearch);

        boolean isNvDp = n != null
                && n.getVaitro() != null
                && "ROLE_STAFF".equals(n.getVaitro().getTenVaiTro());

        if (isNvDp) {
            // Nhân viên đang thao tác -> gán nhân viên đó vào nv
            dp.setNv(n);
        } else {
            // Khách vãng lai -> không gán nv, chỉ dùng hoten để hiển thị
            dp.setNv(null);
        }
        System.out.println("Amount Xac nhan thong tin khach hang: "+amount);
        System.out.println("Amount dich vu xac nhan thong tin khach hang: "+amountdv);
            KhachHang khachDangNhap = null;
            if (!isNvDp && auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                KhachHang ndAuth = nguoiDungService.findByEmail(auth.getName());
                if (ndAuth != null && ndAuth.getVaiTro() != null
                        && "ROLE_KHACHHANG".equals(ndAuth.getVaiTro().getTenVaiTro())) {
                    khachDangNhap = ndAuth;
                }
            }
            // Khach da dang nhap: Ho ten/Email/SDT bi khoa (readonly) o giao dien,
            // nen luon lay tu tai khoan thay vi tin tuong gia tri gui len tu form
            // de tranh bi sua tay qua devtools.
            dp.setHoten(khachDangNhap != null ? khachDangNhap.getHoTen() : hoten);
            dp.setEmail(khachDangNhap != null ? khachDangNhap.getEmail() : email);
            dp.setSdt(khachDangNhap != null ? khachDangNhap.getSoDienThoai() : sodienthoai);
            dp.setYeuCauThem(yeucauthem);

            datphongservice.save(dp);

            // LƯU COOKIE để backup trong luồng VNPay: nếu khách bấm "Thanh toán qua
            // VNPay" rồi bị out ra ngoài (mất mạng, đóng tab,...) trước khi VNPay
            // callback thì khi quay lại trang chủ vẫn còn chuông nhắc "đơn đang
            // chờ thanh toán" để tiếp tục. Cookie CHỈ được xóa khi thanh toán
            // thành công (xem ThanhToanController.vnpayParser hoặc callback VNPay).
            // KHÔNG xóa ở đây vì khách chưa thanh toán xong.
            bookingDraftService.remember(request, response, dp.getId());

            return "redirect:/thanh-toan/dat-phong/"+dp.getId();
    }
    @PostMapping("/dat-phong/quick")
    public String quickBooking(@RequestParam Integer maLoaiPhong,
                               @RequestParam Integer maPhong,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ngayNhan,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ngayTra,
                               @RequestParam Integer nguoiLon,
                               @RequestParam Integer treEm,

                               @RequestParam(required = false) String yeuCauThem,
                               @RequestParam(required = false) String ma_cccd,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        System.out.println("vao Controller");
        Phong phong = phongService.findById(maPhong);
        RoomBookingGuardDTO guard = phong != null ? phongService.buildRoomGuardFor(maPhong) : null;
        if (phong == null || guard == null || !guard.isCoTheDat()) {
            redirectAttributes.addFlashAttribute("bookingError", "Phòng không khả dụng, vui lòng chọn phòng khác.");
            return "redirect:/loai-phong/" + maLoaiPhong;
        }

        long soDem = ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate());
        if (soDem < 1) {
            redirectAttributes.addFlashAttribute("bookingError", "Ngày trả phòng phải sau ngày nhận phòng ít nhất 1 ngày.");
            return "redirect:/loai-phong/" + maLoaiPhong;
        }

        if (phong.getLoaiPhong() != null) {
            int sucChua = phong.getLoaiPhong().getSucChuaToiDa();
            int tongNguoi = (nguoiLon != null ? nguoiLon : 0) + (treEm != null ? treEm : 0);
            if (tongNguoi > sucChua) {
                redirectAttributes.addFlashAttribute("bookingError",
                        "Số lượng người (" + tongNguoi + ") vượt quá sức chứa của phòng (" + sucChua + " người).");
                return "redirect:/loai-phong/" + maLoaiPhong;
            }
        }

        String guardError = validateRoomBookingGuard(guard, ngayNhan, ngayTra);
        if (guardError != null) {
            redirectAttributes.addFlashAttribute("bookingError", guardError);
            return "redirect:/loai-phong/" + maLoaiPhong;
        }

        DatPhong dp = new DatPhong();
        dp.setNgaydatPhong(ngayNhan);
        dp.setNgaytraPhong(ngayTra);
        dp.setSonguoiLon(nguoiLon);
        dp.setSotreEm(treEm);

        dp.setYeuCauThem(appendGuardNote(yeuCauThem, guard, ngayNhan, ngayTra));
        if (ma_cccd != null && !ma_cccd.isBlank()) {
            dp.setMa_cccd(ma_cccd.trim());
        }
        dp.setNgayTao(LocalDateTime.now());
        boolean isLoggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (isLoggedIn && !isNhanVienOrAdmin(authentication)) {

            KhachHang nd = nguoiDungService.findByEmail(authentication.getName());
            if(nd !=null) {
                dp.setHoten(nd.getHoTen());
                dp.setEmail(nd.getEmail());
                dp.setSdt(nd.getSoDienThoai());

                dp.setN(nd);
            }
        }

        dp.setTrangThai("Chua thanh toan");
        DatPhong savedDp = datphongservice.save(dp);
        BigDecimal phuPhiNgoaiGio = calculateExtraFee(guard, ngayNhan, ngayTra);

        ChiTietDatPhong ctdp = new ChiTietDatPhong();
        ctdp.setD(savedDp);
        ctdp.setP(phong);
        ctdp.setGiaMoiDem(phong.getGiaMoiDem());
        ctdp.setGiaKhiDat(phong.getGiaMoiDem().multiply(BigDecimal.valueOf(soDem)).add(phuPhiNgoaiGio));
        ctdp.setPhuPhi(phuPhiNgoaiGio);
        chiTietDatPhongService.save(ctdp);

        // ===== Ghi nhớ vào COOKIE cho khách vãng lai (không bị mất khi restart server) =====
        if (savedDp.getN() == null) {
            bookingDraftService.remember(request, response, savedDp.getId());
        }

        return "redirect:/phong/dat-phong/xac-nhan/" + savedDp.getId();
    }

    private boolean isNhanVienOrAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority()));
    }
//
    private String validateRoomBookingGuard(RoomBookingGuardDTO guard,
                                            LocalDateTime ngayNhan,
                                            LocalDateTime ngayTra) {

        if (guard == null || !guard.isCoTheDat()) {
            return "Phong khong kha dung.";
        }

        // Duyệt TỪNG đơn đang giữ chỗ, không gộp min-max, tránh chặn nhầm
        // khoảng trống giữa 2 đơn không liên tục.
        for (su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO khoang : guard.getDanhSachKhoaLich()) {
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
//
    private String appendGuardNote(String yeuCauThem, RoomBookingGuardDTO guard, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        String result = yeuCauThem;
        if (guard == null) {
            return result;
        }

        BigDecimal phuPhi = calculateExtraFee(guard, ngayNhan, ngayTra);
        if (BigDecimal.ZERO.compareTo(phuPhi) == 0) {
            return result;
        }

        String note = "[PHU_PHI_NGOAI_GIO=" + phuPhi.toPlainString() + "]";
        if (result == null || result.isBlank()) {
            return note;
        }
        return result + " " + note;
    }
//
    private BigDecimal calculateExtraFee(RoomBookingGuardDTO guard, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        if (guard == null) {
            return BigDecimal.ZERO;
        }

        // ===== Chính sách mới: KHÔNG tính phụ phí cho check-in muộn hoặc check-out sớm.
        // Chỉ tính phụ phí khi check-in QUÁ SỚM (trước giờ nhận tối thiểu) hoặc
        // check-out QUÁ TRỄ (sau giờ trả tối đa). Hai trường hợp này ảnh hưởng
        // đến vận hành phòng (phòng chưa sẵn sàng / khách ở lại quá lâu).
        LocalTime gioNhan = ngayNhan.toLocalTime();
        LocalTime gioTra = ngayTra.toLocalTime();
        boolean nhanQuaSom = gioNhan.isBefore(guard.getGioNhanToiThieu());
        boolean traQuaTre = gioTra.isAfter(guard.getGioTraToiDa());
        return (nhanQuaSom || traQuaTre) ? guard.getPhuPhiNgoaiGioVND() : BigDecimal.ZERO;
    }
    private String buildLockedRangesJson(RoomBookingGuardDTO guard) {
        List<su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO> list =
                guard != null ? guard.getDanhSachKhoaLich() : java.util.Collections.emptyList();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO k = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                    .append("\"tu\":\"").append(k.getNgayBatDau() != null ? k.getNgayBatDau().toLocalDate() : "").append("\",")
                    .append("\"den\":\"").append(k.getNgayKetThuc() != null ? k.getNgayKetThuc().toLocalDate() : "").append("\",")
                    .append("\"trangThai\":\"").append(escapeJson(k.getTrangThaiDon())).append("\"")
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

//    /**
//     * Tính số đêm giữa 2 thời điểm: bỏ phần giờ, chỉ so sánh ngày (LocalDate).
//     * Tối thiểu 1 đêm nếu ngày trả > ngày nhận. Xử lý đúng khi qua năm mới
//     * (tránh lỗi âm của getDayOfYear()).
//     */
    private long soDem(LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        if (ngayNhan == null || ngayTra == null) return 1;
        long diff = ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate());
        return Math.max(1, diff);
    }
//
//
}
