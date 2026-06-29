package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.repository.DanhGiaRepo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class DanhGiaService {


    @Autowired
    DanhGiaRepo repo;


    public List<DanhGia> findAll(){
        return repo.findAll();
    }

    public List<DanhGia> search(String keyword) {
        List<DanhGia> danhGias = repo.findAll()
                .stream()
                .sorted((a, b) -> {
                    if (a.getNgayTao() == null && b.getNgayTao() == null) return 0;
                    if (a.getNgayTao() == null) return 1;
                    if (b.getNgayTao() == null) return -1;
                    return b.getNgayTao().compareTo(a.getNgayTao());
                })
                .toList();

        if (keyword == null || keyword.isBlank()) {
            return danhGias;
        }

        String q = keyword.toLowerCase(Locale.ROOT);
        return danhGias.stream()
                .filter(d -> contains(d.getNoiDung(), q)
                        || contains(d.getPhanHoi(), q)
                        || (d.getN() != null && (contains(d.getN().getHoTen(), q) || contains(d.getN().getEmail(), q)))
                        || (d.getD() != null && String.valueOf(d.getD().getId()).contains(q)))
                .toList();
    }

    public DanhGia findByid(int id){
        return repo.findById(id).orElse(null);
    }

    public void remove(DanhGia danhgia){
        repo.delete(danhgia);
    }
    public void save(DanhGia danhGia){
        repo.save(danhGia);
    }
    public Page<DanhGia> findByNguoiDung(int id , Pageable pageable){
      return repo.findByNguoiDung(id,pageable);
    }

    public List<DanhGia> findByNguoiDung(int id ){
      return repo.FindByNguoiDung(id);
    }

    public List<DanhGia> findDaDuyetByPhong(int maPhong) {
        return repo.findDaDuyetByPhong(maPhong);
    }

    public void updateDuyet(int id, boolean daDuyet) {
        DanhGia danhGia = findByid(id);
        if (danhGia == null) {
            return;
        }
        danhGia.setDaDuyet(daDuyet);
        repo.save(danhGia);
    }

    public void updatePhanHoi(int id, String phanHoi) {
        DanhGia danhGia = findByid(id);
        if (danhGia == null) {
            return;
        }
        danhGia.setPhanHoi(phanHoi);
        repo.save(danhGia);
    }

    public void removeById(int id) {
        repo.findById(id).ifPresent(repo::delete);
    }

    public void createReview(NguoiDung nguoiDung, int diemDanhGia, String noiDung) {
        DanhGia danhGia = new DanhGia();
        danhGia.setN(nguoiDung);
        danhGia.setDiemDanhGia(Math.max(1, Math.min(5, diemDanhGia)));
        danhGia.setNoiDung(noiDung);
        danhGia.setDaDuyet(false);        danhGia.setNgayTao(LocalDateTime.now());
        repo.save(danhGia);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

}
