package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.constants.HuyDonConstants;
import su26sd09.su26sd09.dto.KetQuaHuyDonDTO;
import su26sd09.su26sd09.entity.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class HuyDonService {

    @Autowired DatPhongService datPhongService;
    @Autowired HoaDonService hoaDonService;
    @Autowired ThanhToanService thanhToanService;
    @Autowired PhongService phongService; // giả định đã có, dùng để nhả phòng

    /**
     * Rule hoàn tiền mới (áp dụng từ 2026): dựa vào số NGÀY từ thời điểm tạo yêu cầu hủy
     * (NgayYeuCauHoan trên HoaDon) đến ngày check-in (ngày nhận phòng dự kiến trên DatPhong).
     *
     *  - >= 10 ngày       : 100% (hoàn toàn bộ)
     *  - 9 ngày           : 90%
     *  - 8 ngày           : 80%
     *  - 7 ngày           : 70%
     *  - 6 ngày           : 60%
     *  - 5 ngày           : 50%
     *  - 4 ngày           : 40%
     *  - 3 ngày           : 30%
     *  - 2 ngày           : 20%
     *  - 1 ngày           : 10%
     *  - 0 ngày (trong ngày check-in) : 0%
     *  - âm (quá ngày check-in)       : 0%
     *
     * Mỗi ngày rút ngắn so với ngày check-in sẽ -10%.
     * Tỉ lệ thuận nghịch với khoảng cách: khoảng cách từ "ngày tạo yêu cầu hủy" đến
     * "ngày check-in" càng ngắn thì tỉ lệ hoàn càng thấp; tỉ lệ 100% áp dụng từ ngày thứ 10 trở đi.
     *
     * Ngoài ra: nếu khách đã nhận phòng / trả phòng thì tỷ lệ = 0%
     * (chính sách không cho hủy sau check-in).
     */
    public BigDecimal tinhTyLeHoan(DatPhong dp) {
        return tinhTyLeHoan(dp, null);
    }

    /**
     * Overload có thêm {@code hd} để ưu tiên dùng {@code hd.ngayYeuCauHoan} làm mốc tính.
     * Nếu {@code hd} = null hoặc chưa có ngayYeuCauHoan sẽ fallback về thời điểm hiện tại.
     */
    public BigDecimal tinhTyLeHoan(DatPhong dp, HoaDon hd) {
        if (dp == null || dp.getNgaydatPhong() == null) return BigDecimal.ZERO;
        boolean chuaCheckIn = !"Da nhan phong".equals(dp.getTrangThai())
                && !"Da tra phong".equals(dp.getTrangThai());
        if (!chuaCheckIn) return BigDecimal.ZERO;

        // Mốc tính: NGÀY TẠO YÊU CẦU HỦY (NgayYeuCauHoan trên HoaDon).
        // Nếu chưa có (đơn cũ / tính tay) thì fallback về thời điểm hiện tại
        // để tránh null, nhưng rule mới luôn ưu tiên dùng ngayYeuCauHoan.
        LocalDateTime mocTaoYeuCau;
        if (hd != null && hd.getNgayYeuCauHoan() != null) {
            mocTaoYeuCau = hd.getNgayYeuCauHoan();
        } else {
            mocTaoYeuCau = LocalDateTime.now();
        }
        LocalDate ngayTaoYeuCauHuy = mocTaoYeuCau.toLocalDate();
        LocalDate ngayNhanPhong = dp.getNgaydatPhong().toLocalDate();
        long soNgayConLai = ChronoUnit.DAYS.between(ngayTaoYeuCauHuy, ngayNhanPhong);

        if (soNgayConLai >= 10) {
            return new BigDecimal("1.00");
        }
        if (soNgayConLai < 0) {
            // Da qua ngay check-in (huỷ muộn sau check-in) -> khong hoan.
            return BigDecimal.ZERO;
        }
        // 0..9 ngày: moi ngay giam 10% (ngay 9 = 90%, ngay 8 = 80%, ..., ngay 0 = 0%)
        // soNgayConLai=9 -> 0.90, soNgayConLai=0 -> 0.00
        long phanTram = soNgayConLai * 10L;
        return new BigDecimal(phanTram).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
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

        // Set ngày tạo yêu cầu hủy TRƯỚC khi tính tỷ lệ - đây là mốc tính khoảng cách
        // giữa "ngày tạo yêu cầu hủy" và "ngày check-in" theo rule mới.
        if (hd.getNgayYeuCauHoan() == null) {
            hd.setNgayYeuCauHoan(LocalDateTime.now());
        }
        BigDecimal tyLe = tinhTyLeHoan(dp, hd);

        BigDecimal daThu = hd.getDaThanhToan() == null ? BigDecimal.ZERO : hd.getDaThanhToan();
        BigDecimal soTienHoan = daThu.multiply(tyLe).setScale(0, RoundingMode.HALF_UP);

        // ===== Luôn set đơn sang "Cho huy" trung gian, KHÔNG nhả phòng =====
        dp.setTrangThai(HuyDonConstants.DP_CHO_HUY);
        dp.setNgayCapNhat(LocalDateTime.now());
        datPhongService.save(dp);

        // ===== Lưu thông tin hoàn tiền lên hóa đơn (LUÔN set "Cho xu ly" để NV/Admin tự xử lý) =====
        hd.setTyLeHoan(tyLe);
        hd.setSoTienHoan(soTienHoan);
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
     * Bước 2 (luồng "hủy đơn không hoàn"): NV/Admin xác nhận hủy đơn nhưng KHÔNG hoàn tiền.
     * Dùng khi tỷ lệ hoàn = 0% theo rule (khách hủy muộn) hoặc NV/Admin quyết định không hoàn.
     *
     * Hành vi:
     *  - Set trạng thái hóa đơn = "Huy khong hoan", số tiền hoàn = 0.
     *  - Ghi lý do hủy vào ghiChu hóa đơn (phân cách bằng " | " nếu đã có ghi chú).
     *  - Set đơn đặt phòng = "Da huy" (CHÍNH THỨC hủy, không phải "Cho huy" trung gian).
     *  - Nhả tất cả phòng liên quan về "Trong" (nếu không còn đơn nào khác giữ).
     *  - KHÔNG tạo giao dịch hoàn tiền, KHÔNG cộng vào daHoanTra.
     *  - Hóa đơn vẫn được phép xuất PDF cho khách cầm về (minh bạch, số tiền hoàn = 0).
     */
    public void xacNhanHuyKhongHoan(Integer hoaDonId, String lyDo, NhanSu nvXuLy) {
        HoaDon hd = hoaDonService.findById(hoaDonId);
        if (hd == null) return;

        // Cập nhật hóa đơn: set trạng thái, số tiền hoàn = 0, ghi lý do
        hd.setTrangThaiHoanTien(HuyDonConstants.TT_HOAN_HUY_KHONG_HOAN);
        hd.setSoTienHoan(BigDecimal.ZERO);
        hd.setDaHoanTra(BigDecimal.ZERO);
        String lyDoFull = "Huy don khong hoan: " + (lyDo == null || lyDo.isBlank() ? "Khong ro ly do" : lyDo);
        hd.setGhiChu((hd.getGhiChu() == null || hd.getGhiChu().isBlank())
                ? lyDoFull
                : hd.getGhiChu() + " | " + lyDoFull);
        hd.setNgayCapNhat(LocalDateTime.now());
        hoaDonService.save(hd);

        // Chính thức hủy đơn + nhả phòng
        DatPhong dp = hd.getD();
        if (dp != null) {
            dp.setTrangThai("Da huy");
            dp.setNgayCapNhat(LocalDateTime.now());
            datPhongService.save(dp);
            nhaPhong(dp.getId());
        }
    }

    /**
     * Tính thời gian đã trôi qua t� lúc tạo đơn đến hiện tại (dùng để hiển thị toast cảnh báo).
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

    /**
     * Khoảng cách (số ngày) từ thời điểm TẠO YÊU CẦU HỦY (ngayYeuCauHoan trên HoaDon)
     * đến ngày check-in (ngaydatPhong trên DatPhong). Đây là căn cứ tính % hoàn tiền theo rule mới.
     * Trả về:
     *  - null nếu ngaydatPhong = null
     *  - số ngày >= 0 nếu chưa đến check-in
     *  - số ngày âm nếu đã qua ngày check-in
     * Nếu chưa có ngayYeuCauHoan (đơn cũ / tính tay) thì fallback về thời điểm hiện tại.
     */
    public Long tinhKhoangCachNgayCheckIn(DatPhong dp) {
        return tinhKhoangCachNgayCheckIn(dp, null);
    }

    public Long tinhKhoangCachNgayCheckIn(DatPhong dp, HoaDon hd) {
        if (dp == null || dp.getNgaydatPhong() == null) return null;
        LocalDateTime mocTaoYeuCau;
        if (hd != null && hd.getNgayYeuCauHoan() != null) {
            mocTaoYeuCau = hd.getNgayYeuCauHoan();
        } else {
            mocTaoYeuCau = LocalDateTime.now();
        }
        LocalDate ngayTaoYeuCau = mocTaoYeuCau.toLocalDate();
        LocalDate ngayNhan = dp.getNgaydatPhong().toLocalDate();
        return ChronoUnit.DAYS.between(ngayTaoYeuCau, ngayNhan);
    }

    /**
     * Sinh chuỗi mô tả khoảng cách để hiển thị trên UI, ví dụ:
     *  - "Còn 9 ngày đến check-in"
     *  - "Còn 0 ngày đến check-in (trong ngày)"
     *  - "Đã qua 2 ngày so với check-in"
     * Trả về null nếu không tính được.
     */
    public String moTaKhoangCachNgayCheckIn(DatPhong dp) {
        return moTaKhoangCachNgayCheckIn(dp, null);
    }

    public String moTaKhoangCachNgayCheckIn(DatPhong dp, HoaDon hd) {
        Long soNgay = tinhKhoangCachNgayCheckIn(dp, hd);
        if (soNgay == null) return null;
        if (soNgay > 0) {
            return "Còn " + soNgay + " ngày đến check-in";
        }
        if (soNgay == 0) {
            return "Còn 0 ngày đến check-in (trong ngày)";
        }
        return "Đã qua " + Math.abs(soNgay) + " ngày so với check-in";
    }
}