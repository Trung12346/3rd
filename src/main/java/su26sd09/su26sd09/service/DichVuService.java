package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.Dich_vu;
import su26sd09.su26sd09.repository.DichVuRepo;

import java.util.List;

@Service
public class DichVuService {

    @Autowired
    DichVuRepo dichVuRepo;

    public List<Dich_vu> findAll(){
        return dichVuRepo.findAll();
    }

    public Dich_vu findById(Integer id){
        return dichVuRepo.findById(id).orElse(null);
    }
}
