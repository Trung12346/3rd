package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.LichSuHoatDong;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.repository.LichSuHoatDongRepo;
import su26sd09.su26sd09.repository.NhanVienRepo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Ghi va tra cuu nhat ky hoat dong (audit log) cua nhan su (STAFF/ADMIN).
 *
 * Moi service/controller thuc hien mot "hanh dong nghiep vu" cua nhan su
 * (check-in, check-out, thu tien, hoan tien, xac nhan/huy don...) nen goi
 * {@link #ghiLog} (hoac {@link #ghiLogAn}) ngay sau khi thao tac chinh
 * thanh cong, de co mot dau vet day du ai lam gi, luc nao, tren doi tuong nao.
 */
@Service
public class LichSuHoatDongService {

    @Autowired
    private LichSuHoatDongRepo repo;

    @Autowired
    private NhanVienRepo nhanVienRepo;

    /**
     * Ghi 1 dong nhat ky hoat dong. Duoc chay trong mot transaction RIENG
     * (REQUIRES_NEW): neu viec ghi log loi vi ly do nao do, no se khong
     * lam rollback thao tac nghiep vu chinh (vd: check-in/thu tien) da
     * thuc hien truoc do.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ghiLog(NhanSu nhanSu, String loaiHanhDong, String doiTuong, Integer maDoiTuong, String ghiChu) {
        if (nhanSu == null || loaiHanhDong == null || loaiHanhDong.isBlank()) {
            return;
        }
        LichSuHoatDong log = new LichSuHoatDong();
        log.setNhanSu(nhanSu);
        log.setLoaiHanhDong(loaiHanhDong);
        log.setDoiTuong(doiTuong);
        log.setMaDoiTuong(maDoiTuong);
        log.setThoiGian(LocalDateTime.now());
        log.setGhiChu(ghiChu);
        repo.save(log);
    }

    /**
     * Tien ich: ghi log tu Authentication (email nhan su dang dang nhap o
     * /nhan-su/**). Neu khong tim thay nhan su tuong ung (vd: goi tu luong
     * khach hang tu phuc vu) thi bo qua, khong ghi.
     */
    public void ghiLog(Authentication authentication, String loaiHanhDong, String doiTuong, Integer maDoiTuong, String ghiChu) {
        if (authentication == null) {
            return;
        }
        NhanSu nv = nhanVienRepo.findByEmail(authentication.getName()).orElse(null);
        if (nv == null) {
            return;
        }
        ghiLog(nv, loaiHanhDong, doiTuong, maDoiTuong, ghiChu);
    }

    /**
     * Ghi log "an toan": khong bao gio nem exception ra ngoai (kê ca khi
     * repo.save that bai vi ly do ha tang), vi day chi la nghiep vu phu tro
     * (audit), khong duoc phep lam hong luong nghiep vu chinh.
     */
    public void ghiLogAn(Authentication authentication, String loaiHanhDong, String doiTuong, Integer maDoiTuong, String ghiChu) {
        try {
            ghiLog(authentication, loaiHanhDong, doiTuong, maDoiTuong, ghiChu);
        } catch (Exception ignored) {
            // audit khong duoc phep lam vo hieu thao tac nghiep vu chinh
        }
    }

    public Page<LichSuHoatDong> search(String hoTenNv, String loaiHanhDong, String doiTuong, Integer maDoiTuong,
                                        LocalDate tuNgay, LocalDate denNgay, int page, int size) {
        LocalDateTime tu = (tuNgay == null) ? null : tuNgay.atStartOfDay();
        LocalDateTime den = (denNgay == null) ? null : LocalDateTime.of(denNgay, LocalTime.of(23, 59, 59));
        // Luu y: day la native query, da tu sap xep "ORDER BY l.thoi_gian DESC"
        // ngay trong @Query. KHONG duoc truyen them Sort vao PageRequest o day —
        // Spring Data se noi them "order by <ten thuoc tinh Java>" (vd: "thoiGian")
        // vao cuoi cau SQL goc thay vi ten cot that (thoi_gian), gay loi
        // "Invalid column name 'thoiGian'" tren SQL Server.
        return repo.search(
                blankToNull(hoTenNv), blankToNull(loaiHanhDong), blankToNull(doiTuong), maDoiTuong, tu, den,
                PageRequest.of(page, size)
        );
    }

    public List<String> danhSachLoaiHanhDong() {
        return repo.findDistinctLoaiHanhDong();
    }

    public List<String> danhSachDoiTuong() {
        return repo.findDistinctDoiTuong();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
