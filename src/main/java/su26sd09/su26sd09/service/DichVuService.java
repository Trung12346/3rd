package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.Dich_vu;
import su26sd09.su26sd09.repository.ChiTietDichvuRepo;
import su26sd09.su26sd09.repository.DichVuRepo;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DichVuService {

    @Autowired
    DichVuRepo dichVuRepo;

    @Autowired
    ChiTietDichvuRepo chiTietDichvuRepo;

    public List<Dich_vu> findAll(){
        return dichVuRepo.findAll();
    }

    public Dich_vu findById(Integer id){
        return dichVuRepo.findById(id).orElse(null);
    }

    public List<Dich_vu> search(String keyword, String trangThai){
        return dichVuRepo.search(keyword, trangThai);
    }

    public Dich_vu save(Dich_vu dv){
        return dichVuRepo.save(dv);
    }

    public void deleteById(Integer id){
        dichVuRepo.deleteById(id);
    }

    public long countAll(){
        return dichVuRepo.count();
    }

    public long countActive(){
        return dichVuRepo.countActive();
    }

    public Map<Integer, Long> soLuongSuDungTheoDichVu(){
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] row : chiTietDichvuRepo.thongKeSoLuongTheoDichVu()){
            Integer maDichVu = (Integer) row[0];
            Long tong = ((Number) row[1]).longValue();
            map.put(maDichVu, tong);
        }
        return map;
    }

    public Dich_vu dichVuDuocSuDungNhieuNhat(){
        List<Object[]> thongKe = chiTietDichvuRepo.thongKeSoLuongTheoDichVu();
        if (thongKe.isEmpty()) return null;
        Integer maDichVu = (Integer) thongKe.get(0)[0];
        return findById(maDichVu);
    }

    public BigDecimal tongTienDichVu(){
        BigDecimal tong = chiTietDichvuRepo.tongTienDichVu();
        return tong != null ? tong : BigDecimal.ZERO;
    }
}
