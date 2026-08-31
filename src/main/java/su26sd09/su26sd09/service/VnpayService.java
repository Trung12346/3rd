package su26sd09.su26sd09.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.config.vnpayConfig;
import su26sd09.su26sd09.dto.InvoicePricingResult;
import su26sd09.su26sd09.dto.RefundDraft;
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

    @Autowired
    BookingEmailService bookingEmailService;

    @Autowired
    HuyDonService huyDonService;

    @Autowired
    InvoicePricingService invoicePricingService;

    /** Phân biệt các luồng callback VNPay. */
    public static final String ORDER_INFO_THU_THEM_DICH_VU = "ThuThemDichVu";
    public static final String ORDER_INFO_DAT_PHONG        = "DatPhong";
    public static final String ORDER_INFO_HOAN_TIEN        = "HoanTienChoKhach";

    public String createOrder(long total,int maDatPhong, String orderInfor, String urlReturn){
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

    /**
     * Tạo URL VNPay cho luồng hoàn tiền (giả lập pay-in: từ tài khoản merchant
     * tới 1 tài khoản đối tác trung gian, dùng làm bằng chứng giao dịch).
     *
     * @param maHoaDon mã hóa đơn cần hoàn
     * @param soTien   số tiền hoàn (VND, chưa nhân 100)
     * @param urlReturn base URL (scheme://host:port)
     */
    public String createRefundOrder(int maHoaDon, long soTien, String urlReturn) {
        System.out.println("Truy cap createRefundOrder, maHoaDon=" + maHoaDon + ", soTien=" + soTien);

        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = "REFUND_" + maHoaDon + "_" + vnpayConfig.getRandomNumber(8);
        String vnp_IpAddr = "127.0.0.1";
        String vnp_TmnCode = vnpayConfig.vnp_TmnCode;
        String orderType = "refund";

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(soTien * 100));
        vnp_Params.put("vnp_CurrCode", "VND");

        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", ORDER_INFO_HOAN_TIEN);
        vnp_Params.put("vnp_OrderType", orderType);

        vnp_Params.put("vnp_Locale", "vn");

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
                hashData.append(fieldName);
                hashData.append('=');
                try {
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
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
        String vnp_SecureHash = vnpayConfig.hmacSHA512(vnpayConfig.vnp_HashSecret, hashData.toString());
        String paymentUrl = vnpayConfig.vnp_PayUrl + "?" + query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;
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

        Long amountParse = Long.parseLong(amount) / 100;
        BigDecimal amountVnpay = BigDecimal.valueOf(amountParse);

        boolean vnpayBaoThanhCong = "00".equals(vnp_ResponseCode);
        boolean laThuThemDichVu = "ThuThemDichVu".equals(vnp_OrderInfo);
        boolean laHoanTienChoKhach = ORDER_INFO_HOAN_TIEN.equals(vnp_OrderInfo);

        // ===== Nhánh HOÀN TIỀN CHO KHÁCH =====
        if (laHoanTienChoKhach) {
            if (!vnpayBaoThanhCong) {
                System.out.println("Hoan tien that bai (VNPay), txnRef=" + vnp_TxnRef);
                return 0;
            }
            // TxnRef dạng: REFUND_{maHoaDon}_{rand}
            String[] parts = vnp_TxnRef.split("_");
            if (parts.length < 2) {
                System.out.println("TxnRef khong dung dinh dang hoan tien: " + vnp_TxnRef);
                return 0;
            }
            int maHoaDon = Integer.parseInt(parts[1]);
            HoaDon hd = hoaDonService.findById(maHoaDon);
            if (hd == null) {
                System.out.println("Khong tim thay HoaDon de hoan tien: " + maHoaDon);
                return 0;
            }

            LocalDateTime thoiGianThanhToan;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                Date date = sdf.parse(vnp_PayDate);
                thoiGianThanhToan = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception e) {
                thoiGianThanhToan = LocalDateTime.now();
            }

            // Lấy thông tin nháp từ session do controller lưu trước khi redirect sang VNPay
            RefundDraft draft = null;
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session != null) {
                Object o = session.getAttribute("refundDraft_" + maHoaDon);
                if (o instanceof RefundDraft) {
                    draft = (RefundDraft) o;
                }
                // Dùng thành công hay thất bại thì cũng xoá để tránh dùng lại
                session.removeAttribute("refundDraft_" + maHoaDon);
            }

            NhanSu nvXuLy = null;
            if (draft != null && draft.getEmailNhanVienXuLy() != null) {
                nvXuLy = nhanVienService.FindByemail(draft.getEmailNhanVienXuLy());
            }
            if (nvXuLy == null) {
                // fallback: lấy nhân viên đang đăng nhập hoặc 1 lễ tân
                authentication = SecurityContextHolder.getContext().getAuthentication();
                String email = null;
                if (authentication != null && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken)) {
                    email = authentication.getName();
                }
                if (email != null) {
                    nvXuLy = nhanVienService.FindByemail(email);
                }
                if (nvXuLy == null) {
                    nvXuLy = nhanVienService.findAll().stream()
                            .filter(nv -> "lễ tân".equalsIgnoreCase(nv.getBoPhan()))
                            .findFirst().orElse(null);
                }
            }

            huyDonService.xacNhanHoanTienVnpay(maHoaDon, vnp_TransactionNo,
                    draft == null ? null : draft.getStkNhanHoan(),
                    draft == null ? null : draft.getTenNganHang(),
                    draft == null ? null : draft.getGhiChu(),
                    thoiGianThanhToan, nvXuLy);
            System.out.println("Hoan tien VNPay thanh cong: maHD=" + maHoaDon + ", maGD=" + vnp_TransactionNo);
            return 1;
        }

        int maDatPhong = Integer.parseInt(vnp_TxnRef.split("_")[0]);
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
            hoaDonService.saveWithPaymentStatusCheck(hd);

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

        HoaDon _hd = hoaDonService.findByDatPhongId(dp.id);
        System.out.println("ID DP: " + dp.id);

        BigDecimal daThanhToan = _hd != null ? _hd.daThanhToan : BigDecimal.ZERO;
        System.out.println("DA THANH TOAN: " + daThanhToan);

        // VIEW: xac minh so tien da thanh toan qua VNPay, cung cong thuc voi
        // UPDATE_EXISTING (xem InvoicePricingService).
        InvoicePricingResult gia = invoicePricingService.previewInvoice(maDatPhong, dp.getKm());
        BigDecimal amountPhong = gia.getTienPhong();
        BigDecimal amountDv = gia.getTienDichVu();
        BigDecimal tienGiam = gia.getTienGiam();
        BigDecimal tienVat = gia.getTienVat();
        BigDecimal amountTongTien = gia.getTongTien();

        BigDecimal tongDaThanhToan = amountVnpay.add(daThanhToan);
        System.out.println("DA THANH TOAN: " + tongDaThanhToan);
        boolean thanhCong = tongDaThanhToan.compareTo(amountTongTien) == 0 && vnpayBaoThanhCong;
        if (!thanhCong) {
            System.out.println("thanh toan that bai vnpay=" + amountVnpay + " amountTongTien=" + amountTongTien);
            return 0;
        }

        // KHONG doi trangThai DatPhong o day — don dang o trang thai "Yeu cau dat phong"
        // (khach online dat qua he thong), nhan vien se xac nhan + xep phong trong
        // trang /nhan-su/yeu-cau-dat-phong. Viec gan NV + set trang thai chi do NV thuc hien.
        // Cap nhat ngayCapNhat de audit lich su giao dich.
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        // KHONG cap nhat trang thai Phong thanh "Dang su dung" o day — cho den khi NV
        // xac nhan yeu cau va kiem tra phong, phong moi chinh thuc duoc danh dau.
        // Phong chi chuyen sang "Da dat truoc" qua createAutoAssignedBooking() luc tao don.

        // FIX: hoa_don.ma_dat_phong la UNIQUE (1 don chi co 1 hoa don). _hd o tren
        // da kiem tra xem hoa don cho don nay da ton tai chua (vd duoc tao truoc
        // do o buoc "Xac nhan thong tin khach" hoac 1 lan goi callback truoc), nen
        // o day PHAI cap nhat lai _hd neu no da ton tai thay vi luon insert HoaDon
        // moi - insert moi trong khi da co san se vi pham UNIQUE KEY constraint
        // (loi 500 "Cannot insert duplicate key... object 'dbo.hoa_don'").
        HoaDon hd = _hd != null ? _hd : new HoaDon();
        hd.setNgayXuat(hd.getNgayXuat() != null ? hd.getNgayXuat() : LocalDateTime.now());
        hd.setD(dp);
        hd.setK(dp.getKm());
        hd.setTienDichVu(amountDv);
        hd.setTienPhong(amountPhong);
        hd.setN(n);
        hd.setTongTien(amountTongTien);
        hd.setTienGiam(tienGiam);
        hd.setTienVat(tienVat);
        // Cong don voi so da thu truoc do (vd da tra coc tien mat truoc, gio tra
        // not phan con lai qua VNPay) thay vi ghi de chi bang so tien cua giao
        // dich VNPay lan nay - tranh lam "mat" tien da thu truoc do trong so sach.
        hd.setDaThanhToan(tongDaThanhToan);
        hd.setGhiChu("So Phong Dat: " + chiTietDatPhong.size() + " Ma Dat Phong: " + maDatPhong);
        hd.setNgayCapNhat(_hd != null ? LocalDateTime.now() : null);
        hoaDonService.saveWithPaymentStatusCheck(hd);

        ThanhToan thanhToan = new ThanhToan();
        thanhToan.setPhuongThuc("Chuyen Khoan");
        thanhToan.setH(hd);
        // FIX: ghi dung so tien CUA GIAO DICH VNPAY NAY (amountVnpay), khong phai
        // amountTongTien (tong ca don) - neu truoc do da co thanh toan mot phan
        // (vd tien mat), dung amountTongTien o day se lam tong cac dong ThanhToan
        // bi cong trung/vuot qua tong tien thuc su da thu.
        thanhToan.setSoTien(amountVnpay);
        thanhToan.setTrangThai("Thanh cong");
        thanhToan.setMagiaodich(vnp_TransactionNo);
        thanhToan.setNv(nvGan);
        thanhToan.setNgaythanhToan(thoiGianThanhToan);
        thanhToan.setGichu("Thanh Toan Don Dat Phong: " + maDatPhong);
        thanhToanService.save(thanhToan);

        // Gui email xac nhan cho khach (async)
        try {
            bookingEmailService.guiEmailThanhToanThanhCong(maDatPhong);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        System.out.println("Thanh toan thanh cong (dat phong lan dau): " + amountVnpay + " Ma GD: " + vnp_TransactionNo);
        return 1;
    }
}

