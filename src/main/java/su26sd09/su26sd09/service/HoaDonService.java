package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.HoaDon;
import su26sd09.su26sd09.repository.HoaDonRepo;

import java.util.List;

@Service
public class HoaDonService {

    @Autowired
    HoaDonRepo hoaDonRepo;

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
}
