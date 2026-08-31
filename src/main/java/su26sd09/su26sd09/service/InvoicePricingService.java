package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.dto.InvoicePricingResult;
import su26sd09.su26sd09.entity.*;
import su26sd09.su26sd09.repository.ChiTietDatPhongRepo;
import su26sd09.su26sd09.repository.ChiTietDichvuRepo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * ĐIỂM DUY NHẤT chịu trách nhiệm tính tiền hóa đơn (hoa_don) cho toàn bộ hệ
 * thống. Thay thế mọi công thức tính tongTien/tienVat/tienGiam từng bị lặp
 * lại rải rác ở ThanhToanController, NhanVienCheckoutController,
 * AdminDatPhongController, HoaDonService...
 *
 * CÔNG THỨC CHUẨN (VAT tính trên phần SAU giảm giá — "VAT on net"):
 *   tongSauGiam = tienPhong + tienDichVu - tienGiam
 *   tienVat     = tongSauGiam * 10%              (làm tròn 2 chữ số, HALF_UP)
 *   tongTien    = tongSauGiam + tienVat
 *
 * 3 hàm global, phân loại theo NGUỒN LẤY ĐƠN GIÁ (không phải theo tên
 * controller/nghiệp vụ gọi tới):
 *
 *  - createLineItemPrice(...)   [NEW]
 *      Dùng khi một phòng/dịch vụ được gắn vào đơn LẦN ĐẦU — kể cả khi việc
 *      gắn thêm đó xảy ra trong lúc "sửa" một đơn đã tồn tại (thêm dịch vụ
 *      phát sinh, đổi/thêm phòng...). Đơn giá luôn lấy trực tiếp từ bảng gốc
 *      (Phong.giaMoiDem, Dich_vu.gia) — KHÔNG đọc lại từ bảng trung gian.
 *
 *  - recalculateInvoice(maDatPhong, km)   [UPDATE_EXISTING]
 *      Dùng để tính lại toàn bộ hóa đơn dựa trên các dòng ĐÃ LƯU trong bảng
 *      trung gian (chi_tiet_dat_phong, chi_tiet_dich_vu). Gọi hàm này SAU
 *      MỌI thao tác thêm/sửa/xóa chi tiết (kể cả sau khi vừa gọi
 *      createLineItemPrice ở trên) để đồng bộ lại tienPhong/tienDichVu/
 *      tienGiam/tienVat/tongTien của HoaDon. Đây là nguồn CHÂN LÝ khi ghi
 *      xuống DB.
 *
 *  - previewInvoice(maDatPhong, km)   [VIEW]
 *      Logic tính giống hệt recalculateInvoice (đọc từ cùng 2 bảng trung
 *      gian), nhưng KHÔNG lưu gì xuống DB — chỉ trả về DTO để hiển thị
 *      (trang thanh toán, trang xem trước hóa đơn, trang thành công...).
 */
@Service
public class InvoicePricingService {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");

    @Autowired
    private ChiTietDatPhongRepo chiTietDatPhongRepo;

    @Autowired
    private ChiTietDichvuRepo chiTietDichVuRepo;

    // =====================================================================
    // [NEW] — đơn giá lấy trực tiếp từ bảng gốc (Phong / Dich_vu)
    // =====================================================================

    /**
     * Tính giá cho MỘT phòng khi gắn vào đơn đặt phòng lần đầu (tạo mới đơn,
     * hoặc thêm/đổi phòng vào đơn đã tồn tại). Đơn giá lấy trực tiếp từ
     * {@code phong.getGiaMoiDem()}, KHÔNG đọc lại từ ChiTietDatPhong.
     *
     * @param phong   phòng được chọn (nguồn giá gốc)
     * @param ngayNhan ngày nhận phòng
     * @param ngayTra  ngày trả phòng
     * @param phuPhi   phụ phí phát sinh (vd trả muộn); truyền BigDecimal.ZERO nếu không có
     * @return giá phòng cho lần gắn này = giaMoiDem * soDem + phuPhi
     */
    public BigDecimal createRoomLineItemPrice(Phong phong,
                                               java.time.LocalDateTime ngayNhan,
                                               java.time.LocalDateTime ngayTra,
                                               BigDecimal phuPhi) {
        long soDem = ChronoUnit.DAYS.between(ngayNhan.toLocalDate(), ngayTra.toLocalDate());
        if (soDem < 1) soDem = 1;
        BigDecimal phi = phuPhi == null ? BigDecimal.ZERO : phuPhi;
        return phong.getGiaMoiDem().multiply(BigDecimal.valueOf(soDem)).add(phi);
    }

    /**
     * Tính giá cho MỘT dịch vụ khi gắn vào đơn lần đầu (thêm dịch vụ mới,
     * kể cả thêm vào đơn đã tồn tại). Đơn giá lấy trực tiếp từ
     * {@code dichVu.getGia()}, KHÔNG đọc lại từ Chi_tiet_dich_vu.
     *
     * @param dichVu  dịch vụ được chọn (nguồn giá gốc)
     * @param soLuong số lượng
     * @return đơn giá cho lần gắn này = gia * soLuong
     */
    public BigDecimal createServiceLineItemPrice(Dich_vu dichVu, int soLuong) {
        return dichVu.getGia().multiply(BigDecimal.valueOf(Math.max(1, soLuong)));
    }

    // =====================================================================
    // [UPDATE_EXISTING] — đọc lại từ bảng trung gian, tính lại toàn bộ hóa
    // đơn, và LƯU xuống DB.
    // =====================================================================

    /**
     * Tính lại và LƯU tongTien/tienVat/tienGiam/tienPhong/tienDichVu cho hóa
     * đơn của một đơn đặt phòng, dựa trên các dòng hiện có trong
     * chi_tiet_dat_phong + chi_tiet_dich_vu. Gọi sau MỌI thay đổi (thêm/sửa/
     * xóa phòng hoặc dịch vụ), kể cả các dòng vừa được tạo giá bằng
     * createRoomLineItemPrice()/createServiceLineItemPrice() ở trên.
     *
     * Nếu hóa đơn chưa tồn tại, hàm chỉ trả về kết quả tính toán (DTO)
     * chứ không có gì để lưu — caller tự quyết định tạo mới HoaDon từ đó.
     *
     * @param maDatPhong id đơn đặt phòng
     * @param km         khuyến mãi áp dụng cho đơn (có thể null)
     * @param hoaDonHienTai hóa đơn hiện tại nếu đã tồn tại (để lưu); truyền null nếu chưa có
     * @return kết quả tính toán đầy đủ (đã set vào hoaDonHienTai nếu khác null, CHƯA gọi save)
     */
    public InvoicePricingResult recalculateInvoice(Integer maDatPhong, KhuyenMai km, HoaDon hoaDonHienTai) {
        InvoicePricingResult result = computeFromLinkedTables(maDatPhong, km);

        if (hoaDonHienTai != null) {
            hoaDonHienTai.setTienPhong(result.getTienPhong());
            hoaDonHienTai.setTienDichVu(result.getTienDichVu());
            hoaDonHienTai.setTienGiam(result.getTienGiam());
            hoaDonHienTai.setTienVat(result.getTienVat());
            hoaDonHienTai.setTongTien(result.getTongTien());
        }
        return result;
    }

    // =====================================================================
    // [VIEW] — cùng logic đọc từ bảng trung gian như UPDATE_EXISTING,
    // nhưng KHÔNG lưu gì xuống DB. Dùng cho mọi trang hiển thị.
    // =====================================================================

    /**
     * Xem trước hóa đơn (không lưu DB). Logic tính giống hệt
     * recalculateInvoice(), dùng cho trang thanh toán / trang xem trước /
     * trang thành công... để đảm bảo số hiển thị luôn khớp với số sẽ được
     * lưu khi thực sự chốt hóa đơn.
     *
     * @param maDatPhong id đơn đặt phòng
     * @param km         khuyến mãi áp dụng cho đơn (có thể null)
     * @return kết quả tính toán để hiển thị
     */
    public InvoicePricingResult previewInvoice(Integer maDatPhong, KhuyenMai km) {
        return computeFromLinkedTables(maDatPhong, km);
    }

    // =====================================================================
    // Core dùng chung cho UPDATE_EXISTING và VIEW (cùng công thức, khác việc
    // có ghi xuống DB hay không).
    // =====================================================================

    /**
     * Ap dung cong thuc CHUAN (VAT tren gia SAU giam) cho MOT CAP tong tien
     * phong/tong tien dich vu ĐÃ BIẾT SẴN — dùng cho các trường hợp "giỏ
     * hàng" / booking nháp CHƯA được lưu vào chi_tiet_dat_phong /
     * chi_tiet_dich_vu (nên không thể đọc lại bằng previewInvoice/
     * recalculateInvoice, vốn cần một maDatPhong đã tồn tại trong DB).
     *
     * Cac dong tien phong/dich vu dau vao PHAI da duoc tinh bang
     * createRoomLineItemPrice()/createServiceLineItemPrice() (nguon NEW).
     */
    public InvoicePricingResult computeTotals(BigDecimal tienPhong, BigDecimal tienDichVu, KhuyenMai km) {
        BigDecimal soPhong = tienPhong == null ? BigDecimal.ZERO : tienPhong;
        BigDecimal soDichVu = tienDichVu == null ? BigDecimal.ZERO : tienDichVu;

        BigDecimal tongTruocGiam = soPhong.add(soDichVu);
        BigDecimal tienGiam = tinhTienGiam(tongTruocGiam, km);

        BigDecimal tongSauGiam = tongTruocGiam.subtract(tienGiam);
        BigDecimal tienVat = tongSauGiam.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tongTien = tongSauGiam.add(tienVat);

        return new InvoicePricingResult(soPhong, soDichVu, tienGiam, tienVat, tongTien);
    }

    private InvoicePricingResult computeFromLinkedTables(Integer maDatPhong, KhuyenMai km) {
        List<ChiTietDatPhong> dsPhong = chiTietDatPhongRepo.findByDatPhongId(maDatPhong);
        List<Chi_tiet_dich_vu> dsDichVu = chiTietDichVuRepo.findByDatPhongId(maDatPhong);

        BigDecimal tienPhong = dsPhong.stream()
                .map(ChiTietDatPhong::getGiaKhiDat)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tienDichVu = dsDichVu.stream()
                .map(Chi_tiet_dich_vu::getDonGia)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return computeTotals(tienPhong, tienDichVu, km);
    }

    /**
     * Công thức giảm giá dùng chung. Áp dụng trên TỔNG (tiền phòng + tiền
     * dịch vụ) trước VAT.
     */
    private BigDecimal tinhTienGiam(BigDecimal tongTruocGiam, KhuyenMai km) {
        if (km == null || !km.isHoatDong() || km.getGiatriGiam() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal dieuKien = km.getGiaToiThieuDuocGiam() == null ? BigDecimal.ZERO : km.getGiaToiThieuDuocGiam();
        if (tongTruocGiam.compareTo(dieuKien) < 0) {
            return BigDecimal.ZERO;
        }
        if ("PERCENT".equalsIgnoreCase(km.getLoaiGiam())) {
            return tongTruocGiam.multiply(km.getGiatriGiam())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if ("AMOUNT".equalsIgnoreCase(km.getLoaiGiam()) || "FIXED".equalsIgnoreCase(km.getLoaiGiam())) {
            return km.getGiatriGiam().min(tongTruocGiam);
        }
        return BigDecimal.ZERO;
    }
}
