package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.ThanhToan;
import su26sd09.su26sd09.repository.ThanhToanRepo;

@Service
public class ThanhToanService {
    @Autowired
    ThanhToanRepo thanhToanRepo;


    public ThanhToan save(ThanhToan thanhToan){
        return thanhToanRepo.save(thanhToan);
    }



}
