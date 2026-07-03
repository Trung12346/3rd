package su26sd09.su26sd09.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.config.vnpayConfig;
import su26sd09.su26sd09.entity.*;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

@Service
public class VnpayService {

    @Autowired
    ChiTietDatPhongService CtdatPhongService;

    @Autowired
    DatPhongService datPhongService;

    @Autowired
    PhongService phongService;

    @Autowired
    ChiTietDichVuService chiTietDichVuService;

    @Autowired
    NguoiDungService nguoiDungService;

    @Autowired
    HoaDonService hoaDonService;
    @Autowired
    ThanhToanService thanhToanService;

    @Autowired
    NhanVienService nhanVienService;

    public String createOrder(int total,int maDatPhong, String orderInfor, String urlReturn){
        System.out.println("Truy cap createOrder");
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = maDatPhong + "_" + vnpayConfig.getRandomNumber(8);
        String vnp_IpAddr = "127.0.0.1";
        String vnp_TmnCode = vnpayConfig.vnp_TmnCode;
        String orderType = "order-type";

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(total*100));
        vnp_Params.put("vnp_CurrCode", "VND");

        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfor);
        vnp_Params.put("vnp_OrderType", orderType);

        String locate = "vn";
        vnp_Params.put("vnp_Locale", locate);

        urlReturn += vnpayConfig.vnp_Returnurl;
        vnp_Params.put("vnp_ReturnUrl", urlReturn);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List fieldNames = new ArrayList(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = (String) vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                //Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                try {
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    //Build query
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = vnpayConfig.hmacSHA512(vnpayConfig.vnp_HashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = vnpayConfig.vnp_PayUrl + "?" + queryUrl;
        return paymentUrl;
    }

    public int orderReturn(HttpServletRequest request, Authentication authentication) {
        System.out.println("Truy cap Order Return");

         Map<String, String> fields = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String value = request.getParameter(name);
            if (name.startsWith("vnp_") && value != null && value.length() > 0) {
                fields.put(name, value);
            }
        }
        String vnp_SecureHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String checkHash = vnpayConfig.hashAllFields(fields);
        if (vnp_SecureHash == null || !checkHash.equalsIgnoreCase(vnp_SecureHash)) {
            System.out.println("Chu ky khong hop le, nghi ngo gia mao. checkHash=" + checkHash + " vnp_SecureHash=" + vnp_SecureHash);
            return 0;
        }

         String vnp_ResponseCode  = request.getParameter("vnp_ResponseCode");
        String vnp_TxnRef        = request.getParameter("vnp_TxnRef");
        String amount             = request.getParameter("vnp_Amount");
        String vnp_TransactionNo = request.getParameter("vnp_TransactionNo");
        String vnp_PayDate       = request.getParameter("vnp_PayDate");
        String vnp_OrderInfo     = request.getParameter("vnp_OrderInfo");

        int maDatPhong = Integer.parseInt(vnp_TxnRef.split("_")[0]);
        Long amountParse = Long.parseLong(amount) / 100;
        BigDecimal amountVnpay = BigDecimal.valueOf(amountParse);

        boolean vnpayBaoThanhCong = "00".equals(vnp_ResponseCode);
        boolean laThuThemDichVu = "ThuThemDichVu".equals(vnp_OrderInfo);

        DatPhong dp = datPhongService.findById(maDatPhong);
        if (dp == null) {
            System.out.println("Khong tim thay DatPhong: " + maDatPhong);
            return 0;
        }

         authentication = SecurityContextHolder.getContext().getAuthentication();
        List<NhanSu> listNhanVien = nhanVienService.findAll();
        Stream<NhanSu> ListNvLeTan = listNhanVien.stream().filter(nv -> nv.getBoPhan().equalsIgnoreCase("lễ tân"));
        String email = null;
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            email = authentication.getName();
        } else {
            for (NhanSu nv : ListNvLeTan.toList()) {
                email = nv.getEmail();
            }
        }

        NhanSu n = nhanVienService.FindByemail(email);
        NhanSu nvGan = null;
        if (n != null && n.getVaitro() != null && "ROLE_STAFF".equals(n.getVaitro().getTenVaiTro())) {
            // Người thao tác là nhân viên -> gán nhân viên đó
            nvGan = n;
        } else {
            // Không phải nhân viên (khách vãng lai) -> lấy 1 nhân viên lễ tân làm mặc định
            nvGan = nhanVienService.findAll().stream()
                    .filter(nv -> "lễ tân".equalsIgnoreCase(nv.getBoPhan()))
                    .findFirst()
                    .orElse(null);
        }

        LocalDateTime thoiGianThanhToan;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Date date = sdf.parse(vnp_PayDate);
            thoiGianThanhToan = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            thoiGianThanhToan = LocalDateTime.now();
        }

         if (laThuThemDichVu) {
            if (!vnpayBaoThanhCong) {
                System.out.println("Thu them dich vu that bai, maDatPhong=" + maDatPhong);
                return 0;
            }

            HoaDon hd = hoaDonService.findByDatPhongId(maDatPhong);
            if (hd == null) {
                System.out.println("Khong tim thay HoaDon de cong don, maDatPhong=" + maDatPhong);
                return 0;
            }

            BigDecimal daThanhToan = hd.getDaThanhToan() == null ? BigDecimal.ZERO : hd.getDaThanhToan();
            BigDecimal conNo = hd.getTongTien().subtract(daThanhToan);

             if (amountVnpay.compareTo(conNo) > 0) {
                System.out.println("Canh bao: so tien VNPay (" + amountVnpay + ") vuot qua con no (" + conNo + "), co the la callback trung.");
                return 0;
            }

            hd.setDaThanhToan(daThanhToan.add(amountVnpay));
            hd.setNgayCapNhat(LocalDateTime.now());
            hoaDonService.save(hd);

            ThanhToan thanhToan = new ThanhToan();
            thanhToan.setH(hd);
            thanhToan.setPhuongThuc("Chuyen Khoan");
            thanhToan.setSoTien(amountVnpay);
            thanhToan.setTrangThai("Thanh cong");
            thanhToan.setMagiaodich(vnp_TransactionNo);
            thanhToan.setNv(nvGan);
            thanhToan.setNgaythanhToan(thoiGianThanhToan);
            thanhToan.setGichu("Thu chuyen khoan dich vu phat sinh, ma don: " + maDatPhong);
            thanhToanService.save(thanhToan);

            System.out.println("Thu them dich vu thanh cong: " + amountVnpay + " Ma GD: " + vnp_TransactionNo);
            return 1;
        }

        List<ChiTietDatPhong> chiTietDatPhong = CtdatPhongService.findByDatPhongId(maDatPhong);
        List<Chi_tiet_dich_vu> chiTietDichVus = chiTietDichVuService.findByDatPhongId(maDatPhong);

        BigDecimal amountPhong = BigDecimal.ZERO;
        for (ChiTietDatPhong ctdp : chiTietDatPhong) {
            amountPhong = amountPhong.add(ctdp.getGiaKhiDat());
        }
        BigDecimal amountDv = BigDecimal.ZERO;
        for (Chi_tiet_dich_vu ctdv : chiTietDichVus) {
            amountDv = amountDv.add(ctdv.getDonGia());
        }

        BigDecimal VATCD = new BigDecimal("0.10");
        BigDecimal tienGiam = tinhTienGiam(amountPhong, dp.getKm());
        BigDecimal amountTongTien = amountPhong.subtract(tienGiam).add(amountDv);
        BigDecimal tienVat = amountTongTien.multiply(VATCD).setScale(2, RoundingMode.HALF_UP);
        amountTongTien = amountTongTien.add(tienVat);

        boolean thanhCong = amountVnpay.compareTo(amountTongTien) == 0 && vnpayBaoThanhCong;
        if (!thanhCong) {
            System.out.println("thanh toan that bai vnpay=" + amountVnpay + " amountTongTien=" + amountTongTien);
            return 0;
        }

        dp.setTrangThai("Cho xac nhan");
        dp.setNv(nvGan);
        datPhongService.save(dp);

        for (ChiTietDatPhong ctdp : chiTietDatPhong) {
            Phong p = ctdp.getP();
            p.setTrangThai("Dang su dung");
            phongService.save1(p);
        }

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(dp);
        hd.setK(dp.getKm());
        hd.setTienDichVu(amountDv);
        hd.setTienPhong(amountPhong);
        hd.setN(n);
        hd.setTongTien(amountTongTien);
        hd.setTienGiam(tienGiam);
        hd.setTienVat(tienVat);
        hd.setDaThanhToan(amountVnpay);
        hd.setGhiChu("So Phong Dat: " + chiTietDatPhong.size() + " Ma Dat Phong: " + maDatPhong);
        hd.setNgayCapNhat(null);
        hoaDonService.save(hd);

        ThanhToan thanhToan = new ThanhToan();
        thanhToan.setPhuongThuc("Chuyen Khoan");
        thanhToan.setH(hd);
        thanhToan.setSoTien(amountTongTien);
        thanhToan.setTrangThai("Thanh cong");
        thanhToan.setMagiaodich(vnp_TransactionNo);
        thanhToan.setNv(nvGan);
        thanhToan.setNgaythanhToan(thoiGianThanhToan);
        thanhToan.setGichu("Thanh Toan Don Dat Phong: " + maDatPhong);
        thanhToanService.save(thanhToan);

        System.out.println("Thanh toan thanh cong (dat phong lan dau): " + amountVnpay + " Ma GD: " + vnp_TransactionNo);
        return 1;
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

