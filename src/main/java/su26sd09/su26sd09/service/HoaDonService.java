package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.constants.HuyDonConstants;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.repository.HoaDonRepo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HoaDonService {

    @Autowired
    HoaDonRepo hoaDonRepo;

    @Autowired
    DatPhongService datPhongService;

    public static final String TT_DA_THANH_TOAN = "Da thanh toan";
    public static final String TT_DA_XUAT = "Da xuat";
    public static final String TT_CHO_THANH_TOAN = "Cho thanh toan";

    public List<HoaDon> findAll(){
        return hoaDonRepo.findAll();
    }
    public HoaDon save(HoaDon hd){
        return hoaDonRepo.save(hd);
    }
    public HoaDon findById(Integer id){
        return hoaDonRepo.findById(id).orElse(null);
    }

    public HoaDon findByDatPhongId(Integer maDatPhong) {
        return hoaDonRepo.findByD_Id(maDatPhong);
    }

    /**
     * Lưu hóa đơn và tự động đồng bộ trangThai theo mức thanh toán:
     * - Nếu khách đã thanh toán đủ (tongTien - daThanhToan <= 0) và đã có
     *   khoản thanh toán thực tế (daThanhToan > 0) → "Da thanh toan".
     * - Nếu tongTien > daThanhToan (chưa thanh toán đủ) → "Cho thanh toan".
     * - Trạng thái "Da xuat" (đã xuất PDF) không bị thay đổi.
     *
     * Dùng ở mọi nơi vừa cập nhật daThanhToan hoặc tongTien cho hóa đơn
     * (gồm cả các luồng sửa dịch vụ / phòng làm tăng tổng tiền).
     */
    public HoaDon saveWithPaymentStatusCheck(HoaDon hd) {
        if (hd != null) {
            // Không động vào "Da xuat" (trạng thái hạ nguồn, sinh ra khi xuất PDF).
            if (!TT_DA_XUAT.equals(hd.getTrangThai())) {
                if (isFullyPaid(hd)) {
                    hd.setTrangThai(TT_DA_THANH_TOAN);
                    capNhatDatPhongDaXacNhanNeuCanThanhToanDu(hd);
                } else {
                    hd.setTrangThai(TT_CHO_THANH_TOAN);
                }
            }
        }
        return hoaDonRepo.save(hd);
    }

    /**
     * Model nghiệp vụ MOI: đơn đặt phòng tự động chuyển sang "Da xac nhan" ngay khi
     * hóa đơn được thanh toán đủ 100% — KHÔNG còn phụ thuộc vào việc nhân viên bấm
     * nút "Xac nhan yeu cau" thủ công (model cũ đã bỏ).
     *
     * Chỉ đẩy trạng thái đi TỚI (Yeu cau dat phong / Cho xac nhan -> Da xac nhan),
     * không bao giờ ghi đè lên các trạng thái hạ nguồn hơn (Da nhan phong, Da tra
     * phong, Da huy, Cho huy...) — ví dụ đơn đặt tại quầy đã "Da nhan phong" ngay
     * lúc tạo + thanh toán đủ tiền mặt thì vẫn giữ nguyên "Da nhan phong".
     */
    private void capNhatDatPhongDaXacNhanNeuCanThanhToanDu(HoaDon hd) {
        DatPhong dp = hd.getD();
        if (dp == null || dp.getId() == null) {
            return;
        }
        // Lay ban ghi moi nhat tu DB, tranh ghi de bang 1 instance DatPhong cu/stale
        // duoc gan san trong doi tuong HoaDon truyen vao.
        DatPhong dpHienTai = datPhongService.findById(dp.getId());
        if (dpHienTai == null) {
            return;
        }
        if (HuyDonConstants.DP_TRANG_THAI_CHO_THANH_TOAN_TU_DONG.contains(dpHienTai.getTrangThai())) {
            dpHienTai.setTrangThai(HuyDonConstants.DP_DA_XAC_NHAN);
            dpHienTai.setNgayCapNhat(LocalDateTime.now());
            datPhongService.save(dpHienTai);
        }
    }

    private boolean isFullyPaid(HoaDon hd) {
        BigDecimal tongTien = hd.getTongTien() == null ? BigDecimal.ZERO : hd.getTongTien();
        BigDecimal daThanhToan = hd.getDaThanhToan() == null ? BigDecimal.ZERO : hd.getDaThanhToan();
        return daThanhToan.compareTo(BigDecimal.ZERO) > 0
                && tongTien.subtract(daThanhToan).compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * True nếu đơn đặt phòng đã có hóa đơn VÀ hóa đơn đó đã được thanh toán đủ
     * ("Da thanh toan") hoặc đã xuất PDF ("Da xuat" — chỉ xảy ra sau khi thanh
     * toán đủ). Dùng để chặn/ẩn các thao tác yêu cầu khách đã thanh toán đủ
     * (vd: "Khách nhận phòng" ở Sơ đồ phòng).
     */
    public boolean isDaThanhToanDu(Integer maDatPhong) {
        if (maDatPhong == null) {
            return false;
        }
        HoaDon hd = findByDatPhongId(maDatPhong);
        if (hd == null) {
            return false;
        }
        String tt = hd.getTrangThai();
        return TT_DA_THANH_TOAN.equals(tt) || TT_DA_XUAT.equals(tt);
    }

    /**
     * Trả về true nếu hóa đơn (nếu có) của đơn đặt phòng này đã được
     * xuất PDF — khi đó các thao tác chỉnh sửa trên đơn phải bị chặn.
     */
    public boolean isDaXuat(Integer maDatPhong) {
        if (maDatPhong == null) {
            return false;
        }
        HoaDon hd = findByDatPhongId(maDatPhong);
        return hd != null && TT_DA_XUAT.equals(hd.getTrangThai());
    }
}
