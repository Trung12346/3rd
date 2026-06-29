package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.repository.DatPhongRepo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class DatPhongService {

    @Autowired
    DatPhongRepo repo;




    public List<DatPhong> findAll(){
        return repo.findAll();
    }

    public Page<DatPhong> findAll(Pageable page){
        return repo.findAll(page);
    }

    public List<DatPhong> findByTrangThaiAndNgayTaoBefore(String trangThai, LocalDateTime ngay) {
        return repo.findByTrangThaiAndNgayTaoBefore(trangThai, ngay);
    }

    public void delete(DatPhong dp) {
        repo.delete(dp);
    }

    public void removePhong(DatPhong d){
        repo.delete(d);
    }



    public void updatePhong(DatPhong d){
        repo.save(d);
    }
    public void themPhong(DatPhong d){
        repo.save(d);
    }

    public Page<DatPhong> FindbyNguoiDung(int id, Pageable pageable){
        return repo.findByNguoiDung(id,pageable);
    }
    public List<DatPhong> FindbyNguoiDung(Integer id){
        return repo.FindByNguoiDung(id);
    }

    public List<Phong> findPhongByDatPhongId(Integer maDatPhong){
        System.out.println("Finding phong for datPhong id: " + maDatPhong);
        List<Phong> result = repo.findPhongByDatPhongId(maDatPhong);
        System.out.println("Result size: " + result.size());
        return result;
    }
    public List<DatPhong> search(
            Integer maDatPhong, String tenKhach, Integer maNhanVien, String ma_cccd,
            String ngayNhanTu, String ngayNhanDen, String ngayTraTu, String ngayTraDen,
            Integer soNguoiLon, Integer soTreEm, String trangThai, String yeuCauThem,
            String ngayTaoTu, String ngayTaoDen, String ngayCapNhatTu, String ngayCapNhatDen) {

        List<DatPhong> all = repo.findAll();

        return all.stream().filter(dp -> {
            if (maDatPhong != null && !maDatPhong.equals(dp.getId())) return false;

            if (tenKhach != null && !tenKhach.trim().isEmpty()) {
                if (dp.getN() == null) return false;
                if (!dp.getN().getHoTen().toLowerCase().contains(tenKhach.trim().toLowerCase())) return false;
            }

            if (maNhanVien != null && (dp.getNv() == null || !maNhanVien.equals(dp.getNv().getId()))) return false;
            if (ma_cccd != null && !ma_cccd.isEmpty() && !ma_cccd.equals(dp.getMa_cccd())) return false;
            if (soNguoiLon != null && !soNguoiLon.equals(dp.getSonguoiLon())) return false;
            if (soTreEm != null && !soTreEm.equals(dp.getSotreEm())) return false;
            if (trangThai != null && !trangThai.isEmpty() && !trangThai.equals(dp.getTrangThai())) return false;

            if (yeuCauThem != null && !yeuCauThem.isEmpty()) {
                if (dp.getYeuCauThem() == null) return false;
                if (!dp.getYeuCauThem().toLowerCase().contains(yeuCauThem.trim().toLowerCase())) return false;
            }
            if (ngayNhanTu != null && !ngayNhanTu.isEmpty()) {
                LocalDateTime tu = LocalDate.parse(ngayNhanTu).atStartOfDay();
                if (dp.getNgaydatPhong().isBefore(tu)) return false;
            }
            if (ngayNhanDen != null && !ngayNhanDen.isEmpty()) {
                LocalDateTime den = LocalDate.parse(ngayNhanDen).atTime(23, 59, 59);
                if (dp.getNgaydatPhong().isAfter(den)) return false;
            }

            if (ngayTraTu != null && !ngayTraTu.isEmpty()) {
                LocalDateTime tu = LocalDate.parse(ngayTraTu).atStartOfDay();
                if (dp.getNgaytraPhong().isBefore(tu)) return false;
            }
            if (ngayTraDen != null && !ngayTraDen.isEmpty()) {
                LocalDateTime den = LocalDate.parse(ngayTraDen).atTime(23, 59, 59);
                if (dp.getNgaytraPhong().isAfter(den)) return false;
            }

            if (ngayTaoTu != null && !ngayTaoTu.isEmpty()) {
                LocalDateTime tu = LocalDate.parse(ngayTaoTu).atStartOfDay();
                if (dp.getNgayTao().isBefore(tu)) return false;
            }
            if (ngayTaoDen != null && !ngayTaoDen.isEmpty()) {
                LocalDateTime den = LocalDate.parse(ngayTaoDen).atTime(23, 59, 59);
                if (dp.getNgayTao().isAfter(den)) return false;
            }

            if (ngayCapNhatTu != null && !ngayCapNhatTu.isEmpty()) {
                LocalDateTime tu = LocalDate.parse(ngayCapNhatTu).atStartOfDay();
                if (dp.getNgayCapNhat() == null || dp.getNgayCapNhat().isBefore(tu)) return false;
            }
            if (ngayCapNhatDen != null && !ngayCapNhatDen.isEmpty()) {
                LocalDateTime den = LocalDate.parse(ngayCapNhatDen).atTime(23, 59, 59);
                if (dp.getNgayCapNhat() == null || dp.getNgayCapNhat().isAfter(den)) return false;
            }

            return true;
        }).collect(Collectors.toList());
    }
    public DatPhong save(DatPhong datPhong){
        return repo.save(datPhong);
    }

    public DatPhong findById(int id){
        return repo.findById(id).orElse(null);
    }
}
