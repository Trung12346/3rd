package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;
import su26sd09.su26sd09.repository.ChiTietDichvuRepo;

import java.util.List;

@Service
public class ChiTietDichVuService {

    @Autowired
    ChiTietDichvuRepo chiTietDichvuRepo;

    public List<Chi_tiet_dich_vu> findByDatPhongId(Integer id){
        return chiTietDichvuRepo.findByDatPhongId(id);

    }
    public Chi_tiet_dich_vu save(Chi_tiet_dich_vu ctdv){
        return chiTietDichvuRepo.save(ctdv);
    }

    public void deleteByDatPhongId(Integer id) {
        chiTietDichvuRepo.deleteByDatPhongId(id);
    }
}
