package su26sd09.su26sd09.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
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
import java.security.Principal;
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
        String vnp_ResponseCode  = request.getParameter("vnp_ResponseCode");
        String vnp_TxnRef        = request.getParameter("vnp_TxnRef");
        String amount            = request.getParameter("vnp_Amount");
        String vnp_TransactionNo = request.getParameter("vnp_TransactionNo");
        String vnp_PayDate       = request.getParameter("vnp_PayDate");
        String vnp_OrderInfo     = request.getParameter("vnp_OrderInfo");

        int maDatPhong = Integer.parseInt(vnp_TxnRef.split("_")[0]);

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
        BigDecimal amountTongTien = BigDecimal.ZERO.add(amountPhong).add(amountDv);
        BigDecimal TienVat = amountTongTien.multiply(VATCD).setScale(2, RoundingMode.HALF_UP);
        amountTongTien = amountTongTien.add(TienVat);

        System.out.println("Amount String: " + amount);
        Long amountParse = Long.parseLong(amount) / 100;
        BigDecimal amountVnpay = BigDecimal.valueOf(amountParse);

        boolean thanhCong = amountVnpay.compareTo(amountTongTien) == 0 && "00".equals(vnp_ResponseCode);

        if (!thanhCong) {
            System.out.println("thanh toan that bai vnpay=" + amountVnpay
                    + " amountTongTien=" + amountTongTien
                    + " TienVat=" + TienVat
                    + " amountDv=" + amountDv);
            return 0;
        }

        DatPhong dp = datPhongService.findById(maDatPhong);
        dp.setTrangThai("Cho xac nhan");

        authentication = SecurityContextHolder.getContext().getAuthentication();
        List<Nhanvien> listNhanVien = nhanVienService.findAll();
        Stream<Nhanvien> ListNvLeTan = listNhanVien.stream().filter(nv -> nv.getBoPhan().equalsIgnoreCase("lễ tân"));
        String email = null;
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            email = authentication.getName();
        } else {
            for(Nhanvien nv: ListNvLeTan.toList()) {
                email = nv.getN().getEmail();
            }
        }

        NguoiDung n = nguoiDungService.findByEmail(email);
        if (n == null) {
            System.out.println("Khong tim thay NguoiDung voi email: " + email);
            return 0;
        }

        boolean isNvDp = n.getVaiTro() != null && "ROLE_STAFF".equals(n.getVaiTro().getTenVaiTro());

        Nhanvien nvGan = null;
        if (isNvDp) {
            nvGan = nhanVienService.findByMaNguoiDung(n.getMaNguoiDung());
        }else{
            for (Nhanvien nv : ListNvLeTan.toList()) {
                nvGan = nv;
            }
        }
        if (nvGan == null) {

            NguoiDung staffDefault = nguoiDungService.findByEmail(email);
            if (staffDefault != null) {
                nvGan = nhanVienService.findByMaNguoiDung(staffDefault.getMaNguoiDung());
            }
        }
        if (nvGan == null) {
            System.out.println("Khong tim thay Nhanvien fallback nv bo phan le tan");
        }

        dp.setNv(nvGan);
        datPhongService.save(dp);

        LocalDateTime thoiGianThanhToan;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Date date = sdf.parse(vnp_PayDate);
            thoiGianThanhToan = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            thoiGianThanhToan = LocalDateTime.now();
        }

        for (ChiTietDatPhong ctdp : chiTietDatPhong) {
            Phong p = ctdp.getP();
            p.setTrangThai("Dang su dung");
            phongService.save1(p);
        }

        System.out.println("Principal from Vnpay: " + email);

        HoaDon hd = new HoaDon();
        hd.setNgayXuat(LocalDateTime.now());
        hd.setD(dp);
        hd.setTienDichVu(amountDv);
        hd.setTienPhong(amountPhong);
        hd.setN(n);
        hd.setTongTien(amountTongTien);
        hd.setTienGiam(BigDecimal.ZERO);
        hd.setTienVat(TienVat);
        hd.setDaThanhToan(amountVnpay);
        hd.setGhiChu("So Phong Dat: " + chiTietDatPhong.size() + " Ma Dat Phong: " + maDatPhong);
        System.out.println("Amount dich vu: " + amountDv);
        hd.setNgayCapNhat(null);
        hoaDonService.save(hd);

        System.out.println("Ma Nguoi Dung trong Vnpay: " + n.getMaNguoiDung());

        ThanhToan thanhToan = new ThanhToan();
        thanhToan.setPhuongThuc("Chuyen Khoan");
        thanhToan.setH(hd);
        thanhToan.setSoTien(amountTongTien);
        thanhToan.setTrangThai("Thanh cong");
        thanhToan.setMagiaodich(vnp_TransactionNo);
        thanhToan.setNv(nvGan);
        thanhToan.setNgaythanhToan(LocalDateTime.now());
        thanhToan.setGichu("Thanh Toan Don Dat Phong: " + maDatPhong);
        thanhToanService.save(thanhToan);

        System.out.println("Ma nhan vien duoc gan: " + (thanhToan.getNv() != null ? thanhToan.getNv().getId() : "null"));
        System.out.println("Hoa Don: " + hd.getId());
        System.out.println("Thanh toan thanh cong: " + amountVnpay
                + "  Ma GD VNPay: " + vnp_TransactionNo
                + "  Thoi gian: " + thoiGianThanhToan);

        return 1;
    }
}

