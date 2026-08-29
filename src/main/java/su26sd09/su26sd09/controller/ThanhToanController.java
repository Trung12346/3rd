package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import su26sd09.su26sd09.dto.VNPayParserDTO;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/thanh-toan")
public class ThanhToanController {

    public static Map<Integer, String> VNPayRequests = new HashMap<>();

    @Autowired
    VnpayService vnpayService;



    @Autowired
    ChiTietDichVuService ctdvService;

    @Autowired
    HoaDonService hoaDonService;

    @Autowired
    ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    ThanhToanService thanhToanService;

    @Autowired
    DatPhongService datPhongService;

    @Autowired
    BookingEmailService bookingEmailService;

    @GetMapping("/dat-phong/{id}")
    public String submitTransaction(@PathVariable Integer id,Model model,
                                    RedirectAttributes redirectAttributes){
        DatPhong dp = datPhongService.findById(id);
        if(dp == null){
            return "redirect:/home";
        }
        if(dp.getTrangThai().equals("Da xac nhan") || dp.getTrangThai().equalsIgnoreCase("Da thanh toan")){
            return "redirect:/home";
        }

        // ===== Backup thông minh cho khách vãng lai =====
        // Kiểm tra xem khách đã điền đủ thông tin chưa. Nếu thiếu -> redirect
        // về trang điền thông tin (KHÔNG hiện trang chọn PTTT VNPay).
        // Dịch vụ bổ sung là optional, nên KHÔNG ép khách quay lại chọn DV.
        if (dp.getN() == null) {
            boolean thieuThongTin = dp.getHoten() == null || dp.getHoten().isBlank()
                    || dp.getEmail() == null || dp.getEmail().isBlank()
                    || dp.getSdt() == null || dp.getSdt().isBlank();
            if (thieuThongTin) {
                redirectAttributes.addFlashAttribute("thongBao",
                        "Vui lòng hoàn tất thông tin khách trước khi thanh toán.");
                return "redirect:/phong/dat-phong/thong-tin-khach/" + id;
            }
        }

        BigDecimal Totalamount = BigDecimal.ZERO;
        BigDecimal amountDv = BigDecimal.ZERO;
        BigDecimal amountPhong = BigDecimal.ZERO;
        BigDecimal ThueVat = new BigDecimal("0.10");

        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);

        for(ChiTietDatPhong ctdp : chiTietDatPhongs){
            Totalamount = Totalamount.add(ctdp.getGiaKhiDat());
            amountPhong = amountPhong.add(ctdp.getGiaKhiDat());
        }

        for(Chi_tiet_dich_vu ctdv: chiTietDichVus){
            amountDv = amountDv.add(ctdv.getDonGia());
        }

        // KM: ap dung tren TONG (phong + dich vu), VAT 10% tinh tren gia SAU GIAM
        BigDecimal tienGiam = tinhTienGiam(amountPhong.add(amountDv), dp.getKm());
        BigDecimal tongSauGiam = amountPhong.add(amountDv).subtract(tienGiam);
        BigDecimal tienVat = tongSauGiam.multiply(ThueVat).setScale(2,RoundingMode.HALF_UP);
        Totalamount = tongSauGiam.add(tienVat);

        long nightCount = java.time.temporal.ChronoUnit.DAYS.between(
                dp.getNgaydatPhong().toLocalDate(), dp.getNgaytraPhong().toLocalDate());

        model.addAttribute("datPhong",dp);
        model.addAttribute("TongTien",amountPhong);
        model.addAttribute("TienDv",amountDv);
        model.addAttribute("TienGiam",tienGiam);
        model.addAttribute("TienVat",tienVat);
        model.addAttribute("TongCong",Totalamount);
        // Chi tiet phong va dich vu de khach kiem duyet lai truoc khi thanh toan
        model.addAttribute("chiTietDatPhongList", chiTietDatPhongs);
        model.addAttribute("chiTietDichVuList", chiTietDichVus);
        model.addAttribute("nightCount", Math.max(1, nightCount));
        return "Thanh-Toan";
    }

    @PostMapping("/vnpay/{id}")
    public String submitVnpay(@PathVariable Integer id, HttpServletRequest request) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal amountDv = BigDecimal.ZERO;

        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
            amount = amount.add(ctdp.getGiaKhiDat());
        }

        List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);
        for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
            amountDv = amountDv.add(ctdv.getDonGia());
        }

        DatPhong dp = datPhongService.findById(id);
        BigDecimal VATCD = new BigDecimal("0.10");
        // KM: ap dung tren TONG (phong + dich vu), VAT 10% tinh tren gia SAU GIAM
        BigDecimal tienGiam = tinhTienGiam(amount.add(amountDv), dp != null ? dp.getKm() : null);
        BigDecimal tongSauGiam = amount.add(amountDv).subtract(tienGiam);
        BigDecimal tienVat = tongSauGiam.multiply(VATCD).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tongTien = tongSauGiam.add(tienVat);

        //them cong nang
        HoaDon hd = hoaDonService.findByDatPhongId(id);
        if(hd != null && hd.getTrangThai().equals("Cho thanh toan"))
        {
            tongTien = hd.getTongTien().subtract(hd.getDaThanhToan());
        }

        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String vnpayUrl = vnpayService.createOrder(tongTien.longValue(), id, "ChuyenKhoan", baseUrl);

        VNPayRequests.put(id, vnpayUrl);

        return "redirect:" + vnpayUrl;
    }

    @GetMapping("/pool")
    public String thanhToanPool(
            @RequestParam("dat-phong-id") Integer id,
            Model model,
            HttpServletRequest request
    )
    {
        HoaDon hd = hoaDonService.findByDatPhongId(id);
        if(
                hd == null ||
                        // Yoda-style + "Cho thanh toan".equals(...) thay vi hd.getTrangThai().equals(...):
                        // tranh NullPointerException neu mot luong tao HoaDon nao khac quen gan
                        // trangThai (vd: bug da fix o submitTienMat phia tren).
                        (hd.getTongTien().subtract(hd.getDaThanhToan()).compareTo(BigDecimal.ZERO) > 0 && "Cho thanh toan".equals(hd.getTrangThai()))
        )
        {
            String url = VNPayRequests.get(id);
            if(url != null)
            {
                String params = url.substring(url.indexOf("?"));
//                String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
//                RestClient client = RestClient.create();
//                VNPayParserDTO result = client.get()
//                        .uri(baseUrl + "/thanh-toan/vnpay-parser" + params)
//                        .retrieve()
//                        .body(VNPayParserDTO.class);
                UriComponents uri = UriComponentsBuilder
                        .fromUriString(url)
                        .build();

                String timeString = uri.getQueryParams().getFirst("vnp_ExpireDate");

                LocalDateTime expireDate = LocalDateTime.of(
                        Integer.parseInt(timeString.substring(0, 4)),
                        Integer.parseInt(timeString.substring(4, 6)),
                        Integer.parseInt(timeString.substring(6, 8)),
                        Integer.parseInt(timeString.substring(8, 10)),
                        Integer.parseInt(timeString.substring(10, 12)),
                        Integer.parseInt(timeString.substring(12, 14))
                );
                if(LocalDateTime.now().isBefore(expireDate))
                {
                    System.out.println("111111111111111111");
                    return "redirect:" + url;
                }
            }
            model.addAttribute("id", id);
            System.out.println("222222222222222222222");
            return "vnpay-forward";

        }
        System.out.println("33333333333333333333");
        return "inform-thanh-toan-du";
    }

    @GetMapping("/vnpay-parser")
    public VNPayParserDTO VNPayParse(@RequestParam("vnp_ExpireDate") String vnpExpireDate)
    {
        return new VNPayParserDTO(
                vnpExpireDate
        );
    }

    @PostMapping("/dat-phong/{id}")
    public String submitTienMat(@PathVariable Integer id,
                                @RequestParam String phuongThucThanhToan,
                                RedirectAttributes redirectAttributes) {

        DatPhong dp = datPhongService.findById(id);
        if (dp == null) {
            redirectAttributes.addFlashAttribute("error", "Khong tim thay don dat phong");
            return "redirect:/home";
        }

        BigDecimal amountPhong = BigDecimal.ZERO;
        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
        for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
            amountPhong = amountPhong.add(ctdp.getGiaKhiDat());
        }

        BigDecimal amountDv = BigDecimal.ZERO;
        List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);
        for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
            BigDecimal donGia = ctdv.getDonGia() == null ? BigDecimal.ZERO : ctdv.getDonGia();
            // Phu thu (check-in som / check-out muon) gio duoc gop chung vao tienDichVu,
            // chiu KM + VAT nhu dich vu thuong/phat sinh - KHONG con tach rieng ngoai VAT nua.
            amountDv = amountDv.add(donGia);
        }

        BigDecimal VATCD = new BigDecimal("0.10");
        // KM: ap dung tren TONG (phong + dich vu, bao gom ca phu thu), VAT 10% tinh tren gia SAU GIAM.
        BigDecimal tienGiam = tinhTienGiam(amountPhong.add(amountDv), dp.getKm());
        BigDecimal tongSauGiam = amountPhong.add(amountDv).subtract(tienGiam);
        BigDecimal tienVat = tongSauGiam.multiply(VATCD).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountTongTien = tongSauGiam.add(tienVat);

        //-------------------------------------------------------deprecated--
        // KHONG doi trangThai DatPhong o day — don nay la "Yeu cau dat phong",
        // nhan vien se xac nhan + xep phong trong trang /nhan-su/yeu-cau-dat-phong.
        // Trang thai chi duoc phep thay doi boi NV qua nut "Xac nhan yeu cau".
        // Luu ngayCapNhat de audit.
        //-------------------------------------------------------------------
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(dp);
        hd.setK(dp.getKm());
        hd.setTienPhong(amountPhong);
        hd.setTienDichVu(amountDv);
        hd.setTienGiam(tienGiam);
        hd.setTienVat(tienVat);
        hd.setTongTien(amountTongTien);
        hd.setDaThanhToan(BigDecimal.ZERO);
        hd.setGhiChu("Thanh toan tien mat tai quay, ma don: " + id);
        // BUG CU: goi hoaDonService.save(hd) truc tiep khong gan trangThai, khien
        // hd.getTrangThai() == null. /thanh-toan/pool sau do goi
        // hd.getTrangThai().equals("Cho thanh toan") tren hoa don nay se nem NPE.
        // Dung saveWithPaymentStatusCheck (dong bo voi moi noi khac tao HoaDon)
        // de tu dong gan trangThai = "Cho thanh toan" / "Da thanh toan".
        hoaDonService.saveWithPaymentStatusCheck(hd);





        ThanhToan tt = new ThanhToan();
        tt.setH(hd);
        tt.setPhuongThuc("Tien Mat");
        tt.setSoTien(amountTongTien);
        tt.setTrangThai("Cho thanh toan");
        tt.setNgaythanhToan(LocalDateTime.now());
        tt.setGichu("Chua thu tien, khach se thanh toan khi nhan phong");
        thanhToanService.save(tt);

        // Gui email xac nhan cho khach (async) — bao gom hoa don + thong tin thanh toan
        try {
            bookingEmailService.guiEmailThanhToanThanhCong(id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        redirectAttributes.addFlashAttribute("success", "Da xac nhan dat phong. Vui long thanh toan tien mat khi den nhan phong.");
        return "redirect:/thanh-toan/thanh-cong/" + id;
    }


    @GetMapping("/thanh-cong/{id}")
    public String thanhToanThanhCong(@PathVariable Integer id, Model model) {
        DatPhong dp = datPhongService.findById(id);

        BigDecimal Totalamount = BigDecimal.ZERO;
        BigDecimal amountDv = BigDecimal.ZERO;
        BigDecimal amountPhong = BigDecimal.ZERO;
        BigDecimal ThueVat = new BigDecimal("0.10");

        List<ChiTietDatPhong> chiTietDatPhongs = chiTietDatPhongService.findByDatPhongId(id);
        List<Chi_tiet_dich_vu> chiTietDichVus = ctdvService.findByDatPhongId(id);

        for (ChiTietDatPhong ctdp : chiTietDatPhongs) {
            amountPhong = amountPhong.add(ctdp.getGiaKhiDat());
        }
        for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
            amountDv = amountDv.add(ctdv.getDonGia());
        }

        BigDecimal tienGiam = tinhTienGiam(amountPhong, dp.getKm());
        Totalamount = amountPhong.subtract(tienGiam).add(amountDv);
        BigDecimal tienVat = Totalamount.multiply(ThueVat).setScale(2, RoundingMode.HALF_UP);
        Totalamount = Totalamount.add(tienVat);
        HoaDon hd = hoaDonService.findByDatPhongId(id);
        model.addAttribute("transactionId",thanhToanService.findByHoaDonId(hd.getId()).getMagiaodich());
        model.addAttribute("datPhong", dp);
        model.addAttribute("TongTien", amountPhong);
        model.addAttribute("TienVat",tienVat);
        model.addAttribute("TienDv", amountDv);
        model.addAttribute("TienGiam", tienGiam);
        model.addAttribute("TongCong", Totalamount);



        return "thanh-toan-thanh-cong";
    }

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


}