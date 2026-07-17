package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.ThanhToan;
import su26sd09.su26sd09.repository.ThanhToanRepo;

import java.util.List;

@Service
public class ThanhToanService {
    @Autowired
    ThanhToanRepo thanhToanRepo;


    public ThanhToan save(ThanhToan thanhToan){
        return thanhToanRepo.save(thanhToan);
    }

    public ThanhToan findByHoaDonId (int id){
        return thanhToanRepo.findByH_Id(id);
    }
    public List<ThanhToan> findAllByHoaDonId(int id) {
        return thanhToanRepo.findAllByH_Id(id);
    }

}
