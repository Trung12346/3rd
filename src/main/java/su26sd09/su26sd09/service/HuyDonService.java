package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.constants.HuyDonConstants;
import su26sd09.su26sd09.dto.KetQuaHuyDonDTO;
import su26sd09.su26sd09.entity.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HuyDonService {

    @Autowired DatPhongService datPhongService;
    @Autowired HoaDonService hoaDonService;
    @Autowired ThanhToanService thanhToanService;
    @Autowired PhongService phongService; // giả định đã có, dùng để nhả phòng

    /**
     * Tính tỷ lệ hoàn tiền dựa trên SỐ PHÚT đã trôi qua kể từ khi đặt phòng đến lúc yêu cầu hủy.
     * Quy tắc hoàn tiền:
     *  - Dưới 30 phút             : 70%
     *  - Từ 30 phút đến 3 tiếng   : 50%
     *  - Từ 3 tiếng đến 8 tiếng   : 40%
     *  - Trên 8 tiếng             : 30%
     * Ngoài ra: nếu khách đã nhận phòng / trả phòng thì tỷ lệ = 0% (chính sách không cho hủy).
     */
    public BigDecimal tinhTyLeHoan(DatPhong dp) {
        if (dp == null || dp.getNgayTao() == null) return BigDecimal.ZERO;
        boolean chuaCheckIn = !"Da nhan phong".equals(dp.getTrangThai())
                && !"Da tra phong".equals(dp.getTrangThai());
        if (!chuaCheckIn) return BigDecimal.ZERO;

        long soPhutDaTroi = Duration.between(dp.getNgayTao(), LocalDateTime.now()).toMinutes();
        if (soPhutDaTroi < 0) soPhutDaTroi = 0; // trường hợp clock lệch

        if (soPhutDaTroi < 30) {
            return new BigDecimal("0.70");
        }
        if (soPhutDaTroi < 180) { // 3 tiếng = 180 phút
            return new BigDecimal("0.50");
        }
        if (soPhutDaTroi < 480) { // 8 tiếng = 480 phút
            return new BigDecimal("0.40");
        }
        return new BigDecimal("0.30");
    }

    /**
     * Bước 1: Tiếp nhận yêu cầu hủy đơn.
     * - Tính tỷ lệ hoàn + số tiền hoàn dự kiến dựa trên rule, lưu vào hóa đơn.
     * - Set trạng thái hóa đơn "Cho xu ly" để admin/nhân viên xử lý thủ công.
     * - Set trạng thái đơn đặt phòng = "Cho huy" (TRUNG GIAN), KHÔNG nhả phòng, KHÔNG set "Da huy".
     * Việc chuyển sang "Da huy" + nhả phòng sẽ được thực hiện ở bước 2 (xacNhanHoanTien).
     *
     * Lưu ý quan trọng: hàm này KHÔNG tự ý set "Da huy" + nhả phòng ở bất kỳ nhánh nào.
     * Mọi case (kể cả chưa thu tiền, tỷ lệ hoàn thấp, quá hạn...) đều để trạng thái = "Cho xu ly"
     * để nhân viên/admin được đưa vào trang chi tiết hoàn tiền và tự quyết định xử lý thủ công.
     * Ngoại lệ duy nhất: đơn chưa có hóa đơn (hd == null) thì hủy thẳng vì không có gì để xử lý.
     */
    public KetQuaHuyDonDTO huyDon(Integer datPhongId) {
        DatPhong dp = datPhongService.findById(datPhongId);
        if (dp == null) return new KetQuaHuyDonDTO("Khong tim thay don", null, false);

        if ("Da huy".equals(dp.getTrangThai()) || HuyDonConstants.DP_CHO_HUY.equals(dp.getTrangThai()))
            return new KetQuaHuyDonDTO("Don da duoc yeu cau huy truoc do", null, false);
        if ("Da nhan phong".equals(dp.getTrangThai()) || "Da tra phong".equals(dp.getTrangThai()))
            return new KetQuaHuyDonDTO("Khach da nhan phong, khong the huy theo chinh sach nay", null, false);

        HoaDon hd = hoaDonService.findByDatPhongId(datPhongId);
        BigDecimal tyLe = tinhTyLeHoan(dp);

        // ===== Case đặc biệt: đơn chưa có hóa đơn =====
        // Không có hóa đơn thì không có gì để hoàn, không cần đưa vào trang hoàn tiền.
        // Hủy thẳng + nhả phòng luôn.
        if (hd == null) {
            dp.setTrangThai("Da huy");
            dp.setNgayCapNhat(LocalDateTime.now());
            datPhongService.save(dp);
            nhaPhong(datPhongId);
            return new KetQuaHuyDonDTO("Da huy don. Khong phat sinh hoan tien (don chua co hoa don).", null, false);
        }

        BigDecimal daThu = hd.getDaThanhToan() == null ? BigDecimal.ZERO : hd.getDaThanhToan();
        BigDecimal soTienHoan = daThu.multiply(tyLe).setScale(0, RoundingMode.HALF_UP);

        // ===== Luôn set đơn sang "Cho huy" trung gian, KHÔNG nhả phòng =====
        dp.setTrangThai(HuyDonConstants.DP_CHO_HUY);
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        // ===== Lưu thông tin hoàn tiền lên hóa đơn (LUÔN set "Cho xu ly" để NV/Admin tự xử lý) =====
        hd.setTyLeHoan(tyLe);
        hd.setSoTienHoan(soTienHoan);
        hd.setNgayYeuCauHoan(LocalDateTime.now());
        hd.trangThaiHoanTien = HuyDonConstants.TT_HOAN_CHO_XU_LY;
        hoaDonService.save(hd);

        String msg = "Da ghi nhan yeu cau huy don. Vui long xu ly hoan "
                + (soTienHoan.compareTo(BigDecimal.ZERO) > 0 ? soTienHoan + " VND" : "(so tien = 0, NV/Admin tu quyet dinh)")
                + " cho khach. Ty le hoan ap dung: " + tyLe.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP) + "%.";
        return new KetQuaHuyDonDTO(msg, hd.getId(), true);
    }

    /**
     * Nối thêm ghi chú vào ghiChu cũ, phân cách bằng " | ". Nếu ghiChu cũ null/rỗng thì trả về ghiChu mới.
     */
    private String appendGhiChu(String ghiChuCu, String ghiChuMoi) {
        if (ghiChuCu == null || ghiChuCu.isBlank()) return ghiChuMoi;
        return ghiChuCu + " | " + ghiChuMoi;
    }

    /**
     * Bước 2: NV/Admin xác nhận đã trả tiền cho khách (tiền mặt hoặc chuyển khoản thủ công).
     * SAU KHI xác nhận hoàn tiền xong mới chính thức chuyển trạng thái đơn sang "Da huy" + nhả phòng.
     */
    public void xacNhanHoanTien(Integer hoaDonId, String phuongThucHoan,
                                String maGiaoDichHoan, String stkNhanHoan,
                                String tenNganHang, String ghiChu, BigDecimal soTienHoanNhap,
                                NhanSu nvXuLy) {
        HoaDon hd = hoaDonService.findById(hoaDonId);
        if (hd == null) return;

        // Số tiền hoàn thực tế: ưu tiên dùng giá NV/Admin nhập (cho phép chỉnh tay),
        // nếu không nhập thì dùng số tiền đã được hệ thống tính sẵn theo rule.
        BigDecimal soTienHoanThucTe;
        if (soTienHoanNhap != null && soTienHoanNhap.signum() > 0) {
            soTienHoanThucTe = soTienHoanNhap.setScale(0, RoundingMode.HALF_UP);
        } else {
            soTienHoanThucTe = hd.getSoTienHoan() == null ? BigDecimal.ZERO : hd.getSoTienHoan();
        }

        // Cập nhật số tiền hoàn cuối cùng lên hóa đơn (phòng trường hợp NV/Admin override)
        hd.setSoTienHoan(soTienHoanThucTe);

        // Chỉ tạo giao dịch hoàn khi thực sự có tiền cần trả cho khách
        if (soTienHoanThucTe.compareTo(BigDecimal.ZERO) > 0) {
            ThanhToan ttHoan = new ThanhToan();
            ttHoan.setH(hd);
            ttHoan.setLoaiGiaoDich(HuyDonConstants.LOAI_GD_HOAN);
            ttHoan.setPhuongThuc(phuongThucHoan);
            ttHoan.setSoTien(soTienHoanThucTe);
            ttHoan.setTrangThai(HuyDonConstants.TT_HOAN_DA_HOAN);
            ttHoan.setMagiaodich(maGiaoDichHoan); // null nếu tiền mặt
            ttHoan.setStkNhanHoan(stkNhanHoan);
            ttHoan.setTenNganHangNhanHoan(tenNganHang);
            ttHoan.setNv(nvXuLy);
            ttHoan.setNgaythanhToan(LocalDateTime.now());
            ttHoan.setGichu(ghiChu);
            thanhToanService.save(ttHoan);
        }

        BigDecimal daHoanTruoc = hd.getDaHoanTra() == null ? BigDecimal.ZERO : hd.getDaHoanTra();
        hd.setDaHoanTra(daHoanTruoc.add(soTienHoanThucTe));
        hd.setTrangThaiHoanTien(HuyDonConstants.TT_HOAN_DA_HOAN);
        hd.setNgayCapNhat(LocalDateTime.now());
        hoaDonService.save(hd);

        // === Đã hoàn tiền xong -> chính thức hủy đơn + nhả phòng ===
        DatPhong dp = hd.getD();
        if (dp != null) {
            dp.setTrangThai("Da huy");
            dp.setNgayCapNhat(LocalDateTime.now());
            datPhongService.save(dp);
            nhaPhong(dp.getId());
        }
    }

    /**
     * Nhả tất cả các phòng của 1 đơn đặt phòng nếu không còn đơn nào khác giữ phòng.
     */
    private void nhaPhong(Integer datPhongId) {
        List<Phong> dsPhong = datPhongService.findPhongByDatPhongId(datPhongId);
        for (Phong p : dsPhong) {
            boolean conDonKhacGiuPhong = datPhongService.hasBookingNotCheckout(p.getMaPhong(), datPhongId);
            if (!conDonKhacGiuPhong) {
                p.setTrangThai("Trong");
                phongService.save1(p);
            }
        }
    }

    /**
     * Bước 2 (luồng chuyển khoản qua VNPay): Sau khi VNPay callback thành công.
     * Tạo giao dịch hoàn tiền với phương thức "Chuyen Khoan" + mã GD VNPay, lưu STK/ngân hàng/ghi chú
     * từ draft admin nhập, rồi chuyển trạng thái đơn sang "Da huy" + nhả phòng.
     */
    public void xacNhanHoanTienVnpay(Integer hoaDonId, String vnpTransactionNo,
                                     String stkNhanHoan, String tenNganHang,
                                     String ghiChu, LocalDateTime thoiGianThanhToan, NhanSu nvXuLy) {
        HoaDon hd = hoaDonService.findById(hoaDonId);
        if (hd == null || hd.getSoTienHoan() == null) return;

        // Chỉ tạo giao dịch hoàn khi thực sự có tiền (tránh tạo record rỗng khi soTienHoan = 0)
        if (hd.getSoTienHoan().compareTo(BigDecimal.ZERO) > 0) {
            ThanhToan ttHoan = new ThanhToan();
            ttHoan.setH(hd);
            ttHoan.setLoaiGiaoDich(HuyDonConstants.LOAI_GD_HOAN);
            ttHoan.setPhuongThuc(HuyDonConstants.PT_CHUYEN_KHOAN);
            ttHoan.setSoTien(hd.getSoTienHoan());
            ttHoan.setTrangThai(HuyDonConstants.TT_HOAN_DA_HOAN);
            ttHoan.setMagiaodich(vnpTransactionNo);
            ttHoan.setStkNhanHoan(stkNhanHoan);
            ttHoan.setTenNganHangNhanHoan(tenNganHang);
            ttHoan.setNv(nvXuLy);
            ttHoan.setNgaythanhToan(thoiGianThanhToan == null ? LocalDateTime.now() : thoiGianThanhToan);
            ttHoan.setGichu(ghiChu);
            thanhToanService.save(ttHoan);
        }

        BigDecimal daHoanTruoc = hd.getDaHoanTra() == null ? BigDecimal.ZERO : hd.getDaHoanTra();
        hd.setDaHoanTra(daHoanTruoc.add(hd.getSoTienHoan()));
        hd.setTrangThaiHoanTien(HuyDonConstants.TT_HOAN_DA_HOAN);
        hd.setNgayCapNhat(LocalDateTime.now());
        hoaDonService.save(hd);

        // Hoàn tiền xong -> chính thức hủy đơn + nhả phòng
        DatPhong dp = hd.getD();
        if (dp != null) {
            dp.setTrangThai("Da huy");
            dp.setNgayCapNhat(LocalDateTime.now());
            datPhongService.save(dp);
            nhaPhong(dp.getId());
        }
    }

    public void tuChoiHoanTien(Integer hoaDonId, String lyDo) {
        HoaDon hd = hoaDonService.findById(hoaDonId);
        if (hd == null) return;
        hd.setTrangThaiHoanTien(HuyDonConstants.TT_HOAN_TU_CHOI);
        hd.setGhiChu((hd.getGhiChu() == null ? "" : hd.getGhiChu() + " | ") + "Tu choi hoan: " + lyDo);
        hoaDonService.save(hd);

        // Từ chối hoàn -> đơn KHÔNG hủy, trả đơn về trạng thái "Da xac nhan" để có thể tiếp tục xử lý
        DatPhong dp = hd.getD();
        if (dp != null && HuyDonConstants.DP_CHO_HUY.equals(dp.getTrangThai())) {
            dp.setTrangThai("Da xac nhan");
            dp.setNgayCapNhat(LocalDateTime.now());
            datPhongService.save(dp);
        }
    }

    /**
     * Tính thời gian đã trôi qua từ lúc tạo đơn đến hiện tại (dùng để hiển thị toast cảnh báo).
     * - Nếu < 1 ngày: trả về chuỗi "hh:mm:ss" (ví dụ "02:35:12").
     * - Nếu >= 1 ngày: trả về "Qua han tao yeu cau huy".
     * - Nếu ngayTao = null: trả về null (view sẽ không hiện toast).
     */
    public String tinhThoiGianXuLyYeuCauHuy(DatPhong dp) {
        if (dp == null || dp.getNgayTao() == null) return null;
        Duration d = Duration.between(dp.getNgayTao(), LocalDateTime.now());
        if (d.isNegative()) d = Duration.ZERO;
        if (d.toDays() >= 1) {
            return "Qua han tao yeu cau huy";
        }
        long totalSeconds = d.getSeconds();
        long hh = totalSeconds / 3600;
        long mm = (totalSeconds % 3600) / 60;
        long ss = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }
}