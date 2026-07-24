package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.repository.HoaDonRepo;

import java.math.BigDecimal;
import java.util.List;

@Service
public class HoaDonService {

    @Autowired
    HoaDonRepo hoaDonRepo;

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
                } else {
                    hd.setTrangThai(TT_CHO_THANH_TOAN);
                }
            }
        }
        return hoaDonRepo.save(hd);
    }

    private boolean isFullyPaid(HoaDon hd) {
        BigDecimal tongTien = hd.getTongTien() == null ? BigDecimal.ZERO : hd.getTongTien();
        BigDecimal daThanhToan = hd.getDaThanhToan() == null ? BigDecimal.ZERO : hd.getDaThanhToan();
        return daThanhToan.compareTo(BigDecimal.ZERO) > 0
                && tongTien.subtract(daThanhToan).compareTo(BigDecimal.ZERO) <= 0;
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
