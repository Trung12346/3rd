package su26sd09.su26sd09.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import su26sd09.su26sd09.dto.InvoicePricingResult;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.entity.KhuyenMai;
import su26sd09.su26sd09.entity.Phong;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service gui email HTML xac nhan dat phong cho khach hang.
 *
 * <p>Tach rieng khoi {@link MailSenderService} (chi gui text thuan cho xac thuc
 * tai khoan) vi email dat phong can:
 * <ul>
 *   <li>HTML (multipart) voi template Thymeleaf chuyen nghiep</li>
 *   <li>Bang chi tiet phong, dich vu, thanh toan</li>
 *   <li>Chay async (khong block luong dat phong / thanh toan)</li>
 * </ul>
 *
 * <p>Cac diem hook:
 * <ol>
 *   <li>Sau khi khach online tao yeu cau dat phong (gui email "Yeu cau da gui")</li>
 *   <li>Sau khi NV xac nhan yeu cau (gui email "Da xac nhan, vui long thanh toan")</li>
 *   <li>Sau khi thanh toan thanh cong (gui email "Thanh toan thanh cong")</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEmailService {

    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final DatPhongService datPhongService;
    private final ChiTietDatPhongService chiTietDatPhongService;
    private final ChiTietDichVuService chiTietDichVuService;
    private final HoaDonService hoaDonService;
    private final InvoicePricingService invoicePricingService;

    @Value("${spring.mail.username:noreply@hotel.com}")
    private String fromAddress;

    @Value("${app.hotel-name:Hotel}")
    private String hotelName;

    @Value("${app.hotel-phone:+84 123 456 789}")
    private String hotelPhone;

    @Value("${app.hotel-address:123 Đường ABC, Quận 1, TP.HCM}")
    private String hotelAddress;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Gui email xac nhan cho khach sau khi tao dat phong online.
     * Trang thai don luc nay la "Yeu cau dat phong" nhung KHONG can NV duyet
     * nua — don da duoc tinh la mot dat phong (reservation) va giu phong
     * ngay. Khach co 24h de thanh toan (xem BookingExpiryScheduler / job huy
     * don qua han), neu khong don se bi XOA khoi CSDL. Vi vay email nay
     * luon kem QR + link toi /thanh-toan/pool de khach thanh toan ngay,
     * phong khi luong thanh toan chinh (VNPay redirect) bi gian doan.
     */
    @Async
    public void guiEmailYeuCauDatPhong(Integer maDatPhong) {
        DatPhong dp = layDatPhong(maDatPhong);
        if (dp == null) return;

        String emailNhan = layEmailKhach(dp);
        if (!emailHopLe(emailNhan)) {
            log.warn("[Email] Bo qua: don #{} khong co email hop le (email={})", maDatPhong, emailNhan);
            return;
        }

        try {
            Map<String, Object> vars = buildCommonVars(dp, "yeu-cau");
            vars.put("tieuDe", "Đặt phòng đã được ghi nhận");
            vars.put("loiChao", "Cảm ơn quý khách đã chọn " + hotelName + "!");
            vars.put("thongBaoChinh",
                    "Đặt phòng #" + dp.getId() + " của quý khách đã được ghi nhận và giữ chỗ trong hệ thống. "
                            + "Quý khách vui lòng hoàn tất thanh toán trong vòng 24 giờ kể từ khi đặt — "
                            + "nếu quá thời hạn mà chưa thanh toán, đơn sẽ tự động bị hủy.");

            // Kem QR + link thanh toan toi /thanh-toan/pool ngay tu email dau tien,
            // vi don khong con qua buoc NV xac nhan truoc khi thanh toan nua.
            BigDecimal soTienCanTra = (BigDecimal) vars.getOrDefault("tongCongRaw", BigDecimal.ZERO);
            String linkThanhToan = baseUrl + "/thanh-toan/pool?dat-phong-id=" + maDatPhong;
            vars.put("linkThanhToan", linkThanhToan);
            vars.put("qrThanhToanUrl", buildQrCodeUrl(linkThanhToan));
            vars.put("soTienConLai", formatTien(soTienCanTra));

            sendEmail(emailNhan,
                    "[Hotel] Đặt phòng #" + dp.getId() + " đã được ghi nhận - Vui lòng thanh toán trong 24h",
                    "email/xac-nhan-dat-phong", vars);
            log.info("[Email] Da gui email dat phong (kem QR thanh toan) toi {} (don #{})", emailNhan, maDatPhong);
        } catch (Exception e) {
            log.error("[Email] Loi gui email yeu cau dat phong don #{}: {}", maDatPhong, e.getMessage(), e);
        }
    }

    /**
     * Gui email khi NV xac nhan yeu cau dat phong (trang thai -> "Cho xac nhan").
     * Luc nay khach can biet: da xac nhan, vui long thanh toan.
     */
    @Async
    public void guiEmailXacNhanYeuCau(Integer maDatPhong) {
        DatPhong dp = layDatPhong(maDatPhong);
        if (dp == null) return;

        String emailNhan = layEmailKhach(dp);
        if (!emailHopLe(emailNhan)) {
            log.warn("[Email] Bo qua: don #{} khong co email hop le (email={})", maDatPhong, emailNhan);
            return;
        }

        try {
            Map<String, Object> vars = buildCommonVars(dp, "da-xac-nhan");
            vars.put("tieuDe", "Yêu cầu đặt phòng đã được xác nhận");
            vars.put("loiChao", "Yêu cầu của quý khách đã được xác nhận!");
            vars.put("thongBaoChinh",
                    "Yêu cầu đặt phòng #" + dp.getId() + " đã được nhân viên xác nhận. "
                            + "Quý khách vui lòng hoàn tất thanh toán để giữ phòng.");

            sendEmail(emailNhan,
                    "[Hotel] Yêu cầu đặt phòng #" + dp.getId() + " đã được xác nhận",
                    "email/xac-nhan-dat-phong", vars);
            log.info("[Email] Da gui email xac nhan yeu cau toi {} (don #{})", emailNhan, maDatPhong);
        } catch (Exception e) {
            log.error("[Email] Loi gui email xac nhan don #{}: {}", maDatPhong, e.getMessage(), e);
        }
    }

    /**
     * Gui email khi khach thanh toan thanh cong (hoa don da thanh toan du).
     */
    @Async
    public void guiEmailThanhToanThanhCong(Integer maDatPhong) {
        DatPhong dp = layDatPhong(maDatPhong);
        if (dp == null) return;

        String emailNhan = layEmailKhach(dp);
        if (!emailHopLe(emailNhan)) {
            log.warn("[Email] Bo qua: don #{} khong co email hop le (email={})", maDatPhong, emailNhan);
            return;
        }

        HoaDon hd = hoaDonService.findByDatPhongId(maDatPhong);

        try {
            Map<String, Object> vars = buildCommonVars(dp, "thanh-toan");
            vars.put("tieuDe", "Thanh toán đặt phòng thành công");
            vars.put("loiChao", "Cảm ơn quý khách đã thanh toán!");
            vars.put("thongBaoChinh",
                    "Đơn đặt phòng #" + dp.getId() + " đã được thanh toán thành công. "
                            + "Chúng tôi rất mong được đón tiếp quý khách tại " + hotelName + ".");

            if (hd != null) {
                vars.put("hoaDon", hd);
                vars.put("maHoaDon", hd.getId());
            }

            sendEmail(emailNhan,
                    "[Hotel] Thanh toán thành công - Đơn đặt phòng #" + dp.getId(),
                    "email/xac-nhan-dat-phong", vars);
            log.info("[Email] Da gui email thanh toan toi {} (don #{})", emailNhan, maDatPhong);
        } catch (Exception e) {
            log.error("[Email] Loi gui email thanh toan don #{}: {}", maDatPhong, e.getMessage(), e);
        }
    }

    /**
     * Gui email kem QR thanh toan khi don co mot khoan CON PHAI TRA (soTienConLai > 0).
     * Dung chung cho 2 truong hop:
     *   1) Don vua duoc "len lich" (tao) tu So Do Phong ma chua thu du tien.
     *   2) Don vua phat sinh chi phi (phu thu nhan som/tra muon...) lam tang cong no.
     *
     * QR tro thang toi /thanh-toan/pool?dat-phong-id={id} — endpoint nay se tu tao
     * (hoac tai su dung) yeu cau VNPay va redirect khach toi trang thanh toan phan
     * con lai cua don.
     */
    @Async
    public void guiEmailYeuCauThanhToan(Integer maDatPhong, String tieuDeNgan, String thongBao, BigDecimal soTienConLai) {
        DatPhong dp = layDatPhong(maDatPhong);
        if (dp == null) return;
        if (soTienConLai == null || soTienConLai.compareTo(BigDecimal.ZERO) <= 0) return;

        String emailNhan = layEmailKhach(dp);
        if (!emailHopLe(emailNhan)) {
            log.warn("[Email] Bo qua QR thanh toan: don #{} khong co email hop le (email={})", maDatPhong, emailNhan);
            return;
        }

        try {
            Map<String, Object> vars = buildCommonVars(dp, "yeu-cau-thanh-toan");
            vars.put("tieuDe", tieuDeNgan);
            vars.put("loiChao", "Kính gửi quý khách,");
            vars.put("thongBaoChinh", thongBao);
            vars.put("soTienConLai", formatTien(soTienConLai));

            String linkThanhToan = baseUrl + "/thanh-toan/pool?dat-phong-id=" + maDatPhong;
            vars.put("linkThanhToan", linkThanhToan);
            vars.put("qrThanhToanUrl", buildQrCodeUrl(linkThanhToan));

            sendEmail(emailNhan,
                    "[Hotel] Yêu cầu thanh toán - Đơn đặt phòng #" + dp.getId(),
                    "email/xac-nhan-dat-phong", vars);
            log.info("[Email] Da gui email QR thanh toan toi {} (don #{}, con lai {})", emailNhan, maDatPhong, soTienConLai);
        } catch (Exception e) {
            log.error("[Email] Loi gui email QR thanh toan don #{}: {}", maDatPhong, e.getMessage(), e);
        }
    }

    /**
     * Sinh URL anh QR (qua dich vu QR cong khai) ma-hoa link thanh toan.
     * Khong luu file, khong can them thu vien QR o backend — client mail app se
     * tu tai anh nay khi mo thu.
     */
    private String buildQrCodeUrl(String data) {
        String encoded = java.net.URLEncoder.encode(data, StandardCharsets.UTF_8);
        return "https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=" + encoded;
    }

    // ============== PRIVATE HELPERS ==============

    private DatPhong layDatPhong(Integer id) {
        if (id == null) return null;
        try {
            return datPhongService.findById(id);
        } catch (Exception e) {
            log.warn("[Email] Loi load don dat phong #{}: {}", id, e.getMessage());
            return null;
        }
    }

    private String layEmailKhach(DatPhong dp) {
        // Uu tien email tu tai khoan, neu khong co thi lay email tren don dat phong
        if (dp.getN() != null && dp.getN().getEmail() != null && !dp.getN().getEmail().isBlank()) {
            return dp.getN().getEmail().trim();
        }
        return dp.getEmail();
    }

    private boolean emailHopLe(String email) {
        return email != null && !email.isBlank() && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Build map bien dung chung cho ca 3 loai email:
     * - Thong tin khach (ho ten, sdt)
     * - Thong tin don (ma don, ngay nhan/tra, loai phong, ngay tao)
     * - Bang phong da gan
     * - Bang dich vu da chon
     * - Tong tien uoc tinh
     * - Ma tra cuu (neu co)
     */
    private Map<String, Object> buildCommonVars(DatPhong dp, String trangThaiEmail) {
        Map<String, Object> vars = new HashMap<>();

        // Thong tin khach
        String hoTen = dp.getN() != null ? dp.getN().getHoTen() : (dp.getHoten() != null ? dp.getHoten() : "Quý khách");
        vars.put("hoTenKhach", hoTen);
        vars.put("sdtKhach", dp.getN() != null ? dp.getN().getSoDienThoai() : dp.getSdt());
        // CCCD dung de doi soat chong gian lan (vd: nguoi khac claim don da thanh
        // toan la cua minh) — lay tu DatPhong (nhap luc dat/xac nhan don), KHONG
        // phai giay_to (chi thu thap luc check-in thuc te tai quay).
        vars.put("maCccdKhach", dp.getMa_cccd());

        // Thong tin dat phong
        vars.put("maDatPhong", dp.getId());
        vars.put("ngayNhanPhong", dp.getNgaydatPhong() != null ? dp.getNgaydatPhong().format(FMT_DATETIME) : "—");
        vars.put("ngayTraPhong", dp.getNgaytraPhong() != null ? dp.getNgaytraPhong().format(FMT_DATETIME) : "—");
        vars.put("ngayDat", dp.getNgayTao() != null ? dp.getNgayTao().format(FMT_DATETIME) : LocalDateTime.now().format(FMT_DATETIME));
        vars.put("soNguoiLon", dp.getSonguoiLon());
        vars.put("soTreEm", dp.getSotreEm());
        vars.put("soDem", tinhSoDem(dp));
        vars.put("yeuCauThem", dp.getYeuCauThem());
        vars.put("maTraCuu", dp.getMaTraCuu());

        // Trang thai don
        vars.put("trangThaiDon", formatTrangThai(dp.getTrangThai()));
        vars.put("trangThaiEmail", trangThaiEmail);

        // Bang phong da gan
        List<Map<String, Object>> dsPhong = new ArrayList<>();
        BigDecimal tongTienPhong = BigDecimal.ZERO;
        BigDecimal tongPhuPhi = BigDecimal.ZERO;
        try {
            List<ChiTietDatPhong> ctdpList = chiTietDatPhongService.findByDatPhongId(dp.getId());
            for (ChiTietDatPhong ct : ctdpList) {
                Map<String, Object> p = new HashMap<>();
                Phong phong = ct.getP();
                p.put("soPhong", phong != null ? phong.getSoPhong() : "—");
                p.put("tenLoaiPhong", phong != null && phong.getLoaiPhong() != null
                        ? phong.getLoaiPhong().getTenLoai() : "—");
                p.put("sucChua", phong != null && phong.getLoaiPhong() != null
                        ? phong.getLoaiPhong().getSucChuaToiDa() : null);
                p.put("giaMoiDem", formatTien(ct.getGiaMoiDem()));
                p.put("phuPhi", formatTien(ct.getPhuPhi()));
                p.put("tongPhong", formatTien(ct.getGiaKhiDat()));
                dsPhong.add(p);

                if (ct.getGiaKhiDat() != null) tongTienPhong = tongTienPhong.add(ct.getGiaKhiDat());
                if (ct.getPhuPhi() != null) tongPhuPhi = tongPhuPhi.add(ct.getPhuPhi());
            }
        } catch (Exception e) {
            log.warn("[Email] Loi lay danh sach phong don #{}: {}", dp.getId(), e.getMessage());
        }
        vars.put("danhSachPhong", dsPhong);
        vars.put("tongTienPhong", formatTien(tongTienPhong));
        vars.put("tongPhuPhi", formatTien(tongPhuPhi));

        // Bang dich vu
        List<Map<String, Object>> dsDichVu = new ArrayList<>();
        BigDecimal tongTienDv = BigDecimal.ZERO;
        try {
            List<Chi_tiet_dich_vu> dvList = chiTietDichVuService.findByDatPhongId(dp.getId());
            for (Chi_tiet_dich_vu dv : dvList) {
                Map<String, Object> d = new HashMap<>();
                d.put("tenDichVu", dv.getDv() != null ? dv.getDv().getTen_dich_vu() : "Dịch vụ");
                d.put("soLuong", dv.getSoluong());
                d.put("donGia", formatTien(dv.getDonGia()));
                d.put("thanhTien", formatTien(
                        dv.getDonGia() != null && dv.getSoluong() != null
                                ? dv.getDonGia().multiply(BigDecimal.valueOf(dv.getSoluong()))
                                : BigDecimal.ZERO));
                d.put("ngaySuDung", dv.getNgay_su_dung() != null ? dv.getNgay_su_dung().format(FMT_DATETIME) : "—");
                dsDichVu.add(d);
                if (dv.getDonGia() != null && dv.getSoluong() != null) {
                    tongTienDv = tongTienDv.add(dv.getDonGia().multiply(BigDecimal.valueOf(dv.getSoluong())));
                }
            }
        } catch (Exception e) {
            log.warn("[Email] Loi lay danh sach dich vu don #{}: {}", dp.getId(), e.getMessage());
        }
        vars.put("danhSachDichVu", dsDichVu);
        vars.put("tongTienDichVu", formatTien(tongTienDv));

        // Khuyen mai (neu co) - ap dung tren TONG (phong + dich vu). VIEW: dung
        // cong thuc CHUAN dung chung voi moi luong khac (xem InvoicePricingService).
        KhuyenMai km = dp.getKm();
        InvoicePricingResult gia = invoicePricingService.previewInvoice(dp.getId(), km);
        BigDecimal tienGiam = gia.getTienGiam();
        if (km != null) {
            vars.put("khuyenMai", km);
            vars.put("moTaKhuyenMai", km.getPromoCode() != null ? km.getPromoCode() : "—");
        } else {
            vars.put("khuyenMai", null);
            vars.put("moTaKhuyenMai", null);
        }
        vars.put("tienGiam", formatTien(tienGiam));

        // Phu phi gio nhan/tra phong: LUC MOI DAT khong tinh (se tinh sau khi check-in/out that su).
        // Phu phi trong DB (ct.getPhuPhi()) chi la du lieu khi NV check-in/out thuc te.
        // Hien tai trong BookingEmail chi hien thi "phu phi" neu da co du lieu that.
        boolean coPhuPhiThucTe = tongPhuPhi != null && tongPhuPhi.compareTo(BigDecimal.ZERO) > 0;
        vars.put("coPhuPhiThucTe", coPhuPhiThucTe);

        // Tong cong = (phong + DV - giam) + VAT 10% (VAT tren gia SAU giam).
        // KHONG cong phu phi (phu phi chi ap dung khi da check-in/out that su)
        BigDecimal truocVat = gia.getTongSauGiam();
        BigDecimal tienVat = gia.getTienVat();
        BigDecimal tongCong = gia.getTongTien();

        vars.put("tienTruocVat", formatTien(truocVat));
        vars.put("tienVat", formatTien(tienVat));
        vars.put("tongCong", formatTien(tongCong));
        vars.put("tongCongRaw", tongCong); // BigDecimal chua format, dung de tinh QR/soTienConLai

        // Thong tin khach san
        vars.put("tenKhachSan", hotelName);
        vars.put("sdtKhachSan", hotelPhone);
        vars.put("diaChiKhachSan", hotelAddress);
        vars.put("namHienTai", java.time.LocalDate.now().getYear());

        return vars;
    }

    private long tinhSoDem(DatPhong dp) {
        if (dp.getNgaydatPhong() == null || dp.getNgaytraPhong() == null) return 1;
        long days = ChronoUnit.DAYS.between(
                dp.getNgaydatPhong().toLocalDate(),
                dp.getNgaytraPhong().toLocalDate());
        return Math.max(1, days);
    }

    private String formatTrangThai(String tt) {
        if (tt == null) return "—";
        return switch (tt) {
            case "Yeu cau dat phong" -> "Đã giữ phòng - chờ thanh toán (trong 24h)";
            case "Cho xac nhan" -> "Chờ xác nhận thanh toán";
            case "Da xac nhan" -> "Đã xác nhận";
            case "Da nhan phong" -> "Đã nhận phòng";
            case "Da tra phong" -> "Đã trả phòng";
            case "Khach vang" -> "Khách vắng mặt (quá hạn check-in)";
            case "Da huy" -> "Đã hủy";
            default -> tt;
        };
    }

    private String formatTien(BigDecimal tien) {
        if (tien == null) tien = BigDecimal.ZERO;
        return String.format("%,.0f", tien.doubleValue()) + " VND";
    }

    private void sendEmail(String to, String subject, String templateName, Map<String, Object> vars)
            throws MessagingException {
        Context context = new Context();
        context.setVariables(vars);

        String htmlBody = templateEngine.process(templateName, context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true = HTML
        mailSender.send(message);
    }
}
